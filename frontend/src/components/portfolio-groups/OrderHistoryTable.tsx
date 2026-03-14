import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ValueFormatterParams, ICellRendererParams } from 'ag-grid-community'
import type { TradeOrder } from '../../types/trading'
import { formatCurrency } from '../../services/brokerService'
import { useCancelOrder } from '../../hooks/useTrading'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'

interface OrderHistoryTableProps {
  orders: TradeOrder[]
}

const statusColors: Record<string, { bg: string; color: string }> = {
  PENDING: { bg: 'rgba(234, 179, 8, 0.12)', color: '#b45309' },
  SUBMITTED: { bg: 'rgba(59, 130, 246, 0.12)', color: '#2563eb' },
  FILLED: { bg: 'rgba(5, 150, 105, 0.12)', color: '#059669' },
  PARTIALLY_FILLED: { bg: 'rgba(139, 92, 246, 0.12)', color: '#7c3aed' },
  REJECTED: { bg: 'rgba(220, 38, 38, 0.12)', color: '#dc2626' },
  CANCELLED: { bg: 'rgba(107, 114, 128, 0.12)', color: '#6b7280' },
  FAILED: { bg: 'rgba(220, 38, 38, 0.12)', color: '#dc2626' }
}

export function OrderHistoryTable({ orders }: OrderHistoryTableProps) {
  const cancelOrder = useCancelOrder()

  const columnDefs: ColDef<TradeOrder>[] = [
    {
      headerName: 'Status',
      field: 'status',
      width: 130,
      cellRenderer: (params: ICellRendererParams<TradeOrder>) => {
        const status = params.value as string
        const colors = statusColors[status] || statusColors.PENDING
        return `<span style="display:inline-block;padding:0.15rem 0.5rem;border-radius:4px;font-size:0.75rem;font-weight:700;background:${colors.bg};color:${colors.color}">${status}</span>`
      }
    },
    {
      headerName: 'Action',
      field: 'action',
      width: 80,
      cellRenderer: (params: ICellRendererParams<TradeOrder>) => {
        const isBuy = params.value === 'BUY'
        return `<span class="trade-action-badge ${isBuy ? 'buy' : 'sell'}">${params.value}</span>`
      }
    },
    {
      headerName: 'Symbol',
      field: 'symbol',
      width: 90,
      cellStyle: { fontWeight: 600 }
    },
    {
      headerName: 'Req. Units',
      field: 'requestedUnits',
      width: 100,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) => Number(params.value).toFixed(2)
    },
    {
      headerName: 'Req. Amount',
      field: 'requestedAmount',
      width: 120,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) =>
        formatCurrency(Number(params.value), params.data?.currency || 'CAD')
    },
    {
      headerName: 'Filled',
      field: 'filledAmount',
      width: 110,
      type: 'rightAligned',
      valueFormatter: (params: ValueFormatterParams) =>
        params.value ? formatCurrency(Number(params.value), params.data?.currency || 'CAD') : '-'
    },
    {
      headerName: 'Account',
      field: 'accountName',
      width: 130,
      valueFormatter: (params: ValueFormatterParams) => params.value || '-'
    },
    {
      headerName: 'Date',
      field: 'createdAt',
      width: 140,
      valueFormatter: (params: ValueFormatterParams) =>
        params.value ? new Date(params.value).toLocaleDateString('en-CA', {
          month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
        }) : '-'
    },
    {
      headerName: '',
      width: 90,
      cellRenderer: (params: ICellRendererParams<TradeOrder>) => {
        const order = params.data
        if (!order) return ''
        if (order.status === 'PENDING' || order.status === 'SUBMITTED') {
          return `<button class="cancel-order-btn" style="background:none;border:1px solid #dc2626;color:#dc2626;padding:0.15rem 0.4rem;border-radius:4px;font-size:0.75rem;cursor:pointer">Cancel</button>`
        }
        return ''
      },
      onCellClicked: (params) => {
        const order = params.data
        if (order && (order.status === 'PENDING' || order.status === 'SUBMITTED')) {
          if (window.confirm(`Cancel order for ${order.requestedUnits} ${order.symbol}?`)) {
            cancelOrder.mutate(order.id)
          }
        }
      }
    }
  ]

  if (orders.length === 0) {
    return (
      <div className="empty-trades">
        <p>No order history yet.</p>
      </div>
    )
  }

  return (
    <div className="ag-theme-quartz" style={{ width: '100%' }}>
      <AgGridReact
        rowData={orders}
        columnDefs={columnDefs}
        defaultColDef={{ sortable: true, resizable: true }}
        animateRows={true}
        suppressCellFocus={true}
        domLayout="autoHeight"
      />
    </div>
  )
}
