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

    companion object {
        private const val MAX_DELTA = 0.45
        private const val ESTIMATED_IV = 0.30
        private const val MAX_CHAIN_SUBSCRIPTIONS = 80
    }

    fun startStreamingChain(underlying: String) {
        startStreamingChainForExpiry(underlying, null)
    }

    fun startStreamingChainForExpiryPublic(underlying: String, expiry: LocalDate, side: String? = null) {
        startStreamingChainForExpiry(underlying, expiry, side)
    }

    fun switchChainExpiry(underlying: String, expiry: LocalDate, side: String? = null) {
        stopStreamingChain(underlying)
        startStreamingChainForExpiry(underlying, expiry, side)
    }

    private fun startStreamingChainForExpiry(underlying: String, targetExpiry: LocalDate?, side: String? = null) {
        if (activeChainUnderlying.containsKey(underlying)) {
            if (targetExpiry == null) {
                log.debug("Chain already streaming for {}", underlying)
                return
            }
            stopStreamingChain(underlying)
        }

        val spotPrice = quoteCacheService.getQuote(underlying)?.last ?: run {
            val stk = ibkrClient.requestContractDetails(underlying, "STK").firstOrNull() ?: return
            val snapshot = ibkrClient.requestMarketDataSnapshot(stk.conId) ?: return
            snapshot.last?.let { java.math.BigDecimal.valueOf(it) } ?: return
        }
        val contracts = if (targetExpiry != null) {
            try {
                ibkrClient.requestContractDetails(underlying, "OPT", targetExpiry).filter { c ->
                    c.tradingClass == null || c.tradingClass == underlying
                }.ifEmpty { ibkrClient.requestContractDetails(underlying, "OPT", targetExpiry) }
            } catch (e: Exception) {
                log.error("Failed to load contracts for {} expiry {}", underlying, targetExpiry, e)
                return
            }
        } else {
            try { ibkrClient.requestOptionChain(underlying) } catch (e: Exception) {
                log.error("Failed to load option chain for streaming: {}", underlying, e)
                return
            }
        }

        val chainKeys = ConcurrentHashMap.newKeySet<String>()
        activeChainUnderlying[underlying] = chainKeys

        val expiry = targetExpiry ?: contracts.filter { it.expiry != null }.minByOrNull { it.expiry!! }?.expiry ?: return

        val sideFiltered = if (side != null) {
            contracts.filter { c ->
                when (side.lowercase()) {
                    "put" -> c.right?.uppercase() in setOf("P", "PUT")
                    "call" -> c.right?.uppercase() in setOf("C", "CALL")
                    else -> true
                }
            }
        } else contracts

        val toSubscribe = sideFiltered.filter { c ->
            c.conId > 0 && c.expiry == expiry && c.strike != null && c.right != null
        }.filter { c ->
            val optionType = if (isCall(c.right)) OptionType.CALL else OptionType.PUT
            val greeks = greeksCalculator.calculate(spotPrice, c.strike!!, c.expiry!!, optionType, ESTIMATED_IV, null)
            greeks.delta.abs().toDouble() <= MAX_DELTA
        }.take(MAX_CHAIN_SUBSCRIPTIONS)

        for (contract in toSubscribe) {
            startStreamingSingleForChain(underlying, contract.conId, contract, chainKeys)
        }
        log.info("Started chain streaming for {} — {} contracts subscribed (delta≤{}, expiry {}, side={})",
            underlying, toSubscribe.size, MAX_DELTA, expiry, side ?: "both")
    }

    private fun isCall(right: String?) = right == "C" || right.equals("Call", ignoreCase = true)

    private fun startStreamingSingleForChain(
        underlying: String, conId: Int, contract: OptionContractDetails, chainKeys: MutableSet<String>
    ) {
        val optionType = if (isCall(contract.right)) OptionType.CALL else OptionType.PUT
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
