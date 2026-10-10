import test from "node:test";
import assert from "node:assert";
import type { AddressInfo } from "node:net";

import { createApp } from "./app.ts";

test("POST /memories/enrichment and /delete validation", async () => {
  const app = createApp({ log: false, database: {} as any });
  const server = app.listen(0, "127.0.0.1");
  await new Promise<void>((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;

  try {
    const res1 = await fetch(`${base}/memories/enrichment`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({})
    });
    assert.strictEqual(res1.status, 400);

    const res2 = await fetch(`${base}/memories/enrichment`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids: [] })
    });
    assert.strictEqual(res2.status, 400);

    const res3 = await fetch(`${base}/memories/delete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({})
    });
    assert.strictEqual(res3.status, 400);

    const res4 = await fetch(`${base}/memories/delete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids: Array(101).fill("id") })
    });
    assert.strictEqual(res4.status, 400);
  } finally {
    server.close();
  }
});
