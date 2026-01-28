package com.portfolio.ingestion.client.alphavantage

import com.portfolio.ingestion.config.AlphaVantageProperties
import com.portfolio.ingestion.config.AlphaVantageRateLimitProperties
import com.portfolio.ingestion.config.IngestionConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlphaVantageRateLimiterTest {

    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
    }

    @Test
    fun `acquire succeeds indefinitely when dailyQuota is -1 (unlimited)`() = runBlocking {
        val config = createMockConfig(dailyQuota = -1, requestsPerMinute = 1000)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        // Make 100 requests - all should succeed
        repeat(100) {
            assertTrue(limiter.acquireAsync(), "Request $it should succeed with unlimited quota")
        }
    }

    @Test
    fun `acquire succeeds indefinitely when dailyQuota is 0 (unlimited edge case)`() = runBlocking {
        val config = createMockConfig(dailyQuota = 0, requestsPerMinute = 1000)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        repeat(100) {
            assertTrue(limiter.acquireAsync(), "Request $it should succeed with dailyQuota=0")
        }
    }

    @Test
    fun `acquire returns false when limited dailyQuota is exhausted`() = runBlocking {
        val config = createMockConfig(dailyQuota = 5, requestsPerMinute = 1000)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        // First 5 requests succeed
        repeat(5) {
            assertTrue(limiter.acquireAsync(), "Request $it should succeed")
        }

        // 6th request fails
        assertFalse(limiter.acquireAsync(), "Request 6 should fail - quota exhausted")
    }

    @Test
    fun `remainingDailyQuota returns MAX_VALUE when dailyQuota is -1`() {
        val config = createMockConfig(dailyQuota = -1, requestsPerMinute = 75)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        assertEquals(Int.MAX_VALUE, limiter.remainingDailyQuota())
    }

    @Test
    fun `remainingDailyQuota returns MAX_VALUE when dailyQuota is 0`() {
        val config = createMockConfig(dailyQuota = 0, requestsPerMinute = 75)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        assertEquals(Int.MAX_VALUE, limiter.remainingDailyQuota())
    }

    @Test
    fun `remainingDailyQuota returns correct remaining count`() = runBlocking {
        val config = createMockConfig(dailyQuota = 10, requestsPerMinute = 1000)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        assertEquals(10, limiter.remainingDailyQuota())

        repeat(3) { limiter.acquireAsync() }

        assertEquals(7, limiter.remainingDailyQuota())
    }

    @Test
    fun `isDailyQuotaExhausted always returns false when dailyQuota is -1`() = runBlocking {
        val config = createMockConfig(dailyQuota = -1, requestsPerMinute = 1000)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        repeat(100) { limiter.acquireAsync() }

        assertFalse(limiter.isDailyQuotaExhausted())
    }

    @Test
    fun `isDailyQuotaExhausted always returns false when dailyQuota is 0`() = runBlocking {
        val config = createMockConfig(dailyQuota = 0, requestsPerMinute = 1000)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        repeat(100) { limiter.acquireAsync() }

        assertFalse(limiter.isDailyQuotaExhausted())
    }

    @Test
    fun `isDailyQuotaExhausted returns true when limited quota exhausted`() = runBlocking {
        val config = createMockConfig(dailyQuota = 3, requestsPerMinute = 1000)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        assertFalse(limiter.isDailyQuotaExhausted())

        repeat(3) { limiter.acquireAsync() }

        assertTrue(limiter.isDailyQuotaExhausted())
    }

    @Test
    fun `requestsMadeToday tracks request count accurately`() = runBlocking {
        val config = createMockConfig(dailyQuota = -1, requestsPerMinute = 1000)
        val limiter = AlphaVantageRateLimiter(config, meterRegistry)

        assertEquals(0, limiter.requestsMadeToday())

        repeat(5) { limiter.acquireAsync() }

        assertEquals(5, limiter.requestsMadeToday())
    }

    private fun createMockConfig(dailyQuota: Int, requestsPerMinute: Int): IngestionConfig {
        val avConfig = mockk<AlphaVantageProperties>()
        val rateLimitConfig = mockk<AlphaVantageRateLimitProperties>()

        every { avConfig.dailyQuota } returns dailyQuota
        every { avConfig.rateLimit } returns rateLimitConfig
        every { rateLimitConfig.requestsPerMinute } returns requestsPerMinute
        // requestsPerSecond = ceil(requestsPerMinute / 60.0)
        every { rateLimitConfig.requestsPerSecond } returns kotlin.math.ceil(requestsPerMinute / 60.0).toInt()

        val config = mockk<IngestionConfig>()
        every { config.alphavantage } returns avConfig

        return config
    }
}
