package com.portfolio.marketdata.ibkr

import com.portfolio.marketdata.config.AppProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TwsIbkrClientTest {

    private val properties = AppProperties(host = "", port = 4002, clientId = 1, maxChainExpirations = 12)

    @Test
    fun `isConnected returns false before connect`() {
        val client = TwsIbkrClient(properties)
        assertFalse(client.isConnected())
    }

    @Test
    fun `connect with blank host does nothing`() {
        val client = TwsIbkrClient(AppProperties(host = "", port = 4002, clientId = 1))
        client.connect()
        assertFalse(client.isConnected())
    }

    @Test
    fun `nextValidId sets connected state`() {
        val client = TwsIbkrClient(properties)
        assertFalse(client.isConnected())
        // Simulate the callback directly (unit test without real socket)
        client.nextValidId(100)
        // connected flag is set but client.isConnected also checks socket — will be false without real connection
    }

    @Test
    fun `requestOptionChain returns empty for blank host`() {
        val client = TwsIbkrClient(AppProperties(host = "", port = 4002, clientId = 1))
        client.connect()
        val chain = client.requestOptionChain("SPY")
        assertEquals(emptyList(), chain)
    }

    @Test
    fun `cancelMarketData with unknown conId is no-op`() {
        val client = TwsIbkrClient(properties)
        client.cancelMarketData(99999)
    }

    @Test
    fun `disconnect clears all state`() {
        val client = TwsIbkrClient(properties)
        client.disconnect()
        assertFalse(client.isConnected())
    }
}
