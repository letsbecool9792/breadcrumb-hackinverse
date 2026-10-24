# Local first: search on the phone, and then mobile only

The judges asked whether people's saves go to a server and get processed there. Today they do:
each memory's text, and pictures OCR could barely read, go to our server and on to Gemini's free
tier, whose terms say not to send personal information. This workstream moves search onto the
phone. Until L6 the search phrase still left it, for Gemini to take apart into filters; L6 does
that on the phone. Until L7 memories could still go when the person turned it on; L7 retired
that, and with it the server: the app is mobile only.

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
search → the phone takes the phrase apart, by rules: {query, types, from, to, sourceApp} (L6)
       → its words at once, narrowed by the filters
       → the phone ranks every memory by meaning, fuses with its words, applies the filters
offline → the same
nothing leaves the phone (L7)
```

## Steps

- [x] **L0. Kotlin 2.4 and LiteRT-LM** (Suparno)
  - LiteRT-LM is compiled with Kotlin 2.4, and the project with 2.2.10: move the project to 2.4.
    Check that KSP, Compose and serialization still build, and that every test still passes.
  - Add `com.google.ai.edge.litertlm:litertlm-android:0.18.0`.
  - **Done:** Kotlin 2.4.21, coroutines 1.11.0 to match what LiteRT-LM brings in. All 275 JVM
    tests pass, and the home screen's and `MemoryDaoTest`'s on the phone.
- [x] **L1. Embeddings on the phone** (Suparno; after L0)
  - `embed/Embedder`: one `EmbeddingEngine` for the app, on the CPU, started on first use and
    closed after two idle minutes, or after a pass over pictures. Documents take
    `title: none | text: …` and queries `task: search result | query: …` (`EmbedText`), and the
    output is normalised. Input is capped at 1,024 tokens, so text is cut at 2,000 characters.
  - `embed/ModelStore`: the model (`embeddinggemma-2-text-vision-440m.litertlm`, 388 MB,
    Apache 2.0, not gated) is downloaded once on an unmetered network into app storage, pinned to
    one revision and checked against its SHA-256; never in the APK. During development,
    `adb push` it to `/sdcard/Android/data/com.lbc.breadcrumb/files/`.
  - Schema version 2: an `embeddings` table of its own (`memoryId`, `key`, `vector`,
    `embeddedAt`), deleted with its memory, so the memories' own reads don't carry the floats.
    The key names the model and a hash of what was embedded, so a finished read or a changed
    note is embedded again.
  - `embed/EmbedWorker` runs `EmbedPass` wherever the upload is scheduled (a save, a read, a
    note, an Undo, a link's page read). A picture OCR could barely read is embedded as a picture
    on the charger; meanwhile its few words stand in.
  - Tests: `EmbedTextTest` and `EmbedPassTest` (JVM). `EmbedOnPhoneTest` uses the real model and
    an in-memory database: a vague phrase finds its memory, and a vector goes with its memory.
- [x] **L2. Ranking on the phone** (Suparno; after L1)
  - `embed/LocalSearch`: the phrase is embedded on the phone and scored against every vector
    (`VectorIndex`, reloaded only when a vector changes; no vector database). Only memories within
    0.12 of the best match count as answers by meaning, so a small corpus doesn't list everything.
    They are fused with the word search by reciprocal rank (k = 60), and the parsed filters apply
    to both halves, as the server's `fuse` and `mqlFilter` do. A link filter takes anything
    carrying a link; dates are the content's own, or when it was saved; a source is the app or a
    link's site.
  - It is the second stage of `twoStageSearch`, with the same `SearchState`, so the screens didn't
    change. Until the model is on the phone, the server still ranks (L6: the words alone).
  - **The parse:** `ServerClient.parse` (L3), sending the phrase with the names it may mean: the
    apps things were shared from and the most-saved sites, named as the server names them
    (`embed/Sites`). A parse that fails, or takes over 2.5 s, costs only the filters. (L6: the
    phone parses, and there is no wait.)
  - Tests: `LocalSearchTest` (JVM): fusion, the meaning window, each filter, a failed or slow
    parse, no model. `EmbedOnPhoneTest` runs the whole search with the real model.
- [x] **L3. Parsing on its own, and forgetting** (Ayushi; server and client)
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
- [x] **L4. What leaves the phone** (Aritra; retired in L7)
  - A setting, "Gemini reads my memories", off by default, held in SharedPreferences. While it is
    off, the upload pass makes no call to the server, deletes included, except one: turning it
    off sets a persisted "forget pending" flag (`SyncSettings.forgetPending`) and marks every
    memory unsent again, and the pass sends `forgetAll()` (L3) first, retrying until the server
    answers 200, before it clears the flag. Turned back on before then, the forget still goes
    first. The sheet says "forgetting…" meanwhile.
  - **The privacy sheet** (`ui/home/PrivacySheet`): "privacy" under the wordmark opens two
    switches (one since L6, which removed the first), "Understand searches in the cloud" (on by default; off, search runs without the
    parsed filters) and "Gemini reads my memories". With both off the app makes no call to the
    server but the forget. "Understand searches in the cloud" sends what is typed and the names
    of the apps and sites the saves came from (`POST /parse`), and the sheet says so.
  - When it is on, `Redactor` strips text before it goes. ML Kit Entity Extraction, on the phone,
    finds phone numbers, emails, addresses, card numbers, IBANs and tracking numbers; regexes
    catch OTPs, Aadhaar and PAN numbers, and API keys and long tokens. Each match becomes its type
    (`[phone]`). Dates stay. The phone's own copy is never changed.
  - **Redaction fails closed** (`Redactor.forUpload`): the entity model not downloaded yet, or
    annotating failing or timing out, means the memory doesn't go this pass; it goes back to
    pending and the pass ends Later. Long text is annotated in pieces of about 2,000 characters,
    each with its own time limit. The model is downloaded, on any network, when the setting is
    turned on.
  - **A link goes as its scheme, host and path** (`Redactor.stripUrl`): no query string, no
    fragment, no `user:password@`, and a long random path segment becomes `[key]`.
  - Pictures never go, setting or not.
  - Links' pages are read inside the upload pass today (`LinkReading`): keep them read with the
    setting off, since search on the phone embeds what they say.
  - Saves from banking, UPI and password-manager apps never go, by package name.
  - Tests: `RedactorTest` (JVM: the regexes with real-looking samples, failing closed, pieces'
    offsets, links), and `UploadPassTest` (on the phone: nothing sent while the setting is off,
    the forget first and until answered, a batch that couldn't be checked held back).
- [x] **L5. A headline and a snippet in place of the summary** (Dhruba)
  - With the server off, there is no summary. `Headline.of(memory)` (pure):
    - a link's page title;
    - a PDF's title, or its first real line;
    - a screenshot's first real line of OCR text, skipping status-bar and app chrome such as
      `12:20`, `KB/S LTE 4G`, battery and `Inbox`;
    - a note's first line.
  - ~~`Snippet.of(memory, words)`~~: dropped. `ResultText.fragment` (design §17) already shows the
    words around the match in a row, and a match by meaning has no words to show.
  - Wired into `ResultText.title`, after the summary, which still wins.
  - Tests (JVM): the OCR text of real screenshots on the phone makes good fixtures.

- [x] **L6. Filters on the phone** (Suparno; after L2)
  - `embed/PhraseRules` (pure) takes the phrase apart, by rules, into the `Interpretation` that
    `POST /parse` gave: kinds from their words (screenshot, pdf, link, note…), days (a month is
    the latest up to today; "last week" is Monday to Sunday; "two weeks ago" that whole week;
    "on the 5th", "since april", "from 2025"), and a source among the names `Sites.sourcesOf`
    offers, with short names (insta, yt, tweet). The query is what's left, without the filler
    ("that", "from", "i saved"), since the word search wants every word of it in a memory.
  - `LocalSearch` takes it with no wait, and **both answers** use it: the words at once
    (`byWords`), narrowed by the filters, and the ranking. A phrase of filters alone
    ("screenshots from last week") lists everything they keep, newest first. Until the model is
    on the phone, the words alone rank, instead of the server's search.
  - "Understand searches in the cloud" goes, with its setting. The app no longer calls
    `POST /parse` or `GET /search`; the server keeps both.
  - **Measured** before building, on the demo phone, against 67 phrases with known answers (42
    the rules were written against, 25 written after): rules 24 of 25 new ones right, in
    microseconds; Gemini through the server 22 of 25, median 3.5 s, and within the search's 2.5 s
    wait for only 18 of 65; Qwen3-0.6B on the phone through LiteRT-LM wrong on nearly all, at
    16–28 s a phrase and 1.5 GB. Gemini's misses hid the right memory ("photos of my cat" as the
    Photos app).
  - **Pitfalls:** "may" is the month only where it can't be the verb ("rent receipt may", "may
    3rd", not "may help"); "april fools" is no date; an app named with an everyday word (Photos,
    Files, X) is the source only as "photos app" or "from photos"; a bare year is words ("2025
    tax return"), a year after "from" or "in" a filter.
  - Tests: `PhraseRulesTest` (JVM, on a fixed Saturday), `LocalSearchTest` (both answers, filters
    alone, no model). `EmbedOnPhoneTest` runs the whole search with the real model.

- [x] **L7. Mobile only** (Suparno; after L6)
  - "Gemini reads my memories" goes, with everything that served it: `UploadPass` and
    `UploadWorker`, `ServerClient`, `Redactor` and the ML Kit entity model it needed,
    `SensitiveApps`, the forget, `SyncSettings`, and the privacy sheet with its "privacy" link.
    The app makes no call to the server; `server/` stays, retired.
  - What the upload pass did for the phone stays: links' pages are read by `ocr/LinkWorker`
    (network required, one at a time, again while saves keep arriving), scheduled with the
    embedding by `workScheduler` after every save and read.
  - Deletes are plain deletes; `pending_deletes` stays in the schema, unwritten. Undo restores the
    row as it was, and capture no longer holds memories back while its note is open.
  - The detail's status says only what is still being read; the count is "17 kept".
  - The search types move to `embed/Search.kt`; `SearchHit` is only what the phone's ranking gives.
    `Pictures` moves to `embed/`.
  - **Before it:** 17 memories uploaded during the hackathon, before L4's switch, were still in
    Atlas. They aren't reachable from the app any more; delete them with the server's
    `POST /memories/forget`, or with the cluster.
  - Tests: the upload pass's, the server client's, redaction's and the payload's went with their
    code; the rest pass (300 JVM, 145 on the phone).

## Rules

- **Never commit the model**, and never put it in the APK.
- **Nothing saved or typed leaves the phone** (L6, L7). The network is for a saved link's page
  and the model's download.
- **One model for everything.** Vectors from different models can't be compared: a memory
  embedded with another model is embedded again.
- **The rules in CLAUDE.md still hold**, including tests on the phone never clearing the real
  database.
