import { motion, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef } from 'react'

// The page leaves a trail as it is read: crumbs down its edge drop in as they
// pass the middle of the screen, small and dim at the top, growing and
// brightening towards the download at the foot, as the logo's crumbs do.

const COUNT = 46
const COLOURS = ['#5a4420', '#7a5a28', '#c8912f', '#ffc46b']

export function ScrollTrail() {
  return (
    <div
      className="pointer-events-none absolute top-[100svh] bottom-40 z-10 w-6 [--amp:3.6px] [--cx:7px] sm:[--amp:6px] sm:[--cx:12px]"
      style={{ left: 'max(2px, calc((100% - 72rem) / 2 - 72px))' }}
      aria-hidden="true"
    >
      {Array.from({ length: COUNT }, (_, i) => (
        <Crumb key={i} i={i} />
      ))}
    </div>
  )
}

function Crumb({ i }: { i: number }) {
  const ref = useRef<HTMLSpanElement>(null)
  const passed = useInView(ref, { once: true, margin: '0px 0px -45% 0px' })
  const t = i / (COUNT - 1)
  const size = 3 + t * 5
  const colour = COLOURS[Math.min(COLOURS.length - 1, Math.floor(t * COLOURS.length))]
  return (
    <motion.span
      ref={ref}
      className="absolute rounded-full max-sm:scale-60"
      style={{
        top: `${t * 100}%`,
        left: `calc(var(--cx) + ${Math.sin(i * 0.62).toFixed(3)} * var(--amp) - ${size / 2}px)`,
        width: size,
        height: size,
        background: colour,
      }}
      initial={{ opacity: 0, scale: 0.4, y: -14 }}
      animate={passed ? { opacity: 1, scale: 1, y: 0 } : undefined}
      transition={{ duration: 0.64, ease: [0.34, 1.4, 0.64, 1] }}
    />
  )
}

// On a computer, the pointer drops crumbs too, which fade behind it.
export function PointerTrail() {
  const canvas = useRef<HTMLCanvasElement>(null)
  const reduced = useReducedMotion()

  useEffect(() => {
    const el = canvas.current
    if (!el || reduced || !matchMedia('(pointer: fine)').matches) return
    const ctx = el.getContext('2d')
    if (!ctx) return

    const crumbs: { x: number; y: number; born: number; n: number }[] = []
    let last: { x: number; y: number } | null = null
    let count = 0
    let frame = 0

    const resize = () => {
      const ratio = window.devicePixelRatio || 1
      el.width = innerWidth * ratio
      el.height = innerHeight * ratio
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
    }
    resize()

    const draw = (now: number) => {
      ctx.clearRect(0, 0, innerWidth, innerHeight)
      while (crumbs.length && now - crumbs[0].born > 1100) crumbs.shift()
      for (const c of crumbs) {
        const age = (now - c.born) / 1100
        const step = c.n % 4
        ctx.globalAlpha = Math.max(0, 1 - age) * 0.9
        ctx.fillStyle = COLOURS[step]
        ctx.beginPath()
        ctx.arc(c.x, c.y, (1.6 + step * 0.9) * (1 - age * 0.4), 0, Math.PI * 2)
        ctx.fill()
      }
      frame = crumbs.length ? requestAnimationFrame(draw) : 0
    }

    const move = (e: PointerEvent) => {
      if (last && Math.hypot(e.clientX - last.x, e.clientY - last.y) < 34) return
      last = { x: e.clientX, y: e.clientY }
      crumbs.push({ x: e.clientX, y: e.clientY, born: performance.now(), n: count++ })
      if (!frame) frame = requestAnimationFrame(draw)
    }

    addEventListener('pointermove', move, { passive: true })
    addEventListener('resize', resize)
    return () => {
      removeEventListener('pointermove', move)
      removeEventListener('resize', resize)
      cancelAnimationFrame(frame)
    }
  }, [reduced])

  return <canvas ref={canvas} className="pointer-events-none fixed inset-0 z-50 h-full w-full" aria-hidden="true" />
}
