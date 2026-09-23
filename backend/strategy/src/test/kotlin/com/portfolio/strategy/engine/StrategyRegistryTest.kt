package com.portfolio.strategy.engine

import com.portfolio.strategy.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StrategyRegistryTest {

    private val registry = StrategyRegistry()

    @Test
    fun `butterfly middle leg requires quantity 2`() {
        val definition = registry.getDefinition(StrategyType.BUTTERFLY_SPREAD)
        val sellLeg = definition.legTemplates.single { it.action == LegAction.SELL }
        assertEquals(2, sellLeg.quantity)
    }

    @Test
    fun `butterfly outer legs default to quantity 1`() {
        val definition = registry.getDefinition(StrategyType.BUTTERFLY_SPREAD)
        val buyLegs = definition.legTemplates.filter { it.action == LegAction.BUY }
        assertEquals(2, buyLegs.size)
        assertEquals(listOf(1, 1), buyLegs.map { it.quantity })
    }

    @Test
    fun `every strategy template quantity is at least 1`() {
        registry.listAll().forEach { definition ->
            definition.legTemplates.forEach { template ->
                assertTrue(template.quantity >= 1) { "${definition.type} has quantity ${template.quantity}" }
            }
        }
    }
}
