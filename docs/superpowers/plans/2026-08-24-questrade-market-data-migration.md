# Questrade Market Data Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the IBKR TWS gateway with the Questrade API as the sole market data provider (options chains, streaming option/equity quotes, snapshots) and remove every IBKR trace from backend, deploy infra, and frontend.

**Architecture:** A new `provider` package holds a provider-neutral `MarketDataProvider` interface (same shapes as today's `IbkrClient`) plus relocated `SubscriptionManager`/`ContractResolver`. A new `questrade` package implements it: token manager (rotating refresh token persisted in Redis), REST client (search/chain/quotes/stream negotiation), single-socket stream client (tick synthesis + equity fallback polling), connection manager, and the `QuestradeProvider` orchestrator. Consumers keep their DTOs, `/ws/quotes` protocol, and Redis tiers unchanged. Spec: `docs/superpowers/specs/2026-08-24-questrade-market-data-migration-design.md`.

**Tech Stack:** Kotlin 2.0.21, Spring Boot 3.3.5 (servlet stack), Java 21 `java.net.http` WebSocket, Spring `RestClient`, Redis, JUnit5 + mockk + MockRestServiceServer. No new dependencies (TwsApi.jar is removed).

## Global Constraints

- Live API verified 2026-08-24 (Phase 0): chain payload key is `optionChain` with inner `optionRoot` (NOT docs' `options`/`root`); option quotes key is `optionQuotes`; option symbol format `SPY28Aug26C600.00`; stock+options negotiation return the SAME `streamPort`; only one socket allowed; access tokens can die mid-session with body `{"code":1017,"message":"Access token is invalid"}`.
- Auth: `GET {authUrl}?grant_type=refresh_token&refresh_token=X` returns `{access_token, api_server, expires_in:1800, refresh_token}`. Practice auth URL: `https://practicelogin.questrade.com/oauth2/token`. Always use returned `api_server` as REST base URL.
- Rate limits: 20 req/s market data; honor `X-RateLimit-Reset` on HTTP 429. Keepalive: at least 1 REST call per 30 min (`GET /v1/time`).
- Questrade `volatility` is a PERCENT (93.08 means 0.9308 fraction): divide by 100 when filling `MarketDataSnapshot.impliedVol`.
- Internal `conId` fields/columns KEEP their names but carry Questrade numeric `symbolId` after cutover (opaque provider id). No DB migration for this.
- FX stays on Bank of Canada Valet API - untouched.
- Frontend `/ws/quotes` actions and REST DTO shapes must not change.
- Every task ends green: `./gradlew :market-data:build` (or the module named in the task).
- Never commit real tokens. Env var `QUESTRADE_REFRESH_TOKEN` seeds the token store; rotated tokens persist in Redis key `questrade:refresh-token`.

---

### Task 1: Create `provider` package with `MarketDataProvider` interface

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/provider/MarketDataProvider.kt`
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/ibkr/TwsIbkrClient.kt` (class declaration + import only)
- Modify (imports only): `streaming/QuoteStreamingService.kt`, `streaming/OptionStreamingService.kt`, `api/controller/ChainController.kt`, `api/controller/QuoteController.kt`, `api/controller/IbkrHealthController.kt`, `distribution/ExpiryRefreshService.kt`, `ibkr/IbkrConnectionManager.kt`, `health/IbkrHealthIndicator.kt`

**Interfaces:**
- Produces: `interface com.portfolio.marketdata.provider.MarketDataProvider` plus DTOs `OptionContractDetails`, `MarketDataSnapshot` moved here (identical fields).

- [ ] **Step 1: Create the interface file**

```kotlin
package com.portfolio.marketdata.provider

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Provider-neutral market data facade. Implementation: QuestradeProvider.
 * NOTE: `conId` parameters/fields carry the provider's opaque instrument id
 * (Questrade numeric symbolId after migration). Names kept for compatibility
 * with ContractResolver serialization and the contract_cache table.
 */
interface MarketDataProvider {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    /** Invoked after the underlying transport reconnects; subscribers re-register. */
    fun registerReconnectHandler(handler: Runnable) {}
    fun requestMarketData(conId: Int, callback: (tickType: Int, value: Double) -> Unit)
    fun cancelMarketData(conId: Int)
    fun requestOptionChain(underlying: String): List<OptionContractDetails>
    fun requestContractDetails(
        symbol: String,
        secType: String,
        expiry: LocalDate? = null,
        strike: BigDecimal? = null,
        right: String? = null
    ): List<OptionContractDetails>
    fun requestMarketDataSnapshot(conId: Int): MarketDataSnapshot?
    /** Batched snapshot fetch used by chain building. Missing ids absent from map. */
    fun requestOptionSnapshots(conIds: List<Int>): Map<Int, MarketDataSnapshot>
    fun requestOptionExpirations(underlying: String): List<LocalDate>
}

data class OptionContractDetails(
    val conId: Int,
    val symbol: String,
    val secType: String,
    val exchange: String,
    val expiry: LocalDate?,
    val strike: BigDecimal?,
    val right: String?,
    val tradingClass: String? = null,
    val multiplier: String? = null
)

data class MarketDataSnapshot(
    val conId: Int,
    val bid: Double? = null,
    val ask: Double? = null,
    val last: Double? = null,
    val volume: Long? = null,
    val impliedVol: Double? = null,
    val delta: Double? = null,
    val gamma: Double? = null,
    val theta: Double? = null,
    val vega: Double? = null
)
```

- [ ] **Step 2: Retype TwsIbkrClient.** In `ibkr/TwsIbkrClient.kt`: change the class declaration from `: IbkrClient {` to `: MarketDataProvider {`; add `import com.portfolio.marketdata.provider.MarketDataProvider`; DELETE the overrides `registerDataFarmErrorHandler` and `isDataFarmHealthy` (and their private data-farm state if compile errors demand); keep everything else.
- [ ] **Step 3: Delete old declarations.** In `ibkr/IbkrClient.kt`: delete the `interface IbkrClient` block and both DTO data classes (moved). Check `grep -rn "OptionChainParams" backend/market-data/src` - if referenced outside ibkr/, move that data class into `provider/MarketDataProvider.kt`; otherwise delete the whole file.
- [ ] **Step 4: Mechanical import fix.** Run `grep -rl "marketdata.ibkr.IbkrClient\|marketdata.ibkr.OptionContractDetails\|marketdata.ibkr.MarketDataSnapshot" backend/market-data/src/main` and swap those imports to `com.portfolio.marketdata.provider.*` in every listed file.
- [ ] **Step 5:** Run: `./gradlew :market-data:compileKotlin` - Expected: BUILD SUCCESSFUL.
- [ ] **Step 6: Commit**

```bash
git add -A backend/market-data && git commit -m "refactor(market-data): extract provider-neutral MarketDataProvider interface"
```

---

### Task 2: Move `SubscriptionManager` and `ContractResolver` into `provider`

**Files:**
- Create (via git mv): `backend/market-data/src/main/kotlin/com/portfolio/marketdata/provider/SubscriptionManager.kt`
- Create (via git mv): `backend/market-data/src/main/kotlin/com/portfolio/marketdata/provider/ContractResolver.kt`
- Delete: `ibkr/SubscriptionManager.kt`, `ibkr/ContractResolver.kt` (moved)
- Move test: `src/test/kotlin/com/portfolio/marketdata/ibkr/SubscriptionManagerTest.kt` -> `provider/SubscriptionManagerTest.kt`

**Interfaces:**
- Produces: `provider.SubscriptionManager(provider: MarketDataProvider, @Value("\${provider.max-subscriptions:100}") maxSubscriptions: Int)` - public API unchanged (`subscribe/unsubscribe/pin/unpin/resubscribeAll/isSubscribed/getActiveCount/getPinnedCount/unsubscribeAll`).
- Produces: `provider.ContractResolver(provider: MarketDataProvider, contractCacheRepository, redisTemplate)` - public API unchanged (`resolve`, `resolveMany`).

- [ ] **Step 1:** `git mv` both files into `provider/`. In each: set `package com.portfolio.marketdata.provider`; rename constructor param `ibkrClient:` to `provider:` typed `MarketDataProvider`; replace body references `ibkrClient.` with `provider.`. In SubscriptionManager change `@Value("\${ibkr.max-subscriptions:100}")` to `@Value("\${provider.max-subscriptions:100}")`. In ContractResolver rename private method `getFromIbkr` to `getFromProvider` (update its call site) and log text "IBKR contract request failed" to "Provider contract request failed".
- [ ] **Step 2:** Fix all references: `grep -rln "marketdata.ibkr.SubscriptionManager\|marketdata.ibkr.ContractResolver" backend/market-data/src` then swap imports to `marketdata.provider.*` (expected files: QuoteStreamingService, OptionStreamingService, IbkrConnectionManager, IbkrHealthController, ExpiryRefreshService, tests).
- [ ] **Step 3:** In `SubscriptionManagerTest.kt`: update package/import; replace `mockk<IbkrClient>(relaxed = true)` with `mockk<MarketDataProvider>(relaxed = true)`; where the test constructs SubscriptionManager directly pass `(provider, 100)`.
- [ ] **Step 4:** Run: `./gradlew :market-data:test --tests "com.portfolio.marketdata.provider.SubscriptionManagerTest"` - Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add -A backend/market-data && git commit -m "refactor(market-data): relocate SubscriptionManager/ContractResolver to provider package"
```

---

### Task 3: Add `GreeksSource.QUESTRADE` (keep `IBKR` until Task 10)

**Files:**
- Modify: `backend/common/src/main/kotlin/com/portfolio/common/domain/Greeks.kt`

- [ ] **Step 1:** Change the enum to:

```kotlin
enum class GreeksSource {
    IBKR,          // legacy cached values; removed in Task 10
    QUESTRADE,
    BLACK_SCHOLES
}
```

- [ ] **Step 2:** Run: `./gradlew :common:build :market-data:compileKotlin` - Expected: SUCCESS (additive change).
- [ ] **Step 3:** Commit:

```bash
git add -A backend/common && git commit -m "feat(common): add QUESTRADE greeks source"
```

---

### Task 4: `QuestradeProperties` + application.yml

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/questrade/QuestradeProperties.kt`
- Modify: `backend/market-data/src/main/resources/application.yml`
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/config/AppProperties.kt`

**Interfaces:**
- Produces: `@ConfigurationProperties(prefix = "questrade") data class QuestradeProperties(refreshToken: String = "", usePractice: Boolean = false, authUrl: String = "https://login.questrade.com/oauth2/token", practiceAuthUrl: String = "https://practicelogin.questrade.com/oauth2/token", rateLimitPerSecond: Int = 18, maxSubscriptions: Int = 100, equityPollIntervalSeconds: Long = 10, keepaliveMinutes: Long = 25)`
- Registration: mirror how AppProperties is registered today (check for `@ConfigurationPropertiesScan` or `@EnableConfigurationProperties(AppProperties::class)` and add QuestradeProperties the same way).

- [ ] **Step 1: Create QuestradeProperties.kt**

```kotlin
package com.portfolio.marketdata.questrade

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "questrade")
data class QuestradeProperties(
    /** Seed refresh token; rotated tokens persist in Redis key questrade:refresh-token. */
    val refreshToken: String = "",
    val usePractice: Boolean = false,
    val authUrl: String = "https://login.questrade.com/oauth2/token",
    val practiceAuthUrl: String = "https://practicelogin.questrade.com/oauth2/token",
    /** Stay under Questrade's 20 req/s market-data bucket. */
    val rateLimitPerSecond: Int = 18,
    val maxSubscriptions: Int = 100,
    /** Poll interval for equities not covered by an active options stream session. */
    val equityPollIntervalSeconds: Long = 10,
    /** Questrade drops sessions without >=1 REST call / 30 min. */
    val keepaliveMinutes: Long = 25
)
```

- [ ] **Step 2:** In `application.yml` REPLACE the whole `ibkr:` block (`host/port/client-id`) with:

```yaml
questrade:
  refresh-token: ${QUESTRADE_REFRESH_TOKEN:}
  use-practice: ${QUESTRADE_USE_PRACTICE:false}
```

Leave the `expiry:` block untouched.
- [ ] **Step 3:** In `config/AppProperties.kt`: change `@ConfigurationProperties(prefix = "ibkr")` to `prefix = "provider"`; DELETE fields `host`, `port`, `clientId`, `maxConnections`, `reconnectDelayMs`; KEEP `maxChainExpirations`, `maxDteDefault`. Fix TwsIbkrClient compile breaks minimally (it dies in Task 9): replace removed-field reads with literals (`properties.host` -> `"127.0.0.1"`, port -> `4002`). Also update `SubscriptionManager` default if it referenced ibkr prefix (it uses `provider.max-subscriptions` since Task 2).
- [ ] **Step 4:** Run: `./gradlew :market-data:compileKotlin :market-data:test --tests "com.portfolio.marketdata.config.ExpiryPropertiesTest"` - Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add -A backend/market-data && git commit -m "feat(market-data): add questrade configuration properties"
```

---

### Task 5: `QuestradeTokenManager` (rotation persisted in Redis)

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/questrade/QuestradeTokenManager.kt`
- Test: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/questrade/QuestradeTokenManagerTest.kt`

**Interfaces:**
- Consumes: `QuestradeProperties`, `RedisTemplate<String,String>`, `RestClient.Builder`.
- Produces:

```kotlin
data class AccessToken(val token: String, val apiServer: String, val expiresAtEpochSeconds: Long)

@Component
class QuestradeTokenManager(
    properties: QuestradeProperties,
    redisTemplate: RedisTemplate<String, String>,
    restClientBuilder: RestClient.Builder = RestClient.builder()
) {
    companion object { const val REDIS_KEY = "questrade:refresh-token" }
    fun getValidAccessToken(): AccessToken   // refresh when expired (60s buffer); persists rotation
    fun forceRefresh(): AccessToken          // used on code 1017
    fun isExpired(t: AccessToken): Boolean
}
```

- [ ] **Step 1: Write failing test**

```kotlin
package com.portfolio.marketdata.questrade

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestradeTokenManagerTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var ops: ValueOperations<String, String>
    private lateinit var mgr: QuestradeTokenManager

    private val body = "{\"access_token\":\"AT1\",\"token_type\":\"Bearer\",\"expires_in\":1800," +
        "\"refresh_token\":\"RT_NEW\",\"api_server\":\"https://api01.iq.questrade.com/\"}"

    @BeforeEach
    fun setup() {
        server = MockRestServiceServer.bindTo(RestClient.builder()).build()
        @Suppress("UNCHECKED_CAST")
        ops = mock(ValueOperations::class.java) as ValueOperations<String, String>
        @Suppress("UNCHECKED_CAST")
        val redis = mock(RedisTemplate::class.java) as RedisTemplate<String, String>
        `when`(redis.opsForValue()).thenReturn(ops)
        `when`(ops.get(QuestradeTokenManager.REDIS_KEY)).thenReturn(null)
        mgr = QuestradeTokenManager(QuestradeProperties(refreshToken = "RT_SEED"), redis, RestClient.builder())
    }

    @Test
    fun `exchanges seed token and persists rotation`() {
        server.expect(requestTo("https://login.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=RT_SEED"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        val t = mgr.forceRefresh()
        assertEquals("AT1", t.token)
        assertEquals("https://api01.iq.questrade.com/", t.apiServer)
        verify(ops).set(QuestradeTokenManager.REDIS_KEY, "RT_NEW")
    }

    @Test
    fun `prefers redis token over env seed`() {
        `when`(ops.get(QuestradeTokenManager.REDIS_KEY)).thenReturn("RT_REDIS")
        server.expect(requestTo("https://login.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=RT_REDIS"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        mgr.forceRefresh()
        server.verify()
    }

    @Test
    fun `expiry respects 60s buffer`() {
        val t = AccessToken("x", "y", System.currentTimeMillis() / 1000 + 30)
        assertTrue(mgr.isExpired(t))
    }
}
```

- [ ] **Step 2:** Run: `./gradlew :market-data:test --tests "*QuestradeTokenManagerTest"` - Expected: FAIL (class missing).
- [ ] **Step 3: Implement**

```kotlin
package com.portfolio.marketdata.questrade

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class AccessToken(val token: String, val apiServer: String, val expiresAtEpochSeconds: Long)

@Component
class QuestradeTokenManager(
    private val properties: QuestradeProperties,
    private val redisTemplate: RedisTemplate<String, String>,
    restClientBuilder: RestClient.Builder = RestClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilder.build()
    @Volatile private var current: AccessToken? = null

    companion object { const val REDIS_KEY = "questrade:refresh-token" }

    fun getValidAccessToken(): AccessToken {
        current?.let { if (!isExpired(it)) return it }
        return forceRefresh()
    }

    fun forceRefresh(): AccessToken {
        val refreshToken = currentRefreshToken()
        val authUrl = if (properties.usePractice) properties.practiceAuthUrl else properties.authUrl
        val url = "$authUrl?grant_type=refresh_token&refresh_token=$refreshToken"
        val node: JsonNode = restClient.get().uri(url).retrieve().body(JsonNode::class.java)
            ?: throw IllegalStateException("Empty Questrade token response")
        val access = node.get("access_token")?.asText()
            ?: throw IllegalStateException("No access_token in Questrade response")
        val refreshed = node.get("refresh_token")?.asText()
            ?: throw IllegalStateException("No refresh_token in Questrade response")
        val apiServer = node.get("api_server")?.asText()
            ?: throw IllegalStateException("No api_server in Questrade response")
        val expiresIn = node.path("expires_in").asLong(1800L).takeIf { it > 0 } ?: 1800L
        persistRotation(refreshed)
        current = AccessToken(access, apiServer, System.currentTimeMillis() / 1000 + expiresIn)
        log.info("Questrade token refreshed (practice={}), expires_in={}s", properties.usePractice, expiresIn)
        return current!!
    }

    fun isExpired(t: AccessToken): Boolean =
        System.currentTimeMillis() / 1000 >= t.expiresAtEpochSeconds - 60

    private fun currentRefreshToken(): String =
        try { redisTemplate.opsForValue().get(REDIS_KEY) } catch (_: Exception) { null }
            ?.takeIf { it.isNotBlank() }
            ?: properties.refreshToken.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No Questrade refresh token configured")

    private fun persistRotation(newToken: String) {
        try { redisTemplate.opsForValue().set(REDIS_KEY, newToken) }
        catch (e: Exception) { log.error("Failed to persist rotated Questrade refresh token", e) }
    }
}
```

- [ ] **Step 4:** Run tests again - Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add -A backend/market-data && git commit -m "feat(market-data): Questrade token manager with Redis-persisted rotation"
```

---

### Task 6: `QuestradeRestClient` + response DTOs

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/questrade/QuestradeRestClient.kt` (DTOs in same file)
- Test: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/questrade/QuestradeRestClientTest.kt`

**Interfaces:**
- Consumes: `QuestradeTokenManager`.
- Produces:

```kotlin
data class QuestradeSymbol(val symbolId: Int, val symbol: String, val description: String,
    val securityType: String, val listingExchange: String?, val isQuotable: Boolean,
    val currency: String?, val hasOptions: Boolean)
data class QuestradeStrike(val strikePrice: Double, val callSymbolId: Int, val putSymbolId: Int)
data class QuestradeRoot(val optionRoot: String, val multiplier: Int, val chainPerStrikePrice: List<QuestradeStrike>)
data class QuestradeExpiry(val expiryDate: String, val optionExerciseType: String?,
    val listingExchange: String?, val chainPerRoot: List<QuestradeRoot>)
data class QuestradeOptionQuote(val symbolId: Int, val underlyingId: Int, val bidPrice: Double?,
    val askPrice: Double?, val lastTradePrice: Double?, val volume: Long, val openInterest: Long,
    val volatility: Double?, val delta: Double?, val gamma: Double?, val theta: Double?,
    val vega: Double?, val rho: Double?, val delay: Int)
data class QuestradeStockQuote(val symbolId: Int, val bidPrice: Double?, val askPrice: Double?,
    val lastTradePrice: Double?, val volume: Long, val delay: Int)

@Component
class QuestradeRestClient(private val tokenManager: QuestradeTokenManager,
    builder: RestClient.Builder = RestClient.builder()) {
    fun searchSymbol(prefix: String): List<QuestradeSymbol>        // GET /v1/symbols/search?prefix=
    fun getOptionChain(underlyingId: Int): List<QuestradeExpiry>   // GET /v1/symbols/{id}/options -> optionChain
    fun getOptionQuotes(optionIds: List<Int>): List<QuestradeOptionQuote> // POST /v1/markets/quotes/options, chunks of <=100
    fun getStockQuotes(symbolIds: List<Int>): List<QuestradeStockQuote>   // GET /v1/markets/quotes?ids=csv
    fun negotiateStockStream(symbolIds: List<Int>): Int            // GET stream=true&mode=WebSocket -> streamPort
    fun negotiateOptionStream(optionIds: List<Int>): Int           // POST {"stream":true,"mode":"WebSocket","optionIds":[...]}
    fun serverTime(): java.time.Instant                            // GET /v1/time (keepalive)
}
```

All calls go through a private helper that reads `tokenManager.getValidAccessToken()`, builds URIs from `apiServer.trimEnd('/')`, retries ONCE after `tokenManager.forceRefresh()` when the body contains `"code":1017`, and on HTTP 429 sleeps per `X-RateLimit-Reset` (capped 60s) then retries once.

- [ ] **Step 1: Write failing test**

```kotlin
package com.portfolio.marketdata.questrade

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestradeRestClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: QuestradeRestClient

    private val tokenBody = "{\"access_token\":\"AT\",\"expires_in\":1800,\"refresh_token\":\"R2\",\"api_server\":\"https://api01.iq.questrade.com/\"}"
    private val searchBody = "{\"symbols\":[{\"symbolId\":34987,\"symbol\":\"SPY\",\"description\":\"SPDR S&P 500 ETF\",\"securityType\":\"Stock\",\"listingExchange\":\"ARCA\",\"isQuotable\":true,\"currency\":\"USD\",\"hasOptions\":true}]}"
    private val chainBody = "{\"optionChain\":[{\"expiryDate\":\"2026-08-28T00:00:00.000000-04:00\",\"description\":\"SPY\",\"listingExchange\":\"OPRA\",\"optionExerciseType\":\"American\",\"chainPerRoot\":[{\"optionRoot\":\"SPY\",\"multiplier\":100,\"chainPerStrikePrice\":[{\"strikePrice\":600,\"callSymbolId\":76915281,\"putSymbolId\":76915626}]}]}]}"
    private val optQuotesBody = "{\"optionQuotes\":[{\"underlying\":\"SPY\",\"underlyingId\":34987,\"symbol\":\"SPY28Aug26C600.00\",\"symbolId\":76915281,\"bidPrice\":162.65,\"askPrice\":165.37,\"lastTradePrice\":166.75,\"volume\":0,\"openInterest\":26,\"volatility\":93.08136,\"delta\":0.988226,\"gamma\":0.000366,\"theta\":-0.252878,\"vega\":0.027186,\"rho\":0.080913,\"delay\":0}]}"
    private val stockQuotesBody = "{\"quotes\":[{\"symbol\":\"SPY\",\"symbolId\":34987,\"tier\":\"\",\"bidPrice\":763.56,\"bidSize\":81,\"askPrice\":763.62,\"askSize\":772,\"lastTradePrice\":763.59,\"volume\":34215,\"delay\":0}]}"

    @BeforeEach
    fun setup() {
        server = MockRestServiceServer.bindTo(RestClient.builder()).build()
        @Suppress("UNCHECKED_CAST")
        val ops = mock(ValueOperations::class.java) as ValueOperations<String, String>
        @Suppress("UNCHECKED_CAST")
        val redis = mock(RedisTemplate::class.java) as RedisTemplate<String, String>
        `when`(redis.opsForValue()).thenReturn(ops)
        `when`(ops.get(QuestradeTokenManager.REDIS_KEY)).thenReturn("RT")
        val tokenMgr = QuestradeTokenManager(QuestradeProperties(refreshToken = "SEED"), redis, RestClient.builder())
        client = QuestradeRestClient(tokenMgr, RestClient.builder())
    }

    private fun expectAuth() {
        server.expect(requestTo("https://login.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=RT"))
            .andRespond(withSuccess(tokenBody, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `search parses symbol`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/symbols/search?prefix=SPY"))
            .andRespond(withSuccess(searchBody, MediaType.APPLICATION_JSON))
        val r = client.searchSymbol("SPY")
        assertEquals(34987, r.first().symbolId)
        assertTrue(r.first().hasOptions)
    }

    @Test
    fun `chain parses optionChain key`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/symbols/34987/options"))
            .andRespond(withSuccess(chainBody, MediaType.APPLICATION_JSON))
        val chain = client.getOptionChain(34987)
        assertEquals(1, chain.size)
        assertEquals("SPY", chain[0].chainPerRoot[0].optionRoot)
        assertEquals(76915281, chain[0].chainPerRoot[0].chainPerStrikePrice[0].callSymbolId)
    }

    @Test
    fun `option quotes parse greeks`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/markets/quotes/options"))
            .andRespond(withSuccess(optQuotesBody, MediaType.APPLICATION_JSON))
        val q = client.getOptionQuotes(listOf(76915281)).first()
        assertEquals(0.988226, q.delta!!, 1e-9)
        assertEquals(26L, q.openInterest)
    }

    @Test
    fun `stock quotes parse`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/markets/quotes?ids=34987"))
            .andRespond(withSuccess(stockQuotesBody, MediaType.APPLICATION_JSON))
        val q = client.getStockQuotes(listOf(34987)).first()
        assertEquals(763.56, q.bidPrice!!, 1e-9)
    }

    @Test
    fun `invalid token retries once after re-exchange`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/markets/quotes?ids=34987"))
            .andRespond(withSuccess("{\"code\":1017,\"message\":\"Access token is invalid\"}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://login.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=R2"))
            .andRespond(withSuccess(tokenBody, MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api01.iq.questrade.com/v1/markets/quotes?ids=34987"))
            .andRespond(withSuccess(stockQuotesBody, MediaType.APPLICATION_JSON))
        val q = client.getStockQuotes(listOf(34987)).first()
        assertEquals(34987, q.symbolId)
        server.verify()
    }
}
```

- [ ] **Step 2:** Run: `./gradlew :market-data:test --tests "*QuestradeRestClientTest"` - Expected: FAIL.
- [ ] **Step 3: Implement.** Parse with Jackson `ObjectMapper.readTree` walking envelopes into the DTOs (`symbols[]`, `optionChain[].chainPerRoot[].chainPerStrikePrice[]`, `optionQuotes[]`, `quotes[]`, `streamPort`). Key helper:

```kotlin
private inline fun <T> withApi(crossinline block: (String) -> T): T {
    val t = tokenManager.getValidAccessToken()
    return try {
        block(t.apiServer.trimEnd('/'))
    } catch (e: RestCallFailedWith1017) {
        val fresh = tokenManager.forceRefresh()
        block(fresh.apiServer.trimEnd('/'))
    }
}

class RestCallFailedWith1017 : RuntimeException()

// inside call sites: read body as String; if it contains "\"code\":1017" throw RestCallFailedWith1017();
// else objectMapper.readValue / readTree
```

Chunk `getOptionQuotes` at 100 ids per POST and concatenate. `negotiate*` return `streamPort` int from `{"streamPort":18101}`.
- [ ] **Step 4:** Run - Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add -A backend/market-data && git commit -m "feat(market-data): Questrade REST client with 1017 retry and chunked batch quotes"
```

---

### Task 7: `QuestradeStreamClient` — single socket, tick synthesis, fallback poller

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/questrade/QuestradeStreamClient.kt`
- Test: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/questrade/StreamFrameParserTest.kt`

**Interfaces:**
- Consumes: `QuestradeRestClient`, `QuestradeTokenManager`, `QuestradeProperties`.
- Produces:

```kotlin
@Component
class QuestradeStreamClient(
    private val restClient: QuestradeRestClient,
    private val tokenManager: QuestradeTokenManager,
    private val properties: QuestradeProperties
) {
    fun subscribe(conId: Int, callback: (tickType: Int, value: Double) -> Unit) // registers; debounced reconcile
    fun unsubscribe(conId: Int)
    fun isConnected(): Boolean
    fun setReconnectHandler(handler: Runnable)
    fun shutdown()
}

// Top-level pure functions (unit tested):
fun parseQuoteFrame(json: String): Pair<List<Pair<Int, QuestradeStockQuote>>, List<Pair<Int, QuestradeOptionQuote>>>
fun synthesizeTicks(q: QuestradeStockQuote): List<Pair<Int, Double>>   // [(1,bid),(2,ask),(4,last),(8,volume)] skipping nulls
fun synthesizeTicks(q: QuestradeOptionQuote): List<Pair<Int, Double>>
```

Behavior spec:
- Desired sets: `equityCallbacks: MutableMap<Int, (Int,Double)->Unit>`, `optionCallbacks: MutableMap<Int, (Int,Double)->Unit>`.
- `reconcile()` runs debounced (750ms, single-thread scheduler): mode = OPTIONS if any option callbacks else EQUITY; negotiate via `restClient.negotiateOptionStream(optionIds)` or `negotiateStockStream(equityIds)`; connect `java.net.http.HttpClient.newWebSocketBuilder().buildAsync(URI("wss://" + apiServerHost + ":" + port), listener)`; on open send raw access token text; on text frame accumulate partial fragments until complete, then `parseQuoteFrame` and dispatch each quote's `synthesizeTicks` to its registered callback (skip unregistered ids).
- Abnormal close/error: exponential backoff reconnect 5s->60s cap; on successful reconnect invoke reconnect handler(s).
- While in OPTIONS mode, equity ids not covered by incoming frames are polled every `equityPollIntervalSeconds` via `restClient.getStockQuotes(uncoveredIds)` and dispatched identically (this keeps multi-ticker watchlists live under the one-socket constraint).
- Scheduler also calls `restClient.serverTime()` every `keepaliveMinutes`.

- [ ] **Step 1: Write failing parser test**

```kotlin
package com.portfolio.marketdata.questrade

import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamFrameParserTest {
    @Test
    fun `parses stock quote frame and synthesizes ticks`() {
        val frame = "{\"quotes\":[{\"symbol\":\"SPY\",\"symbolId\":34987,\"bidPrice\":763.52,\"askPrice\":763.6,\"lastTradePrice\":763.51,\"volume\":34389}]}"
        val (stocks, options) = parseQuoteFrame(frame)
        assertEquals(1, stocks.size); assertTrue(options.isEmpty())
        assertEquals(listOf(1 to 763.52, 2 to 763.6, 4 to 763.51, 8 to 34389.0), synthesizeTicks(stocks[0].second))
    }

    @Test
    fun `parses option quote frame`() {
        val frame = "{\"optionQuotes\":[{\"underlying\":\"SPY\",\"underlyingId\":34987,\"symbolId\":76915281,\"bidPrice\":162.65,\"askPrice\":165.37,\"lastTradePrice\":166.75,\"volume\":0}]}"
        val (stocks, options) = parseQuoteFrame(frame)
        assertTrue(stocks.isEmpty()); assertEquals(76915281, options[0].first)
        assertEquals(listOf(1 to 162.65, 2 to 165.37, 4 to 166.75), synthesizeTicks(options[0].second))
    }

    @Test
    fun `ignores success frame`() {
        val (stocks, options) = parseQuoteFrame("{\"success\":true}")
        assertTrue(stocks.isEmpty() && options.isEmpty())
    }
}
```

- [ ] **Step 2:** Run: `./gradlew :market-data:test --tests "*StreamFrameParserTest"` - Expected: FAIL.
- [ ] **Step 3: Implement** the two pure functions plus `QuestradeStreamClient` per the behavior spec (Jackson ObjectMapper; WebSocket `Listener.onText` receives `PartialText` fragments - concatenate until `last == true` before parsing).
- [ ] **Step 4:** Run - Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add -A backend/market-data && git commit -m "feat(market-data): Questrade single-socket stream client with tick synthesis"
```

---

### Task 8: `QuestradeProvider` implementing `MarketDataProvider`

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/questrade/QuestradeProvider.kt`
- Test: `backend/market-data/src/test/kotlin/com/portfolio/marketdata/questrade/QuestradeProviderTest.kt`

**Interfaces:**
- Consumes: `QuestradeRestClient`, `QuestradeStreamClient`, `QuestradeTokenManager`.
- Produces: `@Component class QuestradeProvider(...) : MarketDataProvider` (becomes the only implementation after Task 9 deletes TwsIbkrClient).

Mapping rules (all verified against Phase 0 payloads):
- `connect()` = `tokenManager.getValidAccessToken()` warm-up; `isConnected()` = `stream.isConnected()`; `disconnect()` = `stream.shutdown()`.
- `registerReconnectHandler(h)` = `stream.setReconnectHandler(h)`.
- `requestMarketData/cancelMarketData` = `stream.subscribe/unsubscribe`.
- `requestContractDetails(symbol,"STK")`: `restClient.searchSymbol(symbol)` then exact match `it.symbol.equals(symbol, true)` preferring `isQuotable`; map to `OptionContractDetails(conId=symbolId, symbol, "STK", listingExchange ?: "", null, null, null, null, null)`. Underlying id resolution cached in `ConcurrentHashMap<String, Int>`.
- `requestOptionChain(underlying)`: resolve underlying id, `getOptionChain(id)`, flatten ALL expiries x roots x strikes into CALL+PUT contracts:
  `OptionContractDetails(conId = s.callSymbolId or s.putSymbolId, symbol = root.optionRoot, secType="OPT", exchange = expiry.listingExchange ?: "OPRA", expiry = LocalDate.parse(expiry.expiryDate.substring(0,10)), strike = BigDecimal.valueOf(s.strikePrice), right = "C"/"P", tradingClass = root.optionRoot, multiplier = root.multiplier.toString())`
- `requestContractDetails(...,"OPT", expiry?, strike?, right?)`: same flatten then filter by provided params.
- `requestOptionExpirations(underlying)`: distinct sorted `LocalDate.parse(expiryDate.substring(0,10))`.
- `requestMarketDataSnapshot(conId)`: try `getOptionQuotes([conId])` first, fall back to `getStockQuotes([conId])`; map to `MarketDataSnapshot(bid, ask, last, volume, impliedVol = volatility?.div(100.0), delta, gamma, theta, vega)`.
- `requestOptionSnapshots(ids)`: chunked `getOptionQuotes` mapped to `Map<Int, MarketDataSnapshot>` keyed by symbolId.

- [ ] **Step 1: Write failing test** using `mockk<QuestradeRestClient>` and `mockk<QuestradeStreamClient>` asserting: STK exact-match mapping fields; OPT filtering by expiry/right/strike; snapshot IV percent-to-fraction conversion (`93.08136` -> `0.09308136` within 1e-9); `requestOptionSnapshots` returns map keyed by conId; market-data methods delegate to stream client.
- [ ] **Step 2:** Run: `./gradlew :market-data:test --tests "*QuestradeProviderTest"` - Expected: FAIL.
- [ ] **Step 3: Implement** per mapping rules.
- [ ] **Step 4:** Run - Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add -A backend/market-data && git commit -m "feat(market-data): QuestradeProvider implementing MarketDataProvider"
```

---

### Task 9: Consumer cutover — wire Questrade everywhere, delete IBKR client

**Files:**
- Modify: `streaming/QuoteStreamingService.kt`, `streaming/OptionStreamingService.kt`, `api/controller/ChainController.kt`, `api/controller/QuoteController.kt`, `distribution/ExpiryRefreshService.kt`, `api/controller/IbkrHealthController.kt`, `health/IbkrHealthIndicator.kt`, `ibkr/IbkrConnectionManager.kt`
- Delete: remaining `ibkr/` package files, `backend/market-data/libs/TwsApi.jar`, test `ibkr/TwsIbkrClientTest.kt`
- Rename: `IbkrHealthController.kt` -> `ProviderHealthController.kt`; `health/IbkrHealthIndicator.kt` -> `health/QuestradeHealthIndicator.kt`; its test -> `QuestradeHealthIndicatorTest.kt`; `ibkr/IbkrConnectionManager.kt` -> `questrade/QuestradeConnectionManager.kt`
- Test updates: `ChainControllerTest.kt`, `ChainControllerExpiryTest.kt`, `QuoteControllerTest.kt`, `ExpiryRefreshServiceTest.kt`

**Key changes:**

1. Replace injected `IbkrClient` with `MarketDataProvider` everywhere (imports already point at `provider.*` from Tasks 1-2). Rename constructor params/fields `ibkrClient` -> `provider` in each consumer.
2. `QuestradeConnectionManager` (moved from IbkrConnectionManager): delete the `registerDataFarmErrorHandler` block in `run()`; thread name `"qt-conn-mgr"`; log prefix "QuestradeConnectionManager". Keep connectWithRetry backoff (5s->60s), 30s health poll on `provider.isConnected()`, and `broadcastConnectionStatus` calls.
3. `ChainController.fetchSnapshots` — REPLACE the per-contract executor loop with one batched call:

```kotlin
private fun fetchSnapshots(contracts: List<com.portfolio.marketdata.provider.OptionContractDetails>): Map<Int, com.portfolio.marketdata.provider.MarketDataSnapshot> {
    if (contracts.isEmpty()) return emptyMap()
    val startTime = System.currentTimeMillis()
    val results = try {
        provider.requestOptionSnapshots(contracts.map { it.conId })
    } catch (e: Exception) {
        log.debug("Batch snapshot fetch failed: {}", e.message)
        emptyMap()
    }
    log.info("Fetched {}/{} snapshots in {}ms (batch)", results.size, contracts.size, System.currentTimeMillis() - startTime)
    return results
}
```

   Then DELETE `snapshotExecutor`, `snapshotThreadCounter`, `snapshotTimeoutSeconds`, `effectiveSnapshotTimeoutSeconds`, and their shutdown line in `destroy()`. Replace both `GreeksSource.IBKR` occurrences (`buildOptionQuote`, `computeGreeks`) with `GreeksSource.QUESTRADE`. Update log strings "IBKR ..." to "provider ...".
4. `ProviderHealthController`: keep class mapping `@RequestMapping("/api/v1/health")`; endpoint becomes `@GetMapping("/provider")` plus a temporary alias `@GetMapping("/ibkr")` calling the same handler (removed in Task 13 after frontend update). Response keys: `connected`, `provider`, `connectionState`, `activeSubscriptions`, `pinnedSubscriptions` (drop `dataFarmHealthy`). Inject `MarketDataProvider`, `SubscriptionManager`, `QuestradeConnectionManager`.
5. `QuestradeHealthIndicator`: `health(): Health` up/down on `provider.isConnected()`; update its test accordingly.
6. `QuoteController`: type swap only; log text "IBKR not connected" -> "Provider not connected".
7. `ExpiryRefreshService`: type swap only.
8. Delete leftover `ibkr/` files + `libs/TwsApi.jar` + these lines in `build.gradle.kts`:

```kotlin
// IBKR TWS API (vendored from interactivebrokers.github.io)
implementation(files("libs/TwsApi.jar"))
implementation("com.google.protobuf:protobuf-java:4.29.2")
```

9. Update the four test classes: swap `mockk<IbkrClient>` -> `mockk<MarketDataProvider>`; in ChainController tests replace per-conId snapshot stubs with `every { provider.requestOptionSnapshots(any()) } returns emptyMap()` (or fixture maps).

- [ ] **Step 1:** Apply changes 1-8.
- [ ] **Step 2:** Run: `./gradlew :market-data:compileKotlin` - Expected: SUCCESS.
- [ ] **Step 3:** Apply change 9; run: `./gradlew :market-data:test` - Expected: ALL PASS.
- [ ] **Step 4: Commit**

```bash
git add -A backend/market-data && git commit -m "feat(market-data)!: cut over to QuestradeProvider, remove IBKR TWS client"
```

---

### Task 10: Remove `GreeksSource.IBKR` + finalize config naming

**Files:**
- Modify: `backend/common/src/main/kotlin/com/portfolio/common/domain/Greeks.kt`
- Modify: `backend/market-data/src/main/resources/application-prod.yml`

- [ ] **Step 1:** Run `grep -rn "GreeksSource.IBKR\|marketdata.ibkr\|\"ibkr\"" backend/market-data/src/main backend/common/src/main --include='*.kt' --include='*.yml'`. Fix every hit: delete the `IBKR` enum value from `GreeksSource` (leave `QUESTRADE`, `BLACK_SCHOLES`); prod yml logger key `com.portfolio.marketdata.ibkr: INFO` -> `com.portfolio.marketdata: INFO`.
- [ ] **Step 2:** Run: `./gradlew :common:build :market-data:build` - Expected: SUCCESS. (Stale Redis chain caches carrying `"source":"IBKR"` would fail deserialization; they are flushed at cutover per Task 14 runbook.)
- [ ] **Step 3: Commit**

```bash
git add -A backend && git commit -m "chore(market-data): drop IBKR greeks source and legacy config naming"
```

---

### Task 11: broker-gateway IBKR removal

**Files:**
- Delete: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/` (6 files: IbkrConfig, IbkrAccountClient, TwsIbkrAccountClient, IbkrAdapter, IbkrConnectionManager, IbkrDtoMappers), tests `adapter/ibkr/IbkrAdapterTest.kt`, `adapter/ibkr/IbkrDtoMappersTest.kt`
- Create: `backend/broker-gateway/src/main/resources/db/migration/V3__remove_ibkr_connections.sql`
- Modify: `adapter/BrokerType.kt` (remove `IBKR`), `adapter/BrokerCredentials.kt` (remove `IbkrCredentials` data class), `config/AdapterRegistry.kt` (drop IbkrAdapter registration/param), `src/main/resources/application.yml` (delete `broker-gateway.ibkr:` block incl. flex-token/flex-query-id)
- Test updates: `AdapterRegistryTest.kt`, `CredentialServiceTest.kt`, `api/controller/HealthControllerTest.kt` (remove IBKR fakes/cases)

**Schema facts (verified):** table `broker_gateway.connections`, column `broker_type VARCHAR(20) NOT NULL`, CHECK constraint `chk_broker_type IN ('IBKR','QUESTRADE','WEALTHSIMPLE')`. Next migration number is V3.

- [ ] **Step 1: Write migration**

```sql
-- Remove IBKR broker connections before dropping the enum value from app code.
DELETE FROM broker_gateway.connections WHERE broker_type = 'IBKR';
ALTER TABLE broker_gateway.connections DROP CONSTRAINT chk_broker_type;
ALTER TABLE broker_gateway.connections ADD CONSTRAINT chk_broker_type
  CHECK (broker_type IN ('QUESTRADE', 'WEALTHSIMPLE'));
```

- [ ] **Step 2:** Delete the six adapter files and two test files; remove `IBKR` from `BrokerType` enum and `IbkrCredentials` from `BrokerCredentials`; shrink `AdapterRegistry` (constructor loses the IbkrAdapter param; registration list loses its entry); delete the `broker-gateway.ibkr:` config block from application.yml.
- [ ] **Step 3:** Fix the three test classes by deleting IBKR cases/fakes.
- [ ] **Step 4:** Run: `./gradlew :broker-gateway:build` - Expected: SUCCESS.
- [ ] **Step 5: Commit**

```bash
git add -A backend/broker-gateway && git commit -m "feat(broker-gateway)!: remove IBKR adapter and connections"
```

---

### Task 12: Deploy infrastructure cleanup

**Files:**
- Delete: `deploy/shared/docker-compose.yml`, `scripts/restart-ibkr.sh`
- Modify: root `docker-compose.yml`, `deploy/prod/docker-compose.yml`, `deploy/uat/docker-compose.yml`, `deploy/prod/.env.example`, `deploy/uat/.env.example`, `deploy/monitoring/prometheus/prometheus.yml`

- [ ] **Step 1:** Root compose: delete the `ib-gateway` service block (~lines 34-56). In `market-data-service`: remove `depends_on: ib-gateway`; replace env vars `IBKR_HOST` / `IBKR_PORT` / `IBKR_CLIENT_ID` with:

```yaml
      QUESTRADE_REFRESH_TOKEN: ${QUESTRADE_REFRESH_TOKEN:-}
      QUESTRADE_USE_PRACTICE: ${QUESTRADE_USE_PRACTICE:-false}
```

In `broker-gateway-service`: remove `IBKR_GATEWAY_ENABLED`, `IBKR_HOST`, `IBKR_PORT`, `IBKR_GATEWAY_CLIENT_ID` and its ib-gateway depends_on entry.
- [ ] **Step 2:** `deploy/prod/docker-compose.yml`: same env swaps on `market-data-service` (lines ~150-152) and `broker-gateway-service` (lines ~230-233); remove `shared-network` from both services' `networks:` lists and delete the external network declaration at the bottom. Identical treatment in `deploy/uat/docker-compose.yml` (its refs are `ib-gateway-paper`, client ids 10/11).
- [ ] **Step 3:** `.env.example` (prod + uat): remove `IBKR_USERNAME`, `IBKR_PASSWORD`, `IBKR_VNC_PASSWORD`, `IBKR_CLIENT_ID`, `IBKR_GATEWAY_CLIENT_ID`; add:

```bash
QUESTRADE_REFRESH_TOKEN=
# uat only:
QUESTRADE_USE_PRACTICE=true
```

- [ ] **Step 4:** `prometheus.yml`: delete scrape jobs targeting ib-gateway containers (keep app service jobs).
- [ ] **Step 5:** Run:

```bash
git rm deploy/shared/docker-compose.yml scripts/restart-ibkr.sh
docker compose -f deploy/prod/docker-compose.yml config -q && docker compose -f deploy/uat/docker-compose.yml config -q && docker compose config -q
```

Expected: no errors.
- [ ] **Step 6: Commit**

```bash
git add -A deploy scripts docker-compose.yml && git commit -m "chore(deploy): remove ib-gateway infrastructure, switch to Questrade env vars"
```

---

### Task 13: Frontend IBKR trace cleanup (wording/state only — protocol unchanged)

**Files:**
- Modify: `frontend/src/stores/quoteStore.ts`, `frontend/src/hooks/useMarketDataWebSocket.ts`, `frontend/src/components/wheel/WheelChainPanel.tsx`, `frontend/src/pages/OptionsPage.tsx`, `frontend/src/components/wheel/OrderPanel.tsx`
- Rename: `frontend/src/components/IbkrConnectionBadge.tsx` -> `ConnectionBadge.tsx` (+ update importers)

- [ ] **Step 1:** Rename store field `ibkrConnected` -> `providerConnected` in quoteStore and every reader (WheelChainPanel lines ~38, 58, 185-188, 409-412; run `grep -rn "ibkrConnected" frontend/src` for stragglers). The WS `connection_status` frame payload is unchanged.
- [ ] **Step 2:** Copy fixes:
  - `'IBKR Gateway may be unavailable...'` -> `'Market data provider may be unavailable. Please check the connection and try again.'` (OptionsPage :74,:96; WheelChainPanel :169,:206,:230)
  - `'IBKR Gateway is not responding. Options data is currently unavailable. Please try again later.'` -> `'Market data is not responding. Options data is currently unavailable. Please try again later.'` (OptionsPage :75)
  - `'IBKR Gateway is disconnected. Data may be stale or unavailable.'` -> `'Market data is disconnected. Data may be stale or unavailable.'` (WheelChainPanel :412)
  - Comment `// Track IBKR connection status via WebSocket` -> `// Track market data connection status via WebSocket`
- [ ] **Step 3:** OrderPanel (:34, :190): drop `|| lower.includes('ibkr') || lower.includes('interactive')` tokens from broker-matching conditionals.
- [ ] **Step 4:** `git mv` badge component to `ConnectionBadge.tsx`, rename component + imports. Verify: `grep -rni "ibkr" frontend/src --include='*.tsx' --include='*.ts' | grep -v test` - Expected: zero hits.
- [ ] **Step 5:** Run: `cd frontend && npm run build` (or repo-standard build command) - Expected: SUCCESS.
- [ ] **Step 6: Commit**

```bash
git add -A frontend && git commit -m "chore(frontend): rename IBKR connection traces to provider-neutral wording"
```

---

### Task 14: Repo artifacts, docs, cutover runbook

**Files:**
- Delete: root-level dev screenshots referencing the IBKR era (`ibkr-*.png`, `wheel-*.png`, `soxl-csp-*.png`, `tecl-csp-*.png`, `tqqq-chain-*.png`, `account-detail-screenshot.png`, `dashboard-screenshot.png`, `ref-*.png`, `chain-response.json`)
- Modify: `README.md` (any IBKR gateway mentions -> Questrade)
- Create: `docs/runbooks/questrade-cutover.md`

- [ ] **Step 1:** List then delete artifacts (verify against git history preservation):

```bash
ls *.png chain-response.json
git rm ibkr-badge-check.png ibkr-dashboard.png ibkr-options-page.png ibkr-spy-atm.png ibkr-spy-chain.png ibkr-spy-chain-v2.png ibkr-spy-chain-v3.png ibkr-wheel-page.png chain-response.json account-detail-screenshot.png dashboard-screenshot.png ref-desktop-account.png ref-desktop-portfolio.png ref-mobile-portfolio.png soxl-csp-final.png soxl-csp-hd-final.png soxl-csp-loaded.png soxl-csp-ui-fixes.png soxl-csp-ui-v2.png soxl-panel-atm.png soxl-panel-zoom.png tecl-csp-final.png tecl-csp-hd.png tecl-csp-live.png tecl-csp-per-expiry.png tecl-csp-streaming2.png tecl-csp-streaming-fixed.png tecl-csp-streaming.png tecl-csp-with-prices.png tecl-final-result.png tqqq-chain-final.png tqqq-chain-fixed.png tqqq-chain-v2.png tqqq-chain-options-chain.png wheel-cc-final.png wheel-cc-loaded.png wheel-cc-test.png wheel-chain-panel.png wheel-csp-after-wait.png wheel-csp-click.png wheel-csp-fixed.png wheel-csp-hd.png wheel-issues-1.png wheel-page.png wheel-redesign-desktop.png wheel-redesign-mobile.png wheel-redesign-tfsa.png
```

(If any filename differs, adjust from the `ls` output.)
- [ ] **Step 2:** Update README market-data section: replace IBKR gateway description with "Questrade API (refresh-token auth; real-time US/OPRA package required)".
- [ ] **Step 3: Create cutover runbook** `docs/runbooks/questrade-cutover.md` with exactly this content:

```markdown
# Questrade Cutover Runbook

## Prerequisites
- Valid Questrade API refresh token (web API Centre). Rotate before prod if it transited chat.
- UAT uses practice token (QUESTRADE_USE_PRACTICE=true); prod uses live token.
- Real-time US/OPRA market data package active ($9.95/mo) - otherwise US options data is delayed.

## Deploy steps (per environment)
1. Set QUESTRADE_REFRESH_TOKEN (+ QUESTRADE_USE_PRACTICE for uat) in .env; deploy new images.
2. Flush stale provider-id caches BEFORE first request:
   redis-cli --scan --pattern 'contract:*' | xargs -r redis-cli del
   redis-cli --scan --pattern 'quote:*' | xargs -r redis-cli del
   redis-cli --scan --pattern 'chain:*' | xargs -r redis-cli del
   redis-cli --scan --pattern 'expiry:*' | xargs -r redis-cli del
   redis-cli --scan --pattern 'expirations:*' | xargs -r redis-cli del
3. Verify: GET /api/v1/chains/SPY/expirations returns expiries; open Wheel page,
   load a chain, confirm streaming ticks arrive; GET /api/v1/health/provider shows connected=true.

## Rollback
Revert to previous image tag; IBKR infra was deleted, so rollback assumes re-provisioning
the shared ib-gateway stack from git history (tag the pre-migration commit before deploying).
```

- [ ] **Step 4:** Tag pre-migration state for rollback: `git tag pre-questrade-migration`
- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "docs: questrade cutover runbook, remove IBKR-era artifacts"
```

---

### Task 15: Final verification sweep

- [ ] **Step 1:** Full backend build + tests: `./gradlew build` - Expected: ALL modules PASS.
- [ ] **Step 2:** Trace sweep: `grep -rni "ibkr\|tws\|ib-gateway" backend/ deploy/ scripts/ docker-compose.yml .env.example 2>/dev/null | grep -v "\.git/" | grep -vi "history"` - Expected: zero hits (or only intentional historical doc references listed in the review notes).
- [ ] **Step 3:** Live smoke against local backend (real token in env):

```bash
QUESTRADE_REFRESH_TOKEN=<token> ./gradlew :market-data:bootRun &
sleep 30
curl -s localhost:8082/api/v1/chains/SPY/expirations | head -c 400
curl -s localhost:8082/api/v1/quotes/SPY | head -c 300
curl -s localhost:8082/api/v1/health/provider
```

Expected: non-empty expiries list; SPY quote with delay=0; health connected=true.
- [ ] **Step 4:** Frontend manual check (docker compose up or dev server): Options page loads SPY chain; Wheel page streams ATM option quotes; connection badge reflects live status.
- [ ] **Step 5:** Commit any straggler fixes: `git add -A && git commit -m "fix: final questrade migration sweep"`

---

## Plan Self-Review Notes

- Spec coverage: Phase 0 done pre-plan; provider swap (Tasks 1-9), greeks source (3,10), config (4,10), token rotation persistence (5), REST+stream+batch snapshots (6,7,8), consumers incl. ChainController batch path (9), broker-gateway full removal incl. DB CHECK constraint migration (11), infra/env/prometheus/scripts (12), frontend wording/state (13), artifacts/docs/runbook/rollback tag (14), verification (15). FX untouched per spec.
- Type consistency: interface name `MarketDataProvider` used uniformly; DTO field `conId` retained deliberately (documented in Task 1); `requestOptionSnapshots(List<Int>): Map<Int, MarketDataSnapshot>` defined in Task 1, implemented in Task 8, consumed in Task 9.
- Deliberate deviation from spec wording: SubscriptionManager/ContractResolver relocate to neutral `provider` package (not `questrade`) since they are provider-agnostic; OCC string parsing intentionally omitted because chain structure provides numeric symbolIds directly (frames matched by id).

