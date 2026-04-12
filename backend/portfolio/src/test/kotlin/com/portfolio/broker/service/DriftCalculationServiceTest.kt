package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriftCalculationServiceTest {

    private lateinit var service: DriftCalculationService

    private lateinit var groupRepository: PortfolioGroupRepository
    private lateinit var groupAccountRepository: PortfolioGroupAccountRepository
    private lateinit var targetRepository: PortfolioTargetRepository
    private lateinit var excludedAssetRepository: PortfolioExcludedAssetRepository
    private lateinit var positionRepository: BrokerPositionRepository
    private lateinit var balanceRepository: BrokerBalanceRepository
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        groupRepository = mockk()
        groupAccountRepository = mockk()
        targetRepository = mockk()
        excludedAssetRepository = mockk()
        positionRepository = mockk()
        balanceRepository = mockk()

        service = DriftCalculationService(
            groupRepository = groupRepository,
            groupAccountRepository = groupAccountRepository,
            targetRepository = targetRepository,
            excludedAssetRepository = excludedAssetRepository,
            positionRepository = positionRepository,
            balanceRepository = balanceRepository,
            objectMapper = objectMapper
        )
    }

    @Test
    fun `calculateDrift returns 100 percent accuracy when perfectly balanced`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)

        every { groupRepository.findById(1L) } returns Optional.of(group)
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { targetRepository.findByGroupId(1L) } returns listOf(
            PortfolioTarget(id = 1, group = group, symbol = "VFV", targetPercent = BigDecimal(50)),
            PortfolioTarget(id = 2, group = group, symbol = "XIC", targetPercent = BigDecimal(50))
        )
        every { excludedAssetRepository.findByGroupId(1L) } returns emptyList()
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(5000)),
            createPosition(connection, "XIC", BigDecimal(5000))
        )
        every { balanceRepository.findLatestByConnectionId(10L) } returns null

        val result = service.calculateDrift(1L)

        assertEquals(BigDecimal("100.00"), result.accuracy)
        assertEquals(BigDecimal(10000), result.totalValue)
        assertEquals(2, result.holdings.size)
        assertTrue(result.newAssets.isEmpty())
    }

    @Test
    fun `calculateDrift shows correct drift for underweight position`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)

        every { groupRepository.findById(1L) } returns Optional.of(group)
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { targetRepository.findByGroupId(1L) } returns listOf(
            PortfolioTarget(id = 1, group = group, symbol = "VFV", targetPercent = BigDecimal(50)),
            PortfolioTarget(id = 2, group = group, symbol = "XIC", targetPercent = BigDecimal(50))
        )
        every { excludedAssetRepository.findByGroupId(1L) } returns emptyList()
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(7000)),
            createPosition(connection, "XIC", BigDecimal(3000))
        )
        every { balanceRepository.findLatestByConnectionId(10L) } returns null

        val result = service.calculateDrift(1L)

        val vfv = result.holdings.find { it.symbol == "VFV" }!!
        val xic = result.holdings.find { it.symbol == "XIC" }!!

        // VFV is 70% actual, 50% target → drift = +20
        assertEquals(BigDecimal("70.0000"), vfv.actualPercent)
        assertEquals(BigDecimal("20.0000"), vfv.driftPercent)

        // XIC is 30% actual, 50% target → drift = -20
        assertEquals(BigDecimal("30.0000"), xic.actualPercent)
        assertEquals(BigDecimal("-20.0000"), xic.driftPercent)

        // Accuracy = 100 - mean(|20|, |20|) = 100 - 20 = 80
        assertEquals(BigDecimal("80.00"), result.accuracy)
    }

    @Test
    fun `calculateDrift excludes excluded assets`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)

        every { groupRepository.findById(1L) } returns Optional.of(group)
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { targetRepository.findByGroupId(1L) } returns listOf(
            PortfolioTarget(id = 1, group = group, symbol = "VFV", targetPercent = BigDecimal(100))
        )
        every { excludedAssetRepository.findByGroupId(1L) } returns listOf(
            PortfolioExcludedAsset(id = 1, group = group, symbol = "CASH")
        )
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(8000)),
            createPosition(connection, "CASH", BigDecimal(2000))
        )
        every { balanceRepository.findLatestByConnectionId(10L) } returns null

        val result = service.calculateDrift(1L)

        // CASH is excluded, so only VFV counts. VFV is 100% of non-excluded positions
        assertEquals(1, result.excludedAssets.size)
        assertEquals("CASH", result.excludedAssets[0].symbol)
        assertEquals(BigDecimal(2000), result.excludedAssets[0].currentValue)
    }

    @Test
    fun `calculateDrift detects new assets`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)

        every { groupRepository.findById(1L) } returns Optional.of(group)
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { targetRepository.findByGroupId(1L) } returns listOf(
            PortfolioTarget(id = 1, group = group, symbol = "VFV", targetPercent = BigDecimal(100))
        )
        every { excludedAssetRepository.findByGroupId(1L) } returns emptyList()
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(8000)),
            createPosition(connection, "UNKNOWN_STOCK", BigDecimal(2000))
        )
        every { balanceRepository.findLatestByConnectionId(10L) } returns null

        val result = service.calculateDrift(1L)

        assertEquals(1, result.newAssets.size)
        assertEquals("UNKNOWN_STOCK", result.newAssets[0].symbol)
    }

    @Test
    fun `calculateDrift handles empty portfolio`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)

        every { groupRepository.findById(1L) } returns Optional.of(group)
        every { groupAccountRepository.findByGroupId(1L) } returns emptyList()
        every { targetRepository.findByGroupId(1L) } returns listOf(
            PortfolioTarget(id = 1, group = group, symbol = "VFV", targetPercent = BigDecimal(50))
        )
        every { excludedAssetRepository.findByGroupId(1L) } returns emptyList()

        val result = service.calculateDrift(1L)

        assertEquals(BigDecimal.ZERO, result.totalValue)
        assertEquals(1, result.holdings.size)
        assertEquals(BigDecimal.ZERO, result.holdings[0].actualPercent)
    }

    @Test
    fun `calculateDrift includes cash in total value`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)

        every { groupRepository.findById(1L) } returns Optional.of(group)
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { targetRepository.findByGroupId(1L) } returns listOf(
            PortfolioTarget(id = 1, group = group, symbol = "VFV", targetPercent = BigDecimal(100))
        )
        every { excludedAssetRepository.findByGroupId(1L) } returns emptyList()
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(9000))
        )
        val balanceSnapshot = BrokerBalanceSnapshot(
            id = 1,
            connection = connection,
            totalValue = BigDecimal(10000),
            cash = """{"CAD": 1000}""",
            asOfDate = LocalDate.now()
        )
        every { balanceRepository.findLatestByConnectionId(10L) } returns balanceSnapshot

        val result = service.calculateDrift(1L)

        assertEquals(BigDecimal(10000), result.totalValue)
        assertEquals(BigDecimal(1000), result.cash["CAD"])
    }

    // ========== Helper Methods ==========

    private fun createUser(id: Long): User {
        return User(id = id, email = "user$id@example.com", passwordHash = "hash", name = "Test User")
    }

    private fun createGroup(id: Long, user: User): PortfolioGroup {
        return PortfolioGroup(id = id, user = user, name = "Test Group")
    }

    private fun createConnection(id: Long, user: User): BrokerConnection {
        return BrokerConnection(
            id = id,
            user = user,
            accountNumber = "ACC-$id",
            accountName = "Test Account",
            status = ConnectionStatus.ACTIVE
        )
    }

    private fun createPosition(
        connection: BrokerConnection,
        symbol: String,
        value: BigDecimal,
        price: BigDecimal = BigDecimal(100)
    ): BrokerPosition {
        val quantity = value.divide(price, 6, RoundingMode.HALF_UP)
        return BrokerPosition(
            connection = connection,
            symbol = symbol,
            securityName = "$symbol Security",
            quantity = quantity,
            currentPrice = price,
            currentValue = value,
            currency = "CAD",
            asOfDate = LocalDate.now()
        )
    }
}
