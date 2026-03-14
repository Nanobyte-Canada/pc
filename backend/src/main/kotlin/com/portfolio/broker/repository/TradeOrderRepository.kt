package com.portfolio.broker.repository

import com.portfolio.broker.entity.OrderStatus
import com.portfolio.broker.entity.TradeOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TradeOrderRepository : JpaRepository<TradeOrder, Long> {

    fun findByUserIdAndGroupIdOrderByCreatedAtDesc(userId: Long, groupId: Long): List<TradeOrder>

    fun findByBatchId(batchId: UUID): List<TradeOrder>

    fun findByStatusIn(statuses: List<OrderStatus>): List<TradeOrder>

    fun findByIdAndUserId(id: Long, userId: Long): TradeOrder?

    fun findByBrokerOrderId(brokerOrderId: String): TradeOrder?

    fun findTop5ByUserIdOrderByCreatedAtDesc(userId: Long): List<TradeOrder>
}
