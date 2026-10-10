import { Type } from "@google/genai";
import type { Db } from "mongodb";
import type { Interpretation } from "./api.ts";
import { localDay, startOfDay } from "./days.ts";
import { gemini, models } from "./gemini.ts";
import { memories, type MemoryType } from "./memories.ts";

const MEMORY_TYPES: readonly MemoryType[] = ["TEXT", "LINK", "IMAGE", "PDF", "AUDIO"];

/** Takes a search phrase apart: the words left to search, and the filters it asks for. */
export type SearchParser = (phrase: string, today: Date, sources: string[]) => Promise<Interpretation>;

export const defaultSearchParser: SearchParser = async (phrase, today, sources) => {
  const prompt = [
    `A person is searching the things they saved. Today is ${today.toLocaleDateString("en-GB", { weekday: "long" })} ${localDay(today)}.`,
    "Take their phrase apart.",
    "query: what is left to search by meaning and words once the filter words are taken out; empty when nothing is left.",
    "types: the kinds it asks for: IMAGE for a screenshot, photo or picture; PDF for a document or PDF;",
    "LINK for a link, article, video or page; TEXT for a note or message. Empty when it names none.",
    'from and to: the days it asks for, YYYY-MM-DD, both inclusive ("April" is the latest April up to today); null when it names none.',
    "sourceApp: the app or site it says something came from, as one of these names, or null:",
    sources.join(", ") || "(none yet)",
    "",
    `Phrase: ${phrase}`,
  ].join("\n");

  const response = await gemini().models.generateContent({
    model: models.query,
    contents: prompt,
    config: {
      responseMimeType: "application/json",
      responseSchema: {
        type: Type.OBJECT,
        properties: {
          query: { type: Type.STRING },
          types: { type: Type.ARRAY, items: { type: Type.STRING, enum: [...MEMORY_TYPES] } },
          from: { type: Type.STRING, nullable: true },
          to: { type: Type.STRING, nullable: true },
          // An enum of the names offered, so the model cannot invent one.
          sourceApp: sources.length > 0 ? { type: Type.STRING, nullable: true, enum: sources } : { type: Type.STRING, nullable: true },
        },
        required: ["query", "types", "from", "to", "sourceApp"],
      },
    },
  });
  return guardInterpretation(JSON.parse(response.text ?? ""), sources);
};

/**
 * Keeps only what a parse may say: known kinds, real days in order, and a
 * source among the names offered. Anything else is dropped, never guessed at.
 */
export function guardInterpretation(raw: unknown, sources: string[]): Interpretation {
  const input = (typeof raw === "object" && raw !== null ? raw : {}) as Record<string, unknown>;
  const types = Array.isArray(input.types)
    ? [...new Set(input.types.filter((type): type is MemoryType => MEMORY_TYPES.includes(type as MemoryType)))]
    : [];
  let from = typeof input.from === "string" && startOfDay(input.from) ? input.from : null;
  let to = typeof input.to === "string" && startOfDay(input.to) ? input.to : null;
  if (from && to && from > to) [from, to] = [to, from];
  const sourceApp = typeof input.sourceApp === "string" && sources.includes(input.sourceApp) ? input.sourceApp : null;
  return { query: typeof input.query === "string" ? input.query.trim() : "", types, from, to, sourceApp };
}

let names: { at: number; list: string[] } | undefined;

/**
 * The names a source filter may use: every app memories were shared from, and
 * the 40 most linked-to sites. Asked again every ten minutes, so an app first
 * shared from today can be searched by today.
 */
export async function sourceNames(database: Db): Promise<string[]> {
  if (names && Date.now() - names.at < 10 * 60_000) return names.list;
  const collection = memories(database);
  const apps = (await collection.distinct("sourceAppLabel")).filter((label): label is string => typeof label === "string" && label !== "");
  const sites = await collection
    .aggregate<{ _id: string }>([
      { $unwind: "$linkSites" },
      { $group: { _id: "$linkSites", count: { $sum: 1 } } },
      { $sort: { count: -1, _id: 1 } },
      { $limit: 40 },
    ])
    .toArray();
  names = { at: Date.now(), list: [...new Set([...apps, ...sites.map((site) => site._id)])].sort() };
  return names.list;
}
