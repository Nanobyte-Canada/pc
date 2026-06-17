package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.broker.client.BrokerGatewayClient
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
    private lateinit var gatewayClient: BrokerGatewayClient
    private lateinit var exchangeRateService: ExchangeRateService
    private val objectMapper = ObjectMapper()

    private lateinit var mockUser: User
    private lateinit var mockConnection: BrokerConnection

    @BeforeEach
    fun setup() {
        connectionRepository = mockk(relaxed = true)
        activityRepository = mockk(relaxed = true)
        balanceRepository = mockk(relaxed = true)
        gatewayClient = mockk()
        exchangeRateService = mockk()

        // Default: return ONE for CAD, null for everything else (tests override as needed)
        every { exchangeRateService.getRate("CAD", any()) } returns BigDecimal.ONE
        every { exchangeRateService.getRate(neq("CAD"), any()) } returns null

        service = ActivityIngestionService(
            connectionRepository, activityRepository, balanceRepository,
            gatewayClient, objectMapper, exchangeRateService,
            maxLookbackYears = 30,
            chunkDays = 29
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
            every { gatewayConnectionId } returns "gw-conn-123"
            every { accountIdExternal } returns "ext-account-123"
            every { accountName } returns "My RRSP"
        }
    }

    private fun buildActivitiesJson(vararg activities: Map<String, Any?>): com.fasterxml.jackson.databind.JsonNode {
        return objectMapper.valueToTree(mapOf("activities" to activities.toList()))
    }

    @Test
    fun `syncActivitiesForConnection maps fields correctly`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        // Use a recent date so the service takes the incremental (non-chunked) path
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.now().minusDays(5)

        val activitiesJson = buildActivitiesJson(
            mapOf(
                "externalId" to "act-1",
                "type" to "BUY",
                "symbol" to "AAPL",
                "description" to "Buy Apple Inc",
                "quantity" to 10.0,
                "price" to 150.25,
                "amount" to 1502.50,
                "fee" to 4.99,
                "currency" to "USD",
                "tradeDate" to "2024-06-15",
                "settlementDate" to "2024-06-17",
                "optionType" to null
            )
        )

        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns activitiesJson
        every { activityRepository.findByConnectionIdAndExternalId(10L, "act-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(1, count)
        val saved = slot.captured
        assertEquals("BUY", saved.type)
        assertEquals("AAPL", saved.symbol)
        assertEquals("Buy Apple Inc", saved.description)
        assertEquals(0, BigDecimal("10.0").compareTo(saved.quantity))
        assertEquals(0, BigDecimal("150.25").compareTo(saved.price))
        assertEquals(0, BigDecimal("1502.5").compareTo(saved.amount))
        assertEquals(0, BigDecimal("4.99").compareTo(saved.fee))
        assertEquals("USD", saved.currency)
        assertEquals(LocalDate.of(2024, 6, 15), saved.tradeDate)
        assertEquals(LocalDate.of(2024, 6, 17), saved.settlementDate)
    }

    @Test
    fun `syncActivitiesForConnection incremental sync fetches from last known date`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.of(2024, 5, 1)
        every { gatewayClient.getActivities(any(), any(), any(), any()) } returns buildActivitiesJson()

        service.syncActivitiesForConnection(10L)

        verify {
            gatewayClient.getActivities(
                connectionId = "gw-conn-123",
                accountId = "ext-account-123",
                startDate = LocalDate.of(2024, 4, 30),  // minus 1 day for safety
                endDate = null
            )
        }
    }

    @Test
    fun `syncActivitiesForConnection does not duplicate existing activities`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activitiesJson = buildActivitiesJson(
            mapOf(
                "externalId" to "existing-id",
                "type" to "SELL",
                "amount" to 500.0,
                "currency" to "CAD",
                "tradeDate" to "2024-07-01"
            )
        )

        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns activitiesJson
        every { activityRepository.findByConnectionIdAndExternalId(10L, "existing-id") } returns mockk()

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(0, count)
        verify(exactly = 0) { activityRepository.save(any()) }
    }

    @Test
    fun `syncActivitiesForConnection handles null optional fields`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        // Use a recent date so the service takes the incremental (non-chunked) path
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.now().minusDays(5)

        val activitiesJson = buildActivitiesJson(
            mapOf(
                "externalId" to "act-null",
                "type" to "DIVIDEND",
                "amount" to 25.0,
                "currency" to "CAD",
                "tradeDate" to "2024-08-01"
            )
        )

        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns activitiesJson
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
            every { gatewayConnectionId } returns "gw-1"
            every { accountIdExternal } returns "acc-1"
        }
        val conn2 = mockk<BrokerConnection>(relaxed = true) {
            every { id } returns 2L
            every { status } returns ConnectionStatus.ACTIVE
            every { user } returns mockUser
            every { gatewayConnectionId } returns "gw-2"
            every { accountIdExternal } returns "acc-2"
        }

        every { connectionRepository.findAll() } returns listOf(conn1, conn2)
        every { connectionRepository.findById(1L) } returns Optional.of(conn1)
        every { connectionRepository.findById(2L) } returns Optional.of(conn2)

        // conn1 throws, conn2 succeeds
        every { activityRepository.findLatestTradeDateByConnectionId(1L) } returns null
        every { gatewayClient.getActivities("gw-1", "acc-1", any(), any()) } throws RuntimeException("API down")

        every { activityRepository.findLatestTradeDateByConnectionId(2L) } returns null
        every { gatewayClient.getActivities("gw-2", "acc-2", any(), any()) } returns buildActivitiesJson()

        val balanceJson = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
            mapOf(
                "accountId" to "acc-2",
                "totalValue" to 10000.0,
                "cashBalances" to emptyList<Any>(),
                "buyingPower" to 0,
                "currency" to "CAD"
            )
        )
        every { gatewayClient.getBalances("gw-2", "acc-2") } returns balanceJson
        every { balanceRepository.findByConnectionIdAndAsOfDate(2L, any()) } returns null

        // Should not throw
        service.syncAllConnections()

        // conn2 balance sync should still execute
        verify { balanceRepository.save(any()) }
    }

    @Test
    fun `gateway returns normalized types directly`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val activitiesJson = buildActivitiesJson(
            mapOf(
                "externalId" to "transfer-1",
                "type" to "TRANSFER_IN",
                "amount" to 1000.0,
                "currency" to "CAD",
                "tradeDate" to "2024-06-15"
            )
        )

        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns activitiesJson
        every { activityRepository.findByConnectionIdAndExternalId(10L, "transfer-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        service.syncActivitiesForConnection(10L)
        assertEquals("TRANSFER_IN", slot.captured.type)
    }

    @Test
    fun `converts USD amount to CAD using exchange rate`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null
        every { exchangeRateService.getRate("USD", LocalDate.of(2024, 6, 15)) } returns BigDecimal("1.35")

        val activitiesJson = buildActivitiesJson(
            mapOf(
                "externalId" to "usd-1",
                "type" to "TRANSFER_IN",
                "amount" to 1000.0,
                "currency" to "USD",
                "tradeDate" to "2024-06-15"
            )
        )

        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns activitiesJson
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

        val activitiesJson = buildActivitiesJson(
            mapOf(
                "externalId" to "cad-1",
                "type" to "TRANSFER_IN",
                "amount" to 2000.0,
                "currency" to "CAD",
                "tradeDate" to "2024-06-15"
            )
        )

        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns activitiesJson
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

        val activitiesJson = buildActivitiesJson(
            mapOf(
                "externalId" to "zero-1",
                "type" to "TRANSFER_IN",
                "amount" to 0.0,
                "currency" to "USD",
                "tradeDate" to "2024-06-15"
            )
        )

        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns activitiesJson
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

        val activitiesJson = buildActivitiesJson(
            mapOf(
                "externalId" to "eur-1",
                "type" to "TRANSFER_IN",
                "amount" to 500.0,
                "currency" to "EUR",
                "tradeDate" to "2024-06-15"
            )
        )

        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns activitiesJson
        every { activityRepository.findByConnectionIdAndExternalId(10L, "eur-1") } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        service.syncActivitiesForConnection(10L)

        val saved = slot.captured
        // Falls back to raw amount
        assertEquals(0, BigDecimal("500.0").compareTo(saved.amountCad))
        assertEquals(null, saved.exchangeRate)
    }

    @Test
    fun `skips connection with null gatewayConnectionId`() {
        val connNoGw = mockk<BrokerConnection>(relaxed = true) {
            every { id } returns 10L
            every { user } returns mockUser
            every { gatewayConnectionId } returns null
            every { accountIdExternal } returns "ext-account-123"
            every { accountName } returns "My RRSP"
        }
        every { connectionRepository.findById(10L) } returns Optional.of(connNoGw)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(0, count)
        verify(exactly = 0) { gatewayClient.getActivities(any(), any(), any(), any()) }
    }
}
