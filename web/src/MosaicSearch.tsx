import { AnimatePresence, LayoutGroup, motion, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import { CrumbTrail } from './glyphs.tsx'
import { MEMORIES as M, type Memory, type Why } from './data.ts'
import { ResultRow, Tile } from './memories.tsx'
import { SearchField } from './SearchField.tsx'

// The app's home screen playing through searches: the mosaic, a phrase typed
// into the field, and the matches leaving their tiles for a ranked list, their
// pictures travelling as they do in the app (design.md §6, §9, §10).

const LEFT: Memory[] = [M.intern, M.rohan, M.cs341, M.offer]
const RIGHT: Memory[] = [M.ramenLink, M.ramenPhoto, M.book, M.lecture]
const SCOPE = 'mosaic'

interface Search {
  phrase: string
  label: string
  counter: string
  hits: { m: Memory; why?: Why }[]
}

const SEARCHES: Search[] = [
  {
    phrase: 'that internship screenshot from april',
    label: 'internship screenshot',
    counter: '2 of 412 · internship · images · apr',
    hits: [
      { m: M.intern, why: ['…summer research ', 'internship', ' · 8 weeks, stipend 25k a month, apply by 30 april…'] },
      { m: M.offer, why: ['…pleased to offer you the ', 'internship', ', starting 2 june…'] },
    ],
  },
  {
    phrase: 'the ramen place someone sent me on whatsapp',
    label: 'ramen from whatsapp',
    counter: '2 of 412 · ramen place · from whatsapp',
    hits: [{ m: M.ramenLink, why: ['', 'Ramen', ' worth the queue. Go before 8 or wait an hour…'] }, { m: M.ramenPhoto }],
  },
  {
    phrase: 'that guy from the hackathon who worked at qualcomm',
    label: 'the qualcomm guy',
    counter: '1 of 412 · hackathon worked qualcomm',
    hits: [{ m: M.rohan, why: ['…does ML at ', 'Qualcomm', ', said to ping him about referrals…'] }],
  },
  {
    phrase: 'the pdf my professor sent about compilers',
    label: "professor's pdf",
    counter: '2 of 412 · professor compilers · pdfs',
    hits: [
      { m: M.cs341, why: ['…lexical analysis for the ', 'compilers', ' project, due friday 14 nov…'] },
      { m: M.lecture, why: ['…bottom-up parsing, the way ', 'compilers', ' actually do it…'] },
    ],
  },
]

type Phase = 'rest' | 'typing' | 'ranking' | 'shown'

export function MosaicSearch() {
  const panel = useRef<HTMLDivElement>(null)
  const inView = useInView(panel, { amount: 0.4 })
  const reduced = useReducedMotion()
  const [index, setIndex] = useState(0)
  const [typed, setTyped] = useState(0)
  const [phase, setPhase] = useState<Phase>('rest')
  // Set only by a choice, so the page announces what a person asked for.
  const [chosen, setChosen] = useState<number | null>(null)

  const search = SEARCHES[index]
  const running = inView && !reduced

  useEffect(() => {
    if (!running) return
    let timer = 0
    const after = (ms: number, step: () => void) => (timer = window.setTimeout(step, ms))
    switch (phase) {
      case 'rest':
        after(1500, () => setPhase('typing'))
        break
      case 'typing':
        if (typed < search.phrase.length) after(28 + Math.random() * 46, () => setTyped(typed + 1))
        else after(180, () => setPhase('ranking'))
        break
      case 'ranking':
        after(650, () => setPhase('shown'))
        break
      case 'shown':
        after(4200, () => {
          setTyped(0)
          setPhase('rest')
          setIndex((index + 1) % SEARCHES.length)
        })
        break
    }
    return () => window.clearTimeout(timer)
  }, [running, phase, typed, index, search.phrase])

  const choose = (next: number) => {
    setIndex(next)
    setChosen(next)
    if (reduced) {
      setTyped(SEARCHES[next].phrase.length)
      setPhase('shown')
    } else {
      setTyped(0)
      setPhase('typing')
    }
  }

  const shown = phase === 'shown'

  return (
    <div className="flex w-full min-w-0 flex-col gap-4">
      <LayoutGroup id={SCOPE}>
        <div
          ref={panel}
          className="relative h-[580px] overflow-hidden rounded-[32px] border border-ink-border bg-ink shadow-[0_40px_120px_-40px_rgb(255_196_107/0.18)] sm:h-[620px]"
          aria-hidden="true"
        >
          <div className="absolute inset-x-0 top-0 bottom-[78px]">
            <AnimatePresence initial={false}>
              {shown ? (
                <motion.div
                  key={`results-${index}`}
                  className="absolute inset-0 px-2.5"
                  exit={{ opacity: 0, transition: { duration: 0.16 } }}
                >
                  <motion.p
                    className="px-3 pt-[22px] pb-3 font-mono text-[11px] tracking-[0.3px] text-ink-outline"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ duration: 0.24, delay: 0.06 }}
                  >
                    {search.counter}
                  </motion.p>
                  <div className="flex flex-col gap-1">
                    {search.hits.map((hit, i) => (
                      <ResultRow key={hit.m.id} m={hit.m} why={hit.why} top={i === 0} scope={SCOPE} index={i} />
                    ))}
                  </div>
                </motion.div>
              ) : (
                <motion.div
                  key="mosaic"
                  className="absolute inset-0 px-3.5"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: phase === 'ranking' ? 0.4 : 1 }}
                  exit={{ opacity: 0, transition: { duration: 0.2 } }}
                  transition={{ duration: 0.3 }}
                >
                  <Masthead />
                  <div className="grid grid-cols-2 items-start gap-2.5">
                    {[LEFT, RIGHT].map((column, c) => (
                      <div key={c} className="flex flex-col gap-2.5">
                        {column.map((m) => (
                          <Tile key={m.id} m={m} scope={SCOPE} />
                        ))}
                      </div>
                    ))}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
            <div className="pointer-events-none absolute inset-x-0 bottom-0 h-12 bg-gradient-to-b from-transparent to-ink" />
          </div>

          <div className="absolute inset-x-0 bottom-0 px-4 pb-4">
            <SearchField text={search.phrase.slice(0, typed)} ranking={phase === 'ranking'} />
          </div>
        </div>
      </LayoutGroup>

      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Example searches">
        <span className="pr-1 font-mono text-[11px] text-ink-outline">try</span>
        {SEARCHES.map((s, i) => (
          <button
            key={s.label}
            type="button"
            onClick={() => choose(i)}
            aria-pressed={i === index && phase !== 'rest'}
            aria-label={s.phrase}
            className={`rounded-full border px-3 py-1.5 font-mono text-[11px] transition-colors ${
              i === index && phase !== 'rest'
                ? 'border-amber-container bg-ink-elevated text-amber'
                : 'border-ink-field-border text-bone-dim hover:border-ink-grabber hover:text-bone'
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>
      <p className="sr-only" aria-live="polite">
        {chosen !== null &&
          `Searching “${SEARCHES[chosen].phrase}” finds: ${SEARCHES[chosen].hits.length} ${SEARCHES[chosen].hits.length === 1 ? 'memory' : 'memories'}.`}
      </p>
    </div>
  )
}

// The mosaic's first item (§5).
function Masthead() {
  return (
    <div className="flex flex-col gap-1.5 px-1.5 pt-4 pb-4">
      <p className="font-serif text-[40px] leading-[44px]">breadcrumb</p>
      <div className="flex items-center gap-[9px] font-mono text-[11px] tracking-[1.4px] text-ink-outline">
        <CrumbTrail size={15} />
        <span className="flex-1">412 KEPT</span>
        <span>BACK UP</span>
      </div>
    </div>
  )
}
