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
        every { adapter.brokerType } returns BrokerType.QUESTRADE
        val registry = AdapterRegistry(listOf(adapter))
        val controller = HealthController(registry)

        val response = controller.health()
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("UP", body.status)
        assertEquals(2, body.brokers.size)

        val qt = body.brokers.first { it.brokerType == BrokerType.QUESTRADE }
        assertEquals(true, qt.enabled)
        assertEquals("OK", qt.status)

        val ws = body.brokers.first { it.brokerType == BrokerType.WEALTHSIMPLE }
        assertEquals(false, ws.enabled)
        assertEquals("DISABLED", ws.status)
    }

    @Test
    fun `brokerHealth returns status for specific broker`() {
        val registry = AdapterRegistry(emptyList())
        val controller = HealthController(registry)

        val response = controller.brokerHealth(BrokerType.QUESTRADE)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, response.body!!.enabled)
        assertEquals("DISABLED", response.body!!.status)
    }
}
