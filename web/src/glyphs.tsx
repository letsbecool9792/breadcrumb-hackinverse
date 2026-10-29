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

// Pictures stand in for the screenshots and photos a real phone holds.

export function ScreenshotThumb() {
  return (
    <svg viewBox="0 0 46 46" className="h-full w-full" aria-hidden="true">
      <rect width="46" height="46" fill="#f3efe9" />
      <rect width="46" height="9" fill="#1d2b3a" />
      <rect x="5" y="3.5" width="14" height="2" rx="1" fill="#9fb3c8" />
      <circle cx="9" cy="16" r="3.5" fill="#c9d4df" />
      <rect x="15" y="13.5" width="22" height="2.2" rx="1.1" fill="#3b4652" />
      <rect x="15" y="17.5" width="14" height="1.8" rx="0.9" fill="#a3abb3" />
      <rect x="5" y="25" width="36" height="2" rx="1" fill="#59636d" />
      <rect x="5" y="30" width="31" height="2" rx="1" fill="#59636d" />
      <rect x="5" y="35" width="34" height="2" rx="1" fill="#59636d" />
      <rect x="5" y="40" width="18" height="2" rx="1" fill="#59636d" />
    </svg>
  )
}

export function RamenThumb() {
  return (
    <svg viewBox="0 0 46 46" className="h-full w-full" aria-hidden="true">
      <rect width="46" height="46" fill="#3a2a1f" />
      <circle cx="23" cy="24" r="19" fill="#e9dccb" />
      <circle cx="23" cy="24" r="15.5" fill="#b8661c" />
      <path d="M12 22c4-3 7 3 11 0s7 3 11 0M12 27c4-3 7 3 11 0s7 3 11 0" stroke="#f1d58f" strokeWidth="1.6" fill="none" strokeLinecap="round" />
      <ellipse cx="29" cy="17.5" rx="5" ry="4.2" fill="#fbf5ea" />
      <circle cx="29.5" cy="17.8" r="2.2" fill="#f2a334" />
      <circle cx="15" cy="17" r="1.3" fill="#7c9a3f" />
      <circle cx="18" cy="31" r="1.3" fill="#7c9a3f" />
      <circle cx="30" cy="30" r="1.3" fill="#7c9a3f" />
      <rect x="27" y="23" width="8" height="5" rx="1" fill="#6d3d1d" transform="rotate(-12 31 25)" />
    </svg>
  )
}
