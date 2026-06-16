# Frontend Analysis

> Complete audit: pages, components, services, hooks, stores, dead code inventory, and redundancies.

---

## 1. File Inventory

| Category | Count | Location |
|----------|-------|----------|
| Pages | 16 (17 including catch-all) | `src/pages/` |
| Components | ~75 | `src/components/` (16 subdirectories) |
| Hooks | 20 | `src/hooks/` |
| Services | 15 | `src/services/` |
| Stores | 8 (2 directories) | `src/stores/` + `src/store/` |
| Type definitions | 12 | `src/types/` |
| CSS files | ~50 | Co-located with components/pages |
| Config | 1 | `src/config/environment.ts` |
| Test files | 5 | Various `.test.ts` + `.test.tsx` |
| E2E tests | 1 | `e2e/questrade-connection.spec.ts` |

---

## 2. Pages and Routing

```
<BrowserRouter>
  ├── /login                    → LoginPage          (public)
  ├── /                         → DashboardPage      (auth)
  ├── /portfolios               → PortfolioPage       (auth)
  ├── /screener/:type           → ScreenerPage        (auth)
  ├── /instruments/:type/:ticker → InstrumentDetailPage (auth)
  ├── /analytics                → AnalyticsPage       (auth)
  ├── /brokers/connections      → BrokerConnectionsPage (auth)
  ├── /brokers/positions        → BrokerPositionsPage  (auth)
  ├── /brokers/positions/:connectionId → PositionDetailsPage (auth)
  ├── /brokers/accounts/:connectionId  → AccountDetailPage (auth)
  ├── /brokers/reporting        → ReportingPage       (auth)
  ├── /options                  → OptionsPage          (auth)
  ├── /wheel                    → WheelPage            (auth)
  ├── /profile                  → ProfilePage          (auth)
  ├── /admin                    → AdminPage            (auth + ADMIN role)
  ├── /unauthorized             → UnauthorizedPage     (public)
  └── /*                        → Redirect to /
```

All pages except `/login` and `/unauthorized` are lazy-loaded via `React.lazy()`.

---

## 3. Component Architecture

### 3.1 Component Directory Structure

```
components/
├── analytics/       # Charts: SectorChart, GeographyChart, RiskProfileChart, etc.
├── auth/            # ProtectedRoute, SessionTimeoutWarning
├── broker/          # BrokerCard, BrokerConnectionCard, BrokerageMatrix, etc.
├── dashboard/       # AccountsStrip, KpiCard, PositionsTable
├── instruments/     # Search autocomplete, detail sections (Stock/ETF/MF)
├── layout/          # AppLayout, IconRail, BottomTabBar, ThemeToggle, etc.
├── notifications/   # NotificationPreferencesForm
├── options/         # OptionsChainTable, LegBuilder, PnlChart, StrategySelector, etc.
├── performance/     # PerformanceTab, CumulativeReturnChart, etc.
├── portfolios/      # ModelPortfolioCard, CustomPortfolioBuilder, etc.
├── reporting/       # ActivityTable, ContributionsChart, etc.
├── screener/        # ScreenerFilters, ScreenerGrid, ScreenerSidebar
├── ui/              # badge, button, card, dialog, ErrorBoundary, Pagination, etc.
└── wheel/           # WheelCalendarGrid, WheelChainPanel, OrderPanel, etc.
```

### 3.2 State Management Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        State Management                          │
│                                                                  │
│  ┌──────────────────────┐      ┌────────────────────────────┐   │
│  │  Zustand Stores       │      │  TanStack React Query      │   │
│  │  (Client State)       │      │  (Server State)            │   │
│  │                       │      │                            │   │
│  │  authStore (persist)  │      │  All API data:              │   │
│  │  themeStore (persist) │      │  - Dashboard widgets        │   │
│  │  quoteStore           │      │  - Broker connections       │   │
│  │  strategyStore        │      │  - Positions, activities    │   │
│  │  toastStore           │      │  - Screener data            │   │
│  │  portfolioStore       │      │  - Model portfolios         │   │
│  │  analysisStore        │      │  - Performance data         │   │
│  │  sidebarStore (DEAD)  │      │  - Notifications            │   │
│  └──────────────────────┘      └────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Dead / Unused Code Inventory

### 4.1 Dead Components (Never Imported)

| # | File | Size (est.) | Notes |
|---|------|-------------|-------|
| 1 | `src/stores/sidebarStore.ts` | 15 lines | Persisted but never read anywhere |
| 2 | `src/components/wheel/ClosePositionDialog.tsx` | 50 lines | No parent imports |
| 3 | `src/components/wheel/ClosePositionDialog.css` | 30 lines | Dead CSS |
| 4 | `src/components/notifications/NotificationPreferencesForm.tsx` | 80 lines | No parent imports |
| 5 | `src/components/screener/ScreenerGrid.tsx` | 200 lines | Replaced by AG Grid |
| 6 | `src/components/screener/ScreenerGrid.css` | 50 lines | Dead CSS |
| 7 | `src/components/screener/ScreenerSidebar.tsx` | 150 lines | No parent imports |
| 8 | `src/components/screener/ScreenerSidebar.css` | 40 lines | Dead CSS |
| 9 | `src/components/instruments/InstrumentTabs.tsx` | 60 lines | No parent imports |
| 10 | `src/components/instruments/InstrumentTabs.css` | 20 lines | Dead CSS |

### 4.2 Effectively Dead (Depends on Dead Component)

| # | File | Reason |
|---|------|--------|
| 11 | `src/components/instruments/InstrumentSearchAutocomplete.tsx` | Only imported by dead `InstrumentTabs.tsx` |
| 12 | `src/components/instruments/InstrumentSearchAutocomplete.css` | Dead CSS |

### 4.3 Unused UI Primitives (Defined but Never Imported)

| # | File | Status |
|---|------|--------|
| 13 | `src/components/ui/card.tsx` + `.css` | Unused |
| 14 | `src/components/ui/separator.tsx` + `.css` | Unused |
| 15 | `src/components/ui/sheet.tsx` + `.css` | Unused |
| 16 | `src/components/ui/switch.tsx` + `.css` | Unused |
| 17 | `src/components/ui/tooltip.tsx` + `.css` | Unused |

**Total frontend dead files: ~22 files**

---

## 5. Redundancies

### 5.1 Inconsistent Store Directory Naming

| Directory | Files | Purpose |
|-----------|-------|---------|
| `src/stores/` | 6 stores | Global application state (auth, theme, quote, strategy, toast, sidebar) |
| `src/store/` | 2 stores | Domain-specific (portfolio, analysis) |

**Issue:** Inconsistent naming between singular `store/` and plural `stores/`. New developers may be confused.

### 5.2 Type Duplication

`PortfolioPosition` is defined in **3** separate locations:
1. `src/types/portfolio.ts` — canonical type
2. `src/services/portfolioService.ts` — API request/response
3. `src/store/portfolioStore.ts` — local store state

### 5.3 Inline Query Keys

While `queryKeys.ts` centralizes some keys (`brokerKeys`, `dashboardKeys`), several hooks define keys inline:
- `useTrading.ts`: `['groupOrders', groupId]`
- `useNotifications.ts`: `['notifications', ...]`

---

## 6. Gaps

### 6.1 Test Coverage

| Test Type | Files | Coverage |
|-----------|-------|----------|
| Unit tests | 5 files | ~5% of components |
| Store tests | 2 | `analysisStore.test.ts`, `portfolioStore.test.ts` |
| Hook tests | 1 | `useWheelPositions.test.ts` |
| E2E tests | 1 | Playwright — Questrade connection flow |

**Critical gaps:**
- No tests for most pages
- No tests for services (except `api.test.ts`, `brokerService.test.ts`)
- No tests for 15+ React Query hooks
- No WebSocket tests

### 6.2 Missing UI States

- Loading states: some pages lack skeleton loaders
- Error boundaries: may not cover all routes
- Empty states: some pages may not handle empty data gracefully

### 6.3 Performance Considerations

- AG Grid and AG Charts not lazy-loaded per page (in main bundle)
- Dashboard widgets may re-render unnecessarily
- No bundle analysis tool configured
