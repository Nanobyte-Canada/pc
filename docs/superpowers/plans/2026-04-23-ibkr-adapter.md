# IBKR Adapter — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the IBKR adapter for the broker-gateway service — a `FakeIbkrAdapter` for dev/test and a real `IbkrAdapter` that connects to TWS/IB Gateway for account data and order execution.

**Architecture:** The adapter implements the `BrokerAdapter` interface from the gateway skeleton. The fake adapter is `@Profile("dev", "local", "test")` and returns realistic mock data (matching `FakeIbkrClient` patterns in market-data). The real adapter uses TWS API's `EClientSocket`/`EWrapper` for async operations, wrapped with `CompletableFuture` to present synchronous responses to the REST layer. A connection manager handles socket lifecycle with exponential backoff reconnection.

**Tech Stack:** Spring Boot 3.3.5, Kotlin 2.0.21, TWS API JAR (vendored), CompletableFuture for async→sync bridging, `@ConditionalOnProperty` for adapter activation.

**Spec:** `docs/superpowers/specs/2026-04-23-broker-gateway-design.md` — IBKR section

---

## File Structure

### New files to create

```
backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/
  FakeIbkrAdapter.kt          — Mock adapter for dev/test (realistic data)
  IbkrAdapter.kt              — Real adapter wrapping TWS API
  IbkrAccountClient.kt        — EClientSocket/EWrapper wrapper with CompletableFuture
  IbkrConnectionManager.kt    — Socket lifecycle, reconnection, health
  IbkrConfig.kt               — @ConfigurationProperties for IBKR settings
  IbkrDtoMappers.kt           — TWS types → UnifiedDTOs mapping functions

backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/ibkr/
  FakeIbkrAdapterTest.kt      — Tests for fake adapter
  IbkrDtoMappersTest.kt       — Tests for mapping logic
  IbkrAdapterTest.kt          — Tests for real adapter (mocked client)
```

### Files to modify

```
backend/broker-gateway/build.gradle.kts           — Add TWS API dependency
backend/broker-gateway/src/main/resources/application.yml — Add ibkr config section
docker-compose.yml                                  — Add IBKR env vars to gateway service
```

---

## Task 1: IBKR Configuration

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrConfig.kt`
- Modify: `backend/broker-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Create IbkrConfig**

```kotlin
// adapter/ibkr/IbkrConfig.kt
package com.portfolio.brokergateway.adapter.ibkr

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway.ibkr")
data class IbkrConfig(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 4002,
    val clientId: Int = 2,
    val connectTimeoutMs: Long = 5000,
    val requestTimeoutMs: Long = 30000,
    val reconnectDelayMs: Long = 5000,
    val maxReconnectDelayMs: Long = 60000,
    val flexToken: String = "",
    val flexQueryId: String = ""
)
```

- [ ] **Step 2: Add ibkr config to application.yml**

Append after the existing `broker-gateway.service-auth` block:

```yaml
  ibkr:
    enabled: ${IBKR_GATEWAY_ENABLED:false}
    host: ${IBKR_HOST:}
    port: ${IBKR_PORT:4002}
    client-id: ${IBKR_GATEWAY_CLIENT_ID:2}
    connect-timeout-ms: ${IBKR_CONNECT_TIMEOUT:5000}
    request-timeout-ms: ${IBKR_REQUEST_TIMEOUT:30000}
    flex-token: ${IBKR_FLEX_TOKEN:}
    flex-query-id: ${IBKR_FLEX_QUERY_ID:}
```

- [ ] **Step 3: Add IBKR env vars to docker-compose.yml gateway service**

In the `broker-gateway-service` environment block, add:

```yaml
      IBKR_GATEWAY_ENABLED: ${IBKR_GATEWAY_ENABLED:-true}
      IBKR_HOST: ${IBKR_HOST:-}
      IBKR_PORT: ${IBKR_PORT:-4002}
      IBKR_GATEWAY_CLIENT_ID: ${IBKR_GATEWAY_CLIENT_ID:-2}
```

- [ ] **Step 4: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrConfig.kt backend/broker-gateway/src/main/resources/application.yml docker-compose.yml
git commit -m "feat(broker-gateway): add IBKR configuration"
```

---

## Task 2: DTO Mappers

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrDtoMappers.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrDtoMappersTest.kt`

- [ ] **Step 1: Write mapper tests**

```kotlin
// test: adapter/ibkr/IbkrDtoMappersTest.kt
package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IbkrDtoMappersTest {

    @Test
    fun `mapAccountType normalizes IBKR account types`() {
        assertEquals(AccountType.CASH, IbkrDtoMappers.mapAccountType("Individual"))
        assertEquals(AccountType.MARGIN, IbkrDtoMappers.mapAccountType("Margin"))
        assertEquals(AccountType.TFSA, IbkrDtoMappers.mapAccountType("TFSA"))
        assertEquals(AccountType.RRSP, IbkrDtoMappers.mapAccountType("RRSP"))
        assertEquals(AccountType.FHSA, IbkrDtoMappers.mapAccountType("FHSA"))
        assertEquals(AccountType.LIRA, IbkrDtoMappers.mapAccountType("LIRA"))
        assertEquals(AccountType.LIF, IbkrDtoMappers.mapAccountType("LIF"))
        assertEquals(AccountType.RIF, IbkrDtoMappers.mapAccountType("RIF"))
        assertEquals(AccountType.OTHER, IbkrDtoMappers.mapAccountType("SomethingNew"))
    }

    @Test
    fun `mapInstrumentType normalizes IBKR secType`() {
        assertEquals(InstrumentType.STOCK, IbkrDtoMappers.mapInstrumentType("STK"))
        assertEquals(InstrumentType.OPTION, IbkrDtoMappers.mapInstrumentType("OPT"))
        assertEquals(InstrumentType.BOND, IbkrDtoMappers.mapInstrumentType("BOND"))
        assertEquals(InstrumentType.MUTUAL_FUND, IbkrDtoMappers.mapInstrumentType("FUND"))
        assertEquals(InstrumentType.CASH, IbkrDtoMappers.mapInstrumentType("CASH"))
        assertEquals(InstrumentType.CRYPTO, IbkrDtoMappers.mapInstrumentType("CRYPTO"))
        assertEquals(InstrumentType.OTHER, IbkrDtoMappers.mapInstrumentType("WAR"))
    }

    @Test
    fun `mapOrderStatus normalizes IBKR order states`() {
        assertEquals(OrderStatus.PENDING, IbkrDtoMappers.mapOrderStatus("PendingSubmit"))
        assertEquals(OrderStatus.SUBMITTED, IbkrDtoMappers.mapOrderStatus("Submitted"))
        assertEquals(OrderStatus.SUBMITTED, IbkrDtoMappers.mapOrderStatus("PreSubmitted"))
        assertEquals(OrderStatus.FILLED, IbkrDtoMappers.mapOrderStatus("Filled"))
        assertEquals(OrderStatus.CANCELLED, IbkrDtoMappers.mapOrderStatus("Cancelled"))
        assertEquals(OrderStatus.CANCELLED, IbkrDtoMappers.mapOrderStatus("ApiCancelled"))
        assertEquals(OrderStatus.REJECTED, IbkrDtoMappers.mapOrderStatus("Inactive"))
        assertEquals(OrderStatus.FAILED, IbkrDtoMappers.mapOrderStatus("Error"))
        assertEquals(OrderStatus.PENDING, IbkrDtoMappers.mapOrderStatus("UnknownState"))
    }

    @Test
    fun `mapActivityType normalizes IBKR Flex codes`() {
        assertEquals(ActivityType.BUY, IbkrDtoMappers.mapActivityType("BUY"))
        assertEquals(ActivityType.BUY, IbkrDtoMappers.mapActivityType("BOT"))
        assertEquals(ActivityType.SELL, IbkrDtoMappers.mapActivityType("SELL"))
        assertEquals(ActivityType.SELL, IbkrDtoMappers.mapActivityType("SLD"))
        assertEquals(ActivityType.DIVIDEND, IbkrDtoMappers.mapActivityType("DIV"))
        assertEquals(ActivityType.DIVIDEND, IbkrDtoMappers.mapActivityType("CDIV"))
        assertEquals(ActivityType.TRANSFER_IN, IbkrDtoMappers.mapActivityType("DEP"))
        assertEquals(ActivityType.TRANSFER_OUT, IbkrDtoMappers.mapActivityType("WITH"))
        assertEquals(ActivityType.FEE, IbkrDtoMappers.mapActivityType("COMM"))
        assertEquals(ActivityType.INTEREST, IbkrDtoMappers.mapActivityType("INT"))
        assertEquals(ActivityType.OPTION_EXPIRATION, IbkrDtoMappers.mapActivityType("EXP"))
        assertEquals(ActivityType.OPTION_ASSIGNMENT, IbkrDtoMappers.mapActivityType("ASSIGN"))
        assertEquals(ActivityType.OPTION_EXERCISE, IbkrDtoMappers.mapActivityType("EXER"))
        assertEquals(ActivityType.STOCK_SPLIT, IbkrDtoMappers.mapActivityType("SPLIT"))
        assertEquals(ActivityType.CORPORATE_ACTION, IbkrDtoMappers.mapActivityType("CA"))
        assertEquals(ActivityType.OTHER, IbkrDtoMappers.mapActivityType("UNKNOWN"))
    }

    @Test
    fun `mapOptionRight normalizes C and P`() {
        assertEquals("CALL", IbkrDtoMappers.mapOptionRight("C"))
        assertEquals("PUT", IbkrDtoMappers.mapOptionRight("P"))
        assertEquals(null, IbkrDtoMappers.mapOptionRight(null))
        assertEquals(null, IbkrDtoMappers.mapOptionRight(""))
    }
}
```

- [ ] **Step 2: Create IbkrDtoMappers**

```kotlin
// adapter/ibkr/IbkrDtoMappers.kt
package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*

object IbkrDtoMappers {

    fun mapAccountType(raw: String?): AccountType = when (raw?.trim()) {
        "Individual", "Cash" -> AccountType.CASH
        "Margin" -> AccountType.MARGIN
        "TFSA" -> AccountType.TFSA
        "RRSP" -> AccountType.RRSP
        "FHSA" -> AccountType.FHSA
        "RESP" -> AccountType.RESP
        "LIRA" -> AccountType.LIRA
        "LIF" -> AccountType.LIF
        "RIF", "RRIF" -> AccountType.RIF
        else -> AccountType.OTHER
    }

    fun mapInstrumentType(secType: String?): InstrumentType = when (secType?.uppercase()) {
        "STK" -> InstrumentType.STOCK
        "OPT" -> InstrumentType.OPTION
        "BOND" -> InstrumentType.BOND
        "FUND" -> InstrumentType.MUTUAL_FUND
        "CASH" -> InstrumentType.CASH
        "CRYPTO" -> InstrumentType.CRYPTO
        else -> InstrumentType.OTHER
    }

    fun mapOrderStatus(status: String?): OrderStatus = when (status) {
        "PendingSubmit", "PendingCancel" -> OrderStatus.PENDING
        "Submitted", "PreSubmitted" -> OrderStatus.SUBMITTED
        "Filled" -> OrderStatus.FILLED
        "Cancelled", "ApiCancelled" -> OrderStatus.CANCELLED
        "Inactive" -> OrderStatus.REJECTED
        "Error" -> OrderStatus.FAILED
        else -> OrderStatus.PENDING
    }

    fun mapActivityType(code: String?): ActivityType = when (code?.uppercase()) {
        "BUY", "BOT" -> ActivityType.BUY
        "SELL", "SLD" -> ActivityType.SELL
        "DIV", "CDIV" -> ActivityType.DIVIDEND
        "DEP" -> ActivityType.TRANSFER_IN
        "WITH" -> ActivityType.TRANSFER_OUT
        "COMM", "OTHER_FEE" -> ActivityType.FEE
        "INT" -> ActivityType.INTEREST
        "EXP" -> ActivityType.OPTION_EXPIRATION
        "ASSIGN" -> ActivityType.OPTION_ASSIGNMENT
        "EXER" -> ActivityType.OPTION_EXERCISE
        "SPLIT" -> ActivityType.STOCK_SPLIT
        "CA" -> ActivityType.CORPORATE_ACTION
        else -> ActivityType.OTHER
    }

    fun mapOptionRight(right: String?): String? = when (right?.uppercase()) {
        "C" -> "CALL"
        "P" -> "PUT"
        "", null -> null
        else -> right
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrDtoMappers.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrDtoMappersTest.kt
git commit -m "feat(broker-gateway): add IBKR DTO mappers with normalization"
```

---

## Task 3: FakeIbkrAdapter

This is the dev/test implementation that returns realistic mock data, making the gateway fully functional without a real TWS connection. Follows the same `@Profile` pattern as `FakeIbkrClient` in market-data.

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/FakeIbkrAdapter.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/ibkr/FakeIbkrAdapterTest.kt`

- [ ] **Step 1: Write FakeIbkrAdapter tests**

```kotlin
// test: adapter/ibkr/FakeIbkrAdapterTest.kt
package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeIbkrAdapterTest {

    private val adapter = FakeIbkrAdapter()

    @Test
    fun `brokerType is IBKR`() {
        assertEquals(BrokerType.IBKR, adapter.brokerType)
    }

    @Test
    fun `validateConnection returns connected`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val result = adapter.validateConnection(creds)
        assertTrue(result.connected)
    }

    @Test
    fun `listAccounts returns mock accounts`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val accounts = adapter.listAccounts(creds)
        assertTrue(accounts.isNotEmpty())
        assertTrue(accounts.any { it.accountType == AccountType.MARGIN })
        assertTrue(accounts.all { it.brokerType == BrokerType.IBKR })
    }

    @Test
    fun `getBalances returns non-null values`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val balance = adapter.getBalances(creds, "DU1234567")
        assertNotNull(balance.totalEquity)
        assertNotNull(balance.buyingPower)
        assertTrue(balance.cashBalances.isNotEmpty())
    }

    @Test
    fun `getPositions returns mock positions`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val positions = adapter.getPositions(creds, "DU1234567")
        assertTrue(positions.isNotEmpty())
        assertTrue(positions.all { it.quantity > BigDecimal.ZERO })
        assertTrue(positions.all { it.currency == "USD" })
    }

    @Test
    fun `getActivities returns mock activities`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val activities = adapter.getActivities(creds, "DU1234567", LocalDate.now().minusDays(30), LocalDate.now())
        assertTrue(activities.isNotEmpty())
    }

    @Test
    fun `getOrders returns mock orders`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val orders = adapter.getOrders(creds, "DU1234567")
        assertTrue(orders.isNotEmpty())
    }

    @Test
    fun `placeOrder returns mock result`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val request = OrderRequest(
            symbol = "AAPL", action = OrderAction.BUY, quantity = BigDecimal.TEN,
            orderType = OrderType.LIMIT, limitPrice = BigDecimal("180.00")
        )
        val result = adapter.placeOrder(creds, "DU1234567", request)
        assertNotNull(result.brokerOrderId)
        assertEquals(OrderStatus.SUBMITTED, result.status)
    }

    @Test
    fun `cancelOrder returns success`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val result = adapter.cancelOrder(creds, "DU1234567", "12345")
        assertTrue(result.success)
    }

    @Test
    fun `capabilities reports IBKR features`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.IBKR, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertTrue(caps.isOfficialApi)
        assertTrue(caps.supportedOrderTypes.contains(OrderType.MARKET))
        assertTrue(caps.supportedOrderTypes.contains(OrderType.LIMIT))
    }
}
```

- [ ] **Step 2: Create FakeIbkrAdapter**

```kotlin
// adapter/ibkr/FakeIbkrAdapter.kt
package com.portfolio.brokergateway.adapter.ibkr

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
import kotlin.random.Random

@Component
@Profile("dev", "local", "test")
class FakeIbkrAdapter : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val orderIdCounter = AtomicInteger(10000)

    override val brokerType = BrokerType.IBKR

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        log.info("FakeIbkrAdapter: validating connection")
        return ConnectionValidationResult(connected = true, message = "Fake IBKR connection OK")
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials {
        return credentials
    }

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        return listOf(
            UnifiedAccount(
                accountId = "DU1234567",
                accountNumber = "DU1234567",
                accountName = "Paper Trading",
                accountType = AccountType.MARGIN,
                currency = "USD",
                brokerType = BrokerType.IBKR,
                status = "ACTIVE"
            ),
            UnifiedAccount(
                accountId = "DU7654321",
                accountNumber = "DU7654321",
                accountName = "RRSP",
                accountType = AccountType.RRSP,
                currency = "CAD",
                brokerType = BrokerType.IBKR,
                status = "ACTIVE"
            )
        )
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        return UnifiedBalance(
            accountId = accountId,
            totalEquity = BigDecimal("250000.00"),
            totalValue = BigDecimal("220000.00"),
            cashBalances = listOf(
                CashBalance(currency = "USD", amount = BigDecimal("30000.00")),
                CashBalance(currency = "CAD", amount = BigDecimal("5000.00"))
            ),
            buyingPower = BigDecimal("150000.00"),
            currency = "USD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        data class MockPos(val sym: String, val name: String, val qty: Int, val cost: Double, val price: Double, val type: InstrumentType)
        val positions = listOf(
            MockPos("SPY", "SPDR S&P 500 ETF", 100, 430.50, 450.25, InstrumentType.ETF),
            MockPos("AAPL", "Apple Inc", 50, 170.00, 185.50, InstrumentType.STOCK),
            MockPos("MSFT", "Microsoft Corp", 30, 380.00, 420.75, InstrumentType.STOCK),
            MockPos("QQQ", "Invesco QQQ Trust", 75, 360.00, 382.10, InstrumentType.ETF),
            MockPos("NVDA", "NVIDIA Corp", 20, 750.00, 880.00, InstrumentType.STOCK)
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
                symbol = p.sym, symbolId = Random.nextInt(1000, 99999).toString(),
                securityName = p.name, instrumentType = p.type,
                quantity = qty, averageCost = cost, currentPrice = price,
                currentValue = value, totalPnl = pnl, totalPnlPercent = pnlPct,
                currency = "USD"
            )
        }
    }

    override fun getActivities(
        credentials: BrokerCredentials, accountId: String,
        startDate: LocalDate?, endDate: LocalDate?
    ): List<UnifiedActivity> {
        val now = LocalDate.now()
        return listOf(
            UnifiedActivity(externalId = "T001", type = ActivityType.BUY, symbol = "AAPL",
                description = "Buy 50 AAPL", quantity = BigDecimal(50), price = BigDecimal("170.00"),
                amount = BigDecimal("-8500.00"), fee = BigDecimal("-1.00"), currency = "USD",
                tradeDate = now.minusDays(15), settlementDate = now.minusDays(13), optionType = null),
            UnifiedActivity(externalId = "T002", type = ActivityType.DIVIDEND, symbol = "SPY",
                description = "Dividend SPY", quantity = null, price = null,
                amount = BigDecimal("165.00"), fee = BigDecimal.ZERO, currency = "USD",
                tradeDate = now.minusDays(7), settlementDate = now.minusDays(5), optionType = null),
            UnifiedActivity(externalId = "T003", type = ActivityType.SELL, symbol = "TSLA",
                description = "Sell 10 TSLA", quantity = BigDecimal(10), price = BigDecimal("175.00"),
                amount = BigDecimal("1750.00"), fee = BigDecimal("-1.00"), currency = "USD",
                tradeDate = now.minusDays(3), settlementDate = now.minusDays(1), optionType = null),
            UnifiedActivity(externalId = "T004", type = ActivityType.TRANSFER_IN, symbol = null,
                description = "Wire deposit", quantity = null, price = null,
                amount = BigDecimal("10000.00"), fee = BigDecimal.ZERO, currency = "USD",
                tradeDate = now.minusDays(20), settlementDate = now.minusDays(20), optionType = null)
        )
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val now = OffsetDateTime.now()
        return listOf(
            UnifiedOrder(brokerOrderId = "9001", symbol = "AAPL", action = OrderAction.BUY,
                orderType = OrderType.LIMIT, timeInForce = TimeInForce.DAY,
                totalQuantity = BigDecimal(50), filledQuantity = BigDecimal(50),
                executionPrice = BigDecimal("170.00"), limitPrice = BigDecimal("171.00"),
                stopPrice = null, status = OrderStatus.FILLED, currency = "USD",
                submittedAt = now.minusDays(15), filledAt = now.minusDays(15)),
            UnifiedOrder(brokerOrderId = "9002", symbol = "GOOG", action = OrderAction.BUY,
                orderType = OrderType.LIMIT, timeInForce = TimeInForce.GTC,
                totalQuantity = BigDecimal(10), filledQuantity = null,
                executionPrice = null, limitPrice = BigDecimal("155.00"),
                stopPrice = null, status = OrderStatus.SUBMITTED, currency = "USD",
                submittedAt = now.minusDays(1), filledAt = null)
        )
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        val orderId = orderIdCounter.incrementAndGet()
        log.info("FakeIbkrAdapter: placed {} {} order for {} {} @ {}",
            request.orderType, request.action, request.quantity, request.symbol, request.limitPrice ?: "MKT")
        return OrderResult(
            brokerOrderId = orderId.toString(),
            status = OrderStatus.SUBMITTED,
            message = "Fake order submitted"
        )
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        log.info("FakeIbkrAdapter: cancelled order {}", brokerOrderId)
        return CancelResult(success = true, message = "Fake order cancelled")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.IBKR,
            supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT),
            supportsOptionPositions = true,
            supportsFractionalShares = true,
            supportsRealTimeData = true,
            supportsHistoricalActivities = true,
            activityHistoryDepth = "Unlimited (via Flex Reports)",
            orderRateLimit = "50 msg/sec",
            isOfficialApi = true,
            notes = "FakeIbkrAdapter — dev/test mock"
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/FakeIbkrAdapter.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/ibkr/FakeIbkrAdapterTest.kt
git commit -m "feat(broker-gateway): add FakeIbkrAdapter for dev/test with realistic mock data"
```

---

## Task 4: Real IbkrAdapter Shell and Connection Manager

The real adapter and connection manager. The connection manager follows the exponential backoff pattern from market-data's `IbkrConnectionManager`. The adapter delegates to `IbkrAccountClient` (Task 5) for TWS operations.

Since the TWS API JAR isn't available yet, this task creates the structure with the TWS client abstracted behind an interface — the same pattern market-data uses (`IbkrClient` interface + `FakeIbkrClient`).

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAccountClient.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrConnectionManager.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAdapter.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAdapterTest.kt`

- [ ] **Step 1: Create IbkrAccountClient interface**

```kotlin
// adapter/ibkr/IbkrAccountClient.kt
package com.portfolio.brokergateway.adapter.ibkr

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

interface IbkrAccountClient {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean

    fun getManagedAccounts(): List<String>
    fun getAccountSummary(accountId: String): Map<String, String>
    fun getPositions(): List<IbkrPosition>
    fun getOpenOrders(): List<IbkrOrder>
    fun getCompletedOrders(): List<IbkrOrder>
    fun getExecutions(accountId: String): List<IbkrExecution>
    fun placeOrder(accountId: String, contract: IbkrContract, order: IbkrOrderSpec): Int
    fun cancelOrder(orderId: Int)
}

data class IbkrPosition(
    val accountId: String,
    val symbol: String,
    val secType: String,
    val exchange: String,
    val currency: String,
    val conId: Int,
    val quantity: BigDecimal,
    val averageCost: BigDecimal,
    val marketPrice: BigDecimal?,
    val marketValue: BigDecimal?,
    val unrealizedPnl: BigDecimal?,
    val strike: BigDecimal? = null,
    val expiry: LocalDate? = null,
    val right: String? = null
)

data class IbkrOrder(
    val orderId: Int,
    val symbol: String,
    val secType: String,
    val action: String,
    val orderType: String,
    val totalQuantity: BigDecimal,
    val filledQuantity: BigDecimal?,
    val limitPrice: BigDecimal?,
    val auxPrice: BigDecimal?,
    val status: String,
    val timeInForce: String?,
    val avgFillPrice: BigDecimal?,
    val currency: String?,
    val submittedAt: OffsetDateTime?,
    val filledAt: OffsetDateTime?
)

data class IbkrExecution(
    val execId: String,
    val symbol: String,
    val secType: String,
    val side: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val commission: BigDecimal?,
    val currency: String,
    val time: OffsetDateTime,
    val accountId: String
)

data class IbkrContract(
    val symbol: String,
    val secType: String = "STK",
    val exchange: String = "SMART",
    val currency: String = "USD"
)

data class IbkrOrderSpec(
    val action: String,
    val orderType: String,
    val totalQuantity: BigDecimal,
    val limitPrice: BigDecimal? = null,
    val auxPrice: BigDecimal? = null,
    val timeInForce: String = "DAY"
)
```

- [ ] **Step 2: Create IbkrConnectionManager**

```kotlin
// adapter/ibkr/IbkrConnectionManager.kt
package com.portfolio.brokergateway.adapter.ibkr

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class IbkrConnectionManager(
    private val client: IbkrAccountClient,
    private val config: IbkrConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val isHealthy = AtomicBoolean(false)
    private var reconnectDelayMs = config.reconnectDelayMs

    fun start() {
        log.info("IbkrConnectionManager: starting connection to {}:{}", config.host, config.port)
        connectWithRetry()
    }

    fun isHealthy(): Boolean = isHealthy.get()

    fun shutdown() {
        log.info("IbkrConnectionManager: shutting down")
        try {
            client.disconnect()
            executor.shutdownNow()
        } catch (e: Exception) {
            log.error("Error during shutdown", e)
        }
    }

    private fun connectWithRetry() {
        executor.execute {
            try {
                client.connect()
                if (client.isConnected()) {
                    log.info("IbkrConnectionManager: connected")
                    isHealthy.set(true)
                    reconnectDelayMs = config.reconnectDelayMs
                } else {
                    log.warn("IbkrConnectionManager: connection failed, scheduling retry")
                    scheduleReconnect()
                }
            } catch (e: Exception) {
                log.error("IbkrConnectionManager: connection error", e)
                isHealthy.set(false)
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        isHealthy.set(false)
        log.info("IbkrConnectionManager: retry in {}ms", reconnectDelayMs)
        executor.schedule({ connectWithRetry() }, reconnectDelayMs, TimeUnit.MILLISECONDS)
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(config.maxReconnectDelayMs)
    }
}
```

- [ ] **Step 3: Write IbkrAdapter test**

```kotlin
// test: adapter/ibkr/IbkrAdapterTest.kt
package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IbkrAdapterTest {

    private val client = mockk<IbkrAccountClient>()
    private val config = IbkrConfig(enabled = true, host = "127.0.0.1", port = 4002, clientId = 2)
    private val adapter = IbkrAdapter(client, config)

    @Test
    fun `brokerType is IBKR`() {
        assertEquals(BrokerType.IBKR, adapter.brokerType)
    }

    @Test
    fun `validateConnection checks client connection state`() {
        every { client.isConnected() } returns true
        val result = adapter.validateConnection(BrokerCredentials.IbkrCredentials("127.0.0.1", 4002, 2))
        assertTrue(result.connected)
    }

    @Test
    fun `listAccounts delegates to client`() {
        every { client.getManagedAccounts() } returns listOf("U1234567", "U7654321")
        val creds = BrokerCredentials.IbkrCredentials("127.0.0.1", 4002, 2)
        val accounts = adapter.listAccounts(creds)
        assertEquals(2, accounts.size)
        assertEquals("U1234567", accounts[0].accountId)
        assertEquals(BrokerType.IBKR, accounts[0].brokerType)
    }

    @Test
    fun `getPositions maps IbkrPosition to UnifiedPosition`() {
        val ibkrPositions = listOf(
            IbkrPosition(accountId = "U123", symbol = "AAPL", secType = "STK", exchange = "SMART",
                currency = "USD", conId = 265598, quantity = BigDecimal(50),
                averageCost = BigDecimal("170.00"), marketPrice = BigDecimal("185.00"),
                marketValue = BigDecimal("9250.00"), unrealizedPnl = BigDecimal("750.00"))
        )
        every { client.getPositions() } returns ibkrPositions

        val creds = BrokerCredentials.IbkrCredentials("127.0.0.1", 4002, 2)
        val positions = adapter.getPositions(creds, "U123")
        assertEquals(1, positions.size)
        assertEquals("AAPL", positions[0].symbol)
        assertEquals(InstrumentType.STOCK, positions[0].instrumentType)
        assertEquals(BigDecimal(50), positions[0].quantity)
        assertEquals(BigDecimal("750.00"), positions[0].totalPnl)
    }

    @Test
    fun `placeOrder builds contract and order spec`() {
        every { client.placeOrder(any(), any(), any()) } returns 12345
        val creds = BrokerCredentials.IbkrCredentials("127.0.0.1", 4002, 2)
        val request = OrderRequest(symbol = "AAPL", action = OrderAction.BUY,
            quantity = BigDecimal(10), orderType = OrderType.LIMIT,
            limitPrice = BigDecimal("180.00"), timeInForce = TimeInForce.DAY)
        val result = adapter.placeOrder(creds, "U123", request)
        assertEquals("12345", result.brokerOrderId)
        assertEquals(OrderStatus.SUBMITTED, result.status)
        verify { client.placeOrder("U123", match { it.symbol == "AAPL" }, match { it.action == "BUY" && it.limitPrice == BigDecimal("180.00") }) }
    }

    @Test
    fun `cancelOrder delegates to client`() {
        every { client.cancelOrder(9001) } returns Unit
        val creds = BrokerCredentials.IbkrCredentials("127.0.0.1", 4002, 2)
        val result = adapter.cancelOrder(creds, "U123", "9001")
        assertTrue(result.success)
        verify { client.cancelOrder(9001) }
    }
}
```

- [ ] **Step 4: Create IbkrAdapter**

```kotlin
// adapter/ibkr/IbkrAdapter.kt
package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import com.portfolio.brokergateway.exception.BrokerConnectionException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@ConditionalOnProperty(prefix = "broker-gateway.ibkr", name = ["enabled"], havingValue = "true")
class IbkrAdapter(
    private val client: IbkrAccountClient,
    private val config: IbkrConfig
) : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    override val brokerType = BrokerType.IBKR

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        return ConnectionValidationResult(
            connected = client.isConnected(),
            message = if (client.isConnected()) "Connected to TWS" else "Not connected to TWS"
        )
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials = credentials

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        requireConnected()
        return client.getManagedAccounts().map { accountId ->
            UnifiedAccount(
                accountId = accountId,
                accountNumber = accountId,
                accountName = null,
                accountType = AccountType.OTHER,
                currency = null,
                brokerType = BrokerType.IBKR,
                status = "ACTIVE"
            )
        }
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        requireConnected()
        val summary = client.getAccountSummary(accountId)
        return UnifiedBalance(
            accountId = accountId,
            totalEquity = summary["NetLiquidation"]?.toBigDecimalOrNull(),
            totalValue = summary["GrossPositionValue"]?.toBigDecimalOrNull(),
            cashBalances = listOfNotNull(
                summary["TotalCashValue"]?.toBigDecimalOrNull()?.let { CashBalance("USD", it) }
            ),
            buyingPower = summary["BuyingPower"]?.toBigDecimalOrNull(),
            currency = summary["Currency"] ?: "USD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        requireConnected()
        return client.getPositions()
            .filter { it.accountId == accountId }
            .map { pos ->
                UnifiedPosition(
                    symbol = pos.symbol,
                    symbolId = pos.conId.toString(),
                    securityName = null,
                    instrumentType = IbkrDtoMappers.mapInstrumentType(pos.secType),
                    quantity = pos.quantity,
                    averageCost = pos.averageCost,
                    currentPrice = pos.marketPrice,
                    currentValue = pos.marketValue,
                    totalPnl = pos.unrealizedPnl,
                    totalPnlPercent = null,
                    currency = pos.currency,
                    strikePrice = pos.strike,
                    expirationDate = pos.expiry,
                    optionType = IbkrDtoMappers.mapOptionRight(pos.right),
                    underlyingSymbol = if (pos.secType == "OPT") pos.symbol else null
                )
            }
    }

    override fun getActivities(
        credentials: BrokerCredentials, accountId: String,
        startDate: LocalDate?, endDate: LocalDate?
    ): List<UnifiedActivity> {
        requireConnected()
        return client.getExecutions(accountId).map { exec ->
            UnifiedActivity(
                externalId = exec.execId,
                type = IbkrDtoMappers.mapActivityType(exec.side),
                symbol = exec.symbol,
                description = "${exec.side} ${exec.quantity} ${exec.symbol}",
                quantity = exec.quantity,
                price = exec.price,
                amount = exec.quantity.multiply(exec.price).let {
                    if (exec.side == "SLD" || exec.side == "SELL") it else it.negate()
                },
                fee = exec.commission?.negate(),
                currency = exec.currency,
                tradeDate = exec.time.toLocalDate(),
                settlementDate = null,
                optionType = null
            )
        }
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        requireConnected()
        val open = client.getOpenOrders()
        val completed = client.getCompletedOrders()
        return (open + completed).map { order ->
            UnifiedOrder(
                brokerOrderId = order.orderId.toString(),
                symbol = order.symbol,
                action = if (order.action == "BUY") OrderAction.BUY else OrderAction.SELL,
                orderType = when (order.orderType) {
                    "MKT" -> OrderType.MARKET
                    "LMT" -> OrderType.LIMIT
                    "STP" -> OrderType.STOP
                    "STP LMT" -> OrderType.STOP_LIMIT
                    else -> OrderType.MARKET
                },
                timeInForce = when (order.timeInForce) {
                    "GTC" -> TimeInForce.GTC
                    "IOC" -> TimeInForce.IOC
                    "FOK" -> TimeInForce.FOK
                    else -> TimeInForce.DAY
                },
                totalQuantity = order.totalQuantity,
                filledQuantity = order.filledQuantity,
                executionPrice = order.avgFillPrice,
                limitPrice = order.limitPrice,
                stopPrice = order.auxPrice,
                status = IbkrDtoMappers.mapOrderStatus(order.status),
                currency = order.currency,
                submittedAt = order.submittedAt,
                filledAt = order.filledAt
            )
        }
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        requireConnected()
        val contract = IbkrContract(
            symbol = request.symbol,
            currency = request.currency ?: "USD"
        )
        val orderSpec = IbkrOrderSpec(
            action = request.action.name,
            orderType = when (request.orderType) {
                OrderType.MARKET -> "MKT"
                OrderType.LIMIT -> "LMT"
                OrderType.STOP -> "STP"
                OrderType.STOP_LIMIT -> "STP LMT"
            },
            totalQuantity = request.quantity,
            limitPrice = request.limitPrice,
            auxPrice = request.stopPrice,
            timeInForce = request.timeInForce.name
        )
        val orderId = client.placeOrder(accountId, contract, orderSpec)
        return OrderResult(
            brokerOrderId = orderId.toString(),
            status = OrderStatus.SUBMITTED
        )
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        requireConnected()
        client.cancelOrder(brokerOrderId.toInt())
        return CancelResult(success = true, message = "Cancel request sent")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.IBKR,
            supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT),
            supportsOptionPositions = true,
            supportsFractionalShares = true,
            supportsRealTimeData = true,
            supportsHistoricalActivities = true,
            activityHistoryDepth = "Unlimited (via Flex Reports)",
            orderRateLimit = "50 msg/sec",
            isOfficialApi = true,
            notes = null
        )
    }

    private fun requireConnected() {
        if (!client.isConnected()) {
            throw BrokerConnectionException("Not connected to TWS/IB Gateway", BrokerType.IBKR)
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAccountClient.kt backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrConnectionManager.kt backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAdapter.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAdapterTest.kt
git commit -m "feat(broker-gateway): add real IbkrAdapter with IbkrAccountClient interface and connection manager"
```

---

## Task 5: Rebuild, Test, and Verify

- [ ] **Step 1: Rebuild the Docker image**

Run: `docker compose build broker-gateway-service`
Expected: Build succeeds

- [ ] **Step 2: Restart the service**

Run: `docker compose up -d broker-gateway-service`
Expected: Container starts

- [ ] **Step 3: Check health endpoint shows IBKR enabled**

Run: `curl http://localhost:8084/api/v1/gateway/health`
Expected: IBKR shows `"enabled": true, "status": "OK"` (FakeIbkrAdapter is active in dev/local profile)

- [ ] **Step 4: Run all tests**

Run: `docker run --rm -v "C:\Users\SaurabhBilakhia\Documents\POC\pc\backend:/work" -w //work/broker-gateway gradle:8.10-jdk21-alpine gradle test --no-daemon`
Expected: All tests pass (previous 18 + new ~20 = ~38 total)

- [ ] **Step 5: Commit any fixes**

---

## Task 6: Update Documentation

**Files:**
- Modify: `docs/reference/configurations.md`
- Modify: `docs/reference/backend-services.md`

- [ ] **Step 1: Add IBKR env vars to configurations.md**

Add to the Broker Gateway section:
- `IBKR_GATEWAY_ENABLED` — Enable IBKR adapter (default: `false`)
- `IBKR_HOST` — TWS/IB Gateway host (shared with market-data service)
- `IBKR_PORT` — TWS/IB Gateway port (default: `4002`)
- `IBKR_GATEWAY_CLIENT_ID` — TWS client ID for gateway (default: `2`, market-data uses `1`)
- `IBKR_FLEX_TOKEN` — Flex Web Service token for historical transaction reports
- `IBKR_FLEX_QUERY_ID` — Pre-configured Flex Query ID

- [ ] **Step 2: Add IBKR adapter description to backend-services.md**

Add a subsection under Broker Gateway Service describing:
- FakeIbkrAdapter (dev/local/test) — returns realistic mock data
- IbkrAdapter (production) — connects to TWS via IbkrAccountClient interface
- IbkrConnectionManager — socket lifecycle with exponential backoff reconnection
- IbkrDtoMappers — normalization for account types, instrument types, order statuses, activity types

- [ ] **Step 3: Commit**

```bash
git add docs/reference/
git commit -m "docs: add IBKR adapter documentation to reference files"
```

---

## Verification Checklist

1. All unit tests pass (~38 tests)
2. Docker image builds successfully
3. Service starts with FakeIbkrAdapter active (dev profile)
4. `GET /api/v1/gateway/health` shows IBKR as enabled
5. `POST /api/v1/gateway/connections` with IBKR credentials creates a connection
6. `GET /api/v1/gateway/connections/{id}/accounts/{accId}/positions` returns mock positions
7. `POST /api/v1/gateway/connections/{id}/accounts/{accId}/orders` returns mock order result
8. Documentation updated

## Future Work (not in this plan)

- **Real TWS client implementation**: Add vendored `TwsApi.jar`, implement `IbkrAccountClient` with `EClientSocket`/`EWrapper`, `CompletableFuture` bridges for async→sync
- **Flex Report client**: HTTP client for `https://gdcdyn.interactivebrokers.com/Universal/servlet/FlexStatementService.SendRequest` for historical transactions
- **Account type enrichment**: `reqAccountSummary` with full tag list for better account type detection
