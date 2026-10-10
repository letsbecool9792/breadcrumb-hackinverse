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

export type Embedder = (texts: string[], titles: string[]) => Promise<number[][]>;

function l2Normalize(vector: number[]): number[] {
  const norm = Math.sqrt(vector.reduce((sum, val) => sum + val * val, 0));
  if (norm === 0) return vector;
  return vector.map(val => val / norm);
}

export async function defaultEmbedder(texts: string[], titles: string[]): Promise<number[][]> {
  const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
  
  const results = await Promise.all(texts.map(async (text, i) => {
    const response = await ai.models.embedContent({
      model: "gemini-embedding-001",
      contents: text,
      config: {
        taskType: "RETRIEVAL_DOCUMENT" as any,
        title: titles[i] || undefined,
        outputDimensionality: 768
      }
    });
    if (!response.embeddings || !response.embeddings[0] || !response.embeddings[0].values) {
      throw new Error("Model returned no embeddings");
    }
    return l2Normalize(response.embeddings[0].values);
  }));
  
  return results;
}

export function makeIngestHandler(database: Db, enricher: Enricher = defaultEnricher, embedder: Embedder = defaultEmbedder) {
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
      const existing = await collection.findOne({ _id: id });
      const now = new Date();
      
      // 1. Enrich
      const textToEnrich = [parsed.title, parsed.rawText, parsed.extractedText].filter(Boolean).join("\n");
      const textFingerprint = textToEnrich ? getFingerprint(textToEnrich) : undefined;
      
      let enriched = false;
      let reason: string | undefined;
      let retryable: boolean | undefined;
      let enrichment: Enrichment | undefined = existing?.enrichment;
      let enrichmentError: string | undefined = existing?.enrichmentError;

      if (textToEnrich) {
        if (existing?.enrichedFrom === textFingerprint && existing?.enrichment) {
           enriched = true;
        } else {
           const acquired = await limiter.acquire(60000);
           if (!acquired) {
             reason = "rate limited";
             retryable = true;
           } else {
             try {
               const result = await enricher(textToEnrich, parsed.datedAt!);
               enrichment = { ...result, at: now };
               enriched = true;
               enrichmentError = undefined;
             } catch (e: any) {
               reason = e.message || "model error";
               enrichmentError = reason;
               retryable = e.status === 429 || e.status === 503 || e.status === 500;
             }
           }
        }
      } else {
        enriched = false;
        reason = "no text";
        enrichmentError = "no text";
      }

      // 2. Embed
      let embedded = false;
      let embeddingError: string | undefined;
      let embedding: number[] | undefined = existing?.embedding;
      let embeddedWith = existing?.embeddedWith;
      
      const textToEmbedParts = [parsed.note, parsed.title, enrichment?.summary, parsed.rawText, parsed.extractedText].filter(Boolean);
      const textToEmbedFull = textToEmbedParts.join("\n");
      const textToEmbed = textToEmbedFull.slice(0, 6000);
      const embedFingerprint = textToEmbed ? getFingerprint(textToEmbed) : undefined;
      
      if (textToEmbed) {
        if (existing?.embeddedWith?.from === embedFingerprint && existing?.embedding) {
           embedded = true;
        } else {
           try {
             const vectors = await embedder([textToEmbed], [parsed.title || ""]);
             embedding = vectors[0];
             embeddedWith = {
               model: "gemini-embedding-001",
               dimensions: 768,
               from: embedFingerprint,
               at: now
             };
             embedded = true;
           } catch (e: any) {
             reason = reason ? `${reason}; embedding failed` : "embedding failed";
             embeddingError = e.message || "embedding failed";
             retryable = retryable || e.status === 429 || e.status === 503 || e.status === 500;
           }
        }
      }

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

      if (enrichment && enriched) {
        doc.enrichment = enrichment;
        doc.enrichedFrom = textFingerprint;
      }
      if (enrichmentError) {
        doc.enrichmentError = enrichmentError;
      }
      if (embedding && embedded) {
        doc.embedding = embedding;
        doc.embeddedWith = embeddedWith;
      }

      await collection.updateOne(
        { _id: id },
        { $set: doc },
        { upsert: true }
      );

      results.push({ id, enriched, embedded, reason, retryable });
    }

    const hasRetryable = results.some(r => r.retryable);
    if (hasRetryable) {
      res.status(503).json({ results });
    } else {
      res.status(200).json({ results });
    }
  };
}
