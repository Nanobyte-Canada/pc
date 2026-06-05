package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.broker.entity.PortfolioCashFlow
import com.portfolio.broker.entity.CashFlowType
import com.portfolio.broker.entity.PortfolioGroup
import com.portfolio.broker.entity.PortfolioSnapshot
import com.portfolio.broker.repository.BenchmarkReturnRepository
import com.portfolio.broker.repository.PortfolioCashFlowRepository
import com.portfolio.broker.repository.PortfolioSnapshotRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceCalculationServiceTest {

    private lateinit var service: PerformanceCalculationService
    private lateinit var snapshotRepository: PortfolioSnapshotRepository
    private lateinit var cashFlowRepository: PortfolioCashFlowRepository
    private lateinit var benchmarkReturnRepository: BenchmarkReturnRepository
    private lateinit var benchmarkService: BenchmarkService

    @BeforeEach
    fun setup() {
        snapshotRepository = mockk()
        cashFlowRepository = mockk()
        benchmarkReturnRepository = mockk()
        benchmarkService = mockk()

        service = PerformanceCalculationService(
            snapshotRepository = snapshotRepository,
            cashFlowRepository = cashFlowRepository,
            benchmarkReturnRepository = benchmarkReturnRepository,
            benchmarkService = benchmarkService
        )
    }

    @Test
    fun `calculates TWR for simple growth scenario`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val start = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2025, 1, 5)

        val snapshots = listOf(
            createSnapshot(group, start, BigDecimal(10000)),
            createSnapshot(group, start.plusDays(1), BigDecimal(10100)),
            createSnapshot(group, start.plusDays(2), BigDecimal(10200)),
            createSnapshot(group, start.plusDays(3), BigDecimal(10300)),
            createSnapshot(group, start.plusDays(4), BigDecimal(10500))
        )

        every { snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, end) } returns snapshots
        every { cashFlowRepository.findByGroupIdAndFlowDateBetweenOrderByFlowDateAsc(1L, start, end) } returns emptyList()

        val twr = service.calculateTWR(1L, start, end)

        // 5% growth: (10500 - 10000) / 10000 = 5%
        assertTrue(twr > BigDecimal.ZERO)
        assertTrue(twr.toDouble() > 4.0 && twr.toDouble() < 6.0)
    }

    @Test
    fun `calculates MWR returns zero for no snapshots`() {
        val start = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2025, 6, 1)

        every { snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, end) } returns emptyList()
        every { cashFlowRepository.findByGroupIdAndFlowDateBetweenOrderByFlowDateAsc(1L, start, end) } returns emptyList()

        val mwr = service.calculateMWR(1L, start, end)

        assertEquals(BigDecimal.ZERO, mwr)
    }

    @Test
    fun `calculates volatility from daily returns`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val start = LocalDate.of(2025, 1, 1)

        // Simulate volatile portfolio
        val snapshots = listOf(
            createSnapshot(group, start, BigDecimal(10000)),
            createSnapshot(group, start.plusDays(1), BigDecimal(10200)),
            createSnapshot(group, start.plusDays(2), BigDecimal(9800)),
            createSnapshot(group, start.plusDays(3), BigDecimal(10100)),
            createSnapshot(group, start.plusDays(4), BigDecimal(10300))
        )

        every { snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(any(), any(), any()) } returns snapshots
        every { cashFlowRepository.findByGroupIdAndFlowDateBetweenOrderByFlowDateAsc(any(), any(), any()) } returns emptyList()

        val summary = service.getPerformanceSummary(1L, start, start.plusDays(4))

        assertTrue(summary.volatility > BigDecimal.ZERO)
    }

    @Test
    fun `calculates max drawdown correctly`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val start = LocalDate.of(2025, 1, 1)

        // Peak at 12000, trough at 9000 = 25% drawdown
        val snapshots = listOf(
            createSnapshot(group, start, BigDecimal(10000)),
            createSnapshot(group, start.plusDays(1), BigDecimal(12000)),
            createSnapshot(group, start.plusDays(2), BigDecimal(9000)),
            createSnapshot(group, start.plusDays(3), BigDecimal(11000))
        )

        val maxDD = service.calculateMaxDrawdown(snapshots)

        // (12000 - 9000) / 12000 = 25%
        assertEquals(25.0, maxDD.toDouble(), 0.01)
    }

    @Test
    fun `performance summary returns zeros for insufficient data`() {
        val start = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2025, 6, 1)

        every { snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, end) } returns emptyList()
        every { cashFlowRepository.findByGroupIdAndFlowDateBetweenOrderByFlowDateAsc(1L, start, end) } returns emptyList()

        val summary = service.getPerformanceSummary(1L, start, end)

        assertEquals(BigDecimal.ZERO, summary.twr)
        assertEquals(BigDecimal.ZERO, summary.mwr)
        assertEquals(BigDecimal.ZERO, summary.maxDrawdown)
    }

    // ========== Helper Methods ==========

    private fun createUser(id: Long) = User(id = id, email = "user$id@example.com", passwordHash = "hash", name = "Test")
    private fun createGroup(id: Long, user: User) = PortfolioGroup(id = id, user = user, name = "Test Group")

    private fun createSnapshot(group: PortfolioGroup, date: LocalDate, value: BigDecimal) =
        PortfolioSnapshot(
            group = group,
            snapshotDate = date,
            totalValue = value,
            positions = "[]",
            cash = "{}",
            accuracy = BigDecimal("95.00")
        )
}
