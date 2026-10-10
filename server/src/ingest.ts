import { Type } from "@google/genai";
import type { Request, Response } from "express";
import type { Db } from "mongodb";
import { createHash } from "node:crypto";
import { MAX_MEMORY_BATCH, type IngestResult } from "./api.ts";
import { localDay } from "./days.ts";
import {
  EMBEDDING_DIMENSIONS,
  GATE_WAIT_MS,
  embedDocuments,
  gemini,
  ingestGate,
  modelFailure,
  models,
} from "./gemini.ts";
import { datedAt, memories, type Enrichment, type MemoryDoc, type MemoryType } from "./memories.ts";
import { linkSites } from "./sites.ts";

const MEMORY_TYPES: readonly MemoryType[] = ["TEXT", "LINK", "IMAGE", "PDF", "AUDIO"];
const TEXT_FIELDS = ["sourceApp", "sourceAppLabel", "title", "rawText", "extractedText", "note"] as const;
type TextField = (typeof TEXT_FIELDS)[number];
const MAX_ID_CHARS = 200;
/** What the model reads of one memory's text; past this it learns little and costs much. */
const MAX_ENRICH_CHARS = 4_000;
/** What is embedded; the model reads the first 4,000 of it. */
const MAX_EMBED_CHARS = 6_000;

/** A memory as the phone sent it, checked field by field. */
export interface ParsedMemory extends Record<TextField, string | null> {
  _id: string;
  type: MemoryType;
  hasLink: boolean;
  capturedAt: Date;
  contentCreatedAt: Date | null;
  updatedAt: Date;
  datedAt: Date;
  linkSites: string[];
}

/**
 * Parses one memory field by field. Only the contract's fields are read, so an
 * unknown one (localUri above all) can never be stored, whatever the phone
 * sends. Blank strings become null. [errors] says why it is invalid, if it is.
 */
export function parseMemory(raw: unknown): { memory?: ParsedMemory; errors: string[] } {
  if (typeof raw !== "object" || raw === null || Array.isArray(raw)) return { errors: ["not an object"] };
  const input = raw as Record<string, unknown>;
  const errors: string[] = [];

  const id = typeof input.id === "string" ? input.id.trim() : "";
  if (!id) errors.push("id: required, a non-empty string");
  else if (id.length > MAX_ID_CHARS) errors.push(`id: at most ${MAX_ID_CHARS} characters`);

  const type = MEMORY_TYPES.find((known) => known === input.type);
  if (!type) errors.push(`type: one of ${MEMORY_TYPES.join(", ")}`);

  const capturedAt = time(input.capturedAt);
  if (!capturedAt) errors.push("capturedAt: required, epoch milliseconds");
  const contentCreatedAt = input.contentCreatedAt == null ? null : time(input.contentCreatedAt);
  if (input.contentCreatedAt != null && !contentCreatedAt) errors.push("contentCreatedAt: epoch milliseconds or null");
  const updatedAt = input.updatedAt == null ? capturedAt : time(input.updatedAt);
  if (input.updatedAt != null && !updatedAt) errors.push("updatedAt: epoch milliseconds");
  if (input.hasLink != null && typeof input.hasLink !== "boolean") errors.push("hasLink: true or false");

  const texts = {} as Record<TextField, string | null>;
  for (const field of TEXT_FIELDS) {
    const value = input[field];
    if (value != null && typeof value !== "string") errors.push(`${field}: a string or null`);
    texts[field] = typeof value === "string" && value.trim() ? value : null;
  }

  if (errors.length > 0 || !type || !capturedAt || !updatedAt) return { errors };
  const fields = { _id: id, type, hasLink: input.hasLink === true, capturedAt, contentCreatedAt, updatedAt, ...texts };
  // Links from the shared text only, as hasLink: a screenshot with a URL bar
  // in it is not a link someone sent.
  return { memory: { ...fields, datedAt: datedAt(fields), linkSites: linkSites(fields.rawText) }, errors };
}

function time(value: unknown): Date | null {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? new Date(value) : null;
}

/** What the model reads of a memory. Never the note: the note is the person's, not the thing's. */
export function enrichmentText(
  memory: Pick<MemoryDoc, "title" | "rawText" | "extractedText">,
  readText?: string | null,
): string {
  return [memory.title, memory.rawText, memory.extractedText, readText].filter(Boolean).join("\n");
}

/** What is embedded: the note near the front, then title, summary and the text. */
export function embeddingText(
  memory: Pick<MemoryDoc, "note" | "title" | "rawText" | "extractedText">,
  summary?: string | null,
  readText?: string | null,
): string {
  return [memory.note, memory.title, summary, memory.rawText, memory.extractedText, readText]
    .filter(Boolean)
    .join("\n")
    .slice(0, MAX_EMBED_CHARS);
}

export function fingerprint(text: string): string {
  return createHash("sha256").update(text).digest("hex");
}

/** What the model made of one memory, before it is dated and stored. */
export type Reading = Pick<Enrichment, "summary" | "kind" | "entities" | "dates">;

/**
 * Reads a batch of memories in one model call. A memory the model left out of
 * its answer comes back null.
 */
export type Enricher = (batch: { text: string; asOf: Date }[]) => Promise<(Reading | null)[]>;

/** Embeds a batch of texts in one request. */
export type Embedder = (texts: string[]) => Promise<number[][]>;

export const READING_FIELDS = {
  summary: { type: Type.STRING, description: "one line, in the words someone would use looking for it" },
  kind: { type: Type.STRING, description: "what it is, in a few words: job posting, restaurant recommendation, receipt" },
  entities: { type: Type.ARRAY, items: { type: Type.STRING }, description: "people, organisations, places, products it names" },
  dates: { type: Type.ARRAY, items: { type: Type.STRING }, description: "dates it mentions, YYYY-MM-DD" },
};

export const defaultEnricher: Enricher = async (batch) => {
  const prompt = [
    "Each numbered item is something a person saved to find again later.",
    "For each, give a one-line summary in the words they would use looking for it, what kind of thing it is,",
    "the people, organisations, places and products it names, and the dates it mentions as YYYY-MM-DD,",
    'reading relative dates as of when it was saved or taken ("next Friday" is the Friday after that day).',
    "",
    ...batch.map((item, index) => `#${index}, saved or taken ${localDay(item.asOf)}:\n${item.text.slice(0, MAX_ENRICH_CHARS)}`),
  ].join("\n");

  const response = await gemini().models.generateContent({
    model: models.ingest,
    contents: prompt,
    config: {
      responseMimeType: "application/json",
      responseSchema: {
        type: Type.ARRAY,
        items: {
          type: Type.OBJECT,
          properties: { index: { type: Type.INTEGER }, ...READING_FIELDS },
          required: ["index", "summary", "kind", "entities", "dates"],
        },
      },
    },
  });
  const answers = JSON.parse(response.text ?? "") as ({ index: number } & Reading)[];
  return batch.map((_, index) => {
    const answer = answers.find((candidate) => candidate.index === index);
    return answer ? { summary: answer.summary, kind: answer.kind, entities: answer.entities, dates: answer.dates } : null;
  });
};

export function makeIngestHandler(database: Db, enricher: Enricher = defaultEnricher, embedder: Embedder = embedDocuments) {
  return async (req: Request, res: Response) => {
    // One object is accepted too, for curl, and answered with one result.
    const single = !Array.isArray(req.body);
    const items: unknown[] = single ? [req.body] : req.body;
    if (items.length < 1 || items.length > MAX_MEMORY_BATCH) {
      res.status(400).json({ error: `send 1 to ${MAX_MEMORY_BATCH} memories` });
      return;
    }
    const parsed = items.map(parseMemory);
    const invalid = parsed.flatMap((outcome, index) => (outcome.errors.length > 0 ? [{ index, errors: outcome.errors }] : []));
    if (invalid.length > 0) {
      res.status(400).json({ error: "invalid memory", invalid });
      return;
    }

    const results = await ingest(database, parsed.map((outcome) => outcome.memory!), enricher, embedder);
    // A model call that may succeed later answers 503, never 200: the phone
    // sending it again is the retry, and a 200 would mark it synced for good.
    res.status(results.some((result) => result.retryable) ? 503 : 200).json(single ? results[0] : { results });
  };
}

/**
 * Stores every memory, whatever the models do: the phone has already said
 * "Saved". Reads the batch's new text with one model call and embeds the
 * batch's new text with one request; a memory whose text has not changed
 * costs neither, and one with no text costs no call at all.
 */
export async function ingest(
  database: Db,
  batch: ParsedMemory[],
  enricher: Enricher,
  embedder: Embedder,
): Promise<IngestResult[]> {
  const collection = memories(database);
  const held = new Map((await collection.find({ _id: { $in: batch.map((memory) => memory._id) } }).toArray()).map((doc) => [doc._id, doc]));
  const now = new Date();

  const work = batch.map((memory) => {
    const existing = held.get(memory._id);
    // What the picture showed (S3) stays, and is read with the text.
    const readText = existing?.enrichment?.readText ?? null;
    const text = enrichmentText(memory, readText);
    const from = text ? fingerprint(text) : undefined;
    const current = existing?.enrichment && existing.enrichedFrom === from ? existing.enrichment : undefined;
    return {
      memory,
      existing,
      readText,
      text,
      from,
      enrichment: current,
      enriched: current !== undefined,
      fresh: false,
      vector: undefined as number[] | undefined,
      embedFrom: undefined as string | undefined,
      embedded: false,
      error: undefined as string | undefined,
      reason: text ? undefined : "no text",
      retryable: false,
    };
  });
  type Work = (typeof work)[number];
  const fail = (item: Work, reason: string, retryable: boolean, enrichment: boolean) => {
    item.reason = item.reason ? `${item.reason}; ${reason}` : reason;
    item.retryable ||= retryable;
    if (enrichment) item.error = reason;
  };

  const toEnrich = work.filter((item) => item.text && !item.enriched);
  if (toEnrich.length > 0) {
    if (!(await ingestGate.acquire(GATE_WAIT_MS))) {
      for (const item of toEnrich) fail(item, "rate limited: the model's minute was full", true, true);
    } else {
      try {
        const readings = await enricher(toEnrich.map((item) => ({ text: item.text, asOf: item.memory.datedAt })));
        toEnrich.forEach((item, index) => {
          const reading = readings[index];
          if (!reading) return fail(item, "the model left it out of its answer", true, true);
          item.enrichment = { ...reading, ...(item.readText ? { readText: item.readText } : {}), at: now };
          item.enriched = true;
          item.fresh = true;
        });
      } catch (error) {
        const { reason, retryable } = modelFailure(error);
        for (const item of toEnrich) fail(item, reason, retryable, true);
      }
    }
  }

  for (const item of work) {
    const text = embeddingText(item.memory, item.enrichment?.summary ?? item.existing?.enrichment?.summary, item.readText);
    item.embedFrom = text ? fingerprint(text) : undefined;
    const done = item.existing?.embeddedWith;
    item.embedded = !!(item.existing?.embedding && done?.from === item.embedFrom && done?.model === models.embedding);
  }
  const toEmbed = work.filter((item) => item.embedFrom && !item.embedded);
  if (toEmbed.length > 0) {
    try {
      const vectors = await embedder(
        toEmbed.map((item) => embeddingText(item.memory, item.enrichment?.summary ?? item.existing?.enrichment?.summary, item.readText)),
      );
      toEmbed.forEach((item, index) => {
        const vector = vectors[index];
        if (vector?.length !== EMBEDDING_DIMENSIONS) return fail(item, "the embedding was missing", true, false);
        item.vector = vector;
        item.embedded = true;
      });
    } catch (error) {
      const { reason, retryable } = modelFailure(error);
      for (const item of toEmbed) fail(item, `embedding: ${reason}`, retryable, false);
    }
  }

  await collection.bulkWrite(
    work.map((item) => {
      const { _id, ...fields } = item.memory;
      const set: Partial<MemoryDoc> = { ...fields, syncedAt: now };
      const unset: Record<string, ""> = {};
      if (item.fresh) {
        set.enrichment = item.enrichment;
        set.enrichedFrom = item.from;
      }
      if (item.error) set.enrichmentError = item.error;
      else if (item.enriched) unset.enrichmentError = "";
      else if (!item.text) set.enrichmentError = "no text";
      if (item.vector) {
        set.embedding = item.vector;
        set.embeddedWith = { model: models.embedding, dimensions: EMBEDDING_DIMENSIONS, from: item.embedFrom, at: now };
      }
      return {
        updateOne: {
          filter: { _id },
          update: Object.keys(unset).length > 0 ? { $set: set, $unset: unset } : { $set: set },
          upsert: true,
        },
      };
    }),
  );

  return work.map((item) => ({
    id: item.memory._id,
    enriched: item.enriched,
    embedded: item.embedded,
    ...(item.reason ? { reason: item.reason } : {}),
    ...(item.retryable ? { retryable: true } : {}),
  }));
}
