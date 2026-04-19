import { useDashboardIrr } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import './IrrWidget.css'

function formatCurrency(val: number | null): string {
  if (val === null) return '—'
  return val.toLocaleString('en-CA', { style: 'currency', currency: 'CAD', minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

function formatPct(val: number | null): string {
  if (val === null) return '—'
  return `${val >= 0 ? '+' : ''}${val.toFixed(2)}%`
}

function valueClass(val: number | null): string {
  if (val === null) return 'neutral'
  return val >= 0 ? 'positive' : 'negative'
}

export default function IrrWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardIrr(connectionId)

  if (isLoading || !data) return <Skeleton style={{ height: '10rem', width: '100%' }} />

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
    <div>
      <div className="irr-headline">
        <span className={`irr-value ${valueClass(totalReturn)}`}>
          {formatCurrency(totalReturn)}
        </span>
        <span className="irr-label">total return</span>
      </div>

      <div className="irr-metrics">
        <div className="irr-metric">
          <span className="irr-metric-label">Return</span>
          <span className={`irr-metric-value ${valueClass(totalReturnPct)}`}>{formatPct(totalReturnPct)}</span>
        </div>
        <div className="irr-metric">
          <span className="irr-metric-label">XIRR</span>
          <span className={`irr-metric-value ${valueClass(xirr)}`}>{formatPct(xirr)}</span>
        </div>
        <div className="irr-metric">
          <span className="irr-metric-label">Div Yield</span>
          <span className="irr-metric-value">{dividendYield !== null ? `${dividendYield.toFixed(2)}%` : '—'}</span>
        </div>
      </div>

      {!isSingleAccount && data.accounts.length > 0 && (
        <div className="irr-accounts">
          {data.accounts.map(a => (
            <div key={a.connectionId} className="irr-account-row">
              <span className="irr-account-name">
                {a.accountName || a.brokerName || `Account #${a.connectionId}`}
              </span>
              <span className={`irr-account-value ${valueClass(a.totalReturn)}`}>
                {formatCurrency(a.totalReturn)}
              </span>
              <span className={`irr-account-value ${valueClass(a.totalReturnPct)}`}>
                {formatPct(a.totalReturnPct)}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
