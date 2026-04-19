import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import * as api from '../services/dashboardWidgetService'
import { brokerKeys, dashboardKeys } from './queryKeys'

export { dashboardKeys }

export function useDashboardSummary(connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.summary(connectionId),
    queryFn: () => api.getDashboardSummary(connectionId),
    staleTime: 60_000,
    refetchInterval: 2 * 60_000,
  })
}

export function useDashboardCash(connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.cash(connectionId),
    queryFn: () => api.getDashboardCash(connectionId),
    staleTime: 60_000,
  })
}

export function useSectorExposure(connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.sectorExposure(connectionId),
    queryFn: () => api.getSectorExposure(connectionId),
    staleTime: 5 * 60_000,
  })
}

export function useGeographyExposure(connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.geographyExposure(connectionId),
    queryFn: () => api.getGeographyExposure(connectionId),
    staleTime: 5 * 60_000,
  })
}

export function useRiskProfile(connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.riskProfile(connectionId),
    queryFn: () => api.getRiskProfile(connectionId),
    staleTime: 5 * 60_000,
  })
}

export function useOpenOrders() {
  return useQuery({
    queryKey: dashboardKeys.openOrders(),
    queryFn: () => api.getOpenOrders(),
    staleTime: 30_000,
    refetchInterval: 60_000,
  })
}

export function useFees(connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.fees(connectionId),
    queryFn: () => api.getFees(connectionId),
    staleTime: 5 * 60_000,
  })
}

export function useDividendCalendar(month?: string, connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.dividendCalendar(month, connectionId),
    queryFn: () => api.getDividendCalendar(month, connectionId),
    staleTime: 5 * 60_000,
  })
}

export function useDashboardPositions(connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.positions(connectionId),
    queryFn: () => api.getDashboardPositions(connectionId),
    staleTime: 60_000,
    refetchInterval: 2 * 60_000,
  })
}

export function useDashboardHoldings(connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.holdings(connectionId),
    queryFn: () => api.getDashboardHoldings(connectionId),
    staleTime: 5 * 60_000,
  })
}

export function useDashboardIrr(connectionId?: number) {
  return useQuery({
    queryKey: dashboardKeys.irr(connectionId),
    queryFn: () => api.getDashboardIrr(connectionId),
    staleTime: 5 * 60_000,
  })
}

export function useDashboardAccounts() {
  return useQuery({
    queryKey: dashboardKeys.accounts(),
    queryFn: () => api.getDashboardAccounts(),
    staleTime: 60_000,
  })
}

export function useRefreshAll() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => api.refreshAll(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
      queryClient.invalidateQueries({ queryKey: brokerKeys.all })
    },
  })
}
