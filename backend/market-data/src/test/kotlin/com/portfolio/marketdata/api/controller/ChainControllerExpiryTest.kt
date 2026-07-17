package com.portfolio.marketdata.api.controller

import com.portfolio.marketdata.config.AppProperties
import com.portfolio.marketdata.distribution.ExpiryCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class ChainControllerExpiryTest {

    private val quoteCacheService = mockk<com.portfolio.marketdata.distribution.QuoteCacheService>(relaxed = true)
    private val ibkrClient = mockk<IbkrClient>()
    private val expiryCacheService = mockk<ExpiryCacheService>(relaxed = true)
    private val properties = AppProperties(maxDteDefault = 90)
    private lateinit var controller: ChainController

    @BeforeEach
    fun setup() {
        every { quoteCacheService.getChain(any()) } returns null
        every { quoteCacheService.getExpirations(any()) } returns null
        controller = ChainController(
            quoteCacheService = quoteCacheService,
            chainBuilder = mockk(relaxed = true),
            greeksCalculator = mockk(relaxed = true),
            ibkrClient = ibkrClient,
            properties = properties,
            buildTimeoutSeconds = 15,
            buildMaxThreads = 2,
            expiryCacheService = expiryCacheService
        )
    }

    @Test
    fun `getExpirations uses ExpiryCacheService first`() {
        val cachedExpirations = listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 25))
        every { expiryCacheService.getExpiry("SOXL") } returns cachedExpirations
        every { quoteCacheService.getQuote("SOXL") } returns mockk { every { last } returns BigDecimal("50.00") }

        val response = controller.getExpirations("SOXL", null)

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body?.expirations?.size)
        verify(exactly = 0) { ibkrClient.requestOptionExpirations(any()) }
    }

    @Test
    fun `getExpirations falls back to IBKR on cache miss`() {
        val ibkrExpirations = listOf(LocalDate.of(2026, 8, 1))
        every { expiryCacheService.getExpiry("SOXL") } returns null
        every { ibkrClient.isConnected() } returns true
        every { ibkrClient.requestOptionExpirations("SOXL") } returns ibkrExpirations
        every { quoteCacheService.getQuote("SOXL") } returns mockk { every { last } returns BigDecimal("50.00") }

        val response = controller.getExpirations("SOXL", null)

        assertEquals(200, response.statusCode.value())
        assertEquals(1, response.body?.expirations?.size)
        verify { expiryCacheService.cacheExpiry("SOXL", ibkrExpirations) }
    }

    @Test
    fun `getExpirations filters by maxDte`() {
        val expirations = listOf(
            LocalDate.now().plusDays(10),
            LocalDate.now().plusDays(50),
            LocalDate.now().plusDays(100)
        )
        every { expiryCacheService.getExpiry("SOXL") } returns expirations
        every { quoteCacheService.getQuote("SOXL") } returns mockk { every { last } returns BigDecimal("50.00") }

        val response = controller.getExpirations("SOXL", 30)

        assertEquals(200, response.statusCode.value())
        assertEquals(1, response.body?.expirations?.size)
    }
}
