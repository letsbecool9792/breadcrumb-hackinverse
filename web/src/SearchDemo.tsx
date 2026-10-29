import { useEffect, useRef, useState, type ReactNode } from 'react'
import { CrumbTrail, DocumentGlyph, NoteGlyph, RamenThumb, ScreenshotThumb, VoiceGlyph } from './glyphs.tsx'
import { useInView, useReducedMotion } from './lib/motion.ts'

// The app's search, played through a few example phrases: the field at the
// foot, the counter line, and ranked rows with the matched word lit
// (docs/design.md §8 and §9). The memories are made up.

type Thumb = 'screenshot' | 'ramen' | 'pdf' | 'note' | 'voice' | { initial: string }

interface Row {
  thumb: Thumb
  title: string
  // The fragment around the hit: before, the lit word, after. Absent for a
  // match by meaning alone, like a photo with no words in it.
  why?: [string, string, string]
  meta: string
}

interface Search {
  phrase: string
  label: string
  counter: string
  rows: Row[]
}

const SEARCHES: Search[] = [
  {
    phrase: 'that internship screenshot from april',
    label: 'internship screenshot',
    counter: '2 of 412 · internship · images · apr',
    rows: [
      {
        thumb: 'screenshot',
        title: 'Summer research internship 2026, applications open',
        why: ['…summer research ', 'internship', ' · 8 weeks, stipend 25k a month, apply by 30 april…'],
        meta: '6mo · linkedin · image',
      },
      {
        thumb: 'screenshot',
        title: 'Offer letter: Software Engineering Intern',
        why: ['…pleased to offer you the ', 'internship', ', starting 2 june in…'],
        meta: '6mo · gmail · image',
      },
    ],
  },
  {
    phrase: 'the ramen place someone sent me on whatsapp',
    label: 'ramen from whatsapp',
    counter: '2 of 412 · ramen place · from whatsapp',
    rows: [
      {
        thumb: { initial: 'I' },
        title: 'Hoshi Ramen Bar: the spicy miso is back',
        why: ['', 'Ramen', ' worth the queue. Go before 8 or wait an hour…'],
        meta: '3w · whatsapp · link',
      },
      {
        thumb: 'ramen',
        title: 'Photo',
        meta: '3w · whatsapp · image',
      },
    ],
  },
  {
    phrase: 'that guy from the hackathon who worked at qualcomm',
    label: 'the qualcomm guy',
    counter: '1 of 412 · hackathon worked qualcomm',
    rows: [
      {
        thumb: 'voice',
        title: 'Rohan, met him at the hackathon',
        why: ['…does ML at ', 'Qualcomm', ', said to ping him about referrals…'],
        meta: '3w · voice note',
      },
    ],
  },
  {
    phrase: 'the pdf my professor sent about compilers',
    label: "professor's pdf",
    counter: '2 of 412 · professor compilers · pdfs',
    rows: [
      {
        thumb: 'pdf',
        title: 'CS341 Compiler Design: Assignment 3',
        why: ['…lexical analysis for the ', 'compilers', ' project, due friday 14 nov…'],
        meta: '2w · gmail · pdf',
      },
      {
        thumb: 'pdf',
        title: 'Lecture 7: Parsing',
        why: ['…bottom-up parsing, the way ', 'compilers', ' actually do it…'],
        meta: '1mo · whatsapp · pdf',
      },
    ],
  },
]

type Phase = 'typing' | 'ranking' | 'shown' | 'erasing'

export function SearchDemo() {
  const panel = useRef<HTMLDivElement>(null)
  const inView = useInView(panel)
  const reduced = useReducedMotion()
  const [index, setIndex] = useState(0)
  // It opens on a finished search, so the page is complete before anything moves.
  const [typed, setTyped] = useState(SEARCHES[0].phrase.length)
  const [phase, setPhase] = useState<Phase>('shown')
  const [animated, setAnimated] = useState(false)
  // Set only by a choice, so the page announces what a person asked for and
  // stays quiet while the examples play by themselves.
  const [chosen, setChosen] = useState<number | null>(null)

  const search = SEARCHES[index]
  const running = inView && !reduced

  useEffect(() => {
    if (!running) return
    const phrase = search.phrase
    let timer: number
    switch (phase) {
      case 'typing':
        timer =
          typed < phrase.length
            ? window.setTimeout(() => setTyped(typed + 1), 30 + Math.random() * 45)
            : window.setTimeout(() => setPhase('ranking'), 160)
        break
      case 'ranking':
        timer = window.setTimeout(() => setPhase('shown'), 650)
        break
      case 'shown':
        timer = window.setTimeout(() => {
          setAnimated(true)
          setPhase('erasing')
        }, 4200)
        break
      case 'erasing':
        timer =
          typed > 0
            ? window.setTimeout(() => setTyped(typed - 1), 12)
            : window.setTimeout(() => {
                setIndex((index + 1) % SEARCHES.length)
                setPhase('typing')
              }, 300)
        break
    }
    return () => window.clearTimeout(timer)
  }, [running, phase, typed, index, search.phrase])

  const choose = (next: number) => {
    setIndex(next)
    setChosen(next)
    setAnimated(true)
    if (reduced) {
      setTyped(SEARCHES[next].phrase.length)
      setPhase('shown')
    } else {
      setTyped(0)
      setPhase('typing')
    }
  }

  const text = search.phrase.slice(0, typed)
  const searching = phase === 'ranking' || phase === 'shown'

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div
        ref={panel}
        className="relative overflow-hidden rounded-[28px] border border-ink-border bg-ink"
        aria-hidden="true"
      >
        <div className="flex items-baseline justify-between px-5 pt-3.5 pb-1">
          <span className="font-serif text-xl leading-[22px]">breadcrumb</span>
          <span className="font-mono text-[11px] text-ink-outline">412 kept</span>
        </div>

        <div className="min-h-[296px] px-2 sm:min-h-[276px]">
          {phase === 'shown' && (
            <>
              <p className="px-3 pt-[18px] pb-3 font-mono text-[11px] tracking-[0.3px] text-ink-outline">
                {search.counter}
              </p>
              <div className="flex flex-col gap-1">
                {search.rows.map((row, i) => (
                  <ResultRow key={`${index}-${i}`} row={row} top={i === 0} animated={animated} delay={40 * i} />
                ))}
              </div>
            </>
          )}
          {phase === 'ranking' && (
            <div className="pt-12 pl-5">
              <CrumbTrail size={44} walking />
            </div>
          )}
        </div>

        <div className="relative px-4 pt-1.5 pb-4">
          <div className="relative">
            <div className={`search-light ${searching ? 'breathing' : ''}`} />
            <div className="relative flex h-[54px] items-center gap-[11px] rounded-[27px] border border-amber-container bg-ink-elevated pr-4 pl-5">
              <CrumbTrail size={17} walking={phase === 'ranking'} />
              <span className="min-w-0 flex-1 truncate font-mono text-[13px] text-bone">
                {text === '' ? (
                  <>
                    <span className="caret" />
                    <span className="text-ink-outline">search your memory</span>
                  </>
                ) : (
                  <>
                    {text}
                    <span className="caret" />
                  </>
                )}
              </span>
              {text !== '' && (
                <span className="grid size-[22px] shrink-0 place-items-center rounded-full bg-ink-field-border">
                  <svg width="9" height="9" viewBox="0 0 9 9">
                    <path d="M1 1l7 7M8 1L1 8" stroke="var(--color-bone-dim)" strokeWidth="1.5" strokeLinecap="round" />
                  </svg>
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Example searches">
        <span className="pr-1 font-mono text-[11px] text-ink-outline">try</span>
        {SEARCHES.map((s, i) => (
          <button
            key={s.label}
            type="button"
            onClick={() => choose(i)}
            aria-pressed={i === index}
            aria-label={s.phrase}
            className={`rounded-full border px-3 py-1.5 font-mono text-[11px] transition-colors ${
              i === index
                ? 'border-amber-container bg-ink-elevated text-amber'
                : 'border-ink-field-border text-bone-dim hover:text-bone'
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>
      <p className="sr-only" aria-live="polite">
        {chosen !== null &&
          `Searching “${SEARCHES[chosen].phrase}” finds: ${SEARCHES[chosen].rows.map((row) => row.title).join('; ')}.`}
      </p>
    </div>
  )
}

function ResultRow({ row, top, animated, delay }: { row: Row; top: boolean; animated: boolean; delay: number }) {
  return (
    <div
      className={`flex items-start gap-3 rounded-[10px] px-2.5 py-[13px] ${top ? 'bg-ink-elevated' : ''} ${
        animated ? 'row-arrive' : ''
      }`}
      style={animated ? { animationDelay: `${delay}ms` } : undefined}
    >
      <div className="grid size-[46px] shrink-0 place-items-center overflow-hidden rounded-[7px] border border-ink-border bg-ink-media">
        <ThumbView thumb={row.thumb} />
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-[5px]">
        <p className="line-clamp-2 text-base leading-[21px] font-medium text-bone">{row.title}</p>
        {row.why && (
          <p className="line-clamp-3 font-mono text-[11px] leading-4 text-bone-dim">
            {row.why[0]}
            <span className="lit" style={animated ? { animationDelay: `${delay + 180}ms` } : undefined}>
              {row.why[1]}
            </span>
            {row.why[2]}
          </p>
        )}
        <p className="font-mono text-[10px] tracking-[0.3px] text-ink-outline">{row.meta}</p>
      </div>
    </div>
  )
}

function ThumbView({ thumb }: { thumb: Thumb }): ReactNode {
  if (typeof thumb === 'object') return <span className="font-mono text-base text-amber">{thumb.initial}</span>
  switch (thumb) {
    case 'screenshot':
      return <ScreenshotThumb />
    case 'ramen':
      return <RamenThumb />
    case 'pdf':
      return <DocumentGlyph width={24} height={30} />
    case 'note':
      return <NoteGlyph className="w-full p-[9px]" />
    case 'voice':
      return <VoiceGlyph size={22} />
  }
}
