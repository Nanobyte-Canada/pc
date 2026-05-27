import { useState, useCallback, useMemo, useRef } from 'react'
import { AgGridReact } from 'ag-grid-react'
import { Search, TableProperties } from 'lucide-react'
import { useDashboardPositions, useOpenOrders } from '@/hooks/useDashboardWidgets'
import { useAgGridTheme } from '@/hooks/useAgGridTheme'
import { Skeleton } from '@/components/ui/skeleton'
import type { ColDef, ValueFormatterParams, ICellRendererParams } from 'ag-grid-community'
import type { AggregatedPosition } from '@/types/broker'
import { useAutoPageSize } from '@/hooks/useAutoPageSize'
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
  autoFit?: boolean
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

export function PositionsTable({ connectionId, autoFit }: PositionsTableProps) {
  const agTheme = useAgGridTheme()
  const gridContainerRef = useRef<HTMLDivElement>(null)
  const autoPageSize = useAutoPageSize(gridContainerRef, 44, 50)
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
      flex: 2,
      minWidth: 160,
      cellRenderer: SymbolCellRenderer,
    },
    {
      field: 'totalValue',
      headerName: 'Market Value',
      flex: 1.2,
      minWidth: 120,
      type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtCurrency(p.value),
      cellClass: 'positions-table__mono',
    },
    {
      field: 'totalQuantity',
      headerName: 'Qty',
      flex: 0.8,
      minWidth: 70,
      type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtNumber(p.value, 0),
      cellClass: 'positions-table__mono',
    },
    {
      field: 'averageCost',
      headerName: 'Avg Cost',
      flex: 1,
      minWidth: 100,
      type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtCurrency(p.value),
      cellClass: 'positions-table__mono',
    },
    {
      headerName: 'Price',
      flex: 1,
      minWidth: 100,
      type: 'numericColumn',
      valueGetter: (params) => {
        const data = params.data
        if (!data || !data.totalQuantity) return null
        return data.totalValue / data.totalQuantity
      },
      valueFormatter: (p: ValueFormatterParams) => fmtCurrency(p.value),
      cellClass: 'positions-table__mono',
    },
    {
      field: 'totalPnl',
      headerName: 'P&L',
      flex: 1,
      minWidth: 110,
      type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => fmtCurrency(p.value),
      cellClass: 'positions-table__mono',
      cellStyle: (p) => ({
        color: (p.value ?? 0) >= 0 ? '#6ee7b7' : '#f87171',
      }),
    },
    {
      headerName: 'Weight',
      flex: 0.8,
      minWidth: 70,
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

  const holdingsData = positionsData?.positions ?? []
  const orders = ordersData?.orders ?? []

  const filteredHoldings = useMemo(() => {
    if (!searchText) return holdingsData
    const q = searchText.toLowerCase()
    return holdingsData.filter(p =>
      p.symbol?.toLowerCase().includes(q) ||
      p.securityName?.toLowerCase().includes(q)
    )
  }, [holdingsData, searchText])

  const isLoading = activeTab === 'holdings' ? positionsLoading : ordersLoading

  if (isLoading) {
    return (
      <div className="positions-table">
        <Skeleton style={{ height: 300, width: '100%', borderRadius: 10 }} />
      </div>
    )
  }

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
      ) : activeTab === 'holdings' ? (
        <>
          {/* Desktop: AG Grid */}
          <div ref={autoFit ? gridContainerRef : undefined} className={`${agTheme} positions-table__grid positions-table__desktop-only`}>
            <AgGridReact
              rowData={filteredHoldings}
              columnDefs={holdingsColumns}
              domLayout={autoFit ? undefined : 'autoHeight'}
              quickFilterText={searchText}
              pagination={true}
              paginationPageSize={autoFit ? autoPageSize : 15}
              suppressCellFocus={true}
              rowHeight={44}
            />
          </div>
          {/* Mobile: Card list */}
          <div className="positions-table__mobile-only">
            {filteredHoldings.map((pos) => {
              const pnl = pos.totalPnl ?? 0
              const pnlPct = pos.totalValue && pos.averageCost && pos.totalQuantity
                ? ((pos.totalValue - pos.averageCost * pos.totalQuantity) / (pos.averageCost * pos.totalQuantity) * 100)
                : 0
              const weight = positionsData?.aggregateSummary?.totalValue
                ? (pos.totalValue / positionsData.aggregateSummary.totalValue * 100)
                : 0
              return (
                <div key={pos.symbol} className="positions-table__card">
                  <div className="positions-table__card-top">
                    <div className="positions-table__card-left">
                      <span className="positions-table__card-symbol">{pos.symbol}</span>
                      <span className="positions-table__card-weight">{weight.toFixed(1)}%</span>
                    </div>
                    <div className="positions-table__card-right">
                      <span className="positions-table__card-value">{fmtCurrency(pos.totalValue)}</span>
                      <span className={`positions-table__card-pnl ${pnl >= 0 ? 'positions-table__card-pnl--positive' : 'positions-table__card-pnl--negative'}`}>
                        {pnl >= 0 ? '+' : ''}{fmtCurrency(pnl)} ({pnlPct >= 0 ? '+' : ''}{pnlPct.toFixed(1)}%)
                      </span>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </>
      ) : (
        <div ref={autoFit ? gridContainerRef : undefined} className={`${agTheme} positions-table__grid`}>
          <AgGridReact
            rowData={orders}
            columnDefs={ordersColumns}
            domLayout={autoFit ? undefined : 'autoHeight'}
            quickFilterText={searchText}
            pagination={true}
            paginationPageSize={autoFit ? autoPageSize : 15}
            suppressCellFocus={true}
            rowHeight={36}
          />
        </div>
      )}
    </div>
  )
}
