package com.portfolio.marketdata.ibkr

import java.math.BigDecimal
import java.time.LocalDate

interface IbkrClient {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    /** Register a callback invoked after reconnection. Implementations must store and invoke handlers
     *  on reconnect (e.g. in the `nextValidId` TWS callback after the initial connection). */
    fun registerReconnectHandler(handler: Runnable) {}
    /** Register a callback for data farm error (2108) notifications. Currently, error 2108 is treated
     *  as informational (log-only) and handlers are not invoked — the infrastructure is retained for
     *  future use when active farm management may be needed. */
    fun registerDataFarmErrorHandler(handler: Runnable) {}
    fun requestMarketData(conId: Int, callback: (tickType: Int, value: Double) -> Unit)
    fun cancelMarketData(conId: Int)
    fun requestOptionChain(underlying: String): List<OptionContractDetails>
    fun requestContractDetails(
        symbol: String,
        secType: String,
        expiry: LocalDate? = null,
        strike: BigDecimal? = null,
        right: String? = null
    ): List<OptionContractDetails>
    fun requestMarketDataSnapshot(conId: Int): MarketDataSnapshot?
    fun requestOptionExpirations(underlying: String): List<LocalDate>
}

data class OptionContractDetails(
    val conId: Int,
    val symbol: String,
    val secType: String,
    val exchange: String,
    val expiry: LocalDate?,
    val strike: BigDecimal?,
    val right: String?,
    val tradingClass: String? = null,
    val multiplier: String? = null
)

data class OptionChainParams(
    val exchange: String,
    val underlyingConId: Int,
    val tradingClass: String?,
    val multiplier: String?,
    val expirations: Set<LocalDate>
)

data class MarketDataSnapshot(
    val conId: Int,
    val bid: Double? = null,
    val ask: Double? = null,
    val last: Double? = null,
    val volume: Long? = null,
    val impliedVol: Double? = null,
    val delta: Double? = null,
    val gamma: Double? = null,
    val theta: Double? = null,
    val vega: Double? = null
)
