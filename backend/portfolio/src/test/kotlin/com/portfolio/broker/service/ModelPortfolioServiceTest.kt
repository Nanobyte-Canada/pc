package com.portfolio.broker.service

import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

class ModelPortfolioServiceTest {

    private val modelRepository = mockk<ModelPortfolioRepository>()
    private val allocationRepository = mockk<ModelPortfolioAllocationRepository>()
    private val connectionRepository = mockk<BrokerConnectionRepository>()
    private val instrumentLookup = mockk<com.portfolio.service.IngestionInstrumentLookupService>(relaxed = true)
    private val lookThroughService = mockk<com.portfolio.service.LookThroughService>(relaxed = true)
    private val service = ModelPortfolioService(
        modelRepository, allocationRepository, connectionRepository, instrumentLookup, lookThroughService
    )

    @Test
    fun `applyToAccounts links model to multiple connections`() {
        val model = ModelPortfolio(id = 1, name = "Growth", riskLevel = RiskLevel.HIGH, isSystem = true)
        val conn1 = mockk<BrokerConnection>(relaxed = true)
        val conn2 = mockk<BrokerConnection>(relaxed = true)

        every { modelRepository.findById(1) } returns Optional.of(model)
        every { connectionRepository.findByIdAndUserId(10, 1) } returns conn1
        every { connectionRepository.findByIdAndUserId(20, 1) } returns conn2
        every { connectionRepository.save(any()) } returnsArgument 0

        service.applyToAccounts(userId = 1, modelId = 1, connectionIds = listOf(10, 20))

        verify { conn1.modelPortfolio = model }
        verify { conn2.modelPortfolio = model }
        verify(exactly = 2) { connectionRepository.save(any()) }
    }

    @Test
    fun `applyToAccounts throws for invalid connection`() {
        val model = ModelPortfolio(id = 1, name = "Growth", riskLevel = RiskLevel.HIGH, isSystem = true)
        every { modelRepository.findById(1) } returns Optional.of(model)
        every { connectionRepository.findByIdAndUserId(999, 1) } returns null

        assertThrows<IllegalArgumentException> {
            service.applyToAccounts(userId = 1, modelId = 1, connectionIds = listOf(999))
        }
    }

    @Test
    fun `applyToAccounts replaces existing model on account`() {
        val oldModel = ModelPortfolio(id = 1, name = "Conservative", riskLevel = RiskLevel.LOW, isSystem = true)
        val newModel = ModelPortfolio(id = 2, name = "Growth", riskLevel = RiskLevel.HIGH, isSystem = true)
        val conn = mockk<BrokerConnection>(relaxed = true)

        every { conn.modelPortfolio } returns oldModel
        every { modelRepository.findById(2) } returns Optional.of(newModel)
        every { connectionRepository.findByIdAndUserId(10, 1) } returns conn
        every { connectionRepository.save(any()) } returnsArgument 0

        service.applyToAccounts(userId = 1, modelId = 2, connectionIds = listOf(10))

        verify { conn.modelPortfolio = newModel }
    }

    @Test
    fun `applyToAccounts with empty list does nothing`() {
        val model = ModelPortfolio(id = 1, name = "Growth", riskLevel = RiskLevel.HIGH, isSystem = true)
        every { modelRepository.findById(1) } returns Optional.of(model)

        service.applyToAccounts(userId = 1, modelId = 1, connectionIds = emptyList())

        verify(exactly = 0) { connectionRepository.save(any()) }
    }
}
