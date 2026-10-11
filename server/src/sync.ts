import type { Request, Response } from "express";
import type { Db } from "mongodb";
import { MAX_IDS, type EnrichmentReply } from "./api.ts";
import { memories } from "./memories.ts";

const MAX_ID_CHARS = 200;

/** The ids of a request, or null when they are not 1 to MAX_IDS non-empty strings. */
function idsOf(body: unknown): string[] | null {
  const ids = (body as { ids?: unknown } | null)?.ids;
  if (!Array.isArray(ids) || ids.length < 1 || ids.length > MAX_IDS) return null;
  return ids.every((id) => typeof id === "string" && id.trim() !== "" && id.length <= MAX_ID_CHARS) ? (ids as string[]) : null;
}

function badIds(res: Response) {
  res.status(400).json({ error: `ids: 1 to ${MAX_IDS} non-empty strings` });
}

function unavailable(res: Response, error: unknown) {
  console.error(`database error: ${(error as Error)?.name ?? "error"}`);
  res.status(503).json({ error: "the database did not answer", retryable: true });
}

/**
 * What the model made of memories the phone has sent, so the phone keeps a
 * copy. An id the server does not hold is absent; one held but not enriched
 * yet comes back with nulls, which is an answer too.
 */
export function makeEnrichmentHandler(database: Db) {
  return async (req: Request, res: Response) => {
    const ids = idsOf(req.body);
    if (!ids) return badIds(res);
    try {
      const docs = await memories(database)
        .find({ _id: { $in: ids } }, { projection: { enrichment: 1 } })
        .toArray();
      const results: EnrichmentReply[] = docs.map((doc) => ({
        id: doc._id,
        summary: doc.enrichment?.summary ?? null,
        kind: doc.enrichment?.kind ?? null,
        readText: doc.enrichment?.readText ?? null,
      }));
      res.json({ results });
    } catch (error) {
      unavailable(res, error);
    }
  };
}

/**
 * Memories deleted on the phone: the text, the enrichment and the vector all
 * go. Idempotent: an id already gone, or never sent, is simply not counted.
 */
export function makeDeleteHandler(database: Db) {
  return async (req: Request, res: Response) => {
    const ids = idsOf(req.body);
    if (!ids) return badIds(res);
    try {
      const { deletedCount } = await memories(database).deleteMany({ _id: { $in: ids } });
      res.json({ deleted: deletedCount });
    } catch (error) {
      unavailable(res, error);
    }
  };
}

/**
 * Deletes every memory the server holds. Idempotent: answers the count deleted.
 */
export function makeForgetHandler(database: Db) {
  return async (_req: Request, res: Response) => {
    try {
      const { deletedCount } = await memories(database).deleteMany({});
      res.json({ deleted: deletedCount });
    } catch (error) {
      unavailable(res, error);
    }
  };
}
