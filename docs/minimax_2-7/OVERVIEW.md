# Portfolio Construction App — Module/Feature Overview

> Comprehensive inventory of all modules, services, and features in the repository.
> Generated: June 2026

---

## 1. Repository Layout

```
pc/
├── backend/           # 5 microservices + 1 shared library (Kotlin/Spring Boot)
├── frontend/          # React 18 SPA (TypeScript/Vite)
├── deploy/            # Docker Compose, monitoring, scripts
├── docs/              # Documentation
├── config/            # Environment templates
├── scripts/           # Utility scripts
├── screenshots/       # UI screenshots
├── .github/           # CI/CD workflows
└── docker-compose.yml # Local dev orchestration
```

---

## 2. Backend Services

### 2.1 `common` — Shared Library

| Attribute | Value |
|-----------|-------|
| **Path** | `backend/common/` |
| **Type** | Kotlin library (JAR) |
| **Port** | N/A |
| **Build** | `kotlin("jvm")` 2.0.21, composite build |

**Domain Objects:**
- `Quote` — Real-time stock/ETF quote with bid/ask/last/volume
- `OptionType` — Enum: CALL, PUT
- `OrderBookLevel` / `OrderSide` — Order book level with side
- `Greeks` / `GreeksSource` — Delta, gamma, theta, vega, rho with source tracking
- `OptionQuote` — Full option quote with optional Greeks
- `OptionsChain` / `StrikeData` — Nested map: expiry -> strike -> {call, put}

**Math:**
- `BlackScholes` — Full pricing: `price()`, `delta()`, `gamma()`, `theta()`, `vega()`, `rho()`, `impliedVolatility()` (Newton-Raphson)

**Utilities:**
- `Money` — Extension functions: rounding, currency formatting, safe division, percentage change
- `TradingCalendar` — US market hours, trading days, time-to-expiry calculations

**Consumers:** market-data (heavy), strategy (declared but unused), broker-gateway (declared but unused)

---

### 2.2 `portfolio` — Main API Service

| Attribute | Value |
|-----------|-------|
| **Path** | `backend/portfolio/` |
| **Port** | 8080 |
| **Frameworks** | Spring Boot 3.3.5, Kotlin 2.0.21, JDK 21 |
| **Database** | PostgreSQL 16 + Flyway (70+ migrations), Redis 7 |
| **Test files** | 22 (unit + integration) |

**Sub-modules:**

| Sub-module | Files | Endpoints | Description |
|------------|-------|-----------|-------------|
| **auth** | 24 | 6 | Google OAuth2, JWT (HS512), refresh tokens, password management, email verification, audit logging |
| **broker** | 80+ | 60+ | Broker connections, positions, activities, balance history; portfolio groups, drift, rebalance; trading orders; dashboard (summary, cash, exposure, risk, widgets); performance (TWR, benchmarks); notifications; exchange rates; model portfolios |
| **controller** (core) | 4 | 12 | Health, version, instrument screener (search, filter, detail), portfolio analysis/validate/normalize |
| **service** (core) | 6 | - | Portfolio analysis, risk metrics, look-through, country/region lookup, ingestion instrument lookup |
| **config** | 3 | - | Cache (Redis TTLs), global exception handler, health check MDC filter |
| **health** | 1 | - | Ingestion service health indicator |

**Key Endpoint Groups:**
- `GET /health` — Health check
- `GET /api/v1/version` — App version
- `POST /api/v1/portfolio/analyze|validate|normalize` — Portfolio operations
- `GET /api/v1/screener/*` — Instrument screener (5 endpoints)
- `/auth/*` — Authentication (6 endpoints)
- `/api/v1/brokers/*` — Broker operations (18 endpoints)
- `/api/v1/portfolio-groups/*` — Portfolio groups (19 endpoints)
- `/api/v1/model-portfolios/*` — Model portfolios (7 endpoints)
- `/api/v1/trading/*` — Trade execution (5 endpoints)
- `/api/v1/dashboard/*` — Dashboard data (15 endpoints)
- `/api/v1/portfolio-groups/{id}/performance/*` — Performance (3 endpoints)
- `/api/v1/notifications/*` — Notifications (7 endpoints)
- `/api/v1/exchange-rates/*` — Exchange rates (1 endpoint)

---

### 2.3 `ingestion` — Data Ingestion Service

| Attribute | Value |
|-----------|-------|
| **Path** | `backend/ingestion/` |
| **Port** | 8081 |
| **Data Source** | EODHD (eodhd.com) — exchanges, symbols, fundamentals |
| **Pipeline Steps** | ExchangeSync → UniverseSync → RawDataFetch |
| **Database** | 7 tables in `ingestion` schema, 4 Flyway migrations |
| **Test files** | 0 |

**Features:**
- Scheduled daily ingestion at 10 PM (configurable cron)
- Admin triggers: exchange sync, full ingestion, cancel, stats
- Rate-limited batch processing (4 req/s, 100k daily quota)
- SHA-256 hash deduplication via Redis (36h TTL)
- Extensible provider framework (`DataProvider` interface, `ProviderRegistry`)
- Run/step/error tracking with partial completion support
- EODHD health indicator + quota health indicator

**Endpoints (admin):**
| Method | Path | Description |
|--------|------|-------------|
| POST | `/admin/ingestion/exchanges` | Trigger exchange sync |
| POST | `/admin/ingestion/run` | Trigger full ingestion |
| GET | `/admin/ingestion/active-run` | Get active run |
| POST | `/admin/ingestion/cancel` | Cancel active run |
| GET | `/admin/ingestion/stats` | Ingestion statistics |
| GET | `/admin/ingestion/runs` | List recent runs |
| GET | `/admin/ingestion/runs/{id}/steps` | Get run steps |
| GET | `/admin/ingestion/runs/{id}/errors` | Get run errors |

---

### 2.4 `market-data` — Market Data Service

| Attribute | Value |
|-----------|-------|
| **Path** | `backend/market-data/` |
| **Port** | 8082 |
| **Data Source** | Interactive Brokers TWS API |
| **Streaming** | WebSocket at `/ws/quotes` |
| **Database** | 4 tables in `market_data` schema, Redis cache |
| **Test files** | 1 |

**Features:**
- Real-time stock quotes via IBKR TWS (streaming + snapshot)
- Options chain streaming with Greeks (IBKR or Black-Scholes)
- Multi-tier contract resolution: Memory → Redis → PostgreSQL → IBKR
- Subscription management with LRU eviction (max 100, pinning support)
- IV rank/percentile calculation (365-day lookback)
- Auto-reconnect with exponential backoff (5s initial, 60s max)

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/quotes/{symbol}` | Stock quote |
| GET | `/api/v1/chains/{underlying}` | Options chain |
| GET | `/api/v1/chains/{underlying}/greeks` | Chain with Greeks |
| GET | `/api/v1/chains/{underlying}/expirations` | Available expirations |
| GET | `/api/v1/chains/{underlying}/expiry/{expiry}` | Chain for specific expiry |
| GET | `/api/v1/iv/{ticker}` | IV rank/percentile |
| GET | `/api/v1/health/ibkr` | IBKR connection status |
| WS | `/ws/quotes` | Real-time streaming |

---

### 2.5 `strategy` — Options Strategy Service

| Attribute | Value |
|-----------|-------|
| **Path** | `backend/strategy/` |
| **Port** | 8083 |
| **Strategy Types** | 7 (Bull Call Spread, Bear Put Spread, Bull Put Spread, Bear Call Spread, Iron Condor, Covered Call, Protective Put) |
| **Database** | 9+ tables in `strategy` schema (incl. wheel strategy tables) |
| **Test files** | 0 |

**Features:**
- Strategy calculator: PnL curves, max profit/loss, break-even, risk/reward, net Greeks
- Leg validator: duplicate detection, unified expiry enforcement
- Strategy suggester: filter by outlook (bullish/bearish/neutral)
- Education engine: strategy descriptions, risk explanations, warnings
- **Wheel strategy DB schema exists but no implementation code**

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/strategies` | List all strategies |
| GET | `/api/v1/strategies/{name}` | Strategy info + education |
| POST | `/api/v1/strategies/calculate` | Calculate PnL |
| POST | `/api/v1/strategies/suggest` | Suggest by outlook |

---

### 2.6 `broker-gateway` — Unified Broker Gateway

| Attribute | Value |
|-----------|-------|
| **Path** | `backend/broker-gateway/` |
| **Port** | 8084 |
| **Brokers** | Questrade (REST), IBKR (TWS API), Wealthsimple (GraphQL) |
| **Database** | 1 table in `broker_gateway` schema, Redis cache |
| **Test files** | 13 |

**Broker Integrations:**
| Broker | Auth | API Type | Capabilities |
|--------|------|----------|--------------|
| **Questrade** | OAuth2 refresh token | REST | Accounts, balances, positions, activities, orders, options, practice mode |
| **IBKR** | Persistent TCP socket | TWS API (`com.ib.client`) | Accounts, balances, positions, orders, executions, Flex Queries, options |
| **Wealthsimple** | OAuth2 refresh token | GraphQL (unofficial) | Accounts, balances, positions, activities, orders; rate limited (7/hr) |

**Features:**
- Unified adapter pattern (`BrokerAdapter` interface)
- AES-256-GCM credential encryption at rest
- Auto-token refresh with per-connection ReentrantLock
- Scheduled token refresh every 5 minutes
- Failure escalation: 3 failures → ERROR, 10 failures → EXPIRED
- Auto-retry on 401 with credential refresh

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/gateway/health` | Gateway health |
| GET | `/api/v1/gateway/health/{brokerType}` | Broker-specific health |
| POST | `/api/v1/gateway/connections` | Create connection |
| GET | `/api/v1/gateway/connections` | List connections |
| GET | `/api/v1/gateway/connections/{id}` | Get connection |
| DELETE | `/api/v1/gateway/connections/{id}` | Delete connection |
| POST | `/api/v1/gateway/connections/{id}/validate` | Validate connection |
| POST | `/api/v1/gateway/connections/{id}/refresh` | Force token refresh |
| GET | `.../{connectionId}/accounts` | List accounts |
| GET | `.../{accountId}/balances` | Get balances |
| GET | `.../{accountId}/positions` | Get positions |
| GET | `.../{accountId}/activities` | Get activities |
| GET | `.../{accountId}/orders` | Get orders |
| POST | `.../{accountId}/orders` | Place order |
| DELETE | `.../{accountId}/orders/{orderId}` | Cancel order |
| POST | `.../{accountId}/orders/impact` | Order impact preview |

---

## 3. Frontend (React 18 SPA)

| Attribute | Value |
|-----------|-------|
| **Path** | `frontend/` |
| **Port** | 3000 (dev) / 80 (prod) |
| **Framework** | React 18.3.1, TypeScript 5.6.3, Vite 5.4.9 |
| **State** | Zustand 5 + TanStack React Query 5 |
| **UI** | AG Grid 32, AG Charts 10, lucide-react |
| **Tests** | Vitest (unit), Playwright (E2E) |

**Pages (17):**
| Route | Page | Auth | Description |
|-------|------|------|-------------|
| `/login` | LoginPage | No | Google OAuth login |
| `/` | DashboardPage | Yes | Widget-based dashboard |
| `/portfolios` | PortfolioPage | Yes | Portfolio analysis |
| `/screener/:type` | ScreenerPage | Yes | Instrument screener |
| `/instruments/:type/:ticker` | InstrumentDetailPage | Yes | Instrument details |
| `/analytics` | AnalyticsPage | Yes | Portfolio analytics |
| `/brokers/connections` | BrokerConnectionsPage | Yes | Manage broker connections |
| `/brokers/positions` | BrokerPositionsPage | Yes | View positions |
| `/brokers/positions/:connectionId` | PositionDetailsPage | Yes | Position details |
| `/brokers/accounts/:connectionId` | AccountDetailPage | Yes | Account details |
| `/brokers/reporting` | ReportingPage | Yes | Broker reporting |
| `/options` | OptionsPage | Yes | Options trading |
| `/wheel` | WheelPage | Yes | Wheel strategy |
| `/profile` | ProfilePage | Yes | User profile |
| `/admin` | AdminPage | Yes+ADMIN | Ingestion admin |
| `/unauthorized` | UnauthorizedPage | No | Access denied |

**Services (15):**
| Service | Backend | Endpoints |
|---------|---------|-----------|
| `api.ts` | portfolio | Core fetch wrapper, health, version |
| `authService.ts` | portfolio | Login, logout, refresh, me, profile |
| `portfolioService.ts` | portfolio | Analyze, validate, normalize |
| `brokerService.ts` | portfolio | Broker CRUD, sync, positions, activities |
| `marketDataService.ts` | market-data (proxy) | Quotes, chains, IV rank |
| `tradingService.ts` | portfolio | Execute, cancel, batch orders |
| `screenerService.ts` | portfolio | Search, detail, reference, counts |
| `optionsStrategyService.ts` | strategy (proxy) | Strategies, calculate, suggest, wheel |
| `notificationService.ts` | portfolio | Notifications, preferences |
| `performanceService.ts` | portfolio | Performance summary/chart |
| `adminService.ts` | ingestion (proxy) | Ingestion admin |
| `modelPortfolioService.ts` | portfolio | Model portfolio CRUD |
| `dashboardWidgetService.ts` | portfolio | Dashboard data |

**Hooks (20):** Data fetching hooks wrapping React Query mutations/queries

**Stores (8, 2 directories):**
| Store | Persisted | Purpose |
|-------|-----------|---------|
| `stores/authStore` | Yes | User, JWT, session |
| `stores/quoteStore` | No | Real-time quotes, chains |
| `stores/strategyStore` | No | Options strategy state |
| `stores/toastStore` | No | Toast notifications |
| `stores/themeStore` | Yes | Dark/light mode |
| `stores/sidebarStore` | Yes | **DEAD — never imported** |
| `store/portfolioStore` | No | Portfolio builder state |
| `store/analysisStore` | No | Analysis results |

---

## 4. Infrastructure

| Component | Technology | Details |
|-----------|-----------|---------|
| **Orchestration** | Docker Compose | Local, UAT, Prod, Monitoring, Shared |
| **CI** | GitHub Actions | Test all services → Build & push 7 Docker images to GHCR |
| **CD** | GitHub Actions (manual) | Vault secrets → SCP + SSH → docker compose up |
| **Secrets** | HashiCorp Vault | AppRole auth, deployed via cloudflared tunnel |
| **Ingress** | Cloudflare Tunnel | Secure public access without open ports |
| **Monitoring** | Prometheus + Grafana + Loki | Metrics, dashboards, log aggregation, alerting |
| **Uptime** | Uptime Kuma | External monitoring |
| **Database** | PostgreSQL 16 | 1 instance per environment |
| **Cache** | Redis 7 | 1 instance per environment |

---

## 5. Feature Summary by Business Domain

| Domain | Backend Service | Frontend Pages |
|--------|----------------|----------------|
| **Authentication** | portfolio (auth) | Login, Profile |
| **Portfolio Analysis** | portfolio | Portfolios, Analytics |
| **Instrument Screener** | portfolio | Screener, Instrument Detail |
| **Broker Integration** | portfolio + broker-gateway | Broker Connections, Positions |
| **Dashboard** | portfolio | Dashboard |
| **Trading/Orders** | portfolio + broker-gateway | - |
| **Options Trading** | strategy + market-data | Options |
| **Wheel Strategy** | strategy (DB only) | Wheel |
| **Performance** | portfolio | Reporting |
| **Notifications** | portfolio | - |
| **Model Portfolios** | portfolio | - |
| **Data Ingestion** | ingestion | Admin |
| **Market Data** | market-data | - |
| **Exchange Rates** | portfolio | - |