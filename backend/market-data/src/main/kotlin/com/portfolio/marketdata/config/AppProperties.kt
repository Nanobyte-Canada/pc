package com.portfolio.marketdata.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "provider")
data class AppProperties(
    val maxChainExpirations: Int = 12,
    val maxDteDefault: Int = 3650
)
