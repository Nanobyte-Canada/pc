import { useMemo } from 'react'
import type { WheelPosition, CCInfo } from '@/types/wheel'
import { getDteUrgency } from '@/types/wheel'
import { PositionCard } from './PositionCard'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import './WheelCalendarGrid.css'

interface ExpiryColumn {
  date: string
  dte: number
  dayOfWeek: string
  isMonthly: boolean
}

interface TickerRow {
  symbol: string
  currentPrice: number | null
  currency: string
  totalExposure: number
  ccInfo: CCInfo | null
  cells: Record<string, { positions: WheelPosition[] }>
}

interface WheelCalendarGridProps {
  tickerRows: TickerRow[]
  expiries: ExpiryColumn[]
  dateRange: string
  onPrev: () => void
  onNext: () => void
  onToday: () => void
  onPositionClick: (position: WheelPosition, ticker: string, expiryDate: string) => void
  onEmptySlotClick: (ticker: string, expiryDate: string) => void
  onCCSlotClick: (ticker: string, expiryDate: string) => void
  onAddTicker: () => void
}

export function WheelCalendarGrid({
  tickerRows, expiries, dateRange,
  onPrev, onNext, onToday,
  onPositionClick, onEmptySlotClick, onCCSlotClick,
  onAddTicker,
}: WheelCalendarGridProps) {
  const today = new Date().toISOString().split('T')[0]

  const visibleDateRange = useMemo(() => {
    if (expiries.length === 0) return dateRange
    const isMobile = typeof window !== 'undefined' && window.innerWidth < 768
    const visibleCount = isMobile ? Math.min(2, expiries.length) : expiries.length
    const fmt = (iso: string) => {
      const d = new Date(iso + 'T00:00:00')
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    }
    const last = expiries[visibleCount - 1]
    const year = new Date(expiries[0].date + 'T00:00:00').getFullYear()
    return `${fmt(expiries[0].date)} — ${fmt(last.date)}, ${year}`
  }, [expiries, dateRange])

  return (
    <>
      <div className="wcg-timeline">
        <button className="wcg-timeline__btn" onClick={onPrev} aria-label="Previous weeks">
          <ChevronLeft size={14} />
        </button>
        <span className="wcg-timeline__range">{visibleDateRange}</span>
        <button className="wcg-timeline__btn" onClick={onNext} aria-label="Next weeks">
          <ChevronRight size={14} />
        </button>
        <button className="wcg-timeline__today" onClick={onToday}>Today</button>
      </div>

      <div className="wcg-wrapper">
        <div className="wcg-scroll">
          <table className="wcg-table">
            <thead>
              <tr>
                <th className="wcg-th-ticker">Ticker</th>
                {expiries.map(exp => {
                  const urgency = getDteUrgency(exp.dte)
                  const isToday = exp.date === today
                  return (
                    <th key={exp.date} className={`wcg-th-expiry ${isToday ? 'wcg-th-expiry--today' : ''}`}>
                      <div className="wcg-expiry-date">
                        {formatExpiryShort(exp.date)}
                        {exp.isMonthly && <span className="wcg-monthly-badge">Monthly</span>}
                      </div>
                      <div className="wcg-expiry-meta">
                        <span className={`wcg-dte wcg-dte--${urgency}`}>{exp.dte}d</span>
                        <span className="wcg-expiry-day">{exp.dayOfWeek.slice(0, 3)}</span>
                      </div>
                    </th>
                  )
                })}
              </tr>
            </thead>
            <tbody>
              {tickerRows.map(row => (
                <tr key={row.symbol}>
                  <td className="wcg-td-ticker">
                    <div className="wcg-ticker-name">{row.symbol}</div>
                    {row.ccInfo && (
                      <div className="wcg-ticker-shares">{row.ccInfo.sharesOwned} shares</div>
                    )}
                  </td>
                  {expiries.map(exp => {
                    const cell = row.cells[exp.date]
                    const positions = cell?.positions ?? []
                    const isToday = exp.date === today

                    return (
                      <td key={exp.date} className={`wcg-td-cell ${isToday ? 'wcg-td-cell--today' : ''}`}>
                        <div className="wcg-cell-content">
                          {positions.map(pos => (
                            <PositionCard
                              key={pos.id}
                              position={pos}
                              onClick={p => onPositionClick(p, row.symbol, exp.date)}
                            />
                          ))}
                          <div className="wcg-slot-row">
                            <button className="wcg-empty-slot" onClick={() => onEmptySlotClick(row.symbol, exp.date)}>
                              + CSP
                            </button>
                            <button className="wcg-cc-slot" onClick={() => onCCSlotClick(row.symbol, exp.date)}>
                              + CC
                            </button>
                          </div>
                        </div>
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <button className="wcg-add-ticker" onClick={onAddTicker}>+ Add Ticker</button>
      </div>

      <div className="wcg-legend">
        <span className="wcg-legend__item"><span className="wcg-legend__dot wcg-legend__dot--csp" /> CSP</span>
        <span className="wcg-legend__item"><span className="wcg-legend__dot wcg-legend__dot--cc" /> CC</span>
      </div>
    </>
  )
}

function formatExpiryShort(iso: string): string {
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}
