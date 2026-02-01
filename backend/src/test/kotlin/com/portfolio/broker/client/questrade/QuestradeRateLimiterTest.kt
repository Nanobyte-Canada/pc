package com.portfolio.broker.client.questrade

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestradeRateLimiterTest {

    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
    }

    @Test
    fun `tryAcquire succeeds when capacity available`() = runBlocking {
        val limiter = QuestradeRateLimiter(
            requestsPerSecond = 10,
            burstCapacity = 20,
            meterRegistry = meterRegistry
        )

        assertTrue(limiter.tryAcquire(), "First request should succeed")
    }

    @Test
    fun `tryAcquire returns false when burst capacity exhausted`() = runBlocking {
        val limiter = QuestradeRateLimiter(
            requestsPerSecond = 1,
            burstCapacity = 3,
            meterRegistry = meterRegistry
        )

        // First 3 requests should succeed (burst capacity)
        assertTrue(limiter.tryAcquire(), "Request 1 should succeed")
        assertTrue(limiter.tryAcquire(), "Request 2 should succeed")
        assertTrue(limiter.tryAcquire(), "Request 3 should succeed")

        // 4th request should fail - burst exhausted
        assertFalse(limiter.tryAcquire(), "Request 4 should fail - burst exhausted")
    }

    @Test
    fun `reset restores full capacity`() = runBlocking {
        val limiter = QuestradeRateLimiter(
            requestsPerSecond = 1,
            burstCapacity = 2,
            meterRegistry = meterRegistry
        )

        // Exhaust capacity
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())

        // Reset
        limiter.reset()

        // Should work again
        assertTrue(limiter.tryAcquire(), "Request after reset should succeed")
    }

    @Test
    fun `multiple limiters are independent`() = runBlocking {
        val limiter1 = QuestradeRateLimiter(
            requestsPerSecond = 1,
            burstCapacity = 1,
            meterRegistry = meterRegistry
        )
        val limiter2 = QuestradeRateLimiter(
            requestsPerSecond = 1,
            burstCapacity = 1,
            meterRegistry = meterRegistry
        )

        // Exhaust limiter1
        assertTrue(limiter1.tryAcquire())
        assertFalse(limiter1.tryAcquire())

        // limiter2 should still have capacity
        assertTrue(limiter2.tryAcquire(), "Second limiter should be independent")
    }
}
