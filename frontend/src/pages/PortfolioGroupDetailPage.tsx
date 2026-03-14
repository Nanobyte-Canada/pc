import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  usePortfolioGroup,
  usePortfolioGroups,
  useDriftAnalysis,
  useRebalanceTrades,
  useSetTargets,
  useLinkAccount,
  useUnlinkAccount,
  useUpdateGroupSettings,
  useAddExcludedAsset,
  useRemoveExcludedAsset,
  useDeletePortfolioGroup
} from '../hooks/usePortfolioGroups'
import { useBrokerConnections } from '../hooks/useBrokerConnections'
import { PortfolioGroupSidebar } from '../components/portfolio-groups/PortfolioGroupSidebar'
import { AccuracyGauge } from '../components/portfolio-groups/AccuracyGauge'
import { TotalValueCard } from '../components/portfolio-groups/TotalValueCard'
import { CashCard } from '../components/portfolio-groups/CashCard'
import { DriftHoldingsTable } from '../components/portfolio-groups/DriftHoldingsTable'
import { TradeExecutionPanel } from '../components/portfolio-groups/TradeExecutionPanel'
import { OrderHistoryTable } from '../components/portfolio-groups/OrderHistoryTable'
import { TargetAllocationsEditor } from '../components/portfolio-groups/TargetAllocationsEditor'
import { AccountLinker } from '../components/portfolio-groups/AccountLinker'
import { SettingsPanel } from '../components/portfolio-groups/SettingsPanel'
import { ExcludedAssetsPanel } from '../components/portfolio-groups/ExcludedAssetsPanel'
import { NewAssetsAlert } from '../components/portfolio-groups/NewAssetsAlert'
import { CreateGroupModal } from '../components/portfolio-groups/CreateGroupModal'
import { useCreatePortfolioGroup } from '../hooks/usePortfolioGroups'
import type { CreatePortfolioGroupRequest } from '../types/portfolioGroup'
import { useGroupOrders } from '../hooks/useTrading'
import { PerformanceTab } from '../components/performance/PerformanceTab'
import './PortfolioGroupDetailPage.css'

type Tab = 'overview' | 'targets' | 'accounts' | 'trades' | 'performance' | 'settings'

export function PortfolioGroupDetailPage() {
  const { groupId: groupIdParam } = useParams<{ groupId: string }>()
  const groupId = Number(groupIdParam)
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const [showCreateModal, setShowCreateModal] = useState(false)

  const { data: groupsData } = usePortfolioGroups()
  const { data: group, isLoading: groupLoading } = usePortfolioGroup(groupId)
  const { data: drift, isLoading: driftLoading } = useDriftAnalysis(groupId, activeTab === 'overview')
  const { data: rebalance, isLoading: rebalanceLoading } = useRebalanceTrades(groupId, activeTab === 'trades')
  const { data: connectionsData } = useBrokerConnections()
  const { data: ordersData } = useGroupOrders(groupId, activeTab === 'trades')

  const setTargets = useSetTargets()
  const linkAccount = useLinkAccount()
  const unlinkAccount = useUnlinkAccount()
  const updateSettings = useUpdateGroupSettings()
  const addExcluded = useAddExcludedAsset()
  const removeExcluded = useRemoveExcludedAsset()
  const createGroup = useCreatePortfolioGroup()
  const deleteGroup = useDeletePortfolioGroup()

  const groups = groupsData?.groups || []
  const connections = connectionsData?.connections || []

  const handleCreateGroup = (request: CreatePortfolioGroupRequest) => {
    createGroup.mutate(request, {
      onSuccess: (data) => {
        setShowCreateModal(false)
        navigate(`/portfolios/${data.id}`)
      }
    })
  }

  if (groupLoading) {
    return (
      <div className="portfolio-detail-page">
        <div className="loading-state">
          <div className="loading-spinner" />
          <p>Loading portfolio group...</p>
        </div>
      </div>
    )
  }

  if (!group) {
    return (
      <div className="portfolio-detail-page">
        <div className="empty-state">
          <h2>Portfolio group not found</h2>
          <button className="btn-primary" onClick={() => navigate('/portfolios')}>Back to Groups</button>
        </div>
      </div>
    )
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'targets', label: 'Targets' },
    { key: 'accounts', label: 'Accounts' },
    { key: 'trades', label: 'Orders' },
    { key: 'performance', label: 'Performance' },
    { key: 'settings', label: 'Settings' }
  ]

  return (
    <div className="portfolio-detail-page">
      <div className="portfolio-groups-layout">
        <PortfolioGroupSidebar
          groups={groups}
          selectedGroupId={groupId}
          onSelectGroup={(id) => navigate(`/portfolios/${id}`)}
          onCreateGroup={() => setShowCreateModal(true)}
        />
        <div className="portfolio-detail-main">
          <div className="detail-header">
            <div>
              <h1>{group.name}</h1>
              {group.description && <p className="detail-description">{group.description}</p>}
            </div>
            <button
              className="delete-group-btn"
              onClick={() => {
                if (window.confirm('Delete this portfolio group?')) {
                  deleteGroup.mutate(groupId, { onSuccess: () => navigate('/portfolios') })
                }
              }}
            >
              Delete Group
            </button>
          </div>

          <div className="detail-tabs">
            {tabs.map(tab => (
              <button
                key={tab.key}
                className={`detail-tab ${activeTab === tab.key ? 'active' : ''}`}
                onClick={() => setActiveTab(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          <div className="detail-content">
            {activeTab === 'overview' && (
              <div className="overview-tab">
                <div className="overview-cards">
                  <div className="overview-gauge-card">
                    <AccuracyGauge accuracy={drift?.accuracy ?? group.accuracy} size={140} />
                  </div>
                  <TotalValueCard totalValue={drift?.totalValue ?? group.totalValue} />
                  <CashCard cash={drift?.cash ?? {}} />
                </div>

                {drift && drift.newAssets.length > 0 && (
                  <NewAssetsAlert
                    newAssets={drift.newAssets}
                    onExclude={(symbol) => addExcluded.mutate({ groupId, symbol })}
                  />
                )}

                <div className="section">
                  <h3>Holdings & Drift</h3>
                  {driftLoading ? (
                    <p className="text-muted">Loading drift analysis...</p>
                  ) : drift ? (
                    <DriftHoldingsTable holdings={drift.holdings} />
                  ) : (
                    <p className="text-muted">No drift data available. Link accounts and set targets.</p>
                  )}
                </div>
              </div>
            )}

            {activeTab === 'targets' && (
              <div className="targets-tab">
                <h3>Target Allocations</h3>
                <TargetAllocationsEditor
                  targets={group.targets}
                  onSave={(targets) => setTargets.mutate({ groupId, request: { targets } })}
                  isSaving={setTargets.isPending}
                />
              </div>
            )}

            {activeTab === 'accounts' && (
              <div className="accounts-tab">
                <AccountLinker
                  linkedAccounts={group.linkedAccounts}
                  availableConnections={connections}
                  onLink={(connectionId) => linkAccount.mutate({ groupId, connectionId })}
                  onUnlink={(connectionId) => unlinkAccount.mutate({ groupId, connectionId })}
                  isLinking={linkAccount.isPending || unlinkAccount.isPending}
                />
              </div>
            )}

            {activeTab === 'trades' && (
              <div className="trades-tab">
                <div className="section">
                  <h3>Rebalance Trades</h3>
                  <TradeExecutionPanel
                    groupId={groupId}
                    rebalance={rebalance ?? null}
                    isLoading={rebalanceLoading}
                  />
                </div>

                <div className="section" style={{ marginTop: '2rem' }}>
                  <h3>Order History</h3>
                  <OrderHistoryTable orders={ordersData?.orders ?? []} />
                </div>
              </div>
            )}

            {activeTab === 'performance' && (
              <div className="performance-tab-container">
                <PerformanceTab groupId={groupId} />
              </div>
            )}

            {activeTab === 'settings' && (
              <div className="settings-tab">
                <div className="section">
                  <h3>Rebalancing Settings</h3>
                  <SettingsPanel
                    settings={group.settings}
                    onUpdate={(request) => updateSettings.mutate({ groupId, request })}
                    isUpdating={updateSettings.isPending}
                  />
                </div>
                <div className="section" style={{ marginTop: '2rem' }}>
                  <h3>Excluded Assets</h3>
                  <ExcludedAssetsPanel
                    excludedAssets={group.excludedAssets}
                    onAdd={(symbol) => addExcluded.mutate({ groupId, symbol })}
                    onRemove={(symbol) => removeExcluded.mutate({ groupId, symbol })}
                    isUpdating={addExcluded.isPending || removeExcluded.isPending}
                  />
                </div>
              </div>
            )}
          </div>
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
