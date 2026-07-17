package com.portfolio.marketdata.distribution

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.portfolio.marketdata.config.ExpiryProperties
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpiryCacheServiceTest {

    private val redisTemplate = mockk<RedisTemplate<String, String>>()
    private val opsForValue = mockk<ValueOperations<String, String>>()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(kotlinModule())
    }
    private val properties = ExpiryProperties(
        cache = ExpiryProperties.Cache(ttlDays = 90)
    )
    private lateinit var service: ExpiryCacheService

    @BeforeEach
    fun setup() {
        every { redisTemplate.opsForValue() } returns opsForValue
        every { opsForValue.set(any(), any(), any(), any()) } just runs
        service = ExpiryCacheService(redisTemplate, objectMapper, properties)
    }

    @Test
    fun `cacheExpiry stores expirations with correct key and TTL`() {
        val symbol = "SOXL"
        val expirations = listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 25))
        val expectedJson = objectMapper.writeValueAsString(expirations)
        val expectedTtlDays = 90L

        service.cacheExpiry(symbol, expirations)

        verify {
            opsForValue.set(
                "expiry:$symbol",
                expectedJson,
                expectedTtlDays,
                java.util.concurrent.TimeUnit.DAYS
            )
        }
    }

    @Test
    fun `getExpiry returns expirations when cache hit`() {
        val symbol = "SOXL"
        val expirations = listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 25))
        val json = objectMapper.writeValueAsString(expirations)

        every { opsForValue.get("expiry:$symbol") } returns json

        val result = service.getExpiry(symbol)

        assertEquals(expirations, result)
    }

    @Test
    fun `getExpiry returns null when cache miss`() {
        val symbol = "SOXL"
        every { opsForValue.get("expiry:$symbol") } returns null

        val result = service.getExpiry(symbol)

        assertNull(result)
    }

    @Test
    fun `getExpiry returns null when deserialization fails`() {
        val symbol = "SOXL"
        every { opsForValue.get("expiry:$symbol") } returns "invalid json"

        val result = service.getExpiry(symbol)

        assertNull(result)
    }

    @Test
    fun `cacheExpiry skips empty list to avoid poisoning cache`() {
        service.cacheExpiry("SOXL", emptyList())

        verify(exactly = 0) { opsForValue.set(any(), any(), any(), any()) }
    }
}
