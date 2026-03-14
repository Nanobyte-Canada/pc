import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import type { DriftHolding } from '../../types/portfolioGroup'
import { formatCurrency } from '../../services/brokerService'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'

interface DriftHoldingsTableProps {
  holdings: DriftHolding[]
}

export function DriftHoldingsTable({ holdings }: DriftHoldingsTableProps) {
  const columnDefs: ColDef<DriftHolding>[] = [
    {
      headerName: 'Symbol',
      field: 'symbol',
      width: 100,
      cellStyle: { fontWeight: 600 }
    },
    {
      headerName: 'Security',
      field: 'securityName',
      flex: 1,
      minWidth: 150,
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    {
      headerName: 'Target %',
      field: 'targetPercent',
      width: 100,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) => `${Number(params.value).toFixed(2)}%`
    },
    {
      headerName: 'Actual %',
      field: 'actualPercent',
      width: 100,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) => `${Number(params.value).toFixed(2)}%`
    },
    {
      headerName: 'Drift',
      field: 'driftPercent',
      width: 100,
      type: 'rightAligned',
      cellStyle: (params) => ({
        fontWeight: 600,
        color: Number(params.value) > 0 ? '#059669' : Number(params.value) < 0 ? '#dc2626' : 'inherit'
      }),
      valueFormatter: (params: ValueFormatterParams) => {
        const val = Number(params.value)
        const sign = val > 0 ? '+' : ''
        return `${sign}${val.toFixed(2)}%`
      }
    },
    {
      headerName: 'Actual Value',
      field: 'actualValue',
      width: 130,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) =>
        formatCurrency(Number(params.value), params.data?.currency || 'CAD')
    },
    {
      headerName: 'Target Value',
      field: 'targetValue',
      width: 130,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) =>
        formatCurrency(Number(params.value), params.data?.currency || 'CAD')
    }
  ]

  return (
    <div className="ag-theme-quartz" style={{ width: '100%' }}>
      <AgGridReact
        rowData={holdings}
        columnDefs={columnDefs}
        defaultColDef={{ sortable: true, resizable: true }}
        animateRows={true}
        suppressCellFocus={true}
        domLayout="autoHeight"
      />
    </div>
  )
}
