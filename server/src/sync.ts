import { type Request, type Response } from "express";
import { type Db } from "mongodb";
import { memories } from "./memories.ts";

export function makeEnrichmentHandler(database: Db) {
  return async (req: Request, res: Response) => {
    try {
      const ids = req.body.ids;
      if (!Array.isArray(ids) || ids.length < 1 || ids.length > 100) {
        res.status(400).json({ error: "missing or invalid ids array" });
        return;
      }
      
      const collection = memories(database);
      const docs = await collection.find({ _id: { $in: ids as any[] } as any }).toArray();
      
      const results = docs.map(doc => ({
        id: doc._id,
        summary: doc.enrichment?.summary || null,
        kind: doc.enrichment?.kind || null,
        readText: doc.enrichment?.readText || null
      }));
      
      res.status(200).json({ results });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "internal" });
    }
  };
}

export function makeDeleteHandler(database: Db) {
  return async (req: Request, res: Response) => {
    try {
      const ids = req.body.ids;
      if (!Array.isArray(ids) || ids.length < 1 || ids.length > 100) {
        res.status(400).json({ error: "missing or invalid ids array" });
        return;
      }
      
      const collection = memories(database);
      const result = await collection.deleteMany({ _id: { $in: ids as any[] } as any });
      
      res.status(200).json({ deleted: result.deletedCount });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "internal" });
    }
  };
}
