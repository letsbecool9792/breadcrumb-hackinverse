# The website

Roadmap 5. Breadcrumb isn't going to the Play Store yet, so the website is how people get it: one
landing page that says what the app is and, once there are releases, downloads the latest one.
It lives in `web/` and is a static site with no server of its own.

## What it is

- **One page, in the app's look**: the tokens, faces and words of [`design.md`](../design.md),
  dark always. Tailwind's own palette is dropped (`web/src/index.css`), so only the app's
  colours exist there. The glyphs (`web/src/glyphs.tsx`) are drawn to design.md §2 and §15 on
  the same unit grids.
- **Shown, not explained**: words are few, the app's own screens do the telling. A tilted field
  of memories drifting behind the headline, now and then one lit as found; the home screen
  playing through searches, the matches leaving their tiles for a ranked list with their
  pictures travelling (§10); the four ways to save as small loops (share sheet, selection
  menu, Quick Settings tile, the "+" taking a voice note); airplane mode on a switch, with the
  search still finding; a trail of crumbs dropping down the page as it is read, and behind the
  pointer on a computer. Things arrive as the page scrolls to them (titles rising word by
  word, screens standing up, the save scenes dealt in), and the hero lifts away as it is left.
  On a computer the search section fits one screenful, its "try" chips beside the screen. The
  example memories are made up.
- **Motion only while it is seen**, as in the app (§0), through Motion (`motion`, pinned to a
  release at least two weeks old) and CSS: loops pause off screen, and with reduced motion on
  nothing moves and each scene rests on its last beat, where the thing is saved.
- **Nothing from a third party.** The fonts ship with the site (Fontsource, not Google Fonts):
  a page about keeping things private doesn't hand its visitors to a font host. No analytics.
- **Credited**: the footer says "built by Suparno Saha", linking https://suparno.me, his own
  site; the page's head names him as its author.
- **Vite + React + TypeScript + Tailwind 4**, scaffolded with
  `npm create vite@latest web -- --template react-ts`, at Suparno's request the one exception to
  "scaffolding is done by hand"; Tailwind through `@tailwindcss/vite`. `base: './'`, so the build
  works from any folder that serves it (a GitHub Pages project path, a preview).

## Getting a release to the page

Decided (2026-10-29): GitHub Releases, uploaded by hand (1), until releases are frequent
enough for Actions (2). What there was to choose from:

1. **GitHub Releases, uploaded by hand.** Build a signed release APK, make a release and attach
   it, always under one name (`breadcrumb.apk`). The page links to
   `https://github.com/letsbecool9792/breadcrumb-hackinverse/releases/latest/download/breadcrumb.apk`,
   which GitHub sends to the newest release's file, so a release never needs the site rebuilt.
   The page can ask GitHub's API for the version, size and date, and keep the bare link when that
   fails. Free, no new account. The likely start.
2. **GitHub Releases, built by GitHub Actions** on a pushed tag. The same link; the signing key
   goes into the repo's secrets. Worth it once releases are frequent.
3. **Obtainium**, over either: a free app that watches a repo's releases and offers updates. The
   app can't update itself, so the page should point to it.

Not these: the APK served from the website's host (Cloudflare Pages takes 25 MiB a file, git
refuses 100 MB, and a binary in git stays in its history for good); F-Droid (ML Kit is closed
source); Firebase App Distribution or Play's test tracks (testers invited by email, not a public
download).

Whichever it is:

- **One release signing key, kept for good.** Android installs an update only when it is signed
  like the app already there; otherwise the person must uninstall, and that deletes everything
  they kept. Back the key up in two places; never commit it. It is
  `D:\keys\breadcrumb-release.jks` on Suparno's machine, alias `breadcrumb`, made 2026-10-29;
  `v1.0-demo` was signed with a debug key.
- **Suparno's phone runs the release** (v1.0, since 2026-10-29), so a debug build can't
  install over it (CLAUDE.md, "Android"). A new release installs over it as an update. Debug
  builds could get `applicationIdSuffix = ".debug"` to sit beside it; undecided.
- **`/releases/latest/` skips prereleases**: `v1.0-demo` is one, so the link answers 404 until
  a real release exists.
- **Size**: `v1.0-demo` is 152 MB, debug and unminified. The release build is arm64 only (LiteRT-LM
  ships no 32-bit ARM, so those phones couldn't search anyway): 68 MB, about half of it code.
  R8 could shrink that, but needs keep rules for Room, LiteRT-LM and ML Kit and a full check on
  the phone, so it is off for now.
- **Google's developer verification**: apps installed from outside Play are to come from a
  verified developer, enforced from September 2026 in Brazil, Indonesia, Singapore and Thailand
  and everywhere in 2027 (an unverified app then needs an "advanced" install flow or adb). There
  is a lighter tier for students and hobbyists. Verify before 2027.

## Releasing

Each release, from `android/`:

1. Raise `versionCode` by one and set `versionName` in `app/build.gradle.kts`; a phone refuses
   an update whose `versionCode` isn't higher. v1.0 was 1, "1.0"; the next is 2.
2. `./gradlew assembleRelease`, and copy `app/build/outputs/apk/release/app-release.apk` to
   `breadcrumb.apk`. An `app-release-unsigned.apk` instead means `keystore.properties` is missing
   (copy `keystore.properties.example`, fill in the passwords).
3. Install it on a phone without a debug build (`adb install -r breadcrumb.apk`): open it, keep
   something, find it.
4. `gh release create v1.0 breadcrumb.apk --title "Breadcrumb 1.0" --notes "…"`, with that
   release's version. Never `--prerelease`, and the file named exactly `breadcrumb.apk`, or the
   page's link misses it.
5. `curl -sI https://github.com/letsbecool9792/breadcrumb-hackinverse/releases/latest/download/breadcrumb.apk`
   answers 302.

## Steps

- [x] **W1. The page** (Suparno, 2026-10-29)
  - `web/src`: `App.tsx` (the page), `Hero.tsx` (the field and headline), `MosaicSearch.tsx`,
    `SaveScenes.tsx`, `Offline.tsx`, `Trail.tsx`; the pieces they share: `data.ts` (the made-up
    memories), `memories.tsx` (tiles and rows), `art.tsx` (the pictures), `glyphs.tsx`,
    `SearchField.tsx`, `Reveal.tsx` (arriving on scroll), `ease.ts`, `links.ts` (the APK's and
    the source's links), `QrCode.tsx`.
  - The download (`DownloadButton.tsx`): the page's one amber button, "Download for Android",
    linked to `releases/latest/download/breadcrumb.apk`, the newest full release's file. On a
    computer, "on a computer? scan a code" under it brings up the same link as a large QR code
    in a dialog, drawn locally (`uqr`) in crumbs around the trail; it decodes to the link.
  - Checks: `npm run build` (type-checks first) and `npm run lint`; at 390 and 1440 px wide in
    Chromium, no sideways scroll and no console errors.
  - *Check:* on a phone and a computer: the field drifts and the headline's words roll over in
    their light, which is never empty; the home screen searches by itself while on screen, its pictures flying into the results and
    back, and "try" picks a search; the four save scenes loop (on a phone, swiped sideways);
    airplane mode turns on by itself once and the ramen photo is still found; on a computer,
    "scan a code" brings up a code a phone's camera opens, and Escape closes it; crumbs drop down
    the page's edge while scrolling, and each section arrives as it is reached; on a computer the
    search section and its chips fit one screen. With reduced motion on (Android: Remove animations),
    nothing moves and every part still reads.
- [x] **W2. The download** (Suparno, 2026-10-29)
  - The release key, made by Suparno and kept off the repo; release builds signed from
    `android/keystore.properties` (ignored by git), and arm64 only.
  - v1.0 on GitHub Releases as `breadcrumb.apk` (68 MB); the page's link answers 302 to it.
  - Later, perhaps: the version and size by the button, a line on allowing the install, and the
    Obtainium link.
- [x] **W3. Hosting** (Suparno, 2026-10-29): live at https://breadcrumb.suparno.me.
  - On Vercel's free Hobby plan, chosen over GitHub Pages for its preview links: every pushed
    branch gets one, so a change can be seen on a phone before it reaches `main`. Vercel builds
    the site from `web/` on each push; its settings live in Vercel's dashboard, not the repo.
  - `breadcrumb.suparno.me` is Suparno's domain (his portfolio is at the root), pointed at
    Vercel by a CNAME record at Namecheap, which holds the domain's DNS.
  - The README links to the site.
  - *Check:* on a phone that has never had Breadcrumb, from the hosted page: download, install,
    open, keep something.

## Rules

- **The page says only what the release it offers does.** It shows voice notes, still waiting
  on their check by hand; when W2 puts in the first release, check the page against that
  release.
- **Nothing loads from anywhere but the site.** Fonts, scripts and pictures ship with it.
- **The APK never goes into git**, in `web/` or anywhere else.
