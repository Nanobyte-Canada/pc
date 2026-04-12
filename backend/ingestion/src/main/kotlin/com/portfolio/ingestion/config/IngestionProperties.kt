package com.portfolio.ingestion.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ingestion")
data class IngestionProperties(
    val enabled: Boolean = true,
    val schedule: String = "0 0 22 * * *",
    val staleThresholdDays: Int = 7,
    val targetExchanges: List<String> = listOf("US", "TO", "V", "INDX", "GBOND"),
    val eodhd: EodhdProperties = EodhdProperties()
)

data class EodhdProperties(
    val baseUrl: String = "https://eodhd.com/api",
    val apiKey: String = "",
    val rateLimitPerSecond: Int = 5,
    val dailyQuota: Int = 100000,
    val fundamentalsCost: Int = 10,
    val batchSize: Int = 500
)
