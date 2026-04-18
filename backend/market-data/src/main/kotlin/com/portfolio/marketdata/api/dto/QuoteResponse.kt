package com.portfolio.marketdata.api.dto

import com.portfolio.common.domain.Quote
import java.math.BigDecimal
import java.time.Instant

data class QuoteResponse(
    val symbol: String,
    val bid: BigDecimal,
    val ask: BigDecimal,
    val last: BigDecimal,
    val mid: BigDecimal,
    val spread: BigDecimal,
    val volume: Long,
    val timestamp: Instant
) {
    companion object {
        fun fromDomain(quote: Quote): QuoteResponse = QuoteResponse(
            symbol = quote.symbol, bid = quote.bid, ask = quote.ask, last = quote.last,
            mid = quote.mid, spread = quote.spread, volume = quote.volume, timestamp = quote.timestamp
        )
    }
}
