package com.portfolio.marketdata.processing

import com.portfolio.common.domain.OptionQuote
import com.portfolio.common.domain.OptionType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Component
class OptionQuoteNormalizer {

    private val pendingQuotes = ConcurrentHashMap<String, OptionAccumulator>()

    fun processTick(
        contractKey: String,
        underlying: String,
        optionType: OptionType,
        strike: BigDecimal,
        expiry: LocalDate,
        tickType: TickType,
        value: Double,
        onQuoteReady: (OptionQuote) -> Unit
    ) {
        val accumulator = pendingQuotes.computeIfAbsent(contractKey) { OptionAccumulator() }

        when (tickType) {
            TickType.BID -> accumulator.bid = value
            TickType.ASK -> accumulator.ask = value
            TickType.LAST -> accumulator.last = value
            TickType.VOLUME -> accumulator.volume = value.toLong()
        }

        accumulator.tryEmit(underlying, optionType, strike, expiry)?.let(onQuoteReady)
    }

    private class OptionAccumulator {
        var bid: Double? = null
        var ask: Double? = null
        var last: Double? = null
        var volume: Long? = null

        fun tryEmit(underlying: String, optionType: OptionType, strike: BigDecimal, expiry: LocalDate): OptionQuote? {
            val b = bid ?: return null
            val a = ask ?: return null

            val normalizedBid = if (b == 0.0) a else b
            val normalizedLast = last?.let { if (it == 0.0) (normalizedBid + a) / 2.0 else it } ?: (normalizedBid + a) / 2.0

            return OptionQuote(
                underlying = underlying, optionType = optionType, strike = strike, expiry = expiry,
                bid = BigDecimal.valueOf(normalizedBid).setScale(4, RoundingMode.HALF_UP),
                ask = BigDecimal.valueOf(a).setScale(4, RoundingMode.HALF_UP),
                last = BigDecimal.valueOf(normalizedLast).setScale(4, RoundingMode.HALF_UP),
                volume = volume ?: 0L, openInterest = 0L, greeks = null, timestamp = Instant.now()
            )
        }
    }
}
