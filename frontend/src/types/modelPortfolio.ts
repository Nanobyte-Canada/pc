// ========== Model Portfolio Types ==========

export type RiskLevel = 'LOW' | 'MODERATE' | 'HIGH' | 'EXTRA_HIGH'

export interface ModelAllocation {
  id: number
  symbol: string
  targetPercent: number
  assetClass: string | null
}

export interface ModelPortfolioSummary {
  id: number
  name: string
  description: string | null
  riskLevel: RiskLevel
  isSystem: boolean
  allocationCount: number
  totalPercent: number
}

export interface ModelPortfolioDetail {
  id: number
  name: string
  description: string | null
  riskLevel: RiskLevel
  isSystem: boolean
  allocations: ModelAllocation[]
}

export interface ModelPortfoliosListResponse {
  models: ModelPortfolioSummary[]
}

// ========== Request Types ==========

export interface CreateModelPortfolioRequest {
  name: string
  description?: string
  riskLevel: RiskLevel
  allocations: ModelAllocationInput[]
}

export interface UpdateModelPortfolioRequest {
  name?: string
  description?: string
  riskLevel?: RiskLevel
  allocations?: ModelAllocationInput[]
}

export interface ModelAllocationInput {
  symbol: string
  targetPercent: number
  assetClass?: string
}

// ========== Apply to Accounts ==========

export interface ApplyToAccountsRequest {
  connectionIds: number[]
}

// ========== Rebalance Progress ==========

export interface RebalanceProgressEntry {
  symbol: string
  securityName: string | null
  targetPercent: number
  actualPercent: number
  isNonModel: boolean
}

export interface RebalanceProgressResponse {
  connectionId: number
  modelName: string
  accuracy: number
  entries: RebalanceProgressEntry[]
}

// ========== Pending Orders ==========

export interface PendingOrder {
  action: 'BUY' | 'SELL'
  symbol: string
  securityName: string | null
  units: number
  price: number
  amount: number
  currency: string
  accountName: string
  targetPercent: number | null
  targetValue: number | null
  cashInsufficient: boolean
}

export interface PendingOrdersResponse {
  connectionId: number
  orders: PendingOrder[]
  totalAmount: number
  cashRemaining: number
  cashWarning: string | null
  totalSellAmount: number
  totalBuyAmount: number
}

// ========== Model Analysis ==========

export interface ExposureEntry {
  name: string
  percentage: number
}

export interface ModelAnalysisResponse {
  modelId: number
  sectorExposure: ExposureEntry[]
  geographyExposure: ExposureEntry[]
  riskScore: number
  riskLevel: string
  holdings: ModelAllocation[]
}
