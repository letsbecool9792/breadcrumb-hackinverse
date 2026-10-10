import { type Request, type Response } from "express";
import { type Db, type Document } from "mongodb";
import { memories, type MemoryDoc } from "./memories.ts";
import { GoogleGenAI } from "@google/genai";
import { defaultSearchParser, getSourceLabels, type SearchInterpretation, type SearchParser } from "./search_parser.ts";

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

const parseCache = new Map<string, { interpretation: SearchInterpretation; at: string }>();

function buildFilters(interp: SearchInterpretation): any {
  const filters: any = {};
  if (interp.types.length > 0) {
    if (interp.types.includes("LINK")) {
      const otherTypes = interp.types.filter(t => t !== "LINK");
      if (otherTypes.length > 0) {
         filters.$or = [
           { type: { $in: otherTypes } },
           { type: "LINK" },
           { hasLink: true }
         ];
      } else {
         filters.$or = [
           { type: "LINK" },
           { hasLink: true }
         ];
      }
    } else {
      filters.type = { $in: interp.types };
    }
  }

  if (interp.from || interp.to) {
    filters.datedAt = {};
    if (interp.from) filters.datedAt.$gte = new Date(interp.from);
    if (interp.to) filters.datedAt.$lte = new Date(interp.to + "T23:59:59.999Z");
  }

  if (interp.sourceApp) {
    if (!filters.$or) filters.$or = [];
    filters.$or.push({ sourceAppLabel: interp.sourceApp });
    filters.$or.push({ linkSites: interp.sourceApp });
  }
  
  return filters;
}

function convertToSearchCompound(filters: any): any[] {
  const compound: any[] = [];
  if (filters.type && filters.type.$in) {
    compound.push({ text: { path: "type", query: filters.type.$in } });
  }
  if (filters.$or) {
    const shoulds = filters.$or.map((o: any) => {
      if (o.type && o.type.$in) return { text: { path: "type", query: o.type.$in } };
      if (o.type === "LINK") return { text: { path: "type", query: "LINK" } };
      if (o.hasLink === true) return { equals: { path: "hasLink", value: true } };
      if (o.sourceAppLabel) return { text: { path: "sourceAppLabel", query: o.sourceAppLabel } };
      if (o.linkSites) return { text: { path: "linkSites", query: o.linkSites } };
      return null;
    }).filter(Boolean);
    if (shoulds.length > 0) {
       compound.push({ compound: { should: shoulds, minimumShouldMatch: 1 } });
    }
  }
  if (filters.datedAt) {
    const range: any = { path: "datedAt" };
    if (filters.datedAt.$gte) range.gte = filters.datedAt.$gte;
    if (filters.datedAt.$lte) range.lte = filters.datedAt.$lte;
    compound.push({ range });
  }
  return compound;
}

export function makeSearchHandler(database: Db, embedder: QueryEmbedder = defaultQueryEmbedder, parser: SearchParser = defaultSearchParser) {
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
      const now = new Date();
      const todayStr = now.toISOString().split("T")[0]!;

      // Parse and embed raw concurrently
      const rawEmbedPromise = embedder(q).catch(() => null);
      
      const sourceLabels = await getSourceLabels(database);
      const cacheKey = `${q}::${sourceLabels.join(",")}::${todayStr}`;
      
      let interp: SearchInterpretation | null = null;
      let interpError: string | null = null;

      if (parseCache.has(cacheKey) && parseCache.get(cacheKey)!.at === todayStr) {
        interp = parseCache.get(cacheKey)!.interpretation;
      } else {
        try {
          interp = await parser(q, now, sourceLabels);
          parseCache.set(cacheKey, { interpretation: interp, at: todayStr });
        } catch (e: any) {
          interpError = e.message || "parse failed";
        }
      }

      const filters = interp ? buildFilters(interp) : {};
      const filterKeys = Object.keys(filters);
      
      const actualQuery = interp?.query?.trim() || q;
      const isAllFilter = interp && actualQuery === "" && filterKeys.length > 0;

      const results: any[] = [];

      if (isAllFilter) {
        const docs = await collection.find(filters).sort({ datedAt: -1 }).limit(limit).toArray();
        docs.forEach((doc, i) => {
          results.push({
            id: doc._id,
            score: null,
            ranks: { vector: undefined, text: undefined },
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
          });
        });
      } else {
        let queryVector = await rawEmbedPromise;
        if (actualQuery !== q) {
          try {
            queryVector = await embedder(actualQuery);
          } catch (e: any) {
             // Fall back
          }
        }
        
        if (!queryVector) {
           res.status(503).json({ error: "could not read the search", retryable: true });
           return;
        }

        const fusionLimit = Math.max(limit * 2, 20);

        // 1. Vector Search
        const vectorStage: any = {
          index: "memories_vector",
          path: "embedding",
          queryVector,
          numCandidates: Math.max(limit * 10, 100),
          limit: fusionLimit,
        };
        if (filterKeys.length > 0) {
           vectorStage.filter = filters;
        }

        const vectorResults = await collection.aggregate([
          { $vectorSearch: vectorStage },
          { $project: { _id: 1, _score: { $meta: "vectorSearchScore" } } }
        ]).toArray();

        // 2. Text Search
        const textFilters = convertToSearchCompound(filters);
        const textStage: any = {
          index: "memories_text",
          compound: {
             must: [
               {
                 text: {
                   query: actualQuery,
                   path: ["title", "note", "enrichment.summary", "rawText", "extractedText", "enrichment.readText"]
                 }
               }
             ]
          }
        };
        if (textFilters.length > 0) {
           textStage.compound.filter = textFilters;
        }

        const textResults = await collection.aggregate([
          { $search: textStage },
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

        const sortedIds = Array.from(combined.entries())
          .sort((a, b) => b[1].score - a[1].score)
          .slice(0, limit);

        const docs = await collection.find({ _id: { $in: sortedIds.map(e => e[0]) } as any }).toArray();
        const docMap = new Map(docs.map(d => [d._id, d]));

        sortedIds.forEach(([id, info]) => {
          const doc = docMap.get(id);
          if (!doc) return;
          results.push({
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
          });
        });
      }

      res.status(200).json({
        query: actualQuery,
        interpretation: interp,
        interpretationError: interpError,
        results
      });

    } catch (e: any) {
      console.error(e);
      res.status(500).json({ error: "internal" });
    }
  };
}
