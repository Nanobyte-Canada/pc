import { apiFetch } from './api'
import type { PerformanceSummary, PerformanceChartData } from '../types/performance'

const BASE = '/api/v1/portfolio-groups'

export async function getPerformanceSummary(
  groupId: number,
  period: string = '1Y'
): Promise<PerformanceSummary> {
  const response = await apiFetch(`${BASE}/${groupId}/performance?period=${period}`)
  if (!response.ok) throw new Error('Failed to fetch performance summary')
  return response.json()
}

export async function getPerformanceChart(
  groupId: number,
  period: string = '1Y',
  benchmark?: string
): Promise<PerformanceChartData> {
  const params = new URLSearchParams({ period })
  if (benchmark) params.append('benchmark', benchmark)
  const response = await apiFetch(`${BASE}/${groupId}/performance/chart?${params}`)
  if (!response.ok) throw new Error('Failed to fetch performance chart')
  return response.json()
}
