import { useDashboardIrr } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import './IrrWidget.css'

function formatCurrency(val: number | null): string {
  if (val === null) return '--'
  const sign = val >= 0 ? '+' : ''
  return `${sign}C$ ${Math.abs(val).toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
}

function formatPct(val: number | null): string {
  if (val === null) return '--'
  return `${val >= 0 ? '+' : ''}${val.toFixed(1)}%`
}

function valueClass(val: number | null): string {
  if (val === null) return 'irr-neutral'
  return val >= 0 ? 'irr-positive' : 'irr-negative'
}

export default function IrrWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardIrr(connectionId)

  if (isLoading || !data) return <Skeleton style={{ height: '8rem', width: '100%' }} />

  const hasData = data.portfolioTotalReturn !== null || data.portfolioIrr !== null ||
    data.accounts.some(a => a.totalReturn !== null || a.irr !== null)

  if (!hasData) {
    return <div className="irr-empty">No return data available yet</div>
  }

  const isSingleAccount = !!connectionId
  const acct = isSingleAccount ? data.accounts[0] : null

  const totalReturn = isSingleAccount ? acct?.totalReturn ?? null : data.portfolioTotalReturn ?? null
  const totalReturnPct = isSingleAccount ? acct?.totalReturnPct ?? null : data.portfolioTotalReturnPct ?? null
  const xirr = isSingleAccount ? acct?.irr ?? null : data.portfolioIrr ?? null
  const dividendYield = isSingleAccount ? acct?.dividendYield ?? null : data.portfolioDividendYield ?? null

  return (
    <div className="irr-card">
      {/* Headline: total gain/loss */}
      <div className={`irr-headline-value ${valueClass(totalReturn)}`}>
        {formatCurrency(totalReturn)}
      </div>

      {/* Divider + metric rows */}
      <div className="irr-divider" />

      <div className="irr-metrics">
        <div className="irr-metric-row">
          <span className="irr-metric-label">ROI</span>
          <span className={`irr-metric-value ${valueClass(totalReturnPct)}`}>
            {formatPct(totalReturnPct)}
          </span>
        </div>
        <div className="irr-metric-row">
          <span className="irr-metric-label">IRR</span>
          <span className={`irr-metric-value ${valueClass(xirr)}`}>
            {formatPct(xirr)}
          </span>
        </div>
        {dividendYield !== null && (
          <div className="irr-metric-row">
            <span className="irr-metric-label">Div Yield</span>
            <span className="irr-metric-value">
              {dividendYield.toFixed(2)}%
            </span>
          </div>
        )}
      </div>
    </div>
  )
}
