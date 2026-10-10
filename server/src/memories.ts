import type { Collection, Db } from "mongodb";

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
   * The sites the links in its shared text point to ("instagram.com"), so a
   * source filter finds an Instagram link however it arrived. Derived here,
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
}

/** When a memory is dated: when its picture was taken, else when it was saved. */
export function datedAt(memory: { capturedAt: Date; contentCreatedAt?: Date | null }): Date {
  return memory.contentCreatedAt ?? memory.capturedAt;
}
