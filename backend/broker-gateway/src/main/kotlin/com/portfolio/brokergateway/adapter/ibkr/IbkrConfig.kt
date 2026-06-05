package com.portfolio.brokergateway.adapter.ibkr

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway.ibkr")
data class IbkrConfig(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 4002,
    val clientId: Int = 2,
    val connectTimeoutMs: Long = 5000,
    val requestTimeoutMs: Long = 30000,
    val reconnectDelayMs: Long = 5000,
    val maxReconnectDelayMs: Long = 60000,
    val flexToken: String = "",
    val flexQueryId: String = ""
)
