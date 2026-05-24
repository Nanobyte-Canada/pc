package com.portfolio.broker.dto

import java.math.BigDecimal
import java.time.LocalDate

data class ExchangeRateResponse(
    val currency: String,
    val rateToCAD: BigDecimal,
    val date: LocalDate
)
