import { motion, MotionConfig, useInView, useReducedMotion, useScroll, useTransform } from 'motion/react'
import { useRef, type ReactNode } from 'react'
import { DownloadButton, DownloadCode } from './DownloadButton.tsx'
import { CrumbMark, CrumbTrail } from './glyphs.tsx'
import { Hero } from './Hero.tsx'
import { MosaicSearch } from './MosaicSearch.tsx'
import { Offline } from './Offline.tsx'
import { EASE } from './ease.ts'
import { Reveal, RevealHeading } from './Reveal.tsx'
import { SaveScenes } from './SaveScenes.tsx'
import { PointerTrail, ScrollTrail } from './Trail.tsx'

const SOURCE = 'https://github.com/letsbecool9792/breadcrumb-hackinverse'

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <div className="relative overflow-x-clip">
        <Header />
        <Hero />
        <main className="flex flex-col">
          {/* On a computer the whole search fits one screenful. */}
          <section className="gutter">
            <div className="mx-auto max-w-6xl py-24 sm:py-32 lg:flex lg:min-h-svh lg:items-center lg:py-12">
              <MosaicSearch heading={<RevealHeading title="Type what you remember." note="it finds the thing itself" />} />
            </div>
          </section>
          <NotThis />
          <Section title="Save from anywhere." note="one tap, nothing to fill in" wide>
            <SaveScenes />
          </Section>
          <Section title="Nothing leaves your phone." note="no account · no server · works offline">
            <Reveal tilt>
              <Offline />
            </Reveal>
          </Section>
        </main>
        <Footer />
        <ScrollTrail />
        <PointerTrail />
      </div>
    </MotionConfig>
  )
}

function Header() {
  return (
    <motion.header
      className="gutter absolute inset-x-0 top-0 z-20"
      initial={{ opacity: 0, y: -12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.7, ease: EASE }}
    >
      <div className="mx-auto flex max-w-6xl items-center justify-between py-6">
        <a href="./" className="flex items-center gap-2.5" aria-label="Breadcrumb, home">
          <CrumbTrail size={22} />
          <span className="font-serif text-2xl leading-[26px]">breadcrumb</span>
        </a>
        <ExternalLink href={SOURCE} className="font-mono text-xs text-bone-dim hover:text-bone">
          source
        </ExternalLink>
      </div>
    </motion.header>
  )
}

// A title with its one line, then the thing itself: side by side on a wide
// screen, unless the thing needs the whole width.
function Section({ title, note, wide = false, children }: { title: string; note: string; wide?: boolean; children: ReactNode }) {
  return (
    <section className="gutter">
      <div className={`mx-auto grid max-w-6xl items-center gap-x-16 gap-y-10 py-24 sm:py-32 ${wide ? '' : 'lg:grid-cols-2'}`}>
        <RevealHeading title={title} note={note} />
        {children}
      </div>
    </section>
  )
}

// What it will never be, scrolling past in the app's own voice, and pushed
// along by the page's own scrolling.
function NotThis() {
  const strip = useRef<HTMLDivElement>(null)
  const reduced = useReducedMotion()
  const { scrollYProgress } = useScroll({ target: strip, offset: ['start end', 'end start'] })
  const x = useTransform(scrollYProgress, [0, 1], ['6%', '-14%'])
  const words = ['no folders', 'no tags', 'no titles', 'no chatbot', 'no account']
  const line = (
    <span className="flex shrink-0 items-center">
      {words.map((w) => (
        <span key={w} className="flex items-center">
          <span className="px-[0.35em]">{w}</span>
          <span className="text-crumb-dim">·</span>
        </span>
      ))}
    </span>
  )
  return (
    <div ref={strip} className="relative overflow-hidden border-y border-ink-elevated py-6" role="img" aria-label={words.join(', ')}>
      <motion.div style={reduced ? undefined : { x }} aria-hidden="true">
        <div className="not-this flex w-max font-serif text-[clamp(2.6rem,9vw,7.5rem)] leading-none whitespace-nowrap text-ink-grabber">
          {line}
          {line}
        </div>
      </motion.div>
    </div>
  )
}

const WORDMARK = 'breadcrumb'.split('')

function Footer() {
  const mark = useRef<HTMLDivElement>(null)
  const seen = useInView(mark, { once: true, amount: 0.6 })
  return (
    <footer className="gutter border-t border-ink-elevated">
      <div className="mx-auto flex max-w-6xl flex-col items-center gap-10 pt-24 pb-14 text-center">
        {/* The wordmark lands as "Saved" does in the app: the crumbs drop in,
            then its letters. */}
        <div ref={mark} className="flex flex-col items-center gap-3">
          <CrumbMark size={44} play={seen} />
          <p className="font-serif text-[clamp(3.2rem,12vw,8rem)] leading-none">
            <span className="sr-only">breadcrumb</span>
            <span className="-mb-[0.2em] inline-flex overflow-hidden pb-[0.2em]" aria-hidden="true">
              {WORDMARK.map((ch, i) => (
                <motion.span
                  key={i}
                  className="inline-block"
                  initial={{ y: '105%' }}
                  animate={seen ? { y: 0 } : undefined}
                  transition={{ duration: 0.8, ease: EASE, delay: 0.25 + i * 0.045 }}
                >
                  {ch}
                </motion.span>
              ))}
            </span>
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-center gap-x-12 gap-y-8">
          <Reveal delay={0.2}>
            <DownloadButton />
          </Reveal>
          <Reveal delay={0.35} className="hidden lg:block">
            <DownloadCode />
          </Reveal>
        </div>
        <ExternalLink href={SOURCE} className="font-mono text-xs text-ink-outline hover:text-bone">
          source on github
        </ExternalLink>
      </div>
    </footer>
  )
}

function ExternalLink({ href, className, children }: { href: string; className?: string; children: ReactNode }) {
  return (
    <a href={href} target="_blank" rel="noreferrer" className={`transition-colors ${className ?? ''}`}>
      {children}
    </a>
  )
}
