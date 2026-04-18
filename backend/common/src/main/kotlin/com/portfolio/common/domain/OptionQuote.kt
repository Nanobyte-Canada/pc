package com.portfolio.common.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

data class OptionQuote(
    val underlying: String,
    val optionType: OptionType,
    val strike: BigDecimal,
    val expiry: LocalDate,
    val bid: BigDecimal,
    val ask: BigDecimal,
    val last: BigDecimal,
    val volume: Long,
    val openInterest: Long,
    val greeks: Greeks?,
    val timestamp: Instant
) {
    val mid: BigDecimal
        get() = (bid + ask).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP)

    val spread: BigDecimal
        get() = ask - bid

    val spreadQuality: BigDecimal
        get() = if (mid.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO
        } else {
            spread.divide(mid, 4, RoundingMode.HALF_UP)
        }
}
