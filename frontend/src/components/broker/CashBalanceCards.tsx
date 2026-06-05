import { formatCurrency } from '../../services/brokerService'
import type { BalanceSnapshotDto } from '../../types/broker'
import './CashBalanceCards.css'

interface CashBalanceCardsProps {
  latestSnapshot: BalanceSnapshotDto | null
}

export function CashBalanceCards({ latestSnapshot }: CashBalanceCardsProps) {
  if (!latestSnapshot) return null

  const cashEntries = Object.entries(latestSnapshot.cash || {})
  const cadEntry = cashEntries.find(([c]) => c === 'CAD')
  const usdEntry = cashEntries.find(([c]) => c === 'USD')
  const otherEntries = cashEntries.filter(([c]) => c !== 'CAD' && c !== 'USD')

  return (
    <div className="cash-balance-cards">
      {/* Total value card */}
      {latestSnapshot.totalValue != null && (
        <div className="cash-card">
          <div className="cash-card-label">Total Value</div>
          <div className="cash-card-value">{formatCurrency(latestSnapshot.totalValue)}</div>

          {/* Dual-currency breakdown below divider */}
          {(cadEntry || usdEntry) && (
            <>
              <div className="cash-card-divider" />
              <div className="cash-card-breakdown">
                {cadEntry && (
                  <div className="cash-breakdown-row">
                    <span className="cash-breakdown-label">C$</span>
                    <span className="cash-breakdown-value">{formatCurrency(cadEntry[1], 'CAD')}</span>
                  </div>
                )}
                {usdEntry && (
                  <div className="cash-breakdown-row">
                    <span className="cash-breakdown-label">US$</span>
                    <span className="cash-breakdown-value">{formatCurrency(usdEntry[1], 'USD')}</span>
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      )}

      {/* Other currency cards (non CAD/USD) */}
      {otherEntries.map(([currency, amount]) => (
        <div className="cash-card" key={currency}>
          <div className="cash-card-label">Cash ({currency})</div>
          <div className="cash-card-value">{formatCurrency(amount, currency)}</div>
        </div>
      ))}
    </div>
  )
}
