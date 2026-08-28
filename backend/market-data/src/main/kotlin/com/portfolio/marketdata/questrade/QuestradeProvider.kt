package com.portfolio.marketdata.questrade

import com.portfolio.marketdata.provider.MarketDataSnapshot
import com.portfolio.marketdata.provider.MarketDataProvider
import com.portfolio.marketdata.provider.OptionContractDetails
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Provider-neutral market data facade backed by Questrade.
 *
 * Composes [QuestradeRestClient] (REST snapshots/chain/search), [QuestradeStreamClient]
 * (tick subscriptions) and [QuestradeTokenManager] (auth warm-up). All `conId` values
 * are Questrade numeric symbolIds.
 */
@Component
class QuestradeProvider(
    private val restClient: QuestradeRestClient,
    private val streamClient: QuestradeStreamClient,
    private val tokenManager: QuestradeTokenManager
) : MarketDataProvider {

    /** Underlying ticker -> Questrade symbolId, resolved once via symbol search. */
    private val underlyingIds = ConcurrentHashMap<String, Int>()

    /** Full search matches for STK detail mapping, populated together with [underlyingIds]. */
    private val underlyingMatches = ConcurrentHashMap<String, QuestradeSymbol>()

    override fun connect() {
        // Warm-up: force a token exchange so the first data call never pays auth latency.
        tokenManager.getValidAccessToken()
    }

    override fun disconnect() {
        streamClient.shutdown()
    }

    override fun isConnected(): Boolean = try {
        tokenManager.getValidAccessToken(); true
    } catch (_: Exception) {
        false
    }

    override fun registerReconnectHandler(handler: Runnable) {
        streamClient.setReconnectHandler(handler)
    }

    override fun requestMarketData(conId: Int, callback: (tickType: Int, value: Double) -> Unit) {
        streamClient.subscribe(conId, callback)
    }

    override fun cancelMarketData(conId: Int) {
        streamClient.unsubscribe(conId)
    }

    override fun requestContractDetails(
        symbol: String,
        secType: String,
        expiry: LocalDate?,
        strike: BigDecimal?,
        right: String?
    ): List<OptionContractDetails> {
        return when (secType.uppercase()) {
            "STK" -> listOfNotNull(
                resolveUnderlying(symbol)?.let { match ->
                    OptionContractDetails(
                        conId = match.symbolId,
                        symbol = match.symbol,
                        secType = "STK",
                        exchange = match.listingExchange ?: "",
                        expiry = null,
                        strike = null,
                        right = null,
                        tradingClass = null,
                        multiplier = null
                    )
                }
            )
            "OPT" -> flattenChain(underlyingId(symbol) ?: return emptyList()).filter { c ->
                (expiry == null || c.expiry == expiry) &&
                    (strike == null || c.strike?.compareTo(strike) == 0) &&
                    (right == null || c.right.equals(right, ignoreCase = true))
            }
            else -> emptyList()
        }
    }

    override fun requestOptionChain(underlying: String): List<OptionContractDetails> {
        val underlyingId = underlyingId(underlying) ?: return emptyList()
        return flattenChain(underlyingId)
    }

    override fun requestOptionExpirations(underlying: String): List<LocalDate> {
        val underlyingId = underlyingId(underlying) ?: return emptyList()
        return restClient.getOptionChain(underlyingId)
            .map { LocalDate.parse(it.expiryDate.substring(0, 10)) }
            .distinct()
            .sorted()
    }

    override fun requestMarketDataSnapshot(conId: Int): MarketDataSnapshot? {
        // Questrade option symbolIds are >= 10_000_000; equity ids are far below.
        // Trying the wrong endpoint crashes the request, so route by id range.
        return if (conId >= OPTION_ID_THRESHOLD) {
            restClient.getOptionQuotes(listOf(conId)).firstOrNull()?.toSnapshot()
        } else {
            restClient.getStockQuotes(listOf(conId)).firstOrNull()?.toSnapshot()
        }
    }

    override fun requestOptionSnapshots(conIds: List<Int>): Map<Int, MarketDataSnapshot> =
        if (conIds.isEmpty()) {
            emptyMap()
        } else {
            // getOptionQuotes chunks internally at the 100-id API cap.
            restClient.getOptionQuotes(conIds).associate { it.symbolId to it.toSnapshot() }
        }

    /** Exact-case-insensitive symbol match, preferring quotable listings. */
    private fun searchExact(symbol: String): QuestradeSymbol? =
        restClient.searchSymbol(symbol)
            .filter { it.symbol.equals(symbol, ignoreCase = true) }
            .sortedByDescending { it.isQuotable }
            .firstOrNull()

    /** Resolves the underlying's full search match, memoizing it with its symbolId. */
    private fun resolveUnderlying(symbol: String): QuestradeSymbol? {
        val key = symbol.uppercase()
        underlyingMatches[key]?.let { return it }
        val match = searchExact(symbol) ?: return null
        underlyingIds[key] = match.symbolId
        underlyingMatches[key] = match
        return match
    }

    /** Id-only fast path for chain/expiration lookups; searches at most once per ticker. */
    private fun underlyingId(symbol: String): Int? =
        underlyingIds[symbol.uppercase()] ?: resolveUnderlying(symbol)?.symbolId

    /** Flattens every expiry x root x strike into CALL+PUT contracts. */
    private fun flattenChain(underlyingId: Int): List<OptionContractDetails> =
        restClient.getOptionChain(underlyingId).flatMap { expiry ->
            val exp = LocalDate.parse(expiry.expiryDate.substring(0, 10))
            val exchange = expiry.listingExchange ?: "OPRA"
            expiry.chainPerRoot.flatMap { root ->
                root.chainPerStrikePrice.flatMap { strike ->
                    listOf(
                        OptionContractDetails(
                            conId = strike.callSymbolId,
                            symbol = root.optionRoot,
                            secType = "OPT",
                            exchange = exchange,
                            expiry = exp,
                            strike = BigDecimal.valueOf(strike.strikePrice),
                            right = "C",
                            tradingClass = root.optionRoot,
                            multiplier = root.multiplier.toString()
                        ),
                        OptionContractDetails(
                            conId = strike.putSymbolId,
                            symbol = root.optionRoot,
                            secType = "OPT",
                            exchange = exchange,
                            expiry = exp,
                            strike = BigDecimal.valueOf(strike.strikePrice),
                            right = "P",
                            tradingClass = root.optionRoot,
                            multiplier = root.multiplier.toString()
                        )
                    )
                }
            }
        }

    private fun QuestradeOptionQuote.toSnapshot() = MarketDataSnapshot(
        conId = symbolId,
        bid = bidPrice,
        ask = askPrice,
        last = lastTradePrice,
        volume = volume,
        impliedVol = volatility?.div(100.0),
        delta = delta,
        gamma = gamma,
        theta = theta,
        vega = vega
    )

    private fun QuestradeStockQuote.toSnapshot() = MarketDataSnapshot(
        conId = symbolId,
        bid = bidPrice,
        ask = askPrice,
        last = lastTradePrice,
        volume = volume
    )

    companion object {
        /** Questrade option symbolIds are 8+ digits; equity ids are far below. */
        private const val OPTION_ID_THRESHOLD = 10_000_000
    }
}
