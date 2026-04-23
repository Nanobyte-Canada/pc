package com.portfolio.brokergateway.adapter.ibkr

import java.math.BigDecimal
import java.time.OffsetDateTime

interface IbkrAccountClient {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    fun getManagedAccounts(): List<String>
    fun getAccountSummary(accountId: String): Map<String, String>
    fun getPositions(): List<IbkrPosition>
    fun getOpenOrders(): List<IbkrOrder>
    fun getCompletedOrders(): List<IbkrOrder>
    fun getExecutions(accountId: String): List<IbkrExecution>
    fun placeOrder(accountId: String, contract: IbkrContract, order: IbkrOrderSpec): Int
    fun cancelOrder(orderId: Int)
}

data class IbkrPosition(
    val accountId: String,
    val symbol: String,
    val secType: String,
    val exchange: String,
    val currency: String,
    val conId: Int,
    val quantity: BigDecimal,
    val averageCost: BigDecimal,
    val marketPrice: BigDecimal? = null,
    val marketValue: BigDecimal? = null,
    val unrealizedPnl: BigDecimal? = null,
    val strike: BigDecimal? = null,
    val expiry: String? = null,
    val right: String? = null
)

data class IbkrOrder(
    val orderId: Int,
    val symbol: String,
    val secType: String,
    val action: String,
    val orderType: String,
    val totalQuantity: BigDecimal,
    val filledQuantity: BigDecimal? = null,
    val limitPrice: BigDecimal? = null,
    val auxPrice: BigDecimal? = null,
    val status: String,
    val timeInForce: String? = null,
    val avgFillPrice: BigDecimal? = null,
    val currency: String? = null,
    val submittedAt: OffsetDateTime? = null,
    val filledAt: OffsetDateTime? = null
)

data class IbkrExecution(
    val execId: String,
    val symbol: String,
    val secType: String,
    val side: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val commission: BigDecimal? = null,
    val currency: String,
    val time: OffsetDateTime,
    val accountId: String
)

data class IbkrContract(
    val symbol: String,
    val secType: String = "STK",
    val exchange: String = "SMART",
    val currency: String = "USD"
)

data class IbkrOrderSpec(
    val action: String,
    val orderType: String,
    val totalQuantity: BigDecimal,
    val limitPrice: BigDecimal? = null,
    val auxPrice: BigDecimal? = null,
    val timeInForce: String = "DAY"
)
