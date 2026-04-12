import { useDashboardSummary, useDashboardCash } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { TrendingUp, TrendingDown } from 'lucide-react'
import './PortfolioSummaryWidget.css'

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

export default function PortfolioSummaryWidget(_props: { connectionId?: number }) {
  const { data: summaryData, isLoading: summaryLoading } = useDashboardSummary()
  const { data: cashData, isLoading: cashLoading } = useDashboardCash()

  const isLoading = summaryLoading || cashLoading

  if (isLoading || !summaryData) {
    return <Skeleton className="ps-skeleton" />
  }

  const { totalValue, investmentValue, cashValue, totalChange, totalChangePercent } =
    summaryData.portfolioValue

  const dayChangeAvailable = totalChange != null && totalChangePercent != null
  const isPositive = dayChangeAvailable ? totalChange >= 0 : true
  const sign = isPositive ? '+' : ''

  // Cash & buying power from cash endpoint
  const totalCash = cashData?.totalCashCAD ?? cashValue
  const totalBuyingPower = cashData?.totalBuyingPowerCAD ?? 0

  return (
    <div className="ps-widget">
      <div className="ps-top">
        <div className="ps-value-section">
          <div className="ps-value">{formatCurrency(totalValue)}</div>
          <div className="ps-change-row">
            {dayChangeAvailable ? (
              <>
                <span className={`ps-change ${isPositive ? 'ps-change--positive' : 'ps-change--negative'}`}>
                  {isPositive
                    ? <TrendingUp className="ps-change-icon" />
                    : <TrendingDown className="ps-change-icon" />}
                  {sign}{formatCurrency(totalChange)}
                </span>
                <span className={`ps-change-pct ${isPositive ? 'ps-change--positive' : 'ps-change--negative'}`}>
                  {sign}{totalChangePercent.toFixed(2)}% today
                </span>
              </>
            ) : (
              <span className="ps-change--unavailable">Day change unavailable</span>
            )}
          </div>
        </div>
      </div>

      <div className="ps-stats">
        <div className="ps-stat">
          <span className="ps-stat-label">Investment</span>
          <span className="ps-stat-value">{formatCurrency(investmentValue)}</span>
        </div>
        <div className="ps-stat">
          <span className="ps-stat-label">Cash</span>
          <span className="ps-stat-value">{formatCurrency(totalCash)}</span>
        </div>
        <div className="ps-stat">
          <span className="ps-stat-label">Buying Power</span>
          <span className="ps-stat-value">{formatCurrency(totalBuyingPower)}</span>
        </div>
      </div>
    </div>
  )
}
