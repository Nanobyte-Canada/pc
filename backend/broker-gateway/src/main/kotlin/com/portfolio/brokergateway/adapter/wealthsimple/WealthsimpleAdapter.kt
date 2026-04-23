// adapter/wealthsimple/WealthsimpleAdapter.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
@ConditionalOnProperty(prefix = "broker-gateway.wealthsimple", name = ["enabled"], havingValue = "true")
class WealthsimpleAdapter(
    private val config: WealthsimpleConfig
) : BrokerAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val graphQlClient = WealthsimpleGraphQlClient(config)
    private val tokenManager = WealthsimpleTokenManager(config)
    private val rateLimiter = WealthsimpleRateLimiter(config.orderRateLimitPerHour)

    override val brokerType = BrokerType.WEALTHSIMPLE

    override fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult {
        return try {
            val creds = credentials as BrokerCredentials.WealthsimpleCredentials
            graphQlClient.execute(creds.accessToken, "FetchAllAccounts",
                "query FetchAllAccounts { identity { accounts { edges { node { id unifiedAccountType } } } } }")
            ConnectionValidationResult(connected = true, message = "Connected to Wealthsimple")
        } catch (e: Exception) {
            ConnectionValidationResult(connected = false, message = e.message, needsReauth = true)
        }
    }

    override fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        return tokenManager.refreshTokens(creds)
    }

    override fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount> {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchAllAccounts { identity { accounts { edges { node { id unifiedAccountNumber unifiedAccountType nickname status currency } } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchAllAccounts", query)
        val edges = data.at("/identity/accounts/edges") ?: return emptyList()
        return edges.mapNotNull { edge ->
            val node = edge.get("node") ?: return@mapNotNull null
            UnifiedAccount(
                accountId = node.get("id")?.asText() ?: return@mapNotNull null,
                accountNumber = node.get("unifiedAccountNumber")?.asText(),
                accountName = node.get("nickname")?.asText(),
                accountType = WealthsimpleDtoMappers.mapAccountType(node.get("unifiedAccountType")?.asText()),
                currency = node.get("currency")?.asText(),
                brokerType = BrokerType.WEALTHSIMPLE,
                status = node.get("status")?.asText()
            )
        }
    }

    override fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchAccountFinancials(${'$'}accountId: String!) { account(id: ${'$'}accountId) { financials { currentBalance { amount currency } netLiquidationValue { amount currency } availableToTrade { amount currency } buyingPower { amount currency } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchAccountFinancials", query, mapOf("accountId" to accountId))
        val financials = data.at("/account/financials")

        return UnifiedBalance(
            accountId = accountId,
            totalEquity = financials?.at("/netLiquidationValue/amount")?.decimalValue(),
            totalValue = financials?.at("/currentBalance/amount")?.decimalValue(),
            cashBalances = listOfNotNull(
                financials?.at("/availableToTrade/amount")?.decimalValue()?.let {
                    CashBalance(financials.at("/availableToTrade/currency")?.asText() ?: "CAD", it)
                }
            ),
            buyingPower = financials?.at("/buyingPower/amount")?.decimalValue(),
            currency = financials?.at("/currentBalance/currency")?.asText() ?: "CAD"
        )
    }

    override fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition> {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchIdentityPositions(${'$'}accountId: String!) { identity { positions(accountId: ${'$'}accountId) { edges { node { id quantity book_value { amount currency } market_value { amount currency } stock { symbol name security_type } quote { amount } } } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchIdentityPositions", query, mapOf("accountId" to accountId))
        val edges = data.at("/identity/positions/edges") ?: return emptyList()

        return edges.mapNotNull { edge ->
            val node = edge.get("node") ?: return@mapNotNull null
            val quantity = node.get("quantity")?.decimalValue() ?: return@mapNotNull null
            val bookValue = node.at("/book_value/amount")?.decimalValue()
            val marketValue = node.at("/market_value/amount")?.decimalValue()
            val avgCost = if (bookValue != null && quantity > BigDecimal.ZERO) bookValue.divide(quantity, 6, java.math.RoundingMode.HALF_UP) else null
            val pnl = if (marketValue != null && bookValue != null) marketValue.subtract(bookValue) else null

            UnifiedPosition(
                symbol = node.at("/stock/symbol")?.asText() ?: return@mapNotNull null,
                symbolId = node.get("id")?.asText(),
                securityName = node.at("/stock/name")?.asText(),
                instrumentType = WealthsimpleDtoMappers.mapInstrumentType(node.at("/stock/security_type")?.asText()),
                quantity = quantity,
                averageCost = avgCost,
                currentPrice = node.at("/quote/amount")?.decimalValue(),
                currentValue = marketValue,
                totalPnl = pnl,
                totalPnlPercent = null,
                currency = node.at("/market_value/currency")?.asText() ?: "CAD"
            )
        }
    }

    override fun getActivities(
        credentials: BrokerCredentials, accountId: String,
        startDate: LocalDate?, endDate: LocalDate?
    ): List<UnifiedActivity> {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchActivityFeedItems(${'$'}accountIds: [String!], ${'$'}limit: Int) { activities(accountIds: ${'$'}accountIds, first: ${'$'}limit) { edges { node { canonicalId type occurredAt amount { amount currency } securitySymbol description quantity price { amount } fee { amount } } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchActivityFeedItems", query,
            mapOf("accountIds" to listOf(accountId), "limit" to 99))
        val edges = data.at("/activities/edges") ?: return emptyList()

        return edges.mapNotNull { edge ->
            val node = edge.get("node") ?: return@mapNotNull null
            UnifiedActivity(
                externalId = node.get("canonicalId")?.asText(),
                type = WealthsimpleDtoMappers.mapActivityType(node.get("type")?.asText()),
                symbol = node.get("securitySymbol")?.asText(),
                description = node.get("description")?.asText(),
                quantity = node.get("quantity")?.decimalValue(),
                price = node.at("/price/amount")?.decimalValue(),
                amount = node.at("/amount/amount")?.decimalValue() ?: BigDecimal.ZERO,
                fee = node.at("/fee/amount")?.decimalValue(),
                currency = node.at("/amount/currency")?.asText() ?: "CAD",
                tradeDate = node.get("occurredAt")?.asText()?.let { LocalDate.parse(it.substring(0, 10)) } ?: LocalDate.now(),
                settlementDate = null,
                optionType = null
            )
        }
    }

    override fun getOrders(
        credentials: BrokerCredentials, accountId: String, status: OrderStatusFilter?
    ): List<UnifiedOrder> {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val query = """query FetchActivityFeedItems(${'$'}accountIds: [String!], ${'$'}types: [String!]) { activities(accountIds: ${'$'}accountIds, types: ${'$'}types) { edges { node { canonicalId securitySymbol type status quantity createdAt updatedAt amount { amount } limitPrice { amount } fillQuantity fillPrice { amount } } } } }"""
        val data = graphQlClient.execute(creds.accessToken, "FetchActivityFeedItems", query,
            mapOf("accountIds" to listOf(accountId), "types" to listOf("buy", "sell")))
        val edges = data.at("/activities/edges") ?: return emptyList()

        return edges.mapNotNull { edge ->
            val node = edge.get("node") ?: return@mapNotNull null
            val limitPrice = node.at("/limitPrice/amount")?.decimalValue()
            UnifiedOrder(
                brokerOrderId = node.get("canonicalId")?.asText() ?: return@mapNotNull null,
                symbol = node.get("securitySymbol")?.asText() ?: return@mapNotNull null,
                action = if (node.get("type")?.asText() == "buy") OrderAction.BUY else OrderAction.SELL,
                orderType = if (limitPrice != null) OrderType.LIMIT else OrderType.MARKET,
                timeInForce = TimeInForce.DAY,
                totalQuantity = node.get("quantity")?.decimalValue() ?: BigDecimal.ZERO,
                filledQuantity = node.get("fillQuantity")?.decimalValue(),
                executionPrice = node.at("/fillPrice/amount")?.decimalValue(),
                limitPrice = limitPrice,
                stopPrice = null,
                status = WealthsimpleDtoMappers.mapOrderStatus(node.get("status")?.asText()),
                currency = node.at("/amount/currency")?.asText(),
                submittedAt = node.get("createdAt")?.asText()?.let { parseDateTime(it) },
                filledAt = node.get("updatedAt")?.asText()?.let { parseDateTime(it) }
            )
        }
    }

    override fun placeOrder(
        credentials: BrokerCredentials, accountId: String, request: OrderRequest
    ): OrderResult {
        rateLimiter.checkOrderAllowed()
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val mutation = """mutation SoOrdersOrderCreate(${'$'}input: OrderInput!) { createOrder(input: ${'$'}input) { order { orderId status } } }"""
        val input = mutableMapOf<String, Any?>(
            "accountId" to accountId,
            "securityId" to request.symbol,
            "quantity" to request.quantity,
            "orderType" to if (request.action == OrderAction.BUY) "buy_quantity" else "sell_quantity",
            "timeInForce" to if (request.timeInForce == TimeInForce.GTC) "until_cancel" else "day"
        )
        if (request.limitPrice != null) {
            input["limitPrice"] = mapOf("amount" to request.limitPrice, "currency" to (request.currency ?: "CAD"))
        }
        val data = graphQlClient.execute(creds.accessToken, "SoOrdersOrderCreate", mutation, mapOf("input" to input))
        val order = data.at("/createOrder/order")
        rateLimiter.recordOrder()
        return OrderResult(
            brokerOrderId = order?.get("orderId")?.asText(),
            status = WealthsimpleDtoMappers.mapOrderStatus(order?.get("status")?.asText())
        )
    }

    override fun cancelOrder(
        credentials: BrokerCredentials, accountId: String, brokerOrderId: String
    ): CancelResult {
        val creds = credentials as BrokerCredentials.WealthsimpleCredentials
        val mutation = """mutation SoOrdersOrderCancel(${'$'}orderId: String!) { cancelOrder(orderId: ${'$'}orderId) { order { orderId status } } }"""
        graphQlClient.execute(creds.accessToken, "SoOrdersOrderCancel", mutation, mapOf("orderId" to brokerOrderId))
        return CancelResult(success = true, message = "Cancel request sent")
    }

    override fun capabilities(): BrokerCapabilities {
        return BrokerCapabilities(
            brokerType = BrokerType.WEALTHSIMPLE, supportsOrders = true,
            supportedOrderTypes = listOf(OrderType.MARKET, OrderType.LIMIT),
            supportsOptionPositions = false, supportsFractionalShares = false,
            supportsRealTimeData = false, supportsHistoricalActivities = true,
            activityHistoryDepth = "Full history via activity feed", orderRateLimit = "7 trades/hour",
            isOfficialApi = false, notes = "Unofficial API — may break without notice. TOS concerns."
        )
    }

    private fun parseDateTime(iso: String): OffsetDateTime? {
        return try { OffsetDateTime.parse(iso) } catch (_: Exception) { null }
    }
}
