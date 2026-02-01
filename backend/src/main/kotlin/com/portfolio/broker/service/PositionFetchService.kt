package com.portfolio.broker.service

import com.portfolio.auth.entity.AuditEventType
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.client.BrokerClientFactory
import com.portfolio.broker.client.BrokerRateLimitException
import com.portfolio.broker.client.BrokerTokenExpiredException
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import com.portfolio.broker.security.TokenEncryptionService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class PositionFetchService(
    private val connectionRepository: BrokerConnectionRepository,
    private val tokenRepository: ConnectionTokenRepository,
    private val positionRepository: BrokerPositionRepository,
    private val fetchLogRepository: PositionFetchLogRepository,
    private val userRepository: UserRepository,
    private val brokerClientFactory: BrokerClientFactory,
    private val encryptionService: TokenEncryptionService,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Triggers a manual position fetch for a connection.
     * Returns immediately with fetch log, actual fetch happens async.
     */
    @Transactional
    fun triggerManualFetch(connectionId: Long, userId: Long): PositionFetchLog {
        val connection = connectionRepository.findByIdAndUserIdWithBrokerAndToken(connectionId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")

        // Create fetch log entry
        val fetchLog = PositionFetchLog(
            connection = connection,
            user = connection.user,
            fetchType = PositionFetchType.MANUAL,
            status = FetchStatus.PENDING,
            triggeredBy = "user:$userId"
        )
        val savedLog = fetchLogRepository.save(fetchLog)

        // Trigger async fetch
        executeAsyncFetch(connectionId, savedLog.id, userId)

        return savedLog
    }

    /**
     * Executes position fetch for scheduled jobs.
     */
    @Transactional
    fun executeScheduledFetch(connectionId: Long, userId: Long): PositionFetchLog {
        val connection = connectionRepository.findByIdAndUserIdWithBrokerAndToken(connectionId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")

        val fetchLog = PositionFetchLog(
            connection = connection,
            user = connection.user,
            fetchType = PositionFetchType.SCHEDULED,
            status = FetchStatus.PENDING,
            triggeredBy = "scheduler"
        )
        val savedLog = fetchLogRepository.save(fetchLog)

        return executePositionFetch(connectionId, savedLog.id, userId)
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

        val connection = connectionRepository.findByIdAndUserIdWithBrokerAndToken(connectionId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")

        val token = tokenRepository.findByConnectionId(connectionId)
            ?: throw IllegalStateException("Token not found for connection: $connectionId")

        val user = connection.user

        try {
            val client = brokerClientFactory.getClient(connection.broker.code)

            // Check if token needs refresh
            var accessToken = encryptionService.decrypt(token.accessTokenEncrypted)
            var apiServerUrl = token.apiServerUrl

            if (token.isExpiringSoon()) {
                log.info("Token expiring soon for connection {}, refreshing...", connectionId)

                val refreshToken = token.refreshTokenEncrypted?.let { encryptionService.decrypt(it) }
                    ?: throw BrokerTokenExpiredException("No refresh token available")

                val newTokens = runBlocking { client.refreshAccessToken(refreshToken) }

                // Update stored tokens
                token.accessTokenEncrypted = encryptionService.encrypt(newTokens.accessToken)
                token.refreshTokenEncrypted = newTokens.refreshToken?.let { encryptionService.encrypt(it) }
                token.expiresAt = newTokens.expiresIn?.let { OffsetDateTime.now().plusSeconds(it) }
                token.apiServerUrl = newTokens.apiServerUrl ?: token.apiServerUrl
                token.lastRefreshedAt = OffsetDateTime.now()
                token.refreshCount++
                tokenRepository.save(token)

                accessToken = newTokens.accessToken
                apiServerUrl = newTokens.apiServerUrl ?: apiServerUrl

                auditService.log(
                    eventType = AuditEventType.BROKER_TOKEN_REFRESH,
                    user = user,
                    resourceType = "broker_connection",
                    resourceId = connectionId.toString(),
                    details = mapOf("broker" to connection.broker.code)
                )
            }

            // Fetch positions from broker
            val response = runBlocking {
                client.fetchPositions(
                    accessToken = accessToken,
                    accountId = connection.accountIdExternal ?: connection.accountNumber ?: "",
                    apiServerUrl = apiServerUrl
                )
            }

            // Mark old positions as non-current
            positionRepository.markAllNonCurrent(connectionId)

            // Save new positions
            var totalValue = BigDecimal.ZERO
            val positions = response.positions.map { dto ->
                val position = BrokerPosition(
                    connection = connection,
                    symbol = dto.symbol,
                    symbolIdExternal = dto.symbolId,
                    securityName = dto.securityName,
                    instrumentType = dto.instrumentType?.let {
                        try { InstrumentType.valueOf(it) } catch (e: Exception) { null }
                    },
                    quantity = dto.quantity,
                    averageCost = dto.averageCost,
                    currentPrice = dto.currentPrice,
                    currentValue = dto.currentValue,
                    dayPnl = dto.dayPnl,
                    totalPnl = dto.totalPnl,
                    totalPnlPercent = dto.totalPnlPercent,
                    currency = dto.currency,
                    asOfDate = LocalDate.now(),
                    asOfTimestamp = response.asOfTimestamp ?: OffsetDateTime.now(),
                    isCurrent = true,
                    rawPayload = dto.rawData?.let { com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(it) }
                )
                totalValue += dto.currentValue ?: BigDecimal.ZERO
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
            fetchLog.rawResponse = response.rawPayload
            fetchLogRepository.save(fetchLog)

            auditService.log(
                eventType = AuditEventType.BROKER_FETCH_POSITIONS,
                user = user,
                resourceType = "broker_connection",
                resourceId = connectionId.toString(),
                details = mapOf(
                    "broker" to connection.broker.code,
                    "positionsCount" to positions.size,
                    "totalValue" to totalValue,
                    "fetchType" to fetchLog.fetchType.name
                )
            )

            log.info("Successfully fetched {} positions for connection {}", positions.size, connectionId)
            return fetchLog

        } catch (e: BrokerTokenExpiredException) {
            log.warn("Token expired for connection {}", connectionId)
            connection.markAsExpired(e.message)
            connectionRepository.save(connection)

            fetchLog.markFailed("TOKEN_EXPIRED", e.message ?: "Token expired")
            fetchLogRepository.save(fetchLog)

            auditService.log(
                eventType = AuditEventType.BROKER_FETCH_ERROR,
                user = user,
                resourceType = "broker_connection",
                resourceId = connectionId.toString(),
                success = false,
                details = mapOf<String, Any>(
                    "broker" to connection.broker.code,
                    "errorCode" to "TOKEN_EXPIRED",
                    "errorMessage" to (e.message ?: "Token expired")
                )
            )
            throw e

        } catch (e: BrokerRateLimitException) {
            log.warn("Rate limited for connection {}", connectionId)
            fetchLog.markFailed("RATE_LIMITED", e.message ?: "Rate limited")
            fetchLog.retryCount++
            fetchLogRepository.save(fetchLog)

            auditService.log(
                eventType = AuditEventType.BROKER_FETCH_ERROR,
                user = user,
                resourceType = "broker_connection",
                resourceId = connectionId.toString(),
                success = false,
                details = mapOf<String, Any>(
                    "broker" to connection.broker.code,
                    "errorCode" to "RATE_LIMITED"
                )
            )
            throw e

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
                    "broker" to connection.broker.code,
                    "errorCode" to "FETCH_ERROR",
                    "errorMessage" to (e.message ?: "Unknown error")
                )
            )
            throw e
        }
    }
}
