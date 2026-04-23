package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
@ConditionalOnProperty(prefix = "broker-gateway.questrade", name = ["enabled"], havingValue = "true")
class QuestradeAdapter(
    private val config: QuestradeConfig
) : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = QuestradeRestClient()
    private val tokenManager = QuestradeTokenManager(config)

    override val brokerType = BrokerType.QUESTRADE

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        return try {
            val creds = credentials as BrokerCredentials.QuestradeCredentials
            restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts")
            ConnectionValidationResult(connected = true, message = "Connected to Questrade")
        } catch (e: Exception) {
            ConnectionValidationResult(connected = false, message = e.message, needsReauth = true)
        }
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        return tokenManager.refreshTokens(creds)
    }

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val response = restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts")
        val accounts = response.get("accounts") ?: return emptyList()
        return accounts.map { acct ->
            UnifiedAccount(
                accountId = acct.get("number")?.asText() ?: "",
                accountNumber = acct.get("number")?.asText(),
                accountName = acct.get("type")?.asText(),
                accountType = QuestradeDtoMappers.mapAccountType(acct.get("type")?.asText()),
                currency = acct.get("currency")?.asText(),
                brokerType = BrokerType.QUESTRADE,
                status = acct.get("status")?.asText()
            )
        }
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val response = restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts/$accountId/balances")
        val combined = response.get("combinedBalances")?.firstOrNull()
        val perCurrency = response.get("perCurrencyBalances") ?: response.get("combinedBalances")
        val cashBalances = perCurrency?.map { b ->
            CashBalance(
                currency = b.get("currency")?.asText() ?: "CAD",
                amount = b.get("cash")?.decimalValue() ?: BigDecimal.ZERO
            )
        } ?: emptyList()

        return UnifiedBalance(
            accountId = accountId,
            totalEquity = combined?.get("totalEquity")?.decimalValue(),
            totalValue = combined?.get("marketValue")?.decimalValue(),
            cashBalances = cashBalances,
            buyingPower = combined?.get("buyingPower")?.decimalValue(),
            currency = combined?.get("currency")?.asText() ?: "CAD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val response = restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts/$accountId/positions")
        val positions = response.get("positions") ?: return emptyList()
        return positions.map { pos ->
            UnifiedPosition(
                symbol = pos.get("symbol")?.asText() ?: "",
                symbolId = pos.get("symbolId")?.asText(),
                securityName = null,
                instrumentType = QuestradeDtoMappers.mapInstrumentType(pos.get("symbolTypeCode")?.asText()),
                quantity = pos.get("openQuantity")?.decimalValue() ?: BigDecimal.ZERO,
                averageCost = pos.get("averageEntryPrice")?.decimalValue(),
                currentPrice = pos.get("currentPrice")?.decimalValue(),
                currentValue = pos.get("currentMarketValue")?.decimalValue(),
                totalPnl = pos.get("openPnl")?.decimalValue(),
                totalPnlPercent = null,
                currency = pos.get("currencyCode")?.asText() ?: "CAD"
            )
        }
    }

    override fun getActivities(
        credentials: BrokerCredentials, accountId: String,
        startDate: LocalDate?, endDate: LocalDate?
    ): List<UnifiedActivity> {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val start = (startDate ?: LocalDate.now().minusDays(30)).format(DateTimeFormatter.ISO_DATE)
        val end = (endDate ?: LocalDate.now()).format(DateTimeFormatter.ISO_DATE)
        val response = restClient.get(creds.apiServerUrl, creds.accessToken,
            "/v1/accounts/$accountId/activities?startTime=${start}T00:00:00-05:00&endTime=${end}T23:59:59-05:00")
        val activities = response.get("activities") ?: return emptyList()
        return activities.map { act ->
            UnifiedActivity(
                externalId = null,
                type = QuestradeDtoMappers.mapActivityType(act.get("type")?.asText(), act.get("action")?.asText()),
                symbol = act.get("symbol")?.asText(),
                description = act.get("description")?.asText(),
                quantity = act.get("quantity")?.decimalValue(),
                price = act.get("price")?.decimalValue(),
                amount = act.get("netAmount")?.decimalValue() ?: BigDecimal.ZERO,
                fee = act.get("commission")?.decimalValue(),
                currency = act.get("currency")?.asText() ?: "CAD",
                tradeDate = act.get("tradeDate")?.asText()?.let { LocalDate.parse(it.substring(0, 10)) } ?: LocalDate.now(),
                settlementDate = act.get("settlementDate")?.asText()?.let { LocalDate.parse(it.substring(0, 10)) },
                optionType = null
            )
        }
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val response = restClient.get(creds.apiServerUrl, creds.accessToken, "/v1/accounts/$accountId/orders")
        val orders = response.get("orders") ?: return emptyList()
        return orders.map { ord ->
            UnifiedOrder(
                brokerOrderId = ord.get("id")?.asText() ?: "",
                symbol = ord.get("symbol")?.asText() ?: "",
                action = QuestradeDtoMappers.mapOrderAction(ord.get("side")?.asText()),
                orderType = QuestradeDtoMappers.mapOrderType(ord.get("orderType")?.asText()),
                timeInForce = QuestradeDtoMappers.mapTimeInForce(ord.get("timeInForce")?.asText()),
                totalQuantity = ord.get("totalQuantity")?.decimalValue() ?: BigDecimal.ZERO,
                filledQuantity = ord.get("filledQuantity")?.decimalValue(),
                executionPrice = ord.get("avgExecPrice")?.decimalValue(),
                limitPrice = ord.get("limitPrice")?.decimalValue(),
                stopPrice = ord.get("stopPrice")?.decimalValue(),
                status = QuestradeDtoMappers.mapOrderStatus(ord.get("state")?.asText()),
                currency = ord.get("currency")?.asText(),
                submittedAt = ord.get("creationTime")?.asText()?.let { parseDateTime(it) },
                filledAt = ord.get("updateTime")?.asText()?.let { parseDateTime(it) }
            )
        }
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val body = mapOf(
            "symbolId" to request.symbol,
            "quantity" to request.quantity,
            "orderType" to when (request.orderType) {
                OrderType.MARKET -> "Market"; OrderType.LIMIT -> "Limit"
                OrderType.STOP -> "Stop"; OrderType.STOP_LIMIT -> "StopLimit"
            },
            "timeInForce" to when (request.timeInForce) {
                TimeInForce.DAY -> "Day"; TimeInForce.GTC -> "GoodTillCanceled"
                TimeInForce.IOC -> "ImmediateOrCancel"; TimeInForce.FOK -> "FillOrKill"
            },
            "action" to if (request.action == OrderAction.BUY) "Buy" else "Sell",
            "limitPrice" to request.limitPrice,
            "stopPrice" to request.stopPrice
        ).filterValues { it != null }

        val response = restClient.post(creds.apiServerUrl, creds.accessToken,
            "/v1/accounts/$accountId/orders", body)
        val orderId = response.get("orderId")?.asText() ?: response.get("id")?.asText()
        return OrderResult(brokerOrderId = orderId, status = OrderStatus.SUBMITTED)
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        restClient.delete(creds.apiServerUrl, creds.accessToken,
            "/v1/accounts/$accountId/orders/$brokerOrderId")
        return CancelResult(success = true, message = "Cancel request sent")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.QUESTRADE, supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT),
            supportsOptionPositions = true, supportsFractionalShares = false,
            supportsRealTimeData = true, supportsHistoricalActivities = true,
            activityHistoryDepth = "Unlimited", orderRateLimit = "~1 req/sec",
            isOfficialApi = true, notes = "Order placement may require Questrade Partner App registration"
        )
    }

    private fun parseDateTime(iso: String): OffsetDateTime? {
        return try { OffsetDateTime.parse(iso) } catch (_: Exception) { null }
    }
}
