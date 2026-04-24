package com.portfolio.broker.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway")
data class BrokerGatewayConfig(
    val url: String = "http://localhost:8084",
    val apiKey: String = "dev-gateway-key",
    val timeoutMs: Long = 30000
)
