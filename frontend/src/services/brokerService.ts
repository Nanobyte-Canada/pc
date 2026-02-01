import { apiFetch } from './api'
import type {
  BrokersResponse,
  BrokerConnectionsResponse,
  OAuthInitiateResponse,
  PositionFetchResponse,
  ConnectionPositionsResponse,
  AggregatedPositionsResponse,
  BrokerPrefs,
  UpdateBrokerPrefsRequest,
  BrokerPrefsResponse
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

export async function initiateConnection(brokerCode: string): Promise<OAuthInitiateResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/${brokerCode}/connect`, {
    method: 'POST'
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to initiate broker connection')
  }
  return response.json()
}

export async function disconnectBroker(connectionId: number): Promise<void> {
  const response = await apiFetch(`${BROKER_API_BASE}/connections/${connectionId}`, {
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

// ========== User Preferences ==========

export async function getBrokerPreferences(): Promise<BrokerPrefs> {
  const response = await apiFetch(`${BROKER_API_BASE}/preferences`)
  if (!response.ok) {
    throw new Error('Failed to fetch broker preferences')
  }
  return response.json()
}

export async function updateBrokerPreferences(request: UpdateBrokerPrefsRequest): Promise<BrokerPrefsResponse> {
  const response = await apiFetch(`${BROKER_API_BASE}/preferences`, {
    method: 'PUT',
    body: JSON.stringify(request)
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to update broker preferences')
  }
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
