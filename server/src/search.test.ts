import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";
import type { Interpretation } from "./api.ts";
import { startOfDay } from "./days.ts";
import { TEXT_INDEX, VECTOR_INDEX, ensureIndexes, memories, type MemoryDoc } from "./memories.ts";
import { filtersOf, fuse, mqlFilter, search, searchFilters, type SearchOptions } from "./search.ts";
import { guardInterpretation } from "./search_parser.ts";
import { axis, realDatabase, serve } from "./test_support.ts";

const interpretation = (fields: Partial<Interpretation>): Interpretation => ({
  query: "",
  types: [],
  from: null,
  to: null,
  sourceApp: null,
  ...fields,
});

describe("taking a phrase apart", () => {
  test("made-up kinds, impossible days and unknown sources are dropped, never guessed at", () => {
    const guarded = guardInterpretation(
      { query: " internship ", types: ["IMAGE", "VIDEO", "IMAGE"], from: "2026-02-31", to: "2026-04-30", sourceApp: "Snapchat" },
      ["WhatsApp", "Instagram"],
    );
    assert.deepEqual(guarded, { query: "internship", types: ["IMAGE"], from: null, to: "2026-04-30", sourceApp: null });
  });

  test("days given the wrong way round are put in order", () => {
    const guarded = guardInterpretation({ query: "", types: [], from: "2026-04-30", to: "2026-04-01", sourceApp: null }, []);
    assert.deepEqual([guarded.from, guarded.to], ["2026-04-01", "2026-04-30"]);
  });
});

describe("filters", () => {
  test("a day runs from the server's midnight to the next", () => {
    const filters = filtersOf(interpretation({ from: "2026-04-01", to: "2026-04-30" }));
    assert.equal(filters.from?.getTime(), new Date(2026, 3, 1).getTime());
    assert.equal(filters.until?.getTime(), new Date(2026, 4, 1).getTime());
  });

  test("a link from WhatsApp is a link AND from WhatsApp, and a link is anything carrying one", () => {
    const filters = filtersOf(interpretation({ types: ["LINK"], sourceApp: "WhatsApp" }));
    assert.deepEqual(mqlFilter(filters), {
      $and: [
        { $or: [{ type: { $in: ["LINK"] } }, { hasLink: true }] },
        { $or: [{ sourceAppLabel: "WhatsApp" }, { linkSites: "WhatsApp" }] },
      ],
    });
  });

  test("the words half filters alike, with token operators", () => {
    const clauses = searchFilters(filtersOf(interpretation({ types: ["IMAGE"], sourceApp: "Instagram" })), ["a"]);
    assert.deepEqual(clauses[0], { in: { path: "type", value: ["IMAGE"] } });
    assert.deepEqual(clauses[1], {
      compound: {
        should: [{ equals: { path: "sourceAppLabel", value: "Instagram" } }, { equals: { path: "linkSites", value: "Instagram" } }],
        minimumShouldMatch: 1,
      },
    });
    assert.deepEqual(clauses[2], { in: { path: "_id", value: ["a"] } });
  });

  test("no filters is no filter", () => {
    assert.equal(mqlFilter(filtersOf(null)), undefined);
    assert.deepEqual(searchFilters(filtersOf(null)), []);
  });
});

describe("fusing the two halves", () => {
  test("placed modestly by both can rise above placed first by one", () => {
    const fused = fuse(["first-by-meaning", "both"], ["first-by-words", "both"], 10);
    assert.equal(fused[0]?.id, "both");
    assert.deepEqual(fused[0]?.ranks, { vector: 2, text: 2 });
  });

  test("a half that did not place a result says null", () => {
    const fused = fuse(["only-meaning"], [], 10);
    assert.deepEqual(fused[0]?.ranks, { vector: 1, text: null });
  });
});

describe("GET /search", () => {
  test("a missing or long phrase, or a limit out of range, is a 400", async () => {
    const app = await serve({ database: {} as never });
    try {
      assert.equal((await app.get("/search")).status, 400);
      assert.equal((await app.get(`/search?q=${"a".repeat(501)}`)).status, 400);
      assert.equal((await app.get("/search?q=test&limit=51")).status, 400);
      assert.equal((await app.get("/search?q=test&limit=0")).status, 400);
    } finally {
      await app.close();
    }
  });
});

/**
 * Against the real indexes, borrowed: every memory here has a fixed id starting
 * breadcrumb-test-, every search is narrowed to those ids in both halves, and
 * cleanup deletes those ids and nothing else. Seeded words and URL paths are
 * id-like, so no memory is found by another test's phrase.
 */
describe("searching Atlas", () => {
  let database: Awaited<ReturnType<typeof realDatabase>>;
  let ready = false;
  const id = (name: string) => `breadcrumb-test-search-${name}`;
  const ids = ["intern", "burger", "reel", "page"].map(id);

  const seed = (name: string, vector: number, fields: Partial<MemoryDoc>): MemoryDoc => ({
    _id: id(name),
    type: "TEXT",
    hasLink: false,
    capturedAt: new Date(2026, 5, 1),
    datedAt: new Date(2026, 5, 1),
    updatedAt: new Date(2026, 5, 1),
    syncedAt: new Date(),
    embedding: axis(vector),
    ...fields,
  });

  const run = (phrase: string, parsed: Interpretation, query: number) => {
    const options: SearchOptions = { limit: 10, embedder: async () => axis(query), parser: async () => parsed, onlyIds: ids };
    return search(database!.db, phrase, options);
  };

  before(async () => {
    database = await realDatabase();
    if (!database) return;
    await ensureIndexes(database.db);
    const collection = memories(database.db);
    await collection.deleteMany({ _id: { $in: ids } });
    await collection.insertMany([
      seed("intern", 0, { type: "IMAGE", datedAt: new Date(2026, 3, 12, 9), extractedText: "Qualcomm hiring interns, apply by April 30" }),
      seed("burger", 1, { sourceAppLabel: "WhatsApp", rawText: "Try the brewery burger in Indiranagar" }),
      seed("reel", 2, { type: "IMAGE", hasLink: true, sourceAppLabel: "WhatsApp", rawText: "https://www.instagram.com/reel/bct1", linkSites: ["Instagram"] }),
      seed("page", 3, { type: "LINK", hasLink: true, sourceAppLabel: "Chrome", rawText: "https://example.org/bct2", linkSites: ["Example"] }),
    ]);
    // A new index takes about 30 s to become searchable, and a write a moment to be indexed.
    for (let waited = 0; waited < 120_000 && !ready; waited += 3_000) {
      const listed = await collection.listSearchIndexes().toArray();
      const queryable = [VECTOR_INDEX.name, TEXT_INDEX.name].every((name) => listed.some((index) => index.name === name && (index as { queryable?: boolean }).queryable));
      if (queryable) {
        const found = await run("Qualcomm", interpretation({ query: "Qualcomm" }), 0);
        ready = found.results.some((result) => result.id === id("intern") && result.ranks.text !== null);
      }
      if (!ready) await new Promise((resolve) => setTimeout(resolve, 3_000));
    }
  });
  after(async () => {
    if (!database) return;
    try {
      await memories(database.db).deleteMany({ _id: { $in: ids } });
    } finally {
      await database.close();
    }
  });

  test("a proper noun's words lift its memory above what only the meaning picked", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    if (!ready) return t.skip("the search indexes did not become searchable in time");
    // The phrase's meaning points at the burger; its words at the internship.
    const answer = await run("Qualcomm", interpretation({ query: "Qualcomm" }), 1);
    assert.equal(answer.results[0]?.id, id("intern"));
    assert.equal(answer.results[0]?.ranks.text, 1);
    assert.ok((answer.results[0]?.score ?? 0) > 0);
  });

  test("an all-filter phrase lists its matches newest first, with no score", async (t) => {
    if (!database || !ready) return t.skip("no searchable Atlas");
    const answer = await run("screenshots from April", interpretation({ types: ["IMAGE"], from: "2026-04-01", to: "2026-04-30" }), 0);
    assert.deepEqual(answer.results.map((result) => result.id), [id("intern")]);
    assert.equal(answer.results[0]?.score, null);
    assert.deepEqual(answer.results[0]?.ranks, { vector: null, text: null });
  });

  test("the link someone sent on WhatsApp is a link from WhatsApp, a captioned photo included", async (t) => {
    if (!database || !ready) return t.skip("no searchable Atlas");
    const answer = await run("the link someone sent me on WhatsApp", interpretation({ types: ["LINK"], sourceApp: "WhatsApp" }), 0);
    assert.deepEqual(answer.results.map((result) => result.id), [id("reel")]);
  });

  test("an Instagram link is found by the site, however it arrived", async (t) => {
    if (!database || !ready) return t.skip("no searchable Atlas");
    const answer = await run("ig link", interpretation({ types: ["LINK"], sourceApp: "Instagram" }), 0);
    assert.deepEqual(answer.results.map((result) => result.id), [id("reel")]);
  });

  test("a filter narrows both halves", async (t) => {
    if (!database || !ready) return t.skip("no searchable Atlas");
    const answer = await run("brewery from WhatsApp", interpretation({ query: "brewery", sourceApp: "WhatsApp" }), 3);
    assert.equal(answer.results[0]?.id, id("burger"));
    assert.ok(answer.results.every((result) => [id("burger"), id("reel")].includes(result.id)), "Chrome's page is filtered out of both");
  });

  test("the answer echoes the phrase as typed, and how it was taken apart", async (t) => {
    if (!database || !ready) return t.skip("no searchable Atlas");
    const answer = await run("Qualcomm", interpretation({ query: "Qualcomm" }), 0);
    assert.equal(answer.query, "Qualcomm");
    assert.equal(answer.interpretation?.query, "Qualcomm");
    assert.equal("interpretationError" in answer, false);
    assert.ok(startOfDay("2026-04-30"));
  });
});
