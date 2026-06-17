// ─── Types ───

export interface InstrumentTypeStats {
  total: number
  enriched: number
}

export interface IngestionStats {
  totalInstruments: number
  enrichedInstruments: number
  pendingInstruments: number
  remainingDailyQuota: number
  totalDailyQuota: number
  exchangeCount: number
  exchanges: string[]
  lastRunStatus: string
  lastRunCompletedAt: string | null
  instrumentsByType: Record<string, InstrumentTypeStats>
}

export interface ActiveRunStep {
  name: string
  status: string
  processed: number
  created: number
  updated: number
  failed: number
}

export interface ActiveRun {
  isRunning: boolean
  runId?: number
  startedAt?: string
  steps?: ActiveRunStep[]
}

export interface IngestionRun {
  id: number
  runType: string
  status: string
  triggerSource: string | null
  startedAt: string
  completedAt: string | null
}

export interface IngestionStep {
  id: number
  stepName: string
  status: string
  startedAt: string
  completedAt: string | null
  recordsProcessed: number
  recordsCreated: number
  recordsUpdated: number
  recordsFailed: number
}

export interface IngestionError {
  id: number
  errorType: string
  errorCode: string | null
  errorMessage: string | null
  createdAt: string
}

// ─── API Functions ───

const BASE = '/ingestion-api/admin/ingestion'

async function ingestionFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.detail || body.message || `Request failed: ${response.status}`)
  }
  return response.json()
}

export async function getIngestionStats(): Promise<IngestionStats> {
  return ingestionFetch('/stats')
}

export async function getActiveRun(): Promise<ActiveRun> {
  return ingestionFetch('/active-run')
}

export async function triggerExchangeSync(): Promise<{ status: string }> {
  return ingestionFetch('/exchanges', { method: 'POST' })
}

export async function triggerFullIngestion(): Promise<{ status: string }> {
  return ingestionFetch('/run', { method: 'POST' })
}

export async function cancelIngestion(): Promise<{ status: string }> {
  return ingestionFetch('/cancel', { method: 'POST' })
}

export async function getIngestionRuns(limit: number = 10): Promise<IngestionRun[]> {
  return ingestionFetch(`/runs?limit=${limit}`)
}

export async function getRunSteps(runId: number): Promise<IngestionStep[]> {
  return ingestionFetch(`/runs/${runId}/steps`)
}

export async function getRunErrors(runId: number): Promise<IngestionError[]> {
  return ingestionFetch(`/runs/${runId}/errors`)
}
