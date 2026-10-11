/**
 * `npm run search -- that internship screenshot from april [--limit 10]`
 *
 * Asks the running server (`npm run dev`) and prints the ranked list, so the
 * whole pipeline can be shown without a screen. It talks HTTP to the server
 * and opens no database of its own.
 */
import { formatAnswer, type PrintableAnswer } from "./search_format.ts";

const args = process.argv.slice(2);
let limit = 10;
const words: string[] = [];
for (let i = 0; i < args.length; i++) {
  const arg = args[i]!;
  if (arg === "--limit" || arg === "-n") {
    limit = Number(args[++i]);
  } else {
    words.push(arg);
  }
}
const phrase = words.join(" ").trim();
if (phrase === "" || !Number.isInteger(limit) || limit < 1) {
  console.error('usage: npm run search -- <what you are looking for> [--limit N]\n  e.g. npm run search -- "that internship screenshot from april"');
  process.exit(2);
}

const host = process.env.HOST ?? "127.0.0.1";
const port = process.env.PORT ?? "3000";
const url = `http://${host}:${port}/search?${new URLSearchParams({ q: phrase, limit: String(limit) })}`;

let response: Response;
try {
  response = await fetch(url, { signal: AbortSignal.timeout(60_000) });
} catch {
  console.error(`could not reach the server at http://${host}:${port}. Is it running? (npm run dev)`);
  process.exit(1);
}

const body: unknown = await response.json().catch(() => null);
if (!response.ok) {
  const detail = (body as { error?: string; reason?: string } | null) ?? {};
  console.error(`the server answered ${response.status}: ${detail.error ?? "no reason given"}${detail.reason ? ` (${detail.reason})` : ""}`);
  process.exit(1);
}
console.log(formatAnswer(body as PrintableAnswer));
