package com.portfolio.broker.service

import com.portfolio.auth.entity.AuditEventType
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import com.portfolio.exception.ExternalServiceException
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
    private val gatewayClient: BrokerGatewayClient,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ========== Broker Listing ==========

    fun getAvailableBrokers(): List<BrokerDto> {
        return try {
            val health = gatewayClient.getHealth()
            val brokersNode = health.get("brokers") ?: return emptyList()

            brokersNode.filter { it.get("enabled")?.asBoolean() == true }
                .map { broker ->
                    BrokerDto(
                        name = broker.get("brokerType")?.asText() ?: "Unknown",
                        slug = broker.get("brokerType")?.asText()?.lowercase(),
                        enabled = broker.get("enabled")?.asBoolean(),
                        status = broker.get("status")?.asText()
                    )
                }
        } catch (e: Exception) {
            log.error("Failed to fetch available brokers from gateway", e)
            emptyList()
        }
    }

    // ========== Connection Management ==========

    fun getUserConnections(userId: Long): List<BrokerConnectionDto> {
        return connectionRepository.findByUserIdWithBroker(userId)
            .filter { it.status != ConnectionStatus.DISCONNECTED }
            .map { it.toDto() }
    }

    fun getUserConnectionEntities(userId: Long): List<BrokerConnection> {
        return connectionRepository.findByUserId(userId)
    }

    fun getActiveConnections(userId: Long): List<BrokerConnectionDto> {
        return connectionRepository.findByUserIdAndStatusWithBroker(userId, ConnectionStatus.ACTIVE)
            .map { it.toDto() }
    }

    fun getConnection(connectionId: Long, userId: Long): BrokerConnection {
        return connectionRepository.findByIdAndUserId(connectionId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")
    }

    // ========== Gateway Connection Flow ==========

    @Transactional
    fun createGatewayConnection(user: User, brokerType: String, credentials: Map<String, Any>): List<BrokerConnection> {
        val response = try {
            gatewayClient.createConnection(user.id!!, brokerType, credentials)
        } catch (e: Exception) {
            log.error("Gateway API error for user {} connecting brokerType={}: {}", user.id, brokerType, e.message)
            throw ExternalServiceException(
                code = "BROKER_CONNECTION_FAILED",
                message = "Failed to connect to broker service. Please try again later.",
                cause = e
            )
        }

        val gatewayConnectionId = response.get("connectionId")?.asText()
            ?: throw ExternalServiceException(
                code = "BROKER_CONNECTION_FAILED",
                message = "Gateway did not return a connectionId."
            )

        val connections = mutableListOf<BrokerConnection>()

        try {
            val accountsResponse = gatewayClient.listAccounts(gatewayConnectionId)
            val accountsNode = accountsResponse.get("accounts") ?: accountsResponse
            if (accountsNode.isArray && accountsNode.size() > 0) {
                for (accountNode in accountsNode) {
                    val accountId = accountNode.get("accountId")?.asText()
                    val existing = accountId?.let {
                        connectionRepository.findByUserIdAndAccountIdExternal(user.id!!, it)
                    }
                    if (existing != null) {
                        existing.gatewayConnectionId = gatewayConnectionId
                        existing.status = ConnectionStatus.ACTIVE
                        existing.clearError()
                        connectionRepository.save(existing)
                        connections.add(existing)
                    } else {
                        val connection = BrokerConnection(
                            user = user,
                            gatewayConnectionId = gatewayConnectionId,
                            brokerName = brokerType,
                            connectionType = brokerType,
                            status = ConnectionStatus.ACTIVE,
                            accountIdExternal = accountId,
                            accountNumber = accountNode.get("accountNumber")?.asText(),
                            accountName = accountNode.get("accountName")?.asText(),
                            accountType = accountNode.get("accountType")?.asText()
                        )
                        connectionRepository.save(connection)
                        connections.add(connection)
                    }
                }
            } else {
                val connection = BrokerConnection(
                    user = user,
                    gatewayConnectionId = gatewayConnectionId,
                    brokerName = brokerType,
                    connectionType = brokerType,
                    status = ConnectionStatus.ACTIVE
                )
                connectionRepository.save(connection)
                connections.add(connection)
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch accounts from gateway for connection {}: {}", gatewayConnectionId, e.message)
            val connection = BrokerConnection(
                user = user,
                gatewayConnectionId = gatewayConnectionId,
                brokerName = brokerType,
                connectionType = brokerType,
                status = ConnectionStatus.ACTIVE
            )
            connectionRepository.save(connection)
            connections.add(connection)
        }

        log.info("Created {} account(s) for gateway connection {} user {} brokerType={}",
            connections.size, gatewayConnectionId, user.id, brokerType)
        return connections
    }

    @Transactional
    fun syncConnections(userId: Long) {
        userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        log.info("Starting connection sync for user {}: validating gateway connections...", userId)

        val connections = connectionRepository.findByUserId(userId)
        data class ValidationState(val connected: Boolean, val needsReauth: Boolean)
        val validatedGatewayIds = mutableMapOf<String, ValidationState>()
        var validatedCount = 0

        for (connection in connections) {
            val gwId = connection.gatewayConnectionId ?: continue

            if (gwId !in validatedGatewayIds) {
                try {
                    val validationResult = gatewayClient.validateConnection(gwId)
                    val connected = validationResult.get("connected")?.asBoolean() ?: false
                    val needsReauth = validationResult.get("needsReauth")?.asBoolean() ?: false
                    validatedGatewayIds[gwId] = ValidationState(connected, needsReauth)
                } catch (e: Exception) {
                    log.warn("Failed to validate gateway connection {} for user {}: {}", gwId, userId, e.message)
                    validatedGatewayIds[gwId] = ValidationState(connected = false, needsReauth = true)
                }
            }

            val state = validatedGatewayIds[gwId]!!
            if (state.connected) {
                connection.status = ConnectionStatus.ACTIVE
                connection.clearError()
            } else if (state.needsReauth) {
                connection.markAsExpired("Connection expired — please reconnect")
            } else {
                connection.markAsError("VALIDATION_FAILED", "Connection validation failed")
            }
            connectionRepository.save(connection)
            validatedCount++
        }

        log.info("Sync complete for user {}: {} accounts validated across {} gateway connections",
            userId, validatedCount, validatedGatewayIds.size)
    }

    @Transactional
    fun disconnectBroker(gatewayConnId: String, userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

        val connections = connectionRepository.findByGatewayConnectionId(gatewayConnId)
            .filter { it.user.id == userId }

        if (connections.isEmpty()) {
            throw IllegalArgumentException("No connections found for gateway connection: $gatewayConnId")
        }

        try {
            gatewayClient.deleteConnection(gatewayConnId)
        } catch (e: Exception) {
            log.warn("Failed to delete gateway connection {}: {}", gatewayConnId, e.message)
        }

        connections.forEach { connection ->
            connection.status = ConnectionStatus.DISCONNECTED
            connectionRepository.save(connection)
        }

        auditService.log(
            eventType = AuditEventType.BROKER_DISCONNECT,
            user = user,
            resourceType = "broker_connection",
            resourceId = gatewayConnId,
            details = mapOf("gatewayConnectionId" to gatewayConnId, "accountsDisconnected" to connections.size)
        )

        log.info("Disconnected {} accounts for gateway connection {} user {}", connections.size, gatewayConnId, userId)
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
            broker = connection.brokerName ?: connection.accountName,
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
                    broker = pos.connection.brokerName ?: pos.connection.accountName,
                    accountNumber = pos.connection.accountNumber,
                    accountType = pos.connection.accountMetaType ?: pos.connection.accountType,
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
        val brokerCount = positions.map { it.connection.gatewayConnectionId ?: it.connection.id.toString() }.distinct().size
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
