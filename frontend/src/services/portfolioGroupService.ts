import { apiFetch } from './api'
import type {
  PortfolioGroupsListResponse,
  PortfolioGroup,
  TargetAllocation,
  LinkedAccount,
  PortfolioGroupSettings,
  DriftAnalysis,
  RebalanceTradesResult,
  ExcludedAsset,
  CreatePortfolioGroupRequest,
  UpdatePortfolioGroupRequest,
  SetTargetsRequest,
  UpdateSettingsRequest
} from '../types/portfolioGroup'

const API_BASE = '/api/v1/portfolio-groups'

// ========== Group CRUD ==========

export async function getPortfolioGroups(): Promise<PortfolioGroupsListResponse> {
  const response = await apiFetch(API_BASE)
  if (!response.ok) throw new Error('Failed to fetch portfolio groups')
  return response.json()
}

export async function getPortfolioGroup(groupId: number): Promise<PortfolioGroup> {
  const response = await apiFetch(`${API_BASE}/${groupId}`)
  if (!response.ok) throw new Error('Failed to fetch portfolio group')
  return response.json()
}

export async function createPortfolioGroup(request: CreatePortfolioGroupRequest): Promise<PortfolioGroup> {
  const response = await apiFetch(API_BASE, {
    method: 'POST',
    body: JSON.stringify(request)
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to create portfolio group')
  }
  return response.json()
}

export async function updatePortfolioGroup(groupId: number, request: UpdatePortfolioGroupRequest): Promise<PortfolioGroup> {
  const response = await apiFetch(`${API_BASE}/${groupId}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to update portfolio group')
  }
  return response.json()
}

export async function deletePortfolioGroup(groupId: number): Promise<void> {
  const response = await apiFetch(`${API_BASE}/${groupId}`, { method: 'DELETE' })
  if (!response.ok) throw new Error('Failed to delete portfolio group')
}

// ========== Targets ==========

export async function setTargets(groupId: number, request: SetTargetsRequest): Promise<TargetAllocation[]> {
  const response = await apiFetch(`${API_BASE}/${groupId}/targets`, {
    method: 'PUT',
    body: JSON.stringify(request)
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to set targets')
  }
  return response.json()
}

export async function addTarget(groupId: number, symbol: string, targetPercent: number): Promise<TargetAllocation> {
  const response = await apiFetch(`${API_BASE}/${groupId}/targets`, {
    method: 'POST',
    body: JSON.stringify({ symbol, targetPercent })
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to add target')
  }
  return response.json()
}

export async function removeTarget(groupId: number, symbol: string): Promise<void> {
  const response = await apiFetch(`${API_BASE}/${groupId}/targets/${encodeURIComponent(symbol)}`, { method: 'DELETE' })
  if (!response.ok) throw new Error('Failed to remove target')
}

// ========== Account Linking ==========

export async function linkAccount(groupId: number, connectionId: number): Promise<LinkedAccount> {
  const response = await apiFetch(`${API_BASE}/${groupId}/accounts`, {
    method: 'POST',
    body: JSON.stringify({ connectionId })
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to link account')
  }
  return response.json()
}

export async function unlinkAccount(groupId: number, connectionId: number): Promise<void> {
  const response = await apiFetch(`${API_BASE}/${groupId}/accounts/${connectionId}`, { method: 'DELETE' })
  if (!response.ok) throw new Error('Failed to unlink account')
}

// ========== Drift & Rebalance ==========

export async function getDriftAnalysis(groupId: number): Promise<DriftAnalysis> {
  const response = await apiFetch(`${API_BASE}/${groupId}/drift`)
  if (!response.ok) throw new Error('Failed to fetch drift analysis')
  return response.json()
}

export async function getRebalanceTrades(groupId: number): Promise<RebalanceTradesResult> {
  const response = await apiFetch(`${API_BASE}/${groupId}/rebalance`)
  if (!response.ok) throw new Error('Failed to fetch rebalance trades')
  return response.json()
}

// ========== Settings ==========

export async function getGroupSettings(groupId: number): Promise<PortfolioGroupSettings> {
  const response = await apiFetch(`${API_BASE}/${groupId}/settings`)
  if (!response.ok) throw new Error('Failed to fetch group settings')
  return response.json()
}

export async function updateGroupSettings(groupId: number, request: UpdateSettingsRequest): Promise<PortfolioGroupSettings> {
  const response = await apiFetch(`${API_BASE}/${groupId}/settings`, {
    method: 'PATCH',
    body: JSON.stringify(request)
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to update settings')
  }
  return response.json()
}

// ========== Excluded Assets ==========

export async function getExcludedAssets(groupId: number): Promise<ExcludedAsset[]> {
  const response = await apiFetch(`${API_BASE}/${groupId}/excluded-assets`)
  if (!response.ok) throw new Error('Failed to fetch excluded assets')
  return response.json()
}

export async function addExcludedAsset(groupId: number, symbol: string): Promise<ExcludedAsset> {
  const response = await apiFetch(`${API_BASE}/${groupId}/excluded-assets`, {
    method: 'POST',
    body: JSON.stringify({ symbol })
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || 'Failed to add excluded asset')
  }
  return response.json()
}

export async function removeExcludedAsset(groupId: number, symbol: string): Promise<void> {
  const response = await apiFetch(`${API_BASE}/${groupId}/excluded-assets/${encodeURIComponent(symbol)}`, { method: 'DELETE' })
  if (!response.ok) throw new Error('Failed to remove excluded asset')
}
