// ========== Dashboard Summary ==========

export interface PortfolioValueData {
  totalValue: number
  investmentValue: number
  cashValue: number
  totalChange: number | null
  totalChangePercent: number | null
  currency: string
}

export interface PositionsSummaryData {
  stocks: number
  etfs: number
  mutualFunds: number
  options: number
  bonds: number
  cash: number
  other: number
  total: number
}

export interface HoldingsCountData {
  directStocks: number
  lookThroughStocks: number
  totalUniqueHoldings: number
  etfsDecomposed: number
  mutualFundsDecomposed: number
  coveragePercent: number
}

export interface DashboardSummaryResponse {
  portfolioValue: PortfolioValueData
  positionsSummary: PositionsSummaryData
  holdingsCount: HoldingsCountData
}

// ========== Cash & Buying Power ==========

export interface CurrencyAmount {
  currency: string
  amount: number
}

export interface DashboardCashResponse {
  availableCash: CurrencyAmount[]
  buyingPower: CurrencyAmount[]
  totalCashCAD: number
  totalBuyingPowerCAD?: number
}

// ========== Sector Exposure ==========

export interface IndustryGroupExposure {
  code: string
  name: string
  weight: number
}

export interface SectorExposure {
  sectorCode: string
  sectorName: string
  weight: number
  industryGroups: IndustryGroupExposure[]
}

export interface SectorExposureResponse {
  sectors: SectorExposure[]
  coveragePercent: number
  unmappedWeight: number
}

// ========== Geography Exposure ==========

export interface CountryExposure {
  code: string
  name: string
  weight: number
}

export interface RegionExposure {
  name: string
  weight: number
  countries: CountryExposure[]
}

export interface GeographyExposureResponse {
  regions: RegionExposure[]
  coveragePercent: number
  unmappedWeight: number
}

// ========== Risk Profile ==========

export interface RiskFactors {
  concentrationHHI: number
  top10Concentration: number
  sectorConcentrationHHI: number
  geographicConcentration: number
  assetTypeDistribution: Record<string, number>
}

export interface RiskProfileResponse {
  riskScore: number
  riskLevel: string
  factors: RiskFactors
}

// ========== Open Orders ==========

export interface OpenOrder {
  id: number
  symbol: string
  action: string
  requestedUnits: number
  requestedPrice: number | null
  limitPrice: number | null
  status: string
  orderType: string
  accountName: string | null
  createdAt: string
}

export interface OpenOrdersResponse {
  orders: OpenOrder[]
  totalCount: number
}

// ========== Fees & Commission ==========

export interface MonthlyFee {
  month: string
  fees: number
  commissions: number
}

export interface FeesTotal {
  totalFees: number
  totalCommissions: number
  totalManagementExpense: number
  total: number
}

export interface FeesResponse {
  last12Months: FeesTotal
  monthlyBreakdown: MonthlyFee[]
  managementExpensePerMonth: number
}

// ========== Dividend Calendar ==========

export interface DividendEntry {
  date: string
  symbol: string | null
  amount: number
  currency: string
  accountName: string | null
  type: string
}

export interface DividendCalendarResponse {
  month: string
  totalDividends: number
  totalReinvestments: number
  entries: DividendEntry[]
}

// ========== Holdings Table ==========

export interface HoldingSource {
  type: string
  instrumentSymbol: string | null
  contribution: number
}

export interface LookThroughHolding {
  symbol: string
  name: string | null
  effectiveWeight: number
  sector: string | null
  industryGroup: string | null
  country: string | null
  sources: HoldingSource[]
}

export interface HoldingsTableResponse {
  holdings: LookThroughHolding[]
  totalCount: number
  coveragePercent: number
}

// ========== Connected Accounts ==========

export interface LinkedGroupInfo {
  id: number
  name: string
  accuracy: number
}

export interface DashboardAccount {
  connectionId: number
  brokerName: string
  brokerLogoUrl: string | null
  accountName: string | null
  accountType: string | null
  accountNumber: string | null
  status: string
  totalValue: number | null
  investmentValue: number | null
  cash: number | null
  buyingPower: number | null
  positionsCount: number
  lastFetchedAt: string | null
  linkedGroup: LinkedGroupInfo | null
  modelPortfolioId: number | null
  modelPortfolioName: string | null
  needsSetup: boolean
}

export interface DashboardAccountsResponse {
  accounts: DashboardAccount[]
}

// ========== Refresh ==========

export interface RefreshAllResponse {
  connectionsRefreshed: number
  message: string
}

// ========== Performance Metrics ==========

export interface AccountIrr {
  connectionId: number
  brokerName: string | null
  accountName: string | null
  irr: number | null
  totalReturn: number | null
  totalReturnPct: number | null
  dividendYield: number | null
  startDate: string | null
  endDate: string | null
}

export interface DashboardIrrResponse {
  portfolioIrr: number | null
  portfolioTotalReturn: number | null
  portfolioTotalReturnPct: number | null
  portfolioDividendYield: number | null
  accounts: AccountIrr[]
}

