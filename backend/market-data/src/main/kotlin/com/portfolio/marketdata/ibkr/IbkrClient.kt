package com.portfolio.marketdata.ibkr

import java.math.BigDecimal
import java.time.LocalDate

interface IbkrClient {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
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
}

data class OptionContractDetails(
    val conId: Int,
    val symbol: String,
    val secType: String,
    val exchange: String,
    val expiry: LocalDate?,
    val strike: BigDecimal?,
    val right: String?
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
