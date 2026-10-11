/**
 * A search answer as a readable list for a terminal: what the phrase was taken
 * to mean, then each result with why it matched. For `npm run search`, and for
 * showing the pipeline working without a screen.
 */

/** The parts of GET /search's answer this prints. Dates arrive as ISO strings. */
export interface PrintableAnswer {
  query: string;
  interpretation: {
    query: string;
    types: string[];
    from: string | null;
    to: string | null;
    sourceApp: string | null;
  } | null;
  interpretationError?: string;
  results: PrintableHit[];
}

export interface PrintableHit {
  score: number | null;
  ranks: { vector: number | null; text: number | null };
  type: string;
  hasLink: boolean;
  capturedAt: string;
  datedAt: string;
  sourceAppLabel: string | null;
  title: string | null;
  rawText: string | null;
  extractedText: string | null;
  note: string | null;
  readText: string | null;
  summary: string | null;
  kind: string | null;
}

/** The first non-blank line, cut at a word past [max] characters. */
export function firstLine(text: string | null | undefined, max = 90): string | null {
  const line = text?.split(/\r?\n/).map((l) => l.trim()).find((l) => l !== "");
  if (!line) return null;
  if (line.length <= max) return line;
  const cut = line.slice(0, max);
  return `${cut.slice(0, Math.max(cut.lastIndexOf(" "), 1)).trimEnd()}…`;
}

/** What the phrase was taken to mean, in one line. */
export function describeInterpretation(answer: PrintableAnswer): string {
  const i = answer.interpretation;
  if (!i) return `not taken apart (${answer.interpretationError ?? "no reason given"}); searched as typed`;
  const parts: string[] = [];
  if (i.query.trim() !== "") parts.push(`looking for "${i.query}"`);
  if (i.types.length > 0) parts.push(`of type ${i.types.join(" or ")}`);
  if (i.from || i.to) parts.push(`dated ${i.from ?? "…"} to ${i.to ?? "…"}`);
  if (i.sourceApp) parts.push(`from ${i.sourceApp}`);
  return parts.length > 0 ? parts.join(", ") : "no filters";
}

/** Where a result placed in each half of the search, or that it was all filter. */
export function describeRanks(hit: PrintableHit): string {
  if (hit.score === null) return "matched the filters only (newest first)";
  const parts: string[] = [];
  if (hit.ranks.vector !== null) parts.push(`meaning #${hit.ranks.vector}`);
  if (hit.ranks.text !== null) parts.push(`words #${hit.ranks.text}`);
  return parts.length > 0 ? `matched by ${parts.join(" and ")}` : "matched";
}

/** The best single line saying what a result is. */
export function headline(hit: PrintableHit): string {
  return (
    firstLine(hit.title) ??
    firstLine(hit.rawText) ??
    firstLine(hit.summary) ??
    firstLine(hit.extractedText) ??
    firstLine(hit.readText) ??
    `(${hit.type.toLowerCase()})`
  );
}

export function formatAnswer(answer: PrintableAnswer): string {
  const lines: string[] = [`"${answer.query}"`, `  understood as: ${describeInterpretation(answer)}`, ""];
  if (answer.results.length === 0) {
    lines.push("  nothing matches that");
    return lines.join("\n");
  }
  answer.results.forEach((hit, index) => {
    const head = headline(hit);
    const meta = [
      hit.type.toLowerCase() + (hit.hasLink && hit.type !== "LINK" ? " + link" : ""),
      hit.sourceAppLabel?.toLowerCase(),
      hit.datedAt.slice(0, 10),
    ].filter(Boolean);
    lines.push(`${String(index + 1).padStart(2)}  ${head}`);
    lines.push(`    ${meta.join(" · ")}`);
    const summary = firstLine(hit.summary, 140);
    if (summary && summary !== head) lines.push(`    ${summary}`);
    const note = firstLine(hit.note, 100);
    if (note) lines.push(`    your note: ${note}`);
    lines.push(`    ${describeRanks(hit)}`);
    lines.push("");
  });
  return lines.join("\n").trimEnd();
}
