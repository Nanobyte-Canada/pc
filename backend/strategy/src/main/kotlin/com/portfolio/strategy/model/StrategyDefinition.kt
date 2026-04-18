package com.portfolio.strategy.model

import com.portfolio.common.domain.OptionType

data class StrategyDefinition(
    val type: StrategyType,
    val displayName: String,
    val description: String,
    val outlook: String,
    val riskProfile: String,
    val legCount: Int,
    val legTemplates: List<LegTemplate>
)

data class LegTemplate(
    val action: LegAction,
    val optionType: OptionType?,
    val strikeOffset: StrikeOffset
)

enum class StrikeOffset {
    ATM, OTM_1, OTM_2, ITM_1, STOCK
}
