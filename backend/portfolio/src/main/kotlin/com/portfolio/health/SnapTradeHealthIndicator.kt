package com.portfolio.health

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class SnapTradeHealthIndicator : HealthIndicator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun health(): Health {
        return try {
            Health.up().withDetail("status", "available").build()
        } catch (e: Exception) {
            log.warn("SnapTrade health check failed: {}", e.message)
            Health.down().withDetail("error", e.message ?: "Unknown error").build()
        }
    }
}
