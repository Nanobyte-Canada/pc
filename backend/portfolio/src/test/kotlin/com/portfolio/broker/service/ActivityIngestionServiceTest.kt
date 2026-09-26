package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.entity.BrokerActivity
import com.portfolio.broker.entity.BrokerBalanceSnapshot
import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.BrokerSyncProgress
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.BrokerActivityRepository
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionOperations
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityIngestionServiceTest {

    private lateinit var service: ActivityIngestionService
    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var activityRepository: BrokerActivityRepository
    private lateinit var balanceRepository: BrokerBalanceRepository
    private lateinit var gatewayClient: BrokerGatewayClient
    private lateinit var exchangeRateService: ExchangeRateService
    private lateinit var progressService: BrokerSyncProgressService
    private val objectMapper = ObjectMapper()

    private lateinit var mockUser: User
    private lateinit var mockConnection: BrokerConnection

    // Runs the chunk callback inline with no real transaction — a relaxed TransactionTemplate
    // mock would return null and silently skip every chunk.
    private val inlineTx = object : TransactionOperations {
        override fun <T : Any?> execute(action: TransactionCallback<T>): T? =
            action.doInTransaction(SimpleTransactionStatus())
    }

    /**
     * Tags every transaction so a test can tell *which* transaction a write landed in: [openTxId]
     * is the transaction currently open (null outside one) and [lastFailedTxId] is the one that
     * rolled back. Used to prove a status write survives the balance transaction's rollback.
     */
    private class TrackingTransactionOperations : TransactionOperations {
        var openTxId: Int? = null
            private set
        var lastFailedTxId: Int? = null
            private set
        private var nextId = 0

        override fun <T : Any?> execute(action: TransactionCallback<T>): T? {
            val id = ++nextId
            val enclosing = openTxId
            openTxId = id
            return try {
                action.doInTransaction(SimpleTransactionStatus())
            } catch (e: Throwable) {
                lastFailedTxId = id
                throw e
            } finally {
                openTxId = enclosing
            }
        }
    }

    @BeforeEach
    fun setup() {
        connectionRepository = mockk(relaxed = true)
        activityRepository = mockk(relaxed = true)
        balanceRepository = mockk(relaxed = true)
        gatewayClient = mockk()
        exchangeRateService = mockk()
        progressService = mockk(relaxed = true)

        // Default: return ONE for CAD, null for everything else (tests override as needed)
        every { exchangeRateService.getRate("CAD", any()) } returns BigDecimal.ONE
        every { exchangeRateService.getRate(neq("CAD"), any()) } returns null
        // Default: no stored progress — full history from now (tests override as needed)
        every { progressService.get(any(), any()) } returns null

        service = ActivityIngestionService(
            connectionRepository, activityRepository, balanceRepository,
            gatewayClient, objectMapper, exchangeRateService,
            inlineTx, progressService, ConnectionSyncGuard(),
            // maxLookbackYears omitted → production default (5y), which the lookback test asserts
            chunkDays = 29
        )

        // JpaRepository.save() has generic signature <S extends T> S save(S).
        // MockK relaxed mocks can't resolve the generic and return Object(), causing ClassCastException.
        every { connectionRepository.save(any<BrokerConnection>()) } answers { firstArg() }
        every { balanceRepository.save(any<BrokerBalanceSnapshot>()) } answers { firstArg() }
        every { activityRepository.save(any<BrokerActivity>()) } answers { firstArg() }

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

    private fun questradeActivityWithoutExternalId(): Map<String, Any?> = mapOf(
        "type" to "BUY",
        "symbol" to "AAPL",
        "description" to "Buy 10 AAPL",
        "quantity" to 10.0,
        "price" to 150.5,
        "amount" to -1505.0,
        "fee" to 1.0,
        "currency" to "USD",
        "tradeDate" to "2026-03-02",
        "settlementDate" to "2026-03-04",
        "optionType" to null
    )

    @Test
    fun `incremental sync stores fingerprint as external id when broker id is absent`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.of(2026, 3, 1)
        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns
            buildActivitiesJson(questradeActivityWithoutExternalId())

        val fingerprint = "e23ae395c505668a37360d33d08196a2ff38fed00be4d76e3e2330bf0d6e4da6"
        every { activityRepository.findByConnectionIdAndExternalId(10L, fingerprint) } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(1, count)
        assertEquals(fingerprint, slot.captured.externalId)
    }

    @Test
    fun `incremental sync skips an activity already present by fingerprint`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.of(2026, 3, 1)
        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns
            buildActivitiesJson(questradeActivityWithoutExternalId())
        every {
            activityRepository.findByConnectionIdAndExternalId(
                10L, "e23ae395c505668a37360d33d08196a2ff38fed00be4d76e3e2330bf0d6e4da6"
            )
        } returns mockk(relaxed = true)

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(0, count)
        verify(exactly = 0) { activityRepository.save(any<BrokerActivity>()) }
    }

    @Test
    fun `full history resumes from saved progress after a chunk failure`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null

        // Seed a mid-history resume point: the next chunk to attempt is 2026-09-03..2026-10-01 (chunkDays = 29).
        every { progressService.get(10L, "ACTIVITIES_FULL") } returns BrokerSyncProgress(
            connectionId = 10L, syncKind = "ACTIVITIES_FULL", nextChunkEnd = LocalDate.of(2026, 10, 1)
        )
        // Catch-all first (keeps the loop terminating), then override the chunk being resumed with a failure.
        every { gatewayClient.getActivities(any(), any(), any(), any()) } returns buildActivitiesJson()
        every {
            gatewayClient.getActivities(
                "gw-conn-123", "ext-account-123",
                LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 1)
            )
        } throws RuntimeException("boom")

        val firstRun = service.syncActivitiesForConnection(10L)

        assertEquals(0, firstRun)
        verify(exactly = 0) { progressService.advance(10L, "ACTIVITIES_FULL", any()) } // failed chunk never advances
        verify(exactly = 0) { progressService.clear(10L, "ACTIVITIES_FULL") }          // progress survives for resume

        // Second run resumes at the SAME saved point and succeeds.
        every {
            gatewayClient.getActivities(
                "gw-conn-123", "ext-account-123",
                LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 1)
            )
        } returns buildActivitiesJson(questradeActivityWithoutExternalId())
        every { activityRepository.findByConnectionIdAndExternalId(10L, any()) } returns null

        val secondRun = service.syncActivitiesForConnection(10L)

        assertEquals(1, secondRun)
        verify { progressService.advance(10L, "ACTIVITIES_FULL", LocalDate.of(2026, 9, 2)) }
        verify { progressService.clear(10L, "ACTIVITIES_FULL") }
    }

    @Test
    fun `sync resumes full history from saved progress even when activities already exist`() {
        val today = LocalDate.now()
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        // Partial save: some chunks already committed, so a latest trade date exists. Dispatching
        // on latestDate alone would take the incremental path and never fetch the historical gap.
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns today.minusDays(3)
        val resumePoint = today.minusDays(40)
        every { progressService.get(10L, BrokerSyncProgressService.ACTIVITIES_FULL) } returns BrokerSyncProgress(
            connectionId = 10L,
            syncKind = BrokerSyncProgressService.ACTIVITIES_FULL,
            nextChunkEnd = resumePoint
        )

        val calls = mutableListOf<Pair<LocalDate, LocalDate?>>()
        every { gatewayClient.getActivities(any(), any(), any(), any()) } answers {
            calls.add(thirdArg<LocalDate>() to arg<LocalDate?>(3))
            buildActivitiesJson() // empty → 12 consecutive empty chunks terminate the walk
        }

        service.syncActivitiesForConnection(10L)

        assertTrue(calls.isNotEmpty(), "expected gateway activity calls")
        // 1. walk restarts exactly at the stored next_chunk_end (chunk window is chunkDays inclusive)
        assertEquals(resumePoint.minusDays(28), calls.first().first)
        assertEquals(resumePoint, calls.first().second)
        // 2. NOT the incremental shape — that call passes endDate = null over an open window
        assertTrue(calls.none { it.second == null }, "incremental call shape detected: $calls")
        assertTrue(calls.size > 1, "expected a multi-chunk historical walk, got ${calls.size} call(s)")
        // incremental would have started at latestDate.minusDays(1) — never used here
        verify(exactly = 0) { gatewayClient.getActivities("gw-conn-123", "ext-account-123", today.minusDays(4), null) }
    }

    @Test
    fun `stale completed progress record does not pin connection into resume mode`() {
        val today = LocalDate.now()
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns today.minusDays(5)
        // A finished walk's last advance wrote a point below the lookback floor; if the process
        // died before clear(), the row lingers. It must fall through (no re-walk) and be cleaned up.
        val completedPoint = today.minusYears(30).minusDays(1)
        every { progressService.get(10L, BrokerSyncProgressService.ACTIVITIES_FULL) } returns BrokerSyncProgress(
            connectionId = 10L,
            syncKind = BrokerSyncProgressService.ACTIVITIES_FULL,
            nextChunkEnd = completedPoint
        )
        every { gatewayClient.getActivities(any(), any(), any(), any()) } returns buildActivitiesJson()

        val firstRun = service.syncActivitiesForConnection(10L)

        assertEquals(0, firstRun)
        verify(exactly = 0) { gatewayClient.getActivities(any(), any(), any(), any()) } // no re-walk
        verify { progressService.clear(10L, BrokerSyncProgressService.ACTIVITIES_FULL) } // stale row removed

        // Row cleared → next run falls through to the normal incremental path
        every { progressService.get(10L, BrokerSyncProgressService.ACTIVITIES_FULL) } returns null

        service.syncActivitiesForConnection(10L)

        verify { gatewayClient.getActivities("gw-conn-123", "ext-account-123", today.minusDays(6), null) }
    }

    @Test
    fun `full history starts five years back by default`() {
        every { connectionRepository.findById(10L) } returns Optional.of(fullHistoryConnection())
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null
        every { progressService.get(10L, "ACTIVITIES_FULL") } returns null
        // One new activity per chunk keeps the 12-consecutive-empty terminator from stopping the
        // walk before it reaches the lookback floor.
        every { activityRepository.findByConnectionIdAndExternalId(10L, any()) } returns null
        val starts = mutableListOf<LocalDate>()
        every { gatewayClient.getActivities(any(), any(), capture(starts), any()) } returns
            buildActivitiesJson(questradeActivityWithoutExternalId())

        service.syncActivitiesForConnection(10L)

        // newest chunk is [today(ET)-28d .. today(ET)]; the walked-back window must reach ~5y back
        val earliest = LocalDate.now(ZoneId.of("America/Toronto")).minusYears(5)
        assertThat(starts.min()).isAfterOrEqualTo(earliest)
        assertThat(starts.min()).isBeforeOrEqualTo(earliest.plusDays(30))   // 29-day chunks: last chunk clamps to earliest
    }

    // Real entity rather than a relaxed mock: MockK relaxed mocks answer unstubbed nullable
    // properties with child mocks, which would hide the honest nulls these tests assert on.
    private fun fullHistoryConnection(): BrokerConnection = BrokerConnection(
        id = 10L,
        user = mockUser,
        gatewayConnectionId = "gw-conn-123",
        accountIdExternal = "ext-account-123",
        accountName = "My RRSP"
    )

    private fun stubFullHistoryConnection(conn: BrokerConnection) {
        every { connectionRepository.findById(10L) } returns Optional.of(conn)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns null
    }

    @Test
    fun `activities watermark is NOT advanced when the first chunk fails mid-history`() {
        val conn = fullHistoryConnection()
        stubFullHistoryConnection(conn)
        every { gatewayClient.getActivities(any(), any(), any(), any()) } throws RuntimeException("boom")
        val connSlot = slot<BrokerConnection>()
        every { connectionRepository.save(capture(connSlot)) } answers { firstArg() }

        service.syncActivitiesForConnection(10L)

        assertNull(connSlot.captured.lastActivitiesFetchedAt)
        assertEquals("FAILED", connSlot.captured.lastActivitiesSyncStatus)
    }

    @Test
    fun `activities watermark records PARTIAL when some chunks succeeded before a failure`() {
        val conn = fullHistoryConnection()
        stubFullHistoryConnection(conn)
        every { activityRepository.findByConnectionIdAndExternalId(10L, any()) } returns null
        var calls = 0
        every { gatewayClient.getActivities(any(), any(), any(), any()) } answers {
            if (++calls == 1) buildActivitiesJson(questradeActivityWithoutExternalId())
            else throw RuntimeException("boom")
        }
        val connSlot = slot<BrokerConnection>()
        every { connectionRepository.save(capture(connSlot)) } answers { firstArg() }

        service.syncActivitiesForConnection(10L)

        // only a genuine success advances the watermark
        assertNull(connSlot.captured.lastActivitiesFetchedAt)
        assertEquals("PARTIAL", connSlot.captured.lastActivitiesSyncStatus)
        verify { activityRepository.save(any()) } // chunk 1 rows persisted
    }

    @Test
    fun `activities watermark set to SUCCESS only after a clean full run`() {
        val conn = fullHistoryConnection()
        stubFullHistoryConnection(conn)
        every { gatewayClient.getActivities(any(), any(), any(), any()) } returns buildActivitiesJson()
        val connSlot = slot<BrokerConnection>()
        every { connectionRepository.save(capture(connSlot)) } answers { firstArg() }

        service.syncActivitiesForConnection(10L)

        assertNotNull(connSlot.captured.lastActivitiesFetchedAt)
        assertEquals("SUCCESS", connSlot.captured.lastActivitiesSyncStatus)
    }

    @Test
    fun `balance sync records FAILED status without advancing the watermark`() {
        val conn = fullHistoryConnection()
        every { connectionRepository.findById(10L) } returns Optional.of(conn)
        every { gatewayClient.getBalances("gw-conn-123", "ext-account-123") } throws RuntimeException("boom")
        val connSlot = slot<BrokerConnection>()
        every { connectionRepository.save(capture(connSlot)) } answers { firstArg() }

        assertFailsWith<RuntimeException> { service.syncBalanceForConnection(10L) }

        assertNull(connSlot.captured.lastBalanceFetchedAt)
        assertEquals("FAILED", connSlot.captured.lastBalanceSyncStatus)
    }

    @Test
    fun `balance FAILED status is recorded outside the transaction that rolls back`() {
        val conn = fullHistoryConnection()
        every { connectionRepository.findById(10L) } returns Optional.of(conn)

        // Inner sync throws *inside* the balance transaction — after the fetch, while persisting —
        // so only a wrapper sitting outside that transaction can still record the status.
        every { gatewayClient.getBalances("gw-conn-123", "ext-account-123") } returns
            objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
                mapOf(
                    "totalValue" to 10000.0,
                    "cashBalances" to emptyList<Any>(),
                    "buyingPower" to 0,
                    "currency" to "CAD"
                )
            )
        every { balanceRepository.findByConnectionIdAndAsOfDate(10L, any()) } returns null
        every { balanceRepository.save(any<BrokerBalanceSnapshot>()) } throws IllegalStateException("db down")

        val tx = TrackingTransactionOperations()
        val svc = ActivityIngestionService(
            connectionRepository, activityRepository, balanceRepository,
            gatewayClient, objectMapper, exchangeRateService,
            tx, progressService, ConnectionSyncGuard(),
            chunkDays = 29
        )
        var txIdAtSave: Int? = null
        every { connectionRepository.save(any<BrokerConnection>()) } answers {
            txIdAtSave = tx.openTxId
            firstArg()
        }

        val ex = assertFailsWith<IllegalStateException> { svc.syncBalanceForConnection(10L) }

        // the original exception still propagates, unchanged
        assertEquals("db down", ex.message)
        // ...and the FAILED status was still recorded, exactly once, without the watermark moving
        verify(exactly = 1) { connectionRepository.save(any<BrokerConnection>()) }
        assertEquals("FAILED", conn.lastBalanceSyncStatus)
        assertNull(conn.lastBalanceFetchedAt)
        // the balance transaction did run and did roll back
        assertNotNull(tx.lastFailedTxId)
        // ...and the FAILED status was written inside a *different* transaction, so it survives
        assertNotNull(txIdAtSave)
        assertNotEquals(tx.lastFailedTxId, txIdAtSave)
    }

    @Test
    fun `balance sync records SUCCESS status after a clean fetch`() {
        val conn = fullHistoryConnection()
        every { connectionRepository.findById(10L) } returns Optional.of(conn)
        every { gatewayClient.getBalances("gw-conn-123", "ext-account-123") } returns
            objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
                mapOf(
                    "totalValue" to 10000.0,
                    "cashBalances" to emptyList<Any>(),
                    "buyingPower" to 0,
                    "currency" to "CAD"
                )
            )
        every { balanceRepository.findByConnectionIdAndAsOfDate(10L, any()) } returns null
        val connSlot = slot<BrokerConnection>()
        every { connectionRepository.save(capture(connSlot)) } answers { firstArg() }

        service.syncBalanceForConnection(10L)

        assertNotNull(connSlot.captured.lastBalanceFetchedAt)
        assertEquals("SUCCESS", connSlot.captured.lastBalanceSyncStatus)
    }
}
