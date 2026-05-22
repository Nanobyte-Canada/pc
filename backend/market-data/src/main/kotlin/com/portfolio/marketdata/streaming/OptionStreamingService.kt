package com.portfolio.marketdata.streaming

import com.portfolio.common.domain.OptionType
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.distribution.QuoteWebSocketHandler
import com.portfolio.marketdata.ibkr.ContractResolver
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.ibkr.OptionContractDetails
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
    private val ibkrClient: IbkrClient,
    private val optionQuoteNormalizer: OptionQuoteNormalizer,
    private val greeksCalculator: GreeksCalculator,
    private val quoteWebSocketHandler: QuoteWebSocketHandler,
    private val quoteCacheService: QuoteCacheService
) {
    private val log = LoggerFactory.getLogger(OptionStreamingService::class.java)
    private val activeStreams = ConcurrentHashMap<String, Int>()
    private val refCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val activeChainUnderlying = ConcurrentHashMap<String, MutableSet<String>>()

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

    fun startStreamingChain(underlying: String) {
        if (activeChainUnderlying.containsKey(underlying)) {
            log.debug("Chain already streaming for {}", underlying)
            return
        }

        val contracts = try { ibkrClient.requestOptionChain(underlying) } catch (e: Exception) {
            log.error("Failed to load option chain for streaming: {}", underlying, e)
            return
        }

        val chainKeys = ConcurrentHashMap.newKeySet<String>()
        activeChainUnderlying[underlying] = chainKeys
        var subscribed = 0

        for (contract in contracts) {
            if (contract.expiry == null || contract.strike == null || contract.right == null) continue
            if (contract.conId <= 0) {
                val resolved = contractResolver.resolve(
                    contract.symbol, "OPT", contract.expiry, contract.strike, contract.right
                ) ?: continue
                startStreamingSingleForChain(underlying, resolved.conId, contract, chainKeys)
            } else {
                startStreamingSingleForChain(underlying, contract.conId, contract, chainKeys)
            }
            subscribed++
        }
        log.info("Started chain streaming for {} — {} contracts subscribed", underlying, subscribed)
    }

    private fun startStreamingSingleForChain(
        underlying: String, conId: Int, contract: OptionContractDetails, chainKeys: MutableSet<String>
    ) {
        val optionType = if (contract.right == "C") OptionType.CALL else OptionType.PUT
        val contractKey = "$underlying:${contract.expiry}:${contract.strike}:$optionType"

        if (activeStreams.containsKey(contractKey)) {
            chainKeys.add(contractKey)
            return
        }

        activeStreams[contractKey] = conId
        chainKeys.add(contractKey)

        subscriptionManager.subscribe(conId) { tickTypeInt, value ->
            val tick = when (tickTypeInt) {
                1 -> TickType.BID; 2 -> TickType.ASK; 4 -> TickType.LAST; 8 -> TickType.VOLUME
                else -> return@subscribe
            }
            optionQuoteNormalizer.processTick(
                contractKey, underlying, optionType, contract.strike!!, contract.expiry!!, tick, value
            ) { optionQuote ->
                val enriched = try {
                    val spot = quoteCacheService.getQuote(underlying)?.last ?: BigDecimal.valueOf(450.0)
                    optionQuote.copy(greeks = greeksCalculator.calculate(
                        spot, contract.strike, contract.expiry, optionType, null, null
                    ))
                } catch (_: Exception) { optionQuote }
                quoteWebSocketHandler.broadcastOptionQuote(enriched)
            }
        }
    }

    fun stopStreamingChain(underlying: String) {
        val chainKeys = activeChainUnderlying.remove(underlying) ?: return
        var stopped = 0
        for (contractKey in chainKeys) {
            val conId = activeStreams.remove(contractKey) ?: continue
            refCounts.remove(contractKey)
            subscriptionManager.unsubscribe(conId)
            stopped++
        }
        log.info("Stopped chain streaming for {} — {} contracts unsubscribed", underlying, stopped)
    }
}
