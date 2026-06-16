# Redundancies, Disconnected Code, and Gaps

> Consolidated inventory of all dead code, unused dependencies, disconnected modules, missing features, and architectural gaps.

---

## 1. Dead / Unused Code

### 1.1 Frontend — Dead Components (Never Imported)

| # | File | Type | Notes |
|---|------|------|-------|
| 1 | `src/stores/sidebarStore.ts` | Store | Persisted but never read anywhere |
| 2 | `src/components/wheel/ClosePositionDialog.tsx` | Component | No parent imports |
| 3 | `src/components/wheel/ClosePositionDialog.css` | CSS | Dead CSS |
| 4 | `src/components/notifications/NotificationPreferencesForm.tsx` | Component | No parent imports |
| 5 | `src/components/screener/ScreenerGrid.tsx` | Component | Replaced by AG Grid |
| 6 | `src/components/screener/ScreenerGrid.css` | CSS | Dead CSS |
| 7 | `src/components/screener/ScreenerSidebar.tsx` | Component | No parent imports |
| 8 | `src/components/screener/ScreenerSidebar.css` | CSS | Dead CSS |
| 9 | `src/components/instruments/InstrumentTabs.tsx` | Component | No parent imports |
| 10 | `src/components/instruments/InstrumentTabs.css` | CSS | Dead CSS |
| 11 | `src/components/instruments/InstrumentSearchAutocomplete.tsx` | Component | Only imported by dead InstrumentTabs |
| 12 | `src/components/instruments/InstrumentSearchAutocomplete.css` | CSS | Dead CSS |

### 1.2 Frontend — Unused UI Primitives

| # | File | Type |
|---|------|------|
| 13 | `src/components/ui/card.tsx` + `.css` | Component |
| 14 | `src/components/ui/separator.tsx` + `.css` | Component |
| 15 | `src/components/ui/sheet.tsx` + `.css` | Component |
| 16 | `src/components/ui/switch.tsx` + `.css` | Component |
| 17 | `src/components/ui/tooltip.tsx` + `.css` | Component |

**Total frontend dead files: ~22**

### 1.3 Backend — Dead Imports

| # | File | Dead Import | Reason |
|---|------|-------------|--------|
| 1 | `RebalanceScheduler.kt` | `OrderExecutionService` | Injected but never referenced |
| 2 | `RebalanceScheduler.kt` | `ExecuteTradesRequest` | Imported but never used |
| 3 | `RebalanceScheduler.kt` | `TradeExecutionInput` | Imported but never used |
| 4 | `RebalanceScheduler.kt` | `java.util.UUID` | Imported but never used |

---

## 2. Unused Dependencies

### 2.1 Build Dependencies Declared But Not Used

| # | Module | Dependency | Evidence |
|---|--------|------------|----------|
| 1 | `broker-gateway` | `com.portfolio:common` | Zero imports from `com.portfolio.common` |
| 2 | `strategy` | `com.portfolio:common` | Zero imports from `com.portfolio.common` |
| 3 | `strategy` | `spring-boot-starter-data-redis` | Redis configured but no code uses it |

### 2.2 Configured But Not Used

| # | Service | Feature | Evidence |
|---|---------|---------|----------|
| 1 | `strategy` | `@EnableScheduling` | Annotation present, zero `@Scheduled` methods |
| 2 | `strategy` | `services.market-data-url` | URL configured, no HTTP calls |
| 3 | `strategy` | `services.portfolio-url` | URL configured, no HTTP calls |
| 4 | `strategy` | JPA entities | No `@Entity` classes; all models are `data class`es |

---

## 3. Disconnected Modules

### 3.1 Wheel Strategy: DB schema but no code

The `strategy` service has 9+ Flyway-created tables for Wheel (wheel_accounts, wheel_configs, wheel_recommendations, wheel_holdings, etc.) but:
- The `StrategyType` enum has no `WHEEL` entry
- No `WheelAccountRepository`, `WheelService`, or wheel-related controllers exist
- The frontend has a complete Wheel page but it queries broker positions directly, not the strategy service
- **DB schema is ahead of implementation by a significant margin**

### 3.2 AnalyticsPage Disconnected from Store

`AnalyticsPage.tsx` uses `useAnalysisStore` to display portfolio analytics results, but:
- No mutation populates `analysisStore` with data
- `usePortfolioAnalysis` hook doesn't connect to the store
- Page appears non-functional for its intended purpose

### 3.3 Strategy Orders/Positions Tables Unused

`strategy.orders`, `strategy.order_legs`, `strategy.executions`, `strategy.positions`, `strategy.position_legs` tables:
- No Kotlin repositories for these tables
- No service layer for strategy order management
- Snaptrade order ID column (`snaptrade_order_id`) still present after Snaptrade removal

### 3.4 Strategy Admin Actions Table Unused

`strategy.admin_actions` table exists with no corresponding service or controller.

### 3.5 Common Library Underutilized

`common` has canonical domain objects (`Quote`, `Greeks`, `OptionsChain`) but:
- **`portfolio` service (largest service) doesn't use `common` at all**
- `broker-gateway` declares dependency but zero imports
- `strategy` declares dependency but zero imports

### 3.6 Provider Registry Single Implementation

`ProviderRegistry` suggests multiple providers (AlphaVantage, etc.) but only `EodhdProvider` is implemented. AlphaVantage and SeekingAlpha references remain in Flyway migrations despite being removed.

---

## 4. Redundant Code Patterns

### 4.1 Type Duplication (Frontend)

| Concept | Defined In | Count |
|---------|-----------|-------|
| `PortfolioPosition` | `src/types/portfolio.ts`, `src/services/portfolioService.ts`, `src/store/portfolioStore.ts` | **3 times** |

### 4.2 Store Directory Inconsistency

| Directory | Convention | Files |
|-----------|-----------|-------|
| `src/store/` | Singular | 2 stores |
| `src/stores/` | Plural | 6 stores |

### 4.3 Dockerfile Duplication

5 Dockerfiles with ~90% identical content. Only varying lines: port, common/ copy, SSL cert import.

### 4.4 Spring Profile Duplication

5 services × 2 profiles (uat + prod) = 10 nearly identical files differing only in log levels.

### 4.5 Three API Methods in Frontend

| Method | Used By | Auth |
|--------|---------|------|
| `apiFetch` | Most services | CSRF + JWT |
| `proxyFetch` | marketDataService, optionsStrategyService | None |
| Raw `fetch()` | adminService.ts | None (missing CSRF) |

### 4.6 Duplicate Theme Hooks

- `useChartTheme` — wraps `useThemeStore`
- `useAgGridTheme` — wraps `useThemeStore`

Both do the same thing; could be consolidated.

---

## 5. Missing Features / Gaps

### 5.1 Security Gaps

| # | Gap | Severity | Service |
|---|-----|----------|---------|
| 1 | API key not validated on gateway | **High** | broker-gateway |
| 2 | Hardcoded Wealthsimple client ID | **High** | broker-gateway |
| 3 | No API rate limiting | **High** | All services |
| 4 | No MFA | **Medium** | Auth system |

### 5.2 Testing Gaps

| # | Gap | Service |
|---|-----|---------|
| 1 | Zero tests | strategy service |
| 2 | Zero tests | ingestion service |
| 3 | Only 1 test file | market-data service |
| 4 | ~5% component coverage | frontend |
| 5 | No React Query hook tests | frontend |
| 6 | No WebSocket tests | frontend |

### 5.3 Operational Gaps

| # | Gap | Impact |
|---|-----|-------|
| 1 | No Kubernetes manifests | Not scalable beyond single host |
| 2 | No blue/green deployment | Brief downtime on deploy |
| 3 | No rollback automation | Manual rollback required |
| 4 | No synthetic monitoring | Only HTTP-level uptime checks |
| 5 | No distributed tracing | Hard to debug cross-service issues |
| 6 | No pre-built Grafana dashboards | Manual creation needed |

### 5.4 Resilience Gaps

| # | Gap | Service |
|---|-----|---------|
| 1 | Only one data provider (EODHD) | ingestion |
| 2 | No fallback for IBKR | market-data |
| 3 | No fallback for broker gateway | broker-gateway |
| 4 | Single VPS host = single point of failure | Infrastructure |
| 5 | No data retention/cleanup policies | All services |

---

## 6. Summary by Priority

### High Priority

| # | Issue | Effort | Category |
|---|-------|--------|----------|
| 1 | Broker gateway API key validation | Low | Security |
| 2 | Remove hardcoded Wealthsimple client ID | Low | Security |
| 3 | Remove 22 dead frontend files | Low | Cleanup |
| 4 | Remove 4 dead imports in RebalanceScheduler | Low | Cleanup |
| 5 | Implement API rate limiting | Medium | Security |

### Medium Priority

| # | Issue | Effort | Category |
|---|-------|--------|----------|
| 6 | Consolidate store directories | Low | Cleanup |
| 7 | Deduplicate `PortfolioPosition` type | Low | Cleanup |
| 8 | Add tests for strategy + ingestion services | High | Quality |
| 9 | Remove unused common dependency in broker-gateway and strategy | Low | Build |
| 10 | Implement wheel strategy in strategy service | High | Feature |
| 11 | Consolidate Dockerfiles | Medium | DevOps |

### Lower Priority

| # | Issue | Effort | Category |
|---|-------|--------|----------|
| 12 | Consolidate UAT/Prod docker-compose files | Low | DevOps |
| 13 | Remove unused Redis dependency from strategy | Low | Build |
| 14 | Remove unused `@EnableScheduling` from strategy | Low | Cleanup |
| 15 | Add second data provider to ingestion | High | Resilience |
| 16 | Add Grafana dashboard JSONs | Medium | Observability |
| 17 | Add Kubernetes manifests | High | Scalability |