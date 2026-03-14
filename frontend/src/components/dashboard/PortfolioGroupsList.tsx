import { useNavigate } from 'react-router-dom'
import { formatCurrency } from '../../services/brokerService'
import type { DashboardGroupSummary } from '../../types/notification'

interface PortfolioGroupsListProps {
  groups: DashboardGroupSummary[]
}

export function PortfolioGroupsList({ groups }: PortfolioGroupsListProps) {
  const navigate = useNavigate()

  if (groups.length === 0) {
    return (
      <div className="dashboard-card">
        <h3>Portfolio Groups</h3>
        <p className="text-muted">No portfolio groups yet. Create one to get started.</p>
        <button className="btn-primary" onClick={() => navigate('/portfolios')}>
          Create Portfolio Group
        </button>
      </div>
    )
  }

  return (
    <div className="dashboard-card">
      <h3>Portfolio Groups</h3>
      <div className="dashboard-groups-list">
        {groups.map(group => (
          <div
            key={group.id}
            className="dashboard-group-item"
            onClick={() => navigate(`/portfolios/${group.id}`)}
          >
            <div className="group-item-info">
              <span className="group-item-name">{group.name}</span>
              <span className="group-item-detail">
                {group.targetCount} targets &middot; {group.accountCount} accounts
              </span>
            </div>
            <div className="group-item-stats">
              <span className="group-item-value">{formatCurrency(group.totalValue)}</span>
              <span
                className="group-item-accuracy"
                style={{ color: group.accuracy >= 90 ? '#059669' : group.accuracy >= 75 ? '#d97706' : '#dc2626' }}
              >
                {group.accuracy.toFixed(1)}%
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
