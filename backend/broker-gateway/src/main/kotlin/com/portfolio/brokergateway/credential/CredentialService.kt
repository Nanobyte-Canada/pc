package com.portfolio.brokergateway.credential

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.exception.ConnectionNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class CredentialService(
    private val repository: GatewayConnectionRepository,
    private val encryptionService: TokenEncryptionService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

    fun getConnection(connectionId: String): GatewayConnection {
        return repository.findById(connectionId)
            .orElseThrow { ConnectionNotFoundException(connectionId) }
    }

    fun listConnections(userId: Long): List<GatewayConnection> {
        return repository.findByUserId(userId)
    }

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
