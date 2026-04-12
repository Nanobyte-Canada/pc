import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getModelPortfolios,
  getModelPortfolio,
  createModelPortfolio,
  updateModelPortfolio,
  deleteModelPortfolio,
  applyModelToAccounts,
  getModelAnalysis,
  getRebalanceProgress,
  getPendingOrders,
} from '@/services/modelPortfolioService'
import type { CreateModelPortfolioRequest, UpdateModelPortfolioRequest } from '@/types/modelPortfolio'

const keys = {
  all: ['model-portfolios'] as const,
  detail: (id: number) => ['model-portfolios', id] as const,
}

export function useModelPortfolios() {
  return useQuery({
    queryKey: keys.all,
    queryFn: getModelPortfolios,
  })
}

export function useModelPortfolio(id: number, enabled = true) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => getModelPortfolio(id),
    enabled,
  })
}

export function useCreateModelPortfolio() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateModelPortfolioRequest) => createModelPortfolio(request),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.all }),
  })
}

export function useUpdateModelPortfolio() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: UpdateModelPortfolioRequest }) =>
      updateModelPortfolio(id, request),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: keys.all })
      qc.invalidateQueries({ queryKey: keys.detail(id) })
    },
  })
}

export function useDeleteModelPortfolio() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteModelPortfolio(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.all }),
  })
}

export function useApplyModelToAccounts() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ modelId, connectionIds }: { modelId: number; connectionIds: number[] }) =>
      applyModelToAccounts(modelId, { connectionIds }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.all })
      qc.invalidateQueries({ queryKey: ['broker-connections'] })
    },
  })
}

export function useModelAnalysis(modelId: number, enabled = true) {
  return useQuery({
    queryKey: ['model-analysis', modelId] as const,
    queryFn: () => getModelAnalysis(modelId),
    enabled,
    staleTime: 5 * 60 * 1000,
  })
}

export function useRebalanceProgress(connectionId: number, enabled = true) {
  return useQuery({
    queryKey: ['rebalance-progress', connectionId] as const,
    queryFn: () => getRebalanceProgress(connectionId),
    enabled,
    staleTime: 60 * 1000,
  })
}

export function usePendingOrders(connectionId: number, enabled = true) {
  return useQuery({
    queryKey: ['pending-orders', connectionId] as const,
    queryFn: () => getPendingOrders(connectionId),
    enabled,
    staleTime: 60 * 1000,
  })
}
