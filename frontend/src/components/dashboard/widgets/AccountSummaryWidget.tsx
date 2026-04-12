import { useDashboardSummary, useDashboardCash, useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { TrendingUp, TrendingDown } from 'lucide-react'
import './AccountSummaryWidget.css'

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

function getAccuracyVariant(accuracy: number): 'green' | 'amber' | 'red' {
  if (accuracy >= 85) return 'green'
  if (accuracy >= 65) return 'amber'
  return 'red'
}

export default function AccountSummaryWidget({ connectionId }: { connectionId?: number }) {
  const { data: summaryData, isLoading: summaryLoading } = useDashboardSummary(connectionId)
  const { data: cashData, isLoading: cashLoading } = useDashboardCash(connectionId)
  const { data: accountsData, isLoading: accountsLoading } = useDashboardAccounts()

  const isLoading = summaryLoading || cashLoading || accountsLoading

  if (isLoading || !summaryData) {
    return <Skeleton className="as-skeleton" />
  }

  const { totalValue, investmentValue, cashValue, totalChange, totalChangePercent } =
    summaryData.portfolioValue

  const dayChangeAvailable = totalChange != null && totalChangePercent != null
  const isPositive = dayChangeAvailable ? totalChange >= 0 : true
  const sign = isPositive ? '+' : ''

  // Cash & buying power from cash endpoint
  const totalCash = cashData?.totalCashCAD ?? cashValue
  const totalBuyingPower = cashData?.totalBuyingPowerCAD ?? 0

  // Find matching account for model/accuracy info
  const matchedAccount = connectionId
    ? accountsData?.accounts.find(a => a.connectionId === connectionId)
    : accountsData?.accounts[0]

  const accuracy = matchedAccount?.linkedGroup?.accuracy ?? null
  const groupName = matchedAccount?.linkedGroup?.name ?? null
  const modelName = matchedAccount?.modelPortfolioName ?? null

  return (
    <div className="as-widget">
      <div className="as-top">
        <div className="as-value-section">
          <div className="as-value">{formatCurrency(totalValue)}</div>
          <div className="as-change-row">
            {dayChangeAvailable ? (
              <>
                <span className={`as-change ${isPositive ? 'as-change--positive' : 'as-change--negative'}`}>
                  {isPositive
                    ? <TrendingUp className="as-change-icon" />
                    : <TrendingDown className="as-change-icon" />}
                  {sign}{formatCurrency(totalChange)}
                </span>
                <span className={`as-change-pct ${isPositive ? 'as-change--positive' : 'as-change--negative'}`}>
                  {sign}{totalChangePercent.toFixed(2)}%
                </span>
              </>
            ) : (
              <span className="as-change--unavailable">Day change unavailable</span>
            )}
          </div>
        </div>

        {(groupName || modelName || accuracy != null) && (
          <div className="as-model-section">
            {(groupName || modelName) && (
              <span className="as-model-name">{groupName || modelName}</span>
            )}
            {accuracy != null && (
              <span className={`as-accuracy-pill as-accuracy-pill--${getAccuracyVariant(accuracy)}`}>
                {accuracy.toFixed(0)}% Accuracy
              </span>
            )}
          </div>
        )}
      </div>

      <div className="as-stats">
        <div className="as-stat">
          <span className="as-stat-label">Investment</span>
          <span className="as-stat-value">{formatCurrency(investmentValue)}</span>
        </div>
        <div className="as-stat">
          <span className="as-stat-label">Cash</span>
          <span className="as-stat-value">{formatCurrency(totalCash)}</span>
        </div>
        <div className="as-stat">
          <span className="as-stat-label">Buying Power</span>
          <span className="as-stat-value">{formatCurrency(totalBuyingPower)}</span>
        </div>
        {dayChangeAvailable && (
          <div className="as-stat">
            <span className="as-stat-label">Day P&L</span>
            <span className={`as-stat-value ${isPositive ? 'as-stat-value--positive' : 'as-stat-value--negative'}`}>
              {sign}{formatCurrency(totalChange)}
            </span>
          </div>
        )}
      </div>
    </div>
  )
}
