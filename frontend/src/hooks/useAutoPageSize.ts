import { useState, useEffect, type RefObject } from 'react'

export function useAutoPageSize(
  containerRef: RefObject<HTMLDivElement | null>,
  rowHeight: number,
  reservedHeight: number = 0
): number {
  const [pageSize, setPageSize] = useState(10)

  useEffect(() => {
    const el = containerRef.current
    if (!el) return

    const calculate = () => {
      const available = el.clientHeight - reservedHeight
      const rows = Math.floor(available / rowHeight)
      setPageSize(Math.max(rows, 3))
    }

    calculate()

    const observer = new ResizeObserver(calculate)
    observer.observe(el)
    return () => observer.disconnect()
  }, [containerRef, rowHeight, reservedHeight])

  return pageSize
}
