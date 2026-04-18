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
