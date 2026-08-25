package com.portfolio.marketdata.health

import com.portfolio.marketdata.provider.MarketDataProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

class IbkrHealthIndicatorTest {

    @Test
    fun `health returns DOWN when IBKR is disconnected`() {
        val ibkrClient = mockk<MarketDataProvider>()
        every { ibkrClient.isConnected() } returns false

        val indicator = IbkrHealthIndicator(ibkrClient)
        val health = indicator.health()

        assert(health.status == Status.DOWN)
    }

    @Test
    fun `health returns UP when IBKR is connected`() {
        val ibkrClient = mockk<MarketDataProvider>()
        every { ibkrClient.isConnected() } returns true

        val indicator = IbkrHealthIndicator(ibkrClient)
        val health = indicator.health()

        assert(health.status == Status.UP)
    }
}
