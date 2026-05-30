package com.portfolio.marketdata.ibkr

import com.ib.client.*
import com.portfolio.marketdata.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Component
@ConditionalOnExpression("'\${ibkr.host:}'.length() > 0")
class TwsIbkrClient(
    private val properties: AppProperties
) : DefaultEWrapper(), IbkrClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var client: EClientSocket
    private lateinit var signal: EReaderSignal
    private val connected = AtomicBoolean(false)
    private val nextReqId = AtomicInteger(1)
    @Volatile private var connectionReady = CountDownLatch(1)

    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tws-send").apply { isDaemon = true }
    }

    private val tickCallbacks = ConcurrentHashMap<Int, (Int, Double) -> Unit>()
    private val reqIdToConId = ConcurrentHashMap<Int, Int>()
    private val conIdToReqId = ConcurrentHashMap<Int, Int>()

    private val pendingRequests = ConcurrentHashMap<Int, CompletableFuture<Any>>()
    private val contractAccumulators = ConcurrentHashMap<Int, MutableList<OptionContractDetails>>()
    private val optionParamAccumulators = ConcurrentHashMap<Int, MutableList<OptionContractDetails>>()
    private val snapshotAccumulators = ConcurrentHashMap<Int, MarketDataSnapshot>()
    private val greeksStore = ConcurrentHashMap<Int, GreeksData>()

    private val requestTimeout = properties.reconnectDelayMs.coerceAtLeast(10000)

    data class GreeksData(
        val impliedVol: Double = Double.MAX_VALUE,
        val delta: Double = Double.MAX_VALUE,
        val gamma: Double = Double.MAX_VALUE,
        val theta: Double = Double.MAX_VALUE,
        val vega: Double = Double.MAX_VALUE
    )

    fun getGreeks(conId: Int): GreeksData? = greeksStore[conId]

    // === IbkrClient interface ===

    override fun connect() {
        log.info("TwsIbkrClient: connecting to {}:{} clientId={}", properties.host, properties.port, properties.clientId)

        if (properties.host.isBlank()) {
            log.warn("TwsIbkrClient: IBKR_HOST is blank, skipping connection")
            return
        }

        connectionReady = CountDownLatch(1)
        signal = EJavaSignal()
        client = EClientSocket(this, signal)
        client.eConnect(properties.host, properties.port, properties.clientId)

        if (!client.isConnected) {
            log.error("TwsIbkrClient: eConnect returned but not connected")
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
                    log.error("TwsIbkrClient: error processing messages", e)
                }
            }
            log.info("TwsIbkrClient: message processing thread exiting")
        }, "tws-reader").apply { isDaemon = true }.start()

        if (!connectionReady.await(10, TimeUnit.SECONDS)) {
            log.warn("TwsIbkrClient: timed out waiting for nextValidId callback")
        }

        // Request delayed data as fallback if real-time API subscription not available
        if (connected.get()) {
            client.reqMarketDataType(3)
            log.info("TwsIbkrClient: requested delayed market data type (fallback)")
        }
    }

    override fun disconnect() {
        log.info("TwsIbkrClient: disconnecting")
        connected.set(false)
        tickCallbacks.clear()
        reqIdToConId.clear()
        conIdToReqId.clear()
        pendingRequests.values.forEach { it.completeExceptionally(IllegalStateException("Disconnected")) }
        pendingRequests.clear()
        contractAccumulators.clear()
        optionParamAccumulators.clear()
        if (::client.isInitialized && client.isConnected) {
            client.eDisconnect()
        }
    }

    override fun isConnected(): Boolean = connected.get() && (::client.isInitialized && client.isConnected)

    override fun requestMarketData(conId: Int, callback: (tickType: Int, value: Double) -> Unit) {
        val reqId = nextReqId.getAndIncrement()
        reqIdToConId[reqId] = conId
        conIdToReqId[conId] = reqId
        tickCallbacks[conId] = callback

        val contract = Contract().apply {
            conid(conId)
            exchange("SMART")
        }

        sendExecutor.submit {
            try {
                client.reqMktData(reqId, contract, "", false, false, null)
                log.debug("TwsIbkrClient: subscribed market data reqId={} conId={}", reqId, conId)
            } catch (e: Exception) {
                log.error("TwsIbkrClient: failed to request market data for conId={}", conId, e)
            }
        }
    }

    override fun cancelMarketData(conId: Int) {
        val reqId = conIdToReqId.remove(conId) ?: return
        reqIdToConId.remove(reqId)
        tickCallbacks.remove(conId)

        sendExecutor.submit {
            try {
                client.cancelMktData(reqId)
                log.debug("TwsIbkrClient: cancelled market data reqId={} conId={}", reqId, conId)
            } catch (e: Exception) {
                log.error("TwsIbkrClient: failed to cancel market data for conId={}", conId, e)
            }
        }
    }

    override fun requestMarketDataSnapshot(conId: Int): MarketDataSnapshot? {
        val reqId = nextReqId.getAndIncrement()
        @Suppress("UNCHECKED_CAST")
        val future = CompletableFuture<Any>()
        val snapshot = MarketDataSnapshot(conId = conId)
        snapshotAccumulators[reqId] = snapshot
        reqIdToConId[reqId] = conId
        pendingRequests[reqId] = future

        val contract = Contract().apply {
            conid(conId)
            exchange("SMART")
        }

        sendExecutor.submit {
            try {
                client.reqMktData(reqId, contract, "", true, false, null)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            future.get(5000, TimeUnit.MILLISECONDS) as? MarketDataSnapshot
        } catch (e: Exception) {
            log.debug("TwsIbkrClient: snapshot timed out for conId={}", conId)
            val partial = snapshotAccumulators[reqId]
            if (partial != null && (partial.bid != null || partial.ask != null)) partial else null
        } finally {
            pendingRequests.remove(reqId)
            snapshotAccumulators.remove(reqId)
            reqIdToConId.remove(reqId)
        }
    }

    override fun requestOptionChain(underlying: String): List<OptionContractDetails> {
        val stocks = requestContractDetails(underlying, "STK")
        if (stocks.isEmpty()) {
            log.warn("TwsIbkrClient: could not resolve underlying conId for {}", underlying)
            return emptyList()
        }
        val underlyingConId = stocks.first().conId

        val reqId = nextReqId.getAndIncrement()
        @Suppress("UNCHECKED_CAST")
        val future = CompletableFuture<Any>() as CompletableFuture<Any>
        val results = CopyOnWriteArrayList<OptionContractDetails>()
        optionParamAccumulators[reqId] = results
        pendingRequests[reqId] = future

        sendExecutor.submit {
            try {
                client.reqSecDefOptParams(reqId, underlying, "", "STK", underlyingConId)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            future.get(requestTimeout, TimeUnit.MILLISECONDS) as List<OptionContractDetails>
        } catch (e: TimeoutException) {
            log.warn("TwsIbkrClient: option chain request timed out for {}", underlying)
            results.toList()
        } catch (e: Exception) {
            log.error("TwsIbkrClient: option chain request failed for {}", underlying, e)
            emptyList()
        } finally {
            pendingRequests.remove(reqId)
            optionParamAccumulators.remove(reqId)
        }
    }

    override fun requestContractDetails(
        symbol: String, secType: String,
        expiry: LocalDate?, strike: BigDecimal?, right: String?
    ): List<OptionContractDetails> {
        val reqId = nextReqId.getAndIncrement()
        @Suppress("UNCHECKED_CAST")
        val future = CompletableFuture<Any>() as CompletableFuture<Any>
        val results = CopyOnWriteArrayList<OptionContractDetails>()
        contractAccumulators[reqId] = results
        pendingRequests[reqId] = future

        val contract = Contract().apply {
            symbol(symbol)
            secType(secType)
            exchange("SMART")
            currency("USD")
            expiry?.let { lastTradeDateOrContractMonth(it.format(DateTimeFormatter.BASIC_ISO_DATE)) }
            strike?.let { strike(it.toDouble()) }
            right?.let { right(it) }
        }

        sendExecutor.submit {
            try {
                client.reqContractDetails(reqId, contract)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            future.get(requestTimeout, TimeUnit.MILLISECONDS) as List<OptionContractDetails>
        } catch (e: TimeoutException) {
            log.warn("TwsIbkrClient: contract details request timed out for {} {}", symbol, secType)
            results.toList()
        } catch (e: Exception) {
            log.error("TwsIbkrClient: contract details failed for {} {}", symbol, secType, e)
            emptyList()
        } finally {
            pendingRequests.remove(reqId)
            contractAccumulators.remove(reqId)
        }
    }

    // === EWrapper callbacks ===

    override fun nextValidId(orderId: Int) {
        log.info("TwsIbkrClient: connected, nextValidId={}", orderId)
        nextReqId.set(orderId.coerceAtLeast(nextReqId.get()))
        connected.set(true)
        connectionReady.countDown()
    }

    override fun managedAccounts(accountsList: String?) {
        log.info("TwsIbkrClient: managed accounts: {}", accountsList)
    }

    override fun tickPrice(tickerId: Int, field: Int, price: Double, attribs: TickAttrib?) {
        val conId = reqIdToConId[tickerId] ?: return
        // Populate snapshot accumulator if this is a snapshot request
        snapshotAccumulators[tickerId]?.let { snap ->
            when (field) {
                1 -> snapshotAccumulators[tickerId] = snap.copy(bid = price)
                2 -> snapshotAccumulators[tickerId] = snap.copy(ask = price)
                4 -> snapshotAccumulators[tickerId] = snap.copy(last = price)
                9 -> if (snap.last == null) snapshotAccumulators[tickerId] = snap.copy(last = price)
            }
        }
        tickCallbacks[conId]?.invoke(field, price)
    }

    override fun tickSize(tickerId: Int, field: Int, size: Decimal?) {
        val conId = reqIdToConId[tickerId] ?: return
        val value = size?.longValue()?.toDouble() ?: return
        snapshotAccumulators[tickerId]?.let { snap ->
            if (field == 8) snapshotAccumulators[tickerId] = snap.copy(volume = value.toLong())
        }
        tickCallbacks[conId]?.invoke(field, value)
    }

    override fun tickString(tickerId: Int, tickType: Int, value: String?) {
        // Tick type 45 = last timestamp, 84 = dividends
    }

    override fun tickGeneric(tickerId: Int, tickType: Int, value: Double) {
        val conId = reqIdToConId[tickerId] ?: return
        tickCallbacks[conId]?.invoke(tickType, value)
    }

    override fun tickOptionComputation(
        tickerId: Int, field: Int, tickAttrib: Int,
        impliedVol: Double, delta: Double, optPrice: Double,
        pvDividend: Double, gamma: Double, vega: Double, theta: Double, undPrice: Double
    ) {
        val conId = reqIdToConId[tickerId] ?: return
        // field 13 = model-based, most reliable for Greeks
        if (field == 13 || field == 10 || field == 11 || field == 12) {
            val validIV = if (impliedVol >= 0 && impliedVol < 10) impliedVol else null
            val validDelta = if (delta > -2 && delta < 2) delta else null

            greeksStore[conId] = GreeksData(
                impliedVol = validIV ?: Double.MAX_VALUE,
                delta = validDelta ?: Double.MAX_VALUE,
                gamma = if (gamma >= 0) gamma else Double.MAX_VALUE,
                theta = if (theta != Double.MAX_VALUE) theta else Double.MAX_VALUE,
                vega = if (vega >= 0) vega else Double.MAX_VALUE
            )

            snapshotAccumulators[tickerId]?.let { snap ->
                snapshotAccumulators[tickerId] = snap.copy(
                    impliedVol = validIV,
                    delta = validDelta,
                    gamma = if (gamma >= 0) gamma else null,
                    theta = if (theta != Double.MAX_VALUE) theta else null,
                    vega = if (vega >= 0) vega else null
                )
            }
        }
        log.debug("TwsIbkrClient: Greeks conId={} field={} IV={} delta={}", conId, field, impliedVol, delta)
    }

    override fun tickSnapshotEnd(reqId: Int) {
        val snapshot = snapshotAccumulators[reqId]
        @Suppress("UNCHECKED_CAST")
        val future = pendingRequests[reqId] as? CompletableFuture<Any>
        future?.complete(snapshot ?: MarketDataSnapshot(conId = reqIdToConId[reqId] ?: 0))
    }

    override fun contractDetails(reqId: Int, contractDetails: ContractDetails?) {
        val cd = contractDetails ?: return
        val c = cd.contract()
        val secTypeStr = c.secType()?.toString() ?: "STK"
        val rightStr = c.right()?.toString()?.takeIf { it != "None" && it != "?" }
        val detail = OptionContractDetails(
            conId = c.conid(),
            symbol = c.symbol(),
            secType = secTypeStr,
            exchange = c.exchange() ?: "SMART",
            expiry = c.lastTradeDateOrContractMonth()?.takeIf { it.length >= 8 }?.let {
                LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE)
            },
            strike = if (c.strike() > 0) BigDecimal.valueOf(c.strike()) else null,
            right = rightStr
        )
        contractAccumulators[reqId]?.add(detail)
    }

    override fun contractDetailsEnd(reqId: Int) {
        val results = contractAccumulators[reqId] ?: return
        @Suppress("UNCHECKED_CAST")
        val future = pendingRequests[reqId] as? CompletableFuture<Any>
        future?.complete(results.toList())
    }

    override fun securityDefinitionOptionalParameter(
        reqId: Int, exchange: String?, underlyingConId: Int,
        tradingClass: String?, multiplier: String?,
        expirations: MutableSet<String>?, strikes: MutableSet<Double>?
    ) {
        if (exchange != "SMART") return

        val results = mutableListOf<OptionContractDetails>()
        val exps = expirations ?: return
        val stks = strikes ?: return

        for (exp in exps) {
            val expDate = try {
                LocalDate.parse(exp, DateTimeFormatter.BASIC_ISO_DATE)
            } catch (_: Exception) { continue }

            for (strike in stks) {
                for (right in listOf("C", "P")) {
                    results.add(
                        OptionContractDetails(
                            conId = 0,
                            symbol = tradingClass ?: "",
                            secType = "OPT",
                            exchange = exchange,
                            expiry = expDate,
                            strike = BigDecimal.valueOf(strike),
                            right = right
                        )
                    )
                }
            }
        }

        optionParamAccumulators[reqId]?.addAll(results)
    }

    override fun securityDefinitionOptionalParameterEnd(reqId: Int) {
        val results = optionParamAccumulators[reqId] ?: return
        @Suppress("UNCHECKED_CAST")
        val future = pendingRequests[reqId] as? CompletableFuture<Any>
        future?.complete(results.toList())
    }

    override fun error(e: Exception?) {
        log.error("TwsIbkrClient: connection error", e)
    }

    override fun error(str: String?) {
        log.error("TwsIbkrClient: {}", str)
    }

    override fun error(id: Int, errorCode: Int, errorMsg: String?, advancedOrderRejectJson: String?) {
        when (errorCode) {
            // Connection-level errors
            502, 504 -> {
                log.error("TwsIbkrClient: connection error [{}]: {}", errorCode, errorMsg)
                connected.set(false)
            }
            // Market data farm messages (informational)
            2104, 2106, 2158 -> log.info("TwsIbkrClient: [{}] {}", errorCode, errorMsg)
            // Market data subscription warning — delayed data may follow
            10089, 10090 -> log.info("TwsIbkrClient: [{}] {} (reqId={})", errorCode, errorMsg, id)
            // No security definition found
            200 -> {
                log.warn("TwsIbkrClient: [{}] {} (reqId={})", errorCode, errorMsg, id)
                @Suppress("UNCHECKED_CAST")
                val future = pendingRequests.remove(id) as? CompletableFuture<Any>
                future?.complete(emptyList<OptionContractDetails>())
            }
            else -> {
                log.warn("TwsIbkrClient: error id={} code={} msg={}", id, errorCode, errorMsg)
                if (id > 0) {
                    @Suppress("UNCHECKED_CAST")
                    val future = pendingRequests.remove(id) as? CompletableFuture<Any>
                    future?.completeExceptionally(RuntimeException("TWS error $errorCode: $errorMsg"))
                }
            }
        }
    }

    override fun connectionClosed() {
        log.warn("TwsIbkrClient: connection closed by TWS")
        connected.set(false)
        pendingRequests.values.forEach { it.completeExceptionally(IllegalStateException("Connection closed")) }
        pendingRequests.clear()
        contractAccumulators.clear()
        optionParamAccumulators.clear()
        snapshotAccumulators.clear()
    }
}
