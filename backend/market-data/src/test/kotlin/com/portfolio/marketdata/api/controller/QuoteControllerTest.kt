package com.portfolio.marketdata.api.controller

import com.portfolio.marketdata.db.repository.UnderlyingPriceRepository
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class QuoteControllerTest {

    private val quoteCacheService = mockk<QuoteCacheService>(relaxed = true)
    private val underlyingPriceRepository = mockk<UnderlyingPriceRepository>(relaxed = true)
    private val ibkrClient = mockk<IbkrClient>(relaxed = true)

    private val controller = QuoteController(quoteCacheService, underlyingPriceRepository, ibkrClient)

    @Test
    fun `getQuote returns 503 when IBKR not connected and no cached data`() {
        every { quoteCacheService.getQuote("SPY") } returns null
        every { underlyingPriceRepository.findByTickerOrderByObservedAtDesc("SPY") } returns emptyList()
        every { ibkrClient.isConnected() } returns false

        val response = controller.getQuote("SPY")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getQuote returns 200 from cache when IBKR not connected`() {
        val cachedQuote = com.portfolio.common.domain.Quote(
            symbol = "SPY",
            bid = java.math.BigDecimal("450.0"),
            ask = java.math.BigDecimal("450.10"),
            last = java.math.BigDecimal("450.05"),
            volume = 1000,
            timestamp = java.time.Instant.now()
        )
        every { quoteCacheService.getQuote("SPY") } returns cachedQuote

        val response = controller.getQuote("SPY")

        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
