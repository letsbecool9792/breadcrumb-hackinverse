import { type Request, type Response } from "express";
import { type Db } from "mongodb";
import { createHash } from "node:crypto";
import { GoogleGenAI, Type } from "@google/genai";
import { datedAt, memories, type MemoryDoc, type MemoryType, type Enrichment } from "./memories.ts";
import { type IngestResult, type IncomingMemory, MAX_MEMORY_BATCH } from "./api.ts";

const INGEST_MODEL = "gemini-3.5-flash-lite";

function getFingerprint(text: string): string {
  return createHash("sha256").update(text).digest("hex");
}

function extractLinkSites(text?: string | null): string[] {
  if (!text) return [];
  const urls = text.match(/https?:\/\/[^\s"'<>]+/g);
  if (!urls) return [];
  const sites = new Set<string>();
  for (const url of urls) {
    try {
      const hostname = new URL(url).hostname;
      const domain = hostname.replace(/^www\./i, "");
      sites.add(domain);
    } catch {
      // Ignore invalid URLs
    }
  }
  return Array.from(sites);
}

export function parseMemory(raw: any): Partial<MemoryDoc> {
  const parsed = {
    _id: String(raw.id),
    type: String(raw.type) as MemoryType,
    hasLink: Boolean(raw.hasLink),
    capturedAt: new Date(raw.capturedAt),
    contentCreatedAt: raw.contentCreatedAt ? new Date(raw.contentCreatedAt) : null,
    sourceApp: raw.sourceApp ? String(raw.sourceApp) : null,
    sourceAppLabel: raw.sourceAppLabel ? String(raw.sourceAppLabel) : null,
    title: raw.title ? String(raw.title) : null,
    rawText: raw.rawText ? String(raw.rawText) : null,
    extractedText: raw.extractedText ? String(raw.extractedText) : null,
    note: raw.note ? String(raw.note) : null,
    updatedAt: raw.updatedAt ? new Date(raw.updatedAt) : new Date(raw.capturedAt),
  };
  
  const dated = datedAt(parsed);
  const linkSites = extractLinkSites([parsed.rawText, parsed.extractedText].filter(Boolean).join(" "));
  
  return { ...parsed, datedAt: dated, linkSites };
}

class RateLimiter {
  private timestamps: number[] = [];
  private maxRequests: number;
  private windowMs: number;
  
  constructor(maxRequests: number, windowMs: number) {
    this.maxRequests = maxRequests;
    this.windowMs = windowMs;
  }

  async acquire(timeoutMs: number): Promise<boolean> {
    const start = Date.now();
    while (true) {
      const now = Date.now();
      this.timestamps = this.timestamps.filter((t) => now - t < this.windowMs);
      if (this.timestamps.length < this.maxRequests) {
        this.timestamps.push(now);
        return true;
      }
      if (now - start > timeoutMs) {
        return false;
      }
      await new Promise((r) => setTimeout(r, 100));
    }
  }
}

const limiter = new RateLimiter(Number(process.env.GEMINI_INGEST_PER_MINUTE || 15), 60000);

export type Enricher = (text: string, asOf: Date) => Promise<Omit<Enrichment, "at">>;

export async function defaultEnricher(text: string, asOf: Date): Promise<Omit<Enrichment, "at">> {
  const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
  const prompt = `Read the following text saved by a user.
Date saved/taken: ${asOf.toISOString()}
Extract information from it.`;
  
  const response = await ai.models.generateContent({
    model: INGEST_MODEL,
    contents: `${prompt}\n\n${text}`,
    config: {
      responseMimeType: "application/json",
      responseSchema: {
        type: Type.OBJECT,
        properties: {
          summary: { type: Type.STRING, description: "One line summary in the words someone would use looking for it" },
          kind: { type: Type.STRING, description: "The kind of content (e.g. 'receipt', 'job posting', 'article')" },
          entities: { type: Type.ARRAY, items: { type: Type.STRING } },
          dates: { type: Type.ARRAY, items: { type: Type.STRING } },
        },
        required: ["summary", "kind", "entities", "dates"],
      }
    }
  });

  if (!response.text) {
      throw new Error("Model returned no text");
  }

  const data = JSON.parse(response.text);
  return data;
}

export function makeIngestHandler(database: Db, enricher: Enricher = defaultEnricher) {
  return async (req: Request, res: Response) => {
    let items = req.body;
    if (!Array.isArray(items)) {
      items = [items];
    }
    
    if (items.length === 0 || items.length > MAX_MEMORY_BATCH) {
      res.status(400).json({ error: "invalid batch size" });
      return;
    }

    const results: IngestResult[] = [];
    const collection = memories(database);

    for (const item of items) {
      if (!item || typeof item.id !== "string") {
         results.push({ id: item?.id || "unknown", enriched: false, embedded: false, reason: "invalid memory" });
         continue;
      }

      let parsed: Partial<MemoryDoc>;
      try {
        parsed = parseMemory(item);
      } catch (e) {
        results.push({ id: item.id, enriched: false, embedded: false, reason: "invalid memory format" });
        continue;
      }

      const id = parsed._id!;
      const textToEnrich = [parsed.title, parsed.rawText, parsed.extractedText].filter(Boolean).join("\n");
      const textFingerprint = textToEnrich ? getFingerprint(textToEnrich) : undefined;
      
      let enriched = false;
      let reason: string | undefined;
      let retryable: boolean | undefined;
      let enrichment: Enrichment | undefined;

      // Only call model if there is text
      if (textToEnrich) {
        // Fetch existing memory to check fingerprint
        const existing = await collection.findOne({ _id: id });
        if (existing?.enrichedFrom === textFingerprint && existing?.enrichment) {
           enrichment = existing.enrichment;
           enriched = true;
        } else {
           const acquired = await limiter.acquire(60000);
           if (!acquired) {
             reason = "rate limited";
             retryable = true;
           } else {
             try {
               const result = await enricher(textToEnrich, parsed.datedAt!);
               enrichment = { ...result, at: new Date() };
               enriched = true;
             } catch (e: any) {
               reason = e.message || "model error";
               retryable = e.status === 429 || e.status === 503 || e.status === 500;
             }
           }
        }
      } else {
        enriched = false;
        reason = "no text";
      }

      const now = new Date();
      const doc: MemoryDoc = {
        _id: id,
        type: parsed.type!,
        hasLink: parsed.hasLink!,
        capturedAt: parsed.capturedAt!,
        contentCreatedAt: parsed.contentCreatedAt,
        datedAt: parsed.datedAt!,
        sourceApp: parsed.sourceApp,
        sourceAppLabel: parsed.sourceAppLabel,
        linkSites: parsed.linkSites,
        title: parsed.title,
        rawText: parsed.rawText,
        extractedText: parsed.extractedText,
        note: parsed.note,
        updatedAt: parsed.updatedAt!,
        syncedAt: now,
      };

      if (enrichment) {
        doc.enrichment = enrichment;
        doc.enrichedFrom = textFingerprint;
      } else if (reason) {
        doc.enrichmentError = reason;
      }

      await collection.updateOne(
        { _id: id },
        { $set: doc },
        { upsert: true }
      );

      results.push({ id, enriched, embedded: false, reason, retryable });
    }

    const hasRetryable = results.some(r => r.retryable);
    if (hasRetryable) {
      res.status(503).json({ results });
    } else {
      res.status(200).json({ results });
    }
  };
}
