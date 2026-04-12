import { apiFetch } from './api'
import type {
  ModelPortfoliosListResponse,
  ModelPortfolioDetail,
  CreateModelPortfolioRequest,
  UpdateModelPortfolioRequest,
  ApplyToAccountsRequest,
  ModelAnalysisResponse,
  RebalanceProgressResponse,
  PendingOrdersResponse,
} from '@/types/modelPortfolio'

const BASE = '/api/v1/model-portfolios'

export async function getModelPortfolios(): Promise<ModelPortfoliosListResponse> {
  const response = await apiFetch(BASE)
  if (!response.ok) throw new Error('Failed to fetch model portfolios')
  return response.json()
}

export async function getModelPortfolio(id: number): Promise<ModelPortfolioDetail> {
  const response = await apiFetch(`${BASE}/${id}`)
  if (!response.ok) throw new Error('Failed to fetch model portfolio')
  return response.json()
}

export async function createModelPortfolio(request: CreateModelPortfolioRequest): Promise<ModelPortfolioDetail> {
  const response = await apiFetch(BASE, { method: 'POST', body: JSON.stringify(request) })
  if (!response.ok) throw new Error('Failed to create model portfolio')
  return response.json()
}

export async function updateModelPortfolio(id: number, request: UpdateModelPortfolioRequest): Promise<ModelPortfolioDetail> {
  const response = await apiFetch(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(request) })
  if (!response.ok) throw new Error('Failed to update model portfolio')
  return response.json()
}

export async function deleteModelPortfolio(id: number): Promise<void> {
  const response = await apiFetch(`${BASE}/${id}`, { method: 'DELETE' })
  if (!response.ok) throw new Error('Failed to delete model portfolio')
}

export async function applyModelToAccounts(modelId: number, request: ApplyToAccountsRequest): Promise<void> {
  const response = await apiFetch(`${BASE}/${modelId}/apply-to-accounts`, { method: 'POST', body: JSON.stringify(request) })
  if (!response.ok) throw new Error('Failed to apply model to accounts')
}

export async function getModelAnalysis(modelId: number): Promise<ModelAnalysisResponse> {
  const response = await apiFetch(`${BASE}/${modelId}/analysis`)
  if (!response.ok) throw new Error('Failed to fetch model analysis')
  return response.json()
}

export async function getRebalanceProgress(connectionId: number): Promise<RebalanceProgressResponse> {
  const response = await apiFetch(`/api/v1/brokers/connections/${connectionId}/rebalance-progress`)
  if (!response.ok) throw new Error('Failed to fetch rebalance progress')
  return response.json()
}

export async function getPendingOrders(connectionId: number): Promise<PendingOrdersResponse> {
  const response = await apiFetch(`/api/v1/brokers/connections/${connectionId}/pending-orders`)
  if (!response.ok) throw new Error('Failed to fetch pending orders')
  return response.json()
}
