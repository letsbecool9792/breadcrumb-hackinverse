# Voice notes

Roadmap 2. A voice note is a memory like any other: recorded in the app, or shared in (a WhatsApp
voice note, a recording), kept as its file, and found by what was said in it. What was said is
heard on the phone, by the phone's own speech recognizer, offline: nothing said leaves the phone.

The type was declared in V1 (`MemoryType.AUDIO`), so a voice note changes nothing in the schema:
the file is the memory's original, and what was said is its `extractedText`, as a picture's OCR
is. A note said over another memory (V4) is the one change: a `spoken_notes` table, schema 3.

## How it fits

```
record (the "+", or "Say something") ─┐
share a voice note in ────────────────┴→ a row, type AUDIO, extractedText null, its file stored
   → voice/ListenQueue hears it: the file decoded to 16 kHz mono, piped to the phone's own
     recognizer (offline only), what was said written as extractedText
      → the phone embeds it; word search finds it; "voice note about ramen" filters to it
```

## Steps

- [x] **V1. Heard on the phone** (Suparno, 2026-10-27)
  - `voice/OnDeviceTranscriber`: `SpeechRecognizer.createOnDeviceSpeechRecognizer` (Android
    13+), offline only (`EXTRA_PREFER_OFFLINE`), fed the file through a pipe
    (`EXTRA_AUDIO_SOURCE`, 16-bit mono at 16 kHz) as a segmented session, so the whole file is
    heard and not just up to the first pause. Its language is the phone's, or an installed one of
    the same language ("en-US" for "en-IN"); one that can be downloaded is asked for, and the
    note waits. Without an on-device recognizer, or before Android 13, a note is never heard.
  - `voice/PcmDecoder` decodes whatever MediaCodec reads (AAC, Opus, MP3, WAV) and `voice/Pcm`
    downmixes and resamples it, a chunk at a time. `voice/Paced` holds the feed to three times
    the note's own speed: fed as fast as the recognizer reads (about 6x on the demo phone), it
    lost words at a pause in two runs of three; at 3x, none in ten.
  - One recognizer for the language check and the listening: destroying one while the next
    connects unbinds the service, and the second hears nothing (error 11).
  - `voice/ListenQueue`, started in `BreadcrumbApp.onCreate`: the notes not heard yet, newest
    first. A note that has to wait (the recognizer busy, its language downloading, the app not on
    screen) is tried again when an activity of the app starts; one the phone can never hear, or
    whose file is gone, is heard with no words (empty, as for a picture with none).
  - `EmbedText` embeds a voice note once it is heard (by its words and its note).
  - Tests: `PcmTest`, `ListenQueueTest` (JVM); `OnDeviceTranscriberTest` on the phone hears two
    fixtures of the same two sentences, a recording's AAC and a WhatsApp Opus file (made with
    Windows' speech synthesis, 50 and 15 KB).
- [x] **V2. Shared in, shown, played, found** (Suparno, 2026-10-27)
  - The share sheet takes `audio/*` and `application/ogg`; the type's parameters are dropped
    ("audio/ogg; codecs=opus"), the file stored by it (ogg, m4a, mp3…, m4a when unknown), and a
    file's name never titles it.
  - The tile, the search row's mark, the detail's card with its player, the capture sheet's card
    (design §6, §9, §11, §15). Its title is the first line said, else "Voice note"; its status
    "still listening to it" until it is heard. Open original and Share hand on the file.
  - A search for "that voice note about ramen", "audio message from priya" or "lecture
    recording" keeps to voice notes (`PhraseRules`).
  - Tests: the rules on the JVM; `MosaicTest`, `DetailTest` and `VoicePlayerTest` (plays the
    fixture from the cache) on the phone.
- [x] **V3. Recording** (Suparno, 2026-10-27)
  - `voice/VoiceRecorder`: AAC in .m4a, mono, 44.1 kHz, 64 kbps (about 0.5 MB a minute), into
    the cache; `Keep.keepVoice` moves it into the original store and writes the row. Under 0.8 s
    it isn't kept; at half an hour it stops and is kept.
  - The voice chip in the composer, and the voice panel (design §13). The microphone is asked for
    the first time it opens (`RECORD_AUDIO`; the microphone is not required to install). Swiping
    the sheet down or leaving the app keeps what was recorded; "discard" throws it away.
  - The launcher shortcut "Say something" opens the sheet on the voice panel.
  - No test records: a panel that records keeps into the app's real database.
  - *Check:* tap +, then the voice chip; allow the microphone; say a sentence with a name in it;
    Keep. The tile drops in saying "listening…", and within seconds shows what you said; search
    a word from it, and "voice note". Open it: play, pause, drag the track. Record again and
    swipe the sheet down mid-sentence: it is kept. Long-press the app's icon: "Say something"
    opens recording. Share a WhatsApp voice note to Breadcrumb: the sheet shows "Voice note ·
    ogg · 0:12", and the tile its words.

- [x] **V4. A note said over any memory** (Suparno, 2026-10-27)
  - Beside "add a note", "say it": in the detail, and in the capture sheet once something is
    saved or found kept already. It records in place (`SayANote`): Done, the sheet going or the
    app leaving keeps it; discard doesn't. A share that saved several gets a copy each.
  - Schema version 3: `spoken_notes` (`memoryId`, `recordedAt`, `heardAt`), deleted with its
    memory. The AutoMigration is one `CREATE TABLE`; every other table is as it was. Checked
    against a copy of the demo phone's database (`MigrationOnACopyTest`: every row of every table
    the same after as before), and then the phone's own, pulled and compared row by row with the
    backup taken just before: 30 memories, 30 vectors, 4 import keys, 12 pending deletes, all
    identical.
  - The recording is `<memory id>.spoken.m4a` in the original store (`OriginalStore.spokenFile`),
    so `OriginalStore.delete` takes it with the memory's own original, without the database.
    Recording again replaces it, and it is heard again. "remove" takes the recording away; what
    was said stays in the note.
  - The listen queue is generic: a second one hears spoken notes, and `SpokenNoteDao.heard`
    marks one heard and adds what was said under the memory's note, in one transaction, once.
    The two share one recognizer, one note at a time.
  - Undo of a delete in the home screen brings the spoken note back with its memory (held aside
    for the five seconds; its file goes with the original when the delete is final).
  - `ui/common/Recording.kt` holds what records (the state, the trace, the dot, the microphone
    refused), shared by the voice panel and both places a note is said.
  - Tests: `SpokenNoteDaoTest` (heard once, into the note, found by a word of it; gone with its
    memory); `MigrationOnACopyTest`; "say it" in `DetailTest` and `CaptureSheetTest`. Nothing
    records.
  - *Check:* open a photo, "say it", say "Priya's flat, the one with the balcony", Done: the
    player shows with "listening…", and within seconds the note reads what you said; search
    "balcony" finds the photo. Share a screenshot in, "say it" on the sheet, Done: "note said";
    open it in the app: the same. "remove" takes the player away and leaves the words. Delete
    the photo and Undo: the spoken note comes back with it.
    V3's and V4's checks: done by Suparno, 2026-10-30.

## Rules

- **Nothing said leaves the phone.** Only the on-device recognizer, only with
  `EXTRA_PREFER_OFFLINE`. Never the default recognizer without it, which may go online.
- **extractedText null is not heard yet; empty is heard, with no words.** The queue finds its work
  by the null, as the reading queues do.
- **A test never records**, and never keeps into `app.database`: the fixtures play from the cache.
- **A spoken note's file is named by its memory** (`<id>.spoken.m4a`), never stored in a column:
  whatever deletes a memory's original deletes it too.
