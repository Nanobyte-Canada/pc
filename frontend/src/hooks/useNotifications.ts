import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  getNotificationPreferences,
  updateNotificationPreferences
} from '../services/notificationService'
import type { UpdateNotificationPreferencesRequest } from '../types/notification'

export const notificationKeys = {
  all: ['notifications'] as const,
  list: (unreadOnly?: boolean) => [...notificationKeys.all, 'list', unreadOnly] as const,
  unreadCount: () => [...notificationKeys.all, 'unread-count'] as const,
  preferences: () => [...notificationKeys.all, 'preferences'] as const
}

export function useNotifications(unreadOnly: boolean = false, page: number = 0) {
  return useQuery({
    queryKey: notificationKeys.list(unreadOnly),
    queryFn: () => getNotifications(unreadOnly, page),
    staleTime: 30 * 1000
  })
}

export function useUnreadCount() {
  return useQuery({
    queryKey: notificationKeys.unreadCount(),
    queryFn: getUnreadCount,
    refetchInterval: 30 * 1000,
    staleTime: 15 * 1000
  })
}

export function useMarkAsRead() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => markAsRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.all })
    }
  })
}

export function useMarkAllAsRead() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: markAllAsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.all })
    }
  })
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: notificationKeys.preferences(),
    queryFn: getNotificationPreferences,
    staleTime: 60 * 1000
  })
}

export function useUpdateNotificationPreferences() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: UpdateNotificationPreferencesRequest) => updateNotificationPreferences(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.preferences() })
    }
  })
}
