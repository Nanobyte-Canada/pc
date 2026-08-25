package com.portfolio.marketdata.health

import com.portfolio.marketdata.provider.MarketDataProvider
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class IbkrHealthIndicator(
    private val ibkrClient: MarketDataProvider
) : HealthIndicator {

    override fun health(): Health {
        return if (ibkrClient.isConnected()) {
            Health.up().withDetail("ibkr", "connected").build()
        } else {
            Health.down().withDetail("ibkr", "disconnected").build()
        }
    }
}
