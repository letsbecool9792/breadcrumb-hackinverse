import { type Request, type Response } from "express";
import { type Db } from "mongodb";
import { createHash } from "node:crypto";
import { GoogleGenAI } from "@google/genai";
import { memories, type MemoryDoc } from "./memories.ts";
import { type IngestResult } from "./api.ts";
import { defaultEmbedder, type Embedder } from "./ingest.ts";

function getFingerprint(text: string): string {
  return createHash("sha256").update(text).digest("hex");
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

export type ImageReader = (mimeType: string, base64: string, note?: string | null) => Promise<string>;

export async function defaultImageReader(mimeType: string, base64: string, note?: string | null): Promise<string> {
  const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
  const prompt = "Read the text in this image. Do not describe the image, just output the text." + (note ? ` Context note from the user: ${note}` : "");
  
  const response = await ai.models.generateContent({
    model: "gemini-3.5-flash-lite",
    contents: [
       prompt,
       { inlineData: { mimeType, data: base64 } }
    ]
  });
  
  if (!response.text) {
      throw new Error("Model returned no text");
  }
  return response.text;
}

export function makeImageHandler(database: Db, imageReader: ImageReader = defaultImageReader, embedder: Embedder = defaultEmbedder) {
  return async (req: Request, res: Response) => {
    let items = req.body;
    if (!Array.isArray(items)) {
      items = [items];
    }
    
    if (items.length === 0 || items.length > 8) { // max 8 per request based on api.md "An array of 1 to 8"
      res.status(400).json({ error: "invalid batch size" });
      return;
    }

    const results: IngestResult[] = [];
    const collection = memories(database);

    for (const item of items) {
      if (!item || typeof item.id !== "string" || typeof item.data !== "string" || typeof item.mimeType !== "string") {
         results.push({ id: item?.id || "unknown", enriched: false, embedded: false, reason: "invalid image format" });
         continue;
      }

      const id = item.id;
      const existing = await collection.findOne({ _id: id });
      
      if (!existing) {
         results.push({ id, enriched: false, embedded: false, reason: "no such memory" });
         continue;
      }

      const incomingFingerprint = getFingerprint(item.data);
      const now = new Date();
      
      let enriched = false;
      let embedded = false;
      let reason: string | undefined;
      let retryable: boolean | undefined;

      let readText = existing.enrichment?.readText;
      let imageFingerprint = existing.imageFingerprint;
      let enrichmentError = existing.enrichmentError;

      // 1. Read Image
      if (imageFingerprint === incomingFingerprint && typeof readText === "string") {
         enriched = true;
      } else {
         const acquired = await limiter.acquire(60000);
         if (!acquired) {
           reason = "rate limited";
           retryable = true;
         } else {
           try {
             readText = await imageReader(item.mimeType, item.data, existing.note);
             imageFingerprint = incomingFingerprint;
             enriched = true;
             enrichmentError = undefined;
           } catch (e: any) {
             reason = e.message || "model error";
             enrichmentError = reason;
             retryable = e.status === 429 || e.status === 503 || e.status === 500;
           }
         }
      }

      // 2. Re-embed
      let embeddingError: string | undefined;
      let embedding: number[] | undefined = existing.embedding;
      let embeddedWith = existing.embeddedWith;
      
      const textToEmbedParts = [existing.note, existing.title, existing.enrichment?.summary, existing.rawText, existing.extractedText, readText].filter(Boolean);
      const textToEmbedFull = textToEmbedParts.join("\n");
      const textToEmbed = textToEmbedFull.slice(0, 6000);
      const embedFingerprint = textToEmbed ? getFingerprint(textToEmbed) : undefined;
      
      if (textToEmbed) {
        if (existing.embeddedWith?.from === embedFingerprint && existing.embedding) {
           embedded = true;
        } else {
           try {
             const vectors = await embedder([textToEmbed], [existing.title || ""]);
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

      // Update Database
      const updateDoc: Partial<MemoryDoc> = {};
      
      if (enriched) {
         if (!existing.enrichment) {
            updateDoc.enrichment = { readText, at: now } as any;
         } else {
            updateDoc.enrichment = { ...existing.enrichment, readText, at: now };
         }
         updateDoc.imageFingerprint = imageFingerprint;
         updateDoc.imageReadAt = now;
      }
      if (enrichmentError) {
         updateDoc.enrichmentError = enrichmentError;
      }
      if (embedded) {
         updateDoc.embedding = embedding;
         updateDoc.embeddedWith = embeddedWith;
      }

      if (Object.keys(updateDoc).length > 0) {
         await collection.updateOne({ _id: id }, { $set: updateDoc });
      }

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
