package com.portfolio.marketdata.distribution

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuoteCacheServiceTest {

    private val redisTemplate = mockk<RedisTemplate<String, String>>()
    private val opsForValue = mockk<ValueOperations<String, String>>()
    private lateinit var service: QuoteCacheService

    @BeforeEach
    fun setup() {
        every { redisTemplate.opsForValue() } returns opsForValue
        service = QuoteCacheService(redisTemplate)
    }

    @Test
    fun `cacheExpirations stores JSON with 24h TTL`() {
        val expirations = listOf(
            LocalDate.of(2026, 6, 20),
            LocalDate.of(2026, 7, 18)
        )
        every { opsForValue.set(any(), any(), any(), any()) } just Runs

        service.cacheExpirations("AAPL", expirations)

        verify {
            opsForValue.set(
                "expirations:AAPL",
                match { it.contains("2026-06-20") && it.contains("2026-07-18") },
                24L,
                TimeUnit.HOURS
            )
        }
    }

    @Test
    fun `getExpirations returns cached list`() {
        every { opsForValue.get("expirations:SPY") } returns """["2026-06-20","2026-07-18"]"""

        val result = service.getExpirations("SPY")

        assertEquals(listOf(LocalDate.of(2026, 6, 20), LocalDate.of(2026, 7, 18)), result)
    }

    @Test
    fun `getExpirations returns null on cache miss`() {
        every { opsForValue.get("expirations:MSFT") } returns null

        assertNull(service.getExpirations("MSFT"))
    }

    @Test
    fun `getExpirations returns null on malformed JSON`() {
        every { opsForValue.get("expirations:BAD") } returns "not-json"

        assertNull(service.getExpirations("BAD"))
    }
}
