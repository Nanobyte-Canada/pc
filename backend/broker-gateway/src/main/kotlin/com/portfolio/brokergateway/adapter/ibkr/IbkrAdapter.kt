package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import com.portfolio.brokergateway.exception.BrokerConnectionException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
@ConditionalOnProperty(prefix = "broker-gateway.ibkr", name = ["enabled"], havingValue = "true")
class IbkrAdapter(
    private val client: IbkrAccountClient,
    private val ibkrConfig: IbkrConfig
) : BrokerAdapter {

    private val logger = LoggerFactory.getLogger(IbkrAdapter::class.java)
    private val connectionManager = IbkrConnectionManager(client, ibkrConfig)

    override val brokerType = BrokerType.IBKR

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        val connected = client.isConnected()
        return ConnectionValidationResult(
            connected = connected,
            message = if (connected) "Connected to TWS/IB Gateway" else "Not connected to TWS/IB Gateway"
        )
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials = credentials

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        requireConnected()
        val accountIds = client.getManagedAccounts()
        return accountIds.map { accountId ->
            val summary = try {
                client.getAccountSummary(accountId)
            } catch (e: Exception) {
                logger.warn("Failed to get account summary for {}: {}", accountId, e.message)
                emptyMap()
            }
            UnifiedAccount(
                accountId = accountId,
                accountNumber = accountId,
                accountName = summary["AccountAlias"] ?: accountId,
                accountType = IbkrDtoMappers.mapAccountType(summary["AccountType"]),
                currency = summary["Currency"] ?: "USD",
                brokerType = BrokerType.IBKR,
                status = "Active"
            )
        }
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        requireConnected()
        val summary = client.getAccountSummary(accountId)
        val currency = summary["Currency"] ?: "USD"
        return UnifiedBalance(
            accountId = accountId,
            totalEquity = summary["NetLiquidation"]?.toBigDecimalOrNull(),
            totalValue = summary["GrossPositionValue"]?.toBigDecimalOrNull(),
            cashBalances = listOf(
                CashBalance(
                    currency = currency,
                    amount = summary["TotalCashValue"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                )
            ),
            buyingPower = summary["BuyingPower"]?.toBigDecimalOrNull(),
            currency = currency
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        requireConnected()
        return client.getPositions()
            .filter { it.accountId == accountId }
            .map { pos -> mapPosition(pos) }
    }

    override fun getActivities(
        credentials: BrokerCredentials,
        accountId: String,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): List<UnifiedActivity> {
        requireConnected()
        return client.getExecutions(accountId).map { exec ->
            UnifiedActivity(
                externalId = exec.execId,
                type = IbkrDtoMappers.mapActivityType(exec.side),
                symbol = exec.symbol,
                description = "${exec.side} ${exec.quantity} ${exec.symbol} @ ${exec.price}",
                quantity = exec.quantity,
                price = exec.price,
                amount = exec.price * exec.quantity * if (exec.side == "SLD") BigDecimal.ONE else BigDecimal("-1"),
                fee = exec.commission?.negate(),
                currency = exec.currency,
                tradeDate = exec.time.toLocalDate(),
                settlementDate = exec.time.toLocalDate().plusDays(2),
                optionType = if (exec.secType == "OPT") exec.secType else null
            )
        }
    }

    override fun getOrders(
        credentials: BrokerCredentials,
        accountId: String,
        status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        requireConnected()
        val openOrders = client.getOpenOrders()
        val completedOrders = client.getCompletedOrders()
        val allOrders = openOrders + completedOrders
        return allOrders.map { order -> mapOrder(order) }
    }

    override fun placeOrder(
        credentials: BrokerCredentials,
        accountId: String,
        request: OrderRequest
    ): OrderResult {
        requireConnected()
        val contract = IbkrContract(
            symbol = request.symbol,
            secType = "STK",
            exchange = "SMART",
            currency = request.currency ?: "USD"
        )
        val orderSpec = IbkrOrderSpec(
            action = request.action.name,
            orderType = mapOrderTypeToIbkr(request.orderType),
            totalQuantity = request.quantity,
            limitPrice = request.limitPrice,
            auxPrice = request.stopPrice,
            timeInForce = request.timeInForce.name
        )
        val orderId = client.placeOrder(accountId, contract, orderSpec)
        return OrderResult(
            brokerOrderId = orderId.toString(),
            status = OrderStatus.SUBMITTED,
            message = "Order $orderId submitted"
        )
    }

    override fun cancelOrder(
        credentials: BrokerCredentials,
        accountId: String,
        brokerOrderId: String
    ): CancelResult {
        requireConnected()
        return try {
            client.cancelOrder(brokerOrderId.toInt())
            CancelResult(success = true, message = "Cancel request sent for order $brokerOrderId")
        } catch (e: Exception) {
            logger.error("Failed to cancel order {}: {}", brokerOrderId, e.message, e)
            CancelResult(success = false, message = "Failed to cancel order: ${e.message}")
        }
    }

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
        notes = "Interactive Brokers TWS API via IbkrAccountClient"
    )

    private fun requireConnected() {
        if (!client.isConnected()) {
            throw BrokerConnectionException(
                message = "Not connected to IBKR TWS/Gateway",
                brokerType = BrokerType.IBKR
            )
        }
    }

    private fun mapPosition(pos: IbkrPosition): UnifiedPosition {
        val quantity = pos.quantity
        val avgCost = pos.averageCost
        val marketPrice = pos.marketPrice
        val marketValue = pos.marketValue ?: marketPrice?.let { it * quantity }
        val totalCost = avgCost * quantity
        val pnl = pos.unrealizedPnl ?: marketValue?.let { it - totalCost }
        val pnlPercent = if (totalCost.signum() != 0 && pnl != null)
            pnl.divide(totalCost.abs(), 4, RoundingMode.HALF_UP) * BigDecimal("100")
        else null

        return UnifiedPosition(
            symbol = pos.symbol,
            symbolId = pos.conId.toString(),
            securityName = null,
            instrumentType = IbkrDtoMappers.mapInstrumentType(pos.secType),
            quantity = quantity,
            averageCost = avgCost,
            currentPrice = marketPrice,
            currentValue = marketValue,
            totalPnl = pnl,
            totalPnlPercent = pnlPercent,
            currency = pos.currency,
            strikePrice = pos.strike,
            expirationDate = pos.expiry?.let { parseExpiry(it) },
            optionType = IbkrDtoMappers.mapOptionRight(pos.right),
            underlyingSymbol = if (pos.secType == "OPT") pos.symbol else null
        )
    }

    private fun mapOrder(order: IbkrOrder): UnifiedOrder = UnifiedOrder(
        brokerOrderId = order.orderId.toString(),
        symbol = order.symbol,
        action = if (order.action == "BUY") OrderAction.BUY else OrderAction.SELL,
        orderType = mapIbkrOrderType(order.orderType),
        timeInForce = mapIbkrTimeInForce(order.timeInForce),
        totalQuantity = order.totalQuantity,
        filledQuantity = order.filledQuantity,
        executionPrice = order.avgFillPrice,
        limitPrice = order.limitPrice,
        stopPrice = order.auxPrice,
        status = IbkrDtoMappers.mapOrderStatus(order.status),
        currency = order.currency,
        submittedAt = order.submittedAt,
        filledAt = order.filledAt
    )

    private fun mapOrderTypeToIbkr(type: OrderType): String = when (type) {
        OrderType.MARKET -> "MKT"
        OrderType.LIMIT -> "LMT"
        OrderType.STOP -> "STP"
        OrderType.STOP_LIMIT -> "STP LMT"
    }

    private fun mapIbkrOrderType(type: String): OrderType = when (type) {
        "MKT" -> OrderType.MARKET
        "LMT" -> OrderType.LIMIT
        "STP" -> OrderType.STOP
        "STP LMT" -> OrderType.STOP_LIMIT
        else -> OrderType.MARKET
    }

    private fun mapIbkrTimeInForce(tif: String?): TimeInForce = when (tif) {
        "DAY" -> TimeInForce.DAY
        "GTC" -> TimeInForce.GTC
        "IOC" -> TimeInForce.IOC
        "FOK" -> TimeInForce.FOK
        else -> TimeInForce.DAY
    }

    private fun parseExpiry(expiry: String): LocalDate? = try {
        LocalDate.parse(expiry.take(10))
    } catch (_: Exception) {
        null
    }
}
