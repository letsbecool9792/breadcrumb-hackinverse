import { GoogleGenAI } from "@google/genai";

/**
 * Everything the server asks of Gemini goes through here: which models, one
 * client, one pacing gate for the ingest model, and how a failure is told
 * apart from a refusal.
 */

/**
 * A model named by version, from [envName] or [fallback]. Never an alias:
 * "gemini-flash-latest" and its kind change under you between runs, so a name
 * with "latest" in it, or no version number at all, stops the server at start.
 */
export function pinnedModel(envName: string, fallback: string): string {
  const name = process.env[envName]?.trim() || fallback;
  if (/latest/i.test(name) || !/\d/.test(name)) {
    throw new Error(`${envName}=${name} is an alias; name a model by its version (e.g. ${fallback})`);
  }
  return name;
}

/** Ingest and query parsing use different models, so they never share one daily allowance. */
export const models = {
  ingest: pinnedModel("GEMINI_INGEST_MODEL", "gemini-3.5-flash-lite"),
  query: pinnedModel("GEMINI_QUERY_MODEL", "gemini-3.1-flash-lite"),
  embedding: pinnedModel("GEMINI_EMBEDDING_MODEL", "gemini-embedding-001"),
};

/** Matryoshka-trained: 768 scores barely behind the full 3,072, at a quarter of M0's space. */
export const EMBEDDING_DIMENSIONS = 768;

let client: GoogleGenAI | undefined;

/** One client for the process. */
export function gemini(): GoogleGenAI {
  client ??= new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
  return client;
}

/**
 * A rolling-minute gate. A call waits for a free slot rather than be refused:
 * a refusal costs the phone an exponential backoff, and during a bulk import
 * that compounds into hours of nothing happening.
 */
export class MinuteGate {
  private readonly perMinute: number;
  private readonly now: () => number;
  private starts: number[] = [];

  constructor(perMinute: number, now: () => number = Date.now) {
    this.perMinute = perMinute;
    this.now = now;
  }

  /** A slot, waiting up to [timeoutMs] for one; false when none came in time. */
  async acquire(timeoutMs: number): Promise<boolean> {
    const deadline = this.now() + timeoutMs;
    for (;;) {
      const now = this.now();
      this.starts = this.starts.filter((at) => now - at < 60_000);
      if (this.starts.length < this.perMinute) {
        this.starts.push(now);
        return true;
      }
      const oldest = this.starts[0] ?? now;
      const wait = Math.min(oldest + 60_000 - now, deadline - now);
      if (wait <= 0) return false;
      await new Promise((resolve) => setTimeout(resolve, wait));
    }
  }
}

/**
 * The ingest model's calls a minute, text and pictures alike: the free tier's
 * allowance is per model, so the two share one gate.
 */
export const ingestGate = new MinuteGate(Number(process.env.GEMINI_INGEST_PER_MINUTE || 15));

/** How long a request waits for a slot before it answers as rate-limited (the phone waits 90 s). */
export const GATE_WAIT_MS = 60_000;

/**
 * Why a model call failed, and whether asking again may work: a rate limit,
 * an overloaded or failing model, or no answer at all (the network) may pass;
 * any other 4xx is the model refusing this request, which it would refuse
 * again.
 */
export function modelFailure(error: unknown): { reason: string; retryable: boolean } {
  const status = (error as { status?: unknown } | null)?.status;
  const message = error instanceof Error ? error.message : String(error);
  const retryable = typeof status !== "number" || status === 429 || status >= 500;
  return { reason: message.slice(0, 300) || "model error", retryable };
}

/** Unit length: gemini-embedding-001 returns unit vectors only at its full 3,072. */
export function l2Normalize(vector: number[]): number[] {
  const norm = Math.sqrt(vector.reduce((sum, value) => sum + value * value, 0));
  return norm === 0 ? vector : vector.map((value) => value / norm);
}

/** Memories, in one request: RETRIEVAL_DOCUMENT, 768 dimensions, unit length. */
export async function embedDocuments(texts: string[]): Promise<number[][]> {
  return embed(texts, "RETRIEVAL_DOCUMENT");
}

/** A search phrase: RETRIEVAL_QUERY, so it lands near the documents it should find. */
export async function embedQuery(text: string): Promise<number[]> {
  const [vector] = await embed([text], "RETRIEVAL_QUERY");
  if (!vector) throw new Error("the embedding model returned nothing");
  return vector;
}

async function embed(texts: string[], taskType: string): Promise<number[][]> {
  const response = await gemini().models.embedContent({
    model: models.embedding,
    contents: texts,
    config: { taskType, outputDimensionality: EMBEDDING_DIMENSIONS },
  });
  const embeddings = response.embeddings ?? [];
  if (embeddings.length !== texts.length) {
    throw new Error(`the embedding model returned ${embeddings.length} of ${texts.length}`);
  }
  return embeddings.map((embedding) => {
    if (!embedding.values?.length) throw new Error("the embedding model returned an empty vector");
    return l2Normalize(embedding.values);
  });
}
