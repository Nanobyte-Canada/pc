package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.broker.adapter.SnapTradeActivityDto
import com.portfolio.broker.adapter.SnapTradeBalanceDto
import com.portfolio.broker.entity.BrokerActivity
import com.portfolio.broker.entity.BrokerBalanceSnapshot
import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.BrokerActivityRepository
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals

class ActivityIngestionServiceTest {

    private lateinit var service: ActivityIngestionService
    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var activityRepository: BrokerActivityRepository
    private lateinit var balanceRepository: BrokerBalanceRepository
    private lateinit var snapTradeService: SnapTradeService
    private lateinit var userRepository: UserRepository
    private lateinit var exchangeRateService: ExchangeRateService
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
        exchangeRateService = mockk()

        // Default: return ONE for CAD, null for everything else (tests override as needed)
        every { exchangeRateService.getRate("CAD", any()) } returns BigDecimal.ONE
        every { exchangeRateService.getRate(neq("CAD"), any()) } returns null

        service = ActivityIngestionService(
            connectionRepository, activityRepository, balanceRepository,
            snapTradeService, userRepository, objectMapper, exchangeRateService
        )

        // JpaRepository.save() has generic signature <S extends T> S save(S).
        // MockK relaxed mocks can't resolve the generic and return Object(), causing ClassCastException.
        every { connectionRepository.save(any<BrokerConnection>()) } answers { firstArg() }
        every { balanceRepository.save(any<BrokerBalanceSnapshot>()) } answers { firstArg() }

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

        val activity = SnapTradeActivityDto(
            id = "act-1",
            type = "BUY",
            symbol = "AAPL",
            description = "Buy Apple Inc",
            units = 10.0,
            price = 150.25,
            amount = 1502.50,
            fee = 4.99,
            currency = "USD",
            tradeDate = LocalDate.of(2024, 6, 15),
            settlementDate = LocalDate.of(2024, 6, 17),
            optionType = null,
            rawJson = null
        )

        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
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
        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns emptyList()

        service.syncActivitiesForConnection(10L)

        verify {
            snapTradeService.getAllAccountActivities(
                user = mockUser,
                accountId = "ext-account-123",
                startDate = LocalDate.of(2024, 4, 30),  // minus 1 day for safety
                endDate = null,
                type = null
            )
        }
    }

    @Test
    fun `syncActivitiesForConnection does not duplicate existing activities`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activity = SnapTradeActivityDto(
            id = "existing-id",
            type = "SELL",
            symbol = null,
            description = null,
            units = null,
            price = null,
            amount = 500.0,
            fee = null,
            currency = "CAD",
            tradeDate = LocalDate.of(2024, 7, 1),
            settlementDate = null,
            optionType = null,
            rawJson = null
        )

        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "existing-id") } returns mockk()

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(0, count)
        verify(exactly = 0) { activityRepository.save(any()) }
    }

    @Test
    fun `syncActivitiesForConnection handles null optional fields`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activity = SnapTradeActivityDto(
            id = "act-null",
            type = "DIVIDEND",
            symbol = null,
            description = null,
            units = null,
            price = null,
            amount = 25.0,
            fee = null,
            currency = "CAD",
            tradeDate = LocalDate.of(2024, 8, 1),
            settlementDate = null,
            optionType = null,
            rawJson = null
        )

        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
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
        every { snapTradeService.getAllAccountActivities(any(), eq("acc-1"), any(), any(), any()) } throws RuntimeException("API down")

        every { activityRepository.findLatestTradeDateByConnectionId(2L) } returns null
        every { snapTradeService.getAllAccountActivities(any(), eq("acc-2"), any(), any(), any()) } returns emptyList()
        every { snapTradeService.getAccountBalance(any(), "acc-2") } returns emptyList()
        every { balanceRepository.findByConnectionIdAndAsOfDate(2L, any()) } returns null

        // Should not throw
        service.syncAllConnections()

        // conn2 balance sync should still execute
        verify { balanceRepository.save(any()) }
    }

    @Test
    fun `maps TRANSFERS to TRANSFER_IN`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activity = SnapTradeActivityDto(
            id = "transfer-1", type = "TRANSFERS", symbol = null, description = "Transfer in",
            units = null, price = null, amount = 1000.0, fee = null, currency = "CAD",
            tradeDate = LocalDate.of(2024, 6, 15), settlementDate = null, optionType = null, rawJson = null
        )

        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "transfer-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        service.syncActivitiesForConnection(10L)
        assertEquals("TRANSFER_IN", slot.captured.type)
    }

    @Test
    fun `maps WITHDRAWALS to TRANSFER_OUT`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activity = SnapTradeActivityDto(
            id = "wd-1", type = "WITHDRAWALS", symbol = null, description = "Withdrawal",
            units = null, price = null, amount = 500.0, fee = null, currency = "CAD",
            tradeDate = LocalDate.of(2024, 6, 15), settlementDate = null, optionType = null, rawJson = null
        )

        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "wd-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        service.syncActivitiesForConnection(10L)
        assertEquals("TRANSFER_OUT", slot.captured.type)
    }

    @Test
    fun `converts USD amount to CAD using exchange rate`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null
        every { exchangeRateService.getRate("USD", LocalDate.of(2024, 6, 15)) } returns BigDecimal("1.35")

        val activity = SnapTradeActivityDto(
            id = "usd-1", type = "TRANSFERS", symbol = null, description = "USD transfer",
            units = null, price = null, amount = 1000.0, fee = null, currency = "USD",
            tradeDate = LocalDate.of(2024, 6, 15), settlementDate = null, optionType = null, rawJson = null
        )

        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "usd-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        service.syncActivitiesForConnection(10L)

        val saved = slot.captured
        assertEquals("TRANSFER_IN", saved.type)
        assertEquals(0, BigDecimal("1350.00").compareTo(saved.amountCad))
        assertEquals(0, BigDecimal("1.35").compareTo(saved.exchangeRate))
    }

    @Test
    fun `keeps CAD amount unchanged with exchange rate of 1`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activity = SnapTradeActivityDto(
            id = "cad-1", type = "CONTRIBUTION", symbol = null, description = "CAD contribution",
            units = null, price = null, amount = 2000.0, fee = null, currency = "CAD",
            tradeDate = LocalDate.of(2024, 6, 15), settlementDate = null, optionType = null, rawJson = null
        )

        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "cad-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        service.syncActivitiesForConnection(10L)

        val saved = slot.captured
        assertEquals(0, BigDecimal("2000.0").compareTo(saved.amountCad))
        assertEquals(0, BigDecimal.ONE.compareTo(saved.exchangeRate))
    }

    @Test
    fun `handles zero amount without FX lookup`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activity = SnapTradeActivityDto(
            id = "zero-1", type = "TRANSFERS", symbol = null, description = "Zero transfer",
            units = null, price = null, amount = 0.0, fee = null, currency = "USD",
            tradeDate = LocalDate.of(2024, 6, 15), settlementDate = null, optionType = null, rawJson = null
        )

        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "zero-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        service.syncActivitiesForConnection(10L)

        val saved = slot.captured
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.amountCad))
        // No FX lookup for zero amount
        verify(exactly = 0) { exchangeRateService.getRate("USD", any()) }
    }

    @Test
    fun `falls back to raw amount when exchange rate unavailable`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null
        every { exchangeRateService.getRate("EUR", any()) } returns null

        val activity = SnapTradeActivityDto(
            id = "eur-1", type = "CONTRIBUTION", symbol = null, description = "EUR contribution",
            units = null, price = null, amount = 500.0, fee = null, currency = "EUR",
            tradeDate = LocalDate.of(2024, 6, 15), settlementDate = null, optionType = null, rawJson = null
        )

        every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
        every { activityRepository.findByConnectionIdAndExternalId(10L, "eur-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        service.syncActivitiesForConnection(10L)

        val saved = slot.captured
        // Falls back to raw amount
        assertEquals(0, BigDecimal("500.0").compareTo(saved.amountCad))
        assertEquals(null, saved.exchangeRate)
    }
}
