import { GoogleGenAI, Type } from "@google/genai";
import { type Db } from "mongodb";
import { memories, type MemoryType } from "./memories.ts";

export interface SearchInterpretation {
  query: string;
  types: MemoryType[];
  from: string | null; // YYYY-MM-DD
  to: string | null; // YYYY-MM-DD
  sourceApp: string | null;
}

export type SearchParser = (phrase: string, asOf: Date, sourceLabels: string[]) => Promise<SearchInterpretation>;

export async function defaultSearchParser(phrase: string, asOf: Date, sourceLabels: string[]): Promise<SearchInterpretation> {
  const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
  const prompt = `Extract intent from this search phrase.
Today is ${asOf.toISOString().split("T")[0]}.
Available source apps/sites: ${sourceLabels.join(", ")}
Rules:
- Types can only be TEXT, LINK, IMAGE, PDF, AUDIO.
- Extract date ranges if present (e.g. 'April' -> from 2026-04-01 to 2026-04-30).
- Extract the source app/site if it matches one in the list.
- Keep the remaining search terms in 'query'. If none remain, use "".`;

  const response = await ai.models.generateContent({
    model: "gemini-3.1-flash-lite",
    contents: prompt + "\n\nPhrase: " + phrase,
    config: {
      responseMimeType: "application/json",
      responseSchema: {
        type: Type.OBJECT,
        properties: {
          query: { type: Type.STRING },
          types: { type: Type.ARRAY, items: { type: Type.STRING } },
          from: { type: Type.STRING, nullable: true },
          to: { type: Type.STRING, nullable: true },
          sourceApp: { type: Type.STRING, nullable: true },
        },
        required: ["query", "types", "from", "to", "sourceApp"],
      }
    }
  });

  if (!response.text) throw new Error("Model returned no text");
  
  const parsed = JSON.parse(response.text);
  
  // Guard values
  const validTypes: MemoryType[] = ["TEXT", "LINK", "IMAGE", "PDF", "AUDIO"];
  const types = (parsed.types || []).filter((t: any) => validTypes.includes(t as MemoryType));
  
  const from = parsed.from && /^\d{4}-\d{2}-\d{2}$/.test(parsed.from) ? parsed.from : null;
  const to = parsed.to && /^\d{4}-\d{2}-\d{2}$/.test(parsed.to) ? parsed.to : null;
  
  let sourceApp = parsed.sourceApp || null;
  if (sourceApp && !sourceLabels.includes(sourceApp)) {
      sourceApp = null;
  }
  
  return {
    query: parsed.query || "",
    types,
    from,
    to,
    sourceApp
  };
}

let cachedSources: { labels: string[]; at: number } | null = null;

export async function getSourceLabels(database: Db): Promise<string[]> {
  const now = Date.now();
  // Cache for 24h
  if (cachedSources && now - cachedSources.at < 86400000) {
    return cachedSources.labels;
  }
  
  const collection = memories(database);
  
  const appLabels = await collection.distinct("sourceAppLabel", { sourceAppLabel: { $ne: null } });
  
  const siteResults = await collection.aggregate([
    { $unwind: "$linkSites" },
    { $group: { _id: "$linkSites", count: { $sum: 1 } } },
    { $sort: { count: -1 } },
    { $limit: 40 }
  ]).toArray();
  
  const sites = siteResults.map(s => s._id);
  
  const labels = Array.from(new Set([...appLabels, ...sites]));
  
  cachedSources = { labels, at: now };
  return labels;
}
