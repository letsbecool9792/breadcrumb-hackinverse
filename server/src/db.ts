import { type Db, MongoClient } from "mongodb";

/**
 * One client for the whole process. It owns a connection pool and reconnects
 * on its own, so a client per request would only burn through M0's connection
 * limit.
 */
let client: MongoClient | undefined;

export function databaseName(): string {
  return process.env.MONGODB_DB ?? "breadcrumb";
}

export async function connectMongo(): Promise<MongoClient> {
  if (client) return client;

  const uri = process.env.MONGODB_URI;
  if (!uri) {
    throw new Error("MONGODB_URI is not set -- copy server/.env.example to server/.env and fill it in");
  }

  // No serverApi: "strict" would reject the search-index commands hybrid search needs.
  const connecting = new MongoClient(uri, {
    appName: "breadcrumb-server",
    // Fail in seconds rather than hanging for the default 30, which in
    // development almost always means the IP is not allowlisted.
    serverSelectionTimeoutMS: 5_000,
  });

  await connecting.connect();
  client = connecting;
  return client;
}

/** The database. Call [connectMongo] first -- at startup, not per request. */
export function db(name: string = databaseName()): Db {
  if (!client) throw new Error("connectMongo() has not run yet");
  return client.db(name);
}

export async function closeMongo(): Promise<void> {
  await client?.close();
  client = undefined;
}

/** Turns Atlas's vaguer failures into the thing that is actually wrong. */
export function describeMongoError(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error);
  if (/bad auth|authentication failed/i.test(message)) {
    return `${message}\nCheck the user and password in MONGODB_URI. A password containing @ : / ? # % must be URL-encoded.`;
  }
  if (/server selection timed out|ENOTFOUND|querySrv/i.test(message)) {
    return `${message}\nCheck that Atlas Network Access allows this machine's IP, and that the cluster hostname is right.`;
  }
  return message;
}
