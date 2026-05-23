import type { CapitalMetrics } from '@/types/wheel'
import { formatCurrency } from '@/services/brokerService'
import './CapitalSummary.css'

interface CapitalSummaryProps {
  metrics: CapitalMetrics | null
}

export function CapitalSummary({ metrics }: CapitalSummaryProps) {
  if (!metrics) return null

  const items = [
    { label: 'Available Cash', value: metrics.availableCash },
    { label: 'Deployed (CSPs)', value: metrics.deployedCsp },
    { label: 'Shares Held', value: metrics.sharesHeld },
    { label: 'CCs Written', value: metrics.ccsWritten },
    { label: 'Total Premium', value: metrics.totalPremium, isPnl: true },
    { label: 'Unrealized P&L', value: metrics.unrealizedPnl, isPnl: true },
  ]

  return (
    <div className="wheel-capital-bar">
      {items.map(item => (
        <div key={item.label} className="wheel-capital-item">
          <div className="wheel-capital-label">{item.label}</div>
          <div
            className={`wheel-capital-value ${
              item.isPnl
                ? item.value >= 0
                  ? 'wheel-pnl-positive'
                  : 'wheel-pnl-negative'
                : ''
            }`}
          >
            {item.isPnl && item.value >= 0 ? '+' : ''}
            {formatCurrency(item.value, 'USD')}
          </div>
        </div>
      ))}
    </div>
  )
}
