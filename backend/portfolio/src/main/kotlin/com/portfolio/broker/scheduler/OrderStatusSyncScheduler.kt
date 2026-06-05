package com.portfolio.broker.scheduler

import com.portfolio.broker.entity.OrderStatus
import com.portfolio.broker.repository.TradeOrderRepository
import com.portfolio.broker.service.PositionFetchService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
@ConditionalOnProperty(
    prefix = "broker.sync",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class OrderStatusSyncScheduler(
    private val tradeOrderRepository: TradeOrderRepository,
    private val positionFetchService: PositionFetchService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    fun syncActiveOrders() {
        val cutoff = OffsetDateTime.now().minusHours(24)
        val activeOrders = tradeOrderRepository.findByStatusInAndCreatedAtAfter(
            listOf(OrderStatus.SUBMITTED, OrderStatus.PARTIALLY_FILLED),
            cutoff
        )

        if (activeOrders.isEmpty()) return

        val ordersByConnection = activeOrders.groupBy { it.connection.id }
        log.info("Order status sync: {} active orders across {} connections",
            activeOrders.size, ordersByConnection.size)

        for ((connectionId, orders) in ordersByConnection) {
            try {
                val connection = orders.first().connection
                val gwConnId = connection.gatewayConnectionId ?: continue
                val accountId = connection.accountIdExternal ?: continue

                positionFetchService.syncOrdersForConnection(
                    connection = connection,
                    user = connection.user,
                    gwConnId = gwConnId,
                    accountId = accountId
                )
            } catch (e: Exception) {
                log.warn("Order sync failed for connection {}: {}", connectionId, e.message)
            }
        }
    }
}
