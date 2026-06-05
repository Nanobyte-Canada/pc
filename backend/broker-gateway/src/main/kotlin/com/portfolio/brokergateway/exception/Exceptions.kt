package com.portfolio.brokergateway.exception

import com.portfolio.brokergateway.adapter.BrokerType

sealed class BrokerGatewayException(
    val errorCode: String,
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

class BrokerAuthenticationException(
    message: String,
    val brokerType: BrokerType,
    val needsReauth: Boolean = true,
    cause: Throwable? = null
) : BrokerGatewayException("BROKER_AUTH_FAILED", message, cause)

class BrokerConnectionException(
    message: String,
    val brokerType: BrokerType,
    cause: Throwable? = null
) : BrokerGatewayException("BROKER_CONNECTION_FAILED", message, cause)

class BrokerRateLimitException(
    message: String,
    val brokerType: BrokerType,
    val retryAfterSeconds: Int? = null
) : BrokerGatewayException("BROKER_RATE_LIMITED", message)

class BrokerOrderRejectedException(
    message: String,
    val brokerType: BrokerType,
    val brokerRejectionReason: String? = null
) : BrokerGatewayException("ORDER_REJECTED", message)

class BrokerUnsupportedOperationException(
    message: String,
    val brokerType: BrokerType
) : BrokerGatewayException("UNSUPPORTED_OPERATION", message)

class ConnectionNotFoundException(
    connectionId: String
) : BrokerGatewayException("CONNECTION_NOT_FOUND", "Connection not found: $connectionId")

class BrokerDataException(
    message: String,
    val brokerType: BrokerType,
    cause: Throwable? = null
) : BrokerGatewayException("BROKER_DATA_ERROR", message, cause)
