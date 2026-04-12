# Ingestion Workflow Reference

> Documents two data ingestion pipelines:
> 1. **Ingestion Microservice** -- market data pipeline (instruments, fundamentals) at `backend/ingestion/` (port 8081)
> 2. **Brokerage Data Ingestion** -- broker account data via SnapTrade in the main backend
>
> Cross-references: [backend-services.md](backend-services.md) | [database-schema.md](database-schema.md) | [configurations.md](configurations.md) | [api-endpoints.md](api-endpoints.md)

---

## 1. Ingestion Microservice Pipeline (port 8081)

Separate Spring Boot application at `backend/ingestion/` with its own `ingestion` PostgreSQL schema.

### Architecture

```
                         +-------------------+
                         | IngestionScheduler|  (nightly cron)
                         +--------+----------+
                                  |
                                  v
                    +----------------------------+
                    |   IngestionOrchestrator     |  Coordinates pipeline, prevents concurrent runs
                    +----------------------------+
                         |                  |
                         v                  v
               +-----------+       +-----------------+
               | Universe  |       | Raw Data Fetch  |
               | SyncStep  |       | Step            |
               +-----------+       +-----------------+
                    |                       |
                    v                       v
              +----------+       +---------------------+
              | Provider |       | FundamentalsBatch   |
              | Registry |       | Processor           |
              +----------+       +---------------------+
                    |                       |
                    v                       v
              +----------+          +----------+
              | EODHD    |          | EODHD    |
              | Provider |          | Provider |
              +----------+          +----------+
                    |                       |
                    v                       v
              +-----------+         +-----------+
              |instruments|         |provider_  |
              | (DB)      |         |raw_data   |
              +-----------+         | (DB)      |
                                    +-----------+
```

### Pipeline Steps

#### Step 1: Exchange Sync (`ExchangeSyncStep`)

Only runs when triggered manually via `POST /admin/ingestion/exchanges`. Not part of the nightly pipeline.

**Purpose:** Syncs exchange metadata from EODHD into the `ingestion.exchanges` table.

**Flow:**
1. Calls `DataProvider.fetchExchanges()` via `ProviderRegistry`
2. For each exchange, upserts into `exchanges` table
3. Tracks processed/created/updated/failed counts

**Target exchanges:** US, TO, V, INDX, GBOND (configured in `ingestion.target-exchanges`)

#### Step 2: Universe Sync (`UniverseSyncStep`)

**Purpose:** Discovers all instruments listed on target exchanges.

**Flow:**
1. Iterates over configured target exchanges
2. For each exchange, calls `DataProvider.fetchUniverse(exchange)`
3. EODHD returns all symbols for that exchange with type, name, ISIN, currency, country
4. Maps EODHD types to `InstrumentType` enum:
   - `Common Stock` -> STOCK
   - `Preferred Stock` -> PREFERRED_STOCK
   - `ETF` -> ETF
   - `FUND` -> MUTUAL_FUND
   - `INDEX` -> INDEX
   - `BOND` -> BOND
5. For each instrument:
   - If ISIN exists: upsert by ISIN (handles ticker changes)
   - If no ISIN: upsert by ticker + instrument type
   - Creates `InstrumentExchange` link
   - Updates `source_last_seen_at` timestamp
6. Tracks counts per exchange in step metadata

**Deduplication:** ISIN-based when available, ticker-based fallback. Handles instruments listed on multiple exchanges.

#### Step 3: Raw Data Fetch (`RawDataFetchStep`)

**Purpose:** Fetches fundamentals data for instruments and stores as JSONB.

**Flow:**
1. Queries instruments that need data (stale or never fetched)
2. Delegates to `FundamentalsBatchProcessor` for batch processing
3. Each batch:
   - Calls `DataProvider.fetchFundamentals(ticker, exchange)` for each instrument
   - Stores raw JSON response in `provider_raw_data` table
   - Computes SHA-256 hash for change detection via `HashCacheService`
   - Skips storage if payload hash unchanged (saves DB writes)
4. Rate limiting via `EodhdRateLimiter`:
   - 5 requests per second (token bucket)
   - 100,000 daily quota (each fundamentals call costs 10 API calls)
   - Stops batch when daily quota exhausted

**Batch size:** 500 instruments per batch (configurable via `ingestion.eodhd.batchSize`)

### Strategy Pattern: DataProvider

```kotlin
interface DataProvider {
    fun name(): String
    fun capabilities(): Set<ProviderCapability>
    suspend fun fetchExchanges(): List<RawExchange>
    suspend fun fetchUniverse(exchange: String): List<RawInstrument>
    suspend fun fetchFundamentals(ticker: String, exchange: String): JsonNode?
}
```

**ProviderRegistry:** Discovers all `DataProvider` beans and provides lookup by name or capability.

**Adding a new provider:**
1. Implement `DataProvider` interface
2. Annotate with `@Component`
3. The `ProviderRegistry` auto-discovers it
4. Pipeline steps query the registry for providers with needed capabilities

### Rate Limiting (EodhdRateLimiter)

Token bucket rate limiter with two constraints:

1. **Per-second limit:** 5 requests/second (configurable)
2. **Daily quota:** 100,000 API calls/day
   - Each fundamentals request costs 10 API calls
   - Tracked via `provider_config.requests_used_today`
   - Auto-resets at midnight or on manual trigger

When quota is exhausted, `RawDataFetchStep` stops processing and `QuotaHealthIndicator` reports DOWN.

### Change Detection (HashCacheService)

Redis-backed SHA-256 hash cache. Key pattern: `ingestion:{entityType}:{key}:hash` (TTL: 36h).

1. Compute SHA-256 hash of new payload
2. Compare with stored hash in Redis
3. If changed: store new hash, write to DB
4. If unchanged: skip DB write (saves I/O)
5. Falls back to "changed" if Redis unavailable

### Scheduling

**Nightly cron:** `0 0 22 * * *` (10 PM daily, configurable via `INGESTION_SCHEDULE`)
**Feature flag:** `INGESTION_ENABLED` (default true)

`IngestionOrchestrator` maintains a `@Volatile` `activeRunId` to prevent concurrent runs. Admin endpoints return HTTP 409 if a run is in progress.

### Tracking and Observability

- **Runs:** `ingestion.ingestion_runs` -- one row per execution (status: RUNNING/COMPLETED/FAILED/PARTIAL)
- **Steps:** `ingestion.ingestion_steps` -- one row per step with record counts
- **Errors:** `ingestion.ingestion_errors` -- individual error records with context JSONB
- **Health:** `EodhdHealthIndicator` (API reachability), `QuotaHealthIndicator` (quota status)

### Cross-Schema Access

The main backend reads ingestion data via cross-schema queries:

```sql
SELECT * FROM ingestion.instruments WHERE instrument_type = 'STOCK';
```

### Instrument Types

| Type | EODHD Source | Description |
|------|-------------|-------------|
| `STOCK` | Common Stock | Regular equity shares |
| `PREFERRED_STOCK` | Preferred Stock | Preferred equity shares |
| `ETF` | ETF | Exchange-traded funds |
| `MUTUAL_FUND` | FUND | Open-ended mutual funds |
| `INDEX` | INDEX | Market indices |
| `BOND` | BOND | Government and corporate bonds |

### Target Exchanges

| Code | Description |
|------|-------------|
| `US` | NYSE, NASDAQ |
| `TO` | Toronto Stock Exchange |
| `V` | TSX Venture Exchange |
| `INDX` | Market indices |
| `GBOND` | Government bonds |

### Key Files

| Layer | File | Role |
|---|---|---|
| Application | `IngestionServiceApplication.kt` | Spring Boot entry point |
| Config | `config/IngestionProperties.kt` | Configuration properties |
| Controller | `controller/AdminIngestionController.kt` | REST API for admin triggers |
| Pipeline | `pipeline/IngestionOrchestrator.kt` | Pipeline coordinator |
| Pipeline | `pipeline/ExchangeSyncStep.kt` | Exchange sync |
| Pipeline | `pipeline/UniverseSyncStep.kt` | Instrument discovery |
| Pipeline | `pipeline/RawDataFetchStep.kt` | Fundamentals fetch |
| Pipeline | `pipeline/FundamentalsBatchProcessor.kt` | Batch processing |
| Provider | `provider/DataProvider.kt` | Strategy interface |
| Provider | `provider/eodhd/EodhdProvider.kt` | EODHD implementation |
| Provider | `provider/eodhd/EodhdRateLimiter.kt` | Rate limiter |
| Tracking | `tracking/IngestionTrackingService.kt` | Run/step/error tracking |
| Tracking | `tracking/HashCacheService.kt` | Redis change detection |
| Health | `health/EodhdHealthIndicator.kt` | EODHD health check |
| Health | `health/QuotaHealthIndicator.kt` | Quota monitoring |
| Migration | `db/migration/V1__initial_schema.sql` | Ingestion schema |

---

## 2. Brokerage Data Ingestion (SnapTrade)

The main backend uses **SnapTrade** as a broker aggregator for account data. All brokerage data flows through SnapTrade's SDK.

### SnapTrade Configuration

| Variable | Purpose |
|---|---|
| `SNAPTRADE_CLIENT_ID` | SnapTrade API client ID |
| `SNAPTRADE_CONSUMER_KEY` | SnapTrade API consumer key |
| `SNAPTRADE_REDIRECT_URI` | OAuth callback URL |
| `BROKER_ENCRYPTION_KEY` | AES-256-GCM key for encrypting user secrets at rest |

### API Calls Made

| # | SDK Method | Purpose | When Called |
|---|---|---|---|
| 1 | `registerSnapTradeUser` | Create user, get `userSecret` | First broker connect |
| 2 | `loginSnapTradeUser` | Get portal URL for broker OAuth | User clicks "Connect" |
| 3 | `listUserAccounts` | List broker accounts | After OAuth / sync |
| 4 | `listBrokerageAuthorizations` | List OAuth authorizations | After OAuth / sync |
| 5 | `getUserAccountPositions` | Fetch holdings | Manual "Fetch Now" or nightly sync |
| 6 | `removeBrokerageAuthorization` | Disconnect broker | User clicks "Disconnect" |
| 7 | `listAllBrokerages` | List available brokers | Loading connections page |

### Data Fetch Triggers

| Trigger | Type | Endpoint | What Happens |
|---|---|---|---|
| User clicks "Connect" | Manual | `POST /api/v1/brokers/connect` | SnapTrade APIs #1, #2. Returns OAuth redirect URL |
| OAuth callback return | Auto | Frontend detects query params | Calls `syncConnections()` (APIs #3, #4) |
| User clicks "Fetch Now" | Manual | `POST /api/v1/brokers/connections/{id}/fetch` | API #5 async. Returns 202 immediately |
| Nightly sync | Scheduled | `AccountDataSyncScheduler` | Syncs activities, balances, positions for all connections |
| User clicks "Disconnect" | Manual | `DELETE /api/v1/brokers/connections/{authId}` | API #6. Marks DISCONNECTED |

### Position Fetch Flow

```
POST /api/v1/brokers/connections/{id}/fetch
  -> BrokerController.triggerPositionFetch()
  -> PositionFetchService.triggerManualFetch()
     1. Creates PositionFetchLog (PENDING)
     2. Returns 202 Accepted
     3. @Async: executePositionFetch()
        a. Fetch positions from SnapTrade
        b. Mark old positions non-current
        c. Save new positions with P&L calculations
        d. Enrich option positions (strike, expiration, underlying)
        e. Save balance snapshot
        f. Sync broker orders into trade_orders table
        g. Update BrokerConnection metadata
        h. Write audit log
```

### Security: SnapTrade User Secret

- Encrypted with AES-256-GCM via `TokenEncryptionService`
- Stored in `users.snaptrade_user_secret_encrypted`
- Decrypted on-demand for each API call

### Database Tables

| Table | Purpose |
|---|---|
| `users` | `snaptrade_user_id`, `snaptrade_user_secret_encrypted` |
| `broker_connections` | Links user to SnapTrade account |
| `broker_positions` | Holdings snapshot (`is_current=true` for latest) |
| `broker_activities` | Transaction history |
| `broker_balance_snapshots` | Daily balance snapshots |
| `position_fetch_log` | Fetch audit trail |
| `trade_orders` | Orders synced from broker |

### Key Files

| Layer | File | Role |
|---|---|---|
| Controller | `broker/controller/BrokerController.kt` | REST endpoints |
| Service | `broker/service/BrokerService.kt` | Connection management |
| Service | `broker/service/PositionFetchService.kt` | Async fetch orchestration |
| Service | `broker/service/SnapTradeService.kt` | SnapTrade SDK wrapper |
| Service | `broker/service/ActivityIngestionService.kt` | Activity sync |
| Security | `broker/security/TokenEncryptionService.kt` | AES-256-GCM |
| Scheduler | `broker/scheduler/AccountDataSyncScheduler.kt` | Nightly sync |
| Frontend | `services/brokerService.ts` | API client |
| Frontend | `hooks/useBrokerConnections.ts` | React Query hooks |
