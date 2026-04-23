# Wealthsimple Adapter — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Wealthsimple adapter for the broker-gateway service — a `FakeWealthsimpleAdapter` for dev/test and a real `WealthsimpleAdapter` that calls the unofficial Wealthsimple GraphQL API for account data and order execution.

**Architecture:** The adapter implements the `BrokerAdapter` interface. Wealthsimple uses an unofficial GraphQL API at `my.wealthsimple.com/graphql` with OAuth 2.0 password-grant authentication and optional 2FA. The `WealthsimpleGraphQlClient` sends raw POST requests with operation names and variables. The `WealthsimpleTokenManager` handles password-grant OAuth and token refresh. A `WealthsimpleRateLimiter` enforces the 7 trades/hour hard limit. The `FakeWealthsimpleAdapter` provides realistic Canadian mock data for dev/test.

**Tech Stack:** Spring Boot 3.3.5, Kotlin 2.0.21, Spring WebFlux `WebClient`, Jackson for JSON/GraphQL parsing.

**Spec:** `docs/superpowers/specs/2026-04-23-broker-gateway-design.md` — Wealthsimple section

---

## File Structure

### New files to create

```
backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/
  WealthsimpleConfig.kt             — @ConfigurationProperties
  WealthsimpleDtoMappers.kt         — Wealthsimple types → UnifiedDTOs
  WealthsimpleGraphQlClient.kt      — Raw HTTP POST GraphQL client
  WealthsimpleTokenManager.kt       — Password-grant OAuth + 2FA + refresh
  WealthsimpleRateLimiter.kt        — 7 trades/hour sliding window enforcer
  WealthsimpleAdapter.kt            — Real adapter orchestrating client + token + rate limiter
  FakeWealthsimpleAdapter.kt        — Dev/test mock

backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/
  WealthsimpleDtoMappersTest.kt
  WealthsimpleRateLimiterTest.kt
  FakeWealthsimpleAdapterTest.kt
  WealthsimpleAdapterTest.kt
```

### Files to modify

```
backend/broker-gateway/src/main/resources/application.yml  — Add wealthsimple config
docker-compose.yml                                          — Add WEALTHSIMPLE env vars
```

---

## Task 1: Configuration

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleConfig.kt`
- Modify: `backend/broker-gateway/src/main/resources/application.yml`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Create WealthsimpleConfig**

```kotlin
// adapter/wealthsimple/WealthsimpleConfig.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway.wealthsimple")
data class WealthsimpleConfig(
    val enabled: Boolean = false,
    val authUrl: String = "https://api.production.wealthsimple.com/v1/oauth/v2/token",
    val graphqlUrl: String = "https://my.wealthsimple.com/graphql",
    val clientId: String = "4da53ac2b03225bed1550eba8e4611e086c7b905a3855571f1c77e1bbdc5f62b",
    val orderRateLimitPerHour: Int = 7
)
```

- [ ] **Step 2: Add wealthsimple config to application.yml**

Append after the `broker-gateway.questrade` block:

```yaml
  wealthsimple:
    enabled: ${WEALTHSIMPLE_ENABLED:false}
    auth-url: ${WEALTHSIMPLE_AUTH_URL:https://api.production.wealthsimple.com/v1/oauth/v2/token}
    graphql-url: ${WEALTHSIMPLE_GRAPHQL_URL:https://my.wealthsimple.com/graphql}
    client-id: ${WEALTHSIMPLE_CLIENT_ID:4da53ac2b03225bed1550eba8e4611e086c7b905a3855571f1c77e1bbdc5f62b}
    order-rate-limit-per-hour: ${WS_ORDER_RATE_LIMIT:7}
```

- [ ] **Step 3: Add Wealthsimple env var to docker-compose.yml**

In the `broker-gateway-service` environment block, add:

```yaml
      WEALTHSIMPLE_ENABLED: ${WEALTHSIMPLE_ENABLED:-false}
```

- [ ] **Step 4: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleConfig.kt backend/broker-gateway/src/main/resources/application.yml docker-compose.yml
git commit -m "feat(broker-gateway): add Wealthsimple configuration"
```

---

## Task 2: DTO Mappers

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleDtoMappers.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleDtoMappersTest.kt`

- [ ] **Step 1: Create WealthsimpleDtoMappers**

```kotlin
// adapter/wealthsimple/WealthsimpleDtoMappers.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*

object WealthsimpleDtoMappers {

    fun mapAccountType(raw: String?): AccountType = when (raw?.lowercase()) {
        "ca_non_registered" -> AccountType.CASH
        "ca_non_registered_margin" -> AccountType.MARGIN
        "ca_tfsa" -> AccountType.TFSA
        "ca_rrsp" -> AccountType.RRSP
        "ca_fhsa" -> AccountType.FHSA
        "ca_lira" -> AccountType.LIRA
        "ca_crypto" -> AccountType.CRYPTO
        else -> AccountType.OTHER
    }

    fun mapInstrumentType(securityType: String?): InstrumentType = when (securityType?.lowercase()) {
        "equity" -> InstrumentType.STOCK
        "etf" -> InstrumentType.ETF
        "mutual_fund" -> InstrumentType.MUTUAL_FUND
        "crypto" -> InstrumentType.CRYPTO
        else -> InstrumentType.OTHER
    }

    fun mapOrderStatus(status: String?): OrderStatus = when (status?.lowercase()) {
        "submitted" -> OrderStatus.PENDING
        "posted" -> OrderStatus.SUBMITTED
        "filled" -> OrderStatus.FILLED
        "partial_fill" -> OrderStatus.PARTIALLY_FILLED
        "cancelled" -> OrderStatus.CANCELLED
        "rejected" -> OrderStatus.REJECTED
        "failed" -> OrderStatus.FAILED
        else -> OrderStatus.PENDING
    }

    fun mapActivityType(type: String?): ActivityType = when (type?.lowercase()) {
        "buy" -> ActivityType.BUY
        "sell" -> ActivityType.SELL
        "dividend" -> ActivityType.DIVIDEND
        "deposit", "institutional_transfer" -> ActivityType.TRANSFER_IN
        "withdrawal" -> ActivityType.TRANSFER_OUT
        "fee" -> ActivityType.FEE
        "interest" -> ActivityType.INTEREST
        "stock_split" -> ActivityType.STOCK_SPLIT
        "reorganization" -> ActivityType.CORPORATE_ACTION
        "refund" -> ActivityType.OTHER
        else -> ActivityType.OTHER
    }
}
```

- [ ] **Step 2: Create WealthsimpleDtoMappersTest**

```kotlin
// test: adapter/wealthsimple/WealthsimpleDtoMappersTest.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WealthsimpleDtoMappersTest {

    @Test
    fun `mapAccountType normalizes Wealthsimple account types`() {
        assertEquals(AccountType.CASH, WealthsimpleDtoMappers.mapAccountType("ca_non_registered"))
        assertEquals(AccountType.MARGIN, WealthsimpleDtoMappers.mapAccountType("ca_non_registered_margin"))
        assertEquals(AccountType.TFSA, WealthsimpleDtoMappers.mapAccountType("ca_tfsa"))
        assertEquals(AccountType.RRSP, WealthsimpleDtoMappers.mapAccountType("ca_rrsp"))
        assertEquals(AccountType.FHSA, WealthsimpleDtoMappers.mapAccountType("ca_fhsa"))
        assertEquals(AccountType.LIRA, WealthsimpleDtoMappers.mapAccountType("ca_lira"))
        assertEquals(AccountType.CRYPTO, WealthsimpleDtoMappers.mapAccountType("ca_crypto"))
        assertEquals(AccountType.OTHER, WealthsimpleDtoMappers.mapAccountType("unknown"))
    }

    @Test
    fun `mapInstrumentType normalizes Wealthsimple security types`() {
        assertEquals(InstrumentType.STOCK, WealthsimpleDtoMappers.mapInstrumentType("equity"))
        assertEquals(InstrumentType.ETF, WealthsimpleDtoMappers.mapInstrumentType("etf"))
        assertEquals(InstrumentType.MUTUAL_FUND, WealthsimpleDtoMappers.mapInstrumentType("mutual_fund"))
        assertEquals(InstrumentType.CRYPTO, WealthsimpleDtoMappers.mapInstrumentType("crypto"))
        assertEquals(InstrumentType.OTHER, WealthsimpleDtoMappers.mapInstrumentType("bond"))
    }

    @Test
    fun `mapOrderStatus normalizes Wealthsimple order statuses`() {
        assertEquals(OrderStatus.PENDING, WealthsimpleDtoMappers.mapOrderStatus("submitted"))
        assertEquals(OrderStatus.SUBMITTED, WealthsimpleDtoMappers.mapOrderStatus("posted"))
        assertEquals(OrderStatus.FILLED, WealthsimpleDtoMappers.mapOrderStatus("filled"))
        assertEquals(OrderStatus.PARTIALLY_FILLED, WealthsimpleDtoMappers.mapOrderStatus("partial_fill"))
        assertEquals(OrderStatus.CANCELLED, WealthsimpleDtoMappers.mapOrderStatus("cancelled"))
        assertEquals(OrderStatus.REJECTED, WealthsimpleDtoMappers.mapOrderStatus("rejected"))
        assertEquals(OrderStatus.FAILED, WealthsimpleDtoMappers.mapOrderStatus("failed"))
    }

    @Test
    fun `mapActivityType normalizes Wealthsimple activity types`() {
        assertEquals(ActivityType.BUY, WealthsimpleDtoMappers.mapActivityType("buy"))
        assertEquals(ActivityType.SELL, WealthsimpleDtoMappers.mapActivityType("sell"))
        assertEquals(ActivityType.DIVIDEND, WealthsimpleDtoMappers.mapActivityType("dividend"))
        assertEquals(ActivityType.TRANSFER_IN, WealthsimpleDtoMappers.mapActivityType("deposit"))
        assertEquals(ActivityType.TRANSFER_IN, WealthsimpleDtoMappers.mapActivityType("institutional_transfer"))
        assertEquals(ActivityType.TRANSFER_OUT, WealthsimpleDtoMappers.mapActivityType("withdrawal"))
        assertEquals(ActivityType.FEE, WealthsimpleDtoMappers.mapActivityType("fee"))
        assertEquals(ActivityType.INTEREST, WealthsimpleDtoMappers.mapActivityType("interest"))
        assertEquals(ActivityType.STOCK_SPLIT, WealthsimpleDtoMappers.mapActivityType("stock_split"))
        assertEquals(ActivityType.CORPORATE_ACTION, WealthsimpleDtoMappers.mapActivityType("reorganization"))
        assertEquals(ActivityType.OTHER, WealthsimpleDtoMappers.mapActivityType("unknown"))
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleDtoMappers.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleDtoMappersTest.kt
git commit -m "feat(broker-gateway): add Wealthsimple DTO mappers with normalization"
```

---

## Task 3: GraphQL Client, Token Manager, and Rate Limiter

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleGraphQlClient.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleTokenManager.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleRateLimiter.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleRateLimiterTest.kt`

- [ ] **Step 1: Create WealthsimpleGraphQlClient**

```kotlin
// adapter/wealthsimple/WealthsimpleGraphQlClient.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import com.portfolio.brokergateway.exception.BrokerConnectionException
import com.portfolio.brokergateway.exception.BrokerDataException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class WealthsimpleGraphQlClient(
    private val config: WealthsimpleConfig,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val webClientBuilder: WebClient.Builder = WebClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(accessToken: String, operationName: String, query: String, variables: Map<String, Any?> = emptyMap()): JsonNode {
        val body = mapOf(
            "operationName" to operationName,
            "query" to query,
            "variables" to variables
        )

        return try {
            val client = webClientBuilder
                .baseUrl(config.graphqlUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-ws-api-version", "12")
                .defaultHeader("x-platform-os", "web")
                .defaultHeader("x-ws-locale", "en-CA")
                .defaultHeader("x-ws-profile", "trade")
                .build()

            val response = client.post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerDataException("Empty GraphQL response for $operationName", BrokerType.WEALTHSIMPLE)

            val errors = response.get("errors")
            if (errors != null && errors.size() > 0) {
                val msg = errors.firstOrNull()?.get("message")?.asText() ?: "Unknown GraphQL error"
                log.error("Wealthsimple GraphQL error on {}: {}", operationName, msg)
                throw BrokerDataException("GraphQL error on $operationName: $msg", BrokerType.WEALTHSIMPLE)
            }

            response.get("data") ?: throw BrokerDataException("No data in GraphQL response for $operationName", BrokerType.WEALTHSIMPLE)
        } catch (e: WebClientResponseException) {
            handleError(e, operationName)
        } catch (e: BrokerDataException) {
            throw e
        } catch (e: BrokerAuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw BrokerConnectionException("Failed to reach Wealthsimple API: ${e.message}", BrokerType.WEALTHSIMPLE, e)
        }
    }

    private fun handleError(e: WebClientResponseException, operationName: String): Nothing {
        log.error("Wealthsimple HTTP error {} on {}: {}", e.statusCode, operationName, e.responseBodyAsString)
        when {
            e.statusCode == HttpStatusCode.valueOf(401) -> {
                val needsOtp = e.headers.getFirst("x-wealthsimple-otp-required") != null
                throw BrokerAuthenticationException(
                    "Wealthsimple auth failed on $operationName" + if (needsOtp) " (2FA required)" else "",
                    BrokerType.WEALTHSIMPLE, needsReauth = true)
            }
            else -> throw BrokerDataException("Wealthsimple error ${e.statusCode} on $operationName", BrokerType.WEALTHSIMPLE, e)
        }
    }
}
```

- [ ] **Step 2: Create WealthsimpleTokenManager**

```kotlin
// adapter/wealthsimple/WealthsimpleTokenManager.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class WealthsimpleTokenManager(
    private val config: WealthsimpleConfig,
    private val webClientBuilder: WebClient.Builder = WebClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun refreshTokens(credentials: BrokerCredentials.WealthsimpleCredentials): BrokerCredentials.WealthsimpleCredentials {
        val body = mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to credentials.refreshToken,
            "client_id" to config.clientId
        )

        log.info("Refreshing Wealthsimple tokens")

        val response: JsonNode = try {
            webClientBuilder.build()
                .post().uri(config.authUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerAuthenticationException(
                    "Empty response from Wealthsimple token refresh", BrokerType.WEALTHSIMPLE)
        } catch (e: BrokerAuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw BrokerAuthenticationException(
                "Wealthsimple token refresh failed: ${e.message}", BrokerType.WEALTHSIMPLE, cause = e)
        }

        val accessToken = response.get("access_token")?.asText()
            ?: throw BrokerAuthenticationException("No access_token in Wealthsimple response", BrokerType.WEALTHSIMPLE)
        val newRefreshToken = response.get("refresh_token")?.asText()
            ?: throw BrokerAuthenticationException("No refresh_token in Wealthsimple response", BrokerType.WEALTHSIMPLE)
        val expiresIn = response.get("expires_in")?.asLong() ?: 3600L

        log.info("Wealthsimple tokens refreshed")

        return credentials.copy(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn
        )
    }

    fun isTokenExpired(credentials: BrokerCredentials.WealthsimpleCredentials): Boolean {
        val nowEpoch = System.currentTimeMillis() / 1000
        return nowEpoch >= credentials.expiresAtEpochSeconds - 60
    }
}
```

- [ ] **Step 3: Create WealthsimpleRateLimiter**

```kotlin
// adapter/wealthsimple/WealthsimpleRateLimiter.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerRateLimitException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedDeque

class WealthsimpleRateLimiter(
    private val maxOrdersPerHour: Int = 7
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val orderTimestamps = ConcurrentLinkedDeque<Long>()

    fun checkOrderAllowed() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600_000L

        while (orderTimestamps.peekFirst()?.let { it < oneHourAgo } == true) {
            orderTimestamps.pollFirst()
        }

        if (orderTimestamps.size >= maxOrdersPerHour) {
            val oldestInWindow = orderTimestamps.peekFirst() ?: now
            val retryAfterSeconds = ((oldestInWindow + 3600_000L - now) / 1000).toInt().coerceAtLeast(1)
            log.warn("Wealthsimple order rate limit reached: {}/{} in last hour", orderTimestamps.size, maxOrdersPerHour)
            throw BrokerRateLimitException(
                "Wealthsimple order rate limit: $maxOrdersPerHour orders per hour exceeded",
                BrokerType.WEALTHSIMPLE, retryAfterSeconds = retryAfterSeconds)
        }
    }

    fun recordOrder() {
        orderTimestamps.addLast(System.currentTimeMillis())
    }

    fun getOrdersInLastHour(): Int {
        val oneHourAgo = System.currentTimeMillis() - 3600_000L
        while (orderTimestamps.peekFirst()?.let { it < oneHourAgo } == true) {
            orderTimestamps.pollFirst()
        }
        return orderTimestamps.size
    }
}
```

- [ ] **Step 4: Create WealthsimpleRateLimiterTest**

```kotlin
// test: adapter/wealthsimple/WealthsimpleRateLimiterTest.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.exception.BrokerRateLimitException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WealthsimpleRateLimiterTest {

    @Test
    fun `allows orders under the limit`() {
        val limiter = WealthsimpleRateLimiter(maxOrdersPerHour = 3)
        assertDoesNotThrow { limiter.checkOrderAllowed() }
        limiter.recordOrder()
        assertDoesNotThrow { limiter.checkOrderAllowed() }
        limiter.recordOrder()
        assertDoesNotThrow { limiter.checkOrderAllowed() }
        limiter.recordOrder()
    }

    @Test
    fun `throws when limit is reached`() {
        val limiter = WealthsimpleRateLimiter(maxOrdersPerHour = 2)
        limiter.recordOrder()
        limiter.recordOrder()
        val ex = assertThrows<BrokerRateLimitException> { limiter.checkOrderAllowed() }
        assertTrue(ex.retryAfterSeconds != null && ex.retryAfterSeconds!! > 0)
    }

    @Test
    fun `getOrdersInLastHour tracks count`() {
        val limiter = WealthsimpleRateLimiter(maxOrdersPerHour = 7)
        assertEquals(0, limiter.getOrdersInLastHour())
        limiter.recordOrder()
        limiter.recordOrder()
        assertEquals(2, limiter.getOrdersInLastHour())
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleGraphQlClient.kt backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleTokenManager.kt backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleRateLimiter.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleRateLimiterTest.kt
git commit -m "feat(broker-gateway): add Wealthsimple GraphQL client, token manager, and rate limiter"
```

---

## Task 4: FakeWealthsimpleAdapter

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/FakeWealthsimpleAdapter.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/FakeWealthsimpleAdapterTest.kt`

- [ ] **Step 1: Create FakeWealthsimpleAdapter**

```kotlin
// adapter/wealthsimple/FakeWealthsimpleAdapter.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger

@Component
@Profile("dev", "local", "test")
class FakeWealthsimpleAdapter : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val orderIdCounter = AtomicInteger(30000)

    override val brokerType = BrokerType.WEALTHSIMPLE

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        log.info("FakeWealthsimpleAdapter: validating connection")
        return ConnectionValidationResult(connected = true, message = "Fake Wealthsimple connection OK")
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials {
        val ws = credentials as BrokerCredentials.WealthsimpleCredentials
        return ws.copy(
            accessToken = "fake-ws-refreshed-token",
            refreshToken = "fake-ws-new-refresh-token",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 3600
        )
    }

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        return listOf(
            UnifiedAccount(accountId = "non-registered-abcdef", accountNumber = "WS-NR-001",
                accountName = "Personal", accountType = AccountType.CASH,
                currency = "CAD", brokerType = BrokerType.WEALTHSIMPLE, status = "ACTIVE"),
            UnifiedAccount(accountId = "tfsa-ghijkl", accountNumber = "WS-TFSA-001",
                accountName = "TFSA", accountType = AccountType.TFSA,
                currency = "CAD", brokerType = BrokerType.WEALTHSIMPLE, status = "ACTIVE"),
            UnifiedAccount(accountId = "crypto-mnopqr", accountNumber = "WS-CRYPTO-001",
                accountName = "Crypto", accountType = AccountType.CRYPTO,
                currency = "CAD", brokerType = BrokerType.WEALTHSIMPLE, status = "ACTIVE")
        )
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        return UnifiedBalance(
            accountId = accountId,
            totalEquity = BigDecimal("42000.00"),
            totalValue = BigDecimal("38500.00"),
            cashBalances = listOf(
                CashBalance(currency = "CAD", amount = BigDecimal("3500.00"))
            ),
            buyingPower = BigDecimal("3500.00"),
            currency = "CAD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        data class MockPos(val sym: String, val name: String, val qty: Int, val cost: Double, val price: Double, val type: InstrumentType)
        val positions = listOf(
            MockPos("XEQT.TO", "iShares Core Equity ETF", 300, 25.50, 27.80, InstrumentType.ETF),
            MockPos("SHOP.TO", "Shopify Inc", 15, 85.00, 102.50, InstrumentType.STOCK),
            MockPos("BN.TO", "Brookfield Corp", 50, 55.00, 62.30, InstrumentType.STOCK),
            MockPos("VEQT.TO", "Vanguard All-Equity ETF", 200, 36.00, 39.50, InstrumentType.ETF)
        )
        return positions.map { p ->
            val qty = BigDecimal(p.qty)
            val cost = BigDecimal(p.cost.toString())
            val price = BigDecimal(p.price.toString())
            val value = qty.multiply(price).setScale(2, RoundingMode.HALF_UP)
            val totalCost = qty.multiply(cost).setScale(2, RoundingMode.HALF_UP)
            val pnl = value.subtract(totalCost)
            val pnlPct = if (totalCost > BigDecimal.ZERO) pnl.multiply(BigDecimal(100)).divide(totalCost, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
            UnifiedPosition(
                symbol = p.sym, symbolId = null, securityName = p.name, instrumentType = p.type,
                quantity = qty, averageCost = cost, currentPrice = price,
                currentValue = value, totalPnl = pnl, totalPnlPercent = pnlPct, currency = "CAD"
            )
        }
    }

    override fun getActivities(
        credentials: BrokerCredentials, accountId: String,
        startDate: LocalDate?, endDate: LocalDate?
    ): List<UnifiedActivity> {
        val now = LocalDate.now()
        return listOf(
            UnifiedActivity(externalId = "ws-001", type = ActivityType.BUY, symbol = "XEQT.TO",
                description = "Buy 300 XEQT.TO", quantity = BigDecimal(300), price = BigDecimal("25.50"),
                amount = BigDecimal("-7650.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(30), settlementDate = now.minusDays(28), optionType = null),
            UnifiedActivity(externalId = "ws-002", type = ActivityType.DIVIDEND, symbol = "VEQT.TO",
                description = "Dividend VEQT.TO", quantity = null, price = null,
                amount = BigDecimal("32.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(14), settlementDate = null, optionType = null),
            UnifiedActivity(externalId = "ws-003", type = ActivityType.TRANSFER_IN, symbol = null,
                description = "EFT Deposit", quantity = null, price = null,
                amount = BigDecimal("2000.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(7), settlementDate = null, optionType = null)
        )
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val now = OffsetDateTime.now()
        return listOf(
            UnifiedOrder(brokerOrderId = "ws-ord-001", symbol = "XEQT.TO", action = OrderAction.BUY,
                orderType = OrderType.MARKET, timeInForce = TimeInForce.DAY,
                totalQuantity = BigDecimal(300), filledQuantity = BigDecimal(300),
                executionPrice = BigDecimal("25.50"), limitPrice = null,
                stopPrice = null, status = OrderStatus.FILLED, currency = "CAD",
                submittedAt = now.minusDays(30), filledAt = now.minusDays(30)),
            UnifiedOrder(brokerOrderId = "ws-ord-002", symbol = "BAM.TO", action = OrderAction.BUY,
                orderType = OrderType.LIMIT, timeInForce = TimeInForce.GTC,
                totalQuantity = BigDecimal(50), filledQuantity = null,
                executionPrice = null, limitPrice = BigDecimal("45.00"),
                stopPrice = null, status = OrderStatus.SUBMITTED, currency = "CAD",
                submittedAt = now.minusDays(1), filledAt = null)
        )
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        val orderId = orderIdCounter.incrementAndGet()
        log.info("FakeWealthsimpleAdapter: placed {} {} order for {} {} @ {}",
            request.orderType, request.action, request.quantity, request.symbol, request.limitPrice ?: "MKT")
        return OrderResult(brokerOrderId = orderId.toString(), status = OrderStatus.SUBMITTED, message = "Fake order submitted")
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        log.info("FakeWealthsimpleAdapter: cancelled order {}", brokerOrderId)
        return CancelResult(success = true, message = "Fake order cancelled")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.WEALTHSIMPLE, supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT),
            supportsOptionPositions = false, supportsFractionalShares = false,
            supportsRealTimeData = false, supportsHistoricalActivities = true,
            activityHistoryDepth = "Full history via activity feed", orderRateLimit = "7 trades/hour",
            isOfficialApi = false, notes = "FakeWealthsimpleAdapter — dev/test mock. Unofficial API, may break."
        )
    }
}
```

- [ ] **Step 2: Create FakeWealthsimpleAdapterTest**

```kotlin
// test: adapter/wealthsimple/FakeWealthsimpleAdapterTest.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeWealthsimpleAdapterTest {

    private val adapter = FakeWealthsimpleAdapter()

    @Test
    fun `brokerType is WEALTHSIMPLE`() {
        assertEquals(BrokerType.WEALTHSIMPLE, adapter.brokerType)
    }

    @Test
    fun `validateConnection returns connected`() {
        val creds = BrokerCredentials.WealthsimpleCredentials(
            accessToken = "t", refreshToken = "r", expiresAtEpochSeconds = 999999999)
        assertTrue(adapter.validateConnection(creds).connected)
    }

    @Test
    fun `listAccounts includes crypto account`() {
        val creds = BrokerCredentials.WealthsimpleCredentials(
            accessToken = "t", refreshToken = "r", expiresAtEpochSeconds = 999999999)
        val accounts = adapter.listAccounts(creds)
        assertEquals(3, accounts.size)
        assertTrue(accounts.any { it.accountType == AccountType.CRYPTO })
        assertTrue(accounts.any { it.accountType == AccountType.TFSA })
        assertTrue(accounts.all { it.brokerType == BrokerType.WEALTHSIMPLE })
    }

    @Test
    fun `getPositions returns CAD positions`() {
        val creds = BrokerCredentials.WealthsimpleCredentials(
            accessToken = "t", refreshToken = "r", expiresAtEpochSeconds = 999999999)
        val positions = adapter.getPositions(creds, "tfsa-ghijkl")
        assertTrue(positions.isNotEmpty())
        assertTrue(positions.all { it.currency == "CAD" })
        assertTrue(positions.any { it.symbol.endsWith(".TO") })
    }

    @Test
    fun `placeOrder returns submitted result`() {
        val creds = BrokerCredentials.WealthsimpleCredentials(
            accessToken = "t", refreshToken = "r", expiresAtEpochSeconds = 999999999)
        val request = OrderRequest(symbol = "XEQT.TO", action = OrderAction.BUY,
            quantity = BigDecimal(50), orderType = OrderType.MARKET)
        val result = adapter.placeOrder(creds, "tfsa-ghijkl", request)
        assertNotNull(result.brokerOrderId)
        assertEquals(OrderStatus.SUBMITTED, result.status)
    }

    @Test
    fun `capabilities reflects Wealthsimple limitations`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.WEALTHSIMPLE, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertFalse(caps.isOfficialApi)
        assertFalse(caps.supportsOptionPositions)
        assertFalse(caps.supportsFractionalShares)
        assertEquals("7 trades/hour", caps.orderRateLimit)
        assertEquals(2, caps.supportedOrderTypes.size)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/FakeWealthsimpleAdapter.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/FakeWealthsimpleAdapterTest.kt
git commit -m "feat(broker-gateway): add FakeWealthsimpleAdapter for dev/test with Canadian mock data"
```

---

## Task 5: Real WealthsimpleAdapter

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleAdapter.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleAdapterTest.kt`

- [ ] **Step 1: Create WealthsimpleAdapter**

```kotlin
// adapter/wealthsimple/WealthsimpleAdapter.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
@ConditionalOnProperty(prefix = "broker-gateway.wealthsimple", name = ["enabled"], havingValue = "true")
class WealthsimpleAdapter(
    private val config: WealthsimpleConfig
) : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val graphQlClient = WealthsimpleGraphQlClient(config)
    private val tokenManager = WealthsimpleTokenManager(config)
    private val rateLimiter = WealthsimpleRateLimiter(config.orderRateLimitPerHour)

    override val brokerType = BrokerType.WEALTHSIMPLE

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        return try {
            val creds = credentials as BrokerCredentials.WealthsimpleCredentials
            graphQlClient.execute(creds.accessToken, "FetchAllAccounts",
                "query FetchAllAccounts { identity { accounts { edges { node { id unifiedAccountType } } } } }")
            ConnectionValidationResult(connected = true, message = "Connected to Wealthsimple")
        } catch (e: Exception) {
            ConnectionValidationResult(connected = false, message = e.message, needsReauth = true)
        }
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        return tokenManager.refreshTokens(creds)
    }

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchAllAccounts { identity { accounts { edges { node { id unifiedAccountNumber unifiedAccountType nickname status currency } } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchAllAccounts", query)
        val edges = data.at("/identity/accounts/edges") ?: return emptyList()
        return edges.mapNotNull { edge ->
            val node = edge.get("node") ?: return@mapNotNull null
            UnifiedAccount(
                accountId = node.get("id")?.asText() ?: return@mapNotNull null,
                accountNumber = node.get("unifiedAccountNumber")?.asText(),
                accountName = node.get("nickname")?.asText(),
                accountType = WealthsimpleDtoMappers.mapAccountType(node.get("unifiedAccountType")?.asText()),
                currency = node.get("currency")?.asText(),
                brokerType = BrokerType.WEALTHSIMPLE,
                status = node.get("status")?.asText()
            )
        }
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchAccountFinancials(${'$'}accountId: String!) { account(id: ${'$'}accountId) { financials { currentBalance { amount currency } netLiquidationValue { amount currency } availableToTrade { amount currency } buyingPower { amount currency } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchAccountFinancials", query, mapOf("accountId" to accountId))
        val financials = data.at("/account/financials")

        return UnifiedBalance(
            accountId = accountId,
            totalEquity = financials?.at("/netLiquidationValue/amount")?.decimalValue(),
            totalValue = financials?.at("/currentBalance/amount")?.decimalValue(),
            cashBalances = listOfNotNull(
                financials?.at("/availableToTrade/amount")?.decimalValue()?.let {
                    CashBalance(financials.at("/availableToTrade/currency")?.asText() ?: "CAD", it)
                }
            ),
            buyingPower = financials?.at("/buyingPower/amount")?.decimalValue(),
            currency = financials?.at("/currentBalance/currency")?.asText() ?: "CAD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchIdentityPositions(${'$'}accountId: String!) { identity { positions(accountId: ${'$'}accountId) { edges { node { id quantity book_value { amount currency } market_value { amount currency } stock { symbol name security_type } quote { amount } } } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchIdentityPositions", query, mapOf("accountId" to accountId))
        val edges = data.at("/identity/positions/edges") ?: return emptyList()

        return edges.mapNotNull { edge ->
            val node = edge.get("node") ?: return@mapNotNull null
            val quantity = node.get("quantity")?.decimalValue() ?: return@mapNotNull null
            val bookValue = node.at("/book_value/amount")?.decimalValue()
            val marketValue = node.at("/market_value/amount")?.decimalValue()
            val avgCost = if (bookValue != null && quantity > BigDecimal.ZERO) bookValue.divide(quantity, 6, java.math.RoundingMode.HALF_UP) else null
            val pnl = if (marketValue != null && bookValue != null) marketValue.subtract(bookValue) else null

            UnifiedPosition(
                symbol = node.at("/stock/symbol")?.asText() ?: return@mapNotNull null,
                symbolId = node.get("id")?.asText(),
                securityName = node.at("/stock/name")?.asText(),
                instrumentType = WealthsimpleDtoMappers.mapInstrumentType(node.at("/stock/security_type")?.asText()),
                quantity = quantity,
                averageCost = avgCost,
                currentPrice = node.at("/quote/amount")?.decimalValue(),
                currentValue = marketValue,
                totalPnl = pnl,
                totalPnlPercent = null,
                currency = node.at("/market_value/currency")?.asText() ?: "CAD"
            )
        }
    }

    override fun getActivities(
        credentials: BrokerCredentials, accountId: String,
        startDate: LocalDate?, endDate: LocalDate?
    ): List<UnifiedActivity> {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchActivityFeedItems(${'$'}accountIds: [String!], ${'$'}limit: Int) { activities(accountIds: ${'$'}accountIds, first: ${'$'}limit) { edges { node { canonicalId type occurredAt amount { amount currency } securitySymbol description quantity price { amount } fee { amount } } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchActivityFeedItems", query,
            mapOf("accountIds" to listOf(accountId), "limit" to 99))
        val edges = data.at("/activities/edges") ?: return emptyList()

        return edges.mapNotNull { edge ->
            val node = edge.get("node") ?: return@mapNotNull null
            UnifiedActivity(
                externalId = node.get("canonicalId")?.asText(),
                type = WealthsimpleDtoMappers.mapActivityType(node.get("type")?.asText()),
                symbol = node.get("securitySymbol")?.asText(),
                description = node.get("description")?.asText(),
                quantity = node.get("quantity")?.decimalValue(),
                price = node.at("/price/amount")?.decimalValue(),
                amount = node.at("/amount/amount")?.decimalValue() ?: BigDecimal.ZERO,
                fee = node.at("/fee/amount")?.decimalValue(),
                currency = node.at("/amount/currency")?.asText() ?: "CAD",
                tradeDate = node.get("occurredAt")?.asText()?.let { LocalDate.parse(it.substring(0, 10)) } ?: LocalDate.now(),
                settlementDate = null,
                optionType = null
            )
        }
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchActivityFeedItems(${'$'}accountIds: [String!], ${'$'}types: [String!]) { activities(accountIds: ${'$'}accountIds, types: ${'$'}types) { edges { node { canonicalId securitySymbol type status quantity createdAt updatedAt amount { amount } limitPrice { amount } fillQuantity fillPrice { amount } } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchActivityFeedItems", query,
            mapOf("accountIds" to listOf(accountId), "types" to listOf("buy", "sell")))
        val edges = data.at("/activities/edges") ?: return emptyList()

        return edges.mapNotNull { edge ->
            val node = edge.get("node") ?: return@mapNotNull null
            val limitPrice = node.at("/limitPrice/amount")?.decimalValue()
            UnifiedOrder(
                brokerOrderId = node.get("canonicalId")?.asText() ?: return@mapNotNull null,
                symbol = node.get("securitySymbol")?.asText() ?: return@mapNotNull null,
                action = if (node.get("type")?.asText() == "buy") OrderAction.BUY else OrderAction.SELL,
                orderType = if (limitPrice != null) OrderType.LIMIT else OrderType.MARKET,
                timeInForce = TimeInForce.DAY,
                totalQuantity = node.get("quantity")?.decimalValue() ?: BigDecimal.ZERO,
                filledQuantity = node.get("fillQuantity")?.decimalValue(),
                executionPrice = node.at("/fillPrice/amount")?.decimalValue(),
                limitPrice = limitPrice,
                stopPrice = null,
                status = WealthsimpleDtoMappers.mapOrderStatus(node.get("status")?.asText()),
                currency = node.at("/amount/currency")?.asText(),
                submittedAt = node.get("createdAt")?.asText()?.let { parseDateTime(it) },
                filledAt = node.get("updatedAt")?.asText()?.let { parseDateTime(it) }
            )
        }
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        rateLimiter.checkOrderAllowed()
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val mutation = """mutation SoOrdersOrderCreate(${'$'}input: OrderInput!) { createOrder(input: ${'$'}input) { order { orderId status } } }"""
        val input = mutableMapOf<String, Any?>(
            "accountId" to accountId,
            "securityId" to request.symbol,
            "quantity" to request.quantity,
            "orderType" to if (request.action == OrderAction.BUY) "buy_quantity" else "sell_quantity",
            "timeInForce" to if (request.timeInForce == TimeInForce.GTC) "until_cancel" else "day"
        )
        if (request.limitPrice != null) {
            input["limitPrice"] = mapOf("amount" to request.limitPrice, "currency" to (request.currency ?: "CAD"))
        }
        val data = graphQlClient.execute(creds.accessToken, "SoOrdersOrderCreate", mutation, mapOf("input" to input))
        val order = data.at("/createOrder/order")
        rateLimiter.recordOrder()
        return OrderResult(
            brokerOrderId = order?.get("orderId")?.asText(),
            status = WealthsimpleDtoMappers.mapOrderStatus(order?.get("status")?.asText())
        )
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val mutation = """mutation SoOrdersOrderCancel(${'$'}orderId: String!) { cancelOrder(orderId: ${'$'}orderId) { order { orderId status } } }"""
        graphQlClient.execute(creds.accessToken, "SoOrdersOrderCancel", mutation, mapOf("orderId" to brokerOrderId))
        return CancelResult(success = true, message = "Cancel request sent")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.WEALTHSIMPLE, supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT),
            supportsOptionPositions = false, supportsFractionalShares = false,
            supportsRealTimeData = false, supportsHistoricalActivities = true,
            activityHistoryDepth = "Full history via activity feed", orderRateLimit = "7 trades/hour",
            isOfficialApi = false, notes = "Unofficial API — may break without notice. TOS concerns."
        )
    }

    private fun parseDateTime(iso: String): OffsetDateTime? {
        return try { OffsetDateTime.parse(iso) } catch (_: Exception) { null }
    }
}
```

- [ ] **Step 2: Create WealthsimpleAdapterTest**

```kotlin
// test: adapter/wealthsimple/WealthsimpleAdapterTest.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.adapter.OrderType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WealthsimpleAdapterTest {

    private val config = WealthsimpleConfig(enabled = true)
    private val adapter = WealthsimpleAdapter(config)

    @Test
    fun `brokerType is WEALTHSIMPLE`() {
        assertEquals(BrokerType.WEALTHSIMPLE, adapter.brokerType)
    }

    @Test
    fun `capabilities reflects Wealthsimple limitations`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.WEALTHSIMPLE, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertFalse(caps.isOfficialApi)
        assertFalse(caps.supportsOptionPositions)
        assertFalse(caps.supportsFractionalShares)
        assertFalse(caps.supportsRealTimeData)
        assertEquals("7 trades/hour", caps.orderRateLimit)
        assertEquals(listOf(OrderType.MARKET, OrderType.LIMIT), caps.supportedOrderTypes)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleAdapter.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleAdapterTest.kt
git commit -m "feat(broker-gateway): add real WealthsimpleAdapter with GraphQL client integration"
```

---

## Task 6: Build, Test, and Verify

- [ ] **Step 1: Rebuild Docker image**

Run: `docker compose build broker-gateway-service`

- [ ] **Step 2: Restart service**

Run: `docker compose up -d broker-gateway-service`

- [ ] **Step 3: Verify health shows all three brokers enabled**

Run: `curl http://localhost:8084/api/v1/gateway/health`
Expected: IBKR, QUESTRADE, and WEALTHSIMPLE all show `"enabled": true`

- [ ] **Step 4: Run all tests**

Run: `docker run --rm -v "C:\Users\SaurabhBilakhia\Documents\POC\pc\backend:/work" -w //work/broker-gateway gradle:8.10-jdk21-alpine gradle test --no-daemon`
Expected: All tests pass

- [ ] **Step 5: Commit any fixes**

---

## Task 7: Update Documentation

- [ ] **Step 1: Update configurations.md** with Wealthsimple env vars (WEALTHSIMPLE_ENABLED, WEALTHSIMPLE_AUTH_URL, WEALTHSIMPLE_GRAPHQL_URL, WEALTHSIMPLE_CLIENT_ID, WS_ORDER_RATE_LIMIT)

- [ ] **Step 2: Update backend-services.md** with Wealthsimple adapter section (FakeWealthsimpleAdapter, WealthsimpleAdapter, WealthsimpleGraphQlClient, WealthsimpleTokenManager, WealthsimpleRateLimiter, WealthsimpleDtoMappers)

- [ ] **Step 3: Commit**

```bash
git add docs/reference/
git commit -m "docs: add Wealthsimple adapter documentation to reference files"
```

---

## Verification Checklist

1. All unit tests pass
2. Docker image builds
3. Service starts with all three fake adapters active
4. `GET /api/v1/gateway/health` shows IBKR, QUESTRADE, WEALTHSIMPLE all enabled
5. Documentation updated
