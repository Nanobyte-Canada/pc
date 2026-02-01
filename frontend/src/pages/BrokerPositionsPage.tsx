import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import { useAggregatedPositions, useBrokerConnections } from '../hooks/useBrokerConnections'
import { formatCurrency, formatPercent, formatQuantity, getRelativeTime } from '../services/brokerService'
import type { AggregatedPosition, BrokerConnection } from '../types/broker'

import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-alpine.css'

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
        color: params.value >= 0 ? '#10b981' : '#ef4444',
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
        color: params.value >= 0 ? '#10b981' : '#ef4444',
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
        return params.value.map((b: { broker: string }) => b.broker.charAt(0)).join(', ')
      },
      cellRenderer: (params: { value: { broker: string; quantity: number }[] }) => {
        if (!params.value || params.value.length === 0) return '-'
        return (
          <div style={{ display: 'flex', gap: '4px' }}>
            {params.value.map((b, i) => (
              <span
                key={i}
                style={{
                  padding: '2px 6px',
                  borderRadius: '4px',
                  backgroundColor: '#f3f4f6',
                  fontSize: '11px',
                  color: '#374151'
                }}
                title={`${b.broker}: ${formatQuantity(b.quantity)}`}
              >
                {b.broker.substring(0, 2)}
              </span>
            ))}
          </div>
        )
      }
    }
  ]

  if (positionsLoading) {
    return (
      <div style={{ padding: '24px', textAlign: 'center' }}>
        <div>Loading positions...</div>
      </div>
    )
  }

  return (
    <div style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto', height: 'calc(100vh - 100px)' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <h1 style={{ fontSize: '24px', fontWeight: 600, color: '#111827', margin: 0 }}>
            Portfolio Positions
          </h1>

          {/* View mode toggle */}
          <div style={{ display: 'flex', borderRadius: '6px', border: '1px solid #e5e7eb', overflow: 'hidden' }}>
            <button
              onClick={() => setViewMode('all')}
              style={{
                padding: '6px 12px',
                border: 'none',
                backgroundColor: viewMode === 'all' ? '#3b82f6' : '#fff',
                color: viewMode === 'all' ? '#fff' : '#374151',
                fontSize: '13px',
                fontWeight: 500,
                cursor: 'pointer'
              }}
            >
              All
            </button>
            <button
              onClick={() => setViewMode('by-broker')}
              style={{
                padding: '6px 12px',
                border: 'none',
                borderLeft: '1px solid #e5e7eb',
                backgroundColor: viewMode === 'by-broker' ? '#3b82f6' : '#fff',
                color: viewMode === 'by-broker' ? '#fff' : '#374151',
                fontSize: '13px',
                fontWeight: 500,
                cursor: 'pointer'
              }}
            >
              By Broker
            </button>
          </div>
        </div>

        {lastFetchTime && (
          <span style={{ fontSize: '13px', color: '#6b7280' }}>
            Last updated: {getRelativeTime(lastFetchTime)}
          </span>
        )}
      </div>

      {/* Summary Cards */}
      {summary && (
        <div style={{ display: 'flex', gap: '16px', marginBottom: '24px' }}>
          <SummaryCard title="Total Value" value={formatCurrency(summary.totalValue)} />
          <SummaryCard
            title="Total P&L"
            value={`${summary.totalPnl >= 0 ? '+' : ''}${formatCurrency(summary.totalPnl)}`}
            subtitle={formatPercent(summary.totalPnlPercent)}
            valueColor={summary.totalPnl >= 0 ? '#10b981' : '#ef4444'}
          />
          <SummaryCard title="Accounts" value={summary.accountCount.toString()} subtitle={`${summary.brokerCount} broker${summary.brokerCount > 1 ? 's' : ''}`} />
          <SummaryCard title="Positions" value={positions.length.toString()} />
        </div>
      )}

      {/* Content based on view mode */}
      {viewMode === 'all' ? (
        // Aggregated Positions Grid
        positions.length === 0 ? (
          <div
            style={{
              border: '1px dashed #d1d5db',
              borderRadius: '8px',
              padding: '48px',
              textAlign: 'center',
              color: '#6b7280'
            }}
          >
            <p style={{ margin: 0, fontSize: '16px' }}>No positions found.</p>
            <p style={{ margin: '8px 0 0', fontSize: '14px' }}>
              Connect a broker and fetch positions to see your portfolio here.
            </p>
          </div>
        ) : (
          <div className="ag-theme-alpine" style={{ height: 'calc(100% - 180px)', width: '100%' }}>
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
    <div
      style={{
        flex: 1,
        padding: '16px',
        backgroundColor: '#fff',
        border: '1px solid #e5e7eb',
        borderRadius: '8px'
      }}
    >
      <div style={{ fontSize: '13px', color: '#6b7280', marginBottom: '4px' }}>{title}</div>
      <div style={{ fontSize: '24px', fontWeight: 600, color: valueColor || '#111827' }}>{value}</div>
      {subtitle && <div style={{ fontSize: '13px', color: '#6b7280', marginTop: '2px' }}>{subtitle}</div>}
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
      <div
        style={{
          border: '1px dashed #d1d5db',
          borderRadius: '8px',
          padding: '48px',
          textAlign: 'center',
          color: '#6b7280'
        }}
      >
        <p style={{ margin: 0, fontSize: '16px' }}>No broker accounts with positions.</p>
        <p style={{ margin: '8px 0 0', fontSize: '14px' }}>
          <Link to="/brokers/connections" style={{ color: '#3b82f6' }}>
            Connect a broker
          </Link>{' '}
          to start tracking your positions.
        </p>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {activeConnections.map(connection => (
        <Link
          key={connection.id}
          to={`/brokers/positions/${connection.id}`}
          style={{ textDecoration: 'none' }}
        >
          <div
            style={{
              padding: '16px 20px',
              backgroundColor: '#fff',
              border: '1px solid #e5e7eb',
              borderRadius: '8px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              transition: 'border-color 0.2s, box-shadow 0.2s',
              cursor: 'pointer'
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = '#3b82f6'
              e.currentTarget.style.boxShadow = '0 2px 4px rgba(59, 130, 246, 0.1)'
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = '#e5e7eb'
              e.currentTarget.style.boxShadow = 'none'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div
                style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '8px',
                  backgroundColor: '#f3f4f6',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 600,
                  color: '#374151',
                  fontSize: '14px'
                }}
              >
                {connection.broker.code.substring(0, 2)}
              </div>
              <div>
                <div style={{ fontWeight: 600, color: '#111827', fontSize: '15px' }}>
                  {connection.broker.name}
                </div>
                <div style={{ fontSize: '13px', color: '#6b7280' }}>
                  {connection.accountType && `${connection.accountType} - `}
                  {connection.accountNumber || 'Account'}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '32px' }}>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '13px', color: '#6b7280' }}>Positions</div>
                <div style={{ fontWeight: 600, color: '#111827' }}>{connection.positionsCount}</div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '13px', color: '#6b7280' }}>Total Value</div>
                <div style={{ fontWeight: 600, color: '#111827' }}>
                  {connection.totalValue !== null ? formatCurrency(connection.totalValue) : '-'}
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '13px', color: '#6b7280' }}>Last Updated</div>
                <div style={{ fontSize: '13px', color: '#374151' }}>
                  {connection.lastPositionsFetchedAt
                    ? getRelativeTime(connection.lastPositionsFetchedAt)
                    : 'Never'}
                </div>
              </div>
              <div
                style={{
                  color: '#3b82f6',
                  fontSize: '18px',
                  fontWeight: 500
                }}
              >
                &rarr;
              </div>
            </div>
          </div>
        </Link>
      ))}
    </div>
  )
}
