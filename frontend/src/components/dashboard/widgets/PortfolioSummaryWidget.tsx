import { useDashboardSummary, useDashboardCash } from '@/hooks/useDashboardWidgets'
import { Skeleton } from '@/components/ui/skeleton'
import { TrendingUp, TrendingDown } from 'lucide-react'
import './PortfolioSummaryWidget.css'

function fmtNum(value: number): string {
  return value.toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

function currencyPrefix(currency: string): string {
  if (currency === 'USD') return 'US$'
  return 'C$'
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
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

  // Cash & buying power from cash endpoint with per-currency breakdowns
  const totalCash = cashData?.totalCashCAD ?? cashValue
  const totalBuyingPower = cashData?.totalBuyingPowerCAD ?? 0
  const cashBreakdown = cashData?.availableCash ?? []
  const bpBreakdown = cashData?.buyingPower ?? []

  return (
    <div className="ps-widget">
      {/* Mobile hero: total portfolio value */}
      <div className="ps-hero">
        <div className="ps-hero-label">Portfolio Value</div>
        <div className="ps-hero-value">C$ {fmtNum(totalValue)}</div>
        <div className="ps-hero-change">
          {dayChangeAvailable ? (
            <span className={isPositive ? 'ps-gain' : 'ps-loss'}>
              {isPositive
                ? <TrendingUp className="ps-change-icon" />
                : <TrendingDown className="ps-change-icon" />}
              {sign}C$ {fmtNum(Math.abs(totalChange))}
              <span className="ps-change-pct">
                ({sign}{totalChangePercent.toFixed(2)}%)
              </span>
            </span>
          ) : (
            <span className="ps-change-na">Day change unavailable</span>
          )}
        </div>
      </div>

      {/* KPI cards row */}
      <div className="ps-kpi-row">
        {/* Investment card */}
        <div className="ps-kpi-card">
          <div className="ps-kpi-label">Investment</div>
          <div className="ps-kpi-value">C$ {fmtNum(investmentValue)}</div>
          {dayChangeAvailable && (
            <div className={`ps-kpi-delta ${isPositive ? 'ps-gain' : 'ps-loss'}`}>
              {sign}C$ {fmtNum(Math.abs(totalChange))} ({sign}{totalChangePercent.toFixed(1)}%)
            </div>
          )}
        </div>

        {/* Cash card with dual-currency breakdown */}
        <div className="ps-kpi-card">
          <div className="ps-kpi-label">Cash</div>
          <div className="ps-kpi-value">C$ {fmtNum(totalCash)}</div>
          {cashBreakdown.length > 0 && (
            <div className="ps-kpi-breakdown">
              <div className="ps-kpi-divider" />
              {cashBreakdown.map(c => (
                <div key={c.currency} className="ps-kpi-row-item">
                  <span className="ps-kpi-row-prefix">{currencyPrefix(c.currency)}</span>
                  <span className="ps-kpi-row-amount">{fmtNum(c.amount)}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Buying Power card with dual-currency breakdown */}
        <div className="ps-kpi-card">
          <div className="ps-kpi-label">Buying Power</div>
          <div className="ps-kpi-value">C$ {fmtNum(totalBuyingPower)}</div>
          {bpBreakdown.length > 0 && (
            <div className="ps-kpi-breakdown">
              <div className="ps-kpi-divider" />
              {bpBreakdown.map(c => (
                <div key={c.currency} className="ps-kpi-row-item">
                  <span className="ps-kpi-row-prefix">{currencyPrefix(c.currency)}</span>
                  <span className="ps-kpi-row-amount">{fmtNum(c.amount)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
