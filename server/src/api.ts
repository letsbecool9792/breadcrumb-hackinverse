/**
 * The phone-server contract, as types. docs/api.md says the same in words,
 * with every status code; change both together, and the phone's Kotlin data
 * classes with them.
 */
import type { MemoryType } from "./memories.ts";

/** POST /memories: one memory as the phone sends it -- the Room row, minus what is device-only. */
export interface IncomingMemory {
  id: string;
  type: MemoryType;
  hasLink: boolean;
  capturedAt: Date;
  contentCreatedAt: Date | null;
  sourceApp: string | null;
  sourceAppLabel: string | null;
  title: string | null;
  rawText: string | null;
  extractedText: string | null;
  /** The person's own words about it, added when saving or later. */
  note: string | null;
  updatedAt: Date;
}

/** One memory's outcome, in the order sent. */
export interface IngestResult {
  id: string;
  enriched: boolean;
  embedded: boolean;
  /** Why the model work did not finish, when it did not. */
  reason?: string;
  /**
   * The memory is stored, but a model call failed for a reason that may pass
   * (a rate limit, an overloaded model). The phone sends it again later, and
   * that re-send is what enriches it.
   */
  retryable?: boolean;
}

/** POST /memories/images: one saved picture, for a memory already sent. Base64, never a path or a file. */
export interface IncomingImage {
  id: string;
  mimeType: "image/jpeg" | "image/png" | "image/webp";
  data: string;
}

/** POST /memories/enrichment: what the model made of one memory, for the phone to keep beside its row. */
export interface EnrichmentReply {
  id: string;
  summary: string | null;
  kind: string | null;
  readText: string | null;
}

/** GET /search: how the phrase was taken apart, so a searcher can see why the list is what it is. */
export interface Interpretation {
  /** What is left to search by meaning and words. Empty when the phrase was all filter. */
  query: string;
  /** Any of these; empty means any kind. LINK also matches anything carrying a link. */
  types: MemoryType[];
  /** Inclusive, YYYY-MM-DD. Either may be open. */
  from: string | null;
  to: string | null;
  /** An app something was shared from, or a site a saved link points to. */
  sourceApp: string | null;
}

/** Where a result placed in each half of the search; null where it did not place at all. */
export interface Ranks {
  /** By meaning: nearness of its embedding to the phrase's. */
  vector: number | null;
  /** By words: the phrase's words in its text. */
  text: number | null;
}

/** One result. The text comes back whole; the vector never leaves. */
export interface SearchHit {
  id: string;
  /** The fused score, for ordering only. Null when the search was all filter, newest first. */
  score: number | null;
  ranks: Ranks;
  type: MemoryType;
  hasLink: boolean;
  capturedAt: Date;
  datedAt: Date;
  sourceAppLabel: string | null;
  title: string | null;
  rawText: string | null;
  extractedText: string | null;
  note: string | null;
  readText: string | null;
  summary: string | null;
  kind: string | null;
}

export interface SearchAnswer {
  /** The phrase as typed. */
  query: string;
  interpretation: Interpretation | null;
  /** Why it could not be taken apart; the search then ran on the raw phrase, unfiltered. */
  interpretationError?: string;
  results: SearchHit[];
}

/** Ceilings the phone keeps under. */
export const MAX_MEMORY_BATCH = 50;
export const MAX_IMAGE_BATCH = 8;
export const MAX_IDS = 100;
export const MAX_QUERY_CHARS = 500;
export const DEFAULT_SEARCH_LIMIT = 20;
export const MAX_SEARCH_LIMIT = 50;
