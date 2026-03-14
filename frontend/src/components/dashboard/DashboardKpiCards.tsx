import { formatCurrency } from '../../services/brokerService'

interface DashboardKpiCardsProps {
  totalValue: number
  dayChange: number
  dayChangePercent: number
  averageAccuracy: number
}

export function DashboardKpiCards({ totalValue, dayChange, dayChangePercent, averageAccuracy }: DashboardKpiCardsProps) {
  const changeColor = dayChange >= 0 ? '#059669' : '#dc2626'
  const changeSign = dayChange >= 0 ? '+' : ''

  return (
    <div className="dashboard-kpi-grid">
      <div className="dashboard-kpi-card kpi-total-value">
        <div className="kpi-label">Total Portfolio Value</div>
        <div className="kpi-value">{formatCurrency(totalValue)}</div>
      </div>

      <div className="dashboard-kpi-card">
        <div className="kpi-label">Day Change</div>
        <div className="kpi-value" style={{ color: changeColor }}>
          {changeSign}{formatCurrency(dayChange)}
          <span className="kpi-sub"> ({changeSign}{dayChangePercent.toFixed(2)}%)</span>
        </div>
      </div>

      <div className="dashboard-kpi-card">
        <div className="kpi-label">Average Accuracy</div>
        <div className="kpi-value" style={{ color: averageAccuracy >= 90 ? '#059669' : averageAccuracy >= 75 ? '#d97706' : '#dc2626' }}>
          {averageAccuracy.toFixed(1)}%
        </div>
      </div>

      <div className="dashboard-kpi-card kpi-actions">
        <div className="kpi-label">Quick Actions</div>
        <div className="quick-actions">
          <a href="/portfolios" className="quick-action-link">View Portfolios</a>
          <a href="/brokers/connections" className="quick-action-link">Broker Connections</a>
        </div>
      </div>
    </div>
  )
}
