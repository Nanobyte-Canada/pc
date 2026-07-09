package com.portfolio.brokergateway.credential

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.exception.ConnectionNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Service
class CredentialService(
    private val repository: GatewayConnectionRepository,
    private val encryptionService: TokenEncryptionService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val refreshLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun createConnection(userId: Long, credentials: BrokerCredentials): String {
        val json = objectMapper.writeValueAsString(credentials)
        val encrypted = encryptionService.encrypt(json)

        val entity = GatewayConnection(
            userId = userId,
            brokerType = credentials.brokerType.name,
            credentialsEncrypted = encrypted
        )

        val saved = repository.save(entity)
        log.info("Created {} connection {} for user {}", credentials.brokerType, saved.id, userId)
        return saved.id
    }

    fun getCredentials(connectionId: String): BrokerCredentials {
        val entity = getConnection(connectionId)
        val json = encryptionService.decrypt(entity.credentialsEncrypted)
        return objectMapper.readValue(json, BrokerCredentials::class.java)
    }

    fun getCredentialsWithRefresh(connectionId: String, adapter: BrokerAdapter): BrokerCredentials {
        val credentials = getCredentials(connectionId)
        val nowEpoch = System.currentTimeMillis() / 1000

        val needsRefresh = when (credentials) {
            is BrokerCredentials.QuestradeCredentials -> {
                val secsLeft = credentials.expiresAtEpochSeconds - nowEpoch
                log.info("Connection {} token expires in {}s (at {})", connectionId, secsLeft, credentials.expiresAtEpochSeconds)
                secsLeft <= 300
            }
            is BrokerCredentials.WealthsimpleCredentials ->
                nowEpoch >= credentials.expiresAtEpochSeconds - 300
            else -> false
        }

        if (!needsRefresh) return credentials

        val lock = refreshLocks.computeIfAbsent(connectionId) { ReentrantLock() }

        if (!lock.tryLock()) {
            lock.lock()
            try {
                return getCredentials(connectionId)
            } finally {
                lock.unlock()
            }
        }

        try {
            val freshCredentials = getCredentials(connectionId)
            val stillNeeds = when (freshCredentials) {
                is BrokerCredentials.QuestradeCredentials ->
                    System.currentTimeMillis() / 1000 >= freshCredentials.expiresAtEpochSeconds - 300
                is BrokerCredentials.WealthsimpleCredentials ->
                    System.currentTimeMillis() / 1000 >= freshCredentials.expiresAtEpochSeconds - 300
                else -> false
            }
            if (!stillNeeds) return freshCredentials

            log.info("Token expiring soon for connection {}, refreshing...", connectionId)
            val refreshed = adapter.refreshAuth(freshCredentials)
            val entity = getConnection(connectionId)
            val json = objectMapper.writeValueAsString(refreshed)
            entity.credentialsEncrypted = encryptionService.encrypt(json)
            entity.lastRefreshedAt = OffsetDateTime.now()
            entity.refreshFailureCount = 0
            entity.updatedAt = OffsetDateTime.now()
            repository.save(entity)
            log.info("Token refreshed and saved for connection {}", connectionId)
            return refreshed
        } catch (e: Exception) {
            log.error("Token refresh failed for {}: {}", connectionId, e.message)
            throw e
        } finally {
            lock.unlock()
        }
    }

    @Transactional
    fun forceRefresh(connectionId: String, adapter: BrokerAdapter): BrokerCredentials {
        val lock = refreshLocks.computeIfAbsent(connectionId) { ReentrantLock() }
        lock.lock()
        try {
            val credentials = getCredentials(connectionId)
            log.info("Force-refreshing token for connection {} (401 retry)", connectionId)
            val refreshed = adapter.refreshAuth(credentials)
            val entity = getConnection(connectionId)
            val json = objectMapper.writeValueAsString(refreshed)
            entity.credentialsEncrypted = encryptionService.encrypt(json)
            entity.lastRefreshedAt = OffsetDateTime.now()
            entity.refreshFailureCount = 0
            entity.updatedAt = OffsetDateTime.now()
            repository.save(entity)
            log.info("Force-refresh succeeded for connection {}", connectionId)
            return refreshed
        } finally {
            lock.unlock()
        }
    }

    fun getConnection(connectionId: String): GatewayConnection {
        return repository.findById(connectionId)
            .orElseThrow { ConnectionNotFoundException(connectionId) }
    }

    fun listConnections(userId: Long): List<GatewayConnection> {
        return repository.findByUserId(userId)
    }

    @Transactional
    fun updateCredentials(connectionId: String, credentials: BrokerCredentials) {
        val entity = getConnection(connectionId)
        val json = objectMapper.writeValueAsString(credentials)
        entity.credentialsEncrypted = encryptionService.encrypt(json)
        entity.lastRefreshedAt = OffsetDateTime.now()
        entity.updatedAt = OffsetDateTime.now()
        repository.save(entity)
        log.info("Updated credentials for connection {}", connectionId)
    }

    fun updateStatus(connectionId: String, status: String, errorMessage: String? = null) {
        val entity = getConnection(connectionId)
        entity.status = status
        entity.errorMessage = errorMessage
        entity.updatedAt = OffsetDateTime.now()
        repository.save(entity)
    }

    fun clearError(connectionId: String) {
        val entity = getConnection(connectionId)
        entity.errorMessage = null
        entity.updatedAt = OffsetDateTime.now()
        repository.save(entity)
    }

    fun updateAccountsJson(connectionId: String, accountsJson: String) {
        val entity = getConnection(connectionId)
        entity.accountsJson = accountsJson
        entity.updatedAt = OffsetDateTime.now()
        repository.save(entity)
    }

    fun deleteConnection(connectionId: String) {
        val entity = getConnection(connectionId)
        repository.delete(entity)
        log.info("Deleted connection {}", connectionId)
    }
}
