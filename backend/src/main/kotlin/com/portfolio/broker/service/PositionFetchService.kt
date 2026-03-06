package com.portfolio.broker.service

import com.portfolio.auth.entity.AuditEventType
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import com.snaptrade.client.model.Position
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
    private val userRepository: UserRepository,
    private val snapTradeService: SnapTradeService,
    private val auditService: AuditService
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
            val snapPositions: List<Position> = snapTradeService.fetchPositions(user, accountId)

            // Mark old positions as non-current
            positionRepository.markAllNonCurrent(connectionId)

            // Save new positions
            var totalValue = BigDecimal.ZERO
            val positions = snapPositions.map { snapPos: Position ->
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

                // Position.symbol is PositionSymbol, which has .symbol (UniversalSymbol)
                val universalSymbol = snapPos.symbol?.symbol
                // Position.currency is PositionCurrency with .code
                val currencyCode: String = snapPos.currency?.code
                    ?: universalSymbol?.currency?.code
                    ?: "CAD"

                val position = BrokerPosition(
                    connection = connection,
                    symbol = universalSymbol?.symbol ?: "UNKNOWN",
                    symbolIdExternal = snapPos.symbol?.id?.toString(),
                    securityName = universalSymbol?.description,
                    instrumentType = mapInstrumentType(universalSymbol?.type?.code),
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
                totalValue += currentValue ?: BigDecimal.ZERO
                position
            }
            positionRepository.saveAll(positions)

            // Update connection
            connection.lastPositionsFetchedAt = OffsetDateTime.now()
            connection.positionsCount = positions.size
            connection.totalValue = totalValue
            connection.status = ConnectionStatus.ACTIVE
            connection.clearError()
            connectionRepository.save(connection)

            // Update fetch log
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
