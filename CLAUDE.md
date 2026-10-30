# Breadcrumb — working context

Personal memory retrieval. Save anything in one tap; find it later by describing it in natural
language.

This file is the source of truth for **what we are building, what we decided, who builds what,
and what we are deliberately not building.** Read it before proposing work. Then read:

- [`docs/api.md`](docs/api.md): the phone–server contract
- [`docs/design.md`](docs/design.md): the screens, exactly: sizes, colours, words and motion.
  Every screen, and the capture sheet, builds to it.
- your workstream's brief in [`docs/workstreams/`](docs/workstreams/): what to build, in what
  order, the pitfalls, and what to check on the phone

---

## Where things stand

**Breadcrumb won 1st place at Hackinverse 1.0**
([the event](https://hackinverse-s1.devfolio.co/overview),
[the project](https://devfolio.co/projects/breadcrumb-54b8)). On `main`, each step checked as its
brief says:

- **Capture, C1 to C9.** The share sheet, the selection toolbar, the Quick Settings tile and the
  "+" save in one tap, with the source app and an optional note.
- **Reading, R1 to R8.** OCR of pictures, PDFs' text, links' pages and their preview pictures,
  local word search, and the importer with its way in from the app.
- **The screens**, built to [`docs/design.md`](docs/design.md) (S8 to S10, U3 to U6): the mosaic
  with search under it, a memory's detail that opens its original, and the "+" sheet (the
  privacy sheet went with L7). The debug list sits behind a long press on the wordmark, in debug builds.
- **Local first** ([`docs/workstreams/local-first.md`](docs/workstreams/local-first.md)): the
  app is mobile only. Memories are embedded with EmbeddingGemma 2 and ranked on the phone, a
  search phrase is taken apart into its filters on the phone by rules (L6), and search works in
  airplane mode. Nothing saved or typed leaves the phone (L7): the phone goes online only to
  read a saved link's page and to download the embedding model.
- **The server (S1 to S7, L3) and sync (U1, U2) are retired** (L7): the app makes no call to a
  server. `server/` stays in the repo, never deleted, and still runs and passes its tests; it is
  simply no longer used.
- **Released: v1.0** (2026-10-29), the first signed release, on
  [GitHub Releases](https://github.com/letsbecool9792/breadcrumb-hackinverse/releases/tag/v1.0)
  as `breadcrumb.apk` (68 MB, arm64). Suparno's own phone runs it (see "Android"). The website,
  live at https://breadcrumb.suparno.me (`web/`, on Vercel), downloads it.

**Roadmap**, in this order (set 2026-10-25):

1. **Saved already.** Saving something the phone already holds says so, and offers to add a note
   to the one kept, or to keep it again as a memory of its own. Built as capture C10, checked
   2026-10-30; the "+" composer doesn't check yet.
2. **Voice notes**: recording, playback, audio storage, and making them searchable; and a note
   said over any memory, not only written. Built
   ([`docs/workstreams/voice-notes.md`](docs/workstreams/voice-notes.md)), heard by the phone's
   own speech recognizer offline. Checked 2026-10-30.
3. **A first-run screen for the model.** The 388 MB embedding model downloaded silently, and
   search by meaning waited for it with nothing said. Built as local-first L8: it downloads from
   the first launch, and the first-run screen and a bar show it coming. Checked 2026-10-29 on a
   fresh install of v1.0.
4. **Export and import**: every memory, its originals and vectors, out to one file the person
   keeps, and back in on a new phone. Built
   ([`docs/workstreams/backup.md`](docs/workstreams/backup.md)): a zip from the masthead's
   count, restored from there or from "Bring in"; a restore only adds. Checked 2026-10-29: a
   backup restored into a fresh install.
5. **A website, for getting the app.** Breadcrumb isn't going to the Play Store yet, so a
   landing page in the app's look says what it is and downloads the latest release from it. The
   page is built ([`docs/workstreams/website.md`](docs/workstreams/website.md), in `web/`), and
   its button downloads v1.0 from GitHub Releases. Live since 2026-10-29 at
   https://breadcrumb.suparno.me, on Vercel.
6. **Faster picture embedding on the GPU**, so pictures needn't wait for the charger. Built
   as local-first L9 (2026-10-30): pictures no longer wait for the charger at all, and their
   vision encoder runs on the GPU where it works (about 2 s a picture on the demo phone, against
   4.5 s on the CPU). Waiting on its check by hand (below).
7. **Offer new screenshots automatically**: a `ContentObserver` on MediaStore. Last: it costs
   `READ_MEDIA_IMAGES` and real battery care.

**Later, maybe:** seed 500+ real items and judge search against a fixed list of about 20 real
searches, tuning the 0.12 meaning window and the fusion against it; check with a real password
manager that a clip it marks sensitive is never saved (only the JVM test covers it); OCR of
Devanagari and other scripts (ML Kit has bundled models).

Each brief holds its own checklist; tick a step with the commits that finish it.

---

## Team and workstreams

| Workstream | Owner | Brief | Builds | Needs the server? |
|---|---|---|---|---|
| Capture | Aritra (GitHub AM2004legend) | [capture.md](docs/workstreams/capture.md) | Share sheet, selection toolbar, Quick Settings tile, source app, the capture sheet and its look, the "+" composer and its draft | No |
| Reading on the phone | Dhruba (DhrubaIsha2006) | [reading.md](docs/workstreams/reading.md) | OCR, PDF text, links' pages, local word search, the bulk importer and its way in; the screens' type, glyphs and thumbnails; opening and sharing originals | No |
| Server | Ayushi (aeryxii) | [server.md](docs/workstreams/server.md) | Gemini ingest, embeddings, Atlas indexes, hybrid search, query parsing; on the phone, the search client, two-stage search and the text rules | Is the server |
| Sync, search screens, integration | Suparno (letsbecool9792) | [sync-and-search.md](docs/workstreams/sync-and-search.md) | Upload queue, server client, home, search, the detail and its sheet, stitching the screens together; reviews every PR | Yes |
| Website | Suparno (letsbecool9792) | [website.md](docs/workstreams/website.md) | The landing page in `web/`, its download, and how a release reaches it | No |

The team built V1 for Hackinverse; since 2026-10-24 Suparno alone works on the repo, and the
app is mobile only, so the server column is history.

**How the pieces meet:**

```
capture writes a row (extractedText null), and calls the work scheduler
   → reading fills extractedText (OCR, PDF text, a link's page, read by ocr/LinkWorker)
      → the phone embeds it (EmbeddingGemma 2); search finds it on the phone, by meaning and words
a search: the phrase is taken apart on the phone (embed/PhraseRules), then its words at once and
its own ranking by meaning, both narrowed by the phrase's filters; nothing typed leaves the phone
```

Each workstream can be built and checked alone: capture and reading against the debug list,
search against what the phone holds.

**The few shared points** (each named in the briefs that touch it):

- `BreadcrumbApp`: `applicationScope`, `database`, `originals`, `embedder`; reading adds
  `readingOriginals` and starts the OCR queue in `onCreate`.
- `workScheduler(context)` (`ocr/WorkSchedule.kt`): capture and reading call it after a save or
  a read; it schedules `LinkWorker` (links' pages) and `EmbedWorker`.
- `voice/ListenQueue`, started in `BreadcrumbApp.onCreate`: hears voice notes into
  `extractedText`, as the OCR queue reads pictures.
- `BreadcrumbDatabase`: each workstream registers its own DAO there (one abstract function).
- `AndroidManifest.xml` and `strings.xml`: each adds its own entries, with a comment saying why.
- The schema: **every column is already there.** A change is a version bump with an
  AutoMigration; talk to Suparno first. Version 3 (voice notes V4) added the `spoken_notes`
  table. Before installing a bump, back the phone up, and run `MigrationOnACopyTest` against a
  copy of its database (the test says how).

---

## How we work

- **Work goes straight to `main`**, now that one person works on the repo (since 2026-10-24):
  small commits, each fit for history. Push once the tests the change touches pass; when it needs
  a check by hand on the phone, commit but hold the push until Suparno confirms the check. Only a
  big separate task, or an experiment, gets its own branch and a PR. Check the branch
  (`git status -sb`) before every commit.
- **Test on a real phone**, not in an emulator and not "it compiles". The brief's *check* lines
  are the checks by hand.
- **A PR, when there is one, Suparno merges with "Rebase and merge"**, never squash, so every
  commit stays visible on `main`.
- **Tests come with the code**: JVM tests (`./gradlew testDebugUnitTest`) for anything pure, tests
  on the phone for anything that touches Android, `npm test` for the server.
- **Tick your step's box in your brief with the step's commits.** The briefs are the only progress
  tracker: no separate TODO files, no issues.
- **Never commit secrets.** `server/.env` is gitignored; `.env.example` holds placeholders only.
  Agents never read or print `.env`.
- **Free services only:** a generous free tier and no credit card. Every service in the stack
  meets that today.
- **No process without a reason.** Every tool, config file or check needs a concrete reason tied
  to this project; anything that cannot be justified does not go in.
- **Scaffolding is done by hand.** Agents do not run project generators (`npm create`,
  `android create`); a person does, and commits the result. The one exception is `web/`, which
  an agent scaffolded with `npm create vite` at Suparno's request.
- On Windows, **write commit messages to a file and commit with `git commit -F <file>`**:
  PowerShell 5.1 strips double quotes from arguments to native programs, so `git commit -m` with a
  `"` in it splits into pathspecs.

### Waiting on Suparno (manual)

Things only a person at the phone, or the project owner, can do. Keep this list current: add to
it whenever a change needs a manual check, and tick items off when done.

- [x] **Run the Android tests once**, JVM and on the phone (commands under "Dev workflow").
- [x] **Install the foundation on the phone.**
- [x] **Create the GitHub repo** `breadcrumb-hackinverse`, push `main`, and give AM2004legend,
      aeryxii and DhrubaIsha2006 write access.
- [x] **Confirm the owners** in the table above, and that each person is set up. Dhruba's adb
      connection was down for R3 and R4, so their phone checks ran on Suparno's phone.
- [x] **Server tests against Atlas** (`cd server`, `npm test`, with `MONGODB_URI` set): passing
      on the M0 cluster since 2026-10-10.
- [ ] **Seed the phone** (roadmap 1). Search only impresses with a real corpus: put 500+ real
      items on the phone with the importer (reading R5). Pictures with no words are embedded as
      they come (L9), on the GPU. It costs nothing.
- [x] **Check R3 and R4 on the phone** (2026-10-25): a scanned PDF is read by OCR; a bare link
      gets its page's description, and its title when it had none; a link saved in airplane mode
      stays unread, and is read when the network is back.
  - [ ] Left for later: a PDF behind a password shows *no text found*.
- [x] **Instagram links** (2026-10-27): a shared reel, post or profile gets its title and
      thumbnail, and so does one saved before the fix (it is read once more).
- [x] **Saved already** (capture C10, 2026-10-30): the brief's *check* line.
- [x] **Voice notes** (roadmap 2, 2026-10-30): V3's and V4's *check* lines in
      [`voice-notes.md`](docs/workstreams/voice-notes.md).
- [x] **Export and import** (roadmap 4, 2026-10-29): backed up on Suparno's phone, the debug
      build uninstalled, v1.0 installed, and the backup restored: everything came back
      ([`backup.md`](docs/workstreams/backup.md) B2).
- [x] **The first-run download** (roadmap 3, 2026-10-29): on that fresh install of v1.0 the
      model downloaded and search found by meaning, on Wi-Fi; the mobile-data path wasn't tried
      ([`local-first.md`](docs/workstreams/local-first.md) L8).
- [ ] **The website** (roadmap 5): W1's and W3's *check* lines in
      [`website.md`](docs/workstreams/website.md), on a phone and a computer, on the live site.
- [x] **The first release** (roadmap 5, W2, 2026-10-29): the key made and kept off the repo,
      v1.0 published; the website's download link answers. Keep the key backed up in two places.
- [x] **Hosting the website** (roadmap 5, W3, 2026-10-29): on Vercel, at
      `breadcrumb.suparno.me` by a CNAME record at Namecheap, which holds the domain's DNS.
- [ ] **Pictures on the GPU, and the smaller release** (roadmap 6, L9's *check* line in
      [`local-first.md`](docs/workstreams/local-first.md); the release built with R8, already
      on Suparno's phone as an update): use the app as usual for a while: keep, search, OCR,
      voice notes, a backup. Then push, and release it as 1.1.
- [x] **Decide how debug builds reach the phone** (2026-10-30): a `.debug` applicationId
      suffix, an app of its own beside the release (see "Android").
- [x] **Turn off VS Code's Gradle import** on this machine: `"java.import.gradle.enabled": false`
      in the workspace's `.vscode/settings.json` (gitignored). Its daemon kept coming back and
      breaking command-line builds with the jlink error (see "Android").

---

## Product definition

The core problem is **information fragmentation**. People save useful things all day
(screenshots, links, PDFs, photos, recommendations) and they scatter across apps. Later the user
remembers *what* they saved and the context around it, but not *where*.

The whole product is two flows:

> **Save → Forget about it → Search → Find**

### Reference memory, not action memory

This is the central distinction and the thing that keeps the product from drifting.

- **Action memory** = "I need to *do* something later." Pay a bill, submit an assignment, attend
  a meeting. Calendars, reminders and task managers already solve this well. **Breadcrumb does
  not compete here.**
- **Reference memory** = "I need to *remember* something later." A restaurant recommendation, a
  useful article, an internship screenshot, a PDF from a professor, a person met at an event.
  Nothing needs to happen with these. The user just wants to find them again.

Breadcrumb is **only** the second one.

### Non-goals (reject features that pull toward these)

- Not a notes app. Not Notion, not Obsidian.
- Not a task manager or reminder app.
- Not a "second brain" / knowledge graph / PKM tool.
- **Not a chatbot.** Search returns a ranked list of memories that open the original artifact.
  When retrieval starts working there will be real pull to make it conversational; resist it.
  Conversational answers make it worse at its actual job, which is getting the user back to the
  original thing. At most, one line of "why this matched."
- **No manual organization, ever.** No folders, no tags, no titles, no required input at save
  time. If a feature asks the user to organize something, it is the wrong feature.

The product should feel like a personal search engine, not a digital notebook.

---

## Stack

| Layer | Choice |
|---|---|
| Mobile | **Native Android**: Kotlin, Jetpack Compose, MVVM, Room (with full-text search), WorkManager |
| On the phone | **EmbeddingGemma 2** (text and pictures, 768 dimensions) through **LiteRT-LM**; ML Kit Text Recognition v2 (bundled); the phone's own speech recognizer, offline only, for voice notes |
| Query parsing | **On the phone, by rules** (`embed/PhraseRules`) |
| Website | **Vite + React + TypeScript + Tailwind 4**, with Motion for the animation: a static page in `web/`; its fonts ship with it. Hosted on **Vercel** (Hobby, free) at https://breadcrumb.suparno.me |
| Server (**retired**, L7) | Node + Express + TypeScript, MongoDB Atlas (M0) with Atlas Vector Search, Gemini 3.1 and 3.5 Flash Lite, `gemini-embedding-001`. Kept in `server/`; the app makes no call to it |

For the retired server: pin explicit Gemini model versions in code. The `gemini-flash-latest`
alias exists but must not be used: behaviour shifts under you. The server refuses aliases
outright.

**Why native Android:** every capture surface is an Android platform surface (share sheet,
`TileService`, clipboard), so it is Kotlin either way, and both capture paths are cold starts:
every save is a fresh process launch. A native transparent activity opens in about 50 ms; a
JavaScript runtime booting from a tile tap takes hundreds of milliseconds to seconds. The product
promise is that saving feels free. Revisit only if iOS becomes a real target.

### The free tier is the design constraint

*The server's, kept for history: since L7 the app uses no cloud service at all.*

**No billing.** What the free tier allows, **per model per project** (each model has its own
allowance):

| Model | Per minute | Per day |
|---|---|---|
| Every full Flash | 5 | **20** |
| **Gemini 3.5 / 3.1 Flash Lite** | 15 | **500** |
| Gemini Embedding | 100 | 1,000 |
| Gemma 4 26B / 31B | 30 | 14,400 |

Daily limits reset at midnight Pacific. AI Studio's rate-limit page shows only per-minute limits;
the daily ones are on the key's project quota page in the Cloud Console, filtered on "free tier".

**Ingest runs on 3.5 Flash Lite and query parsing on 3.1 Flash Lite**, so the two never share one
500. Everything is built to spend requests, not memories:

- **Batch.** One call reads ten memories; embeddings take an array too. 500 items is ~50 requests.
- **Never pay twice.** Fingerprints of what the model read mean a retry or a re-send costs nothing.
- **Ask only when there is something new to read.** An image waits for OCR; a memory with no text
  is stored without a model call at all.
- **Send a picture only when OCR could not read it** (under 80 characters). Images cost ~1,000
  prompt tokens each even when tiny, roughly ten times the text beside them, so a text-heavy
  screenshot is left to its words. Pictures go downscaled, four to a request, once each.
- **Wait rather than be refused.** The server holds ingest to the model's calls a minute and waits
  for a slot; a refusal costs the phone an exponential backoff, and during a bulk import that
  compounds into hours of nothing happening.
- **A search phrase is parsed once a day.** The same phrase against the same sources on the same
  day reuses its parse; a parse that fails costs the search only its filters.

Gemma's 14,400 a day is the escape hatch if 500 ever binds, though it likely has no structured
output.

---

## Architecture rules

These are decisions, not suggestions. Changing one is a deliberate call, not an implementation
detail.

**1. Everything stays on the device.**
Originals live in app-private storage, and since L7 so does everything made from them: text,
embeddings, metadata. Nothing is uploaded. Before L7 the rule was narrower (the server could
hold extracted text and an embedding, never a blob), and the reasons it gave still hold: M0 is
512 MB and Mongo caps documents at 16 MB.

**2. Save never blocks on the network.**
Share sheet / tile → write to Room → return immediately → WorkManager reads a link's page and
embeds, later. The user sees "Saved" before anything touches the network.

**3. No Gemini pass (retired, L7).**
Until L7, "Gemini reads my memories" (off by default) sent a memory's text, stripped of personal
details, to one multimodal Flash Lite call for a one-line summary, entities, a kind and dates.
Search never used it: the phone embeds only what it wrote or read itself. It went with the
upload queue and the privacy sheet. Summaries copied back before then still show.

**4. On-device OCR, and the only reading.**
ML Kit reads text locally at save time. Free, instant, offline. The item is searchable on the
phone, by its words and its embedding, with no network at all.

**5. Hybrid search, never pure vector, on the phone.** EmbeddingGemma 2 embeds memories and the
phrase on the phone, every vector is scored there (no vector database at this size), and the
result is fused with the phone's word search by reciprocal rank (`embed/LocalSearch`). The server's
hybrid search below is no longer called by the app (L6).
Pure semantic search is weakest at exactly what the target queries contain: proper nouns. In
*"that guy from the hackathon who worked at Qualcomm"*, "Qualcomm" wants keyword matching and the
rest wants meaning. `$vectorSearch` and `$search` run in parallel, and the server fuses them by
reciprocal rank (k = 60, the constant `$rankFusion` uses), not with the `$rankFusion` stage:
it returns each half's rank directly, and passes against the cluster. No extra infrastructure.
For whoever revisits it: `$rankFusion` would save one round trip, and with `scoreDetails: true`
each result's `{$meta: "scoreDetails"}` lists every input pipeline's rank
(`details[].inputPipelineName`, `details[].rank`), so the per-half ranks survive a switch.

**6. Parse the query before searching it, on the phone.**
*"that internship screenshot from April"* decomposes into words (internship), a type filter
(screenshot) and a date filter (April). Vector search handles none of the filtering: "from April"
just becomes noise in the embedding. `embed/PhraseRules` turns the phrase into
`{query, types, from, to, sourceApp}` by rules, against the app and site names saves came from,
and both the word search and the ranking are **filtered**. Without this layer the headline
queries do not actually work. It was a Gemini call on the server until L6: on 67 test phrases
the rules did as well (24 of 25 they weren't written against, to Gemini's 22) in microseconds,
where Gemini beat the search's 2.5 s wait for 18 of 65.

**7. Capture provenance: it is a first-class retrieval signal.**
On a share, record the calling app via `Activity.getReferrer()`. That makes *"that link someone
sent me on WhatsApp"* answerable. Also store the capture time and, for existing files, the
original creation date. Cheap to store, and it matches how people actually remember things.

**8. On the (retired) server, embeddings at 768 dims, not 3,072.** (The phone's EmbeddingGemma 2
gives 768 too; the two models' vectors are never compared.)
`gemini-embedding-001` defaults to 3,072 but is Matryoshka-trained: 768 scores barely behind the
full size on MTEB. On a 512 MB M0 the storage difference matters. M0 also allows **3 search
indexes per cluster**; one vector index plus one text index fits.

---

## Android

- **minSdk 29** (Android 10), target 36. The clipboard restriction lands at 29, so a 29 floor
  means one code path instead of two; scoped storage is mandatory from 29 anyway.
- **The applicationId is `com.lbc.breadcrumb`, the same as the code's namespace, and permanent
  once published.** The release's adb commands and the launcher shortcut's `targetPackage` use
  it. It was `com.lbc.breadcrumb.hackinverse` until the hackathon.
- **Debug builds are an app of their own, `com.lbc.breadcrumb.debug`** (`applicationIdSuffix`,
  since 2026-10-30), named "Breadcrumb debug", beside the release on the same phone, with its
  own data. The on-device tests run in it (`com.lbc.breadcrumb.debug.test`), so neither a debug
  build nor a test ever touches what the release keeps. Its launcher shortcuts are a copy in
  `src/debug/res/xml/shortcuts.xml` (a shortcut's `targetPackage` is fixed): change both
  together. It needs the model too: before its first start, `adb push` it to
  `/sdcard/Android/data/com.lbc.breadcrumb.debug/files/` and `adb shell chmod 666` it, or it
  downloads its own 388 MB. A copy of the model, its hash checked, is at
  `D:\Programming\models\` on Suparno's machine.
- **Release builds are signed by one key, kept for good**: `android/keystore.properties`
  (ignored by git; copy `keystore.properties.example`) says where it is and its passwords. Never
  commit either. Lose the key and no installed copy can ever be updated, only uninstalled, which
  deletes what it kept. A release and a debug build can't install over each other. Releasing:
  [`website.md`](docs/workstreams/website.md).
- **Suparno's phone runs the release build** (v1.0 since 2026-10-29), and the debug app beside
  it. The release isn't debuggable, so `run-as` reaches only the debug app: the release's data
  is backed up in the app (the masthead's "BACK UP"). A new release installs over it as an
  update (`adb install -r`) and keeps its data. **Never uninstall the release** to get past an
  install error: uninstalling deletes everything kept.
- **Moving a phone's data to a new applicationId**, by hand (done once, from
  `com.lbc.breadcrumb.hackinverse`). From Git Bash, never PowerShell: its `>` and `<` re-encode
  binary data and the archive comes out corrupt. Git Bash also rewrites `/sdcard/...` arguments
  into Windows paths, so those commands take `MSYS_NO_PATHCONV=1`.
  ```
  adb shell am force-stop com.lbc.breadcrumb.hackinverse
  adb exec-out run-as com.lbc.breadcrumb.hackinverse tar cf - databases files shared_prefs > app.tar
  ./gradlew installDebug                    # the new id, installed beside the old app
  adb shell am force-stop com.lbc.breadcrumb
  adb exec-in run-as com.lbc.breadcrumb tar xf - < app.tar
  ```
  Then re-add the Quick Settings tile, and give the new app the embedding model: copy it across
  (`adb shell cp` from the old app's `/sdcard/Android/data/<id>/files/` to the new one's) and
  `adb shell chmod 666` the copy, or the app can't read it; or let it download. Originals are
  found by name in the new store (`OriginalStore.fileFor`), so the old package in their stored
  paths doesn't matter. Files picked into a "+" draft don't survive: read grants are per
  package. Uninstall the old app only once the new one shows everything.
- **No Android Studio.** The app is built, installed and tested from the command line (`gradlew`
  in `android/`, and adb), and edited in any editor. Live Edit and `@Preview` are not used.
- **Setting up a machine** (once; checked on Windows by building against a fresh SDK):
  - JDK 21, which Gradle's daemon asks for (`gradle/gradle-daemon-jvm.properties`):
    `winget install --id EclipseAdoptium.Temurin.21.JDK -e`. `gradlew` prefers `JAVA_HOME`, so
    one left pointing at an older Java has to be moved.
  - The SDK's command-line tools, version 23.0
    ([`commandlinetools-win-16111833_latest.zip`](https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip);
    other platforms' zips are on developer.android.com/studio), unzipped so that
    `C:\Android\Sdk\cmdline-tools\latest\bin` exists. `ANDROID_HOME` set to `C:\Android\Sdk`,
    and its `platform-tools` and `cmdline-tools\latest\bin` on `PATH`.
  - In a new terminal: `android sdk install platform-tools "platforms;android-36.1"`. The first
    build downloads the build-tools it needs.
- **Install SDK packages with `android sdk install`, not `sdkmanager`.** From command-line tools
  23.0, `sdkmanager` is deprecated and hands over to the new `android` CLI. Its `--licenses` no
  longer writes a licence; `android sdk install` does, and the build needs one to download
  build-tools. From PowerShell, `sdkmanager.bat "platforms;android-36.1"` also fails: the batch
  file splits the package name at the `;`.
- **Testing is on a physical phone over USB or wireless debugging.** No emulator.
- **compileSdk is 36.1.** Some libraries' newest versions need compileSdk 37 (lifecycle 2.11, OkHttp
  5.5): read `minCompileSdk` from the AAR's `aar-metadata.properties`, and prefer pinning the
  library back over pulling down another SDK platform.
- **AGP 9 compiles Kotlin itself** (built-in Kotlin), so there is no
  `org.jetbrains.kotlin.android` plugin, and KSP must be **2.3.1 or newer**. Do **not** set
  `android.disallowKotlinSourceSets=false`; bump KSP instead.
- **Upsert with `@Upsert`, never `@Insert(onConflict = REPLACE)`:** `memories_fts` is an
  external-content index kept in step by triggers, and REPLACE deletes the old row without firing
  delete triggers, so the old text would stay searchable. `MemoryDaoTest` covers it.
- **Schema changes go through Room `AutoMigration` against the checked-in schema JSON**
  (`app/schemas/`). Never `fallbackToDestructiveMigration`: it would silently wipe saved memories
  on upgrade. A migration that changes the word index's columns must rebuild it.
- **Back up before installing a schema bump**: the release in the app ("BACK UP"); the debug
  app with `adb exec-out run-as com.lbc.breadcrumb.debug cat databases/breadcrumb.db > breadcrumb.db`,
  and the same for `-wal` and `-shm` (the WAL holds recent writes). Run it from Git Bash:
  PowerShell 5.1's `>` re-encodes binary output and corrupts the copy.
- **Run the instrumented tests without wiping saved memories.** Gradle's
  `connectedDebugAndroidTest` uninstalls the app when it finishes, which deletes its database
  and originals. Install and instrument directly instead (commands below). The test APK runs in
  the app's process, so once `BreadcrumbApp.onCreate` opens the real database, running tests
  migrates it: back up first.
- **A finished `am instrument` force-stops the app**, and a force-stopped app's WorkManager jobs
  don't run until it starts again: a link read or an embedding left by a test run waits for the
  next launch. To run them without opening the app,
  `adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS -p com.lbc.breadcrumb.debug`
  starts its process.
- **An on-phone test never clears, or lists-and-deletes, the real database or `originals/`.** The
  test APK runs in the debug app's process, so `app.database` is that app's real one (no longer
  the release's, since the `.debug` suffix, but keep to the rule). Use an
  in-memory Room database; where a test must go through `app.database` (it exercises code that
  does), have it note what existed before, look only at what it created, and remove only that.
  `WrittenCaptureTest` once deleted everything, and a test run wiped the phone's saved memories
  and, through the upload queue, the server's.
- **Cleartext HTTP is blocked**, in every build. The debug build permitted it to `localhost`
  for the server until L7; there is nothing local to reach now.
- **"The paging file is too small"** (or a JVM `hs_err_pid` crash log in `android/`): the machine
  is out of memory. Idle Gradle daemons hold ~1 GB each; `./gradlew --stop` frees them. Delete the
  crash log; it is not for committing.
- **"jlink executable … redhat.java … does not exist":** VS Code's Java extension imported
  `android/` and left an idle Gradle daemon on its bundled JRE, which has no `jlink`, and gradlew
  reused it. `./gradlew --stop` and rebuild. To stop it recurring, set
  `"java.import.gradle.enabled": false` in VS Code for this workspace.
- **Text styles name their face** (`Serif`, `Sans`, `Mono` in `ui/theme/Type.kt`). A Text given a
  style takes nothing from the theme and falls back to the platform font otherwise.
- **The app is dark always**: it is mostly a frame around other people's screenshots.
- Icons and the splash are **generated, not hand-drawn**: edit the constants at the top of
  `tools/generate_icons.py` and re-run it (`python tools/generate_icons.py`, needs Pillow); never
  edit the PNGs. The exception is `res/drawable/ic_tile_crumb.xml`, the Quick Settings tile icon:
  a hand-built single-colour vector, because the system tints tile icons.

## Server (retired)

**Retired since local-first L7**: the app makes no call to it. The code stays in `server/`,
never deleted, and still runs and passes its tests (`npm test`); the notes below are for anyone
who runs it.

- **No build step.** Node 22.18+ runs `.ts` files itself by stripping types; `tsc` only
  type-checks. So `erasableSyntaxOnly` is on (no enums, namespaces or parameter properties), and
  relative imports carry the `.ts` extension.
- Listens on **127.0.0.1** unless `HOST` is set; `PORT` defaults to 3000. `adb reverse` connects
  from the dev machine, so nothing on the network needs to reach it.
- **`/health` names the service** (`{"service":"breadcrumb","status":"ok"}`) and the app checks the
  name, so another dev server holding port 3000 is not mistaken for this one. Change both sides
  together.
- **Secrets live in `server/.env`**, copied from `.env.example` and loaded by Node's own
  `--env-file-if-exists`. Every line needs its `KEY=` prefix.
- **The Atlas user is `readWriteAnyDatabase`, not `atlasAdmin`:** enough to read, write and create
  indexes, deliberately not enough to drop a database.
- More, for whoever builds the server: [`docs/workstreams/server.md`](docs/workstreams/server.md).

## Dev workflow

The app needs nothing but the phone: no server, no `adb reverse`.

```
cd android
./gradlew testDebugUnitTest                         # JVM tests
./gradlew installDebug                              # build and install on the phone
adb shell am start -n com.lbc.breadcrumb.debug/com.lbc.breadcrumb.MainActivity
./gradlew installDebug installDebugAndroidTest      # then the on-device tests, without a wipe:
adb shell am instrument -w com.lbc.breadcrumb.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The debug app (`.debug`) installs beside the release; see "Android".

The retired server, for anyone who runs it:

```
cd server
npm install
npm run dev         # node --watch; restarts on save
npm test
npm run typecheck
```

The website:

```
cd web
npm install
npm run dev         # http://localhost:5173, reloads on save
npm run build       # type-checks, then builds the static site into web/dist
npm run lint
```

**Released and live.** The app is on GitHub Releases ("Releasing" in
[`website.md`](docs/workstreams/website.md)). The website is on Vercel at
https://breadcrumb.suparno.me: each push to `main` goes live, and Vercel
gives any other pushed branch a preview link of its own, which is how to see a change to the
site from a phone or a cloud session before it reaches `main`. Vercel's settings (the project,
the domain) live in its dashboard, not in the repo.
The app has no server since L7.

## Repo layout

```
breadcrumb-hackinverse/
  android/      Gradle project: run ./gradlew from here
  server/       Express + TypeScript, its own package.json: retired (L7), kept
  web/          the website: Vite + React + Tailwind, its own package.json
  docs/         api.md, design.md, workstreams/
  tools/        generate_icons.py
  CLAUDE.md
  README.md
```

A polyglot monorepo with separate toolchains and no shared build system. **Do not add Nx or
Turborepo**: there is nothing meaningful to share between Gradle and npm. No npm workspaces, and
no type codegen between server and Android: with one backend and one client, hand-written Kotlin
data classes matching `docs/api.md` are faster and clearer.

---

# ══════════ V1 SCOPE ══════════

**In V1:** everything in the four briefs, and local first. It shipped for Hackinverse 1.0 (below),
nice-to-haves included.

**Also in V1, outside the briefs:**

- **Auth: no user accounts.** One device, a long-lived token in the Android keystore, which the
  backend validates. Do not spend V1 on user management for a one-user app.
- Express stays thin: ingest, search, and keeping the phone in step. Each route is a few lines
  over a function tested on its own.
- Search UI is search-bar-first. No dashboard, no feed, no folder tree.

**V1 excluded voice notes**: recording UI, playback and audio storage were their own slice of
work. They came after, as roadmap 2.

**Why seeding is not optional:** a personal memory search engine is worthless until it holds a
few hundred items. Seed 500+ real items, so search is judged against a corpus that actually
exercises it.

# ══════════ END OF V1 ══════════

## Ideas, not planned

Listed so the ideas are not lost, not so they get pulled forward. Voice notes and screenshot
auto-detect were here; they are on the roadmap now.

- **Browser extension**: a second frontend, a second auth flow and a second capture pipeline for
  maybe 10% of saves.
- **Real auth**: Google Sign-In, proper user accounts.
- **Cross-device sync**: needs object storage for originals, which reverses rule 1. A deliberate
  call, not a drive-by change.
- Multimodal embeddings (embedding the picture itself, for screenshots with little text); a
  reranking pass over the top ~20 hits; iOS; desktop capture.

---

## Hackinverse 1.0

History, kept for the reasoning. Breadcrumb won 1st place. At mid-evals the judges asked about
personal data on the server, and local first was the answer: the final demo ran in airplane
mode, with search by meaning on the phone ("ramen" found a photo with no words in it).

## The name

From leaving breadcrumbs while walking through a forest. You don't know when you'll want to
return somewhere; you just leave a trail as you go. Users drop small pieces of information as they
move through their digital lives, then follow the trail back when they need something.
