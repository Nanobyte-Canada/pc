package com.portfolio.marketdata.distribution

import com.portfolio.marketdata.config.ExpiryProperties
import com.portfolio.marketdata.ibkr.IbkrClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ExpiryRefreshServiceTest {

    private val ibkrClient = mockk<IbkrClient>()
    private val expiryCacheService = mockk<ExpiryCacheService>(relaxed = true)
    private val properties = ExpiryProperties(
        refresh = ExpiryProperties.Refresh(
            symbols = listOf("SOXL", "TECL", "TQQQ")
        )
    )
    private lateinit var service: ExpiryRefreshService

    @BeforeEach
    fun setup() {
        service = ExpiryRefreshService(ibkrClient, expiryCacheService, properties)
    }

    @Test
    fun `refreshAll fetches and caches expirations for all configured symbols`() {
        val soxlExpirations = listOf(LocalDate.of(2026, 7, 18))
        val teclExpirations = listOf(LocalDate.of(2026, 7, 25))
        val tqqqExpirations = listOf(LocalDate.of(2026, 8, 1))

        every { ibkrClient.requestOptionExpirations("SOXL") } returns soxlExpirations
        every { ibkrClient.requestOptionExpirations("TECL") } returns teclExpirations
        every { ibkrClient.requestOptionExpirations("TQQQ") } returns tqqqExpirations

        service.refreshAll()

        verify { expiryCacheService.cacheExpiry("SOXL", soxlExpirations) }
        verify { expiryCacheService.cacheExpiry("TECL", teclExpirations) }
        verify { expiryCacheService.cacheExpiry("TQQQ", tqqqExpirations) }
    }

    @Test
    fun `refreshSymbol fetches and caches expirations for single symbol`() {
        val expirations = listOf(LocalDate.of(2026, 7, 18))
        every { ibkrClient.requestOptionExpirations("SOXL") } returns expirations

        service.refreshSymbol("SOXL")

        verify { expiryCacheService.cacheExpiry("SOXL", expirations) }
    }

    @Test
    fun `refreshSymbol handles IBKR failure gracefully`() {
        every { ibkrClient.requestOptionExpirations("SOXL") } throws RuntimeException("IBKR disconnected")

        service.refreshSymbol("SOXL")

        verify(exactly = 0) { expiryCacheService.cacheExpiry(any(), any()) }
    }

    @Test
    fun `refreshAll continues when one symbol fails`() {
        every { ibkrClient.requestOptionExpirations("SOXL") } throws RuntimeException("IBKR disconnected")
        every { ibkrClient.requestOptionExpirations("TECL") } returns listOf(LocalDate.of(2026, 7, 25))
        every { ibkrClient.requestOptionExpirations("TQQQ") } returns listOf(LocalDate.of(2026, 8, 1))

        service.refreshAll()

        verify(exactly = 0) { expiryCacheService.cacheExpiry("SOXL", any()) }
        verify { expiryCacheService.cacheExpiry("TECL", any()) }
        verify { expiryCacheService.cacheExpiry("TQQQ", any()) }
    }
}
