import { useState, useCallback } from 'react'
import { useDashboardHoldings } from '@/hooks/useDashboardWidgets'
import { AgGridReact } from 'ag-grid-react'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import { Search, Layers } from 'lucide-react'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'
import './HoldingsTableWidget.css'

export default function HoldingsTableWidget({ connectionId }: { connectionId?: number }) {
  const { data, isLoading } = useDashboardHoldings(connectionId)
  const [searchText, setSearchText] = useState('')

  const onFilterChanged = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchText(e.target.value)
  }, [])

  if (isLoading || !data) return <Skeleton style={{ height: '16rem', width: '100%' }} />

  if (data.holdings.length === 0) {
    return (
      <div className="ht-empty">
        <Layers style={{ height: '2.5rem', width: '2.5rem' }} />
        <span>No holdings data available</span>
      </div>
    )
  }

  const columnDefs: ColDef[] = [
    { field: 'symbol', headerName: 'Symbol', width: 100, pinned: 'left' },
    { field: 'name', headerName: 'Name', flex: 1, minWidth: 150 },
    {
      field: 'effectiveWeight', headerName: 'Weight', width: 90, type: 'numericColumn',
      valueFormatter: (p: ValueFormatterParams) => p.value != null ? `${(p.value * 100).toFixed(2)}%` : '-',
    },
    { field: 'sector', headerName: 'Sector', width: 140 },
    { field: 'industryGroup', headerName: 'Industry Group', width: 160 },
    { field: 'country', headerName: 'Country', width: 120 },
    {
      field: 'sources', headerName: 'Sources', width: 120,
      valueGetter: (p: any) => p.data?.sources?.map((s: any) => s.type === 'DIRECT' ? 'Direct' : s.instrumentSymbol).join(', '),
    },
  ]

  return (
    <div>
      <div className="ht-toolbar">
        <div className="ht-search">
          <Search className="ht-search-icon" />
          <input
            type="text"
            placeholder="Search holdings..."
            value={searchText}
            onChange={onFilterChanged}
            className="ht-search-input"
          />
        </div>
        <div className="ht-badges">
          <Badge variant="secondary">{data.totalCount} holdings</Badge>
          <Badge variant="secondary">{data.coveragePercent.toFixed(1)}% coverage</Badge>
        </div>
      </div>
      <div className="ag-theme-quartz ht-grid-container">
        <AgGridReact
          rowData={data.holdings}
          columnDefs={columnDefs}
          domLayout="autoHeight"
          pagination={true}
          paginationPageSize={15}
          quickFilterText={searchText}
        />
      </div>
    </div>
  )
}
