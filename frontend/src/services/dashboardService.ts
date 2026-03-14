import { apiFetch } from './api'
import type { DashboardData } from '../types/notification'

export async function getDashboard(): Promise<DashboardData> {
  const response = await apiFetch('/api/v1/dashboard')
  if (!response.ok) throw new Error('Failed to fetch dashboard')
  return response.json()
}
