// test: adapter/wealthsimple/FakeWealthsimpleAdapterTest.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeWealthsimpleAdapterTest {

    private val adapter = FakeWealthsimpleAdapter()

    @Test
    fun `brokerType is WEALTHSIMPLE`() {
        assertEquals(BrokerType.WEALTHSIMPLE, adapter.brokerType)
    }

    @Test
    fun `validateConnection returns connected`() {
        val creds = BrokerCredentials.WealthsimpleCredentials(
            accessToken = "t", refreshToken = "r", expiresAtEpochSeconds = 999999999)
        assertTrue(adapter.validateConnection(creds).connected)
    }

    @Test
    fun `listAccounts includes crypto account`() {
        val creds = BrokerCredentials.WealthsimpleCredentials(
            accessToken = "t", refreshToken = "r", expiresAtEpochSeconds = 999999999)
        val accounts = adapter.listAccounts(creds)
        assertEquals(3, accounts.size)
        assertTrue(accounts.any { it.accountType == AccountType.CRYPTO })
        assertTrue(accounts.any { it.accountType == AccountType.TFSA })
        assertTrue(accounts.all { it.brokerType == BrokerType.WEALTHSIMPLE })
    }

    @Test
    fun `getPositions returns CAD positions`() {
        val creds = BrokerCredentials.WealthsimpleCredentials(
            accessToken = "t", refreshToken = "r", expiresAtEpochSeconds = 999999999)
        val positions = adapter.getPositions(creds, "tfsa-ghijkl")
        assertTrue(positions.isNotEmpty())
        assertTrue(positions.all { it.currency == "CAD" })
        assertTrue(positions.any { it.symbol.endsWith(".TO") })
    }

    @Test
    fun `placeOrder returns submitted result`() {
        val creds = BrokerCredentials.WealthsimpleCredentials(
            accessToken = "t", refreshToken = "r", expiresAtEpochSeconds = 999999999)
        val request = OrderRequest(symbol = "XEQT.TO", action = OrderAction.BUY,
            quantity = BigDecimal(50), orderType = OrderType.MARKET)
        val result = adapter.placeOrder(creds, "tfsa-ghijkl", request)
        assertNotNull(result.brokerOrderId)
        assertEquals(OrderStatus.SUBMITTED, result.status)
    }

    @Test
    fun `capabilities reflects Wealthsimple limitations`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.WEALTHSIMPLE, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertFalse(caps.isOfficialApi)
        assertFalse(caps.supportsOptionPositions)
        assertFalse(caps.supportsFractionalShares)
        assertEquals("7 trades/hour", caps.orderRateLimit)
        assertEquals(2, caps.supportedOrderTypes.size)
    }
}
