package com.portfolio.dto.request

data class StockFilterRequest(
    val sector: String? = null,
    val country: String? = null,
    val exchange: String? = null,
    val status: String? = null,
    val tickerContains: String? = null,
    val nameContains: String? = null
)

data class EtfFilterRequest(
    val issuer: String? = null,
    val assetClass: String? = null,
    val status: String? = null,
    val symbolContains: String? = null,
    val nameContains: String? = null,
    val maxExpenseRatio: Double? = null
)

data class MutualFundFilterRequest(
    val issuer: String? = null,
    val fundType: String? = null,
    val assetClass: String? = null,
    val status: String? = null,
    val symbolContains: String? = null,
    val nameContains: String? = null,
    val maxExpenseRatio: Double? = null,
    val maxMinimumInvestment: Double? = null
)
