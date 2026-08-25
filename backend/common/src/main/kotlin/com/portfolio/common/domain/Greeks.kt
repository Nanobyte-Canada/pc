package com.portfolio.common.domain

import java.math.BigDecimal

enum class GreeksSource {
    IBKR,          // legacy cached values; removed in Task 10
    QUESTRADE,
    BLACK_SCHOLES
}

data class Greeks(
    val delta: BigDecimal,
    val gamma: BigDecimal,
    val theta: BigDecimal,
    val vega: BigDecimal,
    val rho: BigDecimal,
    val source: GreeksSource
)
