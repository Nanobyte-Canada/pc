import type { PortfolioGroupSummary } from '../../types/portfolioGroup'
import { formatCurrency } from '../../services/brokerService'
import './PortfolioGroupSidebar.css'

interface PortfolioGroupSidebarProps {
  groups: PortfolioGroupSummary[]
  selectedGroupId: number | null
  onSelectGroup: (groupId: number) => void
  onCreateGroup: () => void
}

export function PortfolioGroupSidebar({
  groups,
  selectedGroupId,
  onSelectGroup,
  onCreateGroup
}: PortfolioGroupSidebarProps) {
  const getAccuracyColor = (accuracy: number) => {
    if (accuracy >= 80) return '#059669'
    if (accuracy >= 50) return '#d97706'
    return '#dc2626'
  }

  return (
    <div className="portfolio-sidebar">
      <div className="sidebar-header">
        <h3>Portfolio Groups</h3>
        <button className="sidebar-create-btn" onClick={onCreateGroup}>+ New</button>
      </div>
      <div className="sidebar-list">
        {groups.length === 0 ? (
          <div className="sidebar-empty">
            <p>No portfolio groups yet.</p>
            <p>Create one to get started.</p>
          </div>
        ) : (
          groups.map(group => (
            <div
              key={group.id}
              className={`sidebar-item ${selectedGroupId === group.id ? 'selected' : ''}`}
              onClick={() => onSelectGroup(group.id)}
            >
              <div className="sidebar-item-header">
                <span className="sidebar-item-name">{group.name}</span>
                <span
                  className="sidebar-item-accuracy"
                  style={{ color: getAccuracyColor(group.accuracy) }}
                >
                  {Math.round(group.accuracy)}%
                </span>
              </div>
              <div className="sidebar-item-details">
                <span>{formatCurrency(group.totalValue)}</span>
                <span>{group.accountCount} account{group.accountCount !== 1 ? 's' : ''}</span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
