package com.portfolio.marketdata.processing

import com.portfolio.common.domain.Quote
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class QuoteNormalizer {

    private val pendingQuotes = ConcurrentHashMap<String, QuoteAccumulator>()

    fun processTick(symbol: String, tickType: TickType, value: Double, onQuoteReady: (Quote) -> Unit) {
        val accumulator = pendingQuotes.computeIfAbsent(symbol) { QuoteAccumulator() }

        when (tickType) {
            TickType.BID -> accumulator.bid = value
            TickType.ASK -> accumulator.ask = value
            TickType.LAST -> accumulator.last = value
            TickType.VOLUME -> accumulator.volume = value.toLong()
        }

        accumulator.tryEmit(symbol)?.let(onQuoteReady)
    }

    private class QuoteAccumulator {
        var bid: Double? = null
        var ask: Double? = null
        var last: Double? = null
        var volume: Long? = null

        fun tryEmit(symbol: String): Quote? {
            val currentBid = bid ?: return null
            val currentAsk = ask ?: return null
            val currentVolume = volume ?: return null

            val normalizedBid = if (currentBid == 0.0) currentAsk else currentBid
            val normalizedLast = if (last == null || last == 0.0) (normalizedBid + currentAsk) / 2.0 else last!!

            return Quote(
                symbol = symbol,
                bid = BigDecimal.valueOf(normalizedBid).setScale(4, RoundingMode.HALF_UP),
                ask = BigDecimal.valueOf(currentAsk).setScale(4, RoundingMode.HALF_UP),
                last = BigDecimal.valueOf(normalizedLast).setScale(4, RoundingMode.HALF_UP),
                volume = currentVolume,
                timestamp = Instant.now()
            )
        }
    }
}
