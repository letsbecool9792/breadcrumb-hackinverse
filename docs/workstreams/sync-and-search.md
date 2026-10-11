# Workstream: Sync, search screens, integration

**Owner:** Suparno · **Android, Kotlin** · talks to the server

The phone's half of the cloud (the upload queue and the server client), the screens people
actually use (home, search, the detail of a memory), and stitching the four workstreams into one
app. Also: reviewing everyone's PRs on the phone.

**The screens build to [`docs/design.md`](../design.md)**, U3 to U6 and capture's sheet alike:
every size, colour, word and motion is written there. Where this brief and the design differ,
the design wins.

## What you own

- `…/breadcrumb/net/ServerClient.kt` and the Kotlin data classes matching
  [`docs/api.md`](../api.md)
- `…/breadcrumb/sync/`: the upload queue (WorkManager)
- `…/breadcrumb/ui/home/`: the screens (home, search, the detail, `SheetLayer`, the undo bar),
  stitched from pieces others build. The type, glyphs and thumbnails come from reading's R6;
  opening and sharing from R7; bringing in from R8. The text rules, the search client and
  two-stage search come from S8 to S10. The "+" composer comes from capture's C7 and C9.
- `MainActivity`, and your own DAOs (`UploadDao`, `SearchDao`)

## What you use

- Capture's rows, and its `uploadHolds` (ids whose capture sheet is still open)
- Reading's `readingOriginals` flag, its link-reading step, and its local word search
- The server's five endpoints (stubs answering 501 until the server workstream builds them)

## Steps

- [x] **U1. The server client, and seeing it from the phone**
  - OkHttp (5.4.0; 5.5 needs compileSdk 37) and kotlinx.serialization. `GET /health` and check
    `service == "breadcrumb"`.
  - A status line on the debug list that tells the four states apart, and rechecks on tap:
    reachable; *connection refused* (no `adb reverse`); *connection closed without a reply*
    (the forward is set but the server is not running); another server on the port.
  - Tests with `mockwebserver3`: reachable, another server, refused, accepted-then-closed,
    timeout.
  - **The upload calls' read timeout is at least 90 s.** The server holds a paced request up to
    a minute while it waits for a free model slot; OkHttp's 10 s default turns every held
    request into a timeout and a backoff. `/health` keeps a short one.
  - Built as `net/ServerClient` (`BreadcrumbApp.server`): `/health` waits 5 s and never retries,
    so a check says what happened; uploads use `uploadClient`, read 100 s, call 130 s. Two more
    states beside the four: *no answer in time*, and *unreachable* with its reason (a cleartext
    refusal, say).
  - Failures are told apart by exception type, never by message: a connection closed under the
    request reads "Connection reset" or "Broken pipe" on the phone and "An established
    connection was aborted" on Windows.
  - *Check:* the line says *reachable* with `npm run dev` and `npm run reverse`; *connection
    closed without a reply* with the server stopped; *connection refused* after
    `adb reverse --remove-all`. A tap checks again.
- [x] **U2. The upload queue**
  - Unique WorkManager work, with a network constraint, scheduled after every save and every
    read (`UploadWorker.schedule(context)`, which capture and reading call).
  - `BreadcrumbApp.onCreate` sets `uploadScheduler` (capture's and reading's hook, a no-op until
    then) to `UploadWorker.schedule` **before the OCR queue starts**: a read landing first would
    call the no-op, and nothing would be scheduled. Uploads use the 90 s read timeout (U1).
  - Each pass: pending deletes first; then reading's link step; then memories, **oldest first,
    ten per request**; then pictures; then enrichment.
  - States: PENDING → UPLOADING → SYNCED. A 503 or a network failure goes back to PENDING (with
    backoff); a 400 goes to FAILED and is **not** retried (the same bytes would be refused
    again; a debug button queues refused ones again). An UPLOADING row left by a killed process
    goes back to PENDING at the start of a pass.
  - **Any write that changes what the server reads puts the memory back in the queue**
    (PENDING): reading's `updateExtractedText` when the text is non-empty, capture's `setNote`
    when the note changed. So **marking synced only moves UPLOADING to SYNCED**
    (`… WHERE syncState = 'UPLOADING'`): a read or a note that lands mid-upload set the row
    back to PENDING, and stays PENDING for the next pass instead of being marked sent.
  - Held back for now: ids in `uploadHolds`; unread pictures and PDFs while `readingOriginals`
    is true. A 10-minute grace covers anything a finished OCR queue left unread (a read that
    failed).
  - **The payload never carries `localUri`** (a test named for it).
  - Pictures: only images whose OCR read under 80 characters, only once (`imageSentAt`), only
    after their memory is on the server, downscaled to a 1,500 px long side as JPEG, four per
    request. A text-heavy screenshot is left to its words.
  - Enrichment: for SYNCED rows whose `enrichedAt` is null, ask in batches of 50 and copy
    `summary`, `kind`, `readText` back. Mark every id asked, answered or not, so the pass cannot
    loop. Sending a memory (or its picture) clears `enrichedAt`, since the server may have read
    it again.
  - *Check:* save in airplane mode → it stays PENDING; reconnect → it syncs, nothing duplicated;
    kill the app mid-upload → it recovers on the next pass.
  - Built as `sync/UploadPass` (one pass, over `UploadDao` and `UploadApi`, tested on the phone
    with a fake server) and `sync/UploadWorker` (unique work `upload`, `KEEP`, backoff
    exponential from 30 s).
    - A 400 that names memories (`invalid[].index`) fails only those; the rest go again.
    - A 503 still marks the memories it finished.
    - A pass scheduled again while it ran runs once more, so a save that lands after a step has
      looked still goes.
    - Held-back unread originals get an `upload-recheck` job for when their grace ends, since a
      failed read schedules nothing.
    - `onCreate` also schedules once, for whatever a killed process left.
  - **A run in backoff stays in backoff:** a save while the server is down does not cut it short.
    After starting the server or running `adb reverse`, the debug list's **sync now** runs the
    queue at once; **retry refused** puts FAILED memories back.
  - WorkManager stops a run after 10 minutes; the next one picks up where it stopped (UPLOADING
    rows go back to PENDING). A large import syncs across several runs.
  - The link step (reading R4) runs after deletes and before memories, so a link goes once, with
    its page's words.
- [ ] **U3. Home: the mosaic**
  - Design §4 to §7: `HomeScreen`, the masthead and slim strip, the mosaic with its entrances,
    the date scrubber, and the empty mosaic. `MainActivity` shows it, and the view model
    schedules an upload once at start. JVM tests: `mastheadCollapse` and `MosaicEntrances`.
    Builds on R6, S8, and S9's unsent count.
  - The app's face: every memory, newest first, as a grid of tiles. Picture tiles are all
    picture, captioned over a shade; notes sit under a small amber mark. "breadcrumb" large in
    the serif at the head, handing over to a slim strip as it scrolls away. Tapping the slim
    strip goes back to the top.
  - A drag on the mosaic puts the keyboard away.
  - An empty mosaic says what to do: share something to Breadcrumb, tap the tile, or bring in
    what is already on the phone.
  - The debug list moves behind a long press on the wordmark, **in debug builds only**: pass the
    callback only when `BuildConfig.DEBUG`, and wire no long press at all when it is null.
- [ ] **U4. Search**
  - Design §8 and §9: the field and its light, the counter, the list and its rows, the lit word,
    the empty states, and the haptic. Builds on S8 to S10.
  - One search field. While typing, reading's local word search answers at once; after a 450 ms
    pause the server's ranking replaces it, rows gliding to their ranked places.
  - Server order is kept; ids the phone does not hold are dropped (the server can hold memories
    deleted before their delete synced).
  - The server out of reach → the word matches stand, marked "offline · words only".
  - A result row: title (sans), a fragment with the matched word lit, chips, age, source app.
  - A drag on the results puts the keyboard away, as on the mosaic.
- [ ] **U5. The detail of a memory, and the way back to the original**
  - Design §10 to §12: `SheetLayer`, the detail, and delete with Undo and the undo bar.
    Opening and sharing come from R7.
  - A sheet: the artifact (the picture, the PDF's first page, a link card), where it came from
    and when (saved, from, taken), the text found, what the model saw, the person's note
    (editable), and **one** main action: open original, open link, or copy text.
  - Originals are app-private (architecture rule 1), so another app can only open one through a
    `FileProvider` content URI with a one-off read grant (`res/xml/originals_paths.xml` exposing
    `filesDir/originals/` and nothing else; authority `${applicationId}.originals`).
  - A status line in the detail while there is one: still reading (OCR, or a link's page, not
    done yet); page not read (the link's page could not be read); not sent yet, found by
    words only (PENDING, so the server has not read it); refused (FAILED: the server refused
    it with a 400).
  - Share onward: the file (through the originals `FileProvider`), the URL, or a note's words,
    with the person's note alongside; Breadcrumb left out of the chooser
    (`Intent.EXTRA_EXCLUDE_COMPONENTS`), so a memory is not shared back into itself.
  - Delete with five seconds of Undo instead of a confirmation (`deleteEverywhere`, `restore`).
  - *Check:* a screenshot opens in the gallery, a PDF in a PDF viewer, a link in the browser,
    a note's text copies.
- [ ] **U6. The "+" and the polish** (nice to have, in this order)
  - Design §13, §14 and §18: the "+" sheet, hosting C9's composer and R8's import panel; the
    import bar; the shortcut's request count in `MainActivity`; and the haptics.
  - An amber "+" beside the search field opens a sheet for keeping something from inside the
    app: one box to write in, and a photo (system photo picker, no permission) or a PDF to go
    with it. Text alone is the memory; with files, each file is a memory and the text is their
    note. A draft survives the sheet being dismissed and the app closing, picked files
    included: each file is held by a persistable read grant (`takePersistableUriPermission`),
    released once the file is kept or removed from the draft, since an app may hold only 512.
    A launcher shortcut, "Keep something", opens it from the app icon.
  - The import's way in (reading's R5) lives in this sheet, as a quiet line.
  - Motion: the mosaic rising in on launch, results staggering in, a new save dropping in, the
    picture travelling from its tile into the detail and back. Haptics on results landing and on
    a delete.

## Rules and pitfalls

- **Search UI is search-bar-first.** No dashboard, no feed, no folder tree. Never a chatbot: a
  ranked list that opens the original, with at most one line of why it matched.
- **Dark always** (`BreadcrumbTheme(darkTheme = true)`): the app is a frame around other
  people's screenshots, and a loud ground competes with them.
- **A Text given a style takes nothing from the theme**, so every style names its face: use
  `Serif`, `Sans` and `Mono` from `ui/theme/Type.kt`, never a bare `TextStyle(fontSize = …)`.
- **Serif or sans is decided by where a title sits, never by the kind of memory.** Among others
  (tiles, result rows, the capture sheet) a title is sans; opened in its detail it is serif. The
  serif is otherwise only the app's voice: the wordmark, "Saved", empty states.
- **Use your own sheet, not Material's `ModalBottomSheet`,** if a picture is to travel from its
  tile into the sheet: a shared-element transition only works within one composition, and
  Material's sheet lives in a window of its own. Your sheet then has to give back what
  Material's gave: a height limit (84% of the window), a scrim that closes it, predictive Back,
  standing on the keyboard, drag-to-dismiss handed off from the content's scroll.
- **Animations that loop or follow a gesture run only while shown, and are read while drawing**
  (`graphicsLayer`, `drawBehind`, `Canvas`), not in composition: a transition left running at
  rest redraws every frame, and reading one in composition recomposes every frame.
- **Haptics follow the phone's Touch feedback setting** (`performHapticFeedback` drops them when
  it is off). Felt nothing? Check `adb shell settings get system haptic_feedback_enabled` before
  the code.
- **A launcher shortcut names the applicationId** (`com.lbc.breadcrumb.hackinverse`) in
  `res/xml/shortcuts.xml` as its `targetPackage`. An `applicationIdSuffix` on a build type
  breaks it for that build.
- **Cleartext HTTP is blocked by default.** The debug build's network security config permits it
  to `localhost` only; release gets none.
