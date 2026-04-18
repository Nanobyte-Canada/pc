package com.portfolio.common.domain

import java.math.BigDecimal
import java.time.LocalDate

data class StrikeData(
    val call: OptionQuote?,
    val put: OptionQuote?
)

data class OptionsChain(
    val underlying: String,
    val spotPrice: BigDecimal,
    val expirations: Map<LocalDate, Map<BigDecimal, StrikeData>>
)
