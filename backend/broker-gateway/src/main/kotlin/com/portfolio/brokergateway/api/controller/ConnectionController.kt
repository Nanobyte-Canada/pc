package com.portfolio.brokergateway.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.api.dto.ConnectionListResponse
import com.portfolio.brokergateway.api.dto.ConnectionResponse
import com.portfolio.brokergateway.api.dto.CreateConnectionRequest
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.credential.GatewayConnection
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/gateway/connections")
class ConnectionController(
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun createConnection(@RequestBody request: CreateConnectionRequest): ResponseEntity<ConnectionResponse> {
        val adapter = adapterRegistry.getAdapter(request.brokerType)
        var credentials = parseCredentials(request)
        val connectionId = credentialService.createConnection(request.userId, credentials)

        // Exchange initial tokens before validation (e.g., Questrade refresh_token → access_token + api_server)
        val refreshed = adapter.refreshAuth(credentials)
        if (refreshed !== credentials) {
            credentials = refreshed
            credentialService.updateCredentials(connectionId, credentials)
        }

        val validation = adapter.validateConnection(credentials)
        if (!validation.connected) {
            credentialService.updateStatus(connectionId, "ERROR", validation.message)
        } else {
            credentialService.updateStatus(connectionId, "ACTIVE")
            val accounts = adapter.listAccounts(credentials)
            credentialService.updateAccountsJson(connectionId, objectMapper.writeValueAsString(accounts))
        }

        val updated = credentialService.getConnection(connectionId)
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(updated))
    }

    @GetMapping
    fun listConnections(@RequestParam userId: Long): ResponseEntity<ConnectionListResponse> {
        val connections = credentialService.listConnections(userId)
        return ResponseEntity.ok(ConnectionListResponse(connections.map { toResponse(it) }))
    }

    @GetMapping("/{connectionId}")
    fun getConnection(@PathVariable connectionId: String): ResponseEntity<ConnectionResponse> {
        val entity = credentialService.getConnection(connectionId)
        return ResponseEntity.ok(toResponse(entity))
    }

    @DeleteMapping("/{connectionId}")
    fun deleteConnection(@PathVariable connectionId: String): ResponseEntity<Void> {
        credentialService.deleteConnection(connectionId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{connectionId}/validate")
    fun validateConnection(@PathVariable connectionId: String): ResponseEntity<Map<String, Any?>> {
        val rawCredentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(rawCredentials.brokerType)
        var credentials = credentialService.getCredentialsWithRefresh(connectionId, adapter)
        var result = adapter.validateConnection(credentials)

        if (!result.connected) {
            log.warn("Validation failed for connection {}, force-refreshing...", connectionId)
            try {
                credentials = credentialService.forceRefresh(connectionId, adapter)
                result = adapter.validateConnection(credentials)
            } catch (e: Exception) {
                log.warn("Force-refresh failed for connection {}: {}", connectionId, e.message)
            }
        }

        return ResponseEntity.ok(mapOf(
            "connected" to result.connected,
            "message" to result.message,
            "needsReauth" to result.needsReauth
        ))
    }

    @PostMapping("/{connectionId}/refresh")
    fun refreshConnection(@PathVariable connectionId: String): ResponseEntity<Map<String, String>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        val refreshed = adapter.refreshAuth(credentials)
        credentialService.updateCredentials(connectionId, refreshed)
        return ResponseEntity.ok(mapOf("status" to "REFRESHED"))
    }

    private fun parseCredentials(request: CreateConnectionRequest): BrokerCredentials {
        val json = objectMapper.writeValueAsString(
            request.credentials + ("brokerType" to request.brokerType.name)
        )
        return objectMapper.readValue(json, BrokerCredentials::class.java)
    }

    private fun toResponse(entity: GatewayConnection) = ConnectionResponse(
        connectionId = entity.id,
        brokerType = BrokerType.valueOf(entity.brokerType),
        status = entity.status,
        accountsJson = entity.accountsJson,
        lastValidatedAt = entity.lastValidatedAt,
        lastRefreshedAt = entity.lastRefreshedAt,
        errorMessage = entity.errorMessage,
        createdAt = entity.createdAt
    )
}
