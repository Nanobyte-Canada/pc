package com.portfolio.strategy.api.controller

import com.portfolio.strategy.engine.EducationEngine
import com.portfolio.strategy.engine.LegValidator
import com.portfolio.strategy.engine.StrategyCalculator
import com.portfolio.strategy.engine.StrategyRegistry
import com.portfolio.strategy.service.BrokerGatewayClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StrategyControllerTest {

    private val registry = StrategyRegistry()
    private val controller = StrategyController(
        registry = registry,
        calculator = StrategyCalculator(),
        educationEngine = EducationEngine(registry),
        legValidator = LegValidator(),
        brokerGatewayClient = BrokerGatewayClient("http://localhost:8084")
    )

    @Test
    fun `listStrategies carries butterfly leg template quantities`() {
        val responses = controller.listStrategies()
        val butterfly = responses.single { it.name == "BUTTERFLY_SPREAD" }
        val sellDto = butterfly.legTemplates.single { it.action == "SELL" }
        assertEquals(2, sellDto.quantity)
        assertEquals("CALL", sellDto.optionType)
        assertEquals("OTM_1", sellDto.strikeOffset)
    }

    @Test
    fun `getStrategyInfo carries butterfly leg template quantities`() {
        val response = controller.getStrategyInfo("BUTTERFLY_SPREAD")
        val sellDto = response.legTemplates.single { it.action == "SELL" }
        assertEquals(2, sellDto.quantity)
        assertEquals("CALL", sellDto.optionType)
        assertEquals("OTM_1", sellDto.strikeOffset)
    }

    @Test
    fun `every listed strategy exposes non-empty leg templates`() {
        val responses = controller.listStrategies()
        responses.forEach { response ->
            assertTrue(response.legTemplates.isNotEmpty()) { "${response.name} has no legTemplates" }
            assertEquals(response.legCount, response.legTemplates.size) {
                "${response.name} legCount ${response.legCount} != ${response.legTemplates.size} templates"
            }
        }
    }
}
