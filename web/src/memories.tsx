import { motion } from 'motion/react'
import { Art } from './art.tsx'
import { meta, rowMeta, titleOf, type Memory, type Why } from './data.ts'
import { DocumentGlyph, NoteGlyph, VoiceGlyph } from './glyphs.tsx'

// Memories drawn as the app draws them: tiles in the mosaic (design.md §6)
// and rows in a search's results (§9).

// A picture or a PDF's page travels between its tile and its row, as in the app (§10).
const travels = (m: Memory) => m.kind === 'image' || m.kind === 'pdf'
const travelId = (m: Memory, scope: string) => `${scope}-${m.id}`

const Meta = ({ m, className = 'text-ink-outline' }: { m: Memory; className?: string }) => (
  <p className={`font-mono text-[10px] tracking-[0.3px] ${className}`}>{meta(m)}</p>
)

export function Tile({ m, scope, className = '' }: { m: Memory; scope?: string; className?: string }) {
  const layoutId = scope && travels(m) ? travelId(m, scope) : undefined
  const shell = `overflow-hidden rounded-[10px] border border-ink-border bg-ink-elevated ${className}`

  switch (m.kind) {
    case 'image':
      return (
        <div className={`relative ${shell}`}>
          <motion.div layoutId={layoutId} className="bg-ink-media" style={{ height: m.height ?? 190 }}>
            <Art name={m.art!} />
          </motion.div>
          <div className="absolute inset-x-0 bottom-0 flex flex-col gap-1 bg-[linear-gradient(transparent,rgb(23_19_16/0.6),rgb(23_19_16/0.94))] px-[11px] pt-[30px] pb-2.5">
            {m.title && <p className="line-clamp-3 text-[13px] leading-[17px] font-medium text-bone">{m.title}</p>}
            <Meta m={m} className="text-bone-dim" />
          </div>
        </div>
      )
    case 'pdf':
      return (
        <div className={shell}>
          <div className="flex h-[108px] items-center justify-center bg-ink-media px-[13px] pt-[13px]">
            <motion.div layoutId={layoutId}>
              <DocumentGlyph width={40} height={52} />
            </motion.div>
          </div>
          <div className="flex flex-col gap-[5px] px-[11px] pt-[9px] pb-[11px]">
            <p className="line-clamp-2 text-sm leading-[19px] font-medium">{m.title}</p>
            <Meta m={m} />
          </div>
        </div>
      )
    case 'link':
      return (
        <div className={`flex flex-col gap-[9px] p-[13px] ${shell}`}>
          <div className="flex items-center gap-[7px]">
            <span className="grid size-4 place-items-center rounded bg-amber-container font-mono text-[9px] font-medium text-amber">
              {m.host![0].toUpperCase()}
            </span>
            <span className="truncate font-mono text-[10px] text-bone-dim">{m.host}</span>
          </div>
          <p className="line-clamp-4 text-sm leading-[19px] font-medium">{m.title}</p>
          <Meta m={m} />
        </div>
      )
    case 'note':
      return (
        <div className={`flex flex-col gap-1.5 px-[14px] pt-2 pb-[13px] ${shell}`}>
          <span className="h-[26px] text-[34px] leading-[34px] font-medium text-amber" aria-hidden="true">
            “
          </span>
          <p className="line-clamp-6 text-[15px] leading-[21px]">{m.text}</p>
          <Meta m={m} />
        </div>
      )
    case 'voice':
      return (
        <div className={`flex flex-col gap-[9px] px-[14px] py-[13px] ${shell}`}>
          <div className="flex items-center gap-2">
            <span className="grid size-[26px] place-items-center rounded-full bg-amber-container">
              <VoiceGlyph size={14} />
            </span>
            <span className="font-mono text-[10px] text-bone-dim">{m.length}</span>
          </div>
          <p className="line-clamp-5 text-[15px] leading-[21px]">{m.text}</p>
          <Meta m={m} />
        </div>
      )
  }
}

export function RowThumb({ m, scope }: { m: Memory; scope?: string }) {
  const layoutId = scope && travels(m) ? travelId(m, scope) : undefined
  return (
    <div className="grid size-[46px] shrink-0 place-items-center overflow-hidden rounded-[7px] border border-ink-border bg-ink-media">
      {m.kind === 'image' && (
        <motion.div layoutId={layoutId} className="size-full">
          <Art name={m.art!} />
        </motion.div>
      )}
      {m.kind === 'pdf' && (
        <motion.div layoutId={layoutId}>
          <DocumentGlyph width={24} height={30} />
        </motion.div>
      )}
      {m.kind === 'link' && <span className="font-mono text-base text-amber">{m.host![0].toUpperCase()}</span>}
      {m.kind === 'note' && <NoteGlyph className="w-full p-[9px]" />}
      {m.kind === 'voice' && <VoiceGlyph size={22} />}
    </div>
  )
}

export function ResultRow({ m, why, top, scope, index }: { m: Memory; why?: Why; top: boolean; scope?: string; index: number }) {
  return (
    <div className={`flex items-start gap-3 rounded-[10px] px-2.5 py-[13px] ${top ? 'bg-ink-elevated' : ''}`}>
      <RowThumb m={m} scope={scope} />
      <motion.div
        className="flex min-w-0 flex-1 flex-col gap-[5px]"
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.34, delay: 0.04 * index + 0.12, ease: [0.4, 0, 0.2, 1] }}
      >
        <p className="line-clamp-2 text-base leading-[21px] font-medium">{titleOf(m)}</p>
        {why && (
          <p className="line-clamp-3 font-mono text-[11px] leading-4 text-bone-dim">
            {why[0]}
            <motion.span
              className="rounded-[3px] px-0.5"
              initial={{ color: '#bfb2a4', backgroundColor: 'rgba(58, 44, 20, 0)' }}
              animate={{ color: '#ffc46b', backgroundColor: 'rgba(58, 44, 20, 1)' }}
              transition={{ duration: 0.52, delay: 0.04 * index + 0.3, ease: [0.4, 0, 0.2, 1] }}
            >
              {why[1]}
            </motion.span>
            {why[2]}
          </p>
        )}
        <p className="font-mono text-[10px] tracking-[0.3px] text-ink-outline">{rowMeta(m)}</p>
      </motion.div>
    </div>
  )
}
