import test from "node:test";
import assert from "node:assert";
import type { AddressInfo } from "node:net";

import { createApp } from "./app.ts";

test("GET /search validation", async () => {
  const app = createApp({ log: false, database: {} as any });
  const server = app.listen(0, "127.0.0.1");
  await new Promise<void>((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;

  try {
    const res1 = await fetch(`${base}/search`);
    assert.strictEqual(res1.status, 400);

    const res2 = await fetch(`${base}/search?q=test&limit=100`);
    assert.strictEqual(res2.status, 400);

    const res3 = await fetch(`${base}/search?q=test&limit=0`);
    assert.strictEqual(res3.status, 400);
  } finally {
    server.close();
  }
});
