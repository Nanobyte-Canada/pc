package com.portfolio.strategy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "services")
data class AppProperties(
    val marketDataUrl: String = "http://localhost:8082",
    val portfolioUrl: String = "http://localhost:8080"
)
