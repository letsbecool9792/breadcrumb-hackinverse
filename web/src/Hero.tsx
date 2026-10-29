import { AnimatePresence, motion, useInView, useReducedMotion, useScroll, useTransform } from 'motion/react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { DownloadButton } from './DownloadButton.tsx'
import { MEMORIES as M, type Memory } from './data.ts'
import { Tile } from './memories.tsx'
import { EASE } from './ease.ts'

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
  const reduced = useReducedMotion()

  // Scrolling away, the field sinks and dims behind the words, which lift and go.
  const { scrollYProgress } = useScroll({ target: field, offset: ['start start', 'end start'] })
  const fieldY = useTransform(scrollYProgress, [0, 1], [0, 180])
  const fieldOpacity = useTransform(scrollYProgress, [0, 1], [1, 0.25])
  const wordsY = useTransform(scrollYProgress, [0, 1], [0, -90])
  const wordsOpacity = useTransform(scrollYProgress, [0, 0.75], [1, 0])

  return (
    <section ref={field} className="relative isolate overflow-hidden">
      <motion.div className="absolute inset-0" style={reduced ? undefined : { y: fieldY, opacity: fieldOpacity }} aria-hidden="true">
        <motion.div
          className={`memory-field ${inView ? '' : 'paused'}`}
          initial={{ opacity: 0, scale: 1.12 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 1.8, ease: EASE }}
        >
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
        </motion.div>
      </motion.div>
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_70%_60%_at_50%_50%,rgb(23_19_16/0.92)_20%,rgb(23_19_16/0.55)_60%,rgb(23_19_16/0.2)_100%)]" />
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-40 bg-gradient-to-b from-transparent to-ink" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-28 bg-gradient-to-b from-ink to-transparent" />

      <motion.div className="gutter relative" style={reduced ? undefined : { y: wordsY, opacity: wordsOpacity }}>
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-8 pt-40 pb-36 text-center sm:pt-48 sm:pb-44 lg:pt-32 lg:pb-40">
          <h1 className="flex flex-col items-center font-serif text-[clamp(2.4rem,9vw,5.6rem)] leading-[1.06]">
            <Arrive delay={0.15}>Find</Arrive>
            <Arrive delay={0.27}>
              <FlipWord />
            </Arrive>
            <Arrive delay={0.39}>by describing it.</Arrive>
          </h1>
          <Arrive delay={0.6}>
            <p className="font-mono text-xs tracking-[1.4px] text-bone-dim uppercase">a search engine for your own memory</p>
          </Arrive>
          <Arrive delay={0.75}>
            <DownloadButton />
          </Arrive>
        </div>
      </motion.div>
    </section>
  )
}

// The first things on the page, rising out of a blur as it opens.
function Arrive({ delay, children }: { delay: number; children: ReactNode }) {
  return (
    <motion.span
      className="block"
      initial={{ opacity: 0, y: 36, filter: 'blur(10px)' }}
      animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
      transition={{ duration: 0.9, ease: EASE, delay }}
    >
      {children}
    </motion.span>
  )
}

// The headline's changing words, lit as a search's match is (design.md §9).
// The light stays and stretches to each new word, which rolls up into it as
// the last rolls out, so the line is never empty.
function FlipWord() {
  const reduced = useReducedMotion()
  const [i, setI] = useState(0)
  const measure = useRef<HTMLSpanElement>(null)
  const [width, setWidth] = useState<number>()

  useEffect(() => {
    if (reduced) return
    const timer = window.setInterval(() => setI((n) => (n + 1) % WORDS.length), 2600)
    return () => window.clearInterval(timer)
  }, [reduced])

  // The next width, read off a hidden copy of the word; again when the font
  // arrives or the screen resizes.
  useEffect(() => {
    const element = measure.current
    if (!element) return
    const observer = new ResizeObserver(() => setWidth(element.offsetWidth))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  const word = WORDS[i]
  return (
    <span className="relative inline-flex justify-center">
      <span className="sr-only">{WORDS[0]}</span>
      <span ref={measure} className="invisible absolute px-[0.14em] whitespace-nowrap" aria-hidden="true">
        {word}
      </span>
      <motion.span
        className="relative inline-flex justify-center overflow-hidden rounded-[0.12em] bg-hit text-amber"
        animate={{ width }}
        transition={{ type: 'spring', stiffness: 220, damping: 28 }}
        aria-hidden="true"
      >
        <AnimatePresence mode="popLayout" initial={false}>
          <motion.span
            key={word}
            className="block px-[0.14em] whitespace-nowrap"
            initial={{ y: '90%', opacity: 0, filter: 'blur(6px)' }}
            animate={{ y: 0, opacity: 1, filter: 'blur(0px)' }}
            exit={{ y: '-90%', opacity: 0, filter: 'blur(6px)' }}
            transition={{ duration: 0.55, ease: EASE }}
          >
            {word}
          </motion.span>
        </AnimatePresence>
      </motion.span>
    </span>
  )
}
