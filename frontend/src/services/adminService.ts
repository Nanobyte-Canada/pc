import { apiFetch } from './api';

// Response types
export interface TriggerIngestionResponse {
  runId: number;
  status: string;
  message: string;
}

export interface IngestionRun {
  id: number;
  runType: string;
  status: string;
  triggeredBy: string | null;
  startedAt: string;
  completedAt: string | null;
  totalSteps: number;
  completedSteps: number;
  failedSteps: number;
  errorCount: number;
}

export interface IngestionStep {
  id: number;
  stepName: string;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
  itemsProcessed: number;
  itemsFailed: number;
  errorMessage: string | null;
}

export interface IngestionRunDetails {
  run: IngestionRun;
  steps: IngestionStep[];
}

export interface IngestionError {
  id: number;
  runId: number;
  stepName: string;
  errorType: string;
  errorMessage: string;
  stackTrace: string | null;
  itemIdentifier: string | null;
  createdAt: string;
}

// Ingestion trigger functions
export async function triggerFullIngestion(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/ingestion/run', {
    method: 'POST',
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger full ingestion');
  }

  return response.json();
}

export async function triggerUniverseRefresh(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/ingestion/universe', {
    method: 'POST',
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger universe refresh');
  }

  return response.json();
}

export async function triggerStockIngestion(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/ingestion/stocks/run', {
    method: 'POST',
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger stock ingestion');
  }

  return response.json();
}

export async function triggerEtfIngestion(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/ingestion/etfs/run', {
    method: 'POST',
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger ETF ingestion');
  }

  return response.json();
}

export async function triggerStockEnrichment(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/enrichment/stocks/run', {
    method: 'POST',
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger stock enrichment');
  }

  return response.json();
}

export async function triggerEtfEnrichment(): Promise<TriggerIngestionResponse> {
  const response = await apiFetch('/admin/enrichment/etfs/run', {
    method: 'POST',
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to trigger ETF enrichment');
  }

  return response.json();
}

// Query functions
export async function getIngestionRuns(limit: number = 10): Promise<IngestionRun[]> {
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

export async function getIngestionRunErrors(id: number): Promise<IngestionError[]> {
  const response = await apiFetch(`/admin/ingestion/runs/${id}/errors`);

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to fetch run errors');
  }

  return response.json();
}

export async function getRecentErrors(limit: number = 50): Promise<IngestionError[]> {
  const response = await apiFetch(`/admin/ingestion/errors?limit=${limit}`);

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Failed to fetch recent errors');
  }

  return response.json();
}
