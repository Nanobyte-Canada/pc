package com.portfolio.broker.client

/**
 * Base exception for broker-related errors.
 */
open class BrokerException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String = "BROKER_ERROR"
) : RuntimeException(message, cause)

/**
 * Thrown when broker API rate limit is exceeded.
 */
class BrokerRateLimitException(
    message: String = "Broker API rate limit exceeded",
    val retryAfterSeconds: Long? = null
) : BrokerException(message, errorCode = "RATE_LIMITED")

/**
 * Thrown when OAuth token is expired or invalid.
 */
class BrokerTokenExpiredException(
    message: String = "Broker token expired or invalid"
) : BrokerException(message, errorCode = "TOKEN_EXPIRED")

/**
 * Thrown when OAuth token refresh fails.
 */
class BrokerTokenRefreshException(
    message: String,
    cause: Throwable? = null
) : BrokerException(message, cause, errorCode = "TOKEN_REFRESH_FAILED")

/**
 * Thrown when broker connection is not found.
 */
class BrokerConnectionNotFoundException(
    connectionId: Long
) : BrokerException("Broker connection not found: $connectionId", errorCode = "CONNECTION_NOT_FOUND")

/**
 * Thrown when broker API returns an error.
 */
class BrokerApiException(
    message: String,
    val httpStatus: Int? = null,
    val brokerErrorCode: String? = null,
    cause: Throwable? = null
) : BrokerException(message, cause, errorCode = "API_ERROR")

/**
 * Thrown when token is not found for a connection.
 */
class TokenNotFoundException(
    connectionId: Long
) : BrokerException("Token not found for connection: $connectionId", errorCode = "TOKEN_NOT_FOUND")

/**
 * Thrown when OAuth state validation fails.
 */
class OAuthStateException(
    message: String
) : BrokerException(message, errorCode = "OAUTH_STATE_ERROR")
