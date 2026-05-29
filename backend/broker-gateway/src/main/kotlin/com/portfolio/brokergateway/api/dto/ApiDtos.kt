package com.portfolio.brokergateway.api.dto

import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.adapter.OrderAction
import com.portfolio.brokergateway.adapter.OrderType
import com.portfolio.brokergateway.adapter.TimeInForce
import java.math.BigDecimal
import java.time.OffsetDateTime

data class CreateConnectionRequest(
    val userId: Long,
    val brokerType: BrokerType,
    val credentials: Map<String, Any>
)

data class ConnectionResponse(
    val connectionId: String,
    val brokerType: BrokerType,
    val status: String,
    val accountsJson: String?,
    val lastValidatedAt: OffsetDateTime?,
    val lastRefreshedAt: OffsetDateTime?,
    val errorMessage: String?,
    val createdAt: OffsetDateTime
)

data class ConnectionListResponse(
    val connections: List<ConnectionResponse>
)

data class PlaceOrderRequest(
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

data class BrokerHealthResponse(
    val brokerType: BrokerType,
    val enabled: Boolean,
    val status: String
)

data class GatewayHealthResponse(
    val status: String,
    val brokers: List<BrokerHealthResponse>
)
