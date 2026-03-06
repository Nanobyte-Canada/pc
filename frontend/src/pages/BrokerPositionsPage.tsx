import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import { useAggregatedPositions, useBrokerConnections } from '../hooks/useBrokerConnections'
import { formatCurrency, formatPercent, formatQuantity, getRelativeTime } from '../services/brokerService'
import type { AggregatedPosition, BrokerConnection } from '../types/broker'

import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'
import './BrokerPositionsPage.css'

type ViewMode = 'all' | 'by-broker'

export function BrokerPositionsPage() {
  const [viewMode, setViewMode] = useState<ViewMode>('all')
  const { data: positionsData, isLoading: positionsLoading } = useAggregatedPositions()
  const { data: connectionsData } = useBrokerConnections()

  const positions = positionsData?.positions || []
  const summary = positionsData?.aggregateSummary

  // Get latest fetch time from connections
  const lastFetchTime = connectionsData?.connections
    .filter(c => c.lastPositionsFetchedAt)
    .sort((a, b) => new Date(b.lastPositionsFetchedAt!).getTime() - new Date(a.lastPositionsFetchedAt!).getTime())[0]?.lastPositionsFetchedAt

  const columnDefs: ColDef<AggregatedPosition>[] = [
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
      field: 'totalQuantity',
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
      headerName: 'Value',
      field: 'totalValue',
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
        color: params.value >= 0 ? '#059669' : '#dc2626',
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
        color: params.value >= 0 ? '#059669' : '#dc2626',
        fontWeight: 500
      }),
      valueFormatter: (params: ValueFormatterParams) => formatPercent(params.value)
    },
    {
      headerName: 'Brokers',
      field: 'brokerBreakdown',
      width: 150,
      valueFormatter: (params: ValueFormatterParams) => {
        if (!params.value || params.value.length === 0) return '-'
        return params.value.map((b: { broker: string | null }) => (b.broker || '?').charAt(0)).join(', ')
      },
      cellRenderer: (params: { value: { broker: string | null; quantity: number }[] }) => {
        if (!params.value || params.value.length === 0) return '-'
        return (
          <div style={{ display: 'flex', gap: '4px' }}>
            {params.value.map((b, i) => (
              <span
                key={i}
                className="broker-badge"
                title={`${b.broker || 'Unknown'}: ${formatQuantity(b.quantity)}`}
              >
                {(b.broker || '??').substring(0, 2)}
              </span>
            ))}
          </div>
        )
      }
    }
  ]

  if (positionsLoading) {
    return (
      <div className="broker-positions-page page-loading">
        <div>Loading positions...</div>
      </div>
    )
  }

  return (
    <div className="broker-positions-page">
      {/* Header */}
      <div className="positions-header">
        <div className="positions-header-left">
          <h1>Portfolio Positions</h1>

          {/* View mode toggle */}
          <div className="view-toggle">
            <button
              onClick={() => setViewMode('all')}
              className={viewMode === 'all' ? 'active' : ''}
            >
              All
            </button>
            <button
              onClick={() => setViewMode('by-broker')}
              className={viewMode === 'by-broker' ? 'active' : ''}
            >
              By Broker
            </button>
          </div>
        </div>

        {lastFetchTime && (
          <span className="positions-last-updated">
            Last updated: {getRelativeTime(lastFetchTime)}
          </span>
        )}
      </div>

      {/* Summary Cards */}
      {summary && (
        <div className="positions-summary">
          <SummaryCard title="Total Value" value={formatCurrency(summary.totalValue)} />
          <SummaryCard
            title="Total P&L"
            value={`${summary.totalPnl >= 0 ? '+' : ''}${formatCurrency(summary.totalPnl)}`}
            subtitle={formatPercent(summary.totalPnlPercent)}
            valueColor={summary.totalPnl >= 0 ? '#059669' : '#dc2626'}
          />
          <SummaryCard title="Accounts" value={summary.accountCount.toString()} subtitle={`${summary.brokerCount} broker${summary.brokerCount > 1 ? 's' : ''}`} />
          <SummaryCard title="Positions" value={positions.length.toString()} />
        </div>
      )}

      {/* Content based on view mode */}
      {viewMode === 'all' ? (
        // Aggregated Positions Grid
        positions.length === 0 ? (
          <div className="positions-empty-state">
            <p>No positions found.</p>
            <p>Connect a broker and fetch positions to see your portfolio here.</p>
          </div>
        ) : (
          <div className="ag-theme-quartz positions-grid-container">
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
        // By Broker View
        <BrokerAccountsList connections={connectionsData?.connections || []} />
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

interface BrokerAccountsListProps {
  connections: BrokerConnection[]
}

function BrokerAccountsList({ connections }: BrokerAccountsListProps) {
  const activeConnections = connections.filter(c => c.status === 'ACTIVE' || c.positionsCount > 0)

  if (activeConnections.length === 0) {
    return (
      <div className="positions-empty-state">
        <p>No broker accounts with positions.</p>
        <p>
          <Link to="/brokers/connections" className="broker-account-connect-link">
            Connect a broker
          </Link>{' '}
          to start tracking your positions.
        </p>
      </div>
    )
  }

  return (
    <div className="broker-accounts-list">
      {activeConnections.map(connection => (
        <Link
          key={connection.id}
          to={`/brokers/positions/${connection.id}`}
          className="broker-account-link"
        >
          <div className="broker-account-card">
            <div className="broker-account-left">
              <div className="broker-account-icon">
                {(connection.broker.name || '??').substring(0, 2)}
              </div>
              <div>
                <div className="broker-account-name">
                  {connection.broker.name}
                </div>
                <div className="broker-account-detail">
                  {connection.accountType && `${connection.accountType} - `}
                  {connection.accountNumber || 'Account'}
                </div>
              </div>
            </div>

            <div className="broker-account-right">
              <div className="broker-account-stat">
                <div className="stat-label">Positions</div>
                <div className="stat-value">{connection.positionsCount}</div>
              </div>
              <div className="broker-account-stat">
                <div className="stat-label">Total Value</div>
                <div className="stat-value">
                  {connection.totalValue !== null ? formatCurrency(connection.totalValue) : '-'}
                </div>
              </div>
              <div className="broker-account-stat">
                <div className="stat-label">Last Updated</div>
                <div className="stat-time">
                  {connection.lastPositionsFetchedAt
                    ? getRelativeTime(connection.lastPositionsFetchedAt)
                    : 'Never'}
                </div>
              </div>
              <div className="broker-account-arrow">&rarr;</div>
            </div>
          </div>
        </Link>
      ))}
    </div>
  )
}
