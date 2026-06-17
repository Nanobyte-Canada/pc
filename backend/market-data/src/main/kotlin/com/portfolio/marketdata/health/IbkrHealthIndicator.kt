package com.portfolio.marketdata.health

import com.portfolio.marketdata.ibkr.IbkrClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class IbkrHealthIndicator(
    private val ibkrClient: IbkrClient
) : HealthIndicator {

    override fun health(): Health {
        return if (ibkrClient.isConnected()) {
            Health.up().withDetail("ibkr", "connected").build()
        } else {
            Health.down().withDetail("ibkr", "disconnected").build()
        }
    }
}
