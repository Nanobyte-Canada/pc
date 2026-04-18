package com.portfolio.marketdata.api.dto

import java.math.BigDecimal
import java.time.LocalDate

data class IvRankResponse(
    val ticker: String,
    val currentIv: BigDecimal,
    val ivRank: BigDecimal,
    val ivPercentile: BigDecimal,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val observationCount: Int
)
