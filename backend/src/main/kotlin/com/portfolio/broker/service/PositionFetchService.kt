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
    private val userRepository: UserRepository,
    private val snapTradeService: SnapTradeService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
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
                    instrumentType = mapInstrumentType(snapPos.symbolTypeCode),
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

            // Save balance snapshot from the same holdings response
            saveBalanceSnapshot(connection, holdings.balances, portfolioValue)

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
