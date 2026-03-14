// ========== Response Types ==========

export interface Notification {
  id: number
  type: string
  title: string
  message: string
  link: string | null
  isRead: boolean
  metadata: string | null
  createdAt: string
}

export interface NotificationsResponse {
  notifications: Notification[]
  unreadCount: number
  totalCount: number
}

export interface NotificationPreferences {
  emailEnabled: boolean
  inAppEnabled: boolean
  driftAlerts: boolean
  driftThreshold: number
  orderAlerts: boolean
  syncFailureAlerts: boolean
  newAssetAlerts: boolean
  rebalanceReminder: boolean
  reminderFrequency: string
}

export interface DashboardData {
  totalPortfolioValue: number
  dayChange: number
  dayChangePercent: number
  averageAccuracy: number
  portfolioGroups: DashboardGroupSummary[]
  recentOrders: import('../types/trading').TradeOrder[]
  activeAlerts: Notification[]
  unreadNotificationCount: number
}

export interface DashboardGroupSummary {
  id: number
  name: string
  totalValue: number
  accuracy: number
  targetCount: number
  accountCount: number
}

// ========== Request Types ==========

export interface UpdateNotificationPreferencesRequest {
  emailEnabled?: boolean
  inAppEnabled?: boolean
  driftAlerts?: boolean
  driftThreshold?: number
  orderAlerts?: boolean
  syncFailureAlerts?: boolean
  newAssetAlerts?: boolean
  rebalanceReminder?: boolean
  reminderFrequency?: string
}
