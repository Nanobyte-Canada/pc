# Frontend Map -- AI Agent Reference

> React 18.3.1 + TypeScript 5.6.3 + Vite 5.4.9
> Source root: `frontend/src/`
> Path alias: `@/` maps to `./src/` (configured in vite.config.ts and tsconfig.json)
> Cross-reference: [api-endpoints.md](./api-endpoints.md) | [backend-services.md](./backend-services.md)

---

## Table of Contents

1. [Components](#components)
2. [Pages](#pages)
3. [Routing](#routing)
4. [Hooks](#hooks)
5. [Services](#services)
6. [Stores](#stores)
7. [Types](#types)
8. [CSS Files](#css-files)
9. [Test Files](#test-files)
10. [Configuration](#configuration)

---

## Components

### analytics/ (6 files)

| File | Component | Description |
|------|-----------|-------------|
| `SummaryCards.tsx` | SummaryCards | Displays portfolio analysis summary cards (positions count, stock/ETF counts) |
| `SectorChart.tsx` | SectorChart | Pie/donut chart showing GICS sector exposure breakdown |
| `GeographyChart.tsx` | GeographyChart | Chart showing geographic/regional exposure distribution |
| `TopHoldingsGrid.tsx` | TopHoldingsGrid | AG Grid table of top underlying holdings with effective weights |
| `RiskProfileChart.tsx` | RiskProfileChart | Chart displaying risk metrics (HHI, concentration, volatility) |
| `SummaryCards.css` | -- | Styles for SummaryCards |
| `ChartStyles.css` | -- | Shared chart styles for analytics charts |
| `TopHoldingsGrid.css` | -- | Styles for TopHoldingsGrid |

### auth/ (2 files)

| File | Component | Description |
|------|-----------|-------------|
| `ProtectedRoute.tsx` | ProtectedRoute | Route guard that checks authentication and optional role requirements |
| `SessionTimeoutWarning.tsx` | SessionTimeoutWarning | Modal warning shown before session timeout, offers "Stay logged in" or "Logout" |

### broker/ (8 files)

| File | Component | Description |
|------|-----------|-------------|
| `BrokerCard.tsx` | BrokerCard | Card displaying a broker with brand-colored icon (Q/W/IB), name, auth badges, and connection state pill. Whole card is clickable to connect. |
| `BrokerConnectionCard.tsx` | BrokerConnectionCard | Horizontal card for a connected account: brand icon, account type, masked number, status dot, sync time, value, Sync + More (3-dot) menu |
| `ConnectionStatus.tsx` | ConnectionStatus | Badge-style status indicator for broker connection status |
| `ConnectBrokerDialog.tsx` | ConnectBrokerDialog | Dialog with manual token entry form for connecting to brokers (e.g., Questrade refresh token). Replaces the old SnapTrade redirect flow. |
| `CashBalanceCards.tsx` | CashBalanceCards | Cash balance cards with dual-currency breakdown (total in C$ at top, divider, C$/US$ component rows) |
| `AccountActivitiesGrid.tsx` | AccountActivitiesGrid | AG Grid table showing broker account activities (trades, dividends, etc.) |
| `BrokerageMatrix.tsx` | BrokerageMatrix | Grid showing available brokerages with features (trading, fractional, etc.) |
| `BrokerCard.css` | -- | Styles for BrokerCard |
| `BrokerConnectionCard.css` | -- | Styles for BrokerConnectionCard |
| `CashBalanceCards.css` | -- | Styles for CashBalanceCards |
| `AccountActivitiesGrid.css` | -- | Styles for AccountActivitiesGrid |
| `BrokerageMatrix.css` | -- | Styles for BrokerageMatrix |

### dashboard/ (6 container files)

| File | Component | Description |
|------|-----------|-------------|
| `DashboardGrid.tsx` | DashboardGrid | Main dashboard layout grid that renders visible widgets based on preferences |
| `DashboardEditMode.tsx` | DashboardEditMode | UI for toggling widget visibility and reordering widgets |
| `AccountTabs.tsx` | AccountTabs | Tab selector for filtering dashboard by connected account |
| `PositionsHoldingsTabs.tsx` | PositionsHoldingsTabs | Tabbed view switching between positions table and look-through holdings |
| `WidgetWrapper.tsx` | WidgetWrapper | Wrapper component providing consistent card styling and loading states for widgets |
| `DashboardGrid.css` | -- | Styles for DashboardGrid |
| `DashboardEditMode.css` | -- | Styles for DashboardEditMode |
| `AccountTabs.css` | -- | Styles for AccountTabs |
| `PositionsHoldingsTabs.css` | -- | Styles for PositionsHoldingsTabs |
| `WidgetWrapper.css` | -- | Styles for WidgetWrapper |

### dashboard/widgets/ (18 widget files)

| File | Component | Description |
|------|-----------|-------------|
| `PortfolioValueWidget.tsx` | PortfolioValueWidget | Total portfolio value with investment vs cash breakdown and change indicator |
| `PortfolioSummaryWidget.tsx` | PortfolioSummaryWidget | Combined portfolio summary card (value + account info) |
| `AvailableCashWidget.tsx` | AvailableCashWidget | Available cash balances broken down by currency |
| `BuyingPowerWidget.tsx` | BuyingPowerWidget | Buying power amounts by currency |
| `RiskProfileWidget.tsx` | RiskProfileWidget | Risk score gauge with risk factors breakdown |
| `SectorExposureWidget.tsx` | SectorExposureWidget | Pie chart of GICS sector exposure via look-through analysis |
| `GeographyExposureWidget.tsx` | GeographyExposureWidget | Pie chart of geographic exposure by region/country |
| `OpenOrdersWidget.tsx` | OpenOrdersWidget | Table of currently open/pending trade orders |
| `OrdersWidget.tsx` | OrdersWidget | Combined orders widget (open + recent) |
| `FeesCommissionWidget.tsx` | FeesCommissionWidget | Fees and commissions chart over last 12 months |
| `FeesAndDividendsWidget.tsx` | FeesAndDividendsWidget | Combined fees and dividends summary widget |
| `DividendCalendarWidget.tsx` | DividendCalendarWidget | Monthly dividend calendar with per-symbol breakdown |
| `PositionsTableWidget.tsx` | PositionsTableWidget | AG Grid table of all positions with P&L data |
| `HoldingsTableWidget.tsx` | HoldingsTableWidget | AG Grid table of look-through holdings with sources |
| `ConnectedAccountsWidget.tsx` | ConnectedAccountsWidget | List of connected broker accounts with status and value |
| `AccountSummaryWidget.tsx` | AccountSummaryWidget | Summary widget for individual account detail pages |
| `RebalancingProgressWidget.tsx` | RebalancingProgressWidget | Progress bars showing model vs actual allocation per symbol |
| `PendingOrdersWidget.tsx` | PendingOrdersWidget | Table of pending rebalance orders with cash impact |
| `PortfolioValueWidget.css` | -- | Styles for PortfolioValueWidget |
| `AvailableCashWidget.css` | -- | Styles for AvailableCashWidget |
| `BuyingPowerWidget.css` | -- | Styles for BuyingPowerWidget |
| `RiskProfileWidget.css` | -- | Styles for RiskProfileWidget |
| `SectorExposureWidget.css` | -- | Styles for SectorExposureWidget |
| `GeographyExposureWidget.css` | -- | Styles for GeographyExposureWidget |
| `OpenOrdersWidget.css` | -- | Styles for OpenOrdersWidget |
| `OrdersWidget.css` | -- | Styles for OrdersWidget |
| `FeesCommissionWidget.css` | -- | Styles for FeesCommissionWidget |
| `FeesAndDividendsWidget.css` | -- | Styles for FeesAndDividendsWidget |
| `DividendCalendarWidget.css` | -- | Styles for DividendCalendarWidget |
| `PositionsTableWidget.css` | -- | Styles for PositionsTableWidget |
| `HoldingsTableWidget.css` | -- | Styles for HoldingsTableWidget |
| `ConnectedAccountsWidget.css` | -- | Styles for ConnectedAccountsWidget |
| `AccountSummaryWidget.css` | -- | Styles for AccountSummaryWidget |
| `RebalancingProgressWidget.css` | -- | Styles for RebalancingProgressWidget |
| `PendingOrdersWidget.css` | -- | Styles for PendingOrdersWidget |
| `PortfolioSummaryWidget.css` | -- | Styles for PortfolioSummaryWidget |

### instruments/ (2 files + detail/ subdirectory)

| File | Component | Description |
|------|-----------|-------------|
| `InstrumentSearchAutocomplete.tsx` | InstrumentSearchAutocomplete | Autocomplete search input for stocks and ETFs, used in portfolio builder |
| `InstrumentTabs.tsx` | InstrumentTabs | Tabbed detail view for stock/ETF fundamentals sections |
| `InstrumentSearchAutocomplete.css` | -- | Styles for InstrumentSearchAutocomplete |
| `InstrumentTabs.css` | -- | Styles for InstrumentTabs |
| `detail/StockDetailSections.tsx` | StockDetailSections | Stub: type-specific sections for stock instruments (Overview, Financials, Valuation, Technicals, Dividends, Ownership, Earnings) |
| `detail/EtfDetailSections.tsx` | EtfDetailSections | Stub: type-specific sections for ETF instruments (Overview, Performance, Holdings, Sectors, Regions, Valuation) |
| `detail/MutualFundDetailSections.tsx` | MutualFundDetailSections | Stub: type-specific sections for mutual fund instruments (Overview, Performance, Holdings, Sectors, Regions, Valuation) |
| `detail/BasicDetailSections.tsx` | BasicDetailSections | Stub: basic overview section for sparse instrument types (Preferred Stock, Index, Bond) |

### layout/ (5 files)

| File | Component | Description |
|------|-----------|-------------|
| `AppLayout.tsx` | AppLayout | Main authenticated layout with sidebar, header, and Outlet for child routes |
| `AppSidebar.tsx` | AppSidebar | Collapsible sidebar navigation with route links and icons |
| `MobileHeader.tsx` | MobileHeader | Mobile-responsive header with hamburger menu |
| `ThemeToggle.tsx` | ThemeToggle | Dark/light theme toggle button |
| `NotificationBell.tsx` | NotificationBell | Notification bell icon with unread count badge and dropdown |
| `AppLayout.css` | -- | Styles for AppLayout |
| `AppSidebar.css` | -- | Styles for AppSidebar |
| `MobileHeader.css` | -- | Styles for MobileHeader |
| `ThemeToggle.css` | -- | Styles for ThemeToggle |
| `NotificationBell.css` | -- | Styles for NotificationBell |

### notifications/ (1 file)

| File | Component | Description |
|------|-----------|-------------|
| `NotificationPreferencesForm.tsx` | NotificationPreferencesForm | Form for managing notification preferences (drift alerts, order alerts, etc.) |

### performance/ (6 files)

| File | Component | Description |
|------|-----------|-------------|
| `PerformanceTab.tsx` | PerformanceTab | Main performance tab container with summary and charts |
| `PeriodSelector.tsx` | PeriodSelector | Period selector buttons (1M, 3M, 6M, YTD, 1Y, ALL) |
| `BenchmarkSelector.tsx` | BenchmarkSelector | Dropdown to select benchmark for performance comparison |
| `CumulativeReturnChart.tsx` | CumulativeReturnChart | AG Charts line chart showing cumulative portfolio returns vs benchmark |
| `DrawdownChart.tsx` | DrawdownChart | AG Charts area chart showing portfolio drawdown periods |
| `RiskMetricsCards.tsx` | RiskMetricsCards | Cards displaying risk metrics (volatility, Sharpe, Sortino, max drawdown) |

### portfolios/ (4 files)

| File | Component | Description |
|------|-----------|-------------|
| `ModelPortfolioCard.tsx` | ModelPortfolioCard | Card showing model portfolio summary with allocations and risk level |
| `CustomPortfolioBuilder.tsx` | CustomPortfolioBuilder | Form for creating/editing custom model portfolios with allocation inputs |
| `ApplyToAccountModal.tsx` | ApplyToAccountModal | Modal dialog for applying a model portfolio to selected broker accounts |
| `ModelAnalysisPanel.tsx` | ModelAnalysisPanel | Panel showing sector/geography exposure analysis for a model portfolio |
| `ModelPortfolioCard.css` | -- | Styles for ModelPortfolioCard |
| `CustomPortfolioBuilder.css` | -- | Styles for CustomPortfolioBuilder |
| `ApplyToAccountModal.css` | -- | Styles for ApplyToAccountModal |
| `ModelAnalysisPanel.css` | -- | Styles for ModelAnalysisPanel |

### reporting/ (6 files)

| File | Component | Description |
|------|-----------|-------------|
| `KpiCard.tsx` | KpiCard | Generic KPI card used in reporting dashboard |
| `DateRangeSelector.tsx` | DateRangeSelector | Date range picker for filtering report data |
| `ContributionsChart.tsx` | ContributionsChart | Bar chart of contributions/withdrawals over time |
| `TotalValueChart.tsx` | TotalValueChart | Line chart of total portfolio value history |
| `DividendHistoryChart.tsx` | DividendHistoryChart | Bar chart of dividend income by period with symbol breakdown |
| `TotalDividendsChart.tsx` | TotalDividendsChart | Pie/bar chart of total dividends by symbol |
| `ActivityTable.tsx` | ActivityTable | AG Grid table of all account activities with filtering |

### screener/ (2 files)

| File | Component | Description |
|------|-----------|-------------|
| `ScreenerFilters.tsx` | ScreenerFilters | Filter panel for stock/ETF screener (sector, country, issuer, etc.) |
| `ScreenerGrid.tsx` | ScreenerGrid | AG Grid table for screener results with sorting and pagination |
| `ScreenerFilters.css` | -- | Styles for ScreenerFilters |
| `ScreenerGrid.css` | -- | Styles for ScreenerGrid |

### ui/ (13 files)

| File | Component | Description |
|------|-----------|-------------|
| `button.tsx` | Button | Reusable button component with variant and size props |
| `badge.tsx` | Badge | Status/label badge component with variant styling |
| `card.tsx` | Card, CardHeader, CardTitle, CardContent, CardFooter | Card container components |
| `dialog.tsx` | Dialog, DialogTrigger, DialogContent, DialogHeader, DialogTitle, DialogDescription | Modal dialog components |
| `sheet.tsx` | Sheet, SheetTrigger, SheetContent, SheetHeader, SheetTitle, SheetDescription | Slide-out panel components |
| `skeleton.tsx` | Skeleton | Loading skeleton placeholder component |
| `switch.tsx` | Switch | Toggle switch input component |
| `separator.tsx` | Separator | Horizontal/vertical separator line |
| `tooltip.tsx` | Tooltip, TooltipTrigger, TooltipContent | Hover tooltip components |
| `Pagination.tsx` | Pagination | Page navigation component with prev/next and page numbers |
| `ErrorBoundary.tsx` | ErrorBoundary | React error boundary with fallback UI |
| `button.css` | -- | Styles for Button |
| `badge.css` | -- | Styles for Badge |
| `card.css` | -- | Styles for Card |
| `dialog.css` | -- | Styles for Dialog |
| `sheet.css` | -- | Styles for Sheet |
| `skeleton.css` | -- | Styles for Skeleton |
| `switch.css` | -- | Styles for Switch |
| `separator.css` | -- | Styles for Separator |
| `tooltip.css` | -- | Styles for Tooltip |
| `Pagination.css` | -- | Styles for Pagination |
| `ErrorBoundary.css` | -- | Styles for ErrorBoundary |
| `toast.tsx` | ToastContainer, ToastItem | Toast notification system for user-visible messages (success/error/warning/info) |
| `toast.css` | -- | Styles for Toast |

---

## Pages

| File | Component | Route | Description |
|------|-----------|-------|-------------|
| `DashboardPage.tsx` | DashboardPage | `/` (index) | Main dashboard with widget grid, account tabs, and edit mode |
| `PortfolioPage.tsx` | PortfolioPage (default export) | `/portfolios` | Model portfolios management page |
| `ScreenerPage.tsx` | ScreenerPage | `/screener/:type` | Unified instrument screener with filters, grid, and sidebar for all instrument types (STOCK, ETF, MUTUAL_FUND, PREFERRED_STOCK, INDEX, BOND) |
| `InstrumentDetailPage.tsx` | InstrumentDetailPage | `/instruments/:type/:ticker` | Unified instrument detail page with hero metrics, section nav, and type-specific sections |
| `AnalyticsPage.tsx` | AnalyticsPage | `/analytics` | Portfolio analysis builder with sector, geography, and risk charts |
| `BrokerConnectionsPage.tsx` | BrokerConnectionsPage | `/brokers/connections` | Broker connection management (dialog-based connect flow via ConnectBrokerDialog, disconnect, status) |
| `BrokerPositionsPage.tsx` | BrokerPositionsPage | `/brokers/positions` | Aggregated positions view across all broker accounts |
| `PositionDetailsPage.tsx` | PositionDetailsPage | `/brokers/positions/:connectionId` | Detailed positions for a single broker connection |
| `AccountDetailPage.tsx` | AccountDetailPage | `/brokers/accounts/:connectionId` | Individual account detail with breadcrumb nav ("Accounts > [Type]") and DashboardGrid in ACCOUNT context |
| `ReportingPage.tsx` | ReportingPage | `/brokers/reporting` | Reporting dashboard with KPIs, charts, and activity table |
| `ProfilePage.tsx` | ProfilePage | `/profile` | User profile management (name, avatar, password, linked identities) |
| `admin/AdminPage.tsx` | AdminPage | `/admin` (ADMIN role required) | Admin panel rewired to ingestion-service (port 8081). Enhanced stats with per-instrument-type breakdowns, async triggers with live progress polling, auto-refresh every 10 seconds |
| `auth/LoginPage.tsx` | LoginPage | `/login` (public) | Login form with Google OAuth option |
| `auth/SignupPage.tsx` | SignupPage | `/signup` (public) | Registration form |
| `auth/ForgotPasswordPage.tsx` | ForgotPasswordPage | `/forgot-password` (public) | Password reset request form |
| `auth/ResetPasswordPage.tsx` | ResetPasswordPage | `/reset-password` (public) | Password reset form (with token from email) |
| `auth/VerifyEmailPage.tsx` | VerifyEmailPage | `/verify-email` (public) | Email verification landing page |
| `UnauthorizedPage.tsx` | UnauthorizedPage | `/unauthorized` | Unauthorized access page |

---

## Routing

Defined in `App.tsx`. All pages are lazy-loaded with `React.lazy()` and wrapped in `<Suspense>`.

```
/login                              -- LoginPage (public)
/signup                             -- SignupPage (public)
/forgot-password                    -- ForgotPasswordPage (public)
/reset-password                     -- ResetPasswordPage (public)
/verify-email                       -- VerifyEmailPage (public)
/unauthorized                       -- UnauthorizedPage (public)

/ (ProtectedRoute + AppLayout)
  /                                 -- DashboardPage (index)
  /portfolios                       -- PortfolioPage
  /screener/:type                   -- ScreenerPage
  /instruments/:type/:ticker        -- InstrumentDetailPage
  /analytics                        -- AnalyticsPage
  /brokers/connections              -- BrokerConnectionsPage
  /brokers/positions                -- BrokerPositionsPage
  /brokers/positions/:connectionId  -- PositionDetailsPage
  /brokers/accounts/:connectionId   -- AccountDetailPage
  /brokers/reporting                -- ReportingPage
  /options                          -- OptionsPage
  /wheel                            -- WheelPage
  /profile                          -- ProfilePage
  /admin                            -- AdminPage (ProtectedRoute with ADMIN role)

/*                                  -- Redirect to /
```

---

## Hooks

### useBrokerConnections.ts

| Hook | Type | Query Key | Service Call | Stale Time | Refetch Interval |
|------|------|-----------|-------------|------------|-----------------|
| `useAvailableBrokers` | Query | `['brokers', 'available']` | `getAvailableBrokers()` | 5 min | -- |
| `useBrokerConnections` | Query | `['brokers', 'connections']` | `getUserConnections()` | 30 sec | -- |
| `useConnectionPositions(id)` | Query | `['brokers', 'positions', id]` | `getConnectionPositions(id)` | 1 min | -- |
| `useAggregatedPositions` | Query | `['brokers', 'positions', 'aggregated']` | `getAggregatedPositions()` | 1 min | -- |
| `useConnectionActivities(id, params)` | Query | `['brokers', 'activities', id, params]` | `getConnectionActivities(id, params)` | 1 min | -- |
| `useBalanceHistory(id, days)` | Query | `['brokers', 'balance-history', id, days]` | `getBalanceHistory(id, days)` | 1 min | -- |
| `useConnectBroker` | Mutation | -- | `connectBroker(request)` | -- | -- |
| `useDisconnectBroker` | Mutation | -- | `disconnectBroker(authId)` | -- | -- |
| `useTriggerPositionFetch` | Mutation | -- | `triggerPositionFetch(id)` | -- | -- |
| `useSyncActivities` | Mutation | -- | `syncConnectionActivities(id)` | -- | -- |
| `useSyncConnections` | Mutation | -- | `syncConnections()` | -- | -- |

### useDashboardPreferences.ts

| Hook | Type | Query Key | Service Call | Stale Time |
|------|------|-----------|-------------|------------|
| `useDashboardPreferences(contextType?, contextId?)` | Query | `['dashboard', 'preferences', contextType, contextId]` | `getDashboardPreferences(...)` | 5 min |
| `useUpdateDashboardPreferences` | Mutation | -- | `updateDashboardPreferences(...)` | -- |
| `useResetDashboardPreferences` | Mutation | -- | `resetDashboardPreferences(...)` | -- |

### useDashboardWidgets.ts

| Hook | Type | Query Key | Service Call | Stale Time | Refetch Interval |
|------|------|-----------|-------------|------------|-----------------|
| `useDashboardSummary(connId?)` | Query | `['dashboard', 'summary', connId]` | `getDashboardSummary(connId)` | 1 min | 2 min |
| `useDashboardCash(connId?)` | Query | `['dashboard', 'cash', connId]` | `getDashboardCash(connId)` | 1 min | -- |
| `useSectorExposure(connId?)` | Query | `['dashboard', 'sectorExposure', connId]` | `getSectorExposure(connId)` | 5 min | -- |
| `useGeographyExposure(connId?)` | Query | `['dashboard', 'geographyExposure', connId]` | `getGeographyExposure(connId)` | 5 min | -- |
| `useRiskProfile(connId?)` | Query | `['dashboard', 'riskProfile', connId]` | `getRiskProfile(connId)` | 5 min | -- |
| `useOpenOrders` | Query | `['dashboard', 'openOrders']` | `getOpenOrders()` | 30 sec | 1 min |
| `useFees(connId?)` | Query | `['dashboard', 'fees', connId]` | `getFees(connId)` | 5 min | -- |
| `useDividendCalendar(month?, connId?)` | Query | `['dashboard', 'dividendCalendar', month, connId]` | `getDividendCalendar(month, connId)` | 5 min | -- |
| `useDashboardPositions(connId?)` | Query | `['dashboard', 'positions', connId]` | `getDashboardPositions(connId)` | 1 min | 2 min |
| `useDashboardHoldings(connId?)` | Query | `['dashboard', 'holdings', connId]` | `getDashboardHoldings(connId)` | 5 min | -- |
| `useDashboardAccounts` | Query | `['dashboard', 'accounts']` | `getDashboardAccounts()` | 1 min | -- |
| `useRefreshAll` | Mutation | -- | `refreshAll()` | -- | -- |

### useNewScreener.ts

| Hook | Type | Query Key | Service Call | Stale Time |
|------|------|-----------|-------------|------------|
| `useInstrumentScreener(type, filter, page, size, sortField, sortDirection)` | Query | `['screener', type, filter, page, size, sortField, sortDirection]` | `getScreenerInstruments(...)` | 2 min |
| `useInstrumentDetail(type, ticker)` | Query | `['instrument-detail', type, ticker]` | `getInstrumentDetail(type, ticker)` | 5 min |
| `useReferenceValues(type, field)` | Query | `['reference-values', type, field]` | `getReferenceData(type, field)` | 10 min |
| `useTypeCounts()` | Query | `['type-counts']` | `getTypeCounts()` | 5 min |
| `useNewInstrumentSearch(query, types?, limit?)` | Query | `['new-instrument-search', query, types, limit]` | `searchInstruments(query, types, limit)` | 5 min |

`useNewInstrumentSearch` is enabled when `query.length >= 1`. Uses `placeholderData` to keep previous results visible.

### useModelPortfolios.ts

| Hook | Type | Query Key | Service Call | Stale Time |
|------|------|-----------|-------------|------------|
| `useModelPortfolios` | Query | `['model-portfolios']` | `getModelPortfolios()` | default |
| `useModelPortfolio(id)` | Query | `['model-portfolios', id]` | `getModelPortfolio(id)` | default |
| `useModelAnalysis(modelId)` | Query | `['model-analysis', modelId]` | `getModelAnalysis(modelId)` | 5 min |
| `useRebalanceProgress(connId)` | Query | `['rebalance-progress', connId]` | `getRebalanceProgress(connId)` | 1 min |
| `usePendingOrders(connId)` | Query | `['pending-orders', connId]` | `getPendingOrders(connId)` | 1 min |
| `useCreateModelPortfolio` | Mutation | -- | `createModelPortfolio(request)` | -- |
| `useUpdateModelPortfolio` | Mutation | -- | `updateModelPortfolio(id, request)` | -- |
| `useDeleteModelPortfolio` | Mutation | -- | `deleteModelPortfolio(id)` | -- |
| `useApplyModelToAccounts` | Mutation | -- | `applyModelToAccounts(modelId, {connectionIds})` | -- |

### useNotifications.ts

| Hook | Type | Query Key | Service Call | Stale Time | Refetch Interval |
|------|------|-----------|-------------|------------|-----------------|
| `useNotifications(unreadOnly, page)` | Query | `['notifications', 'list', unreadOnly]` | `getNotifications(unreadOnly, page)` | 30 sec | -- |
| `useUnreadCount` | Query | `['notifications', 'unread-count']` | `getUnreadCount()` | 15 sec | 30 sec |
| `useNotificationPreferences` | Query | `['notifications', 'preferences']` | `getNotificationPreferences()` | 1 min | -- |
| `useMarkAsRead` | Mutation | -- | `markAsRead(id)` | -- | -- |
| `useMarkAllAsRead` | Mutation | -- | `markAllAsRead()` | -- | -- |
| `useUpdateNotificationPreferences` | Mutation | -- | `updateNotificationPreferences(request)` | -- | -- |

### usePerformance.ts

| Hook | Type | Query Key | Service Call | Stale Time |
|------|------|-----------|-------------|------------|
| `usePerformanceSummary(groupId, period)` | Query | `['performance', 'summary', groupId, period]` | `getPerformanceSummary(groupId, period)` | 1 min |
| `usePerformanceChart(groupId, period, benchmark?)` | Query | `['performance', 'chart', groupId, period, benchmark]` | `getPerformanceChart(groupId, period, benchmark)` | 1 min |

### usePortfolioAnalysis.ts

| Hook | Type | Service Call |
|------|------|-------------|
| `usePortfolioAnalysis` | Mutation | `analyzePortfolio(request)` |

### useReporting.ts

| Hook | Type | Query Key | Service Call | Stale Time |
|------|------|-----------|-------------|------------|
| `useReportingPerformance(params)` | Query | `['reporting', 'performance', params]` | `getReportingPerformance(params)` | 2 min |
| `useReportingActivities(params)` | Query | `['reporting', 'activities', params]` | `getReportingActivities(params)` | 1 min |

### useTrading.ts

| Hook | Type | Query Key | Service Call | Stale Time | Refetch Interval |
|------|------|-----------|-------------|------------|-----------------|
| `useGroupOrders(groupId)` | Query | `['trading', 'group-orders', groupId]` | `getGroupOrders(groupId)` | 10 sec | 15 sec |
| `useBatchOrders(batchId)` | Query | `['trading', 'batch-orders', batchId]` | `getBatchOrders(batchId)` | -- | 10 sec |
| `useExecuteTrades` | Mutation | -- | `executeTrades(request)` | -- | -- |
| `useExecuteSingleTrade` | Mutation | -- | `executeSingleTrade(groupId, trade)` | -- | -- |
| `useCancelOrder` | Mutation | -- | `cancelOrder(orderId)` | -- | -- |

### useSessionManager.ts

Not a React Query hook. Custom hook managing session lifecycle:
- Tracks user activity (mouse, keyboard, scroll, click, touch)
- Proactively refreshes JWT tokens every 5 minutes if user is active
- Fires `session-expiring` custom event 5 minutes before timeout
- Forces logout after 6 hours of idle time
- Exports `extendSession()` for the session timeout warning modal

### useChartTheme.ts

Not a React Query hook. Returns AG Charts theme config based on current dark/light theme setting.

---

## Services

### api.ts

Base API utility module. All other services depend on this.

| Export | Parameters | Description |
|--------|-----------|-------------|
| `API_URL` | -- | `import.meta.env.VITE_API_URL \|\| ''` |
| `apiFetch(endpoint, options?)` | `endpoint: string, options?: RequestInit` | Authenticated fetch with CSRF, credentials, auto 401 refresh retry. Throws `ApiError` on non-OK responses. |
| `ApiError` (class) | `status: number, code: string, message: string` | Structured error class for API failures with status, code, and message |
| `parseErrorResponse(response)` | `response: Response` | Parses RFC 7807 ProblemDetail responses into `ApiError` instances |
| `getVersion()` | -- | `GET /api/v1/version` -> `VersionResponse` |
| `getHealth()` | -- | `GET /health` -> `HealthResponse` |
| `setCsrfToken(token)` | `token: string \| null` | No-op legacy function |
| `getCsrfToken()` | -- | Reads XSRF-TOKEN from cookie |

### authService.ts

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `signup(request)` | `SignupRequest` | `/auth/signup` | POST |
| `login(request)` | `LoginRequest` | `/auth/login` | POST |
| `logout()` | -- | `/auth/logout` | POST |
| `refreshToken()` | -- | `/auth/refresh` | POST |
| `forgotPassword(request)` | `ForgotPasswordRequest` | `/auth/forgot-password` | POST |
| `resetPassword(request)` | `ResetPasswordRequest` | `/auth/reset-password` | POST |
| `resendVerification(email)` | `email: string` | `/auth/resend-verification` | POST |
| `getMe()` | -- | `/auth/me` | GET |
| `initiateGoogleLogin()` | -- | Redirects to `/auth/google` | -- |
| `updateProfile(data)` | `UpdateProfileData` | `/auth/profile` | PUT |
| `changePassword(data)` | `ChangePasswordData` | `/auth/change-password` | POST |

### brokerService.ts

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `getAvailableBrokers()` | -- | `/api/v1/brokers` | GET |
| `getUserConnections()` | -- | `/api/v1/brokers/connections` | GET |
| `connectBroker(request)` | `ConnectBrokerRequest { brokerType: string, credentials: Record<string, unknown> }` | `/api/v1/brokers/connect` | POST |
| `disconnectBroker(authId)` | `authorizationId: string` | `/api/v1/brokers/connections/{authId}` | DELETE |
| `triggerPositionFetch(id)` | `connectionId: number` | `/api/v1/brokers/connections/{id}/fetch` | POST |
| `getConnectionPositions(id)` | `connectionId: number` | `/api/v1/brokers/connections/{id}/positions` | GET |
| `getAggregatedPositions()` | -- | `/api/v1/brokers/positions` | GET |
| `syncConnections()` | -- | `/api/v1/brokers/connections/sync` | POST |
| `getConnectionActivities(id, params)` | `connectionId, {page, size, startDate, endDate, type}` | `/api/v1/brokers/connections/{id}/activities` | GET |
| `syncConnectionActivities(id)` | `connectionId: number` | `/api/v1/brokers/connections/{id}/sync-activities` | POST |
| `getBalanceHistory(id, days)` | `connectionId, days` | `/api/v1/brokers/connections/{id}/balance-history` | GET |
| `getReportingPerformance(params)` | `{startDate, endDate, accounts, granularity}` | `/api/v1/brokers/reporting/performance` | GET |
| `getReportingActivities(params)` | `{page, size, startDate, endDate, accounts, type}` | `/api/v1/brokers/reporting/activities` | GET |
| `formatCurrency(value, currency)` | -- | Utility: formats number as CAD currency |
| `formatPercent(value)` | -- | Utility: formats number as signed percentage |
| `formatQuantity(value)` | -- | Utility: formats number with up to 4 decimals |
| `getRelativeTime(dateString)` | -- | Utility: converts ISO date to relative time string |

### dashboardWidgetService.ts

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `getDashboardPreferences(contextType?, contextId?)` | -- | `/api/v1/dashboard/preferences` | GET |
| `updateDashboardPreferences(request, contextType?, contextId?)` | `UpdateDashboardPreferencesRequest` | `/api/v1/dashboard/preferences` | PUT |
| `resetDashboardPreferences(contextType?, contextId?)` | -- | `/api/v1/dashboard/preferences/reset` | POST |
| `getDashboardSummary(connectionId?)` | -- | `/api/v1/dashboard/summary` | GET |
| `getDashboardCash(connectionId?)` | -- | `/api/v1/dashboard/cash` | GET |
| `getSectorExposure(connectionId?)` | -- | `/api/v1/dashboard/exposure/sector` | GET |
| `getGeographyExposure(connectionId?)` | -- | `/api/v1/dashboard/exposure/geography` | GET |
| `getRiskProfile(connectionId?)` | -- | `/api/v1/dashboard/risk-profile` | GET |
| `getOpenOrders()` | -- | `/api/v1/dashboard/orders/open` | GET |
| `getFees(connectionId?)` | -- | `/api/v1/dashboard/fees` | GET |
| `getDividendCalendar(month?, connectionId?)` | -- | `/api/v1/dashboard/dividends` | GET |
| `getDashboardPositions(connectionId?)` | -- | `/api/v1/dashboard/positions` | GET |
| `getDashboardHoldings(connectionId?)` | -- | `/api/v1/dashboard/holdings` | GET |
| `getDashboardAccounts()` | -- | `/api/v1/dashboard/accounts` | GET |
| `refreshAll()` | -- | `/api/v1/dashboard/refresh` | POST |

### screenerService.ts

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `getScreenerInstruments(type, filter, page, size, sortField?, sortDirection?)` | `type: string, filter: ScreenerFilter, page, size, sortField?, sortDirection?` | `/api/v1/screener/{type}` | GET |
| `getInstrumentDetail(type, ticker)` | `type: string, ticker: string` | `/api/v1/screener/detail/{type}/{ticker}` | GET |
| `searchInstruments(query, types?, limit?)` | `query: string, types?: string[], limit?: number` | `/api/v1/screener/search` | GET |
| `getReferenceData(type, field)` | `type: string, field: string` | `/api/v1/screener/reference/{type}/{field}` | GET |
| `getTypeCounts()` | -- | `/api/v1/screener/counts` | GET |

### modelPortfolioService.ts

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `getModelPortfolios()` | -- | `/api/v1/model-portfolios` | GET |
| `getModelPortfolio(id)` | `id: number` | `/api/v1/model-portfolios/{id}` | GET |
| `createModelPortfolio(request)` | `CreateModelPortfolioRequest` | `/api/v1/model-portfolios` | POST |
| `updateModelPortfolio(id, request)` | `id, UpdateModelPortfolioRequest` | `/api/v1/model-portfolios/{id}` | PUT |
| `deleteModelPortfolio(id)` | `id: number` | `/api/v1/model-portfolios/{id}` | DELETE |
| `applyModelToAccounts(modelId, request)` | `modelId, ApplyToAccountsRequest` | `/api/v1/model-portfolios/{id}/apply-to-accounts` | POST |
| `getModelAnalysis(modelId)` | `modelId: number` | `/api/v1/model-portfolios/{id}/analysis` | GET |
| `getRebalanceProgress(connId)` | `connectionId: number` | `/api/v1/brokers/connections/{id}/rebalance-progress` | GET |
| `getPendingOrders(connId)` | `connectionId: number` | `/api/v1/brokers/connections/{id}/pending-orders` | GET |

### notificationService.ts

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `getNotifications(unreadOnly, page, size)` | `unreadOnly: boolean, page: number, size: number` | `/api/v1/notifications` | GET |
| `getUnreadCount()` | -- | `/api/v1/notifications/count` | GET |
| `markAsRead(id)` | `id: number` | `/api/v1/notifications/{id}/read` | POST |
| `markAllAsRead()` | -- | `/api/v1/notifications/read-all` | POST |
| `getNotificationPreferences()` | -- | `/api/v1/notifications/preferences` | GET |
| `updateNotificationPreferences(request)` | `UpdateNotificationPreferencesRequest` | `/api/v1/notifications/preferences` | PATCH |

### performanceService.ts

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `getPerformanceSummary(groupId, period)` | `groupId: number, period: string` | `/api/v1/portfolio-groups/{id}/performance` | GET |
| `getPerformanceChart(groupId, period, benchmark?)` | `groupId, period, benchmark?` | `/api/v1/portfolio-groups/{id}/performance/chart` | GET |

### portfolioService.ts

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `analyzePortfolio(request)` | `AnalyzeRequest` | `/api/v1/portfolio/analyze` | POST |
| `validatePortfolio(positions)` | `PortfolioPosition[]` | `/api/v1/portfolio/validate` | POST |
| `normalizePortfolio(positions)` | `PortfolioPosition[]` | `/api/v1/portfolio/normalize` | POST |

Also exports interfaces: `PortfolioPosition`, `ValidateResponse`, `NormalizedPosition`, `NormalizeResponse`.

### tradingService.ts

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `executeTrades(request)` | `ExecuteTradesRequest` | `/api/v1/trading/execute` | POST |
| `executeSingleTrade(groupId, trade)` | `groupId, TradeExecutionInput` | `/api/v1/trading/groups/{id}/execute-single` | POST |
| `getGroupOrders(groupId)` | `groupId: number` | `/api/v1/trading/groups/{id}/orders` | GET |
| `getBatchOrders(batchId)` | `batchId: string` | `/api/v1/trading/batches/{id}` | GET |
| `cancelOrder(orderId)` | `orderId: number` | `/api/v1/trading/orders/{id}/cancel` | POST |

### adminService.ts

Rewired to ingestion-service (port 8081) via `/ingestion-api` Vite proxy. All endpoints below target the ingestion microservice.

| Export | Parameters | Endpoint | Method |
|--------|-----------|----------|--------|
| `syncExchanges()` | -- | `/ingestion-api/admin/ingestion/exchanges` | POST |
| `triggerFullIngestion()` | -- | `/ingestion-api/admin/ingestion/run` | POST |
| `getIngestionStats()` | -- | `/ingestion-api/admin/ingestion/stats` | GET |
| `getActiveRun()` | -- | `/ingestion-api/admin/ingestion/active-run` | GET |
| `getIngestionRuns(limit)` | `limit: number` | `/ingestion-api/admin/ingestion/runs` | GET |
| `getRunSteps(id)` | `id: number` | `/ingestion-api/admin/ingestion/runs/{id}/steps` | GET |
| `getIngestionRunErrors(id)` | `id: number` | `/ingestion-api/admin/ingestion/runs/{id}/errors` | GET |

Also exports interfaces: `IngestionStats` (with `instrumentsByType` per-type breakdowns), `ActiveRunResponse`, `IngestionRun`, `IngestionStep`, `IngestionError`.

---

## Stores

### stores/authStore.ts (Zustand with persist)

**State shape:**
```typescript
interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  csrfToken: string | null
  sessionExpired: boolean
}
```

**Actions:**
- `setUser(user)` -- Sets user and isAuthenticated
- `setCsrfToken(token)` -- Sets CSRF token
- `logout()` -- Clears user, auth state, and token
- `setLoading(loading)` -- Sets loading state
- `setSessionExpired(expired)` -- Sets session expired flag
- `checkAuth()` -- Async: calls `/auth/me` to verify session

**Persistence:** `localStorage` key `auth-storage`. Partializes `user` and `isAuthenticated` only.

**Selector hooks:** `useUser()`, `useIsAuthenticated()`, `useIsLoading()`.

### stores/themeStore.ts (Zustand with persist)

**State shape:**
```typescript
interface ThemeState {
  theme: 'dark' | 'light'
}
```

**Actions:**
- `toggleTheme()` -- Toggles between dark and light
- `setTheme(theme)` -- Sets specific theme

**Side effects:** Adds/removes `dark` class on `document.documentElement`.

**Persistence:** `localStorage` key `theme-storage`. Rehydration applies theme class.

### stores/toastStore.ts (Zustand, no persist)

**State shape:**
```typescript
interface ToastState {
  toasts: Toast[]  // { id, type: 'success'|'error'|'warning'|'info', message, duration? }
}
```

**Actions:**
- `addToast(type, message, duration?)` -- Adds a toast notification, auto-removes after duration (default 5s)
- `removeToast(id)` -- Removes a specific toast

**Custom hook:** `useToast()` -- Returns `{ success, error, warning, info }` convenience methods.

**Usage:** Import `useToast()` in components to show toast notifications for API errors, success confirmations, etc.

### stores/sidebarStore.ts (Zustand with persist)

**State shape:**
```typescript
interface SidebarState {
  collapsed: boolean
}
```

**Actions:**
- `toggleSidebar()` -- Toggles collapsed state
- `setCollapsed(collapsed)` -- Sets specific collapse state

**Persistence:** `localStorage` key `sidebar-storage`.

### store/analysisStore.ts (Zustand, no persist)

**State shape:**
```typescript
interface AnalysisStore {
  analysis: PortfolioAnalysis | null
  isLoading: boolean
  error: string | null
}
```

**Actions:**
- `setAnalysis(analysis)` -- Sets analysis result, clears error
- `setLoading(loading)` -- Sets loading state
- `setError(error)` -- Sets error message
- `clearAnalysis()` -- Clears analysis and error

### store/portfolioStore.ts (Zustand, no persist)

**State shape:**
```typescript
interface PortfolioStore {
  positions: PortfolioPosition[]  // {instrumentType, instrumentId, symbol, name, weight}
}
```

**Actions:**
- `addPosition(pos)` -- Adds position with weight=0 if not already present
- `removePosition(type, id)` -- Removes position by type and id
- `updateWeight(type, id, weight)` -- Sets weight clamped to [0, 1]
- `normalizeWeights()` -- Normalizes all weights to sum to 1.0
- `clearAll()` -- Removes all positions
- `totalWeight()` -- Returns sum of all weights
- `hasPosition(type, id)` -- Returns boolean

---

## Types

### types/auth.ts

| Type | Key Fields |
|------|------------|
| `User` | id, email, name, avatarUrl, emailVerified, roles, identities, lastLoginAt, createdAt |
| `Identity` | provider, providerEmail, connectedAt |
| `AuthResponse` | user: User, message? |
| `SignupResponse` | message, userId |
| `MessageResponse` | message |
| `AuthErrorResponse` | error, message, field?, lockedUntil? |
| `SignupRequest` | email, password, name? |
| `LoginRequest` | email, password |
| `ForgotPasswordRequest` | email |
| `ResetPasswordRequest` | token, newPassword |

### types/broker.ts

| Type | Key Fields |
|------|------------|
| `Broker` | id?, name, slug?, status?, logoUrl, description, allowsTrading?, authTypes? |
| `BrokerConnection` | id, broker, status ('PENDING'\|'ACTIVE'\|'EXPIRED'\|'ERROR'\|'DISCONNECTED'), accountName, totalValue, modelPortfolioId? |
| `BrokerPosition` | id, symbol, quantity, currentPrice, currentValue, totalPnl, currency, strikePrice?, optionType? |
| `AggregatedPosition` | symbol, totalQuantity, totalValue, brokerBreakdown[] |
| `BrokerActivityDto` | id, type, symbol, amount, fee, tradeDate |
| `BalanceSnapshotDto` | totalValue, cash: Record, currency, asOfDate |
| `ReportingPerformanceResponse` | contributionsWithdrawals[], totalValueHistory[], dividendHistory[], kpis |
| `ConnectionStatusType` | Union: 'PENDING'\|'ACTIVE'\|'EXPIRED'\|'ERROR'\|'DISCONNECTED' |

### types/dashboard.ts

| Type | Key Fields |
|------|------------|
| `WidgetPreference` | key, visible, sortOrder, columnSpan |
| `DashboardSummaryResponse` | portfolioValue, positionsSummary, holdingsCount |
| `DashboardCashResponse` | availableCash[], buyingPower[], totalCashCAD |
| `SectorExposureResponse` | sectors[], coveragePercent, unmappedWeight |
| `GeographyExposureResponse` | regions[], coveragePercent, unmappedWeight |
| `RiskProfileResponse` | riskScore, riskLevel, factors |
| `FeesResponse` | last12Months, monthlyBreakdown[], managementExpensePerMonth |
| `DividendCalendarResponse` | month, totalDividends, entries[] |
| `HoldingsTableResponse` | holdings[], totalCount, coveragePercent |
| `DashboardAccount` | connectionId, brokerName, status, totalValue, linkedGroup?, needsSetup |
| `WidgetKey` | Union of 19 widget key strings |
| `WidgetDefinition` | key, title, defaultVisible, defaultColumnSpan, defaultSortOrder |

### types/screener.ts

| Type | Key Fields |
|------|------------|
| `InstrumentType` | `'STOCK' \| 'ETF' \| 'MUTUAL_FUND' \| 'PREFERRED_STOCK' \| 'INDEX' \| 'BOND'` |
| `InstrumentScreenerItem` | id, ticker, name, instrumentType, isin, currency, country, exchange, sector, marketCap, pe, eps, dividendYield, weekHigh52, weekLow52, beta, issuer, assetClass, expenseRatio, yield, totalAssets, holdingsCount, return1Y, fundCategory, fundStyle, nav |
| `InstrumentDetail` | id, ticker, name, instrumentType, isin, currency, country, general, highlights, valuation, technicals, financials, earnings, splitsDividends, sharesStats, analystRatings, etfData, mutualFundData |
| `ScreenerFilter` | tickerContains?, nameContains?, country?, exchange?, sector?, issuer?, assetClass?, fundCategory?, fundStyle? |
| `PagedResponse<T>` | data: T[], meta: {page, size, totalElements, totalPages} |
| `SearchResult` | id, type, ticker, name, exchange, matchType |
| `SearchResponse` | data: SearchResult[], meta: {query, resultCount, searchTimeMs} |
| `TypeCounts` | Record<string, number> |
| `INSTRUMENT_TYPE_CONFIG` | Constant mapping instrument types to label, pluralLabel, route, detailRoute |

### types/portfolio.ts

| Type | Key Fields |
|------|------------|
| `PortfolioPosition` | instrumentType, instrumentId, symbol, name, weight |
| `PortfolioAnalysis` | summary, validation, sectorExposure[], geographyExposure[], topHoldings[], riskMetrics |
| `AnalyzeRequest` | positions: {instrumentType, instrumentId, weight}[], analysisDate? |
| `TopHolding` | stockId, ticker, name, effectiveWeight, sources[] |
| `ExposureSource` | type ('DIRECT'\|'ETF'\|'NESTED_ETF'), contribution |
| `RiskMetrics` | concentrationHHI, top10Concentration, sectorConcentrationHHI, estimatedVolatility |

### types/trading.ts

| Type | Key Fields |
|------|------------|
| `TradeOrder` | id, groupId?, connectionId, batchId?, symbol, action ('BUY'\|'SELL'), status, requestedUnits/Price/Amount, filled* |
| `OrderStatus` | Union: 'PENDING'\|'SUBMITTED'\|'FILLED'\|'PARTIALLY_FILLED'\|'REJECTED'\|'CANCELLED'\|'FAILED' |
| `ExecuteTradesRequest` | groupId, trades: TradeExecutionInput[], orderType?, timeInForce? |
| `TradeExecutionInput` | symbol, action, units, price, amount, currency, connectionId, limitPrice? |
| `ExecuteTradesResponse` | batchId, orders[], submittedCount, failedCount |

### types/notification.ts

| Type | Key Fields |
|------|------------|
| `Notification` | id, type, title, message, link?, isRead, createdAt |
| `NotificationsResponse` | notifications[], unreadCount, totalCount |
| `NotificationPreferences` | emailEnabled, inAppEnabled, driftAlerts, driftThreshold, orderAlerts, syncFailureAlerts, etc. |
| `UpdateNotificationPreferencesRequest` | All fields optional booleans/numbers/strings |

### types/performance.ts

| Type | Key Fields |
|------|------------|
| `PerformanceSummary` | twr, mwr, totalReturn, volatility, sharpeRatio, sortinoRatio, maxDrawdown, startingValue, endingValue |
| `PerformanceChartData` | summary, cumulativeReturns[], drawdowns[], benchmarkComparison? |
| `ReturnPoint` | date, cumulativeReturn, portfolioValue |
| `DrawdownPoint` | date, drawdown |
| `BenchmarkComparison` | portfolioReturns[], benchmarkReturns[], alpha |
| `PerformancePeriod` | `'1M' \| '3M' \| '6M' \| 'YTD' \| '1Y' \| 'ALL'` |

### types/modelPortfolio.ts

| Type | Key Fields |
|------|------------|
| `RiskLevel` | `'LOW' \| 'MODERATE' \| 'HIGH' \| 'EXTRA_HIGH'` |
| `ModelPortfolioSummary` | id, name, riskLevel, isSystem, allocationCount, totalPercent |
| `ModelPortfolioDetail` | id, name, riskLevel, isSystem, allocations[] |
| `CreateModelPortfolioRequest` | name, riskLevel, allocations: ModelAllocationInput[] |
| `RebalanceProgressResponse` | connectionId, modelName, accuracy, entries[] |
| `PendingOrdersResponse` | connectionId, orders[], totalAmount, cashRemaining, cashWarning? |
| `ModelAnalysisResponse` | modelId, sectorExposure[], geographyExposure[], riskScore, riskLevel |

### types/reference.ts

| Type | Key Fields |
|------|------------|
| `GicsSector` | code, name, industryGroups[] |
| `GicsIndustryGroup` | code, name, industries[] |
| `GicsIndustry` | code, name, subIndustries[] |
| `GicsSubIndustry` | code, name |
| `Country` | code, codeAlpha2, name, region |
| `Region` | code, name |

---

## CSS Files

### Global
- `src/App.css` -- App-level styles, loading spinner, page-loading
- `src/index.css` -- CSS custom properties (theme variables), base styles, dark mode

### By directory
- `components/analytics/` -- SummaryCards.css, ChartStyles.css, TopHoldingsGrid.css
- `components/auth/` -- (none, styled inline or in auth pages)
- `components/broker/` -- BrokerCard.css, BrokerConnectionCard.css, CashBalanceCards.css, AccountActivitiesGrid.css, BrokerageMatrix.css
- `components/dashboard/` -- DashboardGrid.css, DashboardEditMode.css, AccountTabs.css, PositionsHoldingsTabs.css, WidgetWrapper.css
- `components/dashboard/widgets/` -- 18 widget CSS files (one per widget that has custom styles)
- `components/instruments/` -- InstrumentSearchAutocomplete.css, InstrumentTabs.css
- `components/layout/` -- AppLayout.css, AppSidebar.css, MobileHeader.css, ThemeToggle.css, NotificationBell.css
- `components/portfolios/` -- ModelPortfolioCard.css, CustomPortfolioBuilder.css, ApplyToAccountModal.css, ModelAnalysisPanel.css
- `components/screener/` -- ScreenerFilters.css, ScreenerGrid.css
- `components/ui/` -- button.css, badge.css, card.css, dialog.css, sheet.css, skeleton.css, switch.css, separator.css, tooltip.css, Pagination.css, ErrorBoundary.css
- `pages/` -- DashboardPage.css, PortfolioPage.css, AnalyticsPage.css, InstrumentDetailPage.css, ScreenerPage.css, BrokerConnectionsPage.css, BrokerPositionsPage.css, PositionDetailsPage.css, AccountDetailPage.css, ReportingPage.css, ProfilePage.css, UnauthorizedPage.css
- `pages/auth/` -- AuthPages.css (shared auth page styles)

---

## Test Files

| File | Tests |
|------|-------|
| `App.test.tsx` | App component rendering and routing basics |
| `services/api.test.ts` | apiFetch CSRF handling, 401 auto-refresh, error flows |
| `services/brokerService.test.ts` | Broker service functions, API calls, error handling |
| `store/portfolioStore.test.ts` | PortfolioStore actions: add, remove, update weight, normalize |
| `store/analysisStore.test.ts` | AnalysisStore actions: set, clear, error handling |
| `components/broker/BrokerCard.test.tsx` | BrokerCard rendering with different broker data |
| `components/broker/BrokerConnectionCard.test.tsx` | BrokerConnectionCard status display, actions |
| `components/broker/ConnectionStatus.test.tsx` | ConnectionStatus badge rendering for each status |
| `components/ui/ErrorBoundary.test.tsx` | ErrorBoundary fallback rendering on child error |
| `components/dashboard/widgets/RebalancingProgressWidget.test.tsx` | RebalancingProgressWidget with various progress data |
| `components/dashboard/widgets/PendingOrdersWidget.test.tsx` | PendingOrdersWidget rendering with pending orders |
| `pages/PortfolioPage.test.tsx` | PortfolioPage integration tests |
| `pages/admin/AdminPage.test.tsx` | AdminPage rendering and ingestion trigger buttons |

---

## Configuration

### vite.config.ts

```typescript
{
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
  },
  server: {
    port: 3000,
    host: true,
    proxy: {
      '/api':    { target: 'http://localhost:8080', changeOrigin: true },
      '/health': { target: 'http://localhost:8080', changeOrigin: true },
      '/admin':  { target: 'http://localhost:8080', changeOrigin: true },
      '/ingestion-api': { target: 'http://localhost:8081', changeOrigin: true, rewrite: path => path.replace(/^\/ingestion-api/, '') }
    }
  },
  build: { outDir: 'dist', sourcemap: true },
  test: { globals: true, environment: 'jsdom', setupFiles: './src/setupTests.ts' }
}
```

### tsconfig.json

- **Target:** ES2020
- **Module:** ESNext with bundler resolution
- **JSX:** react-jsx
- **Strict mode:** Enabled (strict, noUnusedLocals, noUnusedParameters, noFallthroughCasesInSwitch)
- **Path alias:** `@/*` -> `./src/*`
- **Types:** `vitest/globals`
- **Lib:** ES2020, DOM, DOM.Iterable

### .eslintrc.cjs

- **Extends:** eslint:recommended, @typescript-eslint/recommended, react-hooks/recommended
- **Parser:** @typescript-eslint/parser
- **Plugins:** react-refresh
- **Rules:** `react-refresh/only-export-components` (warn, allowConstantExport)
- **Ignores:** dist, .eslintrc.cjs

### package.json dependencies

**Runtime dependencies:**
| Package | Version |
|---------|---------|
| `@tanstack/react-query` | ^5.60.0 |
| `ag-charts-community` | ^10.3.3 |
| `ag-charts-react` | ^10.3.3 |
| `ag-grid-community` | ^32.3.3 |
| `ag-grid-react` | ^32.3.3 |
| `clsx` | ^2.1.1 |
| `lucide-react` | ^0.577.0 |
| `react` | ^18.3.1 |
| `react-dom` | ^18.3.1 |
| `react-router-dom` | ^6.28.0 |
| `zustand` | ^5.0.1 |

**Dev dependencies:**
| Package | Version |
|---------|---------|
| `@testing-library/jest-dom` | ^6.5.0 |
| `@testing-library/react` | ^16.0.1 |
| `@types/react` | ^18.3.11 |
| `@types/react-dom` | ^18.3.0 |
| `@typescript-eslint/eslint-plugin` | ^8.8.0 |
| `@typescript-eslint/parser` | ^8.8.0 |
| `@vitejs/plugin-react` | ^4.3.2 |
| `eslint` | ^8.57.1 |
| `eslint-plugin-react-hooks` | ^4.6.2 |
| `eslint-plugin-react-refresh` | ^0.4.12 |
| `jsdom` | ^25.0.1 |
| `typescript` | ^5.6.3 |
| `vite` | ^5.4.9 |
| `vitest` | ^2.1.9 |

### npm scripts

| Script | Command |
|--------|---------|
| `dev` | `vite` |
| `build` | `tsc && vite build` |
| `preview` | `vite preview` |
| `test` | `vitest` (watch mode) |
| `test:run` | `vitest run` (single run) |
| `test:coverage` | `vitest run --coverage` |
| `lint` | `eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0` |

---

## Options Trading Module

### Pages

| File | Route | Description |
|---|---|---|
| `pages/OptionsPage.tsx` + `.css` | `/options` | Two-column layout (55% chain / 45% sidebar). Header with integrated search + live indicator. Mobile: floating "N Legs Selected" bar, bottom sheet for leg builder + P&L results |

### Components (`components/options/`)

| File | Description |
|---|---|
| `UnderlyingSearch.tsx` + `.css` | Symbol search input joined to "Load Chain" button, monospace uppercase input |
| `QuoteBar.tsx` + `.css` | Full-width quote bar: symbol (20px), price (22px), change indicator, bid/ask/spread/volume (15px) with vertical divider |
| `StrategySelector.tsx` + `.css` | Desktop: pill buttons (rounded, 13px). Mobile: dropdown select. Supports toggle on/off via store |
| `OptionsChainTable.tsx` + `.css` | Desktop: 7-column bidirectional (Bid,Ask,Delta | Strike | Delta,Bid,Ask) with expiry tabs. ATM row emerald left border. Mobile: expiry dropdown + calls/puts segmented toggle, 4-column (Strike,Bid,Ask,Delta). JetBrains Mono 14px data |
| `LegBuilder.tsx` + `.css` | Card-based leg display with BUY/SELL + CALL/PUT color-coded badges (12px), 3-column fields (Strike, Mid, Expiry). Clear All + Calculate P&L buttons |
| `PnlChart.tsx` + `.css` | SVG P&L curve with green/red fill areas, 2x2 metrics grid (18px values), styled break-even row, warning cards |

### Hooks

| File | Description |
|---|---|
| `hooks/useMarketDataWebSocket.ts` | WebSocket connection to `/ws/quotes` with subscribe/unsubscribe, exponential backoff reconnection (1s-30s) |

### Services

| File | Description |
|---|---|
| `services/marketDataService.ts` | REST: getQuote, getOptionsChain, getOptionsChainWithGreeks, getIvRank via `/market-data-api/` proxy |
| `services/optionsStrategyService.ts` | REST: getStrategies, getStrategyInfo, calculateStrategy, suggestStrategy, submitOptionsOrder, getOptionsOrders via `/strategy-api/` proxy |

### Stores

| File | Description |
|---|---|
| `stores/quoteStore.ts` | Zustand: quotes map, chains map, selectedUnderlying |
| `stores/strategyStore.ts` | Zustand: strategies list, selectedStrategy, legs array, calculationResult, isCalculating |

### Types

| File | Description |
|---|---|
| `types/options.ts` | Quote, OptionQuote, Greeks, OptionsChain, StrikeData, StrategyType (7 values), Leg, CalculationResult, PnlPoint, NetGreeks, StrategyInfo, EducationContent, WheelAccount/Config/Recommendation, OptionsOrderRequest/Response |

### Vite Proxy Routes

| Path | Target | Description |
|---|---|---|
| `/market-data-api/*` | `http://market-data-service:8082` | Market data REST API (path prefix stripped) |
| `/ws/quotes` | `ws://market-data-service:8082` | WebSocket for real-time quotes |
| `/strategy-api/*` | `http://strategy-service:8083` | Strategy REST API (path prefix stripped) |

---

## Wheel Strategy Module

### Pages

| File | Route | Description |
|---|---|---|
| `pages/WheelPage.tsx` + `.css` | `/wheel` | Wheel positions management: timeline grid of CSP/CC positions by expiry (Y-axis) and ticker (X-axis), account tabs, capital summary, close position dialog |

### Components (`components/wheel/`)

| File | Description |
|---|---|
| `WheelGrid.tsx` + `.css` | Timeline grid table: expiry rows, ticker columns, position cards in cells, empty slots, DTE badges, sticky header/footer with totals |
| `PositionCard.tsx` + `.css` | Individual position card: strike/premium/P&L/OTM labels, CSP (blue) vs CC (pink) color coding, optional account badge |
| `CapitalSummary.tsx` + `.css` | Capital metrics bar: available cash, deployed CSPs, shares held, CCs written, total premium, unrealized P&L |
| `ClosePositionDialog.tsx` + `.css` | Modal dialog for closing a position (buy-to-close) with position details and confirmation |

### Hooks

| File | Description |
|---|---|
| `hooks/useWheelPositions.ts` | Fetches positions via React Query, filters to options, groups into grid structure by expiry/ticker. Exports: `useWheelPositions`, `buildWheelGrid`, `computeTickerTotals`, `computeCapitalMetrics` |

### Types

| File | Description |
|---|---|
| `types/wheel.ts` | WheelTicker, WheelPosition, WheelCell, WheelExpiryRow, WheelGridData, TickerTotals, CapitalMetrics, DteUrgency, getDteUrgency(), isMonthlyExpiry() |

### Tests

| File | Description |
|---|---|
| `hooks/__tests__/useWheelPositions.test.ts` | 9 unit tests: position placement (CSP/CC), filtering (non-options, beyond 90 DTE), stacking, DTE calculation, available expiries, ticker totals, capital metrics |
