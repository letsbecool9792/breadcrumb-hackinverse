import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { encode, QrCodeDataType } from 'uqr'
import { EASE } from './ease.ts'
import { CrumbMark } from './glyphs.tsx'
import { APK } from './links.ts'

// On a computer the download is no use where it is, so the page offers it as
// a code for the phone's camera: a button that brings the code up, large.
export function QrButton() {
  const [open, setOpen] = useState(false)
  const trigger = useRef<HTMLButtonElement>(null)
  const close = useRef<HTMLButtonElement>(null)
  const title = useId()

  // While it is up: Escape closes it, focus stays in it, and the page beneath
  // doesn't scroll. Closed, focus goes back to the button.
  useEffect(() => {
    if (!open) return
    close.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
      if (e.key === 'Tab') {
        e.preventDefault()
        close.current?.focus()
      }
    }
    addEventListener('keydown', onKey)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const opener = trigger.current
    return () => {
      removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      opener?.focus({ preventScroll: true })
    }
  }, [open])

  return (
    <>
      <button
        ref={trigger}
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        className="group inline-flex items-center gap-2.5 rounded-full border border-ink-field-border bg-ink-elevated/70 py-1.5 pr-4 pl-1.5 font-mono text-xs text-bone-dim backdrop-blur-md transition-colors hover:border-amber-container hover:text-bone"
      >
        <span className="grid size-7 place-items-center rounded-full bg-ink text-amber transition-transform duration-200 group-hover:scale-110">
          <QrGlyph />
        </span>
        on a computer? scan a code
      </button>

      {/* Rendered at the page's root: the button sits inside things that move,
          and a fixed layer inside a transformed one would move with them. */}
      {createPortal(
        <AnimatePresence>
          {open && (
            <motion.div
              key="qr"
              className="fixed inset-0 z-[60] grid place-items-center p-6"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.25 }}
            >
              <div className="absolute inset-0 bg-scrim/75 backdrop-blur-sm" onClick={() => setOpen(false)} />
              <motion.div
                role="dialog"
                aria-modal="true"
                aria-labelledby={title}
                className="relative flex w-full max-w-[360px] flex-col items-center gap-6 rounded-[28px] border border-ink-border bg-ink-elevated px-8 pt-9 pb-8 text-center shadow-[0_40px_120px_-30px_rgb(255_196_107/0.35)]"
                initial={{ opacity: 0, y: 28, scale: 0.94 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 14, scale: 0.97 }}
                transition={{ duration: 0.45, ease: EASE }}
              >
                <button
                  ref={close}
                  type="button"
                  onClick={() => setOpen(false)}
                  aria-label="Close"
                  className="absolute top-4 right-4 grid size-9 place-items-center rounded-full bg-ink-field-border text-bone-dim transition-colors hover:text-bone"
                >
                  <svg width="11" height="11" viewBox="0 0 11 11" aria-hidden="true">
                    <path d="M1 1l9 9M10 1L1 10" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                  </svg>
                </button>
                <QrCode />
                <div className="flex flex-col gap-2">
                  <p id={title} className="font-serif text-2xl leading-7">
                    Scan with your phone
                  </p>
                  <p className="font-mono text-[11px] leading-[17px] text-bone-dim">
                    its camera opens the download there
                    <br />
                    android 10 and up
                  </p>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </>
  )
}

// The download's link as a code: its modules drawn as crumbs, rippling in from
// the middle, where the logo's crumbs drop in as they do on "Saved".
function QrCode() {
  const { data, size, types } = encode(APK, { ecc: 'H', border: 0 })
  const logo = Math.ceil(size * 0.24) | 1
  const from = (size - logo) / 2
  const middle = (size - 1) / 2
  const inLogo = (x: number, y: number) => x >= from - 0.5 && x < from + logo + 0.5 && y >= from - 0.5 && y < from + logo + 0.5
  const finders = [
    [0, 0],
    [size - 7, 0],
    [0, size - 7],
  ]

  return (
    <div className="relative size-[248px] rounded-[22px] bg-bone p-4">
      <svg viewBox={`0 0 ${size} ${size}`} className="size-full" role="img" aria-label="QR code for the Android download">
        {data.flatMap((row, y) =>
          row.map((dark, x) =>
            dark && types[y][x] !== QrCodeDataType.Position && !inLogo(x, y) ? (
              <circle
                key={`${x}-${y}`}
                cx={x + 0.5}
                cy={y + 0.5}
                r="0.44"
                fill="#171310"
                className="qr-dot"
                style={{ animationDelay: `${Math.round(Math.hypot(x - middle, y - middle) * 16)}ms` }}
              />
            ) : null,
          ),
        )}
        {finders.map(([x, y]) => (
          <g key={`${x}-${y}`} fill="#171310" className="qr-dot" style={{ animationDelay: '320ms' }}>
            <path
              fillRule="evenodd"
              d={`M${x + 2} ${y}h3a2 2 0 012 2v3a2 2 0 01-2 2h-3a2 2 0 01-2-2v-3a2 2 0 012-2zM${x + 2} ${y + 1}a1 1 0 00-1 1v3a1 1 0 001 1h3a1 1 0 001-1v-3a1 1 0 00-1-1z`}
            />
            <rect x={x + 2} y={y + 2} width="3" height="3" rx="0.9" />
          </g>
        ))}
      </svg>
      <span className="absolute inset-4 grid place-items-center">
        <span className="grid aspect-square place-items-center rounded-[12px] bg-ink" style={{ width: `${(logo / size) * 100}%` }}>
          <CrumbMark size={40} play />
        </span>
      </span>
    </div>
  )
}

// Three finder squares and a few modules: a code, small.
function QrGlyph() {
  return (
    <svg width="15" height="15" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
      <rect x="1.5" y="1.5" width="5" height="5" rx="1.2" />
      <rect x="9.5" y="1.5" width="5" height="5" rx="1.2" />
      <rect x="1.5" y="9.5" width="5" height="5" rx="1.2" />
      <g fill="currentColor" stroke="none">
        <circle cx="10.5" cy="10.5" r="1" />
        <circle cx="13.5" cy="13.5" r="1" />
        <circle cx="13.5" cy="10.5" r="1" />
        <circle cx="10.5" cy="13.5" r="1" />
      </g>
    </svg>
  )
}
