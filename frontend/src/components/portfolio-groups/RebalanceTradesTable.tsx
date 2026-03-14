import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ValueFormatterParams } from 'ag-grid-community'
import type { RebalanceTrade } from '../../types/portfolioGroup'
import { formatCurrency } from '../../services/brokerService'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'

interface RebalanceTradesTableProps {
  trades: RebalanceTrade[]
  onExecute?: (trade: RebalanceTrade) => void
}

export function RebalanceTradesTable({ trades, onExecute }: RebalanceTradesTableProps) {
  const columnDefs: ColDef<RebalanceTrade>[] = [
    {
      headerName: 'Action',
      field: 'action',
      width: 90,
      cellRenderer: (params: { value: string }) => {
        const isBuy = params.value === 'BUY'
        return `<span class="trade-action-badge ${isBuy ? 'buy' : 'sell'}">${params.value}</span>`
      }
    },
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
      minWidth: 140,
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    {
      headerName: 'Price',
      field: 'price',
      width: 110,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) =>
        formatCurrency(Number(params.value), params.data?.currency || 'CAD')
    },
    {
      headerName: 'Units',
      field: 'units',
      width: 90,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) => Number(params.value).toFixed(2)
    },
    {
      headerName: 'Amount',
      field: 'amount',
      width: 120,
      type: 'rightAligned',
      cellStyle: (params) => ({
        fontWeight: 600,
        color: params.data?.action === 'BUY' ? '#059669' : '#dc2626'
      }),
      valueFormatter: (params: ValueFormatterParams) =>
        formatCurrency(Number(params.value), params.data?.currency || 'CAD')
    },
    {
      headerName: 'Account',
      field: 'accountName',
      width: 140,
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    ...(onExecute ? [{
      headerName: '',
      width: 90,
      cellRenderer: () =>
        `<button style="background:var(--accent);color:white;border:none;padding:0.15rem 0.5rem;border-radius:4px;font-size:0.75rem;cursor:pointer">Execute</button>`,
      onCellClicked: (params: { data: RebalanceTrade | undefined }) => {
        if (params.data && onExecute) onExecute(params.data)
      }
    } as ColDef<RebalanceTrade>] : [])
  ]

  if (trades.length === 0) {
    return (
      <div className="empty-trades">
        <p>No trades needed. Portfolio is balanced or no cash available.</p>
      </div>
    )
  }

  return (
    <div className="ag-theme-quartz" style={{ width: '100%' }}>
      <AgGridReact
        rowData={trades}
        columnDefs={columnDefs}
        defaultColDef={{ sortable: true, resizable: true }}
        animateRows={true}
        suppressCellFocus={true}
        domLayout="autoHeight"
      />
    </div>
  )
}
