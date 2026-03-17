import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import * as api from '../services/dashboardWidgetService'

export const dashboardKeys = {
  all: ['dashboard'] as const,
  summary: (connectionId?: number) => [...dashboardKeys.all, 'summary', connectionId] as const,
  cash: (connectionId?: number) => [...dashboardKeys.all, 'cash', connectionId] as const,
  sectorExposure: (connectionId?: number) => [...dashboardKeys.all, 'sectorExposure', connectionId] as const,
  geographyExposure: (connectionId?: number) => [...dashboardKeys.all, 'geographyExposure', connectionId] as const,
  riskProfile: (connectionId?: number) => [...dashboardKeys.all, 'riskProfile', connectionId] as const,
  openOrders: () => [...dashboardKeys.all, 'openOrders'] as const,
  fees: (connectionId?: number) => [...dashboardKeys.all, 'fees', connectionId] as const,
  dividendCalendar: (month?: string, connectionId?: number) =>
    [...dashboardKeys.all, 'dividendCalendar', month, connectionId] as const,
  positions: (connectionId?: number) => [...dashboardKeys.all, 'positions', connectionId] as const,
  holdings: (connectionId?: number) => [...dashboardKeys.all, 'holdings', connectionId] as const,
  accounts: () => [...dashboardKeys.all, 'accounts'] as const,
}

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
    },
  })
}
