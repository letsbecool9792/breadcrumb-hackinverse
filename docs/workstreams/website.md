# The website

Roadmap 5. Breadcrumb isn't going to the Play Store yet, so the website is how people get it: one
landing page that says what the app is and, once there are releases, downloads the latest one.
It lives in `web/` and is a static site with no server of its own.

## What it is

- **One page, in the app's look**: the tokens, faces and words of [`design.md`](../design.md),
  dark always. Tailwind's own palette is dropped (`web/src/index.css`), so only the app's
  colours exist there. The glyphs (`web/src/glyphs.tsx`) are drawn to design.md §2 and §15 on
  the same unit grids.
- **What it shows**: the app's search playing through example phrases (the field, the counter
  line, ranked rows with the matched word lit); save, forget, search, find; the ways to save,
  beside the capture sheet saying "Saved"; a phrase taken apart into words, kind, date and app;
  everything staying on the phone, beside the first-run bar; reference memory. The example
  memories are made up.
- **Motion only while it is seen**, as in the app (§0): the example searches and the capture
  sheet's mark play while on screen, and nothing moves under reduced motion. The page is
  complete before anything moves: it opens on a finished search.
- **Nothing from a third party.** The fonts ship with the site (Fontsource, not Google Fonts):
  a page about keeping things private doesn't hand its visitors to a font host. No analytics.
- **Vite + React + TypeScript + Tailwind 4**, scaffolded with
  `npm create vite@latest web -- --template react-ts`, at Suparno's request the one exception to
  "scaffolding is done by hand"; Tailwind through `@tailwindcss/vite`. `base: './'`, so the build
  works from any folder that serves it (a GitHub Pages project path, a preview).

## Getting a release to the page

Decided at W2. What there is to choose from:

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
  they kept. Back the key up in two places; never commit it. `v1.0-demo` was signed with a debug
  key.
- **Suparno's phone runs debug builds**, which a release-signed APK can't install over. Test
  releases on a second phone, or give debug builds `applicationIdSuffix = ".debug"` (which moves
  the dev install's data again).
- **`/releases/latest/` skips prereleases**: `v1.0-demo` is one, so the link answers 404 until
  a real release exists.
- **Size**: `v1.0-demo` is 152 MB, debug and unminified. A release build, arm64 only and shrunk
  by R8, should be far smaller; measure it.
- **Google's developer verification**: apps installed from outside Play are to come from a
  verified developer, enforced from September 2026 in Brazil, Indonesia, Singapore and Thailand
  and everywhere in 2027 (an unverified app then needs an "advanced" install flow or adb). There
  is a lighter tier for students and hobbyists. Verify before 2027.

## Steps

- [x] **W1. The page** (Suparno, 2026-10-29)
  - `web/`: `App.tsx` (the sections), `SearchDemo.tsx`, `CaptureSheet.tsx`, `glyphs.tsx`,
    `lib/motion.ts` (reduced motion; whether a thing is on screen).
  - In the download's place, "download for android · soon" (`DownloadSlot` in `App.tsx`).
  - Checks: `npm run build` (type-checks first) and `npm run lint`; at 390 and 1440 px wide in
    Chromium, no sideways scroll and no console errors.
  - *Check:* on a phone and a computer: the search plays through its four examples, only while on
    screen; "try" switches between them; the capture sheet's crumbs drop in on "Saved" as it
    comes into view; with reduced motion on (Android: Remove animations), nothing moves and every
    part still reads.
- [ ] **W2. The download**
  - The release flow (above): the signing key, a signed `assembleRelease`, the first real release
    with `breadcrumb.apk`.
  - `DownloadSlot` becomes the link, with the version, size and date; a line on allowing the
    install; the Obtainium link.
  - *Check:* on a phone that has never had Breadcrumb, from the page: download, install, open,
    keep something.
- [ ] **W3. Hosting**: GitHub Pages (built by Actions from `web/`, no new account, one version
  only), or Cloudflare Pages or Netlify (free, no card, a preview link for every pushed branch).
  Then the README links to it.

## Rules

- **The page says only what the release it offers does.** It names voice notes and backups,
  both still waiting on their checks by hand; when W2 puts in the first release, read the copy
  against that release.
- **Nothing loads from anywhere but the site.** Fonts, scripts and pictures ship with it.
- **The APK never goes into git**, in `web/` or anywhere else.
