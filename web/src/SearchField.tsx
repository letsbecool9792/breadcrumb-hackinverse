import { CrumbTrail } from './glyphs.tsx'

// The search field (design.md §8): a pill with the trail at its start, walking
// while the ranked answer is on its way, and the amber light spilling under it.
export function SearchField({ text, ranking, lit = true }: { text: string; ranking: boolean; lit?: boolean }) {
  return (
    <div className="relative">
      <div className={`search-light ${lit ? '' : 'opacity-40'} ${ranking || text ? 'breathing' : ''}`} />
      <div
        className={`relative flex h-[54px] items-center gap-[11px] rounded-[27px] border bg-ink-elevated pr-4 pl-5 transition-colors ${
          lit ? 'border-amber-container' : 'border-ink-field-border'
        }`}
      >
        <CrumbTrail size={17} walking={ranking} />
        <span className="min-w-0 flex-1 truncate font-mono text-[13px] text-bone">
          {text === '' ? (
            <>
              {lit && <span className="caret" />}
              <span className="text-ink-outline">search your memory</span>
            </>
          ) : (
            <>
              {text}
              {lit && <span className="caret" />}
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
  )
}
