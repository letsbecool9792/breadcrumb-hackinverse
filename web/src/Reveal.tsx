import { motion, type Variants } from 'motion/react'
import { Fragment, type ReactNode } from 'react'
import { EASE } from './ease.ts'

// Things arriving as the page scrolls to them. Each plays once; with motion
// reduced, only the fades remain (MotionConfig in App).

// A section's title, its words rising into place from behind their line, then
// its one line beneath.
export function RevealHeading({ title, note }: { title: string; note: string }) {
  const words = title.split(' ')
  return (
    <motion.div className="flex flex-col gap-4" initial="hidden" whileInView="shown" viewport={{ once: true, amount: 0.6 }}>
      <h2 className="font-serif text-[clamp(2.2rem,6vw,4rem)] leading-[1.05]">
        <span className="sr-only">{title}</span>
        {words.map((word, i) => (
          <Fragment key={i}>
            <span className="-mb-[0.16em] inline-block overflow-hidden pb-[0.16em] align-top" aria-hidden="true">
              <motion.span
                className="inline-block"
                variants={{ hidden: { y: '110%' }, shown: { y: 0, transition: { duration: 0.85, ease: EASE, delay: i * 0.07 } } }}
              >
                {word}
              </motion.span>
            </span>{' '}
          </Fragment>
        ))}
      </h2>
      <motion.p
        className="font-mono text-xs text-ink-outline"
        variants={{
          hidden: { opacity: 0, y: 8 },
          shown: { opacity: 1, y: 0, transition: { duration: 0.6, delay: 0.35 + words.length * 0.07 } },
        }}
      >
        {note}
      </motion.p>
    </motion.div>
  )
}

const RISE: Variants = {
  hidden: { opacity: 0, y: 56, scale: 0.97 },
  shown: { opacity: 1, y: 0, scale: 1 },
}

// A screen rising into view; tilted, it also lies back first and stands up.
const TILT: Variants = {
  hidden: { opacity: 0, y: 70, rotateX: 18, scale: 0.94 },
  shown: { opacity: 1, y: 0, rotateX: 0, scale: 1 },
}

export function Reveal({
  children,
  className,
  delay = 0,
  tilt = false,
}: {
  children: ReactNode
  className?: string
  delay?: number
  tilt?: boolean
}) {
  return (
    <motion.div
      className={className}
      style={tilt ? { transformPerspective: 1200 } : undefined}
      variants={tilt ? TILT : RISE}
      initial="hidden"
      whileInView="shown"
      viewport={{ once: true, amount: 0.25 }}
      transition={{ duration: 1, ease: EASE, delay }}
    >
      {children}
    </motion.div>
  )
}
