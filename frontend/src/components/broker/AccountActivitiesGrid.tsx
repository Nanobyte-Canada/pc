import { useState } from 'react'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import { useConnectionActivities, useSyncActivities } from '../../hooks/useBrokerConnections'
import { formatCurrency } from '../../services/brokerService'
import type { BrokerActivityDto } from '../../types/broker'

import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'
import './AccountActivitiesGrid.css'

const ACTIVITY_TYPES = ['BUY', 'SELL', 'DIVIDEND', 'TRANSFER_IN', 'TRANSFER_OUT', 'FEE', 'INTEREST', 'OTHER']

const typeColors: Record<string, string> = {
  BUY: '#059669',
  SELL: '#dc2626',
  DIVIDEND: '#7c3aed',
  TRANSFER_IN: '#0284c7',
  TRANSFER_OUT: '#d97706',
  FEE: '#6b7280',
  INTEREST: '#16a34a',
  OTHER: '#6b7280'
}

interface AccountActivitiesGridProps {
  connectionId: number
  connectionActive: boolean
}

export function AccountActivitiesGrid({ connectionId, connectionActive }: AccountActivitiesGridProps) {
  const [page, setPage] = useState(0)
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const pageSize = 50

  const { data, isLoading } = useConnectionActivities(connectionId, {
    page,
    size: pageSize,
    startDate: startDate || undefined,
    endDate: endDate || undefined,
    type: typeFilter || undefined
  })

  const syncActivities = useSyncActivities()

  const activities = data?.activities || []
  const totalCount = data?.totalCount || 0
  const totalPages = Math.ceil(totalCount / pageSize)

  const columnDefs: ColDef<BrokerActivityDto>[] = [
    {
      headerName: 'Date',
      field: 'tradeDate',
      width: 110,
      valueFormatter: (params: ValueFormatterParams) => {
        if (!params.value) return '-'
        return new Date(params.value).toLocaleDateString()
      }
    },
    {
      headerName: 'Type',
      field: 'type',
      width: 120,
      cellRenderer: (params: { value: string }) => {
        const color = typeColors[params.value] || typeColors.OTHER
        return `<span style="
          display: inline-block;
          padding: 2px 8px;
          border-radius: 4px;
          font-size: 12px;
          font-weight: 500;
          color: ${color};
          background: ${color}15;
        ">${params.value}</span>`
      }
    },
    {
      headerName: 'Symbol',
      field: 'symbol',
      width: 100,
      cellStyle: { fontWeight: 600 },
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    {
      headerName: 'Description',
      field: 'description',
      flex: 1,
      minWidth: 200,
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    {
      headerName: 'Quantity',
      field: 'quantity',
      width: 100,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) => params.value != null ? params.value.toFixed(4) : '-'
    },
    {
      headerName: 'Price',
      field: 'price',
      width: 100,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) => params.value != null ? formatCurrency(params.value) : '-'
    },
    {
      headerName: 'Amount',
      field: 'amount',
      width: 130,
      type: 'rightAligned',
      cellStyle: (params) => ({
        fontWeight: 600,
        color: params.value >= 0 ? '#059669' : '#dc2626'
      }),
      valueFormatter: (params: ValueFormatterParams) => {
        const sign = params.value >= 0 ? '+' : ''
        return sign + formatCurrency(params.value)
      }
    },
    {
      headerName: 'Currency',
      field: 'currency',
      width: 80
    }
  ]

  return (
    <div className="activities-grid-wrapper">
      <div className="activities-filters">
        <div className="filter-group">
          <label>From</label>
          <input type="date" value={startDate} onChange={e => { setStartDate(e.target.value); setPage(0) }} />
        </div>
        <div className="filter-group">
          <label>To</label>
          <input type="date" value={endDate} onChange={e => { setEndDate(e.target.value); setPage(0) }} />
        </div>
        <div className="filter-group">
          <label>Type</label>
          <select value={typeFilter} onChange={e => { setTypeFilter(e.target.value); setPage(0) }}>
            <option value="">All Types</option>
            {ACTIVITY_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <button
          className="sync-activities-btn"
          onClick={() => syncActivities.mutate(connectionId)}
          disabled={syncActivities.isPending || !connectionActive}
        >
          {syncActivities.isPending ? 'Syncing...' : 'Sync Activities'}
        </button>
      </div>

      {isLoading ? (
        <div className="activities-loading">Loading activities...</div>
      ) : activities.length === 0 ? (
        <div className="activities-empty">
          <p>No activities found.</p>
          <p>Click "Sync Activities" to fetch transaction history from your broker.</p>
        </div>
      ) : (
        <>
          <div className="ag-theme-quartz activities-grid-container">
            <AgGridReact
              rowData={activities}
              columnDefs={columnDefs}
              defaultColDef={{ sortable: true, resizable: true }}
              animateRows={true}
              suppressCellFocus={true}
              domLayout="autoHeight"
            />
          </div>
          <div className="activities-pagination">
            <span className="pagination-info">
              Showing {page * pageSize + 1}-{Math.min((page + 1) * pageSize, totalCount)} of {totalCount}
            </span>
            <div className="pagination-controls">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
              <span>Page {page + 1} of {totalPages}</span>
              <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Next</button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
