# Ingestion Workflow

How brokerage data flows into the application: what data is fetched, when, and which external APIs are called.

## Architecture Overview

The app uses **SnapTrade** as a broker aggregator (no direct broker OAuth). All brokerage data flows through SnapTrade's SDK.

```
Frontend (React Query) --> Backend (Spring Boot) --> SnapTrade API --> PostgreSQL
```

## External API: SnapTrade

### Configuration

Set via `application.yml` / environment variables:

| Variable | Purpose |
|---|---|
| `SNAPTRADE_CLIENT_ID` | SnapTrade API client ID |
| `SNAPTRADE_CONSUMER_KEY` | SnapTrade API consumer key |
| `SNAPTRADE_REDIRECT_URI` | OAuth callback URL (default: `http://localhost:3000/brokers/connections`) |
| `BROKER_ENCRYPTION_KEY` | AES-256-GCM key for encrypting user secrets at rest |

### API Calls Made

| # | SnapTrade SDK Method | HTTP | Purpose | When Called |
|---|---|---|---|---|
| 1 | `authentication.registerSnapTradeUser(userId)` | POST | Create SnapTrade user, get `userSecret` | First broker connect |
| 2 | `authentication.loginSnapTradeUser(userId, secret)` | POST | Get portal URL for broker OAuth | User clicks "Connect" |
| 3 | `accountInformation.listUserAccounts(userId, secret)` | GET | List broker accounts | After OAuth / sync |
| 4 | `connections.listBrokerageAuthorizations(userId, secret)` | GET | List OAuth authorizations | After OAuth / sync |
| 5 | `accountInformation.getUserAccountPositions(userId, secret, accountId)` | GET | Fetch holdings (core ingestion) | Manual "Fetch Now" |
| 6 | `connections.removeBrokerageAuthorization(authId, userId, secret)` | DELETE | Disconnect broker | User clicks "Disconnect" |
| 7 | `referenceData.listAllBrokerages()` | GET | List available brokers | Loading connections page |

## When Data is Fetched (Triggers)

| Trigger | Type | Endpoint | What Happens |
|---|---|---|---|
| User clicks "Connect" | Manual | `POST /api/v1/brokers/connect` | Calls SnapTrade APIs #1, #2. Returns redirect URL to SnapTrade OAuth portal |
| OAuth callback return | Automatic | Frontend detects query params | Calls `syncConnections()` (APIs #3, #4). Creates/updates `BrokerConnection` rows |
| User clicks "Fetch Now" | Manual | `POST /api/v1/brokers/connections/{id}/fetch` | Calls API #5 asynchronously. Returns 202 immediately |
| User clicks "Disconnect" | Manual | `DELETE /api/v1/brokers/connections/{authId}` | Calls API #6. Marks connection DISCONNECTED |
| Page load (connections) | Automatic | `GET /api/v1/brokers` | Calls API #7 for available brokers list |

**No scheduled/automatic fetching exists.** The V31 migration removed scheduled fetch infrastructure (`user_broker_prefs`, `DailyPositionFetchScheduler`). Only `MANUAL` and `INITIAL` fetch types remain.

## Core Ingestion Flow: Position Fetch

### Request Path

```
Frontend: POST /api/v1/brokers/connections/{connectionId}/fetch
  |
BrokerController.triggerPositionFetch()
  | validates connection belongs to user
PositionFetchService.triggerManualFetch()
  +-- Creates PositionFetchLog (status: PENDING)
  +-- Returns 202 Accepted: { fetchId, status, message }
  +-- Launches @Async background task
```

### Background Task (`executePositionFetch`)

```
1. Update PositionFetchLog -> IN_PROGRESS
2. Load BrokerConnection -> get accountIdExternal
3. Call SnapTrade: getUserAccountPositions(userId, secret, accountId)
4. For each Position returned:
   +-- Extract: symbol, units, price, avgCost, currency, type
   +-- Calculate: currentValue, costBasis, totalPnl, totalPnlPercent
   +-- Map type code: CS -> STOCK, ET -> ETF, MF -> MUTUAL_FUND
   +-- Create BrokerPosition entity (is_current=true)
5. Mark old positions non-current: UPDATE SET is_current=false
6. Save new positions
7. Update BrokerConnection:
      last_positions_fetched_at, positions_count, total_value, status=ACTIVE
8. Update PositionFetchLog -> SUCCESS (with duration_ms, positions_count)
9. Write audit log: BROKER_FETCH_POSITIONS
```

### On Error

```
- BrokerConnection.markAsError(code, message)
- PositionFetchLog.markFailed(code, message)
- Audit log: BROKER_FETCH_ERROR
- Exception swallowed (no propagation to client)
```

## Data Mapping: SnapTrade Position to BrokerPosition

| SnapTrade Field | DB Column | Notes |
|---|---|---|
| `symbol.symbol.symbol` | `symbol` | Ticker (e.g. "AAPL") |
| `symbol.symbol.description` | `security_name` | Full name |
| `symbol.symbol.type.code` | `instrument_type` | Mapped: CS->STOCK, ET->ETF, MF->MUTUAL_FUND |
| `units` | `quantity` | Share count |
| `averagePurchasePrice` | `average_cost` | Per-share cost basis |
| `price` | `current_price` | Latest price |
| `units * price` | `current_value` | Calculated |
| `currentValue - costBasis` | `total_pnl` | Calculated |
| `(pnl / costBasis) * 100` | `total_pnl_percent` | Calculated |
| `currency.code` | `currency` | e.g. "CAD", "USD" |

### Security: SnapTrade User Secret

- Encrypted with AES-256-GCM via `TokenEncryptionService`
- Stored in `users.snaptrade_user_secret_encrypted`
- Decrypted on-demand for each API call

## Database Tables Involved

| Table | Purpose |
|---|---|
| `users` | Stores `snaptrade_user_id`, `snaptrade_user_secret_encrypted` |
| `broker_connections` | Links user to SnapTrade account. Status: PENDING / ACTIVE / EXPIRED / ERROR / DISCONNECTED |
| `broker_positions` | Holdings snapshot. `is_current=true` for latest data |
| `position_fetch_log` | Fetch audit: PENDING -> IN_PROGRESS -> SUCCESS / FAILED, duration, counts |
| `audit_log` | Security audit trail |

## Frontend Polling

No WebSocket or SSE is used. The frontend uses React Query with stale-time-based polling.

| Hook | Endpoint | Stale Time |
|---|---|---|
| `useAvailableBrokers` | `GET /api/v1/brokers` | 5 min |
| `useBrokerConnections` | `GET /api/v1/brokers/connections` | 30 sec |
| `useAggregatedPositions` | `GET /api/v1/brokers/positions` | 60 sec |
| `useConnectionPositions` | `GET /api/v1/brokers/connections/{id}/positions` | 60 sec |
| `useTriggerPositionFetch` | `POST .../fetch` (mutation) | Invalidates queries after 2s delay |

## Key Files

| Layer | File | Role |
|---|---|---|
| Controller | `broker/controller/BrokerController.kt` | REST endpoints |
| Service | `broker/service/BrokerService.kt` | Connection management, aggregation |
| Service | `broker/service/PositionFetchService.kt` | Async fetch orchestration |
| Service | `broker/service/SnapTradeService.kt` | SnapTrade SDK wrapper |
| Security | `broker/security/TokenEncryptionService.kt` | AES-256-GCM encrypt/decrypt |
| Config | `broker/config/BrokerConfig.kt` | SnapTrade client bean |
| Entity | `broker/entity/BrokerConnection.kt` | Connection JPA entity |
| Entity | `broker/entity/PositionFetchLog.kt` | Fetch log JPA entity |
| Migration | `db/migration/V30__broker_integration_schema.sql` | DB schema |
| Migration | `db/migration/V31__snaptrade_migration.sql` | SnapTrade migration |
| Frontend | `services/brokerService.ts` | API client |
| Frontend | `hooks/useBrokerConnections.ts` | React Query hooks |
