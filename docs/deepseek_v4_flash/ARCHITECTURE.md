# Architecture Documentation

> System architecture, inter-service communication, data flow, and deployment topology.

---

## 1. System Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           FRONTEND (React 18 SPA)                        │
│                              Nginx / Vite Dev Server                     │
│                                  Port 3000/80                            │
└──────┬──────────┬──────────────┬──────────────┬──────────────┬───────────┘
       │ /api/*   │ /ws/*        │ /strategy-api│ /market-data │ /ingestion
       │          │              │  (proxy)     │   (proxy)    │  (proxy)
       ▼          ▼              ▼              ▼              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         BACKEND MICROSERVICES                            │
│                                                                          │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐        │
│  │ portfolio  │  │ ingestion  │  │market-data │  │ strategy   │        │
│  │  :8080     │  │  :8081     │  │  :8082     │  │  :8083     │        │
│  │ Main API   │  │ EODHD pipe │  │ IBKR TWS   │  │ Options    │        │
│  │ Auth       │  │ Exchange   │  │ Quotes WS   │  │ Calculator  │        │
│  │ Screener   │  │ Universe   │  │ Chains      │  │ Strategy    │        │
│  │ Portfolio  │  │ Fundaments │  │ Greeks      │  │ DB schema   │        │
│  │ Dashboard  │  └─────┬──────┘  │ IV Rank     │  └──────┬─────┘        │
│  │ Trading    │        │         └──────┬──────┘         │              │
│  │ Reporting  │        │               │                │              │
│  └──────┬─────┘        │               │                │              │
│         │              │               │                │              │
│  ┌──────▼──────────────▼───────────────▼────────────────▼──────┐       │
│  │              broker-gateway  :8084                          │       │
│  │    Unified adapter: Questrade REST / IBKR TWS / WS GraphQL  │       │
│  │    Credential encryption, token refresh, rate limiting       │       │
│  └──────────────────────────┬──────────────────────────────────┘       │
│                             │                                           │
│  ┌──────────────────────────▼──────────────────────────────────┐       │
│  │              common (shared library)                        │       │
│  │    Domain: Quote, Greeks, OptionsChain, OptionType          │       │
│  │    Math: BlackScholes, Money, TradingCalendar               │       │
│  └─────────────────────────────────────────────────────────────┘       │
│                                                                          │
└──────────┬───────────┬──────────────────────┬───────────────────────────┘
           │           │                      │
           ▼           ▼                      ▼
      ┌─────────┐ ┌─────────┐          ┌──────────────┐
      │Postgres │ │  Redis  │          │  IBKR TWS    │
      │   16    │ │    7    │          │  Gateway     │
      │ (Flyway)│ │ (Cache) │          │ (paper.trade)│
      └─────────┘ └─────────┘          └──────────────┘
           │           │                      │
           ▼           ▼                      ▼
      ┌───────────────────────────────────────────┐
      │        External Broker APIs                │
      │  Questrade · Wealthsimple · SnapTrade      │
      └───────────────────────────────────────────┘
```

---

## 2. Inter-Service Communication

### 2.1 Frontend → Backend

| Backend Service | Frontend Proxy Path | Authentication |
|----------------|---------------------|----------------|
| portfolio (8080) | `/api/*` | JWT (HttpOnly cookie) + CSRF token |
| market-data (8082) | `/market-data-api/*` | Vite proxy, no additional auth |
| strategy (8083) | `/strategy-api/*` | Vite proxy, no additional auth |
| ingestion (8081) | `/ingestion-api/*` | Vite proxy, no additional auth |

### 2.2 Backend → Backend

| Source | Target | Protocol | Auth | Purpose |
|--------|--------|----------|------|---------|
| portfolio | broker-gateway | HTTP REST | `GATEWAY_API_KEY` header | Broker CRUD, positions, orders, activities |
| portfolio | ingestion | HTTP REST | None | Health check only |
| strategy | market-data | Configured URL | None | Quote/chain data (not implemented) |
| strategy | portfolio | Configured URL | None | Portfolio data (not implemented) |

### 2.3 Backend → External

| Service | External API | Auth Method |
|---------|-------------|-------------|
| ingestion | EODHD | API key query param (`api_token`) |
| market-data | IBKR TWS | TCP socket to local gateway |
| broker-gateway | Questrade | OAuth2 refresh token (Bearer) |
| broker-gateway | Wealthsimple | OAuth2 refresh token (Bearer) |
| broker-gateway | IBKR | TCP socket to TWS/IB Gateway |

### 2.4 Communication Patterns

- **REST**: Primary pattern for all inter-service and client-server communication
- **WebSocket**: Market data streaming (frontend ↔ market-data at `/ws/quotes`)
- **No event bus / message queue**: All communication is synchronous HTTP
- **No service discovery**: URLs are configured via environment variables
- **No API gateway**: Frontend proxies directly to individual services (Vite dev / Nginx)

---

## 3. Data Flow: Critical Paths

### 3.1 Broker Position Sync

```
[Frontend] → POST /api/v1/brokers/connections/sync
  → [portfolio] BrokerController.syncConnections()
    → BrokerService.syncAllConnections()
      → [portfolio] BrokerGatewayClient.fetchPositions(connId)
        → [broker-gateway] GET /connections/{id}/accounts/{accountId}/positions
          → AdapterRegistry → [QuestradeAdapter/IBKRAdapter/WealthsimpleAdapter]
            → External Broker API
          ← UnifiedPosition[]
        ← UnifiedPosition[]
      → [portfolio] BrokerPositionRepository.saveAll()
        → [portfolio] ActivityIngestionService (async processing)
        → [portfolio] Dashboard cache invalidation
```

### 3.2 Options Chain Streaming

```
[Frontend] WS /ws/quotes → { action: "subscribe_chain", data: { underlying: "SPY" } }
  → [market-data] QuoteWebSocketHandler
    → OptionStreamingService.startStreamingChain("SPY")
      → ContractResolver.resolve("SPY") → [Redis] → [PostgreSQL] → [IBKR TWS]
      → [IBKR TWS] reqSecDefOptParams → expirations
      → [IBKR TWS] reqContractDetails × N expirations → strikes/contracts
      → SubscriptionManager.subscribe(conId) × up to 80 contracts
        → [IBKR TWS] reqMktData for each contract
        ← tickPrice/tickSize/tickOptionComputation events
      → OptionQuoteNormalizer.processTick() → accumulated quote
      → GreeksCalculator.computeGreeks() → BlackScholes deltas
      → QuoteWebSocketHandler.broadcastOptionQuote()
        → JSON → subscribed WebSocket sessions
```

### 3.3 Data Ingestion Pipeline

```
[Scheduler 22:00 daily] or [Admin POST /admin/ingestion/run]
  → IngestionOrchestrator.runFullIngestion()
    → ExchangeSyncStep → EodhdClient.fetchExchanges()
      → GET /exchanges-list/?api_token=KEY&fmt=json
      → ExchangeRepository.upsert() × N
    → UniverseSyncStep
      → For each target exchange [US, TO, V, INDX, GBOND]:
        → EodhdClient.fetchUniverse(exchange)
          → GET /exchange-symbol-list/{exchange}?api_token=KEY&fmt=json
        → InstrumentRepository.dedupAndSave()
    → RawDataFetchStep
      → For batch of stale instruments (batch size 50):
        → HashCacheService.isUnchanged() → Redis check
        → GET /fundamentals/{ticker}.{exchange}?api_token=KEY&fmt=json
        → ProviderRawDataRepository.upsert() (if hash changed)
```

---

## 4. Deployment Architecture

### 4.1 Local Development

```
docker-compose.yml (root)
├── redis:7-alpine (6379)
├── postgres:16-alpine (5432)
├── ib-gateway:10.30.1t (4002, paper mode)
├── backend (8080, debug 5005) ← builds from source
├── ingestion (8081) ← builds from source
├── market-data (8082) ← builds from source
├── strategy (8083) ← builds from source
├── broker-gateway (8084) ← builds from source
└── frontend (3000, Vite HMR) ← builds from source
```

### 4.2 Production

```
VPS Server
├── Cloudflare Tunnel → public internet
├── prod docker-compose → ghcr.io images (backends on ports 10080-10084, frontend :10000)
├── uat docker-compose → ghcr.io images (backends on ports 20080-20084, frontend :20000)
├── monitoring docker-compose → Prometheus, Grafana, Loki, Vault, exporters
└── shared docker-compose → Live IB Gateway (:14001)
```

### 4.3 CI/CD Pipeline

```
[Push to main] → GitHub Actions (build.yml)
  → test-backend: Gradle test × 5 services
  → test-frontend: npm ci → lint → test → build
  → Build 7 Docker images → Push to ghcr.io → Slack notification

[Manual dispatch] → GitHub Actions (deploy.yml)
  → Fetch secrets from Vault → SSH via cloudflared tunnel
  → docker compose pull && up -d → Verify health → Slack
```

---

## 5. Database Architecture

### 5.1 Schema Overview

| Schema | Service | Tables | Purpose |
|--------|---------|--------|---------|
| `public` | portfolio | ~53 | Core: auth, broker, portfolio, trading, instruments, notifications |
| `ingestion` | ingestion | 7 | EODHD pipeline: exchanges, instruments, runs |
| `market_data` | market-data | 4 | Contract cache, quotes, IV observations |
| `strategy` | strategy | 9+ | Orders, positions, wheel accounts |
| `broker_gateway` | broker-gateway | 1 | Encrypted broker credentials |

### 5.2 Migration Management

- All schemas use Flyway, Hibernate DDL = `validate`
- `backend/portfolio/.../db/migration/` — V1 through V74
- `backend/ingestion/.../db/migration/` — V1 through V4
- `backend/market-data/.../db/migration/` — V1 through V2
- `backend/strategy/.../db/migration/` — V1
- `backend/broker-gateway/.../db/migration/` — V1 through V2

---

## 6. Security Architecture

### 6.1 Authentication Flow

```
[User] → /auth/google → Google OAuth2 → Authorization code
  → /auth/google/callback
    → Exchange code for tokens
    → Generate JWT (15min access + 7d refresh)
    → Set HttpOnly cookie → Redirect to frontend
```

### 6.2 Security Measures

| Measure | Status |
|---------|--------|
| JWT (HS512, HttpOnly cookie) | ✅ Implemented |
| CSRF token | ✅ Implemented |
| Google OAuth2 | ✅ Implemented |
| Argon2id password hashing | ✅ Implemented |
| AES-256-GCM credential encryption | ✅ Implemented |
| Role-based access (ADMIN) | ✅ Implemented |
| API key validation (broker-gateway) | ❌ Not implemented |
| Rate limiting | ❌ Not implemented |
| MFA | ❌ Not implemented |