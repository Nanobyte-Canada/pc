# IBKR Gateway Connection & Market Data Streaming

**Date:** 2026-05-30
**Status:** Draft

## Summary

Connect the existing market-data service (`TwsIbkrClient`) and broker-gateway service (`IbkrAdapter`) to a local IB Gateway running on Windows (port 4001, live trading). Implement the missing `TwsIbkrAccountClient` for broker-gateway, add connection health endpoints and a frontend health indicator, improve reconnection resilience, and prepare for removal of fake services after successful testing.

## Current State

### What already works
- **Market-data service:** `TwsIbkrClient` implements the full TWS API client (ticks, contract resolution, option chains, Greeks, snapshots). `IbkrConnectionManager` handles startup and reconnection with exponential backoff.
- **Frontend:** `useMarketDataWebSocket` hook connects to `/ws/quotes` with auto-reconnect. Quote/chain subscriptions with ref-counting.
- **Distribution layer:** `QuoteWebSocketHandler`, `QuoteStreamingService`, `OptionStreamingService`, `QuoteCacheService` — full pipeline from IBKR ticks to WebSocket broadcasts.
- **Broker-gateway:** `IbkrAdapter` has complete account/position/order mapping logic using `IbkrAccountClient` interface.

### What's missing
1. **Docker networking:** No `IBKR_HOST` set in docker-compose — containers can't reach IB Gateway on host.
2. **`TwsIbkrAccountClient`:** No concrete implementation of `IbkrAccountClient` interface. The broker-gateway adapter compiles but has no real backend.
3. **Connection health API:** No REST endpoints to check IBKR connectivity status.
4. **Frontend health indicator:** No UI for IBKR connection state.
5. **Reconnection gaps:** `TwsIbkrClient` doesn't auto-reconnect after disconnect; `connectionReady` latch is single-use.

## Design

### 1. Docker Networking Configuration

Update `docker-compose.yml` to pass IB Gateway host and port via `.env` file or environment variables:

```yaml
# market-data-service
IBKR_HOST: ${IBKR_HOST:-}           # Set to host.docker.internal for local IB Gateway
IBKR_PORT: ${IBKR_PORT:-4001}       # Change default to 4001 (live)
IBKR_CLIENT_ID: ${IBKR_CLIENT_ID:-1}

# broker-gateway-service
IBKR_GATEWAY_ENABLED: ${IBKR_GATEWAY_ENABLED:-false}  # Set to true
IBKR_HOST: ${IBKR_HOST:-}
IBKR_PORT: ${IBKR_PORT:-4001}
IBKR_GATEWAY_CLIENT_ID: ${IBKR_GATEWAY_CLIENT_ID:-2}
```

Add `extra_hosts` to both services for Docker Desktop on Windows:
```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

Create/update `.env` file (gitignored) with:
```
IBKR_HOST=host.docker.internal
IBKR_PORT=4001
IBKR_GATEWAY_ENABLED=true
```

### 2. TwsIbkrAccountClient Implementation

New file: `backend/broker-gateway/.../adapter/ibkr/TwsIbkrAccountClient.kt`

Implements `IbkrAccountClient` using the IB TWS API (`EClientSocket` + `DefaultEWrapper`), mirroring the pattern from market-data's `TwsIbkrClient`:

- **Connection:** Socket to IB Gateway using `IbkrConfig` host/port/clientId (default clientId=2, different from market-data's clientId=1).
- **EReader thread:** Same pattern — daemon thread processing messages from `EJavaSignal`.
- **`getManagedAccounts()`:** Waits for `managedAccounts()` callback (latch with timeout).
- **`getAccountSummary(accountId)`:** Uses `reqAccountSummary()` with tags: NetLiquidation, GrossPositionValue, TotalCashValue, BuyingPower, Currency, AccountAlias, AccountType. Collects via callback accumulator, completes on `accountSummaryEnd()`.
- **`getPositions()`:** Uses `reqPositions()`, accumulates via `position()` callback, completes on `positionEnd()`.
- **`getOpenOrders()`:** Uses `reqOpenOrders()`, accumulates via `openOrder()` callback, completes on `openOrderEnd()`.
- **`getCompletedOrders()`:** Uses `reqCompletedOrders()`, accumulates via `completedOrder()` callback, completes on `completedOrdersEnd()`.
- **`getExecutions(accountId)`:** Uses `reqExecutions()` with `ExecutionFilter(accountId)`, accumulates via `execDetails()` callback, completes on `execDetailsEnd()`.
- **`placeOrder()`:** Uses `reqIds()` for next valid order ID, then `placeOrder()` with mapped Contract + Order objects.
- **`cancelOrder()`:** Delegates to `cancelOrder()`.

All request methods use `CompletableFuture` with configurable timeout from `IbkrConfig.requestTimeoutMs` (default 30s).

Spring bean registration: `@Component @ConditionalOnProperty(prefix = "broker-gateway.ibkr", name = ["enabled"], havingValue = "true")`.

### 3. TwsIbkrClient Reconnection Fix

The current `TwsIbkrClient.connect()` uses a single-shot `CountDownLatch(1)` — after first connection, the latch is spent and reconnection stalls.

Fix:
- Replace `CountDownLatch` with a resettable mechanism (new `CountDownLatch` per connect attempt).
- In `connectionClosed()` and error callbacks (502/504), call disconnect cleanup (clear callbacks, maps) then notify the `IbkrConnectionManager` via a callback or event.
- `IbkrConnectionManager` already has reconnection logic — just needs to detect disconnects actively rather than only at startup.
- Add a periodic health check (every 30s) in `IbkrConnectionManager` that calls `isConnected()` and triggers reconnect if false.

### 4. Connection Health REST Endpoints

**Market-data service** — new `HealthController`:
```
GET /api/v1/health/ibkr
Response: {
  "connected": true,
  "service": "market-data",
  "activeSubscriptions": 5,
  "connectionState": "CONNECTED"
}
```

**Broker-gateway service** — new endpoint on existing connection controller or a new health controller:
```
GET /api/v1/health/ibkr
Response: {
  "connected": true,
  "service": "broker-gateway",
  "managedAccounts": ["U1234567"],
  "connectionState": "CONNECTED"
}
```

Both use the respective `IbkrConnectionManager.getConnectionState()`.

### 5. WebSocket Health Messages

Add a `connection_status` message type to the WebSocket protocol. When IBKR connection state changes, the `QuoteWebSocketHandler` broadcasts:

```json
{
  "type": "connection_status",
  "connected": true,
  "service": "market-data"
}
```

Frontend `useMarketDataWebSocket` hook: parse `connection_status` messages and expose `ibkrConnected` state alongside the existing `isConnected` (WebSocket connection) state.

### 6. Frontend Connection Health Indicator

New component: `IbkrConnectionBadge`

Displays a small status indicator showing:
- **Green dot + "IBKR Connected"** — both WebSocket and IBKR are connected
- **Yellow dot + "IBKR Connecting..."** — WebSocket connected but IBKR not yet
- **Red dot + "IBKR Disconnected"** — IBKR connection lost

Placement: In the app header/navbar area, near existing account info.

Uses `useMarketDataWebSocket`'s `ibkrConnected` state. Polls the health endpoint as a fallback if WebSocket isn't available.

### 7. Fake Service Removal (Deferred)

After successful testing with real IB Gateway:
- Remove `FakeIbkrClient` from market-data service
- Remove `FakeIbkrAdapter` from broker-gateway service
- Remove `@ConditionalOnExpression`/`@ConditionalOnProperty` toggling — always use real client
- Remove hardcoded prices, mock accounts, mock positions

This is a separate follow-up change, not part of the initial connection work.

## File Changes

### New files
| File | Purpose |
|------|---------|
| `broker-gateway/.../ibkr/TwsIbkrAccountClient.kt` | Real TWS API implementation of `IbkrAccountClient` |
| `market-data/.../api/HealthController.kt` | IBKR connection health REST endpoint |
| `broker-gateway/.../api/HealthController.kt` | IBKR connection health REST endpoint |
| `frontend/src/components/IbkrConnectionBadge.tsx` | Connection status indicator |
| `frontend/src/components/IbkrConnectionBadge.css` | Badge styles |

### Modified files
| File | Change |
|------|--------|
| `docker-compose.yml` | Add `extra_hosts`, update default port to 4001 |
| `.env` (gitignored) | Add IBKR_HOST, IBKR_PORT, IBKR_GATEWAY_ENABLED |
| `TwsIbkrClient.kt` | Replace CountDownLatch with resettable mechanism, add disconnect notification |
| `IbkrConnectionManager.kt` (market-data) | Add periodic health check, disconnect detection |
| `QuoteWebSocketHandler.kt` | Broadcast `connection_status` messages |
| `useMarketDataWebSocket.ts` | Parse `connection_status`, expose `ibkrConnected` state |
| `broker-gateway/build.gradle.kts` | Add `tws-api` dependency (already in market-data) |
| App header component | Mount `IbkrConnectionBadge` |

## Testing Plan

1. **Connection test:** Start IB Gateway on port 4001 with API enabled. Run `docker compose up --build`. Check logs for successful connection messages from both services.
2. **Market data test:** Open frontend, navigate to a page that subscribes to quotes. Verify real prices appear (not the fake hardcoded values).
3. **Health endpoint test:** `curl http://localhost:8082/api/v1/health/ibkr` and `curl http://localhost:8084/api/v1/health/ibkr` — both return `connected: true`.
4. **Reconnection test:** Stop IB Gateway, verify logs show reconnection attempts. Restart IB Gateway, verify auto-reconnect and data resumes.
5. **Badge test:** Verify the green/yellow/red indicator updates correctly during connect/disconnect cycles.
6. **Broker operations test:** Test `listAccounts`, `getPositions`, `getBalances` through the portfolio service UI.

## IB Gateway Prerequisites

Before running, ensure:
- IB Gateway is running on port 4001
- API connections are enabled in IB Gateway config (Configure → API → Settings)
- "Read-Only API" is unchecked if order placement is needed
- Socket client connections are allowed from `0.0.0.0` or the Docker network range
- The TWS API port matches what's configured (4001)
