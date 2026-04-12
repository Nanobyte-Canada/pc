package com.portfolio.ingestion.health

import com.portfolio.ingestion.config.IngestionProperties
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class QuotaHealthIndicator(
    private val rateLimiter: EodhdRateLimiter,
    private val props: IngestionProperties
) : HealthIndicator {

    override fun health(): Health {
        val remaining = rateLimiter.remainingDailyQuota()
        val total = props.eodhd.dailyQuota
        val pct = if (total > 0) (remaining.toDouble() / total * 100) else 0.0

        val details = mapOf(
            "remaining" to remaining,
            "total" to total,
            "percentRemaining" to "%.1f%%".format(pct)
        )

        return if (pct > 1.0) {
            Health.up().withDetails(details).build()
        } else {
            Health.down().withDetails(details).withDetail("reason", "Daily quota exhausted").build()
        }
    }
}
