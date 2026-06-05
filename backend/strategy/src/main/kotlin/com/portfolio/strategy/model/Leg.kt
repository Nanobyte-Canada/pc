package com.portfolio.strategy.model

import com.portfolio.common.domain.OptionType
import java.math.BigDecimal
import java.time.LocalDate

data class Leg(
    val action: LegAction,
    val optionType: OptionType?,
    val strike: BigDecimal,
    val expiry: LocalDate?,
    val quantity: Int = 1,
    val bid: BigDecimal = BigDecimal.ZERO,
    val ask: BigDecimal = BigDecimal.ZERO,
    val mid: BigDecimal = BigDecimal.ZERO,
    val delta: BigDecimal = BigDecimal.ZERO
)

enum class LegAction {
    BUY,
    SELL
}
