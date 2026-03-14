import { useDashboard } from '../hooks/useDashboard'
import { DashboardKpiCards } from '../components/dashboard/DashboardKpiCards'
import { PortfolioGroupsList } from '../components/dashboard/PortfolioGroupsList'
import { RecentOrdersList } from '../components/dashboard/RecentOrdersList'
import { AlertsList } from '../components/dashboard/AlertsList'
import './DashboardPage.css'

export function DashboardPage() {
  const { data: dashboard, isLoading } = useDashboard()

  if (isLoading) {
    return (
      <div className="dashboard-page">
        <div className="loading-state">
          <div className="loading-spinner" />
          <p>Loading dashboard...</p>
        </div>
      </div>
    )
  }

  if (!dashboard) {
    return (
      <div className="dashboard-page">
        <div className="empty-state">
          <h2>Welcome to Portfolio Builder</h2>
          <p>Connect your broker accounts and create portfolio groups to get started.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="dashboard-page">
      <div className="dashboard-header">
        <h1>Dashboard</h1>
      </div>

      <DashboardKpiCards
        totalValue={dashboard.totalPortfolioValue}
        dayChange={dashboard.dayChange}
        dayChangePercent={dashboard.dayChangePercent}
        averageAccuracy={dashboard.averageAccuracy}
      />

      <div className="dashboard-grid">
        <div className="dashboard-main">
          <PortfolioGroupsList groups={dashboard.portfolioGroups} />
        </div>
        <div className="dashboard-sidebar">
          <RecentOrdersList orders={dashboard.recentOrders} />
          <AlertsList alerts={dashboard.activeAlerts} />
        </div>
      </div>
    </div>
  )
}
