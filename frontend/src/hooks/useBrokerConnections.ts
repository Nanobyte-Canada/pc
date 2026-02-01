import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getAvailableBrokers,
  getUserConnections,
  initiateConnection,
  disconnectBroker,
  triggerPositionFetch,
  getConnectionPositions,
  getAggregatedPositions,
  getBrokerPreferences,
  updateBrokerPreferences
} from '../services/brokerService'
import type { UpdateBrokerPrefsRequest } from '../types/broker'

// Query keys
export const brokerKeys = {
  all: ['brokers'] as const,
  available: () => [...brokerKeys.all, 'available'] as const,
  connections: () => [...brokerKeys.all, 'connections'] as const,
  positions: () => [...brokerKeys.all, 'positions'] as const,
  connectionPositions: (id: number) => [...brokerKeys.positions(), id] as const,
  aggregatedPositions: () => [...brokerKeys.positions(), 'aggregated'] as const,
  preferences: () => [...brokerKeys.all, 'preferences'] as const
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

export function useBrokerPreferences() {
  return useQuery({
    queryKey: brokerKeys.preferences(),
    queryFn: getBrokerPreferences,
    staleTime: 5 * 60 * 1000 // 5 minutes
  })
}

// ========== Mutations ==========

export function useInitiateConnection() {
  return useMutation({
    mutationFn: (brokerCode: string) => initiateConnection(brokerCode),
    onSuccess: (data) => {
      // Redirect to OAuth URL
      window.location.href = data.redirectUrl
    }
  })
}

export function useDisconnectBroker() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (connectionId: number) => disconnectBroker(connectionId),
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

export function useUpdateBrokerPreferences() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: UpdateBrokerPrefsRequest) => updateBrokerPreferences(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: brokerKeys.preferences() })
    }
  })
}
