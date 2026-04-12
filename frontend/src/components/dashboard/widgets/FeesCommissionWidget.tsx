import { useFees } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { Receipt, DollarSign, Percent } from 'lucide-react'
import './FeesCommissionWidget.css'

function fmt(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

export default function FeesCommissionWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useFees(connectionId)
  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  const metrics = [
    { label: 'Fees', value: data.last12Months.totalFees, icon: Receipt, bgClass: 'fc-bg-red', textClass: 'fc-text-red' },
    { label: 'Commissions', value: data.last12Months.totalCommissions, icon: DollarSign, bgClass: 'fc-bg-orange', textClass: 'fc-text-orange' },
    { label: 'MER (annual)', value: data.last12Months.totalManagementExpense, icon: Percent, bgClass: 'fc-bg-amber', textClass: 'fc-text-amber' },
  ]

  return (
    <div>
      <div className="fc-header">
        <span className="fc-header-label">Last 12 months</span>
        <span className="fc-header-value">{fmt(data.last12Months.total)}</span>
      </div>

      <div className="fc-metrics">
        {metrics.map(m => {
          const Icon = m.icon
          return (
            <div key={m.label} className={`fc-metric-item ${m.bgClass}`}>
              <div className="fc-metric-left">
                <Icon className={`fc-metric-icon ${m.textClass}`} />
                <span className="fc-metric-label">{m.label}</span>
              </div>
              <span className="fc-metric-value">{fmt(m.value)}</span>
            </div>
          )
        })}
      </div>

      <div className="fc-footer">
        <span className="fc-footer-label">MER per month</span>
        <span className="fc-footer-value">{fmt(data.managementExpensePerMonth)}</span>
      </div>
    </div>
  )
}
