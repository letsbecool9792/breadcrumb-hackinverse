import { AnimatePresence, motion, useInView } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import { CrumbTrail } from './glyphs.tsx'
import { MEMORIES } from './data.ts'
import { ResultRow } from './memories.tsx'
import { SearchField } from './SearchField.tsx'

// Airplane mode, on a switch: the phone goes dark and the search still finds
// the photo, because all of it runs on the phone.
export function Offline() {
  const card = useRef<HTMLDivElement>(null)
  const seen = useInView(card, { amount: 0.6, once: true })
  const [airplane, setAirplane] = useState(false)
  const [touched, setTouched] = useState(false)
  const [ranking, setRanking] = useState(false)

  // Turn it on once by itself, the first time it is seen.
  useEffect(() => {
    if (!seen || touched) return
    const timer = window.setTimeout(() => flip(true), 1100)
    return () => window.clearTimeout(timer)
  }, [seen, touched])

  // Every change searches again, so the answer is seen arriving.
  useEffect(() => {
    if (!ranking) return
    const timer = window.setTimeout(() => setRanking(false), 600)
    return () => window.clearTimeout(timer)
  }, [ranking])

  function flip(on: boolean) {
    setAirplane(on)
    setRanking(true)
  }

  return (
    <div ref={card} className="mx-auto flex w-full max-w-[440px] flex-col gap-5 rounded-[32px] border border-ink-border bg-ink p-4 sm:p-5">
      <div className="flex items-center justify-between px-2 font-mono text-[11px] text-bone-dim">
        <span>9:41</span>
        <span className="flex h-4 items-center gap-2">
          <AnimatePresence mode="popLayout" initial={false}>
            {airplane ? (
              <motion.span key="plane" initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 6 }}>
                <PlaneIcon />
              </motion.span>
            ) : (
              <motion.span key="bars" className="flex items-center gap-2" initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 6 }}>
                <SignalIcon />
                <WifiIcon />
              </motion.span>
            )}
          </AnimatePresence>
          <span className="h-2.5 w-5 rounded-[3px] border border-bone-dim p-[1.5px]">
            <span className="block h-full w-3/4 rounded-[1px] bg-bone-dim" />
          </span>
        </span>
      </div>

      <button
        type="button"
        role="switch"
        aria-checked={airplane}
        onClick={() => {
          setTouched(true)
          flip(!airplane)
        }}
        className="flex items-center justify-between rounded-[20px] bg-ink-elevated px-5 py-4 text-left"
      >
        <span className="flex items-center gap-3">
          <span className={airplane ? 'text-amber' : 'text-bone-dim'}>
            <PlaneIcon />
          </span>
          <span className="text-[15px] font-medium">Airplane mode</span>
        </span>
        <span className={`relative h-7 w-12 rounded-full transition-colors duration-300 ${airplane ? 'bg-amber' : 'bg-ink-grabber'}`}>
          <motion.span
            className={`absolute top-1 size-5 rounded-full ${airplane ? 'bg-amber-on' : 'bg-bone-dim'}`}
            animate={{ left: airplane ? 24 : 4 }}
            transition={{ type: 'spring', stiffness: 600, damping: 32 }}
          />
        </span>
      </button>

      <SearchField text="ramen" ranking={ranking} />

      <div className="min-h-[96px]" aria-live="polite">
        {ranking ? (
          <div className="px-3 pt-4">
            <CrumbTrail size={34} walking />
          </div>
        ) : (
          <div key={String(airplane)}>
            <ResultRow m={MEMORIES.ramenPhoto} top index={0} />
            <span className="sr-only">Found the ramen photo{airplane ? ', with airplane mode on' : ''}.</span>
          </div>
        )}
      </div>
    </div>
  )
}

function PlaneIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M21 16v-2l-8-5V3.5a1.5 1.5 0 00-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5z" />
    </svg>
  )
}

function SignalIcon() {
  return (
    <svg width="14" height="12" viewBox="0 0 14 12" fill="currentColor" aria-hidden="true">
      <rect x="0" y="8" width="2.5" height="4" rx="0.6" />
      <rect x="3.8" y="5.5" width="2.5" height="6.5" rx="0.6" />
      <rect x="7.6" y="3" width="2.5" height="9" rx="0.6" />
      <rect x="11.4" y="0" width="2.5" height="12" rx="0.6" />
    </svg>
  )
}

function WifiIcon() {
  return (
    <svg width="14" height="12" viewBox="0 0 16 14" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
      <path d="M1 5a10 10 0 0114 0M3.5 8a6.4 6.4 0 019 0M6 11a2.8 2.8 0 014 0" />
    </svg>
  )
}
