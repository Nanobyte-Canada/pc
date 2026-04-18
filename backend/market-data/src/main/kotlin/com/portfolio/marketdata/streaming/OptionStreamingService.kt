package com.portfolio.marketdata.streaming

import com.portfolio.common.domain.OptionType
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.distribution.QuoteWebSocketHandler
import com.portfolio.marketdata.ibkr.ContractResolver
import com.portfolio.marketdata.ibkr.SubscriptionManager
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionQuoteNormalizer
import com.portfolio.marketdata.processing.TickType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class OptionStreamingService(
    private val subscriptionManager: SubscriptionManager,
    private val contractResolver: ContractResolver,
    private val optionQuoteNormalizer: OptionQuoteNormalizer,
    private val greeksCalculator: GreeksCalculator,
    private val quoteWebSocketHandler: QuoteWebSocketHandler,
    private val quoteCacheService: QuoteCacheService
) {
    private val log = LoggerFactory.getLogger(OptionStreamingService::class.java)
    private val activeStreams = ConcurrentHashMap<String, Int>()
    private val refCounts = ConcurrentHashMap<String, AtomicInteger>()

    fun startStreaming(symbol: String, expiry: LocalDate, strike: BigDecimal, optionType: OptionType) {
        val contractKey = "$symbol:$expiry:$strike:$optionType"
        val count = refCounts.computeIfAbsent(contractKey) { AtomicInteger(0) }
        count.incrementAndGet()
        if (activeStreams.containsKey(contractKey)) return

        val right = if (optionType == OptionType.CALL) "C" else "P"
        val contract = contractResolver.resolve(symbol, "OPT", expiry, strike, right) ?: run {
            log.warn("Could not resolve option contract: {}", contractKey)
            return
        }

        val conId = contract.conId
        activeStreams[contractKey] = conId
        log.info("Starting option streaming for {} (conId={})", contractKey, conId)

        subscriptionManager.subscribe(conId) { tickTypeInt, value ->
            val tick = when (tickTypeInt) {
                1 -> TickType.BID; 2 -> TickType.ASK; 4 -> TickType.LAST; 8 -> TickType.VOLUME
                else -> return@subscribe
            }
            optionQuoteNormalizer.processTick(contractKey, symbol, optionType, strike, expiry, tick, value) { optionQuote ->
                val enriched = try {
                    val spot = quoteCacheService.getQuote(symbol)?.last ?: BigDecimal.valueOf(450.0)
                    optionQuote.copy(greeks = greeksCalculator.calculate(spot, strike, expiry, optionType, null, null))
                } catch (_: Exception) { optionQuote }
                quoteWebSocketHandler.broadcastOptionQuote(enriched)
            }
        }
    }

    fun stopStreaming(symbol: String, expiry: LocalDate, strike: BigDecimal, optionType: OptionType) {
        val contractKey = "$symbol:$expiry:$strike:$optionType"
        val count = refCounts[contractKey] ?: return
        if (count.decrementAndGet() <= 0) {
            refCounts.remove(contractKey)
            val conId = activeStreams.remove(contractKey) ?: return
            subscriptionManager.unsubscribe(conId)
            log.info("Stopped option streaming for {} (conId={})", contractKey, conId)
        }
    }
}
