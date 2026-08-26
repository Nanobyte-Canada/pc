package com.portfolio.marketdata.health

import com.portfolio.marketdata.provider.MarketDataProvider
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class QuestradeHealthIndicator(
    private val provider: MarketDataProvider
) : HealthIndicator {

    override fun health(): Health {
        return if (provider.isConnected()) {
            Health.up().withDetail("provider", "connected").build()
        } else {
            Health.down().withDetail("provider", "disconnected").build()
        }
    }
}
