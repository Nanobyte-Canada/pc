package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.broker.adapter.*
import com.portfolio.broker.config.SnapTradeConfig
import com.portfolio.broker.security.TokenEncryptionService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate

@Service
class SnapTradeService(
    private val config: SnapTradeConfig,
    private val userRepository: UserRepository,
    private val encryptionService: TokenEncryptionService,
    private val adapter: SnapTradeAdapter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        val clientIdSet = config.clientId.isNotBlank()
        val consumerKeySet = config.consumerKey.isNotBlank()
        log.info("SnapTrade config: clientId={}, consumerKey={}, redirectUri={}",
            if (clientIdSet) "SET" else "MISSING",
            if (consumerKeySet) "SET" else "MISSING",
            config.redirectUri)
        if (!clientIdSet || !consumerKeySet) {
            log.warn("SnapTrade credentials are not configured. Broker features will not work.")
        }
    }

    private fun generateSnapTradeUserId(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(email.lowercase().toByteArray())
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * Registers a user with SnapTrade if not already registered.
     * Stores the encrypted userSecret in the users table.
     */
    fun ensureUserRegistered(user: User): SnapTradeUserInfo {
        if (user.snaptradeUserId != null && user.snaptradeUserSecretEncrypted != null) {
            return SnapTradeUserInfo(
                userId = user.snaptradeUserId!!,
                userSecret = encryptionService.decrypt(user.snaptradeUserSecretEncrypted!!)
            )
        }

        val snapUserId = generateSnapTradeUserId(user.email)
        log.info("Registering SnapTrade user for user {} (snapUserId={})", user.id, snapUserId)

        val userSecret: String = try {
            adapter.registerUser(snapUserId)
        } catch (e: SnapTradeApiException) {
            if (e.errorCode == SnapTradeApiException.ERROR_PERSONAL_KEY_SLOT_OCCUPIED) {
                log.error(
                    "SnapTrade error 1012: personal key slot occupied. " +
                    "Another user is registered with these SnapTrade personal keys. " +
                    "Use production keys for multi-user support, or manually clear " +
                    "users via the SnapTrade dashboard.",
                )
                throw IllegalStateException(
                    "SnapTrade personal key limit reached. Cannot register new user."
                )
            } else {
                throw e
            }
        }

        user.snaptradeUserId = snapUserId
        user.snaptradeUserSecretEncrypted = encryptionService.encrypt(userSecret)
        userRepository.save(user)

        return SnapTradeUserInfo(userId = snapUserId, userSecret = userSecret)
    }

    /**
     * Gets the SnapTrade connection portal URL for connecting a broker.
     */
    fun getConnectionPortalUrl(user: User, broker: String? = null, reconnectAuthId: String? = null): String {
        val snapUser = ensureUserRegistered(user)
        return adapter.getLoginRedirectUrl(
            snapUser.userId, snapUser.userSecret, config.redirectUri, broker, reconnectAuthId
        )
    }

    /**
     * Lists all connected accounts for a user.
     */
    fun listAccounts(user: User): List<SnapTradeAccountDto> {
        val snapUser = ensureUserRegistered(user)
        return adapter.listAccounts(snapUser.userId, snapUser.userSecret)
    }

    /**
     * Lists all brokerage authorizations (connections) for a user.
     */
    fun listConnections(user: User): List<SnapTradeConnectionDto> {
        val snapUser = ensureUserRegistered(user)
        return adapter.listConnections(snapUser.userId, snapUser.userSecret)
    }

    /**
     * Fetches positions for a specific account.
     */
    fun fetchPositions(user: User, accountId: String): List<SnapTradePositionDto> {
        val snapUser = ensureUserRegistered(user)
        return adapter.getPositions(snapUser.userId, snapUser.userSecret, accountId)
    }

    /**
     * Fetches complete holdings (positions + balances + total_value) for a specific account.
     * The total_value is FX-converted by the broker.
     */
    fun getHoldings(user: User, accountId: String): SnapTradeHoldingsDto {
        val snapUser = ensureUserRegistered(user)
        return adapter.getHoldings(snapUser.userId, snapUser.userSecret, accountId)
    }

    /**
     * Fetches option-specific position data for a specific account.
     * Returns empty list if the broker doesn't support options.
     */
    fun fetchOptionPositions(user: User, accountId: String): List<SnapTradeOptionPositionDto> {
        val snapUser = ensureUserRegistered(user)
        return adapter.getOptionPositions(snapUser.userId, snapUser.userSecret, accountId)
    }

    /**
     * Disconnects a brokerage authorization.
     */
    fun disconnectBrokerage(user: User, authorizationId: String) {
        val snapUser = ensureUserRegistered(user)
        adapter.disconnectBrokerage(snapUser.userId, snapUser.userSecret, authorizationId)
    }

    /**
     * Lists all available brokerages from SnapTrade.
     */
    fun listAvailableBrokerages(): List<SnapTradeBrokerageDto> {
        return adapter.listBrokerages()
    }

    /**
     * Fetches activities (transactions) for a user from SnapTrade.
     */
    fun getActivities(
        user: User,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        accounts: String? = null,
        type: String? = null
    ): List<SnapTradeActivityDto> {
        val snapUser = ensureUserRegistered(user)
        return adapter.getActivities(snapUser.userId, snapUser.userSecret, startDate, endDate, accounts, type)
    }

    /**
     * Fetches account balance for a specific account.
     */
    fun getAccountBalance(user: User, accountId: String): List<SnapTradeBalanceDto> {
        val snapUser = ensureUserRegistered(user)
        return adapter.getBalances(snapUser.userId, snapUser.userSecret, accountId)
    }

    /**
     * Places an order via SnapTrade trading API.
     */
    fun placeOrder(
        user: User,
        accountId: String,
        action: String,
        symbol: String,
        units: BigDecimal,
        orderType: String = "MARKET",
        limitPrice: BigDecimal? = null,
        timeInForce: String = "DAY"
    ): SnapTradeOrderDto {
        val snapUser = ensureUserRegistered(user)
        log.info("Placing {} order for {} units of {} in account {} for user {}",
            action, units, symbol, accountId, user.id)

        val response = adapter.placeOrder(
            snapUser.userId, snapUser.userSecret, accountId,
            action, symbol, units, orderType, limitPrice, timeInForce
        )

        log.info("Order placed successfully for user {}: {}", user.id, response.brokerageOrderId)
        return response
    }

    /**
     * Cancels an order via SnapTrade trading API.
     */
    fun cancelOrder(user: User, accountId: String, brokerOrderId: String) {
        val snapUser = ensureUserRegistered(user)
        log.info("Cancelling order {} in account {} for user {}", brokerOrderId, accountId, user.id)

        adapter.cancelOrder(snapUser.userId, snapUser.userSecret, accountId, brokerOrderId)

        log.info("Order {} cancelled for user {}", brokerOrderId, user.id)
    }

    data class SnapTradeUserInfo(
        val userId: String,
        val userSecret: String
    )
}
