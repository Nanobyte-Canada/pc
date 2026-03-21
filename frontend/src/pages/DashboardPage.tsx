import { useDashboardSummary } from '@/hooks/useDashboardWidgets'
import { DashboardGrid } from '../components/dashboard/DashboardGrid'
import { TrendingUp, TrendingDown } from 'lucide-react'
import './DashboardPage.css'

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

function getGreeting() {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

export function DashboardPage() {
  const { data } = useDashboardSummary()
  const pv = data?.portfolioValue
  const isPositive = (pv?.totalChange ?? 0) >= 0

  return (
    <div className="dashboard-page">
      {/* Hero Header */}
      <div className="dashboard-hero">
        <div className="dashboard-hero-top">
          <div>
            <p className="dashboard-hero-greeting">{getGreeting()}</p>
            <p className="dashboard-hero-date">
              {new Date().toLocaleDateString('en-CA', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
            </p>
          </div>
        </div>
        {pv && (
          <div className="dashboard-hero-value-section">
            <p className="dashboard-hero-label">Total Portfolio Value</p>
            <p className="dashboard-hero-value">{formatCurrency(pv.totalValue)}</p>
            <div className="dashboard-hero-change">
              {isPositive ? (
                <TrendingUp style={{ height: '1rem', width: '1rem', color: '#86efac' }} />
              ) : (
                <TrendingDown style={{ height: '1rem', width: '1rem', color: '#fca5a5' }} />
              )}
              <span className={isPositive ? 'dashboard-hero-change-positive' : 'dashboard-hero-change-negative'}>
                {isPositive ? '+' : ''}{formatCurrency(pv.totalChange ?? 0)} ({isPositive ? '+' : ''}{(pv.totalChangePercent ?? 0).toFixed(2)}%)
              </span>
              <span className="dashboard-hero-change-label">today</span>
            </div>
            <div className="dashboard-hero-breakdown">
              <div className="dashboard-hero-breakdown-item">
                <span className="dashboard-hero-breakdown-label">Investment</span>
                <span className="dashboard-hero-breakdown-value">{formatCurrency(pv.investmentValue)}</span>
              </div>
              <div className="dashboard-hero-breakdown-item">
                <span className="dashboard-hero-breakdown-label">Cash</span>
                <span className="dashboard-hero-breakdown-value">{formatCurrency(pv.cashValue)}</span>
              </div>
            </div>
          </div>
        )}
      </div>

      <DashboardGrid />
    </div>
  )
}
