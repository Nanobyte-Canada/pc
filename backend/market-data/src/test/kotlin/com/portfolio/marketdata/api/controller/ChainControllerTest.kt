package com.portfolio.marketdata.api.controller

import com.portfolio.common.domain.OptionsChain
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.ibkr.MarketDataSnapshot
import com.portfolio.marketdata.ibkr.OptionContractDetails
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionsChainBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals

class ChainControllerTest {

    private val quoteCacheService = mockk<QuoteCacheService>()
    private val chainBuilder = mockk<OptionsChainBuilder>()
    private val greeksCalculator = mockk<GreeksCalculator>()
    private val ibkrClient = mockk<IbkrClient>()

    @Test
    fun `getChain returns 503 when IBKR not connected`() {
        every { ibkrClient.isConnected() } returns false
        every { quoteCacheService.getChain("SPY") } returns null

        val controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, 15, 2)
        val response = controller.getChain("SPY")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getChainWithGreeks returns 503 when IBKR not connected`() {
        every { ibkrClient.isConnected() } returns false
        every { quoteCacheService.getChain("SPY") } returns null

        val controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, 15, 2)
        val response = controller.getChainWithGreeks("SPY")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getExpirations returns 503 when IBKR not connected`() {
        every { ibkrClient.isConnected() } returns false

        val controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, 15, 2)
        val response = controller.getExpirations("SPY")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    @Test
    fun `getChainForExpiry returns 503 when IBKR not connected`() {
        every { ibkrClient.isConnected() } returns false

        val controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, 15, 2)
        val response = controller.getChainForExpiry("SPY", "20260618", 0.45, 25)

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

        val controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, 15, 2)
        val response = controller.getChain("SPY")

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 0) { ibkrClient.isConnected() }
    }

    @Test
    fun `getChain returns 200 on successful build`() {
        val expiry = LocalDate.now().plusDays(30)
        every { quoteCacheService.getChain("SPY") } returns null
        every { ibkrClient.isConnected() } returns true
        every { quoteCacheService.getQuote("SPY") } returns null
        every { ibkrClient.requestContractDetails("SPY", "STK") } returns listOf(
            OptionContractDetails(conId=1, symbol="SPY", secType="STK", exchange="SMART", expiry=null, strike=null, right=null)
        )
        every { ibkrClient.requestMarketDataSnapshot(1) } returns MarketDataSnapshot(conId=1, last=400.0)
        every { ibkrClient.requestOptionChain("SPY") } returns listOf(
            OptionContractDetails(conId=2, symbol="SPY", secType="OPT", exchange="SMART", expiry=expiry, strike=BigDecimal.valueOf(400), right="C")
        )
        every { ibkrClient.requestMarketDataSnapshot(2) } returns MarketDataSnapshot(
            conId=2, bid=5.0, ask=5.5, last=5.25, delta=0.5, gamma=0.01, theta=-0.1, vega=0.2
        )
        every { chainBuilder.build(any(), any(), any()) } returns OptionsChain(
            underlying = "SPY",
            spotPrice = BigDecimal.valueOf(400),
            expirations = emptyMap()
        )

        val controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, 15, 2)
        val response = controller.getChain("SPY")

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `getChain returns 503 on timeout`() {
        val latch = CountDownLatch(1)
        every { quoteCacheService.getChain("SPY") } returns null
        every { ibkrClient.isConnected() } returns true
        every { quoteCacheService.getQuote("SPY") } returns null
        every { ibkrClient.requestContractDetails("SPY", "STK") } returns listOf(
            OptionContractDetails(conId=1, symbol="SPY", secType="STK", exchange="SMART", expiry=null, strike=null, right=null)
        )
        every { ibkrClient.requestMarketDataSnapshot(1) } returns MarketDataSnapshot(conId=1, last=400.0)
        every { ibkrClient.requestOptionChain("SPY") } answers { latch.await(); emptyList() }

        val controller = ChainController(quoteCacheService, chainBuilder, greeksCalculator, ibkrClient, 1, 2)
        val response = controller.getChain("SPY")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        // Release the latch so the background thread can exit (clean up for other tests)
        latch.countDown()
    }
}
