package com.portfolio.broker.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.entity.BrokerActivity
import com.portfolio.broker.entity.BrokerBalanceSnapshot
import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.FetchStatus
import com.portfolio.broker.entity.PositionFetchLog
import com.portfolio.broker.repository.BrokerActivityRepository
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import com.portfolio.broker.repository.BrokerPositionRepository
import com.portfolio.broker.repository.PositionFetchLogRepository
import com.portfolio.broker.repository.TradeOrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionOperations
import java.time.LocalDate
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Single-flight guard for per-connection syncs: scheduled runs, `POST /sync-activities`,
 * `POST /sync-all`, `/fetch` and the dashboard refresh all converge on the three guarded
 * entry points below, so the guard (not the callers) is where overlap must be rejected.
 */
class ConnectionSyncGuardTest {

    private lateinit var guard: ConnectionSyncGuard

    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var activityRepository: BrokerActivityRepository
    private lateinit var balanceRepository: BrokerBalanceRepository
    private lateinit var positionRepository: BrokerPositionRepository
    private lateinit var fetchLogRepository: PositionFetchLogRepository
    private lateinit var tradeOrderRepository: TradeOrderRepository
    private lateinit var userRepository: UserRepository
    private lateinit var gatewayClient: BrokerGatewayClient
    private lateinit var exchangeRateService: ExchangeRateService
    private lateinit var progressService: BrokerSyncProgressService
    private lateinit var auditService: AuditService
    private lateinit var accountAnalyticsComputeService: AccountAnalyticsComputeService
    private lateinit var notificationService: NotificationService

    private val objectMapper = ObjectMapper()

    private val inlineTx = object : TransactionOperations {
        override fun <T : Any?> execute(action: TransactionCallback<T>): T? =
            action.doInTransaction(SimpleTransactionStatus())
    }

    private lateinit var activityService: ActivityIngestionService
    private lateinit var positionService: PositionFetchService

    private lateinit var user: User

    @BeforeEach
    fun setup() {
        guard = ConnectionSyncGuard()

        connectionRepository = mockk(relaxed = true)
        activityRepository = mockk(relaxed = true)
        balanceRepository = mockk(relaxed = true)
        positionRepository = mockk(relaxed = true)
        fetchLogRepository = mockk(relaxed = true)
        tradeOrderRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        gatewayClient = mockk(relaxed = true)
        exchangeRateService = mockk(relaxed = true)
        progressService = mockk(relaxed = true)
        auditService = mockk(relaxed = true)
        accountAnalyticsComputeService = mockk(relaxed = true)
        notificationService = mockk(relaxed = true)

        every { exchangeRateService.getRate("CAD", any()) } returns java.math.BigDecimal.ONE
        every { exchangeRateService.getRate(neq("CAD"), any()) } returns null
        every { progressService.get(any(), any()) } returns null

        // JpaRepository.save() has a generic signature MockK relaxed mocks can't resolve.
        every { connectionRepository.save(any<BrokerConnection>()) } answers { firstArg() }
        every { balanceRepository.save(any<BrokerBalanceSnapshot>()) } answers { firstArg() }
        every { activityRepository.save(any<BrokerActivity>()) } answers { firstArg() }
        every { fetchLogRepository.save(any<PositionFetchLog>()) } answers { firstArg() }

        user = mockk(relaxed = true)

        activityService = ActivityIngestionService(
            connectionRepository, activityRepository, balanceRepository,
            gatewayClient, objectMapper, exchangeRateService,
            inlineTx, progressService, guard
        )
        positionService = PositionFetchService(
            connectionRepository, positionRepository, fetchLogRepository, balanceRepository,
            tradeOrderRepository, userRepository, gatewayClient, auditService, objectMapper,
            mockk(relaxed = true), accountAnalyticsComputeService, notificationService, guard
        )
    }

    private fun connection(id: Long, gatewayConnectionId: String? = "gw-$id") = BrokerConnection(
        id = id,
        user = user,
        gatewayConnectionId = gatewayConnectionId,
        accountIdExternal = "acc-$id",
        accountName = "acct-$id"
    )

    private fun emptyActivitiesJson(): JsonNode =
        objectMapper.valueToTree(mapOf("activities" to emptyList<Any>()))

    private fun balanceJson(): JsonNode = objectMapper.valueToTree(
        mapOf(
            "totalValue" to 10000.0,
            "cashBalances" to emptyList<Any>(),
            "buyingPower" to 0,
            "currency" to "CAD"
        )
    )

    /**
     * Single-flight means one holder regardless of thread, and the guard must reject a
     * second acquire by ANY thread — so contention is simulated from a helper thread,
     * exactly as a concurrent scheduled/manual run would hit it.
     */
    private fun holdGuardFromOtherThread(connectionId: Long) {
        val held = AtomicBoolean()
        Thread { held.set(guard.tryAcquire(connectionId)) }.apply {
            start()
            join()
        }
        assertTrue(held.get(), "helper thread should hold the guard for connection $connectionId")
    }

    // ---------- guard unit behaviour ----------

    @Test
    fun `second acquire for the same connection fails while the first is held`() {
        val guard = ConnectionSyncGuard()
        assertThat(guard.tryAcquire(7L)).isTrue()
        assertThat(guard.tryAcquire(7L)).isFalse()
        guard.release(7L)
        assertThat(guard.tryAcquire(7L)).isTrue()
    }

    @Test
    fun `different connections do not interfere`() {
        val guard = ConnectionSyncGuard()
        assertThat(guard.tryAcquire(1L)).isTrue()
        assertThat(guard.tryAcquire(2L)).isTrue()
    }

    // ---------- guarded entry points skip while the guard is held ----------

    @Test
    fun `activities sync skips while another run holds the guard`() {
        holdGuardFromOtherThread(10L)
        // A full run is stubbed out, so only the guard can keep the count at 0.
        every { connectionRepository.findById(10L) } returns Optional.of(connection(10L))
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.now().minusDays(3)
        every { gatewayClient.getActivities(any(), any(), any(), any()) } returns emptyActivitiesJson()

        val count = activityService.syncActivitiesForConnection(10L)

        assertEquals(0, count)
        verify(exactly = 0) { connectionRepository.findById(any()) }
        verify(exactly = 0) { gatewayClient.getActivities(any(), any(), any(), any()) }
    }

    @Test
    fun `balance sync skips while another run holds the guard`() {
        holdGuardFromOtherThread(20L)
        every { connectionRepository.findById(20L) } returns Optional.of(connection(20L))
        every { gatewayClient.getBalances(any(), any()) } returns balanceJson()
        every { balanceRepository.findByConnectionIdAndAsOfDate(20L, any()) } returns null

        activityService.syncBalanceForConnection(20L)

        verify(exactly = 0) { connectionRepository.findById(any()) }
        verify(exactly = 0) { gatewayClient.getBalances(any(), any()) }
    }

    @Test
    fun `manual fetch returns a failed log while another run holds the guard`() {
        holdGuardFromOtherThread(30L)
        every { connectionRepository.findByIdAndUserId(30L, 1L) } returns connection(30L)

        val fetchLog = positionService.triggerManualFetch(30L, 1L)

        assertEquals(FetchStatus.FAILED, fetchLog.status)
        assertEquals("already in progress; skipped", fetchLog.errorMessage)
        verify(exactly = 1) { fetchLogRepository.save(any<PositionFetchLog>()) }
        verify(exactly = 0) { gatewayClient.getPositions(any(), any()) }
    }

    // ---------- the guard is released when a guarded method finishes ----------

    @Test
    fun `activities sync releases the guard when it finishes`() {
        every { connectionRepository.findById(10L) } returns Optional.of(connection(10L))
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.now().minusDays(3)
        every { gatewayClient.getActivities(any(), any(), any(), any()) } returns emptyActivitiesJson()

        assertEquals(0, activityService.syncActivitiesForConnection(10L))

        assertThat(guard.tryAcquire(10L)).isTrue()
    }

    @Test
    fun `balance sync releases the guard after a successful run`() {
        every { connectionRepository.findById(10L) } returns Optional.of(connection(10L))
        every { gatewayClient.getBalances("gw-10", "acc-10") } returns balanceJson()
        every { balanceRepository.findByConnectionIdAndAsOfDate(10L, any()) } returns null

        activityService.syncBalanceForConnection(10L)

        assertThat(guard.tryAcquire(10L)).isTrue()
    }

    @Test
    fun `balance sync releases the guard when the sync throws`() {
        every { connectionRepository.findById(10L) } returns Optional.of(connection(10L))
        every { gatewayClient.getBalances(any(), any()) } throws RuntimeException("boom")

        assertFailsWith<RuntimeException> { activityService.syncBalanceForConnection(10L) }

        assertThat(guard.tryAcquire(10L)).isTrue()
    }

    @Test
    fun `manual fetch releases the guard when the fetch fails`() {
        val conn = connection(10L)
        every { connectionRepository.findByIdAndUserId(10L, 1L) } returns conn
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { gatewayClient.getPositions(any(), any()) } throws RuntimeException("broker down")

        var savedLog: PositionFetchLog? = null
        every { fetchLogRepository.save(any<PositionFetchLog>()) } answers {
            firstArg<PositionFetchLog>().also { savedLog = it }
        }
        // executePositionFetch reloads the log that triggerManualFetch just saved (id 0 in tests)
        every { fetchLogRepository.findById(any()) } answers { Optional.of(savedLog!!) }

        assertFailsWith<RuntimeException> { positionService.triggerManualFetch(10L, 1L) }

        assertThat(guard.tryAcquire(10L)).isTrue()
    }
}
