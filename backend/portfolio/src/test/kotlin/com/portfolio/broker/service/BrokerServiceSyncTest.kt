package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class BrokerServiceSyncTest {

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

    @Test
    fun `syncConnections validates active gateway connections`() {
        val user = createUser(1L)
        val connection = BrokerConnection(
            id = 10L,
            user = user,
            gatewayConnectionId = "gw-conn-1",
            accountIdExternal = "acc-ext-1",
            accountNumber = "12345",
            accountType = "TFSA",
            accountName = "Test Account",
            status = ConnectionStatus.ACTIVE
        )

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { connectionRepository.findByUserId(1L) } returns listOf(connection)

        val validationResult = objectMapper.createObjectNode().apply {
            put("connected", true)
        }
        every { gatewayClient.validateConnection("gw-conn-1") } returns validationResult
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.syncConnections(1L)

        verify {
            connectionRepository.save(match {
                it.id == 10L && it.status == ConnectionStatus.ACTIVE
            })
        }
    }

    @Test
    fun `syncConnections marks connection as ERROR when validation fails`() {
        val user = createUser(1L)
        val connection = BrokerConnection(
            id = 10L,
            user = user,
            gatewayConnectionId = "gw-conn-1",
            accountIdExternal = "acc-ext-1",
            accountNumber = "12345",
            accountType = "TFSA",
            accountName = "Test Account",
            status = ConnectionStatus.ACTIVE
        )

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { connectionRepository.findByUserId(1L) } returns listOf(connection)

        val validationResult = objectMapper.createObjectNode().apply {
            put("connected", false)
        }
        every { gatewayClient.validateConnection("gw-conn-1") } returns validationResult
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.syncConnections(1L)

        verify {
            connectionRepository.save(match {
                it.id == 10L && it.status == ConnectionStatus.ERROR
            })
        }
    }

    @Test
    fun `syncConnections skips connections without gateway ID`() {
        val user = createUser(1L)
        val legacyConnection = BrokerConnection(
            id = 10L,
            user = user,
            gatewayConnectionId = null,
            accountIdExternal = "acc-ext-1",
            accountNumber = "12345",
            accountType = "TFSA",
            accountName = "Test Account",
            status = ConnectionStatus.ACTIVE
        )

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { connectionRepository.findByUserId(1L) } returns listOf(legacyConnection)

        service.syncConnections(1L)

        verify(exactly = 0) { gatewayClient.validateConnection(any()) }
        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    @Test
    fun `syncConnections handles gateway API failure gracefully`() {
        val user = createUser(1L)
        val connection = BrokerConnection(
            id = 10L,
            user = user,
            gatewayConnectionId = "gw-conn-1",
            accountIdExternal = "acc-ext-1",
            accountNumber = "12345",
            accountType = "TFSA",
            accountName = "Test Account",
            status = ConnectionStatus.ACTIVE
        )

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { connectionRepository.findByUserId(1L) } returns listOf(connection)
        every { gatewayClient.validateConnection("gw-conn-1") } throws RuntimeException("Gateway unavailable")
        every { connectionRepository.save(any()) } answers { firstArg() }

        // Should not throw
        service.syncConnections(1L)

        verify {
            connectionRepository.save(match {
                it.status == ConnectionStatus.ERROR
            })
        }
    }

    private fun createUser(id: Long): User {
        return User(
            id = id,
            email = "user$id@example.com",
            passwordHash = "hashedPassword",
            name = "Test User"
        )
    }
}
