package com.portfolio.marketdata.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "expiry")
data class ExpiryProperties(
    val refresh: Refresh = Refresh(),
    val cache: Cache = Cache()
) {
    data class Refresh(
        val cron: String = "0 0 8 ? * MON",
        val symbols: List<String> = listOf("SOXL", "TECL", "TQQQ", "SPXU", "SPY", "QQQ", "XLF", "NVDA", "AVGO")
    )

    data class Cache(
        val ttlDays: Long = 90
    )
}
