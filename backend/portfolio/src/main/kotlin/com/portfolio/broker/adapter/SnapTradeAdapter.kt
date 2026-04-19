package com.portfolio.broker.adapter

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Abstraction over the SnapTrade Java SDK.
 *
 * All raw SDK types are mapped to our own DTOs in the implementation,
 * so that SDK signature changes are isolated to a single file.
 */
interface SnapTradeAdapter {

    fun registerUser(snapUserId: String): String

    fun getLoginRedirectUrl(
        userId: String,
        userSecret: String,
        redirectUri: String,
        broker: String?,
        reconnectAuthId: String?,
        connectionType: String? = null
    ): String

    fun listAccounts(userId: String, userSecret: String): List<SnapTradeAccountDto>

    fun listConnections(userId: String, userSecret: String): List<SnapTradeConnectionDto>

    fun getPositions(userId: String, userSecret: String, accountId: String): List<SnapTradePositionDto>

    fun getOptionPositions(userId: String, userSecret: String, accountId: String): List<SnapTradeOptionPositionDto>

    fun getBalances(userId: String, userSecret: String, accountId: String): List<SnapTradeBalanceDto>

    fun getHoldings(userId: String, userSecret: String, accountId: String): SnapTradeHoldingsDto

    fun getActivities(
        userId: String,
        userSecret: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
        accounts: String?,
        type: String?
    ): List<SnapTradeActivityDto>

    fun getAccountActivities(
        userId: String,
        userSecret: String,
        accountId: String,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        offset: Int = 0,
        limit: Int = 1000,
        type: String? = null
    ): PaginatedActivitiesResult

    fun disconnectBrokerage(userId: String, userSecret: String, authorizationId: String)

    fun listBrokerages(): List<SnapTradeBrokerageDto>

    fun listBrokerageAuthorizationTypes(brokerageSlug: String? = null): List<SnapTradeBrokerageAuthTypeDto>

    fun placeOrder(
        userId: String,
        userSecret: String,
        accountId: String,
        action: String,
        symbol: String,
        units: BigDecimal,
        orderType: String,
        limitPrice: BigDecimal?,
        timeInForce: String
    ): SnapTradeOrderDto

    fun cancelOrder(userId: String, userSecret: String, accountId: String, brokerOrderId: String)

    fun listAccountOrders(userId: String, userSecret: String, accountId: String): List<SnapTradeAccountOrderDto>

    fun checkApiStatus(): SnapTradeApiStatusDto
}
