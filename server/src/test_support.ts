import { MongoClient, type Db } from "mongodb";
import type { Server } from "node:http";
import type { AddressInfo } from "node:net";
import { createApp, type AppOptions } from "./app.ts";

/**
 * Shared by the test files. node --test runs each file in its own process, in
 * parallel: two files sharing a database would wipe each other's documents
 * mid-test, and the failures look like impossible counts.
 */

export interface TestDatabase {
  db: Db;
  /** Drops this file's own collection, never a database, and closes the client whatever happens. */
  close: () => Promise<void>;
}

/** `<MONGODB_DB>_test_<suite>`, this file's own; undefined when MONGODB_URI is unset. */
export async function testDatabase(suite: string): Promise<TestDatabase | undefined> {
  const client = await connect();
  if (!client) return undefined;
  const db = client.db(`${process.env.MONGODB_DB || "breadcrumb"}_test_${suite}`);
  return {
    db,
    close: async () => {
      try {
        await db.collection("memories").drop().catch(() => undefined);
      } finally {
        await client.close();
      }
    },
  };
}

/**
 * The real database, for the search tests: they borrow the real indexes, since
 * a test pair plus the real pair would be four, over M0's three. The caller
 * deletes its own fixed ids and nothing else.
 */
export async function realDatabase(): Promise<{ db: Db; close: () => Promise<void> } | undefined> {
  const client = await connect();
  if (!client) return undefined;
  return { db: client.db(process.env.MONGODB_DB || "breadcrumb"), close: () => client.close() };
}

async function connect(): Promise<MongoClient | undefined> {
  const uri = process.env.MONGODB_URI;
  if (!uri) return undefined;
  const client = new MongoClient(uri, { serverSelectionTimeoutMS: 5_000 });
  try {
    await client.connect();
    return client;
  } catch (error) {
    await client.close();
    throw error;
  }
}

/** The app on a free port, with [post] and [get] at hand. */
export async function serve(options: AppOptions) {
  const server: Server = createApp({ log: false, ...options }).listen(0, "127.0.0.1");
  await new Promise<void>((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  return {
    post: (path: string, body: unknown) =>
      fetch(`${base}${path}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
    get: (path: string) => fetch(`${base}${path}`),
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

/** A unit vector pointing along [axis]: stand-in embeddings whose nearness is known. */
export function axis(axis: number, dimensions = 768): number[] {
  return Array.from({ length: dimensions }, (_, index) => (index === axis ? 1 : 0));
}
