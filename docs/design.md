# Breadcrumb: the screens

The app is three things on one screen, with no navigation anywhere: the **mosaic** at rest, a
**ranked list** while searching, and **one memory opened** in a sheet over either. Plus the
**"+" sheet** for keeping something from inside the app, and the **capture sheet** over other
apps.

## §0 Principles

- **Dark always** (`BreadcrumbTheme(darkTheme = true)`): the app frames other people's
  screenshots; a loud ground competes with them.
- **Every text style names its face**, through the helpers in §1. A Text given its own style takes
  nothing from the theme.
- **Serif or sans by position, never by kind.** Among others (tiles, result rows, the capture
  sheet) a memory's title is sans; opened in its detail it is serif. The serif is otherwise only
  the app's voice: the wordmark, "Saved", sheet titles, empty-state lines.
- **Chrome is lowercase mono**: counts, ages, sources, labels, hints. It recedes.
- **Amber is rare**: the "+", the main action, the search field's light, the lit match, the note's
  rule. Everything else sits a step off the ink.
- **Motion that loops or follows a gesture runs only while shown, and is read while drawing**
  (`graphicsLayer`, `drawBehind`, `Canvas`), never in composition.
- No dashboard, feed, folders, tabs or titles. Never a chatbot.

## §1 Tokens and type

Colours are the existing tokens in `ui/theme/Color.kt`: Ink #171310 (ground), InkElevated #221B16
(lifted surface), InkBorder #2E2620, InkMedia #1C1713 (behind a picture), InkFieldBorder #322A23,
InkGrabber #3A322A, InkLabel #5A4E44, InkOutline #7A6E62, Bone #EDE3D8, BoneDim #BFB2A4,
AmberBright #FFC46B, AmberContainerDark #5A4420, AmberOnContainer #2C1A00, HitBackground #3A2C14,
CaptureScrim #080605, CrumbFaint #5A4420, CrumbDim #7A5A28, CrumbMid #C8912F, CrumbBright #FFC46B.
The dark scheme: primary AmberBright, onPrimary AmberOnContainer, surface Ink, onSurface Bone,
surfaceVariant InkElevated, onSurfaceVariant BoneDim, outline InkOutline, outlineVariant InkBorder.

Helpers (`ui/home/HomeType.kt`):
- `serif(size, color = Bone, lineHeight = size × 1.15)`: Young Serif.
- `sans(size, color = Bone, weight = Normal, lineHeight = Unspecified)`: Instrument Sans.
- `monoStyle(size, color)`: IBM Plex Mono.
- `metaStyle` = mono 10sp InkOutline, letterSpacing 0.3sp ("2d · chrome").
- `Quiet(title, hint)`: Column, padding 26dp horizontal, 40dp vertical, spacing 9dp: title
  serif 22sp; hint mono 11sp InkOutline, lineHeight 18sp.

## §2 Marks and glyphs

**CrumbTrail(modifier, walking = false)**: the logo's four crumbs, drawn on a 20-unit square
(u = width/20): (4, 15.5) r 1.6 CrumbFaint; (8.4, 12.4) r 2.1 CrumbDim; (13.2, 8.6) r 2.7
CrumbMid; (17, 4.2) r 3 CrumbBright. Still, all at full alpha. Walking: an infinite linear lap of
1,300 ms; `stepLight(phase, i)`: step = 1/5, peak = step·i + step/2,
lit = clamp(1 − |phase − peak| / step, 0, 1); each crumb's alpha 0.28 + 0.72·lit, scale
0.82 + 0.3·lit. The lap only exists while walking.

**DocumentGlyph(modifier)**: a page in outlineVariant (InkBorder), corner radius 3dp; a folded
corner at the top right, a square of 28% of the width in outline (InkOutline) at alpha 0.55 × 0.35;
five ruled lines 2dp high, rounded, from x = 18% of the width, at y = height × (0.36 + 0.12·i),
widths 44%, 64%, 58%, 64%, 36% of the width, in InkOutline at alpha 0.55.

**NoteGlyph(modifier)**: four rounded lines 3dp high with 4dp gaps, widths 70% (InkOutline α0.55),
100%, 88%, 94% (InkBorder).

Small glyphs on a 16-unit grid (u = size/16), round caps and joins:
- **Picture** (15dp, stroke 1.4u): rounded rect at (1.5, 2.5) size 13×11, radius 2; a polyline
  (2,12) (6.5,7.5) (10,11) (11.5,9.5) (14,12); a sun, a filled circle r 1.2 at (11, 6).
- **Share** (18dp): lines from (4,8) to (12,3.5) and to (12,12.5), stroke 1.4u; filled circles
  r 2.1u at all three points.
- **Bin** (18dp, stroke 1.5u): lid line (2.5,4)–(13.5,4); handle polyline (6,4) (6.5,2) (9.5,2)
  (10,4); body polyline (3.8,4) (4.8,14) (11.2,14) (12.2,4).
- **Leaves the app** (15dp, stroke 1.6u): box polyline (6,3) (3,3) (3,13) (13,13) (13,10); arrow
  head (9.5,2.5) (13.5,2.5) (13.5,6.5); shaft (13,3)–(7.5,8.5).
- **Voice**: five upright strokes at x 2, 5, 8, 11, 14, centred on y 8, 4, 8, 12, 7 and 3 units
  tall, stroke 1.6u, round caps. The voice note's mark everywhere.
- **Play**: a filled triangle (5,3) (13,8) (5,13), its corners rounded by a 1.4u stroke.
  **Pause**: two bars 3u × 10u at x 4 and 9 from y 3, radius 1u.
- **Clear ×**: two diagonals across the box, stroke 1.5dp, round caps.
- **Plus**: two lines across the box's centre lines, round caps.

## §3 Thumbnails

`object Thumbnails`: an LruCache of ImageBitmap sized at an eighth of the heap in KB (sizeOf =
w·h·4/1024), keyed `"$id@$targetPx:$localUri"`. `load(context, memory, targetPx)`, off the main
thread: the stored original via OriginalStore.fileFor; a picture decoded with the largest
power-of-two inSampleSize that keeps both sides ≥ targetPx; a PDF's page one rendered targetPx wide
on white (`eraseColor(WHITE)`, RENDER_MODE_FOR_DISPLAY). Null when there is no file or it can't
be drawn. `cached(memory, targetPx)`. `@Composable rememberThumbnail(memory, targetPx)`: starts
from the cache, so a tile scrolled back does not blink, and decodes on IO when missing. Sizes
used: tiles 360, result rows 140, the detail 1080 (picture) and 900 (PDF page), falling back to
the 360 or 140 already cached, so a picture flying into the sheet is never an empty box.

## §4 The screen

`HomeScreen(onOpenDebug: (() -> Unit)?, keepRequests: Int)`. One `SharedTransitionLayout` over
everything, so a picture can travel between its tile and the sheet; it provides the
SharedTransitionScope and the opened memory's id through CompositionLocals.

Layout, top to bottom, on Ink with statusBarsPadding:
1. **Content**, weight 1. Browsing and searching hand over through
   `AnimatedContent(searching)` with `fadeIn(tween(240, delay 60)) togetherWith fadeOut(tween(160))`.
   Browsing shows the mosaic (§6), which carries its own masthead; searching shows the counter
   line (§9) and the results. The search being left stays drawn while it fades out. The grid
   state and the entrance record are held here, so a search and back returns to the same place
   and replays nothing. Over the content's bottom edge, a 48dp vertical gradient from transparent
   to Ink, so content fades under the search field rather than stopping.
2. **The bottom**, `navigationBarsPadding().imePadding()`, padding start 16, end 16, top 6,
   bottom 16, spacing 10dp: the model's bar (§19), the import bar (§14), the backup bar (§20), the undo bar (§12), then a Row: the search
   field (weight 1) and the "+" button (§8) 10dp to its right. The "+" shows only while the field
   has no focus and nothing is typed: enter `fadeIn(180) + expandHorizontally(220)`, exit
   `fadeOut(120) + shrinkHorizontally(200)`.
3. **Two SheetLayers** over all of it: the detail (§11) for the opened memory, and the "+" sheet
   (§13), whose content is the composer or, after its quiet line, the import panel (§14), or after
   its voice chip, the voice panel (§13).

Back clears a search before it leaves the app. Opening a memory hides the keyboard and clears
focus first, so the sheet never rises behind the keyboard. One `now` per screenful
(`remember(memories, search)`), so ages don't tick apart row by row.

## §5 Masthead and the slim strip

**Masthead(kept, onLongPress)** is the mosaic's first item, spanning both columns. Column,
padding start 6, end 6, top 6, bottom 16, spacing 6:
- "breadcrumb" in serif 48sp, lineHeight 52. A long press (no ripple, no click action) does a
  LongPress haptic and opens the debug list; with `onLongPress == null` there is no gesture at all.
- Row, spacing 9, centred: CrumbTrail 15dp (still), then the kept count UPPERCASE in mono 11sp
  InkOutline, letterSpacing 1.4sp ("17 KEPT"), weight 1; at the end "BACK UP" in the same. A tap
  on the Row (no ripple, click label "back up") opens everything kept (§20). Not on the empty
  mosaic: there is nothing to back up, and the import panel offers a restore.

**The slim strip** takes its place as it scrolls away.
`collapse = if (firstVisibleIndex > 0) 1 else clamp((offset/mastheadHeight − 0.45) / 0.45, 0, 1)`.
It is drawn only while collapse > 0, aligned to the top over the grid: `graphicsLayer { alpha =
collapse; translationY = (1 − collapse) × −6dp }`, background Ink, padding start 20, end 20, top 10,
bottom 12, SpaceBetween: "breadcrumb" serif 20sp lineHeight 22 at the left, the kept count (not
uppercase) mono 11sp InkOutline at the right. Tapping it, only once collapse > 0.5 and with no
ripple, animates the grid back to item 0.

## §6 The mosaic

`LazyVerticalStaggeredGrid`, 2 fixed columns, contentPadding start 14, end 14, top 2, bottom 56;
horizontal and vertical spacing 10dp. Item 0 is the masthead (full line). Then every memory,
newest first, keyed by id, each with
`animateItem(fadeInSpec = null, placementSpec = spring(StiffnessMediumLow), fadeOutSpec = tween(200))`,
so a deleted tile fades and its neighbours close the gap. While Room has not answered yet
(null), draw nothing; empty → §7. A drag on the grid hides the keyboard and clears focus (only a
drag, through the grid's interactionSource; a list moving on its own leaves the keyboard be).

**Entrances** (`MosaicEntrances`, held by the screen): the first list the mosaic draws is what the
app opened on; those tiles **rise**: within 1.5 s of opening, wait 45 ms × min(index, 10); then
440 ms FastOutSlowIn, alpha p, translationY (1 − p) × 22dp. A tile first drawn later by a scroll
just rises with no wait. A memory that arrives while the mosaic is up (a new save, or an Undo)
**drops**: 640 ms with the crumb's bounce `CubicBezierEasing(0.34, 1.4, 0.64, 1)`, alpha
min(1, 2.4p), translationY (1 − p) × −40dp, scale 0.84 + 0.16p. A tile already shown does
nothing. A memory that leaves the list is forgotten, so it drops in if it comes back. All of it
in `graphicsLayer`.

**A tile**: full width, clip RoundedCornerShape(10dp), InkElevated, border 1dp InkBorder,
clickable (opens the detail). By kind:

- **Picture** (IMAGE): all picture. Height = width × the thumbnail's aspect, clamped 150–270dp
  (190dp until it loads); InkMedia behind; ContentScale.Crop aligned **TopCenter** (a screenshot
  is recognised by its top); it is a travelling picture (§10). Over its foot, a Column with a
  vertical gradient [transparent, Ink α0.6, Ink α0.94], padding 11 horizontal, top 30, bottom 10,
  spacing 4: the caption, `firstLine(title ?: rawText) ?: summary`, in sans 13sp Medium Bone
  lineHeight 17, at most 3 lines; then the meta in metaStyle coloured BoneDim.
- **Document** (PDF): a 108dp InkMedia box padded 13dp left, right and top, holding the first
  page (Crop TopCenter, top corners 3dp, travelling), or DocumentGlyph 40×52 centred until it
  loads. Below, a caption: padding 11 horizontal, top 9, bottom 11, spacing 5: the title (or
  "Document") in sans 14sp Medium lineHeight 19, at most 2 lines; the meta.
- **Link**: padding 13, spacing 9. When the text has a host: Row, spacing 7: a 16dp square, radius
  4, AmberContainerDark, holding the host's initial uppercase in mono 9sp AmberBright Medium; then
  the host in mono 10sp BoneDim, one line. Then the title (§17) in sans 14sp Medium lineHeight 19,
  at most 4 lines. Then the meta.
- **Note** (TEXT): padding start 14, end 14, top 8, bottom 13, spacing 6: an opening quote mark
  "“" in sans 34sp AmberBright Medium lineHeight 26; the text (rawText, else the title) in sans
  15sp lineHeight 21, at most 7 lines; the meta with 3dp top padding.
- **Voice note** (AUDIO): padding 14 horizontal, 13 vertical, spacing 9: a Row, spacing 8: a 26dp
  AmberContainerDark circle holding the voice glyph 14dp in AmberBright, then the length ("0:42")
  in mono 10sp BoneDim; then what was said in sans 15sp lineHeight 21, at most 6 lines;
  "listening…" in mono 11sp InkOutline until it is heard; when nothing was made out, its title
  ("Voice note") in sans 15sp BoneDim. Then the meta.

The meta is `tileMeta` (§17): "2d · whatsapp".

**Date scrubber**, at the right edge, vertically centred: a Row, spacing 7: a 16×1dp line in
AmberContainerDark, then the month of the first visible tile (its capturedAt) UPPERCASE in mono
10sp InkOutline, letterSpacing 1sp, on Ink, padding end 12, vertical 3. Alpha 1 while the grid
scrolls (120 ms in), fading over 700 ms once it stops. Instead of section headers.

## §7 The empty mosaic

The masthead, then **NothingKept**: a full-size Box whose crumbs are drawn behind it. Column,
padding 26dp horizontal and 36dp vertical, spacing 12: "Nothing kept yet" serif 22sp; "Start here:
the + brings in the screenshots and PDFs already on your phone." sans 15sp lineHeight 22; "or share
anything to breadcrumb from any app,\nor copy it and tap the breadcrumb tile" mono 11sp InkOutline
lineHeight 18.

Behind it, a trail of 7 crumbs walking towards the real "+" at the bottom right: points on a
quadratic curve from (26% of the width, 46% of the height), bending through (34%, 88%), to
(width − 26dp, height − 30dp). Crumb i at t = i/6: colour lerp(CrumbFaint → AmberBright,
0.25 + 0.75t) at alpha 0.45 + 0.55·lit, radius (2.2 + 2.4t)dp × (0.85 + 0.3·lit). The lap is an
infinite linear 2,600 ms; lit = clamp(1 − |phase − peak| / step, 0, 1), with step = 1/8 and
peak = step·i + step/2 (the eighth step is rest). Read while drawing.

## §8 The search field and the "+"

**SearchField**: height 54dp, RoundedCornerShape(27dp), InkElevated, border 1dp: AmberContainerDark
while focused, else InkFieldBorder. Padding start 20, end 16; Row spacing 11:
- CrumbTrail 17dp, walking while the ranked answer is on its way (status RANKING).
- The field: BasicTextField, single line, mono 13sp Bone, cursor AmberBright, ImeAction.Search
  (the key only hides the keyboard; results are already live). The hint "search your memory" in
  mono 13sp InkOutline when empty.
- When something is typed, a clear button: a 22dp circle in InkFieldBorder holding a 9dp × in
  BoneDim, content description "Clear search". It clears the query.

**The light behind it**: before the clip, so it spills past the pill. In `drawBehind`, a circle
of radius width × 0.62 at the centre, filled with a radial gradient from AmberBright at alpha
`glow` to transparent, drawn with `scale(scaleX = 1, scaleY = 0.42)`. glow = base + 0.07 × breath.
base animates (tween 400) to 0.13 while focused or searching, else 0.06. breath runs 0→1→0
(1,400 ms FastOutSlowIn, reversing) and exists only while searching.

**The "+" button** (KeepSomethingButton): a 54dp circle in AmberBright, clickable, holding an 18dp
plus in AmberOnContainer with 2.2dp strokes and round caps. Content description "Keep something",
or "Keep something — a draft is waiting" when there is one, and then a dot at the top end
(padding 3): a 12dp Ink circle with a Bone dot inset 2.5dp. It opens the "+" sheet.

## §9 Search

**Two answers in turn.** On every change of the normalised query (trimmed, runs of whitespace as
one space), with `collectLatest` so a keystroke abandons the search in flight:
1. Empty → resting (the mosaic).
2. The phone's word search answers at once (the words the parse leaves, narrowed by its
   filters; limit 30): state Searching with status RANKING and no interpretation.
3. After a 450 ms pause, the phone's ranking (local-first L2, L6): Found → the joined hits
   (§Part 2), with the interpretation and status RANKED. (Unavailable, with OFFLINE or
   UNAVAILABLE, was for the server's search; the phone's ranking always answers.)

**The counter line** over the results: `counter(...)` (§17) in mono 11sp InkOutline,
letterSpacing 0.3, at most 2 lines, padding start 20, end 20, top 18, bottom 12. RANKING and
RANKED add nothing (the walking trail says a search is out); OFFLINE adds "offline · words only",
UNAVAILABLE "search busy · words only".

**No results**: RANKING → a walking CrumbTrail 44dp at padding start 28, top 36; RANKED →
Quiet("Nothing matches that", "try fewer words, or describe it\nthe way you'd say it out loud");
OFFLINE or UNAVAILABLE → Quiet("No saved words match", "ranked search is out of reach, so
this\nlooked for the words themselves").

**The list**: LazyColumn, contentPadding 16 horizontal, bottom 56; spacing 4dp; keyed by id;
`animateItem(fadeInSpec = null, placementSpec = spring(StiffnessMediumLow), fadeOutSpec = tween(140))`,
so rows glide to their ranked places when the ranking replaces the word matches. A drag hides the
keyboard. A row's first appearance in a search: wait 40 ms × min(index, 8), then 340 ms
FastOutSlowIn, alpha p and translationY (1 − p) × 16dp; a row that already arrived in this search
(re-ranked, or scrolled back) does not arrive again; a new phrase starts afresh. When the ranked
answer lands with results, one SegmentTick haptic.

**A result row**: padding 10 horizontal, 13 vertical; RoundedCornerShape(10dp), background
InkElevated for the top row once RANKED, else transparent; clickable; Row, spacing 12, aligned
top:
- **Thumbnail**: 46dp, radius 7, InkMedia, border 1dp InkBorder, centred. A picture or PDF page
  (140px, Crop TopCenter, travelling); a PDF without one: DocumentGlyph 24×30; a link: the host's
  initial uppercase (or "↗") in mono 16sp AmberBright; a note: NoteGlyph padded 9dp; a voice
  note: the voice glyph 22dp in AmberBright.
- Column, weight 1, spacing 5: the **title** (§17, with the hit's summary) in sans 16sp Medium
  Bone lineHeight 21, at most 2 lines. Then **why it matched**, in mono 11sp BoneDim lineHeight
  16, at most 3 lines: the fragment (§17) with its hit lit, or for a match by meaning alone the
  summary when it differs from the title. Then `rowMeta` in metaStyle.
- **The lit word**: 180 ms after the row lands it warms over 520 ms FastOutSlowIn: its colour
  lerps BoneDim → AmberBright and its background HitBackground goes from alpha 0 to 1. A row
  already shown in this search is drawn lit.
- The words to light: the interpretation's query when it has one, else the phrase, as `words()`.
- The summary and readText come from the phone's row.

## §10 SheetLayer (the app's own sheet)

`SheetLayer(item: T?, onDismiss, maxFraction = 0.86f, content: ColumnScope.(T, AnimatedVisibilityScope))`.
Drawn in the screen's composition, unlike Material's sheet, so a picture can travel into it. The
item being closed stays drawn while the sheet leaves.

- **Scrim**: full size, black at alpha 0.6, fades in over 420 ms and out over 300 ms; it lightens
  as the sheet is pulled: alpha × (1 − 0.8 × clamp(drag / sheetHeight, 0, 1)). A tap on it
  dismisses.
- **The sheet**, inside a full-size Box with `statusBarsPadding().imePadding()` (it stands on the
  keyboard and never slides under the status bar), aligned to the bottom, full width, height at
  most window height × 0.86. It slides in over 420 ms and out over 300 ms, both on
  `CubicBezierEasing(0.2, 0, 0, 1)`. Top corners 20dp, background Ink, taps on it swallowed.
- **Drag to dismiss**: dragging the sheet moves it down (never up past home). On release: dismiss
  when dragged past 22% of its height, or flung down faster than 1,600 px/s; otherwise spring
  back (stiffness 500). Content scrolled to its top hands a further pull down to the sheet (a
  NestedScrollConnection: post-scroll of user input moves the sheet, pre-scroll upward takes the
  sheet home first, pre-fling releases it).
- **Predictive Back**: the sheet follows the back swipe down (drag = height × 0.3 × progress),
  dismisses when the swipe completes, and springs back when it is cancelled.
- **Haptic**: GestureThresholdActivate once when a pull crosses the dismiss point (not during a
  back swipe).
- **Travelling pictures**: `TravellingPicture(memoryId) { modifier -> … }` wraps a picture in a
  tile or row: it is visible (fade 420 ms) only while that memory is not open, with
  `sharedElement(rememberSharedContentState("picture-$id"), …)`. In the sheet the same key is
  applied to the detail's picture with the sheet's AnimatedVisibilityScope. Outside a shared
  scope (previews, tests) pictures simply stay put.

## §11 The detail of a memory

Content of a SheetLayer. The row is observed live, so a summary copied back while it is open
appears in place.

- **Grabber**, centred: top padding 12, bottom 10, 34×4dp, a pill in InkGrabber.
- A scrolling Column (weight 1, not filled): the **artifact**, then a Column padded 22dp left and
  right, 22dp top, spacing 20, that follows the picture in a beat behind (enter
  `fadeIn(tween(320, delay 140)) + slideInVertically(tween(420, delay 100)) { it / 6 }`, exit
  `fadeOut(120)`): **heading**, **note**, **provenance**, **found text**.
- Then the **actions** row, with navigationBarsPadding.

**The artifact**, full width:
- **Picture**: height = width × aspect, clamped 160dp to max(window height × 0.42, 200dp) (260dp
  until loaded); InkMedia; Crop TopCenter; travelling; a tap opens the original.
- **PDF page**: 340dp tall InkMedia box, padding 28 horizontal and 24 top, the first page (Crop
  TopCenter, top corners 4dp, travelling), or DocumentGlyph 72×94 centred; a tap opens it.
- **Link card**: InkMedia, padding 22 horizontal and 24 vertical, spacing 10, a tap opens the
  link: Row, spacing 8: a 7dp AmberBright dot and the host in mono 12sp AmberBright; the title in
  **serif** 23sp lineHeight 28; when the memory has a title, the URL text beneath in mono 11sp
  InkOutline lineHeight 17, at most 6 lines, selectable.
- **Note**: InkMedia, padding 22/24, the text whole in **serif** 20sp lineHeight 28, selectable.
- **Voice note**: InkMedia, padding 22/24, spacing 18: the **player** (when its file is on the
  phone), then what was said, whole, in **serif** 20sp lineHeight 28, selectable; "listening…"
  until it is heard, "no words heard" when nothing was made out, both mono 12sp InkOutline. Its
  words are not repeated under found text.
- **The player** (`VoicePlayer`): a Row, spacing 14, centred: a 52dp AmberBright circle holding
  play or pause 18dp in AmberOnContainer ("Play" / "Pause"); then a Column, weight 1, spacing 2:
  the track, 24dp tall: a 3dp round line in InkBorder inset by the knob, AmberBright as far as it
  has played, and a 10dp AmberBright knob there, read while drawing; a tap or a drag seeks. Under
  it, SpaceBetween, the time gone and the length in mono 11sp InkOutline. One MediaPlayer, made at
  the first play (usage media, content speech); it pauses when the app leaves the screen and
  goes with the player; at the end it returns to 0:00.

**Heading**: for a picture or PDF, its title (§17, with the summary) in **serif** 27sp lineHeight
32; then the caption (rawText, when it differs from the title) in sans 15sp lineHeight 22,
selectable; then the summary (when it differs from the title) in sans 15sp BoneDim lineHeight 22.
Spacing 9. For a link or note, only the summary. Nothing when there is nothing.

**The note** (the person's own words, the only thing editable here; never the memory itself):
- The label "YOUR NOTE" in mono 10sp InkLabel, letterSpacing 1.2.
- With a note: the label at the left and "edit" in mono 10sp AmberBright at the right; beneath,
  a Row (height of its content, spacing 12): a 2dp rounded rule in AmberBright, full height, and
  the note in sans 16sp lineHeight 23. A tap starts editing.
- Without: a Row, spacing 8: "+" in mono 14sp AmberBright and "add a note" in mono 12sp BoneDim.
  A tap starts editing.
- Editing: the label; a box, radius 12, InkElevated, padding 14/12, holding a BasicTextField in
  sans 16sp lineHeight 22, cursor AmberBright, sentence capitalisation, focused at once, hint
  "who sent it, what it's for…" in InkOutline; then "Done" in sans 14sp AmberBright SemiBold,
  aligned to the end, padding 12/7, radius 16, which writes it if changed (blank clears it) and
  hides the keyboard. Closing the sheet mid-edit still writes the draft.
- A written note has the memory embedded again: the note is part of what is embedded.
- **Said, not written** (voice notes V4): beside "add a note", at the end of its Row, **"say it"**:
  the voice glyph 13dp in AmberBright and "say it" in mono 12sp BoneDim, spacing 7, no ripple;
  with a note, "say it" in mono 10sp AmberBright beside "edit" (spacing 16). It opens **saying a
  note** in place of the row: under the label, a box, radius 12, InkElevated, padding 14/12,
  spacing 10: the breathing dot and "recording", the time at the end (mono 12sp Bone); the trace
  (§13) 36dp tall; then at the end "discard" (mono 12sp BoneDim, padding 10/7) and "Done" (sans
  14sp AmberBright SemiBold, padding 12/7). Done, the sheet closing or the app leaving keeps it.
- **A note said over it**, under the label: the player (§11), then "listening…" (mono 11sp
  InkOutline) until it is heard and at the end "remove" (mono 10sp BoneDim, padding 8/5); then the
  note with its rule. Once heard, what was said is under the note's own words.

**Provenance**, mono and receding: a divider in InkElevated, then rows (padding 11 vertical,
SpaceBetween: label mono 11sp InkOutline, value mono 11sp BoneDim), divided by InkElevated at
alpha 0.7: **saved** "12 sep · 2 days ago"; **from** the source, lowercase (when there is one);
**taken** "18 apr" (when known); **status** (§17, only while there is one; live as the phone
reads it and the queue sends it).

**Found text**: for each section, a label UPPERCASE in mono 10sp InkLabel letterSpacing 1.2, then
the text in mono 11sp InkOutline lineHeight 18, selectable, spacing 8. "text found" for what the
phone read (extractedText), or "from the page" for a link; "what it shows" for the model's
readText. Long text shows its first 1,600 characters, cut at a word with " …" (all of it is
searched).

**Actions**: Row, padding 22 horizontal, top 18, bottom 22, spacing 10:
- **The one action**, weight 1: 52dp tall, radius 26, AmberBright, clickable; its label in sans
  15sp SemiBold AmberOnContainer: "Open original" (a picture, PDF or voice note), "Open link" (a link with a
  URL), "Copy text" (a note, or a link without a URL); for the first two the "leaves the app"
  glyph follows after 8dp. With nothing to hand back, "Original isn't on this phone", disabled, on
  InkElevated with InkOutline text.
- **Share**, when there is something to share, and **Delete**: 52dp circles in InkElevated with
  the share and bin glyphs (18dp, BoneDim), content descriptions "Share" and "Delete". Delete
  does a Confirm haptic first.

## §12 Opening, sharing, deleting

**The action** (`OriginalAction`): IMAGE, PDF or AUDIO → ViewFile(file, mime) when the stored
file exists, else Missing; LINK → ViewUrl(first URL; a bare "www." gets https://) else CopyText(rawText)
else Missing; TEXT → CopyText(rawText) else Missing. MIME: application/pdf for a PDF; by
extension jpg/jpeg → image/jpeg, png, webp, gif, heic, heif; otherwise image/*; for a voice note
m4a → audio/mp4, mp3 → audio/mpeg, ogg and opus → audio/ogg, aac, amr, 3gp → audio/3gpp, wav,
flac, webm; otherwise audio/*. ViewFile opens
ACTION_VIEW with the FileProvider content URI and FLAG_GRANT_READ_URI_PERMISSION. CopyText puts
the text on the clipboard; below Android 13 it toasts "Copied" (13+ confirms by itself). No app
able to open it → toast "No app here can open this".

**Sharing onward** (`ShareOut`): a picture, PDF or voice note goes as the file (content URI, read grant,
`ClipData.newRawUri` so the sheet can preview it) with the person's note as EXTRA_TEXT (its
caption); a link goes as text: the note on the line before the URL, the page title as
EXTRA_SUBJECT and EXTRA_TITLE (a link without a URL falls back to its words); a note goes as its
words, then a blank line, then the person's note. Nothing to share → no share button. Always
through `Intent.createChooser`, with Breadcrumb's own share receivers in
`EXTRA_EXCLUDE_COMPONENTS`, so a memory is never shared back into itself.

**Delete**, with five seconds of Undo instead of a confirmation: the detail closes; the row goes
at once (it leaves the mosaic and results immediately, and is removed from the results shown);
the original file stays until the five seconds pass. A second delete makes the first final.
Undo restores the row as it was and has it embedded again. When the five seconds pass, or the
view model is cleared mid-undo, the file is deleted (on the application's scope). Deletes and restores run under one mutex, in
the order asked.

**The undo bar**, over the search field: a 46dp pill, radius 23, InkElevated, border 1dp
InkFieldBorder, padding start 18, end 6: "deleted · {title, lowercase}" in mono 12sp BoneDim,
weight 1, one line; then "Undo" in sans 14sp AmberBright SemiBold, padding 14/9, radius 18.
Enter `fadeIn(160) + slideInVertically(220) { it / 2 }`, exit `fadeOut(200) +
slideOutVertically(240) { it / 2 }`; the last line stays drawn while it slides away.

## §13 The "+": keeping something from inside the app

A SheetLayer whose content is the **composer**:
- Grabber. A scrolling Column (weight 1, not filled), padding 22 horizontal, top 6, spacing 16:
  - "Keep something" in serif 26sp lineHeight 30.
  - The box, at least 96dp tall: a BasicTextField in **serif** 20sp lineHeight 28, cursor
    AmberBright, sentence capitalisation; focused 320 ms after the sheet opens (so the keyboard
    doesn't race it up). Hint in InkOutline: "a thought, a link, anything…", or with files picked
    "a note about it — who sent it, what it's for…".
  - Picked files, a horizontally scrolling Row, spacing 10: each a 76dp tile, radius 10,
    InkMedia, border InkBorder: a picture's thumbnail (ContentResolver.loadThumbnail at 220px,
    Crop); a PDF: DocumentGlyph 22×28 and its name in mono 8sp BoneDim, at most 2 lines, padded 6,
    spacing 5. At the top end (padding 4), a 22dp circle in black α0.6 holding an 8dp × in Bone
    (1.5dp): "Remove".
  - When the last keep saved nothing: "couldn't keep that — try again?" in mono 11sp AmberBright.
  - The quiet line "bring in what's already on the phone" in mono 12sp InkOutline, padding 6
    vertical, radius 12: it switches the sheet to the import panel (§14).
- The bottom row, navigationBarsPadding, padding 22 horizontal, top 16, bottom 20, spacing 8:
  two chips, "photo" (picture glyph) and "pdf" (DocumentGlyph 12×15): 40dp tall, radius 20,
  InkElevated, border InkFieldBorder, padding 14 horizontal, spacing 8, the glyph in a 16dp box,
  the label in mono 12sp BoneDim; the **voice chip**, a 40dp circle of the same, holding the
  voice glyph 16dp in BoneDim, content description "voice note" (no label, so three chips and
  Keep fit a 360dp phone); a spacer; then **Keep**: 48dp, radius 24, padding 22
  horizontal, AmberBright when there is something to keep (or while keeping) else InkElevated;
  "Keep" in sans 15sp SemiBold, AmberOnContainer or InkOutline. While keeping, a walking
  CrumbTrail 15dp (end padding 9) appears before the word (fade + expand) and the button is
  disabled.
- "photo" opens the system photo picker, images only, up to 10. "pdf" opens the document picker
  for application/pdf, several at once. A file picked twice is there once.

**What Keep does**, on the application's scope: text alone is the memory, by the share rules (a
note, or a link when it holds a URL), with no source app; with files, each file is its own
memory (copied in as a share would be), and the text is the note on every one. On success: the
picked files' grants are released, the draft is emptied, the sheet closes, and the work that
follows a save is scheduled (a link's page, the embedding). On failure the sheet stays with the failure line.

**The voice panel**, in the sheet in place of the composer, after the voice chip or the "Say
something" shortcut: Grabber; a Column, padding 22 horizontal, top 6, spacing 18:
- A Row: "Say it" in serif 26sp lineHeight 30, and at the right "discard" while recording, else
  "back", in mono 12sp BoneDim, padding 10/6, radius 14: it throws away what was recorded and
  goes back to the composer.
- **The trace**, full width, 88dp tall: a bar a loudness sample (every 70 ms, the recorder's
  peak since the last, on a square-root scale), 3dp wide with 3dp between, newest at the right,
  centred, as tall as it was loud (3dp at the least); the newest AmberBright, the rest Bone at
  alpha 0.85 fading by 0.6 across the width. Read while drawing.
- A Row, SpaceBetween: while recording, a 7dp AmberBright dot breathing (alpha 1 → 0.3, 700 ms,
  reversing, in graphicsLayer) and "recording" in mono 12sp BoneDim, spacing 8; at the right the
  time in mono 13sp Bone.
- Refused the microphone: "breadcrumb needs the microphone to keep a voice note" in sans 15sp
  lineHeight 22, and a chip "allow the microphone" (mono 12sp BoneDim, InkElevated, border
  InkFieldBorder, radius 20, padding 14/11): it asks again, or once refused for good, opens the
  app's settings.
- Nothing recorded, or kept: "couldn't record that — try again?" in mono 11sp AmberBright; a tap
  records again.
- The bottom row as the composer's, with only Keep, at the end: lit while recording, walking
  while it is kept.

Recording starts as the panel opens, once the microphone is allowed (asked the first time).
Keep stops it and keeps it (on the application's scope), and the sheet closes. Swiping the sheet
down, the app leaving the screen, or half an hour's limit keeps it too; "discard" doesn't.

**The draft** survives the sheet being swiped away and the app closing, picked files included.
It is stored as JSON in SharedPreferences (`Draft(text, attachments: [DraftFile(uri, name,
isPdf)])`, removed when empty; junk decodes to an empty draft). Each picked file gets
`takePersistableUriPermission(READ)` (some providers refuse; then it lasts the run), released when
the file is kept or removed. Loading drops any file whose grant no longer exists. The draft loads
off the main thread at start; anything typed meanwhile wins. Closing the sheet keeps the draft,
and the "+" opens on the composer again, not the import panel.

**The launcher shortcut** "Keep something" (short label "Keep"): `res/xml/shortcuts.xml` with
action `com.lbc.breadcrumb.action.KEEP`, targetPackage `com.lbc.breadcrumb` (the
applicationId), targetClass `com.lbc.breadcrumb.MainActivity`; icon an adaptive icon, background
@color/brand_ink, foreground a 108dp vector with a plus in #FFFFC46B, stroke 6, round caps,
`M54,38 L54,70 M38,54 L70,54`; declared as `android.app.shortcuts` meta-data on MainActivity.
MainActivity counts requests: each intent with that action (onCreate when not restored, and
onNewIntent) adds one, and the count is saved across rotation. The screen opens the sheet for each
new count, closing any open detail, and the debug list gives way first. A second shortcut, "Say
something" (short label "Say it"), action `com.lbc.breadcrumb.action.VOICE`, its icon the voice
glyph over the same 32 units as the plus (strokes 5, `M38,48.67 L38,59.33 M46,43.33 L46,64.67
M54,38 L54,70 M62,44.67 L62,63.33 M70,50 L70,58`), is counted the same way and opens the sheet on
the voice panel.

## §14 Bringing in what's already on the phone

**The import panel**, in the "+" sheet in place of the composer: Grabber; a scrolling Column,
padding 22 horizontal, top 6, spacing 14:
- A Row: "Bring in" in serif 26sp lineHeight 30, and at the right "back" in mono 12sp BoneDim,
  padding 10/6, radius 14 (back to the composer).
- Three choices, each a card: full width, radius 14, InkElevated, border InkFieldBorder, padding
  16/14, spacing 3, a label in sans 15sp Bone SemiBold over a detail in mono 11sp BoneDim:
  - "A whole folder" / "every picture and PDF in it — screenshots, usually": the folder picker,
    opening at `primary:Pictures/Screenshots` (built with DocumentsContract.buildDocumentUri on
    com.android.externalstorage.documents) → importFolder.
  - "Only these pictures" / "pick them yourself": the photo picker, images only, no limit →
    importVisualMedia.
  - "PDFs, or a bookmarks export" / "from the file picker": the document picker for
    application/pdf and text/html, several at once → PDFs to importPdfs, an HTML file to
    importBookmarks.
  - "A breadcrumb backup" / "from another phone, or one you saved": the document picker for a
    zip → restore (§20).
- The import's line (below) in mono 12sp AmberBright, when there is one.
- A navigationBarsPadding spacer of 20dp at the foot.
- Picking closes the sheet at once; the import runs on the application's scope.

The empty mosaic's "+" opens the sheet straight on this panel.

**The import bar**, over the search field, above the undo bar, while there is a line: the same
46dp pill as the undo bar, padding 18 horizontal, spacing 10: a CrumbTrail 15dp, walking while the
import runs, and the line in mono 12sp BoneDim, one line. A tap dismisses it (forget); it goes by
itself 6 s after the import finishes or fails. Same enter and exit as the undo bar. The line:
- scanning → "looking…"
- running → "bringing in · {current} of {total}"
- done → "brought in {imported}", plus " · {skipped} already here" when any were skipped; with
  nothing imported, "nothing new · {skipped} already here"; with nothing found at all, "nothing
  to bring in"
- failed → "import stopped · {reason}"

## §15 The capture sheet

Over the app the share came from. Behaviour as it is now. The look:

- **Scrim**: CaptureScrim at alpha 0.62, full size, its alpha the sheet's shown fraction; a tap
  dismisses (when dismissible).
- **One transition** drives scrim and sheet: in over 420 ms on `CubicBezierEasing(0.22, 1, 0.36,
  1)`, out over 240 ms FastOutLinearIn. The sheet's translationY = its height × (1 − shown) +
  drag. Finish only after the exit has played and two frames have drawn.
- **The sheet**: full width at the bottom, top corners 24dp, background surface (Ink), a 1dp
  hairline in outlineVariant (InkBorder) along the top edge only (both corner arcs and the line
  between, inset by half the stroke), then `navigationBarsPadding().imePadding()`, then padding
  start 20, end 20, top 10, bottom 16. Taps on it are swallowed. A drag moves it down; let go past
  96dp and it dismisses, otherwise it animates back.
- **Grabber**: centred, bottom padding 14, 36×4dp, radius 2, outlineVariant.
- **Content**: a Column with `animateContentSize(tween(320))` (it grows from "Saving…" to the
  preview rather than jumping), spacing 18:
  1. **Header**: a Row, centred, spacing 14: the **CrumbMark** at 42dp (below), then a Column,
     spacing 4: the title in serif 23sp lineHeight 27 onSurface, a polite live region ("Saving…",
     "Saved", "Saved 3", "Saved 2 of 3", "Kept already", "Removed", or the failure's words); and
     under it, when something was saved, the kind line in mono 12sp outline, one line ("image ·
     from whatsapp", "3 images", "2 images · 1 kept already"), or when it was kept already, when
     ("link · kept 3d ago", "image · kept just now", "2 images · kept before").
  2. Saved: the **preview** (below), compact while the note is being written; the **note**; then
     a Row, spacing 10, of two equal buttons: "Undo" (secondary) and "Done" (primary).
     The note's Row has "say it" at its end (§11); saying a note takes the note's place, the
     preview compact meanwhile, and once kept the end of the Row says "note said" (the voice glyph
     13dp in primary, mono 12sp outline). It goes on every memory saved, or on what is kept
     already.
     Kept already (nothing new written; capture C10): the same, with the preview of what is kept,
     the note's closed label "add another note" when it has a note already, and "Keep again" in
     Undo's place. The CrumbMark does not play.
     Failed: one full-width "Close" (secondary). Saving and Removed: nothing more.
- **Buttons**: 52dp tall, radius 26; primary: AmberBright with onPrimary text; secondary:
  surfaceVariant (InkElevated) with onSurface text; the label 15sp SemiBold (sans).
- **The note**: closed, a Row (radius 8, padding 4/2), spacing 8: "+" in mono 15sp primary and
  "add a note" in mono 12sp outline. Open: a box, radius 16, surfaceVariant, padding 16/14, a
  field in sans 15sp lineHeight 21 onSurface, cursor primary, sentence capitalisation, at most 5
  lines, focused at once, hint "who sent it, what it's for…" in outline.
- **Previews**, cards radius 16 on surfaceVariant:
  - One picture: height = width × aspect clamped 160–300dp (220 until loaded; 110 while the note
    is open); Crop TopCenter; its caption below in sans 14sp lineHeight 20 onSurfaceVariant, at
    most 2 lines, not while compact; spacing 10.
  - Several files: a Row, spacing 8, of up to 3 square tiles sharing the width (weight 1,
    aspectRatio 1, radius 12, surfaceVariant): a picture Crop TopCenter, or DocumentGlyph 34×44
    for a PDF; on the last tile, when there are more, black α0.55 with "+N" in 20sp SemiBold white.
  - A PDF: padding 16, spacing 12: a Row, spacing 14: DocumentGlyph 40×52, then the title (or
    "Document") in 16sp Medium lineHeight 21 onSurface, at most 2 lines, over "pdf · 1.2 MB"
    (extension · size) in mono 11sp outline; the caption below as for a picture.
  - A link: padding 16, spacing 8: a Row, spacing 8: a 7dp primary dot and the host in mono 12sp
    primary; the headline (the page title, else the text) in 16sp Medium lineHeight 22
    onSurface, at most 3 lines; when there is a title, the URL in mono 11sp outline, one line.
  - Text: padding 16, the text in 16sp lineHeight 23 onSurface, at most 6 lines (2 while compact).
  - A voice note: as a PDF, with a 44dp AmberContainerDark circle holding the voice glyph 20dp in
    primary in the document's place, "Voice note" (or its title), and "ogg · 0:42" (extension ·
    length). Among several files, its tile holds the voice glyph 30dp in primary.
- **CrumbMark(play, size)**, on a 46-unit square: crumbs (8, 37) r 3.2 CrumbFaint; (17, 30) r 4.4
  CrumbDim; (27.5, 21) r 5.8 CrumbMid; (37, 10) r 7 CrumbBright. Not playing: all at alpha 0.35.
  Playing (on Saved): the brightest drops in, p 0→1 over 700 ms on `CubicBezierEasing(0.34, 1.4,
  0.64, 1)`, alpha min(1, 2.2p), scale 0.4 + 0.6p, offset y −26u × (1 − p); the other three settle
  behind it, Mid after 90 ms, Dim after 160 ms, Faint after 220 ms, each t 0→1 over 420 ms, alpha
  t, scale 0.5 + 0.5t.

## §16 Words

| Where | Words |
|---|---|
| Wordmark | breadcrumb |
| Search hint / clear | search your memory / Clear search |
| Empty mosaic | Nothing kept yet / Start here: the + brings in the screenshots and PDFs already on your phone. / or share anything to breadcrumb from any app,\nor copy it and tap the breadcrumb tile |
| No results | Nothing matches that / try fewer words, or describe it\nthe way you'd say it out loud |
| Words only | No saved words match / ranked search is out of reach, so this\nlooked for the words themselves |
| Detail actions | Open original / Open link / Copy text / Original isn't on this phone / Share / Delete |
| Detail labels | saved / from / taken / status / text found / from the page / what it shows / your note / edit / Done |
| Voice notes | Play / Pause / listening… / no words heard / Voice note / voice note / Say it / back / discard / recording / breadcrumb needs the microphone to keep a voice note / allow the microphone / couldn't record that — try again? / say it / remove / note said |
| Toasts | Copied / No app here can open this |
| Undo bar | deleted / Undo |
| "+" | Keep something / Keep something — a draft is waiting / a thought, a link, anything… / a note about it — who sent it, what it's for… / photo / pdf / Keep / Remove / couldn't keep that — try again? / bring in what's already on the phone |
| Import | Bring in / back / A whole folder / every picture and PDF in it — screenshots, usually / Only these pictures / pick them yourself / PDFs, or a bookmarks export / from the file picker / A breadcrumb backup / from another phone, or one you saved |
| Backup | back up / Everything kept / %s · %s on this phone / Save a backup / every memory, its originals and what the phone made of them, in one file you keep / Restore a backup / its memories join these; nothing here is replaced |
| Shortcut | Keep / Keep something / Say it / Say something |
| First run | Getting search ready / Search is ready / Breadcrumb finds what you kept by what it means… / download it on mobile data now / saving, and search by words, work already;\nsearch by meaning joins in when this is done / Start keeping / Done |
| Capture | Saving… / Saved / Saved %d / Saved %d of %d / Removed / Undo / Done / Close / +%d / Document / add a note / who sent it, what it's for… / Nothing to save / Couldn't save that / Kept already / Keep again / add another note |

All in strings.xml.

## §17 The text rules (`ResultText`, pure, JVM-tested)

- **words(phrase)**: lowercase, split on anything not a letter or digit, keep words of 2+
  characters not in the filler set {a, an, the, that, this, these, those, it, its, i, me, my, we,
  our, from, of, in, on, at, to, for, with, and, or, about, by, is, was, one, thing, stuff, saved,
  save, some, someone, sent, where, what, which}, distinct.
- **fragment(texts, words, lead = 28, tail = 80)**: searched in the order rawText, note,
  extractedText, readText, title, summary, each with whitespace collapsed; the first place any
  word (longest first, case-insensitive) begins a word (not preceded by a letter or digit); the
  hit runs to the end of that word ("intern" lights all of "internship"); the lead-in is the last
  28 characters before it, cut at a word and prefixed "…"; the tail the next 80, cut at a word
  with "…". Null when no word appears (a match by meaning).
- **title(memory, summary)**: LINK: title ?: firstLine(rawText) ?: "Link". TEXT: firstLine(rawText)
  ?: title ?: "Note". AUDIO: title ?: firstLine(what was said) ?: "Voice note". IMAGE: title ?: firstLine(rawText) ?: summary ?: headline ?: "Image". PDF: title ?: summary ?:
  headline ?: "Document". **headline** (`Headline`, for when there is no summary): a picture's
  first real line of OCR text, skipping the clock, network and battery lines, lone app words
  ("Inbox", "to me") and lines mostly symbols; a PDF's first real line.
- **firstLine(text, max = 90)**: the first non-blank line, trimmed; past 90 characters cut at a
  word with "…".
- **opening(text, max)**: whole when short; else cut at a word with " …".
- **host**: the first URL's host, lowercase, without "www.", only when it has a dot.
- **age(then, now)**: "now" under a minute, then "Nm", "Nh", "Nd" (under 7 days), "Nw" (under 30
  days), "Nmo" (under a year), "Ny".
- **tileMeta**: age · source lowercase. **rowMeta**: age · source · chips joined " + "
  lowercase ("2d · whatsapp · image + link").
- **status(memory)**: extractedText null and a LINK → "page not read yet"; null and an IMAGE or
  PDF with a stored file → "still reading it"; null and a voice note with a stored file → "still
  listening to it"; else null. (Until local-first L7 it also said
  what was not sent yet.)
- **kept(total)**: "17 kept".
- **counter(shown, total, typed, interpretation, today, status)**: "{shown} of {total}", then the
  phrase typed when there is no interpretation; otherwise the query (when not blank), the types
  ("images", "links", "pdfs", "notes", "voice notes", joined " or "), the dates, and "from {source lowercase}";
  then the status. Joined " · ": "1 of 17 · internship · images · apr".
- **dates(from, to, today)**: a whole calendar month → "apr" ("apr 2025" when not this year); one
  day → "18 sep"; a range → "1 apr – 15 may"; only from → "since 1 apr"; only to → "until 30 apr".
  Years only when not this year.
- **saved(then, now)**: "12 sep · " + today / yesterday / N days ago (under 14) / N weeks ago
  (under 60 days) / N months ago (under 730 days) / N years ago.
- **taken**: the day alone, "18 apr". **monthOf**: "apr".

## §18 Haptics

Through `LocalHapticFeedback.performHapticFeedback`, which follows the phone's Touch feedback
setting: SegmentTick when ranked results land; Confirm on delete; LongPress on the wordmark;
GestureThresholdActivate when a sheet's pull crosses its dismiss point.

## §20 Everything kept: backing up and restoring

A SheetLayer opened from the masthead's count. Grabber; a scrolling Column, padding 22
horizontal, top 6, spacing 14:
- "Everything kept" in serif 26sp lineHeight 30, over (spacing 6) "17 kept · 13 MB on this phone"
  in mono 12sp BoneDim (the originals' size, read off the main thread; the count alone until
  then).
- Two choices, the import panel's cards (§14): "Save a backup" / "every memory, its originals and
  what the phone made of them, in one file you keep": the system's save dialog for
  `breadcrumb-2026-10-27.zip` (application/zip). "Restore a backup" / "its memories join these;
  nothing here is replaced": the document picker for a zip.
- The backup's line, when there is one, in mono 12sp AmberBright.
- A navigationBarsPadding spacer of 20dp at the foot.
- Picking closes the sheet at once; the work runs on the application's scope.

**The backup bar**, over the search field after the import bar: the import bar's pill (§14),
walking while it runs; a tap clears a finished one, and it goes by itself 6 s after it finishes.
The line:
- saving → "backing up · {done} of {files}"; saved → "backed up {memories} · 13 MB" ("0.4 MB"
  under a megabyte)
- restoring → "restoring · {done} of {files}"; restored → "restored {added}", plus " · {already}
  already here" when any were; "nothing new · {already} already here"; "nothing in that backup"
- failed → "backup stopped · {reason}" or "restore stopped · {reason}": "that isn't a breadcrumb
  backup", "that file is damaged", "the phone is out of space", "couldn't read or write the
  file"

## §19 The model's first run

**FirstRunScreen(status, firstTime, onMobileData, onContinue)**, in place of the home screen (as
the debug list is) on the first launches while the model isn't on the phone, until "Start
keeping"; opened again from the model's bar, with "Done". Back closes it as Start keeping does.
Full size on Ink, a Column with statusBarsPadding and navigationBarsPadding, padding start 26,
end 26, top 24, bottom 22:
- A Row, spacing 10: CrumbTrail 22dp (walking while it downloads, checks, or is about to start),
  "breadcrumb" in serif 24sp lineHeight 26.
- A spacer, weight 1.
- "Getting search ready" ("Search is ready" once it is) in serif 30sp lineHeight 34; 14dp; "Breadcrumb
  finds what you kept by what it means, with a small model that runs on this phone. It downloads
  once, 388 MB, and works offline from then on. Nothing you keep leaves the phone." in sans 16sp
  BoneDim lineHeight 24; 28dp.
- **The bar**: full width, 6dp tall, radius 3, InkElevated, AmberBright as far as it has got
  (eased over 400 ms, read while drawing). Under it, 10dp down, SpaceBetween: what it is doing
  ("downloading", "waiting for wi-fi", "waiting for the internet", "starting", "checking it",
  "ready") in mono 12sp BoneDim, and "142 of 388 MB" (megabytes of a million bytes) in mono 12sp
  Bone.
- While it waits for Wi-Fi, 16dp down, the chip "download it on mobile data now" (mono 12sp
  BoneDim, InkElevated, border InkFieldBorder, radius 20, padding 14/11); once tapped it goes.
- A spacer, weight 1; "saving, and search by words, work already;\nsearch by meaning joins in
  when this is done" in mono 11sp InkOutline lineHeight 18; 16dp; the button, full width, 52dp,
  radius 26, AmberBright, "Start keeping" (or "Done") in sans 15sp SemiBold AmberOnContainer.

**The model's bar**, over the search field above the import bar, until the model is ready: the
import bar's pill (§14, `LineBar`), its trail walking while it downloads or checks: "getting
search ready · 37%" (never 100 before it is ready), "getting search ready · checking it",
"search by meaning waits for wi-fi", "… waits for the internet", "getting search ready…". A tap
opens the first-run screen.
