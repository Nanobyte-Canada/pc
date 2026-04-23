package com.portfolio.brokergateway.adapter.dto

import java.math.BigDecimal

data class UnifiedBalance(
    val accountId: String,
    val totalEquity: BigDecimal?,
    val totalValue: BigDecimal?,
    val cashBalances: List<CashBalance>,
    val buyingPower: BigDecimal?,
    val currency: String
)

data class CashBalance(
    val currency: String,
    val amount: BigDecimal
)
