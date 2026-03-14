import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { usePortfolioGroups, useCreatePortfolioGroup, useDeletePortfolioGroup } from '../hooks/usePortfolioGroups'
import { PortfolioGroupSidebar } from '../components/portfolio-groups/PortfolioGroupSidebar'
import { CreateGroupModal } from '../components/portfolio-groups/CreateGroupModal'
import { AccuracyGauge } from '../components/portfolio-groups/AccuracyGauge'
import { formatCurrency } from '../services/brokerService'
import type { CreatePortfolioGroupRequest } from '../types/portfolioGroup'
import './PortfolioGroupsPage.css'

export function PortfolioGroupsPage() {
  const navigate = useNavigate()
  const [showCreateModal, setShowCreateModal] = useState(false)

  const { data: groupsData, isLoading } = usePortfolioGroups()
  const createGroup = useCreatePortfolioGroup()
  const deleteGroup = useDeletePortfolioGroup()

  const groups = groupsData?.groups || []

  const handleSelectGroup = (groupId: number) => {
    navigate(`/portfolios/${groupId}`)
  }

  const handleCreateGroup = (request: CreatePortfolioGroupRequest) => {
    createGroup.mutate(request, {
      onSuccess: (data) => {
        setShowCreateModal(false)
        navigate(`/portfolios/${data.id}`)
      }
    })
  }

  const handleDeleteGroup = (groupId: number) => {
    if (!window.confirm('Are you sure you want to delete this portfolio group?')) return
    deleteGroup.mutate(groupId)
  }

  if (isLoading) {
    return (
      <div className="portfolio-groups-page">
        <div className="loading-state">
          <div className="loading-spinner" />
          <p>Loading portfolio groups...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="portfolio-groups-page">
      <div className="portfolio-groups-layout">
        <PortfolioGroupSidebar
          groups={groups}
          selectedGroupId={null}
          onSelectGroup={handleSelectGroup}
          onCreateGroup={() => setShowCreateModal(true)}
        />
        <div className="portfolio-groups-main">
          {groups.length === 0 ? (
            <div className="empty-state">
              <h2>Welcome to Model Portfolios</h2>
              <p>Create your first portfolio group to define target allocations and track drift.</p>
              <button className="btn-primary" onClick={() => setShowCreateModal(true)}>
                Create Portfolio Group
              </button>
            </div>
          ) : (
            <div className="groups-overview">
              <h2>Portfolio Groups Overview</h2>
              <div className="groups-grid">
                {groups.map(group => (
                  <div key={group.id} className="group-card" onClick={() => handleSelectGroup(group.id)}>
                    <div className="group-card-header">
                      <h3>{group.name}</h3>
                      <button
                        className="group-delete-btn"
                        onClick={e => { e.stopPropagation(); handleDeleteGroup(group.id) }}
                        title="Delete group"
                      >
                        &times;
                      </button>
                    </div>
                    {group.description && <p className="group-card-desc">{group.description}</p>}
                    <div className="group-card-metrics">
                      <AccuracyGauge accuracy={group.accuracy} size={80} />
                      <div className="group-card-stats">
                        <div className="stat-row">
                          <span className="stat-label">Total Value</span>
                          <span className="stat-value">{formatCurrency(group.totalValue)}</span>
                        </div>
                        <div className="stat-row">
                          <span className="stat-label">Targets</span>
                          <span className="stat-value">{group.targetCount}</span>
                        </div>
                        <div className="stat-row">
                          <span className="stat-label">Accounts</span>
                          <span className="stat-value">{group.accountCount}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      <CreateGroupModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSubmit={handleCreateGroup}
        isLoading={createGroup.isPending}
      />
    </div>
  )
}
