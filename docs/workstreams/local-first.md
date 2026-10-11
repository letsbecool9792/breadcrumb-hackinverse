# Local first: search on the phone, the server only when asked

The judges asked whether people's saves go to a server and get processed there. Today they do:
each memory's text, and pictures OCR could barely read, go to our server and on to Gemini's free
tier, whose terms say not to send personal information. This workstream moves search onto the
phone. By default nothing leaves the phone but the search phrase, which Gemini still reads so
that vague phrasings keep working.

**Branch:** everything here is built on `local-first`, cut from `main`. Branch from
`origin/local-first`, open the PR against `local-first` (not `main`), and Suparno merges with
"Rebase and merge" as usual. `main` stays clean until the whole thing works on the phone.

**The spike** (branch `spike/on-phone-embeddings`, not for merging) ran EmbeddingGemma 2 on the
demo phone (Dimensity 7200) through LiteRT-LM 0.18.0, on the CPU:

- **Loading:** about 2 s and 210 MB.
- **Speed:** a search phrase embeds in about 0.19 s, a memory in about 0.3 s.
- **Ranking:** 12 vague searches against the phone's 15 real memories and 30 made-up ones each
  ranked the right memory first, with EmbeddingGemma 1's prompts.
- **A picture alone:** about 4.8 s, taking memory to about 1 GB. Its own picture ranked second in
  3 of 4 picture searches.

## How it fits

```
save → OCR, PDF text, link page (as now)
     → the phone embeds the text (and the picture, when OCR found little) and keeps the vector
search → the phone's words at once (as now)
       → the server parses the phrase only: {query, types, from, to, sourceApp}
       → the phone ranks every memory by meaning, fuses with its words, applies the filters
offline → the same, without the filters
the server reads memories only when the person turns it on; what goes is stripped first
```

## Steps

- [ ] **L0. Kotlin 2.4 and LiteRT-LM** (Suparno; branch `local/l0-kotlin`)
  - LiteRT-LM is compiled with Kotlin 2.4, and the project with 2.2.10: move the project to 2.4.
    Check that KSP, Compose and serialization still build, and that every test still passes.
  - Add `com.google.ai.edge.litertlm:litertlm-android:0.18.0`.
- [ ] **L1. Embeddings on the phone** (Suparno; after L0)
  - `Embedder`: one `EmbeddingEngine` for the app, on the CPU, started lazily and closed when idle.
    Documents take `title: none | text: …`, queries take `task: search result | query: …`, and the
    output is normalised. Input is capped at 1,024 tokens, so cut long text (2,000 characters).
  - The model (`embeddinggemma-2-text-vision-440m.litertlm`, 388 MB, Apache 2.0, not gated):
    downloaded once over Wi-Fi into app storage, never in the APK. `adb push` for development.
  - Schema: `embedding` (BLOB) and `embeddedWith` (the model's name) on `memories`, by
    AutoMigration. Back up the phone's database before installing.
  - A WorkManager job embeds what reading has finished, newest first. A picture is embedded as a
    picture only when OCR found under 80 characters, and only while charging.
- [ ] **L2. Ranking on the phone** (Suparno; after L1, with L3's client)
  - Every vector in memory, kept current from Room; a query scores against all of them by dot
    product. No vector database: 5,000 memories is 15 MB.
  - Fuse with the word search by reciprocal rank (k = 60), as the server's `fuse` does, and apply
    the parsed filters. This becomes the second stage of `twoStageSearch`, with the same
    `SearchState`, so the screens don't change.
  - Without the server, the same ranking without filters, and the status says so.
- [ ] **L3. Parsing on its own, and forgetting** (Ayushi; server and client)
  - `POST /parse` with `{ "phrase": "...", "sources": ["WhatsApp", "LinkedIn"] }` returns the
    `Interpretation` and nothing else. It uses the existing parser and its cache for the day,
    with no embedding call and no Atlas query. The phone sends its own source app names, since
    the server may hold none.
  - `POST /memories/forget` deletes everything the server holds; the phone calls it when the
    person turns the server off.
  - `ServerClient.parse(phrase, sources)` and `ServerClient.forgetAll()` on the phone, with the
    same `Reply` handling as the other calls, off the main thread.
  - Document both in `docs/api.md`. Tests: `npm test` for the routes, and `ServerClientTest`
    against MockWebServer.
- [ ] **L4. What leaves the phone** (Aritra)
  - A setting, "Gemini reads my memories", off by default, held in SharedPreferences. While it is
    off, the upload pass sends no memories and no pictures; deletes still go. Turning it off calls
    `forgetAll()` (L3). For now the switch sits in the debug list.
  - When it is on, `Redactor` strips text before it goes. ML Kit Entity Extraction, on the phone,
    finds phone numbers, emails, addresses, card numbers, IBANs and tracking numbers; regexes
    catch OTPs, Aadhaar and PAN numbers, and API keys and long tokens. Each match becomes its type
    (`[phone]`). Dates and URLs stay. The phone's own copy is never changed.
  - Pictures never go, setting or not.
  - Saves from banking, UPI and password-manager apps never go, by package name.
  - Tests: `RedactorTest` (JVM, the regexes, with real-looking samples), and the upload pass
    sending nothing while the setting is off.
- [ ] **L5. A headline and a snippet in place of the summary** (Dhruba)
  - With the server off, there is no summary. `Headline.of(memory)` (pure):
    - a link's page title;
    - a PDF's title, or its first real line;
    - a screenshot's first real line of OCR text, skipping status-bar and app chrome such as
      `12:20`, `KB/S LTE 4G`, battery and `Inbox`;
    - a note's first line.
  - `Snippet.of(memory, words)` (pure): in a result row, the one line around the best match for
    the query's words. It is the row's "why this matched", like a search engine's.
  - Wire both into `ResultText.title` and `RowText`'s why line, where the summary sits now. A
    summary, when there is one, still wins.
  - Tests (JVM): the OCR text of real screenshots on the phone makes good fixtures.

## Rules

- **Never commit the model**, and never put it in the APK.
- **A search phrase still goes to the server**; nothing else does unless the person turns it on.
- **One model for everything.** Vectors from different models can't be compared: a memory
  embedded with another model is embedded again.
- **The rules in CLAUDE.md still hold**, including tests on the phone never clearing the real
  database.
