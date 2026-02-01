package com.portfolio.broker.client

import java.time.OffsetDateTime

/**
 * Common interface for all broker API clients.
 * Implements the Strategy pattern for broker-specific logic.
 */
interface BrokerClient {

    /** Broker code (QUESTRADE, IBKR, WEALTHSIMPLE) */
    val brokerCode: String

    /** Generate OAuth authorization URL */
    suspend fun getAuthorizationUrl(
        redirectUri: String,
        state: String,
        codeVerifier: String? = null
    ): String

    /** Exchange authorization code for tokens */
    suspend fun exchangeCodeForTokens(
        code: String,
        redirectUri: String,
        codeVerifier: String? = null
    ): BrokerTokenResponse

    /** Refresh access token */
    suspend fun refreshAccessToken(refreshToken: String): BrokerTokenResponse

    /** Revoke tokens on disconnect */
    suspend fun revokeTokens(accessToken: String)

    /** Fetch all accounts for the connection */
    suspend fun fetchAccounts(
        accessToken: String,
        apiServerUrl: String? = null
    ): List<BrokerAccountDto>

    /** Fetch positions for a specific account */
    suspend fun fetchPositions(
        accessToken: String,
        accountId: String,
        apiServerUrl: String? = null
    ): BrokerPositionsResponse

    /** Check if token is expired or about to expire */
    fun isTokenExpired(expiresAt: OffsetDateTime?): Boolean {
        if (expiresAt == null) return false
        return expiresAt.isBefore(OffsetDateTime.now().plusMinutes(tokenRefreshThresholdMinutes))
    }

    /** Get token refresh threshold in minutes */
    val tokenRefreshThresholdMinutes: Long
        get() = 5
}

/**
 * Token response from broker OAuth flow
 */
data class BrokerTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String = "Bearer",
    val expiresIn: Long?,
    val scope: String? = null,
    val apiServerUrl: String? = null
)

/**
 * Broker account information
 */
data class BrokerAccountDto(
    val accountId: String,
    val accountNumber: String?,
    val accountType: String?,
    val accountName: String?,
    val currency: String = "CAD",
    val status: String? = null,
    val metadata: Map<String, Any?>? = null
)

/**
 * Positions response from broker
 */
data class BrokerPositionsResponse(
    val positions: List<BrokerPositionDto>,
    val rawPayload: String? = null,
    val asOfTimestamp: OffsetDateTime? = null
)

/**
 * Individual position from broker
 */
data class BrokerPositionDto(
    val symbolId: String?,
    val symbol: String,
    val securityName: String? = null,
    val instrumentType: String? = null,
    val quantity: java.math.BigDecimal,
    val averageCost: java.math.BigDecimal?,
    val currentPrice: java.math.BigDecimal?,
    val currentValue: java.math.BigDecimal?,
    val dayPnl: java.math.BigDecimal? = null,
    val totalPnl: java.math.BigDecimal? = null,
    val totalPnlPercent: java.math.BigDecimal? = null,
    val currency: String = "CAD",
    val rawData: Map<String, Any?>? = null
)
