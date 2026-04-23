// adapter/wealthsimple/FakeWealthsimpleAdapter.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger

@Component
@Profile("dev", "local", "test")
class FakeWealthsimpleAdapter : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val orderIdCounter = AtomicInteger(30000)

    override val brokerType = BrokerType.WEALTHSIMPLE

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        log.info("FakeWealthsimpleAdapter: validating connection")
        return ConnectionValidationResult(connected = true, message = "Fake Wealthsimple connection OK")
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials {
        val ws = credentials as BrokerCredentials.WealthsimpleCredentials
        return ws.copy(
            accessToken = "fake-ws-refreshed-token",
            refreshToken = "fake-ws-new-refresh-token",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 3600
        )
    }

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        return listOf(
            UnifiedAccount(accountId = "non-registered-abcdef", accountNumber = "WS-NR-001",
                accountName = "Personal", accountType = AccountType.CASH,
                currency = "CAD", brokerType = BrokerType.WEALTHSIMPLE, status = "ACTIVE"),
            UnifiedAccount(accountId = "tfsa-ghijkl", accountNumber = "WS-TFSA-001",
                accountName = "TFSA", accountType = AccountType.TFSA,
                currency = "CAD", brokerType = BrokerType.WEALTHSIMPLE, status = "ACTIVE"),
            UnifiedAccount(accountId = "crypto-mnopqr", accountNumber = "WS-CRYPTO-001",
                accountName = "Crypto", accountType = AccountType.CRYPTO,
                currency = "CAD", brokerType = BrokerType.WEALTHSIMPLE, status = "ACTIVE")
        )
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        return UnifiedBalance(
            accountId = accountId,
            totalEquity = BigDecimal("42000.00"),
            totalValue = BigDecimal("38500.00"),
            cashBalances = listOf(
                CashBalance(currency = "CAD", amount = BigDecimal("3500.00"))
            ),
            buyingPower = BigDecimal("3500.00"),
            currency = "CAD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        data class MockPos(val sym: String, val name: String, val qty: Int, val cost: Double, val price: Double, val type: InstrumentType)
        val positions = listOf(
            MockPos("XEQT.TO", "iShares Core Equity ETF", 300, 25.50, 27.80, InstrumentType.ETF),
            MockPos("SHOP.TO", "Shopify Inc", 15, 85.00, 102.50, InstrumentType.STOCK),
            MockPos("BN.TO", "Brookfield Corp", 50, 55.00, 62.30, InstrumentType.STOCK),
            MockPos("VEQT.TO", "Vanguard All-Equity ETF", 200, 36.00, 39.50, InstrumentType.ETF)
        )
        return positions.map { p ->
            val qty = BigDecimal(p.qty)
            val cost = BigDecimal(p.cost.toString())
            val price = BigDecimal(p.price.toString())
            val value = qty.multiply(price).setScale(2, RoundingMode.HALF_UP)
            val totalCost = qty.multiply(cost).setScale(2, RoundingMode.HALF_UP)
            val pnl = value.subtract(totalCost)
            val pnlPct = if (totalCost > BigDecimal.ZERO) pnl.multiply(BigDecimal(100)).divide(totalCost, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
            UnifiedPosition(
                symbol = p.sym, symbolId = null, securityName = p.name, instrumentType = p.type,
                quantity = qty, averageCost = cost, currentPrice = price,
                currentValue = value, totalPnl = pnl, totalPnlPercent = pnlPct, currency = "CAD"
            )
        }
    }

    override fun getActivities(
        credentials: BrokerCredentials, accountId: String,
        startDate: LocalDate?, endDate: LocalDate?
    ): List<UnifiedActivity> {
        val now = LocalDate.now()
        return listOf(
            UnifiedActivity(externalId = "ws-001", type = ActivityType.BUY, symbol = "XEQT.TO",
                description = "Buy 300 XEQT.TO", quantity = BigDecimal(300), price = BigDecimal("25.50"),
                amount = BigDecimal("-7650.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(30), settlementDate = now.minusDays(28), optionType = null),
            UnifiedActivity(externalId = "ws-002", type = ActivityType.DIVIDEND, symbol = "VEQT.TO",
                description = "Dividend VEQT.TO", quantity = null, price = null,
                amount = BigDecimal("32.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(14), settlementDate = null, optionType = null),
            UnifiedActivity(externalId = "ws-003", type = ActivityType.TRANSFER_IN, symbol = null,
                description = "EFT Deposit", quantity = null, price = null,
                amount = BigDecimal("2000.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(7), settlementDate = null, optionType = null)
        )
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val now = OffsetDateTime.now()
        return listOf(
            UnifiedOrder(brokerOrderId = "ws-ord-001", symbol = "XEQT.TO", action = OrderAction.BUY,
                orderType = OrderType.MARKET, timeInForce = TimeInForce.DAY,
                totalQuantity = BigDecimal(300), filledQuantity = BigDecimal(300),
                executionPrice = BigDecimal("25.50"), limitPrice = null,
                stopPrice = null, status = OrderStatus.FILLED, currency = "CAD",
                submittedAt = now.minusDays(30), filledAt = now.minusDays(30)),
            UnifiedOrder(brokerOrderId = "ws-ord-002", symbol = "BAM.TO", action = OrderAction.BUY,
                orderType = OrderType.LIMIT, timeInForce = TimeInForce.GTC,
                totalQuantity = BigDecimal(50), filledQuantity = null,
                executionPrice = null, limitPrice = BigDecimal("45.00"),
                stopPrice = null, status = OrderStatus.SUBMITTED, currency = "CAD",
                submittedAt = now.minusDays(1), filledAt = null)
        )
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        val orderId = orderIdCounter.incrementAndGet()
        log.info("FakeWealthsimpleAdapter: placed {} {} order for {} {} @ {}",
            request.orderType, request.action, request.quantity, request.symbol, request.limitPrice ?: "MKT")
        return OrderResult(brokerOrderId = orderId.toString(), status = OrderStatus.SUBMITTED, message = "Fake order submitted")
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        log.info("FakeWealthsimpleAdapter: cancelled order {}", brokerOrderId)
        return CancelResult(success = true, message = "Fake order cancelled")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.WEALTHSIMPLE, supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT),
            supportsOptionPositions = false, supportsFractionalShares = false,
            supportsRealTimeData = false, supportsHistoricalActivities = true,
            activityHistoryDepth = "Full history via activity feed", orderRateLimit = "7 trades/hour",
            isOfficialApi = false, notes = "FakeWealthsimpleAdapter — dev/test mock. Unofficial API, may break."
        )
    }
}
