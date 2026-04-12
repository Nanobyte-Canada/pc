import { apiFetch } from './api'
import type {
  NotificationsResponse,
  Notification,
  NotificationPreferences,
  UpdateNotificationPreferencesRequest
} from '../types/notification'

const API_BASE = '/api/v1/notifications'

export async function getNotifications(
  unreadOnly: boolean = false,
  page: number = 0,
  size: number = 20
): Promise<NotificationsResponse> {
  const params = new URLSearchParams({ unreadOnly: String(unreadOnly), page: String(page), size: String(size) })
  const response = await apiFetch(`${API_BASE}?${params}`)
  if (!response.ok) throw new Error('Failed to fetch notifications')
  return response.json()
}

export async function getUnreadCount(): Promise<{ unreadCount: number }> {
  const response = await apiFetch(`${API_BASE}/count`)
  if (!response.ok) throw new Error('Failed to fetch unread count')
  return response.json()
}

export async function markAsRead(id: number): Promise<Notification> {
  const response = await apiFetch(`${API_BASE}/${id}/read`, { method: 'POST' })
  if (!response.ok) throw new Error('Failed to mark notification as read')
  return response.json()
}

export async function markAllAsRead(): Promise<{ markedCount: number }> {
  const response = await apiFetch(`${API_BASE}/read-all`, { method: 'POST' })
  if (!response.ok) throw new Error('Failed to mark all as read')
  return response.json()
}

export async function getNotificationPreferences(): Promise<NotificationPreferences> {
  const response = await apiFetch(`${API_BASE}/preferences`)
  if (!response.ok) throw new Error('Failed to fetch notification preferences')
  return response.json()
}

export async function updateNotificationPreferences(
  request: UpdateNotificationPreferencesRequest
): Promise<NotificationPreferences> {
  const response = await apiFetch(`${API_BASE}/preferences`, {
    method: 'PATCH',
    body: JSON.stringify(request)
  })
  if (!response.ok) throw new Error('Failed to update notification preferences')
  return response.json()
}
