import { useParams, Link, useNavigate } from 'react-router-dom'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import { useConnectionPositions, useBrokerConnections, useTriggerPositionFetch } from '../hooks/useBrokerConnections'
import { formatCurrency, formatPercent, formatQuantity, getRelativeTime } from '../services/brokerService'
import { ConnectionStatus } from '../components/broker/ConnectionStatus'
import type { BrokerPosition } from '../types/broker'

import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-alpine.css'

export function PositionDetailsPage() {
  const { connectionId } = useParams<{ connectionId: string }>()
  const navigate = useNavigate()
  const id = parseInt(connectionId || '0', 10)

  const { data: connectionsData } = useBrokerConnections()
  const { data: positionsData, isLoading, refetch } = useConnectionPositions(id, id > 0)
  const triggerFetch = useTriggerPositionFetch()

  // Find the connection
  const connection = connectionsData?.connections.find(c => c.id === id)

  const positions = positionsData?.positions || []
  const summary = positionsData?.summary

  const handleRefresh = () => {
    triggerFetch.mutate(id, {
      onSuccess: () => {
        // Refetch positions after a delay
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
      headerName: 'Currency',
      field: 'currency',
      width: 90
    }
  ]

  if (!id || id <= 0) {
    return (
      <div style={{ padding: '24px', textAlign: 'center' }}>
        <p>Invalid connection ID</p>
        <Link to="/brokers/positions" style={{ color: '#3b82f6' }}>
          Back to Positions
        </Link>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div style={{ padding: '24px', textAlign: 'center' }}>
        <div>Loading positions...</div>
      </div>
    )
  }

  return (
    <div style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto', height: 'calc(100vh - 100px)' }}>
      {/* Breadcrumb */}
      <div style={{ marginBottom: '16px' }}>
        <Link
          to="/brokers/positions"
          style={{
            color: '#6b7280',
            textDecoration: 'none',
            fontSize: '14px',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px'
          }}
        >
          <span style={{ fontSize: '18px' }}>&larr;</span>
          Back to All Positions
        </Link>
      </div>

      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
            <h1 style={{ fontSize: '24px', fontWeight: 600, color: '#111827', margin: 0 }}>
              {connection?.broker.name || positionsData?.broker || 'Broker'} Positions
            </h1>
            {connection && <ConnectionStatus status={connection.status} />}
          </div>
          <div style={{ fontSize: '14px', color: '#6b7280' }}>
            {connection?.accountType && <span>{connection.accountType} &middot; </span>}
            {connection?.accountNumber && <span>Account: {connection.accountNumber}</span>}
          </div>
        </div>

        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          {connection?.lastPositionsFetchedAt && (
            <span style={{ fontSize: '13px', color: '#6b7280' }}>
              Last updated: {getRelativeTime(connection.lastPositionsFetchedAt)}
            </span>
          )}
          <button
            onClick={handleRefresh}
            disabled={triggerFetch.isPending || connection?.status !== 'ACTIVE'}
            style={{
              padding: '8px 16px',
              borderRadius: '6px',
              border: '1px solid #d1d5db',
              backgroundColor: '#fff',
              color: '#374151',
              fontSize: '14px',
              fontWeight: 500,
              cursor: connection?.status === 'ACTIVE' ? 'pointer' : 'not-allowed',
              opacity: triggerFetch.isPending || connection?.status !== 'ACTIVE' ? 0.6 : 1
            }}
          >
            {triggerFetch.isPending ? 'Fetching...' : 'Refresh Positions'}
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      {summary && (
        <div style={{ display: 'flex', gap: '16px', marginBottom: '24px' }}>
          <SummaryCard title="Total Value" value={formatCurrency(summary.totalValue)} />
          <SummaryCard title="Total Cost" value={formatCurrency(summary.totalCost)} />
          <SummaryCard
            title="Total P&L"
            value={`${summary.totalPnl >= 0 ? '+' : ''}${formatCurrency(summary.totalPnl)}`}
            subtitle={formatPercent(summary.totalPnlPercent)}
            valueColor={summary.totalPnl >= 0 ? '#10b981' : '#ef4444'}
          />
          <SummaryCard title="Positions" value={positions.length.toString()} />
        </div>
      )}

      {/* Connection Error */}
      {connection?.status === 'ERROR' && connection.errorMessage && (
        <div
          style={{
            padding: '12px 16px',
            borderRadius: '8px',
            marginBottom: '16px',
            backgroundColor: '#fee2e2',
            color: '#991b1b',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}
        >
          <span>Connection Error: {connection.errorMessage}</span>
          <button
            onClick={() => navigate('/brokers/connections')}
            style={{
              padding: '6px 12px',
              borderRadius: '4px',
              border: 'none',
              backgroundColor: '#991b1b',
              color: '#fff',
              fontSize: '13px',
              cursor: 'pointer'
            }}
          >
            Reconnect
          </button>
        </div>
      )}

      {connection?.status === 'EXPIRED' && (
        <div
          style={{
            padding: '12px 16px',
            borderRadius: '8px',
            marginBottom: '16px',
            backgroundColor: '#fef3c7',
            color: '#92400e',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}
        >
          <span>Token expired. Please reconnect to refresh positions.</span>
          <button
            onClick={() => navigate('/brokers/connections')}
            style={{
              padding: '6px 12px',
              borderRadius: '4px',
              border: 'none',
              backgroundColor: '#92400e',
              color: '#fff',
              fontSize: '13px',
              cursor: 'pointer'
            }}
          >
            Reconnect
          </button>
        </div>
      )}

      {/* Positions Grid */}
      {positions.length === 0 ? (
        <div
          style={{
            border: '1px dashed #d1d5db',
            borderRadius: '8px',
            padding: '48px',
            textAlign: 'center',
            color: '#6b7280'
          }}
        >
          <p style={{ margin: 0, fontSize: '16px' }}>No positions found for this account.</p>
          <p style={{ margin: '8px 0 0', fontSize: '14px' }}>
            {connection?.status === 'ACTIVE'
              ? 'Click "Refresh Positions" to fetch the latest data.'
              : 'Please reconnect your broker to fetch positions.'}
          </p>
        </div>
      ) : (
        <div className="ag-theme-alpine" style={{ height: 'calc(100% - 220px)', width: '100%' }}>
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
