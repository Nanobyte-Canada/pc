import { apiFetch } from './api'
import type {
  DashboardSummaryResponse,
  DashboardCashResponse,
  SectorExposureResponse,
  GeographyExposureResponse,
  RiskProfileResponse,
  OpenOrdersResponse,
  FeesResponse,
  DividendCalendarResponse,
  HoldingsTableResponse,
  DashboardAccountsResponse,
  DashboardPreferencesResponse,
  UpdateDashboardPreferencesRequest,
} from '../types/dashboard'
import type { AggregatedPositionsResponse } from '../types/broker'

export async function getDashboardPreferences(
  contextType?: string,
  contextId?: number
): Promise<DashboardPreferencesResponse> {
  const params = new URLSearchParams()
  if (contextType) params.set('contextType', contextType)
  if (contextId) params.set('contextId', String(contextId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/preferences${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch dashboard preferences')
  return response.json()
}

export async function updateDashboardPreferences(
  request: UpdateDashboardPreferencesRequest,
  contextType?: string,
  contextId?: number
): Promise<DashboardPreferencesResponse> {
  const params = new URLSearchParams()
  if (contextType) params.set('contextType', contextType)
  if (contextId) params.set('contextId', String(contextId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/preferences${qs ? '?' + qs : ''}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) throw new Error('Failed to update dashboard preferences')
  return response.json()
}

export async function resetDashboardPreferences(
  contextType?: string,
  contextId?: number
): Promise<DashboardPreferencesResponse> {
  const params = new URLSearchParams()
  if (contextType) params.set('contextType', contextType)
  if (contextId) params.set('contextId', String(contextId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/preferences/reset${qs ? '?' + qs : ''}`, {
    method: 'POST',
  })
  if (!response.ok) throw new Error('Failed to reset dashboard preferences')
  return response.json()
}

export async function getDashboardSummary(connectionId?: number): Promise<DashboardSummaryResponse> {
  const params = new URLSearchParams()
  if (connectionId) params.set('connectionId', String(connectionId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/summary${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch dashboard summary')
  return response.json()
}

export async function getDashboardCash(connectionId?: number): Promise<DashboardCashResponse> {
  const params = new URLSearchParams()
  if (connectionId) params.set('connectionId', String(connectionId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/cash${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch dashboard cash')
  return response.json()
}

export async function getSectorExposure(connectionId?: number): Promise<SectorExposureResponse> {
  const params = new URLSearchParams()
  if (connectionId) params.set('connectionId', String(connectionId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/exposure/sector${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch sector exposure')
  return response.json()
}

export async function getGeographyExposure(connectionId?: number): Promise<GeographyExposureResponse> {
  const params = new URLSearchParams()
  if (connectionId) params.set('connectionId', String(connectionId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/exposure/geography${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch geography exposure')
  return response.json()
}

export async function getRiskProfile(connectionId?: number): Promise<RiskProfileResponse> {
  const params = new URLSearchParams()
  if (connectionId) params.set('connectionId', String(connectionId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/risk-profile${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch risk profile')
  return response.json()
}

export async function getOpenOrders(): Promise<OpenOrdersResponse> {
  const response = await apiFetch('/api/v1/dashboard/orders/open')
  if (!response.ok) throw new Error('Failed to fetch open orders')
  return response.json()
}

export async function getFees(connectionId?: number): Promise<FeesResponse> {
  const params = new URLSearchParams()
  if (connectionId) params.set('connectionId', String(connectionId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/fees${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch fees')
  return response.json()
}

export async function getDividendCalendar(
  month?: string,
  connectionId?: number
): Promise<DividendCalendarResponse> {
  const params = new URLSearchParams()
  if (month) params.set('month', month)
  if (connectionId) params.set('connectionId', String(connectionId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/dividends${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch dividend calendar')
  return response.json()
}

export async function getDashboardPositions(connectionId?: number): Promise<AggregatedPositionsResponse> {
  const params = new URLSearchParams()
  if (connectionId) params.set('connectionId', String(connectionId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/positions${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch dashboard positions')
  return response.json()
}

export async function getDashboardHoldings(connectionId?: number): Promise<HoldingsTableResponse> {
  const params = new URLSearchParams()
  if (connectionId) params.set('connectionId', String(connectionId))
  const qs = params.toString()
  const response = await apiFetch(`/api/v1/dashboard/holdings${qs ? '?' + qs : ''}`)
  if (!response.ok) throw new Error('Failed to fetch dashboard holdings')
  return response.json()
}

export async function getDashboardAccounts(): Promise<DashboardAccountsResponse> {
  const response = await apiFetch('/api/v1/dashboard/accounts')
  if (!response.ok) throw new Error('Failed to fetch dashboard accounts')
  return response.json()
}

export async function refreshAll(): Promise<void> {
  const response = await apiFetch('/api/v1/dashboard/refresh', {
    method: 'POST',
  })
  if (!response.ok) throw new Error('Failed to refresh dashboard data')
}
