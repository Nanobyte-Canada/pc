# Unused, Legacy, and Redundant Code Audit

Comprehensive audit of unused, legacy, and redundant code in the Portfolio Construction App. All findings verified by searching the actual codebase.

**Last cleanup:** 2026-05-30 — Removed `FakeIbkrClient` after real IBKR Gateway connection verified.
- **Market-data service:** Removed `FakeIbkrClient` (synthetic market data generator). `TwsIbkrClient` is now the sole `IbkrClient` implementation — `@ConditionalOnExpression` removed, always active. `IBKR_HOST` must be set for connection.

**Previous cleanup:** 2026-04-12 — V67-V68 screener migration completed.
- **Backend:** Dropped legacy `stocks`/`etfs`/`etf_holdings`/GICS tables, removed `ScreenerService`/`InstrumentSearchService`/`ReferenceDataService`/`HoldingsService`/`CachedLookupService`, added `InstrumentScreenerService`/`IngestionInstrumentLookupService`/`CountryRegionLookupService`.
- **Frontend:** Removed old `StockScreenerPage`/`EtfScreenerPage`/`StockDetailPage`/`EtfDetailPage`, old hooks/services/types, replaced with unified `ScreenerPage`/`InstrumentDetailPage`/`useNewScreener`/`screenerService`/`types/screener`.

---

## Remaining Items (Keep)

These items were evaluated during the audit and confirmed as actively used or intentionally retained.

### benchmark_returns

- **Created in:** `V41__portfolio_snapshots.sql`
- **Entity:** `backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/BenchmarkReturn.kt`
- **Repository:** `backend/portfolio/src/main/kotlin/com/portfolio/broker/repository/BenchmarkReturnRepository.kt`
- **Evidence of usage (keep):**
  - `BenchmarkReturnRepository` is injected into `BenchmarkService` (`backend/portfolio/src/main/kotlin/com/portfolio/broker/service/BenchmarkService.kt`)
  - `BenchmarkService` is injected into `PerformanceCalculationService`
  - `PerformanceCalculationService` is used by `PerformanceController`
  - Tests exist: `PerformanceCalculationServiceTest.kt`
  - **However:** The table has 0 rows -- no benchmark data has been ingested yet
- **Status:** Keep. The code is wired and functional; it just needs benchmark data to be populated (likely via a future ingestion step or manual import).
- **Impact of removal:** Would break `PerformanceCalculationService` and `PerformanceController`.

### snaptrade_status_checks (REMOVED)

- **Created in:** `V35__snaptrade_status_checks.sql`
- **Dropped in:** `V72__snaptrade_to_gateway_migration.sql`
- **Status:** Removed. Table dropped, entity/repository/service/scheduler all deleted as part of the SnapTrade to broker-gateway migration. Gateway health monitoring is now handled via `GET /api/v1/brokers/gateway/health` which calls the broker-gateway service directly.

### portfolio_cash_flows

- **Created in:** `V41__portfolio_snapshots.sql`
- **Entity:** `backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/PortfolioCashFlow.kt`
- **Repository:** `backend/portfolio/src/main/kotlin/com/portfolio/broker/repository/PortfolioCashFlowRepository.kt`
- **Evidence of usage (keep):**
  - `PortfolioCashFlowRepository` is injected into `PerformanceCalculationService`
  - Tests mock it in `PerformanceCalculationServiceTest.kt`
  - Table has 0 rows -- no cash flow events have been recorded yet
- **Status:** Keep. Used by the performance calculation pipeline.
- **Impact of removal:** Would break `PerformanceCalculationService`.

### portfolio_excluded_assets

- **Created in:** `V38__portfolio_groups.sql`
- **Entity:** `backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/PortfolioExcludedAsset.kt`
- **Repository:** `backend/portfolio/src/main/kotlin/com/portfolio/broker/repository/PortfolioExcludedAssetRepository.kt`
- **Evidence of usage (keep):**
  - `PortfolioExcludedAssetRepository` is injected into `PortfolioGroupService` and `DriftCalculationService`
  - Tests mock it in `PortfolioGroupServiceTest.kt` and `DriftCalculationServiceTest.kt`
  - Table may have 0 rows but the feature is functional (users can exclude assets from drift calculations)
- **Status:** Keep. Active feature in the drift calculation pipeline.
- **Impact of removal:** Would break portfolio group and drift calculation functionality.

### adminService.ts

- **File:** `frontend/src/services/adminService.ts`
- **Exports:** `triggerFullIngestion`, `triggerStockIngestion`, `triggerStockEnrichment`, `triggerEtfComUniverse`, `triggerEtfComEnrichment`, `getIngestionRuns`, `getIngestionRunDetails`, `getRunSteps`, `getIngestionRunErrors`, `getRecentErrors`, `getErrorSummary`, `getIngestionStats`
- **Imported by:** `frontend/src/pages/admin/AdminPage.tsx` and `frontend/src/pages/admin/AdminPage.test.tsx`
- **Status:** Actively used. AdminPage is a functional admin panel for managing data ingestion. Keep.

---

## Registered Dashboard Widgets (20 entries in WidgetRegistry.ts)

For reference, these are the 20 widget keys actually registered and functional:

`PORTFOLIO_VALUE`, `AVAILABLE_CASH`, `BUYING_POWER`, `RISK_PROFILE`, `SECTOR_EXPOSURE`, `GEOGRAPHY_EXPOSURE`, `REBALANCING_PROGRESS`, `PENDING_ORDERS`, `OPEN_ORDERS`, `FEES_COMMISSION`, `DIVIDEND_CALENDAR`, `CONNECTED_ACCOUNTS`, `POSITIONS_TABLE`, `HOLDINGS_TABLE`, `ACCOUNT_SUMMARY`, `ORDERS`, `FEES_AND_DIVIDENDS`, `POSITIONS_HOLDINGS`, `PORTFOLIO_SUMMARY`

Note: `POSITIONS_HOLDINGS` maps to `PositionsHoldingsTabs.tsx` which lives at `frontend/src/components/dashboard/PositionsHoldingsTabs.tsx` (not in the widgets subdirectory).

---

## Migration Artifacts (Keep)

### V52__truncate_and_restart.sql

- **File:** `backend/portfolio/src/main/resources/db/migration/V52__truncate_and_restart.sql`
- **Purpose:** One-time data reset. Truncates `etf_holdings`, `etf_sector_allocation_factset`, `etfs`, `stocks`, `ingestion_errors`, `ingestion_steps`, `ingestion_runs` to start fresh under a new pipeline design.
- **Idempotent:** Yes -- uses `IF EXISTS` checks before truncating
- **Status:** Keep. Flyway migrations are append-only and cannot be safely removed without risking checksum mismatches. The migration has already been applied and will not run again.

### V56__backfill_activity_types.sql

- **File:** `backend/portfolio/src/main/resources/db/migration/V56__backfill_activity_types.sql`
- **Purpose:** One-time data fix. Reclassifies `broker_activities` rows from type `FEE` to `COMMISSION` where `raw_payload` contains "commission".
- **Idempotent:** Yes -- the UPDATE is a no-op if already applied
- **Status:** Keep. Same rationale as V52 -- Flyway migrations should not be removed.

---

## Environment Variables

### config/.env.example Gaps (Resolved)

The following variables were missing from `config/.env.example` but present in `application.yml`. These have been added to `config/.env.example` as of the 2026-04-10 cleanup:

- `JWT_SIGNING_KEY` — Security-critical signing key
- `GOOGLE_CLIENT_ID` — Google OAuth
- `GOOGLE_CLIENT_SECRET` — Google OAuth
- `EODHD_API_KEY` — Data ingestion
- `ALPHA_VANTAGE_API_KEY` — Enrichment
- `CORS_ALLOWED_ORIGINS` — Non-local deployments

The following optional variables remain undocumented in `config/.env.example` (feature flags and tuning knobs with safe defaults):

| Variable | Default | Notes |
|----------|---------|-------|
| `JWT_ACCESS_EXPIRATION` | `60m` | Optional tuning |
| `JWT_REFRESH_EXPIRATION` | `6h` | Optional tuning |
| `INGESTION_ENABLED` | `true` | Feature flag |
| `AV_ENRICHMENT_ENABLED` | `true` | Feature flag |
| `ETFCOM_ENABLED` | `true` | Feature flag |
| `BROKER_SYNC_ENABLED` | `false` | Feature flag |
| `EMAIL_PROVIDER` | `console` | Email configuration |
| `EMAIL_FROM` | `noreply@portfolio.local` | Email configuration |

---

## Summary Table

| Item | Type | Status | Action |
|------|------|--------|--------|
| `benchmark_returns` entity+repo | Active code, empty data | Used by PerformanceCalculationService | Keep |
| `snaptrade_status_checks` entity+repo | **Removed** (V72) | SnapTrade replaced by broker-gateway | Removed |
| `portfolio_cash_flows` entity+repo | Active code, empty data | Used by PerformanceCalculationService | Keep |
| `portfolio_excluded_assets` entity+repo | Active code | Used by drift calculation | Keep |
| `adminService.ts` | Active code | Used by AdminPage | Keep |
| V52, V56 migrations | One-time migrations | Already applied | Keep (Flyway requirement) |
| `config/.env.example` gaps | Documentation gap | **Fixed** (6 vars added) | Done |

---

## Resolved Items (Cleaned Up 2026-04-10)

All items below were identified during the original audit and have been removed. Database tables were dropped via the `V66` Flyway migration. Code files were deleted from the codebase.

### Orphan Database Tables (Dropped via V66 Migration)

| Table | Originally Created In | Why Removed |
|-------|----------------------|-------------|
| `external_connections` | `V29__authentication_schema.sql` | Placeholder for brokerage OAuth, superseded by `broker_connections` / SnapTrade |
| `external_connection_tokens` | `V29__authentication_schema.sql` | Token storage for `external_connections`, never used |
| `app_metadata` | `V1__init.sql` | Single-row schema version tracker, redundant with Flyway's `flyway_schema_history` |
| `fund_sector_allocations` | `V14__fund_sector_allocations.sql` | Never populated; ETF sector data comes from `etf_sector_allocation_factset` table instead |

### Dead Backend Code (Deleted)

| File / Item | Why Removed |
|-------------|-------------|
| `FundSectorAllocation.kt` entity | Entity for dropped `fund_sector_allocations` table; no service ever used it |
| `FundSectorAllocationRepository.kt` | Repository for dropped table; never injected anywhere |
| `DashboardService.kt` | Legacy service for `GET /api/v1/dashboard`; fully replaced by `DashboardDataService` and widget endpoints |
| `DashboardServiceTest.kt` | Tests for removed `DashboardService` |
| `GET /api/v1/dashboard` endpoint | Legacy endpoint removed from `DashboardController`; no frontend code called it |
| `DashboardResponse` + `DashboardGroupSummary` DTOs | DTOs for removed legacy endpoint; removed from `NotificationDtos.kt` |

### Dead Frontend Code (Deleted)

| File / Item | Why Removed |
|-------------|-------------|
| `dashboardService.ts` | Called `GET /api/v1/dashboard` (removed); only imported by unused `useDashboard.ts` hook |
| `useDashboard.ts` hook | Never imported by any component |
| `DashboardData` + `DashboardGroupSummary` interfaces | Removed from `notification.ts`; only used by deleted `dashboardService.ts` |

### Orphan Widget Components (Deleted)

These were in `frontend/src/components/dashboard/widgets/` but NOT registered in `WidgetRegistry.ts`, meaning they were never rendered by `DashboardGrid`.

| File | Notes |
|------|-------|
| `RefreshButtonWidget.tsx` + CSS | Not in WidgetRegistry; no imports anywhere |
| `HoldingsCountWidget.tsx` + CSS | Not in WidgetRegistry; no imports anywhere |
| `PositionsSummaryWidget.tsx` + CSS | Not in WidgetRegistry; no imports anywhere |

### Newly Discovered Orphan Components (Deleted)

These four components were **not identified in the original audit** but were discovered during the cleanup process. They existed in `frontend/src/components/dashboard/` but had no imports or references anywhere in the codebase.

| File | Notes |
|------|-------|
| `PortfolioGroupsList.tsx` | Orphan dashboard component; never imported |
| `AlertsList.tsx` | Orphan dashboard component; never imported |
| `RecentOrdersList.tsx` | Orphan dashboard component; never imported |
| `DashboardKpiCards.tsx` | Orphan dashboard component; never imported |

### Old Screener/Detail Frontend Code (Deleted 2026-04-12)

Replaced by the unified instrument screener (`ScreenerPage`, `InstrumentDetailPage`, `useNewScreener`, `screenerService`, `types/screener`).

| File | Why Removed |
|------|-------------|
| `pages/StockScreenerPage.tsx` | Replaced by `ScreenerPage.tsx` with `:type` param |
| `pages/EtfScreenerPage.tsx` | Replaced by `ScreenerPage.tsx` with `:type` param |
| `pages/StockDetailPage.tsx` | Replaced by `InstrumentDetailPage.tsx` with `:type/:ticker` params |
| `pages/EtfDetailPage.tsx` + `.css` | Replaced by `InstrumentDetailPage.tsx` with `:type/:ticker` params |
| `hooks/useScreener.ts` | Old `useStockScreener`/`useEtfScreener` replaced by `useInstrumentScreener` in `useNewScreener.ts` |
| `hooks/useInstrumentSearch.ts` | Replaced by `useNewInstrumentSearch` in `useNewScreener.ts` |
| `hooks/useReferenceData.ts` | Replaced by `useReferenceValues` in `useNewScreener.ts` |
| `services/instrumentService.ts` | Replaced by `screenerService.ts` |
| `services/referenceDataService.ts` | Replaced by `getReferenceData` in `screenerService.ts` |
| `types/instrument.ts` | Replaced by `types/screener.ts` (superset with more instrument types) |

Also cleaned up in this pass:
- Removed 4 lazy imports (`StockScreenerPage`, `EtfScreenerPage`, `StockDetailPage`, `EtfDetailPage`) from `App.tsx`
- Removed 4 commented-out old routes from `App.tsx`
- Redirected `InstrumentType` imports in `types/portfolio.ts`, `services/portfolioService.ts`, `components/instruments/InstrumentTabs.tsx` from `types/instrument` to `types/screener`
- Updated `CustomPortfolioBuilder.tsx` to use `useNewInstrumentSearch` from `useNewScreener` and types from `types/screener`
- Removed unused `Search` import from `AppSidebar.tsx`

---

## Cross-References

- For database table schemas and all migrations, see [database-schema.md](database-schema.md)
- For backend service architecture and dependencies, see [backend-services.md](backend-services.md)
- For frontend component map and import graph, see [frontend-map.md](frontend-map.md)
- For configuration details and env vars, see [configurations.md](configurations.md)
