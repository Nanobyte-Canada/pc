import { formatCurrency } from '../../services/brokerService'

interface KpiCardProps {
  label: string
  value: number
  isCurrency?: boolean
  valueColor?: string
}

export function KpiCard({ label, value, isCurrency = true, valueColor }: KpiCardProps) {
  const displayValue = isCurrency ? formatCurrency(value) : value.toFixed(2)

  return (
    <div className="kpi-card">
      <div className="kpi-label">{label}</div>
      <div className="kpi-value" style={valueColor ? { color: valueColor } : undefined}>
        {displayValue}
      </div>
    </div>
  )
}
