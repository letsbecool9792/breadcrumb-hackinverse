import assert from "node:assert/strict";
import { describe, test } from "node:test";
import { MinuteGate, l2Normalize, modelFailure, pinnedModel } from "./gemini.ts";

describe("talking to Gemini", () => {
  test("a model is pinned by version, and an alias stops the server", () => {
    process.env.BREADCRUMB_TEST_MODEL = "gemini-flash-latest";
    assert.throws(() => pinnedModel("BREADCRUMB_TEST_MODEL", "gemini-3.5-flash-lite"), /alias/);
    process.env.BREADCRUMB_TEST_MODEL = "gemini-flash";
    assert.throws(() => pinnedModel("BREADCRUMB_TEST_MODEL", "gemini-3.5-flash-lite"), /alias/);
    delete process.env.BREADCRUMB_TEST_MODEL;
    assert.equal(pinnedModel("BREADCRUMB_TEST_MODEL", "gemini-3.5-flash-lite"), "gemini-3.5-flash-lite");
  });

  test("a rate limit, an overloaded model or no answer may pass; a refusal does not", () => {
    assert.equal(modelFailure(Object.assign(new Error("slow down"), { status: 429 })).retryable, true);
    assert.equal(modelFailure(Object.assign(new Error("overloaded"), { status: 503 })).retryable, true);
    assert.equal(modelFailure(new Error("fetch failed")).retryable, true);
    assert.equal(modelFailure(Object.assign(new Error("bad request"), { status: 400 })).retryable, false);
  });

  test("the gate lets a minute's allowance through, then waits for the oldest to age out", async () => {
    let clock = 0;
    const gate = new MinuteGate(2, () => clock);
    assert.equal(await gate.acquire(0), true);
    assert.equal(await gate.acquire(0), true);
    assert.equal(await gate.acquire(0), false, "a third in the same minute waits, and 0 ms is no wait");
    clock = 60_000;
    assert.equal(await gate.acquire(0), true, "a minute on, a slot is free again");
  });

  test("vectors are made unit length", () => {
    const [x, y] = l2Normalize([3, 4]);
    assert.equal(x, 0.6);
    assert.equal(y, 0.8);
  });
});
