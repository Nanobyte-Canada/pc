package com.portfolio.marketdata.streaming

import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.distribution.QuoteWebSocketHandler
import com.portfolio.marketdata.ibkr.ContractResolver
import com.portfolio.marketdata.ibkr.SubscriptionManager
import com.portfolio.marketdata.processing.QuoteNormalizer
import com.portfolio.marketdata.processing.TickType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class QuoteStreamingService(
    private val subscriptionManager: SubscriptionManager,
    private val contractResolver: ContractResolver,
    private val quoteNormalizer: QuoteNormalizer,
    private val quoteCacheService: QuoteCacheService,
    private val quoteWebSocketHandler: QuoteWebSocketHandler
) {
    private val log = LoggerFactory.getLogger(QuoteStreamingService::class.java)
    private val activeStreams = ConcurrentHashMap<String, Int>()
    private val refCounts = ConcurrentHashMap<String, AtomicInteger>()

    fun startStreaming(symbol: String) {
        val count = refCounts.computeIfAbsent(symbol) { AtomicInteger(0) }
        count.incrementAndGet()
        if (activeStreams.containsKey(symbol)) return

        val contract = contractResolver.resolve(symbol, "STK") ?: run {
            log.warn("Could not resolve contract for symbol: {}", symbol)
            return
        }

        val conId = contract.conId
        activeStreams[symbol] = conId
        log.info("Starting streaming for {} (conId={})", symbol, conId)

        subscriptionManager.subscribe(conId) { tickType, value ->
            val tick = when (tickType) {
                1 -> TickType.BID; 2 -> TickType.ASK; 4 -> TickType.LAST; 8 -> TickType.VOLUME
                else -> return@subscribe
            }
            quoteNormalizer.processTick(symbol, tick, value) { quote ->
                quoteCacheService.cacheQuote(quote)
                quoteWebSocketHandler.broadcastQuote(quote)
            }
        }
    }

    fun stopStreaming(symbol: String) {
        val count = refCounts[symbol] ?: return
        if (count.decrementAndGet() <= 0) {
            refCounts.remove(symbol)
            val conId = activeStreams.remove(symbol) ?: return
            subscriptionManager.unsubscribe(conId)
            log.info("Stopped streaming for {} (conId={})", symbol, conId)
        }
    }

    fun getActiveSymbols(): Set<String> = activeStreams.keys.toSet()
}
