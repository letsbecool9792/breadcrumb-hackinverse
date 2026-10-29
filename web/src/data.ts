import type { ArtName } from './art.tsx'

// Made-up memories, the ones the page's mosaic, field and searches show.

export type Kind = 'image' | 'pdf' | 'link' | 'note' | 'voice'

export interface Memory {
  id: string
  kind: Kind
  title?: string
  text?: string
  host?: string
  art?: ArtName
  height?: number
  length?: string
  age: string
  source?: string
}

export const MEMORIES = {
  intern: { id: 'intern', kind: 'image', art: 'posting', height: 228, title: 'Summer research internship 2026, applications open', age: '6mo', source: 'linkedin' },
  offer: { id: 'offer', kind: 'image', art: 'offer', height: 176, title: 'Your offer: Software Engineering Intern', age: '6mo', source: 'gmail' },
  ramenLink: { id: 'ramenLink', kind: 'link', host: 'instagram.com', title: 'Hoshi Ramen Bar: the spicy miso is back', age: '3w', source: 'whatsapp' },
  ramenPhoto: { id: 'ramenPhoto', kind: 'image', art: 'ramen', height: 172, age: '3w', source: 'whatsapp' },
  rohan: { id: 'rohan', kind: 'voice', length: '0:42', text: 'Rohan, met him at the hackathon. Does ML at Qualcomm, said to ping him about referrals.', age: '3w' },
  cs341: { id: 'cs341', kind: 'pdf', title: 'CS341 Compiler Design: Assignment 3', age: '2w', source: 'gmail' },
  lecture: { id: 'lecture', kind: 'pdf', title: 'Lecture 7: Parsing', age: '1mo', source: 'whatsapp' },
  book: { id: 'book', kind: 'note', text: 'Piranesi by Susanna Clarke. Arjun says read it without looking anything up.', age: '5d' },
  ticket: { id: 'ticket', kind: 'image', art: 'ticket', height: 168, title: 'Boarding pass, BLR to CCU', age: '2mo', source: 'gmail' },
  sunset: { id: 'sunset', kind: 'image', art: 'sunset', height: 220, age: '1w', source: 'camera' },
  map: { id: 'map', kind: 'image', art: 'map', height: 190, title: '12th Main, Indiranagar', age: '2w', source: 'maps' },
  tweet: { id: 'tweet', kind: 'image', art: 'tweet', height: 160, title: 'the best way to learn a codebase is to break it on purpose', age: '1mo', source: 'x' },
  chat: { id: 'chat', kind: 'image', art: 'chat', height: 206, title: 'you have to try this place', age: '3w', source: 'whatsapp' },
  essay: { id: 'essay', kind: 'link', host: 'paulgraham.com', title: 'How to Do Great Work', age: '4mo', source: 'chrome' },
  gift: { id: 'gift', kind: 'voice', length: '0:18', text: 'Gift for Riya: the blue film camera from the market, and a new strap.', age: '2w' },
  dal: { id: 'dal', kind: 'note', text: 'Dal makhani: soak overnight, kasuri methi only at the very end.', age: '3mo' },
} satisfies Record<string, Memory>

export const meta = (m: Memory) => [m.age, m.source].filter(Boolean).join(' · ')

const KIND_WORD: Record<Kind, string> = { image: 'image', pdf: 'pdf', link: 'link', note: 'note', voice: 'voice note' }
export const rowMeta = (m: Memory) => [m.age, m.source, KIND_WORD[m.kind]].filter(Boolean).join(' · ')

// The row's title (§17): a picture without words of its own is "Image".
export function titleOf(m: Memory): string {
  if (m.kind === 'note' || m.kind === 'voice') return m.text?.split('.')[0] ?? 'Note'
  return m.title ?? (m.kind === 'image' ? 'Image' : 'Document')
}

// Why a row matched (§9): the words around the hit, with the hit lit.
export type Why = [string, string, string]
