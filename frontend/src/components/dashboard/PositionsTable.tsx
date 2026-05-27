import { useState, useCallback, useMemo } from 'react'
import { AgGridReact } from 'ag-grid-react'
import { Search, TableProperties } from 'lucide-react'
import { useDashboardPositions, useOpenOrders } from '@/hooks/useDashboardWidgets'
import { useAgGridTheme } from '@/hooks/useAgGridTheme'
import { Skeleton } from '@/components/ui/skeleton'
import type { ColDef, ValueFormatterParams, ICellRendererParams } from 'ag-grid-community'
import type { AggregatedPosition } from '@/types/broker'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'
import './PositionsTable.css'

function fmtCurrency(value: number | null | undefined): string {
  if (value == null) return '-'
  return `C$ ${value.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function fmtNumber(value: number | null | undefined, decimals = 2): string {
  if (value == null) return '-'
  return value.toLocaleString('en-CA', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
}

function fmtPercent(value: number | null | undefined): string {
  if (value == null) return '-'
  return `${value.toFixed(2)}%`
}

interface PositionsTableProps {
  connectionId?: number
}

type TabType = 'holdings' | 'orders'

function SymbolCellRenderer(params: ICellRendererParams<AggregatedPosition>) {
  return (
    <div className="positions-table__symbol-cell">
      <span className="positions-table__symbol">{params.data?.symbol}</span>
      <span className="positions-table__name">{params.data?.securityName ?? ''}</span>
    </div>
  )
}

export function PositionsTable({ connectionId }: PositionsTableProps) {
  const agTheme = useAgGridTheme()
  const { data: positionsData, isLoading: positionsLoading } = useDashboardPositions(connectionId)
  const { data: ordersData, isLoading: ordersLoading } = useOpenOrders()
  const [activeTab, setActiveTab] = useState<TabType>('holdings')
  const [searchText, setSearchText] = useState('')

  const onSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchText(e.target.value)
  }, [])

  /* Holdings columns */
  const holdingsColumns: ColDef<AggregatedPosition>[] = useMemo(() => [
    {
      field: 'symbol',
      headerName: 'Symbol',
      width: 160,
      pinned: 'left' as const,
      cellRenderer: SymbolCellRenderer,
      autoHeight: true,
    },
    {
      field: 'totalValue',
      headerName: 'Market Value',
      width: 130,
      type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtCurrency(p.value),
      cellClass: 'positions-table__mono',
    },
    {
      field: 'totalQuantity',
      headerName: 'Qty',
      width: 80,
      type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtNumber(p.value, 0),
      cellClass: 'positions-table__mono',
    },
    {
      field: 'averageCost',
      headerName: 'Avg Cost',
      width: 100,
      type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtNumber(p.value),
      cellClass: 'positions-table__mono',
    },
    {
      headerName: 'Price',
      width: 100,
      type: 'numericColumn',
      valueGetter: (params) => {
        const data = params.data
        if (!data || !data.totalQuantity) return null
        return data.totalValue / data.totalQuantity
      },
      valueFormatter: (p: ValueFormatterParams) => fmtNumber(p.value),
      cellClass: 'positions-table__mono',
    },
    {
      field: 'totalPnl',
      headerName: 'P&L',
      width: 120,
      type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtCurrency(p.value),
      cellClass: 'positions-table__mono',
      cellStyle: (p) => ({
        color: (p.value ?? 0) >= 0 ? '#6ee7b7' : '#f87171',
      }),
    },
    {
      headerName: 'Weight',
      width: 80,
      type: 'numericColumn',
      valueGetter: (params) => {
        const data = params.data
        if (!data || !positionsData) return null
        const total = positionsData.aggregateSummary.totalValue
        if (!total) return null
        return (data.totalValue / total) * 100
      },
      valueFormatter: (p: ValueFormatterParams) => fmtPercent(p.value),
      cellClass: 'positions-table__mono',
    },
  ], [positionsData])

  /* Orders columns */
  const ordersColumns: ColDef[] = useMemo(() => [
    { field: 'symbol', headerName: 'Symbol', width: 100 },
    { field: 'action', headerName: 'Action', width: 80 },
    { field: 'orderType', headerName: 'Type', width: 80 },
    {
      field: 'requestedUnits',
      headerName: 'Qty',
      width: 80,
      type: 'numericColumn',
      cellClass: 'positions-table__mono',
    },
    {
      field: 'limitPrice',
      headerName: 'Limit',
      width: 100,
      type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtCurrency(p.value),
      cellClass: 'positions-table__mono',
    },
    { field: 'status', headerName: 'Status', width: 100 },
    { field: 'accountName', headerName: 'Account', flex: 1, minWidth: 100 },
  ], [])

  const isLoading = activeTab === 'holdings' ? positionsLoading : ordersLoading

  if (isLoading) {
    return (
      <div className="positions-table">
        <Skeleton style={{ height: 300, width: '100%', borderRadius: 10 }} />
      </div>
    )
  }

  const holdingsData = positionsData?.positions ?? []
  const orders = ordersData?.orders ?? []

  const isEmpty = activeTab === 'holdings' ? holdingsData.length === 0 : orders.length === 0

  return (
    <div className="positions-table">
      <div className="positions-table__header">
        <div className="positions-table__header-left">
          <h3 className="positions-table__title">Positions</h3>
          <div className="positions-table__tabs">
            <button
              className={`positions-table__tab ${activeTab === 'holdings' ? 'positions-table__tab--active' : 'positions-table__tab--inactive'}`}
              onClick={() => setActiveTab('holdings')}
            >
              Holdings
            </button>
            <button
              className={`positions-table__tab ${activeTab === 'orders' ? 'positions-table__tab--active' : 'positions-table__tab--inactive'}`}
              onClick={() => setActiveTab('orders')}
            >
              Orders
            </button>
          </div>
        </div>
        <div className="positions-table__search">
          <Search className="positions-table__search-icon" />
          <input
            type="text"
            placeholder="Search..."
            value={searchText}
            onChange={onSearchChange}
            className="positions-table__search-input"
          />
        </div>
      </div>

      {isEmpty ? (
        <div className="positions-table__empty">
          <TableProperties className="positions-table__empty-icon" />
          <span>{activeTab === 'holdings' ? 'No positions' : 'No open orders'}</span>
        </div>
      ) : (
        <div className={`${agTheme} positions-table__grid`}>
          <AgGridReact
            rowData={activeTab === 'holdings' ? holdingsData : orders}
            columnDefs={activeTab === 'holdings' ? holdingsColumns : ordersColumns}
            domLayout="autoHeight"
            quickFilterText={searchText}
            pagination={true}
            paginationPageSize={15}
            suppressCellFocus={true}
            rowHeight={activeTab === 'holdings' ? 44 : 36}
          />
        </div>
      )}
    </div>
  )
}
