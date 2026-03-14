import { useQuery } from '@tanstack/react-query'
import { getDashboard } from '../services/dashboardService'

export const dashboardKeys = {
  all: ['dashboard'] as const,
  data: () => [...dashboardKeys.all, 'data'] as const
}

export function useDashboard() {
  return useQuery({
    queryKey: dashboardKeys.data(),
    queryFn: getDashboard,
    staleTime: 60 * 1000,
    refetchInterval: 2 * 60 * 1000
  })
}
