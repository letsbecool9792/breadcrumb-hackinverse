import assert from "node:assert/strict";
import { after, before, beforeEach, describe, test } from "node:test";
import { embedDocuments } from "./gemini.ts";
import { defaultEnricher, parseMemory, type Embedder, type Enricher } from "./ingest.ts";
import { memories } from "./memories.ts";
import { axis, serve, testDatabase, type TestDatabase } from "./test_support.ts";

const saved = Date.UTC(2026, 3, 12, 10, 0, 0);

describe("parsing a memory", () => {
  test("unknown fields are dropped, so localUri can never reach the cloud", () => {
    const { memory } = parseMemory({ id: "m1", type: "TEXT", capturedAt: saved, rawText: "hi", localUri: "file:///data/x.jpg" });
    assert.ok(memory);
    assert.equal("localUri" in memory, false);
  });

  test("an id, a known type and a capture time in epoch milliseconds are required", () => {
    const { memory, errors } = parseMemory({ id: "  ", type: "VIDEO", capturedAt: "2026-04-12T10:00:00Z" });
    assert.equal(memory, undefined);
    assert.equal(errors.length, 3);
  });

  test("blank text is null, and the update time is the capture time unless sent", () => {
    const { memory } = parseMemory({ id: "m2", type: "TEXT", capturedAt: saved, title: "   ", note: "" });
    assert.equal(memory?.title, null);
    assert.equal(memory?.note, null);
    assert.equal(memory?.hasLink, false);
    assert.equal(memory?.updatedAt.getTime(), saved);
  });

  test("a memory is dated by when its picture was taken, else when it was saved", () => {
    const taken = Date.UTC(2024, 1, 3);
    assert.equal(parseMemory({ id: "a", type: "IMAGE", capturedAt: saved, contentCreatedAt: taken }).memory?.datedAt.getTime(), taken);
    assert.equal(parseMemory({ id: "b", type: "IMAGE", capturedAt: saved }).memory?.datedAt.getTime(), saved);
  });

  test("sites come from the shared text, named, never from what OCR read", () => {
    const { memory } = parseMemory({
      id: "c",
      type: "IMAGE",
      capturedAt: saved,
      rawText: "watch https://youtu.be/bct-a",
      extractedText: "https://www.instagram.com/bct-b",
    });
    assert.deepEqual(memory?.linkSites, ["YouTube"]);
  });
});

describe("POST /memories", () => {
  let database: TestDatabase | undefined;
  let app: Awaited<ReturnType<typeof serve>>;
  const calls = { read: [] as string[][], embedded: [] as string[][], failure: undefined as unknown };

  const enricher: Enricher = async (batch) => {
    calls.read.push(batch.map((item) => item.text));
    if (calls.failure) throw calls.failure;
    return batch.map((item) => ({ summary: `about: ${item.text.slice(0, 30)}`, kind: "note", entities: [], dates: [] }));
  };
  const embedder: Embedder = async (texts) => {
    calls.embedded.push(texts);
    return texts.map((_, index) => axis(index));
  };

  before(async () => {
    database = await testDatabase("ingest");
    if (database) app = await serve({ database: database.db, enricher, embedder });
  });
  after(async () => {
    await app?.close();
    await database?.close();
  });
  beforeEach(() => {
    calls.read = [];
    calls.embedded = [];
    calls.failure = undefined;
  });

  const memory = (id: string, fields: Record<string, unknown> = {}) => ({
    id: `breadcrumb-test-ingest-${id}`,
    type: "TEXT",
    capturedAt: saved,
    rawText: `text of ${id}`,
    ...fields,
  });

  test("a batch is read with one model call and embedded with one request", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    const response = await app.post("/memories", [memory("one"), memory("two"), memory("three")]);

    assert.equal(response.status, 200);
    const { results } = await response.json();
    assert.deepEqual(results.map((result: { enriched: boolean; embedded: boolean }) => [result.enriched, result.embedded]), [
      [true, true],
      [true, true],
      [true, true],
    ]);
    assert.equal(calls.read.length, 1);
    assert.equal(calls.read[0]?.length, 3);
    assert.equal(calls.embedded.length, 1);
    const stored = await memories(database.db).findOne({ _id: "breadcrumb-test-ingest-two" });
    assert.equal(stored?.enrichment?.summary, "about: text of two");
    assert.equal(stored?.embedding?.length, 768);
  });

  test("a re-send with unchanged text costs no model call; a new note only a new embedding", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    await app.post("/memories", [memory("four")]);
    calls.read = [];
    calls.embedded = [];

    await app.post("/memories", [memory("four")]);
    assert.equal(calls.read.length, 0);
    assert.equal(calls.embedded.length, 0);

    await app.post("/memories", [memory("four", { note: "Priya says apply early" })]);
    assert.equal(calls.read.length, 0, "the note is never given to the model to describe");
    assert.equal(calls.embedded.length, 1);
    assert.match(calls.embedded[0]?.[0] ?? "", /^Priya says apply early/, "the note leads what is embedded");
  });

  test("a memory with no text is stored without a model call", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    const response = await app.post("/memories", [{ id: "breadcrumb-test-ingest-bare", type: "IMAGE", capturedAt: saved }]);

    assert.equal(response.status, 200);
    const { results } = await response.json();
    assert.equal(results[0].reason, "no text");
    assert.equal(calls.read.length, 0);
    assert.ok(await memories(database.db).findOne({ _id: "breadcrumb-test-ingest-bare" }));
  });

  test("a model call that may pass answers 503, and every memory is still stored", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    calls.failure = Object.assign(new Error("slow down"), { status: 429 });
    const response = await app.post("/memories", [memory("busy")]);

    assert.equal(response.status, 503);
    const { results } = await response.json();
    assert.equal(results[0].retryable, true);
    const stored = await memories(database.db).findOne({ _id: "breadcrumb-test-ingest-busy" });
    assert.equal(stored?.enrichmentError, "slow down");
  });

  test("a refusal is stored with its reason and answers 200, not to be retried as it is", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    calls.failure = Object.assign(new Error("blocked"), { status: 400 });
    const response = await app.post("/memories", [memory("refused")]);

    assert.equal(response.status, 200);
    const { results } = await response.json();
    assert.equal(results[0].retryable, undefined);
    assert.equal(results[0].reason, "blocked");
  });

  test("an invalid memory refuses the batch, saying which, and stores none of it", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    const response = await app.post("/memories", [memory("valid"), { id: "breadcrumb-test-ingest-x", type: "VIDEO" }]);

    assert.equal(response.status, 400);
    const body = await response.json();
    assert.equal(body.error, "invalid memory");
    assert.equal(body.invalid[0].index, 1);
    assert.equal(await memories(database.db).findOne({ _id: "breadcrumb-test-ingest-valid" }), null);
  });

  test("one object, for curl, is answered with one result", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    const response = await app.post("/memories", memory("single"));
    const body = await response.json();
    assert.equal(body.id, "breadcrumb-test-ingest-single");
    assert.equal(body.enriched, true);
  });

  test("what a picture showed is kept, and read with the text", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    await memories(database.db).insertOne({
      _id: "breadcrumb-test-ingest-menu",
      type: "IMAGE",
      hasLink: false,
      capturedAt: new Date(saved),
      datedAt: new Date(saved),
      updatedAt: new Date(saved),
      syncedAt: new Date(saved),
      enrichment: { summary: "a menu", kind: "menu", entities: [], dates: [], readText: "TOIT brewpub menu", at: new Date(saved) },
      imageFingerprint: "bytes",
    });

    await app.post("/memories", [memory("menu", { type: "IMAGE", rawText: "dinner on friday" })]);

    assert.match(calls.read[0]?.[0] ?? "", /TOIT brewpub menu/);
    const stored = await memories(database.db).findOne({ _id: "breadcrumb-test-ingest-menu" });
    assert.equal(stored?.enrichment?.readText, "TOIT brewpub menu");
  });
});

describe("live, against Gemini", () => {
  test("a batch is read and embedded, and related texts land closer than unrelated ones", async (t) => {
    if (!process.env.GEMINI_API_KEY) return t.skip("GEMINI_API_KEY unset");
    let readings;
    let vectors;
    try {
      readings = await defaultEnricher([
        { text: "I have a flight to London next Friday at 10 AM.", asOf: new Date(saved) },
        { text: "British Airways flight leaves for Heathrow on Friday morning.", asOf: new Date(saved) },
        { text: "The recipe for chocolate cake needs eggs and flour.", asOf: new Date(saved) },
      ]);
      vectors = await embedDocuments(readings.map((reading) => reading?.summary ?? ""));
    } catch (error) {
      const status = (error as { status?: number }).status;
      if (status === 429 || status === 503) return t.skip(`Gemini is busy (${status}): a skip, not a pass`);
      throw error;
    }

    assert.ok(readings.every((reading) => reading && reading.summary.length > 0), "one reading per memory, in order");
    const [flight, plane, cake] = vectors;
    assert.equal(flight?.length, 768);
    const dot = (a: number[], b: number[]) => a.reduce((sum, value, index) => sum + value * (b[index] ?? 0), 0);
    assert.ok(dot(flight!, plane!) - dot(flight!, cake!) > 0.1, "related texts closer by more than 0.1");
  });
});
