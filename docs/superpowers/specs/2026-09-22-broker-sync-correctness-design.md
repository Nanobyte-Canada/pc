# Broker Sync Correctness Overhaul — Design

**Status:** Draft for review
**Date:** 2026-09-22
**Issue:** [#268](https://github.com/Nanobyte-Canada/pc/issues/268) — Questrade activities 400/1003
**Scope:** Full broker-sync surface, phased. Phase 1 is detailed below; Phases 2–5 are specified at requirements level and each gets a deeper design pass when it starts.

---

## 1. Context and problem statement

The portfolio sync pipeline has been silently degrading: **activities ingestion has been dead on every UAT connection since 2026-07-17** while positions and balances sync normally. A full audit (issue #268) found the root cause plus a set of compounding correctness bugs that make activity-derived numbers (dividends, fees, IRR/XIRR, performance reports) unreliable even when sync appears to work.

Live UAT evidence (2026-09-23 UTC):

- All 6 connections: `last_activities_fetched_at = 2026-07-16/17`; newest stored activity is July 3–10.
- Positions/balances fetched successfully the same day (`last_positions_fetched_at = 2026-09-23 02:54`), which is why the UI "looks fine".
- Gateway logs show the user's manual sync attempts failing with `400 {"code":1003,"message":"Argument length exceeds imposed limit"}` for accounts 40061528, 53105513, 53445187.
- `broker_activities`: 1,093 rows, **all with `external_id = NULL`**, containing **29 duplicate groups / 29 excess rows**.
- `portfolio_cash_flows`: **0 rows** — TWR/MWR treat all contributions/withdrawals as zero.
- Live 429 storm: `TokenRefreshScheduler` validates each connection against `/v1/accounts` every 5 minutes (~288 calls/day/connection) and is currently rate-limited on every attempt.

### Root cause of #268

`ActivityIngestionService.syncIncremental` (`backend/portfolio/.../ActivityIngestionService.kt:121-142`) requests `[latest trade date − 1 day … now]` in **one unbounded call** (`endDate = null`). `QuestradeAdapter.getActivities` (`backend/broker-gateway/.../QuestradeAdapter.kt:101-127`) substitutes `today` and issues a single request. Questrade caps activities requests at **31 days** (official docs; error 1003). Any connection whose newest activity is older than ~31 days fails forever: the exception rethrows, the `REQUIRES_NEW` transaction rolls back, and the watermark never advances.

### Why duplicates matter next

Questrade activities have **no stable identifier** and the adapter maps `externalId = null` (`QuestradeAdapter.kt:113`), so the portfolio's dedup check (`ActivityIngestionService.kt:147-151`) never runs and the DB unique constraint `uq_activity_external (connection_id, external_id)` never fires (NULLs are distinct in PostgreSQL). The incremental path's deliberate 1-day overlap re-inserts the boundary day on every successful run. **If chunking is fixed without dedup, the July→now backfill re-duplicates on every run and inflates every downstream number.**

---

## 2. Current state — what gets synced

| When | What | Where | Notes |
|---|---|---|---|
| 2×/day (06:00, 16:20 **UTC** = 02:00, 12:20 ET in EDT; 1 h earlier in EST) | Activities + balances per connection | `ActivityIngestionService` via `AccountDataSyncScheduler` | Activities frozen since Jul 17; balance step is skipped when activities throw |
| 2×/day, same run | Positions + balances + orders per ACTIVE connection | `PositionFetchService` | Working; writes `broker_positions`, `trade_orders`, snapshot |
| 2×/day, same run | Portfolio snapshots | `PortfolioSnapshotService` | Working |
| Every 60 s | Order statuses | `OrderStatusSyncScheduler` | Full order list per connection; status filter dead |
| Daily | Rebalance check | `RebalanceScheduler` | No broker I/O |
| Manual | `/connections/{id}/fetch`, `/sync-activities`, `/sync-all`, `/dashboard/refresh` | `BrokerController`, `DashboardController` | Synchronous despite 202 docs; `/sync-all` reports success even when steps fail |

Other services: `market-data` talks to Questrade directly (quotes/option chains/streaming — unaffected by this bug, separate token store); `strategy` only places combo orders through the gateway. Nothing else fetches activities.

---

## 3. Verified findings

### Critical

| ID | Finding | Evidence |
|---|---|---|
| C1 | Incremental activities sync sends an unbounded range; Questrade rejects >31 days; rollback prevents self-healing. Connections idle >31 days are permanently stuck. | Live: all 6 connections stuck since Jul 17; logs 1003. `ActivityIngestionService.kt:52-55,133-138`; `QuestradeAdapter.kt:106-109` |
| C2 | Questrade activities are never deduplicated (`externalId = null`); unique constraint bypassed by NULLs; 1-day overlap re-inserts rows every run. 29 excess rows already present. | Live DB query; `ActivityIngestionService.kt:147-151`; `QuestradeAdapter.kt:113`; `V36__broker_activities.sql:19` |
| C3 | TWR/MWR ignore cash flows — `portfolio_cash_flows` has no writer. | Live: 0 rows; `PerformanceCalculationService.kt:70-73,101-103` |
| C4 | FX fallback silently books non-CAD amounts as CAD 1:1 (activities, cash, analytics); unmarked and unrecoverable. | `ActivityIngestionService.kt:317-324`; `DashboardCashService.kt:61,65,97,116` |
| C5 | Gateway has no service authentication while the prod host publishes port 10084; `X-Gateway-Api-Key` is sent but never validated. | `GatewayProperties.kt:15-17`; `deploy/prod/docker-compose.yml:171-172` |

### High

| ID | Finding | Evidence |
|---|---|---|
| H1 | Balance sync is skipped when activities throw (same try block). | `ActivityIngestionService.kt:269-279` |
| H2 | Failed position fetches leave no trace: error state + FAILED logs are rolled back. | `PositionFetchService.kt:38-56,178-198` |
| H3 | Rollback-only trap: caught `@Transactional` exceptions mark the shared tx rollback-only → whole fetch discarded at commit. | `PositionFetchService.kt:169-174,291-314` |
| H4 | Full history runs ~378 sequential HTTP calls inside ONE DB transaction. | `ActivityIngestionService.kt:37,86-113`; Hikari pool 10 |
| H5 | Full-history chunk failures are swallowed while `lastActivitiesFetchedAt` is still set "fresh"; no resume. | `ActivityIngestionService.kt:58,103-110` |
| H6 | `/sync-activities` + `/sync-all` are synchronous despite 202 docs; broker error detail is lost (generic 500). | `BrokerController.kt:119-132,176-221`; `GlobalExceptionHandler.kt:80-89` |
| H7 | Two writers share the daily balance snapshot row with different semantics (`marketValue` vs `totalEquity`, different currency); last writer wins; race risk on the unique row. | `ActivityIngestionService.kt:237-251`; `PositionFetchService.kt:133,221-236` |
| H8 | Token refresher validates against `/v1/accounts` every 5 min/connection; currently rate-limited continuously. | Live gateway logs; `TokenRefreshScheduler.kt:22-50` |

### Medium (summary)

Timezone bugs (cron runs in UTC; gateway hardcodes `-05:00` year-round; `LocalDate.now()` UTC), no 429/5xx retry or backoff anywhere in gateway, outbound HTTP clients without timeouts, Wealthsimple activities silently capped at 99 items with no pagination, order `status` filter dead end-to-end (risking deletion of locally-open orders), stale/invisible sync status fields, dead config (`chunk-days`, crons not in yml), market-data unbounded id lists, dual Questrade token stores with different refresh semantics, docs still describe SnapTrade. Full ranked list is in the audit output retained with issue #268.

---

## 4. Design overview — phases

| Phase | Goal | Delivers | Depends on |
|---|---|---|---|
| **1. Restore activities** | Chunked fetch + stable dedup + repair existing data | Gateway windowing, portfolio fingerprint, repair migration, UAT verification | — |
| **2. Sync robustness** | Make sync survivable and honest | Per-chunk transactions + resume, timeouts/backoff, truthful statuses, token-refresher fix | 1 |
| **3. Numbers correctness** | Make derived numbers converge to truth | Cash-flow writer, FX fallback marking, single balance writer, reporting consistency | 1 |
| **4. Gateway & integration hardening** | Close security/correctness gaps | Service auth, order-filter wiring, Wealthsimple paging, position-fetch failure trace, error taxonomy, token-store consolidation | 1 |
| **5. Hygiene** | Docs and config describe reality | Doc rewrites, config wiring, ADR finalization | 1–4 |

Each phase lands as its own PR with its own tests and UAT evidence; ADRs are added in the same PR as the change they describe (per `AGENTS.md`).

---

## 5. Phase 1 — Restore activity ingestion (detailed)

### 5.1 Gateway: windowed activities fetch (`QuestradeAdapter.getActivities`)

- Effective span = `[start, end]` after defaults, where defaults become **ET-calendar-aware**: `end = today` (America/Toronto), `start = end − 29 days` (a 30-day inclusive default window).
- Span is measured inclusive of both boundary days (`end − start + 1`). If ≤ 30 days → single request (preserves existing short-range behavior).
- Otherwise split into **contiguous, non-overlapping 30-day windows** (inclusive of both boundary days): window `k` = `[d₀ + 30k, min(d₀ + 30k + 29, end)]`; next window starts the following day. 30 (not 31) absorbs timezone/boundary slop, matching community practice.
- Each boundary is rendered with the **offset computed for that timestamp** via `ZoneId.of("America/Toronto").rules.getOffset(...)` — start at `T00:00:00`, end at `T23:59:59`. This fixes the hardcoded `-05:00` DST bug at the same time.
- Results are merged and sorted by `tradeDate` ascending (stable sort preserves API order within a day).
- Any window failure propagates to the caller (fail-fast; no partial histories). Phase 2 adds retry/backoff before retries are relied upon.
- Safety cap: spans requiring more than 400 windows (~32 years — above the product's configured 30-year lookback) are rejected with a clear error, guarding against pathological spans without breaking dormant-account catch-up. (Portfolio's own full-history loop issues ≤30-day requests, so it is never affected.)
- Extract window computation + merge/sort into **pure functions** so they are unit-testable without HTTP; expose an internal `fetchActivitiesWindowed(windows, fetch)` seam so the window loop, query-string rendering, and merge/sort are testable without touching Spring wiring or the `QuestradeRestClient`.
- Update `capabilities(): activityHistoryDepth` to state the real 31-day API cap handled via transparent 30-day windowing (currently claims "Unlimited").

### 5.2 Portfolio: stable dedup key for Questrade rows

- Add `ActivityFingerprint` utility (portfolio module): SHA-256 (lowercase hex, 64 chars) over the unit-separator-joined canonicalization of: `type, symbol, description, quantity, price, amount, fee, currency, tradeDate, settlementDate, optionType` — nulls as empty, dates ISO-8601, decimals `stripTrailingZeros().toPlainString()`.
- In `processAndSaveActivities`: when the gateway-provided `externalId` is null (Questrade), use the computed fingerprint as the dedup key; Wealthsimple keeps its `canonicalId`. Existing lookup + unique constraint then apply unchanged.
- The 1-day overlap in `syncIncremental` becomes harmless (dedup skips re-fetched rows) and stays as late-posting protection.
- `external_id` is `VARCHAR(100)` (`V36__broker_activities.sql`) — the 64-char fingerprint fits; no schema change.
- Accepted trade-off: two byte-identical fills on the same day collapse into one row. Judged rare; if observed in practice, add an occurrence counter to the canonicalization.

### 5.3 Data repair (one-time, idempotent)

- Backfill fingerprints for all existing rows and delete duplicates keeping the earliest `id` per `(connection_id, fingerprint)` — 29 excess rows across the 6 connections, plus all NULL keys fixed so future dedup matches.
- Preferred mechanism: **class-based Flyway migration** in the portfolio module so the exact same `ActivityFingerprint` code performs the backfill (single source of truth, covered by golden-vector tests).
- Fallback if class-based migrations prove impractical here: a guarded one-shot runner invoked manually, or SQL replicating the canonicalization proven equal by golden vectors. Decision made at plan time; either way the migration is idempotent and runs against a fresh backup.
- Disconnected connections 1–3 are repaired too (their rows are kept for future reconnects).

### 5.4 Verification

- Unit: window generation (1/N windows plus inverted-span rejection, DST-crossing window asserts mixed offsets `-05:00` start / `-04:00` end, inclusive-span edge cases: 30 days → one window, 31 days → two windows), merge/sort, defaults in ET; fingerprint golden vectors (fixed inputs → expected hashes) and null/edge handling; dedup skip proves no insert when fingerprint exists.
- Migration logic is unit-tested via the pure `ActivityBackfillPlanner` (duplicate collapse, per-connection grouping); the migration itself is verified live post-deploy: `flyway_schema_history` shows V77 applied, the duplicate-group query returns 0, and all rows are fingerprinted. A Testcontainers harness that seeds rows before V77 runs is deliberately omitted (no such pattern exists in the repo; portfolio integration tests are excluded from CI unit runs).
- Live UAT (after deploy): trigger `sync-activities` for connections 4–6; assert activities now cover July→now; `SELECT` duplicate-group query returns 0; dividends/fees totals change where duplicates were removed; gateway logs show no 1003 for the accounts; `last_activities_fetched_at` advances.
- Prod rollout after UAT soak; same DB checks.

### 5.5 Rollout

- Implement in an isolated worktree off `main` (concurrent strategy work in PR #272 touches `broker-gateway`; coordinate merge order).
- PR includes: gateway changes, portfolio changes, migration, tests, ADR entry documenting dedup-key semantics and the repair migration.

---

## 6. Phase 2 — Sync robustness (requirements)

- Full-history sync must not hold a single DB transaction across all chunks: per-chunk transactions with durable progress and resume (no re-fetching completed chunks after a failure).
- `lastActivitiesFetchedAt`/`lastBalanceFetchedAt` reflect reality: set only on genuine success; partial results recorded distinctly; staleness surfaced in API DTOs and UI (currently invisible).
- Gateway outbound HTTP: response timeouts on all broker clients; retry with exponential backoff + jitter for 429 (honor `Retry-After`, cap 60 s) and transient 5xx; respect configured rate limits.
- Token refresher stops full `validateConnection` polling every 5 min/connection; use token-expiry metadata and lightweight calls; exponential backoff on failure (ends the live 429 storm).
- Error propagation: portfolio maps `GatewayApiException` to 502 with broker detail (no generic 500); `/sync-all` returns accurate per-step results.
- Single-flight guard prevents overlapping scheduled/manual runs for the same connection (in-process lock; note multi-replica requires ShedLock).
- Timezone hygiene: schedulers run on intended ET wall-clock; broker-day computations use ET.

## 7. Phase 3 — Numbers correctness (requirements)

- **Cash-flow writer:** derive external cash flows (deposits/withdrawals/transfers) from activities into `portfolio_cash_flows` so TWR/MWR are computed from real flows. Requires a domain mapping design at phase start (which activity types count, signs, CAD conversion).
- **FX integrity:** never silently assume 1:1 — persist a fallback marker/reason, exclude-or-flag affected rows in aggregates, cache negative lookups, add request timeouts, and consider backfilling rates for existing `exchange_rate IS NULL` rows.
- **Single balance writer:** one authoritative writer for the daily snapshot with consistent currency semantics; remove the dual-write race (H7).
- **Reporting consistency:** FX-aware aggregation of snapshot totals; align raw-vs-CAD and type-filter behavior across dashboard and reporting.

## 8. Phase 4 — Gateway & integration hardening (requirements)

- **Service auth:** validate `X-Gateway-Api-Key` on all gateway endpoints (filter, key from Vault); stop publishing gateway port 10084 in prod or restrict it; ADR for the topology change. This item is security-relevant and may ship early as a hotfix independent of the phase.
- **Order surfaces:** wire the `status` filter end-to-end; stop full-list polling every 60 s; never delete locally-open orders based on a truncated/empty response.
- **Pagination contract:** extend the adapter interface and Wealthsimple adapter for paging/limits; remove the silent 99-item cap (surface truncation explicitly until fixed).
- **Position-path failure trace:** persist FAILED fetch logs/error state; eliminate the rollback-only trap (H2/H3).
- **Order sync correctness:** `cancelledAt`/`filledAt` fixes (M6).
- **Error taxonomy:** distinguish validation (400/1003) vs availability (5xx/1008) vs rate limit at the gateway boundary; sanitize echoed broker bodies.
- **Token stores:** consolidate or coordinate the gateway-DB vs market-data-Redis refresh tokens; POST-based refresh exchange (no refresh token in query strings).
- **market-data:** chunk unbounded id lists (stock quotes, stream negotiation); reduce duplicated Questrade client code where practical.

## 9. Phase 5 — Hygiene (requirements)

- Rewrite `docs/reference/ingestion-workflow.md` (still describes SnapTrade) and correct `backend-services.md`/`database-schema.md` (fingerprint + NULL-unique semantics).
- Wire or remove dead config: `broker.sync.chunk-days`, scheduler cron properties, `rebalance.check.cron`, unused enums; set container `TZ` explicitly.
- Final ADR pass for anything not covered in earlier phases.

---

## 10. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Class-based Flyway migration is not an established pattern in this repo | Verify at plan time; fallbacks (guarded one-shot runner, or SQL + golden-vector parity test). Repair is idempotent and low-volume (1,093 rows). |
| Fingerprint collapses genuinely identical same-day fills | Accepted, monitored; occurrence-counter extension only if observed. |
| Prod data repair risk | UAT first, backup before prod, idempotent migration, keep-earliest rule deterministic. |
| Concurrent strategy work (PR #272) touches `broker-gateway` | Implement Phase 1 in an isolated worktree; coordinate merge order. |
| Rate limits during backfill | Phase 1 adds only ~3 chunks/connection; Phase 2 adds backoff before deeper histories are re-fetched. |
| Questrade retention (~16 months) makes the 30-year full-history mostly empty calls | Phase 2/5 decision to reduce `max-lookback-years`. |

## 11. Open questions

1. Phase 3 cash-flow mapping: which activity types represent external cash flows, and their signs — needs a domain decision at phase start.
2. Reduce `max-lookback-years` from 30 to a realistic horizon (~2 years) — decide in Phase 2.
3. Gateway auth: ship as early hotfix (recommended) or with Phase 4?

## 12. References

- Issue #268 and retained audit outputs (portfolio inventory, gateway surface, Questrade API research, live DB/log evidence).
- Questrade docs: activities (31-day cap), error handling (1003), rate limiting, `/v1/time` (Eastern). Community: 30-day windowing + dedupe-by-key practice.
- Key code: `ActivityIngestionService.kt`, `QuestradeAdapter.kt`, `AccountDataSyncScheduler.kt`, `PositionFetchService.kt`, `DataController.kt`, `TokenRefreshScheduler.kt`, `V36__broker_activities.sql`.
