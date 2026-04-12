import { useFees, useDividendCalendar } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import './FeesAndDividendsWidget.css'

function fmt(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

function formatMonth(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return new Date(year, m - 1).toLocaleString('en', { month: 'long', year: 'numeric' })
}

export default function FeesAndDividendsWidget({ connectionId }: { connectionId?: number }) {
  const now = new Date()
  const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

  const { data: feesData, isLoading: feesLoading } = useFees(connectionId)
  const { data: divData, isLoading: divLoading } = useDividendCalendar(currentMonth, connectionId)

  if (feesLoading || divLoading || !feesData || !divData) {
    return <Skeleton style={{ height: '10rem', width: '100%' }} />
  }

  const lastEntry = divData.entries.length > 0
    ? divData.entries[divData.entries.length - 1]
    : null
  const lastPayout = lastEntry ? fmt(lastEntry.amount) : '--'

  // YTD dividends: use totalDividends from the current month query as a proxy
  // (the backend returns month-scoped totals)
  const ytdDividends = divData.totalDividends

  return (
    <div className="fad-container">
      <div className="fad-section">
        <div className="fad-section-title">Fees &amp; Commission</div>
        <div className="fad-hero-value">{fmt(feesData.last12Months.total)}</div>
        <div className="fad-hero-sub">YTD Total</div>

        <div className="fad-stat-rows">
          <div className="fad-stat-row">
            <span className="fad-stat-label">Fees</span>
            <span className="fad-stat-value">{fmt(feesData.last12Months.totalFees)}</span>
          </div>
          <div className="fad-stat-row">
            <span className="fad-stat-label">Commission</span>
            <span className="fad-stat-value">{fmt(feesData.last12Months.totalCommissions)}</span>
          </div>
          <div className="fad-stat-row">
            <span className="fad-stat-label">MER (annual)</span>
            <span className="fad-stat-value">{fmt(feesData.last12Months.totalManagementExpense)}</span>
          </div>
        </div>
      </div>

      <div className="fad-divider" />

      <div className="fad-section">
        <div className="fad-section-title">Dividends</div>
        <div className="fad-hero-value">{fmt(divData.totalDividends)}</div>
        <div className="fad-hero-sub">{formatMonth(currentMonth)}</div>

        <div className="fad-stat-rows">
          <div className="fad-stat-row">
            <span className="fad-stat-label">YTD Total</span>
            <span className="fad-stat-value">{fmt(ytdDividends)}</span>
          </div>
          <div className="fad-stat-row">
            <span className="fad-stat-label">Last payout</span>
            <span className="fad-stat-value">{lastPayout}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
