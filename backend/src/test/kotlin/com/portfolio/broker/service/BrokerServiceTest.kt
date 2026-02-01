package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.client.BrokerClientFactory
import com.portfolio.broker.client.BrokerNotSupportedException
import com.portfolio.broker.config.BrokerConfig
import com.portfolio.broker.dto.UpdateBrokerPrefsRequest
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import com.portfolio.broker.security.TokenEncryptionService
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrokerServiceTest {

    private lateinit var service: BrokerService

    private lateinit var brokerRepository: BrokerRepository
    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var tokenRepository: ConnectionTokenRepository
    private lateinit var positionRepository: BrokerPositionRepository
    private lateinit var fetchLogRepository: PositionFetchLogRepository
    private lateinit var oauthStateRepository: BrokerOAuthStateRepository
    private lateinit var userPrefsRepository: UserBrokerPrefsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var brokerClientFactory: BrokerClientFactory
    private lateinit var encryptionService: TokenEncryptionService
    private lateinit var auditService: AuditService
    private lateinit var config: BrokerConfig

    @BeforeEach
    fun setup() {
        brokerRepository = mockk()
        connectionRepository = mockk()
        tokenRepository = mockk()
        positionRepository = mockk()
        fetchLogRepository = mockk()
        oauthStateRepository = mockk()
        userPrefsRepository = mockk()
        userRepository = mockk()
        brokerClientFactory = mockk()
        encryptionService = mockk()
        auditService = mockk(relaxed = true)
        config = mockk()

        service = BrokerService(
            brokerRepository = brokerRepository,
            connectionRepository = connectionRepository,
            tokenRepository = tokenRepository,
            positionRepository = positionRepository,
            fetchLogRepository = fetchLogRepository,
            oauthStateRepository = oauthStateRepository,
            userPrefsRepository = userPrefsRepository,
            userRepository = userRepository,
            brokerClientFactory = brokerClientFactory,
            encryptionService = encryptionService,
            auditService = auditService,
            config = config
        )
    }

    // ========== Broker Listing Tests ==========

    @Test
    fun `getAvailableBrokers returns active brokers`() {
        val brokers = listOf(
            createBroker(1, "QUESTRADE", "Questrade"),
            createBroker(2, "IBKR", "Interactive Brokers")
        )
        every { brokerRepository.findByStatusOrderByNameAsc(BrokerStatus.ACTIVE) } returns brokers

        val result = service.getAvailableBrokers()

        assertEquals(2, result.size)
        assertEquals("QUESTRADE", result[0].code)
        assertEquals("IBKR", result[1].code)
    }

    @Test
    fun `getAvailableBrokers returns empty list when no active brokers`() {
        every { brokerRepository.findByStatusOrderByNameAsc(BrokerStatus.ACTIVE) } returns emptyList()

        val result = service.getAvailableBrokers()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getBrokerByCode returns broker when found`() {
        val broker = createBroker(1, "QUESTRADE", "Questrade")
        every { brokerRepository.findByCode("QUESTRADE") } returns broker

        val result = service.getBrokerByCode("questrade")

        assertEquals("QUESTRADE", result.code)
        assertEquals("Questrade", result.name)
    }

    @Test
    fun `getBrokerByCode throws when broker not found`() {
        every { brokerRepository.findByCode("UNKNOWN") } returns null

        assertThrows<BrokerNotSupportedException> {
            service.getBrokerByCode("unknown")
        }
    }

    // ========== Connection Management Tests ==========

    @Test
    fun `getUserConnections returns user connections`() {
        val user = createUser(1L)
        val broker = createBroker(1, "QUESTRADE", "Questrade")
        val connections = listOf(
            createConnection(1L, user, broker, "12345", ConnectionStatus.ACTIVE),
            createConnection(2L, user, broker, "67890", ConnectionStatus.ACTIVE)
        )
        every { connectionRepository.findByUserIdWithBroker(1L) } returns connections

        val result = service.getUserConnections(1L)

        assertEquals(2, result.size)
        assertEquals("12345", result[0].accountIdExternal)
        assertEquals("67890", result[1].accountIdExternal)
    }

    @Test
    fun `getUserConnections returns empty list for user with no connections`() {
        every { connectionRepository.findByUserIdWithBroker(1L) } returns emptyList()

        val result = service.getUserConnections(1L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getActiveConnections filters by ACTIVE status`() {
        val user = createUser(1L)
        val broker = createBroker(1, "QUESTRADE", "Questrade")
        val connections = listOf(
            createConnection(1L, user, broker, "12345", ConnectionStatus.ACTIVE)
        )
        every { connectionRepository.findByUserIdAndStatusWithBroker(1L, ConnectionStatus.ACTIVE) } returns connections

        val result = service.getActiveConnections(1L)

        assertEquals(1, result.size)
        assertEquals(ConnectionStatus.ACTIVE, result[0].status)
    }

    @Test
    fun `getConnection returns connection for valid user`() {
        val user = createUser(1L)
        val broker = createBroker(1, "QUESTRADE", "Questrade")
        val connection = createConnection(1L, user, broker, "12345", ConnectionStatus.ACTIVE)
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

    // ========== User Preferences Tests ==========

    @Test
    fun `getUserPrefs returns existing preferences`() {
        val user = createUser(1L)
        val prefs = UserBrokerPrefs(
            id = 1L,
            user = user,
            autoFetchEnabled = true,
            fetchTimeUtc = LocalTime.of(8, 0)
        )
        every { userPrefsRepository.findByUserId(1L) } returns prefs

        val result = service.getUserPrefs(1L)

        assertTrue(result.autoFetchEnabled)
        assertEquals("08:00", result.fetchTimeUtc)
    }

    @Test
    fun `getUserPrefs returns defaults when no preferences exist`() {
        every { userPrefsRepository.findByUserId(1L) } returns null

        val result = service.getUserPrefs(1L)

        assertFalse(result.autoFetchEnabled)
        assertEquals("06:00", result.fetchTimeUtc)
    }

    @Test
    fun `updateUserPrefs creates new preferences when none exist`() {
        val user = createUser(1L)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userPrefsRepository.findByUserId(1L) } returns null
        every { userPrefsRepository.save(any()) } answers { firstArg() }

        val request = UpdateBrokerPrefsRequest(
            autoFetchEnabled = true,
            fetchTimeUtc = "07:30"
        )

        val result = service.updateUserPrefs(1L, request)

        assertTrue(result.autoFetchEnabled)
        assertEquals("07:30", result.fetchTimeUtc)
        verify { userPrefsRepository.save(any()) }
    }

    @Test
    fun `updateUserPrefs updates existing preferences`() {
        val user = createUser(1L)
        val existingPrefs = UserBrokerPrefs(
            id = 1L,
            user = user,
            autoFetchEnabled = false,
            fetchTimeUtc = LocalTime.of(6, 0)
        )
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userPrefsRepository.findByUserId(1L) } returns existingPrefs
        every { userPrefsRepository.save(any()) } answers { firstArg() }

        val request = UpdateBrokerPrefsRequest(
            autoFetchEnabled = true,
            fetchTimeUtc = "09:00"
        )

        val result = service.updateUserPrefs(1L, request)

        assertTrue(result.autoFetchEnabled)
        assertEquals("09:00", result.fetchTimeUtc)
    }

    // ========== Helper Methods ==========

    private fun createUser(id: Long): User {
        return User(
            id = id,
            email = "user$id@example.com",
            passwordHash = "hashedPassword",
            name = "Test User",
            roles = mutableSetOf()
        )
    }

    private fun createBroker(id: Long, code: String, name: String): Broker {
        return Broker(
            id = id,
            code = code,
            name = name,
            authType = AuthType.OAUTH2,
            status = BrokerStatus.ACTIVE
        )
    }

    private fun createConnection(
        id: Long,
        user: User,
        broker: Broker,
        accountIdExternal: String,
        status: ConnectionStatus
    ): BrokerConnection {
        return BrokerConnection(
            id = id,
            user = user,
            broker = broker,
            accountIdExternal = accountIdExternal,
            accountNumber = "ACC-$accountIdExternal",
            accountType = "TFSA",
            accountName = "Test Account",
            status = status
        )
    }
}
