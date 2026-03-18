package com.portfolio.ingestion.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ingestion")
class IngestionConfig {
    var enabled: Boolean = true
    var schedule: String = "0 0 22 * * *"
    var exchanges: ExchangesConfig = ExchangesConfig()
    var eodhd: EodhdConfig = EodhdConfig()
    var alphavantage: AlphaVantageProperties = AlphaVantageProperties()
    var etfcom: EtfComProperties = EtfComProperties()
}

class EtfComProperties {
    var enabled: Boolean = true
    var baseUrl: String = "https://api-prod.etf.com/v2/fund"
    var batchSize: Int = 25
    var staleThresholdDays: Int = 7
    var concurrency: Int = 5
    var requestDelayMs: Long = 200
    var interBatchDelayMs: Long = 2000
    var retry: RetryProperties = RetryProperties()
}

/**
 * Configuration for Alpha Vantage API.
 * Free tier: 25 requests/day, 5 requests/minute
 * Premium tiers: 75-1200 requests/minute, unlimited daily quota
 */
class AlphaVantageProperties {
    var enabled: Boolean = true
    var apiKey: String = ""
    var baseUrl: String = "https://www.alphavantage.co/query"
    var rateLimit: AlphaVantageRateLimitProperties = AlphaVantageRateLimitProperties()
    var retry: RetryProperties = RetryProperties()
    var batchSize: Int = 50  // Premium tier allows larger batches
    var staleThresholdDays: Int = 30
    var dailyQuota: Int = -1  // -1 = unlimited (premium tier)
}

class AlphaVantageRateLimitProperties {
    var requestsPerMinute: Int = 75  // Premium tier: 75/min

    /**
     * Computed requests per second, rounded up.
     * 60 RPM → 1 RPS, 75 RPM → 2 RPS, 120 RPM → 2 RPS
     */
    val requestsPerSecond: Int
        get() = kotlin.math.ceil(requestsPerMinute / 60.0).toInt()
}

class RetryProperties {
    var maxAttempts: Int = 3
    var initialBackoffMs: Long = 1000
    var maxBackoffMs: Long = 30000
    var multiplier: Double = 2.0
    var jitterFactor: Double = 0.1
}

class ExchangesConfig {
    var northAmerica: List<String> = listOf("US", "TO", "V")
}

class EodhdConfig {
    var baseUrl: String = "https://eodhd.com/api"
    var apiKey: String = ""
    var rateLimitPerSecond: Int = 5
}
