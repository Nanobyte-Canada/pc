# Project Memory

## Project: Portfolio Construction App
- Monorepo: backend (Kotlin/Spring Boot), frontend (React/TypeScript/Vite)
- DB: PostgreSQL with Flyway migrations (currently at V51)
- Broker integration: SnapTrade SDK
- Auth: Spring Security + JWT

## Key Architecture
- Backend: Spring Boot 3.3.5, Kotlin 2.0.21, JPA/Hibernate
- Frontend: React 18, Vite, React Query, Zustand, AG Grid/Charts
- UI: Custom CSS files + CSS custom properties (Tailwind removed). Shadcn-style components in `components/ui/` with companion `.css` files
- Path alias: `@/` maps to `./src/` in frontend

## Dashboard System (New)
- 15 modular, toggleable widget components in `frontend/src/components/dashboard/widgets/`
- Widget preferences stored in `dashboard_preferences` table (V47 migration)
- Each widget has its own API endpoint under `/api/v1/dashboard/...`
- DashboardGrid renders visible widgets based on user preferences
- DashboardEditMode dialog for toggling widget visibility
- Per-account dashboards via `/brokers/accounts/:connectionId` route

## Ingestion Pipeline (V48-V51 Refactor)
- **Mutual Funds removed**: No MF entity, repository, controller, or screener page
- **Stock deduplication**: One record per ticker (no exchange field). `exchange` column dropped in V49
- **`exchangeCode` field kept** on Stock for display (EODHD exchange string like "NYSE", "TSX")
- **Redis hash cache**: `IngestionHashCacheService` — SHA-256 payload change detection, 36h TTL
  - Key pattern: `ingestion:stock:{ticker}:hash`, `ingestion:etf:{symbol}:hash`
  - Fallback on Redis failure: treat as changed (safe default)
- **GICS FK resolution**: `GicsLookupService` maps AV sector/industry strings → gics_sub_industry FK
  - Uses `gics_sector_aliases` and `gics_sub_industry_aliases` tables with `source='ALPHA_VANTAGE'`
  - Cached via Spring Cache (`gicsLookup` cache name)
- **Stub stock creation**: Unresolved ETF holdings create stub Stock records (PENDING status) for next AV run
  - Guard: only valid tickers (1–5 alphanumeric chars, regex `^[A-Z0-9]{1,5}$`)
- **Exchange scope**: US, TO, V only (MX removed)

## Stock Entity Changes (V49)
- `exchange: String` field REMOVED (no DB column)
- `exchangeCode: String?` KEPT (display-only EODHD exchange string)
- Unique constraint changed from `uq_stocks_ticker_exchange` to `uq_stocks_ticker`

## Key Backend Services
- `DashboardDataService` - orchestrates all widget data endpoints
- `LookThroughService` - ETF decomposition into underlying stocks (MF removed)
- `DriftCalculationService` - portfolio accuracy calculation
- `ReportingService` - fees, dividends, activities aggregation
- `PositionFetchService` - async position fetch from SnapTrade
- `GicsLookupService` - AV sector/industry string → GICS FK resolution (NEW, V51)
- `IngestionHashCacheService` - Redis-backed change detection (NEW)

## Admin Endpoints (New)
- `GET /admin/ingestion/stats` — pipeline statistics (stocks/ETFs enriched/pending, errors 24h)
- `GET /admin/ingestion/errors/summary` — error counts by type, last 24h
- `GET /admin/ingestion/errors?stepName=X&errorType=Y&limit=100` — filtered errors
- `GET /admin/ingestion/runs/{id}/steps` — step breakdown for a run (lazy-loadable)
- `POST /admin/ingestion/run/universe` — correct URL (was `/admin/ingestion/universe` in old frontend)

## Important Entity Relationships
- BrokerConnection -> BrokerPosition (one-to-many)
- BrokerConnection -> Broker (many-to-one, nullable)
- PortfolioGroup -> PortfolioTarget (one-to-many)
- PortfolioGroup -> PortfolioGroupAccount -> BrokerConnection
- Stock -> GicsSubIndustry -> GicsIndustry -> GicsIndustryGroup -> GicsSector
- Country -> Region
- EtfHolding links ETFs to Stocks (no more MF)

## File Conventions
- Backend DTOs: `broker/dto/` package
- Backend entities: `broker/entity/` or `entity/` packages
- Frontend types: `src/types/` directory
- Frontend services: `src/services/` directory
- Frontend hooks: `src/hooks/` directory

## Infrastructure
- Redis: Added to docker-compose.yml (port 6379, redis:7-alpine), backend depends on it
- `spring-boot-starter-data-redis` added to `build.gradle.kts`
- `spring.data.redis.host/port` in `application.yml` (env var backed)

## Migration Sequence (V48-V51)
- V48: Fix ingestion_steps CHECK constraint (remove AV_ETF_INGESTION, AV_ETF_ENRICHMENT)
- V49: Stock dedup — drop exchange column, rebuild unique key (deploy AFTER backend code)
- V50: Drop mutual_funds and mutual_fund_holdings tables (deploy AFTER backend code)
- V51: Seed GICS aliases for Alpha Vantage sector/industry strings
