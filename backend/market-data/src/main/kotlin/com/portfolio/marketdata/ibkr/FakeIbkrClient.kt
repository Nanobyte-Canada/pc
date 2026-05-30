package com.portfolio.marketdata.ibkr

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Component
@ConditionalOnExpression("'\${ibkr.host:}'.length() == 0")
class FakeIbkrClient : IbkrClient {

    private val logger = LoggerFactory.getLogger(FakeIbkrClient::class.java)
    private val executor = Executors.newScheduledThreadPool(2)

    private var connected = false
    private val subscriptions = ConcurrentHashMap<Int, TickSubscription>()
    private var nextConId = 100000

    private val spotPrices = ConcurrentHashMap(mapOf(
        "SPY" to 450.00,
        "QQQ" to 380.00,
        "IWM" to 195.00,
        "AAPL" to 185.00,
        "MSFT" to 420.00,
        "TSLA" to 175.00,
        "GOOG" to 165.00,
        "AMZN" to 185.00,
        "NVDA" to 880.00,
        "META" to 500.00
    ))

    fun getSpotPrice(symbol: String): Double {
        return spotPrices.computeIfAbsent(symbol) {
            val hash = symbol.hashCode().toLong() and 0xFFFFFFFFL
            50.0 + (hash % 950)
        }
    }

    override fun connect() {
        logger.info("FakeIbkrClient: Connecting...")
        Thread.sleep(100)
        connected = true
        logger.info("FakeIbkrClient: Connected")
    }

    override fun disconnect() {
        logger.info("FakeIbkrClient: Disconnecting...")
        subscriptions.values.forEach { it.cancel() }
        subscriptions.clear()
        connected = false
        logger.info("FakeIbkrClient: Disconnected")
    }

    override fun isConnected(): Boolean = connected

    override fun requestMarketData(conId: Int, callback: (tickType: Int, value: Double) -> Unit) {
        if (!connected) throw IllegalStateException("Not connected to IBKR")

        logger.debug("FakeIbkrClient: Requesting market data for conId={}", conId)

        val future = executor.scheduleAtFixedRate({
            try {
                val basePrice = 450.0 + Random.nextDouble(-5.0, 5.0)
                val spread = 0.05
                callback(1, basePrice - spread / 2)
                callback(2, basePrice + spread / 2)
                callback(4, basePrice + Random.nextDouble(-spread, spread))
                callback(8, Random.nextDouble(100.0, 10000.0))
            } catch (e: Exception) {
                logger.error("Error generating fake ticks for conId={}", conId, e)
            }
        }, 0, 500, TimeUnit.MILLISECONDS)

        subscriptions[conId] = TickSubscription(conId, future)
    }

    override fun cancelMarketData(conId: Int) {
        logger.debug("FakeIbkrClient: Canceling market data for conId={}", conId)
        subscriptions.remove(conId)?.cancel()
    }

    override fun requestOptionChain(underlying: String): List<OptionContractDetails> {
        if (!connected) throw IllegalStateException("Not connected to IBKR")

        val spot = getSpotPrice(underlying)
        val expiries = generateMonthlyExpiries(4)
        val strikes = generateStrikes(spot)

        val contracts = mutableListOf<OptionContractDetails>()
        for (expiry in expiries) {
            for (strike in strikes) {
                contracts.add(OptionContractDetails(nextConId++, underlying, "OPT", "SMART", expiry, strike, "C"))
                contracts.add(OptionContractDetails(nextConId++, underlying, "OPT", "SMART", expiry, strike, "P"))
            }
        }

        logger.debug("FakeIbkrClient: Generated {} option contracts for {}", contracts.size, underlying)
        return contracts
    }

    override fun requestMarketDataSnapshot(conId: Int): MarketDataSnapshot {
        val basePrice = 450.0 + Random.nextDouble(-50.0, 50.0)
        val spread = Random.nextDouble(0.05, 0.30)
        return MarketDataSnapshot(
            conId = conId,
            bid = basePrice - spread / 2,
            ask = basePrice + spread / 2,
            last = basePrice,
            volume = Random.nextLong(100, 50000),
            impliedVol = Random.nextDouble(0.15, 0.45),
            delta = Random.nextDouble(-1.0, 1.0),
            gamma = Random.nextDouble(0.0, 0.05),
            theta = -Random.nextDouble(0.01, 0.15),
            vega = Random.nextDouble(0.01, 0.50)
        )
    }

    override fun requestContractDetails(
        symbol: String,
        secType: String,
        expiry: LocalDate?,
        strike: BigDecimal?,
        right: String?
    ): List<OptionContractDetails> {
        if (!connected) throw IllegalStateException("Not connected to IBKR")

        return when (secType) {
            "STK" -> listOf(OptionContractDetails(nextConId++, symbol, "STK", "SMART", null, null, null))
            "OPT" -> listOf(OptionContractDetails(
                nextConId++, symbol, "OPT", "SMART",
                expiry ?: generateMonthlyExpiries(1).first(),
                strike ?: BigDecimal("450.00"),
                right ?: "C"
            ))
            else -> emptyList()
        }
    }

    private fun generateMonthlyExpiries(count: Int): List<LocalDate> {
        val expiries = mutableListOf<LocalDate>()
        var current = LocalDate.now()

        while (expiries.size < count) {
            current = current.plusMonths(1).withDayOfMonth(1)
            var thirdFriday = current
            var fridayCount = 0
            while (fridayCount < 3) {
                if (thirdFriday.dayOfWeek == DayOfWeek.FRIDAY) fridayCount++
                if (fridayCount < 3) thirdFriday = thirdFriday.plusDays(1)
            }
            expiries.add(thirdFriday)
        }

        return expiries
    }

    private fun generateStrikes(spot: Double): List<BigDecimal> {
        val increment = if (spot > 300) 5.0 else 1.0
        val range = spot * 0.10
        val strikes = mutableListOf<BigDecimal>()
        var strike = spot - range
        while (strike <= spot + range) {
            val rounded = (strike / increment).toInt() * increment
            strikes.add(BigDecimal.valueOf(rounded))
            strike += increment
        }
        return strikes.distinct().sorted()
    }

    private data class TickSubscription(val conId: Int, val future: ScheduledFuture<*>) {
        fun cancel() = future.cancel(false)
    }
}
