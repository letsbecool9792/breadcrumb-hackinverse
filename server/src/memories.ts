import type { Collection, Db, Document } from "mongodb";

export type MemoryType = "TEXT" | "LINK" | "IMAGE" | "PDF" | "AUDIO";

/**
 * A memory as the cloud holds it: the phone's text about something, never the
 * thing itself (architecture rule 1). No original, no picture, no local path.
 */
export interface MemoryDoc {
  /** The id the phone generated, so a retried upload replaces rather than duplicates. */
  _id: string;
  type: MemoryType;
  hasLink: boolean;
  capturedAt: Date;
  contentCreatedAt?: Date | null;
  /**
   * The date a person places it by: when a picture was taken, else when it was
   * saved. What "from April" filters on -- a screenshot imported in September
   * was still taken in April. Derived here, never sent by the phone.
   */
  datedAt: Date;
  sourceApp?: string | null;
  sourceAppLabel?: string | null;
  /**
   * The sites the links in its shared text point to, named as people say
   * them ("Instagram", sites.ts), so a source filter finds an Instagram link
   * however it arrived. Derived here,
   * never sent by the phone; empty when it holds no link.
   */
  linkSites?: string[];
  title?: string | null;
  rawText?: string | null;
  /** What the phone read out of it: OCR of a picture, a PDF's text, a link's page. */
  extractedText?: string | null;
  /**
   * The person's own words about it. Embedded and word-indexed, but never
   * given to the model to describe.
   */
  note?: string | null;
  /** The phone's last change to the row. */
  updatedAt: Date;
  /** Server clock, set on every write here. */
  syncedAt: Date;

  /** What one Gemini call made of it. Absent until that succeeds. */
  enrichment?: Enrichment;
  /** Why the model call did not succeed, when it did not. The memory is stored regardless. */
  enrichmentError?: string;
  /** Fingerprint of the text [enrichment] was made from, so an unchanged re-send costs no model call. */
  enrichedFrom?: string;

  /** 768 numbers, L2-normalised. Never leaves the server. */
  embedding?: number[];
  /** Which model and size made [embedding], and from which text, so it is never paid for twice. */
  embeddedWith?: { model: string; dimensions: number; from?: string; at: Date };

  /** When the picture was read, and a fingerprint of its bytes. The picture itself is never stored. */
  imageReadAt?: Date;
  imageFingerprint?: string;
}

/** What one Gemini call made of a memory (architecture rule 3). */
export interface Enrichment {
  summary: string;
  kind: string;
  entities: string[];
  dates: string[];
  /** What the model saw in the picture, when the memory was read as one. */
  readText?: string;
  at: Date;
}

export function memories(database: Db): Collection<MemoryDoc> {
  return database.collection<MemoryDoc>("memories");
}

/**
 * capturedAt, and datedAt for listing a filter's matches newest first when a
 * search is all filter ("screenshots from April"). Filtering within a search
 * runs inside the Atlas search indexes, which the server workstream creates.
 */
export async function ensureIndexes(database: Db): Promise<void> {
  await memories(database).createIndex({ capturedAt: -1 }, { name: "capturedAt_desc" });
  await memories(database).createIndex({ datedAt: -1 }, { name: "datedAt_desc" });

  await syncSearchIndex(memories(database), VECTOR_INDEX);
  await syncSearchIndex(memories(database), TEXT_INDEX);
}

/** What both halves of a search narrow by. A vector index's filter fields must be declared up front. */
const FILTER_PATHS = ["type", "hasLink", "capturedAt", "sourceAppLabel", "datedAt", "linkSites", "_id"];

interface SearchIndexSpec {
  name: string;
  type: "vectorSearch" | "search";
  definition: Document;
}

/** Meaning: the 768-number embedding by cosine, every filter field declared. */
export const VECTOR_INDEX: SearchIndexSpec = {
  name: "memories_vector",
  type: "vectorSearch",
  definition: {
    fields: [
      { type: "vector", path: "embedding", numDimensions: 768, similarity: "cosine" },
      ...FILTER_PATHS.map((path) => ({ type: "filter", path })),
    ],
  },
};

const english = { type: "string", analyzer: "lucene.english" };

/**
 * Words: English analysis (stop words dropped, words stemmed) over the text,
 * the model's summary and picture reading among it as a nested document; the
 * filter fields as tokens, never text, so "WhatsApp" is one value, not a word.
 */
export const TEXT_INDEX: SearchIndexSpec = {
  name: "memories_text",
  type: "search",
  definition: {
    mappings: {
      dynamic: false,
      fields: {
        title: english,
        note: english,
        rawText: english,
        extractedText: english,
        enrichment: { type: "document", fields: { summary: english, readText: english } },
        type: { type: "token" },
        hasLink: { type: "boolean" },
        capturedAt: { type: "date" },
        sourceAppLabel: { type: "token" },
        datedAt: { type: "date" },
        linkSites: { type: "token" },
        _id: { type: "token" },
      },
    },
  },
};

/** The paths an index definition declares, "enrichment.summary" for a nested one. */
export function declaredPaths(definition: Document): string[] {
  if (Array.isArray(definition.fields)) return definition.fields.map((field: { path: string }) => field.path);
  const walk = (fields: Record<string, Document> | undefined, prefix: string): string[] =>
    Object.entries(fields ?? {}).flatMap(([name, field]) =>
      field.type === "document" ? walk(field.fields, `${prefix}${name}.`) : [`${prefix}${name}`],
    );
  return walk(definition.mappings?.fields, "");
}

/**
 * Creates a missing search index, and updates one only when it lacks a declared
 * path: Atlas reports a definition back with its own defaults filled in, so
 * comparing whole definitions would rebuild it at every start. An index of the
 * wrong kind (a vector index made as a plain search index) is dropped and made
 * again. Says what it does out loud: to Atlas a missing index is no error, and
 * the half of the search it serves quietly finds nothing.
 */
async function syncSearchIndex(collection: Collection<MemoryDoc>, spec: SearchIndexSpec): Promise<void> {
  try {
    const existing = (await collection.listSearchIndexes(spec.name).toArray())[0] as Document | undefined;
    if (existing && existing.type === spec.type) {
      const has = new Set(declaredPaths(existing.latestDefinition ?? existing.definition ?? {}));
      const missing = declaredPaths(spec.definition).filter((path) => !has.has(path));
      if (missing.length === 0) return;
      console.log(`search index ${spec.name} lacks ${missing.join(", ")}: updating it (searchable again in about 30 s)`);
      await collection.updateSearchIndex(spec.name, spec.definition);
      return;
    }
    if (existing) {
      console.log(`search index ${spec.name} is a ${existing.type} index, not ${spec.type}: making it again`);
      await collection.dropSearchIndex(spec.name);
      for (let waited = 0; waited < 60_000; waited += 2_000) {
        if ((await collection.listSearchIndexes(spec.name).toArray()).length === 0) break;
        await new Promise((resolve) => setTimeout(resolve, 2_000));
      }
    }
    console.log(`search index ${spec.name} is missing: creating it (searchable in about 30 s)`);
    await collection.createSearchIndex({ name: spec.name, type: spec.type, definition: spec.definition });
  } catch (error) {
    console.warn(
      `could not set up search index ${spec.name}: ${error instanceof Error ? error.message : String(error)}\n` +
        "  If the database user may not manage search indexes, create it in the Atlas UI rather than widen the role.",
    );
  }
}

/** When a memory is dated: when its picture was taken, else when it was saved. */
export function datedAt(memory: { capturedAt: Date; contentCreatedAt?: Date | null }): Date {
  return memory.contentCreatedAt ?? memory.capturedAt;
}
