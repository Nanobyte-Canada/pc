package com.portfolio.marketdata.questrade

import com.portfolio.marketdata.provider.MarketDataProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestradeProviderTest {

    private lateinit var restClient: QuestradeRestClient
    private lateinit var streamClient: QuestradeStreamClient
    private lateinit var tokenManager: QuestradeTokenManager
    private lateinit var provider: QuestradeProvider

    /** Exact matches for "SPY" appear before AND after a non-matching ticker; quotable one wins. */
    private val spySearch = listOf(
        QuestradeSymbol(111, "SPY", "duplicate not quotable", "Stock", null, false, "USD", true),
        QuestradeSymbol(34987, "SPY", "SPDR S&P 500 ETF", "Stock", "ARCA", true, "USD", true),
        QuestradeSymbol(222, "SPYD", "different ticker", "Stock", "ARCA", true, "USD", true)
    )

    /** September expiry listed BEFORE August so distinct+sorted ordering is exercised. */
    private val chain = listOf(
        QuestradeExpiry(
            expiryDate = "2026-09-18T00:00:00.000000-04:00",
            optionExerciseType = "American",
            listingExchange = null,
            chainPerRoot = listOf(
                QuestradeRoot("SPY", 100, listOf(QuestradeStrike(600.0, 86915281, 86915626)))
            )
        ),
        QuestradeExpiry(
            expiryDate = "2026-08-28T00:00:00.000000-04:00",
            optionExerciseType = "American",
            listingExchange = "OPRA",
            chainPerRoot = listOf(
                QuestradeRoot("SPY", 100, listOf(
                    QuestradeStrike(600.0, 76915281, 76915626),
                    QuestradeStrike(605.0, 76915282, 76915627)
                ))
            )
        )
    )

    @BeforeEach
    fun setup() {
        restClient = mockk()
        streamClient = mockk()
        tokenManager = mockk()
        provider = QuestradeProvider(restClient, streamClient, tokenManager)
        every { restClient.searchSymbol("SPY") } returns spySearch
        every { restClient.getOptionChain(34987) } returns chain
    }

    private fun optionQuote() = QuestradeOptionQuote(
        symbolId = 76915281,
        underlyingId = 34987,
        bidPrice = 162.65,
        askPrice = 165.37,
        lastTradePrice = 166.75,
        volume = 7L,
        openInterest = 26L,
        volatility = 93.08136,
        delta = 0.988226,
        gamma = 0.000366,
        theta = -0.252878,
        vega = 0.027186,
        rho = 0.080913,
        delay = 0
    )

    @Test
    fun `stk contract details resolve exact quotable match`() {
        val details = provider.requestContractDetails("SPY", "STK")
        assertEquals(1, details.size)
        val d = details[0]
        assertEquals(34987, d.conId)
        assertEquals("SPY", d.symbol)
        assertEquals("STK", d.secType)
        assertEquals("ARCA", d.exchange)
        assertNull(d.expiry)
        assertNull(d.strike)
        assertNull(d.right)
        assertNull(d.tradingClass)
        assertNull(d.multiplier)
    }

    @Test
    fun `underlying id resolution cached across calls`() {
        provider.requestContractDetails("SPY", "STK")
        provider.requestOptionChain("SPY")
        provider.requestOptionExpirations("SPY")
        verify(exactly = 1) { restClient.searchSymbol("SPY") }
        verify(exactly = 2) { restClient.getOptionChain(34987) }
    }

    @Test
    fun `option chain flattens all expiries roots and strikes into calls and puts`() {
        val contracts = provider.requestOptionChain("SPY")
        // 2 expiries x (1 + 2 strikes) x CALL+PUT
        assertEquals(6, contracts.size)
        val call = contracts.first { it.conId == 76915281 }
        assertEquals("SPY", call.symbol)
        assertEquals("OPT", call.secType)
        assertEquals("OPRA", call.exchange)
        assertEquals(LocalDate.parse("2026-08-28"), call.expiry)
        assertEquals(0, BigDecimal.valueOf(600.0).compareTo(call.strike))
        assertEquals("C", call.right)
        assertEquals("SPY", call.tradingClass)
        assertEquals("100", call.multiplier)
        // Put from the September expiry with null listingExchange defaults to OPRA.
        val put = contracts.first { it.conId == 86915626 }
        assertEquals("P", put.right)
        assertEquals("OPRA", put.exchange)
        assertEquals(LocalDate.parse("2026-09-18"), put.expiry)
        assertTrue(contracts.any { it.conId == 76915627 && it.right == "P" })
    }

    @Test
    fun `opt contract details filter by expiry strike and right`() {
        val details = provider.requestContractDetails(
            "SPY", "OPT",
            expiry = LocalDate.parse("2026-08-28"),
            strike = BigDecimal.valueOf(605.0),
            right = "C"
        )
        assertEquals(listOf(76915282), details.map { it.conId })
    }

    @Test
    fun `option expirations are distinct and sorted`() {
        assertEquals(
            listOf(LocalDate.parse("2026-08-28"), LocalDate.parse("2026-09-18")),
            provider.requestOptionExpirations("SPY")
        )
    }

    @Test
    fun `market data snapshot maps option quote with iv percent to fraction`() {
        every { restClient.getOptionQuotes(listOf(76915281)) } returns listOf(optionQuote())
        val snap = provider.requestMarketDataSnapshot(76915281)!!
        assertEquals(76915281, snap.conId)
        assertEquals(162.65, snap.bid!!, 1e-9)
        assertEquals(165.37, snap.ask!!, 1e-9)
        assertEquals(166.75, snap.last!!, 1e-9)
        assertEquals(7L, snap.volume)
        assertEquals(0.9308136, snap.impliedVol!!, 1e-9)
        assertEquals(0.988226, snap.delta!!, 1e-9)
        assertEquals(0.000366, snap.gamma!!, 1e-9)
        assertEquals(-0.252878, snap.theta!!, 1e-9)
        assertEquals(0.027186, snap.vega!!, 1e-9)
    }

    @Test
    fun `market data snapshot falls back to stock quote without greeks`() {
        every { restClient.getOptionQuotes(listOf(34987)) } returns emptyList()
        every { restClient.getStockQuotes(listOf(34987)) } returns listOf(
            QuestradeStockQuote(34987, 763.56, 763.62, 763.59, 34215L, 0)
        )
        val snap = provider.requestMarketDataSnapshot(34987)!!
        assertEquals(34987, snap.conId)
        assertEquals(763.56, snap.bid!!, 1e-9)
        assertEquals(763.62, snap.ask!!, 1e-9)
        assertEquals(763.59, snap.last!!, 1e-9)
        assertEquals(34215L, snap.volume)
        assertNull(snap.impliedVol)
        assertNull(snap.delta)
        assertNull(snap.gamma)
        assertNull(snap.theta)
        assertNull(snap.vega)
    }

    @Test
    fun `option snapshots keyed by conId`() {
        every { restClient.getOptionQuotes(listOf(76915281, 76915626)) } returns listOf(
            optionQuote(),
            optionQuote().copy(symbolId = 76915626, bidPrice = 1.11, askPrice = 2.22)
        )
        val snaps = provider.requestOptionSnapshots(listOf(76915281, 76915626))
        assertEquals(setOf(76915281, 76915626), snaps.keys)
        assertEquals(162.65, snaps[76915281]!!.bid!!, 1e-9)
        assertEquals(1.11, snaps[76915626]!!.bid!!, 1e-9)
    }

    @Test
    fun `lifecycle and market data delegate to stream client and token manager`() {
        val accessToken = AccessToken("tok", "https://api01.iq.questrade.com/", 9999999999L)
        every { tokenManager.getValidAccessToken() } returns accessToken
        every { streamClient.isConnected() } returns true
        every { streamClient.subscribe(any(), any()) } returns Unit
        every { streamClient.unsubscribe(any()) } returns Unit
        every { streamClient.setReconnectHandler(any()) } returns Unit
        every { streamClient.connect() } returns Unit
        every { streamClient.shutdown() } returns Unit

        provider.connect()
        verify(exactly = 1) { tokenManager.getValidAccessToken() }
        verify(exactly = 1) { streamClient.connect() }

        assertTrue(provider.isConnected())
        verify(exactly = 1) { streamClient.isConnected() }

        val callback: (Int, Double) -> Unit = { _, _ -> }
        provider.requestMarketData(76915281, callback)
        provider.cancelMarketData(76915281)
        verify(exactly = 1) { streamClient.subscribe(76915281, callback) }
        verify(exactly = 1) { streamClient.unsubscribe(76915281) }

        val handler = Runnable {}
        provider.registerReconnectHandler(handler)
        verify(exactly = 1) { streamClient.setReconnectHandler(handler) }

        provider.disconnect()
        verify(exactly = 1) { streamClient.shutdown() }
    }
}
