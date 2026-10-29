import { useRef } from 'react'
import { CrumbMark, VoiceGlyph } from './glyphs.tsx'
import { useInView } from './lib/motion.ts'

// The capture sheet (docs/design.md §15), risen over the app a link was shared
// from. Its mark plays "Saved" each time it comes into view.
export function CaptureSheet() {
  const scene = useRef<HTMLDivElement>(null)
  const shown = useInView(scene, 0.6)

  return (
    <div
      ref={scene}
      className="relative mx-auto w-full max-w-[400px] overflow-hidden rounded-[28px] border border-ink-border"
      aria-label="The capture sheet after sharing a link from Instagram: Saved, link from instagram, with Undo and Done."
      role="img"
    >
      {/* The other app, behind the scrim. */}
      <div className="relative flex h-[132px] flex-col gap-3 bg-ink-media px-5 pt-5" aria-hidden="true">
        <div className="flex items-center gap-2.5">
          <div className="size-8 rounded-full bg-ink-grabber" />
          <div className="h-2 w-24 rounded-full bg-ink-grabber" />
        </div>
        <div className="h-24 rounded-xl bg-ink-border" />
        <div className="absolute inset-0 bg-scrim/60" />
      </div>

      <div className="relative -mt-4 rounded-t-[24px] border-t border-ink-border bg-ink px-5 pt-2.5 pb-4">
        <div className="mx-auto mb-3.5 h-1 w-9 rounded-sm bg-ink-border" />
        <div className="flex flex-col gap-[18px]">
          <div className="flex items-center gap-3.5">
            <CrumbMark size={42} play={shown} />
            <div className="flex min-w-0 flex-col gap-1">
              <p className="font-serif text-[23px] leading-[27px]">Saved</p>
              <p className="truncate font-mono text-xs text-ink-outline">link · from instagram</p>
            </div>
          </div>

          <div className="flex flex-col gap-2 rounded-2xl bg-ink-elevated p-4">
            <div className="flex items-center gap-2">
              <span className="size-[7px] rounded-full bg-amber" />
              <span className="font-mono text-xs text-amber">instagram.com</span>
            </div>
            <p className="line-clamp-3 text-base leading-[22px] font-medium">
              Hoshi Ramen Bar on Instagram: “the spicy miso is back, this week only”
            </p>
            <p className="truncate font-mono text-[11px] text-ink-outline">instagram.com/reel/C9xk2Tq7vLm</p>
          </div>

          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2 px-1">
              <span className="font-mono text-[15px] text-amber">+</span>
              <span className="font-mono text-xs text-ink-outline">add a note</span>
            </span>
            <span className="flex items-center gap-[7px]">
              <VoiceGlyph size={13} />
              <span className="font-mono text-xs text-bone-dim">say it</span>
            </span>
          </div>

          <div className="flex gap-2.5">
            <span className="grid h-[52px] flex-1 place-items-center rounded-[26px] bg-ink-elevated text-[15px] font-semibold">
              Undo
            </span>
            <span className="grid h-[52px] flex-1 place-items-center rounded-[26px] bg-amber text-[15px] font-semibold text-amber-on">
              Done
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}
