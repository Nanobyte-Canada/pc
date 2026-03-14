// ========== Response Types ==========

export interface PortfolioGroupSummary {
  id: number
  name: string
  description: string | null
  accountCount: number
  targetCount: number
  totalValue: number
  accuracy: number
}

export interface PortfolioGroup {
  id: number
  name: string
  description: string | null
  targets: TargetAllocation[]
  linkedAccounts: LinkedAccount[]
  settings: PortfolioGroupSettings
  excludedAssets: ExcludedAsset[]
  totalValue: number
  accuracy: number
}

export interface TargetAllocation {
  id: number
  symbol: string
  targetPercent: number
}

export interface LinkedAccount {
  connectionId: number
  accountName: string | null
  accountNumber: string | null
  accountType: string | null
  totalValue: number | null
  status: string
}

export interface PortfolioGroupSettings {
  sellToRebalance: boolean
  keepCurrenciesSeparate: boolean
  preventNonTradableTrades: boolean
  notifyNewAssets: boolean
  retainCashForExchange: boolean
}

export interface DriftHolding {
  symbol: string
  securityName: string | null
  targetPercent: number
  actualPercent: number
  driftPercent: number
  actualValue: number
  targetValue: number
  currency: string
}

export interface ExcludedAsset {
  symbol: string
  securityName: string | null
  currentValue: number | null
  currency: string | null
}

export interface NewAsset {
  symbol: string
  securityName: string | null
  currentValue: number | null
  currency: string | null
}

export interface DriftAnalysis {
  groupId: number
  groupName: string
  accuracy: number
  totalValue: number
  cash: Record<string, number>
  holdings: DriftHolding[]
  excludedAssets: ExcludedAsset[]
  newAssets: NewAsset[]
}

export interface RebalanceTrade {
  action: 'BUY' | 'SELL'
  symbol: string
  securityName: string | null
  units: number
  price: number
  amount: number
  currency: string
  accountName: string | null
  connectionId: number
}

export interface RebalanceTradesResult {
  groupId: number
  trades: RebalanceTrade[]
  cashRemaining: Record<string, number>
  resultingAccuracy: number
}

// ========== Request Types ==========

export interface CreatePortfolioGroupRequest {
  name: string
  description?: string
  targets?: { symbol: string; targetPercent: number }[]
  accountIds?: number[]
}

export interface UpdatePortfolioGroupRequest {
  name?: string
  description?: string
}

export interface SetTargetsRequest {
  targets: { symbol: string; targetPercent: number }[]
}

export interface UpdateSettingsRequest {
  sellToRebalance?: boolean
  keepCurrenciesSeparate?: boolean
  preventNonTradableTrades?: boolean
  notifyNewAssets?: boolean
  retainCashForExchange?: boolean
}

// ========== List Responses ==========

export interface PortfolioGroupsListResponse {
  groups: PortfolioGroupSummary[]
}
