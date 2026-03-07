package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.broker.config.SnapTradeConfig
import com.portfolio.broker.security.TokenEncryptionService
import com.snaptrade.client.ApiException
import com.snaptrade.client.Configuration
import com.snaptrade.client.Snaptrade
import com.snaptrade.client.model.*
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SnapTradeService(
    private val config: SnapTradeConfig,
    private val userRepository: UserRepository,
    private val encryptionService: TokenEncryptionService
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

    private val snaptrade: Snaptrade by lazy {
        val configuration = Configuration()
        configuration.clientId = config.clientId
        configuration.consumerKey = config.consumerKey
        Snaptrade(configuration)
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

        val snapUserId = user.id.toString()
        log.info("Registering SnapTrade user for user {}", user.id)

        val userSecret: String
        try {
            val response = snaptrade.authentication.registerSnapTradeUser(snapUserId)
                .execute()

            userSecret = response.userSecret
                ?: throw IllegalStateException("SnapTrade registration did not return userSecret")

            log.info("SnapTrade user registered for user {}", user.id)
        } catch (e: ApiException) {
            if (e.code == 400 && e.responseBody?.contains("1012") == true) {
                // Personal keys only allow one user - user already registered.
                // Reset the user secret to recover access.
                log.info("SnapTrade user {} already registered (code 1012), resetting user secret", snapUserId)
                val resetResponse = snaptrade.authentication
                    .resetSnapTradeUserSecret(snapUserId)
                    .execute()

                userSecret = resetResponse.userSecret
                    ?: throw IllegalStateException("SnapTrade resetUserSecret did not return userSecret")
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

        val request = snaptrade.authentication.loginSnapTradeUser(snapUser.userId, snapUser.userSecret)
            .customRedirect(config.redirectUri)

        if (broker != null) {
            request.broker(broker)
        }
        if (reconnectAuthId != null) {
            request.reconnect(reconnectAuthId)
        }

        val response = request.execute()
        // loginSnapTradeUser returns Object at compile time; at runtime it's a Map or LoginRedirectURI
        val redirectUri = when (response) {
            is LoginRedirectURI -> response.redirectURI
            is Map<*, *> -> (response["redirectURI"] ?: response["redirectUri"]) as? String
            else -> null
        } ?: throw IllegalStateException("SnapTrade login did not return redirectURI")
        return redirectUri
    }

    /**
     * Lists all connected accounts for a user.
     */
    fun listAccounts(user: User): List<Account> {
        val snapUser = ensureUserRegistered(user)
        return snaptrade.accountInformation.listUserAccounts(snapUser.userId, snapUser.userSecret).execute()
    }

    /**
     * Lists all brokerage authorizations (connections) for a user.
     */
    fun listConnections(user: User): List<BrokerageAuthorization> {
        val snapUser = ensureUserRegistered(user)
        return snaptrade.connections.listBrokerageAuthorizations(snapUser.userId, snapUser.userSecret).execute()
    }

    /**
     * Fetches positions for a specific account.
     */
    fun fetchPositions(user: User, accountId: String): List<Position> {
        val snapUser = ensureUserRegistered(user)
        return snaptrade.accountInformation.getUserAccountPositions(
            snapUser.userId,
            snapUser.userSecret,
            UUID.fromString(accountId)
        ).execute()
    }

    /**
     * Disconnects a brokerage authorization.
     */
    fun disconnectBrokerage(user: User, authorizationId: String) {
        val snapUser = ensureUserRegistered(user)
        snaptrade.connections.removeBrokerageAuthorization(
            UUID.fromString(authorizationId),
            snapUser.userId,
            snapUser.userSecret
        ).execute()
    }

    /**
     * Lists all available brokerages from SnapTrade.
     */
    fun listAvailableBrokerages(): List<Brokerage> {
        return snaptrade.referenceData.listAllBrokerages().execute()
    }

    data class SnapTradeUserInfo(
        val userId: String,
        val userSecret: String
    )
}
