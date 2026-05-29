package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.BrokerConnectionRepository
import com.portfolio.broker.repository.TradeOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class OrderExecutionService(
    private val tradeOrderRepository: TradeOrderRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val portfolioGroupService: PortfolioGroupService,
    private val gatewayClient: BrokerGatewayClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun executeTradesForGroup(user: User, request: ExecuteTradesRequest): ExecuteTradesResponse {
        val group = portfolioGroupService.getGroupEntity(request.groupId, user.id)
        val batchId = UUID.randomUUID()
        val orderType = try { OrderType.valueOf(request.orderType) } catch (e: Exception) { OrderType.MARKET }
        val timeInForce = try { TimeInForce.valueOf(request.timeInForce) } catch (e: Exception) { TimeInForce.DAY }

        val orders = mutableListOf<TradeOrder>()
        var submittedCount = 0
        var failedCount = 0

        for (tradeInput in request.trades) {
            val connection = connectionRepository.findByIdAndUserId(tradeInput.connectionId, user.id)
                ?: throw IllegalArgumentException("Connection not found: ${tradeInput.connectionId}")

            if (!connection.isActive()) {
                throw IllegalArgumentException("Connection ${tradeInput.connectionId} is not active (status: ${connection.status})")
            }

            val action = try { OrderAction.valueOf(tradeInput.action) } catch (e: Exception) {
                throw IllegalArgumentException("Invalid action: ${tradeInput.action}")
            }

            val order = TradeOrder(
                user = user,
                group = group,
                connection = connection,
                batchId = batchId,
                symbol = tradeInput.symbol,
                action = action,
                orderType = orderType,
                timeInForce = timeInForce,
                requestedUnits = tradeInput.units,
                requestedPrice = tradeInput.price,
                requestedAmount = tradeInput.amount,
                limitPrice = tradeInput.limitPrice,
                optionType = tradeInput.optionType,
                strikePrice = tradeInput.strikePrice,
                expirationDate = tradeInput.expirationDate?.let { java.time.LocalDate.parse(it) },
                symbolId = tradeInput.symbolId,
                stopPrice = tradeInput.stopPrice,
                currency = tradeInput.currency,
                accountIdExternal = connection.accountIdExternal
            )

            val savedOrder = tradeOrderRepository.save(order)

            // Submit to broker
            try {
                val gwConnId = connection.gatewayConnectionId
                    ?: throw IllegalStateException("No gateway connection for connection ${connection.id}")
                val accountId = connection.accountIdExternal
                    ?: throw IllegalStateException("No external account ID for connection ${connection.id}")

                val orderBody = mapOf(
                    "symbol" to savedOrder.symbol,
                    "action" to savedOrder.action.name,
                    "quantity" to savedOrder.requestedUnits,
                    "orderType" to savedOrder.orderType.name,
                    "limitPrice" to savedOrder.limitPrice,
                    "stopPrice" to savedOrder.stopPrice,
                    "timeInForce" to savedOrder.timeInForce.name,
                    "currency" to savedOrder.currency,
                    "symbolId" to savedOrder.symbolId,
                )
                val result = gatewayClient.placeOrder(gwConnId, accountId, orderBody)
                val brokerOrderId = result.get("brokerOrderId")?.asText()

                savedOrder.status = OrderStatus.SUBMITTED
                savedOrder.brokerOrderId = brokerOrderId
                savedOrder.submittedAt = OffsetDateTime.now()
                submittedCount++

                log.info("Order {} submitted to broker for {} {} {}",
                    savedOrder.id, tradeInput.action, tradeInput.units, tradeInput.symbol)
            } catch (e: Exception) {
                savedOrder.status = OrderStatus.FAILED
                savedOrder.errorMessage = e.message
                savedOrder.errorCode = "SUBMISSION_ERROR"
                failedCount++

                log.error("Failed to submit order {} for {} {}: {}",
                    savedOrder.id, tradeInput.symbol, tradeInput.action, e.message)
            }

            tradeOrderRepository.save(savedOrder)
            orders.add(savedOrder)
        }

        log.info("Batch {} completed: {} submitted, {} failed out of {} trades",
            batchId, submittedCount, failedCount, request.trades.size)

        return ExecuteTradesResponse(
            batchId = batchId,
            orders = orders.map { it.toDto() },
            submittedCount = submittedCount,
            failedCount = failedCount
        )
    }

    @Transactional
    fun executeSingleTrade(user: User, groupId: Long, tradeInput: TradeExecutionInput): TradeOrderDto {
        val request = ExecuteTradesRequest(
            groupId = groupId,
            trades = listOf(tradeInput)
        )
        val response = executeTradesForGroup(user, request)
        return response.orders.first()
    }

    fun getOrdersForGroup(userId: Long, groupId: Long): OrderStatusResponse {
        val orders = tradeOrderRepository.findByUserIdAndGroupIdOrderByCreatedAtDesc(userId, groupId)
        return OrderStatusResponse(
            orders = orders.map { it.toDto() },
            totalCount = orders.size
        )
    }

    fun getOrdersForBatch(userId: Long, batchId: UUID): OrderStatusResponse {
        val orders = tradeOrderRepository.findByBatchId(batchId)
            .filter { it.user.id == userId }
        return OrderStatusResponse(
            orders = orders.map { it.toDto() },
            totalCount = orders.size
        )
    }

    @Transactional
    fun cancelOrder(user: User, orderId: Long): TradeOrderDto {
        val order = tradeOrderRepository.findByIdAndUserId(orderId, user.id)
            ?: throw IllegalArgumentException("Order not found: $orderId")

        if (order.status !in listOf(OrderStatus.PENDING, OrderStatus.SUBMITTED)) {
            throw IllegalArgumentException("Cannot cancel order in status: ${order.status}")
        }

        // Try to cancel with broker if submitted
        if (order.status == OrderStatus.SUBMITTED && order.brokerOrderId != null) {
            try {
                val gwConnId = order.connection.gatewayConnectionId
                    ?: throw IllegalStateException("No gateway connection for connection")
                val accountId = order.connection.accountIdExternal
                    ?: throw IllegalStateException("No external account ID for connection")
                gatewayClient.cancelOrder(gwConnId, accountId, order.brokerOrderId!!)
            } catch (e: Exception) {
                log.warn("Failed to cancel order {} with broker: {}", orderId, e.message)
            }
        }

        order.status = OrderStatus.CANCELLED
        order.cancelledAt = OffsetDateTime.now()
        tradeOrderRepository.save(order)

        log.info("Order {} cancelled for user {}", orderId, user.id)
        return order.toDto()
    }
}
