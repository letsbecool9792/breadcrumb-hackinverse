import { useEffect, useState, useSyncExternalStore, type RefObject } from 'react'

const REDUCE = '(prefers-reduced-motion: reduce)'

export function useReducedMotion(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const query = matchMedia(REDUCE)
      query.addEventListener('change', onChange)
      return () => query.removeEventListener('change', onChange)
    },
    () => matchMedia(REDUCE).matches,
    () => false,
  )
}

// Whether the element is on screen. Motion that loops runs only while it is
// shown, as in the app (docs/design.md §0).
export function useInView(ref: RefObject<Element | null>, threshold = 0.3): boolean {
  const [inView, setInView] = useState(false)
  useEffect(() => {
    const element = ref.current
    if (!element) return
    const observer = new IntersectionObserver(([entry]) => setInView(entry.isIntersecting), { threshold })
    observer.observe(element)
    return () => observer.disconnect()
  }, [ref, threshold])
  return inView
}
