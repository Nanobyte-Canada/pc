import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import { useConnectionPositions, useBrokerConnections, useTriggerPositionFetch, useBalanceHistory } from '../hooks/useBrokerConnections'
import { formatCurrency, formatPercent, formatQuantity, getRelativeTime } from '../services/brokerService'
import { ConnectionStatus } from '../components/broker/ConnectionStatus'
import { CashBalanceCards } from '../components/broker/CashBalanceCards'
import { AccountActivitiesGrid } from '../components/broker/AccountActivitiesGrid'
import type { BrokerPosition } from '../types/broker'

import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'
import { useAgGridTheme } from '@/hooks/useAgGridTheme'
import './PositionDetailsPage.css'

type TabType = 'positions' | 'activities'

export function PositionDetailsPage() {
  const agTheme = useAgGridTheme()
  const { connectionId } = useParams<{ connectionId: string }>()
  const navigate = useNavigate()
  const id = parseInt(connectionId || '0', 10)
  const [activeTab, setActiveTab] = useState<TabType>('positions')

  const { data: connectionsData } = useBrokerConnections()
  const { data: positionsData, isLoading, refetch } = useConnectionPositions(id, id > 0)
  const triggerFetch = useTriggerPositionFetch()
  const { data: balanceData } = useBalanceHistory(id, 90, id > 0)

  const connection = connectionsData?.connections.find(c => c.id === id)
  const positions = positionsData?.positions || []
  const summary = positionsData?.summary
  const latestSnapshot = balanceData?.snapshots?.[0] || null

  const handleRefresh = () => {
    triggerFetch.mutate(id, {
      onSuccess: () => {
        setTimeout(() => refetch(), 2500)
      }
    })
  }

  const columnDefs: ColDef<BrokerPosition>[] = [
    {
      headerName: 'Symbol',
      field: 'symbol',
      width: 100,
      pinned: 'left',
      cellStyle: { fontWeight: 600 }
    },
    {
      headerName: 'Name',
      field: 'securityName',
      flex: 1,
      minWidth: 200,
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    {
      headerName: 'Type',
      field: 'instrumentType',
      width: 100,
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    {
      headerName: 'Quantity',
      field: 'quantity',
      width: 120,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) => formatQuantity(params.value)
    },
    {
      headerName: 'Avg Cost',
      field: 'averageCost',
      width: 120,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) => formatCurrency(params.value)
    },
    {
      headerName: 'Current Price',
      field: 'currentPrice',
      width: 130,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) => formatCurrency(params.value)
    },
    {
      headerName: 'Value',
      field: 'currentValue',
      width: 130,
      type: 'rightAligned',
      sort: 'desc',
      cellStyle: { fontWeight: 600 },
      valueFormatter: (params: ValueFormatterParams) => formatCurrency(params.value)
    },
    {
      headerName: 'P&L',
      field: 'totalPnl',
      width: 120,
      type: 'rightAligned',
      cellStyle: (params) => ({
        color: params.value >= 0 ? 'var(--success)' : 'var(--error)',
        fontWeight: 500
      }),
      valueFormatter: (params: ValueFormatterParams) => {
        if (params.value === null) return '-'
        const sign = params.value >= 0 ? '+' : ''
        return sign + formatCurrency(params.value)
      }
    },
    {
      headerName: 'P&L %',
      field: 'totalPnlPercent',
      width: 100,
      type: 'rightAligned',
      cellStyle: (params) => ({
        color: params.value >= 0 ? 'var(--success)' : 'var(--error)',
        fontWeight: 500
      }),
      valueFormatter: (params: ValueFormatterParams) => formatPercent(params.value)
    },
    {
      headerName: 'Currency',
      field: 'currency',
      width: 90
    }
  ]

  if (!id || id <= 0) {
    return (
      <div className="position-invalid">
        <p>Invalid connection ID</p>
        <Link to="/brokers/positions">Back to Positions</Link>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="position-details-page page-loading">
        <div>Loading positions...</div>
      </div>
    )
  }

  return (
    <div className="position-details-page">
      {/* Breadcrumb */}
      <div className="position-breadcrumb">
        <Link to="/brokers/positions">
          <span className="back-arrow">&larr;</span>
          Back to All Positions
        </Link>
      </div>

      {/* Header */}
      <div className="position-details-header">
        <div>
          <div className="position-title-row">
            <h1>
              {connection?.broker.name || positionsData?.broker || 'Broker'} Positions
            </h1>
            {connection && <ConnectionStatus status={connection.status} />}
          </div>
          <div className="position-account-info">
            {connection?.accountType && <span>{connection.accountType} &middot; </span>}
            {connection?.accountNumber && <span>Account: {connection.accountNumber}</span>}
          </div>
        </div>

        <div className="position-header-actions">
          {connection?.lastPositionsFetchedAt && (
            <span className="position-last-updated">
              Last updated: {getRelativeTime(connection.lastPositionsFetchedAt)}
            </span>
          )}
          <button
            onClick={handleRefresh}
            disabled={triggerFetch.isPending || connection?.status !== 'ACTIVE'}
            className="refresh-btn"
          >
            {triggerFetch.isPending ? 'Fetching...' : 'Refresh Positions'}
          </button>
        </div>
      </div>

      {/* Cash Balance Cards */}
      <CashBalanceCards latestSnapshot={latestSnapshot} />

      {/* Summary Cards */}
      {summary && (
        <div className="positions-summary">
          <SummaryCard title="Total Value" value={formatCurrency(summary.totalValue)} />
          <SummaryCard title="Total Contribution" value={formatCurrency(summary.totalCost)} />
          <SummaryCard
            title="Total P&L"
            value={`${summary.totalPnl >= 0 ? '+' : ''}${formatCurrency(summary.totalPnl)}`}
            subtitle={formatPercent(summary.totalPnlPercent)}
            valueColor={summary.totalPnl >= 0 ? 'var(--success)' : 'var(--error)'}
          />
          <SummaryCard title="Positions" value={positions.length.toString()} />
        </div>
      )}

      {/* Connection Error */}
      {connection?.status === 'ERROR' && connection.errorMessage && (
        <div className="position-alert error">
          <span>Connection Error: {connection.errorMessage}</span>
          <button className="alert-btn" onClick={() => navigate('/brokers/connections')}>
            Reconnect
          </button>
        </div>
      )}

      {connection?.status === 'EXPIRED' && (
        <div className="position-alert warning">
          <span>Token expired. Please reconnect to refresh positions.</span>
          <button className="alert-btn" onClick={() => navigate('/brokers/connections')}>
            Reconnect
          </button>
        </div>
      )}

      {/* Tabs */}
      <div className="position-tabs">
        <button
          className={`tab-btn ${activeTab === 'positions' ? 'active' : ''}`}
          onClick={() => setActiveTab('positions')}
        >
          Positions
        </button>
        <button
          className={`tab-btn ${activeTab === 'activities' ? 'active' : ''}`}
          onClick={() => setActiveTab('activities')}
        >
          Activities
        </button>
      </div>

      {/* Tab Content */}
      {activeTab === 'positions' ? (
        positions.length === 0 ? (
          <div className="position-empty-state">
            <p>No positions found for this account.</p>
            <p>
              {connection?.status === 'ACTIVE'
                ? 'Click "Refresh Positions" to fetch the latest data.'
                : 'Please reconnect your broker to fetch positions.'}
            </p>
          </div>
        ) : (
          <div className={`${agTheme} position-grid-container`}>
            <AgGridReact
              rowData={positions}
              columnDefs={columnDefs}
              defaultColDef={{
                sortable: true,
                resizable: true
              }}
              animateRows={true}
              rowSelection="single"
              suppressCellFocus={true}
            />
          </div>
        )
      ) : (
        <AccountActivitiesGrid
          connectionId={id}
          connectionActive={connection?.status === 'ACTIVE'}
        />
      )}
    </div>
  )
}

interface SummaryCardProps {
  title: string
  value: string
  subtitle?: string
  valueColor?: string
}

function SummaryCard({ title, value, subtitle, valueColor }: SummaryCardProps) {
  return (
    <div className="positions-summary-card">
      <div className="card-title">{title}</div>
      <div className="card-value" style={valueColor ? { color: valueColor } : undefined}>{value}</div>
      {subtitle && <div className="card-subtitle">{subtitle}</div>}
    </div>
  )
}
