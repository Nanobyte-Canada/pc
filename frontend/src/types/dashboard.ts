// ========== Dashboard Preferences ==========

export interface WidgetPreference {
  key: string
  visible: boolean
  sortOrder: number
  columnSpan: number
}

export interface DashboardPreferencesResponse {
  widgets: WidgetPreference[]
}

export interface UpdateDashboardPreferencesRequest {
  widgets: WidgetPreference[]
}

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
}

export interface DividendCalendarResponse {
  month: string
  totalDividends: number
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

// ========== Widget Registry Types ==========

export type WidgetCategory = 'CATEGORY_1' | 'CATEGORY_2' | 'ALWAYS_VISIBLE'

export type WidgetKey =
  | 'PORTFOLIO_VALUE'
  | 'AVAILABLE_CASH'
  | 'BUYING_POWER'
  | 'RISK_PROFILE'
  | 'SECTOR_EXPOSURE'
  | 'GEOGRAPHY_EXPOSURE'
  | 'OPEN_ORDERS'
  | 'FEES_COMMISSION'
  | 'DIVIDEND_CALENDAR'
  | 'POSITIONS_TABLE'
  | 'HOLDINGS_TABLE'
  | 'CONNECTED_ACCOUNTS'
  | 'REBALANCING_PROGRESS'
  | 'PENDING_ORDERS'
  | 'ACCOUNT_SUMMARY'
  | 'ORDERS'
  | 'FEES_AND_DIVIDENDS'
  | 'POSITIONS_HOLDINGS'
  | 'PORTFOLIO_SUMMARY'

export interface WidgetDefinition {
  key: WidgetKey
  title: string
  defaultVisible: boolean
  defaultColumnSpan: number
  defaultSortOrder: number
}
