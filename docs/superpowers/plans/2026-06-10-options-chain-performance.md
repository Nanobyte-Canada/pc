# Options Chain Performance Optimization — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make options chain loading faster and more efficient by caching expirations in Redis, filtering by DTE, adding side filters, and giving users control over strike density.

**Architecture:** Cache-first pattern for expirations (Redis, 24h TTL). Per-expiry chain loading gains `side` and `strikesPerSide` query params. WebSocket streaming gains side-aware subscriptions. Frontend adds a 25/50/60 strike selector to both pages.

**Tech Stack:** Kotlin/Spring Boot, Spring Data Redis, React/TypeScript, Zustand, WebSocket

---

## Prerequisite: Merge Snapshot Branch

Before creating the feature branch, merge `fix/options-chain-snapshot-loading` into `main`. This brings in parallel snapshot fetching for `buildChainForExpiry`.

```bash
git checkout main
git merge fix/options-chain-snapshot-loading
git push origin main
git checkout -b feature/options-chain-performance
git push -u origin feature/options-chain-performance
```

---

### Task 1: Add `maxDteDefault` to AppProperties

**Files:**
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/config/AppProperties.kt`
- Modify: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/ibkr/TwsIbkrClientTest.kt`

- [ ] **Step 1: Update AppProperties with new field**

```kotlin
@ConfigurationProperties(prefix = "ibkr")
data class AppProperties(
    val host: String = "127.0.0.1",
    val port: Int = 4002,
    val clientId: Int = 1,
    val maxConnections: Int = 2,
    val reconnectDelayMs: Long = 5000,
    val maxChainExpirations: Int = 12,
    val maxDteDefault: Int = 90
)
```

- [ ] **Step 2: Fix existing tests that construct AppProperties without the new field**

The existing test at `TwsIbkrClientTest.kt:11` constructs `AppProperties` with named params — it will still compile since `maxDteDefault` has a default value. Verify:

```bash
cd backend && ./gradlew :market-data:compileTestKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/config/AppProperties.kt
git commit -m "feat(market-data): add maxDteDefault config property (default 90)"
```

---

### Task 2: Add Expiration Caching to QuoteCacheService

**Files:**
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/QuoteCacheService.kt`
- Create: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/distribution/QuoteCacheServiceTest.kt`

- [ ] **Step 1: Write failing tests for expiration caching**

Create `backend/market-data/src/test/kotlin/com/portfolio/marketdata/distribution/QuoteCacheServiceTest.kt`:

```kotlin
package com.portfolio.marketdata.distribution

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuoteCacheServiceTest {

    private val redisTemplate = mockk<RedisTemplate<String, String>>()
    private val opsForValue = mockk<ValueOperations<String, String>>()
    private lateinit var service: QuoteCacheService

    @BeforeEach
    fun setup() {
        every { redisTemplate.opsForValue() } returns opsForValue
        service = QuoteCacheService(redisTemplate)
    }

    @Test
    fun `cacheExpirations stores JSON with 24h TTL`() {
        val expirations = listOf(
            LocalDate.of(2026, 6, 20),
            LocalDate.of(2026, 7, 18)
        )
        every { opsForValue.set(any(), any(), any(), any()) } just Runs

        service.cacheExpirations("AAPL", expirations)

        verify {
            opsForValue.set(
                "expirations:AAPL",
                match { it.contains("2026-06-20") && it.contains("2026-07-18") },
                24L,
                TimeUnit.HOURS
            )
        }
    }

    @Test
    fun `getExpirations returns cached list`() {
        every { opsForValue.get("expirations:SPY") } returns """["2026-06-20","2026-07-18"]"""

        val result = service.getExpirations("SPY")

        assertEquals(listOf(LocalDate.of(2026, 6, 20), LocalDate.of(2026, 7, 18)), result)
    }

    @Test
    fun `getExpirations returns null on cache miss`() {
        every { opsForValue.get("expirations:MSFT") } returns null

        assertNull(service.getExpirations("MSFT"))
    }

    @Test
    fun `getExpirations returns null on malformed JSON`() {
        every { opsForValue.get("expirations:BAD") } returns "not-json"

        assertNull(service.getExpirations("BAD"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && ./gradlew :market-data:test --tests "*.QuoteCacheServiceTest" -i
```

Expected: Compilation error — `cacheExpirations` and `getExpirations` don't exist yet.

- [ ] **Step 3: Implement expiration caching in QuoteCacheService**

Add to `QuoteCacheService.kt`:

1. Add import at the top:
```kotlin
import com.fasterxml.jackson.core.type.TypeReference
import java.time.LocalDate
```

2. Add constants to the companion object:
```kotlin
private const val EXPIRATION_PREFIX = "expirations:"
private const val EXPIRATION_TTL_HOURS = 24L
```

3. Add methods after `getChain`:
```kotlin
fun cacheExpirations(symbol: String, expirations: List<LocalDate>) {
    val key = EXPIRATION_PREFIX + symbol
    redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(expirations), EXPIRATION_TTL_HOURS, TimeUnit.HOURS)
}

fun getExpirations(symbol: String): List<LocalDate>? {
    val json = redisTemplate.opsForValue().get(EXPIRATION_PREFIX + symbol) ?: return null
    return try {
        objectMapper.readValue(json, object : TypeReference<List<LocalDate>>() {})
    } catch (_: Exception) { null }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./gradlew :market-data:test --tests "*.QuoteCacheServiceTest" -i
```

Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/QuoteCacheService.kt backend/market-data/src/test/kotlin/com/portfolio/marketdata/distribution/QuoteCacheServiceTest.kt
git commit -m "feat(market-data): add Redis expiration caching with 24h TTL"
```

---

### Task 3: Add Cache-First Expirations + DTE Filtering to ChainController

**Files:**
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/ChainController.kt`
- Create: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerTest.kt`

- [ ] **Step 1: Write failing tests for DTE filtering and cache-first logic**

Create `backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerTest.kt`:

```kotlin
package com.portfolio.marketdata.api.controller

import com.portfolio.marketdata.config.AppProperties
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionsChainBuilder
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class ChainControllerTest {

    private val quoteCacheService = mockk<QuoteCacheService>()
    private val chainBuilder = mockk<OptionsChainBuilder>()
    private val greeksCalculator = mockk<GreeksCalculator>()
    private val ibkrClient = mockk<IbkrClient>()
    private val properties = AppProperties(maxDteDefault = 90)

    private lateinit var controller: ChainController

    @BeforeEach
    fun setup() {
        controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, properties)
    }

    @Test
    fun `getExpirations returns cached expirations filtered by default maxDte`() {
        val today = LocalDate.now()
        val within90 = today.plusDays(30)
        val beyond90 = today.plusDays(180)
        val allExpirations = listOf(within90, beyond90)

        every { quoteCacheService.getExpirations("AAPL") } returns allExpirations
        every { quoteCacheService.getQuote("AAPL") } returns mockk { every { last } returns BigDecimal("150.00") }

        val response = controller.getExpirations("AAPL", null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(within90), response.body!!.expirations)
        verify(exactly = 0) { ibkrClient.requestOptionExpirations(any()) }
    }

    @Test
    fun `getExpirations fetches from IBKR on cache miss and caches raw result`() {
        val today = LocalDate.now()
        val within90 = today.plusDays(30)
        val beyond90 = today.plusDays(180)

        every { quoteCacheService.getExpirations("SPY") } returns null
        every { ibkrClient.requestOptionExpirations("SPY") } returns listOf(within90, beyond90)
        every { quoteCacheService.cacheExpirations("SPY", listOf(within90, beyond90)) } just Runs
        every { quoteCacheService.getQuote("SPY") } returns mockk { every { last } returns BigDecimal("450.00") }

        val response = controller.getExpirations("SPY", null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(within90), response.body!!.expirations)
        verify { quoteCacheService.cacheExpirations("SPY", listOf(within90, beyond90)) }
    }

    @Test
    fun `getExpirations respects custom maxDte parameter`() {
        val today = LocalDate.now()
        val in30 = today.plusDays(30)
        val in60 = today.plusDays(60)
        val in120 = today.plusDays(120)

        every { quoteCacheService.getExpirations("QQQ") } returns listOf(in30, in60, in120)
        every { quoteCacheService.getQuote("QQQ") } returns mockk { every { last } returns BigDecimal("400.00") }

        val response = controller.getExpirations("QQQ", 45)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(in30), response.body!!.expirations)
    }

    @Test
    fun `getExpirations returns 404 when IBKR returns empty`() {
        every { quoteCacheService.getExpirations("XYZ") } returns null
        every { ibkrClient.requestOptionExpirations("XYZ") } returns emptyList()
        every { quoteCacheService.getQuote("XYZ") } returns mockk { every { last } returns BigDecimal("10.00") }

        val response = controller.getExpirations("XYZ", null)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && ./gradlew :market-data:test --tests "*.ChainControllerTest" -i
```

Expected: Compilation error — `ChainController` constructor doesn't accept `AppProperties` yet, and `getExpirations` doesn't accept `maxDte`.

- [ ] **Step 3: Implement cache-first expirations with DTE filtering**

Modify `ChainController.kt`:

1. Add import:
```kotlin
import com.portfolio.marketdata.config.AppProperties
import java.time.temporal.ChronoUnit
```

2. Add `properties` to constructor:
```kotlin
class ChainController(
    private val quoteCacheService: QuoteCacheService,
    private val chainBuilder: OptionsChainBuilder,
    private val greeksCalculator: GreeksCalculator,
    private val ibkrClient: IbkrClient,
    private val properties: AppProperties
) {
```

3. Replace the `getExpirations` method:
```kotlin
@GetMapping("/{underlying}/expirations")
fun getExpirations(
    @PathVariable underlying: String,
    @RequestParam(required = false) maxDte: Int?
): ResponseEntity<OptionExpirationsResponse> {
    val spotPrice = resolveSpotPrice(underlying) ?: return ResponseEntity.notFound().build()

    val allExpirations = quoteCacheService.getExpirations(underlying)
        ?: ibkrClient.requestOptionExpirations(underlying).also { exps ->
            if (exps.isNotEmpty()) quoteCacheService.cacheExpirations(underlying, exps)
        }

    if (allExpirations.isEmpty()) return ResponseEntity.notFound().build()

    val effectiveMaxDte = maxDte ?: properties.maxDteDefault
    val today = LocalDate.now()
    val filtered = allExpirations.filter {
        ChronoUnit.DAYS.between(today, it) in 0..effectiveMaxDte.toLong()
    }

    if (filtered.isEmpty()) return ResponseEntity.notFound().build()
    return ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice, filtered))
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./gradlew :market-data:test --tests "*.ChainControllerTest" -i
```

Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/ChainController.kt backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerTest.kt
git commit -m "feat(market-data): cache-first expirations with configurable DTE filter"
```

---

### Task 4: Add Side Filter to ChainController `/expiry/{expiry}`

**Files:**
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/ChainController.kt`
- Modify: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerTest.kt`

- [ ] **Step 1: Write failing tests for side filtering**

Add to `ChainControllerTest.kt`:

```kotlin
import com.portfolio.common.domain.*
import com.portfolio.marketdata.ibkr.OptionContractDetails

// ... inside the class:

@Test
fun `getChainForExpiry with side=put filters to puts only`() {
    val expiry = LocalDate.now().plusDays(30)
    val putContract = OptionContractDetails(
        conId = 1, symbol = "AAPL", secType = "OPT", exchange = "SMART",
        expiry = expiry, strike = BigDecimal("150"), right = "P"
    )
    val callContract = OptionContractDetails(
        conId = 2, symbol = "AAPL", secType = "OPT", exchange = "SMART",
        expiry = expiry, strike = BigDecimal("150"), right = "C"
    )
    val putQuote = OptionQuote(
        underlying = "AAPL", optionType = OptionType.PUT, strike = BigDecimal("150"),
        expiry = expiry, bid = BigDecimal("2.50"), ask = BigDecimal("2.80"),
        last = BigDecimal("2.65"), volume = 100, openInterest = 0,
        greeks = null, timestamp = java.time.Instant.now()
    )
    val chain = OptionsChain("AAPL", BigDecimal("155"),
        mapOf(expiry to mapOf(BigDecimal("150") to StrikeData(call = null, put = putQuote)))
    )

    every { quoteCacheService.getQuote("AAPL") } returns mockk { every { last } returns BigDecimal("155.00") }
    every { ibkrClient.requestContractDetails("AAPL", "OPT", expiry) } returns listOf(putContract, callContract)
    every { ibkrClient.requestMarketDataSnapshot(1) } returns mockk {
        every { bid } returns 2.50; every { ask } returns 2.80; every { last } returns 2.65
        every { volume } returns 100; every { delta } returns -0.35; every { gamma } returns 0.02
        every { theta } returns -0.05; every { vega } returns 0.15
    }
    every { chainBuilder.build("AAPL", BigDecimal("155"), any()) } returns chain

    val response = controller.getChainForExpiry("AAPL", expiry.toString(), 0.45, 25, "put")

    assertEquals(HttpStatus.OK, response.statusCode)
    // Verify only put contract was passed to snapshot fetching
    verify(exactly = 0) { ibkrClient.requestMarketDataSnapshot(2) }
}

@Test
fun `getChainForExpiry with side=both returns all contracts`() {
    val expiry = LocalDate.now().plusDays(30)
    val contracts = listOf(
        OptionContractDetails(conId = 1, symbol = "SPY", secType = "OPT", exchange = "SMART",
            expiry = expiry, strike = BigDecimal("450"), right = "P"),
        OptionContractDetails(conId = 2, symbol = "SPY", secType = "OPT", exchange = "SMART",
            expiry = expiry, strike = BigDecimal("450"), right = "C")
    )
    val chain = OptionsChain("SPY", BigDecimal("455"), emptyMap())

    every { quoteCacheService.getQuote("SPY") } returns mockk { every { last } returns BigDecimal("455.00") }
    every { ibkrClient.requestContractDetails("SPY", "OPT", expiry) } returns contracts
    every { ibkrClient.requestMarketDataSnapshot(any()) } returns null
    every { chainBuilder.build("SPY", BigDecimal("455"), any()) } returns chain

    val response = controller.getChainForExpiry("SPY", expiry.toString(), 0.45, 25, "both")

    assertEquals(HttpStatus.OK, response.statusCode)
    verify { ibkrClient.requestMarketDataSnapshot(1) }
    verify { ibkrClient.requestMarketDataSnapshot(2) }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && ./gradlew :market-data:test --tests "*.ChainControllerTest" -i
```

Expected: Compilation error — `getChainForExpiry` doesn't accept `side` parameter.

- [ ] **Step 3: Add side parameter to getChainForExpiry and buildChainForExpiry**

Modify `ChainController.kt`:

1. Update `getChainForExpiry`:
```kotlin
@GetMapping("/{underlying}/expiry/{expiry}")
fun getChainForExpiry(
    @PathVariable underlying: String,
    @PathVariable expiry: String,
    @RequestParam(defaultValue = "0.45") maxDelta: Double,
    @RequestParam(defaultValue = "25") strikesPerSide: Int,
    @RequestParam(defaultValue = "both") side: String
): ResponseEntity<OptionsChainResponse> {
    val expiryDate = try { LocalDate.parse(expiry) } catch (_: Exception) {
        return ResponseEntity.badRequest().build()
    }
    val chain = buildChainForExpiry(underlying, expiryDate, maxDelta, strikesPerSide, side) ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(OptionsChainResponse.fromDomain(chain))
}
```

2. Update `buildChainForExpiry` signature and add side filter before `filterByStrikeCount`:
```kotlin
private fun buildChainForExpiry(underlying: String, expiry: LocalDate, maxDelta: Double, strikesPerSide: Int = 25, side: String = "both"): OptionsChain? {
    val spotPrice = resolveSpotPrice(underlying) ?: return null

    val contracts = try {
        ibkrClient.requestContractDetails(underlying, "OPT", expiry).filter { c ->
            c.tradingClass == null || c.tradingClass == underlying
        }.ifEmpty { ibkrClient.requestContractDetails(underlying, "OPT", expiry) }
    } catch (e: Exception) {
        log.error("Failed to load contracts for {} expiry {}", underlying, expiry, e)
        return null
    }
    if (contracts.isEmpty()) return null

    val sideFiltered = when (side.lowercase()) {
        "put" -> contracts.filter { it.right?.uppercase() in setOf("P", "PUT") }
        "call" -> contracts.filter { it.right?.uppercase() in setOf("C", "CALL") }
        else -> contracts
    }
    if (sideFiltered.isEmpty()) return null

    val filtered = filterByStrikeCount(sideFiltered, spotPrice, expiry, strikesPerSide)

    log.info("Fetching snapshots for {} contracts ({} per side, side={}) for {} expiry {}",
        filtered.size, strikesPerSide, side, underlying, expiry)

    val snapshots = fetchSnapshots(filtered)

    val optionQuotes = filtered.mapNotNull { contract ->
        buildOptionQuote(underlying, contract, spotPrice, snapshots)
    }

    return chainBuilder.build(underlying, spotPrice, optionQuotes)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./gradlew :market-data:test --tests "*.ChainControllerTest" -i
```

Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/ChainController.kt backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerTest.kt
git commit -m "feat(market-data): add side filter to per-expiry chain endpoint"
```

---

### Task 5: Add Side Filter to OptionStreamingService and QuoteWebSocketHandler

**Files:**
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/streaming/OptionStreamingService.kt`
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/QuoteWebSocketHandler.kt`

- [ ] **Step 1: Update OptionStreamingService methods to accept side parameter**

In `OptionStreamingService.kt`:

1. Update `startStreamingChainForExpiryPublic`:
```kotlin
fun startStreamingChainForExpiryPublic(underlying: String, expiry: LocalDate, side: String? = null) {
    startStreamingChainForExpiry(underlying, expiry, side)
}
```

2. Update `switchChainExpiry`:
```kotlin
fun switchChainExpiry(underlying: String, expiry: LocalDate, side: String? = null) {
    stopStreamingChain(underlying)
    startStreamingChainForExpiry(underlying, expiry, side)
}
```

3. Update `startStreamingChainForExpiry` signature and add side filter before delta filtering:
```kotlin
private fun startStreamingChainForExpiry(underlying: String, targetExpiry: LocalDate?, side: String? = null) {
```

After the `contracts` variable is assigned (either from `requestContractDetails` or `requestOptionChain`), add the side filter:

```kotlin
    val sideFiltered = if (side != null) {
        contracts.filter { c ->
            when (side.lowercase()) {
                "put" -> c.right?.uppercase() in setOf("P", "PUT")
                "call" -> c.right?.uppercase() in setOf("C", "CALL")
                else -> true
            }
        }
    } else contracts
```

Then use `sideFiltered` in place of `contracts` for the `toSubscribe` filter chain:
```kotlin
    val toSubscribe = sideFiltered.filter { c ->
        c.conId > 0 && c.expiry == expiry && c.strike != null && c.right != null
    }.filter { c ->
```

Update the log message to include side:
```kotlin
    log.info("Started chain streaming for {} — {} contracts subscribed (delta≤{}, expiry {}, side={})",
        underlying, toSubscribe.size, MAX_DELTA, expiry, side ?: "both")
```

- [ ] **Step 2: Update QuoteWebSocketHandler to parse and pass side**

In `QuoteWebSocketHandler.kt`, update the `subscribe_chain_expiry` and `switch_chain_expiry` cases:

```kotlin
"subscribe_chain_expiry" -> {
    val underlying = tree.get("underlying")?.asText() ?: return
    val expiry = tree.get("expiry")?.asText() ?: return
    val side = tree.get("side")?.asText()
    chainSubscriptions.computeIfAbsent(session.id) { ConcurrentHashMap.newKeySet() }.add(underlying)
    chainToSessions.computeIfAbsent(underlying) { ConcurrentHashMap.newKeySet() }.add(session.id)
    optionStreamingService.startStreamingChainForExpiryPublic(underlying, LocalDate.parse(expiry), side)
    logger.info("Session {} subscribed to chain {} expiry {} side={}", session.id, underlying, expiry, side ?: "both")
}
"switch_chain_expiry" -> {
    val underlying = tree.get("underlying")?.asText() ?: return
    val expiry = tree.get("expiry")?.asText() ?: return
    val side = tree.get("side")?.asText()
    optionStreamingService.switchChainExpiry(underlying, LocalDate.parse(expiry), side)
    logger.info("Session {} switched chain {} to expiry {} side={}", session.id, underlying, expiry, side ?: "both")
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd backend && ./gradlew :market-data:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all existing tests**

```bash
cd backend && ./gradlew :market-data:test -i
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/streaming/OptionStreamingService.kt backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/QuoteWebSocketHandler.kt
git commit -m "feat(market-data): add side filter to WebSocket chain streaming"
```

---

### Task 6: Update Frontend marketDataService with strikesPerSide and side params

**Files:**
- Modify: `frontend/src/services/marketDataService.ts`

- [ ] **Step 1: Update getOptionsChainForExpiry to accept options**

Replace the current function:

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

- [ ] **Step 2: Verify TypeScript compilation**

```bash
cd frontend && npx tsc --noEmit
```

Expected: No errors. Existing callers pass `(underlying, expiry)` which is still valid since `opts` is optional.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/services/marketDataService.ts
git commit -m "feat(frontend): add strikesPerSide and side params to chain API call"
```

---

### Task 7: Update useMarketDataWebSocket with side parameter

**Files:**
- Modify: `frontend/src/hooks/useMarketDataWebSocket.ts`

- [ ] **Step 1: Update subscribeChainExpiry and switchChainExpiry to accept side**

Replace the two callbacks:

```typescript
const subscribeChainExpiry = useCallback((underlying: string, expiry: string, side?: 'put' | 'call') => {
  subscribedChainsRef.current.add(underlying)
  if (wsRef.current?.readyState === WebSocket.OPEN) {
    const msg: Record<string, string> = { action: 'subscribe_chain_expiry', underlying, expiry }
    if (side) msg.side = side
    wsRef.current.send(JSON.stringify(msg))
  }
}, [])

const switchChainExpiry = useCallback((underlying: string, expiry: string, side?: 'put' | 'call') => {
  if (wsRef.current?.readyState === WebSocket.OPEN) {
    const msg: Record<string, string> = { action: 'switch_chain_expiry', underlying, expiry }
    if (side) msg.side = side
    wsRef.current.send(JSON.stringify(msg))
  }
}, [])
```

- [ ] **Step 2: Verify TypeScript compilation**

```bash
cd frontend && npx tsc --noEmit
```

Expected: No errors. Existing callers pass `(underlying, expiry)` — the new `side` param is optional.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/useMarketDataWebSocket.ts
git commit -m "feat(frontend): add side parameter to WebSocket chain subscriptions"
```

---

### Task 8: Add Strike Selector to WheelChainPanel + Pass side and strikesPerSide

**Files:**
- Modify: `frontend/src/components/wheel/WheelChainPanel.tsx`
- Modify: `frontend/src/components/wheel/WheelChainPanel.css`

- [ ] **Step 1: Add strike selector state and pass side/strikesPerSide to API and WebSocket**

In `WheelChainPanel.tsx`:

1. Add state after the existing state declarations (after line 23):
```typescript
const [strikesPerSide, setStrikesPerSide] = useState(25)
```

2. Derive `side` from `context.optionSide` (add after `isCsp` on line 30):
```typescript
const side = isCsp ? 'put' as const : 'call' as const
```

3. Update the `init()` function inside `useEffect` — change the `getOptionsChainForExpiry` call:
```typescript
const chainData = await getOptionsChainForExpiry(context.ticker, chosenExpiry, { strikesPerSide, side })
```

4. Update the `subscribeChainExpiry` call in init:
```typescript
subscribeChainExpiry(context.ticker, chosenExpiry, side)
```

5. Update `handleExpiryChange` — change the `getOptionsChainForExpiry` call:
```typescript
const chainData = await getOptionsChainForExpiry(context.ticker, newExpiry, { strikesPerSide, side })
```

6. Update `switchChainExpiry` in handleExpiryChange:
```typescript
switchChainExpiry(context.ticker, newExpiry, side)
```

7. Add a `handleStrikesChange` callback after `handleExpiryChange`:
```typescript
const handleStrikesChange = useCallback(async (newStrikes: number) => {
  setStrikesPerSide(newStrikes)
  setLoadingExpiry(true)
  try {
    const chainData = await getOptionsChainForExpiry(context.ticker, selectedExpiry, { strikesPerSide: newStrikes, side })
    setChain(context.ticker, chainData)
  } catch (err) {
    console.error('Failed to reload chain:', err)
  } finally {
    setLoadingExpiry(false)
  }
}, [context.ticker, selectedExpiry, side, setChain])
```

8. Add the useEffect dependency array — add `strikesPerSide` and `side` to the deps (but keep `context.ticker` and `context.expiryDate` as the re-trigger keys). Since `side` is derived from `context.optionSide` which is part of `context`, no extra dep needed.

9. Add the strike selector UI inside the `wcp2-expiry` div, after the mobile expiry section (before the closing `</div>` of `wcp2-expiry`):
```tsx
<div className="wcp2-strikes">
  <span className="wcp2-strikes__label">Strikes</span>
  <div className="wcp2-strikes__options">
    {[25, 50, 60].map(n => (
      <button
        key={n}
        className={`wcp2-strikes__btn ${strikesPerSide === n ? 'wcp2-strikes__btn--active' : ''}`}
        onClick={() => handleStrikesChange(n)}
      >
        {n}
      </button>
    ))}
  </div>
</div>
```

- [ ] **Step 2: Add CSS for the strike selector**

Append to `WheelChainPanel.css`:

```css
.wcp2-strikes {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.wcp2-strikes__label {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--text-muted);
  font-weight: 500;
}
.wcp2-strikes__options {
  display: flex;
  gap: 2px;
  background: rgba(255,255,255,0.04);
  border-radius: var(--radius-sm);
  padding: 2px;
}
.wcp2-strikes__btn {
  padding: 3px 10px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 11px;
  font-family: inherit;
  font-weight: 500;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}
.wcp2-strikes__btn:hover {
  color: var(--text-secondary);
}
.wcp2-strikes__btn--active {
  background: rgba(255,255,255,0.1);
  color: var(--text-primary);
}
```

- [ ] **Step 3: Verify TypeScript compilation**

```bash
cd frontend && npx tsc --noEmit
```

Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/wheel/WheelChainPanel.tsx frontend/src/components/wheel/WheelChainPanel.css
git commit -m "feat(wheel): add strike selector (25/50/60) and pass side filter"
```

---

### Task 9: Add Strike Selector to OptionsChainTable + Pass strikesPerSide from OptionsPage

**Files:**
- Modify: `frontend/src/components/options/OptionsChainTable.tsx`
- Modify: `frontend/src/components/options/OptionsChainTable.css`
- Modify: `frontend/src/pages/OptionsPage.tsx`

- [ ] **Step 1: Add strikesPerSide to OptionsChainTable props and UI**

In `OptionsChainTable.tsx`:

1. Update the props interface:
```typescript
interface OptionsChainTableProps {
  chain: OptionsChain
  onExpiryChange?: (expiry: string) => void
  strikesPerSide: number
  onStrikesPerSideChange: (n: number) => void
}
```

2. Update the function signature:
```typescript
export function OptionsChainTable({ chain, onExpiryChange, strikesPerSide, onStrikesPerSideChange }: OptionsChainTableProps) {
```

3. Add the strike selector inside the `chain-table__expiry-tabs` div, after the expiry buttons:
```tsx
<div className="chain-table__expiry-tabs">
  {expirations.map((exp) => (
    <button
      key={exp}
      className={`chain-table__expiry-tab ${selectedExpiry === exp ? 'chain-table__expiry-tab--active' : ''}`}
      onClick={() => { setSelectedExpiry(exp); onExpiryChange?.(exp) }}
    >
      {exp}
    </button>
  ))}
  <div className="chain-table__strikes-selector">
    <span className="chain-table__strikes-label">Strikes</span>
    {[25, 50, 60].map(n => (
      <button
        key={n}
        className={`chain-table__strikes-btn ${strikesPerSide === n ? 'chain-table__strikes-btn--active' : ''}`}
        onClick={() => onStrikesPerSideChange(n)}
      >
        {n}
      </button>
    ))}
  </div>
</div>
```

4. Also add the selector to the mobile controls section, after the side toggle div:
```tsx
<div className="chain-table__mobile-controls">
  <select ...>...</select>
  <div className="chain-table__side-toggle">...</div>
  <div className="chain-table__strikes-selector chain-table__strikes-selector--mobile">
    {[25, 50, 60].map(n => (
      <button
        key={n}
        className={`chain-table__strikes-btn ${strikesPerSide === n ? 'chain-table__strikes-btn--active' : ''}`}
        onClick={() => onStrikesPerSideChange(n)}
      >
        {n}
      </button>
    ))}
  </div>
</div>
```

- [ ] **Step 2: Add CSS for the strike selector**

Append to `OptionsChainTable.css`:

```css
.chain-table__strikes-selector {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  background: rgba(255,255,255,0.04);
  border-radius: 7px;
  padding: 3px;
  flex-shrink: 0;
}
.chain-table__strikes-label {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--text-muted);
  font-weight: 500;
  padding: 0 6px;
}
.chain-table__strikes-btn {
  padding: 4px 12px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  font-family: inherit;
  font-weight: 500;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.15s;
}
.chain-table__strikes-btn:hover {
  color: var(--text-secondary);
}
.chain-table__strikes-btn--active {
  background: var(--bg-secondary, #111827);
  color: var(--text-primary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}
```

- [ ] **Step 3: Update OptionsPage to manage strikesPerSide state and pass to API**

In `OptionsPage.tsx`:

1. Add state (after the existing state declarations around line 27):
```typescript
const [strikesPerSide, setStrikesPerSide] = useState(50)
```

2. Update `handleSearch` — change the `getOptionsChainForExpiry` call:
```typescript
const chainData = await getOptionsChainForExpiry(symbol, firstExpiry, { strikesPerSide })
```

3. Update `handleExpiryChange` — change the `getOptionsChainForExpiry` call:
```typescript
const expiryData = await getOptionsChainForExpiry(selectedUnderlying, expiry, { strikesPerSide })
```

4. Add `handleStrikesPerSideChange` callback:
```typescript
const handleStrikesPerSideChange = useCallback(async (newStrikes: number) => {
  setStrikesPerSide(newStrikes)
  if (!selectedUnderlying) return
  const chain = chains[selectedUnderlying]
  if (!chain) return
  const currentExpiry = Object.keys(chain.expirations).sort()[0]
  if (!currentExpiry) return
  try {
    const chainData = await getOptionsChainForExpiry(selectedUnderlying, currentExpiry, { strikesPerSide: newStrikes })
    setChain(selectedUnderlying, { ...chain, expirations: { ...chain.expirations, ...chainData.expirations } })
  } catch (err) {
    console.error('Failed to reload chain with new strikes:', err)
  }
}, [selectedUnderlying, chains, setChain])
```

5. Update `handleSearch` and `handleStrikesPerSideChange` dependency arrays to include `strikesPerSide`.

6. Pass props to `OptionsChainTable`:
```tsx
<OptionsChainTable
  chain={chain}
  onExpiryChange={handleExpiryChange}
  strikesPerSide={strikesPerSide}
  onStrikesPerSideChange={handleStrikesPerSideChange}
/>
```

- [ ] **Step 4: Verify TypeScript compilation**

```bash
cd frontend && npx tsc --noEmit
```

Expected: No errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/options/OptionsChainTable.tsx frontend/src/components/options/OptionsChainTable.css frontend/src/pages/OptionsPage.tsx
git commit -m "feat(options): add strike selector (25/50/60) with default 50"
```

---

### Task 10: Build, Deploy, and Manual Verification

**Files:** None (deployment + manual testing)

- [ ] **Step 1: Run full backend test suite**

```bash
cd backend && ./gradlew test -i
```

Expected: All tests PASS.

- [ ] **Step 2: Run frontend type check**

```bash
cd frontend && npx tsc --noEmit
```

Expected: No errors.

- [ ] **Step 3: Rebuild and deploy docker containers**

```bash
docker compose build market-data-service frontend
docker compose up -d market-data-service frontend
```

- [ ] **Step 4: Manual verification checklist**

Open the app in the browser and verify:

1. **Expirations cache**: Load a ticker's options chain. Reload the page — the expirations should load near-instantly (~50ms vs previous ~2-3s). Check Redis with `docker exec portfolio-redis redis-cli GET "expirations:SOXL"` to confirm the cache entry exists.

2. **DTE filtering**: Verify the expiry dropdown only shows dates within ~90 days (no LEAPS like 2028-01-21 that appeared before).

3. **Wheel page — side filter**: Open CSP mode for a ticker. Check docker logs for `side=put` in the chain fetch log. Verify only put data is shown. Switch to CC mode — verify only call data is shown.

4. **Wheel page — strike selector**: Click 25/50/60 buttons. Verify the chain reloads with more/fewer strikes. The loading indicator should appear briefly.

5. **Options page — strike selector**: Search for a symbol. Verify the 25/50/60 selector appears in the expiry tabs bar. Click each option and verify the chain table updates.

6. **WebSocket streaming**: After loading a chain, verify live price updates still flow (the "Live" indicator should show). Change expiry and verify streaming switches correctly.

- [ ] **Step 5: Commit any final adjustments and push**

```bash
git push -u origin feature/options-chain-performance
```
