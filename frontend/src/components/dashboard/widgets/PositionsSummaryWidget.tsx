import { useDashboardSummary } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { BarChart2, PieChart, Layers, CircleDot, Banknote, HelpCircle, TrendingUp } from 'lucide-react'
import './PositionsSummaryWidget.css'

const TYPE_CONFIG: Record<string, { icon: typeof BarChart2; bgClass: string; textClass: string }> = {
  Stocks: { icon: TrendingUp, bgClass: 'ps-bg-blue', textClass: 'ps-text-blue' },
  ETFs: { icon: PieChart, bgClass: 'ps-bg-teal', textClass: 'ps-text-teal' },
  'Mutual Funds': { icon: Layers, bgClass: 'ps-bg-purple', textClass: 'ps-text-purple' },
  Options: { icon: BarChart2, bgClass: 'ps-bg-orange', textClass: 'ps-text-orange' },
  Bonds: { icon: CircleDot, bgClass: 'ps-bg-indigo', textClass: 'ps-text-indigo' },
  Cash: { icon: Banknote, bgClass: 'ps-bg-green', textClass: 'ps-text-green' },
  Other: { icon: HelpCircle, bgClass: 'ps-bg-gray', textClass: 'ps-text-gray' },
}

export default function PositionsSummaryWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardSummary(connectionId)
  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  const s = data.positionsSummary
  const items = [
    { label: 'Stocks', count: s.stocks },
    { label: 'ETFs', count: s.etfs },
    { label: 'Mutual Funds', count: s.mutualFunds },
    { label: 'Options', count: s.options },
    { label: 'Bonds', count: s.bonds },
    { label: 'Cash', count: s.cash },
    { label: 'Other', count: s.other },
  ].filter(i => i.count > 0)

  return (
    <div>
      <div className="ps-total">{s.total}</div>
      <div className="ps-grid">
        {items.map(item => {
          const config = TYPE_CONFIG[item.label] || TYPE_CONFIG.Other
          const Icon = config.icon
          return (
            <div key={item.label} className={`ps-type-item ${config.bgClass}`}>
              <Icon className={`ps-type-icon ${config.textClass}`} />
              <span className="ps-type-label">{item.count} {item.label}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
