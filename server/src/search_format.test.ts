import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  describeInterpretation,
  describeRanks,
  firstLine,
  formatAnswer,
  headline,
  type PrintableAnswer,
  type PrintableHit,
} from "./search_format.ts";

const hit = (over: Partial<PrintableHit> = {}): PrintableHit => ({
  score: 0.0325,
  ranks: { vector: 1, text: 2 },
  type: "IMAGE",
  hasLink: false,
  capturedAt: "2026-04-18T09:00:00.000Z",
  datedAt: "2026-04-18T09:00:00.000Z",
  sourceAppLabel: "WhatsApp",
  title: null,
  rawText: null,
  extractedText: "Qualcomm is hiring … apply by April 30",
  note: null,
  readText: null,
  summary: "LinkedIn post: Qualcomm SWE internship, apply by April 30",
  kind: "job posting",
  ...over,
});

const answer = (over: Partial<PrintableAnswer> = {}): PrintableAnswer => ({
  query: "that internship screenshot from april",
  interpretation: { query: "internship", types: ["IMAGE"], from: "2026-04-01", to: "2026-04-30", sourceApp: null },
  results: [hit()],
  ...over,
});

describe("search printing", () => {
  test("a phrase is shown as the words, the type and the dates it was taken to mean", () => {
    assert.equal(
      describeInterpretation(answer()),
      'looking for "internship", of type IMAGE, dated 2026-04-01 to 2026-04-30',
    );
  });

  test("a phrase that was all filter says what it filtered by", () => {
    const filters = answer({ interpretation: { query: "", types: ["IMAGE"], from: null, to: null, sourceApp: "WhatsApp" } });
    assert.equal(describeInterpretation(filters), "of type IMAGE, from WhatsApp");
  });

  test("open ends of a date range are shown as open", () => {
    const since = answer({ interpretation: { query: "x", types: [], from: "2026-04-01", to: null, sourceApp: null } });
    assert.equal(describeInterpretation(since), 'looking for "x", dated 2026-04-01 to …');
  });

  test("a phrase that could not be parsed says why, and that it was searched as typed", () => {
    const failed = answer({ interpretation: null, interpretationError: "the model was busy" });
    assert.equal(describeInterpretation(failed), "not taken apart (the model was busy); searched as typed");
  });

  test("a result says where it placed by meaning and by words", () => {
    assert.equal(describeRanks(hit()), "matched by meaning #1 and words #2");
    assert.equal(describeRanks(hit({ ranks: { vector: null, text: 3 } })), "matched by words #3");
    assert.equal(describeRanks(hit({ score: null, ranks: { vector: null, text: null } })), "matched the filters only (newest first)");
  });

  test("a headline is the title, else the first line of what was shared, else what the model said", () => {
    assert.equal(headline(hit({ title: "Intern, Qualcomm" })), "Intern, Qualcomm");
    assert.equal(headline(hit({ rawText: "https://example.com/job\nmore" })), "https://example.com/job");
    assert.equal(headline(hit()), "LinkedIn post: Qualcomm SWE internship, apply by April 30");
    assert.equal(headline(hit({ summary: null })), "Qualcomm is hiring … apply by April 30");
    assert.equal(headline(hit({ summary: null, extractedText: null })), "(image)");
  });

  test("a long first line is cut at a word", () => {
    const cut = firstLine("word ".repeat(40), 20)!;
    assert.ok(cut.endsWith("…"));
    assert.ok(cut.length <= 21);
    assert.equal(firstLine("   \n  ", 20), null);
  });

  test("the list numbers each result with its source, date, summary and why it matched", () => {
    const text = formatAnswer(answer());
    assert.equal(
      text,
      [
        '"that internship screenshot from april"',
        '  understood as: looking for "internship", of type IMAGE, dated 2026-04-01 to 2026-04-30',
        "",
        " 1  LinkedIn post: Qualcomm SWE internship, apply by April 30",
        "    image · whatsapp · 2026-04-18",
        "    matched by meaning #1 and words #2",
      ].join("\n"),
    );
  });

  test("the person's note and a carried link are shown", () => {
    const text = formatAnswer(answer({ results: [hit({ title: "Offer letter", hasLink: true, note: "Priya says apply early" })] }));
    assert.match(text, /image \+ link · whatsapp/);
    assert.match(text, /your note: Priya says apply early/);
    assert.match(text, /LinkedIn post: Qualcomm SWE internship/);
  });

  test("nothing found says so", () => {
    assert.match(formatAnswer(answer({ results: [] })), /nothing matches that$/);
  });
});
