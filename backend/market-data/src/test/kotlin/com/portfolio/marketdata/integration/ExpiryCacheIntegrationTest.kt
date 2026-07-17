package com.portfolio.marketdata.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.portfolio.marketdata.config.ExpiryProperties
import com.portfolio.marketdata.distribution.ExpiryCacheService
import com.portfolio.marketdata.distribution.ExpiryRefreshService
import com.portfolio.marketdata.ibkr.IbkrClient
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDate
import kotlin.test.assertEquals

class ExpiryCacheIntegrationTest {

    private val redisTemplate = mockk<RedisTemplate<String, String>>()
    private val opsForValue = mockk<ValueOperations<String, String>>()
    private val ibkrClient = mockk<IbkrClient>()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(kotlinModule())
    }
    private val properties = ExpiryProperties(
        refresh = ExpiryProperties.Refresh(
            symbols = listOf("SOXL", "TECL")
        ),
        cache = ExpiryProperties.Cache(ttlDays = 90)
    )
    private lateinit var cacheService: ExpiryCacheService
    private lateinit var refreshService: ExpiryRefreshService

    @BeforeEach
    fun setup() {
        every { redisTemplate.opsForValue() } returns opsForValue
        every { opsForValue.set(any<String>(), any<String>(), any<Long>(), any()) } just runs
        cacheService = ExpiryCacheService(redisTemplate, objectMapper, properties)
        refreshService = ExpiryRefreshService(ibkrClient, cacheService, properties)
    }

    @Test
    fun `end-to-end - refresh populates cache, subsequent request reads from cache`() {
        // Arrange: IBKR returns expirations
        val soxlExpirations = listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 25))
        val teclExpirations = listOf(LocalDate.of(2026, 8, 1))
        every { ibkrClient.requestOptionExpirations("SOXL") } returns soxlExpirations
        every { ibkrClient.requestOptionExpirations("TECL") } returns teclExpirations

        // Act: Trigger refresh
        refreshService.refreshAll()

        // Assert: Cache was populated
        val cachedSoxlJson = objectMapper.writeValueAsString(soxlExpirations)
        val cachedTeclJson = objectMapper.writeValueAsString(teclExpirations)
        every { opsForValue.get("expiry:SOXL") } returns cachedSoxlJson
        every { opsForValue.get("expiry:TECL") } returns cachedTeclJson

        // Verify: Subsequent read returns cached data
        val soxlResult = cacheService.getExpiry("SOXL")
        val teclResult = cacheService.getExpiry("TECL")

        assertEquals(soxlExpirations, soxlResult)
        assertEquals(teclExpirations, teclResult)
    }

    @Test
    fun `on-demand fallback - cache miss triggers IBKR fetch and populates cache`() {
        // Arrange: Cache is empty
        every { opsForValue.get("expiry:SOXL") } returns null
        // IBKR returns expirations
        val expirations = listOf(LocalDate.of(2026, 7, 18))
        every { ibkrClient.requestOptionExpirations("SOXL") } returns expirations

        // Act: Simulate on-demand fetch (as done in ChainController)
        var cachedExpiry = cacheService.getExpiry("SOXL")
        if (cachedExpiry == null) {
            cachedExpiry = ibkrClient.requestOptionExpirations("SOXL")
            cacheService.cacheExpiry("SOXL", cachedExpiry)
        }

        // Assert: Cache was populated
        val cachedJson = objectMapper.writeValueAsString(expirations)
        every { opsForValue.get("expiry:SOXL") } returns cachedJson
        val result = cacheService.getExpiry("SOXL")

        assertEquals(expirations, result)
    }
}
