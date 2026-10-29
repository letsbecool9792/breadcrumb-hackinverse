import { APK } from './links.ts'
import { QrButton } from './QrCode.tsx'

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
      <div className="hidden lg:block">
        <QrButton />
      </div>
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
