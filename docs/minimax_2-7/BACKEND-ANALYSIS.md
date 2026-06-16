# Backend Analysis

> Deep dive into each backend service: modules, endpoints, dependencies, redundancies, and disconnected code.

---

## 1. Service Dependency Graph

```
                     ┌─────────────┐
                     │   common    │
                     │  (library)  │
                     └──────┬──────┘
           ┌────────────┼────────────┐
           ▼            ▼            ▼
     ┌──────────┐ ┌──────────┐ ┌──────────┐
     │ market-  │ │ strategy │ │ broker-  │
     │ data     │ │          │ │ gateway  │
     └──────────┘ └──────────┘ └──────────┘
                                     │
                                     ▼
                               ┌──────────┐
                               │portfolio │
                               │ (main)   │
                               └──────────┘
                                     │
                                     ▼
                               ┌──────────┐
                               │ingestion │
                               └──────────┘
```

Note: `portfolio` does NOT depend on `common` — it has its own independent domain model.

---

## 2. Common Library (`backend/common/`)

### Files: 9 source + 3 test

### Strengths
- Zero runtime dependencies (only JUnit + AssertJ for tests)
- Clean, focused domain objects used consistently by consumers
- Black-Scholes implementation is complete (pricing, all Greeks, implied vol)
- TradingCalendar correctly handles US market hours and day-of-week logic

### Disconnected / Redundant
- **`broker-gateway` declares dependency but never uses it** — `build.gradle.kts` has `implementation("com.portfolio:common")` but no `.kt` file in broker-gateway imports from `com.portfolio.common`. The dependency is dead weight in the build.
- **`strategy` declares dependency but never uses it** — No imports from `com.portfolio.common` found in strategy service code.

### Redundancies
- **`portfolio` service has its own domain model** that partially overlaps with `common` (e.g., `Quote`, `Greeks` concepts). The portfolio service does not depend on `common` at all, resulting in parallel type hierarchies.

---

## 3. Portfolio Service (`backend/portfolio/`)

### Files: 131 source + 22 test

### Subsystem Breakdown

| Subsystem | Source Files | Controllers | Services | Entities | Repos | Endpoints |
|-----------|-------------|-------------|----------|----------|-------|-----------|
| Auth | 24 | 1 | 6 | 9 | 9 | 6 |
| Broker | 80+ | 8 | 17 | 22 | 22 | 60+ |
| Core | 14 | 4 | 6 | - | 2 | 12 |
| Config/Health | 4 | - | - | - | - | - |

### Strengths
- Well-organized into sub-packages (auth, broker, config, controller, etc.)
- Comprehensive test coverage for critical services
- Clean separation of concerns: controller → service → repository
- Proper DTO layer (entities never exposed directly in responses)
- Global exception handler with ProblemDetail (RFC 7807)

### Redundancies
1. **`RebalanceScheduler.kt` — 4 unused imports**: `OrderExecutionService`, `ExecuteTradesRequest`, `TradeExecutionInput`, `java.util.UUID`
2. **`IngestionServiceHealthIndicator`** — only cross-service health check; otherwise no portfolio↔ingestion communication

### Disconnected Code
- The broker subsystem is very large (80+ files, 17 services) — it handles portfolio groups, trading, dashboard, performance, notifications, exchange rates all under `broker/` package, mixing domains

---

## 4. Ingestion Service (`backend/ingestion/`)

### Files: 42 source + 0 test

### Strengths
- Clean pipeline architecture (ExchangeSync → UniverseSync → RawDataFetch)
- Extensible provider framework (`DataProvider` interface, `ProviderRegistry`)
- Rate-limited batch processing with quota tracking
- SHA-256 hash deduplication via Redis
- Comprehensive run/step/error tracking

### Disconnected / Redundant
1. **Only one data provider implemented** — `DataProvider` interface supports multiple providers but only `EodhdProvider` exists
2. **No unit tests** — Zero test files
3. **Single point of failure** — EODHD is the only data source
4. **Stale data threshold** — `stale-threshold-days: 7` means data older than 7 days is re-fetched; could be optimized

---

## 5. Market Data Service (`backend/market-data/`)

### Files: 29 source + 1 test

### Strengths
- Full IBKR TWS API integration with async request/response pattern
- Multi-tier contract resolution (memory → Redis → DB → IBKR)
- WebSocket streaming with reference counting and LRU eviction
- Proper reconnection logic with exponential backoff

### Disconnected / Redundant
1. **Extremely thin test coverage** — Only 1 test file for 29 source files (~3%)
2. **IBKR dependency is hard** — Cannot function without a running TWS/IB Gateway
3. **No fallback data provider** — If IBKR is unavailable, no alternative data source
4. **`maxSubscriptions: 100`** — Hard limit on concurrent streaming subscriptions

---

## 6. Strategy Service (`backend/strategy/`)

### Files: 13 source + 0 test

### Strengths
- Clean strategy calculator with PnL curves, break-even, risk/reward
- Education engine with strategy descriptions and risk warnings

### Disconnected / Redundant
1. **`@EnableScheduling` with zero `@Scheduled` methods** — Annotation present, no scheduled tasks
2. **`spring-boot-starter-data-redis` unused** — Redis configured but no code uses it
3. **No tests** — `src/test/` directory does not exist
4. **Wheel strategy DB schema exists but no code** — 9 wheel tables in migration but `StrategyType` enum has no WHEEL entry
5. **External service URLs not consumed** — `market-data-url` and `portfolio-url` configured but no HTTP calls
6. **No JPA entities** — All models are plain `data class`es
7. **Calculator limitations** — Only delta computed (gamma, theta, vega = zero), probability of profit = null
8. **Strategy orders/positions tables unused** — `strategy.orders`, `strategy.order_legs`, `strategy.executions`, `strategy.positions`, `strategy.position_legs` tables have no repositories
9. **`strategy.admin_actions` table unused** — No corresponding service or controller

---

## 7. Broker Gateway Service (`backend/broker-gateway/`)

### Files: 38 source + 13 test

### Strengths
- Clean adapter pattern — each broker separate `BrokerAdapter` implementation
- Questrade, IBKR, Wealthsimple all fully implemented
- AES-256-GCM credential encryption
- Scheduled token refresh with failure escalation
- Good test coverage (13 test files)

### Disconnected / Redundant
1. **`common` dependency declared but unused** — Zero imports from `com.portfolio.common`
2. **API key not validated** — `GATEWAY_API_KEY` configured but no filter enforces it
3. **Hardcoded Wealthsimple client ID** — Real client ID as default env var value in `WealthsimpleConfig.kt`
4. **Wealthsimple unofficial GraphQL API** — May break without notice, TOS concerns
5. **Shared database with portfolio** — `broker_gateway` schema in same PostgreSQL instance creates tight coupling

---

## 8. Cross-Cutting Redundancies

### 8.1 Duplicate Type Hierarchies

| Concept | `common` Library | `portfolio` Service |
|---------|-----------------|-------------------|
| `Greeks` | `com.portfolio.common.domain.Greeks` | Own greeks in broker entities (duplicated) |

### 8.2 Duplicate Configurations

Each of 5 backend services has nearly identical Spring profiles:
- `application.yml` (base)
- `application-uat.yml` (framework + log levels)
- `application-prod.yml` (framework + log levels)

### 8.3 Duplicate Dockerfiles

All 5 Dockerfiles share ~90% identical content. Only differences:
- Port number
- `common/` module copy (3 of 5)
- Invesdwin SSL cert import (2 of 5 for TWS API)

### 8.4 Snaptrade References Remain After Cleanup

- `strategy.orders` table has `snaptrade_order_id VARCHAR(100)` column
- Snaptrade removed from portfolio via V72__gateway_connection_cleanup_snaptrade.sql and V73__remove_snaptrade_columns.sql
- But strategy schema still references it

### 8.5 AlphaVantage / SeekingAlpha Migration Remnants

- V26__add_av_ingestion_step_names.sql — AlphaVantage step names
- V18__alpha_vantage_enrichment.sql, V21__drop_seekingalpha_columns.sql, V13__seekingalpha_enrichment_tracking.sql
- These providers appear to have been removed but migration traces remain