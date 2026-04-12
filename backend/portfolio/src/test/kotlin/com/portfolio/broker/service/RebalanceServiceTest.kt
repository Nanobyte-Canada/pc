package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RebalanceServiceTest {

    private lateinit var service: RebalanceService

    private lateinit var groupAccountRepository: PortfolioGroupAccountRepository
    private lateinit var settingsRepository: PortfolioGroupSettingsRepository
    private lateinit var positionRepository: BrokerPositionRepository
    private lateinit var driftCalculationService: DriftCalculationService

    @BeforeEach
    fun setup() {
        groupAccountRepository = mockk()
        settingsRepository = mockk()
        positionRepository = mockk()
        driftCalculationService = mockk()

        service = RebalanceService(
            groupAccountRepository = groupAccountRepository,
            settingsRepository = settingsRepository,
            positionRepository = positionRepository,
            driftCalculationService = driftCalculationService
        )
    }

    @Test
    fun `generates BUY trades for underweight positions when cash available`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)
        val settings = PortfolioGroupSettings(id = 1, group = group, sellToRebalance = false)

        val drift = DriftAnalysisResponse(
            groupId = 1L,
            groupName = "Test",
            accuracy = BigDecimal("80.00"),
            totalValue = BigDecimal(10000),
            cash = mapOf("CAD" to BigDecimal(2000)),
            holdings = listOf(
                DriftHoldingDto("VFV", "Vanguard S&P 500", BigDecimal(50), BigDecimal("30.0000"),
                    BigDecimal("-20.0000"), BigDecimal(3000), BigDecimal(5000), "CAD"),
                DriftHoldingDto("XIC", "iShares Core S&P/TSX", BigDecimal(50), BigDecimal("50.0000"),
                    BigDecimal("0.0000"), BigDecimal(5000), BigDecimal(5000), "CAD")
            ),
            excludedAssets = emptyList(),
            newAssets = emptyList()
        )

        every { driftCalculationService.calculateDrift(1L) } returns drift
        every { settingsRepository.findByGroupId(1L) } returns settings
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(3000), BigDecimal(100)),
            createPosition(connection, "XIC", BigDecimal(5000), BigDecimal(50))
        )

        val result = service.calculateRebalanceTrades(1L)

        assertTrue(result.trades.isNotEmpty())
        val buyTrade = result.trades.find { it.action == "BUY" && it.symbol == "VFV" }
        assertTrue(buyTrade != null)
        assertEquals("BUY", buyTrade.action)
        assertEquals("VFV", buyTrade.symbol)
        // No SELL trades since sellToRebalance is false
        assertTrue(result.trades.none { it.action == "SELL" })
    }

    @Test
    fun `no SELL trades when sellToRebalance is disabled`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)
        val settings = PortfolioGroupSettings(id = 1, group = group, sellToRebalance = false)

        val drift = DriftAnalysisResponse(
            groupId = 1L,
            groupName = "Test",
            accuracy = BigDecimal("80.00"),
            totalValue = BigDecimal(10000),
            cash = mapOf("CAD" to BigDecimal(0)),
            holdings = listOf(
                DriftHoldingDto("VFV", null, BigDecimal(50), BigDecimal("70.0000"),
                    BigDecimal("20.0000"), BigDecimal(7000), BigDecimal(5000), "CAD"),
                DriftHoldingDto("XIC", null, BigDecimal(50), BigDecimal("30.0000"),
                    BigDecimal("-20.0000"), BigDecimal(3000), BigDecimal(5000), "CAD")
            ),
            excludedAssets = emptyList(),
            newAssets = emptyList()
        )

        every { driftCalculationService.calculateDrift(1L) } returns drift
        every { settingsRepository.findByGroupId(1L) } returns settings
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(7000), BigDecimal(100)),
            createPosition(connection, "XIC", BigDecimal(3000), BigDecimal(50))
        )

        val result = service.calculateRebalanceTrades(1L)

        // No cash, no sell → no trades
        assertTrue(result.trades.isEmpty())
    }

    @Test
    fun `generates SELL and BUY trades when sellToRebalance is enabled`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)
        val settings = PortfolioGroupSettings(id = 1, group = group, sellToRebalance = true)

        val drift = DriftAnalysisResponse(
            groupId = 1L,
            groupName = "Test",
            accuracy = BigDecimal("80.00"),
            totalValue = BigDecimal(10000),
            cash = mapOf("CAD" to BigDecimal(0)),
            holdings = listOf(
                DriftHoldingDto("VFV", "Vanguard S&P 500", BigDecimal(50), BigDecimal("70.0000"),
                    BigDecimal("20.0000"), BigDecimal(7000), BigDecimal(5000), "CAD"),
                DriftHoldingDto("XIC", "iShares Core S&P/TSX", BigDecimal(50), BigDecimal("30.0000"),
                    BigDecimal("-20.0000"), BigDecimal(3000), BigDecimal(5000), "CAD")
            ),
            excludedAssets = emptyList(),
            newAssets = emptyList()
        )

        every { driftCalculationService.calculateDrift(1L) } returns drift
        every { settingsRepository.findByGroupId(1L) } returns settings
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(7000), BigDecimal(100)),
            createPosition(connection, "XIC", BigDecimal(3000), BigDecimal(50))
        )

        val result = service.calculateRebalanceTrades(1L)

        val sellTrades = result.trades.filter { it.action == "SELL" }
        val buyTrades = result.trades.filter { it.action == "BUY" }

        assertTrue(sellTrades.isNotEmpty())
        assertTrue(buyTrades.isNotEmpty())
        assertEquals("VFV", sellTrades[0].symbol)
        assertEquals("XIC", buyTrades[0].symbol)
    }

    @Test
    fun `limits buys to available cash`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)
        val settings = PortfolioGroupSettings(id = 1, group = group, sellToRebalance = false)

        val drift = DriftAnalysisResponse(
            groupId = 1L,
            groupName = "Test",
            accuracy = BigDecimal("80.00"),
            totalValue = BigDecimal(10000),
            cash = mapOf("CAD" to BigDecimal(500)),
            holdings = listOf(
                DriftHoldingDto("VFV", null, BigDecimal(50), BigDecimal("30.0000"),
                    BigDecimal("-20.0000"), BigDecimal(3000), BigDecimal(5000), "CAD")
            ),
            excludedAssets = emptyList(),
            newAssets = emptyList()
        )

        every { driftCalculationService.calculateDrift(1L) } returns drift
        every { settingsRepository.findByGroupId(1L) } returns settings
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(3000), BigDecimal(100))
        )

        val result = service.calculateRebalanceTrades(1L)

        // Should only buy $500 worth, not the full $2000 deficit
        val buyTrade = result.trades.find { it.action == "BUY" }!!
        assertTrue(buyTrade.amount <= BigDecimal(500))
    }

    @Test
    fun `skips trades below minimum threshold`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)
        val link = PortfolioGroupAccount(id = 1, group = group, connection = connection)
        val settings = PortfolioGroupSettings(id = 1, group = group, sellToRebalance = false)

        val drift = DriftAnalysisResponse(
            groupId = 1L,
            groupName = "Test",
            accuracy = BigDecimal("99.00"),
            totalValue = BigDecimal(10000),
            cash = mapOf("CAD" to BigDecimal(5)),
            holdings = listOf(
                DriftHoldingDto("VFV", null, BigDecimal(50), BigDecimal("49.9500"),
                    BigDecimal("-0.0500"), BigDecimal(4995), BigDecimal(5000), "CAD")
            ),
            excludedAssets = emptyList(),
            newAssets = emptyList()
        )

        every { driftCalculationService.calculateDrift(1L) } returns drift
        every { settingsRepository.findByGroupId(1L) } returns settings
        every { groupAccountRepository.findByGroupId(1L) } returns listOf(link)
        every { positionRepository.findCurrentPositionsByConnectionId(10L) } returns listOf(
            createPosition(connection, "VFV", BigDecimal(4995), BigDecimal(100))
        )

        val result = service.calculateRebalanceTrades(1L)

        // $5 cash and $5 deficit → below $10 threshold
        assertTrue(result.trades.isEmpty())
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
        price: BigDecimal
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
