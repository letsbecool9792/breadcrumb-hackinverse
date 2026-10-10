# Workstream: Reading on the phone

**Owner:** Dhruba · **Android, Kotlin** · needs no server

Everything the phone reads out of what was saved, on the device and offline: the words in a
screenshot, a PDF's text, a link's page. Then local word search over all of it, and the bulk
importer that brings in what is already on the phone. A saved screenshot should be searchable by
its words seconds after it is saved, before the network has done anything.

## What you own

- `…/breadcrumb/ocr/`: the OCR queue, the ML Kit reader, PDF reading
- `…/breadcrumb/net/PageReader.kt` and the link-reading step (see R4)
- `…/breadcrumb/imports/`: the bulk importer
- `…/breadcrumb/data/`: your own DAOs (a `ReadingDao` for your queries, an `ImportDao` for
  `imported_sources`) and `FtsQuery`, registered in `BreadcrumbDatabase`
- The line in `BreadcrumbApp.onCreate` that starts the OCR queue
- Your tests, JVM and on the phone

## What you use, and what you hand on

- **You read** rows whose `extractedText` is **null**. **You write** `extractedText`: the text
  found, or **empty** when the thing was read and holds no text. Null means "not read yet", and
  the queues find their work by it, so never write empty to mean anything else. Resetting a row
  to null queues it to be read again.
- **Write through `@Upsert` or a targeted `UPDATE`**, never `@Insert(onConflict = REPLACE)`: the
  word index is kept in step by triggers, and REPLACE deletes without firing them.
- **You hand on** to the sync workstream:
  - a way to tell it there is something new to send. After every read, including one that
    found nothing, call its schedule function (`UploadWorker.schedule(context)`; until it
    exists, a no-op callback). An empty read is exactly the case whose picture gets sent.
  - `readingOriginals: MutableStateFlow<Boolean>` on `BreadcrumbApp`: true while the OCR queue
    is working through unread pictures and PDFs, so the uploader holds them back rather than
    send them unread. **Presume true until the queue has looked**, and set false in a `finally`
    when the queue stops or never starts: a stuck true strands memories off the server.
  - the link-reading step (R4), which the upload queue runs first on each pass.
  - local word search (R2), which the search screens use for instant results.

## Steps

One PR each, checked on a phone. The debug list shows each row's reading state ("not read",
"read · N chars", "no text found").

- [x] **R1. OCR of saved pictures**
  - ML Kit Text Recognition with the **bundled** model (`com.google.mlkit:text-recognition`,
    already a dependency), not `play-services-mlkit-text-recognition`, which downloads its model
    on first use and so is not offline from the first save.
  - Downsample big images first, and honour EXIF rotation.
  - The queue starts in `BreadcrumbApp.onCreate`, in `applicationScope`, in **every** process,
    capture included: a share is usually a cold start, and its picture should be read in that
    same process. It watches Room, so any code that inserts an IMAGE row gets it read without
    calling anything. Newest first. A picture that cannot be read is marked read and empty, so
    it never blocks the rest. A memory deleted mid-read stays deleted: write only if the row
    still exists.
  - *Check:* share a text-heavy screenshot → "read · N lines/chars", its words visible on tap; a
    photo with no text → "no text found"; pictures saved before installing the build are read on
    the next launch.
- [x] **R2. Local word search**
  - Search `memories_fts` with `MATCH`. **Input always goes through `FtsQuery.matchExpression`,
    never straight into MATCH:** raw input containing `"`, `-`, `OR` or `column:` is a syntax
    error or means something else. Every word becomes a quoted prefix term, and all must match:
    `qualcomm intern` → `"qualcomm*" "intern*"`, so "intern" finds "internship" while it is
    still being typed. Split words as unicode61 does: letters and digits make a word, anything
    else separates.
  - FTS4 has no ranking function, so results are newest first. A `Flow` for live search and a
    one-shot `suspend` version, with a limit.
  - Add a search field to the debug list so it can be checked.
  - *Check:* a word that appears only inside a screenshot finds it; case and accents fold
    ("cafe" finds "Café"); typing `"`, `-` or `OR` never breaks the search.
- [ ] **R3. PDFs**
  - The text layer on Android 15+ (`PdfRenderer.Page.getTextContents`, API 35); OCR of the
    first six pages when there is no text layer (a scan) or on older Android. Keep up to 20,000
    characters. A thin text layer is read by OCR too, and the OCR kept when it found clearly
    more: a one-line PDF is easily mistaken for a scan. A PDF behind a password reads as empty.
  - *Check:* a PDF with text → read; a scanned PDF → read by OCR.
- [ ] **R4. Links' pages**
  - Read on the phone, never the server: the person's own connection, as when they open it, and
    the server never fetches arbitrary URLs. Only the `<head>`, up to `</head>` or 512 KB. Plain
    http goes as https; never localhost or a bare IP.
  - Keep the page's title (only when the memory has none: a Chrome share brings its own) and
    its description, in `extractedText`.
  - Every answer is final (an error page, a login wall's title, a dead link while online)
    **except losing the network**, which leaves the link for the next pass.
  - Expose it as one step the upload queue runs first on each pass (`LinkReading`), four pages
    at a time.
  - *Check:* a bare link copied from a chat gets its page's title and description.
- [x] **R5. Bring in what is already on the phone** (needed to seed the demo)
  - Through pickers only, so no storage permission: a whole folder
    (`ACTION_OPEN_DOCUMENT_TREE`, for `Pictures/Screenshots` or `DCIM`); only some pictures (the
    photo picker); PDFs and a bookmarks export (the file picker). The Download folder cannot be
    picked as a folder on Android 11+, which is why PDFs come as files.
  - A bookmarks export (Netscape HTML, which every browser writes) becomes one memory per
    bookmark: dated by `ADD_DATE`, titled as the browser had it, `sourceAppLabel = "Bookmarks"`,
    so "that link I bookmarked" is a source filter like any app.
  - It writes rows and nothing else: R1, R3 and R4 read them. Dated by the file
    (`contentCreatedAt`: EXIF, else the name, else the provider), saved as of now
    (`capturedAt`), oldest content first.
  - **Never twice:** by name and size before the copy (`imported_sources`), then by SHA-256
    against every original already stored, which also catches a screenshot that was shared by
    hand. A memory deleted after an import stays deleted on the next import.
  - Take the folder's grant persistably and release it at the end (Android caps an app at 512).
    The import runs in `applicationScope`, so leaving the screen does not stop it; show how far
    it has got.
  - While OCR works through a big import, `readingOriginals` stays true, so the uploader holds
    the unread pictures back.
  - The debug list's Clear forgets `imported_sources` too: otherwise an import after a Clear
    skips everything as already seen and brings nothing back.
  - *Check:* import a screenshots folder → rows appear, get read, and (once sync exists) sync;
    import it again → nothing new; delete one and import again → it does not come back;
    Clear, then import again → everything comes back.

## Rules and pitfalls

- **OCR is Latin script only** with the bundled model. Say so; do not pretend otherwise.
- The bundled model's native library adds about 11 MB per ABI to the APK. Accepted.
- ML Kit sends Google SDK usage and performance data (not images or recognised text). Accepted:
  originals and their text still stay on the device.
- **A grant that arrives with a picked file dies with the activity.** Read files picked one by
  one while the screen lives; only a folder grant can be kept.
- **Running the instrumented tests migrates and writes the real database** (the test APK runs in
  the app's process). Use in-memory Room databases in tests, and back up the phone's database
  before installing a schema bump (CLAUDE.md, "Android").
- **Never change the schema in place.** Your tables already exist. If you need a column, it is a
  version bump with an AutoMigration (see `BreadcrumbDatabase`); talk to Suparno first.

## Out of scope

The server's reading of what you send (server workstream), uploading (sync), voice notes, scripts
other than Latin.
