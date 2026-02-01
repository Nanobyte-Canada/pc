package com.portfolio.broker.client.questrade

import com.portfolio.broker.config.BrokerConfig
import com.portfolio.broker.config.QuestradeConfig
import com.portfolio.broker.config.RateLimitConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
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

    private fun createConfig(requestsPerSecond: Double, burstSize: Int): BrokerConfig {
        val config = mockk<BrokerConfig>()
        val questradeConfig = mockk<QuestradeConfig>()
        val rateLimitConfig = RateLimitConfig(requestsPerSecond = requestsPerSecond, burstSize = burstSize)
        every { questradeConfig.rateLimit } returns rateLimitConfig
        every { config.questrade } returns questradeConfig
        return config
    }

    @Test
    fun `tryAcquire succeeds when capacity available`() = runBlocking {
        val config = createConfig(requestsPerSecond = 10.0, burstSize = 20)
        val limiter = QuestradeRateLimiter(config = config, meterRegistry = meterRegistry)

        assertTrue(limiter.tryAcquire(), "First request should succeed")
    }

    @Test
    fun `tryAcquire returns false when burst capacity exhausted`() = runBlocking {
        val config = createConfig(requestsPerSecond = 1.0, burstSize = 3)
        val limiter = QuestradeRateLimiter(config = config, meterRegistry = meterRegistry)

        // First 3 requests should succeed (burst capacity)
        assertTrue(limiter.tryAcquire(), "Request 1 should succeed")
        assertTrue(limiter.tryAcquire(), "Request 2 should succeed")
        assertTrue(limiter.tryAcquire(), "Request 3 should succeed")

        // 4th request should fail - burst exhausted
        assertFalse(limiter.tryAcquire(), "Request 4 should fail - burst exhausted")
    }

    @Test
    fun `availableTokens returns current capacity`() = runBlocking {
        val config = createConfig(requestsPerSecond = 1.0, burstSize = 2)
        val limiter = QuestradeRateLimiter(config = config, meterRegistry = meterRegistry)

        // Initial capacity should be equal to burst size
        assertTrue(limiter.availableTokens() >= 1.0, "Should have available tokens initially")

        // Exhaust capacity
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())

        // Should have no tokens left
        assertTrue(limiter.availableTokens() < 1.0, "Should have less than 1 token after exhaustion")
    }
}
