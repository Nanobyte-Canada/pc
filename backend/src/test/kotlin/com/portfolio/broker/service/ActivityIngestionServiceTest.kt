package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.broker.entity.BrokerActivity
import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.BrokerActivityRepository
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import com.snaptrade.client.model.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals

class ActivityIngestionServiceTest {

    private lateinit var service: ActivityIngestionService
    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var activityRepository: BrokerActivityRepository
    private lateinit var balanceRepository: BrokerBalanceRepository
    private lateinit var snapTradeService: SnapTradeService
    private lateinit var userRepository: UserRepository
    private val objectMapper = ObjectMapper()

    private lateinit var mockUser: User
    private lateinit var mockConnection: BrokerConnection

    @BeforeEach
    fun setup() {
        connectionRepository = mockk(relaxed = true)
        activityRepository = mockk(relaxed = true)
        balanceRepository = mockk(relaxed = true)
        snapTradeService = mockk()
        userRepository = mockk()

        service = ActivityIngestionService(
            connectionRepository, activityRepository, balanceRepository,
            snapTradeService, userRepository, objectMapper
        )

        mockUser = mockk {
            every { id } returns 1L
        }
        mockConnection = mockk(relaxed = true) {
            every { id } returns 10L
            every { user } returns mockUser
            every { accountIdExternal } returns "ext-account-123"
            every { accountName } returns "My RRSP"
        }
    }

    @Test
    fun `syncActivitiesForConnection maps fields correctly`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activity = mockk<UniversalActivity>(relaxed = true) {
            every { id } returns "act-1"
            every { type } returns "BUY"
            every { symbol } returns mockk {
                every { symbol } returns "AAPL"
            }
            every { description } returns "Buy Apple Inc"
            every { units } returns 10.0
            every { price } returns 150.25
            every { amount } returns 1502.50
            every { fee } returns 4.99
            every { currency } returns mockk { every { code } returns "USD" }
            every { tradeDate } returns OffsetDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneOffset.UTC)
            every { settlementDate } returns OffsetDateTime.of(2024, 6, 17, 0, 0, 0, 0, ZoneOffset.UTC)
            every { optionType } returns null
        }

        every { snapTradeService.getActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "act-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(1, count)
        val saved = slot.captured
        assertEquals("BUY", saved.type)
        assertEquals("AAPL", saved.symbol)
        assertEquals("Buy Apple Inc", saved.description)
        assertEquals(BigDecimal("10.0"), saved.quantity)
        assertEquals(BigDecimal("150.25"), saved.price)
        assertEquals(BigDecimal("1502.5"), saved.amount)
        assertEquals(BigDecimal("4.99"), saved.fee)
        assertEquals("USD", saved.currency)
        assertEquals(LocalDate.of(2024, 6, 15), saved.tradeDate)
        assertEquals(LocalDate.of(2024, 6, 17), saved.settlementDate)
    }

    @Test
    fun `syncActivitiesForConnection incremental sync fetches from last known date`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.of(2024, 5, 1)
        every { snapTradeService.getActivities(any(), any(), any(), any(), any()) } returns emptyList()

        service.syncActivitiesForConnection(10L)

        verify {
            snapTradeService.getActivities(
                user = mockUser,
                startDate = LocalDate.of(2024, 4, 30),  // minus 1 day for safety
                endDate = null,
                accounts = "ext-account-123",
                type = null
            )
        }
    }

    @Test
    fun `syncActivitiesForConnection does not duplicate existing activities`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activity = mockk<UniversalActivity>(relaxed = true) {
            every { id } returns "existing-id"
            every { type } returns "SELL"
            every { amount } returns 500.0
            every { tradeDate } returns OffsetDateTime.of(2024, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            every { currency } returns mockk { every { code } returns "CAD" }
            every { symbol } returns null
        }

        every { snapTradeService.getActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "existing-id") } returns mockk()

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(0, count)
        verify(exactly = 0) { activityRepository.save(any()) }
    }

    @Test
    fun `syncActivitiesForConnection handles null optional fields`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activity = mockk<UniversalActivity>(relaxed = true) {
            every { id } returns "act-null"
            every { type } returns "DIVIDEND"
            every { symbol } returns null
            every { description } returns null
            every { units } returns null
            every { price } returns null
            every { amount } returns 25.0
            every { fee } returns null
            every { currency } returns mockk { every { code } returns "CAD" }
            every { tradeDate } returns OffsetDateTime.of(2024, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            every { settlementDate } returns null
            every { optionType } returns null
        }

        every { snapTradeService.getActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "act-null") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(1, count)
        val saved = slot.captured
        assertEquals("DIVIDEND", saved.type)
        assertEquals(null, saved.symbol)
        assertEquals(null, saved.quantity)
        assertEquals(null, saved.price)
        assertEquals(null, saved.fee)
        assertEquals(null, saved.settlementDate)
    }

    @Test
    fun `syncAllConnections continues on individual connection error`() {
        val conn1 = mockk<BrokerConnection>(relaxed = true) {
            every { id } returns 1L
            every { status } returns ConnectionStatus.ACTIVE
            every { user } returns mockUser
            every { accountIdExternal } returns "acc-1"
        }
        val conn2 = mockk<BrokerConnection>(relaxed = true) {
            every { id } returns 2L
            every { status } returns ConnectionStatus.ACTIVE
            every { user } returns mockUser
            every { accountIdExternal } returns "acc-2"
        }

        every { connectionRepository.findAll() } returns listOf(conn1, conn2)
        every { connectionRepository.findById(1L) } returns Optional.of(conn1)
        every { connectionRepository.findById(2L) } returns Optional.of(conn2)

        // conn1 throws, conn2 succeeds
        every { activityRepository.findLatestTradeDateByConnectionId(1L) } returns null
        every { snapTradeService.getActivities(any(), any(), any(), eq("acc-1"), any()) } throws RuntimeException("API down")

        every { activityRepository.findLatestTradeDateByConnectionId(2L) } returns null
        every { snapTradeService.getActivities(any(), any(), any(), eq("acc-2"), any()) } returns emptyList()
        every { snapTradeService.getAccountBalance(any(), "acc-2") } returns emptyList()
        every { balanceRepository.findByConnectionIdAndAsOfDate(2L, any()) } returns null

        // Should not throw
        service.syncAllConnections()

        // conn2 balance sync should still execute
        verify { balanceRepository.save(any()) }
    }
}
