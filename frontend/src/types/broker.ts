export interface Broker {
  id: number
  code: string
  name: string
  authType: 'OAUTH2' | 'API_KEY' | 'AGGREGATOR'
  status: 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE'
  logoUrl: string | null
  description: string | null
}

export interface BrokersResponse {
  brokers: Broker[]
}

export interface BrokerConnection {
  id: number
  broker: Broker
  accountNumber: string | null
  accountType: string | null
  accountName: string | null
  status: 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'ERROR' | 'DISCONNECTED'
  lastPositionsFetchedAt: string | null
  positionsCount: number
  totalValue: number | null
  errorMessage: string | null
  createdAt: string
}

export interface BrokerConnectionsResponse {
  connections: BrokerConnection[]
}

export interface OAuthInitiateResponse {
  redirectUrl: string
  state: string
}

export interface PositionFetchResponse {
  fetchId: number
  status: string
  message: string
}

export interface BrokerPosition {
  id: number
  symbol: string
  securityName: string | null
  instrumentType: string | null
  quantity: number
  averageCost: number | null
  currentPrice: number | null
  currentValue: number | null
  totalPnl: number | null
  totalPnlPercent: number | null
  currency: string
}

export interface PositionsSummary {
  totalValue: number
  totalCost: number
  totalPnl: number
  totalPnlPercent: number
}

export interface ConnectionPositionsResponse {
  connectionId: number
  broker: string
  accountNumber: string | null
  asOfDate: string
  positions: BrokerPosition[]
  summary: PositionsSummary
}

export interface BrokerBreakdown {
  broker: string
  accountNumber: string | null
  quantity: number
  value: number | null
}

export interface AggregatedPosition {
  symbol: string
  securityName: string | null
  instrumentType: string | null
  totalQuantity: number
  totalValue: number
  averageCost: number | null
  totalPnl: number | null
  totalPnlPercent: number | null
  currency: string
  brokerBreakdown: BrokerBreakdown[]
}

export interface AggregateSummary {
  totalValue: number
  totalCost: number
  totalPnl: number
  totalPnlPercent: number
  brokerCount: number
  accountCount: number
}

export interface AggregatedPositionsResponse {
  asOfDate: string
  positions: AggregatedPosition[]
  aggregateSummary: AggregateSummary
}

export interface BrokerPrefs {
  autoFetchEnabled: boolean
  fetchTimeUtc: string
  notificationOnFetch: boolean
  notificationOnError: boolean
}

export interface UpdateBrokerPrefsRequest {
  autoFetchEnabled: boolean
  fetchTimeUtc?: string
}

export interface BrokerPrefsResponse {
  autoFetchEnabled: boolean
  fetchTimeUtc: string
  message?: string
}

// Utility types
export type ConnectionStatusType = BrokerConnection['status']

export const connectionStatusColors: Record<ConnectionStatusType, string> = {
  PENDING: '#f59e0b',
  ACTIVE: '#10b981',
  EXPIRED: '#ef4444',
  ERROR: '#ef4444',
  DISCONNECTED: '#6b7280'
}

export const connectionStatusLabels: Record<ConnectionStatusType, string> = {
  PENDING: 'Pending',
  ACTIVE: 'Active',
  EXPIRED: 'Token Expired',
  ERROR: 'Error',
  DISCONNECTED: 'Disconnected'
}
