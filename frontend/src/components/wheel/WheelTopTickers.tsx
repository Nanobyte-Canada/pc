import { useMemo, useRef, useState, useEffect, useCallback } from 'react'
import { useQueries } from '@tanstack/react-query'
import { getQuote } from '@/services/marketDataService'
import { formatCurrency } from '@/services/brokerService'
import { Plus } from 'lucide-react'
import './WheelTopTickers.css'

const TOP_TICKERS = ['SOXL', 'TECL', 'TQQQ', 'SPXU', 'SPY', 'QQQ', 'XLF', 'NVDA', 'AVGO'] as const

interface WheelTopTickersProps {
  onTickerClick: (ticker: string) => void
  onAddTicker: () => void
}

/**
 * Horizontal ticker bar showing top liquid options ETFs/stocks.
 * Each ticker shows current price and change. Clicking opens the chain panel.
 * Includes an "Add Ticker" button at the end.
 */
export function WheelTopTickers({ onTickerClick, onAddTicker }: WheelTopTickersProps) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const [activeIndex, setActiveIndex] = useState(0)
  const [isMobile, setIsMobile] = useState(false)

  useEffect(() => {
    const checkMobile = () => setIsMobile(window.innerWidth < 768)
    checkMobile()
    window.addEventListener('resize', checkMobile)
    return () => window.removeEventListener('resize', checkMobile)
  }, [])

  // Fetch quotes for all top tickers (fetch-on-load with staleTime)
  const quotesQuery = useQueries({
    queries: TOP_TICKERS.map(ticker => ({
      queryKey: ['wheel-quote', ticker],
      queryFn: async () => {
        const response = await getQuote(ticker)
        return {
          ticker,
          last: response.last ?? response.mid ?? 0,
          bid: response.bid ?? 0,
        }
      },
      staleTime: 300_000, // 5 minutes
    })),
  })

  const quotes = useMemo(() => {
    const map: Record<string, { last: number; bid: number }> = {}
    quotesQuery.forEach(q => {
      if (q.data) map[q.data.ticker] = { last: q.data.last, bid: q.data.bid }
    })
    return map
  }, [quotesQuery])

  // Handle scroll dot tracking on mobile
  const handleScroll = useCallback(() => {
    if (!scrollRef.current || !isMobile) return
    const el = scrollRef.current
    const scrollLeft = el.scrollLeft
    const cardWidth = el.children[0]?.getBoundingClientRect().width ?? 100
    const gap = 8
    const idx = Math.round(scrollLeft / (cardWidth + gap))
    setActiveIndex(Math.min(idx, TOP_TICKERS.length - 1))
  }, [isMobile])

  // Scroll to specific dot on mobile
  const scrollToIndex = useCallback((index: number) => {
    if (!scrollRef.current) return
    const el = scrollRef.current
    const child = el.children[index] as HTMLElement | undefined
    if (child) {
      child.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
      setActiveIndex(index)
    }
  }, [])

  return (
    <div className="wtt">
      <div
        ref={scrollRef}
        className="wtt__scroll"
        onScroll={handleScroll}
      >
        {TOP_TICKERS.map(ticker => {
          const q = quotes[ticker]
          const price = q?.last ?? 0
          const bid = q?.bid ?? 0
          const change = bid > 0 ? ((price / bid - 1) * 100) : 0
          const isUp = change >= 0

          return (
            <button
              key={ticker}
              className="wtt__ticker"
              onClick={() => onTickerClick(ticker)}
            >
              <span className="wtt__symbol">{ticker}</span>
              <span className="wtt__price">
                {price > 0 ? formatCurrency(price, 'USD') : '—'}
              </span>
              {price > 0 && bid > 0 && (
                <span className={`wtt__change ${isUp ? 'wtt__change--up' : 'wtt__change--down'}`}>
                  {isUp ? '+' : ''}{change.toFixed(1)}%
                </span>
              )}
            </button>
          )
        })}

        <button className="wtt__add-btn" onClick={onAddTicker}>
          <Plus size={14} />
          <span>Add Ticker</span>
        </button>
      </div>

      {isMobile && (
        <div className="wtt__dots">
          {[...TOP_TICKERS, 'add'].map((_, i) => (
            <span
              key={i}
              className={`wtt__dot ${i === activeIndex ? 'wtt__dot--active' : ''}`}
              onClick={() => i < TOP_TICKERS.length ? scrollToIndex(i) : onAddTicker()}
            />
          ))}
        </div>
      )}
    </div>
  )
}
