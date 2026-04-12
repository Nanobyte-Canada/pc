# Ingestion Microservice Design Spec

## Overview

Extract the ingestion and enrichment system into a separate Spring Boot microservice module within the monorepo. The service ingests stock, ETF, and mutual fund data from EODHD (primary provider) with the architecture designed to support additional providers via a strategy pattern.

**Milestone 1 scope:** US + Canadian exchanges, EODHD as sole provider, JSON-only storage (no enrichment), no pricing data.

## Architecture

### Module Structure

New Gradle module `ingestion-service/` in the monorepo. Separate Spring Boot application with its own `Dockerfile`, `application.yml`, and Flyway migrations targeting the `ingestion` PostgreSQL schema.

### Layered Design

```
External APIs → Provider Adapters → Core Pipeline → Storage (ingestion schema)
                                                  → Tracking (runs/steps/errors)
                                                  → Consumers (portfolio app via cross-schema reads)
```

**Provider Adapters:** Each data provider implements a `DataProvider` interface with methods for each capability (universe, fundamentals, holdings, pricing). The `ProviderRegistry` discovers adapters and routes calls based on capability and priority.

```kotlin
interface DataProvider {
    fun name(): String
    fun capabilities(): Set<ProviderCapability>
    suspend fun fetchExchanges(): List<RawExchange>
    suspend fun fetchUniverse(exchange: String): List<RawInstrument>
    suspend fun fetchFundamentals(ticker: String, exchange: String): JsonNode?
}
```

**Core Pipeline:**
- `IngestionOrchestrator` — coordinates steps, manages run lifecycle
- `ExchangeSyncStep` — one-time exchange list fetch
- `UniverseSyncStep` — universe discovery per exchange
- `RawDataFetchStep` — fundamentals fetch (bulk preferred, individual fallback)
- `BatchProcessor` — transaction-per-batch isolation
- `RateLimitManager` — per-second throttling + daily quota tracking
- `HashCacheService` — Redis-based SHA-256 change detection

### Fundamentals Fetching Strategy

Individual calls to `GET /api/fundamentals/{TICKER}.{EXCHANGE}` (10 API calls per request). Processed in batches to manage rate limits and ensure partial progress is committed.

**Fetch ordering:** Instruments are ordered by staleness first (never-fetched first, then oldest fetched), then by type priority (STOCK → ETF → MUTUAL_FUND → PREFERRED_STOCK → BOND → INDEX). This ensures the most outdated data gets refreshed first regardless of type.

**Batching:**
- Instruments are grouped into configurable batches (default: 500 instruments per batch)
- Each batch runs in its own database transaction (`REQUIRES_NEW`) — if a batch fails, previously committed batches are preserved
- Within a batch, calls are made concurrently using Kotlin coroutines, limited by a semaphore (respects rate limit of 5 req/sec)
- After each batch completes, the batch processor checks remaining daily quota before proceeding

**API budget (100k calls/day):**
- Each fundamentals call costs 10 API calls → 10,000 instruments/day maximum
- With stale threshold of 7 days, full universe refreshes spread across multiple nights
- Rolling refresh prioritized by type then staleness

## EODHD API Endpoints

| Endpoint | Purpose | Cost | Response |
|----------|---------|------|----------|
| `GET /api/exchanges-list/` | Exchange list | 1 call | Code, Name, Country, Currency, OperatingMIC |
| `GET /api/exchange-symbol-list/{EX}` | Universe per exchange | 1 call | Code, Name, Country, Exchange, Currency, Type, Isin |
| `GET /api/fundamentals/{TICKER}.{EX}` | Single instrument fundamentals | 10 calls | General, Highlights, Valuation, Financials, ETF_Data (JSONB) |

Instrument identification: `{TICKER}.{EXCHANGE_CODE}` (e.g., `AAPL.US`, `XIU.TO`).

Exchange codes: `US` (NYSE/NASDAQ), `TO` (TSX), `V` (TSX Venture).

## Pipeline Steps

### Step 1: Exchange Sync (one-time, manual trigger)

Fetch exchange list from EODHD → upsert into `ingestion.exchanges`. Run once to populate reference data, re-run only when adding new exchange support.

### Step 2: Ingestion (nightly scheduled)

**2a. Universe Discovery:**
Call `GET /api/exchange-symbol-list/{EX}` for each target exchange (US, TO, V). Upsert into `ingestion.instruments`, link to exchanges via `ingestion.instrument_exchanges` (many-to-many). Deduplicate by ISIN where available, fall back to ticker matching.

**2b. Raw Data Fetch:**
For instruments where `last_fetched_at` exceeds the stale threshold: fetch fundamentals and store the full JSON response in `ingestion.provider_raw_data`. Use hash-based change detection to skip unchanged payloads. Process in configurable batches with rate limiting.

### Step 3: Enrichment (deferred, not in Milestone 1)

Parse raw JSON payloads and extract key fields into typed relational tables (e.g., `instrument_fundamentals`, `etf_holdings`). Not implemented in Milestone 1 — the portfolio app queries JSONB directly using PostgreSQL operators.

## Database Schema

All tables in the `ingestion` PostgreSQL schema. Managed by Flyway migrations in `ingestion-service/src/main/resources/db/migration/`.

### exchanges

| Column | Type | Constraints |
|--------|------|-------------|
| id | SERIAL | PK |
| code | VARCHAR(10) | UNIQUE, NOT NULL |
| name | VARCHAR(200) | NOT NULL |
| country | VARCHAR(100) | |
| currency | VARCHAR(3) | |
| operating_mic | VARCHAR(10) | |
| is_active | BOOLEAN | DEFAULT true |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |

### instruments

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | PK |
| ticker | VARCHAR(20) | NOT NULL |
| name | VARCHAR(500) | NOT NULL |
| instrument_type | VARCHAR(20) | NOT NULL (STOCK, PREFERRED_STOCK, ETF, MUTUAL_FUND, INDEX, BOND) |
| isin | VARCHAR(12) | UNIQUE (nullable) |
| cusip | VARCHAR(9) | |
| currency | VARCHAR(3) | |
| country | VARCHAR(3) | |
| status | VARCHAR(20) | DEFAULT 'ACTIVE' |
| source_last_seen_at | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

One row per instrument globally. Instruments trading on multiple exchanges are linked via `instrument_exchanges`.

### instrument_exchanges

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | PK |
| instrument_id | BIGINT | FK → instruments, NOT NULL |
| exchange_id | INT | FK → exchanges, NOT NULL |
| local_ticker | VARCHAR(20) | Exchange-specific ticker symbol |
| is_primary | BOOLEAN | DEFAULT false |
| | | UNIQUE(instrument_id, exchange_id) |

### provider_raw_data

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | PK |
| instrument_id | BIGINT | FK → instruments, NOT NULL |
| provider | VARCHAR(50) | NOT NULL |
| data_type | VARCHAR(30) | NOT NULL (FUNDAMENTALS, UNIVERSE) |
| raw_payload | JSONB | NOT NULL |
| payload_hash | VARCHAR(64) | SHA-256 for change detection |
| fetched_at | TIMESTAMPTZ | DEFAULT NOW() |
| | | UNIQUE(instrument_id, provider, data_type) |

Stores latest raw JSON only — overwritten on each re-fetch. One row per (instrument, provider, data_type).

### provider_config

| Column | Type | Constraints |
|--------|------|-------------|
| id | SERIAL | PK |
| provider_name | VARCHAR(50) | UNIQUE, NOT NULL |
| enabled | BOOLEAN | DEFAULT true |
| priority | INT | DEFAULT 0 |
| daily_quota | INT | |
| requests_used_today | INT | DEFAULT 0 |
| last_quota_reset | DATE | |
| config_json | JSONB | Provider-specific config |

### ingestion_runs, ingestion_steps, ingestion_errors

Same schema as existing tracking tables. Moved to `ingestion` schema.

**Step names:** `EXCHANGE_SYNC`, `UNIVERSE_SYNC`, `RAW_DATA_FETCH`

**Error types:** `API_ERROR`, `PARSE_ERROR`, `DB_ERROR`, `RATE_LIMIT`, `VALIDATION_ERROR`, `DUPLICATE_ISIN`, `NOT_FOUND`

## Portfolio App Integration

The portfolio app reads from the `ingestion` schema using cross-schema SQL:

```sql
SELECT i.ticker, i.name, prd.raw_payload
FROM ingestion.instruments i
JOIN ingestion.provider_raw_data prd ON prd.instrument_id = i.id
WHERE prd.provider = 'EODHD' AND prd.data_type = 'FUNDAMENTALS'
```

The portfolio app creates read-only JPA entity views mapped to ingestion schema tables. No write access from the portfolio app.

## Error Handling & Observability

- All errors logged to `ingestion_errors` with structured JSONB context
- Admin REST APIs to query runs, steps, errors, and statistics
- Micrometer metrics for API calls, rate limits, success/failure counts
- Existing error types reused: API_ERROR, PARSE_ERROR, DB_ERROR, RATE_LIMIT, VALIDATION_ERROR, DUPLICATE_ISIN, NOT_FOUND

## Configuration

```yaml
ingestion:
  enabled: true
  schedule: "0 0 22 * * *"  # 10 PM UTC nightly
  target-exchanges:
    - US      # NYSE, NASDAQ
    - TO      # TSX
    - V       # TSX Venture
    - INDX    # Indices (S&P 500, DJIA, etc.)
    - GBOND   # Government Bonds
  stale-threshold-days: 7
  batch-size: 500

  eodhd:
    base-url: https://eodhd.com/api
    api-key: ${EODHD_API_KEY}
    rate-limit-per-second: 5
    daily-quota: 100000
    fundamentals-cost: 10  # API calls per fundamentals request
    batch-size: 500  # Instruments per batch for fundamentals fetch

spring:
  datasource:
    url: ${DATABASE_URL}
  flyway:
    schemas: ingestion
    default-schema: ingestion
  jpa:
    properties:
      hibernate:
        default_schema: ingestion
```

## Milestone 1 Scope

**In scope:**
- EODHD provider adapter (individual fundamentals with batching)
- Exchange sync (one-time)
- Universe discovery (stocks, preferred stocks, ETFs, mutual funds, indices, bonds for US, TO, V, INDX, GBOND)
- Raw fundamentals fetch → JSONB storage
- Instruments with many-to-many exchange mapping
- Hash-based change detection (Redis)
- Ingestion run/step/error tracking
- Admin REST APIs
- Nightly scheduler
- Separate Gradle module + Dockerfile
- Flyway migrations for ingestion schema

**Out of scope (future milestones):**
- Enrichment (JSONB → relational tables)
- Pricing (OHLCV) — needs EOD Historical Data add-on
- ETF holdings extraction to relational table
- Additional providers (FMP, Yahoo, SEC EDGAR)
- Additional exchanges beyond US + Canada
- Portfolio app migration from existing tables to ingestion schema reads
- Admin UI for ingestion monitoring

## Verification

1. `docker compose exec ingestion-service ./gradlew test` — all tests pass
2. Admin API: `POST /admin/ingestion/exchanges` triggers exchange sync
3. Admin API: `POST /admin/ingestion/run` triggers full pipeline
4. `SELECT COUNT(*) FROM ingestion.instruments` shows expected instrument count after universe sync
5. `SELECT COUNT(*) FROM ingestion.provider_raw_data WHERE data_type = 'FUNDAMENTALS'` shows fetched fundamentals
6. Error tracking: `GET /admin/ingestion/errors/summary` returns error breakdown
