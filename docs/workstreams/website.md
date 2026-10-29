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
  pointer on a computer. The example memories are made up.
- **Motion only while it is seen**, as in the app (§0), through Motion (`motion`, pinned to a
  release at least two weeks old) and CSS: loops pause off screen, and with reduced motion on
  nothing moves and each scene rests on its last beat, where the thing is saved.
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
  - `web/src`: `App.tsx` (the page), `Hero.tsx` (the field and headline), `MosaicSearch.tsx`,
    `SaveScenes.tsx`, `Offline.tsx`, `Trail.tsx`; the pieces they share: `data.ts` (the made-up
    memories), `memories.tsx` (tiles and rows), `art.tsx` (the pictures), `glyphs.tsx`,
    `SearchField.tsx`.
  - The download (`DownloadButton.tsx`): the page's one amber button, "Download for Android",
    linked to `releases/latest/download/breadcrumb.apk`, which answers only once W2 has
    published a full release with that file. On a computer, the footer adds the same link as a
    QR code, drawn locally (`uqr`) in crumbs around the trail.
  - Checks: `npm run build` (type-checks first) and `npm run lint`; at 390 and 1440 px wide in
    Chromium, no sideways scroll and no console errors.
  - *Check:* on a phone and a computer: the field drifts and the headline's words change; the
    home screen searches by itself while on screen, its pictures flying into the results and
    back, and "try" picks a search; the four save scenes loop (on a phone, swiped sideways);
    airplane mode turns on by itself once and the ramen photo is still found; crumbs drop down
    the page's edge while scrolling. With reduced motion on (Android: Remove animations),
    nothing moves and every part still reads.
- [ ] **W2. The download**
  - The release flow (above): the signing key, a signed `assembleRelease`, the first real release
    with `breadcrumb.apk`.
  - The button's link answers once that release is out; then perhaps the version and size by
    it, a line on allowing the install, and the Obtainium link.
  - *Check:* on a phone that has never had Breadcrumb, from the page: download, install, open,
    keep something.
- [ ] **W3. Hosting**: GitHub Pages (built by Actions from `web/`, no new account, one version
  only), or Cloudflare Pages or Netlify (free, no card, a preview link for every pushed branch).
  Then the README links to it.

## Rules

- **The page says only what the release it offers does.** It shows voice notes, still waiting
  on their check by hand; when W2 puts in the first release, check the page against that
  release.
- **Nothing loads from anywhere but the site.** Fonts, scripts and pictures ship with it.
- **The APK never goes into git**, in `web/` or anywhere else.
