import type { CapitalMetrics, DualCurrency } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import './CapitalSummary.css'

interface CapitalSummaryProps {
  metrics: CapitalMetrics | null
}

export function CapitalSummary({ metrics }: CapitalSummaryProps) {
  if (!metrics) return null

  return (
    <div className="wheel-capital-bar">
      <div className="wheel-capital-item">
        <div className="wheel-capital-label">Available Cash</div>
        <div className="wheel-capital-breakdown">
          <span className="wheel-capital-usd">{formatCurrency(metrics.cashUsd, 'USD')}</span>
          <span className="wheel-capital-sep">|</span>
          <span className="wheel-capital-cad">{formatCurrency(metrics.cashCad, 'CAD')}</span>
        </div>
        <div className="wheel-capital-total">
          Total: {formatCurrency(metrics.cashTotalUsd, 'USD')} / {formatCurrency(metrics.cashTotalCad, 'CAD')}
        </div>
      </div>
      <DualMetric label="Deployed (CSPs)" value={metrics.deployedCsp} />
      <DualMetric label="CCs Written" value={metrics.ccsWritten} />
      <DualMetric label="Total Premium" value={metrics.totalPremium} isPnl />
      <DualMetric label="Unrealized P&L" value={metrics.unrealizedPnl} isPnl />
    </div>
  )
}

function DualMetric({ label, value, isPnl }: { label: string; value: DualCurrency; isPnl?: boolean }) {
  const pnlClass = isPnl ? (value.usd >= 0 ? 'wheel-pnl-positive' : 'wheel-pnl-negative') : ''
  const prefix = isPnl && value.usd >= 0 ? '+' : ''
  const cadPrefix = isPnl && value.cad >= 0 ? '+' : ''

  return (
    <div className="wheel-capital-item">
      <div className="wheel-capital-label">{label}</div>
      <div className={`wheel-capital-value ${pnlClass}`}>
        {prefix}{formatCurrency(value.usd, 'USD')}
      </div>
      <div className={`wheel-capital-cad-line ${pnlClass}`}>
        {cadPrefix}{formatCurrency(value.cad, 'CAD')}
      </div>
    </div>
  )
}
