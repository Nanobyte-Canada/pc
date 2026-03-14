import { formatCurrency } from '../../services/brokerService'

interface CashCardProps {
  cash: Record<string, number>
}

export function CashCard({ cash }: CashCardProps) {
  const entries = Object.entries(cash)
  const totalCash = Object.values(cash).reduce((sum, v) => sum + v, 0)

  return (
    <div className="info-card cash-card">
      <div className="info-card-label">Cash</div>
      <div className="info-card-value">{formatCurrency(totalCash)}</div>
      {entries.length > 1 && (
        <div className="cash-breakdown">
          {entries.map(([currency, amount]) => (
            <div key={currency} className="cash-breakdown-item">
              <span className="cash-currency">{currency}</span>
              <span className="cash-amount">{formatCurrency(amount, currency)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
