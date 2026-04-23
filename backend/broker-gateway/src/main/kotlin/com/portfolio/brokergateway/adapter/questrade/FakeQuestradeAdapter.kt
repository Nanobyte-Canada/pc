package com.portfolio.brokergateway.adapter.questrade

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
class FakeQuestradeAdapter : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val orderIdCounter = AtomicInteger(20000)

    override val brokerType = BrokerType.QUESTRADE

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        log.info("FakeQuestradeAdapter: validating connection")
        return ConnectionValidationResult(connected = true, message = "Fake Questrade connection OK")
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials {
        val qt = credentials as BrokerCredentials.QuestradeCredentials
        return qt.copy(
            accessToken = "fake-refreshed-token",
            refreshToken = "fake-new-refresh-token",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 1800
        )
    }

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        return listOf(
            UnifiedAccount(accountId = "51443483", accountNumber = "51443483",
                accountName = "Margin", accountType = AccountType.MARGIN,
                currency = "CAD", brokerType = BrokerType.QUESTRADE, status = "ACTIVE"),
            UnifiedAccount(accountId = "51443484", accountNumber = "51443484",
                accountName = "TFSA", accountType = AccountType.TFSA,
                currency = "CAD", brokerType = BrokerType.QUESTRADE, status = "ACTIVE"),
            UnifiedAccount(accountId = "51443485", accountNumber = "51443485",
                accountName = "RRSP", accountType = AccountType.RRSP,
                currency = "CAD", brokerType = BrokerType.QUESTRADE, status = "ACTIVE")
        )
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        return UnifiedBalance(
            accountId = accountId,
            totalEquity = BigDecimal("85000.00"),
            totalValue = BigDecimal("72000.00"),
            cashBalances = listOf(
                CashBalance(currency = "CAD", amount = BigDecimal("13000.00")),
                CashBalance(currency = "USD", amount = BigDecimal("2500.00"))
            ),
            buyingPower = BigDecimal("45000.00"),
            currency = "CAD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        data class MockPos(val sym: String, val name: String, val qty: Int, val cost: Double, val price: Double, val type: InstrumentType)
        val positions = listOf(
            MockPos("XIU.TO", "iShares S&P/TSX 60 ETF", 200, 32.50, 34.80, InstrumentType.ETF),
            MockPos("VFV.TO", "Vanguard S&P 500 ETF", 150, 95.00, 102.50, InstrumentType.ETF),
            MockPos("RY.TO", "Royal Bank of Canada", 80, 130.00, 142.75, InstrumentType.STOCK),
            MockPos("TD.TO", "Toronto-Dominion Bank", 100, 85.00, 78.50, InstrumentType.STOCK)
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
            UnifiedActivity(externalId = null, type = ActivityType.BUY, symbol = "RY.TO",
                description = "Buy 80 RY.TO", quantity = BigDecimal(80), price = BigDecimal("130.00"),
                amount = BigDecimal("-10400.00"), fee = BigDecimal("-4.95"), currency = "CAD",
                tradeDate = now.minusDays(20), settlementDate = now.minusDays(18), optionType = null),
            UnifiedActivity(externalId = null, type = ActivityType.DIVIDEND, symbol = "XIU.TO",
                description = "Dividend XIU.TO", quantity = null, price = null,
                amount = BigDecimal("48.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(10), settlementDate = now.minusDays(8), optionType = null),
            UnifiedActivity(externalId = null, type = ActivityType.TRANSFER_IN, symbol = null,
                description = "EFT Deposit", quantity = null, price = null,
                amount = BigDecimal("5000.00"), fee = BigDecimal.ZERO, currency = "CAD",
                tradeDate = now.minusDays(25), settlementDate = now.minusDays(25), optionType = null)
        )
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val now = OffsetDateTime.now()
        return listOf(
            UnifiedOrder(brokerOrderId = "173577239", symbol = "VFV.TO", action = OrderAction.BUY,
                orderType = OrderType.LIMIT, timeInForce = TimeInForce.DAY,
                totalQuantity = BigDecimal(50), filledQuantity = BigDecimal(50),
                executionPrice = BigDecimal("95.00"), limitPrice = BigDecimal("96.00"),
                stopPrice = null, status = OrderStatus.FILLED, currency = "CAD",
                submittedAt = now.minusDays(30), filledAt = now.minusDays(30)),
            UnifiedOrder(brokerOrderId = "173577240", symbol = "ENB.TO", action = OrderAction.BUY,
                orderType = OrderType.LIMIT, timeInForce = TimeInForce.GTC,
                totalQuantity = BigDecimal(100), filledQuantity = null,
                executionPrice = null, limitPrice = BigDecimal("48.00"),
                stopPrice = null, status = OrderStatus.SUBMITTED, currency = "CAD",
                submittedAt = now.minusDays(2), filledAt = null)
        )
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        val orderId = orderIdCounter.incrementAndGet()
        log.info("FakeQuestradeAdapter: placed {} {} order for {} {} @ {}",
            request.orderType, request.action, request.quantity, request.symbol, request.limitPrice ?: "MKT")
        return OrderResult(brokerOrderId = orderId.toString(), status = OrderStatus.SUBMITTED, message = "Fake order submitted")
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        log.info("FakeQuestradeAdapter: cancelled order {}", brokerOrderId)
        return CancelResult(success = true, message = "Fake order cancelled")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.QUESTRADE, supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT),
            supportsOptionPositions = true, supportsFractionalShares = false,
            supportsRealTimeData = true, supportsHistoricalActivities = true,
            activityHistoryDepth = "Unlimited", orderRateLimit = "~1 req/sec",
            isOfficialApi = true, notes = "FakeQuestradeAdapter — dev/test mock. Order placement requires partner app registration."
        )
    }
}
