package com.portfolio.common.domain

import java.math.BigDecimal

enum class GreeksSource {
    IBKR,
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
