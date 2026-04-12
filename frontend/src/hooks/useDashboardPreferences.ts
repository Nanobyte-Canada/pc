import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import * as api from '../services/dashboardWidgetService'
import type { UpdateDashboardPreferencesRequest } from '../types/dashboard'

const preferencesKeys = {
  all: ['dashboard', 'preferences'] as const,
  detail: (contextType?: string, contextId?: number) =>
    [...preferencesKeys.all, contextType, contextId] as const,
}

export function useDashboardPreferences(contextType?: string, contextId?: number) {
  return useQuery({
    queryKey: preferencesKeys.detail(contextType, contextId),
    queryFn: () => api.getDashboardPreferences(contextType, contextId),
    staleTime: 5 * 60_000,
  })
}

export function useUpdateDashboardPreferences() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      request,
      contextType,
      contextId,
    }: {
      request: UpdateDashboardPreferencesRequest
      contextType?: string
      contextId?: number
    }) => api.updateDashboardPreferences(request, contextType, contextId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: preferencesKeys.all })
    },
  })
}

export function useResetDashboardPreferences() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      contextType,
      contextId,
    }: {
      contextType?: string
      contextId?: number
    }) => api.resetDashboardPreferences(contextType, contextId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: preferencesKeys.all })
    },
  })
}
