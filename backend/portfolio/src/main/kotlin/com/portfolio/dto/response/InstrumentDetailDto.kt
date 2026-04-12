package com.portfolio.dto.response

data class InstrumentDetailDto(
    val id: Long,
    val ticker: String,
    val name: String,
    val instrumentType: String,
    val isin: String?,
    val currency: String?,
    val country: String?,
    // Parsed EODHD sections — nullable per instrument type
    val general: Map<String, Any?>? = null,
    val highlights: Map<String, Any?>? = null,
    val valuation: Map<String, Any?>? = null,
    val technicals: Map<String, Any?>? = null,
    val financials: Map<String, Any?>? = null,
    val earnings: Map<String, Any?>? = null,
    val splitsDividends: Map<String, Any?>? = null,
    val sharesStats: Map<String, Any?>? = null,
    val analystRatings: Map<String, Any?>? = null,
    val etfData: Map<String, Any?>? = null,
    val mutualFundData: Map<String, Any?>? = null
)
