# Options Chain Performance Optimization

**Date:** 2026-06-10
**Status:** Draft

## Problem

Options chain loading is slow and wasteful:

1. Every request to `/expirations` hits IBKR live (~2-3s), even though expirations rarely change
2. All expirations are returned (including LEAPS 2+ years out) when users only care about near-term
3. The backend always fetches both calls and puts, even when the wheel page only needs one side
4. Strike count is hardcoded at 25 per side with no user control
5. Snapshot fetching dominates latency (~20-36s per expiry for 100 contracts)

## Solution

Five targeted changes that reduce IBKR calls, cut payload size, and give users control over data density.

## Prerequisite

Merge branch `fix/options-chain-snapshot-loading` (commit `58d9bad`) into `main` before starting. This branch contains parallel snapshot fetching for `buildChainForExpiry` which the deployed container already runs.

## Design

### 1. Redis Expiration Cache (24h TTL)

**Goal:** Eliminate repeated IBKR calls for expiration lists.

**QuoteCacheService** — add two methods:

```kotlin
companion object {
    private const val EXPIRATION_PREFIX = "expirations:"
    private const val EXPIRATION_TTL_HOURS = 24L
}

fun cacheExpirations(symbol: String, expirations: List<LocalDate>) {
    val key = EXPIRATION_PREFIX + symbol
    val json = objectMapper.writeValueAsString(expirations)
    redisTemplate.opsForValue().set(key, json, EXPIRATION_TTL_HOURS, TimeUnit.HOURS)
}

fun getExpirations(symbol: String): List<LocalDate>? {
    val json = redisTemplate.opsForValue().get(EXPIRATION_PREFIX + symbol) ?: return null
    return try {
        objectMapper.readValue(json, object : TypeReference<List<LocalDate>>() {})
    } catch (e: Exception) { null }
}
```

**ChainController `/expirations` endpoint** — cache-first flow:

1. Check `quoteCacheService.getExpirations(symbol)`
2. Cache hit → filter by `maxDte` → return
3. Cache miss → call `ibkrClient.requestOptionExpirations()` → cache the **raw unfiltered** list → filter by `maxDte` → return

Caching the raw list means changing the DTE parameter doesn't require a new IBKR fetch.

### 2. Configurable Max DTE

**Goal:** Only show expirations users care about (default 90 days).

**AppProperties** — add field:

```kotlin
val maxDteDefault: Int = 90
```

**`/expirations` endpoint** — add query param:

```kotlin
@GetMapping("/{underlying}/expirations")
fun getExpirations(
    @PathVariable underlying: String,
    @RequestParam(required = false) maxDte: Int?
): ResponseEntity<OptionExpirationsResponse>
```

Filter logic:

```kotlin
val effectiveMaxDte = maxDte ?: properties.maxDteDefault
val filtered = allExpirations.filter {
    ChronoUnit.DAYS.between(LocalDate.now(), it) <= effectiveMaxDte
}
```

**Frontend** — `getOptionExpirations()` passes `maxDte` if the user has configured a preference. Default omits it (uses server default of 90).

### 3. Backend Side Filter

**Goal:** Halve IBKR work for wheel page by only fetching the relevant option side.

**`/expiry/{expiry}` endpoint** — add query param:

```kotlin
@GetMapping("/{underlying}/expiry/{expiry}")
fun getChainForExpiry(
    @PathVariable underlying: String,
    @PathVariable expiry: String,
    @RequestParam(defaultValue = "0.45") maxDelta: Double,
    @RequestParam(defaultValue = "25") strikesPerSide: Int,
    @RequestParam(defaultValue = "both") side: String  // "put", "call", or "both"
)
```

**`buildChainForExpiry`** — filter contracts by right before strike filtering:

```kotlin
val sideFiltered = when (side.lowercase()) {
    "put" -> contracts.filter { it.right?.uppercase() in setOf("P", "PUT") }
    "call" -> contracts.filter { it.right?.uppercase() in setOf("C", "CALL") }
    else -> contracts
}
```

This filter is applied after fetching from IBKR but before strike filtering and snapshot fetching. Note: `requestContractDetails` already supports a `right` parameter that could filter at the IBKR level, but filtering in-memory is simpler and avoids a second IBKR call when the user switches sides. The big savings come from halving the snapshot fetch count (the dominant latency).

**Streaming** — `OptionStreamingService.startStreamingChainForExpiry` and `switchChainExpiry` gain an optional `side: String? = null` parameter. When set, only contracts of that right are subscribed.

**WebSocket messages** — `subscribe_chain_expiry` and `switch_chain_expiry` gain optional `side` field:

```json
{"action": "subscribe_chain_expiry", "underlying": "SOXL", "expiry": "2026-06-18", "side": "put"}
```

### 4. Strikes-Per-Side UI Selector (25 / 50 / 60)

**Goal:** Let users control data density.

**Backend** — already supports `?strikesPerSide=N`. No change needed.

**Frontend `marketDataService.ts`** — update signature:

```typescript
export async function getOptionsChainForExpiry(
  underlying: string,
  expiry: string,
  opts?: { strikesPerSide?: number; side?: 'put' | 'call' }
): Promise<OptionsChain> {
  const params = new URLSearchParams()
  if (opts?.strikesPerSide) params.set('strikesPerSide', String(opts.strikesPerSide))
  if (opts?.side) params.set('side', opts.side)
  const qs = params.toString()
  const url = `/market-data-api/api/v1/chains/${encodeURIComponent(underlying)}/expiry/${encodeURIComponent(expiry)}${qs ? '?' + qs : ''}`
  const response = await proxyFetch(url)
  if (!response.ok) throw await parseErrorResponse(response)
  return response.json()
}
```

**UI component** — a compact segmented control placed near the expiry dropdown on both pages:

```
Strikes: [25] [50] [60]
```

- **WheelChainPanel**: default 25, selector inline with expiry dropdown
- **OptionsPage / OptionsChainTable**: default 50, selector in the chain table header

When the user changes the value, re-fetch the chain for the current expiry with the new `strikesPerSide`.

**WebSocket hook** — `subscribeChainExpiry` and `switchChainExpiry` gain optional `side` parameter:

```typescript
subscribeChainExpiry(underlying: string, expiry: string, side?: 'put' | 'call')
switchChainExpiry(underlying: string, expiry: string, side?: 'put' | 'call')
```

### 5. Single-Expiry Discipline

The current code already loads one expiry at a time and uses `switchChainExpiry` to atomically swap streaming subscriptions. No architectural change needed here — just ensuring the new `side` parameter flows through consistently.

**WheelChainPanel** already:
- Calls `getOptionsChainForExpiry(ticker, chosenExpiry)` on mount
- Calls `switchChainExpiry(ticker, newExpiry)` on expiry change
- Unsubscribes on unmount

With this design it will additionally pass `side: 'put'|'call'` and `strikesPerSide: N`.

**OptionsPage** already:
- Loads first expiry on search
- Uses `switchChainExpiry` on expiry change

With this design it will additionally pass `strikesPerSide: N` (no side filter — options page shows both).

## Files Changed

| Layer | File | Change |
|---|---|---|
| Backend config | `AppProperties.kt` | Add `maxDteDefault: Int = 90` |
| Backend cache | `QuoteCacheService.kt` | Add `cacheExpirations` / `getExpirations` with 24h TTL |
| Backend API | `ChainController.kt` | Cache-first expirations, `maxDte` param, `side` param, DTE filter |
| Backend streaming | `OptionStreamingService.kt` | `side` filter on chain subscription methods |
| Backend WS | `QuoteWebSocketHandler.kt` | Parse `side` from `subscribe_chain_expiry` / `switch_chain_expiry` |
| Frontend service | `marketDataService.ts` | Pass `strikesPerSide` and `side` query params |
| Frontend hook | `useMarketDataWebSocket.ts` | Pass `side` in WS subscription messages |
| Frontend | `WheelChainPanel.tsx` | Strike selector (default 25), pass `side` based on CSP/CC |
| Frontend | `OptionsChainTable.tsx` or `OptionsPage.tsx` | Strike selector (default 50) |

## Performance Impact

| Scenario | Before | After |
|---|---|---|
| Load expirations (cache hit) | ~2-3s (IBKR) | ~50ms (Redis) |
| Load expirations (cache miss) | ~2-3s (IBKR) | ~2-3s (IBKR) + cache for 24h |
| Wheel chain load (25 strikes, single side) | ~20-36s (100 contracts) | ~10-18s (~50 contracts) |
| Options chain load (50 strikes, both sides) | ~20-36s (100 contracts) | ~30-50s (~200 contracts) |
| Expiry dropdown entries | 13+ (incl. LEAPS) | ~6-8 (90 DTE) |

## Testing

- Unit test: `QuoteCacheService` expiration cache/retrieve with TTL
- Unit test: `ChainController` DTE filtering with various `maxDte` values
- Unit test: `ChainController` side filtering (put-only, call-only, both)
- Integration: verify Redis cache hit returns same data as cache miss
- Manual: wheel page loads only puts for CSP, only calls for CC
- Manual: options page shows both sides with 50 strikes per side
- Manual: strike selector changes trigger re-fetch
- Manual: expiry change with side filter streams only relevant contracts
