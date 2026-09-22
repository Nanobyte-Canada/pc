import { useCallback, useEffect, useRef, type RefObject } from 'react'

/**
 * AG Grid viewports that scroll horizontally but have no tabbable content.
 * Safari does not auto-add scrollable regions to the tab order, so axe-core
 * (WebKit only) flags them with `scrollable-region-focusable`.
 */
const SCROLLABLE_VIEWPORT_SELECTOR = '.ag-header-viewport, .ag-center-cols-viewport'

/**
 * The scroll-content wrappers inside each viewport. They carry
 * `role="rowgroup"`, so making them focusable keeps the treegrid's ARIA
 * structure valid, while `tabindex` on the viewport itself (which has
 * `role="presentation"`) would trip axe-core's `aria-required-children` rule.
 */
const SCROLL_CONTENT_SELECTOR = '.ag-header-container, .ag-center-cols-container'

/**
 * Allowance for sub-pixel rounding and hidden scrollbars before a viewport is
 * considered horizontally overflowing. Matches axe-core's own scrollable
 * threshold (`getScroll(node, 13)`).
 */
const OVERFLOW_TOLERANCE_PX = 13

const RESIZE_THROTTLE_MS = 150

/**
 * Give horizontally overflowing AG Grid viewports keyboard access by making
 * their scroll-content wrapper focusable. The `tabindex` is added only while
 * the viewport actually overflows and removed otherwise. Scoped to the grid's
 * own root element — never the whole document.
 */
export function patchGridScrollableFocus(root: HTMLElement | null): void {
  if (!root) return

  root.querySelectorAll<HTMLElement>(SCROLLABLE_VIEWPORT_SELECTOR).forEach((viewport) => {
    const content = Array.from(viewport.children).find(
      (child): child is HTMLElement =>
        child instanceof HTMLElement && child.matches(SCROLL_CONTENT_SELECTOR)
    )
    if (!content) return

    if (viewport.scrollWidth > viewport.clientWidth + OVERFLOW_TOLERANCE_PX) {
      content.setAttribute('tabindex', '0')
    } else {
      content.removeAttribute('tabindex')
    }
  })
}

export interface GridScrollableFocusHandlers {
  onGridReady: () => void
  onFirstDataRendered: () => void
  onRowDataUpdated: () => void
  onColumnResized: () => void
}

/**
 * Returns AG Grid event handlers that keep horizontally overflowing viewports
 * keyboard accessible. Spread them onto `<AgGridReact>` and attach `rootRef`
 * to the grid's wrapper element so the patch stays scoped to that grid.
 */
export function useGridScrollableFocus(
  rootRef: RefObject<HTMLElement | null>
): GridScrollableFocusHandlers {
  const throttleRef = useRef<number | null>(null)

  const patch = useCallback(() => {
    patchGridScrollableFocus(rootRef.current)
  }, [rootRef])

  useEffect(() => {
    const handleResize = () => {
      if (throttleRef.current !== null) return
      throttleRef.current = window.setTimeout(() => {
        throttleRef.current = null
        patch()
      }, RESIZE_THROTTLE_MS)
    }

    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      if (throttleRef.current !== null) {
        window.clearTimeout(throttleRef.current)
        throttleRef.current = null
      }
    }
  }, [patch])

  return {
    onGridReady: patch,
    onFirstDataRendered: patch,
    onRowDataUpdated: patch,
    onColumnResized: patch,
  }
}
