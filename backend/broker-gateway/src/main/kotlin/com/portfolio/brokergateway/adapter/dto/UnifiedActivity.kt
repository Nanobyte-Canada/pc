package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.ActivityType
import java.math.BigDecimal
import java.time.LocalDate

data class UnifiedActivity(
    val externalId: String?,
    val type: ActivityType,
    val symbol: String?,
    val description: String?,
    val quantity: BigDecimal?,
    val price: BigDecimal?,
    val amount: BigDecimal,
    val fee: BigDecimal?,
    val currency: String,
    val tradeDate: LocalDate,
    val settlementDate: LocalDate?,
    val optionType: String?
)
