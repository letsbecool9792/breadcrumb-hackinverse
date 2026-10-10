import express, { type NextFunction, type Request, type Response } from "express";
import type { Db } from "mongodb";

export interface AppOptions {
  /** One line per request. Off in tests, where it only buries the results. */
  log?: boolean;
  /**
   * Defaults to the process-wide connection; tests pass their own database.
   * The model calls (extract, embed, parse a query) join these options as
   * injected functions, so tests can run every endpoint without a model.
   */
  database?: Db;
}

/**
 * The HTTP surface, built without listening so tests can serve it on any
 * port. Stays thin: health, ingest, search, and keeping the phone in step --
 * what the model made of a memory, and deletes. Each route is a few lines over
 * a function tested on its own. docs/api.md is the contract.
 */
export function createApp({ log = true }: AppOptions = {}) {
  const app = express();
  app.disable("x-powered-by");

  if (log) app.use(logRequest);

  // Unauthenticated on purpose: it reveals nothing, and it is what the app
  // pings to tell "server down" from "server up". `service` lets the app tell
  // this server from anything else that happens to be holding the port.
  app.get("/health", (_req, res) => {
    res.json({ service: "breadcrumb", status: "ok" });
  });

  // The server workstream builds these (docs/workstreams/server.md). Until
  // then they answer 501, which the phone treats as "try again later".
  app.post("/memories", notBuilt);
  app.post("/memories/images", notBuilt);
  app.post("/memories/enrichment", notBuilt);
  app.post("/memories/delete", notBuilt);
  app.get("/search", notBuilt);

  // JSON, never Express's HTML pages: the only client parses JSON.
  app.use((_req: Request, res: Response) => {
    res.status(404).json({ error: "not found" });
  });
  app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
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
