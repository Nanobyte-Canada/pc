# IBKR Gateway Connection & Market Data Streaming — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect market-data and broker-gateway services to a local IB Gateway on Windows (port 4001), implement the missing `TwsIbkrAccountClient`, add connection health indicators, and fix reconnection resilience.

**Architecture:** Two services connect to IB Gateway on the host machine via `host.docker.internal`. Market-data (clientId=1) handles real-time quotes/options streaming. Broker-gateway (clientId=2) handles account/position/order operations. Both use the TWS API (`com.interactivebrokers:tws-api:10.20.01`). A frontend badge shows IBKR connection status via WebSocket push.

**Tech Stack:** Kotlin/Spring Boot, TWS API, WebSocket, React/TypeScript, CSS custom properties

---

### Task 1: Docker Networking & Environment Configuration

**Files:**
- Modify: `docker-compose.yml:102-132` (market-data-service), `docker-compose.yml:165-200` (broker-gateway-service)
- Modify: `.env`

- [ ] **Step 1: Add `extra_hosts` to market-data-service in `docker-compose.yml`**

After line 131 (the `networks:` line of market-data-service), add `extra_hosts`. Also update the default port from 4002 to 4001:

```yaml
  market-data-service:
    build:
      context: ./backend
      dockerfile: market-data/Dockerfile
    container_name: portfolio-market-data
    environment:
      SPRING_PROFILES_ACTIVE: local
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-portfolio}
      DATABASE_USERNAME: ${POSTGRES_USER:-portfolio}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD:-portfolio}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      IBKR_HOST: ${IBKR_HOST:-}
      IBKR_PORT: ${IBKR_PORT:-4001}
      IBKR_CLIENT_ID: ${IBKR_CLIENT_ID:-1}
    ports:
      - "8082:8082"
    extra_hosts:
      - "host.docker.internal:host-gateway"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    networks:
      - portfolio-network
```

- [ ] **Step 2: Add `extra_hosts` to broker-gateway-service in `docker-compose.yml`**

Same pattern — add `extra_hosts` and update default port to 4001:

```yaml
  broker-gateway-service:
    build:
      context: ./backend
      dockerfile: broker-gateway/Dockerfile
    container_name: portfolio-broker-gateway
    environment:
      SPRING_PROFILES_ACTIVE: local
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-portfolio}
      DATABASE_USERNAME: ${POSTGRES_USER:-portfolio}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD:-portfolio}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      BROKER_ENCRYPTION_KEY: ${BROKER_ENCRYPTION_KEY:-}
      GATEWAY_API_KEY: ${GATEWAY_API_KEY:-}
      IBKR_GATEWAY_ENABLED: ${IBKR_GATEWAY_ENABLED:-false}
      IBKR_HOST: ${IBKR_HOST:-}
      IBKR_PORT: ${IBKR_PORT:-4001}
      IBKR_GATEWAY_CLIENT_ID: ${IBKR_GATEWAY_CLIENT_ID:-2}
      QUESTRADE_ENABLED: ${QUESTRADE_ENABLED:-false}
      WEALTHSIMPLE_ENABLED: ${WEALTHSIMPLE_ENABLED:-false}
    ports:
      - "8084:8084"
    extra_hosts:
      - "host.docker.internal:host-gateway"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8084/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    networks:
      - portfolio-network
```

- [ ] **Step 3: Add IBKR variables to `.env`**

Append to the existing `.env` file:

```
# IBKR Gateway Connection
IBKR_HOST=host.docker.internal
IBKR_PORT=4001
IBKR_GATEWAY_ENABLED=true
```

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml .env
git commit -m "feat(ibkr): configure Docker networking for IB Gateway on host"
```

---

### Task 2: Add TWS API Dependency to Broker Gateway

**Files:**
- Modify: `backend/broker-gateway/build.gradle.kts`

- [ ] **Step 1: Add invesdwin Maven repository and tws-api dependency**

Add the Maven repo (matches market-data's `build.gradle.kts:22`) and the dependency:

In `backend/broker-gateway/build.gradle.kts`, add after the `mavenCentral()` line (line 21):

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://invesdwin.de/repo/invesdwin-oss/") }
}
```

Add the tws-api dependency in the `dependencies` block (after the Kotlin reflect line, around line 36):

```kotlin
    // IBKR TWS API
    implementation("com.interactivebrokers:tws-api:10.20.01")
```

- [ ] **Step 2: Commit**

```bash
git add backend/broker-gateway/build.gradle.kts
git commit -m "feat(broker-gateway): add TWS API dependency"
```

---

### Task 3: Implement TwsIbkrAccountClient

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/TwsIbkrAccountClient.kt`

This is the missing concrete implementation of the `IbkrAccountClient` interface. It uses the same `EClientSocket` + `DefaultEWrapper` pattern as market-data's `TwsIbkrClient`.

- [ ] **Step 1: Create `TwsIbkrAccountClient.kt`**

```kotlin
package com.portfolio.brokergateway.adapter.ibkr

import com.ib.client.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Component
@ConditionalOnProperty(prefix = "broker-gateway.ibkr", name = ["enabled"], havingValue = "true")
class TwsIbkrAccountClient(
    private val config: IbkrConfig
) : DefaultEWrapper(), IbkrAccountClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var client: EClientSocket
    private lateinit var signal: EReaderSignal
    private val connected = AtomicBoolean(false)
    private val nextOrderId = AtomicInteger(0)

    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ibkr-gw-send").apply { isDaemon = true }
    }

    @Volatile
    private var connectionReady = CountDownLatch(1)
    private var managedAccountsFuture: CompletableFuture<List<String>>? = null

    private val pendingAccountSummary = ConcurrentHashMap<Int, CompletableFuture<Map<String, String>>>()
    private val accountSummaryAccumulators = ConcurrentHashMap<Int, ConcurrentHashMap<String, String>>()
    private val nextReqId = AtomicInteger(1)

    private var positionsFuture: CompletableFuture<List<IbkrPosition>>? = null
    private val positionsAccumulator = CopyOnWriteArrayList<IbkrPosition>()

    private var openOrdersFuture: CompletableFuture<List<IbkrOrder>>? = null
    private val openOrdersAccumulator = CopyOnWriteArrayList<IbkrOrder>()

    private var completedOrdersFuture: CompletableFuture<List<IbkrOrder>>? = null
    private val completedOrdersAccumulator = CopyOnWriteArrayList<IbkrOrder>()

    private val pendingExecutions = ConcurrentHashMap<Int, CompletableFuture<List<IbkrExecution>>>()
    private val executionAccumulators = ConcurrentHashMap<Int, CopyOnWriteArrayList<IbkrExecution>>()

    private var orderIdFuture: CompletableFuture<Int>? = null

    override fun connect() {
        if (config.host.isBlank()) {
            log.warn("IBKR host is blank, skipping connection")
            return
        }

        log.info("Connecting to IBKR at {}:{} clientId={}", config.host, config.port, config.clientId)

        connectionReady = CountDownLatch(1)

        signal = EJavaSignal()
        client = EClientSocket(this, signal)
        client.eConnect(config.host, config.port, config.clientId)

        if (!client.isConnected) {
            log.error("eConnect returned but socket not connected")
            return
        }

        val reader = EReader(client, signal)
        reader.start()

        Thread({
            while (client.isConnected) {
                signal.waitForSignal()
                try {
                    reader.processMsgs()
                } catch (e: Exception) {
                    log.error("Error processing IBKR messages", e)
                }
            }
            log.info("IBKR message processing thread exiting")
        }, "ibkr-gw-reader").apply { isDaemon = true }.start()

        if (!connectionReady.await(config.connectTimeoutMs, TimeUnit.MILLISECONDS)) {
            log.warn("Timed out waiting for nextValidId callback")
        }
    }

    override fun disconnect() {
        log.info("Disconnecting from IBKR")
        connected.set(false)
        cancelPendingRequests()
        if (::client.isInitialized && client.isConnected) {
            client.eDisconnect()
        }
    }

    override fun isConnected(): Boolean = connected.get() && (::client.isInitialized && client.isConnected)

    override fun getManagedAccounts(): List<String> {
        val future = CompletableFuture<List<String>>()
        managedAccountsFuture = future
        sendExecutor.submit {
            client.reqManagedAccts()
        }
        return try {
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.error("Failed to get managed accounts", e)
            emptyList()
        } finally {
            managedAccountsFuture = null
        }
    }

    override fun getAccountSummary(accountId: String): Map<String, String> {
        val reqId = nextReqId.getAndIncrement()
        val future = CompletableFuture<Map<String, String>>()
        val accumulator = ConcurrentHashMap<String, String>()
        pendingAccountSummary[reqId] = future
        accountSummaryAccumulators[reqId] = accumulator

        val tags = "NetLiquidation,GrossPositionValue,TotalCashValue,BuyingPower,Currency,AccountType"

        sendExecutor.submit {
            try {
                client.reqAccountSummary(reqId, "All", tags)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.error("Failed to get account summary for {}", accountId, e)
            accumulator.toMap()
        } finally {
            pendingAccountSummary.remove(reqId)
            accountSummaryAccumulators.remove(reqId)
            sendExecutor.submit {
                try { client.cancelAccountSummary(reqId) } catch (_: Exception) {}
            }
        }
    }

    override fun getPositions(): List<IbkrPosition> {
        val future = CompletableFuture<List<IbkrPosition>>()
        positionsAccumulator.clear()
        positionsFuture = future

        sendExecutor.submit {
            try {
                client.reqPositions()
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.error("Failed to get positions", e)
            positionsAccumulator.toList()
        } finally {
            positionsFuture = null
            sendExecutor.submit {
                try { client.cancelPositions() } catch (_: Exception) {}
            }
        }
    }

    override fun getOpenOrders(): List<IbkrOrder> {
        val future = CompletableFuture<List<IbkrOrder>>()
        openOrdersAccumulator.clear()
        openOrdersFuture = future

        sendExecutor.submit {
            try {
                client.reqOpenOrders()
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.error("Failed to get open orders", e)
            openOrdersAccumulator.toList()
        } finally {
            openOrdersFuture = null
        }
    }

    override fun getCompletedOrders(): List<IbkrOrder> {
        val future = CompletableFuture<List<IbkrOrder>>()
        completedOrdersAccumulator.clear()
        completedOrdersFuture = future

        sendExecutor.submit {
            try {
                client.reqCompletedOrders(false)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.error("Failed to get completed orders", e)
            completedOrdersAccumulator.toList()
        } finally {
            completedOrdersFuture = null
        }
    }

    override fun getExecutions(accountId: String): List<IbkrExecution> {
        val reqId = nextReqId.getAndIncrement()
        val future = CompletableFuture<List<IbkrExecution>>()
        val accumulator = CopyOnWriteArrayList<IbkrExecution>()
        pendingExecutions[reqId] = future
        executionAccumulators[reqId] = accumulator

        val filter = ExecutionFilter().apply {
            acctCode(accountId)
        }

        sendExecutor.submit {
            try {
                client.reqExecutions(reqId, filter)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.error("Failed to get executions for {}", accountId, e)
            accumulator.toList()
        } finally {
            pendingExecutions.remove(reqId)
            executionAccumulators.remove(reqId)
        }
    }

    override fun placeOrder(accountId: String, contract: IbkrContract, order: IbkrOrderSpec): Int {
        val orderId = getNextOrderId()

        val ibContract = Contract().apply {
            symbol(contract.symbol)
            secType(contract.secType)
            exchange(contract.exchange)
            currency(contract.currency)
            contract.conId?.let { conid(it) }
            contract.strike?.let { strike(it) }
            contract.lastTradeDateOrContractMonth?.let { lastTradeDateOrContractMonth(it) }
            contract.right?.let { right(it) }
        }

        val ibOrder = Order().apply {
            action(order.action)
            orderType(order.orderType)
            totalQuantity(Decimal.get(order.totalQuantity.toDouble()))
            account(accountId)
            order.limitPrice?.let { lmtPrice(it.toDouble()) }
            order.auxPrice?.let { auxPrice(it.toDouble()) }
            tif(order.timeInForce)
        }

        sendExecutor.submit {
            try {
                client.placeOrder(orderId, ibContract, ibOrder)
                log.info("Placed order {} for {} {} {}", orderId, order.action, order.totalQuantity, contract.symbol)
            } catch (e: Exception) {
                log.error("Failed to place order for {}", contract.symbol, e)
                throw e
            }
        }

        return orderId
    }

    override fun cancelOrder(orderId: Int) {
        sendExecutor.submit {
            try {
                client.cancelOrder(orderId, "")
                log.info("Cancelled order {}", orderId)
            } catch (e: Exception) {
                log.error("Failed to cancel order {}", orderId, e)
                throw e
            }
        }
    }

    // === EWrapper callbacks ===

    override fun nextValidId(orderId: Int) {
        log.info("Connected, nextValidId={}", orderId)
        nextOrderId.set(orderId)
        connected.set(true)
        connectionReady.countDown()
    }

    override fun managedAccounts(accountsList: String?) {
        log.info("Managed accounts: {}", accountsList)
        val accounts = accountsList?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        managedAccountsFuture?.complete(accounts)
    }

    override fun accountSummary(reqId: Int, account: String?, tag: String?, value: String?, currency: String?) {
        if (tag != null && value != null) {
            accountSummaryAccumulators[reqId]?.put(tag, value)
        }
    }

    override fun accountSummaryEnd(reqId: Int) {
        val accumulator = accountSummaryAccumulators[reqId]
        pendingAccountSummary[reqId]?.complete(accumulator?.toMap() ?: emptyMap())
    }

    override fun position(account: String?, contract: Contract?, pos: Decimal?, avgCost: Double) {
        val c = contract ?: return
        val quantity = pos?.longValue()?.let { BigDecimal.valueOf(it) } ?: return

        val rightStr = c.right()?.toString()?.takeIf { it != "None" && it != "?" }
        positionsAccumulator.add(
            IbkrPosition(
                accountId = account ?: "",
                symbol = c.symbol() ?: "",
                secType = c.secType()?.toString() ?: "STK",
                exchange = c.exchange() ?: "SMART",
                currency = c.currency() ?: "USD",
                conId = c.conid(),
                quantity = quantity,
                averageCost = BigDecimal.valueOf(avgCost),
                strike = if (c.strike() > 0) BigDecimal.valueOf(c.strike()) else null,
                expiry = c.lastTradeDateOrContractMonth(),
                right = rightStr
            )
        )
    }

    override fun positionEnd() {
        positionsFuture?.complete(positionsAccumulator.toList())
    }

    override fun openOrder(orderId: Int, contract: Contract?, order: Order?, orderState: OrderState?) {
        val c = contract ?: return
        val o = order ?: return
        val os = orderState ?: return

        openOrdersAccumulator.add(
            IbkrOrder(
                orderId = orderId,
                symbol = c.symbol() ?: "",
                secType = c.secType()?.toString() ?: "STK",
                action = o.action()?.toString() ?: "",
                orderType = o.orderType()?.toString() ?: "",
                totalQuantity = BigDecimal.valueOf(o.totalQuantity()?.longValue() ?: 0),
                filledQuantity = BigDecimal.valueOf(o.filledQuantity()?.longValue() ?: 0),
                limitPrice = if (o.lmtPrice() != Double.MAX_VALUE) BigDecimal.valueOf(o.lmtPrice()) else null,
                auxPrice = if (o.auxPrice() != Double.MAX_VALUE) BigDecimal.valueOf(o.auxPrice()) else null,
                status = os.status()?.toString() ?: "",
                timeInForce = o.tif()?.toString(),
                avgFillPrice = null,
                currency = c.currency() ?: "USD"
            )
        )
    }

    override fun openOrderEnd() {
        openOrdersFuture?.complete(openOrdersAccumulator.toList())
    }

    override fun completedOrder(contract: Contract?, order: Order?, orderState: OrderState?) {
        val c = contract ?: return
        val o = order ?: return
        val os = orderState ?: return

        completedOrdersAccumulator.add(
            IbkrOrder(
                orderId = o.orderId(),
                symbol = c.symbol() ?: "",
                secType = c.secType()?.toString() ?: "STK",
                action = o.action()?.toString() ?: "",
                orderType = o.orderType()?.toString() ?: "",
                totalQuantity = BigDecimal.valueOf(o.totalQuantity()?.longValue() ?: 0),
                filledQuantity = BigDecimal.valueOf(o.filledQuantity()?.longValue() ?: 0),
                limitPrice = if (o.lmtPrice() != Double.MAX_VALUE) BigDecimal.valueOf(o.lmtPrice()) else null,
                auxPrice = if (o.auxPrice() != Double.MAX_VALUE) BigDecimal.valueOf(o.auxPrice()) else null,
                status = os.status()?.toString() ?: "",
                timeInForce = o.tif()?.toString(),
                avgFillPrice = null,
                currency = c.currency() ?: "USD",
                filledAt = os.completedTime()?.let { parseIbkrTime(it) }
            )
        )
    }

    override fun completedOrdersEnd() {
        completedOrdersFuture?.complete(completedOrdersAccumulator.toList())
    }

    override fun execDetails(reqId: Int, contract: Contract?, execution: Execution?) {
        val c = contract ?: return
        val e = execution ?: return

        executionAccumulators[reqId]?.add(
            IbkrExecution(
                execId = e.execId() ?: "",
                symbol = c.symbol() ?: "",
                secType = c.secType()?.toString() ?: "STK",
                side = e.side()?.toString() ?: "",
                quantity = BigDecimal.valueOf(e.shares()?.longValue() ?: 0),
                price = BigDecimal.valueOf(e.price()),
                currency = c.currency() ?: "USD",
                time = parseIbkrTime(e.time() ?: "") ?: OffsetDateTime.now(ZoneOffset.UTC),
                accountId = e.acctNumber() ?: ""
            )
        )
    }

    override fun execDetailsEnd(reqId: Int) {
        val accumulator = executionAccumulators[reqId]
        pendingExecutions[reqId]?.complete(accumulator?.toList() ?: emptyList())
    }

    override fun commissionReport(commissionReport: CommissionReport?) {
        // Commission data is available here if needed in the future
    }

    override fun error(e: Exception?) {
        log.error("IBKR connection error", e)
    }

    override fun error(str: String?) {
        log.error("IBKR: {}", str)
    }

    override fun error(id: Int, errorCode: Int, errorMsg: String?, advancedOrderRejectJson: String?) {
        when (errorCode) {
            502, 504 -> {
                log.error("IBKR connection error [{}]: {}", errorCode, errorMsg)
                connected.set(false)
            }
            2104, 2106, 2158 -> log.info("IBKR [{}] {}", errorCode, errorMsg)
            else -> log.warn("IBKR error id={} code={} msg={}", id, errorCode, errorMsg)
        }
    }

    override fun connectionClosed() {
        log.warn("IBKR connection closed")
        connected.set(false)
    }

    // === Private helpers ===

    private fun getNextOrderId(): Int {
        val future = CompletableFuture<Int>()
        orderIdFuture = future
        sendExecutor.submit {
            client.reqIds(1)
        }
        return try {
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.warn("Failed to get next order ID, using local counter")
            nextOrderId.getAndIncrement()
        } finally {
            orderIdFuture = null
        }
    }

    private fun cancelPendingRequests() {
        val error = IllegalStateException("Disconnected")
        managedAccountsFuture?.completeExceptionally(error)
        positionsFuture?.completeExceptionally(error)
        openOrdersFuture?.completeExceptionally(error)
        completedOrdersFuture?.completeExceptionally(error)
        orderIdFuture?.completeExceptionally(error)
        pendingAccountSummary.values.forEach { it.completeExceptionally(error) }
        pendingExecutions.values.forEach { it.completeExceptionally(error) }
        pendingAccountSummary.clear()
        accountSummaryAccumulators.clear()
        pendingExecutions.clear()
        executionAccumulators.clear()
    }

    private fun parseIbkrTime(timeStr: String): OffsetDateTime? {
        if (timeStr.isBlank()) return null
        return try {
            OffsetDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss").withZone(ZoneOffset.UTC))
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            } catch (_: Exception) {
                null
            }
        }
    }
}
```

- [ ] **Step 2: Verify the adapter wiring in `IbkrAdapter.kt`**

Read `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/IbkrAdapter.kt` and confirm it injects `IbkrAccountClient` in its constructor (line 17). The `TwsIbkrAccountClient` bean will be auto-discovered by Spring since both classes share the same `@ConditionalOnProperty` condition. No changes needed to `IbkrAdapter.kt`.

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/ibkr/TwsIbkrAccountClient.kt
git commit -m "feat(broker-gateway): implement TwsIbkrAccountClient for real IBKR connection"
```

---

### Task 4: Fix TwsIbkrClient Reconnection

**Files:**
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/ibkr/TwsIbkrClient.kt`
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/ibkr/IbkrConnectionManager.kt`

The current `TwsIbkrClient` uses a single-shot `CountDownLatch(1)` that can't be reused. After first disconnect, `connect()` hangs forever on `connectionReady.await()` because the latch is already spent.

- [ ] **Step 1: Make `connectionReady` latch resettable in `TwsIbkrClient.kt`**

Replace the `val connectionReady = CountDownLatch(1)` field at line 28 with a `@Volatile var`:

```kotlin
    @Volatile
    private var connectionReady = CountDownLatch(1)
```

- [ ] **Step 2: Reset the latch at the beginning of `connect()`**

In the `connect()` method (line 57), add a latch reset as the first line after the blank-host check:

Replace:
```kotlin
        signal = EJavaSignal()
```

With:
```kotlin
        connectionReady = CountDownLatch(1)

        signal = EJavaSignal()
```

- [ ] **Step 3: Add disconnect cleanup for pending state in `TwsIbkrClient.kt`**

In the `connectionClosed()` method at line 453, add state cleanup so the client can reconnect cleanly:

Replace:
```kotlin
    override fun connectionClosed() {
        log.warn("TwsIbkrClient: connection closed by TWS")
        connected.set(false)
    }
```

With:
```kotlin
    override fun connectionClosed() {
        log.warn("TwsIbkrClient: connection closed by TWS")
        connected.set(false)
        pendingRequests.values.forEach { it.completeExceptionally(IllegalStateException("Connection closed")) }
        pendingRequests.clear()
        contractAccumulators.clear()
        optionParamAccumulators.clear()
        snapshotAccumulators.clear()
    }
```

- [ ] **Step 4: Add periodic health check to `IbkrConnectionManager.kt` (market-data)**

Replace the entire `IbkrConnectionManager.kt` at `backend/market-data/src/main/kotlin/com/portfolio/marketdata/ibkr/IbkrConnectionManager.kt`:

```kotlin
package com.portfolio.marketdata.ibkr

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
class IbkrConnectionManager(
    private val ibkrClient: IbkrClient
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(IbkrConnectionManager::class.java)
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ibkr-conn-mgr").apply { isDaemon = true }
    }
    private val isHealthy = AtomicBoolean(false)

    private var reconnectDelayMs = 5000L
    private val maxReconnectDelayMs = 60000L
    private val reconnectMultiplier = 2.0
    private val healthCheckIntervalSeconds = 30L

    override fun run(args: ApplicationArguments?) {
        logger.info("IbkrConnectionManager: Starting...")
        connectWithRetry()
        executor.scheduleWithFixedDelay(
            { checkHealth() },
            healthCheckIntervalSeconds,
            healthCheckIntervalSeconds,
            TimeUnit.SECONDS
        )
    }

    fun isHealthy(): Boolean = isHealthy.get()

    fun getConnectionState(): ConnectionState {
        return if (ibkrClient.isConnected()) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    }

    fun reconnect() {
        logger.info("IbkrConnectionManager: Manual reconnect requested")
        ibkrClient.disconnect()
        reconnectDelayMs = 5000L
        connectWithRetry()
    }

    private fun connectWithRetry() {
        executor.execute {
            try {
                logger.info("IbkrConnectionManager: Attempting to connect...")
                ibkrClient.connect()
                if (ibkrClient.isConnected()) {
                    logger.info("IbkrConnectionManager: Connected successfully")
                    isHealthy.set(true)
                    reconnectDelayMs = 5000L
                } else {
                    logger.warn("IbkrConnectionManager: Connection failed, will retry")
                    isHealthy.set(false)
                    scheduleReconnect()
                }
            } catch (e: Exception) {
                logger.error("IbkrConnectionManager: Connection failed with exception", e)
                isHealthy.set(false)
                scheduleReconnect()
            }
        }
    }

    private fun checkHealth() {
        val wasHealthy = isHealthy.get()
        val nowConnected = ibkrClient.isConnected()
        isHealthy.set(nowConnected)
        if (wasHealthy && !nowConnected) {
            logger.warn("IbkrConnectionManager: Lost connection, triggering reconnect")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        isHealthy.set(false)
        logger.info("IbkrConnectionManager: Scheduling reconnect in {}ms", reconnectDelayMs)
        executor.schedule({ connectWithRetry() }, reconnectDelayMs, TimeUnit.MILLISECONDS)
        reconnectDelayMs = (reconnectDelayMs * reconnectMultiplier).toLong().coerceAtMost(maxReconnectDelayMs)
    }

    fun shutdown() {
        logger.info("IbkrConnectionManager: Shutting down...")
        try {
            ibkrClient.disconnect()
            executor.shutdownNow()
        } catch (e: Exception) {
            logger.error("Error during shutdown", e)
        }
    }

    enum class ConnectionState { CONNECTED, DISCONNECTED }
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/ibkr/TwsIbkrClient.kt
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/ibkr/IbkrConnectionManager.kt
git commit -m "fix(market-data): make TwsIbkrClient reconnectable with periodic health check"
```

---

### Task 5: Market-Data Health Endpoint

**Files:**
- Create: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/IbkrHealthController.kt`

- [ ] **Step 1: Create the health controller**

```kotlin
package com.portfolio.marketdata.api.controller

import com.portfolio.marketdata.ibkr.IbkrConnectionManager
import com.portfolio.marketdata.ibkr.SubscriptionManager
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/health")
class IbkrHealthController(
    private val connectionManager: IbkrConnectionManager,
    private val subscriptionManager: SubscriptionManager
) {

    @GetMapping("/ibkr")
    fun ibkrHealth(): Map<String, Any> {
        val state = connectionManager.getConnectionState()
        return mapOf(
            "connected" to (state == IbkrConnectionManager.ConnectionState.CONNECTED),
            "service" to "market-data",
            "connectionState" to state.name,
            "activeSubscriptions" to subscriptionManager.getActiveCount(),
            "pinnedSubscriptions" to subscriptionManager.getPinnedCount()
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/api/controller/IbkrHealthController.kt
git commit -m "feat(market-data): add IBKR connection health endpoint"
```

---

### Task 6: Broker-Gateway Health Endpoint Enhancement

**Files:**
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/HealthController.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/dto/ApiDtos.kt`

The existing `HealthController` reports enabled/disabled status but not live connection state. Add an IBKR-specific endpoint.

- [ ] **Step 1: Add `IbkrHealthResponse` DTO to `ApiDtos.kt`**

Append to the end of `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/dto/ApiDtos.kt`:

```kotlin
data class IbkrHealthResponse(
    val connected: Boolean,
    val service: String = "broker-gateway",
    val connectionState: String
)
```

- [ ] **Step 2: Add IBKR health endpoint to `HealthController.kt`**

Add an import and a new endpoint method. Inject `IbkrAccountClient` optionally:

Replace the entire `HealthController.kt`:

```kotlin
package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.adapter.ibkr.IbkrAccountClient
import com.portfolio.brokergateway.api.dto.BrokerHealthResponse
import com.portfolio.brokergateway.api.dto.GatewayHealthResponse
import com.portfolio.brokergateway.api.dto.IbkrHealthResponse
import com.portfolio.brokergateway.config.AdapterRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/gateway/health")
class HealthController(
    private val adapterRegistry: AdapterRegistry,
    private val ibkrClient: IbkrAccountClient? = null
) {
    @GetMapping
    fun health(): ResponseEntity<GatewayHealthResponse> {
        val enabledBrokers = adapterRegistry.getEnabledBrokers()
        val brokerStatuses = BrokerType.entries.map { type ->
            BrokerHealthResponse(
                brokerType = type,
                enabled = type in enabledBrokers,
                status = if (type in enabledBrokers) "OK" else "DISABLED"
            )
        }
        return ResponseEntity.ok(GatewayHealthResponse(status = "UP", brokers = brokerStatuses))
    }

    @GetMapping("/{brokerType}")
    fun brokerHealth(@PathVariable brokerType: BrokerType): ResponseEntity<BrokerHealthResponse> {
        val enabledBrokers = adapterRegistry.getEnabledBrokers()
        val enabled = brokerType in enabledBrokers
        return ResponseEntity.ok(
            BrokerHealthResponse(
                brokerType = brokerType,
                enabled = enabled,
                status = if (enabled) "OK" else "DISABLED"
            )
        )
    }

    @GetMapping("/ibkr")
    fun ibkrHealth(): ResponseEntity<IbkrHealthResponse> {
        val connected = ibkrClient?.isConnected() ?: false
        return ResponseEntity.ok(
            IbkrHealthResponse(
                connected = connected,
                connectionState = if (connected) "CONNECTED" else "DISCONNECTED"
            )
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/HealthController.kt
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/dto/ApiDtos.kt
git commit -m "feat(broker-gateway): add IBKR live connection health endpoint"
```

---

### Task 7: WebSocket Connection Status Broadcast

**Files:**
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/QuoteWebSocketHandler.kt`
- Modify: `backend/market-data/src/main/kotlin/com/portfolio/marketdata/ibkr/IbkrConnectionManager.kt`

When IBKR connection state changes, broadcast a `connection_status` message to all connected WebSocket sessions.

- [ ] **Step 1: Add `broadcastConnectionStatus` method to `QuoteWebSocketHandler.kt`**

Add at the end of the class (before the closing `}`), after the `broadcastOptionQuote` method:

```kotlin
    fun broadcastConnectionStatus(connected: Boolean) {
        val json = try {
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "connection_status",
                    "connected" to connected,
                    "service" to "market-data"
                )
            )
        } catch (e: Exception) { return }
        val message = TextMessage(json)
        sessions.values.forEach { session ->
            synchronized(session) {
                try { if (session.isOpen) session.sendMessage(message) } catch (_: Exception) {}
            }
        }
    }
```

- [ ] **Step 2: Wire the connection manager to broadcast status changes**

In `IbkrConnectionManager.kt` (market-data), inject `QuoteWebSocketHandler` and call `broadcastConnectionStatus` on state changes.

Add `QuoteWebSocketHandler` as a lazy constructor parameter. Update the class:

```kotlin
@Component
class IbkrConnectionManager(
    private val ibkrClient: IbkrClient,
    @org.springframework.context.annotation.Lazy private val webSocketHandler: com.portfolio.marketdata.distribution.QuoteWebSocketHandler
) : ApplicationRunner {
```

In `connectWithRetry()`, after `isHealthy.set(true)`, add:

```kotlin
                    try { webSocketHandler.broadcastConnectionStatus(true) } catch (_: Exception) {}
```

In `checkHealth()`, after `logger.warn(...)` and before `scheduleReconnect()`, add:

```kotlin
            try { webSocketHandler.broadcastConnectionStatus(false) } catch (_: Exception) {}
```

The full updated methods:

```kotlin
    private fun connectWithRetry() {
        executor.execute {
            try {
                logger.info("IbkrConnectionManager: Attempting to connect...")
                ibkrClient.connect()
                if (ibkrClient.isConnected()) {
                    logger.info("IbkrConnectionManager: Connected successfully")
                    isHealthy.set(true)
                    reconnectDelayMs = 5000L
                    try { webSocketHandler.broadcastConnectionStatus(true) } catch (_: Exception) {}
                } else {
                    logger.warn("IbkrConnectionManager: Connection failed, will retry")
                    isHealthy.set(false)
                    scheduleReconnect()
                }
            } catch (e: Exception) {
                logger.error("IbkrConnectionManager: Connection failed with exception", e)
                isHealthy.set(false)
                scheduleReconnect()
            }
        }
    }

    private fun checkHealth() {
        val wasHealthy = isHealthy.get()
        val nowConnected = ibkrClient.isConnected()
        isHealthy.set(nowConnected)
        if (wasHealthy && !nowConnected) {
            logger.warn("IbkrConnectionManager: Lost connection, triggering reconnect")
            try { webSocketHandler.broadcastConnectionStatus(false) } catch (_: Exception) {}
            scheduleReconnect()
        }
    }
```

- [ ] **Step 3: Commit**

```bash
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/distribution/QuoteWebSocketHandler.kt
git add backend/market-data/src/main/kotlin/com/portfolio/marketdata/ibkr/IbkrConnectionManager.kt
git commit -m "feat(market-data): broadcast IBKR connection status via WebSocket"
```

---

### Task 8: Frontend WebSocket Hook — Parse Connection Status

**Files:**
- Modify: `frontend/src/hooks/useMarketDataWebSocket.ts`

- [ ] **Step 1: Add `ibkrConnected` state and parse `connection_status` messages**

Add a new state variable and handle the new message type. Update the hook:

Add after line 16 (`const [isConnected, setIsConnected] = useState(false)`):

```typescript
  const [ibkrConnected, setIbkrConnected] = useState<boolean | null>(null)
```

In the `ws.onmessage` handler (line 39-56), add a check for `connection_status` before the existing `option_quote` check:

Replace:
```typescript
    ws.onmessage = (event) => {
      try {
        const raw = JSON.parse(event.data)
        if (raw.type === 'option_quote' && raw.data) {
```

With:
```typescript
    ws.onmessage = (event) => {
      try {
        const raw = JSON.parse(event.data)
        if (raw.type === 'connection_status') {
          setIbkrConnected(raw.connected)
          return
        }
        if (raw.type === 'option_quote' && raw.data) {
```

Add `ibkrConnected` to the return object (line 136-147):

Replace:
```typescript
  return {
    isConnected,
    connect,
    disconnect,
    subscribe,
    unsubscribe,
    subscribeChain,
    unsubscribeChain,
    subscribeOption,
    unsubscribeOption,
  }
```

With:
```typescript
  return {
    isConnected,
    ibkrConnected,
    connect,
    disconnect,
    subscribe,
    unsubscribe,
    subscribeChain,
    unsubscribeChain,
    subscribeOption,
    unsubscribeOption,
  }
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/hooks/useMarketDataWebSocket.ts
git commit -m "feat(frontend): parse IBKR connection status from WebSocket"
```

---

### Task 9: Frontend IbkrConnectionBadge Component

**Files:**
- Create: `frontend/src/components/IbkrConnectionBadge.tsx`
- Create: `frontend/src/components/IbkrConnectionBadge.css`
- Modify: `frontend/src/components/layout/IconRail.tsx`

- [ ] **Step 1: Create `IbkrConnectionBadge.css`**

```css
.ibkr-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.02em;
  white-space: nowrap;
  transition: background-color 0.2s, color 0.2s;
}

.ibkr-badge--connected {
  color: var(--success, #059669);
  background: color-mix(in srgb, var(--success, #059669) 12%, transparent);
}

.ibkr-badge--connecting {
  color: var(--warning, #d97706);
  background: color-mix(in srgb, var(--warning, #d97706) 12%, transparent);
}

.ibkr-badge--disconnected {
  color: var(--destructive, #dc2626);
  background: color-mix(in srgb, var(--destructive, #dc2626) 12%, transparent);
}

.ibkr-badge__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ibkr-badge--connected .ibkr-badge__dot {
  background: var(--success, #059669);
}

.ibkr-badge--connecting .ibkr-badge__dot {
  background: var(--warning, #d97706);
  animation: ibkr-pulse 1.5s ease-in-out infinite;
}

.ibkr-badge--disconnected .ibkr-badge__dot {
  background: var(--destructive, #dc2626);
}

@keyframes ibkr-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Compact variant for sidebar */
.ibkr-badge--compact {
  padding: 3px 6px;
  font-size: 9px;
  border-radius: 4px;
}
```

- [ ] **Step 2: Create `IbkrConnectionBadge.tsx`**

```tsx
import { useMarketDataWebSocket } from '@/hooks/useMarketDataWebSocket'
import './IbkrConnectionBadge.css'

export function IbkrConnectionBadge({ compact = false }: { compact?: boolean }) {
  const { isConnected, ibkrConnected } = useMarketDataWebSocket({ autoConnect: false })

  const status = !isConnected
    ? 'disconnected'
    : ibkrConnected === true
      ? 'connected'
      : ibkrConnected === false
        ? 'disconnected'
        : 'connecting'

  const label = status === 'connected'
    ? 'IBKR'
    : status === 'connecting'
      ? 'IBKR...'
      : 'IBKR'

  return (
    <div
      className={`ibkr-badge ibkr-badge--${status}${compact ? ' ibkr-badge--compact' : ''}`}
      title={`IBKR Gateway: ${status}`}
    >
      <span className="ibkr-badge__dot" />
      <span>{label}</span>
    </div>
  )
}
```

- [ ] **Step 3: Mount the badge in `IconRail.tsx`**

Add the badge at the bottom of the icon rail, above the theme toggle. In `frontend/src/components/layout/IconRail.tsx`:

Add import at the top:

```typescript
import { IbkrConnectionBadge } from '@/components/IbkrConnectionBadge'
```

Add the badge after the spacer div (line 63 `<div className="icon-rail__spacer" />`), before the theme toggle button:

```tsx
      <div className="icon-rail__spacer" />

      <IbkrConnectionBadge compact />

      <button
        className="icon-rail__item"
        onClick={toggleTheme}
```

- [ ] **Step 4: Verify the `useMarketDataWebSocket` hook is consumed correctly**

The badge uses `autoConnect: false` because the hook is likely already auto-connecting from another component (e.g., OptionsPage or WheelChainPanel). When `autoConnect: false`, the hook returns whatever state the shared WebSocket connection produces. However, this hook creates a NEW WebSocket per instance. This means we need a shared context.

Actually, looking at the code, `useMarketDataWebSocket` creates a new WebSocket per component instance. The badge should NOT create its own connection. Instead, the badge should read from the Zustand store.

Let's adjust the approach — add `ibkrConnected` to the quote store, and have the badge read from the store.

- [ ] **Step 5: Add `ibkrConnected` to the quote store**

In `frontend/src/stores/quoteStore.ts`, add two new fields to the `QuoteState` interface (after line 12):

```typescript
interface QuoteState {
  quotes: Record<string, Quote>
  chains: Record<string, OptionsChain>
  selectedUnderlying: string | null
  ibkrConnected: boolean | null
  setQuote: (symbol: string, quote: Quote) => void
  setChain: (underlying: string, chain: OptionsChain) => void
  updateChainQuote: (underlying: string, optionQuote: OptionQuoteData) => void
  setSelectedUnderlying: (symbol: string | null) => void
  setIbkrConnected: (connected: boolean | null) => void
  clearQuotes: () => void
}
```

And add the initial value and setter in the store implementation (after `selectedUnderlying: null,`):

```typescript
  ibkrConnected: null,
```

And add the setter (after the `setSelectedUnderlying` line):

```typescript
  setIbkrConnected: (connected) => set({ ibkrConnected: connected }),
```

- [ ] **Step 6: Update `useMarketDataWebSocket.ts` to use the store for ibkrConnected**

Replace the local `ibkrConnected` state with the store:

Remove the local state line:
```typescript
  const [ibkrConnected, setIbkrConnected] = useState<boolean | null>(null)
```

Add store import:
```typescript
const setIbkrConnected = useQuoteStore((state) => state.setIbkrConnected)
```

And in the return object, remove `ibkrConnected` (it's now in the store, not the hook).

The full updated return:
```typescript
  return {
    isConnected,
    connect,
    disconnect,
    subscribe,
    unsubscribe,
    subscribeChain,
    unsubscribeChain,
    subscribeOption,
    unsubscribeOption,
  }
```

- [ ] **Step 7: Update `IbkrConnectionBadge.tsx` to read from store**

```tsx
import { useQuoteStore } from '@/stores/quoteStore'
import './IbkrConnectionBadge.css'

export function IbkrConnectionBadge({ compact = false }: { compact?: boolean }) {
  const ibkrConnected = useQuoteStore((state) => state.ibkrConnected)

  const status = ibkrConnected === true
    ? 'connected'
    : ibkrConnected === false
      ? 'disconnected'
      : 'connecting'

  const label = status === 'connected'
    ? 'IBKR'
    : status === 'connecting'
      ? 'IBKR...'
      : 'IBKR'

  return (
    <div
      className={`ibkr-badge ibkr-badge--${status}${compact ? ' ibkr-badge--compact' : ''}`}
      title={`IBKR Gateway: ${status}`}
    >
      <span className="ibkr-badge__dot" />
      <span>{label}</span>
    </div>
  )
}
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/IbkrConnectionBadge.tsx
git add frontend/src/components/IbkrConnectionBadge.css
git add frontend/src/components/layout/IconRail.tsx
git add frontend/src/stores/quoteStore.ts
git add frontend/src/hooks/useMarketDataWebSocket.ts
git commit -m "feat(frontend): add IBKR connection status badge in sidebar"
```

---

### Task 10: Build & Connection Test

**Files:** None (testing only)

- [ ] **Step 1: Ensure IB Gateway is running on port 4001 with API enabled**

Verify manually:
- IB Gateway is running
- Configure → API → Settings: "Enable ActiveX and Socket Clients" is checked
- Port is set to 4001
- "Allow connections from localhost" is checked

- [ ] **Step 2: Build and start all services**

```bash
docker compose up --build
```

- [ ] **Step 3: Check market-data service logs for IBKR connection**

```bash
docker compose logs -f market-data-service 2>&1 | grep -i ibkr
```

Expected output should include:
```
TwsIbkrClient: connecting to host.docker.internal:4001 clientId=1
TwsIbkrClient: connected, nextValidId=...
IbkrConnectionManager: Connected successfully
```

- [ ] **Step 4: Check broker-gateway service logs for IBKR connection**

```bash
docker compose logs -f broker-gateway-service 2>&1 | grep -i ibkr
```

Expected output should include:
```
Connecting to IBKR at host.docker.internal:4001 clientId=2
Connected, nextValidId=...
IBKR connection established
```

- [ ] **Step 5: Test health endpoints**

```bash
curl http://localhost:8082/api/v1/health/ibkr
```

Expected: `{"connected":true,"service":"market-data","connectionState":"CONNECTED","activeSubscriptions":0,"pinnedSubscriptions":0}`

```bash
curl http://localhost:8084/api/v1/gateway/health/ibkr
```

Expected: `{"connected":true,"service":"broker-gateway","connectionState":"CONNECTED"}`

- [ ] **Step 6: Test frontend WebSocket and badge**

Open `http://localhost:3000` in browser. Verify:
- The IBKR badge in the sidebar shows green "IBKR"
- Navigate to a page with market data (e.g., Wheel page) — verify real prices appear

- [ ] **Step 7: Test reconnection**

Stop IB Gateway. Verify:
- Market-data logs show "Lost connection, triggering reconnect"
- Badge turns red
- Restart IB Gateway → logs show reconnect success, badge turns green

---

### Task 11: Update Documentation

**Files:**
- Modify: `docs/reference/configurations.md`
- Modify: `docs/reference/backend-services.md`
- Modify: `docs/reference/infrastructure.md`
- Modify: `docs/reference/frontend-map.md`
- Modify: `docs/reference/api-endpoints.md`

- [ ] **Step 1: Update `configurations.md`**

Add IBKR environment variables section if not already documented. Add `IBKR_HOST`, `IBKR_PORT`, `IBKR_CLIENT_ID`, `IBKR_GATEWAY_ENABLED`, `IBKR_GATEWAY_CLIENT_ID` with their defaults and descriptions.

- [ ] **Step 2: Update `backend-services.md`**

Add entries for `TwsIbkrAccountClient` (broker-gateway) and the reconnection improvements to `IbkrConnectionManager` (market-data).

- [ ] **Step 3: Update `infrastructure.md`**

Document the `extra_hosts` addition for `host.docker.internal` networking and the IB Gateway prerequisites.

- [ ] **Step 4: Update `frontend-map.md`**

Add entries for `IbkrConnectionBadge.tsx`, `IbkrConnectionBadge.css`, and the `ibkrConnected` addition to `quoteStore`.

- [ ] **Step 5: Update `api-endpoints.md`**

Add entries for `GET /api/v1/health/ibkr` (market-data) and `GET /api/v1/gateway/health/ibkr` (broker-gateway).

- [ ] **Step 6: Commit**

```bash
git add docs/reference/
git commit -m "docs: update reference docs for IBKR gateway connection"
```
