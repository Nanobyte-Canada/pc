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
    private val nextOrderId = AtomicInteger(1)

    @Volatile
    private var connectionReady = CountDownLatch(1)

    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ibkr-account-send").apply { isDaemon = true }
    }

    // Request ID generator — starts high to avoid collision with order IDs
    private val nextReqId = AtomicInteger(50000)

    // Accumulators for async responses
    private val pendingRequests = ConcurrentHashMap<Int, CompletableFuture<Any>>()
    private val managedAccountsList = CopyOnWriteArrayList<String>()
    private val accountSummaryAccumulators = ConcurrentHashMap<Int, ConcurrentHashMap<String, String>>()
    private val positionAccumulators = ConcurrentHashMap<Int, CopyOnWriteArrayList<IbkrPosition>>()
    private val openOrderAccumulators = ConcurrentHashMap<Int, CopyOnWriteArrayList<IbkrOrder>>()
    private val completedOrderAccumulators = ConcurrentHashMap<Int, CopyOnWriteArrayList<IbkrOrder>>()
    private val executionAccumulators = ConcurrentHashMap<Int, CopyOnWriteArrayList<IbkrExecution>>()

    // Shared position accumulator for the position() / positionEnd() callback pair
    // (reqPositions uses no reqId — there is a single global stream)
    private val globalPositionAccumulator = CopyOnWriteArrayList<IbkrPosition>()

    @Volatile
    private var globalPositionFuture: CompletableFuture<Any>? = null

    // ==================== IbkrAccountClient implementation ====================

    override fun connect() {
        log.info("TwsIbkrAccountClient: connecting to {}:{} clientId={}", config.host, config.port, config.clientId)

        if (config.host.isBlank()) {
            log.warn("TwsIbkrAccountClient: host is blank, skipping connection")
            return
        }

        connectionReady = CountDownLatch(1)

        signal = EJavaSignal()
        client = EClientSocket(this, signal)
        client.eConnect(config.host, config.port, config.clientId)

        if (!client.isConnected) {
            log.error("TwsIbkrAccountClient: eConnect returned but not connected")
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
                    log.error("TwsIbkrAccountClient: error processing messages", e)
                }
            }
            log.info("TwsIbkrAccountClient: message processing thread exiting")
        }, "ibkr-account-reader").apply { isDaemon = true }.start()

        if (!connectionReady.await(config.connectTimeoutMs, TimeUnit.MILLISECONDS)) {
            log.warn("TwsIbkrAccountClient: timed out waiting for nextValidId callback")
        }
    }

    override fun disconnect() {
        log.info("TwsIbkrAccountClient: disconnecting")
        connected.set(false)

        // Fail all pending requests
        pendingRequests.values.forEach { it.completeExceptionally(IllegalStateException("Disconnected")) }
        pendingRequests.clear()

        // Clear accumulators
        accountSummaryAccumulators.clear()
        positionAccumulators.clear()
        openOrderAccumulators.clear()
        completedOrderAccumulators.clear()
        executionAccumulators.clear()
        globalPositionAccumulator.clear()
        globalPositionFuture?.completeExceptionally(IllegalStateException("Disconnected"))
        globalPositionFuture = null

        if (::client.isInitialized && client.isConnected) {
            client.eDisconnect()
        }
    }

    override fun isConnected(): Boolean = connected.get() && (::client.isInitialized && client.isConnected)

    override fun getManagedAccounts(): List<String> {
        // managedAccounts callback fires automatically on connect; return cached list
        if (managedAccountsList.isNotEmpty()) {
            return managedAccountsList.toList()
        }
        // If somehow not yet received, wait briefly
        Thread.sleep(500)
        return managedAccountsList.toList()
    }

    override fun getAccountSummary(accountId: String): Map<String, String> {
        val reqId = nextReqId.getAndIncrement()
        val future = CompletableFuture<Any>()
        val accumulator = ConcurrentHashMap<String, String>()
        accountSummaryAccumulators[reqId] = accumulator
        pendingRequests[reqId] = future

        val tags = "NetLiquidation,TotalCashValue,GrossPositionValue,BuyingPower,Currency,AccountType,AccountAlias"

        sendExecutor.submit {
            try {
                client.reqAccountSummary(reqId, "All", tags)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS) as Map<String, String>
        } catch (e: TimeoutException) {
            log.warn("TwsIbkrAccountClient: account summary timed out for {}", accountId)
            accumulator.toMap()
        } catch (e: Exception) {
            log.error("TwsIbkrAccountClient: account summary failed for {}", accountId, e)
            emptyMap()
        } finally {
            pendingRequests.remove(reqId)
            accountSummaryAccumulators.remove(reqId)
            sendExecutor.submit {
                try {
                    client.cancelAccountSummary(reqId)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun getPositions(): List<IbkrPosition> {
        val future = CompletableFuture<Any>()
        globalPositionAccumulator.clear()
        globalPositionFuture = future

        sendExecutor.submit {
            try {
                client.reqPositions()
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS) as List<IbkrPosition>
        } catch (e: TimeoutException) {
            log.warn("TwsIbkrAccountClient: positions request timed out")
            globalPositionAccumulator.toList()
        } catch (e: Exception) {
            log.error("TwsIbkrAccountClient: positions request failed", e)
            emptyList()
        } finally {
            globalPositionFuture = null
            sendExecutor.submit {
                try {
                    client.cancelPositions()
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun getOpenOrders(): List<IbkrOrder> {
        val future = CompletableFuture<Any>()
        val accumulator = CopyOnWriteArrayList<IbkrOrder>()
        // openOrder/openOrderEnd uses no reqId; use a sentinel key
        val sentinelKey = -1
        openOrderAccumulators[sentinelKey] = accumulator
        pendingRequests[sentinelKey] = future

        sendExecutor.submit {
            try {
                client.reqAllOpenOrders()
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS) as List<IbkrOrder>
        } catch (e: TimeoutException) {
            log.warn("TwsIbkrAccountClient: open orders request timed out")
            accumulator.toList()
        } catch (e: Exception) {
            log.error("TwsIbkrAccountClient: open orders request failed", e)
            emptyList()
        } finally {
            pendingRequests.remove(sentinelKey)
            openOrderAccumulators.remove(sentinelKey)
        }
    }

    override fun getCompletedOrders(): List<IbkrOrder> {
        val future = CompletableFuture<Any>()
        val accumulator = CopyOnWriteArrayList<IbkrOrder>()
        val sentinelKey = -2
        completedOrderAccumulators[sentinelKey] = accumulator
        pendingRequests[sentinelKey] = future

        sendExecutor.submit {
            try {
                client.reqCompletedOrders(false)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS) as List<IbkrOrder>
        } catch (e: TimeoutException) {
            log.warn("TwsIbkrAccountClient: completed orders request timed out")
            accumulator.toList()
        } catch (e: Exception) {
            log.error("TwsIbkrAccountClient: completed orders request failed", e)
            emptyList()
        } finally {
            pendingRequests.remove(sentinelKey)
            completedOrderAccumulators.remove(sentinelKey)
        }
    }

    override fun getExecutions(accountId: String): List<IbkrExecution> {
        val reqId = nextReqId.getAndIncrement()
        val future = CompletableFuture<Any>()
        val accumulator = CopyOnWriteArrayList<IbkrExecution>()
        executionAccumulators[reqId] = accumulator
        pendingRequests[reqId] = future

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
            @Suppress("UNCHECKED_CAST")
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS) as List<IbkrExecution>
        } catch (e: TimeoutException) {
            log.warn("TwsIbkrAccountClient: executions request timed out for {}", accountId)
            accumulator.toList()
        } catch (e: Exception) {
            log.error("TwsIbkrAccountClient: executions request failed for {}", accountId, e)
            emptyList()
        } finally {
            pendingRequests.remove(reqId)
            executionAccumulators.remove(reqId)
        }
    }

    override fun placeOrder(accountId: String, contract: IbkrContract, order: IbkrOrderSpec): Int {
        val future = CompletableFuture<Int>()

        sendExecutor.submit {
            try {
                val orderId = nextOrderId.getAndIncrement()

                val ibkrContract = Contract().apply {
                    symbol(contract.symbol)
                    secType(contract.secType)
                    exchange(contract.exchange)
                    currency(contract.currency)
                    contract.conId?.let { conid(it) }
                    contract.strike?.let { strike(it) }
                    contract.lastTradeDateOrContractMonth?.let { lastTradeDateOrContractMonth(it) }
                    contract.right?.let { right(it) }
                }

                val ibkrOrder = Order().apply {
                    action(order.action)
                    orderType(order.orderType)
                    totalQuantity(Decimal.get(order.totalQuantity.toDouble()))
                    order.limitPrice?.let { lmtPrice(it.toDouble()) }
                    order.auxPrice?.let { auxPrice(it.toDouble()) }
                    tif(order.timeInForce)
                    account(accountId)
                }

                client.placeOrder(orderId, ibkrContract, ibkrOrder)
                log.info("TwsIbkrAccountClient: placed order {} for {} {} {}",
                    orderId, order.action, order.totalQuantity, contract.symbol)
                future.complete(orderId)
            } catch (e: Exception) {
                log.error("TwsIbkrAccountClient: failed to place order", e)
                future.completeExceptionally(e)
            }
        }

        return try {
            future.get(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            throw RuntimeException("Failed to place order: ${e.message}", e)
        }
    }

    override fun cancelOrder(orderId: Int) {
        sendExecutor.submit {
            try {
                client.cancelOrder(orderId, OrderCancel())
                log.info("TwsIbkrAccountClient: cancel request sent for order {}", orderId)
            } catch (e: Exception) {
                log.error("TwsIbkrAccountClient: failed to cancel order {}", orderId, e)
            }
        }
    }

    // ==================== EWrapper callbacks ====================

    override fun nextValidId(orderId: Int) {
        log.info("TwsIbkrAccountClient: connected, nextValidId={}", orderId)
        nextOrderId.set(orderId)
        connected.set(true)
        connectionReady.countDown()
    }

    override fun managedAccounts(accountsList: String?) {
        log.info("TwsIbkrAccountClient: managed accounts: {}", accountsList)
        managedAccountsList.clear()
        accountsList?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.let { managedAccountsList.addAll(it) }
    }

    override fun accountSummary(reqId: Int, account: String?, tag: String?, value: String?, currency: String?) {
        val accumulator = accountSummaryAccumulators[reqId] ?: return
        if (tag != null && value != null) {
            accumulator[tag] = value
        }
    }

    override fun accountSummaryEnd(reqId: Int) {
        val accumulator = accountSummaryAccumulators[reqId] ?: return
        @Suppress("UNCHECKED_CAST")
        val future = pendingRequests[reqId] as? CompletableFuture<Any>
        future?.complete(accumulator.toMap())
    }

    override fun position(account: String?, contract: Contract?, pos: Decimal?, avgCost: Double) {
        val c = contract ?: return
        val quantity = pos?.longValue()?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
        if (quantity.signum() == 0) return // skip zero positions

        val ibkrPosition = IbkrPosition(
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
            right = c.right()?.toString()?.takeIf { it != "None" && it != "?" }
        )

        globalPositionAccumulator.add(ibkrPosition)
    }

    override fun positionEnd() {
        @Suppress("UNCHECKED_CAST")
        val future = globalPositionFuture as? CompletableFuture<Any>
        future?.complete(globalPositionAccumulator.toList())
    }

    override fun openOrder(orderId: Int, contract: Contract?, order: Order?, orderState: OrderState?) {
        val c = contract ?: return
        val o = order ?: return
        val state = orderState ?: return

        val ibkrOrder = IbkrOrder(
            orderId = orderId,
            symbol = c.symbol() ?: "",
            secType = c.secType()?.toString() ?: "STK",
            action = o.action()?.toString() ?: "",
            orderType = o.orderType()?.toString() ?: "",
            totalQuantity = BigDecimal.valueOf(o.totalQuantity()?.longValue() ?: 0),
            filledQuantity = BigDecimal.valueOf(o.filledQuantity()?.longValue() ?: 0),
            limitPrice = if (o.lmtPrice() != Double.MAX_VALUE) BigDecimal.valueOf(o.lmtPrice()) else null,
            auxPrice = if (o.auxPrice() != Double.MAX_VALUE) BigDecimal.valueOf(o.auxPrice()) else null,
            status = state.status()?.toString() ?: "Unknown",
            timeInForce = o.tif()?.toString(),
            currency = c.currency() ?: "USD",
            submittedAt = null,
            filledAt = if (state.completedTime()?.isNotBlank() == true) parseIbkrTime(state.completedTime()) else null
        )

        // Add to the sentinel accumulator used by getOpenOrders
        openOrderAccumulators[-1]?.add(ibkrOrder)
    }

    override fun openOrderEnd() {
        @Suppress("UNCHECKED_CAST")
        val future = pendingRequests[-1] as? CompletableFuture<Any>
        val accumulator = openOrderAccumulators[-1]
        future?.complete(accumulator?.toList() ?: emptyList<IbkrOrder>())
    }

    override fun completedOrder(contract: Contract?, order: Order?, orderState: OrderState?) {
        val c = contract ?: return
        val o = order ?: return
        val state = orderState ?: return

        val ibkrOrder = IbkrOrder(
            orderId = o.orderId(),
            symbol = c.symbol() ?: "",
            secType = c.secType()?.toString() ?: "STK",
            action = o.action()?.toString() ?: "",
            orderType = o.orderType()?.toString() ?: "",
            totalQuantity = BigDecimal.valueOf(o.totalQuantity()?.longValue() ?: 0),
            filledQuantity = BigDecimal.valueOf(o.filledQuantity()?.longValue() ?: 0),
            limitPrice = if (o.lmtPrice() != Double.MAX_VALUE) BigDecimal.valueOf(o.lmtPrice()) else null,
            auxPrice = if (o.auxPrice() != Double.MAX_VALUE) BigDecimal.valueOf(o.auxPrice()) else null,
            status = state.status()?.toString() ?: "Filled",
            timeInForce = o.tif()?.toString(),
            currency = c.currency() ?: "USD",
            submittedAt = null,
            filledAt = if (state.completedTime()?.isNotBlank() == true) parseIbkrTime(state.completedTime()) else null
        )

        completedOrderAccumulators[-2]?.add(ibkrOrder)
    }

    override fun completedOrdersEnd() {
        @Suppress("UNCHECKED_CAST")
        val future = pendingRequests[-2] as? CompletableFuture<Any>
        val accumulator = completedOrderAccumulators[-2]
        future?.complete(accumulator?.toList() ?: emptyList<IbkrOrder>())
    }

    override fun execDetails(reqId: Int, contract: Contract?, execution: Execution?) {
        val c = contract ?: return
        val exec = execution ?: return

        val ibkrExecution = IbkrExecution(
            execId = exec.execId() ?: "",
            symbol = c.symbol() ?: "",
            secType = c.secType()?.toString() ?: "STK",
            side = exec.side() ?: "",
            quantity = BigDecimal.valueOf(exec.shares()?.longValue() ?: 0),
            price = BigDecimal.valueOf(exec.price()),
            commission = null, // commission comes separately via commissionReport callback
            currency = c.currency() ?: "USD",
            time = parseIbkrTime(exec.time()) ?: OffsetDateTime.now(ZoneOffset.UTC),
            accountId = exec.acctNumber() ?: ""
        )

        executionAccumulators[reqId]?.add(ibkrExecution)
    }

    override fun execDetailsEnd(reqId: Int) {
        val accumulator = executionAccumulators[reqId] ?: return
        @Suppress("UNCHECKED_CAST")
        val future = pendingRequests[reqId] as? CompletableFuture<Any>
        future?.complete(accumulator.toList())
    }

    override fun error(e: Exception?) {
        log.error("TwsIbkrAccountClient: connection error", e)
    }

    override fun error(str: String?) {
        log.error("TwsIbkrAccountClient: {}", str)
    }

    override fun error(id: Int, errorTime: Long, errorCode: Int, errorMsg: String?, advancedOrderRejectJson: String?) {
        when (errorCode) {
            // Connection-level errors
            502, 504 -> {
                log.error("TwsIbkrAccountClient: connection error [{}]: {}", errorCode, errorMsg)
                connected.set(false)
            }
            // Market data farm messages (informational)
            2104, 2106, 2158 -> log.info("TwsIbkrAccountClient: [{}] {}", errorCode, errorMsg)
            else -> {
                log.warn("TwsIbkrAccountClient: error id={} code={} msg={}", id, errorCode, errorMsg)
                if (id > 0) {
                    @Suppress("UNCHECKED_CAST")
                    val future = pendingRequests.remove(id) as? CompletableFuture<Any>
                    future?.completeExceptionally(RuntimeException("TWS error $errorCode: $errorMsg"))
                }
            }
        }
    }

    override fun connectionClosed() {
        log.warn("TwsIbkrAccountClient: connection closed by TWS")
        connected.set(false)
    }

    // ==================== Helpers ====================

    private fun parseIbkrTime(timeStr: String?): OffsetDateTime? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            // IBKR format: "yyyyMMdd-HH:mm:ss" (most common for executions)
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss")
            val localDateTime = java.time.LocalDateTime.parse(timeStr, formatter)
            localDateTime.atOffset(ZoneOffset.UTC)
        } catch (_: Exception) {
            try {
                // IBKR completed orders sometimes use "yyyyMMdd  HH:mm:ss" (double space)
                val cleaned = timeStr.replace("  ", " ").trim()
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss")
                val localDateTime = java.time.LocalDateTime.parse(cleaned, formatter)
                localDateTime.atOffset(ZoneOffset.UTC)
            } catch (_: Exception) {
                try {
                    // ISO format fallback
                    OffsetDateTime.parse(timeStr)
                } catch (_: Exception) {
                    log.debug("TwsIbkrAccountClient: could not parse time string: {}", timeStr)
                    null
                }
            }
        }
    }
}
