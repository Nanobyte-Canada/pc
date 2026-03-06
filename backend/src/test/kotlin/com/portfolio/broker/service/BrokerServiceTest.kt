package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrokerServiceTest {

    private lateinit var service: BrokerService

    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var positionRepository: BrokerPositionRepository
    private lateinit var userRepository: UserRepository
    private lateinit var snapTradeService: SnapTradeService
    private lateinit var auditService: AuditService

    @BeforeEach
    fun setup() {
        connectionRepository = mockk()
        positionRepository = mockk()
        userRepository = mockk()
        snapTradeService = mockk()
        auditService = mockk(relaxed = true)

        service = BrokerService(
            connectionRepository = connectionRepository,
            positionRepository = positionRepository,
            userRepository = userRepository,
            snapTradeService = snapTradeService,
            auditService = auditService
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

    // ========== Connection Portal URL Tests ==========

    @Test
    fun `getConnectionPortalUrl calls SnapTradeService`() {
        val user = createUser(1L)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { snapTradeService.getConnectionPortalUrl(user, "questrade", null) } returns "https://snaptrade.com/portal"

        val result = service.getConnectionPortalUrl(1L, "questrade")

        assertEquals("https://snaptrade.com/portal", result)
        verify { snapTradeService.getConnectionPortalUrl(user, "questrade", null) }
    }

    @Test
    fun `getConnectionPortalUrl throws when user not found`() {
        every { userRepository.findById(999L) } returns Optional.empty()

        assertThrows<IllegalArgumentException> {
            service.getConnectionPortalUrl(999L)
        }
    }

    // ========== Disconnect Tests ==========

    @Test
    fun `disconnectBroker marks connections as disconnected`() {
        val user = createUser(1L)
        val connection = createConnection(1L, user, "12345", ConnectionStatus.ACTIVE).apply {
            snaptradeAuthorizationId = "auth-uuid-123"
        }
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { snapTradeService.disconnectBrokerage(user, "auth-uuid-123") } just runs
        every { connectionRepository.findByUserId(1L) } returns listOf(connection)
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.disconnectBroker("auth-uuid-123", 1L)

        verify { connectionRepository.save(match { it.status == ConnectionStatus.DISCONNECTED }) }
        verify { snapTradeService.disconnectBrokerage(user, "auth-uuid-123") }
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
