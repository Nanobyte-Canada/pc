import { formatCurrency } from '../../services/brokerService'
import type { BalanceSnapshotDto } from '../../types/broker'

interface CashBalanceCardsProps {
  latestSnapshot: BalanceSnapshotDto | null
}

export function CashBalanceCards({ latestSnapshot }: CashBalanceCardsProps) {
  if (!latestSnapshot) return null

  const cashEntries = Object.entries(latestSnapshot.cash || {})

  return (
    <div className="cash-balance-cards">
      {latestSnapshot.totalValue != null && (
        <div className="cash-card">
          <div className="cash-card-label">Total Value</div>
          <div className="cash-card-value">{formatCurrency(latestSnapshot.totalValue)}</div>
        </div>
      )}
      {cashEntries.map(([currency, amount]) => (
        <div className="cash-card" key={currency}>
          <div className="cash-card-label">Cash ({currency})</div>
          <div className="cash-card-value">{formatCurrency(amount, currency)}</div>
        </div>
      ))}
    </div>
  )
}
