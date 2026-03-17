import { useDashboardSummary } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { TrendingUp, TrendingDown, BarChart3 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import './PortfolioValueWidget.css'

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

export default function PortfolioValueWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardSummary(connectionId)
  if (isLoading || !data) return <Skeleton style={{ height: '5rem', width: '100%' }} />

  const { totalValue, totalChange, totalChangePercent } = data.portfolioValue
  const isPositive = totalChange >= 0
  const sign = isPositive ? '+' : ''

  return (
    <div className="pv-widget">
      <div>
        <div className="pv-value">{formatCurrency(totalValue)}</div>
        <div className="pv-change-row">
          <div className={`pv-change ${isPositive ? 'pv-change-positive' : 'pv-change-negative'}`}>
            {isPositive ? <TrendingUp style={{ height: '1rem', width: '1rem' }} /> : <TrendingDown style={{ height: '1rem', width: '1rem' }} />}
            {sign}{formatCurrency(totalChange)}
          </div>
          <Badge variant={isPositive ? 'success' : 'destructive'} style={{ fontSize: '0.75rem' }}>
            {sign}{totalChangePercent.toFixed(2)}%
          </Badge>
        </div>
      </div>
      <div className="pv-icon-box">
        <BarChart3 style={{ height: '1.25rem', width: '1.25rem' }} />
      </div>
    </div>
  )
}
