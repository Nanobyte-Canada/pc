import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getAvailableBrokers,
  getUserConnections,
  connectBroker,
  disconnectBroker,
  triggerPositionFetch,
  getConnectionPositions,
  getAggregatedPositions,
  getSnapTradeStatus,
  syncConnections,
  getConnectionActivities,
  syncConnectionActivities,
  getBalanceHistory
} from '../services/brokerService'
import type { ConnectBrokerRequest } from '../types/broker'

// Query keys
export const brokerKeys = {
  all: ['brokers'] as const,
  available: () => [...brokerKeys.all, 'available'] as const,
  connections: () => [...brokerKeys.all, 'connections'] as const,
  positions: () => [...brokerKeys.all, 'positions'] as const,
  connectionPositions: (id: number) => [...brokerKeys.positions(), id] as const,
  aggregatedPositions: () => [...brokerKeys.positions(), 'aggregated'] as const,
  activities: (id: number) => [...brokerKeys.all, 'activities', id] as const,
  balanceHistory: (id: number) => [...brokerKeys.all, 'balance-history', id] as const
}

// ========== Queries ==========

export function useAvailableBrokers() {
  return useQuery({
    queryKey: brokerKeys.available(),
    queryFn: getAvailableBrokers,
    staleTime: 5 * 60 * 1000 // 5 minutes
  })
}

export function useBrokerConnections() {
  return useQuery({
    queryKey: brokerKeys.connections(),
    queryFn: getUserConnections,
    staleTime: 30 * 1000 // 30 seconds
  })
}

export function useConnectionPositions(connectionId: number, enabled: boolean = true) {
  return useQuery({
    queryKey: brokerKeys.connectionPositions(connectionId),
    queryFn: () => getConnectionPositions(connectionId),
    enabled,
    staleTime: 60 * 1000 // 1 minute
  })
}

export function useAggregatedPositions() {
  return useQuery({
    queryKey: brokerKeys.aggregatedPositions(),
    queryFn: getAggregatedPositions,
    staleTime: 60 * 1000 // 1 minute
  })
}

// ========== Mutations ==========

export function useConnectBroker() {
  return useMutation({
    mutationFn: (request?: ConnectBrokerRequest) => connectBroker(request),
    onSuccess: (data) => {
      // Redirect to SnapTrade connection portal
      window.location.href = data.redirectUrl
    }
  })
}

export function useDisconnectBroker() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (authorizationId: string) => disconnectBroker(authorizationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: brokerKeys.connections() })
      queryClient.invalidateQueries({ queryKey: brokerKeys.positions() })
    }
  })
}

export function useTriggerPositionFetch() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (connectionId: number) => triggerPositionFetch(connectionId),
    onSuccess: (_, connectionId) => {
      // Invalidate after a short delay to allow fetch to complete
      setTimeout(() => {
        queryClient.invalidateQueries({ queryKey: brokerKeys.connections() })
        queryClient.invalidateQueries({ queryKey: brokerKeys.connectionPositions(connectionId) })
        queryClient.invalidateQueries({ queryKey: brokerKeys.aggregatedPositions() })
      }, 2000)
    }
  })
}

// ========== SnapTrade Status ==========

export function useSnapTradeStatus() {
  return useQuery({
    queryKey: [...brokerKeys.all, 'snaptrade-status'] as const,
    queryFn: getSnapTradeStatus,
    staleTime: 60_000,
    refetchInterval: 5 * 60_000
  })
}

// ========== Activities & Balances ==========

export function useConnectionActivities(
  connectionId: number,
  params: { page?: number; size?: number; startDate?: string; endDate?: string; type?: string } = {},
  enabled: boolean = true
) {
  return useQuery({
    queryKey: [...brokerKeys.activities(connectionId), params] as const,
    queryFn: () => getConnectionActivities(connectionId, params),
    enabled: enabled && connectionId > 0,
    staleTime: 60 * 1000
  })
}

export function useSyncActivities() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (connectionId: number) => syncConnectionActivities(connectionId),
    onSuccess: (_, connectionId) => {
      queryClient.invalidateQueries({ queryKey: brokerKeys.activities(connectionId) })
    }
  })
}

export function useBalanceHistory(connectionId: number, days: number = 90, enabled: boolean = true) {
  return useQuery({
    queryKey: [...brokerKeys.balanceHistory(connectionId), days] as const,
    queryFn: () => getBalanceHistory(connectionId, days),
    enabled: enabled && connectionId > 0,
    staleTime: 60 * 1000
  })
}

export function useSyncConnections() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: syncConnections,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: brokerKeys.connections() })
      queryClient.invalidateQueries({ queryKey: brokerKeys.positions() })
    }
  })
}
