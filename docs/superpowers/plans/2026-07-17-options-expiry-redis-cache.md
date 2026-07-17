# Options Expiry Redis Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement scheduled refresh and on-demand fallback for options expiry dates with 90-day Redis TTL

**Architecture:** Two-tier expiry sourcing: scheduled weekly refresh (primary) + on-demand IBKR fallback (secondary). New `expiry:{symbol}` Redis keys with 90-day TTL. Configurable via application.yml with env var overrides.

**Tech Stack:** Kotlin, Spring Boot 3.3.5, Redis (RedisTemplate), JUnit 5, MockK

## Global Constraints

- Kotlin, Spring Boot 3.3.5, Java 21
- RedisTemplate<String, String> with ObjectMapper serialization
- @ConfigurationProperties for config
- @EnableScheduling already present on MarketDataApplication
- JUnit 5 + MockK for testing
- No Spring context in unit tests (pure constructor injection)

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `backend/market-data/src/main/kotlin/com/portfolio/marketdata/config/ExpiryProperties.kt` | Create | Configuration properties for expiry refresh |
| `backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/ExpiryCacheService.kt` | Create | Redis cache operations for expiry |
| `backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/ExpiryRefreshService.kt` | Create | Scheduled refresh logic |
| `backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/ChainController.kt` | Modify | Use new ExpiryCacheService |
| `backend/market-data/src/main/resources/application.yml` | Modify | Add expiry configuration |
| `backend/market-data/src/test/kotlin/com/portfolio/marketdata/config/ExpiryPropertiesTest.kt` | Create | Properties binding test |
| `backend/market-data/src/test/kotlin/com/portfolio/marketdata/distribution/ExpiryCacheServiceTest.kt` | Create | Cache operations test |
| `backend/market-data/src/test/kotlin/com/portfolio/marketdata/distribution/ExpiryRefreshServiceTest.kt` | Create | Refresh logic test |
| `backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerExpiryTest.kt` | Create | Controller expiry integration test |

---

### Task 1: ExpiryProperties Configuration

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/config/ExpiryProperties.kt`
- Test: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/config/ExpiryPropertiesTest.kt`

**Interfaces:**
- Produces: `ExpiryProperties` data class with `refresh.cron`, `refresh.symbols`, `cache.ttlDays`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.portfolio.marketdata.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpiryPropertiesTest {

    @Test
    fun `default values are correct`() {
        val props = ExpiryProperties()
        assertEquals("0 0 8 ? * MON", props.refresh.cron)
        assertEquals(90, props.cache.ttlDays)
        assertEquals(
            listOf("SOXL", "TECL", "TQQQ", "SPXU", "SPY", "QQQ", "XLF", "NVDA", "AVGO"),
            props.refresh.symbols
        )
    }

    @Test
    fun `custom values are applied`() {
        val props = ExpiryProperties(
            refresh = ExpiryProperties.Refresh(
                cron = "0 0 12 ? * WED",
                symbols = listOf("AAPL", "MSFT")
            ),
            cache = ExpiryProperties.Cache(ttlDays = 30)
        )
        assertEquals("0 0 12 ? * WED", props.refresh.cron)
        assertEquals(listOf("AAPL", "MSFT"), props.refresh.symbols)
        assertEquals(30, props.cache.ttlDays)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.config.ExpiryPropertiesTest" 2>&1 | tail -20`
Expected: FAIL with "Unresolved reference: ExpiryProperties"

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.portfolio.marketdata.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "expiry")
data class ExpiryProperties(
    val refresh: Refresh = Refresh(),
    val cache: Cache = Cache()
) {
    data class Refresh(
        val cron: String = "0 0 8 ? * MON",
        val symbols: List<String> = listOf("SOXL", "TECL", "TQQQ", "SPXU", "SPY", "QQQ", "XLF", "NVDA", "AVGO")
    )

    data class Cache(
        val ttlDays: Long = 90
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.config.ExpiryPropertiesTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/config/ExpiryProperties.kt \
       backend/market-data/src/test/kotlin/com/portfolio/marketdata/config/ExpiryPropertiesTest.kt
git commit -m "feat(market-data): add ExpiryProperties configuration class"
```

---

### Task 2: ExpiryCacheService

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/ExpiryCacheService.kt`
- Test: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/distribution/ExpiryCacheServiceTest.kt`

**Interfaces:**
- Consumes: `RedisTemplate<String, String>`, `ObjectMapper`, `ExpiryProperties`
- Produces: `ExpiryCacheService` with `getExpiry(symbol)`, `cacheExpiry(symbol, expirations)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.portfolio.marketdata.distribution

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.portfolio.marketdata.config.ExpiryProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpiryCacheServiceTest {

    private val redisTemplate = mockk<RedisTemplate<String, String>>()
    private val opsForValue = mockk<ValueOperations<String, String>>()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(kotlinModule())
    }
    private val properties = ExpiryProperties(
        cache = ExpiryProperties.Cache(ttlDays = 90)
    )
    private lateinit var service: ExpiryCacheService

    @BeforeEach
    fun setup() {
        every { redisTemplate.opsForValue() } returns opsForValue
        service = ExpiryCacheService(redisTemplate, objectMapper, properties)
    }

    @Test
    fun `cacheExpiry stores expirations with correct key and TTL`() {
        val symbol = "SOXL"
        val expirations = listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 25))
        val expectedJson = objectMapper.writeValueAsString(expirations)
        val expectedTtlDays = 90L

        service.cacheExpiry(symbol, expirations)

        verify {
            opsForValue.set(
                "expiry:$symbol",
                expectedJson,
                expectedTtlDays,
                java.util.concurrent.TimeUnit.DAYS
            )
        }
    }

    @Test
    fun `getExpiry returns expirations when cache hit`() {
        val symbol = "SOXL"
        val expirations = listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 25))
        val json = objectMapper.writeValueAsString(expirations)

        every { opsForValue.get("expiry:$symbol") } returns json

        val result = service.getExpiry(symbol)

        assertEquals(expirations, result)
    }

    @Test
    fun `getExpiry returns null when cache miss`() {
        val symbol = "SOXL"
        every { opsForValue.get("expiry:$symbol") } returns null

        val result = service.getExpiry(symbol)

        assertNull(result)
    }

    @Test
    fun `getExpiry returns null when deserialization fails`() {
        val symbol = "SOXL"
        every { opsForValue.get("expiry:$symbol") } returns "invalid json"

        val result = service.getExpiry(symbol)

        assertNull(result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.distribution.ExpiryCacheServiceTest" 2>&1 | tail -20`
Expected: FAIL with "Unresolved reference: ExpiryCacheService"

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.portfolio.marketdata.distribution

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.marketdata.config.ExpiryProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Service
class ExpiryCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val properties: ExpiryProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val KEY_PREFIX = "expiry:"
    }

    fun cacheExpiry(symbol: String, expirations: List<LocalDate>) {
        val key = "$KEY_PREFIX$symbol"
        val json = objectMapper.writeValueAsString(expirations)
        val ttlDays = properties.cache.ttlDays
        redisTemplate.opsForValue().set(key, json, ttlDays, TimeUnit.DAYS)
        log.debug("Cached {} expirations for {} with TTL {} days", expirations.size, symbol, ttlDays)
    }

    fun getExpiry(symbol: String): List<LocalDate>? {
        val key = "$KEY_PREFIX$symbol"
        val json = redisTemplate.opsForValue().get(key) ?: return null
        return try {
            objectMapper.readValue(json, objectMapper.typeFactory.constructCollectionType(List::class.java, LocalDate::class.java))
        } catch (e: Exception) {
            log.warn("Failed to deserialize expirations for {}: {}", symbol, e.message)
            null
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.distribution.ExpiryCacheServiceTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/ExpiryCacheService.kt \
       backend/market-data/src/test/kotlin/com/portfolio/marketdata/distribution/ExpiryCacheServiceTest.kt
git commit -m "feat(market-data): add ExpiryCacheService for Redis operations"
```

---

### Task 3: ExpiryRefreshService

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/ExpiryRefreshService.kt`
- Test: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/distribution/ExpiryRefreshServiceTest.kt`

**Interfaces:**
- Consumes: `IbkrClient`, `ExpiryCacheService`, `ExpiryProperties`
- Produces: `ExpiryRefreshService` with `refreshAll()`, `refreshSymbol(symbol)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.portfolio.marketdata.distribution

import com.portfolio.marketdata.config.ExpiryProperties
import com.portfolio.marketdata.ibkr.IbkrClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ExpiryRefreshServiceTest {

    private val ibkrClient = mockk<IbkrClient>()
    private val expiryCacheService = mockk<ExpiryCacheService>(relaxed = true)
    private val properties = ExpiryProperties(
        refresh = ExpiryProperties.Refresh(
            symbols = listOf("SOXL", "TECL", "TQQQ")
        )
    )
    private lateinit var service: ExpiryRefreshService

    @BeforeEach
    fun setup() {
        service = ExpiryRefreshService(ibkrClient, expiryCacheService, properties)
    }

    @Test
    fun `refreshAll fetches and caches expirations for all configured symbols`() {
        val soxlExpirations = listOf(LocalDate.of(2026, 7, 18))
        val teclExpirations = listOf(LocalDate.of(2026, 7, 25))
        val tqqqExpirations = listOf(LocalDate.of(2026, 8, 1))

        every { ibkrClient.requestOptionExpirations("SOXL") } returns soxlExpirations
        every { ibkrClient.requestOptionExpirations("TECL") } returns teclExpirations
        every { ibkrClient.requestOptionExpirations("TQQQ") } returns tqqqExpirations

        service.refreshAll()

        verify { expiryCacheService.cacheExpiry("SOXL", soxlExpirations) }
        verify { expiryCacheService.cacheExpiry("TECL", teclExpirations) }
        verify { expiryCacheService.cacheExpiry("TQQQ", tqqqExpirations) }
    }

    @Test
    fun `refreshSymbol fetches and caches expirations for single symbol`() {
        val expirations = listOf(LocalDate.of(2026, 7, 18))
        every { ibkrClient.requestOptionExpirations("SOXL") } returns expirations

        service.refreshSymbol("SOXL")

        verify { expiryCacheService.cacheExpiry("SOXL", expirations) }
    }

    @Test
    fun `refreshSymbol handles IBKR failure gracefully`() {
        every { ibkrClient.requestOptionExpirations("SOXL") } throws RuntimeException("IBKR disconnected")

        service.refreshSymbol("SOXL")

        verify(exactly = 0) { expiryCacheService.cacheExpiry(any(), any()) }
    }

    @Test
    fun `refreshAll continues when one symbol fails`() {
        every { ibkrClient.requestOptionExpirations("SOXL") } throws RuntimeException("IBKR disconnected")
        every { ibkrClient.requestOptionExpirations("TECL") } returns listOf(LocalDate.of(2026, 7, 25))
        every { ibkrClient.requestOptionExpirations("TQQQ") } returns listOf(LocalDate.of(2026, 8, 1))

        service.refreshAll()

        verify(exactly = 0) { expiryCacheService.cacheExpiry("SOXL", any()) }
        verify { expiryCacheService.cacheExpiry("TECL", any()) }
        verify { expiryCacheService.cacheExpiry("TQQQ", any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.distribution.ExpiryRefreshServiceTest" 2>&1 | tail -20`
Expected: FAIL with "Unresolved reference: ExpiryRefreshService"

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.portfolio.marketdata.distribution

import com.portfolio.marketdata.config.ExpiryProperties
import com.portfolio.marketdata.ibkr.IbkrClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ExpiryRefreshService(
    private val ibkrClient: IbkrClient,
    private val expiryCacheService: ExpiryCacheService,
    private val properties: ExpiryProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${expiry.refresh.cron:0 0 8 ? * MON}")
    fun refreshAll() {
        log.info("Starting scheduled expiry refresh for {} symbols", properties.refresh.symbols.size)
        var successCount = 0
        var failCount = 0

        for (symbol in properties.refresh.symbols) {
            try {
                refreshSymbol(symbol)
                successCount++
            } catch (e: Exception) {
                log.error("Failed to refresh expirations for {}: {}", symbol, e.message)
                failCount++
            }
        }

        log.info("Expiry refresh complete: {} succeeded, {} failed", successCount, failCount)
    }

    fun refreshSymbol(symbol: String) {
        log.debug("Refreshing expirations for {}", symbol)
        val expirations = ibkrClient.requestOptionExpirations(symbol)
        expiryCacheService.cacheExpiry(symbol, expirations)
        log.debug("Cached {} expirations for {}", expirations.size, symbol)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.distribution.ExpiryRefreshServiceTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/ExpiryRefreshService.kt \
       backend/market-data/src/test/kotlin/com/portfolio/marketdata/distribution/ExpiryRefreshServiceTest.kt
git commit -m "feat(market-data): add ExpiryRefreshService for scheduled refresh"
```

---

### Task 4: Update ChainController to Use New Cache

**Files:**
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/ChainController.kt:117-182`
- Modify: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerTest.kt:46` (update constructor call)
- Create: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerExpiryTest.kt`

**Interfaces:**
- Consumes: `ExpiryCacheService` (new), `IbkrClient` (existing)
- Produces: Updated `getExpirations()` method with on-demand fallback

- [ ] **Step 1: Write the failing test**

```kotlin
package com.portfolio.marketdata.api.controller

import com.portfolio.marketdata.config.AppProperties
import com.portfolio.marketdata.distribution.ExpiryCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class ChainControllerExpiryTest {

    private val quoteCacheService = mockk<com.portfolio.marketdata.distribution.QuoteCacheService>(relaxed = true)
    private val ibkrClient = mockk<IbkrClient>()
    private val expiryCacheService = mockk<ExpiryCacheService>()
    private val properties = AppProperties(maxDteDefault = 90)
    private lateinit var controller: ChainController

    @BeforeEach
    fun setup() {
        // Mock getChain to return null (no cached chain)
        every { quoteCacheService.getChain(any()) } returns null
        // Mock getExpirations to return null (no cached expirations)
        every { quoteCacheService.getExpirations(any()) } returns null
        controller = ChainController(
            quoteCacheService = quoteCacheService,
            chainBuilder = mockk(relaxed = true),
            greeksCalculator = mockk(relaxed = true),
            ibkrClient = ibkrClient,
            properties = properties,
            chainRequestTimeoutSeconds = 15,
            chainBuildParallelism = 2,
            expiryCacheService = expiryCacheService
        )
    }

    @Test
    fun `getExpirations uses ExpiryCacheService first`() {
        val cachedExpirations = listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 25))
        every { expiryCacheService.getExpiry("SOXL") } returns cachedExpirations
        every { quoteCacheService.getSpotPrice("SOXL") } returns BigDecimal("50.00")

        val response = controller.getExpirations("SOXL", null)

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body?.expirations?.size)
        verify(exactly = 0) { ibkrClient.requestOptionExpirations(any()) }
    }

    @Test
    fun `getExpirations falls back to IBKR on cache miss`() {
        val ibkrExpirations = listOf(LocalDate.of(2026, 8, 1))
        every { expiryCacheService.getExpiry("SOXL") } returns null
        every { ibkrClient.requestOptionExpirations("SOXL") } returns ibkrExpirations
        every { quoteCacheService.getSpotPrice("SOXL") } returns BigDecimal("50.00")

        val response = controller.getExpirations("SOXL", null)

        assertEquals(200, response.statusCode.value())
        assertEquals(1, response.body?.expirations?.size)
        verify { expiryCacheService.cacheExpiry("SOXL", ibkrExpirations) }
    }

    @Test
    fun `getExpirations filters by maxDte`() {
        val expirations = listOf(
            LocalDate.now().plusDays(10),
            LocalDate.now().plusDays(50),
            LocalDate.now().plusDays(100)
        )
        every { expiryCacheService.getExpiry("SOXL") } returns expirations
        every { quoteCacheService.getSpotPrice("SOXL") } returns BigDecimal("50.00")

        val response = controller.getExpirations("SOXL", 30)

        assertEquals(200, response.statusCode.value())
        assertEquals(1, response.body?.expirations?.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.api.controller.ChainControllerExpiryTest" 2>&1 | tail -20`
Expected: FAIL with compilation error (constructor mismatch)

- [ ] **Step 3: Update ChainController constructor and getExpirations method**

```kotlin
// In ChainController.kt, update constructor to add ExpiryCacheService parameter:
class ChainController(
    private val quoteCacheService: QuoteCacheService,
    private val chainBuilder: OptionsChainBuilder,
    private val greeksCalculator: GreeksCalculator,
    private val ibkrClient: IbkrClient,
    private val properties: AppProperties,
    @Value("\${chain.request-timeout-seconds:15}") private val chainRequestTimeoutSeconds: Int,
    @Value("\${chain.build-parallelism:2}") private val chainBuildParallelism: Int,
    private val expiryCacheService: ExpiryCacheService
) {

// In getExpirations method, replace the IBKR fallback logic with:
@GetMapping("/{underlying}/expirations")
fun getExpirations(
    @PathVariable underlying: String,
    @RequestParam(required = false) maxDte: Int?
): ResponseEntity<OptionExpirationsResponse> {
    // Try to get spot price (needed for response)
    val spotPrice = quoteCacheService.getSpotPrice(underlying)

    // Tier 1: Check ExpiryCacheService (new 90-day cache)
    val cachedExpiry = expiryCacheService.getExpiry(underlying)
    if (cachedExpiry != null) {
        val filtered = filterByDte(cachedExpiry, maxDte ?: properties.maxDteDefault)
        return ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice, filtered))
    }

    // Tier 2: Check full chain cache
    val cachedChain = quoteCacheService.getChain(underlying)
    if (cachedChain != null) {
        val expirations = cachedChain.expirations.keys.toList()
        val filtered = filterByDte(expirations, maxDte ?: properties.maxDteDefault)
        return ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice, filtered))
    }

    // Tier 3: Check IBKR connection
    if (!ibkrClient.isConnected()) {
        log.warn("IBKR not connected, returning empty expirations for {}", underlying)
        return ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice, emptyList()))
    }

    // Tier 4: Fetch from IBKR (on-demand fallback)
    return try {
        val expirations = ibkrClient.requestOptionExpirations(underlying)
        // Cache the result for future requests
        expiryCacheService.cacheExpiry(underlying, expirations)
        log.info("Fetched and cached {} expirations for {} from IBKR", expirations.size, underlying)
        val filtered = filterByDte(expirations, maxDte ?: properties.maxDteDefault)
        ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice, filtered))
    } catch (e: Exception) {
        log.error("Failed to fetch expirations for {} from IBKR: {}", underlying, e.message)
        ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice, emptyList()))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.api.controller.ChainControllerExpiryTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 5: Update existing ChainControllerTest constructor call**

In `backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerTest.kt`, update the constructor call at line 46:

```kotlin
// Before:
controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, properties, 15, 2)

// After:
controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, properties, 15, 2, mockk(relaxed = true))
```

- [ ] **Step 6: Run all ChainController tests to verify no regressions**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.api.controller.ChainControllerTest" --tests "com.portfolio.marketdata.api.controller.ChainControllerExpiryTest" 2>&1 | tail -20`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/ChainController.kt \
       backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerTest.kt \
       backend/market-data/src/test/kotlin/com/portfolio/marketdata/api/controller/ChainControllerExpiryTest.kt
git commit -m "feat(market-data): update ChainController to use ExpiryCacheService"
```

---

### Task 5: Add Configuration to application.yml

**Files:**
- Modify: `backend/market-data/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `ExpiryProperties` (from Task 1)

- [ ] **Step 1: Add expiry configuration to application.yml**

```yaml
# Add to application.yml after the ibkr section:
expiry:
  refresh:
    cron: ${EXPIRY_REFRESH_CRON:0 0 8 ? * MON}
    symbols: ${EXPIRY_REFRESH_SYMBOLS:SOXL,TECL,TQQQ,SPXU,SPY,QQQ,XLF,NVDA,AVGO}
  cache:
    ttl-days: ${EXPIRY_CACHE_TTL_DAYS:90}
```

- [ ] **Step 2: Verify configuration loads**

Run: `cd backend/market-data && ./gradlew bootRun --args='--spring.main.web-application-type=none' 2>&1 | grep -A5 "expiry" | head -10`
Expected: Application starts without errors

- [ ] **Step 3: Commit**

```bash
git add backend/market-data/src/main/resources/application.yml
git commit -m "feat(market-data): add expiry configuration to application.yml"
```

---

### Task 6: Integration Test

**Files:**
- Create: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/integration/ExpiryCacheIntegrationTest.kt`

**Interfaces:**
- Consumes: All components from Tasks 1-5

- [ ] **Step 1: Write the integration test**

```kotlin
package com.portfolio.marketdata.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.portfolio.marketdata.config.ExpiryProperties
import com.portfolio.marketdata.distribution.ExpiryCacheService
import com.portfolio.marketdata.distribution.ExpiryRefreshService
import com.portfolio.marketdata.ibkr.IbkrClient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExpiryCacheIntegrationTest {

    private val redisTemplate = mockk<RedisTemplate<String, String>>()
    private val opsForValue = mockk<ValueOperations<String, String>>()
    private val ibkrClient = mockk<IbkrClient>()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(kotlinModule())
    }
    private val properties = ExpiryProperties(
        refresh = ExpiryProperties.Refresh(
            symbols = listOf("SOXL", "TECL")
        ),
        cache = ExpiryProperties.Cache(ttlDays = 90)
    )
    private lateinit var cacheService: ExpiryCacheService
    private lateinit var refreshService: ExpiryRefreshService

    @BeforeEach
    fun setup() {
        every { redisTemplate.opsForValue() } returns opsForValue
        cacheService = ExpiryCacheService(redisTemplate, objectMapper, properties)
        refreshService = ExpiryRefreshService(ibkrClient, cacheService, properties)
    }

    @Test
    fun `end-to-end: refresh populates cache, subsequent request reads from cache`() {
        // Arrange: IBKR returns expirations
        val soxlExpirations = listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 25))
        val teclExpirations = listOf(LocalDate.of(2026, 8, 1))
        every { ibkrClient.requestOptionExpirations("SOXL") } returns soxlExpirations
        every { ibkrClient.requestOptionExpirations("TECL") } returns teclExpirations

        // Act: Trigger refresh
        refreshService.refreshAll()

        // Assert: Cache was populated
        val cachedSoxlJson = objectMapper.writeValueAsString(soxlExpirations)
        val cachedTeclJson = objectMapper.writeValueAsString(teclExpirations)
        every { opsForValue.get("expiry:SOXL") } returns cachedSoxlJson
        every { opsForValue.get("expiry:TECL") } returns cachedTeclJson

        // Verify: Subsequent read returns cached data
        val soxlResult = cacheService.getExpiry("SOXL")
        val teclResult = cacheService.getExpiry("TECL")

        assertEquals(soxlExpirations, soxlResult)
        assertEquals(teclExpirations, teclResult)
    }

    @Test
    fun `on-demand fallback: cache miss triggers IBKR fetch and populates cache`() {
        // Arrange: Cache is empty
        every { opsForValue.get("expiry:SOXL") } returns null
        // IBKR returns expirations
        val expirations = listOf(LocalDate.of(2026, 7, 18))
        every { ibkrClient.requestOptionExpirations("SOXL") } returns expirations

        // Act: Simulate on-demand fetch (as done in ChainController)
        var cachedExpiry = cacheService.getExpiry("SOXL")
        if (cachedExpiry == null) {
            cachedExpiry = ibkrClient.requestOptionExpirations("SOXL")
            cacheService.cacheExpiry("SOXL", cachedExpiry)
        }

        // Assert: Cache was populated
        val cachedJson = objectMapper.writeValueAsString(expirations)
        every { opsForValue.get("expiry:SOXL") } returns cachedJson
        val result = cacheService.getExpiry("SOXL")

        assertEquals(expirations, result)
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `cd backend/market-data && ./gradlew test --tests "com.portfolio.marketdata.integration.ExpiryCacheIntegrationTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 3: Run all tests to verify no regressions**

Run: `cd backend/market-data && ./gradlew test 2>&1 | tail -30`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/market-data/src/test/kotlin/com/portfolio/marketdata/integration/ExpiryCacheIntegrationTest.kt
git commit -m "test(market-data): add integration test for expiry cache"
```

---

### Task 7: Final Verification

**Files:**
- None (verification only)

- [ ] **Step 1: Run all market-data tests**

Run: `cd backend/market-data && ./gradlew test 2>&1 | tail -50`
Expected: All tests pass

- [ ] **Step 2: Verify Redis key pattern manually**

Run: `cd backend/market-data && ./gradlew bootRun 2>&1 &`
Then: `sleep 30 && docker exec portfolio-redis redis-cli KEYS "expiry:*"`
Expected: Keys like `expiry:SOXL`, `expiry:TECL`, etc.

- [ ] **Step 3: Verify TTL**

Run: `docker exec portfolio-redis redis-cli TTL "expiry:SOXL"`
Expected: TTL around 7776000 (90 days in seconds)

- [ ] **Step 4: Verify scheduled refresh logs**

Run: `docker logs portfolio-market-data 2>&1 | grep -i "expiry refresh"`
Expected: Log entries showing scheduled refresh running

- [ ] **Step 5: Final commit with all changes**

```bash
git add -A
git commit -m "feat(market-data): implement options expiry Redis cache with scheduled refresh

- Add ExpiryProperties for configuration (90-day TTL, weekly refresh)
- Add ExpiryCacheService for Redis operations
- Add ExpiryRefreshService for scheduled refresh (Monday 8 AM)
- Update ChainController to use new cache with on-demand fallback
- Add unit and integration tests
- Configure via application.yml with env var overrides"
```

---

## Success Criteria

1. ✅ Expiry dates load consistently from Redis cache
2. ✅ 90-day TTL configured and verified
3. ✅ Scheduled refresh runs weekly (configurable via cron)
4. ✅ On-demand fallback works when cache is empty
5. ✅ All unit and integration tests pass
6. ✅ Configuration is overridable via environment variables
