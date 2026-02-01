package com.portfolio.broker.service

import com.portfolio.auth.entity.AuditEventType
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.client.BrokerClientFactory
import com.portfolio.broker.client.BrokerNotSupportedException
import com.portfolio.broker.client.OAuthStateException
import com.portfolio.broker.config.BrokerConfig
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import com.portfolio.broker.security.TokenEncryptionService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.*

@Service
class BrokerService(
    private val brokerRepository: BrokerRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val tokenRepository: ConnectionTokenRepository,
    private val positionRepository: BrokerPositionRepository,
    private val fetchLogRepository: PositionFetchLogRepository,
    private val oauthStateRepository: BrokerOAuthStateRepository,
    private val userPrefsRepository: UserBrokerPrefsRepository,
    private val userRepository: UserRepository,
    private val brokerClientFactory: BrokerClientFactory,
    private val encryptionService: TokenEncryptionService,
    private val auditService: AuditService,
    private val config: BrokerConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    // ========== Broker Listing ==========

    fun getAvailableBrokers(): List<BrokerDto> {
        return brokerRepository.findByStatusOrderByNameAsc(BrokerStatus.ACTIVE)
            .map { it.toDto() }
    }

    fun getBrokerByCode(code: String): Broker {
        return brokerRepository.findByCode(code.uppercase())
            ?: throw BrokerNotSupportedException("Broker not found: $code")
    }

    // ========== Connection Management ==========

    fun getUserConnections(userId: Long): List<BrokerConnectionDto> {
        return connectionRepository.findByUserIdWithBroker(userId)
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

    fun getConnectionWithToken(connectionId: Long, userId: Long): BrokerConnection {
        return connectionRepository.findByIdAndUserIdWithBrokerAndToken(connectionId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")
    }

    // ========== OAuth Flow ==========

    @Transactional
    fun initiateOAuthFlow(brokerCode: String, userId: Long): OAuthInitiateResponse {
        val broker = getBrokerByCode(brokerCode)
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

        // Generate state token
        val stateBytes = ByteArray(32)
        secureRandom.nextBytes(stateBytes)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)
        val stateHash = hashState(state)

        // Generate PKCE code verifier
        val codeVerifierBytes = ByteArray(32)
        secureRandom.nextBytes(codeVerifierBytes)
        val codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes)

        // Determine redirect URI based on broker
        val redirectUri = when (brokerCode.uppercase()) {
            "QUESTRADE" -> config.questrade.redirectUri
            "IBKR" -> config.ibkr.redirectUri
            "WEALTHSIMPLE" -> config.snaptrade.redirectUri
            else -> throw BrokerNotSupportedException("Unsupported broker: $brokerCode")
        }

        // Clean up old states for this user/broker
        oauthStateRepository.deleteByUserIdAndBrokerId(userId, broker.id)

        // Save state
        val oauthState = BrokerOAuthState(
            stateHash = stateHash,
            user = user,
            broker = broker,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier,
            expiresAt = OffsetDateTime.now().plusMinutes(10)
        )
        oauthStateRepository.save(oauthState)

        // Get authorization URL from client
        val client = brokerClientFactory.getClient(brokerCode)
        val authUrl = runBlocking {
            client.getAuthorizationUrl(redirectUri, state, codeVerifier)
        }

        log.info("Initiated OAuth flow for user {} with broker {}", userId, brokerCode)

        return OAuthInitiateResponse(
            redirectUrl = authUrl,
            state = state
        )
    }

    @Transactional
    fun handleOAuthCallback(
        brokerCode: String,
        code: String,
        state: String
    ): BrokerConnection {
        val stateHash = hashState(state)

        // Validate state
        val oauthState = oauthStateRepository.findByStateHashWithUserAndBroker(stateHash)
            ?: throw OAuthStateException("Invalid or expired OAuth state")

        if (!oauthState.isValid()) {
            throw OAuthStateException("OAuth state expired or already used")
        }

        if (oauthState.broker.code != brokerCode.uppercase()) {
            throw OAuthStateException("State mismatch: wrong broker")
        }

        // Mark state as used
        oauthState.markAsUsed()
        oauthStateRepository.save(oauthState)

        val client = brokerClientFactory.getClient(brokerCode)
        val user = oauthState.user
        val broker = oauthState.broker

        // Exchange code for tokens
        val tokenResponse = runBlocking {
            client.exchangeCodeForTokens(
                code = code,
                redirectUri = oauthState.redirectUri ?: config.questrade.redirectUri,
                codeVerifier = oauthState.codeVerifier
            )
        }

        // Fetch accounts from broker
        val accounts = runBlocking {
            client.fetchAccounts(
                accessToken = tokenResponse.accessToken,
                apiServerUrl = tokenResponse.apiServerUrl
            )
        }

        if (accounts.isEmpty()) {
            throw IllegalStateException("No accounts found for this connection")
        }

        // Create connections for each account
        val connections = accounts.map { account ->
            val existingConnection = connectionRepository.findByUserIdAndBrokerIdAndAccountIdExternal(
                user.id, broker.id, account.accountId
            )

            val connection = existingConnection?.apply {
                status = ConnectionStatus.ACTIVE
                accountNumber = account.accountNumber
                accountType = account.accountType
                accountName = account.accountName
                clearError()
            } ?: BrokerConnection(
                user = user,
                broker = broker,
                accountIdExternal = account.accountId,
                accountNumber = account.accountNumber,
                accountType = account.accountType,
                accountName = account.accountName,
                status = ConnectionStatus.ACTIVE
            )

            connectionRepository.save(connection)
        }

        // Save token (associated with first connection - shared across accounts)
        val primaryConnection = connections.first()
        val existingToken = tokenRepository.findByConnectionId(primaryConnection.id)

        if (existingToken != null) {
            existingToken.accessTokenEncrypted = encryptionService.encrypt(tokenResponse.accessToken)
            existingToken.refreshTokenEncrypted = tokenResponse.refreshToken?.let { encryptionService.encrypt(it) }
            existingToken.expiresAt = tokenResponse.expiresIn?.let { OffsetDateTime.now().plusSeconds(it) }
            existingToken.apiServerUrl = tokenResponse.apiServerUrl
            existingToken.lastRefreshedAt = OffsetDateTime.now()
            tokenRepository.save(existingToken)
        } else {
            val token = ConnectionToken(
                connection = primaryConnection,
                accessTokenEncrypted = encryptionService.encrypt(tokenResponse.accessToken),
                refreshTokenEncrypted = tokenResponse.refreshToken?.let { encryptionService.encrypt(it) },
                expiresAt = tokenResponse.expiresIn?.let { OffsetDateTime.now().plusSeconds(it) },
                apiServerUrl = tokenResponse.apiServerUrl
            )
            tokenRepository.save(token)
        }

        // Audit log
        auditService.log(
            eventType = AuditEventType.BROKER_CONNECT,
            user = user,
            resourceType = "broker_connection",
            resourceId = primaryConnection.id.toString(),
            details = mapOf(
                "broker" to brokerCode,
                "accountCount" to accounts.size
            )
        )

        log.info("Successfully connected {} accounts for user {} with broker {}",
            accounts.size, user.id, brokerCode)

        return primaryConnection
    }

    @Transactional
    fun disconnectBroker(connectionId: Long, userId: Long) {
        val connection = getConnection(connectionId, userId)

        // Revoke tokens if possible
        val token = tokenRepository.findByConnectionId(connectionId)
        if (token != null) {
            try {
                val client = brokerClientFactory.getClient(connection.broker.code)
                val accessToken = encryptionService.decrypt(token.accessTokenEncrypted)
                runBlocking { client.revokeTokens(accessToken) }
            } catch (e: Exception) {
                log.warn("Failed to revoke tokens for connection {}: {}", connectionId, e.message)
            }
            tokenRepository.delete(token)
        }

        // Update connection status
        connection.status = ConnectionStatus.DISCONNECTED
        connectionRepository.save(connection)

        // Audit log
        auditService.log(
            eventType = AuditEventType.BROKER_DISCONNECT,
            user = connection.user,
            resourceType = "broker_connection",
            resourceId = connectionId.toString(),
            details = mapOf("broker" to connection.broker.code)
        )

        log.info("Disconnected broker connection {} for user {}", connectionId, userId)
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
            broker = connection.broker.code,
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

        // Group by symbol
        val groupedPositions = positions.groupBy { it.symbol }

        val aggregatedPositions = groupedPositions.map { (symbol, positionList) ->
            val totalQuantity = positionList.sumOf { it.quantity }
            val totalValue = positionList.sumOf { it.currentValue ?: BigDecimal.ZERO }
            val totalPnl = positionList.sumOf { it.totalPnl ?: BigDecimal.ZERO }
            val totalCost = totalValue - totalPnl

            val breakdown = positionList.map { pos ->
                BrokerBreakdownDto(
                    broker = pos.connection.broker.code,
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
        val brokerCount = positions.map { it.connection.broker.id }.distinct().size
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

    // ========== User Preferences ==========

    fun getUserPrefs(userId: Long): BrokerPrefsDto {
        val prefs = userPrefsRepository.findByUserId(userId)
        return prefs?.toDto() ?: BrokerPrefsDto(
            autoFetchEnabled = false,
            fetchTimeUtc = "06:00",
            notificationOnFetch = false,
            notificationOnError = true
        )
    }

    @Transactional
    fun updateUserPrefs(userId: Long, request: UpdateBrokerPrefsRequest): BrokerPrefsResponse {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

        val prefs = userPrefsRepository.findByUserId(userId) ?: UserBrokerPrefs(user = user)

        prefs.autoFetchEnabled = request.autoFetchEnabled
        request.fetchTimeUtc?.let { time ->
            prefs.fetchTimeUtc = LocalTime.parse(time)
        }

        userPrefsRepository.save(prefs)

        auditService.log(
            eventType = AuditEventType.BROKER_PREFS_UPDATE,
            user = user,
            resourceType = "user_broker_prefs",
            resourceId = prefs.id.toString(),
            details = mapOf(
                "autoFetchEnabled" to request.autoFetchEnabled,
                "fetchTimeUtc" to prefs.fetchTimeUtc.toString()
            )
        )

        return BrokerPrefsResponse(
            autoFetchEnabled = prefs.autoFetchEnabled,
            fetchTimeUtc = prefs.fetchTimeUtc.toString(),
            message = "Preferences updated successfully"
        )
    }

    // ========== Utilities ==========

    private fun hashState(state: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(state.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
