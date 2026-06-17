# Plan: Fix Options Chain & Quotes Loading in Production

> Root cause analysis and implementation plan for options chain/quote loading failures in the production environment at portfolio.nanobyte.ca.

---

## Problem Summary

Options chain and options quotes fail to load in the PROD environment. The underlying IBKR Gateway connection is unstable or unreachable, and the application has no graceful degradation, user-visible error feedback, or fallback mechanisms.

---

## Issues and Fixes

### Issue 1: IBKR Gateway Network Isolation

**Problem:** The market-data service connects to IBKR Gateway at `host.docker.internal:14001`. The gateway runs in a separate shared docker-compose (`deploy/shared/docker-compose.yml`) on `shared-network`, while the prod services run on `prod-network`. If the gateway container restarts (2FA, session timeout, nightly restart), ALL options data becomes unavailable.

**Fix:**
- [ ] Add a `depends_on` health check in `deploy/prod/docker-compose.yml` for market-data-service to verify IBKR is reachable before fully starting

```yaml
# In market-data-service section of deploy/prod/docker-compose.yml
  market-data-service:
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      # Add: ib-gateway external health check
    # ... existing config ...
```

**Files affected:**
- `deploy/prod/docker-compose.yml`

---

### Issue 2: Silent Frontend Failure (UX Bug)

**Problem:** `OptionsPage.tsx` catches all errors from chain/quote API calls but only logs to console. The user sees a blank content area with no error feedback.

**Fix:**
- [ ] Import toast store and show user-visible toast on failure
- [ ] Display an error message inline in the options page instead of going blank
- [ ] Add a retry button

**Implementation:**

```typescript
// In OptionsPage.tsx, modify handleSearch catch block:
} catch (err) {
  console.error('Failed to load options data:', err)
  // Add toast notification for user
  showToast({ type: 'error', title: 'Failed to load options data', message: 'IBKR Gateway may be unavailable. Please try again.' })
  // Clear the selected underlying so the empty state shows
  setSelectedUnderlying(null)  // ADD THIS LINE
} finally {
  setIsLoadingChain(false)
}
```

**Files affected:**
- `frontend/src/pages/OptionsPage.tsx`

---

### Issue 3: No IBKR Disconnect Distinction in ChainController

**Problem:** `ChainController` returns HTTP 404 for both "symbol has no options" and "IBKR is down". The frontend has no way to distinguish between these cases.

**Fix:**
- [ ] Check `ibkrClient.isConnected()` before attempting chain fetch
- [ ] Return HTTP 503 (Service Unavailable) when IBKR is disconnected
- [ ] Return HTTP 404 only when IBKR responds but has no data

**Implementation:**

```kotlin
// In ChainController.kt, before buildChainFromIbkr():
@GetMapping("/{underlying}")
fun getChain(@PathVariable underlying: String): ResponseEntity<OptionsChainResponse> {
    val cachedChain = quoteCacheService.getChain(underlying)
    if (cachedChain != null) {
        return ResponseEntity.ok(OptionsChainResponse.fromDomain(cachedChain))
    }

    // Check IBKR connectivity
    if (!ibkrClient.isConnected()) {
        log.warn("IBKR not connected, cannot fetch chain for {}", underlying)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
    }

    val chain = buildChainFromIbkr(underlying)
        ?: return ResponseEntity.notFound().build()
    quoteCacheService.cacheChain(underlying, chain)
    return ResponseEntity.ok(OptionsChainResponse.fromDomain(chain))
}
```

Do the same for all chain endpoints: `getChainWithGreeks`, `getExpirations`, `getChainForExpiry`.

**Files affected:**
- `backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/ChainController.kt`
- `backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/QuoteController.kt`

---

### Issue 4: Short Cache TTL With No Fallback

**Problem:** Quote TTL is 5 seconds, chain TTL is 30 seconds. Without active WebSocket streaming seeding the Redis cache, every request hits IBKR. No fallback data source.

**Fix:**
- [ ] Increase chain cache TTL from 30s to 300s (5 minutes)
- [ ] Increase quote cache TTL from 5s to 30s
- [ ] Serve stale cached data when IBKR is unavailable (stale-while-revalidate pattern)

**Implementation:**

```kotlin
// In QuoteCacheService.kt, update TTLs:
companion object {
    private const val QUOTE_PREFIX = "quote:"
    private const val CHAIN_PREFIX = "chain:"
    private const val QUOTE_TTL_SECONDS = 30L       // Changed from 5L
    private const val CHAIN_TTL_SECONDS = 300L      // Changed from 30L
}
```

**Files affected:**
- `backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/QuoteCacheService.kt`

---

### Issue 5: Cascading Timeouts

**Problem:** Loading an options chain involves sequential timeouts that can exceed 30 seconds before the user sees failure. No timeout on the overall operation.

**Fix:**
- [ ] Add a global timeout in `ChainController` for chain building operations (15 seconds)
- [ ] Reduce per-contract snapshot timeout from 8s to 5s
- [ ] Add a timeout-aware CompletableFuture wrapper

**Implementation:**

```kotlin
// In ChainController.kt, add overall timeout to buildChainFromIbkr():
private fun buildChainFromIbkr(underlying: String): OptionsChain? {
    return try {
        CompletableFuture.supplyAsync {
            // existing chain building logic
        }.get(15, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
        log.error("Timed out building option chain for {}", underlying)
        null
    }
}
```

**Files affected:**
- `backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/ChainController.kt`

---

### Issue 6: No Health Indicator for IBKR in Market-Data Service

**Problem:** The prod health check only verifies Spring Actuator, not IBKR connectivity. The frontend WebSocket shows "Disconnected" but REST endpoint failures have no visible feedback.

**Fix:**
- [ ] Create a custom `IbkrHealthIndicator` that exposes IBKR connection status
- [ ] Add a health endpoint in the market-data service for IBKR status

**Implementation:**

```kotlin
// New file: backend/market-data/.../health/IbkrHealthIndicator.kt
@Component
class IbkrHealthIndicator(
    private val ibkrClient: IbkrClient
) : HealthIndicator {
    override fun health(): Health {
        return if (ibkrClient.isConnected()) {
            Health.up().withDetail("ibkr", "connected").build()
        } else {
            Health.down().withDetail("ibkr", "disconnected").build()
        }
    }
}
```

**Files affected:**
- `backend/market-data/src/main/kotlin/com/portfolio/marketdata/health/IbkrHealthIndicator.kt` (new file)

---

## Implementation Order

| Priority | Issue | Effort | Impact | Dependencies |
|----------|-------|--------|--------|--------------|
| P0 | **Issue 2** — Silent frontend failure | 30 min | High (UX) | None |
| P0 | **Issue 3** — 503 vs 404 distinction | 1 hr | High (debuggability) | None |
| P1 | **Issue 4** — Cache TTL increase | 15 min | Medium (resilience) | None |
| P1 | **Issue 6** — Health indicator | 1 hr | Medium (monitoring) | None |
| P2 | **Issue 5** — Timeout hardening | 2 hr | Medium (stability) | Issue 3 |
| P2 | **Issue 1** — Gateway network dependency | 30 min | Low (reliability) | None |

---

## Testing Plan

1. **Unit tests:**
   - `ChainController` returns 503 when IBKR is disconnected
   - `IbkrHealthIndicator` reports down when not connected

2. **Manual verification:**
   - Start market-data service without IBKR → chain endpoints return 503
   - Frontend shows error toast instead of blank page
   - Health endpoint includes IBKR status

3. **Integration:**
   - Deploy to UAT first
   - Verify chain loads with IBKR connected
   - Stop IBKR → verify 503 response and frontend error display
