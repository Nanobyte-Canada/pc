import { apiFetch } from './api'
import type {
  BrokersResponse,
  BrokerConnectionsResponse,
  ConnectBrokerRequest,
  ConnectBrokerResponse,
  PositionFetchResponse,
  ConnectionPositionsResponse,
  AggregatedPositionsResponse,
  SnapTradeStatusResponse,
  ConnectionSyncResponse,
  ActivitiesResponse,
  BalanceHistoryResponse,
  ReportingPerformanceResponse
} from '../types/broker'

const BROKER_API_BASE = '/api/v1/brokers'

// ========== Broker Listing ==========

export async function getAvailableBrokers(): Promise<BrokersResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}`)
  if (!response.ok) {
    throw new Error('Failed to fetch available brokers')
  }
  return response.json()
}

// ========== Connection Management ==========

export async function getUserConnections(): Promise<BrokerConnectionsResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/connections`)
  if (!response.ok) {
    throw new Error('Failed to fetch broker connections')
  }
  return response.json()
}

export async function connectBroker(request?: ConnectBrokerRequest): Promise<ConnectBrokerResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/connect`, {
    method: 'POST',
    body: JSON.stringify(request || {})
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to initiate broker connection')
  }
  return response.json()
}

export async function disconnectBroker(authorizationId: string): Promise<void> {
  const response = await apiFetch(`${BROKER_API_BASE}/connections/${authorizationId}`, {
    method: 'DELETE'
  })
  if (!response.ok) {
    throw new Error('Failed to disconnect broker')
  }
}

// ========== Position Fetching ==========

export async function triggerPositionFetch(connectionId: number): Promise<PositionFetchResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/connections/${connectionId}/fetch`, {
    method: 'POST'
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to trigger position fetch')
  }
  return response.json()
}

export async function getConnectionPositions(connectionId: number): Promise<ConnectionPositionsResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/connections/${connectionId}/positions`)
  if (!response.ok) {
    throw new Error('Failed to fetch positions')
  }
  return response.json()
}

export async function getAggregatedPositions(): Promise<AggregatedPositionsResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/positions`)
  if (!response.ok) {
    throw new Error('Failed to fetch aggregated positions')
  }
  return response.json()
}

// ========== SnapTrade Status ==========

export async function getSnapTradeStatus(): Promise<SnapTradeStatusResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/snaptrade/status`)
  if (!response.ok) {
    throw new Error('Failed to fetch SnapTrade status')
  }
  return response.json()
}

// ========== Connection Sync ==========

export async function syncConnections(): Promise<ConnectionSyncResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/connections/sync`, {
    method: 'POST'
  })
  if (!response.ok) {
    throw new Error('Failed to sync connections')
  }
  return response.json()
}

// ========== Activities ==========

export async function getConnectionActivities(
  connectionId: number,
  params: { page?: number; size?: number; startDate?: string; endDate?: string; type?: string } = {}
): Promise<ActivitiesResponse> {
  const searchParams = new URLSearchParams()
  if (params.page !== undefined) searchParams.set('page', params.page.toString())
  if (params.size !== undefined) searchParams.set('size', params.size.toString())
  if (params.startDate) searchParams.set('startDate', params.startDate)
  if (params.endDate) searchParams.set('endDate', params.endDate)
  if (params.type) searchParams.set('type', params.type)

  const response = await apiFetch(`${BROKER_API_BASE}/connections/${connectionId}/activities?${searchParams}`)
  if (!response.ok) throw new Error('Failed to fetch activities')
  return response.json()
}

export async function syncConnectionActivities(connectionId: number): Promise<{ activitiesSynced: number; message: string }> {
  const response = await apiFetch(`${BROKER_API_BASE}/connections/${connectionId}/sync-activities`, { method: 'POST' })
  if (!response.ok) throw new Error('Failed to sync activities')
  return response.json()
}

// ========== Balances ==========

export async function getBalanceHistory(connectionId: number, days: number = 90): Promise<BalanceHistoryResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/connections/${connectionId}/balance-history?days=${days}`)
  if (!response.ok) throw new Error('Failed to fetch balance history')
  return response.json()
}

// ========== Reporting ==========

export async function getReportingPerformance(
  params: { startDate?: string; endDate?: string; accounts?: string; granularity?: string } = {}
): Promise<ReportingPerformanceResponse> {
  const searchParams = new URLSearchParams()
  if (params.startDate) searchParams.set('startDate', params.startDate)
  if (params.endDate) searchParams.set('endDate', params.endDate)
  if (params.accounts) searchParams.set('accounts', params.accounts)
  if (params.granularity) searchParams.set('granularity', params.granularity)

  const response = await apiFetch(`${BROKER_API_BASE}/reporting/performance?${searchParams}`)
  if (!response.ok) throw new Error('Failed to fetch performance report')
  return response.json()
}

export async function getReportingActivities(
  params: { page?: number; size?: number; startDate?: string; endDate?: string; accounts?: string; type?: string } = {}
): Promise<ActivitiesResponse> {
  const searchParams = new URLSearchParams()
  if (params.page !== undefined) searchParams.set('page', params.page.toString())
  if (params.size !== undefined) searchParams.set('size', params.size.toString())
  if (params.startDate) searchParams.set('startDate', params.startDate)
  if (params.endDate) searchParams.set('endDate', params.endDate)
  if (params.accounts) searchParams.set('accounts', params.accounts)
  if (params.type) searchParams.set('type', params.type)

  const response = await apiFetch(`${BROKER_API_BASE}/reporting/activities?${searchParams}`)
  if (!response.ok) throw new Error('Failed to fetch reporting activities')
  return response.json()
}

// ========== Utility Functions ==========

export function formatCurrency(value: number | null, currency: string = 'CAD'): string {
  if (value === null) return '-'
  return new Intl.NumberFormat('en-CA', {
    style: 'currency',
    currency
  }).format(value)
}

export function formatPercent(value: number | null): string {
  if (value === null) return '-'
  const sign = value >= 0 ? '+' : ''
  return `${sign}${value.toFixed(2)}%`
}

export function formatQuantity(value: number): string {
  return new Intl.NumberFormat('en-CA', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4
  }).format(value)
}

export function getRelativeTime(dateString: string | null): string {
  if (!dateString) return 'Never'

  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return 'Just now'
  if (diffMins < 60) return `${diffMins} min ago`
  if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`
  if (diffDays < 7) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`

  return date.toLocaleDateString()
}
