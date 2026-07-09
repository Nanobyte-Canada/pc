package com.portfolio.brokergateway.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.adapter.dto.ConnectionValidationResult
import com.portfolio.brokergateway.api.dto.CreateConnectionRequest
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.credential.GatewayConnection
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import com.portfolio.brokergateway.exception.BrokerConnectionException
import com.portfolio.brokergateway.exception.BrokerUnsupportedOperationException
import com.portfolio.brokergateway.exception.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.OffsetDateTime
import java.util.UUID

class ConnectionControllerIntegrationTest {

    private val credentialService = mockk<CredentialService>()
    private val adapterRegistry = mockk<AdapterRegistry>()
    private val adapter = mockk<BrokerAdapter>()
    private val objectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    private lateinit var mockMvc: MockMvc
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
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `createConnection with invalid Questrade token returns 401 with BROKER_AUTH_FAILED`() {
        val requestBody = """{
            "userId": $testUserId,
            "brokerType": "QUESTRADE",
            "credentials": {
                "refreshToken": "invalid_token"
            }
        }"""

        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.createConnection(testUserId, any()) } returns testConnectionId
        every { adapter.refreshAuth(any()) } throws BrokerAuthenticationException(
            message = "Questrade rejected the token: invalid refresh token",
            brokerType = BrokerType.QUESTRADE
        )

        mockMvc.perform(post("/api/v1/gateway/connections")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.detail").value("Questrade rejected the token: invalid refresh token"))
            .andExpect(jsonPath("$.code").value("BROKER_AUTH_FAILED"))
            .andExpect(jsonPath("$.instance").value("/api/v1/gateway/connections"))
    }

    @Test
    fun `createConnection with valid Questrade token returns 201 Created`() {
        val requestBody = """{
            "userId": $testUserId,
            "brokerType": "QUESTRADE",
            "credentials": {
                "refreshToken": "valid_refresh_token"
            }
        }"""

        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.createConnection(testUserId, any()) } returns testConnectionId
        every { adapter.refreshAuth(any()) } returns refreshedCredentials
        every { credentialService.updateCredentials(testConnectionId, refreshedCredentials) } returns Unit
        every { adapter.validateConnection(refreshedCredentials) } returns ConnectionValidationResult(connected = true)
        every { adapter.listAccounts(refreshedCredentials) } returns emptyList()
        every { credentialService.updateAccountsJson(testConnectionId, "[]") } returns Unit
        every { credentialService.updateStatus(testConnectionId, "ACTIVE", null) } returns Unit
        every { credentialService.getConnection(testConnectionId) } returns GatewayConnection(
            id = testConnectionId,
            userId = testUserId,
            brokerType = BrokerType.QUESTRADE.name,
            credentialsEncrypted = "encrypted",
            status = "ACTIVE",
            createdAt = OffsetDateTime.now()
        )

        mockMvc.perform(post("/api/v1/gateway/connections")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.connectionId").value(testConnectionId))
            .andExpect(jsonPath("$.brokerType").value("QUESTRADE"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `createConnection with broker that throws BrokerConnectionException returns 502`() {
        val requestBody = """{
            "userId": $testUserId,
            "brokerType": "QUESTRADE",
            "credentials": {
                "refreshToken": "valid_token"
            }
        }"""

        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } returns adapter
        every { credentialService.createConnection(testUserId, any()) } returns testConnectionId
        every { adapter.refreshAuth(any()) } throws BrokerConnectionException(
            message = "Questrade API returned 502 Bad Gateway",
            brokerType = BrokerType.QUESTRADE
        )

        mockMvc.perform(post("/api/v1/gateway/connections")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.code").value("BROKER_CONNECTION_FAILED"))
            .andExpect(jsonPath("$.detail").value("Questrade API returned 502 Bad Gateway"))
    }

    @Test
    fun `createConnection with unsupported broker returns 501 Not Implemented`() {
        val requestBody = """{
            "userId": $testUserId,
            "brokerType": "QUESTRADE",
            "credentials": {}
        }"""

        every { adapterRegistry.getAdapter(BrokerType.QUESTRADE) } throws BrokerUnsupportedOperationException(
            message = "No adapter registered for broker: QUESTRADE",
            brokerType = BrokerType.QUESTRADE
        )

        mockMvc.perform(post("/api/v1/gateway/connections")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isNotImplemented)
            .andExpect(jsonPath("$.status").value(501))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_OPERATION"))
            .andExpect(jsonPath("$.detail").value("No adapter registered for broker: QUESTRADE"))
    }
}
