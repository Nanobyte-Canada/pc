# Screener Migration to EODHD Ingestion Data

**Date:** 2026-04-12
**Status:** Approved

## Context

The portfolio construction app has two disconnected data systems:
- **Old (`public` schema):** Separate `stocks` and `etfs` tables enriched via Alpha Vantage (stocks) and etf.com (ETFs). Legacy ingestion code in the main backend.
- **New (`ingestion` schema):** Unified `instruments` table with 6 types (STOCK, ETF, PREFERRED_STOCK, MUTUAL_FUND, INDEX, BOND) and `provider_raw_data` storing full EODHD fundamentals JSON. Managed by the ingestion-service on port 8081.

Currently no cross-schema queries exist — the two systems are completely disconnected. This design migrates all screener and instrument detail functionality to use the new ingestion data, removes all old tables/code, and adds screeners for new instrument types.

## Data Inventory

**Instruments with fundamentals data:**
- STOCK: 16,171 (99% have Highlights, Valuation, Financials, Technicals, Earnings, SplitsDividends, SharesStats)
- ETF: 3,056 (91% have ETF_Data, 97% have Technicals)
- MUTUAL_FUND: 317 (100% have MutualFund_Data)
- PREFERRED_STOCK, INDEX, BOND: 0 fundamentals (basic instrument data only)

**Analyst Ratings:** Available for 3,519 stocks (22%) — show conditionally.

**EODHD Fundamentals sections per type:**

| Section | STOCK | ETF | MUTUAL_FUND |
|---------|:-----:|:---:|:-----------:|
| General | Yes | Yes | Yes |
| Highlights | Yes | — | — |
| Valuation | Yes | — | — |
| Technicals | Yes | Yes | — |
| Financials (Income/Balance/CashFlow) | Yes | — | — |
| Earnings (History/Annual/Trend) | Yes | — | — |
| SplitsDividends | Yes | — | — |
| SharesStats/Holders | Yes | — | — |
| ETF_Data | — | Yes | — |
| MutualFund_Data | — | — | Yes |
| AnalystRatings | Partial | — | — |

## Architecture Decisions

### 1. Data Access: Cross-Schema Queries (Option A)
The main backend reads directly from `ingestion.instruments` and `ingestion.provider_raw_data` using PostgreSQL JSONB queries. No new tables in `public` schema, no sync process.

### 2. Old Code Removal: Full Clean Break
Drop old `stocks`, `etfs`, `etf_holdings` tables via Flyway migration. Delete all legacy entities, repositories, services, controllers, and ingestion code (Alpha Vantage, etf.com, EODHD client in main backend, IngestionOrchestrator). Migrate all broker/portfolio dependencies (`BrokerPosition`, `LookThroughService`, `DriftCalculationService`, etc.) to use `ingestion` schema.

### 3. Instrument Types: All Six
Screeners for STOCK, ETF, MUTUAL_FUND, PREFERRED_STOCK, INDEX, BOND. Sparse types (PREFERRED_STOCK, INDEX, BOND) show basic columns only.

### 4. Data Depth: Lean Screeners, Rich Detail Pages
Screener columns use basic filterable fields. Detail pages show everything available in the raw EODHD payload with charts and visualizations.

## Screener Design

### Layout: Hybrid Sidebar + Inline Filters
- **Sidebar navigation** for instrument type selection (collapsible — full text when expanded, icon-only when collapsed, matching existing app sidebar behavior)
- **Counts** shown next to each type in the sidebar
- **Inline filters** above the grid (dropdowns + search + Apply/Reset)
- **AG Grid** for the data table with sortable/filterable columns
- **Pagination** at the bottom
- **Type-specific routes:** `/screener/stocks`, `/screener/etfs`, `/screener/mutual-funds`, `/screener/preferred-stocks`, `/screener/indices`, `/screener/bonds`
- **Shared component:** One `ScreenerPage` component with type-specific column definitions and filter configs

### Screener Columns Per Type

| Type | Columns |
|------|---------|
| **STOCK** | Ticker, Name, Sector, Country, Market Cap, P/E, EPS, Dividend Yield, 52wk Range, Beta |
| **ETF** | Ticker, Name, Issuer, Asset Class, Expense Ratio, Yield, Total Assets, Holdings Count, 1Y Return |
| **MUTUAL_FUND** | Ticker, Name, Fund Category, Fund Style, Expense Ratio, Yield, NAV, 1Y Return |
| **PREFERRED_STOCK / INDEX / BOND** | Ticker, Name, Exchange, Currency, Country |

### Screener Filters Per Type

| Type | Filter Fields |
|------|---------------|
| **STOCK** | Sector (dropdown), Country (dropdown), Ticker contains, Name contains |
| **ETF** | Issuer (dropdown from ETF_Data.Company_Name), Asset Class (dropdown), Ticker/Name search |
| **MUTUAL_FUND** | Fund Category (dropdown), Fund Style (dropdown), Ticker/Name search |
| **Sparse types** | Exchange (dropdown), Country (dropdown), Ticker/Name search |

### Data Sources for Screener Columns

**STOCK columns from JSONB:**
- Market Cap: `raw_payload->'Highlights'->>'MarketCapitalizationMln'`
- P/E: `raw_payload->'Highlights'->>'PERatio'`
- EPS: `raw_payload->'Highlights'->>'DilutedEpsTTM'`
- Div Yield: `raw_payload->'Highlights'->>'DividendYield'`
- 52wk High/Low: `raw_payload->'Technicals'->>'52WeekHigh'`, `->>'52WeekLow'`
- Beta: `raw_payload->'Technicals'->>'Beta'`
- Sector: `raw_payload->'General'->>'GicSector'`
- Country: `raw_payload->'General'->>'CountryISO'`

**ETF columns from JSONB:**
- Issuer: `raw_payload->'ETF_Data'->>'Company_Name'`
- Asset Class: `raw_payload->'ETF_Data'->>'Asset_Category'`
- Expense Ratio: `raw_payload->'ETF_Data'->>'NetExpenseRatio'`
- Yield: `raw_payload->'ETF_Data'->>'Yield'`
- Total Assets: `raw_payload->'ETF_Data'->>'TotalAssets'`
- Holdings Count: `raw_payload->'ETF_Data'->'Holdings_Count'`
- 1Y Return: `raw_payload->'ETF_Data'->'Performance'->>'Returns_1Y'`

**MUTUAL_FUND columns from JSONB:**
- Fund Category: `raw_payload->'MutualFund_Data'->>'Fund_Category'`
- Fund Style: `raw_payload->'MutualFund_Data'->>'Fund_Style'`
- Expense Ratio: `raw_payload->'MutualFund_Data'->>'Expense_Ratio'`
- Yield: `raw_payload->'MutualFund_Data'->>'Yield'`
- NAV: `raw_payload->'MutualFund_Data'->>'Nav'`
- 1Y Return: `raw_payload->'MutualFund_Data'->>'Yield_1Year_YTD'`

## Detail Page Design

### Layout: Hybrid — Unified Component, Type-Specific Routes
- **Routes:** `/instruments/stock/:ticker`, `/instruments/etf/:ticker`, `/instruments/mutual-fund/:ticker`, etc.
- **Shared component:** `InstrumentDetailPage` with common header, hero metrics, section nav
- **Type-specific sections:** Loaded conditionally based on instrument type
- **All charts use AG Charts** (already in the project) with **hover tooltips enabled**

### Stock Detail Page Sections

1. **Header:** Name, ticker badge, type badge, GICS classification, exchange, currency, ISIN
2. **Hero Metrics (6 cards):** Market Cap, P/E (with forward), EPS (with YoY), Div Yield, Beta, 52-Week Range (visual range bar with position marker)
3. **Section Nav:** Overview, Financials, Valuation, Technicals, Dividends, Ownership, Earnings
4. **About:** Description, CEO (from Officers), Employees, IPO Date, Fiscal Year End
5. **Financials (2-column):**
   - Left: **Stacked revenue breakdown bar chart** (Gross Profit / Operating Income / Net Income / Costs) over 5 years, annual/quarterly toggle
   - Right: **YoY change table** with Income Statement / Balance Sheet / Cash Flow tabs, multi-year values with colored growth badges
6. **Valuation (2-column):**
   - Left: **Horizontal bar chart** for valuation multiples (Trailing PE, Forward PE, P/B, P/S, EV/Revenue, EV/EBITDA)
   - Right: **Margin trend sparklines** (Profit Margin, Operating Margin, Gross Margin, ROE, ROA)
7. **Technicals (full-width):**
   - Left: Key metrics table (Beta, 50-Day MA, 200-Day MA, Short Ratio)
   - Right: **52-week range visualization** with gradient bar, current price marker, 50-day MA marker, 200-day MA marker
8. **Dividends & Ownership (2-column):**
   - Left: **Dividend history bar chart** (by year from NumberDividendsByYear) + Payout Ratio, Ex-Dividend Date, Forward Rate/Yield
   - Right: **Ownership donut chart** (Institutions, Insiders, Public Float) with short interest stat
9. **Analyst Ratings (conditional):** Show only when data exists (3,519 stocks). Strong Buy/Buy/Hold/Sell/Strong Sell + Target Price.

### ETF Detail Page Sections

1. **Header:** Name, ticker badge, type badge, issuer, asset class, exchange, currency, ISIN
2. **Hero Metrics (6 cards):** Total Assets, Expense Ratio, Yield (with frequency), Holdings Count, Inception Date, Turnover
3. **Section Nav:** Overview, Performance, Holdings, Sectors, Regions, Valuation
4. **Performance (2-column):**
   - Left: **Returns bar chart** (YTD, 1Y, 3Y, 5Y, 10Y)
   - Right: **Risk profile cards** (1Y Volatility, 3Y Volatility, 3Y Sharpe Ratio, Beta) with visual gauge bars
5. **Top Holdings (2-column):**
   - Left: **Horizontal bar chart** for top 10 holdings with weight % + "Other" remainder
   - Right: **Market cap breakdown donut** (Giant, Large, Medium, Small, Micro) with average market cap
6. **Sector & Geographic (2-column):**
   - Left: **Sector weights donut chart** (11 sectors)
   - Right: **Geographic exposure donut chart** (10 regions — no country-level data for ETFs)
7. **Valuation & Asset Allocation (2-column):**
   - Left: **Valuation butterfly table** — Portfolio vs Category with mirror bar chart (portfolio value left, category right, center divider)
   - Right: **Asset allocation stacked bar** (US Stock, Non-US Stock, Cash, Other) + Growth rates table

### Mutual Fund Detail Page Sections

1. **Header:** Name, ticker badge, type badge, fund category, fund style, currency
2. **Hero Metrics (6 cards):** NAV, Expense Ratio, Yield, Holdings Count (from Top_Holdings), Inception Date, Update Date
3. **Section Nav:** Overview, Performance, Holdings, Sectors, Regions, Valuation
4. **Performance (2-column):**
   - Left: **Returns bar chart** (1Y, 3Y, 5Y from Yield_1Year_YTD, Yield_3Year_YTD, Yield_5Year_YTD)
   - Right: **Fund info cards** (Fund Category, Fund Style, Morning Star Rating/Category if available, Portfolio Net Assets)
5. **Top Holdings (2-column):**
   - Left: **Horizontal bar chart** for top 10 holdings (from MutualFund_Data.Top_Holdings — Name + Weight)
   - Right: **Market cap breakdown donut** (Giant, Large, Medium, Small, Micro from Market_Capitalization)
6. **Sector & Geographic (2-column):**
   - Left: **Sector weights donut chart** (from MutualFund_Data.Sector_Weights — Cyclical, Defensive, Sensitive sub-categories)
   - Right: **Geographic exposure concentric chart** — Regions as **inner solid pie** (no hole) + Top Countries as **outer donut ring**. Legends aligned with "Regions (inner)" and "Top Countries (outer)" labels.
7. **Valuation & Asset Allocation (2-column):**
   - Left: **Valuation butterfly table** (same format as ETF) — Portfolio vs Benchmark with mirror bars and center divider. Category Average shown as a compact reference row below the main table.
   - Right: **Asset allocation stacked bar** (Cash, US Stock, Non-US Stock, Bond, Other from Asset_Allocation) + Growth rates

### Sparse Type Detail Pages (PREFERRED_STOCK, INDEX, BOND)
- Header with basic instrument info
- General section from EODHD (if available)
- Technicals (if available)
- Minimal layout — will expand as data becomes available

## Backend Changes

### New (cross-schema access)
- `InstrumentQueryRepository` — Uses `@Query(nativeQuery = true)` to query `ingestion.instruments` joined with `ingestion.provider_raw_data` for JSONB fields (no JPA entity mapping for ingestion tables — all native SQL)
- `InstrumentScreenerService` — Replaces `ScreenerService`. Uses native SQL queries with JSONB operators for filtering/sorting/pagination
- `InstrumentDetailService` — Fetches full fundamentals payload and maps to DTOs
- `InstrumentController` — Replaces `StockController` and `EtfController`. Unified endpoints for all 6 types
- `ReferenceDataService` — Updated to derive sectors, countries, issuers, asset classes from `ingestion.provider_raw_data` JSONB instead of old tables
- DTOs for screener responses and detail page data (per-type sections)

### Removed
- `Stock`, `Etf`, `EtfHolding` entities
- `StockRepository`, `EtfRepository`, `EtfHoldingRepository`
- `StockController`, `EtfController`
- `ScreenerService`, `HoldingsService`
- `InstrumentSearchService` (replaced — search now queries ingestion schema)
- `IngestionOrchestrator`, `StockIngestionController`, `StockEnrichmentController`, `EtfComController`
- All Alpha Vantage and etf.com client/service code
- Backend EODHD client (`backend/src/main/kotlin/com/portfolio/ingestion/client/EodhdClient.kt`)
- GICS tables (sectors, industry groups, industries, sub-industries, aliases) — GICS data now comes from EODHD General section
- `countries`, `regions` tables — country/region data from EODHD General section
- `data_sources`, `ingestion_batches`, `ingestion_runs/steps/errors` (main backend copies) — observability handled by ingestion-service
- `EtfSectorAllocationFactset` and related tables

### Migrated (dependencies on old entities)
- `BrokerPosition` — currently links to `Stock` entity via FK. Must be updated to store ticker + exchange as string fields and resolve instruments via cross-schema lookup (no cross-schema FKs)
- `LookThroughService` — ETF decomposition. Must read holdings from `ingestion.provider_raw_data` ETF_Data.Holdings
- `DriftCalculationService` — uses positions which reference stocks. Must use new instrument references
- `PositionFetchService` — maps SnapTrade positions to instruments. Must look up via ingestion schema
- `DashboardDataService` and sub-services — reference old entities for position data
- `ActivityIngestionService` — maps activities to stocks/ETFs
- `BenchmarkService` — references stock data
- Portfolio group and model portfolio logic — references instruments

### Flyway Migration
- New migration to drop old tables: `stocks`, `etfs`, `etf_holdings`, `etf_sector_allocations_factset`, `gics_sectors`, `gics_industry_groups`, `gics_industries`, `gics_sub_industries`, `gics_sector_aliases`, `gics_sub_industry_aliases`, `countries`, `regions`, `data_sources`, `ingestion_batches`, and main-backend `ingestion_runs/steps/errors` tables
- Ensure `ingestion` schema is accessible from the main backend's DB connection

## Frontend Changes

### New Components
- `ScreenerPage` — shared screener shell with sidebar nav, filters, and AG Grid
- `ScreenerSidebar` — instrument type navigation (collapsible)
- `ScreenerFilters` — type-aware filter panel (reuses existing patterns)
- `InstrumentDetailPage` — unified detail page shell
- `StockDetailSections` — stock-specific chart sections (financials, valuation, technicals, dividends, ownership)
- `EtfDetailSections` — ETF-specific chart sections (performance, holdings, sectors, regions, valuation)
- `MutualFundDetailSections` — mutual fund-specific sections
- `BasicDetailSections` — sparse type fallback sections
- Chart components using AG Charts for all visualizations

### Updated
- `App.tsx` — new routes: `/screener/:type`, `/instruments/:type/:ticker`
- Sidebar navigation — update screener links
- `InstrumentSearchAutocomplete` — search against new backend endpoints
- `portfolioStore` — update instrument references

### Removed
- `StockScreenerPage`, `EtfScreenerPage`
- `StockDetailPage`, `EtfDetailPage`
- Old screener hooks (`useStockScreener`, `useEtfScreener`)
- Old services (`getStocks`, `getEtfs`, `getEtfHoldings`)
- Old types (`Stock`, `Etf` interfaces)

### New Services & Hooks
- `instrumentService.ts` — unified API for all instrument types (screener, detail, search)
- `useInstrumentScreener(type, filter, page, size, sort)` — replaces `useStockScreener`/`useEtfScreener`
- `useInstrumentDetail(type, ticker)` — fetches full detail data
- `useInstrumentReferenceData(type)` — type-specific reference data (sectors for stocks, issuers for ETFs, etc.)

## Verification Plan

### Backend
1. `docker compose exec backend ./gradlew test` — all tests pass
2. Test cross-schema queries work: screener filtering, detail page data loading
3. Verify JSONB query performance with 16K+ stocks
4. Verify broker position and portfolio logic still works after entity migration

### Frontend
1. `npm run test:run` — all tests pass
2. `npm run lint` — no lint errors
3. `npm run build` — production build succeeds
4. Manual testing:
   - Navigate to each screener type, verify columns and filters
   - Click into detail pages for stock, ETF, mutual fund
   - Verify all charts render with real data and hover tooltips work
   - Test search autocomplete
   - Test "Add to Portfolio" flow
   - Test pagination and sorting in screeners
   - Verify sidebar collapse behavior in screener

### Integration
1. Full docker compose stack starts without errors
2. Flyway migration runs cleanly
3. All existing dashboard widgets still function
4. Broker connections and position syncing still work
