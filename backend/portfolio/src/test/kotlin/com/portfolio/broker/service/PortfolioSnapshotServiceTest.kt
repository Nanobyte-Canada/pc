package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.broker.dto.DriftAnalysisResponse
import com.portfolio.broker.dto.DriftHoldingDto
import com.portfolio.broker.entity.PortfolioGroup
import com.portfolio.broker.entity.PortfolioSnapshot
import com.portfolio.broker.repository.PortfolioGroupRepository
import com.portfolio.broker.repository.PortfolioSnapshotRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PortfolioSnapshotServiceTest {

    private lateinit var service: PortfolioSnapshotService
    private lateinit var snapshotRepository: PortfolioSnapshotRepository
    private lateinit var groupRepository: PortfolioGroupRepository
    private lateinit var driftCalculationService: DriftCalculationService

    @BeforeEach
    fun setup() {
        snapshotRepository = mockk()
        groupRepository = mockk()
        driftCalculationService = mockk()

        service = PortfolioSnapshotService(
            snapshotRepository = snapshotRepository,
            groupRepository = groupRepository,
            driftCalculationService = driftCalculationService,
            objectMapper = ObjectMapper()
        )
    }

    @Test
    fun `takes snapshot with position and cash data`() {
        val user = User(id = 1L, email = "test@example.com", passwordHash = "hash", name = "Test")
        val group = PortfolioGroup(id = 1L, user = user, name = "Test Group")
        val today = LocalDate.now()

        every { snapshotRepository.existsByGroupIdAndSnapshotDate(1L, today) } returns false
        every { driftCalculationService.calculateDrift(1L) } returns DriftAnalysisResponse(
            groupId = 1L,
            groupName = "Test Group",
            accuracy = BigDecimal("95.00"),
            totalValue = BigDecimal(50000),
            cash = mapOf("CAD" to BigDecimal(5000)),
            holdings = listOf(
                DriftHoldingDto("VFV", "Vanguard S&P 500", BigDecimal(60), BigDecimal("58.0000"),
                    BigDecimal("-2.0000"), BigDecimal(27000), BigDecimal(28500), "CAD")
            ),
            excludedAssets = emptyList(),
            newAssets = emptyList()
        )
        every { snapshotRepository.save(any()) } answers { firstArg() }

        val result = service.takeSnapshot(group, today)

        assertNotNull(result)
        assertEquals(BigDecimal(50000), result.totalValue)
        assertEquals(BigDecimal("95.00"), result.accuracy)
        verify { snapshotRepository.save(any()) }
    }

    @Test
    fun `skips snapshot if already exists for date`() {
        val user = User(id = 1L, email = "test@example.com", passwordHash = "hash", name = "Test")
        val group = PortfolioGroup(id = 1L, user = user, name = "Test Group")
        val today = LocalDate.now()

        val existing = PortfolioSnapshot(
            id = 1L, group = group, snapshotDate = today,
            totalValue = BigDecimal(45000), positions = "[]", cash = "{}",
            accuracy = BigDecimal("90.00")
        )

        every { snapshotRepository.existsByGroupIdAndSnapshotDate(1L, today) } returns true
        every { snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, today, today) } returns listOf(existing)

        val result = service.takeSnapshot(group, today)

        assertEquals(BigDecimal(45000), result.totalValue)
        verify(exactly = 0) { driftCalculationService.calculateDrift(any()) }
    }

    @Test
    fun `gets snapshots for date range`() {
        val user = User(id = 1L, email = "test@example.com", passwordHash = "hash", name = "Test")
        val group = PortfolioGroup(id = 1L, user = user, name = "Test Group")
        val start = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2025, 1, 31)

        every { snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, end) } returns listOf(
            PortfolioSnapshot(id = 1L, group = group, snapshotDate = start, totalValue = BigDecimal(10000), positions = "[]", cash = "{}"),
            PortfolioSnapshot(id = 2L, group = group, snapshotDate = end, totalValue = BigDecimal(10500), positions = "[]", cash = "{}")
        )

        val snapshots = service.getSnapshots(1L, start, end)

        assertEquals(2, snapshots.size)
    }
}
