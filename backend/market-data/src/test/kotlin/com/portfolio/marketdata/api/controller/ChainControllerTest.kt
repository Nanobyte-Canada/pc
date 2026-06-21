package com.portfolio.marketdata.api.controller

import com.portfolio.common.domain.Greeks
import com.portfolio.common.domain.GreeksSource
import com.portfolio.common.domain.OptionQuote
import com.portfolio.common.domain.OptionType
import com.portfolio.common.domain.OptionsChain
import com.portfolio.common.domain.StrikeData
import com.portfolio.marketdata.config.AppProperties
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.ibkr.MarketDataSnapshot
import com.portfolio.marketdata.ibkr.OptionContractDetails
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionsChainBuilder
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class ChainControllerTest {

    private val quoteCacheService = mockk<QuoteCacheService>(relaxed = true)
    private val chainBuilder = mockk<OptionsChainBuilder>(relaxed = true)
    private val greeksCalculator = mockk<GreeksCalculator>(relaxed = true)
    private val ibkrClient = mockk<IbkrClient>(relaxed = true)
    private val properties = AppProperties(maxDteDefault = 90)

    private lateinit var controller: ChainController

    @BeforeEach
    fun setup() {
        every { greeksCalculator.calculate(any(), any(), any(), any(), any(), any()) } returns Greeks(
            delta = BigDecimal.ZERO, gamma = BigDecimal.ZERO, theta = BigDecimal.ZERO,
            vega = BigDecimal.ZERO, rho = BigDecimal.ZERO, source = GreeksSource.BLACK_SCHOLES
        )
        controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, properties, 15, 2)
    }

    @Test
    fun `getChain returns 503 when IBKR not connected`() {
        every { ibkrClient.isConnected() } returns false
        every { quoteCacheService.getChain("SPY") } returns null

        val response = controller.getChain("SPY")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getChainWithGreeks returns 503 when IBKR not connected`() {
        every { ibkrClient.isConnected() } returns false
        every { quoteCacheService.getChain("SPY") } returns null

        val response = controller.getChainWithGreeks("SPY")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getExpirations returns 503 when IBKR not connected`() {
        every { ibkrClient.isConnected() } returns false
        every { quoteCacheService.getChain("SPY") } returns null

        val response = controller.getExpirations("SPY", null)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getChainForExpiry returns 503 when IBKR not connected`() {
        every { ibkrClient.isConnected() } returns false

        val response = controller.getChainForExpiry("SPY", "20260618", 0.45, 25, "both")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getChain returns 200 from cache without IBKR call`() {
        val cachedChain = OptionsChain(
            underlying = "SPY",
            spotPrice = BigDecimal.valueOf(400),
            expirations = emptyMap()
        )
        every { quoteCacheService.getChain("SPY") } returns cachedChain

        val response = controller.getChain("SPY")

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 0) { ibkrClient.isConnected() }
    }

    @Test
    fun `getExpirations returns cached expirations filtered by default maxDte`() {
        val today = LocalDate.now()
        val within90 = today.plusDays(30)
        val beyond90 = today.plusDays(180)
        val allExpirations = listOf(within90, beyond90)

        every { quoteCacheService.getChain("AAPL") } returns null
        every { quoteCacheService.getExpirations("AAPL") } returns allExpirations
        every { quoteCacheService.getQuote("AAPL") } returns mockk { every { last } returns BigDecimal("150.00") }
        every { ibkrClient.isConnected() } returns true

        val response = controller.getExpirations("AAPL", null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(within90), response.body!!.expirations)
        verify(exactly = 0) { ibkrClient.requestOptionExpirations(any()) }
    }

    @Test
    fun `getExpirations fetches from IBKR on cache miss and filters`() {
        val today = LocalDate.now()
        val within90 = today.plusDays(30)
        val beyond90 = today.plusDays(180)

        every { quoteCacheService.getChain("SPY") } returns null
        every { quoteCacheService.getExpirations("SPY") } returns null
        every { ibkrClient.requestOptionExpirations("SPY") } returns listOf(within90, beyond90)
        every { quoteCacheService.getQuote("SPY") } returns mockk { every { last } returns BigDecimal("450.00") }
        every { ibkrClient.isConnected() } returns true

        val response = controller.getExpirations("SPY", null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(within90), response.body!!.expirations)
    }

    @Test
    fun `getExpirations respects custom maxDte parameter`() {
        val today = LocalDate.now()
        val in30 = today.plusDays(30)
        val in60 = today.plusDays(60)
        val in120 = today.plusDays(120)

        every { quoteCacheService.getChain("QQQ") } returns null
        every { quoteCacheService.getExpirations("QQQ") } returns listOf(in30, in60, in120)
        every { quoteCacheService.getQuote("QQQ") } returns mockk { every { last } returns BigDecimal("400.00") }
        every { ibkrClient.isConnected() } returns true

        val response = controller.getExpirations("QQQ", 45)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(in30), response.body!!.expirations)
    }

    @Test
    fun `getExpirations returns 404 when IBKR returns empty`() {
        every { quoteCacheService.getChain("XYZ") } returns null
        every { quoteCacheService.getExpirations("XYZ") } returns null
        every { ibkrClient.requestOptionExpirations("XYZ") } returns emptyList()
        every { quoteCacheService.getQuote("XYZ") } returns mockk { every { last } returns BigDecimal("10.00") }
        every { ibkrClient.isConnected() } returns true

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

        every { ibkrClient.isConnected() } returns true
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

        every { ibkrClient.isConnected() } returns true
        every { quoteCacheService.getQuote("SPY") } returns mockk { every { last } returns BigDecimal("455.00") }
        every { ibkrClient.requestContractDetails("SPY", "OPT", expiry, any(), any()) } returns contracts
        every { ibkrClient.requestMarketDataSnapshot(any()) } returns null
        every { chainBuilder.build("SPY", any(), any()) } returns chain

        val response = controller.getChainForExpiry("SPY", expiry.toString(), 0.45, 25, "both")

        assertEquals(HttpStatus.OK, response.statusCode)
        verify { ibkrClient.requestMarketDataSnapshot(1) }
        verify { ibkrClient.requestMarketDataSnapshot(2) }
    }

    @Test
    fun `getChainForExpiry returns 200 from cache without IBKR call`() {
        val expiry = LocalDate.now().plusDays(30)
        val cachedChain = OptionsChain(
            underlying = "SPY",
            spotPrice = BigDecimal.valueOf(450),
            expirations = mapOf(expiry to mapOf(BigDecimal("450") to StrikeData(null, null)))
        )
        every { quoteCacheService.getChain("SPY") } returns cachedChain

        val response = controller.getChainForExpiry("SPY", expiry.toString(), 0.45, 25, "both")

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 0) { ibkrClient.isConnected() }
        verify(exactly = 0) { ibkrClient.requestContractDetails(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getChainForExpiry returns 503 when cache miss and IBKR not connected`() {
        val expiry = LocalDate.now().plusDays(30)
        every { quoteCacheService.getChain("SPY") } returns null
        every { ibkrClient.isConnected() } returns false

        val response = controller.getChainForExpiry("SPY", expiry.toString(), 0.45, 25, "both")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getChain returns 503 when IBKR disconnects mid-build`() {
        every { quoteCacheService.getChain("SPY") } returns null
        every { ibkrClient.isConnected() } returnsMany listOf(true, false)
        every { quoteCacheService.getQuote("SPY") } returns mockk { every { last } returns BigDecimal("450.00") }
        every { ibkrClient.requestOptionChain("SPY") } returns emptyList()

        val response = controller.getChain("SPY")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getChainForExpiry returns 503 when IBKR disconnects mid-build`() {
        val expiry = LocalDate.now().plusDays(30)
        every { quoteCacheService.getChain("SPY") } returns null
        every { ibkrClient.isConnected() } returnsMany listOf(true, false)
        every { quoteCacheService.getQuote("SPY") } returns mockk { every { last } returns BigDecimal("450.00") }
        every { ibkrClient.requestContractDetails("SPY", "OPT", expiry, any(), any()) } returns emptyList()

        val response = controller.getChainForExpiry("SPY", expiry.toString(), 0.45, 25, "both")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }
}
