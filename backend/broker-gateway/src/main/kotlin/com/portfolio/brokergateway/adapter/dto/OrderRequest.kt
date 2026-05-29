package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.OrderAction
import com.portfolio.brokergateway.adapter.OrderType
import com.portfolio.brokergateway.adapter.TimeInForce
import java.math.BigDecimal

data class OrderRequest(
    val symbol: String,
    val action: OrderAction,
    val quantity: BigDecimal,
    val orderType: OrderType,
    val limitPrice: BigDecimal? = null,
    val stopPrice: BigDecimal? = null,
    val timeInForce: TimeInForce = TimeInForce.DAY,
    val currency: String? = null,
    val symbolId: Long? = null,
    val primaryRoute: String? = null,
    val secondaryRoute: String? = null,
    val optionType: String? = null,
    val strike: java.math.BigDecimal? = null,
    val expiry: String? = null
)

data class OrderResult(
    val brokerOrderId: String?,
    val status: com.portfolio.brokergateway.adapter.OrderStatus,
    val message: String? = null
)

data class CancelResult(
    val success: Boolean,
    val message: String? = null
)

data class ConnectionValidationResult(
    val connected: Boolean,
    val message: String? = null,
    val needsReauth: Boolean = false
)

data class OrderImpactResult(
    val estimatedCommission: BigDecimal?,
    val buyingPowerEffect: BigDecimal?,
    val maintenanceExcess: BigDecimal?,
    val isOrderAccepted: Boolean,
    val warnings: List<String> = emptyList()
)
