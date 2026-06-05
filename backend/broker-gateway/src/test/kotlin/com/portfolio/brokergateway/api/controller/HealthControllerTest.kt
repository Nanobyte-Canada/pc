package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.config.AdapterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class HealthControllerTest {

    @Test
    fun `health returns UP with broker statuses`() {
        val adapter = mockk<BrokerAdapter>()
        every { adapter.brokerType } returns BrokerType.IBKR
        val registry = AdapterRegistry(listOf(adapter))
        val controller = HealthController(registry)

        val response = controller.health()
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("UP", body.status)
        assertEquals(3, body.brokers.size)

        val ibkr = body.brokers.first { it.brokerType == BrokerType.IBKR }
        assertEquals(true, ibkr.enabled)
        assertEquals("OK", ibkr.status)

        val qt = body.brokers.first { it.brokerType == BrokerType.QUESTRADE }
        assertEquals(false, qt.enabled)
        assertEquals("DISABLED", qt.status)
    }

    @Test
    fun `brokerHealth returns status for specific broker`() {
        val registry = AdapterRegistry(emptyList())
        val controller = HealthController(registry)

        val response = controller.brokerHealth(BrokerType.IBKR)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, response.body!!.enabled)
        assertEquals("DISABLED", response.body!!.status)
    }
}
