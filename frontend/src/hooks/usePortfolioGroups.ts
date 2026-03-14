import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getPortfolioGroups,
  getPortfolioGroup,
  createPortfolioGroup,
  updatePortfolioGroup,
  deletePortfolioGroup,
  setTargets,
  linkAccount,
  unlinkAccount,
  getDriftAnalysis,
  getRebalanceTrades,
  getGroupSettings,
  updateGroupSettings,
  getExcludedAssets,
  addExcludedAsset,
  removeExcludedAsset
} from '../services/portfolioGroupService'
import type {
  CreatePortfolioGroupRequest,
  UpdatePortfolioGroupRequest,
  SetTargetsRequest,
  UpdateSettingsRequest
} from '../types/portfolioGroup'

// Query keys
export const portfolioGroupKeys = {
  all: ['portfolio-groups'] as const,
  lists: () => [...portfolioGroupKeys.all, 'list'] as const,
  detail: (id: number) => [...portfolioGroupKeys.all, 'detail', id] as const,
  drift: (id: number) => [...portfolioGroupKeys.all, 'drift', id] as const,
  rebalance: (id: number) => [...portfolioGroupKeys.all, 'rebalance', id] as const,
  settings: (id: number) => [...portfolioGroupKeys.all, 'settings', id] as const,
  excludedAssets: (id: number) => [...portfolioGroupKeys.all, 'excluded-assets', id] as const
}

// ========== Queries ==========

export function usePortfolioGroups() {
  return useQuery({
    queryKey: portfolioGroupKeys.lists(),
    queryFn: getPortfolioGroups,
    staleTime: 30 * 1000
  })
}

export function usePortfolioGroup(groupId: number, enabled: boolean = true) {
  return useQuery({
    queryKey: portfolioGroupKeys.detail(groupId),
    queryFn: () => getPortfolioGroup(groupId),
    enabled: enabled && groupId > 0,
    staleTime: 30 * 1000
  })
}

export function useDriftAnalysis(groupId: number, enabled: boolean = true) {
  return useQuery({
    queryKey: portfolioGroupKeys.drift(groupId),
    queryFn: () => getDriftAnalysis(groupId),
    enabled: enabled && groupId > 0,
    staleTime: 60 * 1000
  })
}

export function useRebalanceTrades(groupId: number, enabled: boolean = true) {
  return useQuery({
    queryKey: portfolioGroupKeys.rebalance(groupId),
    queryFn: () => getRebalanceTrades(groupId),
    enabled: enabled && groupId > 0,
    staleTime: 60 * 1000
  })
}

export function useGroupSettings(groupId: number, enabled: boolean = true) {
  return useQuery({
    queryKey: portfolioGroupKeys.settings(groupId),
    queryFn: () => getGroupSettings(groupId),
    enabled: enabled && groupId > 0,
    staleTime: 60 * 1000
  })
}

export function useExcludedAssets(groupId: number, enabled: boolean = true) {
  return useQuery({
    queryKey: portfolioGroupKeys.excludedAssets(groupId),
    queryFn: () => getExcludedAssets(groupId),
    enabled: enabled && groupId > 0,
    staleTime: 60 * 1000
  })
}

// ========== Mutations ==========

export function useCreatePortfolioGroup() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: CreatePortfolioGroupRequest) => createPortfolioGroup(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.lists() })
    }
  })
}

export function useUpdatePortfolioGroup() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ groupId, request }: { groupId: number; request: UpdatePortfolioGroupRequest }) =>
      updatePortfolioGroup(groupId, request),
    onSuccess: (_, { groupId }) => {
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.detail(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.lists() })
    }
  })
}

export function useDeletePortfolioGroup() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (groupId: number) => deletePortfolioGroup(groupId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.lists() })
    }
  })
}

export function useSetTargets() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ groupId, request }: { groupId: number; request: SetTargetsRequest }) =>
      setTargets(groupId, request),
    onSuccess: (_, { groupId }) => {
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.detail(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.drift(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.rebalance(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.lists() })
    }
  })
}

export function useLinkAccount() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ groupId, connectionId }: { groupId: number; connectionId: number }) =>
      linkAccount(groupId, connectionId),
    onSuccess: (_, { groupId }) => {
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.detail(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.drift(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.rebalance(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.lists() })
    }
  })
}

export function useUnlinkAccount() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ groupId, connectionId }: { groupId: number; connectionId: number }) =>
      unlinkAccount(groupId, connectionId),
    onSuccess: (_, { groupId }) => {
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.detail(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.drift(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.rebalance(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.lists() })
    }
  })
}

export function useUpdateGroupSettings() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ groupId, request }: { groupId: number; request: UpdateSettingsRequest }) =>
      updateGroupSettings(groupId, request),
    onSuccess: (_, { groupId }) => {
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.settings(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.detail(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.rebalance(groupId) })
    }
  })
}

export function useAddExcludedAsset() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ groupId, symbol }: { groupId: number; symbol: string }) =>
      addExcludedAsset(groupId, symbol),
    onSuccess: (_, { groupId }) => {
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.excludedAssets(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.drift(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.rebalance(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.detail(groupId) })
    }
  })
}

export function useRemoveExcludedAsset() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ groupId, symbol }: { groupId: number; symbol: string }) =>
      removeExcludedAsset(groupId, symbol),
    onSuccess: (_, { groupId }) => {
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.excludedAssets(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.drift(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.rebalance(groupId) })
      queryClient.invalidateQueries({ queryKey: portfolioGroupKeys.detail(groupId) })
    }
  })
}
