package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeQuestradeAdapterTest {

    private val adapter = FakeQuestradeAdapter()

    @Test
    fun `brokerType is QUESTRADE`() {
        assertEquals(BrokerType.QUESTRADE, adapter.brokerType)
    }

    @Test
    fun `validateConnection returns connected`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "t", refreshToken = "r",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 999999999)
        assertTrue(adapter.validateConnection(creds).connected)
    }

    @Test
    fun `listAccounts returns Canadian accounts`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "t", refreshToken = "r",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 999999999)
        val accounts = adapter.listAccounts(creds)
        assertEquals(3, accounts.size)
        assertTrue(accounts.any { it.accountType == AccountType.TFSA })
        assertTrue(accounts.any { it.accountType == AccountType.RRSP })
        assertTrue(accounts.all { it.brokerType == BrokerType.QUESTRADE })
    }

    @Test
    fun `getPositions returns CAD-denominated positions`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "t", refreshToken = "r",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 999999999)
        val positions = adapter.getPositions(creds, "51443483")
        assertTrue(positions.isNotEmpty())
        assertTrue(positions.all { it.currency == "CAD" })
        assertTrue(positions.any { it.symbol.endsWith(".TO") })
    }

    @Test
    fun `placeOrder returns submitted result`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "t", refreshToken = "r",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 999999999)
        val request = OrderRequest(symbol = "XIU.TO", action = OrderAction.BUY,
            quantity = BigDecimal(100), orderType = OrderType.LIMIT,
            limitPrice = BigDecimal("34.00"))
        val result = adapter.placeOrder(creds, "51443483", request)
        assertNotNull(result.brokerOrderId)
        assertEquals(OrderStatus.SUBMITTED, result.status)
    }

    @Test
    fun `refreshAuth returns updated credentials`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "old", refreshToken = "old-refresh",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 1000)
        val refreshed = adapter.refreshAuth(creds) as BrokerCredentials.QuestradeCredentials
        assertEquals("fake-refreshed-token", refreshed.accessToken)
        assertTrue(refreshed.expiresAtEpochSeconds > 1000)
    }

    @Test
    fun `capabilities reports Questrade features`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.QUESTRADE, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertTrue(caps.isOfficialApi)
        assertEquals(false, caps.supportsFractionalShares)
    }
}
