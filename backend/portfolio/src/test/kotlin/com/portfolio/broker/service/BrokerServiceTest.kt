package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.client.GatewayApiException
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import com.portfolio.exception.ExternalServiceException
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrokerServiceTest {

    private lateinit var service: BrokerService

    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var positionRepository: BrokerPositionRepository
    private lateinit var balanceRepository: BrokerBalanceRepository
    private lateinit var userRepository: UserRepository
    private lateinit var gatewayClient: BrokerGatewayClient
    private lateinit var auditService: AuditService
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        connectionRepository = mockk()
        positionRepository = mockk()
        balanceRepository = mockk()
        userRepository = mockk()
        gatewayClient = mockk()
        auditService = mockk(relaxed = true)

        service = BrokerService(
            connectionRepository = connectionRepository,
            positionRepository = positionRepository,
            balanceRepository = balanceRepository,
            userRepository = userRepository,
            gatewayClient = gatewayClient,
            auditService = auditService,
            objectMapper = objectMapper
        )
    }

    // ========== Connection Management Tests ==========

    @Test
    fun `getUserConnections returns user connections excluding disconnected`() {
        val user = createUser(1L)
        val connections = listOf(
            createConnection(1L, user, "12345", ConnectionStatus.ACTIVE),
            createConnection(2L, user, "67890", ConnectionStatus.ACTIVE)
        )
        every { connectionRepository.findByUserIdWithBroker(1L) } returns connections

        val result = service.getUserConnections(1L)

        assertEquals(2, result.size)
        assertEquals("ACC-12345", result[0].accountNumber)
        assertEquals("ACC-67890", result[1].accountNumber)
    }

    @Test
    fun `getUserConnections returns empty list for user with no connections`() {
        every { connectionRepository.findByUserIdWithBroker(1L) } returns emptyList()

        val result = service.getUserConnections(1L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getUserConnections filters out disconnected connections`() {
        val user = createUser(1L)
        val connections = listOf(
            createConnection(1L, user, "12345", ConnectionStatus.ACTIVE),
            createConnection(2L, user, "67890", ConnectionStatus.DISCONNECTED)
        )
        every { connectionRepository.findByUserIdWithBroker(1L) } returns connections

        val result = service.getUserConnections(1L)

        assertEquals(1, result.size)
        assertEquals("ACTIVE", result[0].status)
    }

    @Test
    fun `getActiveConnections filters by ACTIVE status`() {
        val user = createUser(1L)
        val connections = listOf(
            createConnection(1L, user, "12345", ConnectionStatus.ACTIVE)
        )
        every { connectionRepository.findByUserIdAndStatusWithBroker(1L, ConnectionStatus.ACTIVE) } returns connections

        val result = service.getActiveConnections(1L)

        assertEquals(1, result.size)
        assertEquals("ACTIVE", result[0].status)
    }

    @Test
    fun `getConnection returns connection for valid user`() {
        val user = createUser(1L)
        val connection = createConnection(1L, user, "12345", ConnectionStatus.ACTIVE)
        every { connectionRepository.findByIdAndUserId(1L, 1L) } returns connection

        val result = service.getConnection(1L, 1L)

        assertEquals(1L, result.id)
        assertEquals("12345", result.accountIdExternal)
    }

    @Test
    fun `getConnection throws when connection not found`() {
        every { connectionRepository.findByIdAndUserId(999L, 1L) } returns null

        assertThrows<IllegalArgumentException> {
            service.getConnection(999L, 1L)
        }
    }

    // ========== Disconnect Tests ==========

    @Test
    fun `disconnectBroker marks connections as disconnected`() {
        val user = createUser(1L)
        val connection = createConnection(1L, user, "12345", ConnectionStatus.ACTIVE).apply {
            gatewayConnectionId = "gw-conn-123"
        }
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { gatewayClient.deleteConnection("gw-conn-123") } just runs
        every { connectionRepository.findByGatewayConnectionId("gw-conn-123") } returns listOf(connection)
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.disconnectBroker("gw-conn-123", 1L)

        verify { connectionRepository.save(match { it.status == ConnectionStatus.DISCONNECTED }) }
        verify { gatewayClient.deleteConnection("gw-conn-123") }
    }

    // ========== Error Propagation Tests ==========

    @Test
    fun `createGatewayConnection propagates gateway error message on 401`() {
        val user = createUser(1L)
        val gatewayErrorBody = """{
            "type": "about:blank",
            "title": "Unauthorized",
            "status": 401,
            "detail": "Questrade rejected the token: invalid refresh token",
            "instance": "/api/v1/gateway/connections",
            "code": "BROKER_AUTH_FAILED",
            "timestamp": "2026-07-08T05:00:00Z"
        }"""

        val rawBytes = gatewayErrorBody.toByteArray(Charsets.UTF_8)
        val gatewayException = WebClientResponseException.create(
            401, "Unauthorized", org.springframework.http.HttpHeaders.EMPTY, rawBytes, Charsets.UTF_8, null
        )
        val apiException = GatewayApiException(
            gatewayStatusCode = 401,
            gatewayErrorCode = "BROKER_AUTH_FAILED",
            gatewayDetail = "Questrade rejected the token: invalid refresh token",
            cause = gatewayException
        )

        every { gatewayClient.createConnection(1L, "QUESTRADE", any()) } throws apiException

        val ex = assertThrows<ExternalServiceException> {
            service.createGatewayConnection(user, "QUESTRADE", mapOf("refreshToken" to "invalid"))
        }

        assertEquals("BROKER_AUTH_FAILED", ex.code)
        assertEquals("Questrade rejected the token: invalid refresh token", ex.message)
    }

    @Test
    fun `createGatewayConnection returns generic message when gateway is unreachable`() {
        val user = createUser(1L)

        every { gatewayClient.createConnection(1L, "QUESTRADE", any()) } throws RuntimeException(
            "Connection refused"
        )

        val ex = assertThrows<ExternalServiceException> {
            service.createGatewayConnection(user, "QUESTRADE", mapOf("refreshToken" to "some_token"))
        }

        assertEquals("BROKER_CONNECTION_FAILED", ex.code)
        assertEquals("Failed to connect to broker service. Please try again later.", ex.message)
    }

    @Test
    fun `reconnectConnection updates local connection status on success`() {
        val user = createUser(1L)
        val gatewayConnectionId = "gw-conn-reconnect"
        val connection = BrokerConnection(
            id = 100L,
            user = user,
            gatewayConnectionId = gatewayConnectionId,
            accountIdExternal = "acc-ext-1",
            accountNumber = "12345",
            accountType = "TFSA",
            accountName = "Test Account",
            status = ConnectionStatus.ERROR
        )

        every { gatewayClient.reconnectConnection(gatewayConnectionId, any()) } returns objectMapper.createObjectNode()
        every { connectionRepository.findByGatewayConnectionId(gatewayConnectionId) } returns listOf(connection)
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.reconnectConnection(user, gatewayConnectionId, mapOf("refreshToken" to "new_token"))

        verify { gatewayClient.reconnectConnection(gatewayConnectionId, any()) }
        verify { connectionRepository.save(match { it.status == ConnectionStatus.ACTIVE }) }
    }

    @Test
    fun `reconnectConnection propagates GatewayApiException`() {
        val user = createUser(1L)
        val gatewayConnectionId = "gw-conn-reconnect"
        val connection = BrokerConnection(
            id = 100L,
            user = user,
            gatewayConnectionId = gatewayConnectionId,
            status = ConnectionStatus.ERROR
        )

        val apiException = GatewayApiException(
            gatewayStatusCode = 401,
            gatewayErrorCode = "BROKER_AUTH_FAILED",
            gatewayDetail = "Invalid refresh token"
        )
        every { connectionRepository.findByGatewayConnectionId(gatewayConnectionId) } returns listOf(connection)
        every { gatewayClient.reconnectConnection(gatewayConnectionId, any()) } throws apiException

        val ex = assertThrows<ExternalServiceException> {
            service.reconnectConnection(user, gatewayConnectionId, mapOf("refreshToken" to "invalid"))
        }

        assertEquals("BROKER_AUTH_FAILED", ex.code)
        assertEquals("Invalid refresh token", ex.message)
        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    @Test
    fun `reconnectConnection returns generic message when gateway is unreachable`() {
        val user = createUser(1L)
        val gatewayConnectionId = "gw-conn-unreachable"
        val connection = BrokerConnection(
            id = 200L,
            user = user,
            gatewayConnectionId = gatewayConnectionId,
            status = ConnectionStatus.ERROR
        )

        every { connectionRepository.findByGatewayConnectionId(gatewayConnectionId) } returns listOf(connection)
        every { gatewayClient.reconnectConnection(gatewayConnectionId, any()) } throws RuntimeException(
            "Connection refused"
        )

        val ex = assertThrows<ExternalServiceException> {
            service.reconnectConnection(user, gatewayConnectionId, mapOf("refreshToken" to "some_token"))
        }

        assertEquals("BROKER_CONNECTION_FAILED", ex.code)
        assertEquals("Failed to reconnect to broker service. Please try again later.", ex.message)
    }

    @Test
    fun `reconnectConnection throws when user does not own the connection`() {
        val user = createUser(1L)
        val otherUser = createUser(2L)
        val gatewayConnectionId = "gw-conn-other-user"
        val connection = BrokerConnection(
            id = 300L,
            user = otherUser,
            gatewayConnectionId = gatewayConnectionId,
            status = ConnectionStatus.ACTIVE
        )

        every { connectionRepository.findByGatewayConnectionId(gatewayConnectionId) } returns listOf(connection)

        assertThrows<IllegalArgumentException> {
            service.reconnectConnection(user, gatewayConnectionId, mapOf("refreshToken" to "token"))
        }
        verify(exactly = 0) { gatewayClient.reconnectConnection(any(), any()) }
    }

    @Test
    fun `createGatewayConnection propagates gateway error message on 502`() {
        val user = createUser(1L)
        val gatewayErrorBody = """{
            "type": "about:blank",
            "title": "Bad Gateway",
            "status": 502,
            "detail": "Broker gateway is unreachable",
            "instance": "/api/v1/gateway/connections",
            "code": "BROKER_CONNECTION_FAILED",
            "timestamp": "2026-07-08T05:00:00Z"
        }"""

        val rawBytes = gatewayErrorBody.toByteArray(Charsets.UTF_8)
        val gatewayException = WebClientResponseException.create(
            502, "Bad Gateway", org.springframework.http.HttpHeaders.EMPTY, rawBytes, Charsets.UTF_8, null
        )
        val apiException = GatewayApiException(
            gatewayStatusCode = 502,
            gatewayErrorCode = "BROKER_CONNECTION_FAILED",
            gatewayDetail = "Broker gateway is unreachable",
            cause = gatewayException
        )

        every { gatewayClient.createConnection(1L, "QUESTRADE", any()) } throws apiException

        val ex = assertThrows<ExternalServiceException> {
            service.createGatewayConnection(user, "QUESTRADE", mapOf("refreshToken" to "some_token"))
        }

        assertEquals("BROKER_CONNECTION_FAILED", ex.code)
        assertEquals("Broker gateway is unreachable", ex.message)
    }

    // ========== Helper Methods ==========

    private fun createUser(id: Long): User {
        return User(
            id = id,
            email = "user$id@example.com",
            passwordHash = "hashedPassword",
            name = "Test User"
        )
    }

    private fun createConnection(
        id: Long,
        user: User,
        accountIdExternal: String,
        status: ConnectionStatus
    ): BrokerConnection {
        return BrokerConnection(
            id = id,
            user = user,
            accountIdExternal = accountIdExternal,
            accountNumber = "ACC-$accountIdExternal",
            accountType = "TFSA",
            accountName = "Test Account",
            status = status
        )
    }
}
