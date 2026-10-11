# Workstream: Server, ingest and search

**Owner:** Ayushi · **Node + Express + TypeScript** · MongoDB Atlas and Gemini

The cloud half: reading what the phone sends with one Gemini call per batch, embedding it, and
answering a search phrase with a ranked list that mixes meaning and exact words. The contract is
[`docs/api.md`](../api.md) and [`server/src/api.ts`](../../server/src/api.ts); the routes exist
as stubs answering 501.

## Setup (once)

- Your own **free MongoDB Atlas M0** cluster (no card): a database user with
  `readWriteAnyDatabase` (enough to read, write and create indexes, and deliberately not enough
  to drop a database), and Network Access allowing your IP.
- Your own **free Gemini API key** from AI Studio (no card).
- `cd server`, `npm install`, copy `.env.example` to `.env` and fill it in, `npm run dev`.
  `npm test` and `npm run typecheck` before every PR.

An M0 cluster holds only **three search indexes**, which is why everyone who runs search needs
their own cluster.

## What you own

`server/src/`, all of it, apart from the `/health` contract and `db.ts`, which are shared.
On the phone (S8 to S10): `ui/home/ResultText.kt`, `SearchJoin.kt`, `TwoStageSearch.kt`,
`net/Search.kt`, and `ServerClient.search`.

## Steps

One PR each. Test from the terminal with curl or a script; the phone joins once the sync
workstream sends.

- [x] **S1. Ingest text memories**: `POST /memories`
  - Parse field by field (`parseMemory`); unknown fields dropped, so `localUri` can never be
    stored whatever the client sends. Name a test for that.
  - **Store every memory, even when the model call fails**, with the reason in
    `enrichmentError`: the phone has already said "Saved", and a document that is merely
    unenriched can be fixed later, while a missing one is a memory that quietly never synced.
  - **One Gemini call per batch** (ten memories): structured JSON per memory, with `summary`
    (one line, in the words someone would use looking for it), `kind`, `entities`, `dates`. The
    model is **Gemini 3.5 Flash Lite**, pinned by version: `gemini-flash-latest` and other
    aliases are refused, because behaviour shifts under you.
  - The model reads a memory as of its `datedAt` (an imported screenshot taken two years ago:
    "next Friday" belongs to that week).
  - **Never pay twice:** keep a fingerprint of the text the enrichment was made from, so a
    re-send with unchanged text costs nothing. No text, no call: stored without a model call.
  - **The note is never given to the model to describe.** It is embedded and word-indexed, so
    writing a note never costs a model call.
  - **Pace, don't be refused:** one rolling-minute gate (`GEMINI_INGEST_PER_MINUTE`, 15) shared
    by the text and picture calls, since the allowance is per model. A request waits up to a
    minute for a slot; longer than that and it answers as rate-limited, which the phone retries
    (the phone's own timeout is 90 s).
  - **503, never 200, when a model call may succeed later** (rate limit, overload): the phone
    re-sending *is* the retry. A 200 would mark it synced, and nothing would ever enrich it.
  - Derive `datedAt` (taken date, else saved date) and `linkSites` (see S6).
  - Inject the model functions into `createApp`, so tests run every endpoint with a stub model;
    plus one live test proving the pinned model, the schema and the SDK agree.
  - *Check:* curl a link → stored with a summary; curl it again unchanged → no model call.
- [x] **S2. Embeddings**
  - `gemini-embedding-001` at **768 dimensions**. It returns unit-length vectors only at its full
    3,072, so **L2-normalise at 768** before storing, or cosine similarity measures length as
    much as meaning. Same for search phrases.
  - Memories are embedded as `RETRIEVAL_DOCUMENT`, search phrases as `RETRIEVAL_QUERY`: one task
    type for both quietly costs retrieval quality.
  - Embedded text: the note near the front, then title, summary and the text (the first 6,000
    characters); the model reads only the first 4,000.
  - Re-embed only when that text changes (`embeddedWith`).
  - *Check (live test):* two related texts score closer than two unrelated ones, by more than 0.1.
- [x] **S3. Pictures**: `POST /memories/images`
  - Base64 in the body, **never written anywhere**, not even a temp file. Only for memories
    already held. The same picture is never read twice (a fingerprint of its bytes). A picture
    gets the memory's note as context.
  - The model's reading goes into `enrichment.readText`, and the memory is re-embedded.
- [x] **S4. The search indexes**, created at startup
  - `memories_vector`: 768 dims, cosine, with filter fields `type`, `hasLink`, `capturedAt`,
    `sourceAppLabel`, `datedAt`, `linkSites`, `_id`. **A vector index's filter fields must be
    declared up front;** adding one means rebuilding it.
  - `memories_text`: `lucene.english` (stop words dropped, stemmed), over title, note, summary,
    rawText, extractedText and readText; the same filter fields, with the source app a token,
    never text.
  - Startup creates a missing index and updates one only when it lacks a declared field (Atlas
    reports definitions back with its own defaults, so comparing whole definitions would rebuild
    at every start), and says so out loud. **A missing index is not an error:** `$vectorSearch`
    or `$search` against it answers an empty list, and hybrid search silently becomes
    one-half search. A new index takes about 30 s to become searchable.
  - If index creation is refused for the user's role, create it in the Atlas UI rather than
    widening the role for good.
- [x] **S5. Hybrid search**: `GET /search` (architecture rule 5)
  - `$vectorSearch` and `$search` run in parallel, and the server fuses them by reciprocal rank
    (`fuse` in search.ts): equal weights, k = 60, the constant `$rankFusion` uses, not the
    `$rankFusion` stage. It returns each half's rank directly, and passes against the cluster.
    Each half offers the fusion more candidates than the final list (twice the limit, 20 to
    100), so a memory ranked modestly by both can rise above one ranked first by only one.
  - Revisiting it: `$rankFusion` would save one round trip, and with `scoreDetails: true` each
    result's `{$meta: "scoreDetails"}` lists every input pipeline's rank
    (`details[].inputPipelineName`, `details[].rank`), so the per-half ranks survive a switch.
  - Exact nearest neighbours (no `numCandidates`) under about 10,000 documents.
  - Results are an **allow-list projection**: the text goes back whole, the vector never leaves.
    A field added to the document stays in the cloud until it is listed. Each result carries
    `ranks: { vector, text }`.
  - *Check:* "Qualcomm" puts the memory containing that word first, where a pure vector search
    would not; a phrase sharing no words with a memory still finds it by meaning.
- [x] **S6. Parse the phrase before searching it** (architecture rule 6)
  - **Gemini 3.1 Flash Lite** (a different model from ingest, so the two never share one daily
    allowance) turns the phrase into `{ query, types, from, to, sourceApp }`.
  - Guards: made-up types, impossible dates and unknown sources are dropped. `sourceApp` is an
    enum of the source labels memories carry plus the 40 most linked-to site names, so the model
    cannot invent one.
  - A phrase's parse is reused for the day (same phrase, same sources, same day). A failed parse
    never fails the search: it costs the filters, and the raw phrase is searched.
  - **A filter must narrow both halves:** MQL in `$vectorSearch.filter`, a compound `filter` in
    `$search`. Narrow only one, and the other brings back what was filtered out.
  - **A link filter matches `type = LINK OR hasLink`**: filtering on type alone misses every
    captioned photo and PDF.
  - Dates filter on `datedAt`, by whole days on the server's calendar.
  - An all-filter phrase ("screenshots from April") lists matches newest first, with no
    embedding. Embed the raw phrase in parallel with the parse, and reuse it when the parse left
    the words unchanged: a new phrase otherwise waits for both calls in turn.
  - **A source is an app or a site:** "that link from Instagram" matches a memory shared from the
    Instagram app *or* one whose link points to instagram.com, however it arrived. `linkSites`
    holds the registrable domains of a memory's links; sites are named as people say them
    (instagram.com and instagr.am are "Instagram", youtu.be "YouTube", twitter.com "X"),
    anything unlisted by its first label.
  - *Check:* "screenshot from April" filters by both type and month; "that link from WhatsApp"
    also finds photos whose caption carried a link; "ig link" finds an Instagram link that was
    sent on WhatsApp.
- [x] **S7. Keeping the phone in step**: `POST /memories/enrichment` and `POST /memories/delete`,
  as in the contract. A delete removes the text, the enrichment and the vector; idempotent.

### On the phone: the search's logic (Kotlin, tested on the JVM)

The server is done. These steps are the phone's half of search, with no screens: pure Kotlin
tested on the JVM, so they need no phone (Suparno runs anything that must run on one). Set up
the Android build once (CLAUDE.md, "Setting up a machine").

- [x] **S8. The text rules** (design §17; branch `ui/result-text`). **Do this first: home (U3)
  uses them.**
  - `ui/home/ResultText.kt`: `object ResultText` with every rule of §17 (words, fragment,
    title, firstLine, opening, host, age, tileMeta, rowMeta, status, kept, counter, dates,
    saved, taken, monthOf). `fragment` returns the lead-in, the hit and the tail separately, so
    the screen can light the hit.
  - `counter` needs two types the next steps share, so make them here:
    - `Interpretation(query, types, from, to, sourceApp)` in `net/Search.kt`, as docs/api.md
      has it;
    - `enum class SearchStatus { RANKING, RANKED, OFFLINE, UNAVAILABLE }` in `ui/home/`.
  - Tests: `ResultTextTest`, covering every rule and its edges; §17's examples are test cases.
- [x] **S9. The search client, and the queries the screens need** (branch `sync/search-client`)
  - `ServerClient.search(query, limit = 30): SearchOutcome`: GET /search on its own OkHttp
    client from the same base, with a 20 s call timeout. It returns `Found(hits, interpretation)`
    or `Unavailable(reason, offline)`:
    - offline when it could not reach the server (IOException);
    - not offline when the server answered without results (any non-2xx, or a body that would
      not parse).
  - `SearchHit` and `Ranks` match docs/api.md, with `ignoreUnknownKeys`.
  - `UploadDao.observeUnsentCount(): Flow<Int>`: memories not yet SYNCED.
  - The detail's note: `CaptureDao.setNote(listOf(id), note)` already takes null (clearing the
    note), changes the row only when the note differs, re-queues it, and returns the rows
    changed. Add the on-phone test that null clears it; add a by-id wrapper only if it reads
    better.
  - `ui/home/SearchJoin.kt`: `joinHits(hits, held: List<Memory>): List<SearchResult>`, with
    `SearchResult(memory, hit: SearchHit?)`. It keeps the server's order with the phone's rows,
    and drops a hit the phone does not hold.
  - Tests:
    - `ServerClientTest`: found; interpretation null; a non-2xx is unavailable, not offline; a
      refused connection is offline.
    - `SearchJoinTest`: the order is kept, and unknown ids are dropped.
- [x] **S10. Two answers in turn** (design §9's logic; branch `sync/two-stage-search`)
  - `ui/home/TwoStageSearch.kt` turns a `Flow<String>` of what is typed into a
    `Flow<SearchState>`: `Resting`, or `Searching(phrase, results, interpretation, status)`.
  - It runs on every change of the normalised query (trimmed, with runs of whitespace as one
    space), using `collectLatest`:
    - an empty query gives Resting;
    - the phone's word search answers at once (RANKING);
    - after a 450 ms pause, the server answers. Found gives the joined hits, the
      interpretation, and RANKED. Unavailable leaves the word matches standing, as OFFLINE or
      UNAVAILABLE.
  - Word search, the server and the held rows come in as functions, so the tests can fake them.
  - Tests: `TwoStageSearchTest`, on virtual time (`kotlinx-coroutines-test`, added as a test
    dependency with a comment saying why):
    - each state above;
    - a keystroke inside the pause abandons the server call;
    - the same phrase with different spacing starts nothing new.

## Trying a search from the terminal

`npm run search -- <phrase> [--limit N]` asks the running server (`npm run dev`) and prints how the
phrase was taken apart (words, types, dates, source) and each result with its summary and where it
placed by meaning and by words. It speaks HTTP to the server and opens no database of its own, so
it costs one parse and one embedding on the free tier, like any search. The printing is
`src/search_format.ts`, tested on its own.

## Testing rules

- **`node --test` runs each test file in its own process, in parallel.** Two suites sharing a
  database wipe each other's documents mid-test, and the failures look like impossible counts.
  Give every test file its own database: `<MONGODB_DB>_test_<suite>`. Tests drop their own
  collection, never a database.
- Tests against Atlas skip themselves when `MONGODB_URI` is unset.
- **Close the Mongo client in a `finally`** in any hook: a teardown that throws with the client
  open hangs the whole run instead of reporting the failure that caused it.
- **The search tests borrow the real indexes**, since a test pair plus the real pair would be
  four, over M0's three. Every test document has a fixed id starting `breadcrumb-test-`, every
  search in the tests is narrowed to those ids **in both halves**, and cleanup deletes those ids
  and nothing else.
- Every test memory is found by every test phrase: a word slipped into a seeded memory, even in a
  URL, ties another test's ranking. Give seeded URLs id-like paths.
- **Live model tests skip themselves when Gemini is overloaded** (503/429 after retries). A skip
  is not a pass: read the run's skip lines.

## Rules and pitfalls

- **Flash Lite refuses `thinkingConfig`:** never send one, no thinking budget and no level, to
  the Flash Lite models; the request is refused outright.
- **No build step.** Node 22.18+ runs `.ts` itself by stripping types; `tsc` only checks. So
  `erasableSyntaxOnly` is on: no enums, namespaces or parameter properties, and relative imports
  carry the `.ts` extension.
- **Secrets live in `server/.env`**, loaded by Node's own `--env-file-if-exists`, no dotenv.
- A free M0 cluster **pauses itself after 60 days idle**; resume it from the Atlas UI.
- **Never log a search's query string**, or any memory's text.
- The free tier, per model per project: Gemini 3.5 and 3.1 Flash Lite 15 a minute and 500 a day
  each; embeddings 100 a minute and 1,000 a day. Daily limits reset at midnight Pacific; check
  them on the key's project quota page in the Cloud Console, filtered on "free tier".
- **Spend calls per batch, never per memory:** one model call reads a whole `/memories` batch,
  one reads a whole `/memories/images` request, and one embedding request embeds a batch
  (`embedContent` takes a list). Per memory, a 500-item import would use the whole day.
- **A vector index is its own kind:** `createSearchIndex({ type: "vectorSearch", definition:
  { fields: [{ type: "vector", … }, { type: "filter", path }] } })`. A `knnVector` mapping in a
  plain search index does not serve `$vectorSearch`.
- **Nested fields in a search index are `document` mappings**, never dotted keys:
  `enrichment: { type: "document", fields: { summary, readText } }`.
- **Token fields are filtered with `in` and `equals` in `$search`**, never `text`, which finds
  nothing on a token.
- **Days are the server's, not UTC's** (days.ts): at UTC midnight, 3 a.m. in India is still the
  day before.
- **Validate field by field and answer 400 for a bad request**; a model or database that may
  answer later is a 503, never a 200 (the phone would mark it done) or a 500.
