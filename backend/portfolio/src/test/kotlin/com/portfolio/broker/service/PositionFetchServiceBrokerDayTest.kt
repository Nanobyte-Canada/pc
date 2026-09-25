package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.User
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.entity.BrokerBalanceSnapshot
import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.BrokerPosition
import com.portfolio.broker.entity.PositionFetchLog
import com.portfolio.broker.entity.PositionFetchType
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuditService
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import com.portfolio.broker.repository.BrokerPositionRepository
import com.portfolio.broker.repository.PositionFetchLogRepository
import com.portfolio.broker.repository.TradeOrderRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import java.util.TimeZone
import kotlin.test.assertEquals

/**
 * Pins the broker-day date writers in [PositionFetchService] to ET: the balance as-of date
 * doubles as the dedup key for `broker_balance_snapshots` and must agree with the ET writer in
 * `ActivityIngestionService`, otherwise a manual fetch and the scheduled balance sync write rows
 * under different dates between 19:00 and 24:00 ET and dedup misses them.
 *
 * There is no injectable clock (out of scope), so the test controls the JVM default zone instead:
 * it runs the fetch twice with default zones that straddle ET (UTC+14 and UTC-12). A writer that
 * uses `LocalDate.now()` (JVM default) disagrees with the ET date for at least one of the two
 * runs at *every* wall-clock instant, while an ET writer agrees with both — so the assertion is
 * deterministic without freezing the clock.
 */
class PositionFetchServiceBrokerDayTest {

    private val objectMapper = ObjectMapper()
    private val et: ZoneId = ZoneId.of("America/Toronto")

    private lateinit var service: PositionFetchService
    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var positionRepository: BrokerPositionRepository
    private lateinit var fetchLogRepository: PositionFetchLogRepository
    private lateinit var balanceRepository: BrokerBalanceRepository
    private lateinit var tradeOrderRepository: TradeOrderRepository
    private lateinit var userRepository: UserRepository
    private lateinit var gatewayClient: BrokerGatewayClient
    private lateinit var auditService: AuditService

    private lateinit var user: User
    private lateinit var connection: BrokerConnection
    private lateinit var fetchLog: PositionFetchLog

    private val originalDefaultZone: TimeZone = TimeZone.getDefault()

    @BeforeEach
    fun setup() {
        connectionRepository = mockk()
        positionRepository = mockk()
        fetchLogRepository = mockk()
        balanceRepository = mockk()
        tradeOrderRepository = mockk(relaxed = true)
        userRepository = mockk()
        gatewayClient = mockk()
        auditService = mockk(relaxed = true)

        user = mockk(relaxed = true)
        connection = BrokerConnection(
            id = 10L,
            user = user,
            gatewayConnectionId = "gw-conn-123",
            accountIdExternal = "ext-account-123"
        )
        fetchLog = PositionFetchLog(connection = connection, user = user, fetchType = PositionFetchType.MANUAL)

        service = PositionFetchService(
            connectionRepository,
            positionRepository,
            fetchLogRepository,
            balanceRepository,
            tradeOrderRepository,
            userRepository,
            gatewayClient,
            auditService,
            objectMapper,
            mockk<NamedParameterJdbcTemplate>(relaxed = true),
            mockk(relaxed = true),   // analytics: runs after the snapshot, failures are non-fatal
            mockk(relaxed = true),   // notifications: only on terminal order-status changes
            ConnectionSyncGuard()
        )
    }

    @AfterEach
    fun restoreDefaultZone() {
        TimeZone.setDefault(originalDefaultZone)
    }

    private data class WrittenDates(val balanceAsOf: LocalDate, val positionAsOf: LocalDate)

    /** One full fetch; returns the date each writer stamped (captured from the mocks). */
    private fun runFetch(): WrittenDates {
        every { fetchLogRepository.findById(1L) } returns Optional.of(fetchLog)
        every { fetchLogRepository.save(any<PositionFetchLog>()) } answers { firstArg() }
        every { connectionRepository.findByIdAndUserId(10L, 1L) } returns connection
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { connectionRepository.save(any<BrokerConnection>()) } answers { firstArg() }

        every { gatewayClient.getPositions("gw-conn-123", "ext-account-123") } returns
            objectMapper.valueToTree(
                mapOf(
                    "positions" to listOf(
                        mapOf("symbol" to "AAPL", "quantity" to 10.0, "currency" to "CAD")
                    )
                )
            )
        every { gatewayClient.getBalances("gw-conn-123", "ext-account-123") } returns
            objectMapper.valueToTree(mapOf("totalEquity" to 1000.0))
        every { gatewayClient.getOrders("gw-conn-123", "ext-account-123") } returns
            objectMapper.valueToTree(mapOf("orders" to emptyList<Any>()))

        every { positionRepository.markAllNonCurrent(10L) } returns Unit
        val savedPositions = mutableListOf<BrokerPosition>()
        // capture() can't match a collection parameter (it resolves to the element type), so the
        // written entities are read out of the answer instead.
        every { positionRepository.saveAll(any<Iterable<BrokerPosition>>()) } answers {
            val written = firstArg<Iterable<BrokerPosition>>()
            savedPositions.addAll(written)
            written.toList()
        }

        val balanceAsOf = mutableListOf<LocalDate>()
        every { balanceRepository.findByConnectionIdAndAsOfDate(10L, capture(balanceAsOf)) } returns null
        every { balanceRepository.save(any<BrokerBalanceSnapshot>()) } answers { firstArg() }
        every { balanceRepository.flush() } returns Unit

        service.executePositionFetch(connectionId = 10L, fetchLogId = 1L, userId = 1L)

        assertEquals(1, balanceAsOf.size, "balance dedup key written exactly once")
        assertEquals(1, savedPositions.size, "one position written")
        return WrittenDates(balanceAsOf = balanceAsOf.single(), positionAsOf = savedPositions.single().asOfDate)
    }

    @Test
    fun `balance as-of and position as-of are the ET broker day whatever the JVM default zone is`() {
        // UTC+14 and UTC-12 sit on opposite sides of ET: a JVM-default writer matches the ET date
        // in at most one of the two runs (never both), an ET writer matches in both.
        for (defaultZone in listOf("Pacific/Kiritimati", "Etc/GMT+12")) {
            TimeZone.setDefault(TimeZone.getTimeZone(defaultZone))

            val written = runFetch()

            val etToday = LocalDate.now(et)
            assertEquals(
                etToday, written.balanceAsOf,
                "balance snapshot as-of (dedup key) must be the ET day with JVM default $defaultZone"
            )
            assertEquals(
                etToday, written.positionAsOf,
                "position asOfDate must be the ET day with JVM default $defaultZone"
            )
        }
    }
}
