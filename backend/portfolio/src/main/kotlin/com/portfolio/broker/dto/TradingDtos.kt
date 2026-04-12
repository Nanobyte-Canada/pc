package com.portfolio.broker.dto

import com.portfolio.broker.entity.TradeOrder
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

// ========== Request DTOs ==========

data class ExecuteTradesRequest(
    val groupId: Long,
    val trades: List<TradeExecutionInput>,
    val orderType: String = "MARKET",
    val timeInForce: String = "DAY"
)

data class TradeExecutionInput(
    val symbol: String,
    val action: String, // BUY or SELL
    val units: BigDecimal,
    val price: BigDecimal,
    val amount: BigDecimal,
    val currency: String = "CAD",
    val connectionId: Long,
    val limitPrice: BigDecimal? = null
)

// ========== Response DTOs ==========

data class TradeOrderDto(
    val id: Long,
    val groupId: Long?,
    val connectionId: Long,
    val batchId: UUID?,
    val symbol: String,
    val action: String,
    val orderType: String,
    val timeInForce: String,
    val requestedUnits: BigDecimal,
    val requestedPrice: BigDecimal,
    val requestedAmount: BigDecimal,
    val limitPrice: BigDecimal?,
    val filledUnits: BigDecimal?,
    val filledPrice: BigDecimal?,
    val filledAmount: BigDecimal?,
    val currency: String,
    val status: String,
    val brokerOrderId: String?,
    val accountName: String?,
    val errorMessage: String?,
    val errorCode: String?,
    val submittedAt: OffsetDateTime?,
    val filledAt: OffsetDateTime?,
    val cancelledAt: OffsetDateTime?,
    val createdAt: OffsetDateTime
)

data class ExecuteTradesResponse(
    val batchId: UUID,
    val orders: List<TradeOrderDto>,
    val submittedCount: Int,
    val failedCount: Int
)

data class OrderStatusResponse(
    val orders: List<TradeOrderDto>,
    val totalCount: Int
)

// ========== Mapper ==========

fun TradeOrder.toDto() = TradeOrderDto(
    id = id,
    groupId = group?.id,
    connectionId = connection.id,
    batchId = batchId,
    symbol = symbol,
    action = action.name,
    orderType = orderType.name,
    timeInForce = timeInForce.name,
    requestedUnits = requestedUnits,
    requestedPrice = requestedPrice,
    requestedAmount = requestedAmount,
    limitPrice = limitPrice,
    filledUnits = filledUnits,
    filledPrice = filledPrice,
    filledAmount = filledAmount,
    currency = currency,
    status = status.name,
    brokerOrderId = brokerOrderId,
    accountName = connection.accountName,
    errorMessage = errorMessage,
    errorCode = errorCode,
    submittedAt = submittedAt,
    filledAt = filledAt,
    cancelledAt = cancelledAt,
    createdAt = createdAt
)
