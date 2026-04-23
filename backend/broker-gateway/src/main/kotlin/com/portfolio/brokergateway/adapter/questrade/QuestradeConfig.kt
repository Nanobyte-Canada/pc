package com.portfolio.brokergateway.adapter.questrade

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway.questrade")
data class QuestradeConfig(
    val enabled: Boolean = false,
    val authUrl: String = "https://login.questrade.com/oauth2/token",
    val practiceAuthUrl: String = "https://practicelogin.questrade.com/oauth2/token",
    val usePractice: Boolean = false,
    val rateLimitPerSecond: Int = 1
)
