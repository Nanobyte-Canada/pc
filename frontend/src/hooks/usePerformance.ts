import { useQuery } from '@tanstack/react-query'
import { getPerformanceSummary, getPerformanceChart } from '../services/performanceService'

export const performanceKeys = {
  all: ['performance'] as const,
  summary: (groupId: number, period: string) => [...performanceKeys.all, 'summary', groupId, period] as const,
  chart: (groupId: number, period: string, benchmark?: string) =>
    [...performanceKeys.all, 'chart', groupId, period, benchmark] as const
}

export function usePerformanceSummary(groupId: number, period: string, enabled: boolean = true) {
  return useQuery({
    queryKey: performanceKeys.summary(groupId, period),
    queryFn: () => getPerformanceSummary(groupId, period),
    enabled: enabled && groupId > 0,
    staleTime: 60 * 1000
  })
}

export function usePerformanceChart(groupId: number, period: string, benchmark?: string, enabled: boolean = true) {
  return useQuery({
    queryKey: performanceKeys.chart(groupId, period, benchmark),
    queryFn: () => getPerformanceChart(groupId, period, benchmark),
    enabled: enabled && groupId > 0,
    staleTime: 60 * 1000
  })
}
