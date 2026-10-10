import test from "node:test";
import assert from "node:assert";
import { MongoClient, type Db } from "mongodb";
import { createApp } from "./app.ts";
import { parseMemory, defaultEnricher } from "./ingest.ts";
import type { Server } from "node:http";
import type { AddressInfo } from "node:net";

const dbName = `breadcrumb_test_ingest_${Date.now()}`;
let client: MongoClient | undefined;
let db: Db | undefined;

test.before(async () => {
  if (process.env.MONGODB_URI) {
    try {
      client = new MongoClient(process.env.MONGODB_URI, { serverSelectionTimeoutMS: 2000 });
      await client.connect();
      db = client.db(dbName);
    } catch (e) {
      console.warn("Could not connect to MONGODB_URI. Skipping DB tests.");
      client = undefined;
      db = undefined;
    }
  }
});

test.after(async () => {
  if (client && db) {
    await db.dropDatabase();
  }
  if (client) {
    await client.close();
  }
});

test("parseMemory drops unknown fields like localUri", () => {
  const raw = {
    id: "test-id",
    type: "TEXT",
    capturedAt: new Date().toISOString(),
    localUri: "file:///path/to/local/file",
    unknown: "should be dropped"
  };
  const parsed = parseMemory(raw);
  assert.strictEqual(parsed._id, "test-id");
  assert.strictEqual((parsed as any).localUri, undefined);
  assert.strictEqual((parsed as any).unknown, undefined);
});

test("POST /memories parses, stores, and calls enricher", async (t) => {
  if (!db) {
    t.skip("MONGODB_URI unset");
    return;
  }
  
  let called = 0;
  const stubEnricher = async (text: string, asOf: Date) => {
    called++;
    return {
      summary: "test summary",
      kind: "test kind",
      entities: ["entity"],
      dates: []
    };
  };

  const app = createApp({ database: db, enricher: stubEnricher, log: false });
  const server = app.listen(0, "127.0.0.1");
  await new Promise<void>((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;

  const rawMemory = {
    id: "breadcrumb-test-ingest-1",
    type: "TEXT",
    hasLink: false,
    capturedAt: new Date().toISOString(),
    rawText: "This is a test memory",
  };

  try {
    const response = await fetch(`${base}/memories`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify([rawMemory])
    });

    assert.strictEqual(response.status, 200);
    const body = await response.json();
    assert.strictEqual(body.results.length, 1);
    assert.strictEqual(body.results[0].enriched, true);
    assert.strictEqual(called, 1);

    const doc = await db.collection("memories").findOne({ _id: "breadcrumb-test-ingest-1" as any });
    assert.ok(doc);
    assert.strictEqual(doc.enrichment?.summary, "test summary");

    // Re-send with same text should not call enricher again
    await fetch(`${base}/memories`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify([rawMemory])
    });

    assert.strictEqual(called, 1, "Enricher should not be called again for unchanged text");
  } finally {
    server.close();
  }
});

test("POST /memories live test against Gemini", async (t) => {
  if (!db || !process.env.GEMINI_API_KEY) {
    t.skip("MONGODB_URI or GEMINI_API_KEY unset");
    return;
  }

  const app = createApp({ database: db, enricher: defaultEnricher, log: false });
  const server = app.listen(0, "127.0.0.1");
  await new Promise<void>((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;

  const rawMemory = {
    id: "breadcrumb-test-live-1",
    type: "TEXT",
    hasLink: false,
    capturedAt: new Date().toISOString(),
    rawText: "I have a flight to London next Friday at 10 AM.",
  };

  try {
    const response = await fetch(`${base}/memories`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify([rawMemory])
    });

    if (response.status === 503) {
      t.skip("Gemini is overloaded or rate limited");
      return;
    }
    
    assert.strictEqual(response.status, 200);
    const body = await response.json();
    assert.strictEqual(body.results[0].enriched, true);
    
    const doc = await db.collection("memories").findOne({ _id: "breadcrumb-test-live-1" as any });
    assert.ok(doc);
    assert.ok(doc.enrichment);
    assert.ok(doc.enrichment.summary);
    assert.ok(doc.enrichment.kind);
    assert.ok(Array.isArray(doc.enrichment.entities));
    assert.ok(Array.isArray(doc.enrichment.dates));
  } finally {
    server.close();
  }
});
