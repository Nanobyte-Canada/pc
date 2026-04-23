package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger

@Component
@Profile("dev", "local", "test")
class FakeIbkrAdapter : BrokerAdapter {

    override val brokerType = BrokerType.IBKR

    private val orderIdSequence = AtomicInteger(10001)

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult =
        ConnectionValidationResult(connected = true, message = "Fake IBKR connection active")

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials = credentials

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> = listOf(
        UnifiedAccount(
            accountId = "DU1234567",
            accountNumber = "DU1234567",
            accountName = "Paper Margin Account",
            accountType = AccountType.MARGIN,
            currency = "USD",
            brokerType = BrokerType.IBKR,
            status = "Active"
        ),
        UnifiedAccount(
            accountId = "DU7654321",
            accountNumber = "DU7654321",
            accountName = "RRSP Account",
            accountType = AccountType.RRSP,
            currency = "CAD",
            brokerType = BrokerType.IBKR,
            status = "Active"
        )
    )

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance =
        UnifiedBalance(
            accountId = accountId,
            totalEquity = BigDecimal("250000.00"),
            totalValue = BigDecimal("220000.00"),
            cashBalances = listOf(
                CashBalance(currency = "USD", amount = BigDecimal("30000.00")),
                CashBalance(currency = "CAD", amount = BigDecimal("5000.00"))
            ),
            buyingPower = BigDecimal("150000.00"),
            currency = "USD"
        )

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        data class Pos(val sym: String, val name: String, val qty: Int, val avg: String, val cur: String)
        val positions = listOf(
            Pos("SPY", "SPDR S&P 500 ETF Trust", 100, "430.50", "450.25"),
            Pos("AAPL", "Apple Inc.", 50, "170.00", "185.50"),
            Pos("MSFT", "Microsoft Corporation", 30, "380.00", "420.75"),
            Pos("QQQ", "Invesco QQQ Trust", 75, "360.00", "382.10"),
            Pos("NVDA", "NVIDIA Corporation", 20, "750.00", "880.00")
        )
        return positions.map { p ->
            val avgCost = BigDecimal(p.avg)
            val curPrice = BigDecimal(p.cur)
            val qty = BigDecimal(p.qty)
            val totalCost = avgCost * qty
            val currentValue = curPrice * qty
            val pnl = currentValue - totalCost
            val pnlPercent = if (totalCost.signum() != 0)
                pnl.divide(totalCost, 4, RoundingMode.HALF_UP) * BigDecimal("100")
            else BigDecimal.ZERO
            UnifiedPosition(
                symbol = p.sym,
                symbolId = null,
                securityName = p.name,
                instrumentType = InstrumentType.STOCK,
                quantity = qty,
                averageCost = avgCost,
                currentPrice = curPrice,
                currentValue = currentValue,
                totalPnl = pnl,
                totalPnlPercent = pnlPercent,
                currency = "USD"
            )
        }
    }

    override fun getActivities(
        credentials: BrokerCredentials,
        accountId: String,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): List<UnifiedActivity> {
        val today = LocalDate.now()
        return listOf(
            UnifiedActivity(
                externalId = "EXEC-001",
                type = ActivityType.BUY,
                symbol = "AAPL",
                description = "Bought 50 shares of AAPL",
                quantity = BigDecimal("50"),
                price = BigDecimal("170.00"),
                amount = BigDecimal("-8500.00"),
                fee = BigDecimal("-1.00"),
                currency = "USD",
                tradeDate = today.minusDays(10),
                settlementDate = today.minusDays(8),
                optionType = null
            ),
            UnifiedActivity(
                externalId = "DIV-001",
                type = ActivityType.DIVIDEND,
                symbol = "SPY",
                description = "Cash dividend",
                quantity = null,
                price = null,
                amount = BigDecimal("165.25"),
                fee = null,
                currency = "USD",
                tradeDate = today.minusDays(5),
                settlementDate = today.minusDays(3),
                optionType = null
            ),
            UnifiedActivity(
                externalId = "EXEC-002",
                type = ActivityType.SELL,
                symbol = "TSLA",
                description = "Sold 25 shares of TSLA",
                quantity = BigDecimal("25"),
                price = BigDecimal("250.00"),
                amount = BigDecimal("6250.00"),
                fee = BigDecimal("-1.00"),
                currency = "USD",
                tradeDate = today.minusDays(3),
                settlementDate = today.minusDays(1),
                optionType = null
            ),
            UnifiedActivity(
                externalId = "TRANSFER-001",
                type = ActivityType.TRANSFER_IN,
                symbol = null,
                description = "Wire transfer deposit",
                quantity = null,
                price = null,
                amount = BigDecimal("10000.00"),
                fee = null,
                currency = "USD",
                tradeDate = today.minusDays(15),
                settlementDate = today.minusDays(15),
                optionType = null
            )
        )
    }

    override fun getOrders(
        credentials: BrokerCredentials,
        accountId: String,
        status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val now = OffsetDateTime.now()
        return listOf(
            UnifiedOrder(
                brokerOrderId = "10001",
                symbol = "AAPL",
                action = OrderAction.BUY,
                orderType = OrderType.MARKET,
                timeInForce = TimeInForce.DAY,
                totalQuantity = BigDecimal("50"),
                filledQuantity = BigDecimal("50"),
                executionPrice = BigDecimal("170.00"),
                limitPrice = null,
                stopPrice = null,
                status = OrderStatus.FILLED,
                currency = "USD",
                submittedAt = now.minusDays(10),
                filledAt = now.minusDays(10)
            ),
            UnifiedOrder(
                brokerOrderId = "10002",
                symbol = "GOOG",
                action = OrderAction.BUY,
                orderType = OrderType.LIMIT,
                timeInForce = TimeInForce.GTC,
                totalQuantity = BigDecimal("10"),
                filledQuantity = BigDecimal.ZERO,
                executionPrice = null,
                limitPrice = BigDecimal("150.00"),
                stopPrice = null,
                status = OrderStatus.SUBMITTED,
                currency = "USD",
                submittedAt = now.minusDays(2),
                filledAt = null
            )
        )
    }

    override fun placeOrder(
        credentials: BrokerCredentials,
        accountId: String,
        request: OrderRequest
    ): OrderResult {
        val orderId = orderIdSequence.getAndIncrement()
        return OrderResult(
            brokerOrderId = orderId.toString(),
            status = OrderStatus.SUBMITTED,
            message = "Order submitted for ${request.action} ${request.quantity} ${request.symbol}"
        )
    }

    override fun cancelOrder(
        credentials: BrokerCredentials,
        accountId: String,
        brokerOrderId: String
    ): CancelResult = CancelResult(success = true, message = "Order $brokerOrderId cancelled")

    override fun capabilities(): BrokerCapabilities = BrokerCapabilities(
        brokerType = BrokerType.IBKR,
        supportsOrders = true,
        supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT),
        supportsOptionPositions = true,
        supportsFractionalShares = false,
        supportsRealTimeData = true,
        supportsHistoricalActivities = true,
        activityHistoryDepth = "1 year via Flex Queries",
        orderRateLimit = "50/sec",
        isOfficialApi = true,
        notes = "Fake adapter for dev/test"
    )
}
