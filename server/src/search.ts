import type { Request, Response } from "express";
import type { Db, Document } from "mongodb";
import {
  DEFAULT_SEARCH_LIMIT,
  MAX_QUERY_CHARS,
  MAX_SEARCH_LIMIT,
  type Interpretation,
  type Ranks,
  type SearchAnswer,
  type SearchHit,
} from "./api.ts";
import { localDay, startOfDay, startOfNextDay } from "./days.ts";
import { embedQuery, modelFailure } from "./gemini.ts";
import { TEXT_INDEX, VECTOR_INDEX, memories, type MemoryDoc, type MemoryType } from "./memories.ts";
import { cachedParse, defaultSearchParser, sourceNames, type SearchParser } from "./search_parser.ts";

/** Embeds a search phrase. */
export type QueryEmbedder = (phrase: string) => Promise<number[]>;

/** Reciprocal rank fusion's constant, the one $rankFusion uses. */
const RRF_K = 60;
const TEXT_PATHS = ["title", "note", "enrichment.summary", "rawText", "extractedText", "enrichment.readText"];

/** A search that could not be read: the embedding model refused it (502), or may answer later (503). */
export class SearchFailure extends Error {
  readonly status: 502 | 503;
  constructor(status: 502 | 503, message: string) {
    super(message);
    this.status = status;
  }
}

export interface SearchOptions {
  limit: number;
  embedder: QueryEmbedder;
  parser: SearchParser;
  now?: Date;
  /** Tests only: narrows both halves to these ids, so tests can borrow the real indexes. */
  onlyIds?: string[];
}

/** What a search narrows by, after the parse. */
export interface Filters {
  types: MemoryType[];
  from: Date | null;
  /** Exclusive: midnight after the last day asked for. */
  until: Date | null;
  source: string | null;
}

export function filtersOf(interpretation: Interpretation | null): Filters {
  return {
    types: interpretation?.types ?? [],
    from: interpretation?.from ? startOfDay(interpretation.from) : null,
    until: interpretation?.to ? startOfNextDay(interpretation.to) : null,
    source: interpretation?.sourceApp ?? null,
  };
}

function hasFilters(filters: Filters): boolean {
  return filters.types.length > 0 || filters.from !== null || filters.until !== null || filters.source !== null;
}

/**
 * The filters as MQL, for $vectorSearch and for an all-filter listing. Each
 * filter narrows on its own: a link from WhatsApp is a link AND from WhatsApp.
 * A link filter matches anything carrying a link, not only type LINK: a
 * captioned photo or PDF can carry one. A source is an app or a site.
 */
export function mqlFilter(filters: Filters, onlyIds?: string[]): Document | undefined {
  const parts: Document[] = [];
  if (filters.types.length > 0) {
    parts.push(
      filters.types.includes("LINK")
        ? { $or: [{ type: { $in: filters.types } }, { hasLink: true }] }
        : { type: { $in: filters.types } },
    );
  }
  if (filters.from || filters.until) {
    parts.push({ datedAt: { ...(filters.from ? { $gte: filters.from } : {}), ...(filters.until ? { $lt: filters.until } : {}) } });
  }
  if (filters.source) parts.push({ $or: [{ sourceAppLabel: filters.source }, { linkSites: filters.source }] });
  if (onlyIds) parts.push({ _id: { $in: onlyIds } });
  return parts.length === 0 ? undefined : parts.length === 1 ? parts[0] : { $and: parts };
}

/**
 * The same filters as $search clauses, so they narrow the words half too:
 * narrow only one half and the other brings back what was filtered out.
 * Token fields take `in` and `equals`, never `text`.
 */
export function searchFilters(filters: Filters, onlyIds?: string[]): Document[] {
  const clauses: Document[] = [];
  if (filters.types.length > 0) {
    const ofType = { in: { path: "type", value: filters.types } };
    clauses.push(
      filters.types.includes("LINK")
        ? { compound: { should: [ofType, { equals: { path: "hasLink", value: true } }], minimumShouldMatch: 1 } }
        : ofType,
    );
  }
  if (filters.from || filters.until) {
    clauses.push({ range: { path: "datedAt", ...(filters.from ? { gte: filters.from } : {}), ...(filters.until ? { lt: filters.until } : {}) } });
  }
  if (filters.source) {
    clauses.push({
      compound: {
        should: [
          { equals: { path: "sourceAppLabel", value: filters.source } },
          { equals: { path: "linkSites", value: filters.source } },
        ],
        minimumShouldMatch: 1,
      },
    });
  }
  if (onlyIds) clauses.push({ in: { path: "_id", value: onlyIds } });
  return clauses;
}

/**
 * Reciprocal rank fusion, equal weights: a memory placed modestly by both
 * halves can rise above one placed first by only one. Ranks are 1-based, null
 * where a half did not place it.
 */
export function fuse(byMeaning: string[], byWords: string[], limit: number): { id: string; score: number; ranks: Ranks }[] {
  const fused = new Map<string, { score: number; ranks: Ranks }>();
  const add = (ids: string[], half: keyof Ranks) =>
    ids.forEach((id, index) => {
      const entry = fused.get(id) ?? { score: 0, ranks: { vector: null, text: null } };
      entry.score += 1 / (RRF_K + index + 1);
      entry.ranks[half] = index + 1;
      fused.set(id, entry);
    });
  add(byMeaning, "vector");
  add(byWords, "text");
  return [...fused.entries()]
    .map(([id, entry]) => ({ id, ...entry }))
    .sort((a, b) => b.score - a.score)
    .slice(0, limit);
}

/** A result: an allow-list, so a field added to the document stays in the cloud until listed here. */
function hit(doc: MemoryDoc, score: number | null, ranks: Ranks): SearchHit {
  return {
    id: doc._id,
    score,
    ranks,
    type: doc.type,
    hasLink: doc.hasLink,
    capturedAt: doc.capturedAt,
    datedAt: doc.datedAt,
    sourceAppLabel: doc.sourceAppLabel ?? null,
    title: doc.title ?? null,
    rawText: doc.rawText ?? null,
    extractedText: doc.extractedText ?? null,
    note: doc.note ?? null,
    readText: doc.enrichment?.readText ?? null,
    summary: doc.enrichment?.summary ?? null,
    kind: doc.enrichment?.kind ?? null,
  };
}

/** A phrase is parsed once a day: the same phrase, against the same sources, on the same day. */
const parse = cachedParse;

/**
 * A ranked list of memories for [phrase], never an answer. The phrase is
 * parsed into words and filters; a parse that fails costs only the filters.
 * An all-filter phrase ("screenshots from April") lists its matches newest
 * first, with no embedding. Otherwise the words are searched by meaning and
 * by words, both halves filtered alike, and fused by rank.
 */
export async function search(database: Db, phrase: string, options: SearchOptions): Promise<SearchAnswer> {
  const { limit, embedder, parser, now = new Date(), onlyIds } = options;
  const collection = memories(database);

  // The raw phrase is embedded while it is parsed, and reused when the parse
  // leaves its words unchanged: a new phrase would otherwise wait for both.
  const rawEmbedding = embedder(phrase).then(
    (vector) => ({ vector }),
    (error: unknown) => ({ error }),
  );

  let interpretation: Interpretation | null = null;
  let interpretationError: string | undefined;
  try {
    interpretation = await parse(phrase, now, await sourceNames(database), parser);
  } catch (error) {
    interpretationError = modelFailure(error).reason;
  }
  const filters = filtersOf(interpretation);

  if (interpretation && interpretation.query === "" && hasFilters(filters)) {
    const docs = await collection
      .find(mqlFilter(filters, onlyIds) ?? {}, { projection: { embedding: 0 } })
      .sort({ datedAt: -1 })
      .limit(limit)
      .toArray();
    return answer(phrase, interpretation, interpretationError, docs.map((doc) => hit(doc, null, { vector: null, text: null })));
  }

  const words = interpretation?.query || phrase;
  const embedded = words === phrase ? await rawEmbedding : await embedder(words).then((vector) => ({ vector }), (error: unknown) => ({ error }));
  if ("error" in embedded) {
    const { reason, retryable } = modelFailure(embedded.error);
    throw new SearchFailure(retryable ? 503 : 502, reason);
  }

  // Each half offers the fusion more than the final list.
  const candidates = Math.min(Math.max(limit * 2, 20), 100);
  const mql = mqlFilter(filters, onlyIds);
  const clauses = searchFilters(filters, onlyIds);
  const [byMeaning, byWords] = await Promise.all([
    collection
      .aggregate<{ _id: string }>([
        {
          $vectorSearch: {
            index: VECTOR_INDEX.name,
            path: "embedding",
            queryVector: embedded.vector,
            // Exact nearest neighbours: under ~10,000 memories they cost little and miss nothing.
            exact: true,
            limit: candidates,
            ...(mql ? { filter: mql } : {}),
          },
        },
        { $project: { _id: 1 } },
      ])
      .toArray(),
    collection
      .aggregate<{ _id: string }>([
        {
          $search: {
            index: TEXT_INDEX.name,
            compound: {
              must: [{ text: { query: words, path: TEXT_PATHS } }],
              ...(clauses.length > 0 ? { filter: clauses } : {}),
            },
          },
        },
        { $limit: candidates },
        { $project: { _id: 1 } },
      ])
      .toArray(),
  ]);

  const ranked = fuse(byMeaning.map((doc) => doc._id), byWords.map((doc) => doc._id), limit);
  const docs = new Map(
    (await collection.find({ _id: { $in: ranked.map((entry) => entry.id) } }, { projection: { embedding: 0 } }).toArray()).map((doc) => [doc._id, doc]),
  );
  const results = ranked.flatMap((entry) => {
    const doc = docs.get(entry.id);
    return doc ? [hit(doc, entry.score, entry.ranks)] : [];
  });
  return answer(phrase, interpretation, interpretationError, results);
}

function answer(phrase: string, interpretation: Interpretation | null, interpretationError: string | undefined, results: SearchHit[]): SearchAnswer {
  return { query: phrase, interpretation, ...(interpretationError ? { interpretationError } : {}), results };
}

export function makeSearchHandler(database: Db, embedder: QueryEmbedder = embedQuery, parser: SearchParser = defaultSearchParser) {
  return async (req: Request, res: Response) => {
    const phrase = typeof req.query.q === "string" ? req.query.q.trim() : "";
    if (!phrase || phrase.length > MAX_QUERY_CHARS) {
      res.status(400).json({ error: `q: 1 to ${MAX_QUERY_CHARS} characters` });
      return;
    }
    let limit = DEFAULT_SEARCH_LIMIT;
    if (req.query.limit !== undefined) {
      limit = Number(req.query.limit);
      if (!Number.isInteger(limit) || limit < 1 || limit > MAX_SEARCH_LIMIT) {
        res.status(400).json({ error: `limit: 1 to ${MAX_SEARCH_LIMIT}` });
        return;
      }
    }

    try {
      res.json(await search(database, phrase, { limit, embedder, parser }));
    } catch (error) {
      if (error instanceof SearchFailure && error.status === 502) {
        res.status(502).json({ error: "the embedding model refused the search", reason: error.message });
        return;
      }
      // Never the phrase: the server does not log what people search for.
      const what = error instanceof SearchFailure ? error.message : `the database did not answer (${(error as Error)?.name ?? "error"})`;
      if (!(error instanceof SearchFailure)) console.error(`search failed: ${what}`);
      res.status(503).json({ error: "could not read the search", reason: what, retryable: true });
    }
  };
}
