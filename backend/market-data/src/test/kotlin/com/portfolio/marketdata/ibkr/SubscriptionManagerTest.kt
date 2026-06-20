package com.portfolio.marketdata.ibkr

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SubscriptionManagerTest {

    private val ibkrClient = mockk<IbkrClient>(relaxed = true)

    @Test
    fun `resubscribeAll re-requests market data for all active subscriptions`() {
        val manager = SubscriptionManager(ibkrClient, 100)

        manager.subscribe(1001) { _, _ -> }
        manager.subscribe(1002) { _, _ -> }

        manager.resubscribeAll()

        verify(exactly = 1) { ibkrClient.requestMarketData(1001, any()) }
        verify(exactly = 1) { ibkrClient.requestMarketData(1002, any()) }
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

        verify(exactly = 0) { ibkrClient.requestMarketData(1001, any()) }
        verify(exactly = 1) { ibkrClient.requestMarketData(1002, any()) }
    }

    @Test
    fun `resubscribeAll handles exception and continues`() {
        val manager = SubscriptionManager(ibkrClient, 100)

        manager.subscribe(1001) { _, _ -> }
        manager.subscribe(1002) { _, _ -> }

        every { ibkrClient.requestMarketData(1001, any()) } throws RuntimeException("network error")
        every { ibkrClient.requestMarketData(1002, any()) } returns Unit

        manager.resubscribeAll()

        verify(exactly = 1) { ibkrClient.requestMarketData(1001, any()) }
        verify(exactly = 1) { ibkrClient.requestMarketData(1002, any()) }
    }

    @Test
    fun `resubscribeAll retries on repeated failures`() {
        val manager = SubscriptionManager(ibkrClient, 100)

        manager.subscribe(1001) { _, _ -> }

        every { ibkrClient.requestMarketData(1001, any()) } throws RuntimeException("network error")

        manager.resubscribeAll()
        manager.resubscribeAll()
        manager.resubscribeAll()

        verify(exactly = 3) { ibkrClient.requestMarketData(1001, any()) }
    }

    @RepeatedTest(5)
    fun `resubscribeAll is safe under concurrent execution`() {
        val manager = SubscriptionManager(ibkrClient, 100)
        manager.subscribe(1001) { _, _ -> }

        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)
        val futures = (1..3).map {
            executor.submit {
                latch.await()
                manager.resubscribeAll()
            }
        }
        latch.countDown()
        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        verify(atLeast = 1) { ibkrClient.requestMarketData(1001, any()) }
    }
}
