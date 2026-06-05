package com.portfolio.common.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

data class Quote(
    val symbol: String,
    val bid: BigDecimal,
    val ask: BigDecimal,
    val last: BigDecimal,
    val volume: Long,
    val timestamp: Instant
) {
    val mid: BigDecimal
        get() = (bid + ask).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)

    val spread: BigDecimal
        get() = ask - bid
}
