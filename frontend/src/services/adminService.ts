import { apiFetch } from './api';

// ─────────────────────────────────────────────
// Response types
// ─────────────────────────────────────────────

export interface TriggerIngestionResponse {
  runId: number;
  status: string;
  message: string;
}

export interface IngestionRun {
  id: number;
  runType: string;
  status: string;
  triggerSource: string | null;
  startedAt: string;
  completedAt: string | null;
  stepCount: number;
}

export interface IngestionStep {
  id: number;
  stepName: string;
  status: string;
  startedAt: string;
  completedAt: string | null;
  recordsProcessed: number;
  recordsCreated: number;
  recordsUpdated: number;
  recordsFailed: number;
  errorCount: number;
}

export interface IngestionRunDetails {
  id: number;
  runType: string;
  status: string;
  triggerSource: string | null;
  startedAt: string;
  completedAt: string | null;
  steps: IngestionStep[];
}

export interface IngestionError {
  id: number;
  stepId: number;
  errorType: string;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface ErrorSummary {
  errorType: string;
  count: number;
  lastOccurredAt: string | null;
}

export interface IngestionStats {
  totalStocks: number;
  stocksWithRawData: number;
  stocksPendingIngestion: number;
  totalEtfs: number;
  etfsEnriched: number;
  etfsPendingEnrichment: number;
  errorsLast24h: number;
}

// ─────────────────────────────────────────────
// Trigger functions
// ─────────────────────────────────────────────

export async function triggerFullIngestion(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/ingestion/run', { method: 'POST' });
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger full ingestion');
  }
  return response.json();
}

export async function triggerStockIngestion(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/ingestion/stocks/run', { method: 'POST' });
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger stock ingestion');
  }
  return response.json();
}

export async function triggerStockEnrichment(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/enrichment/stocks/run', { method: 'POST' });
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger stock enrichment');
  }
  return response.json();
}

export async function triggerEtfComUniverse(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/ingestion/etfcom/universe', { method: 'POST' });
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger ETF ingestion');
  }
  return response.json();
}

export async function triggerEtfComEnrichment(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/enrichment/etfcom/run', { method: 'POST' });
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger ETF enrichment');
  }
  return response.json();
}

// ─────────────────────────────────────────────
// Query functions
// ─────────────────────────────────────────────

export async function getIngestionRuns(limit: number = 100): Promise<IngestionRun[]> {
  const response = await apiFetch(`/admin/ingestion/runs?limit=${limit}`);
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to fetch ingestion runs');
  }
  return response.json();
}

export async function getIngestionRunDetails(id: number): Promise<IngestionRunDetails> {
  const response = await apiFetch(`/admin/ingestion/runs/${id}`);
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to fetch run details');
  }
  return response.json();
}

export async function getRunSteps(id: number): Promise<IngestionStep[]> {
  const response = await apiFetch(`/admin/ingestion/runs/${id}/steps`);
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to fetch run steps');
  }
  return response.json();
}

export async function getIngestionRunErrors(id: number): Promise<IngestionError[]> {
  const response = await apiFetch(`/admin/ingestion/runs/${id}/errors`);
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to fetch run errors');
  }
  return response.json();
}

export async function getRecentErrors(params?: {
  stepName?: string;
  errorType?: string;
  limit?: number;
}): Promise<IngestionError[]> {
  const searchParams = new URLSearchParams();
  if (params?.stepName) searchParams.set('stepName', params.stepName);
  if (params?.errorType) searchParams.set('errorType', params.errorType);
  if (params?.limit !== undefined) searchParams.set('limit', String(params.limit));
  const qs = searchParams.toString() ? `?${searchParams.toString()}` : '';
  const response = await apiFetch(`/admin/ingestion/errors${qs}`);
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to fetch recent errors');
  }
  return response.json();
}

export async function getErrorSummary(): Promise<ErrorSummary[]> {
  const response = await apiFetch('/admin/ingestion/errors/summary');
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to fetch error summary');
  }
  return response.json();
}

export async function getIngestionStats(): Promise<IngestionStats> {
  const response = await apiFetch('/admin/ingestion/stats');
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to fetch ingestion stats');
  }
  return response.json();
}
