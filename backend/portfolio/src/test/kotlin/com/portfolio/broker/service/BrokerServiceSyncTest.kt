package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.adapter.SnapTradeAccountDto
import com.portfolio.broker.adapter.SnapTradeConnectionDto
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
    private lateinit var snapTradeService: SnapTradeService
    private lateinit var auditService: AuditService
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        connectionRepository = mockk()
        positionRepository = mockk()
        balanceRepository = mockk()
        userRepository = mockk()
        snapTradeService = mockk()
        auditService = mockk(relaxed = true)

        service = BrokerService(
            connectionRepository = connectionRepository,
            positionRepository = positionRepository,
            balanceRepository = balanceRepository,
            userRepository = userRepository,
            snapTradeService = snapTradeService,
            auditService = auditService,
            objectMapper = objectMapper
        )
    }

    @Test
    fun `syncConnections creates new connection for new account`() {
        val user = createUser(1L)
        val authId = UUID.randomUUID()
        val accountId = UUID.randomUUID()

        val auth = SnapTradeConnectionDto(id = authId, disabled = false, brokerageName = null, brokerLogoUrl = null, type = null)
        val account = SnapTradeAccountDto(
            id = accountId, brokerageAuthorization = authId,
            number = "12345", institutionName = "Questrade", name = "TFSA", currency = null
        )

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { snapTradeService.listConnections(user) } returns listOf(auth)
        every { snapTradeService.listAccounts(user) } returns listOf(account)
        every { connectionRepository.findByUserIdAndAccountIdExternal(1L, accountId.toString()) } returns null
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.syncConnections(1L)

        verify {
            connectionRepository.save(match {
                it.accountIdExternal == accountId.toString() &&
                it.status == ConnectionStatus.ACTIVE &&
                it.accountNumber == "12345" &&
                it.accountName == "TFSA"
            })
        }
    }

    @Test
    fun `syncConnections updates existing connection without duplicating`() {
        val user = createUser(1L)
        val authId = UUID.randomUUID()
        val accountId = UUID.randomUUID()

        val existingConnection = BrokerConnection(
            id = 10L,
            user = user,
            accountIdExternal = accountId.toString(),
            accountNumber = "OLD-NUMBER",
            accountType = "OldBroker",
            accountName = "Old Name",
            status = ConnectionStatus.PENDING
        )

        val auth = SnapTradeConnectionDto(id = authId, disabled = false, brokerageName = null, brokerLogoUrl = null, type = null)
        val account = SnapTradeAccountDto(
            id = accountId, brokerageAuthorization = authId,
            number = "NEW-NUMBER", institutionName = "Questrade", name = "Updated TFSA", currency = null
        )

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { snapTradeService.listConnections(user) } returns listOf(auth)
        every { snapTradeService.listAccounts(user) } returns listOf(account)
        every { connectionRepository.findByUserIdAndAccountIdExternal(1L, accountId.toString()) } returns existingConnection
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.syncConnections(1L)

        verify {
            connectionRepository.save(match {
                it.id == 10L &&
                it.accountNumber == "NEW-NUMBER" &&
                it.accountName == "Updated TFSA" &&
                it.status == ConnectionStatus.ACTIVE
            })
        }
    }

    @Test
    fun `syncConnections is idempotent - calling twice does not create duplicates`() {
        val user = createUser(1L)
        val authId = UUID.randomUUID()
        val accountId = UUID.randomUUID()

        val auth = SnapTradeConnectionDto(id = authId, disabled = false, brokerageName = null, brokerLogoUrl = null, type = null)
        val account = SnapTradeAccountDto(
            id = accountId, brokerageAuthorization = authId,
            number = "12345", institutionName = "Questrade", name = "TFSA", currency = null
        )

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { snapTradeService.listConnections(user) } returns listOf(auth)
        every { snapTradeService.listAccounts(user) } returns listOf(account)

        // First call: no existing connection
        every { connectionRepository.findByUserIdAndAccountIdExternal(1L, accountId.toString()) } returns null
        val savedSlot = slot<BrokerConnection>()
        every { connectionRepository.save(capture(savedSlot)) } answers { firstArg() }

        service.syncConnections(1L)

        val createdConnection = savedSlot.captured

        // Second call: connection exists now
        every { connectionRepository.findByUserIdAndAccountIdExternal(1L, accountId.toString()) } returns createdConnection
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.syncConnections(1L)

        // Verify save was called exactly twice (once create, once update) - not 3 times
        verify(exactly = 2) { connectionRepository.save(any()) }
    }

    @Test
    fun `syncConnections sets status to ERROR when authorization is disabled`() {
        val user = createUser(1L)
        val authId = UUID.randomUUID()
        val accountId = UUID.randomUUID()

        val auth = SnapTradeConnectionDto(id = authId, disabled = true, brokerageName = null, brokerLogoUrl = null, type = null)
        val account = SnapTradeAccountDto(
            id = accountId, brokerageAuthorization = authId,
            number = "12345", institutionName = "Questrade", name = "TFSA", currency = null
        )

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { snapTradeService.listConnections(user) } returns listOf(auth)
        every { snapTradeService.listAccounts(user) } returns listOf(account)
        every { connectionRepository.findByUserIdAndAccountIdExternal(1L, accountId.toString()) } returns null
        every { connectionRepository.save(any()) } answers { firstArg() }

        service.syncConnections(1L)

        verify {
            connectionRepository.save(match {
                it.status == ConnectionStatus.ERROR
            })
        }
    }

    @Test
    fun `syncConnections handles SnapTrade API failure gracefully`() {
        val user = createUser(1L)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { snapTradeService.listConnections(user) } throws RuntimeException("API unavailable")

        // Should not throw
        service.syncConnections(1L)

        verify(exactly = 0) { connectionRepository.save(any()) }
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
