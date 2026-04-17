import { lazy } from 'react'
import type { WidgetCategory, WidgetDefinition, WidgetKey } from '@/types/dashboard'

// Lazy-loaded widget components
const PortfolioValueWidget = lazy(() => import('./widgets/PortfolioValueWidget'))
const AvailableCashWidget = lazy(() => import('./widgets/AvailableCashWidget'))
const BuyingPowerWidget = lazy(() => import('./widgets/BuyingPowerWidget'))
const RiskProfileWidget = lazy(() => import('./widgets/RiskProfileWidget'))
const SectorExposureWidget = lazy(() => import('./widgets/SectorExposureWidget'))
const GeographyExposureWidget = lazy(() => import('./widgets/GeographyExposureWidget'))
const OpenOrdersWidget = lazy(() => import('./widgets/OpenOrdersWidget'))
const FeesCommissionWidget = lazy(() => import('./widgets/FeesCommissionWidget'))
const DividendCalendarWidget = lazy(() => import('./widgets/DividendCalendarWidget'))
const PositionsTableWidget = lazy(() => import('./widgets/PositionsTableWidget'))
const HoldingsTableWidget = lazy(() => import('./widgets/HoldingsTableWidget'))
const ConnectedAccountsWidget = lazy(() => import('./widgets/ConnectedAccountsWidget'))
const RebalancingProgressWidget = lazy(() => import('./widgets/RebalancingProgressWidget'))
const PendingOrdersWidget = lazy(() => import('./widgets/PendingOrdersWidget'))
const AccountSummaryWidget = lazy(() => import('./widgets/AccountSummaryWidget'))
const OrdersWidget = lazy(() => import('./widgets/OrdersWidget'))
const FeesAndDividendsWidget = lazy(() => import('./widgets/FeesAndDividendsWidget'))
const PositionsHoldingsWidget = lazy(() =>
  import('./PositionsHoldingsTabs').then(m => ({ default: m.PositionsHoldingsTabs }))
) as React.LazyExoticComponent<React.ComponentType<{ connectionId?: number }>>
const PortfolioSummaryWidget = lazy(() => import('./widgets/PortfolioSummaryWidget'))
const IrrWidget = lazy(() => import('./widgets/IrrWidget'))

export interface WidgetRegistryEntry extends WidgetDefinition {
  category: WidgetCategory
  component: React.LazyExoticComponent<React.ComponentType<{ connectionId?: number }>>
}

export const WIDGET_REGISTRY: Record<WidgetKey, WidgetRegistryEntry> = {
  PORTFOLIO_VALUE: { key: 'PORTFOLIO_VALUE', title: 'Portfolio Value', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 0, category: 'CATEGORY_1', component: PortfolioValueWidget },
  AVAILABLE_CASH: { key: 'AVAILABLE_CASH', title: 'Available Cash', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 1, category: 'CATEGORY_1', component: AvailableCashWidget },
  BUYING_POWER: { key: 'BUYING_POWER', title: 'Buying Power', defaultVisible: false, defaultColumnSpan: 1, defaultSortOrder: 2, category: 'CATEGORY_1', component: BuyingPowerWidget },
  RISK_PROFILE: { key: 'RISK_PROFILE', title: 'Risk Profile', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 3, category: 'CATEGORY_1', component: RiskProfileWidget },
  SECTOR_EXPOSURE: { key: 'SECTOR_EXPOSURE', title: 'Sector & Industry Exposure', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 4, category: 'CATEGORY_1', component: SectorExposureWidget },
  GEOGRAPHY_EXPOSURE: { key: 'GEOGRAPHY_EXPOSURE', title: 'Geographic Exposure', defaultVisible: false, defaultColumnSpan: 1, defaultSortOrder: 5, category: 'CATEGORY_1', component: GeographyExposureWidget },
  REBALANCING_PROGRESS: { key: 'REBALANCING_PROGRESS', title: 'Rebalancing Progress', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 6, category: 'CATEGORY_1', component: RebalancingProgressWidget },
  PENDING_ORDERS: { key: 'PENDING_ORDERS', title: 'Pending Orders', defaultVisible: true, defaultColumnSpan: 2, defaultSortOrder: 7, category: 'CATEGORY_1', component: PendingOrdersWidget },
  OPEN_ORDERS: { key: 'OPEN_ORDERS', title: 'Open Orders', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 8, category: 'CATEGORY_2', component: OpenOrdersWidget },
  FEES_COMMISSION: { key: 'FEES_COMMISSION', title: 'Fees & Commission', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 9, category: 'CATEGORY_2', component: FeesCommissionWidget },
  DIVIDEND_CALENDAR: { key: 'DIVIDEND_CALENDAR', title: 'Dividend Calendar', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 10, category: 'CATEGORY_2', component: DividendCalendarWidget },
  CONNECTED_ACCOUNTS: { key: 'CONNECTED_ACCOUNTS', title: 'Connected Accounts', defaultVisible: true, defaultColumnSpan: 2, defaultSortOrder: 9, category: 'ALWAYS_VISIBLE', component: ConnectedAccountsWidget },
  POSITIONS_TABLE: { key: 'POSITIONS_TABLE', title: 'Positions', defaultVisible: true, defaultColumnSpan: 2, defaultSortOrder: 10, category: 'ALWAYS_VISIBLE', component: PositionsTableWidget },
  HOLDINGS_TABLE: { key: 'HOLDINGS_TABLE', title: 'Holdings', defaultVisible: false, defaultColumnSpan: 2, defaultSortOrder: 11, category: 'CATEGORY_1', component: HoldingsTableWidget },
  ACCOUNT_SUMMARY: { key: 'ACCOUNT_SUMMARY', title: 'Account Summary', defaultVisible: true, defaultColumnSpan: 4, defaultSortOrder: 0, category: 'CATEGORY_1', component: AccountSummaryWidget },
  ORDERS: { key: 'ORDERS', title: 'Orders', defaultVisible: true, defaultColumnSpan: 2, defaultSortOrder: 7, category: 'CATEGORY_1', component: OrdersWidget },
  FEES_AND_DIVIDENDS: { key: 'FEES_AND_DIVIDENDS', title: 'Fees & Dividends', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 5.5, category: 'CATEGORY_1', component: FeesAndDividendsWidget },
  POSITIONS_HOLDINGS: { key: 'POSITIONS_HOLDINGS', title: 'Positions & Holdings', defaultVisible: true, defaultColumnSpan: 4, defaultSortOrder: 10, category: 'ALWAYS_VISIBLE', component: PositionsHoldingsWidget },
  PORTFOLIO_SUMMARY: { key: 'PORTFOLIO_SUMMARY', title: 'Portfolio Summary', defaultVisible: true, defaultColumnSpan: 4, defaultSortOrder: -1, category: 'ALWAYS_VISIBLE', component: PortfolioSummaryWidget },
  IRR: { key: 'IRR', title: 'Returns (IRR)', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 5, category: 'CATEGORY_1', component: IrrWidget },
}

export const ZONE_A_WIDGETS: WidgetKey[] = Object.values(WIDGET_REGISTRY)
  .filter(w => w.category === 'CATEGORY_1')
  .map(w => w.key)

export const ZONE_B_WIDGETS: WidgetKey[] = Object.values(WIDGET_REGISTRY)
  .filter(w => w.category === 'CATEGORY_2')
  .map(w => w.key)

export const CONFIGURABLE_WIDGETS: WidgetKey[] = [
  ...ZONE_A_WIDGETS,
  ...ZONE_B_WIDGETS,
]

export const DEFAULT_WIDGET_ORDER: WidgetKey[] = Object.values(WIDGET_REGISTRY)
  .sort((a, b) => a.defaultSortOrder - b.defaultSortOrder)
  .map(w => w.key)
