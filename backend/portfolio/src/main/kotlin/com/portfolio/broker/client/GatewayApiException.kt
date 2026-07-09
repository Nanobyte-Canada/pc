package com.portfolio.broker.client

import com.portfolio.exception.ExternalServiceException

/**
 * Exception representing an error propagated from the broker-gateway service.
 * Carries the gateway's error code, detail message, and HTTP status for richer
 * error propagation to the frontend.
 */
class GatewayApiException(
    val gatewayStatusCode: Int,
    val gatewayErrorCode: String?,
    gatewayDetail: String?,
    cause: Throwable? = null
) : RuntimeException(
    buildMessage(gatewayErrorCode, gatewayDetail, gatewayStatusCode),
    cause
) {
    companion object {
        private fun buildMessage(errorCode: String?, detail: String?, statusCode: Int): String {
            return if (detail != null) {
                detail
            } else {
                "Gateway returned HTTP $statusCode"
            }
        }
    }
}

/**
 * Converts this [GatewayApiException] into an [ExternalServiceException] suitable
 * for returning from the portfolio service layer.
 */
fun GatewayApiException.toExternalServiceException(): ExternalServiceException {
    return ExternalServiceException(
        code = gatewayErrorCode ?: "BROKER_CONNECTION_FAILED",
        message = message ?: "Failed to connect to broker service",
        cause = this
    )
}
