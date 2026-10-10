# Breadcrumb — working context

Personal memory retrieval. Save anything in one tap; find it later by describing it in natural
language.

This file is the source of truth for **what we are building, what we decided, who builds what,
and what we are deliberately not building.** Read it before proposing work. Then read:

- [`docs/api.md`](docs/api.md): the phone–server contract
- your workstream's brief in [`docs/workstreams/`](docs/workstreams/): what to build, in what
  order, the pitfalls, and what to check on the phone

---

## Where things stand

**The foundation is in**, on `main`:

- `android/`: the project (Compose, minSdk 29, Room, WorkManager, ML Kit, OkHttp and
  serialization already in the dependencies), the brand icon, splash, fonts and theme, the
  **whole database schema** at version 1 (`memories`, its word index `memories_fts`,
  `pending_deletes`, `imported_sources`), the core `MemoryDao`, the store for originals, and a
  debug list that shows every memory and its state.
- `server/`: Express with `/health`, the Mongo connection, the memory document's shape, the
  contract's types (`src/api.ts`), and every route of the contract as a stub answering 501.
- `docs/`: the contract and the four briefs.

**Next:** the four workstreams, in parallel. Each brief holds its own checklist; tick a step in
the PR that finishes it.

---

## Team and workstreams

| Workstream | Owner | Brief | Builds | Needs the server? |
|---|---|---|---|---|
| Capture | Aritra (GitHub AM2004legend) | [capture.md](docs/workstreams/capture.md) | Share sheet, selection toolbar, Quick Settings tile, source app, the capture sheet | No |
| Reading on the phone | Dhruba (DhrubaIsha2006) | [reading.md](docs/workstreams/reading.md) | OCR, PDF text, links' pages, local word search, the bulk importer | No |
| Server | Ayushi (aeryxii) | [server.md](docs/workstreams/server.md) | Gemini ingest, embeddings, Atlas indexes, hybrid search, query parsing | Is the server |
| Sync, search screens, integration | Suparno (letsbecool9792) | [sync-and-search.md](docs/workstreams/sync-and-search.md) | Upload queue, server client, home, search, detail, opening originals; reviews every PR | Yes |

**How the pieces meet:**

```
capture writes a row (extractedText null)
   → reading fills extractedText (OCR, PDF text, a link's page), and calls the upload schedule
      → sync sends it (deletes, then memories, then pictures OCR could barely read)
         → the server reads it with Gemini, embeds it, indexes it
            → sync copies the summary back; search screens find it, by words on the phone
              and by meaning and words on the server
```

Each workstream can be built and checked alone: capture and reading against the debug list,
the server from the terminal, sync against the server.

**The few shared points** (each named in the briefs that touch it):

- `BreadcrumbApp`: `applicationScope`, `database`, `originals`; capture adds `uploadHolds`,
  reading adds `readingOriginals` and starts the OCR queue in `onCreate`.
- `UploadWorker.schedule(context)`: sync provides it; capture and reading call it after a save
  or a read.
- `BreadcrumbDatabase`: each workstream registers its own DAO there (one abstract function).
- `AndroidManifest.xml` and `strings.xml`: each adds its own entries, with a comment saying why.
- The schema: **every column is already there.** A change is a version bump with an
  AutoMigration; talk to Suparno first.

---

## How we work

- **One step, one branch, one PR.** Branch from the latest `main`; never commit on `main`. Check
  the branch (`git status -sb`) before every commit.
- **Test on a real phone before asking for review**, not in an emulator and not "it compiles".
  The brief's *check* lines are what the review runs.
- **Suparno reviews every PR on the phone and merges with "Rebase and merge"**, never squash, so
  every commit stays visible on `main`. A commit message unfit for history gets reworded during
  the rebase, not squashed away.
- **Tests come with the code**: JVM tests (`./gradlew testDebugUnitTest`) for anything pure, tests
  on the phone for anything that touches Android, `npm test` for the server.
- **Tick your step's box in your brief in the same PR.** The briefs are the only progress
  tracker: no separate TODO files, no issues.
- **Never commit secrets.** `server/.env` is gitignored; `.env.example` holds placeholders only.
  Agents never read or print `.env`.
- **Free services only:** a generous free tier and no credit card. Every service in the stack
  meets that today.
- **No process without a reason.** Every tool, config file or check needs a concrete reason tied
  to this project; anything that cannot be justified does not go in.
- **Scaffolding is done by hand.** Agents do not run project generators (`npm create`,
  `android create`); a person does, and commits the result.
- On Windows, **write commit messages to a file and commit with `git commit -F <file>`**:
  PowerShell 5.1 strips double quotes from arguments to native programs, so `git commit -m` with a
  `"` in it splits into pathspecs.

### Waiting on Suparno (manual)

Things only a person at the phone, or the project owner, can do. Keep this list current: add to
it whenever a change needs a manual check, and tick items off when done.

- [ ] **Run the Android tests once.** They compile, but the machine that set the project up had
      no memory left to start a test JVM. `cd android`, `./gradlew testDebugUnitTest` (4 JVM
      tests), then on the phone `./gradlew installDebug installDebugAndroidTest` and
      `adb shell am instrument -w com.lbc.breadcrumb.hackinverse.test/androidx.test.runner.AndroidJUnitRunner`
      (6 tests: `MemoryDaoTest`).
- [ ] **Install the foundation on the phone:** the debug list opens after the splash; **+** adds
      sample memories; **Delete** and **Clear** remove them.
- [ ] **Create the GitHub repo** `breadcrumb-hackinverse`, push `main`, and give AM2004legend,
      aeryxii and DhrubaIsha2006 write access.
- [ ] **Confirm the owners** in the table above, and that each person is set up: JDK 21, the
      Android SDK from the command line (see "Android"; no Android Studio) and an Android phone
      with USB debugging for Aritra and Dhruba; for Ayushi, Node 22.18+, a free Atlas M0 cluster
      and a free Gemini key (see the server brief).
- [ ] **Plan the seeding.** Search only impresses with a real corpus: put 500+ real items on the
      demo phone with the importer (reading R5) the day before the demo. Ingest costs one Flash
      Lite request per ten memories (500 a day), and embeddings take arrays (1,000 requests a
      day), so a few thousand items fit in a day.
- [ ] **Server tests against Atlas** run only where `MONGODB_URI` is set; the skeleton's own
      tests need nothing (`cd server`, `npm install`, `npm test`: 4 pass).

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
| Mobile | **Native Android**: Kotlin, Jetpack Compose, MVVM, Room, WorkManager |
| Backend | Node + Express + TypeScript |
| Database | MongoDB Atlas (M0 free tier) + Atlas Vector Search |
| Ingest AI | **Gemini 3.5 Flash Lite** (multimodal: text and images in one call) |
| Query AI | **Gemini 3.1 Flash Lite** (query parsing) |
| Embeddings | `gemini-embedding-001` at 768 dimensions |
| On-device OCR | ML Kit Text Recognition v2, bundled model |

Pin explicit Gemini model versions in code. The `gemini-flash-latest` alias exists but must not be
used: behaviour shifts under you. The server refuses aliases outright.

**Why native Android:** every capture surface is an Android platform surface (share sheet,
`TileService`, clipboard), so it is Kotlin either way, and both capture paths are cold starts:
every save is a fresh process launch. A native transparent activity opens in about 50 ms; a
JavaScript runtime booting from a tile tap takes hundreds of milliseconds to seconds. The product
promise is that saving feels free. Revisit only if iOS becomes a real target.

### The free tier is the design constraint

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

**1. Originals stay on the device.**
Do not upload blobs to MongoDB. M0 is 512 MB total and Mongo caps documents at 16 MB. Originals
live in app-private storage. Files are sent to the server only for processing, then dropped. The
cloud stores **extracted text + embedding + metadata only.** Cheaper, faster, more private, and a
defensible product stance: your screenshots never permanently leave your phone.

**2. Save never blocks on the network.**
Share sheet / tile → write to Room → return immediately → WorkManager handles upload, processing
and retry. The user sees "Saved" before anything touches a server, which is also why a server
cold start is invisible on the ingest path.

**3. One Gemini call for ingest, not a four-stage pipeline.**
A single multimodal Flash Lite call takes the artifact and returns structured JSON: a one-line
summary, entities, an inferred kind, date references. This is *better* than raw OCR, not just
simpler, because it understands context: "LinkedIn post, Qualcomm SWE internship, deadline April
30" rather than a bag of words.

**4. On-device OCR as a fast path.**
ML Kit reads text locally at save time. Free, instant, offline. The item is searchable in the
local index before the network round-trip completes; the Gemini pass enriches it afterward.

**5. Hybrid search, never pure vector.**
Pure semantic search is weakest at exactly what the target queries contain: proper nouns. In
*"that guy from the hackathon who worked at Qualcomm"*, "Qualcomm" wants keyword matching and the
rest wants meaning. Atlas's `$rankFusion` fuses `$vectorSearch` and `$search` by reciprocal rank
in one pipeline. No extra infrastructure.

**6. Parse the query before searching it.**
*"that internship screenshot from April"* decomposes into words (internship), a type filter
(screenshot) and a date filter (April). Vector search handles none of the filtering: "from April"
just becomes noise in the embedding. A cheap Flash Lite call turns the phrase into
`{query, types, from, to, sourceApp}`, then runs a **filtered** search. Without this layer the
headline demo queries do not actually work.

**7. Capture provenance: it is a first-class retrieval signal.**
On a share, record the calling app via `Activity.getReferrer()`. That makes *"that link someone
sent me on WhatsApp"* answerable. Also store the capture time and, for existing files, the
original creation date. Cheap to store, and it matches how people actually remember things.

**8. Embeddings at 768 dims, not 3,072.**
`gemini-embedding-001` defaults to 3,072 but is Matryoshka-trained: 768 scores barely behind the
full size on MTEB. On a 512 MB M0 the storage difference matters. M0 also allows **3 search
indexes per cluster**; one vector index plus one text index fits.

---

## Android

- **minSdk 29** (Android 10), target 36. The clipboard restriction lands at 29, so a 29 floor
  means one code path instead of two; scoped storage is mandatory from 29 anyway.
- **The applicationId is `com.lbc.breadcrumb.hackinverse`; the code's namespace is
  `com.lbc.breadcrumb`.** adb commands, the test runner and launcher shortcuts use the
  applicationId.
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
- **Back up the phone's database before installing a schema bump:**
  `adb exec-out run-as com.lbc.breadcrumb.hackinverse cat databases/breadcrumb.db > breadcrumb.db`,
  and the same for `-wal` and `-shm` (the WAL holds recent writes). Run it from Git Bash:
  PowerShell 5.1's `>` re-encodes binary output and corrupts the copy.
- **Run the instrumented tests without wiping saved memories.** Gradle's
  `connectedDebugAndroidTest` uninstalls the app when it finishes, which deletes its database
  and originals. Install and instrument directly instead (commands below). The test APK runs in
  the app's process, so once `BreadcrumbApp.onCreate` opens the real database, running tests
  migrates it: back up first.
- **Cleartext HTTP is blocked by default.** The debug build permits it to `localhost` only
  (`src/debug/res/xml/network_security_config.xml`); release gets nothing.
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

## Server

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

**The backend is not deployed during development.** Run it locally and bridge the phone to it:

```
adb reverse tcp:3000 tcp:3000
```

The phone's `localhost:3000` then forwards to port 3000 on the dev machine, over USB or wireless
debugging. **The forward is lost whenever the phone reconnects**: re-run it after replugging.

```
cd server
npm install
npm run dev         # node --watch; restarts on save
npm run reverse     # adb reverse tcp:3000 tcp:3000
npm test
npm run typecheck
```

```
cd android
./gradlew testDebugUnitTest                         # JVM tests
./gradlew installDebug                              # build and install on the phone
adb shell am start -n com.lbc.breadcrumb.hackinverse/com.lbc.breadcrumb.MainActivity
./gradlew installDebug installDebugAndroidTest      # then the on-device tests, without a wipe:
adb shell am instrument -w com.lbc.breadcrumb.hackinverse.test/androidx.test.runner.AndroidJUnitRunner
```

**Deployment is deferred.** When it comes: Google Cloud Run scales to zero with a ~2 s cold
start and 2M requests a month free, but needs a billing account on file; Render's free tier
cold-starts in 30–60 s after 15 idle minutes. Because of rule 2, a cold start is invisible on the
ingest path; only search is latency-sensitive.

## Repo layout

```
breadcrumb-hackinverse/
  android/      Gradle project: run ./gradlew from here
  server/       Express + TypeScript, its own package.json
  docs/         api.md, workstreams/
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

**In V1:** everything in the four briefs. **For the demo, these must work:** share sheet and tile
capture; OCR; upload; ingest; hybrid search with query parsing; the search screens and opening the
original; the importer, for seeding. **Nice to have, in this order:** the capture sheet's note,
PDF and link reading, the "+" sheet, bookmark import, motion and haptics.

**Also in V1, outside the briefs:**

- **Auth: no user accounts.** One device, a long-lived token in the Android keystore, which the
  backend validates. Do not spend V1 on user management for a one-user app.
- Express stays thin: ingest, search, and keeping the phone in step. Each route is a few lines
  over a function tested on its own.
- Search UI is search-bar-first. No dashboard, no feed, no folder tree.

**V1 excludes voice notes.** The ingest call handles audio in principle (rule 3), but recording
UI, playback and audio storage are their own slice of work.

**Why seeding is not optional:** a personal memory search engine is worthless until it holds a
few hundred items. Seed 500+ real items, so search is judged against a corpus that actually
exercises it.

# ══════════ END OF V1 ══════════

## Post-V1: do not build in V1

Listed so the ideas are not lost, not so they get pulled forward.

- **Voice notes**: recording, playback, audio storage.
- **Browser extension**: a second frontend, a second auth flow and a second capture pipeline for
  maybe 10% of saves.
- **Real auth**: Google Sign-In, proper user accounts.
- **Cross-device sync**: needs object storage for originals, which reverses rule 1. A deliberate
  call, not a drive-by change.
- **Screenshot auto-detect**: a `ContentObserver` on MediaStore offering to save new screenshots.
  High-leverage, but costs `READ_MEDIA_IMAGES` and real battery care.
- Multimodal embeddings (embedding the picture itself, for screenshots with little text); a
  reranking pass over the top ~20 hits; iOS; desktop capture.

---

## The demo

What has to be true when it is shown:

- A phone holding a few hundred real memories, seeded with the importer.
- Three searches people actually type, each landing on the right thing in one go:
  *"that internship screenshot from April"*, *"the link someone sent me on WhatsApp"*,
  *"that guy from the hackathon who worked at Qualcomm"*, and one tap to the original.
- A save from the share sheet and one from the tile, found by a search seconds later, with the
  network off for the save.
- One sentence on privacy: the original never leaves the phone; the cloud holds only text and a
  vector.

## The name

From leaving breadcrumbs while walking through a forest. You don't know when you'll want to
return somewhere; you just leave a trail as you go. Users drop small pieces of information as they
move through their digital lives, then follow the trail back when they need something.
