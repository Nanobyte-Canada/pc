package com.portfolio.marketdata.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ibkr")
data class AppProperties(
    val host: String = "127.0.0.1",
    val port: Int = 4002,
    val clientId: Int = 1,
    val maxConnections: Int = 2,
    val reconnectDelayMs: Long = 5000
)
