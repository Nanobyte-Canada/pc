package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.BrokerType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestradeAdapterTest {

    private val config = QuestradeConfig(enabled = true)
    private val adapter = QuestradeAdapter(config)

    @Test
    fun `brokerType is QUESTRADE`() {
        assertEquals(BrokerType.QUESTRADE, adapter.brokerType)
    }

    @Test
    fun `capabilities reports Questrade features`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.QUESTRADE, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertTrue(caps.isOfficialApi)
        assertEquals(false, caps.supportsFractionalShares)
        assertTrue(caps.supportedOrderTypes.size == 4)
    }
}
