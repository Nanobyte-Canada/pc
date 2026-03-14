package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PortfolioGroupServiceTest {

    private lateinit var service: PortfolioGroupService

    private lateinit var groupRepository: PortfolioGroupRepository
    private lateinit var targetRepository: PortfolioTargetRepository
    private lateinit var groupAccountRepository: PortfolioGroupAccountRepository
    private lateinit var settingsRepository: PortfolioGroupSettingsRepository
    private lateinit var excludedAssetRepository: PortfolioExcludedAssetRepository
    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var driftCalculationService: DriftCalculationService

    @BeforeEach
    fun setup() {
        groupRepository = mockk()
        targetRepository = mockk()
        groupAccountRepository = mockk()
        settingsRepository = mockk()
        excludedAssetRepository = mockk()
        connectionRepository = mockk()
        driftCalculationService = mockk()

        service = PortfolioGroupService(
            groupRepository = groupRepository,
            targetRepository = targetRepository,
            groupAccountRepository = groupAccountRepository,
            settingsRepository = settingsRepository,
            excludedAssetRepository = excludedAssetRepository,
            connectionRepository = connectionRepository,
            driftCalculationService = driftCalculationService
        )
    }

    // ========== Create Group Tests ==========

    @Test
    fun `createGroup creates group with default settings`() {
        val user = createUser(1L)
        val request = CreatePortfolioGroupRequest(name = "My Portfolio")

        every { groupRepository.existsByUserIdAndName(1L, "My Portfolio") } returns false
        every { groupRepository.save(any()) } answers { firstArg() }
        every { settingsRepository.save(any()) } answers { firstArg() }
        every { driftCalculationService.calculateAccuracy(any()) } returns BigDecimal.ZERO
        every { driftCalculationService.calculateTotalValue(any()) } returns BigDecimal.ZERO

        val result = service.createGroup(1L, request)

        assertEquals("My Portfolio", result.name)
        assertNotNull(result.settings)
        assertEquals(false, result.settings.sellToRebalance)
        verify { settingsRepository.save(any()) }
    }

    @Test
    fun `createGroup with targets saves targets`() {
        val targets = listOf(
            TargetInput("VFV", BigDecimal(50)),
            TargetInput("XIC", BigDecimal(30))
        )
        val request = CreatePortfolioGroupRequest(name = "Test", targets = targets)

        every { groupRepository.existsByUserIdAndName(1L, "Test") } returns false
        every { groupRepository.save(any()) } answers { firstArg() }
        every { settingsRepository.save(any()) } answers { firstArg() }
        every { targetRepository.save(any()) } answers { firstArg() }
        every { driftCalculationService.calculateAccuracy(any()) } returns BigDecimal.ZERO
        every { driftCalculationService.calculateTotalValue(any()) } returns BigDecimal.ZERO

        val result = service.createGroup(1L, request)

        verify(exactly = 2) { targetRepository.save(any()) }
    }

    @Test
    fun `createGroup rejects duplicate name`() {
        every { groupRepository.existsByUserIdAndName(1L, "Existing") } returns true

        assertThrows<IllegalArgumentException> {
            service.createGroup(1L, CreatePortfolioGroupRequest(name = "Existing"))
        }
    }

    @Test
    fun `createGroup rejects targets exceeding 100 percent`() {
        val targets = listOf(
            TargetInput("VFV", BigDecimal(60)),
            TargetInput("XIC", BigDecimal(50))
        )
        val request = CreatePortfolioGroupRequest(name = "Test", targets = targets)

        every { groupRepository.existsByUserIdAndName(1L, "Test") } returns false
        every { groupRepository.save(any()) } answers { firstArg() }
        every { settingsRepository.save(any()) } answers { firstArg() }

        assertThrows<IllegalArgumentException> {
            service.createGroup(1L, request)
        }
    }

    // ========== Set Targets Tests ==========

    @Test
    fun `setTargets replaces all targets`() {
        val group = createGroup(1L, 1L)
        val request = SetTargetsRequest(
            targets = listOf(
                TargetInput("VFV", BigDecimal(40)),
                TargetInput("XIC", BigDecimal(30)),
                TargetInput("ZAG", BigDecimal(30))
            )
        )

        every { groupRepository.findByIdAndUserId(1L, 1L) } returns group
        every { targetRepository.deleteByGroupId(1L) } just Runs
        every { targetRepository.save(any()) } answers { firstArg() }

        val result = service.setTargets(1L, 1L, request)

        assertEquals(3, result.size)
        verify { targetRepository.deleteByGroupId(1L) }
    }

    @Test
    fun `setTargets rejects total exceeding 100 percent`() {
        val group = createGroup(1L, 1L)
        every { groupRepository.findByIdAndUserId(1L, 1L) } returns group

        assertThrows<IllegalArgumentException> {
            service.setTargets(1L, 1L, SetTargetsRequest(
                targets = listOf(
                    TargetInput("VFV", BigDecimal(60)),
                    TargetInput("XIC", BigDecimal(60))
                )
            ))
        }
    }

    // ========== Account Linking Tests ==========

    @Test
    fun `linkAccount links active connection`() {
        val user = createUser(1L)
        val group = createGroup(1L, 1L)
        val connection = createConnection(10L, user, ConnectionStatus.ACTIVE)

        every { groupRepository.findByIdAndUserId(1L, 1L) } returns group
        every { connectionRepository.findByIdAndUserId(10L, 1L) } returns connection
        every { groupAccountRepository.existsByGroupIdAndConnectionId(1L, 10L) } returns false
        every { groupAccountRepository.save(any()) } answers { firstArg() }

        val result = service.linkAccount(1L, 1L, 10L)

        assertEquals(10L, result.connectionId)
        assertEquals("ACTIVE", result.status)
    }

    @Test
    fun `linkAccount rejects inactive connection`() {
        val user = createUser(1L)
        val group = createGroup(1L, 1L)
        val connection = createConnection(10L, user, ConnectionStatus.EXPIRED)

        every { groupRepository.findByIdAndUserId(1L, 1L) } returns group
        every { connectionRepository.findByIdAndUserId(10L, 1L) } returns connection

        assertThrows<IllegalArgumentException> {
            service.linkAccount(1L, 1L, 10L)
        }
    }

    @Test
    fun `linkAccount rejects already linked account`() {
        val user = createUser(1L)
        val group = createGroup(1L, 1L)
        val connection = createConnection(10L, user, ConnectionStatus.ACTIVE)

        every { groupRepository.findByIdAndUserId(1L, 1L) } returns group
        every { connectionRepository.findByIdAndUserId(10L, 1L) } returns connection
        every { groupAccountRepository.existsByGroupIdAndConnectionId(1L, 10L) } returns true

        assertThrows<IllegalArgumentException> {
            service.linkAccount(1L, 1L, 10L)
        }
    }

    // ========== Delete Group Tests ==========

    @Test
    fun `deleteGroup cascades delete`() {
        val group = createGroup(1L, 1L)
        every { groupRepository.findByIdAndUserId(1L, 1L) } returns group
        every { groupRepository.delete(group) } just Runs

        service.deleteGroup(1L, 1L)

        verify { groupRepository.delete(group) }
    }

    @Test
    fun `deleteGroup throws for non-existent group`() {
        every { groupRepository.findByIdAndUserId(99L, 1L) } returns null

        assertThrows<IllegalArgumentException> {
            service.deleteGroup(99L, 1L)
        }
    }

    // ========== Settings Tests ==========

    @Test
    fun `updateSettings partial update`() {
        val group = createGroup(1L, 1L)
        val settings = PortfolioGroupSettings(id = 1, group = group)

        every { groupRepository.findByIdAndUserId(1L, 1L) } returns group
        every { settingsRepository.findByGroupId(1L) } returns settings
        every { settingsRepository.save(any()) } answers { firstArg() }

        val result = service.updateSettings(1L, 1L, UpdateSettingsRequest(sellToRebalance = true))

        assertEquals(true, result.sellToRebalance)
        assertEquals(false, result.keepCurrenciesSeparate) // unchanged
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

    private fun createGroup(groupId: Long, userId: Long): PortfolioGroup {
        val user = createUser(userId)
        return PortfolioGroup(
            id = groupId,
            user = user,
            name = "Test Group"
        )
    }

    private fun createConnection(id: Long, user: User, status: ConnectionStatus): BrokerConnection {
        return BrokerConnection(
            id = id,
            user = user,
            accountNumber = "ACC-$id",
            accountType = "TFSA",
            accountName = "Test Account $id",
            status = status
        )
    }
}
