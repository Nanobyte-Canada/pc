package com.portfolio.marketdata.api.controller

import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionsChainBuilder
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
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
}
