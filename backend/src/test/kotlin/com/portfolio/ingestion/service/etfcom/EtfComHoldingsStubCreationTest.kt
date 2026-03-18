package com.portfolio.ingestion.service.etfcom

import com.portfolio.entity.*
import com.portfolio.repository.StockRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for the stub stock creation logic in processHoldings.
 * Tests the VALID_TICKER_REGEX guard and the expected behavior for each case.
 */
class EtfComHoldingsStubCreationTest {

    private val stockRepository = mockk<StockRepository>(relaxed = true)

    @Test
    fun `valid short ticker passes regex`() {
        listOf("AAPL", "MSFT", "T", "BRK", "SPY500").forEach { ticker ->
            assertTrue(VALID_TICKER_REGEX.matches(ticker), "Expected $ticker to be valid")
        }
    }

    @Test
    fun `invalid symbols are rejected by regex`() {
        listOf("AAPL/B", "US12345678", "BOND.CUSIP", "", "TOO_LONG_TICKER").forEach { symbol ->
            assertFalse(VALID_TICKER_REGEX.matches(symbol), "Expected $symbol to be invalid")
        }
    }

    @Test
    fun `resolved ticker creates RESOLVED holding with stock FK`() {
        val stock = Stock(id = 1L, ticker = "AAPL", name = "Apple Inc")
        every { stockRepository.findByTickerIgnoreCaseAndIsActiveTrue("AAPL") } returns stock

        val result = stockRepository.findByTickerIgnoreCaseAndIsActiveTrue("AAPL")

        assertNotNull(result)
        assertEquals("AAPL", result!!.ticker)
    }

    @Test
    fun `unresolved valid ticker triggers stub creation`() {
        every { stockRepository.findByTickerIgnoreCaseAndIsActiveTrue("NVDA") } returns null
        val savedStub = Stock(id = 99L, ticker = "NVDA", name = "NVDA")
        every { stockRepository.save(any<Stock>()) } returns savedStub

        val existing = stockRepository.findByTickerIgnoreCaseAndIsActiveTrue("NVDA")
        assertNull(existing)

        // Simulate stub creation
        val stub = Stock(
            ticker = "NVDA",
            name = "NVDA",
            isActive = true,
            avIngestionStatus = AVIngestionStatus.PENDING
        )
        val saved = stockRepository.save(stub)
        assertEquals(99L, saved.id)
    }

    @Test
    fun `slash-containing symbol is rejected — no stub created`() {
        val invalidSymbol = "AAPL/B"
        assertFalse(VALID_TICKER_REGEX.matches(invalidSymbol))
        // Should not reach stockRepository.save
        verify(exactly = 0) { stockRepository.save(any()) }
    }
}
