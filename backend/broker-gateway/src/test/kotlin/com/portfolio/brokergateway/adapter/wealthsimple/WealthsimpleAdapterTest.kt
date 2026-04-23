// test: adapter/wealthsimple/WealthsimpleAdapterTest.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.adapter.OrderType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WealthsimpleAdapterTest {

    private val config = WealthsimpleConfig(enabled = true)
    private val adapter = WealthsimpleAdapter(config)

    @Test
    fun `brokerType is WEALTHSIMPLE`() {
        assertEquals(BrokerType.WEALTHSIMPLE, adapter.brokerType)
    }

    @Test
    fun `capabilities reflects Wealthsimple limitations`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.WEALTHSIMPLE, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertFalse(caps.isOfficialApi)
        assertFalse(caps.supportsOptionPositions)
        assertFalse(caps.supportsFractionalShares)
        assertFalse(caps.supportsRealTimeData)
        assertEquals("7 trades/hour", caps.orderRateLimit)
        assertEquals(listOf(OrderType.MARKET, OrderType.LIMIT), caps.supportedOrderTypes)
    }
}
