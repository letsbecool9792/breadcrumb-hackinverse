import { type Request, type Response } from "express";
import { type Db } from "mongodb";
import { memories, type MemoryDoc } from "./memories.ts";
import { GoogleGenAI } from "@google/genai";

function l2Normalize(vector: number[]): number[] {
  const norm = Math.sqrt(vector.reduce((sum, val) => sum + val * val, 0));
  return norm === 0 ? vector : vector.map(val => val / norm);
}

export type QueryEmbedder = (query: string) => Promise<number[]>;

export async function defaultQueryEmbedder(query: string): Promise<number[]> {
  const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
  const response = await ai.models.embedContent({
    model: "gemini-embedding-001",
    contents: query,
    config: {
      taskType: "RETRIEVAL_QUERY" as any,
      outputDimensionality: 768
    }
  });
  if (!response.embeddings || !response.embeddings[0] || !response.embeddings[0].values) {
    throw new Error("Model returned no embeddings");
  }
  return l2Normalize(response.embeddings[0].values);
}

export function makeSearchHandler(database: Db, embedder: QueryEmbedder = defaultQueryEmbedder) {
  return async (req: Request, res: Response) => {
    try {
      const q = req.query.q as string;
      if (!q || typeof q !== "string") {
        res.status(400).json({ error: "missing q parameter" });
        return;
      }
      if (q.length > 500) {
        res.status(400).json({ error: "q is too long" });
        return;
      }
      let limit = 20;
      if (req.query.limit !== undefined) {
        limit = Number(req.query.limit);
        if (Number.isNaN(limit) || limit < 1 || limit > 50) {
          res.status(400).json({ error: "invalid limit parameter" });
          return;
        }
      }

      const collection = memories(database);

      // Embed the query
      let queryVector: number[];
      try {
        queryVector = await embedder(q);
      } catch (e: any) {
        if (e.status === 429 || e.status === 503) {
            res.status(503).json({ error: "could not read the search", reason: e.message || "rate limited", retryable: true });
        } else {
            res.status(502).json({ error: "could not read the search", reason: e.message || "model refused", retryable: false });
        }
        return;
      }

      const fusionLimit = Math.max(limit * 2, 20);

      // 1. Vector Search
      const vectorResults = await collection.aggregate([
        {
          $vectorSearch: {
            index: "memories_vector",
            path: "embedding",
            queryVector,
            numCandidates: Math.max(limit * 10, 100),
            limit: fusionLimit,
          }
        },
        { $project: { _id: 1, _score: { $meta: "vectorSearchScore" } } }
      ]).toArray();

      // 2. Text Search
      const textResults = await collection.aggregate([
        {
          $search: {
            index: "memories_text",
            text: {
              query: q,
              path: ["title", "note", "enrichment.summary", "rawText", "extractedText", "enrichment.readText"]
            }
          }
        },
        { $limit: fusionLimit },
        { $project: { _id: 1, _score: { $meta: "searchScore" } } }
      ]).toArray();

      const combined = new Map<string, { doc: MemoryDoc | null, ranks: { vector?: number; text?: number }, score: number }>();

      vectorResults.forEach((res, i) => {
        const id = res._id.toString();
        if (!combined.has(id)) combined.set(id, { doc: null, ranks: {}, score: 0 });
        const entry = combined.get(id)!;
        entry.ranks.vector = i + 1;
        entry.score += 1 / (60 + (i + 1));
      });

      textResults.forEach((res, i) => {
        const id = res._id.toString();
        if (!combined.has(id)) combined.set(id, { doc: null, ranks: {}, score: 0 });
        const entry = combined.get(id)!;
        entry.ranks.text = i + 1;
        entry.score += 1 / (60 + (i + 1));
      });

      // Sort combined by score
      const sortedIds = Array.from(combined.entries())
        .sort((a, b) => b[1].score - a[1].score)
        .slice(0, limit);

      // Fetch the actual documents
      const docs = await collection.find({ _id: { $in: sortedIds.map(e => e[0]) } as any }).toArray();
      const docMap = new Map(docs.map(d => [d._id, d]));

      const results = sortedIds.map(([id, info]) => {
        const doc = docMap.get(id);
        if (!doc) return null; // Should not happen
        return {
          id: doc._id,
          score: info.score,
          ranks: info.ranks,
          type: doc.type,
          hasLink: doc.hasLink,
          capturedAt: doc.capturedAt,
          datedAt: doc.datedAt,
          sourceAppLabel: doc.sourceAppLabel || null,
          title: doc.title || null,
          rawText: doc.rawText || null,
          extractedText: doc.extractedText || null,
          note: doc.note || null,
          readText: doc.enrichment?.readText || null,
          summary: doc.enrichment?.summary || null,
          kind: doc.enrichment?.kind || null
        };
      }).filter(Boolean);

      res.status(200).json({
        query: q,
        interpretation: null,
        interpretationError: "not built yet", // S6 builds this
        results
      });

    } catch (e: any) {
      console.error(e);
      res.status(500).json({ error: "internal" });
    }
  };
}
