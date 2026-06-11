package com.portfolio.marketdata.api.controller

import com.portfolio.common.domain.*
import com.portfolio.marketdata.config.AppProperties
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.ibkr.OptionContractDetails
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionsChainBuilder
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class ChainControllerTest {

    private val quoteCacheService = mockk<QuoteCacheService>()
    private val chainBuilder = mockk<OptionsChainBuilder>()
    private val greeksCalculator = mockk<GreeksCalculator>()
    private val ibkrClient = mockk<IbkrClient>()
    private val properties = AppProperties(maxDteDefault = 90)

    private lateinit var controller: ChainController

    @BeforeEach
    fun setup() {
        controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, properties)
    }

    @Test
    fun `getExpirations returns cached expirations filtered by default maxDte`() {
        val today = LocalDate.now()
        val within90 = today.plusDays(30)
        val beyond90 = today.plusDays(180)
        val allExpirations = listOf(within90, beyond90)

        every { quoteCacheService.getExpirations("AAPL") } returns allExpirations
        every { quoteCacheService.getQuote("AAPL") } returns mockk { every { last } returns BigDecimal("150.00") }

        val response = controller.getExpirations("AAPL", null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(within90), response.body!!.expirations)
        verify(exactly = 0) { ibkrClient.requestOptionExpirations(any()) }
    }

    @Test
    fun `getExpirations fetches from IBKR on cache miss and caches raw result`() {
        val today = LocalDate.now()
        val within90 = today.plusDays(30)
        val beyond90 = today.plusDays(180)

        every { quoteCacheService.getExpirations("SPY") } returns null
        every { ibkrClient.requestOptionExpirations("SPY") } returns listOf(within90, beyond90)
        every { quoteCacheService.cacheExpirations("SPY", listOf(within90, beyond90)) } just Runs
        every { quoteCacheService.getQuote("SPY") } returns mockk { every { last } returns BigDecimal("450.00") }

        val response = controller.getExpirations("SPY", null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(within90), response.body!!.expirations)
        verify { quoteCacheService.cacheExpirations("SPY", listOf(within90, beyond90)) }
    }

    @Test
    fun `getExpirations respects custom maxDte parameter`() {
        val today = LocalDate.now()
        val in30 = today.plusDays(30)
        val in60 = today.plusDays(60)
        val in120 = today.plusDays(120)

        every { quoteCacheService.getExpirations("QQQ") } returns listOf(in30, in60, in120)
        every { quoteCacheService.getQuote("QQQ") } returns mockk { every { last } returns BigDecimal("400.00") }

        val response = controller.getExpirations("QQQ", 45)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(in30), response.body!!.expirations)
    }

    @Test
    fun `getExpirations returns 404 when IBKR returns empty`() {
        every { quoteCacheService.getExpirations("XYZ") } returns null
        every { ibkrClient.requestOptionExpirations("XYZ") } returns emptyList()
        every { quoteCacheService.getQuote("XYZ") } returns mockk { every { last } returns BigDecimal("10.00") }

        val response = controller.getExpirations("XYZ", null)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `getChainForExpiry with side=put filters to puts only`() {
        val expiry = LocalDate.now().plusDays(30)
        val putContract = OptionContractDetails(
            conId = 1, symbol = "AAPL", secType = "OPT", exchange = "SMART",
            expiry = expiry, strike = BigDecimal("150"), right = "P"
        )
        val callContract = OptionContractDetails(
            conId = 2, symbol = "AAPL", secType = "OPT", exchange = "SMART",
            expiry = expiry, strike = BigDecimal("150"), right = "C"
        )
        val putQuote = OptionQuote(
            underlying = "AAPL", optionType = OptionType.PUT, strike = BigDecimal("150"),
            expiry = expiry, bid = BigDecimal("2.50"), ask = BigDecimal("2.80"),
            last = BigDecimal("2.65"), volume = 100, openInterest = 0,
            greeks = null, timestamp = java.time.Instant.now()
        )
        val chain = OptionsChain("AAPL", BigDecimal("155"),
            mapOf(expiry to mapOf(BigDecimal("150") to StrikeData(call = null, put = putQuote)))
        )

        every { quoteCacheService.getQuote("AAPL") } returns mockk { every { last } returns BigDecimal("155.00") }
        every { ibkrClient.requestContractDetails("AAPL", "OPT", expiry, any(), any()) } returns listOf(putContract, callContract)
        every { ibkrClient.requestMarketDataSnapshot(1) } returns mockk {
            every { bid } returns 2.50; every { ask } returns 2.80; every { last } returns 2.65
            every { volume } returns 100; every { delta } returns -0.35; every { gamma } returns 0.02
            every { theta } returns -0.05; every { vega } returns 0.15
        }
        every { chainBuilder.build("AAPL", any(), any()) } returns chain

        val response = controller.getChainForExpiry("AAPL", expiry.toString(), 0.45, 25, "put")

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 0) { ibkrClient.requestMarketDataSnapshot(2) }
    }

    @Test
    fun `getChainForExpiry with side=both returns all contracts`() {
        val expiry = LocalDate.now().plusDays(30)
        val contracts = listOf(
            OptionContractDetails(conId = 1, symbol = "SPY", secType = "OPT", exchange = "SMART",
                expiry = expiry, strike = BigDecimal("450"), right = "P"),
            OptionContractDetails(conId = 2, symbol = "SPY", secType = "OPT", exchange = "SMART",
                expiry = expiry, strike = BigDecimal("450"), right = "C")
        )
        val chain = OptionsChain("SPY", BigDecimal("455"), emptyMap())

        every { quoteCacheService.getQuote("SPY") } returns mockk { every { last } returns BigDecimal("455.00") }
        every { ibkrClient.requestContractDetails("SPY", "OPT", expiry, any(), any()) } returns contracts
        every { ibkrClient.requestMarketDataSnapshot(any()) } returns null
        every { chainBuilder.build("SPY", any(), any()) } returns chain

        val response = controller.getChainForExpiry("SPY", expiry.toString(), 0.45, 25, "both")

        assertEquals(HttpStatus.OK, response.statusCode)
        verify { ibkrClient.requestMarketDataSnapshot(1) }
        verify { ibkrClient.requestMarketDataSnapshot(2) }
    }
}
