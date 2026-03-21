package com.portfolio.broker.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.config.SnapTradeConfig
import com.snaptrade.client.ApiException
import com.snaptrade.client.Configuration
import com.snaptrade.client.Snaptrade
import com.snaptrade.client.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * The ONLY class that directly imports and calls the SnapTrade Java SDK.
 *
 * All SDK types are mapped to our own DTOs before leaving this class,
 * so that SDK signature changes are isolated here.
 */
@Component
class SnapTradeAdapterImpl(
    private val config: SnapTradeConfig,
    private val objectMapper: ObjectMapper
) : SnapTradeAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    private val snaptrade: Snaptrade by lazy {
        val configuration = Configuration()
        configuration.clientId = config.clientId
        configuration.consumerKey = config.consumerKey
        Snaptrade(configuration)
    }

    // ========== Authentication ==========

    override fun registerUser(snapUserId: String): String {
        return try {
            val response = snaptrade.authentication.registerSnapTradeUser(snapUserId).execute()
            response.userSecret
                ?: throw SnapTradeApiException(null, "SnapTrade registration did not return userSecret")
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    override fun getLoginRedirectUrl(
        userId: String,
        userSecret: String,
        redirectUri: String,
        broker: String?,
        reconnectAuthId: String?
    ): String {
        return try {
            val request = snaptrade.authentication.loginSnapTradeUser(userId, userSecret)
                .customRedirect(redirectUri)

            if (broker != null) request.broker(broker)
            if (reconnectAuthId != null) request.reconnect(reconnectAuthId)

            val response = request.execute()

            when (response) {
                is LoginRedirectURI -> response.redirectURI
                is Map<*, *> -> (response["redirectURI"] ?: response["redirectUri"]) as? String
                else -> null
            } ?: throw SnapTradeApiException(null, "SnapTrade login did not return redirectURI")
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    // ========== Account Information ==========

    override fun listAccounts(userId: String, userSecret: String): List<SnapTradeAccountDto> {
        return try {
            snaptrade.accountInformation.listUserAccounts(userId, userSecret).execute()
                .map { it.toDto() }
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    override fun listConnections(userId: String, userSecret: String): List<SnapTradeConnectionDto> {
        return try {
            snaptrade.connections.listBrokerageAuthorizations(userId, userSecret).execute()
                .map { it.toDto() }
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    override fun getPositions(userId: String, userSecret: String, accountId: String): List<SnapTradePositionDto> {
        return try {
            snaptrade.accountInformation.getUserAccountPositions(
                userId, userSecret, UUID.fromString(accountId)
            ).execute().map { it.toDto() }
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    override fun getOptionPositions(userId: String, userSecret: String, accountId: String): List<SnapTradeOptionPositionDto> {
        return try {
            snaptrade.options.listOptionHoldings(
                userId, userSecret, UUID.fromString(accountId)
            ).execute().map { it.toOptionDto() }
        } catch (e: ApiException) {
            // Some brokers don't support options — return empty list instead of throwing
            log.debug("Options not available for account {}: {}", accountId, e.message)
            emptyList()
        } catch (e: Exception) {
            log.debug("Failed to fetch option positions for account {}: {}", accountId, e.message)
            emptyList()
        }
    }

    override fun getBalances(userId: String, userSecret: String, accountId: String): List<SnapTradeBalanceDto> {
        return try {
            snaptrade.accountInformation.getUserAccountBalance(
                userId, userSecret, UUID.fromString(accountId)
            ).execute().map { it.toDto() }
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    override fun getHoldings(userId: String, userSecret: String, accountId: String): SnapTradeHoldingsDto {
        return try {
            val response = snaptrade.accountInformation.getUserHoldings(
                UUID.fromString(accountId), userId, userSecret
            ).execute()

            val totalValue = response.totalValue?.value
            val totalValueCurrency = response.totalValue?.currency
            val positions = response.positions?.map { it.toDto() } ?: emptyList()
            val balances = response.balances?.map { it.toDto() } ?: emptyList()

            SnapTradeHoldingsDto(totalValue, totalValueCurrency, positions, balances)
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    // ========== Activities ==========

    override fun getActivities(
        userId: String,
        userSecret: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
        accounts: String?,
        type: String?
    ): List<SnapTradeActivityDto> {
        return try {
            val request = snaptrade.transactionsAndReporting.getActivities(userId, userSecret)
            startDate?.let { request.startDate(it) }
            endDate?.let { request.endDate(it) }
            accounts?.let { request.accounts(it) }
            type?.let { request.type(it) }
            request.execute().map { it.toDto() }
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    // ========== Connections ==========

    override fun disconnectBrokerage(userId: String, userSecret: String, authorizationId: String) {
        try {
            snaptrade.connections.removeBrokerageAuthorization(
                UUID.fromString(authorizationId), userId, userSecret
            ).execute()
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    // ========== Reference Data ==========

    override fun listBrokerages(): List<SnapTradeBrokerageDto> {
        return try {
            snaptrade.referenceData.listAllBrokerages().execute().map { it.toDto() }
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    // ========== Trading ==========

    override fun placeOrder(
        userId: String,
        userSecret: String,
        accountId: String,
        action: String,
        symbol: String,
        units: BigDecimal,
        orderType: String,
        limitPrice: BigDecimal?,
        timeInForce: String
    ): SnapTradeOrderDto {
        val snapAction = when (action.uppercase()) {
            "BUY" -> ActionStrictWithOptions.BUY
            "SELL" -> ActionStrictWithOptions.SELL
            else -> throw IllegalArgumentException("Invalid action: $action")
        }

        val snapOrderType = when (orderType.uppercase()) {
            "MARKET" -> OrderTypeStrict.MARKET
            "LIMIT" -> OrderTypeStrict.LIMIT
            else -> throw IllegalArgumentException("Invalid order type: $orderType")
        }

        val snapTimeInForce = when (timeInForce.uppercase()) {
            "DAY" -> TimeInForceStrict.DAY
            "GTC" -> TimeInForceStrict.GTC
            else -> throw IllegalArgumentException("Invalid time in force: $timeInForce")
        }

        return try {
            val response = snaptrade.trading.placeForceOrder(
                UUID.fromString(accountId),
                snapAction,
                snapOrderType,
                snapTimeInForce,
                userId,
                userSecret
            ).symbol(symbol)
             .units(units.toDouble())
             .apply {
                 if (snapOrderType == OrderTypeStrict.LIMIT && limitPrice != null) {
                     price(limitPrice.toDouble())
                 }
             }.execute()

            SnapTradeOrderDto(
                brokerageOrderId = response.brokerageOrderId,
                status = response.status?.toString(),
                symbol = symbol,
                action = action,
                units = units.toDouble(),
                price = limitPrice?.toDouble()
            )
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    override fun cancelOrder(userId: String, userSecret: String, accountId: String, brokerOrderId: String) {
        try {
            snaptrade.trading.cancelUserAccountOrder(
                userId,
                userSecret,
                accountId,
                UUID.fromString(brokerOrderId)
            ).execute()
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    // ========== API Status ==========

    override fun checkApiStatus(): SnapTradeApiStatusDto {
        return try {
            val result = snaptrade.apiStatus.check().execute()
            when (result) {
                is Map<*, *> -> SnapTradeApiStatusDto(
                    online = result["online"] as? Boolean ?: false,
                    version = result["version"] as? String
                )
                else -> SnapTradeApiStatusDto(online = false, version = null)
            }
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
    }

    // ========== SDK → DTO Mappers ==========

    private fun Account.toDto(): SnapTradeAccountDto {
        // Extract meta fields — meta may be a Map or typed object
        val metaMap: Map<String, Any?>? = try {
            when (val m = meta) {
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") (m as Map<String, Any?>)
                null -> null
                else -> {
                    val json = objectMapper.writeValueAsString(m)
                    objectMapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
                }
            }
        } catch (e: Exception) {
            log.debug("Could not parse account meta: {}", e.message)
            null
        }

        val metaType = metaMap?.get("type")?.toString()
        val metaAccountNumber = metaMap?.get("accountNumberActual")?.toString()

        // Extract balance info
        val balanceAmount: Double? = try { balance?.total?.amount } catch (_: Exception) { null }
        val balanceCurr: String? = try { balance?.total?.currency } catch (_: Exception) { null }

        return SnapTradeAccountDto(
            id = id,
            brokerageAuthorization = brokerageAuthorization,
            number = number,
            name = name,
            institutionName = institutionName,
            currency = balanceCurr,
            metaType = metaType,
            metaAccountNumber = metaAccountNumber,
            rawType = rawType,
            balance = balanceAmount,
            balanceCurrency = balanceCurr
        )
    }

    private fun BrokerageAuthorization.toDto() = SnapTradeConnectionDto(
        id = id,
        disabled = disabled,
        brokerageName = brokerage?.name,
        brokerLogoUrl = brokerage?.awsS3LogoUrl,
        type = type
    )

    private fun Position.toDto(): SnapTradePositionDto {
        val universalSymbol = symbol?.symbol
        return SnapTradePositionDto(
            symbol = universalSymbol?.symbol,
            symbolId = symbol?.id?.toString(),
            symbolDescription = universalSymbol?.description,
            symbolTypeCode = universalSymbol?.type?.code,
            currencyCode = currency?.code ?: universalSymbol?.currency?.code,
            units = units,
            price = price,
            averagePurchasePrice = averagePurchasePrice
        )
    }

    private fun Balance.toDto() = SnapTradeBalanceDto(
        currency = currency?.code,
        cash = cash,
        buyingPower = buyingPower
    )

    private fun UniversalActivity.toDto(): SnapTradeActivityDto {
        val tradeDateLocal = tradeDate?.let {
            try { LocalDate.parse(it.toString().take(10)) } catch (e: Exception) { null }
        }
        val settlementDateLocal = settlementDate?.let {
            try { LocalDate.parse(it.toString().take(10)) } catch (e: Exception) { null }
        }
        val rawJson = try { objectMapper.writeValueAsString(this) } catch (e: Exception) { null }

        return SnapTradeActivityDto(
            id = id,
            type = type,
            symbol = symbol?.symbol,
            description = description,
            units = units,
            price = price,
            amount = amount,
            fee = fee,
            currency = currency?.code,
            tradeDate = tradeDateLocal,
            settlementDate = settlementDateLocal,
            optionType = optionType,
            rawJson = rawJson
        )
    }

    private fun OptionsPosition.toOptionDto(): SnapTradeOptionPositionDto {
        val optionSymbol = symbol
        val underlying = optionSymbol?.optionSymbol?.underlyingSymbol?.symbol
        val strikeVal = optionSymbol?.optionSymbol?.strikePrice
        val expiryVal = optionSymbol?.optionSymbol?.expirationDate?.let {
            try { LocalDate.parse(it.toString().take(10)) } catch (e: Exception) { null }
        }
        val optType = optionSymbol?.optionSymbol?.optionType?.toString()

        return SnapTradeOptionPositionDto(
            symbol = optionSymbol?.description ?: optionSymbol?.optionSymbol?.ticker,
            strikePrice = strikeVal,
            expirationDate = expiryVal,
            optionType = optType?.uppercase(),
            underlyingSymbol = underlying,
            units = units,
            price = price,
            averagePurchasePrice = averagePurchasePrice,
            currencyCode = currency?.code
        )
    }

    private fun Brokerage.toDto() = SnapTradeBrokerageDto(
        name = name,
        slug = slug,
        logoUrl = awsS3LogoUrl,
        description = description
    )

    // ========== Error Mapping ==========

    private fun mapApiException(e: ApiException): SnapTradeApiException {
        val errorCode = SnapTradeApiException.extractErrorCode(e.message)
        return SnapTradeApiException(
            errorCode = errorCode,
            message = e.message,
            cause = e
        )
    }
}
