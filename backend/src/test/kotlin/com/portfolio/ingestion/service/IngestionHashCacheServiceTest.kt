package com.portfolio.ingestion.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class IngestionHashCacheServiceTest {

    private val redis = mockk<StringRedisTemplate>()
    private val valueOps = mockk<ValueOperations<String, String>>()
    private lateinit var service: IngestionHashCacheService

    @BeforeEach
    fun setUp() {
        every { redis.opsForValue() } returns valueOps
        service = IngestionHashCacheService(redis)
    }

    @Test
    fun `same payload returns isChanged=false`() {
        val payload = """{"ticker":"AAPL","name":"Apple Inc"}"""
        val hash = service.computeHash(payload)

        every { valueOps.get("ingestion:stock:AAPL:hash") } returns hash

        assertFalse(service.isChanged("stock", "AAPL", payload))
    }

    @Test
    fun `different payload returns isChanged=true and stores new hash`() {
        val oldPayload = """{"ticker":"AAPL","name":"Apple Inc"}"""
        val newPayload = """{"ticker":"AAPL","name":"Apple Inc.","marketCap":3000000}"""
        val oldHash = service.computeHash(oldPayload)

        every { valueOps.get("ingestion:stock:AAPL:hash") } returns oldHash
        every { valueOps.set(any(), any(), any()) } returns Unit

        assertTrue(service.isChanged("stock", "AAPL", newPayload))

        verify { valueOps.set("ingestion:stock:AAPL:hash", service.computeHash(newPayload), any()) }
    }

    @Test
    fun `cache miss (null) returns isChanged=true and stores hash`() {
        val payload = """{"ticker":"MSFT"}"""

        every { valueOps.get("ingestion:stock:MSFT:hash") } returns null
        every { valueOps.set(any(), any(), any()) } returns Unit

        assertTrue(service.isChanged("stock", "MSFT", payload))
    }

    @Test
    fun `Redis failure returns isChanged=true as safe fallback`() {
        val payload = """{"ticker":"GOOG"}"""

        every { valueOps.get(any()) } throws RuntimeException("Redis connection refused")

        // Should return true (re-enrich) on Redis failure
        assertTrue(service.isChanged("stock", "GOOG", payload))
    }

    @Test
    fun `computeHash returns 64-character hex string`() {
        val hash = service.computeHash("test payload")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it.isLetterOrDigit() })
    }

    @Test
    fun `computeHash is deterministic for same input`() {
        val payload = "same payload"
        assertEquals(service.computeHash(payload), service.computeHash(payload))
    }

    @Test
    fun `computeHash differs for different input`() {
        assertNotEquals(service.computeHash("payload A"), service.computeHash("payload B"))
    }
}
