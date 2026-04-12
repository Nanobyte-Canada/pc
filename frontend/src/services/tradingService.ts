import { apiFetch } from './api'
import type {
  ExecuteTradesRequest,
  ExecuteTradesResponse,
  TradeExecutionInput,
  TradeOrder,
  OrderStatusResponse
} from '../types/trading'

const API_BASE = '/api/v1/trading'

export async function executeTrades(request: ExecuteTradesRequest): Promise<ExecuteTradesResponse> {
  const response = await apiFetch(`${API_BASE}/execute`, {
    method: 'POST',
    body: JSON.stringify(request)
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to execute trades')
  }
  return response.json()
}

export async function executeSingleTrade(groupId: number, trade: TradeExecutionInput): Promise<TradeOrder> {
  const response = await apiFetch(`${API_BASE}/groups/${groupId}/execute-single`, {
    method: 'POST',
    body: JSON.stringify(trade)
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to execute trade')
  }
  return response.json()
}

export async function getGroupOrders(groupId: number): Promise<OrderStatusResponse> {
  const response = await apiFetch(`${API_BASE}/groups/${groupId}/orders`)
  if (!response.ok) throw new Error('Failed to fetch orders')
  return response.json()
}

export async function getBatchOrders(batchId: string): Promise<OrderStatusResponse> {
  const response = await apiFetch(`${API_BASE}/batches/${batchId}`)
  if (!response.ok) throw new Error('Failed to fetch batch orders')
  return response.json()
}

export async function cancelOrder(orderId: number): Promise<TradeOrder> {
  const response = await apiFetch(`${API_BASE}/orders/${orderId}/cancel`, {
    method: 'POST'
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to cancel order')
  }
  return response.json()
}
