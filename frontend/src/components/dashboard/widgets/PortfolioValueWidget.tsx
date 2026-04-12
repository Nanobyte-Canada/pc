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
  const dayChangeAvailable = totalChange != null && totalChangePercent != null
  const isPositive = dayChangeAvailable ? totalChange >= 0 : true
  const sign = isPositive ? '+' : ''

  return (
    <div>
      <div className="pv-widget">
        <div>
          <div className="pv-value">{formatCurrency(totalValue)}</div>
          <div className="pv-change-row">
            {dayChangeAvailable ? (
              <>
                <div className={`pv-change ${isPositive ? 'pv-change-positive' : 'pv-change-negative'}`}>
                  {isPositive ? <TrendingUp style={{ height: '1rem', width: '1rem' }} /> : <TrendingDown style={{ height: '1rem', width: '1rem' }} />}
                  {sign}{formatCurrency(totalChange)}
                </div>
                <Badge variant={isPositive ? 'success' : 'destructive'} style={{ fontSize: '0.75rem' }}>
                  {sign}{totalChangePercent.toFixed(2)}%
                </Badge>
              </>
            ) : (
              <span className="pv-change-unavailable">Day change unavailable</span>
            )}
          </div>
        </div>
        <div className="pv-icon-box">
          <BarChart3 style={{ height: '1.25rem', width: '1.25rem' }} />
        </div>
      </div>
      <div className="pv-breakdown">
        <div className="pv-breakdown-item">
          <span className="pv-breakdown-label">Investment</span>
          <span className="pv-breakdown-value">{formatCurrency(data.portfolioValue.investmentValue)}</span>
        </div>
        <div className="pv-breakdown-item">
          <span className="pv-breakdown-label">Cash</span>
          <span className="pv-breakdown-value">{formatCurrency(data.portfolioValue.cashValue)}</span>
        </div>
      </div>
    </div>
  )
}
