import { useState } from 'react'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import { useReportingActivities } from '../../hooks/useReporting'
import { formatCurrency } from '../../services/brokerService'
import type { BrokerActivityDto } from '../../types/broker'

import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'

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

interface ActivityTableProps {
  startDate: string
  endDate: string
  accounts: string
}

export function ActivityTable({ startDate, endDate, accounts }: ActivityTableProps) {
  const [page, setPage] = useState(0)
  const [typeFilter, setTypeFilter] = useState('')
  const pageSize = 50

  const { data, isLoading, isError, error } = useReportingActivities({
    page,
    size: pageSize,
    startDate: startDate || undefined,
    endDate: endDate || undefined,
    accounts: accounts || undefined,
    type: typeFilter || undefined
  })

  const activities = data?.activities || []
  const totalCount = data?.totalCount || 0
  const totalPages = Math.ceil(totalCount / pageSize)

  const handleExportCsv = () => {
    if (activities.length === 0) return
    const headers = ['Date', 'Type', 'Symbol', 'Description', 'Quantity', 'Price', 'Amount', 'Fee', 'Currency', 'Account']
    const rows = activities.map(a => [
      a.tradeDate, a.type, a.symbol || '', a.description || '',
      a.quantity?.toString() || '', a.price?.toString() || '',
      (a.amount ?? '').toString(), a.fee?.toString() || '', a.currency, a.accountName || ''
    ])
    const csv = [headers, ...rows].map(r => r.map(c => `"${c}"`).join(',')).join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `activities-${new Date().toISOString().split('T')[0]}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  const columnDefs: ColDef<BrokerActivityDto>[] = [
    {
      headerName: 'Date',
      field: 'tradeDate',
      width: 110,
      valueFormatter: (params: ValueFormatterParams) =>
        params.value ? new Date(params.value).toLocaleDateString() : '-'
    },
    {
      headerName: 'Account',
      field: 'accountName',
      width: 140,
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    {
      headerName: 'Type',
      field: 'type',
      width: 120,
      cellRenderer: (params: { value: string }) => {
        const color = typeColors[params.value] || typeColors.OTHER
        return (
          <span style={{
            display: 'inline-block',
            padding: '2px 8px',
            borderRadius: '4px',
            fontSize: '12px',
            fontWeight: '500',
            color: color,
            background: `${color}15`
          }}>
            {params.value}
          </span>
        )
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
      minWidth: 180,
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    {
      headerName: 'Amount',
      field: 'amount',
      width: 130,
      type: 'rightAligned',
      cellStyle: (params) => ({
        fontWeight: 600,
        color: (params.value ?? 0) >= 0 ? '#059669' : '#dc2626'
      }),
      valueFormatter: (params: ValueFormatterParams) => {
        if (params.value == null) return '-'
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
    <div className="activity-table-wrapper">
      <div className="activity-table-controls">
        <div className="filter-group">
          <label>Type</label>
          <select value={typeFilter} onChange={e => { setTypeFilter(e.target.value); setPage(0) }}>
            <option value="">All Types</option>
            {ACTIVITY_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <button className="export-btn" onClick={handleExportCsv} disabled={activities.length === 0}>
          Export CSV
        </button>
      </div>

      {isError ? (
        <div className="activity-table-empty">
          Failed to load activities. {error instanceof Error ? error.message : 'An unexpected error occurred.'}
        </div>
      ) : isLoading ? (
        <div className="activity-table-loading">Loading activities...</div>
      ) : activities.length === 0 ? (
        <div className="activity-table-empty">No activities found for the selected filters.</div>
      ) : (
        <>
          <div className="ag-theme-quartz activity-grid-container">
            <AgGridReact
              rowData={activities}
              columnDefs={columnDefs}
              defaultColDef={{ sortable: true, resizable: true }}
              animateRows={true}
              suppressCellFocus={true}
              domLayout="autoHeight"
            />
          </div>
          <div className="activity-table-pagination">
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
