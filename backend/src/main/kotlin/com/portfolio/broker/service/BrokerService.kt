package com.portfolio.broker.service

import com.portfolio.auth.entity.AuditEventType
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import com.snaptrade.client.model.Account
import com.snaptrade.client.model.Brokerage
import com.snaptrade.client.model.BrokerageAuthorization
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class BrokerService(
    private val connectionRepository: BrokerConnectionRepository,
    private val positionRepository: BrokerPositionRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val userRepository: UserRepository,
    private val snapTradeService: SnapTradeService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ========== Broker Listing ==========

    fun getAvailableBrokers(): List<BrokerDto> {
        return try {
            snapTradeService.listAvailableBrokerages().map { brokerage ->
                BrokerDto(
                    name = brokerage.name ?: "Unknown",
                    slug = brokerage.slug,
                    logoUrl = brokerage.awsS3LogoUrl,
                    description = brokerage.description
                )
            }
        } catch (e: Exception) {
            log.error("Failed to fetch available brokerages from SnapTrade", e)
            emptyList()
        }
    }

    // ========== Connection Management ==========

    fun getUserConnections(userId: Long): List<BrokerConnectionDto> {
        return connectionRepository.findByUserIdWithBroker(userId)
            .filter { it.status != ConnectionStatus.DISCONNECTED }
            .map { it.toDto() }
    }

    fun getActiveConnections(userId: Long): List<BrokerConnectionDto> {
        return connectionRepository.findByUserIdAndStatusWithBroker(userId, ConnectionStatus.ACTIVE)
            .map { it.toDto() }
    }

    fun getConnection(connectionId: Long, userId: Long): BrokerConnection {
        return connectionRepository.findByIdAndUserId(connectionId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")
    }

    // ========== SnapTrade Connection Flow ==========

    @Transactional
    fun getConnectionPortalUrl(userId: Long, broker: String? = null, reconnectAuthId: String? = null): String {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        val redirectUrl = snapTradeService.getConnectionPortalUrl(user, broker, reconnectAuthId)

        log.info("Generated SnapTrade connection portal URL for user {}, broker={}", userId, broker)
        return redirectUrl
    }

    @Transactional
    fun syncConnections(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

        val snapConnections = try {
            snapTradeService.listConnections(user)
        } catch (e: Exception) {
            log.error("Failed to sync connections for user {}: {}", userId, e.message)
            return
        }

        val accounts = try {
            snapTradeService.listAccounts(user)
        } catch (e: Exception) {
            log.error("Failed to list accounts for user {}: {}", userId, e.message)
            return
        }

        // Sync each SnapTrade authorization as a connection
        for (auth in snapConnections) {
            val authId = auth.id?.toString() ?: continue

            // Find related accounts
            val relatedAccounts = accounts.filter { it.brokerageAuthorization == auth.id }

            for (account in relatedAccounts) {
                val accountId = account.id?.toString() ?: continue
                val existingConnection = connectionRepository.findByUserIdAndAccountIdExternal(userId, accountId)

                if (existingConnection != null) {
                    existingConnection.snaptradeAuthorizationId = authId
                    existingConnection.accountNumber = account.number
                    existingConnection.accountType = account.institutionName
                    existingConnection.accountName = account.name
                    existingConnection.status = if (auth.disabled == true) ConnectionStatus.ERROR else ConnectionStatus.ACTIVE
                    if (auth.disabled != true) existingConnection.clearError()
                    connectionRepository.save(existingConnection)
                } else {
                    val connection = BrokerConnection(
                        user = user,
                        snaptradeAuthorizationId = authId,
                        accountIdExternal = accountId,
                        accountNumber = account.number,
                        accountType = account.institutionName,
                        accountName = account.name,
                        status = if (auth.disabled == true) ConnectionStatus.ERROR else ConnectionStatus.ACTIVE
                    )
                    connectionRepository.save(connection)
                }
            }
        }

        log.info("Synced {} connections for user {}", snapConnections.size, userId)
    }

    @Transactional
    fun disconnectBroker(authorizationId: String, userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

        try {
            snapTradeService.disconnectBrokerage(user, authorizationId)
        } catch (e: Exception) {
            log.warn("Failed to disconnect from SnapTrade for authorizationId {}: {}", authorizationId, e.message)
        }

        // Mark all connections with this authorization as disconnected
        val connections = connectionRepository.findByUserId(userId)
            .filter { it.snaptradeAuthorizationId == authorizationId }

        connections.forEach { connection ->
            connection.status = ConnectionStatus.DISCONNECTED
            connectionRepository.save(connection)
        }

        auditService.log(
            eventType = AuditEventType.BROKER_DISCONNECT,
            user = user,
            resourceType = "broker_connection",
            resourceId = authorizationId,
            details = mapOf("authorizationId" to authorizationId)
        )

        log.info("Disconnected broker authorization {} for user {}", authorizationId, userId)
    }

    // ========== Positions ==========

    fun getPositionsForConnection(connectionId: Long, userId: Long): ConnectionPositionsResponse {
        val connection = getConnection(connectionId, userId)
        val positions = positionRepository.findCurrentPositionsByConnectionId(connectionId)

        val totalValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        val totalCost = positions.sumOf {
            (it.averageCost ?: BigDecimal.ZERO) * it.quantity
        }
        val totalPnl = totalValue - totalCost
        val totalPnlPercent = if (totalCost > BigDecimal.ZERO) {
            (totalPnl / totalCost) * BigDecimal(100)
        } else BigDecimal.ZERO

        return ConnectionPositionsResponse(
            connectionId = connectionId,
            broker = connection.broker?.code ?: connection.accountName,
            accountNumber = connection.accountNumber,
            asOfDate = LocalDate.now().toString(),
            positions = positions.map { it.toDto() },
            summary = PositionsSummary(
                totalValue = totalValue,
                totalCost = totalCost,
                totalPnl = totalPnl,
                totalPnlPercent = totalPnlPercent.setScale(2, java.math.RoundingMode.HALF_UP)
            )
        )
    }

    fun getAggregatedPositions(userId: Long): AggregatedPositionsResponse {
        val positions = positionRepository.findCurrentPositionsByUserIdFromActiveConnections(userId)

        val groupedPositions = positions.groupBy { it.symbol }

        val aggregatedPositions = groupedPositions.map { (symbol, positionList) ->
            val totalQuantity = positionList.sumOf { it.quantity }
            val totalValue = positionList.sumOf { it.currentValue ?: BigDecimal.ZERO }
            val totalPnl = positionList.sumOf { it.totalPnl ?: BigDecimal.ZERO }
            val totalCost = totalValue - totalPnl

            val breakdown = positionList.map { pos ->
                BrokerBreakdownDto(
                    broker = pos.connection.broker?.code ?: pos.connection.accountName,
                    accountNumber = pos.connection.accountNumber,
                    quantity = pos.quantity,
                    value = pos.currentValue
                )
            }

            AggregatedPositionDto(
                symbol = symbol,
                securityName = positionList.firstOrNull()?.securityName,
                instrumentType = positionList.firstOrNull()?.instrumentType?.name,
                totalQuantity = totalQuantity,
                totalValue = totalValue,
                averageCost = if (totalQuantity > BigDecimal.ZERO) totalCost / totalQuantity else null,
                totalPnl = totalPnl,
                totalPnlPercent = if (totalCost > BigDecimal.ZERO) {
                    (totalPnl / totalCost) * BigDecimal(100)
                } else null,
                currency = positionList.firstOrNull()?.currency ?: "CAD",
                brokerBreakdown = breakdown
            )
        }.sortedByDescending { it.totalValue }

        val totalValue = aggregatedPositions.sumOf { it.totalValue }
        val totalPnl = aggregatedPositions.sumOf { it.totalPnl ?: BigDecimal.ZERO }
        val totalCost = totalValue - totalPnl
        val brokerCount = positions.map { it.connection.snaptradeAuthorizationId ?: it.connection.id }.distinct().size
        val accountCount = positions.map { it.connection.id }.distinct().size

        return AggregatedPositionsResponse(
            asOfDate = LocalDate.now().toString(),
            positions = aggregatedPositions,
            aggregateSummary = AggregateSummary(
                totalValue = totalValue,
                totalCost = totalCost,
                totalPnl = totalPnl,
                totalPnlPercent = if (totalCost > BigDecimal.ZERO) {
                    (totalPnl / totalCost * BigDecimal(100)).setScale(2, java.math.RoundingMode.HALF_UP)
                } else BigDecimal.ZERO,
                brokerCount = brokerCount,
                accountCount = accountCount
            )
        )
    }

    // ========== Balance History ==========

    fun getBalanceHistory(connectionId: Long, startDate: LocalDate, endDate: LocalDate): BalanceHistoryResponse {
        val snapshots = balanceRepository.findByConnectionIdAndAsOfDateBetween(connectionId, startDate, endDate)
        val cashTypeRef = object : TypeReference<Map<String, BigDecimal>>() {}
        return BalanceHistoryResponse(
            snapshots = snapshots.sortedByDescending { it.asOfDate }.map { snapshot ->
                val cashMap: Map<String, BigDecimal> = snapshot.cash?.let {
                    try { objectMapper.readValue(it, cashTypeRef) } catch (e: Exception) { emptyMap() }
                } ?: emptyMap()
                BalanceSnapshotDto(
                    totalValue = snapshot.totalValue,
                    cash = cashMap,
                    currency = snapshot.currency,
                    asOfDate = snapshot.asOfDate.toString()
                )
            },
            connectionId = connectionId
        )
    }
}
