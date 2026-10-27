# Voice notes

Roadmap 2. A voice note is a memory like any other: recorded in the app, or shared in (a WhatsApp
voice note, a recording), kept as its file, and found by what was said in it. What was said is
heard on the phone, by the phone's own speech recognizer, offline: nothing said leaves the phone.

The type was declared in V1 (`MemoryType.AUDIO`), so none of this changes the schema: the file is
the memory's original, and what was said is its `extractedText`, as a picture's OCR is.

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

## Rules

- **Nothing said leaves the phone.** Only the on-device recognizer, only with
  `EXTRA_PREFER_OFFLINE`. Never the default recognizer without it, which may go online.
- **extractedText null is not heard yet; empty is heard, with no words.** The queue finds its work
  by the null, as the reading queues do.
- **A test never records**, and never keeps into `app.database`: the fixtures play from the cache.
