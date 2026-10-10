import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";
import { memories } from "./memories.ts";
import { serve, testDatabase, type TestDatabase } from "./test_support.ts";

describe("POST /memories/enrichment and /memories/delete", () => {
  let database: TestDatabase | undefined;

  before(async () => {
    database = await testDatabase("sync");
  });
  after(async () => {
    await database?.close();
  });

  test("ids must be 1 to 100 non-empty strings", async () => {
    const app = await serve({ database: {} as never });
    try {
      for (const path of ["/memories/enrichment", "/memories/delete"]) {
        assert.equal((await app.post(path, {})).status, 400);
        assert.equal((await app.post(path, { ids: [] })).status, 400);
        assert.equal((await app.post(path, { ids: Array(101).fill("id") })).status, 400);
        assert.equal((await app.post(path, { ids: [{ $ne: null }] })).status, 400);
        assert.equal((await app.post(path, { ids: [""] })).status, 400);
      }
    } finally {
      await app.close();
    }
  });

  test("enrichment answers held memories, with nulls until enriched, and leaves unknown ids out", async (t) => {
    if (!database) return t.skip("MONGODB_URI unset");
    const at = new Date();
    await memories(database.db).insertMany([
      { _id: "breadcrumb-test-sync-read", type: "TEXT", hasLink: false, capturedAt: at, datedAt: at, updatedAt: at, syncedAt: at,
        enrichment: { summary: "a summary", kind: "note", entities: [], dates: [], at } },
      { _id: "breadcrumb-test-sync-unread", type: "TEXT", hasLink: false, capturedAt: at, datedAt: at, updatedAt: at, syncedAt: at },
    ]);
    const app = await serve({ database: database.db });
    try {
      const response = await app.post("/memories/enrichment", { ids: ["breadcrumb-test-sync-read", "breadcrumb-test-sync-unread", "breadcrumb-test-sync-none"] });
      const { results } = await response.json();
      const byId = Object.fromEntries(results.map((result: { id: string }) => [result.id, result]));
      assert.deepEqual(byId["breadcrumb-test-sync-read"], { id: "breadcrumb-test-sync-read", summary: "a summary", kind: "note", readText: null });
      assert.deepEqual(byId["breadcrumb-test-sync-unread"], { id: "breadcrumb-test-sync-unread", summary: null, kind: null, readText: null });
      assert.equal(byId["breadcrumb-test-sync-none"], undefined);

      const deleted = await (await app.post("/memories/delete", { ids: ["breadcrumb-test-sync-read", "breadcrumb-test-sync-none"] })).json();
      assert.deepEqual(deleted, { deleted: 1 });
      const again = await (await app.post("/memories/delete", { ids: ["breadcrumb-test-sync-read"] })).json();
      assert.deepEqual(again, { deleted: 0 }, "a delete sent again is simply not counted");
    } finally {
      await app.close();
    }
  });
});
