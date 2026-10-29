import type { ReactNode } from 'react'
import { CaptureSheet } from './CaptureSheet.tsx'
import { CrumbTrail } from './glyphs.tsx'
import { SearchDemo } from './SearchDemo.tsx'

const SOURCE = 'https://github.com/letsbecool9792/breadcrumb-hackinverse'
const DEVFOLIO = 'https://devfolio.co/projects/breadcrumb-54b8'

export default function App() {
  return (
    <div className="gutter overflow-x-clip">
      <div className="mx-auto flex max-w-6xl flex-col">
        <Header />
        <main className="flex flex-col">
          <Hero />
          <TheWholeApp />
          <Keeping />
          <Searching />
          <OnThePhone />
          <ReferenceMemory />
        </main>
        <Footer />
      </div>
    </div>
  )
}

function Header() {
  return (
    <header className="flex items-center justify-between py-6">
      <a href="./" className="flex items-center gap-2.5" aria-label="Breadcrumb, home">
        <CrumbTrail size={22} />
        <span className="font-serif text-2xl leading-[26px]">breadcrumb</span>
      </a>
      <ExternalLink href={SOURCE} className="font-mono text-xs text-bone-dim hover:text-bone">
        source
      </ExternalLink>
    </header>
  )
}

function Hero() {
  return (
    <section className="grid items-center gap-x-14 gap-y-12 pt-10 pb-24 sm:pt-16 lg:grid-cols-[1.05fr_0.95fr] lg:pb-32">
      <div className="flex min-w-0 flex-col gap-7">
        <p className="flex items-center gap-[9px] font-mono text-[11px] tracking-[1.4px] text-ink-outline uppercase">
          <CrumbTrail size={15} />A search engine for your own memory
        </p>
        <h1 className="font-serif text-[2.75rem] leading-[1.04] sm:text-6xl lg:text-[4.25rem]">
          Save it in one tap. Find it by describing it.
        </h1>
        <p className="max-w-[34rem] text-lg leading-[1.6] text-bone-dim">
          Screenshots, links, PDFs, a thought, a voice note: Breadcrumb keeps them together on your
          phone. When you want one back, type what you remember of it, the way you'd say it out loud.
        </p>
        <DownloadSlot />
      </div>
      <SearchDemo />
    </section>
  )
}

// Where the download goes once releases are published (docs/workstreams/website.md W2).
function DownloadSlot() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
      <span className="inline-flex h-[52px] items-center gap-2.5 rounded-[26px] border border-ink-field-border bg-ink-elevated px-6 font-mono text-[13px] text-bone-dim">
        <span className="size-[7px] rounded-full bg-crumb-dim" aria-hidden="true" />
        download for android · soon
      </span>
      <span className="font-mono text-[11px] leading-[18px] text-ink-outline">
        android 10 and up
        <br />
        free, no account
      </span>
    </div>
  )
}

const FLOW: [string, string, number, string][] = [
  ['Save', 'One tap from wherever you are. Nothing to fill in.', 10, 'bg-crumb-faint'],
  ['Forget about it', 'No folders, tags or titles. The phone reads it while you get on with your day.', 13, 'bg-crumb-dim'],
  ['Search', 'Describe it the way you remember it.', 17, 'bg-crumb-mid'],
  ['Find', 'The original opens: the screenshot, the PDF, the page.', 21, 'bg-crumb-bright'],
]

function TheWholeApp() {
  return (
    <Section title="That's the whole app.">
      <ol className="grid gap-x-8 gap-y-9 sm:grid-cols-2 lg:grid-cols-4">
        {FLOW.map(([step, line, size, colour]) => (
          <li key={step} className="flex flex-col gap-3">
            <div className="flex h-6 items-end">
              <span className={`rounded-full ${colour}`} style={{ width: size, height: size }} aria-hidden="true" />
            </div>
            <p className="font-serif text-[26px] leading-[30px]">{step}</p>
            <p className="max-w-[17rem] text-[15px] leading-[23px] text-bone-dim">{line}</p>
          </li>
        ))}
      </ol>
    </Section>
  )
}

const WAYS: [string, string][] = [
  ['share', 'From any app’s share sheet: a link, a picture, a PDF, a reel.'],
  ['select', 'Select text anywhere and pick Breadcrumb from the menu.'],
  ['tile', 'Copied something? The Quick Settings tile keeps what’s on the clipboard.'],
  ['+', 'Inside the app: a thought, photos, PDFs or a voice note.'],
  ['bring in', 'What’s already on the phone: the screenshots folder, PDFs, your browser’s bookmarks.'],
]

function Keeping() {
  return (
    <Section title="Saving costs one tap.">
      <div className="grid items-start gap-x-14 gap-y-12 lg:grid-cols-2">
        <div className="flex min-w-0 flex-col gap-8">
          <p className="max-w-[34rem] text-lg leading-[1.6] text-bone-dim">
            You see <span className="font-serif text-bone">Saved</span> before anything touches the
            network. Reading the words in a picture, the text of a PDF and the page behind a link all
            happens afterwards, on the phone. Add a note if you like, typed or said. You never have to.
          </p>
          <dl className="flex flex-col border-t border-ink-elevated">
            {WAYS.map(([way, line]) => (
              <div key={way} className="grid grid-cols-[5.5rem_1fr] gap-4 border-b border-ink-elevated py-3.5">
                <dt className="font-mono text-xs leading-[22px] text-amber">{way}</dt>
                <dd className="text-[15px] leading-[22px] text-bone">{line}</dd>
              </div>
            ))}
          </dl>
        </div>
        <CaptureSheet />
      </div>
    </Section>
  )
}

interface Part {
  text: string
  took?: string
}

const PHRASES: { parts: Part[]; counter: string }[] = [
  {
    parts: [
      { text: 'that' },
      { text: 'internship', took: 'words' },
      { text: 'screenshot', took: 'kind · images' },
      { text: 'from april', took: 'when · apr' },
    ],
    counter: '2 of 412 · internship · images · apr',
  },
  {
    parts: [
      { text: 'that' },
      { text: 'link', took: 'kind · links' },
      { text: 'someone sent me' },
      { text: 'on whatsapp', took: 'app · whatsapp' },
    ],
    counter: '9 of 412 · links · from whatsapp',
  },
]

function Searching() {
  return (
    <Section title="Describe it the way you remember it.">
      <div className="grid gap-x-14 gap-y-10 lg:grid-cols-[0.9fr_1.1fr]">
        <div className="flex min-w-0 flex-col gap-5 text-lg leading-[1.6] text-bone-dim">
          <p>
            Breadcrumb takes the phrase apart first: the words to look for, what kind of thing it was,
            when you saved it and which app it came from.
          </p>
          <p>
            Then it ranks everything you kept two ways at once, by meaning and by the words
            themselves. A name like Qualcomm is matched exactly, and the rest by what you meant. A
            photo of ramen with no words in it still turns up for “ramen”.
          </p>
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          {PHRASES.map((phrase) => (
            <div key={phrase.counter} className="flex flex-col gap-5 rounded-[20px] border border-ink-border p-5 sm:p-6">
              <p className="flex flex-wrap items-start gap-x-[1.2ch] gap-y-3 font-mono text-[15px] sm:text-base">
                {phrase.parts.flatMap((part, i) =>
                  part.took
                    ? [
                        <span key={i} className="inline-flex flex-col gap-1.5">
                          <span className="border-b-2 border-amber-container pb-0.5 text-bone">{part.text}</span>
                          <span className="text-[10px] tracking-[1.2px] text-ink-outline uppercase">{part.took}</span>
                        </span>,
                      ]
                    : part.text.split(' ').map((word, j) => (
                        <span key={`${i}-${j}`} className="pb-[4px] text-ink-outline">
                          {word}
                        </span>
                      )),
                )}
              </p>
              <p className="border-t border-ink-elevated pt-4 font-mono text-[11px] tracking-[0.3px] text-bone-dim">
                {phrase.counter}
              </p>
            </div>
          ))}
        </div>
      </div>
    </Section>
  )
}

const ON_THE_PHONE: [string, ReactNode][] = [
  [
    'stays here',
    'What you keep, its originals and everything the phone makes of them live in the app’s own storage. No account, no server, no sync.',
  ],
  ['airplane mode', 'Search by meaning runs on the phone. Once its model has downloaded, everything works with the network off.'],
  [
    'goes online for',
    'Two things only: reading the page of a link you saved, and downloading the model. Nothing you saved or typed is sent anywhere.',
  ],
  ['yours to move', 'Back everything up to one file you keep, and restore it on a new phone.'],
]

function OnThePhone() {
  return (
    <Section title="Nothing you keep leaves the phone.">
      <div className="grid gap-x-14 gap-y-12 lg:grid-cols-[0.9fr_1.1fr]">
        <ModelBar />
        <dl className="grid gap-x-10 gap-y-8 sm:grid-cols-2">
          {ON_THE_PHONE.map(([label, line]) => (
            <div key={label} className="flex flex-col gap-2">
              <dt className="font-mono text-[11px] tracking-[1.2px] text-ink-label uppercase">{label}</dt>
              <dd className="text-[15px] leading-[23px] text-bone">{line}</dd>
            </div>
          ))}
        </dl>
      </div>
    </Section>
  )
}

// The first-run screen's bar (docs/design.md §19), part way down.
function ModelBar() {
  return (
    <div className="flex flex-col gap-6 self-start rounded-[20px] bg-ink-media p-6 sm:p-7">
      <div className="flex items-center gap-2.5">
        <CrumbTrail size={22} walking />
        <span className="font-serif text-2xl leading-[26px]">breadcrumb</span>
      </div>
      <div className="flex flex-col gap-3.5">
        <p className="font-serif text-[30px] leading-[34px]">Getting search ready</p>
        <p className="text-base leading-6 text-bone-dim">
          A small model that runs on this phone. It downloads once, 388 MB, and works offline from then
          on.
        </p>
      </div>
      <div className="flex flex-col gap-2.5">
        <div className="h-1.5 overflow-hidden rounded-[3px] bg-ink-elevated">
          <div className="h-full w-[37%] rounded-[3px] bg-amber" />
        </div>
        <div className="flex justify-between font-mono text-xs">
          <span className="text-bone-dim">downloading</span>
          <span className="text-bone tabular-nums">142 of 388 MB</span>
        </div>
      </div>
    </div>
  )
}

function ReferenceMemory() {
  return (
    <Section title="For what you want to find again.">
      <div className="flex max-w-[44rem] flex-col gap-7">
        <p className="text-lg leading-[1.6] text-bone-dim">
          Calendars and to-do lists already handle what you need to do. Breadcrumb holds what you want
          to remember: a restaurant someone recommended, an article, an internship posting, a person you
          met at an event. A search gives back the things themselves, ranked, with a line on why each
          one matched.
        </p>
        <p className="flex flex-wrap gap-x-5 gap-y-2 font-mono text-xs text-ink-outline">
          <span>no folders</span>
          <span>no tags</span>
          <span>no titles to write</span>
          <span>no chatbot</span>
        </p>
      </div>
    </Section>
  )
}

function Footer() {
  return (
    <footer className="flex flex-col gap-10 border-t border-ink-elevated pt-12 pb-14">
      <div className="grid gap-x-14 gap-y-8 lg:grid-cols-[1.05fr_0.95fr]">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2.5">
            <CrumbTrail size={22} />
            <span className="font-serif text-2xl leading-[26px]">breadcrumb</span>
          </div>
          <p className="max-w-[30rem] text-[15px] leading-[23px] text-bone-dim">
            Named for the trail you leave walking through a forest. Drop small pieces as you go, and
            follow them back when you need one.
          </p>
        </div>
        <DownloadSlot />
      </div>
      <div className="flex flex-wrap gap-x-6 gap-y-2 font-mono text-xs text-ink-outline">
        <ExternalLink href={DEVFOLIO} className="hover:text-bone">
          1st place · hackinverse 1.0
        </ExternalLink>
        <ExternalLink href={SOURCE} className="hover:text-bone">
          source on github
        </ExternalLink>
      </div>
    </footer>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-10 border-t border-ink-elevated py-20 sm:gap-12 sm:py-24">
      <h2 className="max-w-[40rem] font-serif text-[2rem] leading-[1.12] sm:text-[2.6rem]">{title}</h2>
      {children}
    </section>
  )
}

function ExternalLink({ href, className, children }: { href: string; className?: string; children: ReactNode }) {
  return (
    <a href={href} target="_blank" rel="noreferrer" className={`transition-colors ${className ?? ''}`}>
      {children}
    </a>
  )
}
