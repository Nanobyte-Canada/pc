package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.BrokerConnectionRepository
import com.portfolio.broker.repository.TradeOrderRepository
import com.portfolio.broker.adapter.SnapTradeOrderDto
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OrderExecutionServiceTest {

    private lateinit var service: OrderExecutionService
    private lateinit var tradeOrderRepository: TradeOrderRepository
    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var portfolioGroupService: PortfolioGroupService
    private lateinit var snapTradeService: SnapTradeService

    @BeforeEach
    fun setup() {
        tradeOrderRepository = mockk()
        connectionRepository = mockk()
        portfolioGroupService = mockk()
        snapTradeService = mockk()

        service = OrderExecutionService(
            tradeOrderRepository = tradeOrderRepository,
            connectionRepository = connectionRepository,
            portfolioGroupService = portfolioGroupService,
            snapTradeService = snapTradeService
        )
    }

    @Test
    fun `creates orders for all trades in batch`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)

        every { portfolioGroupService.getGroupEntity(1L, 1L) } returns group
        every { connectionRepository.findByIdAndUserId(10L, 1L) } returns connection
        every { tradeOrderRepository.save(any()) } answers { firstArg() }

        val brokerResponse = SnapTradeOrderDto(
            brokerageOrderId = "BROKER-123", status = "SUBMITTED",
            symbol = "VFV", action = "BUY", units = 10.0, price = null
        )
        every { snapTradeService.placeOrder(any(), any(), any(), any(), any(), any(), any(), any()) } returns brokerResponse

        val request = ExecuteTradesRequest(
            groupId = 1L,
            trades = listOf(
                TradeExecutionInput("VFV", "BUY", BigDecimal(10), BigDecimal(100), BigDecimal(1000), "CAD", 10L),
                TradeExecutionInput("XIC", "BUY", BigDecimal(20), BigDecimal(30), BigDecimal(600), "CAD", 10L)
            )
        )

        val response = service.executeTradesForGroup(user, request)

        assertEquals(2, response.orders.size)
        assertEquals(2, response.submittedCount)
        assertEquals(0, response.failedCount)
        assertNotNull(response.batchId)
        verify(exactly = 2) { tradeOrderRepository.save(any()) }
    }

    @Test
    fun `validates user owns connection`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)

        every { portfolioGroupService.getGroupEntity(1L, 1L) } returns group
        every { connectionRepository.findByIdAndUserId(99L, 1L) } returns null

        val request = ExecuteTradesRequest(
            groupId = 1L,
            trades = listOf(
                TradeExecutionInput("VFV", "BUY", BigDecimal(10), BigDecimal(100), BigDecimal(1000), "CAD", 99L)
            )
        )

        assertFailsWith<IllegalArgumentException>("Connection not found") {
            service.executeTradesForGroup(user, request)
        }
    }

    @Test
    fun `rejects inactive connection`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user).apply {
            status = ConnectionStatus.EXPIRED
        }

        every { portfolioGroupService.getGroupEntity(1L, 1L) } returns group
        every { connectionRepository.findByIdAndUserId(10L, 1L) } returns connection

        val request = ExecuteTradesRequest(
            groupId = 1L,
            trades = listOf(
                TradeExecutionInput("VFV", "BUY", BigDecimal(10), BigDecimal(100), BigDecimal(1000), "CAD", 10L)
            )
        )

        assertFailsWith<IllegalArgumentException>("not active") {
            service.executeTradesForGroup(user, request)
        }
    }

    @Test
    fun `cancel updates status to CANCELLED`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)

        val order = TradeOrder(
            id = 1L,
            user = user,
            group = group,
            connection = connection,
            symbol = "VFV",
            action = OrderAction.BUY,
            requestedUnits = BigDecimal(10),
            requestedPrice = BigDecimal(100),
            requestedAmount = BigDecimal(1000),
            status = OrderStatus.SUBMITTED,
            brokerOrderId = "BROKER-123",
            accountIdExternal = "ext-123"
        )

        every { tradeOrderRepository.findByIdAndUserId(1L, 1L) } returns order
        every { tradeOrderRepository.save(any()) } answers { firstArg() }
        every { snapTradeService.cancelOrder(any(), any(), any()) } just runs

        val result = service.cancelOrder(user, 1L)

        assertEquals("CANCELLED", result.status)
        assertNotNull(result.cancelledAt)
    }

    @Test
    fun `cannot cancel filled order`() {
        val user = createUser(1L)
        val group = createGroup(1L, user)
        val connection = createConnection(10L, user)

        val order = TradeOrder(
            id = 1L,
            user = user,
            group = group,
            connection = connection,
            symbol = "VFV",
            action = OrderAction.BUY,
            requestedUnits = BigDecimal(10),
            requestedPrice = BigDecimal(100),
            requestedAmount = BigDecimal(1000),
            status = OrderStatus.FILLED
        )

        every { tradeOrderRepository.findByIdAndUserId(1L, 1L) } returns order

        assertFailsWith<IllegalArgumentException>("Cannot cancel") {
            service.cancelOrder(user, 1L)
        }
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
            accountIdExternal = "ext-$id",
            status = ConnectionStatus.ACTIVE
        )
    }
}
