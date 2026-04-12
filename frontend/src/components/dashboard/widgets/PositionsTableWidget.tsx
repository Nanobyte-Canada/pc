import { useState, useCallback } from 'react'
import { useDashboardPositions } from '@/hooks/useDashboardWidgets'
import { AgGridReact } from 'ag-grid-react'
import { Skeleton } from '@/components/ui/skeleton'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import { Search, TableProperties } from 'lucide-react'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'
import './PositionsTableWidget.css'

function fmtCurrency(value: number | null) {
  if (value == null) return '-'
  return new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' }).format(value)
}

function fmtPercent(value: number | null) {
  if (value == null) return '-'
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
}

export default function PositionsTableWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardPositions(connectionId)
  const [searchText, setSearchText] = useState('')

  const onFilterChanged = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchText(e.target.value)
  }, [])

  if (isLoading || !data) return <Skeleton style={{ height: '16rem', width: '100%' }} />

  if (data.positions.length === 0) {
    return (
      <div className="pt-empty">
        <TableProperties style={{ height: '2.5rem', width: '2.5rem' }} />
        <span>No positions</span>
      </div>
    )
  }

  const columnDefs: ColDef[] = [
    { field: 'symbol', headerName: 'Symbol', width: 100, pinned: 'left' },
    { field: 'securityName', headerName: 'Name', flex: 1, minWidth: 150 },
    { field: 'instrumentType', headerName: 'Type', width: 80 },
    {
      field: 'totalQuantity', headerName: 'Qty', width: 90, type: 'numericColumn',
      tooltipValueGetter: (params: any) => {
        const breakdown = params.data?.brokerBreakdown
        if (!breakdown || breakdown.length === 0) return ''
        return breakdown.map((b: any) => `${b.accountType || b.broker}: ${b.quantity}`).join('\n')
      },
    },
    {
      field: 'totalValue', headerName: 'Value', width: 120, type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtCurrency(p.value),
    },
    {
      field: 'totalPnl', headerName: 'P&L', width: 110, type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtCurrency(p.value),
      cellStyle: (p: any) => ({ color: (p.value ?? 0) >= 0 ? 'var(--success)' : 'var(--error)' }),
    },
    {
      field: 'totalPnlPercent', headerName: 'P&L %', width: 90, type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtPercent(p.value),
      cellStyle: (p: any) => ({ color: (p.value ?? 0) >= 0 ? 'var(--success)' : 'var(--error)' }),
    },
  ]

  return (
    <div>
      <div className="pt-search">
        <Search className="pt-search-icon" />
        <input
          type="text"
          placeholder="Search positions..."
          value={searchText}
          onChange={onFilterChanged}
          className="pt-search-input"
        />
      </div>
      <div className="ag-theme-quartz pt-grid-container">
        <AgGridReact
          rowData={data.positions}
          columnDefs={columnDefs}
          domLayout="autoHeight"
          tooltipShowDelay={200}
          enableBrowserTooltips={false}
          quickFilterText={searchText}
          pagination={true}
          paginationPageSize={15}
        />
      </div>
    </div>
  )
}
