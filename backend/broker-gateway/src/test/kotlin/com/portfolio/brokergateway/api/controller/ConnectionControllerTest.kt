package com.portfolio.brokergateway.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.adapter.dto.ConnectionValidationResult
import com.portfolio.brokergateway.api.dto.ConnectionResponse
import com.portfolio.brokergateway.api.dto.CreateConnectionRequest
import com.portfolio.brokergateway.api.dto.ReconnectRequest
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.credential.GatewayConnection
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import com.portfolio.brokergateway.exception.BrokerConnectionException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConnectionControllerTest {

    private val credentialService = mockk<CredentialService>()
    private val adapterRegistry = mockk<AdapterRegistry>()
    private val adapter = mockk<BrokerAdapter>()
    private val objectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    private lateinit var controller: ConnectionController

    private val testUserId = 12345L
    private val testConnectionId = UUID.randomUUID().toString()
    private val refreshedCredentials = BrokerCredentials.QuestradeCredentials(
        refreshToken = "valid_refresh_token",
        accessToken = "new_access_token",
        apiServerUrl = "https://api01.iq.questrade.com",
        expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 86400,
        usePractice = false
    )

    @BeforeEach
    fun setUp() {
        controller = ConnectionController(
            credentialService = credentialService,
            adapterRegistry = adapterRegistry,
            objectMapper = objectMapper
        )
    }

    @Test
    fun `createConnection returns 201 with valid credentials`() {
        val request = CreateConnectionRequest(
            userId = testUserId,
            brokerType = BrokerType.QUESTRADE,
            credentials = mapOf("refreshToken" to "valid_refresh_token")
        )

        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.createConnection(testUserId, any()) } returns testConnectionId
        every { adapter.refreshAuth(any()) } returns refreshedCredentials
        every { credentialService.updateCredentials(testConnectionId, refreshedCredentials) } returns Unit
        every { adapter.validateConnection(refreshedCredentials) } returns ConnectionValidationResult(connected = true)
        every { adapter.listAccounts(refreshedCredentials) } returns emptyList()
        every { credentialService.updateAccountsJson(testConnectionId, "[]") } returns Unit
        every { credentialService.updateStatus(testConnectionId, "ACTIVE", null) } returns Unit
        every { credentialService.getConnection(testConnectionId) } returns createTestGatewayConnection("ACTIVE")

        val response = controller.createConnection(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(testConnectionId, body.connectionId)
        assertEquals(BrokerType.QUESTRADE, body.brokerType)
        assertEquals("ACTIVE", body.status)

        verify { credentialService.createConnection(testUserId, any()) }
        verify { adapter.refreshAuth(any()) }
        verify { credentialService.updateCredentials(testConnectionId, refreshedCredentials) }
        verify { adapter.validateConnection(refreshedCredentials) }
        verify { credentialService.updateStatus(testConnectionId, "ACTIVE", null) }
    }

    @Test
    fun `createConnection propagates BrokerAuthenticationException from refreshAuth`() {
        val request = CreateConnectionRequest(
            userId = testUserId,
            brokerType = BrokerType.QUESTRADE,
            credentials = mapOf("refreshToken" to "invalid_token")
        )

        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.createConnection(testUserId, any()) } returns testConnectionId
        every { adapter.refreshAuth(any()) } throws BrokerAuthenticationException(
            message = "Questrade rejected the token: invalid refresh token",
            brokerType = BrokerType.QUESTRADE
        )

        try {
            controller.createConnection(request)
            throw AssertionError("Expected BrokerAuthenticationException to propagate")
        } catch (e: BrokerAuthenticationException) {
            assertEquals("Questrade rejected the token: invalid refresh token", e.message)
            assertEquals(BrokerType.QUESTRADE, e.brokerType)
        }

        verify { credentialService.createConnection(testUserId, any()) }
        verify { adapter.refreshAuth(any()) }
        verify(exactly = 0) { adapter.validateConnection(any()) }
        verify(exactly = 0) { credentialService.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `createConnection propagates BrokerConnectionException from refreshAuth`() {
        val request = CreateConnectionRequest(
            userId = testUserId,
            brokerType = BrokerType.QUESTRADE,
            credentials = mapOf("refreshToken" to "valid_token")
        )

        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.createConnection(testUserId, any()) } returns testConnectionId
        every { adapter.refreshAuth(any()) } throws BrokerConnectionException(
            message = "Questrade API unreachable",
            brokerType = BrokerType.QUESTRADE
        )

        try {
            controller.createConnection(request)
            throw AssertionError("Expected BrokerConnectionException to propagate")
        } catch (e: BrokerConnectionException) {
            assertEquals("Questrade API unreachable", e.message)
            assertEquals(BrokerType.QUESTRADE, e.brokerType)
        }

        verify { credentialService.createConnection(testUserId, any()) }
        verify { adapter.refreshAuth(any()) }
        verify(exactly = 0) { adapter.validateConnection(any()) }
        verify(exactly = 0) { credentialService.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `createConnection does not call updateCredentials when refreshAuth returns the same instance`() {
        val request = CreateConnectionRequest(
            userId = testUserId,
            brokerType = BrokerType.QUESTRADE,
            credentials = mapOf("refreshToken" to "valid_refresh_token")
        )

        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.createConnection(testUserId, any()) } returns testConnectionId
        // refreshAuth returns the same reference (firstArg)
        every { adapter.refreshAuth(any()) } answers { firstArg() }
        every { adapter.validateConnection(any()) } returns ConnectionValidationResult(connected = true)
        every { adapter.listAccounts(any()) } returns emptyList()
        every { credentialService.updateAccountsJson(testConnectionId, "[]") } returns Unit
        every { credentialService.updateStatus(testConnectionId, "ACTIVE", null) } returns Unit
        every { credentialService.getConnection(testConnectionId) } returns createTestGatewayConnection("ACTIVE")

        val response = controller.createConnection(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        assertEquals("ACTIVE", response.body!!.status)

        // verify updateCredentials was NOT called since refreshed is the same reference
        verify(exactly = 0) { credentialService.updateCredentials(testConnectionId, any()) }
    }

    @Test
    fun `createConnection returns 201 with ERROR status when validation fails`() {
        val request = CreateConnectionRequest(
            userId = testUserId,
            brokerType = BrokerType.QUESTRADE,
            credentials = mapOf("refreshToken" to "valid_refresh_token")
        )

        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.createConnection(testUserId, any()) } returns testConnectionId
        every { adapter.refreshAuth(any()) } returns refreshedCredentials
        every { credentialService.updateCredentials(testConnectionId, refreshedCredentials) } returns Unit
        every { adapter.validateConnection(refreshedCredentials) } returns ConnectionValidationResult(
            connected = false,
            message = "Invalid API server"
        )
        every { credentialService.updateStatus(testConnectionId, "ERROR", "Invalid API server") } returns Unit
        every { credentialService.getConnection(testConnectionId) } returns createTestGatewayConnection("ERROR", "Invalid API server")

        val response = controller.createConnection(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals("ERROR", body!!.status)
        assertEquals("Invalid API server", body.errorMessage)

        verify { credentialService.updateStatus(testConnectionId, "ERROR", "Invalid API server") }
    }

    @Test
    fun `reconnectConnection updates credentials and returns 200`() {
        val request = ReconnectRequest(
            credentials = mapOf("refreshToken" to "new_refresh_token")
        )
        val existingConnection = createTestGatewayConnection("ACTIVE")

        every { credentialService.getConnection(testConnectionId) } returns existingConnection
        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.updateCredentials(testConnectionId, any()) } returns Unit
        every { adapter.refreshAuth(any()) } returns refreshedCredentials
        every { adapter.validateConnection(refreshedCredentials) } returns ConnectionValidationResult(connected = true)
        every { adapter.listAccounts(refreshedCredentials) } returns emptyList()
        every { credentialService.updateStatus(testConnectionId, "ACTIVE", null) } returns Unit
        every { credentialService.updateAccountsJson(testConnectionId, "[]") } returns Unit
        every { credentialService.clearError(testConnectionId) } returns Unit

        val response = controller.reconnectConnection(testConnectionId, request)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(testConnectionId, body.connectionId)
        assertEquals(BrokerType.QUESTRADE, body.brokerType)

        verify { credentialService.updateCredentials(testConnectionId, any()) }
        verify { adapter.refreshAuth(any()) }
        verify { adapter.validateConnection(any()) }
        verify { credentialService.clearError(testConnectionId) }
    }

    @Test
    fun `reconnectConnection propagates BrokerAuthenticationException`() {
        val request = ReconnectRequest(
            credentials = mapOf("refreshToken" to "invalid_token")
        )
        val existingConnection = createTestGatewayConnection("ACTIVE")

        every { credentialService.getConnection(testConnectionId) } returns existingConnection
        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.updateCredentials(testConnectionId, any()) } returns Unit
        every { adapter.refreshAuth(any()) } throws BrokerAuthenticationException(
            message = "Questrade rejected the token",
            brokerType = BrokerType.QUESTRADE
        )

        try {
            controller.reconnectConnection(testConnectionId, request)
            throw AssertionError("Expected BrokerAuthenticationException to propagate")
        } catch (e: BrokerAuthenticationException) {
            assertEquals("Questrade rejected the token", e.message)
        }

        verify { credentialService.updateCredentials(testConnectionId, any()) }
        verify { adapter.refreshAuth(any()) }
        verify(exactly = 0) { adapter.validateConnection(any()) }
    }

    @Test
    fun `reconnectConnection returns 200 with ERROR status when validation fails`() {
        val request = ReconnectRequest(
            credentials = mapOf("refreshToken" to "new_refresh_token")
        )
        val existingConnection = createTestGatewayConnection("ACTIVE")

        every { credentialService.getConnection(testConnectionId) } returns existingConnection
        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.updateCredentials(testConnectionId, any()) } returns Unit
        every { adapter.refreshAuth(any()) } returns refreshedCredentials
        every { credentialService.updateCredentials(testConnectionId, refreshedCredentials) } returns Unit
        every { adapter.validateConnection(refreshedCredentials) } returns ConnectionValidationResult(
            connected = false,
            message = "Invalid API server"
        )
        every { credentialService.updateStatus(testConnectionId, "ERROR", "Invalid API server") } returns Unit
        every { credentialService.getConnection(testConnectionId) } returns createTestGatewayConnection("ERROR", "Invalid API server")

        val response = controller.reconnectConnection(testConnectionId, request)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals("ERROR", body!!.status)
        assertEquals("Invalid API server", body.errorMessage)

        verify { credentialService.updateStatus(testConnectionId, "ERROR", "Invalid API server") }
    }

    private fun createTestGatewayConnection(status: String, errorMessage: String? = null): GatewayConnection {
        return GatewayConnection(
            id = testConnectionId,
            userId = testUserId,
            brokerType = BrokerType.QUESTRADE.name,
            credentialsEncrypted = "encrypted",
            status = status,
            errorMessage = errorMessage
        )
    }
}
