# Questrade Market Data Migration — Design

**Date:** 2026-08-24
**Status:** Approved design, pending implementation plan
**Goal:** Replace IBKR Client Portal Gateway with the Questrade API as the sole market data provider (options chains, streaming options quotes, streaming/snapshot stock & ETF quotes). Remove all IBKR traces from both `market-data` and `broker-gateway` modules and their deploy infrastructure. FX stays on the Bank of Canada Valet API (unchanged).

---

## 1. Feasibility Findings

Researched against official Questrade docs + community sources (Aug 2026):

| Requirement | Verdict | Notes |
|---|---|---|
| Options chain | ✅ YES | 2-step REST: `GET /v1/symbols/{id}/options` (expiries × strikes × call/put symbolIds), then `POST /v1/markets/quotes/options` for quotes **incl. delta/gamma/theta/vega/rho/IV/openInterest** |
| Streaming options quotes | ✅ YES | `POST /v1/markets/quotes/options` with `stream:true`; same socket infra as stocks |
| Streaming stocks/ETFs | ✅ YES | `GET /v1/markets/quotes?ids=…&stream=true&mode=WebSocket` → connect to returned port → send raw access token as first frame |
| Snapshot quotes | ✅ YES | `GET /v1/markets/quotes?ids=` batch; check `delay` field |
| FX | ❌ NO | No FX asset class/endpoints at all → app keeps BoC Valet API (already independent of IBKR) |
| Symbol search | ✅ ticker/name; ❌ ISIN/CUSIP | `GET /v1/symbols/search?prefix=` |
| Auth | ✅ | Refresh-token flow (`POST /oauth2/token`), ~30-min access tokens, rotating refresh tokens, practice env via `practicelogin.questrade.com` |
| Rate limits | 20 req/s, 15k/h (market data) | Headers `X-RateLimit-*`; HTTP 429 on breach |
| Constraints | ⚠️ | **One socket at a time** (2nd connection kills 1st); ≥1 REST call per 30 min or session drops; legacy SignalR protocol is gone — plain WebSocket/RawSocket only |
| Data entitlements | 💰 | Free: TSX/TSXV L1 + limited US (CBOE One). Real-time US equities + OPRA options requires $9.95 CAD/mo package; otherwise delayed |

**Verdict:** Feasible for all required data domains. Phase 0 live smoke test confirms before migration code is written.

### Phase 0 Live Smoke Test Results (2026-08-24) — ALL PASS

| Check | Result | Evidence |
|---|---|---|
| Token exchange | ✅ | 30-min access token, rotating refresh token, `api01.iq.questrade.com` |
| Symbol search | ✅ | `SPY` → `symbolId=34987`, `hasOptions=true`, ARCA/USD |
| Options chain | ✅ | 32 expiries, ~178 strikes each, multiplier 100, American |
| Option quotes + greeks | ✅ | delta/gamma/theta/vega/rho/IV/openInterest present, `delay=0` |
| Stock snapshot real-time | ✅ | SPY bid/ask/last, `delay=0` (paid package confirmed active) |
| Stock streaming | ✅ | negotiate → `streamPort`, WS auth via raw token → `{"success":true}` + quote frames |
| Options streaming | ✅ | same socket pattern; streams `optionQuotes` **and** underlying `quotes` frames |

**Docs-vs-reality corrections (implementation must follow actual API):**
- Chain endpoint returns top-level key `optionChain` (not `options` as documented); per-root field is `optionRoot` (not `root`).
- Option quotes response key is `optionQuotes`; option symbol format observed: `SPY28Aug26C600.00`.
- Stock and options stream negotiation return the **same** `streamPort`; one connection carries both `quotes` and `optionQuotes` frames — confirms single-socket multiplexing design.
- Access tokens can be invalidated mid-session (code 1017 observed once); re-exchange + retry resolved it.

## 2. Decisions

1. **Approach A — provider-neutral port swap.** Rename `IbkrClient` → `MarketDataProvider`, implement `QuestradeProvider`. No dual-provider toggle (cleanup goal makes it YAGNI).
2. **FX stays** on Bank of Canada Valet API (`ExchangeRateService`). Untouched.
3. **Full IBKR removal** across `market-data` AND `broker-gateway` (its `IBKR_GATEWAY_ENABLED` adapter path, flex-token/query config) plus shared ib-gateway containers/scripts/env vars.
4. **Topology preserved:** broker-gateway remains a per-environment service (uat-broker-gateway / prod-broker-gateway in their own stacks). The external `shared-network` existed solely for ib-gateway containers and disappears; nothing else changes.
5. **Fake IBKR loader:** already deleted (2026-05-30). Nothing to do beyond confirming no remnants.

## 3. Architecture

New package `com.portfolio.marketdata.questrade` replaces `com.portfolio.marketdata.ibkr`:

| Component | Role |
|---|---|
| `MarketDataProvider` | Interface renamed from `IbkrClient`; same method shapes minus IBKR-only members (`registerDataFarmErrorHandler`, `isDataFarmHealthy`) |
| `QuestradeProvider` | Implements `MarketDataProvider`; owns OCC-format mapping internally |
| `QuestradeRestClient` | WebClient-based authenticated calls (tokens supplied by `QuestradeTokenManager`): symbol search, chain fetch, batch option quotes, snapshots |
| `QuestradeStreamClient` | Owns the single WebSocket; raw-token first frame; parses quote frames; multiplexes stock + option subscriptions |
| `QuestradeConnectionManager` | Replaces `IbkrConnectionManager`: startup connect, backoff reconnect, **30-min REST keepalive**, health indicator, `connection_status` broadcasts |
| `QuestradeTokenManager` | Mirrors broker-gateway's pattern (refresh→access exchange, expiry tracking w/ 60s buffer). Refresh token seeded from env var; **rotated refresh token persisted in Redis** (rotation makes env-only storage stale) |

Unchanged consumers (same code, re-pointed to new interface): `ChainController`, `QuoteController`, `QuoteStreamingService`, `OptionStreamingService`, `SubscriptionManager` (ref-counting/LRU kept; cap re-keyed `questrade.max-subscriptions`), `ContractResolver` (conId→symbolId; same Redis `contract:` cache semantics), `ExpiryRefreshService`.

`GreeksSource.IBKR` → `GreeksSource.QUESTRADE`; `BLACK_SCHOLES` fallback retained for gaps/enrichment.

### Data mapping

| Capability | Today (IBKR) | After (Questrade) |
|---|---|---|
| Symbol resolution | conId via contract details | `GET /v1/symbols/search?prefix=` → symbolId |
| Expiries + chain structure | optional-parameters endpoint | `GET /v1/symbols/{id}/options` |
| Option quotes + greeks | tick computations | `POST /v1/markets/quotes/options` (batch by optionIds/filters) |
| Stock snapshots | `requestMarketDataSnapshot` | `GET /v1/markets/quotes?ids=` |
| Stock streaming | TWS socket ticks | WebSocket `stream=true&mode=WebSocket` |
| Options streaming | same socket | `POST …/options` with `stream:true` |
| FX | BoC Valet API | unchanged |

Frontend protocol untouched: REST `/market-data-api/api/v1/chains*`, `/quotes`; WS `/ws/quotes` actions (`subscribe_option`, `subscribe_chain`, `switch_chain_expiry`, …).

## 4. Streaming Behavior

- One socket total; `SubscriptionManager` ref-counting sits above `QuestradeStreamClient`.
- Reconnect → resubscribe all active subscriptions (existing reconnect-handler pattern).
- Keepalive: ≥1 REST request every 30 min from `QuestradeConnectionManager`.
- Batch option-quote requests; on 429 back off per `X-RateLimit-Reset`.
- Non-zero `delay` field → log warning (detects missing paid package / delayed fallback). Detection is log-level only; no frontend change.

## 5. Cleanup Inventory

| Area | Action |
|---|---|
| `marketdata/ibkr/` package (`TwsIbkrClient`, `IbkrClient`, `IbkrConnectionManager`, data-farm stubs, deprecated reconnect shim) | Delete; `SubscriptionManager` relocates to `questrade` package |
| broker-gateway `adapter/ibkr/`, `gateways.ibkr.*` config keys, flex-token/query-id settings + related tests | Delete |
| Root `docker-compose.yml`: ib-gateway service, IBKR_* env on market-data/gateway services | Remove; add `QUESTRADE_REFRESH_TOKEN`, `QUESTRADE_USE_PRACTICE` |
| `deploy/shared/docker-compose.yml` (live+paper ib-gateway) | Delete file; drop `shared-network` refs from prod/uat stacks |
| `deploy/{prod,uat}/docker-compose.yml` IBKR env vars; `.env.example` files | Swap to Questrade vars |
| `scripts/restart-ibkr.sh`; Prometheus scrape configs for ib-gateway | Delete |
| Repo-root dev artifacts (~40 `ibkr-*`/`wheel-*` PNGs, `chain-response.json`) | Delete (preserved in git history) |
| README/docs references to IBKR gateway | Update where they describe market-data flow |
| **Redis caches** `contract:`, `quote:`, `chain:`, `expiry:`, `expirations:` | **Flush at cutover** — conIds become symbolIds; stale IDs resolve wrong contracts |

## 6. Error Handling

- Token refresh failure → retry w/ backoff, health indicator DOWN, `connection_status` broadcast.
- Socket drop → auto-reconnect + resubscribe.
- Always use `api_server` URL returned by token exchange (prevents practice/live mismatch).
- 429 → honor reset header, exponential backoff.
- Code 1017 (invalid access token mid-session, observed live) → transparent re-exchange of refresh token + single retry of the failed call.

## 7. Testing & Rollout

1. **Phase 0 — smoke test (go/no-go):** throwaway script vs real API with user-provided refresh token: token exchange → SPY symbol search → chain fetch → batch option quotes w/ greeks → stock WS frames → options stream frames. Requires user to generate token in Questrade web API Centre; confirm practice vs live and $9.95 package status.
2. Unit tests: MockWebServer fixtures (chain/options/symbol JSON), token rotation, OCC mapping, stream-frame parsing; existing market-data tests remocked to `MarketDataProvider`.
3. UAT deploy with **practice** token → manual chain/streaming verification on uatportfolio.
4. Prod cutover with live token (+ confirmed real-time package) + Redis flush.
5. Infra removal (shared ib-gateway compose) after both envs verified.

## 8. Out of Scope

- FX provider changes (BoC Valet stays).
- ISIN/CUSIP resolution (not needed today).
- Level 2 data, strategy/spread streaming endpoints.
- Frontend changes (protocol unchanged).
