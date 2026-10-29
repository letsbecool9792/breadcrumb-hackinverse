// The app's marks and glyphs, drawn to docs/design.md §2 and §15 on the same
// unit grids, so the site and the app share one hand.

const CRUMB = {
  faint: 'var(--color-crumb-faint)',
  dim: 'var(--color-crumb-dim)',
  mid: 'var(--color-crumb-mid)',
  bright: 'var(--color-crumb-bright)',
}

// The logo's four crumbs on a 20-unit square. Walking, a light runs up the
// trail on a 1,300 ms lap (§2's stepLight).
export function CrumbTrail({ size, walking = false }: { size: number; walking?: boolean }) {
  const crumbs: [number, number, number, string][] = [
    [4, 15.5, 1.6, CRUMB.faint],
    [8.4, 12.4, 2.1, CRUMB.dim],
    [13.2, 8.6, 2.7, CRUMB.mid],
    [17, 4.2, 3, CRUMB.bright],
  ]
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" aria-hidden="true" className="shrink-0">
      {crumbs.map(([x, y, r, fill], i) => (
        <circle
          key={i}
          cx={x}
          cy={y}
          r={r}
          fill={fill}
          className={walking ? 'crumb-walk' : undefined}
          // Crumb i peaks at (i + ½)/5 of the lap; the keyframes peak at 20%.
          style={walking ? { animationDelay: `${260 * i - 1430}ms` } : undefined}
        />
      ))}
    </svg>
  )
}

// The capture sheet's mark, on a 46-unit square. Not playing, all at 0.35; on
// "Saved" the brightest drops in and the rest settle behind it.
export function CrumbMark({ size, play }: { size: number; play: boolean }) {
  const crumbs: [number, number, number, string, string][] = [
    [8, 37, 3.2, CRUMB.faint, 'mark-settle mark-d220'],
    [17, 30, 4.4, CRUMB.dim, 'mark-settle mark-d160'],
    [27.5, 21, 5.8, CRUMB.mid, 'mark-settle mark-d90'],
    [37, 10, 7, CRUMB.bright, 'mark-drop'],
  ]
  return (
    <svg width={size} height={size} viewBox="0 0 46 46" aria-hidden="true" className="shrink-0 overflow-visible">
      {crumbs.map(([x, y, r, fill, motion], i) => (
        <circle
          key={`${i}-${play}`}
          cx={x}
          cy={y}
          r={r}
          fill={fill}
          opacity={play ? undefined : 0.35}
          className={play ? motion : undefined}
        />
      ))}
    </svg>
  )
}

// Four rounded lines, the first lighter (§2 NoteGlyph).
export function NoteGlyph({ className = '' }: { className?: string }) {
  const lines: [number, string, number][] = [
    [0.7, 'var(--color-ink-outline)', 0.55],
    [1, 'var(--color-ink-border)', 1],
    [0.88, 'var(--color-ink-border)', 1],
    [0.94, 'var(--color-ink-border)', 1],
  ]
  return (
    <div className={`flex flex-col gap-1 ${className}`} aria-hidden="true">
      {lines.map(([w, colour, alpha], i) => (
        <div key={i} className="h-[3px] rounded-full" style={{ width: `${w * 100}%`, background: colour, opacity: alpha }} />
      ))}
    </div>
  )
}

// A page with a folded corner and five ruled lines (§2 DocumentGlyph).
export function DocumentGlyph({ width, height }: { width: number; height: number }) {
  const widths = [0.44, 0.64, 0.58, 0.64, 0.36]
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-hidden="true">
      <rect x="0.5" y="0.5" width={width - 1} height={height - 1} rx="3" fill="none" stroke="var(--color-ink-border)" />
      <rect x={width * 0.72} y="0" width={width * 0.28} height={width * 0.28} fill="var(--color-ink-outline)" opacity={0.55 * 0.35} />
      {widths.map((w, i) => (
        <rect
          key={i}
          x={width * 0.18}
          y={height * (0.36 + 0.12 * i)}
          width={width * w}
          height="2"
          rx="1"
          fill="var(--color-ink-outline)"
          opacity="0.55"
        />
      ))}
    </svg>
  )
}

// Five upright strokes: the voice note's mark everywhere (§2).
export function VoiceGlyph({ size, colour = 'var(--color-amber)' }: { size: number; colour?: string }) {
  const strokes: [number, number, number][] = [
    [2, 8, 3],
    [5, 4, 7],
    [8, 8, 12],
    [11, 12, 7],
    [14, 7, 3],
  ]
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" aria-hidden="true">
      {strokes.map(([x, , h], i) => (
        <line key={i} x1={x} x2={x} y1={8 - h / 2} y2={8 + h / 2} stroke={colour} strokeWidth="1.6" strokeLinecap="round" />
      ))}
    </svg>
  )
}
