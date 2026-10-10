import assert from "node:assert/strict";
import { after, before, beforeEach, describe, test } from "node:test";
import { defaultImageReader, type ImageReader, type Picture } from "./images.ts";
import type { Embedder, Enricher } from "./ingest.ts";
import { memories } from "./memories.ts";
import { axis, serve, testDatabase, type TestDatabase } from "./test_support.ts";

const saved = Date.UTC(2026, 3, 12, 10, 0, 0);
// 1x1 white JPEG
const PIXEL =
  "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=";

describe("POST /memories/images", () => {
  let database: TestDatabase | undefined;
  let app: Awaited<ReturnType<typeof serve>>;
  const calls = { pictures: [] as Picture[][], read: 0 };

  const imageReader: ImageReader = async (pictures) => {
    calls.pictures.push(pictures);
    return pictures.map(() => ({ readText: "TOIT brewpub, Indiranagar", summary: "brewpub menu", kind: "menu", entities: ["Toit"], dates: [] }));
  };
  const enricher: Enricher = async (batch) => {
    calls.read += 1;
    return batch.map(() => ({ summary: "s", kind: "k", entities: [], dates: [] }));
  };
  const embedder: Embedder = async (texts) => texts.map((_, index) => axis(index));

  before(async () => {
    database = await testDatabase("images");
    if (database) app = await serve({ database: database.db, imageReader, enricher, embedder });
  });
  after(async () => {
    await app?.close();
    await database?.close();
  });
  beforeEach(() => {
    calls.pictures = [];
    calls.read = 0;
  });

  const held = { id: "breadcrumb-test-images-menu", type: "IMAGE", capturedAt: saved, extractedText: "TOIT", note: "Priya's pick" };

  test("a picture of a held memory is read once, with its note as context, and re-embedded", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    await app.post("/memories", [held]);

    const response = await app.post("/memories/images", [{ id: held.id, mimeType: "image/jpeg", data: PIXEL }]);

    assert.equal(response.status, 200);
    const { results } = await response.json();
    assert.deepEqual([results[0].enriched, results[0].embedded], [true, true]);
    assert.equal(calls.pictures.length, 1);
    assert.equal(calls.pictures[0]?.[0]?.note, "Priya's pick");
    const stored = await memories(database.db).findOne({ _id: held.id });
    assert.equal(stored?.enrichment?.readText, "TOIT brewpub, Indiranagar");
    assert.equal(stored?.enrichment?.summary, "brewpub menu");
    assert.ok(stored?.imageFingerprint);
  });

  test("the same bytes are never read twice, and the memory's next re-send costs no model call", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    const again = await app.post("/memories/images", [{ id: held.id, mimeType: "image/jpeg", data: PIXEL }]);
    assert.equal(again.status, 200);
    assert.equal(calls.pictures.length, 0);

    await app.post("/memories", [held]);
    assert.equal(calls.read, 0, "the text and what the picture showed are unchanged");
  });

  test("a picture of a memory the server does not hold says so", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    const response = await app.post("/memories/images", [{ id: "breadcrumb-test-images-none", mimeType: "image/png", data: PIXEL }]);
    const { results } = await response.json();
    assert.equal(results[0].reason, "no such memory");
    assert.equal(calls.pictures.length, 0);
  });

  test("a picture that is not JPEG, PNG or WebP is refused", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    const response = await app.post("/memories/images", [{ id: held.id, mimeType: "image/gif", data: PIXEL }]);
    assert.equal(response.status, 400);
  });
});

describe("live, against Gemini", () => {
  test("a picture is read in one call, with its reading and the rest", async (t) => {
    if (!process.env.GEMINI_API_KEY) return t.skip("GEMINI_API_KEY unset");
    let readings;
    try {
      readings = await defaultImageReader([{ mimeType: "image/jpeg", data: PIXEL, text: "", note: null, asOf: new Date(saved) }]);
    } catch (error) {
      const status = (error as { status?: number }).status;
      if (status === 429 || status === 503) return t.skip(`Gemini is busy (${status}): a skip, not a pass`);
      throw error;
    }
    assert.equal(typeof readings[0]?.readText, "string");
    assert.equal(typeof readings[0]?.summary, "string");
  });
});
