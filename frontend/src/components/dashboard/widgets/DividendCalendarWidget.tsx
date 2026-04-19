import { useState, useMemo } from 'react'
import { useDividendCalendar } from '@/hooks/useDashboardWidgets'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ChevronLeft, ChevronRight, Calendar } from 'lucide-react'
import type { DividendEntry } from '@/types/dashboard'
import './DividendCalendarWidget.css'

function fmt(value: number, currency = 'CAD') {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency }).format(value)
}

export default function DividendCalendarWidget({ connectionId }: { connectionId?: number }) {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [monthNum, setMonthNum] = useState(now.getMonth() + 1)
  const month = `${year}-${String(monthNum).padStart(2, '0')}`
  const { data, isLoading } = useDividendCalendar(month, connectionId)
  const [hoveredDay, setHoveredDay] = useState<number | null>(null)

  const navigate = (dir: number) => {
    let m = monthNum + dir
    let y = year
    if (m > 12) { m = 1; y++ }
    if (m < 1) { m = 12; y-- }
    setMonthNum(m)
    setYear(y)
  }

  const entriesByDay = useMemo(() => {
    if (!data) return new Map<number, DividendEntry[]>()
    const map = new Map<number, DividendEntry[]>()
    for (const entry of data.entries) {
      const day = new Date(entry.date).getDate()
      const arr = map.get(day) ?? []
      arr.push(entry)
      map.set(day, arr)
    }
    return map
  }, [data])

  const dayClassification = useMemo(() => {
    const map = new Map<number, 'dividend' | 'reinvestment' | 'both'>()
    for (const [day, entries] of entriesByDay) {
      const hasDividend = entries.some(e => e.type !== 'REI')
      const hasReinvestment = entries.some(e => e.type === 'REI')
      map.set(day, hasDividend && hasReinvestment ? 'both' : hasReinvestment ? 'reinvestment' : 'dividend')
    }
    return map
  }, [entriesByDay])

  if (isLoading || !data) return <Skeleton style={{ height: '10rem', width: '100%' }} />

  const monthLabel = new Date(year, monthNum - 1).toLocaleString('en', { month: 'long', year: 'numeric' })

  const firstDay = new Date(year, monthNum - 1, 1).getDay()
  const daysInMonth = new Date(year, monthNum, 0).getDate()
  const dayLabels = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']

  const hoveredEntries = hoveredDay != null ? entriesByDay.get(hoveredDay) ?? [] : []

  return (
    <div>
      <div className="dc-nav">
        <Button variant="ghost" size="icon" style={{ height: '1.75rem', width: '1.75rem' }} onClick={() => navigate(-1)}>
          <ChevronLeft style={{ height: '1rem', width: '1rem' }} />
        </Button>
        <span className="dc-month-label">{monthLabel}</span>
        <Button variant="ghost" size="icon" style={{ height: '1.75rem', width: '1.75rem' }} onClick={() => navigate(1)}>
          <ChevronRight style={{ height: '1rem', width: '1rem' }} />
        </Button>
      </div>

      <div className="dc-total-row">
        <div>
          <span className="dc-total-label">Dividends</span>
          <span className="dc-total-value">{fmt(data.totalDividends)}</span>
        </div>
        <div>
          <span className="dc-total-label">Reinvested</span>
          <span className="dc-total-value dc-total-value--reinvestment">{fmt(data.totalReinvestments)}</span>
        </div>
      </div>

      <div className="dc-calendar-grid">
        {dayLabels.map(d => (
          <div key={d} className="dc-day-header">{d}</div>
        ))}
        {Array.from({ length: firstDay }).map((_, i) => (
          <div key={`empty-${i}`} />
        ))}
        {Array.from({ length: daysInMonth }).map((_, i) => {
          const day = i + 1
          const cls = dayClassification.get(day)
          const hasEntry = cls != null
          const dayClass = cls === 'both' ? 'dc-day-both' : cls === 'reinvestment' ? 'dc-day-reinvestment' : cls === 'dividend' ? 'dc-day-dividend' : ''
          return (
            <div
              key={day}
              className={`dc-day ${dayClass} ${hoveredDay === day ? 'dc-day-active' : ''}`}
              onMouseEnter={hasEntry ? () => setHoveredDay(day) : undefined}
              onMouseLeave={hasEntry ? () => setHoveredDay(null) : undefined}
            >
              {day}
            </div>
          )
        })}
      </div>

      {data.entries.length === 0 ? (
        <div className="dc-empty">
          <Calendar style={{ height: '1.5rem', width: '1.5rem' }} />
          <span>No dividends this month</span>
        </div>
      ) : (
        <div className="dc-hover-detail">
          {hoveredEntries.length > 0 ? (
            hoveredEntries.map((entry, i) => (
              <div key={i} className="dc-hover-entry">
                <span className={`dc-hover-type ${entry.type === 'REI' ? 'dc-hover-type--reinvestment' : 'dc-hover-type--dividend'}`}>
                  {entry.type === 'REI' ? 'DRIP' : 'DIV'}
                </span>
                <span className="dc-hover-symbol">{entry.symbol ?? 'N/A'}</span>
                <span className="dc-hover-account">{entry.accountName ?? ''}</span>
                <span className={`dc-hover-amount ${entry.type === 'REI' ? 'dc-hover-amount--reinvestment' : ''}`}>
                  {fmt(entry.amount, entry.currency)}
                </span>
              </div>
            ))
          ) : (
            <span className="dc-hover-hint">Hover on a highlighted day to see details</span>
          )}
        </div>
      )}
    </div>
  )
}
