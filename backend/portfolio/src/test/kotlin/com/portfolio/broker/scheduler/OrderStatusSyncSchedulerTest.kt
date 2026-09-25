package com.portfolio.broker.scheduler

import com.portfolio.auth.entity.User
import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.OrderAction
import com.portfolio.broker.entity.OrderStatus
import com.portfolio.broker.entity.TradeOrder
import com.portfolio.broker.repository.TradeOrderRepository
import com.portfolio.broker.service.ConnectionSyncGuard
import com.portfolio.broker.service.PositionFetchService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

/**
 * The scheduled order-status sync is the one per-connection sync path that does NOT go
 * through `triggerManualFetch`, so it must take [ConnectionSyncGuard] itself — otherwise an
 * overlapping scheduled run and a manual fetch both do find-then-insert on `brokerOrderId`
 * and can duplicate trade orders.
 */
class OrderStatusSyncSchedulerTest {

    private lateinit var guard: ConnectionSyncGuard
    private lateinit var tradeOrderRepository: TradeOrderRepository
    private lateinit var positionFetchService: PositionFetchService
    private lateinit var scheduler: OrderStatusSyncScheduler

    private lateinit var user: User

    @BeforeEach
    fun setup() {
        guard = ConnectionSyncGuard()
        tradeOrderRepository = mockk()
        positionFetchService = mockk(relaxed = true)
        scheduler = OrderStatusSyncScheduler(tradeOrderRepository, positionFetchService, guard)
        user = mockk(relaxed = true)
    }

    private fun connection(id: Long) = BrokerConnection(
        id = id,
        user = user,
        gatewayConnectionId = "gw-$id",
        accountIdExternal = "acc-$id",
        accountName = "acct-$id"
    )

    private fun activeOrder(connection: BrokerConnection) = TradeOrder(
        user = user,
        connection = connection,
        symbol = "AAPL",
        action = OrderAction.BUY,
        requestedUnits = BigDecimal.ONE,
        requestedPrice = BigDecimal.TEN,
        requestedAmount = BigDecimal.TEN,
        status = OrderStatus.SUBMITTED
    )

    private fun stubActiveOrders(vararg connections: BrokerConnection) {
        every {
            tradeOrderRepository.findByStatusInAndCreatedAtAfter(any(), any())
        } returns connections.map { activeOrder(it) }
    }

    /** Cross-thread contention, exactly as a concurrent manual fetch would hit the guard. */
    private fun holdGuardFromOtherThread(connectionId: Long) {
        val held = AtomicBoolean()
        Thread { held.set(guard.tryAcquire(connectionId)) }.apply {
            start()
            join()
        }
        assertTrue(held.get(), "helper thread should hold the guard for connection $connectionId")
    }

    @Test
    fun `scheduled sync skips a connection whose guard is held and still syncs the next one`() {
        val held = connection(101L)
        val other = connection(202L)
        stubActiveOrders(held, other)
        holdGuardFromOtherThread(101L)

        scheduler.syncActiveOrders()

        verify(exactly = 0) {
            positionFetchService.syncOrdersForConnection(eq(held), any(), any(), any())
        }
        verify(exactly = 1) {
            positionFetchService.syncOrdersForConnection(other, user, "gw-202", "acc-202")
        }
    }

    @Test
    fun `scheduled sync releases the guard for every connection it syncs`() {
        val first = connection(101L)
        val second = connection(202L)
        stubActiveOrders(first, second)

        scheduler.syncActiveOrders()

        verify(exactly = 1) {
            positionFetchService.syncOrdersForConnection(first, user, "gw-101", "acc-101")
        }
        verify(exactly = 1) {
            positionFetchService.syncOrdersForConnection(second, user, "gw-202", "acc-202")
        }
        assertThat(guard.tryAcquire(101L)).isTrue()
        assertThat(guard.tryAcquire(202L)).isTrue()
    }

    @Test
    fun `scheduled sync releases the guard and keeps looping when a connection throws`() {
        val failing = connection(101L)
        val next = connection(202L)
        stubActiveOrders(failing, next)
        every {
            positionFetchService.syncOrdersForConnection(eq(failing), any(), any(), any())
        } throws RuntimeException("boom")

        scheduler.syncActiveOrders()

        verify(exactly = 1) {
            positionFetchService.syncOrdersForConnection(next, user, "gw-202", "acc-202")
        }
        assertThat(guard.tryAcquire(101L)).isTrue()
    }
}
