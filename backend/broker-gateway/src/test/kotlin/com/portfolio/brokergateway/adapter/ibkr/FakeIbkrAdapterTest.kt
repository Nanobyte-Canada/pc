package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeIbkrAdapterTest {

    private val adapter = FakeIbkrAdapter()
    private val credentials = BrokerCredentials.IbkrCredentials(host = "localhost", port = 4002, clientId = 2)

    @Test
    fun `brokerType is IBKR`() {
        assertEquals(BrokerType.IBKR, adapter.brokerType)
    }

    @Test
    fun `validateConnection always returns connected`() {
        val result = adapter.validateConnection(credentials)
        assertTrue(result.connected)
        assertNotNull(result.message)
    }

    @Test
    fun `listAccounts returns two accounts`() {
        val accounts = adapter.listAccounts(credentials)
        assertEquals(2, accounts.size)

        val margin = accounts.first { it.accountId == "DU1234567" }
        assertEquals(AccountType.MARGIN, margin.accountType)
        assertEquals("USD", margin.currency)
        assertEquals(BrokerType.IBKR, margin.brokerType)

        val rrsp = accounts.first { it.accountId == "DU7654321" }
        assertEquals(AccountType.RRSP, rrsp.accountType)
        assertEquals("CAD", rrsp.currency)
    }

    @Test
    fun `getBalances returns expected totals`() {
        val balance = adapter.getBalances(credentials, "DU1234567")
        assertEquals("DU1234567", balance.accountId)
        assertEquals(BigDecimal("250000.00"), balance.totalEquity)
        assertEquals(BigDecimal("220000.00"), balance.totalValue)
        assertEquals(BigDecimal("150000.00"), balance.buyingPower)
        assertEquals(2, balance.cashBalances.size)

        val usdCash = balance.cashBalances.first { it.currency == "USD" }
        assertEquals(BigDecimal("30000.00"), usdCash.amount)

        val cadCash = balance.cashBalances.first { it.currency == "CAD" }
        assertEquals(BigDecimal("5000.00"), cadCash.amount)
    }

    @Test
    fun `getPositions returns five positions with computed PnL`() {
        val positions = adapter.getPositions(credentials, "DU1234567")
        assertEquals(5, positions.size)

        val spy = positions.first { it.symbol == "SPY" }
        assertEquals(BigDecimal("100"), spy.quantity)
        assertEquals(BigDecimal("430.50"), spy.averageCost)
        assertEquals(BigDecimal("450.25"), spy.currentPrice)
        assertNotNull(spy.totalPnl)
        assertTrue(spy.totalPnl!! > BigDecimal.ZERO, "SPY should have positive PnL")
        assertNotNull(spy.totalPnlPercent)
        assertEquals(InstrumentType.STOCK, spy.instrumentType)
        assertEquals("USD", spy.currency)

        // Verify all positions have PnL computed
        positions.forEach { pos ->
            assertNotNull(pos.totalPnl)
            assertNotNull(pos.totalPnlPercent)
            assertNotNull(pos.currentValue)
        }
    }

    @Test
    fun `getActivities returns four activities`() {
        val activities = adapter.getActivities(credentials, "DU1234567", null, null)
        assertEquals(4, activities.size)

        val buy = activities.first { it.type == ActivityType.BUY }
        assertEquals("AAPL", buy.symbol)

        val div = activities.first { it.type == ActivityType.DIVIDEND }
        assertEquals("SPY", div.symbol)

        val sell = activities.first { it.type == ActivityType.SELL }
        assertEquals("TSLA", sell.symbol)

        val transfer = activities.first { it.type == ActivityType.TRANSFER_IN }
        assertEquals(BigDecimal("10000.00"), transfer.amount)
    }

    @Test
    fun `getOrders returns two orders`() {
        val orders = adapter.getOrders(credentials, "DU1234567")
        assertEquals(2, orders.size)

        val filled = orders.first { it.status == OrderStatus.FILLED }
        assertEquals("AAPL", filled.symbol)
        assertEquals(OrderAction.BUY, filled.action)
        assertEquals(OrderType.MARKET, filled.orderType)
        assertEquals(BigDecimal("50"), filled.totalQuantity)

        val open = orders.first { it.status == OrderStatus.SUBMITTED }
        assertEquals("GOOG", open.symbol)
        assertEquals(OrderType.LIMIT, open.orderType)
        assertEquals(BigDecimal("150.00"), open.limitPrice)
    }

    @Test
    fun `placeOrder returns auto-incremented orderId with SUBMITTED status`() {
        val request = OrderRequest(
            symbol = "MSFT",
            action = OrderAction.BUY,
            quantity = BigDecimal("10"),
            orderType = OrderType.LIMIT,
            limitPrice = BigDecimal("400.00")
        )
        val result1 = adapter.placeOrder(credentials, "DU1234567", request)
        val result2 = adapter.placeOrder(credentials, "DU1234567", request)

        assertEquals(OrderStatus.SUBMITTED, result1.status)
        assertNotNull(result1.brokerOrderId)
        assertEquals(OrderStatus.SUBMITTED, result2.status)
        assertNotNull(result2.brokerOrderId)

        val id1 = result1.brokerOrderId!!.toInt()
        val id2 = result2.brokerOrderId!!.toInt()
        assertEquals(id1 + 1, id2, "Order IDs should auto-increment")
    }

    @Test
    fun `cancelOrder returns success`() {
        val result = adapter.cancelOrder(credentials, "DU1234567", "10001")
        assertTrue(result.success)
        assertNotNull(result.message)
    }

    @Test
    fun `capabilities reports expected values`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.IBKR, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertEquals(4, caps.supportedOrderTypes.size)
        assertTrue(caps.supportsOptionPositions)
        assertTrue(caps.supportsRealTimeData)
        assertTrue(caps.isOfficialApi)
    }
}
