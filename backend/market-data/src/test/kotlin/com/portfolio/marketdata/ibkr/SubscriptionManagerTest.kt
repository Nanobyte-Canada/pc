package com.portfolio.marketdata.ibkr

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SubscriptionManagerTest {

    private val ibkrClient = mockk<IbkrClient>(relaxed = true)

    @Test
    fun `resubscribeAll re-requests market data for all active subscriptions`() {
        val manager = SubscriptionManager(ibkrClient, 100)

        manager.subscribe(1001) { _, _ -> }
        manager.subscribe(1002) { _, _ -> }

        manager.resubscribeAll()

        verify(exactly = 2) { ibkrClient.requestMarketData(1001, any()) }
        verify(exactly = 2) { ibkrClient.requestMarketData(1002, any()) }
    }

    @Test
    fun `resubscribeAll is no-op when no active subscriptions`() {
        val manager = SubscriptionManager(ibkrClient, 100)
        manager.resubscribeAll()
        verify(exactly = 0) { ibkrClient.requestMarketData(any(), any()) }
    }

    @Test
    fun `resubscribeAll does not resubscribe unsubscribed contracts`() {
        val manager = SubscriptionManager(ibkrClient, 100)

        manager.subscribe(1001) { _, _ -> }
        manager.subscribe(1002) { _, _ -> }
        manager.unsubscribe(1001)

        manager.resubscribeAll()

        verify(exactly = 1) { ibkrClient.requestMarketData(1001, any()) }
        verify(exactly = 2) { ibkrClient.requestMarketData(1002, any()) }
    }

    @Test
    fun `resubscribeAll handles exception and continues`() {
        val manager = SubscriptionManager(ibkrClient, 100)

        manager.subscribe(1001) { _, _ -> }
        manager.subscribe(1002) { _, _ -> }

        every { ibkrClient.requestMarketData(1001, any()) } throws RuntimeException("network error")
        every { ibkrClient.requestMarketData(1002, any()) } returns Unit

        manager.resubscribeAll()

        verify(exactly = 2) { ibkrClient.requestMarketData(1001, any()) }
        verify(exactly = 2) { ibkrClient.requestMarketData(1002, any()) }
    }

    @Test
    fun `resubscribeAll retries on repeated failures`() {
        val manager = SubscriptionManager(ibkrClient, 100)

        manager.subscribe(1001) { _, _ -> }

        every { ibkrClient.requestMarketData(1001, any()) } throws RuntimeException("network error")

        manager.resubscribeAll()
        manager.resubscribeAll()
        manager.resubscribeAll()

        verify(exactly = 4) { ibkrClient.requestMarketData(1001, any()) }
    }
}
