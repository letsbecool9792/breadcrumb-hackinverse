# Workstream: Capture

**Owner:** Aritra · **Android, Kotlin** · needs no server

Getting things *into* Breadcrumb: the share sheet, the selection toolbar, the Quick Settings tile,
and the capture sheet that confirms a save. The product's promise lives here: **saving feels
free.** One tap, no questions, done before the user's thumb leaves the screen.

## What you own

- `android/app/src/main/java/com/lbc/breadcrumb/capture/`: the receiving activities, the tile
  service, the rules that turn a share into a memory
- `android/app/src/main/java/com/lbc/breadcrumb/ui/capture/`: the capture sheet
- Your entries in `AndroidManifest.xml` (activities, the tile service, the `<queries>` block) and
  your strings in `res/values/strings.xml`
- Your tests under `app/src/test/…/capture/` (JVM) and `app/src/androidTest/…/capture/` (on the phone)

## What you use, and what you hand on

- **Write** with `MemoryDao.upsert` / `upsertAll` (`BreadcrumbApp.database.memoryDao()`), on
  `BreadcrumbApp.applicationScope`. Never a `lifecycleScope`: a capture activity finishes in
  milliseconds, and the insert would be cancelled with it.
- **Copy files in** with `OriginalStore.copyIn` (`BreadcrumbApp.originals`), named by the memory id.
- **Undo** with `MemoryDao.deleteEverywhere` plus `OriginalStore.delete`.
- **A note** with `CaptureDao.setNote`: a targeted UPDATE that writes only a changed note and
  then puts the memory back in the upload queue (PENDING), since the server embeds the note.
  Never write the saved row back: the OCR queue may have read the picture meanwhile, and the
  old row would wipe its text.
- **What you hand on:** rows with `type`, `hasLink`, `title`, `rawText`, `localUri`,
  `contentCreatedAt`, `sourceApp`, `sourceAppLabel` (and `note`, if you build the note). Leave
  `extractedText` **null**: the reading workstream finds its work by that null, and reads every
  picture and PDF you save without you calling anything. After a save, call
  `uploadScheduler`, which `BreadcrumbApp.onCreate` points at `UploadWorker.schedule(context)`.

## Steps

One PR each. Check every step on a real phone before asking for review; the "check" lines are
what Suparno runs when he reviews.

- [x] **C1. Text and links from the share sheet and the selection toolbar**
  - `ShareReceiverActivity`: `ACTION_SEND` with `text/plain` and `text/html`, and
    `ACTION_PROCESS_TEXT` (select text anywhere → Breadcrumb in the toolbar).
  - The rules, as pure functions with JVM tests: text containing a URL → `LINK` and
    `hasLink = true`; otherwise `TEXT`. `EXTRA_SUBJECT` becomes the title, unless it is generic
    (WhatsApp sends `"Photo from <sender>"`).
  - Confirm with a toast for now; the sheet comes at C5.
  - *Check:* share a page from Chrome → a LINK row titled with the page's title. Select text in
    any app → Breadcrumb in the toolbar → a TEXT row.
- [x] **C2. Pictures and PDFs, one or many**
  - `MediaReceiverActivity`: `ACTION_SEND` and `ACTION_SEND_MULTIPLE`, `image/*` and
    `application/pdf`. Copy each file into the store **before the activity finishes**.
  - Read the provider's `DATE_TAKEN` (or EXIF) **at capture** into `contentCreatedAt`: it cannot
    be read later.
  - A photo shared with a caption arrives as `image/jpeg` with the caption in `EXTRA_TEXT`: an
    IMAGE row with the caption as `rawText`, plus `hasLink` when the caption has a URL.
  - *Check:* a screenshot from the gallery → IMAGE row with its taken date; a WhatsApp photo with
    a caption → IMAGE (+ LINK chip if the caption has a link); several images → one row each; a
    PDF → PDF row titled by its file name; deleting a row deletes its stored file too.
- [x] **C3. Where it came from**
  - `Activity.getReferrer()` → `sourceApp`; the app's name → `sourceAppLabel`, **resolved and
    stored at capture** (later, an uninstalled app has no name).
  - System surfaces are not a source: the clipboard arrives as `com.android.systemui`, a share
    passes through `com.android.intentresolver`, and `android`, `com.android.shell` and
    Breadcrumb itself are not sources either; record those as null, not as "System UI". A name
    equal to its package is no name. (`Sources.of`, a pure function with JVM tests.)
  - Add the `<queries>` block (launcher intent) to the manifest, or Android 11+ hides most
    packages and no name can be read. Never `QUERY_ALL_PACKAGES`. Check visibility with
    `adb shell dumpsys package queries`.
  - *Check:* a Chrome share → "from Chrome"; WhatsApp → "from WhatsApp"; the selection toolbar →
    the host app; the clipboard tile → no source.
- [x] **C4. The Quick Settings tile saves the clipboard**
  - `CaptureTileService` launches `ClipboardCaptureActivity` (not exported).
  - *Check:* copy text → pull down the shade → tap the tile → a TEXT row with no source; copy a
    link → a LINK row; Chrome's "copy image" → an IMAGE row; from the lock screen → it asks to
    unlock, then saves; an empty clipboard → a clear message, nothing saved.
- [x] **C5. The capture sheet** (replaces the toast)
  - A sheet over the app the share came from, on the transparent `Theme.Breadcrumb.Capture`:
    a preview (one photo following its shape, capped in height and cropped from the top;
    several as square tiles sharing the row's width, "+N" on the last; a link card with the
    host of the first URL anywhere in the text, the page title as headline when there is one,
    and the URL beneath; the text itself; a PDF card with title, type and size), then "Saved"
    / "Saved 3" / "Saved 2 of 3", a lowercase mono line of kind and source under it ("image ·
    from whatsapp", the source only when every item agrees), **Undo** and **Done**. "Saving…"
    is said in words, and every word comes from `strings.xml`.
  - It **stays until dismissed**: Done, swipe down, tap outside, Back. Only Undo closes it by
    itself, after showing "Removed".
  - The scrim and the sheet leave together: nothing slides away a beat after the sheet (see the
    pitfall on closing below).
  - A failure (an empty clipboard) shows the reason and a Close button.
  - *Check:* each kind of preview; Undo → "Removed", the row and its file gone; dismissing every
    way, with no dim left behind.
- [x] **C6. A note on a save** (nice to have)
  - A quiet "add a note" in the sheet opens a field, closed by default, so saving stays one tap.
    The note goes on every memory that share saved, when the sheet goes.
  - While the sheet is open, its memories are held back from upload (a note written there would
    otherwise cost a second send): add `uploadHolds: MutableSet<String>` (a
    `ConcurrentHashMap.newKeySet()`) to `BreadcrumbApp`; the sync workstream's queue skips those
    ids. Release on dismiss, on leaving for another app, and in `onDestroy`: a hold never
    released strands a memory until the process dies.

## Rules and pitfalls

- **Every capture activity is its own task:** `excludeFromRecents`, `noHistory`,
  `taskAffinity=""`, the capture theme, `adjustResize`, and
  `configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode"`,
  so a rotation mid-save does not recreate the activity and drop the sheet.
- **`EXTRA_TEXT` and `EXTRA_PROCESS_TEXT` are `CharSequence`, not `String`.** `getStringExtra`
  returns null when the sender supplies styled text, turning a real share into "nothing to save"
  with no error. Always `getCharSequenceExtra(...)?.toString()`.
- **A shared `content://` URI is readable only while the receiving activity is alive.** Finish
  first and copy later, and the copy fails with a SecurityException. So the sheet **cannot be
  dismissed while it shows "Saving…"**: swipe, Back and tapping outside are ignored until the
  copy is done.
- **The clipboard cannot be read from a `TileService`**, and not in the activity's `onCreate`
  either: Android 10+ hands it only to the window with focus, and focus arrives after `onCreate`.
  Read it in `onWindowFocusChanged(true)`. That rules out `windowNoDisplay` (a window never shown
  never gains focus); give it a 2 s timeout so a window that never gains focus cannot sit
  invisibly on top, eating touches.
- **`TileService.startActivityAndCollapse(Intent)` throws `UnsupportedOperationException`** on
  API 34+. Use the `PendingIntent` overload there.
- **Never save a clip marked sensitive.** Password managers and OTP autofill set
  `android.content.extra.IS_SENSITIVE` on what they copy. Read it on every API level: apps set it
  regardless of the constant's API 33 introduction.
- Android 12+ shows its own "Breadcrumb pasted from your clipboard" notice when the tile reads
  the clipboard. Expected; do not try to suppress it.
- **WhatsApp offers no Share on text messages or link previews**, only Copy and Forward. The
  clipboard tile is the way in for those.
- **Closing a capture activity is a cross-task transition, and apps cannot customise those:**
  `overrideActivityTransition`, `overridePendingTransition` and `windowAnimationStyle` are all
  ignored. Whatever the window still draws slides away with it. To close cleanly: set
  `FLAG_NOT_TOUCHABLE`, make the decor view `INVISIBLE`, wait two frames for the window manager
  to hide the surface, then `finish()`; and post a ~150 ms fallback `finish()`, since a hidden
  window may stop receiving frames and the two-frame wait would never end.
- **One transition drives the scrim and the sheet, both ways:** the dim fades in as the sheet
  rises, and both leave together. The activity finishes only after the exit has played and
  one clear frame has drawn; finishing when the exit starts hides the window in that frame,
  and the exit is never seen. Read the transition and the drag while laying out and drawing,
  never in composition.
- **The scrim is the sheet's sibling, behind it, not its parent:** a parent's clickable also
  hears every tap inside the sheet, so a tap on the preview would close it.
- **Edge to edge:** targetSdk 36 is edge to edge on Android 15+, where `adjustResize` no
  longer lifts Compose content. The sheet pads `navigationBarsPadding()` then `imePadding()`,
  in that order, and the capture activities call `enableEdgeToEdge` on older versions too, so
  every version behaves alike.
- **One base class runs every capture** (`CaptureActivity`): the sheet's state, the upload
  hold, the note, Undo and closing. An activity only captures, through `saveText`,
  `saveFiles` or `fail`.
- **`content://` only.** An exported activity handed a `file://` URI would copy the app's own
  private files into the store.
- **Type and extension:** the provider's `getType`, else the intent's (or the clip's) type.
  Only `image/*` and `application/pdf` are saved; the rest is skipped. The extension comes
  from a fixed map (jpg png webp gif heic heif avif bmp pdf), never from the sender's name.
- **Copy and write each file in turn, in `applicationScope`**, its row right after its copy:
  leaving mid-copy keeps what was copied. "Saved N of M" when some failed.
- **Shared text goes on a file only when exactly one came.** A subject equal to the shared
  text is dropped; on media, a generic one ("Photo/Image/Video/Document/File from …") or one
  equal to the file name is no title, and a PDF falls back to its file name without ".pdf".
- **Dates:** `DATE_TAKEN`, else `DATE_MODIFIED` (seconds), else `COLUMN_LAST_MODIFIED`, else
  EXIF from the stored copy, all read at capture.
- **`noHistory` finishes the activity the moment the person leaves**, so the sheet's ending
  runs in `onStop` as well as on dismiss: write whatever note is typed, then release the
  hold, then call the upload hook, in that order. A note kept for dismiss is lost on Home.
- **Undo joins the insert job before deleting**, or a row still being written outlives it.
- **Re-created with a saved state** (the process died): finish without capturing. Capturing
  again would save everything twice.
- **The clipboard:** every item, coerced to text unless it is a `content://` picture or PDF.
  A sensitive clip is never saved; text wins over files. Each failure has its own message and
  a Close button ("Clipboard is empty", "Not saved: clipboard is marked sensitive", "Couldn't
  save that"), and the focus timeout shows "Couldn't read the clipboard": never a silent close.
- **The tile is an action, not a toggle:** `STATE_INACTIVE` in `onStartListening`, with a "Save
  clipboard" subtitle. From the lock screen, `unlockAndRun` before launching.
- **Chip model: one primary `type`, plus `hasLink`.** A file decides the type (IMAGE, PDF);
  otherwise LINK if the text has a URL; otherwise TEXT. `hasLink` comes from shared text only,
  never from OCR: a screenshot with a URL bar in it is not a link someone sent.
- Text styles name their face (`Serif`, `Sans`, `Mono` in `ui/theme/Type.kt`); a bare
  `TextStyle(fontSize = …)` falls back to the platform font.

## Out of scope

Reading the text out of what you save (reading workstream), uploading it (sync), voice notes.
