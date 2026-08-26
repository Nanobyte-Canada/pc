package com.portfolio.marketdata.health

import com.portfolio.marketdata.provider.MarketDataProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

class QuestradeHealthIndicatorTest {

    @Test
    fun `health returns DOWN when provider is disconnected`() {
        val provider = mockk<MarketDataProvider>()
        every { provider.isConnected() } returns false

        val indicator = QuestradeHealthIndicator(provider)
        val health = indicator.health()

        assert(health.status == Status.DOWN)
    }

    @Test
    fun `health returns UP when provider is connected`() {
        val provider = mockk<MarketDataProvider>()
        every { provider.isConnected() } returns true

        val indicator = QuestradeHealthIndicator(provider)
        val health = indicator.health()

        assert(health.status == Status.UP)
    }
}
