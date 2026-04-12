package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.AuditEventType
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.adapter.SnapTradeBalanceDto
import com.portfolio.broker.adapter.SnapTradeOptionPositionDto
import com.portfolio.broker.adapter.SnapTradePositionDto
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
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
    private val snapTradeService: SnapTradeService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Triggers a manual position fetch for a connection.
     * Returns immediately with fetch log, actual fetch happens async.
     */
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

        executeAsyncFetch(connectionId, savedLog.id, userId)

        return savedLog
    }

    @Async
    fun executeAsyncFetch(connectionId: Long, fetchLogId: Long, userId: Long) {
        try {
            executePositionFetch(connectionId, fetchLogId, userId)
        } catch (e: Exception) {
            log.error("Async fetch failed for connection {}: {}", connectionId, e.message, e)
        }
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

        val accountId = connection.accountIdExternal
            ?: throw IllegalStateException("No account ID for connection: $connectionId")

        try {
            // Use holdings endpoint: positions + balances + total_value in one call
            val holdings = snapTradeService.getHoldings(user, accountId)
            val snapPositions: List<SnapTradePositionDto> = holdings.positions

            // Mark old positions as non-current
            positionRepository.markAllNonCurrent(connectionId)

            // Save new positions
            val positions = snapPositions.map { snapPos: SnapTradePositionDto ->
                val units: BigDecimal? = snapPos.units?.let { BigDecimal.valueOf(it) }
                val price: BigDecimal? = snapPos.price?.let { BigDecimal.valueOf(it) }
                val currentValue = if (units != null && price != null) units.multiply(price) else null

                val avgCost: BigDecimal? = snapPos.averagePurchasePrice?.let { BigDecimal.valueOf(it) }
                val qty = units ?: BigDecimal.ZERO
                val totalPnl = if (currentValue != null && avgCost != null) {
                    currentValue - avgCost.multiply(qty)
                } else null
                val costBasis = if (avgCost != null) avgCost.multiply(qty) else null
                val totalPnlPercent = if (totalPnl != null && costBasis != null && costBasis.compareTo(BigDecimal.ZERO) > 0) {
                    totalPnl.divide(costBasis, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
                } else null

                val currencyCode: String = snapPos.currencyCode ?: "CAD"

                BrokerPosition(
                    connection = connection,
                    symbol = snapPos.symbol ?: "UNKNOWN",
                    symbolIdExternal = snapPos.symbolId,
                    securityName = snapPos.symbolDescription,
                    instrumentType = resolveInstrumentType(snapPos.symbol ?: "UNKNOWN", snapPos.symbolTypeCode),
                    quantity = qty,
                    averageCost = avgCost,
                    currentPrice = price,
                    currentValue = currentValue,
                    dayPnl = null,
                    totalPnl = totalPnl,
                    totalPnlPercent = totalPnlPercent,
                    currency = currencyCode,
                    asOfDate = LocalDate.now(),
                    asOfTimestamp = OffsetDateTime.now(),
                    isCurrent = true
                )
            }
            positionRepository.saveAll(positions)

            // Fetch option-specific data and merge into option positions
            enrichOptionPositions(user, accountId, connection, positions)

            // Use SnapTrade's FX-converted total_value as the portfolio value
            val portfolioValue = holdings.totalValue?.let { BigDecimal.valueOf(it) }

            // Use dedicated balance endpoint for per-currency breakdown (CAD + USD)
            val balances = try {
                snapTradeService.getAccountBalance(user, accountId)
            } catch (e: Exception) {
                log.warn("Dedicated balance fetch failed for connection {}, falling back to holdings balance: {}",
                    connectionId, e.message)
                holdings.balances
            }
            saveBalanceSnapshot(connection, balances, portfolioValue)

            // Sync broker orders into trade_orders table
            syncOrders(connection, user, accountId)

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

    private fun enrichOptionPositions(
        user: com.portfolio.auth.entity.User,
        accountId: String,
        connection: BrokerConnection,
        savedPositions: List<BrokerPosition>
    ) {
        try {
            val optionHoldings = snapTradeService.fetchOptionPositions(user, accountId)
            if (optionHoldings.isEmpty()) return

            log.info("Fetched {} option holdings for connection {}", optionHoldings.size, connection.id)

            for (optionDto in optionHoldings) {
                val symbol = optionDto.symbol ?: continue

                // Try to find a matching position by symbol
                val matchingPosition = savedPositions.find {
                    it.symbol.equals(symbol, ignoreCase = true) ||
                    (optionDto.underlyingSymbol != null && it.symbol.equals(optionDto.underlyingSymbol, ignoreCase = true) && it.instrumentType == InstrumentType.OPTION)
                }

                if (matchingPosition != null) {
                    // Enrich existing position with option-specific fields
                    matchingPosition.strikePrice = optionDto.strikePrice?.let { BigDecimal.valueOf(it) }
                    matchingPosition.expirationDate = optionDto.expirationDate
                    matchingPosition.optionType = optionDto.optionType
                    matchingPosition.underlyingSymbol = optionDto.underlyingSymbol
                    positionRepository.save(matchingPosition)
                } else {
                    // Create a new option position not found in regular positions
                    val units = optionDto.units?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
                    val price = optionDto.price?.let { BigDecimal.valueOf(it) }
                    val currentValue = if (price != null) units.multiply(price) else null
                    val avgCost = optionDto.averagePurchasePrice?.let { BigDecimal.valueOf(it) }

                    val newPosition = BrokerPosition(
                        connection = connection,
                        symbol = symbol,
                        instrumentType = InstrumentType.OPTION,
                        quantity = units,
                        averageCost = avgCost,
                        currentPrice = price,
                        currentValue = currentValue,
                        currency = optionDto.currencyCode ?: "CAD",
                        asOfDate = LocalDate.now(),
                        asOfTimestamp = OffsetDateTime.now(),
                        isCurrent = true,
                        strikePrice = optionDto.strikePrice?.let { BigDecimal.valueOf(it) },
                        expirationDate = optionDto.expirationDate,
                        optionType = optionDto.optionType,
                        underlyingSymbol = optionDto.underlyingSymbol
                    )
                    positionRepository.save(newPosition)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to enrich option positions for connection {}: {}", connection.id, e.message)
            // Non-fatal — regular positions are already saved
        }
    }

    private fun saveBalanceSnapshot(
        connection: BrokerConnection,
        balances: List<SnapTradeBalanceDto>,
        portfolioValue: BigDecimal?
    ) {
        val cashMap = mutableMapOf<String, BigDecimal>()
        val buyingPowerMap = mutableMapOf<String, BigDecimal>()

        for (balance in balances) {
            val curr = balance.currency ?: "CAD"
            val amount = balance.cash?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
            cashMap["cash_$curr"] = (cashMap["cash_$curr"] ?: BigDecimal.ZERO) + amount

            val bp = balance.buyingPower?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
            if (bp > BigDecimal.ZERO) {
                buyingPowerMap["buying_power_$curr"] = (buyingPowerMap["buying_power_$curr"] ?: BigDecimal.ZERO) + bp
            }
        }

        val combined = cashMap + buyingPowerMap
        val today = LocalDate.now()

        val existing = balanceRepository.findByConnectionIdAndAsOfDate(connection.id, today)
        if (existing != null) {
            balanceRepository.delete(existing)
            balanceRepository.flush()
        }

        val snapshot = BrokerBalanceSnapshot(
            connection = connection,
            totalValue = portfolioValue,
            cash = objectMapper.writeValueAsString(combined),
            currency = "CAD",
            asOfDate = today
        )
        balanceRepository.save(snapshot)

        connection.lastBalanceFetchedAt = OffsetDateTime.now()
    }

    private fun syncOrders(
        connection: BrokerConnection,
        user: com.portfolio.auth.entity.User,
        accountId: String
    ) {
        try {
            val orders = snapTradeService.listOrders(user, accountId)
            log.info("Fetched {} orders for connection {}", orders.size, connection.id)

            for (orderDto in orders) {
                val brokerOrderId = orderDto.brokerageOrderId ?: continue
                val symbol = orderDto.symbol ?: continue

                val status = mapSnapTradeOrderStatus(orderDto.status)
                val action = mapSnapTradeOrderAction(orderDto.action) ?: continue
                val orderType = mapSnapTradeOrderType(orderDto.orderType)
                val timeInForce = mapSnapTradeTimeInForce(orderDto.timeInForce)

                val totalQty = orderDto.totalQuantity?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
                val filledQty = orderDto.filledQuantity?.let { BigDecimal.valueOf(it) }
                val execPrice = orderDto.executionPrice?.let { BigDecimal.valueOf(it) }
                val limitPrice = orderDto.limitPrice?.let { BigDecimal.valueOf(it) }
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
                        existing.filledAt = orderDto.timeExecuted?.let { parseOffsetDateTime(it) }
                    }
                    if (status == OrderStatus.CANCELLED && existing.cancelledAt == null) {
                        existing.cancelledAt = orderDto.timeUpdated?.let { parseOffsetDateTime(it) }
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
                        currency = orderDto.currency ?: "CAD",
                        filledUnits = filledQty,
                        filledPrice = execPrice,
                        submittedAt = orderDto.timePlaced?.let { parseOffsetDateTime(it) }
                    )
                    tradeOrderRepository.save(newOrder)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to sync orders for connection {}: {}", connection.id, e.message)
        }
    }

    private fun mapSnapTradeOrderStatus(status: String?): OrderStatus {
        return when (status?.uppercase()) {
            "EXECUTED" -> OrderStatus.FILLED
            "CANCELED", "CANCELLED" -> OrderStatus.CANCELLED
            "PARTIAL" -> OrderStatus.PARTIALLY_FILLED
            "REJECTED" -> OrderStatus.REJECTED
            else -> OrderStatus.PENDING
        }
    }

    private fun mapSnapTradeOrderAction(action: String?): OrderAction? {
        return when (action?.uppercase()) {
            "BUY" -> OrderAction.BUY
            "SELL" -> OrderAction.SELL
            else -> null
        }
    }

    private fun mapSnapTradeOrderType(type: String?): OrderType {
        return when (type?.lowercase()) {
            "limit" -> OrderType.LIMIT
            else -> OrderType.MARKET
        }
    }

    private fun mapSnapTradeTimeInForce(tif: String?): TimeInForce {
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

    private fun resolveInstrumentType(symbol: String, snapTradeTypeCode: String?): InstrumentType {
        val mappedType = mapInstrumentType(snapTradeTypeCode)

        // DB takes priority: look up instrument type from ingestion schema
        if (mappedType == InstrumentType.STOCK || mappedType == InstrumentType.OTHER) {
            val ingestionType = lookupIngestionInstrumentType(symbol)
            if (ingestionType != null) {
                return when (ingestionType.uppercase()) {
                    "ETF" -> InstrumentType.ETF
                    "STOCK", "COMMON_STOCK" -> InstrumentType.STOCK
                    "MUTUAL_FUND" -> InstrumentType.MUTUAL_FUND
                    else -> mappedType ?: InstrumentType.OTHER
                }
            }
        }

        return mappedType ?: InstrumentType.OTHER
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

    private fun mapInstrumentType(typeCode: String?): InstrumentType? {
        return when (typeCode?.lowercase()) {
            "cs", "equity", "stock" -> InstrumentType.STOCK
            "et", "etf" -> InstrumentType.ETF
            "mf", "mutual_fund", "mutualfund" -> InstrumentType.MUTUAL_FUND
            "op", "option" -> InstrumentType.OPTION
            "bnd", "bond", "fixed_income" -> InstrumentType.BOND
            "cash", "currency" -> InstrumentType.CASH
            else -> InstrumentType.OTHER
        }
    }
}
