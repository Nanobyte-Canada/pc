package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import com.portfolio.brokergateway.exception.BrokerConnectionException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IbkrAdapterTest {

    private val client = mockk<IbkrAccountClient>(relaxed = true)
    private val config = IbkrConfig(
        enabled = true,
        host = "localhost",
        port = 4002,
        clientId = 2
    )
    private val credentials = BrokerCredentials.IbkrCredentials(host = "localhost", port = 4002, clientId = 2)
    private lateinit var adapter: IbkrAdapter

    @BeforeEach
    fun setUp() {
        every { client.isConnected() } returns true
        adapter = IbkrAdapter(client, config)
    }

    @Test
    fun `brokerType is IBKR`() {
        assertEquals(BrokerType.IBKR, adapter.brokerType)
    }

    @Test
    fun `validateConnection checks client isConnected`() {
        every { client.isConnected() } returns true
        val result = adapter.validateConnection(credentials)
        assertTrue(result.connected)

        every { client.isConnected() } returns false
        val disconnected = adapter.validateConnection(credentials)
        assertEquals(false, disconnected.connected)
    }

    @Test
    fun `listAccounts delegates to client getManagedAccounts`() {
        every { client.getManagedAccounts() } returns listOf("U1234567", "U7654321")
        every { client.getAccountSummary("U1234567") } returns mapOf(
            "AccountAlias" to "Main Account",
            "AccountType" to "Margin",
            "Currency" to "USD"
        )
        every { client.getAccountSummary("U7654321") } returns mapOf(
            "AccountAlias" to "RRSP Account",
            "AccountType" to "RRSP",
            "Currency" to "CAD"
        )

        val accounts = adapter.listAccounts(credentials)
        assertEquals(2, accounts.size)

        val main = accounts.first { it.accountId == "U1234567" }
        assertEquals("Main Account", main.accountName)
        assertEquals(AccountType.MARGIN, main.accountType)
        assertEquals("USD", main.currency)
        assertEquals(BrokerType.IBKR, main.brokerType)

        val rrsp = accounts.first { it.accountId == "U7654321" }
        assertEquals(AccountType.RRSP, rrsp.accountType)
        assertEquals("CAD", rrsp.currency)
    }

    @Test
    fun `getPositions maps IbkrPosition to UnifiedPosition`() {
        val ibkrPosition = IbkrPosition(
            accountId = "U1234567",
            symbol = "AAPL",
            secType = "STK",
            exchange = "SMART",
            currency = "USD",
            conId = 265598,
            quantity = BigDecimal("50"),
            averageCost = BigDecimal("170.00"),
            marketPrice = BigDecimal("185.50"),
            marketValue = BigDecimal("9275.00"),
            unrealizedPnl = BigDecimal("775.00")
        )
        every { client.getPositions() } returns listOf(ibkrPosition)

        val positions = adapter.getPositions(credentials, "U1234567")
        assertEquals(1, positions.size)

        val pos = positions[0]
        assertEquals("AAPL", pos.symbol)
        assertEquals("265598", pos.symbolId)
        assertEquals(InstrumentType.STOCK, pos.instrumentType)
        assertEquals(BigDecimal("50"), pos.quantity)
        assertEquals(BigDecimal("170.00"), pos.averageCost)
        assertEquals(BigDecimal("185.50"), pos.currentPrice)
        assertEquals(BigDecimal("9275.00"), pos.currentValue)
        assertEquals(BigDecimal("775.00"), pos.totalPnl)
        assertNotNull(pos.totalPnlPercent)
        assertEquals("USD", pos.currency)
    }

    @Test
    fun `getPositions filters by accountId`() {
        val pos1 = IbkrPosition("U1234567", "AAPL", "STK", "SMART", "USD", 1, BigDecimal("50"), BigDecimal("170.00"))
        val pos2 = IbkrPosition("U9999999", "MSFT", "STK", "SMART", "USD", 2, BigDecimal("30"), BigDecimal("380.00"))
        every { client.getPositions() } returns listOf(pos1, pos2)

        val positions = adapter.getPositions(credentials, "U1234567")
        assertEquals(1, positions.size)
        assertEquals("AAPL", positions[0].symbol)
    }

    @Test
    fun `placeOrder builds contract and order spec correctly`() {
        every { client.placeOrder(any(), any(), any()) } returns 12345

        val request = OrderRequest(
            symbol = "AAPL",
            action = OrderAction.BUY,
            quantity = BigDecimal("100"),
            orderType = OrderType.LIMIT,
            limitPrice = BigDecimal("180.00"),
            timeInForce = TimeInForce.GTC,
            currency = "USD"
        )

        val result = adapter.placeOrder(credentials, "U1234567", request)
        assertEquals("12345", result.brokerOrderId)
        assertEquals(OrderStatus.SUBMITTED, result.status)

        verify {
            client.placeOrder(
                "U1234567",
                match { c ->
                    c.symbol == "AAPL" && c.secType == "STK" && c.exchange == "SMART" && c.currency == "USD"
                },
                match { o ->
                    o.action == "BUY" && o.orderType == "LMT" &&
                        o.totalQuantity == BigDecimal("100") &&
                        o.limitPrice == BigDecimal("180.00") &&
                        o.timeInForce == "GTC"
                }
            )
        }
    }

    @Test
    fun `placeOrder maps all order types correctly`() {
        every { client.placeOrder(any(), any(), any()) } returns 1

        val baseRequest = OrderRequest(
            symbol = "SPY",
            action = OrderAction.BUY,
            quantity = BigDecimal("10"),
            orderType = OrderType.MARKET
        )

        adapter.placeOrder(credentials, "U1234567", baseRequest)
        verify { client.placeOrder(any(), any(), match { it.orderType == "MKT" }) }

        adapter.placeOrder(credentials, "U1234567", baseRequest.copy(orderType = OrderType.LIMIT, limitPrice = BigDecimal("100")))
        verify { client.placeOrder(any(), any(), match { it.orderType == "LMT" }) }

        adapter.placeOrder(credentials, "U1234567", baseRequest.copy(orderType = OrderType.STOP, stopPrice = BigDecimal("90")))
        verify { client.placeOrder(any(), any(), match { it.orderType == "STP" }) }

        adapter.placeOrder(credentials, "U1234567", baseRequest.copy(orderType = OrderType.STOP_LIMIT, limitPrice = BigDecimal("100"), stopPrice = BigDecimal("90")))
        verify { client.placeOrder(any(), any(), match { it.orderType == "STP LMT" }) }
    }

    @Test
    fun `cancelOrder delegates to client cancelOrder`() {
        val result = adapter.cancelOrder(credentials, "U1234567", "12345")
        assertTrue(result.success)
        verify { client.cancelOrder(12345) }
    }

    @Test
    fun `methods throw BrokerConnectionException when not connected`() {
        every { client.isConnected() } returns false

        assertThrows<BrokerConnectionException> {
            adapter.listAccounts(credentials)
        }
        assertThrows<BrokerConnectionException> {
            adapter.getBalances(credentials, "U1234567")
        }
        assertThrows<BrokerConnectionException> {
            adapter.getPositions(credentials, "U1234567")
        }
        assertThrows<BrokerConnectionException> {
            adapter.placeOrder(
                credentials, "U1234567",
                OrderRequest("AAPL", OrderAction.BUY, BigDecimal("10"), OrderType.MARKET)
            )
        }
    }

    @Test
    fun `capabilities returns expected values`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.IBKR, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertEquals(4, caps.supportedOrderTypes.size)
        assertTrue(caps.isOfficialApi)
    }
}
