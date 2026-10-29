import { encode, QrCodeDataType } from 'uqr'
import { CrumbTrail } from './glyphs.tsx'

// The newest release's APK. GitHub sends this to the latest full release's
// file named breadcrumb.apk; it answers once the first one is published
// (docs/workstreams/website.md W2).
const APK = 'https://github.com/letsbecool9792/breadcrumb-hackinverse/releases/latest/download/breadcrumb.apk'

// The page's one main action, so it takes the amber (design.md §0).
export function DownloadButton() {
  return (
    <div className="flex flex-col items-center gap-4">
      <div className="relative">
        <span className="download-glow pointer-events-none absolute -inset-x-8 -inset-y-5 rounded-full bg-amber/25 blur-2xl" aria-hidden="true" />
        <a
          href={APK}
          className="group relative isolate inline-flex h-16 items-center gap-3.5 overflow-hidden rounded-full bg-amber pr-3 pl-5 text-amber-on shadow-[0_0_0_1px_rgb(255_196_107/0.5),0_18px_50px_-12px_rgb(255_196_107/0.6)] transition-transform duration-200 hover:-translate-y-0.5 active:translate-y-0 active:scale-[0.97]"
        >
          <span className="download-shine" aria-hidden="true" />
          <AndroidGlyph />
          <span className="flex flex-col items-start gap-1 text-left leading-none">
            <span className="font-mono text-[10px] tracking-[1.4px] uppercase opacity-70">Download for</span>
            <span className="text-xl font-semibold tracking-[-0.01em]">Android</span>
          </span>
          <span className="ml-3 grid size-10 place-items-center rounded-full bg-amber-on text-amber">
            <svg
              width="16"
              height="16"
              viewBox="0 0 16 16"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="transition-transform duration-200 group-hover:translate-y-0.5"
              aria-hidden="true"
            >
              <path d="M8 2.5v10M3.5 8.5L8 13l4.5-4.5" />
            </svg>
          </span>
        </a>
      </div>
      <p className="font-mono text-[11px] text-ink-outline">apk · android 10 and up · free, no account</p>
    </div>
  )
}

// The Android robot's head, which marks a download for Android.
function AndroidGlyph() {
  return (
    <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M3 17.5a9 9 0 0118 0z" />
      <path d="M6.6 7.4L5 4.6M17.4 7.4L19 4.6" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <circle cx="8.6" cy="13.3" r="1.15" fill="var(--color-amber)" />
      <circle cx="15.4" cy="13.3" r="1.15" fill="var(--color-amber)" />
    </svg>
  )
}

// On a computer, the same download as a code to scan with the phone: its
// modules drawn as crumbs, the trail in the middle.
export function DownloadCode() {
  const { data, size, types } = encode(APK, { ecc: 'H', border: 0 })
  const logo = Math.ceil(size * 0.24) | 1
  const from = (size - logo) / 2
  const inLogo = (x: number, y: number) => x >= from - 0.5 && x < from + logo + 0.5 && y >= from - 0.5 && y < from + logo + 0.5
  const finders = [
    [0, 0],
    [size - 7, 0],
    [0, size - 7],
  ]

  return (
    <div className="flex items-center gap-5 rounded-[24px] border border-ink-border bg-ink-elevated p-4 pr-6">
      <div className="relative size-[132px] shrink-0 rounded-2xl bg-bone p-2.5">
        <svg viewBox={`0 0 ${size} ${size}`} className="size-full" role="img" aria-label="QR code for the Android download">
          {data.flatMap((row, y) =>
            row.map((dark, x) =>
              dark && types[y][x] !== QrCodeDataType.Position && !inLogo(x, y) ? <circle key={`${x}-${y}`} cx={x + 0.5} cy={y + 0.5} r="0.44" fill="#171310" /> : null,
            ),
          )}
          {finders.map(([x, y]) => (
            <g key={`${x}-${y}`} fill="#171310">
              <path
                fillRule="evenodd"
                d={`M${x + 2} ${y}h3a2 2 0 012 2v3a2 2 0 01-2 2h-3a2 2 0 01-2-2v-3a2 2 0 012-2zM${x + 2} ${y + 1}a1 1 0 00-1 1v3a1 1 0 001 1h3a1 1 0 001-1v-3a1 1 0 00-1-1z`}
              />
              <rect x={x + 2} y={y + 2} width="3" height="3" rx="0.9" />
            </g>
          ))}
        </svg>
        <span className="absolute inset-2.5 grid place-items-center">
          <span className="grid aspect-square place-items-center rounded-[9px] bg-ink" style={{ width: `${(logo / size) * 100}%` }}>
            <CrumbTrail size={20} />
          </span>
        </span>
      </div>
      <div className="flex max-w-[11rem] flex-col gap-2 text-left">
        <p className="font-serif text-lg leading-6">On a computer?</p>
        <p className="font-mono text-[11px] leading-[17px] text-bone-dim">scan it with your phone's camera to download there</p>
      </div>
    </div>
  )
}
