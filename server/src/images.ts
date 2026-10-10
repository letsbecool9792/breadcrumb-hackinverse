import { Type, type Part } from "@google/genai";
import type { Request, Response } from "express";
import type { Db } from "mongodb";
import { MAX_IMAGE_BATCH, type IngestResult } from "./api.ts";
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
import { READING_FIELDS, embeddingText, enrichmentText, fingerprint, type Embedder, type Reading } from "./ingest.ts";
import { memories, type MemoryDoc } from "./memories.ts";

const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
type ImageType = (typeof IMAGE_TYPES)[number];
const MAX_ID_CHARS = 200;
/** A 1,500 px JPEG is well under 1 MB; base64 adds a third. */
const MAX_BASE64_CHARS = 8 * 1024 * 1024;

/** What the model made of a picture: what it says and shows, and the rest of a reading. */
export type PictureReading = Reading & { readText: string };

/** One picture to read, with what is known of its memory as context. */
export interface Picture {
  mimeType: ImageType;
  data: string;
  /** The memory's own text, which the picture goes with. */
  text: string;
  /** The person's note: context about the picture, not part of it. */
  note: string | null;
  asOf: Date;
}

/** Reads a batch of pictures in one model call. A picture the model left out comes back null. */
export type ImageReader = (pictures: Picture[]) => Promise<(PictureReading | null)[]>;

export const defaultImageReader: ImageReader = async (pictures) => {
  const parts: Part[] = [
    {
      text: [
        "Each numbered picture was saved by a person to find again later; their phone's OCR found little or no text in it.",
        "For each: readText is every word of text in it, then, if it holds little text, one line on what it shows.",
        "Then a one-line summary in the words they would use looking for it, what kind of thing it is, the people,",
        'organisations, places and products it shows, and the dates it mentions as YYYY-MM-DD ("next Friday" as of the day it was taken).',
        "A note, where given, is the person's own words about it: context, not part of the picture.",
      ].join(" "),
    },
  ];
  pictures.forEach((picture, index) => {
    const context = [
      `#${index}, saved or taken ${localDay(picture.asOf)}.`,
      picture.text ? `Text saved with it: ${picture.text.slice(0, 2_000)}` : "",
      picture.note ? `Note: ${picture.note}` : "",
    ].filter(Boolean).join("\n");
    parts.push({ text: context }, { inlineData: { mimeType: picture.mimeType, data: picture.data } });
  });

  const response = await gemini().models.generateContent({
    model: models.ingest,
    contents: [{ role: "user", parts }],
    config: {
      responseMimeType: "application/json",
      responseSchema: {
        type: Type.ARRAY,
        items: {
          type: Type.OBJECT,
          properties: { index: { type: Type.INTEGER }, readText: { type: Type.STRING }, ...READING_FIELDS },
          required: ["index", "readText", "summary", "kind", "entities", "dates"],
        },
      },
    },
  });
  const answers = JSON.parse(response.text ?? "") as ({ index: number } & PictureReading)[];
  return pictures.map((_, index) => {
    const answer = answers.find((candidate) => candidate.index === index);
    return answer
      ? { readText: answer.readText, summary: answer.summary, kind: answer.kind, entities: answer.entities, dates: answer.dates }
      : null;
  });
};

interface IncomingPicture {
  id: string;
  mimeType: ImageType;
  data: string;
}

function parsePicture(raw: unknown): { picture?: IncomingPicture; errors: string[] } {
  if (typeof raw !== "object" || raw === null || Array.isArray(raw)) return { errors: ["not an object"] };
  const input = raw as Record<string, unknown>;
  const errors: string[] = [];
  const id = typeof input.id === "string" ? input.id.trim() : "";
  if (!id || id.length > MAX_ID_CHARS) errors.push(`id: required, at most ${MAX_ID_CHARS} characters`);
  const mimeType = IMAGE_TYPES.find((known) => known === input.mimeType);
  if (!mimeType) errors.push(`mimeType: one of ${IMAGE_TYPES.join(", ")}`);
  const data = typeof input.data === "string" ? input.data : "";
  if (!data || data.length > MAX_BASE64_CHARS || !/^[A-Za-z0-9+/]+={0,2}$/.test(data)) {
    errors.push("data: base64, at most 8 MB");
  }
  return errors.length > 0 || !mimeType ? { errors } : { picture: { id, mimeType, data }, errors };
}

export function makeImageHandler(database: Db, reader: ImageReader = defaultImageReader, embedder: Embedder = embedDocuments) {
  return async (req: Request, res: Response) => {
    const single = !Array.isArray(req.body);
    const items: unknown[] = single ? [req.body] : req.body;
    if (items.length < 1 || items.length > MAX_IMAGE_BATCH) {
      res.status(400).json({ error: `send 1 to ${MAX_IMAGE_BATCH} pictures` });
      return;
    }
    const parsed = items.map(parsePicture);
    const invalid = parsed.flatMap((outcome, index) => (outcome.errors.length > 0 ? [{ index, errors: outcome.errors }] : []));
    if (invalid.length > 0) {
      res.status(400).json({ error: "invalid picture", invalid });
      return;
    }

    const results = await readPictures(database, parsed.map((outcome) => outcome.picture!), reader, embedder);
    res.status(results.some((result) => result.retryable) ? 503 : 200).json(single ? results[0] : { results });
  };
}

/**
 * Reads pictures of memories already held, in one model call through the
 * ingest model's gate, then re-embeds them in one request. The picture is
 * never written anywhere, not even a temp file: only what the model read is
 * kept, and the same bytes are never read twice.
 */
export async function readPictures(
  database: Db,
  pictures: IncomingPicture[],
  reader: ImageReader,
  embedder: Embedder,
): Promise<IngestResult[]> {
  const collection = memories(database);
  const held = new Map((await collection.find({ _id: { $in: pictures.map((picture) => picture.id) } }).toArray()).map((doc) => [doc._id, doc]));
  const now = new Date();

  const work = pictures.map((picture) => {
    const doc = held.get(picture.id);
    const bytes = fingerprint(picture.data);
    const read = !!doc && doc.imageFingerprint === bytes && doc.enrichment?.readText !== undefined;
    return {
      picture,
      doc,
      bytes,
      read,
      enrichment: undefined as MemoryDoc["enrichment"],
      vector: undefined as number[] | undefined,
      embedFrom: undefined as string | undefined,
      embedded: read && !!doc?.embedding,
      error: undefined as string | undefined,
      reason: doc ? undefined : "no such memory",
      retryable: false,
    };
  });
  type Work = (typeof work)[number];
  const fail = (item: Work, reason: string, retryable: boolean, reading: boolean) => {
    item.reason = item.reason ? `${item.reason}; ${reason}` : reason;
    item.retryable ||= retryable;
    if (reading) item.error = reason;
  };

  const toRead = work.filter((item) => item.doc && !item.read);
  if (toRead.length > 0) {
    if (!(await ingestGate.acquire(GATE_WAIT_MS))) {
      for (const item of toRead) fail(item, "rate limited: the model's minute was full", true, true);
    } else {
      try {
        const readings = await reader(
          toRead.map(({ picture, doc }) => ({
            mimeType: picture.mimeType,
            data: picture.data,
            text: enrichmentText(doc!),
            note: doc!.note ?? null,
            asOf: doc!.datedAt,
          })),
        );
        toRead.forEach((item, index) => {
          const reading = readings[index];
          if (!reading) return fail(item, "the model left it out of its answer", true, true);
          item.enrichment = { ...reading, at: now };
          item.read = true;
        });
      } catch (error) {
        const { reason, retryable } = modelFailure(error);
        for (const item of toRead) fail(item, reason, retryable, true);
      }
    }
  }

  const toEmbed = work.filter((item) => item.enrichment);
  for (const item of toEmbed) {
    const text = embeddingText(item.doc!, item.enrichment!.summary, item.enrichment!.readText);
    item.embedFrom = fingerprint(text);
  }
  if (toEmbed.length > 0) {
    try {
      const vectors = await embedder(toEmbed.map((item) => embeddingText(item.doc!, item.enrichment!.summary, item.enrichment!.readText)));
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

  const writes = work.flatMap((item) => {
    if (!item.doc || (!item.enrichment && !item.error)) return [];
    const set: Partial<MemoryDoc> = { syncedAt: now };
    const unset: Record<string, ""> = {};
    if (item.enrichment) {
      set.enrichment = item.enrichment;
      // The text the next ingest compares against, so an unchanged re-send costs nothing.
      set.enrichedFrom = fingerprint(enrichmentText(item.doc, item.enrichment.readText));
      set.imageFingerprint = item.bytes;
      set.imageReadAt = now;
      unset.enrichmentError = "";
    } else if (item.error) {
      set.enrichmentError = item.error;
    }
    if (item.vector) {
      set.embedding = item.vector;
      set.embeddedWith = { model: models.embedding, dimensions: EMBEDDING_DIMENSIONS, from: item.embedFrom, at: now };
    }
    const update = Object.keys(unset).length > 0 ? { $set: set, $unset: unset } : { $set: set };
    return [{ updateOne: { filter: { _id: item.doc._id }, update } }];
  });
  if (writes.length > 0) await collection.bulkWrite(writes);

  return work.map((item) => ({
    id: item.picture.id,
    enriched: item.read,
    embedded: item.embedded,
    ...(item.reason ? { reason: item.reason } : {}),
    ...(item.retryable ? { retryable: true } : {}),
  }));
}
