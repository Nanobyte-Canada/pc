import { formatCurrency } from '../../services/brokerService'

interface TotalValueCardProps {
  totalValue: number
}

export function TotalValueCard({ totalValue }: TotalValueCardProps) {
  return (
    <div className="info-card total-value-card">
      <div className="info-card-label">Total Value</div>
      <div className="info-card-value">{formatCurrency(totalValue)}</div>
    </div>
  )
}
