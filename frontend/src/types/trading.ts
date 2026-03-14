// ========== Response Types ==========

export interface TradeOrder {
  id: number
  groupId: number
  connectionId: number
  batchId: string | null
  symbol: string
  action: 'BUY' | 'SELL'
  orderType: string
  timeInForce: string
  requestedUnits: number
  requestedPrice: number
  requestedAmount: number
  limitPrice: number | null
  filledUnits: number | null
  filledPrice: number | null
  filledAmount: number | null
  currency: string
  status: OrderStatus
  brokerOrderId: string | null
  accountName: string | null
  errorMessage: string | null
  errorCode: string | null
  submittedAt: string | null
  filledAt: string | null
  cancelledAt: string | null
  createdAt: string
}

export type OrderStatus =
  | 'PENDING'
  | 'SUBMITTED'
  | 'FILLED'
  | 'PARTIALLY_FILLED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'FAILED'

export interface ExecuteTradesResponse {
  batchId: string
  orders: TradeOrder[]
  submittedCount: number
  failedCount: number
}

export interface OrderStatusResponse {
  orders: TradeOrder[]
  totalCount: number
}

// ========== Request Types ==========

export interface ExecuteTradesRequest {
  groupId: number
  trades: TradeExecutionInput[]
  orderType?: string
  timeInForce?: string
}

export interface TradeExecutionInput {
  symbol: string
  action: 'BUY' | 'SELL'
  units: number
  price: number
  amount: number
  currency: string
  connectionId: number
  limitPrice?: number
}
