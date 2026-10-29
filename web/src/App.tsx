import { MotionConfig } from 'motion/react'
import type { ReactNode } from 'react'
import { DownloadButton, DownloadCode } from './DownloadButton.tsx'
import { CrumbTrail } from './glyphs.tsx'
import { Hero } from './Hero.tsx'
import { MosaicSearch } from './MosaicSearch.tsx'
import { Offline } from './Offline.tsx'
import { SaveScenes } from './SaveScenes.tsx'
import { PointerTrail, ScrollTrail } from './Trail.tsx'

const SOURCE = 'https://github.com/letsbecool9792/breadcrumb-hackinverse'
const DEVFOLIO = 'https://devfolio.co/projects/breadcrumb-54b8'

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <div className="relative overflow-x-clip">
        <Header />
        <Hero />
        <main className="flex flex-col">
          <Section title="Type what you remember." note="it finds the thing itself">
            <div className="mx-auto w-full max-w-[420px] lg:mx-0 lg:justify-self-end">
              <MosaicSearch />
            </div>
          </Section>
          <NotThis />
          <Section title="Save from anywhere." note="one tap, nothing to fill in" wide>
            <SaveScenes />
          </Section>
          <Section title="Nothing leaves your phone." note="no account · no server · works offline">
            <Offline />
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
    <header className="gutter absolute inset-x-0 top-0 z-20">
      <div className="mx-auto flex max-w-6xl items-center justify-between py-6">
        <a href="./" className="flex items-center gap-2.5" aria-label="Breadcrumb, home">
          <CrumbTrail size={22} />
          <span className="font-serif text-2xl leading-[26px]">breadcrumb</span>
        </a>
        <ExternalLink href={SOURCE} className="font-mono text-xs text-bone-dim hover:text-bone">
          source
        </ExternalLink>
      </div>
    </header>
  )
}

// A title with its one line, then the thing itself: side by side on a wide
// screen, unless the thing needs the whole width.
function Section({ title, note, wide = false, children }: { title: string; note: string; wide?: boolean; children: ReactNode }) {
  return (
    <section className="gutter">
      <div className={`mx-auto grid max-w-6xl items-center gap-x-16 gap-y-10 py-24 sm:py-32 ${wide ? '' : 'lg:grid-cols-2'}`}>
        <div className="flex flex-col gap-4">
          <h2 className="font-serif text-[clamp(2.2rem,6vw,4rem)] leading-[1.05]">{title}</h2>
          <p className="font-mono text-xs text-ink-outline">{note}</p>
        </div>
        {children}
      </div>
    </section>
  )
}

// What it will never be, scrolling past in the app's own voice.
function NotThis() {
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
    <div className="relative overflow-hidden border-y border-ink-elevated py-6" role="img" aria-label={words.join(', ')}>
      <div className="not-this flex w-max font-serif text-[clamp(2.6rem,9vw,7.5rem)] leading-none whitespace-nowrap text-ink-grabber" aria-hidden="true">
        {line}
        {line}
      </div>
    </div>
  )
}

function Footer() {
  return (
    <footer className="gutter border-t border-ink-elevated">
      <div className="mx-auto flex max-w-6xl flex-col items-center gap-10 pt-24 pb-14 text-center">
        <div className="flex flex-col items-center gap-3">
          <CrumbTrail size={34} />
          <p className="font-serif text-[clamp(3.2rem,12vw,8rem)] leading-none">breadcrumb</p>
        </div>
        <div className="flex flex-wrap items-center justify-center gap-x-12 gap-y-8">
          <DownloadButton />
          <div className="hidden lg:block">
            <DownloadCode />
          </div>
        </div>
        <div className="flex flex-wrap justify-center gap-x-6 gap-y-2 font-mono text-xs text-ink-outline">
          <ExternalLink href={DEVFOLIO} className="hover:text-bone">
            1st place · hackinverse 1.0
          </ExternalLink>
          <ExternalLink href={SOURCE} className="hover:text-bone">
            source on github
          </ExternalLink>
        </div>
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
