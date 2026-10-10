import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { after, before, describe, test } from "node:test";
import type { Server } from "node:http";
import { createApp } from "./app.ts";

describe("the HTTP surface", () => {
  let server: Server;
  let base: string;

  before(async () => {
    server = createApp({ log: false }).listen(0, "127.0.0.1");
    await new Promise<void>((resolve) => server.once("listening", resolve));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  });

  after(() => new Promise<void>((resolve) => server.close(() => resolve())));

  test("/health names the service, so the phone can tell this server from another on the port", async () => {
    const response = await fetch(`${base}/health`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { service: "breadcrumb", status: "ok" });
  });

  test("an unknown path answers JSON, not an HTML page", async () => {
    const response = await fetch(`${base}/nope`);
    assert.equal(response.status, 404);
    assert.match(response.headers.get("content-type") ?? "", /application\/json/);
    assert.deepEqual(await response.json(), { error: "not found" });
  });

  test("Express does not announce itself", async () => {
    const response = await fetch(`${base}/health`);
    assert.equal(response.headers.get("x-powered-by"), null);
  });

  test("every route in the contract exists, even before it is built", async () => {
    const routes: [string, string][] = [
      ["POST", "/memories"],
      ["POST", "/memories/images"],
      ["POST", "/memories/enrichment"],
      ["POST", "/memories/delete"],
      ["GET", "/search?q=anything"],
    ];
    for (const [method, path] of routes) {
      const response = await fetch(`${base}${path}`, { method });
      assert.notEqual(response.status, 404, `${method} ${path} is missing`);
    }
  });
});
