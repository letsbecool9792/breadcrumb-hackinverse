import test from "node:test";
import assert from "node:assert";
import type { AddressInfo } from "node:net";
import { MongoClient, type Db } from "mongodb";

import { createApp } from "./app.ts";

let db: Db | undefined;

test.before(async () => {
  if (process.env.MONGODB_URI) {
    try {
      const client = new MongoClient(process.env.MONGODB_URI, { serverSelectionTimeoutMS: 2000 });
      await client.connect();
      db = client.db("test_breadcrumb_" + Date.now());
    } catch {
      db = undefined;
    }
  }
});

test("POST /memories/images handles base64 image and reads text", async (t) => {
  if (!db || !process.env.GEMINI_API_KEY) {
    t.skip("MONGODB_URI or GEMINI_API_KEY unset");
    return;
  }

  // Pre-insert a memory
  await db.collection("memories").insertOne({
    _id: "test-image-1" as any,
    type: "IMAGE",
    hasLink: false,
    capturedAt: new Date(),
    datedAt: new Date(),
    updatedAt: new Date(),
    syncedAt: new Date(),
    note: "This is a test note."
  });

  const app = createApp({ database: db, log: false });
  const server = app.listen(0, "127.0.0.1");
  await new Promise<void>((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;

  try {
    // 1x1 white pixel in JPEG
    const base64 = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=";

    const response = await fetch(`${base}/memories/images`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify([
        {
          id: "test-image-1",
          mimeType: "image/jpeg",
          data: base64
        }
      ])
    });

    if (response.status === 503) {
      t.skip("Gemini is overloaded or rate limited");
      return;
    }
    
    assert.strictEqual(response.status, 200);
    const body = await response.json();
    assert.strictEqual(body.results[0].id, "test-image-1");
    assert.strictEqual(body.results[0].enriched, true);
    assert.strictEqual(body.results[0].embedded, true);
    
    const doc = await db.collection("memories").findOne({ _id: "test-image-1" as any });
    assert.ok(doc);
    assert.ok(doc.enrichment);
    assert.strictEqual(typeof doc.enrichment.readText, "string");
    assert.ok(doc.imageFingerprint);
    assert.ok(doc.imageReadAt);
    assert.ok(doc.embedding);
    assert.strictEqual(doc.embedding.length, 768);

  } finally {
    server.close();
  }
});
