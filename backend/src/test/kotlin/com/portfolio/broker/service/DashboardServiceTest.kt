package com.portfolio.broker.service

import com.portfolio.broker.dto.*
import com.portfolio.broker.repository.TradeOrderRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardServiceTest {

    private lateinit var service: DashboardService
    private lateinit var portfolioGroupService: PortfolioGroupService
    private lateinit var driftCalculationService: DriftCalculationService
    private lateinit var tradeOrderRepository: TradeOrderRepository
    private lateinit var notificationService: NotificationService

    @BeforeEach
    fun setup() {
        portfolioGroupService = mockk()
        driftCalculationService = mockk()
        tradeOrderRepository = mockk()
        notificationService = mockk()

        service = DashboardService(
            portfolioGroupService = portfolioGroupService,
            driftCalculationService = driftCalculationService,
            tradeOrderRepository = tradeOrderRepository,
            notificationService = notificationService
        )
    }

    @Test
    fun `aggregates portfolio groups for dashboard`() {
        val groups = listOf(
            PortfolioGroupSummaryDto(1L, "Growth", null, 2, 3, BigDecimal(50000), BigDecimal("95.00")),
            PortfolioGroupSummaryDto(2L, "Income", null, 1, 2, BigDecimal(30000), BigDecimal("85.00"))
        )

        every { portfolioGroupService.listGroups(1L) } returns PortfolioGroupsListResponse(groups)
        every { tradeOrderRepository.findTop5ByUserIdOrderByCreatedAtDesc(1L) } returns emptyList()
        every { notificationService.getNotifications(1L, true, 0, 5) } returns NotificationsResponse(emptyList(), 0L, 0L)
        every { notificationService.getUnreadCount(1L) } returns 0L

        val dashboard = service.getDashboard(1L)

        assertEquals(BigDecimal(80000), dashboard.totalPortfolioValue)
        assertEquals(BigDecimal("90.00"), dashboard.averageAccuracy)
        assertEquals(2, dashboard.portfolioGroups.size)
    }

    @Test
    fun `handles empty portfolio`() {
        every { portfolioGroupService.listGroups(1L) } returns PortfolioGroupsListResponse(emptyList())
        every { tradeOrderRepository.findTop5ByUserIdOrderByCreatedAtDesc(1L) } returns emptyList()
        every { notificationService.getNotifications(1L, true, 0, 5) } returns NotificationsResponse(emptyList(), 0L, 0L)
        every { notificationService.getUnreadCount(1L) } returns 0L

        val dashboard = service.getDashboard(1L)

        assertEquals(BigDecimal.ZERO, dashboard.totalPortfolioValue)
        assertEquals(BigDecimal.ZERO, dashboard.averageAccuracy)
        assertTrue(dashboard.portfolioGroups.isEmpty())
    }
}
