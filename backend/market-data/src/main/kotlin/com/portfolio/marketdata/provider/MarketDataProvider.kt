package com.portfolio.marketdata.provider

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Provider-neutral market data facade. Implementation: QuestradeProvider.
 * NOTE: `conId` parameters/fields carry the provider's opaque instrument id
 * (Questrade numeric symbolId after migration). Names kept for compatibility
 * with ContractResolver serialization and the contract_cache table.
 */
interface MarketDataProvider {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    /** Invoked after the underlying transport reconnects; subscribers re-register. */
    fun registerReconnectHandler(handler: Runnable) {}
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
    /** Batched snapshot fetch used by chain building. Missing ids absent from map. */
    fun requestOptionSnapshots(conIds: List<Int>): Map<Int, MarketDataSnapshot>
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
