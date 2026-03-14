import { useQuery } from '@tanstack/react-query'
import { getReportingPerformance, getReportingActivities } from '../services/brokerService'

export const reportingKeys = {
  all: ['reporting'] as const,
  performance: (params: Record<string, string | undefined>) => [...reportingKeys.all, 'performance', params] as const,
  activities: (params: Record<string, string | number | undefined>) => [...reportingKeys.all, 'activities', params] as const
}

export function useReportingPerformance(params: { startDate?: string; endDate?: string; accounts?: string; granularity?: string } = {}) {
  return useQuery({
    queryKey: reportingKeys.performance(params),
    queryFn: () => getReportingPerformance(params),
    staleTime: 2 * 60 * 1000
  })
}

export function useReportingActivities(params: {
  page?: number; size?: number; startDate?: string; endDate?: string; accounts?: string; type?: string
} = {}) {
  return useQuery({
    queryKey: reportingKeys.activities(params),
    queryFn: () => getReportingActivities(params),
    staleTime: 60 * 1000
  })
}
