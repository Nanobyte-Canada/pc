import { useDashboardSummary, useDashboardCash, useDashboardAccounts } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { TrendingUp, TrendingDown } from 'lucide-react'
import './AccountSummaryWidget.css'

function fmtNum(value: number): string {
  return value.toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

function currencyPrefix(currency: string): string {
  if (currency === 'USD') return 'US$'
  return 'C$'
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

  // Cash & buying power
  const totalCash = cashData?.totalCashCAD ?? cashValue
  const totalBuyingPower = cashData?.totalBuyingPowerCAD ?? 0
  const cashBreakdown = cashData?.availableCash ?? []
  const bpBreakdown = cashData?.buyingPower ?? []

  // Find matching account for model/accuracy info
  const matchedAccount = connectionId
    ? accountsData?.accounts.find(a => a.connectionId === connectionId)
    : accountsData?.accounts[0]

  const accuracy = matchedAccount?.linkedGroup?.accuracy ?? null
  const groupName = matchedAccount?.linkedGroup?.name ?? null
  const modelName = matchedAccount?.modelPortfolioName ?? null

  return (
    <div className="as-widget">
      {/* Header: value + model info */}
      <div className="as-top">
        <div className="as-value-section">
          <div className="as-value">C$ {fmtNum(totalValue)}</div>
          <div className="as-change-row">
            {dayChangeAvailable ? (
              <span className={isPositive ? 'as-gain' : 'as-loss'}>
                {isPositive
                  ? <TrendingUp className="as-change-icon" />
                  : <TrendingDown className="as-change-icon" />}
                {sign}C$ {fmtNum(Math.abs(totalChange))}
                <span className="as-change-pct">
                  ({sign}{totalChangePercent.toFixed(2)}%)
                </span>
              </span>
            ) : (
              <span className="as-change-na">Day change unavailable</span>
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

      {/* KPI row */}
      <div className="as-stats">
        <div className="as-stat">
          <span className="as-stat-label">Investment</span>
          <span className="as-stat-value">{currencyPrefix('CAD')} {fmtNum(investmentValue)}</span>
        </div>
        <div className="as-stat">
          <span className="as-stat-label">Cash</span>
          <span className="as-stat-value">{currencyPrefix('CAD')} {fmtNum(totalCash)}</span>
          {cashBreakdown.length > 0 && (
            <div className="as-stat-breakdown">
              {cashBreakdown.map(c => (
                <span key={c.currency} className="as-stat-sub">
                  {currencyPrefix(c.currency)} {fmtNum(c.amount)}
                </span>
              ))}
            </div>
          )}
        </div>
        <div className="as-stat">
          <span className="as-stat-label">Buying Power</span>
          <span className="as-stat-value">{currencyPrefix('CAD')} {fmtNum(totalBuyingPower)}</span>
          {bpBreakdown.length > 0 && (
            <div className="as-stat-breakdown">
              {bpBreakdown.map(c => (
                <span key={c.currency} className="as-stat-sub">
                  {currencyPrefix(c.currency)} {fmtNum(c.amount)}
                </span>
              ))}
            </div>
          )}
        </div>
        {dayChangeAvailable && (
          <div className="as-stat">
            <span className="as-stat-label">Day P&L</span>
            <span className={`as-stat-value ${isPositive ? 'as-gain' : 'as-loss'}`}>
              {sign}C$ {fmtNum(Math.abs(totalChange))}
            </span>
          </div>
        )}
      </div>
    </div>
  )
}
