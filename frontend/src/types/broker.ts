export interface Broker {
  id?: number
  name: string
  slug?: string
  status?: string
  logoUrl: string | null
  description: string | null
}

export interface BrokersResponse {
  brokers: Broker[]
}

export interface BrokerConnection {
  id: number
  broker: Broker
  snaptradeAuthorizationId: string | null
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

export interface ConnectBrokerRequest {
  broker?: string
  reconnectAuthId?: string
}

export interface ConnectBrokerResponse {
  redirectUrl: string
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
  broker: string | null
  accountNumber: string | null
  asOfDate: string
  positions: BrokerPosition[]
  summary: PositionsSummary
}

export interface BrokerBreakdown {
  broker: string | null
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

// SnapTrade Status types
export interface SnapTradeStatus {
  status: 'ONLINE' | 'DEGRADED' | 'OFFLINE' | 'UNKNOWN'
  responseTimeMs: number | null
  version: string | null
  uptimePercent24h: number
  lastChecked: string
}

export interface SnapTradeStatusResponse {
  status: SnapTradeStatus
}

export interface ConnectionSyncResponse {
  syncedCount: number
  message: string
}

// ========== Activity Types ==========

export interface BrokerActivityDto {
  id: number
  type: string
  symbol: string | null
  description: string | null
  quantity: number | null
  price: number | null
  amount: number
  fee: number | null
  currency: string
  tradeDate: string
  settlementDate: string | null
  accountName: string | null
  optionType: string | null
}

export interface ActivitiesResponse {
  activities: BrokerActivityDto[]
  totalCount: number
  page: number
  pageSize: number
}

// ========== Balance Types ==========

export interface BalanceSnapshotDto {
  totalValue: number | null
  cash: Record<string, number>
  currency: string
  asOfDate: string
}

export interface BalanceHistoryResponse {
  snapshots: BalanceSnapshotDto[]
  connectionId: number
}

// ========== Reporting Types ==========

export interface PeriodSummary {
  period: string
  contributions: number
  withdrawals: number
  net: number
}

export interface ValuePoint {
  date: string
  totalValue: number
  costBasis: number | null
}

export interface DividendPeriod {
  period: string
  total: number
  bySymbol: Record<string, number>
}

export interface SymbolDividend {
  symbol: string
  total: number
}

export interface PerformanceKpis {
  netContributions: number
  monthlyAvgContributions: number
  netChange: number
  totalDividendIncome: number
  avgMonthlyDividends: number
  feesAndCommissions: number
}

export interface ReportingPerformanceResponse {
  contributionsWithdrawals: PeriodSummary[]
  totalValueHistory: ValuePoint[]
  dividendHistory: DividendPeriod[]
  totalDividendsBySymbol: SymbolDividend[]
  kpis: PerformanceKpis
}

// Utility types
export type ConnectionStatusType = BrokerConnection['status']

export const connectionStatusColors: Record<ConnectionStatusType, string> = {
  PENDING: '#d97706',
  ACTIVE: '#059669',
  EXPIRED: '#dc2626',
  ERROR: '#dc2626',
  DISCONNECTED: '#6b7280'
}

export const connectionStatusLabels: Record<ConnectionStatusType, string> = {
  PENDING: 'Pending',
  ACTIVE: 'Active',
  EXPIRED: 'Token Expired',
  ERROR: 'Error',
  DISCONNECTED: 'Disconnected'
}
