package com.portfolio.broker.service

import com.portfolio.broker.dto.*
import com.portfolio.broker.repository.TradeOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class DashboardService(
    private val portfolioGroupService: PortfolioGroupService,
    private val driftCalculationService: DriftCalculationService,
    private val tradeOrderRepository: TradeOrderRepository,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getDashboard(userId: Long): DashboardResponse {
        // Get all portfolio groups with summaries
        val groupsResponse = portfolioGroupService.listGroups(userId)
        val groups = groupsResponse.groups

        val totalValue = groups.sumOf { it.totalValue }
        val averageAccuracy = if (groups.isNotEmpty()) {
            groups.sumOf { it.accuracy }.divide(BigDecimal(groups.size), 2, java.math.RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val groupSummaries = groups.map { g ->
            DashboardGroupSummary(
                id = g.id,
                name = g.name,
                totalValue = g.totalValue,
                accuracy = g.accuracy,
                targetCount = g.targetCount,
                accountCount = g.accountCount
            )
        }

        // Recent orders
        val recentOrders = tradeOrderRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId)
            .map { it.toDto() }

        // Active alerts (unread notifications)
        val alertsResponse = notificationService.getNotifications(userId, unreadOnly = true, page = 0, size = 5)
        val unreadCount = notificationService.getUnreadCount(userId)

        return DashboardResponse(
            totalPortfolioValue = totalValue,
            dayChange = BigDecimal.ZERO, // TODO: implement when snapshots available
            dayChangePercent = BigDecimal.ZERO,
            averageAccuracy = averageAccuracy,
            portfolioGroups = groupSummaries,
            recentOrders = recentOrders,
            activeAlerts = alertsResponse.notifications,
            unreadNotificationCount = unreadCount
        )
    }
}
