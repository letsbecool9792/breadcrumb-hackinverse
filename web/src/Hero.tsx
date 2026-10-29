import { AnimatePresence, motion, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import { DownloadButton } from './DownloadButton.tsx'
import { MEMORIES as M, type Memory } from './data.ts'
import { Tile } from './memories.tsx'

// Everything a phone keeps, drifting past in a tilted field; now and then one
// is found and lights up. Over it, the one line that says what Breadcrumb does.

const COLUMNS: Memory[][] = [
  [M.sunset, M.essay, M.cs341, M.chat, M.gift],
  [M.book, M.intern, M.tweet, M.lecture, M.map],
  [M.ramenPhoto, M.rohan, M.ticket, M.dal, M.offer],
  [M.map, M.ramenLink, M.sunset, M.essay, M.intern],
  [M.offer, M.dal, M.chat, M.book, M.ramenPhoto],
  [M.tweet, M.gift, M.ticket, M.cs341, M.rohan],
]

// Which tiles are found, and when, in each column's lap.
const FOUND = new Set(['0-2', '1-1', '2-0', '3-1', '4-3', '5-2'])

const WORDS = ['that screenshot', 'that link', 'that PDF', 'that voice note', 'that ramen place']

export function Hero() {
  const field = useRef<HTMLDivElement>(null)
  const inView = useInView(field)

  return (
    <section ref={field} className="relative isolate overflow-hidden">
      <div className={`memory-field ${inView ? '' : 'paused'}`} aria-hidden="true">
        <div className="memory-plane">
          {COLUMNS.map((column, c) => (
            <div key={c} className={`memory-column ${c % 2 ? 'down' : 'up'}`} style={{ animationDuration: `${70 + c * 9}s` }}>
              {[0, 1].map((lap) =>
                column.map((m, i) => (
                  <Tile
                    key={`${lap}-${i}`}
                    m={m}
                    className={FOUND.has(`${c}-${i}`) ? 'found' : ''}
                  />
                )),
              )}
            </div>
          ))}
        </div>
      </div>
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_70%_60%_at_50%_50%,rgb(23_19_16/0.92)_20%,rgb(23_19_16/0.55)_60%,rgb(23_19_16/0.2)_100%)]" />
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-40 bg-gradient-to-b from-transparent to-ink" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-28 bg-gradient-to-b from-ink to-transparent" />

      <div className="gutter relative">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-8 pt-40 pb-36 text-center sm:pt-48 sm:pb-44">
          <h1 className="font-serif text-[clamp(2.4rem,9vw,5.6rem)] leading-[1.06]">
            Find
            <br />
            <FlipWord />
            <br />
            by describing it.
          </h1>
          <p className="font-mono text-xs tracking-[1.4px] text-bone-dim uppercase">a search engine for your own memory</p>
          <DownloadButton />
        </div>
      </div>
    </section>
  )
}

// The headline's changing words, lit as a search's match is (design.md §9).
function FlipWord() {
  const reduced = useReducedMotion()
  const [i, setI] = useState(0)
  useEffect(() => {
    if (reduced) return
    const timer = window.setInterval(() => setI((n) => (n + 1) % WORDS.length), 2600)
    return () => window.clearInterval(timer)
  }, [reduced])

  const word = WORDS[i]
  return (
    <span className="relative inline-block">
      <span className="sr-only">{WORDS[0]}</span>
      <AnimatePresence mode="wait" initial={false}>
        <motion.span
          key={word}
          aria-hidden="true"
          className="inline-block rounded-[0.12em] bg-hit px-[0.14em] text-amber"
          initial={{ opacity: 0, y: 14, filter: 'blur(8px)' }}
          animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
          exit={{ opacity: 0, y: -18, filter: 'blur(8px)', scale: 1.04 }}
          transition={{ duration: 0.38, ease: [0.22, 1, 0.36, 1] }}
        >
          {word.split('').map((ch, n) => (
            <motion.span
              key={n}
              className="inline-block"
              initial={{ opacity: 0, filter: 'blur(6px)' }}
              animate={{ opacity: 1, filter: 'blur(0px)' }}
              transition={{ delay: n * 0.028, duration: 0.3 }}
            >
              {ch === ' ' ? ' ' : ch}
            </motion.span>
          ))}
        </motion.span>
      </AnimatePresence>
    </span>
  )
}
