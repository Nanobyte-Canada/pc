package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.InstrumentType
import java.math.BigDecimal
import java.time.LocalDate

data class UnifiedPosition(
    val symbol: String,
    val symbolId: String?,
    val securityName: String?,
    val instrumentType: InstrumentType,
    val quantity: BigDecimal,
    val averageCost: BigDecimal?,
    val currentPrice: BigDecimal?,
    val currentValue: BigDecimal?,
    val totalPnl: BigDecimal?,
    val totalPnlPercent: BigDecimal?,
    val currency: String,
    val strikePrice: BigDecimal? = null,
    val expirationDate: LocalDate? = null,
    val optionType: String? = null,
    val underlyingSymbol: String? = null
)
