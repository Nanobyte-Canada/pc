package com.portfolio.dto.request

data class InstrumentFilterRequest(
    val instrumentType: String,
    val tickerContains: String? = null,
    val nameContains: String? = null,
    val country: String? = null,
    val exchange: String? = null,
    // Stock-specific
    val sector: String? = null,
    // ETF-specific
    val issuer: String? = null,
    val assetClass: String? = null,
    // Mutual fund-specific
    val fundCategory: String? = null,
    val fundStyle: String? = null
)
