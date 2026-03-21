# Portfolio Construction App — Engineering Reference

Monorepo for a portfolio construction and analysis application using public ETFs.
Backend (Kotlin/Spring Boot) + Frontend (React/TypeScript) + PostgreSQL + Redis.

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.0.21 |
| JDK | Temurin | 21 |
| Framework | Spring Boot | 3.3.5 |
| ORM | Hibernate / Spring Data JPA | (managed by Boot) |
| Migrations | Flyway | (managed by Boot) |
| Auth | Spring Security + JWT (jjwt) | 0.12.5 |
| Password hash | Argon2id (BouncyCastle) | 1.77 |
| Broker SDK | SnapTrade | 5.0.168 |
| Resilience | Resilience4j | 2.2.0 |
| Coroutines | kotlinx-coroutines | 1.8.1 |
| Cache | Redis (Spring Data Redis) | 7 (Alpine image) |
| Metrics | Micrometer + Prometheus | (managed by Boot) |
| Frontend | React | 18.3.1 |
| Type system | TypeScript | 5.6.3 |
| Bundler | Vite | 5.4.9 |
| Data fetching | TanStack React Query | 5.60.0 |
| State mgmt | Zustand | 5.0.1 |
| Tables/Charts | AG Grid 32.3.3 + AG Charts 10.3.3 | |
| Router | React Router | 6.28.0 |
| Icons | Lucide React | 0.577.0 |
| CSS | Custom CSS + CSS custom properties | No Tailwind |
| Database | PostgreSQL | 16 (Alpine image) |
| IaC | Terraform | modules in `infra/` |

## Local Environment Constraint

**No JDK/Java is installed on the local machine.** All backend compilation, testing, and validation must be done inside Docker containers. Never run `./gradlew` commands directly on the host. Use docker compose or `docker exec` to run Gradle tasks inside the backend container.

## Commands

### Docker (local full stack)
```bash
docker compose up --build          # Start postgres + redis + backend + frontend
docker compose down                # Stop all
docker compose logs -f backend     # Tail backend logs
```

### Backend validation (inside Docker — no local JDK)
```bash
docker compose exec backend ./gradlew test          # Run tests inside container
docker compose exec backend ./gradlew build          # Build inside container
docker compose run --rm backend ./gradlew test        # Run tests in a one-off container
```
Do NOT run `./gradlew` directly on the host — it will fail (no Java installed).

### Frontend (npm — run from `frontend/`)
```bash
npm install                        # Install dependencies
npm run dev                        # Vite dev server on :3000 (proxies /api, /health, /admin to :8080)
npm run build                      # tsc + vite build → frontend/dist/
npm run test                       # Vitest in watch mode
npm run test:run                   # Vitest single run (CI)
npm run lint                       # ESLint
```

## Architecture

### Backend request flow
```
Client → Spring Security (JWT filter) → Controller → Service → Repository → PostgreSQL
                                                   → SnapTrade adapter → Broker APIs
                                                   → Redis (caching)
```

### Frontend request flow
```
Component → React Query hook (useXxx) → service function → apiFetch() → Backend API
                                                         ↳ auto CSRF token from cookie
                                                         ↳ auto 401 refresh retry
```

### Backend package structure
```
com.portfolio
├── Application.kt
├── auth/           # Authentication: JWT, OAuth2 (Google), password management
│   ├── config/     # SecurityConfig, AuthConfig, CorsConfig
│   ├── controller/ # AuthController (/auth/**)
│   ├── security/   # JwtAuthenticationFilter, JwtTokenProvider
│   ├── entity/     # AppUser, RefreshToken
│   └── service/    # AuthService, UserService, PasswordService
├── broker/         # Brokerage integration via SnapTrade
│   ├── adapter/    # SnapTrade SDK wrapper
│   ├── controller/ # BrokerController, DashboardController, PerformanceController,
│   │               # PortfolioGroupController, TradingController, NotificationController
│   ├── entity/     # BrokerConnection, BrokerPosition, BrokerActivity, PortfolioGroup, etc.
│   ├── scheduler/  # Sync jobs (position refresh, health checks)
│   └── service/    # DashboardDataService, LookThroughService, DriftCalculationService,
│                   # ReportingService, PositionFetchService, PerformanceService
├── controller/     # Core controllers: Health, Version, Stock, Etf, Instrument, ReferenceData, Portfolio
├── entity/         # Core entities: Stock, EtfHolding, GicsSector/Industry, Country, Region
├── ingestion/      # Data pipeline: EODHD, AlphaVantage, ETF.com
│   ├── client/     # HTTP clients for data providers
│   ├── controller/ # AdminIngestionController, StockIngestionController, EtfComController
│   ├── scheduler/  # Scheduled ingestion jobs
│   └── service/    # ActivityIngestionService, enrichment orchestrators
├── repository/     # Spring Data JPA repositories
└── service/        # Core services: StockService, EtfService, InstrumentService
```

### Frontend directory structure
```
frontend/src/
├── components/
│   ├── analytics/        # Portfolio analysis components
│   ├── auth/             # Login, signup, password reset forms
│   ├── broker/           # Broker connection management
│   ├── dashboard/        # DashboardGrid, DashboardEditMode
│   │   └── widgets/      # 15 modular widget components
│   ├── instruments/      # Stock/ETF detail views
│   ├── layout/           # AppLayout, Sidebar, Header
│   ├── notifications/    # Notification center
│   ├── performance/      # Portfolio performance charts
│   ├── portfolio-groups/ # Portfolio group management
│   ├── reporting/        # Fee, dividend, activity reports
│   ├── screener/         # ETF and stock screener tables
│   └── ui/               # Reusable primitives (button, card, dialog, badge, sheet, etc.)
├── config/               # environment.ts
├── hooks/                # 14 custom React Query hooks
├── lib/                  # utils.ts (clsx helper)
├── pages/                # Route-level page components
├── services/             # API service modules (each calls apiFetch)
├── store/                # Zustand: analysisStore, portfolioStore
├── stores/               # Zustand: authStore, themeStore, sidebarStore
└── types/                # TypeScript interfaces and types
```

### Key entity relationships
```
AppUser → BrokerConnection (one-to-many)
BrokerConnection → BrokerPosition (one-to-many)
BrokerConnection → BrokerActivity (one-to-many)
BrokerConnection → Broker (many-to-one, nullable)
PortfolioGroup → PortfolioTarget (one-to-many)
PortfolioGroup → PortfolioGroupAccount → BrokerConnection
Stock → GicsSubIndustry → GicsIndustry → GicsIndustryGroup → GicsSector
Stock → Country → Region
EtfHolding: links ETFs (Stock type=ETF) to underlying Stocks
DashboardPreference → AppUser (per-user widget visibility)
```

## Key Business Logic

- **LookThrough**: Decomposes ETFs into underlying stock holdings for true exposure analysis
- **Drift Calculation**: Compares actual portfolio weights against target allocations
- **Rebalancing**: Generates trade orders to bring portfolio back to target
- **Performance**: Time-weighted return calculation across portfolio groups
- **Ingestion Pipeline**: Multi-source data enrichment (EODHD → AlphaVantage → ETF.com) with retry, rate limiting, and circuit breakers

## API Routes

| Prefix | Controller | Auth |
|--------|-----------|------|
| `GET /health` | HealthController | Public |
| `GET /api/v1/version` | VersionController | Public |
| `/auth/**` | AuthController | Public (login, signup, refresh, OAuth) |
| `/auth/me`, `/auth/profile` | AuthController | Authenticated |
| `/api/v1/stocks` | StockController | Authenticated |
| `/api/v1/etfs` | EtfController | Authenticated |
| `/api/v1/instruments` | InstrumentController | Authenticated |
| `/api/v1/reference` | ReferenceDataController | Authenticated |
| `/api/v1/portfolio` | PortfolioController | Authenticated |
| `/api/v1/brokers` | BrokerController | Authenticated |
| `/api/v1/dashboard` | DashboardController | Authenticated |
| `/api/v1/portfolio-groups` | PortfolioGroupController | Authenticated |
| `/api/v1/portfolio-groups/{id}/performance` | PerformanceController | Authenticated |
| `/api/v1/trading` | TradingController | Authenticated |
| `/api/v1/notifications` | NotificationController | Authenticated |
| `/admin/ingestion/**` | AdminIngestionController, StockIngestionController | Admin role |
| `/admin/enrichment/stocks` | StockEnrichmentController | Admin role |
| `/admin/etfcom` | EtfComController | Admin role |

## Security

- **JWT**: HttpOnly cookies for access + refresh tokens. HS512 signing. Access token: 60min, refresh: 6h.
- **CSRF**: `CookieCsrfTokenRepository` sets `XSRF-TOKEN` cookie; frontend sends `X-XSRF-TOKEN` header. CSRF disabled for auth endpoints and `/api/**`.
- **Password hashing**: Argon2id via BouncyCastle. Min 12 chars, account lockout after 5 failures (30min).
- **Broker token encryption**: AES-256 with `BROKER_ENCRYPTION_KEY` env var.
- **CORS**: Configurable via `CORS_ALLOWED_ORIGINS` env var. Credentials allowed.
- **OAuth2**: Google sign-in integration.
- **Session**: Stateless (no server-side sessions).

## Environment & Configuration

### Spring profiles
| Profile | Purpose | Config file |
|---------|---------|-------------|
| `local` | Local development (default) | `application.yml` + `application-local.yml` |
| `dev` | VPS deployment | `application-dev.yml` |
| `prod` | GCP Cloud Run | `application-prod.yml` |

### Key environment variables
| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | JDBC PostgreSQL connection string |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection |
| `JWT_SIGNING_KEY` | HS512 signing key (min 64 chars) |
| `BROKER_ENCRYPTION_KEY` | AES-256 key (Base64-encoded 32 bytes) |
| `SNAPTRADE_CLIENT_ID` / `SNAPTRADE_CONSUMER_KEY` | SnapTrade API credentials |
| `SNAPTRADE_REDIRECT_URI` | OAuth redirect for broker linking |
| `EODHD_API_KEY` | EODHD market data API key |
| `ALPHA_VANTAGE_API_KEY` | AlphaVantage enrichment API key |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 credentials |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins |
| `VITE_API_URL` | Frontend: backend API base URL |

## Database

- **PostgreSQL 16** with Flyway migrations
- **Hibernate DDL**: `validate` (schema managed exclusively by Flyway)
- **Migration location**: `backend/src/main/resources/db/migration/`
- **Current state**: 52 migration files (V1 through V56, gaps at V4, V5, V19, V20)
- **Naming convention**: `V{number}__{description}.sql` (double underscore)
- **Next migration**: Check the highest existing number and increment by 1

## CI/CD

| Workflow | File | Trigger | Purpose |
|----------|------|---------|---------|
| CI | `ci.yml` | PR to main/development, push to main | Test + build + Docker build verification |
| Deploy GCP | `deploy.yml` | Push to main, manual dispatch | Build → push to Artifact Registry → Cloud Run + Cloud Storage |
| Deploy VPS | `deploy-vps.yml` | Push to development, manual dispatch | Build → SCP → docker compose on VPS (devpc.nanobyte.ca) |

- GCP auth uses **Workload Identity Federation** (no long-lived keys)
- VPS deploy uses **SSH key** secrets for SCP + remote docker compose
- CI is reusable (`workflow_call`) — both deploy workflows invoke it for tests first

## Testing

| Layer | Framework | Command |
|-------|----------|---------|
| Backend unit | JUnit 5 + MockK 1.13.9 | `./gradlew test` |
| Backend integration | Testcontainers (PostgreSQL) | `./gradlew test` |
| Frontend unit | Vitest 2.1.9 + Testing Library + jsdom | `npm run test:run` |

## Role & Workflow

### Backend changes
- Controllers return DTOs, never entities directly
- Services contain business logic; repositories handle data access
- Use constructor injection (Kotlin primary constructor)
- Coroutines for concurrent external API calls (`kotlinx-coroutines`)
- Resilience4j for circuit breakers and rate limiting on external APIs

### Frontend changes
- All API calls go through `apiFetch()` from `services/api.ts` — handles CSRF, credentials, 401 refresh
- Data fetching via React Query hooks in `hooks/` — call service functions from `services/`
- State: React Query for server state, Zustand for client state (auth, theme, sidebar)
- Path alias: `@/` maps to `./src/` (configured in `vite.config.ts`)
- Styling: plain CSS files alongside components, CSS custom properties for theming. No Tailwind.
- UI primitives in `components/ui/` follow shadcn-style patterns with companion `.css` files

### Adding a new page
1. Create page component in `pages/`
2. Add route in `App.tsx`
3. Create service module in `services/` using `apiFetch`
4. Create React Query hook in `hooks/`
5. Add CSS file alongside page component

## Implementation Requirements

- **Schema changes**: Always use Flyway migrations. Never modify Hibernate DDL mode.
- **API calls (frontend)**: Always use `apiFetch()` from `services/api.ts`. Never use raw `fetch`.
- **Tests (backend)**: Use MockK for mocking, not Mockito. Use `@SpringBootTest` with Testcontainers for integration tests.
- **Tests (frontend)**: Use Vitest + Testing Library. Test files sit next to source files (`*.test.ts(x)`).
- **No Tailwind**: Use plain CSS with custom properties. Component styles in companion `.css` files.
- **New entities**: Create entity class, repository interface, service, and Flyway migration.
- **New endpoints**: Add to existing controller or create new one following the `/api/v1/` prefix pattern.
- **Environment config**: Add new env vars to `application.yml` with `${VAR:default}` syntax and document in docker-compose files.

## Mandatory Updates

When adding a new feature, ensure:
- [ ] Flyway migration for any schema changes (increment from highest existing V number)
- [ ] DTO created for API responses (never expose entities)
- [ ] Service layer between controller and repository
- [ ] React Query hook wrapping the service call
- [ ] Tests for backend service logic
- [ ] CSS file for any new UI component
- [ ] Environment variables documented in docker-compose.yml

## Quality Bar

Before committing:
- `docker compose exec backend ./gradlew test` passes (no local JDK — run in container)
- `npm run test:run` passes (from `frontend/`)
- `npm run lint` passes (from `frontend/`)
- `npm run build` succeeds (from `frontend/`)
- No secrets in committed files
- Flyway migration number does not conflict with existing migrations
