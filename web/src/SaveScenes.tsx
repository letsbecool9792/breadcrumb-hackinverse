import { AnimatePresence, motion, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Art } from './art.tsx'
import { EASE } from './ease.ts'
import { CrumbMark, CrumbTrail } from './glyphs.tsx'

// The four ways in, each a small loop that plays while it is on screen:
// the share sheet, the selection menu, the Quick Settings tile and the "+".

const SHEET = { type: 'tween', duration: 0.42, ease: [0.22, 1, 0.36, 1] } as const

// Steps through a scene's beats, round and round, while it is seen. With
// motion reduced it rests on the last beat, where the thing is saved.
function useBeats(durations: number[], seen: boolean): number {
  const reduced = useReducedMotion()
  const [beat, setBeat] = useState(0)
  useEffect(() => {
    if (!seen || reduced) return
    const timer = window.setTimeout(() => setBeat((b) => (b + 1) % durations.length), durations[beat])
    return () => window.clearTimeout(timer)
  }, [beat, seen, reduced, durations])
  return reduced ? durations.length - 1 : beat
}

// Dealt in one after another, each tipped a little, as the row comes into view.
const DEAL = {
  hidden: (i: number) => ({ opacity: 0, y: 80, rotate: i % 2 ? 4 : -4 }),
  shown: (i: number) => ({ opacity: 1, y: 0, rotate: 0, transition: { duration: 0.9, ease: EASE, delay: i * 0.1 } }),
}

function Scene({
  index,
  label,
  caption,
  children,
}: {
  index: number
  label: string
  caption: string
  children: (seen: boolean) => ReactNode
}) {
  const ref = useRef<HTMLElement>(null)
  const seen = useInView(ref, { amount: 0.5 })
  return (
    <motion.figure
      ref={ref}
      custom={index}
      variants={DEAL}
      className="flex h-[360px] w-[82%] shrink-0 snap-center flex-col overflow-hidden rounded-[24px] border border-ink-border bg-ink-media sm:w-auto"
    >
      <div className="relative flex-1 overflow-hidden" aria-hidden="true">
        {children(seen)}
      </div>
      <figcaption className="flex items-baseline justify-between gap-3 border-t border-ink-border px-5 py-4">
        <span className="font-mono text-xs text-amber">{label}</span>
        <span className="text-sm text-bone-dim">{caption}</span>
      </figcaption>
    </motion.figure>
  )
}

export function SaveScenes() {
  return (
    <motion.div
      className="-mx-5 flex snap-x snap-mandatory gap-3 overflow-x-auto px-5 pt-4 pb-2 [scrollbar-width:none] sm:mx-0 sm:grid sm:grid-cols-2 sm:overflow-visible sm:px-0 lg:grid-cols-4"
      initial="hidden"
      whileInView="shown"
      viewport={{ once: true, amount: 0.3 }}
    >
      <Scene index={0} label="share" caption="from any app">
        {(seen) => <ShareScene seen={seen} />}
      </Scene>
      <Scene index={1} label="select" caption="any text, anywhere">
        {(seen) => <SelectScene seen={seen} />}
      </Scene>
      <Scene index={2} label="tile" caption="whatever you copied">
        {(seen) => <TileScene seen={seen} />}
      </Scene>
      <Scene index={3} label="+" caption="a thought, said out loud">
        {(seen) => <PlusScene seen={seen} />}
      </Scene>
    </motion.div>
  )
}

function Saved({ kind, play }: { kind: string; play: boolean }) {
  return (
    <div className="flex items-center gap-3">
      <CrumbMark size={38} play={play} />
      <div className="flex min-w-0 flex-col gap-0.5">
        <p className="font-serif text-xl leading-6">Saved</p>
        <p className="truncate font-mono text-[11px] text-ink-outline">{kind}</p>
      </div>
    </div>
  )
}

const SHARE_BEATS = [1300, 1500, 450, 2900]

function ShareScene({ seen }: { seen: boolean }) {
  const beat = useBeats(SHARE_BEATS, seen)
  const apps = ['messages', 'mail', 'breadcrumb', 'drive']
  return (
    <>
      <div className="absolute inset-0 opacity-70">
        <Art name="chat" />
      </div>
      <motion.div className="absolute inset-0 bg-scrim" animate={{ opacity: beat > 0 ? 0.55 : 0 }} />
      <AnimatePresence>
        {(beat === 1 || beat === 2) && (
          <motion.div
            key="share"
            className="absolute inset-x-0 bottom-0 rounded-t-[22px] bg-ink-elevated px-5 pt-3 pb-6"
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%', transition: { duration: 0.2 } }}
            transition={SHEET}
          >
            <div className="mx-auto mb-4 h-1 w-9 rounded-sm bg-ink-grabber" />
            <p className="mb-4 font-mono text-[11px] text-ink-outline">share</p>
            <div className="grid grid-cols-4 gap-2">
              {apps.map((app) => (
                <div key={app} className="flex flex-col items-center gap-1.5">
                  {app === 'breadcrumb' ? (
                    <motion.span
                      className="relative grid size-12 place-items-center rounded-full bg-ink ring-1 ring-amber-container"
                      animate={{ scale: beat === 2 ? 0.86 : 1 }}
                    >
                      <CrumbTrail size={24} />
                      {beat === 2 && (
                        <motion.span
                          className="absolute inset-0 rounded-full border-2 border-amber"
                          initial={{ scale: 0.9, opacity: 1 }}
                          animate={{ scale: 1.7, opacity: 0 }}
                          transition={{ duration: 0.45 }}
                        />
                      )}
                    </motion.span>
                  ) : (
                    <span className="size-12 rounded-full bg-ink-grabber" />
                  )}
                  <span className={`font-mono text-[9px] ${app === 'breadcrumb' ? 'text-amber' : 'text-ink-outline'}`}>{app}</span>
                </div>
              ))}
            </div>
          </motion.div>
        )}
        {beat === 3 && (
          <motion.div
            key="saved"
            className="absolute inset-x-0 bottom-0 flex flex-col gap-4 rounded-t-[22px] border-t border-ink-border bg-ink px-5 pt-3 pb-5"
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%', transition: { duration: 0.24 } }}
            transition={SHEET}
          >
            <div className="mx-auto h-1 w-9 rounded-sm bg-ink-border" />
            <Saved kind="link · from whatsapp" play />
            <div className="flex gap-2">
              <span className="grid h-10 flex-1 place-items-center rounded-full bg-ink-elevated text-sm font-semibold">Undo</span>
              <span className="grid h-10 flex-1 place-items-center rounded-full bg-amber text-sm font-semibold text-amber-on">Done</span>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}

const SELECT_BEATS = [1100, 900, 1300, 2700]

function SelectScene({ seen }: { seen: boolean }) {
  const beat = useBeats(SELECT_BEATS, seen)
  return (
    <div className="absolute inset-0 flex flex-col bg-ink-media px-6 pt-14">
      <p className="mb-3 font-mono text-[10px] text-ink-outline">chrome · a reading list</p>
      <div className="relative">
        <AnimatePresence>
          {beat === 2 && (
            <motion.div
              className="absolute -top-14 left-0 flex items-center gap-3 rounded-full bg-ink-elevated px-4 py-2.5 text-[13px] shadow-[0_12px_30px_rgb(0_0_0/0.5)]"
              initial={{ opacity: 0, y: 6, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, transition: { duration: 0.15 } }}
            >
              <span className="text-bone-dim">Copy</span>
              <span className="text-bone-dim">Share</span>
              <span className="font-semibold text-amber">Breadcrumb</span>
            </motion.div>
          )}
        </AnimatePresence>
        <p className="text-[17px] leading-[27px] text-bone-dim">
          Arjun swears by{' '}
          <motion.span
            className="rounded-[3px] text-bone"
            animate={{ backgroundColor: beat >= 1 ? 'rgba(90, 68, 32, 0.95)' : 'rgba(90, 68, 32, 0)' }}
            transition={{ duration: 0.35 }}
          >
            Piranesi by Susanna Clarke
          </motion.span>
          . Read it without looking anything up.
        </p>
      </div>
      <AnimatePresence>
        {beat === 3 && (
          <motion.div
            className="absolute inset-x-4 bottom-4 rounded-[18px] border border-ink-border bg-ink px-4 py-3"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 12, transition: { duration: 0.2 } }}
            transition={SHEET}
          >
            <Saved kind="note · from chrome" play />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

const TILE_BEATS = [1300, 380, 2900]

function TileScene({ seen }: { seen: boolean }) {
  const beat = useBeats(TILE_BEATS, seen)
  const on = beat === 2
  return (
    <div className="absolute inset-0 flex flex-col gap-4 bg-ink-media px-5 pt-5">
      <div className="flex items-center justify-between font-mono text-[11px] text-bone-dim">
        <span>9:41</span>
        <span className="truncate pl-3 text-ink-outline">copied · instagram.com/reel/C9xk</span>
      </div>
      <div className="grid grid-cols-2 gap-2.5">
        <QuickTile label="Internet" icon={<WifiIcon />} />
        <QuickTile label="Bluetooth" icon={<BluetoothIcon />} />
        <QuickTile label="Torch" icon={<span className="block h-3.5 w-2 rounded-sm border-2 border-current" />} />
        <motion.div animate={{ scale: beat === 1 ? 0.93 : 1 }} transition={{ type: 'spring', stiffness: 500, damping: 26 }}>
          <QuickTile label="Breadcrumb" on={on} icon={<CrumbTrail size={18} />} />
        </motion.div>
      </div>
      <AnimatePresence>
        {on && (
          <motion.div
            className="absolute inset-x-4 bottom-4 rounded-[18px] border border-ink-border bg-ink px-4 py-3"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 12, transition: { duration: 0.2 } }}
            transition={SHEET}
          >
            <Saved kind="link · from the clipboard" play />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function QuickTile({ label, icon, on = false }: { label: string; icon: ReactNode; on?: boolean }) {
  return (
    <div
      className={`flex h-[76px] flex-col justify-between rounded-[20px] px-3.5 py-3 transition-colors duration-300 ${
        on ? 'bg-amber text-amber-on' : 'bg-ink-elevated text-bone-dim'
      }`}
    >
      <span className="grid size-5 place-items-center">{icon}</span>
      <span className="truncate text-[13px] font-medium">{label}</span>
    </div>
  )
}

function BluetoothIcon() {
  return (
    <svg width="12" height="16" viewBox="0 0 12 16" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M2 4.5l7.5 7L6 15V1l3.5 3.5L2 11.5" />
    </svg>
  )
}

function WifiIcon() {
  return (
    <svg width="16" height="14" viewBox="0 0 16 14" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
      <path d="M1 5a10 10 0 0114 0M3.5 8a6.4 6.4 0 019 0M6 11a2.8 2.8 0 014 0" />
    </svg>
  )
}

const PLUS_BEATS = [1300, 2800, 2600]

function PlusScene({ seen }: { seen: boolean }) {
  const beat = useBeats(PLUS_BEATS, seen)
  return (
    <div className="absolute inset-0 bg-ink">
      <div className="grid grid-cols-2 gap-2 p-3 opacity-30 blur-[1.5px]">
        <div className="h-28 rounded-lg bg-ink-elevated" />
        <div className="h-20 rounded-lg bg-ink-elevated" />
        <div className="h-20 rounded-lg bg-ink-elevated" />
        <div className="h-28 rounded-lg bg-ink-elevated" />
      </div>
      <motion.span
        className="absolute right-4 bottom-4 grid size-[54px] place-items-center rounded-full bg-amber shadow-[0_0_40px_rgb(255_196_107/0.35)]"
        animate={beat === 0 ? { scale: [1, 1.08, 1] } : { scale: 1 }}
        transition={{ duration: 1.1, repeat: beat === 0 ? Infinity : 0 }}
      >
        <svg width="18" height="18" viewBox="0 0 18 18" stroke="var(--color-amber-on)" strokeWidth="2.2" strokeLinecap="round">
          <path d="M9 2v14M2 9h14" />
        </svg>
      </motion.span>
      <AnimatePresence>
        {beat > 0 && (
          <motion.div
            className="absolute inset-x-0 bottom-0 flex flex-col gap-4 rounded-t-[22px] border-t border-ink-border bg-ink px-5 pt-3 pb-5"
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%', transition: { duration: 0.24 } }}
            transition={SHEET}
          >
            <div className="mx-auto h-1 w-9 rounded-sm bg-ink-grabber" />
            {beat === 1 ? (
              <>
                <p className="font-serif text-[22px] leading-[26px]">Say it</p>
                <Trace />
                <div className="flex items-center justify-between font-mono text-xs">
                  <span className="flex items-center gap-2 text-bone-dim">
                    <motion.span
                      className="size-[7px] rounded-full bg-amber"
                      animate={{ opacity: [1, 0.3, 1] }}
                      transition={{ duration: 1.4, repeat: Infinity }}
                    />
                    recording
                  </span>
                  <span className="text-bone">0:03</span>
                </div>
              </>
            ) : (
              <Saved kind="voice note · 0:04" play />
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

// The voice panel's trace (§13): a bar per loudness sample, newest at the
// right and lit, the rest fading across the width.
function Trace() {
  const [bars, setBars] = useState(() => Array.from({ length: 30 }, () => 0.1))
  useEffect(() => {
    const timer = window.setInterval(() => {
      setBars((b) => [...b.slice(1), Math.sqrt(Math.random()) * (Math.random() < 0.15 ? 0.3 : 1)])
    }, 70)
    return () => window.clearInterval(timer)
  }, [])
  return (
    <div className="flex h-14 items-center gap-[3px]">
      {bars.map((h, i) => (
        <span
          key={i}
          className={`w-[3px] rounded-full ${i === bars.length - 1 ? 'bg-amber' : 'bg-bone'}`}
          style={{ height: `${Math.max(3, h * 52)}px`, opacity: i === bars.length - 1 ? 1 : 0.85 * (0.4 + (0.6 * i) / bars.length) }}
        />
      ))}
    </div>
  )
}
