package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.OrderAction
import com.portfolio.brokergateway.adapter.OrderStatus
import com.portfolio.brokergateway.adapter.OrderType
import com.portfolio.brokergateway.adapter.TimeInForce
import java.math.BigDecimal
import java.time.OffsetDateTime

data class UnifiedOrder(
    val brokerOrderId: String,
    val symbol: String,
    val action: OrderAction,
    val orderType: OrderType,
    val timeInForce: TimeInForce,
    val totalQuantity: BigDecimal,
    val filledQuantity: BigDecimal?,
    val executionPrice: BigDecimal?,
    val limitPrice: BigDecimal?,
    val stopPrice: BigDecimal?,
    val status: OrderStatus,
    val currency: String?,
    val submittedAt: OffsetDateTime?,
    val filledAt: OffsetDateTime?
)
