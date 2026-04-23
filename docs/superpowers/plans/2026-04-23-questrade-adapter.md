# Questrade Adapter — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Questrade adapter for the broker-gateway service — a `FakeQuestradeAdapter` for dev/test and a real `QuestradeAdapter` that calls the Questrade REST API for account data and order execution.

**Architecture:** The adapter implements the `BrokerAdapter` interface. Questrade uses OAuth 2.0 with single-use refresh tokens and a dynamic `api_server` URL returned on each token exchange. The `QuestradeTokenManager` handles atomic token rotation — persisting new refresh tokens immediately after each exchange. The `QuestradeRestClient` uses Spring `WebClient` for HTTP calls against the dynamic base URL. A `FakeQuestradeAdapter` provides realistic mock data for dev/test.

**Tech Stack:** Spring Boot 3.3.5, Kotlin 2.0.21, Spring WebFlux `WebClient`, Jackson for JSON parsing.

**Spec:** `docs/superpowers/specs/2026-04-23-broker-gateway-design.md` — Questrade section

---

## File Structure

### New files to create

```
backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/
  QuestradeConfig.kt             — @ConfigurationProperties
  QuestradeDtoMappers.kt         — Questrade types → UnifiedDTOs
  QuestradeRestClient.kt         — WebClient HTTP calls to Questrade API
  QuestradeTokenManager.kt       — OAuth token rotation with single-use refresh tokens
  QuestradeAdapter.kt            — Real adapter orchestrating client + token manager
  FakeQuestradeAdapter.kt        — Dev/test mock

backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/
  QuestradeDtoMappersTest.kt
  QuestradeTokenManagerTest.kt
  FakeQuestradeAdapterTest.kt
  QuestradeAdapterTest.kt
```

### Files to modify

```
backend/broker-gateway/src/main/resources/application.yml  — Add questrade config
docker-compose.yml                                          — Add QUESTRADE env vars
```

---

## Task 1: Configuration

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeConfig.kt`
- Modify: `backend/broker-gateway/src/main/resources/application.yml`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Create QuestradeConfig**

```kotlin
// adapter/questrade/QuestradeConfig.kt
package com.portfolio.brokergateway.adapter.questrade

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway.questrade")
data class QuestradeConfig(
    val enabled: Boolean = false,
    val authUrl: String = "https://login.questrade.com/oauth2/token",
    val practiceAuthUrl: String = "https://practicelogin.questrade.com/oauth2/token",
    val usePractice: Boolean = false,
    val rateLimitPerSecond: Int = 1
)
```

- [ ] **Step 2: Add questrade config to application.yml**

Append after the `broker-gateway.ibkr` block:

```yaml
  questrade:
    enabled: ${QUESTRADE_ENABLED:false}
    auth-url: ${QUESTRADE_AUTH_URL:https://login.questrade.com/oauth2/token}
    practice-auth-url: ${QUESTRADE_PRACTICE_AUTH_URL:https://practicelogin.questrade.com/oauth2/token}
    use-practice: ${QUESTRADE_USE_PRACTICE:false}
    rate-limit-per-second: ${QUESTRADE_RATE_LIMIT:1}
```

- [ ] **Step 3: Add Questrade env vars to docker-compose.yml gateway service**

In the `broker-gateway-service` environment block, add:

```yaml
      QUESTRADE_ENABLED: ${QUESTRADE_ENABLED:-false}
```

- [ ] **Step 4: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeConfig.kt backend/broker-gateway/src/main/resources/application.yml docker-compose.yml
git commit -m "feat(broker-gateway): add Questrade configuration"
```

---

## Task 2: DTO Mappers

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeDtoMappers.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeDtoMappersTest.kt`

- [ ] **Step 1: Create QuestradeDtoMappers**

```kotlin
// adapter/questrade/QuestradeDtoMappers.kt
package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.*

object QuestradeDtoMappers {

    fun mapAccountType(raw: String?): AccountType = when (raw?.trim()) {
        "Cash" -> AccountType.CASH
        "Margin" -> AccountType.MARGIN
        "TFSA" -> AccountType.TFSA
        "RRSP", "SRRSP", "LRRSP" -> AccountType.RRSP
        "FHSA" -> AccountType.FHSA
        "RESP", "FRESP" -> AccountType.RESP
        "LIRA" -> AccountType.LIRA
        "LIF" -> AccountType.LIF
        "RIF", "SRIF", "RRIF", "PRIF", "LRIF" -> AccountType.RIF
        else -> AccountType.OTHER
    }

    fun mapInstrumentType(typeCode: String?): InstrumentType = when (typeCode) {
        "Stock" -> InstrumentType.STOCK
        "ETF" -> InstrumentType.ETF
        "Option" -> InstrumentType.OPTION
        "Bond" -> InstrumentType.BOND
        "MutualFund" -> InstrumentType.MUTUAL_FUND
        else -> InstrumentType.OTHER
    }

    fun mapOrderStatus(state: String?): OrderStatus = when (state) {
        "Pending" -> OrderStatus.PENDING
        "Accepted", "Open" -> OrderStatus.SUBMITTED
        "Executed" -> OrderStatus.FILLED
        "PartiallyExecuted" -> OrderStatus.PARTIALLY_FILLED
        "Canceled", "Expired" -> OrderStatus.CANCELLED
        "Rejected" -> OrderStatus.REJECTED
        "Failed" -> OrderStatus.FAILED
        else -> OrderStatus.PENDING
    }

    fun mapOrderType(type: String?): OrderType = when (type) {
        "Market" -> OrderType.MARKET
        "Limit" -> OrderType.LIMIT
        "Stop" -> OrderType.STOP
        "StopLimit" -> OrderType.STOP_LIMIT
        else -> OrderType.MARKET
    }

    fun mapTimeInForce(tif: String?): TimeInForce = when (tif) {
        "Day" -> TimeInForce.DAY
        "GoodTillCanceled" -> TimeInForce.GTC
        "ImmediateOrCancel" -> TimeInForce.IOC
        "FillOrKill" -> TimeInForce.FOK
        else -> TimeInForce.DAY
    }

    fun mapOrderAction(side: String?): OrderAction = when (side) {
        "Buy", "BTO" -> OrderAction.BUY
        "Sell", "STC", "BTC" -> OrderAction.SELL
        else -> OrderAction.BUY
    }

    fun mapActivityType(type: String?, action: String?): ActivityType = when (type) {
        "Trades" -> if (action == "Buy") ActivityType.BUY else ActivityType.SELL
        "Dividends" -> ActivityType.DIVIDEND
        "Deposits" -> ActivityType.TRANSFER_IN
        "Withdrawals" -> ActivityType.TRANSFER_OUT
        "Fees", "FX conversion" -> ActivityType.FEE
        "Commissions" -> ActivityType.COMMISSION
        "Interest" -> ActivityType.INTEREST
        "Corporate actions" -> ActivityType.CORPORATE_ACTION
        else -> ActivityType.OTHER
    }
}
```

- [ ] **Step 2: Create QuestradeDtoMappersTest**

```kotlin
// test: adapter/questrade/QuestradeDtoMappersTest.kt
package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class QuestradeDtoMappersTest {

    @Test
    fun `mapAccountType normalizes Questrade account types`() {
        assertEquals(AccountType.CASH, QuestradeDtoMappers.mapAccountType("Cash"))
        assertEquals(AccountType.MARGIN, QuestradeDtoMappers.mapAccountType("Margin"))
        assertEquals(AccountType.TFSA, QuestradeDtoMappers.mapAccountType("TFSA"))
        assertEquals(AccountType.RRSP, QuestradeDtoMappers.mapAccountType("RRSP"))
        assertEquals(AccountType.RRSP, QuestradeDtoMappers.mapAccountType("SRRSP"))
        assertEquals(AccountType.FHSA, QuestradeDtoMappers.mapAccountType("FHSA"))
        assertEquals(AccountType.RESP, QuestradeDtoMappers.mapAccountType("RESP"))
        assertEquals(AccountType.LIRA, QuestradeDtoMappers.mapAccountType("LIRA"))
        assertEquals(AccountType.LIF, QuestradeDtoMappers.mapAccountType("LIF"))
        assertEquals(AccountType.RIF, QuestradeDtoMappers.mapAccountType("RIF"))
        assertEquals(AccountType.RIF, QuestradeDtoMappers.mapAccountType("RRIF"))
        assertEquals(AccountType.OTHER, QuestradeDtoMappers.mapAccountType("Unknown"))
    }

    @Test
    fun `mapInstrumentType normalizes Questrade symbol types`() {
        assertEquals(InstrumentType.STOCK, QuestradeDtoMappers.mapInstrumentType("Stock"))
        assertEquals(InstrumentType.ETF, QuestradeDtoMappers.mapInstrumentType("ETF"))
        assertEquals(InstrumentType.OPTION, QuestradeDtoMappers.mapInstrumentType("Option"))
        assertEquals(InstrumentType.BOND, QuestradeDtoMappers.mapInstrumentType("Bond"))
        assertEquals(InstrumentType.MUTUAL_FUND, QuestradeDtoMappers.mapInstrumentType("MutualFund"))
        assertEquals(InstrumentType.OTHER, QuestradeDtoMappers.mapInstrumentType("Warrant"))
    }

    @Test
    fun `mapOrderStatus normalizes Questrade order states`() {
        assertEquals(OrderStatus.PENDING, QuestradeDtoMappers.mapOrderStatus("Pending"))
        assertEquals(OrderStatus.SUBMITTED, QuestradeDtoMappers.mapOrderStatus("Accepted"))
        assertEquals(OrderStatus.SUBMITTED, QuestradeDtoMappers.mapOrderStatus("Open"))
        assertEquals(OrderStatus.FILLED, QuestradeDtoMappers.mapOrderStatus("Executed"))
        assertEquals(OrderStatus.PARTIALLY_FILLED, QuestradeDtoMappers.mapOrderStatus("PartiallyExecuted"))
        assertEquals(OrderStatus.CANCELLED, QuestradeDtoMappers.mapOrderStatus("Canceled"))
        assertEquals(OrderStatus.CANCELLED, QuestradeDtoMappers.mapOrderStatus("Expired"))
        assertEquals(OrderStatus.REJECTED, QuestradeDtoMappers.mapOrderStatus("Rejected"))
    }

    @Test
    fun `mapActivityType normalizes Questrade activity types`() {
        assertEquals(ActivityType.BUY, QuestradeDtoMappers.mapActivityType("Trades", "Buy"))
        assertEquals(ActivityType.SELL, QuestradeDtoMappers.mapActivityType("Trades", "Sell"))
        assertEquals(ActivityType.DIVIDEND, QuestradeDtoMappers.mapActivityType("Dividends", null))
        assertEquals(ActivityType.TRANSFER_IN, QuestradeDtoMappers.mapActivityType("Deposits", null))
        assertEquals(ActivityType.TRANSFER_OUT, QuestradeDtoMappers.mapActivityType("Withdrawals", null))
        assertEquals(ActivityType.FEE, QuestradeDtoMappers.mapActivityType("Fees", null))
        assertEquals(ActivityType.COMMISSION, QuestradeDtoMappers.mapActivityType("Commissions", null))
        assertEquals(ActivityType.INTEREST, QuestradeDtoMappers.mapActivityType("Interest", null))
        assertEquals(ActivityType.CORPORATE_ACTION, QuestradeDtoMappers.mapActivityType("Corporate actions", null))
        assertEquals(ActivityType.OTHER, QuestradeDtoMappers.mapActivityType("Unknown", null))
    }

    @Test
    fun `mapOrderAction normalizes Questrade sides`() {
        assertEquals(OrderAction.BUY, QuestradeDtoMappers.mapOrderAction("Buy"))
        assertEquals(OrderAction.BUY, QuestradeDtoMappers.mapOrderAction("BTO"))
        assertEquals(OrderAction.SELL, QuestradeDtoMappers.mapOrderAction("Sell"))
        assertEquals(OrderAction.SELL, QuestradeDtoMappers.mapOrderAction("STC"))
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeDtoMappers.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeDtoMappersTest.kt
git commit -m "feat(broker-gateway): add Questrade DTO mappers with normalization"
```

---

## Task 3: Questrade REST Client and Token Manager

The token manager handles Questrade's unique auth: single-use refresh tokens that rotate on every exchange, plus a dynamic `api_server` URL.

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeRestClient.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeTokenManager.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeTokenManagerTest.kt`

- [ ] **Step 1: Create QuestradeRestClient**

```kotlin
// adapter/questrade/QuestradeRestClient.kt
package com.portfolio.brokergateway.adapter.questrade

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import com.portfolio.brokergateway.exception.BrokerConnectionException
import com.portfolio.brokergateway.exception.BrokerDataException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class QuestradeRestClient(
    private val webClientBuilder: WebClient.Builder = WebClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(apiServerUrl: String, accessToken: String, path: String): JsonNode {
        return try {
            val client = buildClient(apiServerUrl, accessToken)
            client.get().uri(path)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerDataException("Empty response from Questrade: $path", BrokerType.QUESTRADE)
        } catch (e: WebClientResponseException) {
            handleError(e, path)
        } catch (e: BrokerDataException) {
            throw e
        } catch (e: Exception) {
            throw BrokerConnectionException("Failed to reach Questrade API: ${e.message}", BrokerType.QUESTRADE, e)
        }
    }

    fun post(apiServerUrl: String, accessToken: String, path: String, body: Any): JsonNode {
        return try {
            val client = buildClient(apiServerUrl, accessToken)
            client.post().uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerDataException("Empty response from Questrade: $path", BrokerType.QUESTRADE)
        } catch (e: WebClientResponseException) {
            handleError(e, path)
        } catch (e: BrokerDataException) {
            throw e
        } catch (e: Exception) {
            throw BrokerConnectionException("Failed to reach Questrade API: ${e.message}", BrokerType.QUESTRADE, e)
        }
    }

    fun delete(apiServerUrl: String, accessToken: String, path: String): JsonNode? {
        return try {
            val client = buildClient(apiServerUrl, accessToken)
            client.delete().uri(path)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block()
        } catch (e: WebClientResponseException) {
            handleError(e, path)
        } catch (e: Exception) {
            throw BrokerConnectionException("Failed to reach Questrade API: ${e.message}", BrokerType.QUESTRADE, e)
        }
    }

    private fun buildClient(apiServerUrl: String, accessToken: String): WebClient {
        return webClientBuilder
            .baseUrl(apiServerUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .build()
    }

    private fun handleError(e: WebClientResponseException, path: String): Nothing {
        log.error("Questrade API error {} on {}: {}", e.statusCode, path, e.responseBodyAsString)
        when {
            e.statusCode == HttpStatusCode.valueOf(401) ->
                throw BrokerAuthenticationException("Questrade auth failed on $path", BrokerType.QUESTRADE)
            e.statusCode == HttpStatusCode.valueOf(429) ->
                throw com.portfolio.brokergateway.exception.BrokerRateLimitException(
                    "Questrade rate limit hit on $path", BrokerType.QUESTRADE)
            else ->
                throw BrokerDataException("Questrade error ${e.statusCode} on $path: ${e.responseBodyAsString}",
                    BrokerType.QUESTRADE, e)
        }
    }
}
```

- [ ] **Step 2: Create QuestradeTokenManager**

```kotlin
// adapter/questrade/QuestradeTokenManager.kt
package com.portfolio.brokergateway.adapter.questrade

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient

class QuestradeTokenManager(
    private val config: QuestradeConfig,
    private val webClientBuilder: WebClient.Builder = WebClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun refreshTokens(credentials: BrokerCredentials.QuestradeCredentials): BrokerCredentials.QuestradeCredentials {
        val authUrl = if (config.usePractice) config.practiceAuthUrl else config.authUrl
        val url = "$authUrl?grant_type=refresh_token&refresh_token=${credentials.refreshToken}"

        log.info("Refreshing Questrade tokens via {}", if (config.usePractice) "practice" else "production")

        val response: JsonNode = try {
            webClientBuilder.build()
                .get().uri(url)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerAuthenticationException(
                    "Empty response from Questrade token exchange", BrokerType.QUESTRADE)
        } catch (e: BrokerAuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw BrokerAuthenticationException(
                "Questrade token refresh failed: ${e.message}", BrokerType.QUESTRADE, cause = e)
        }

        val accessToken = response.get("access_token")?.asText()
            ?: throw BrokerAuthenticationException("No access_token in Questrade response", BrokerType.QUESTRADE)
        val newRefreshToken = response.get("refresh_token")?.asText()
            ?: throw BrokerAuthenticationException("No refresh_token in Questrade response", BrokerType.QUESTRADE)
        val apiServer = response.get("api_server")?.asText()
            ?: throw BrokerAuthenticationException("No api_server in Questrade response", BrokerType.QUESTRADE)
        val expiresIn = response.get("expires_in")?.asLong() ?: 1800L

        log.info("Questrade tokens refreshed, api_server={}", apiServer)

        return BrokerCredentials.QuestradeCredentials(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            apiServerUrl = apiServer,
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn
        )
    }

    fun isTokenExpired(credentials: BrokerCredentials.QuestradeCredentials): Boolean {
        val nowEpoch = System.currentTimeMillis() / 1000
        return nowEpoch >= credentials.expiresAtEpochSeconds - 60
    }
}
```

- [ ] **Step 3: Create QuestradeTokenManagerTest**

```kotlin
// test: adapter/questrade/QuestradeTokenManagerTest.kt
package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.BrokerCredentials
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestradeTokenManagerTest {

    private val config = QuestradeConfig(enabled = true)
    private val manager = QuestradeTokenManager(config)

    @Test
    fun `isTokenExpired returns true when token is past expiry`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "token", refreshToken = "refresh",
            apiServerUrl = "https://api05.iq.questrade.com/",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 - 100
        )
        assertTrue(manager.isTokenExpired(creds))
    }

    @Test
    fun `isTokenExpired returns true when within 60s of expiry`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "token", refreshToken = "refresh",
            apiServerUrl = "https://api05.iq.questrade.com/",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 30
        )
        assertTrue(manager.isTokenExpired(creds))
    }

    @Test
    fun `isTokenExpired returns false when token has time remaining`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "token", refreshToken = "refresh",
            apiServerUrl = "https://api05.iq.questrade.com/",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 600
        )
        assertFalse(manager.isTokenExpired(creds))
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeRestClient.kt backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeTokenManager.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeTokenManagerTest.kt
git commit -m "feat(broker-gateway): add Questrade REST client and token manager"
```

---

## Task 4: FakeQuestradeAdapter

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/FakeQuestradeAdapter.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/FakeQuestradeAdapterTest.kt`

- [ ] **Step 1: Create FakeQuestradeAdapter**

```kotlin
// adapter/questrade/FakeQuestradeAdapter.kt
package com.portfolio.brokergateway.adapter.questrade

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
class FakeQuestradeAdapter : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val orderIdCounter = AtomicInteger(20000)

    override val brokerType = BrokerType.QUESTRADE

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        log.info("FakeQuestradeAdapter: validating connection")
        return ConnectionValidationResult(connected = true, message = "Fake Questrade connection OK")
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials {
        val qt = credentials as BrokerCredentials.QuestradeCredentials
        return qt.copy(
            accessToken = "fake-refreshed-token",
            refreshToken = "fake-new-refresh-token",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 1800
        )
    }

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        return listOf(
            UnifiedAccount(accountId = "51443483", accountNumber = "51443483",
                accountName = "Margin", accountType = AccountType.MARGIN,
                currency = "CAD", brokerType = BrokerType.QUESTRADE, status = "ACTIVE"),
            UnifiedAccount(accountId = "51443484", accountNumber = "51443484",
                accountName = "TFSA", accountType = AccountType.TFSA,
                currency = "CAD", brokerType = BrokerType.QUESTRADE, status = "ACTIVE"),
            UnifiedAccount(accountId = "51443485", accountNumber = "51443485",
                accountName = "RRSP", accountType = AccountType.RRSP,
                currency = "CAD", brokerType = BrokerType.QUESTRADE, status = "ACTIVE")
        )
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        return UnifiedBalance(
            accountId = accountId,
            totalEquity = BigDecimal("85000.00"),
            totalValue = BigDecimal("72000.00"),
            cashBalances = listOf(
                CashBalance(currency = "CAD", amount = BigDecimal("13000.00")),
                CashBalance(currency = "USD", amount = BigDecimal("2500.00"))
            ),
            buyingPower = BigDecimal("45000.00"),
            currency = "CAD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        data class MockPos(val sym: String, val name: String, val qty: Int, val cost: Double, val price: Double, val type: InstrumentType)
        val positions = listOf(
            MockPos("XIU.TO", "iShares S&P/TSX 60 ETF", 200, 32.50, 34.80, InstrumentType.ETF),
            MockPos("VFV.TO", "Vanguard S&P 500 ETF", 150, 95.00, 102.50, InstrumentType.ETF),
            MockPos("RY.TO", "Royal Bank of Canada", 80, 130.00, 142.75, InstrumentType.STOCK),
            MockPos("TD.TO", "Toronto-Dominion Bank", 100, 85.00, 78.50, InstrumentType.STOCK)
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
            UnifiedActivity(externalId = null, type = ActivityType.BUY, symbol = "RY.TO",
                description = "Buy 80 RY.TO", quantity = BigDecimal(80), price = BigDecimal("130.00"),
                amount = BigDecimal("-10400.00"), fee = BigDecimal("-4.95"), currency = "CAD",
                tradeDate = now.minusDays(20), settlementDate = now.minusDays(18), optionType = null),
            UnifiedActivity(externalId = null, type = ActivityType.DIVIDEND, symbol = "XIU.TO",
                description = "Dividend XIU.TO", quantity = null, price = null,
                amount = BigDecimal("48.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(10), settlementDate = now.minusDays(8), optionType = null),
            UnifiedActivity(externalId = null, type = ActivityType.TRANSFER_IN, symbol = null,
                description = "EFT Deposit", quantity = null, price = null,
                amount = BigDecimal("5000.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(25), settlementDate = now.minusDays(25), optionType = null)
        )
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val now = OffsetDateTime.now()
        return listOf(
            UnifiedOrder(brokerOrderId = "173577239", symbol = "VFV.TO", action = OrderAction.BUY,
                orderType = OrderType.LIMIT, timeInForce = TimeInForce.DAY,
                totalQuantity = BigDecimal(50), filledQuantity = BigDecimal(50),
                executionPrice = BigDecimal("95.00"), limitPrice = BigDecimal("96.00"),
                stopPrice = null, status = OrderStatus.FILLED, currency = "CAD",
                submittedAt = now.minusDays(30), filledAt = now.minusDays(30)),
            UnifiedOrder(brokerOrderId = "173577240", symbol = "ENB.TO", action = OrderAction.BUY,
                orderType = OrderType.LIMIT, timeInForce = TimeInForce.GTC,
                totalQuantity = BigDecimal(100), filledQuantity = null,
                executionPrice = null, limitPrice = BigDecimal("48.00"),
                stopPrice = null, status = OrderStatus.SUBMITTED, currency = "CAD",
                submittedAt = now.minusDays(2), filledAt = null)
        )
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        val orderId = orderIdCounter.incrementAndGet()
        log.info("FakeQuestradeAdapter: placed {} {} order for {} {} @ {}",
            request.orderType, request.action, request.quantity, request.symbol, request.limitPrice ?: "MKT")
        return OrderResult(brokerOrderId = orderId.toString(), status = OrderStatus.SUBMITTED, message = "Fake order submitted")
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        log.info("FakeQuestradeAdapter: cancelled order {}", brokerOrderId)
        return CancelResult(success = true, message = "Fake order cancelled")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.QUESTRADE, supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT),
            supportsOptionPositions = true, supportsFractionalShares = false,
            supportsRealTimeData = true, supportsHistoricalActivities = true,
            activityHistoryDepth = "Unlimited", orderRateLimit = "~1 req/sec",
            isOfficialApi = true, notes = "FakeQuestradeAdapter — dev/test mock. Order placement requires partner app registration."
        )
    }
}
```

- [ ] **Step 2: Create FakeQuestradeAdapterTest**

```kotlin
// test: adapter/questrade/FakeQuestradeAdapterTest.kt
package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeQuestradeAdapterTest {

    private val adapter = FakeQuestradeAdapter()

    @Test
    fun `brokerType is QUESTRADE`() {
        assertEquals(BrokerType.QUESTRADE, adapter.brokerType)
    }

    @Test
    fun `validateConnection returns connected`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "t", refreshToken = "r",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 999999999)
        assertTrue(adapter.validateConnection(creds).connected)
    }

    @Test
    fun `listAccounts returns Canadian accounts`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "t", refreshToken = "r",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 999999999)
        val accounts = adapter.listAccounts(creds)
        assertEquals(3, accounts.size)
        assertTrue(accounts.any { it.accountType == AccountType.TFSA })
        assertTrue(accounts.any { it.accountType == AccountType.RRSP })
        assertTrue(accounts.all { it.brokerType == BrokerType.QUESTRADE })
    }

    @Test
    fun `getPositions returns CAD-denominated positions`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "t", refreshToken = "r",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 999999999)
        val positions = adapter.getPositions(creds, "51443483")
        assertTrue(positions.isNotEmpty())
        assertTrue(positions.all { it.currency == "CAD" })
        assertTrue(positions.any { it.symbol.endsWith(".TO") })
    }

    @Test
    fun `placeOrder returns submitted result`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "t", refreshToken = "r",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 999999999)
        val request = OrderRequest(symbol = "XIU.TO", action = OrderAction.BUY,
            quantity = BigDecimal(100), orderType = OrderType.LIMIT,
            limitPrice = BigDecimal("34.00"))
        val result = adapter.placeOrder(creds, "51443483", request)
        assertNotNull(result.brokerOrderId)
        assertEquals(OrderStatus.SUBMITTED, result.status)
    }

    @Test
    fun `refreshAuth returns updated credentials`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "old", refreshToken = "old-refresh",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 1000)
        val refreshed = adapter.refreshAuth(creds) as BrokerCredentials.QuestradeCredentials
        assertEquals("fake-refreshed-token", refreshed.accessToken)
        assertTrue(refreshed.expiresAtEpochSeconds > 1000)
    }

    @Test
    fun `capabilities reports Questrade features`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.QUESTRADE, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertTrue(caps.isOfficialApi)
        assertEquals(false, caps.supportsFractionalShares)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/FakeQuestradeAdapter.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/FakeQuestradeAdapterTest.kt
git commit -m "feat(broker-gateway): add FakeQuestradeAdapter for dev/test with Canadian mock data"
```

---

## Task 5: Real QuestradeAdapter

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapter.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapterTest.kt`

- [ ] **Step 1: Create QuestradeAdapter**

```kotlin
// adapter/questrade/QuestradeAdapter.kt
package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
@ConditionalOnProperty(prefix = "broker-gateway.questrade", name = ["enabled"], havingValue = "true")
class QuestradeAdapter(
    private val config: QuestradeConfig
) : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = QuestradeRestClient()
    private val tokenManager = QuestradeTokenManager(config)

    override val brokerType = BrokerType.QUESTRADE

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        return try {
            val creds = credentials as BrokerCredentials.QuestradeCredentials
            restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts")
            ConnectionValidationResult(connected = true, message = "Connected to Questrade")
        } catch (e: Exception) {
            ConnectionValidationResult(connected = false, message = e.message, needsReauth = true)
        }
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        return tokenManager.refreshTokens(creds)
    }

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val response = restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts")
        val accounts = response.get("accounts") ?: return emptyList()
        return accounts.map { acct ->
            UnifiedAccount(
                accountId = acct.get("number")?.asText() ?: "",
                accountNumber = acct.get("number")?.asText(),
                accountName = acct.get("type")?.asText(),
                accountType = QuestradeDtoMappers.mapAccountType(acct.get("type")?.asText()),
                currency = acct.get("currency")?.asText(),
                brokerType = BrokerType.QUESTRADE,
                status = acct.get("status")?.asText()
            )
        }
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val response = restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts/$accountId/balances")
        val combined = response.get("combinedBalances")?.firstOrNull()
        val perCurrency = response.get("perCurrencyBalances") ?: response.get("combinedBalances")
        val cashBalances = perCurrency?.map { b ->
            CashBalance(
                currency = b.get("currency")?.asText() ?: "CAD",
                amount = b.get("cash")?.decimalValue() ?: BigDecimal.ZERO
            )
        } ?: emptyList()

        return UnifiedBalance(
            accountId = accountId,
            totalEquity = combined?.get("totalEquity")?.decimalValue(),
            totalValue = combined?.get("marketValue")?.decimalValue(),
            cashBalances = cashBalances,
            buyingPower = combined?.get("buyingPower")?.decimalValue(),
            currency = combined?.get("currency")?.asText() ?: "CAD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val response = restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts/$accountId/positions")
        val positions = response.get("positions") ?: return emptyList()
        return positions.map { pos ->
            UnifiedPosition(
                symbol = pos.get("symbol")?.asText() ?: "",
                symbolId = pos.get("symbolId")?.asText(),
                securityName = null,
                instrumentType = QuestradeDtoMappers.mapInstrumentType(pos.get("symbolTypeCode")?.asText()),
                quantity = pos.get("openQuantity")?.decimalValue() ?: BigDecimal.ZERO,
                averageCost = pos.get("averageEntryPrice")?.decimalValue(),
                currentPrice = pos.get("currentPrice")?.decimalValue(),
                currentValue = pos.get("currentMarketValue")?.decimalValue(),
                totalPnl = pos.get("openPnl")?.decimalValue(),
                totalPnlPercent = null,
                currency = pos.get("currencyCode")?.asText() ?: "CAD"
            )
        }
    }

    override fun getActivities(
        credentials: BrokerCredentials, accountId: String,
        startDate: LocalDate?, endDate: LocalDate?
    ): List<UnifiedActivity> {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val start = (startDate ?: LocalDate.now().minusDays(30)).format(DateTimeFormatter.ISO_DATE)
        val end = (endDate ?: LocalDate.now()).format(DateTimeFormatter.ISO_DATE)
        val response = restClient.get(creds.apiServerUrl, creds.accessToken,
            "/v1/accounts/$accountId/activities?startTime=${start}T00:00:00-05:00&endTime=${end}T23:59:59-05:00")
        val activities = response.get("activities") ?: return emptyList()
        return activities.map { act ->
            UnifiedActivity(
                externalId = null,
                type = QuestradeDtoMappers.mapActivityType(act.get("type")?.asText(), act.get("action")?.asText()),
                symbol = act.get("symbol")?.asText(),
                description = act.get("description")?.asText(),
                quantity = act.get("quantity")?.decimalValue(),
                price = act.get("price")?.decimalValue(),
                amount = act.get("netAmount")?.decimalValue() ?: BigDecimal.ZERO,
                fee = act.get("commission")?.decimalValue(),
                currency = act.get("currency")?.asText() ?: "CAD",
                tradeDate = act.get("tradeDate")?.asText()?.let { LocalDate.parse(it.substring(0, 10)) } ?: LocalDate.now(),
                settlementDate = act.get("settlementDate")?.asText()?.let { LocalDate.parse(it.substring(0, 10)) },
                optionType = null
            )
        }
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val response = restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts/$accountId/orders")
        val orders = response.get("orders") ?: return emptyList()
        return orders.map { ord ->
            UnifiedOrder(
                brokerOrderId = ord.get("id")?.asText() ?: "",
                symbol = ord.get("symbol")?.asText() ?: "",
                action = QuestradeDtoMappers.mapOrderAction(ord.get("side")?.asText()),
                orderType = QuestradeDtoMappers.mapOrderType(ord.get("orderType")?.asText()),
                timeInForce = QuestradeDtoMappers.mapTimeInForce(ord.get("timeInForce")?.asText()),
                totalQuantity = ord.get("totalQuantity")?.decimalValue() ?: BigDecimal.ZERO,
                filledQuantity = ord.get("filledQuantity")?.decimalValue(),
                executionPrice = ord.get("avgExecPrice")?.decimalValue(),
                limitPrice = ord.get("limitPrice")?.decimalValue(),
                stopPrice = ord.get("stopPrice")?.decimalValue(),
                status = QuestradeDtoMappers.mapOrderStatus(ord.get("state")?.asText()),
                currency = ord.get("currency")?.asText(),
                submittedAt = ord.get("creationTime")?.asText()?.let { parseDateTime(it) },
                filledAt = ord.get("updateTime")?.asText()?.let { parseDateTime(it) }
            )
        }
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val body = mapOf(
            "symbolId" to request.symbol,
            "quantity" to request.quantity,
            "orderType" to when (request.orderType) {
                OrderType.MARKET -> "Market"; OrderType.LIMIT -> "Limit"
                OrderType.STOP -> "Stop"; OrderType.STOP_LIMIT -> "StopLimit"
            },
            "timeInForce" to when (request.timeInForce) {
                TimeInForce.DAY -> "Day"; TimeInForce.GTC -> "GoodTillCanceled"
                TimeInForce.IOC -> "ImmediateOrCancel"; TimeInForce.FOK -> "FillOrKill"
            },
            "action" to if (request.action == OrderAction.BUY) "Buy" else "Sell",
            "limitPrice" to request.limitPrice,
            "stopPrice" to request.stopPrice
        ).filterValues { it != null }

        val response = restClient.post(creds.apiServerUrl, creds.accessToken,
            "/v1/accounts/$accountId/orders", body)
        val orderId = response.get("orderId")?.asText() ?: response.get("id")?.asText()
        return OrderResult(brokerOrderId = orderId, status = OrderStatus.SUBMITTED)
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        restClient.delete(creds.apiServerUrl, creds.accessToken,
            "/v1/accounts/$accountId/orders/$brokerOrderId")
        return CancelResult(success = true, message = "Cancel request sent")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.QUESTRADE, supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT),
            supportsOptionPositions = true, supportsFractionalShares = false,
            supportsRealTimeData = true, supportsHistoricalActivities = true,
            activityHistoryDepth = "Unlimited", orderRateLimit = "~1 req/sec",
            isOfficialApi = true, notes = "Order placement may require Questrade Partner App registration"
        )
    }

    private fun parseDateTime(iso: String): OffsetDateTime? {
        return try { OffsetDateTime.parse(iso) } catch (_: Exception) { null }
    }
}
```

- [ ] **Step 2: Create QuestradeAdapterTest**

```kotlin
// test: adapter/questrade/QuestradeAdapterTest.kt
package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.BrokerType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestradeAdapterTest {

    private val config = QuestradeConfig(enabled = true)
    private val adapter = QuestradeAdapter(config)

    @Test
    fun `brokerType is QUESTRADE`() {
        assertEquals(BrokerType.QUESTRADE, adapter.brokerType)
    }

    @Test
    fun `capabilities reports Questrade features`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.QUESTRADE, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertTrue(caps.isOfficialApi)
        assertEquals(false, caps.supportsFractionalShares)
        assertTrue(caps.supportedOrderTypes.size == 4)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapter.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapterTest.kt
git commit -m "feat(broker-gateway): add real QuestradeAdapter with REST client integration"
```

---

## Task 6: Build, Test, and Verify

- [ ] **Step 1: Rebuild Docker image**

Run: `docker compose build broker-gateway-service`

- [ ] **Step 2: Restart service**

Run: `docker compose up -d broker-gateway-service`

- [ ] **Step 3: Verify health shows Questrade enabled**

Run: `curl http://localhost:8084/api/v1/gateway/health`
Expected: Both IBKR and QUESTRADE show `"enabled": true`

- [ ] **Step 4: Run all tests**

Run: `docker run --rm -v "C:\Users\SaurabhBilakhia\Documents\POC\pc\backend:/work" -w //work/broker-gateway gradle:8.10-jdk21-alpine gradle test --no-daemon`
Expected: All tests pass

- [ ] **Step 5: Commit any fixes**

---

## Task 7: Update Documentation

- [ ] **Step 1: Update configurations.md** with Questrade env vars (QUESTRADE_ENABLED, QUESTRADE_AUTH_URL, QUESTRADE_USE_PRACTICE, QUESTRADE_RATE_LIMIT)

- [ ] **Step 2: Update backend-services.md** with Questrade adapter section (FakeQuestradeAdapter, QuestradeAdapter, QuestradeRestClient, QuestradeTokenManager, QuestradeDtoMappers)

- [ ] **Step 3: Commit**

```bash
git add docs/reference/
git commit -m "docs: add Questrade adapter documentation to reference files"
```

---

## Verification Checklist

1. All unit tests pass
2. Docker image builds
3. Service starts with both FakeIbkrAdapter and FakeQuestradeAdapter active
4. `GET /api/v1/gateway/health` shows IBKR and QUESTRADE enabled
5. Documentation updated
