import { useDashboardSummary } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import './HoldingsCountWidget.css'

export default function HoldingsCountWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardSummary(connectionId)
  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  const h = data.holdingsCount
  const coveragePct = Math.min(100, Math.max(0, h.coveragePercent))

  return (
    <div>
      <div className="hc-total">{h.totalUniqueHoldings}</div>

      <div className="hc-rows">
        <div className="hc-row">
          <span className="hc-row-label">Direct Stocks</span>
          <span className="hc-row-value">{h.directStocks}</span>
        </div>
        <div className="hc-row">
          <span className="hc-row-label">Look-Through</span>
          <span className="hc-row-value">{h.lookThroughStocks}</span>
        </div>
      </div>

      {/* Coverage bar */}
      <div>
        <div className="hc-coverage-header">
          <span className="hc-coverage-label">Coverage</span>
          <span className="hc-coverage-value">{coveragePct.toFixed(1)}%</span>
        </div>
        <div className="hc-progress-bg">
          <div
            className="hc-progress-fill"
            style={{ width: `${coveragePct}%` }}
          />
        </div>
      </div>

      {h.etfsDecomposed > 0 && (
        <div className="hc-decomposed">
          <span>{h.etfsDecomposed} ETFs decomposed</span>
        </div>
      )}
    </div>
  )
}
