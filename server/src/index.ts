import { createApp } from "./app.ts";
import { connectMongo, databaseName, db, describeMongoError } from "./db.ts";
import { ensureIndexes } from "./memories.ts";

// 127.0.0.1, not 0.0.0.0. In development the phone arrives through adb reverse,
// which connects from this machine, so nothing on the local network needs to
// reach the server -- and it holds personal data. A host that must listen
// publicly sets HOST.
const host = process.env.HOST ?? "127.0.0.1";
const port = Number(process.env.PORT ?? 3000);

// Before listening: a bad URI or a blocked IP should say so at startup, not on
// the first save. The client reconnects by itself after this. The server
// workstream adds the Gemini key check and the search indexes here.
try {
  if (!process.env.GEMINI_API_KEY) {
    throw new Error("GEMINI_API_KEY is not set in the environment.");
  }
  await connectMongo();
  await ensureIndexes(db());
  console.log(`mongodb connected, database "${databaseName()}"`);
} catch (error) {
  console.error(`could not reach MongoDB or start up: ${describeMongoError(error)}`);
  process.exit(1);
}

createApp({ database: db() }).listen(port, host, (error) => {
  if (error) {
    console.error(
      (error as NodeJS.ErrnoException).code === "EADDRINUSE"
        ? `port ${port} is already in use -- another dev server? Stop it, or set PORT (and adb reverse to match).`
        : error,
    );
    process.exit(1);
  }
  console.log(`breadcrumb server on http://${host}:${port}`);
});
