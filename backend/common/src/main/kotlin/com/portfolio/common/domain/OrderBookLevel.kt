package com.portfolio.common.domain

import java.math.BigDecimal

enum class OrderSide {
    BID,
    ASK
}

data class OrderBookLevel(
    val price: BigDecimal,
    val size: Long,
    val side: OrderSide
)
