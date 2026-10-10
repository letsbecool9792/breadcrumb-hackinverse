import express, { type NextFunction, type Request, type Response } from "express";
import type { Db } from "mongodb";

import { makeIngestHandler, type Enricher, type Embedder } from "./ingest.ts";
import { makeImageHandler, type ImageReader } from "./images.ts";
import { makeSearchHandler, type QueryEmbedder } from "./search.ts";
import { type SearchParser } from "./search_parser.ts";
import { makeEnrichmentHandler, makeDeleteHandler } from "./sync.ts";

export interface AppOptions {
  /** One line per request. Off in tests, where it only buries the results. */
  log?: boolean;
  /**
   * Defaults to the process-wide connection; tests pass their own database.
   * The model calls (extract, embed, parse a query) join these options as
   * injected functions, so tests can run every endpoint without a model.
   */
  database?: Db;
  enricher?: Enricher;
  embedder?: Embedder;
  imageReader?: ImageReader;
  queryEmbedder?: QueryEmbedder;
  searchParser?: SearchParser;
}

/**
 * The HTTP surface, built without listening so tests can serve it on any
 * port. Stays thin: health, ingest, search, and keeping the phone in step --
 * what the model made of a memory, and deletes. Each route is a few lines over
 * a function tested on its own. docs/api.md is the contract.
 */
export function createApp({ log = true, database, enricher, embedder, imageReader, queryEmbedder, searchParser }: AppOptions = {}) {
  const app = express();
  app.disable("x-powered-by");

  if (log) app.use(logRequest);

  // Eight downscaled pictures in base64 fit well inside this.
  app.use(express.json({ limit: "16mb" }));

  // Unauthenticated on purpose: it reveals nothing, and it is what the app
  // pings to tell "server down" from "server up". `service` lets the app tell
  // this server from anything else that happens to be holding the port.
  app.get("/health", (_req, res) => {
    res.json({ service: "breadcrumb", status: "ok" });
  });

  // Without a database (the HTTP-surface tests) they answer 501, which the
  // phone treats as "try again later".
  if (database) {
    app.post("/memories", makeIngestHandler(database, enricher, embedder));
    app.post("/memories/images", makeImageHandler(database, imageReader, embedder));
    app.get("/search", makeSearchHandler(database, queryEmbedder, searchParser));
    app.post("/memories/enrichment", makeEnrichmentHandler(database));
    app.post("/memories/delete", makeDeleteHandler(database));
  } else {
    app.post("/memories", notBuilt);
    app.post("/memories/images", notBuilt);
    app.get("/search", notBuilt);
    app.post("/memories/enrichment", notBuilt);
    app.post("/memories/delete", notBuilt);
  }

  // JSON, never Express's HTML pages: the only client parses JSON.
  app.use((_req: Request, res: Response) => {
    res.status(404).json({ error: "not found" });
  });
  app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
    // A body that is not JSON, or too big, is the request's fault: a 400 the
    // phone will not resend as it is, never a 500.
    const type = (err as { type?: string } | null)?.type;
    if (type === "entity.parse.failed") return void res.status(400).json({ error: "the body is not valid JSON" });
    if (type === "entity.too.large") return void res.status(413).json({ error: "the body is too large" });
    console.error(err);
    res.status(500).json({ error: "internal" });
  });

  return app;
}

function notBuilt(_req: Request, res: Response) {
  res.status(501).json({ error: "not built yet" });
}

/**
 * The path only, never the query string: a search's `q` is what someone was
 * looking for, and it does not belong in a log.
 */
function logRequest(req: Request, res: Response, next: NextFunction) {
  const started = performance.now();
  res.on("finish", () => {
    const ms = Math.round(performance.now() - started);
    console.log(`${req.method} ${req.path} ${res.statusCode} ${ms}ms`);
  });
  next();
}
