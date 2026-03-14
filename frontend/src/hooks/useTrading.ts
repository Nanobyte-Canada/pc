import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  executeTrades,
  executeSingleTrade,
  getGroupOrders,
  getBatchOrders,
  cancelOrder
} from '../services/tradingService'
import type { ExecuteTradesRequest, TradeExecutionInput } from '../types/trading'

export const tradingKeys = {
  all: ['trading'] as const,
  groupOrders: (groupId: number) => [...tradingKeys.all, 'group-orders', groupId] as const,
  batchOrders: (batchId: string) => [...tradingKeys.all, 'batch-orders', batchId] as const
}

export function useGroupOrders(groupId: number, enabled: boolean = true) {
  return useQuery({
    queryKey: tradingKeys.groupOrders(groupId),
    queryFn: () => getGroupOrders(groupId),
    enabled: enabled && groupId > 0,
    refetchInterval: 15 * 1000,
    staleTime: 10 * 1000
  })
}

export function useBatchOrders(batchId: string | null) {
  return useQuery({
    queryKey: tradingKeys.batchOrders(batchId || ''),
    queryFn: () => getBatchOrders(batchId!),
    enabled: !!batchId,
    refetchInterval: 10 * 1000
  })
}

export function useExecuteTrades() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: ExecuteTradesRequest) => executeTrades(request),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: tradingKeys.groupOrders(variables.groupId) })
    }
  })
}

export function useExecuteSingleTrade() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ groupId, trade }: { groupId: number; trade: TradeExecutionInput }) =>
      executeSingleTrade(groupId, trade),
    onSuccess: (_, { groupId }) => {
      queryClient.invalidateQueries({ queryKey: tradingKeys.groupOrders(groupId) })
    }
  })
}

export function useCancelOrder() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (orderId: number) => cancelOrder(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tradingKeys.all })
    }
  })
}
