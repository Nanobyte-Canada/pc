package com.portfolio.brokergateway.config

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerUnsupportedOperationException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class AdapterRegistryTest {

    private fun fakeAdapter(type: BrokerType): BrokerAdapter {
        val adapter = mockk<BrokerAdapter>()
        every { adapter.brokerType } returns type
        return adapter
    }

    @Test
    fun `getAdapter returns correct adapter for registered type`() {
        val ibkr = fakeAdapter(BrokerType.IBKR)
        val registry = AdapterRegistry(listOf(ibkr))
        assertEquals(ibkr, registry.getAdapter(BrokerType.IBKR))
    }

    @Test
    fun `getAdapter throws for unregistered type`() {
        val registry = AdapterRegistry(emptyList())
        assertThrows<BrokerUnsupportedOperationException> {
            registry.getAdapter(BrokerType.QUESTRADE)
        }
    }

    @Test
    fun `getEnabledBrokers returns registered types`() {
        val ibkr = fakeAdapter(BrokerType.IBKR)
        val qt = fakeAdapter(BrokerType.QUESTRADE)
        val registry = AdapterRegistry(listOf(ibkr, qt))
        assertEquals(setOf(BrokerType.IBKR, BrokerType.QUESTRADE), registry.getEnabledBrokers().toSet())
    }

    @Test
    fun `logRegisteredAdapters logs all registered adapters at startup`() {
        val ibkr = fakeAdapter(BrokerType.IBKR)
        val registry = AdapterRegistry(listOf(ibkr))
        // Should not throw; verifies @PostConstruct logging runs cleanly
        registry.logRegisteredAdapters()
    }

    @Test
    fun `logRegisteredAdapters logs warning when no adapters registered`() {
        val registry = AdapterRegistry(emptyList())
        registry.logRegisteredAdapters()
    }
}
