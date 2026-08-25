package com.portfolio.marketdata.questrade

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "questrade")
data class QuestradeProperties(
    /** Seed refresh token; rotated tokens persist in Redis key questrade:refresh-token. */
    val refreshToken: String = "",
    val usePractice: Boolean = false,
    val authUrl: String = "https://login.questrade.com/oauth2/token",
    val practiceAuthUrl: String = "https://practicelogin.questrade.com/oauth2/token",
    /** Stay under Questrade's 20 req/s market-data bucket. */
    val rateLimitPerSecond: Int = 18,
    val maxSubscriptions: Int = 100,
    /** Poll interval for equities not covered by an active options stream session. */
    val equityPollIntervalSeconds: Long = 10,
    /** Questrade drops sessions without >=1 REST call / 30 min. */
    val keepaliveMinutes: Long = 25
)
