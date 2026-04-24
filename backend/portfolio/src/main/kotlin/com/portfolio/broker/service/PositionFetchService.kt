package com.portfolio.broker.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.AuditEventType
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class PositionFetchService(
    private val connectionRepository: BrokerConnectionRepository,
    private val positionRepository: BrokerPositionRepository,
    private val fetchLogRepository: PositionFetchLogRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val tradeOrderRepository: TradeOrderRepository,
    private val userRepository: UserRepository,
    private val gatewayClient: BrokerGatewayClient,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val accountAnalyticsComputeService: AccountAnalyticsComputeService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun triggerManualFetch(connectionId: Long, userId: Long): PositionFetchLog {
        val connection = connectionRepository.findByIdAndUserId(connectionId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")

        val fetchLog = PositionFetchLog(
            connection = connection,
            user = connection.user,
            fetchType = PositionFetchType.MANUAL,
            status = FetchStatus.PENDING,
            triggeredBy = "user:$userId"
        )
        val savedLog = fetchLogRepository.save(fetchLog)

        return executePositionFetch(connectionId, savedLog.id, userId)
    }

    @Transactional
    fun executePositionFetch(connectionId: Long, fetchLogId: Long, userId: Long): PositionFetchLog {
        val fetchLog = fetchLogRepository.findById(fetchLogId).orElseThrow {
            IllegalArgumentException("Fetch log not found: $fetchLogId")
        }
        fetchLog.status = FetchStatus.IN_PROGRESS
        fetchLogRepository.save(fetchLog)

        val connection = connectionRepository.findByIdAndUserId(connectionId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")

        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User not found: $userId")
        }

        val gwConnId = connection.gatewayConnectionId
            ?: throw IllegalStateException("No gateway connection ID for connection: $connectionId")

        val accountId = connection.accountIdExternal
            ?: throw IllegalStateException("No account ID for connection: $connectionId")

        try {
            // Fetch positions and balances from broker gateway
            val positionsResponse = gatewayClient.getPositions(gwConnId, accountId)
            val balanceResponse = gatewayClient.getBalances(gwConnId, accountId)

            // Mark old positions as non-current
            positionRepository.markAllNonCurrent(connectionId)

            // Map gateway position nodes to BrokerPosition entities
            val positionNodes = positionsResponse.path("positions")
            val positions = mutableListOf<BrokerPosition>()
            for (node in positionNodes) {
                val symbol = node.path("symbol").asText("UNKNOWN")
                val quantity = node.path("quantity").decimalValue() ?: BigDecimal.ZERO
                val currentPrice = node.path("currentPrice").decimalValueOrNull()
                val averageCost = node.path("averageCost").decimalValueOrNull()
                val currentValue = node.path("currentValue").decimalValueOrNull()
                val totalPnl = node.path("totalPnl").decimalValueOrNull()
                val totalPnlPercent = node.path("totalPnlPercent").decimalValueOrNull()
                val currency = node.path("currency").asText("CAD")
                val instrumentTypeStr = node.path("instrumentType").asText(null)

                // Option fields from gateway (already included in position data)
                val strikePrice = node.path("strikePrice").decimalValueOrNull()
                val expirationDateStr = node.path("expirationDate").asText(null)
                val expirationDate = expirationDateStr?.let { parseLocalDate(it) }
                val optionType = node.path("optionType").asText(null)
                val underlyingSymbol = node.path("underlyingSymbol").asText(null)

                val instrumentType = resolveInstrumentType(symbol, instrumentTypeStr)

                positions.add(
                    BrokerPosition(
                        connection = connection,
                        symbol = symbol,
                        instrumentType = instrumentType,
                        quantity = quantity,
                        averageCost = averageCost,
                        currentPrice = currentPrice,
                        currentValue = currentValue,
                        dayPnl = null,
                        totalPnl = totalPnl,
                        totalPnlPercent = totalPnlPercent,
                        currency = currency,
                        asOfDate = LocalDate.now(),
                        asOfTimestamp = OffsetDateTime.now(),
                        isCurrent = true,
                        strikePrice = strikePrice,
                        expirationDate = expirationDate,
                        optionType = optionType,
                        underlyingSymbol = underlyingSymbol
                    )
                )
            }
            positionRepository.saveAll(positions)

            // Use totalEquity from balance response as portfolio value
            val portfolioValue = balanceResponse.path("totalEquity").decimalValueOrNull()

            // Save balance snapshot from gateway balance response
            saveBalanceSnapshot(connection, balanceResponse, portfolioValue)

            // Sync broker orders into trade_orders table
            syncOrders(connection, user, gwConnId, accountId)

            // Update connection
            connection.lastPositionsFetchedAt = OffsetDateTime.now()
            connection.positionsCount = positions.size
            connection.totalValue = portfolioValue
            connection.status = ConnectionStatus.ACTIVE
            connection.clearError()
            connectionRepository.save(connection)

            // Update fetch log
            val totalValue = portfolioValue ?: BigDecimal.ZERO
            fetchLog.markSuccess(positions.size, totalValue)
            fetchLogRepository.save(fetchLog)

            auditService.log(
                eventType = AuditEventType.BROKER_FETCH_POSITIONS,
                user = user,
                resourceType = "broker_connection",
                resourceId = connectionId.toString(),
                details = mapOf(
                    "positionsCount" to positions.size,
                    "totalValue" to totalValue,
                    "fetchType" to fetchLog.fetchType.name
                )
            )

            log.info("Successfully fetched {} positions for connection {}", positions.size, connectionId)

            // Compute analytics snapshot after successful position sync
            try {
                accountAnalyticsComputeService.computeForConnection(connectionId)
                log.info("Analytics computed for connection {}", connectionId)
            } catch (e: Exception) {
                log.warn("Analytics computation failed for connection {} (non-fatal): {}", connectionId, e.message)
            }

            return fetchLog

        } catch (e: Exception) {
            log.error("Position fetch failed for connection {}: {}", connectionId, e.message, e)
            connection.markAsError("FETCH_ERROR", e.message ?: "Unknown error")
            connectionRepository.save(connection)

            fetchLog.markFailed("FETCH_ERROR", e.message ?: "Unknown error")
            fetchLogRepository.save(fetchLog)

            auditService.log(
                eventType = AuditEventType.BROKER_FETCH_ERROR,
                user = user,
                resourceType = "broker_connection",
                resourceId = connectionId.toString(),
                success = false,
                details = mapOf<String, Any>(
                    "errorCode" to "FETCH_ERROR",
                    "errorMessage" to (e.message ?: "Unknown error")
                )
            )
            throw e
        }
    }

    private fun saveBalanceSnapshot(
        connection: BrokerConnection,
        balanceResponse: JsonNode,
        portfolioValue: BigDecimal?
    ) {
        val cashMap = mutableMapOf<String, BigDecimal>()

        val cashBalances = balanceResponse.path("cashBalances")
        for (entry in cashBalances) {
            val curr = entry.path("currency").asText("CAD")
            val amount = entry.path("amount").decimalValueOrNull() ?: BigDecimal.ZERO
            cashMap["cash_$curr"] = (cashMap["cash_$curr"] ?: BigDecimal.ZERO) + amount
        }

        val buyingPower = balanceResponse.path("buyingPower").decimalValueOrNull()
        if (buyingPower != null && buyingPower > BigDecimal.ZERO) {
            val bpCurrency = balanceResponse.path("currency").asText("CAD")
            cashMap["buying_power_$bpCurrency"] = buyingPower
        }

        val today = LocalDate.now()

        val existing = balanceRepository.findByConnectionIdAndAsOfDate(connection.id, today)
        if (existing != null) {
            balanceRepository.delete(existing)
            balanceRepository.flush()
        }

        val snapshot = BrokerBalanceSnapshot(
            connection = connection,
            totalValue = portfolioValue,
            cash = objectMapper.writeValueAsString(cashMap),
            currency = balanceResponse.path("currency").asText("CAD"),
            asOfDate = today
        )
        balanceRepository.save(snapshot)

        connection.lastBalanceFetchedAt = OffsetDateTime.now()
    }

    private fun syncOrders(
        connection: BrokerConnection,
        user: com.portfolio.auth.entity.User,
        gwConnId: String,
        accountId: String
    ) {
        try {
            val ordersResponse = gatewayClient.getOrders(gwConnId, accountId)
            val orderNodes = ordersResponse.path("orders")
            log.info("Fetched {} orders for connection {}", orderNodes.size(), connection.id)

            val syncedBrokerOrderIds = mutableSetOf<String>()

            for (node in orderNodes) {
                val brokerOrderId = node.path("brokerOrderId").asText(null) ?: continue
                val symbol = node.path("symbol").asText(null) ?: continue

                syncedBrokerOrderIds.add(brokerOrderId)

                val status = mapOrderStatus(node.path("status").asText(null))
                val action = mapOrderAction(node.path("action").asText(null)) ?: continue
                val orderType = mapOrderType(node.path("orderType").asText(null))
                val timeInForce = mapTimeInForce(node.path("timeInForce").asText(null))

                val totalQty = node.path("totalQuantity").decimalValueOrNull() ?: BigDecimal.ZERO
                val filledQty = node.path("filledQuantity").decimalValueOrNull()
                val execPrice = node.path("executionPrice").decimalValueOrNull()
                val limitPrice = node.path("limitPrice").decimalValueOrNull()
                val requestedPrice = limitPrice ?: execPrice ?: BigDecimal.ZERO
                val requestedAmount = totalQty.multiply(requestedPrice)

                val existing = tradeOrderRepository.findByBrokerOrderId(brokerOrderId)
                if (existing != null) {
                    existing.status = status
                    existing.filledUnits = filledQty
                    existing.filledPrice = execPrice
                    existing.filledAmount = filledQty?.multiply(execPrice ?: BigDecimal.ZERO)
                    existing.updatedAt = OffsetDateTime.now()
                    if (status == OrderStatus.FILLED && existing.filledAt == null) {
                        existing.filledAt = node.path("filledAt").asText(null)?.let { parseOffsetDateTime(it) }
                    }
                    if (status == OrderStatus.CANCELLED && existing.cancelledAt == null) {
                        existing.cancelledAt = node.path("filledAt").asText(null)?.let { parseOffsetDateTime(it) }
                    }
                    tradeOrderRepository.save(existing)
                } else {
                    val newOrder = TradeOrder(
                        user = user,
                        group = null,
                        connection = connection,
                        symbol = symbol,
                        action = action,
                        orderType = orderType,
                        timeInForce = timeInForce,
                        requestedUnits = totalQty,
                        requestedPrice = requestedPrice,
                        requestedAmount = requestedAmount,
                        limitPrice = limitPrice,
                        status = status,
                        brokerOrderId = brokerOrderId,
                        accountIdExternal = connection.accountIdExternal,
                        currency = node.path("currency").asText("CAD"),
                        filledUnits = filledQty,
                        filledPrice = execPrice,
                        submittedAt = node.path("submittedAt").asText(null)?.let { parseOffsetDateTime(it) }
                    )
                    tradeOrderRepository.save(newOrder)
                }
            }

            // Clean up stale orders: delete local open orders not returned by broker
            val localOpenOrders = tradeOrderRepository.findByConnectionIdAndStatusIn(
                connection.id,
                listOf(OrderStatus.PENDING, OrderStatus.SUBMITTED, OrderStatus.PARTIALLY_FILLED)
            )
            for (localOrder in localOpenOrders) {
                if (localOrder.brokerOrderId != null && localOrder.brokerOrderId !in syncedBrokerOrderIds) {
                    log.info("Removing stale order {} for connection {}", localOrder.brokerOrderId, connection.id)
                    tradeOrderRepository.delete(localOrder)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to sync orders for connection {}: {}", connection.id, e.message, e)
        }
    }

    private fun mapOrderStatus(status: String?): OrderStatus {
        return when (status?.uppercase()) {
            "EXECUTED", "FILLED" -> OrderStatus.FILLED
            "CANCELED", "CANCELLED" -> OrderStatus.CANCELLED
            "PARTIAL", "PARTIALLY_FILLED" -> OrderStatus.PARTIALLY_FILLED
            "REJECTED" -> OrderStatus.REJECTED
            "SUBMITTED" -> OrderStatus.SUBMITTED
            "FAILED" -> OrderStatus.FAILED
            else -> OrderStatus.PENDING
        }
    }

    private fun mapOrderAction(action: String?): OrderAction? {
        return when (action?.uppercase()) {
            "BUY" -> OrderAction.BUY
            "SELL" -> OrderAction.SELL
            else -> null
        }
    }

    private fun mapOrderType(type: String?): OrderType {
        return when (type?.lowercase()) {
            "limit" -> OrderType.LIMIT
            else -> OrderType.MARKET
        }
    }

    private fun mapTimeInForce(tif: String?): TimeInForce {
        return when (tif?.uppercase()) {
            "GTC" -> TimeInForce.GTC
            else -> TimeInForce.DAY
        }
    }

    private fun parseOffsetDateTime(value: String): OffsetDateTime? {
        return try {
            OffsetDateTime.parse(value)
        } catch (e: Exception) {
            try {
                java.time.LocalDateTime.parse(value.removeSuffix("Z")).atOffset(java.time.ZoneOffset.UTC)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun parseLocalDate(value: String): LocalDate? {
        return try {
            LocalDate.parse(value)
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveInstrumentType(symbol: String, gatewayTypeStr: String?): InstrumentType {
        val mappedType = mapInstrumentType(gatewayTypeStr)

        // DB takes priority: look up instrument type from ingestion schema
        if (mappedType == InstrumentType.STOCK || mappedType == InstrumentType.OTHER) {
            val ingestionType = lookupIngestionInstrumentType(symbol)
            if (ingestionType != null) {
                return when (ingestionType.uppercase()) {
                    "ETF" -> InstrumentType.ETF
                    "STOCK", "COMMON_STOCK" -> InstrumentType.STOCK
                    "MUTUAL_FUND" -> InstrumentType.MUTUAL_FUND
                    else -> mappedType
                }
            }
        }

        return mappedType
    }

    /**
     * Looks up instrument type from the ingestion schema by ticker.
     */
    private fun lookupIngestionInstrumentType(ticker: String): String? {
        return try {
            val sql = """
                SELECT instrument_type FROM ingestion.instruments
                WHERE UPPER(ticker) = UPPER(:ticker) AND status = 'ACTIVE'
                LIMIT 1
            """.trimIndent()
            val params = MapSqlParameterSource().addValue("ticker", ticker)
            jdbcTemplate.queryForObject(sql, params, String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun mapInstrumentType(typeCode: String?): InstrumentType {
        return when (typeCode?.lowercase()) {
            "stock", "equity", "cs", "common_stock" -> InstrumentType.STOCK
            "etf", "et" -> InstrumentType.ETF
            "mutual_fund", "mf", "mutualfund" -> InstrumentType.MUTUAL_FUND
            "option", "op" -> InstrumentType.OPTION
            "bond", "bnd", "fixed_income" -> InstrumentType.BOND
            "cash", "currency" -> InstrumentType.CASH
            else -> InstrumentType.OTHER
        }
    }

    /**
     * Extension to safely extract a BigDecimal from a JsonNode, returning null for missing/null nodes.
     */
    private fun JsonNode.decimalValueOrNull(): BigDecimal? {
        return if (this.isNull || this.isMissingNode) null else this.decimalValue()
    }
}
