package com.portfolio.strategy.engine

import com.portfolio.common.domain.OptionType
import com.portfolio.strategy.model.*
import org.springframework.stereotype.Component

@Component
class StrategyRegistry {

    private val strategies: Map<StrategyType, StrategyDefinition>
    private val educationContent: Map<StrategyType, EducationContent>

    init {
        strategies = mapOf(
            StrategyType.BULL_CALL_SPREAD to StrategyDefinition(
                type = StrategyType.BULL_CALL_SPREAD, displayName = "Bull Call Spread",
                description = "Buy lower strike call and sell higher strike call with same expiration",
                outlook = "Moderately Bullish", riskProfile = "Limited risk, limited profit", legCount = 2,
                legTemplates = listOf(
                    LegTemplate(LegAction.BUY, OptionType.CALL, StrikeOffset.ATM),
                    LegTemplate(LegAction.SELL, OptionType.CALL, StrikeOffset.OTM_1)
                )
            ),
            StrategyType.BEAR_PUT_SPREAD to StrategyDefinition(
                type = StrategyType.BEAR_PUT_SPREAD, displayName = "Bear Put Spread",
                description = "Buy higher strike put and sell lower strike put with same expiration",
                outlook = "Moderately Bearish", riskProfile = "Limited risk, limited profit", legCount = 2,
                legTemplates = listOf(
                    LegTemplate(LegAction.BUY, OptionType.PUT, StrikeOffset.ATM),
                    LegTemplate(LegAction.SELL, OptionType.PUT, StrikeOffset.OTM_1)
                )
            ),
            StrategyType.BULL_PUT_SPREAD to StrategyDefinition(
                type = StrategyType.BULL_PUT_SPREAD, displayName = "Bull Put Spread",
                description = "Sell higher strike put and buy lower strike put with same expiration",
                outlook = "Moderately Bullish (Income Strategy)", riskProfile = "Limited risk, limited profit (credit received)", legCount = 2,
                legTemplates = listOf(
                    LegTemplate(LegAction.SELL, OptionType.PUT, StrikeOffset.ATM),
                    LegTemplate(LegAction.BUY, OptionType.PUT, StrikeOffset.OTM_1)
                )
            ),
            StrategyType.BEAR_CALL_SPREAD to StrategyDefinition(
                type = StrategyType.BEAR_CALL_SPREAD, displayName = "Bear Call Spread",
                description = "Sell lower strike call and buy higher strike call with same expiration",
                outlook = "Moderately Bearish (Income Strategy)", riskProfile = "Limited risk, limited profit (credit received)", legCount = 2,
                legTemplates = listOf(
                    LegTemplate(LegAction.SELL, OptionType.CALL, StrikeOffset.ATM),
                    LegTemplate(LegAction.BUY, OptionType.CALL, StrikeOffset.OTM_1)
                )
            ),
            StrategyType.IRON_CONDOR to StrategyDefinition(
                type = StrategyType.IRON_CONDOR, displayName = "Iron Condor",
                description = "Combination of bear call spread and bull put spread",
                outlook = "Neutral (Range-Bound)", riskProfile = "Limited risk, limited profit (credit received)", legCount = 4,
                legTemplates = listOf(
                    LegTemplate(LegAction.SELL, OptionType.CALL, StrikeOffset.OTM_1),
                    LegTemplate(LegAction.BUY, OptionType.CALL, StrikeOffset.OTM_2),
                    LegTemplate(LegAction.SELL, OptionType.PUT, StrikeOffset.OTM_1),
                    LegTemplate(LegAction.BUY, OptionType.PUT, StrikeOffset.OTM_2)
                )
            ),
            StrategyType.BUTTERFLY_SPREAD to StrategyDefinition(
                type = StrategyType.BUTTERFLY_SPREAD, displayName = "Butterfly Spread",
                description = "Buy 1 ATM call, sell 2 OTM calls, buy 1 further OTM call — profits from low volatility at the middle strike",
                outlook = "Neutral (Range-Bound)", riskProfile = "Limited risk, limited profit", legCount = 3,
                legTemplates = listOf(
                    LegTemplate(LegAction.BUY, OptionType.CALL, StrikeOffset.ATM),
                    LegTemplate(LegAction.SELL, OptionType.CALL, StrikeOffset.OTM_1, quantity = 2),
                    LegTemplate(LegAction.BUY, OptionType.CALL, StrikeOffset.OTM_2)
                )
            )
        )

        educationContent = mapOf(
            StrategyType.BULL_CALL_SPREAD to EducationContent(
                whenToUse = "Use when you expect a moderate rise in the underlying stock price.",
                riskExplanation = "Maximum loss is limited to the net debit paid. Maximum profit is capped at the difference between strikes minus the net debit.",
                keyCharacteristics = listOf("Lower cost than buying a call alone", "Profit limited to spread width minus net debit", "Break-even is lower strike plus net debit"),
                warnings = listOf("Profit potential is capped", "Time decay works against you if stock doesn't move")
            ),
            StrategyType.BEAR_PUT_SPREAD to EducationContent(
                whenToUse = "Use when you expect a moderate decline in the underlying stock price.",
                riskExplanation = "Maximum loss is limited to the net debit paid. Maximum profit is capped at the difference between strikes minus the net debit.",
                keyCharacteristics = listOf("Lower cost than buying a put alone", "Profit limited to spread width minus net debit", "Break-even is higher strike minus net debit"),
                warnings = listOf("Profit potential is capped", "Time decay works against you if stock doesn't move")
            ),
            StrategyType.BULL_PUT_SPREAD to EducationContent(
                whenToUse = "Use when you expect the stock to stay flat or rise moderately. Generates income if stock stays above the short put strike.",
                riskExplanation = "Maximum profit is the credit received. Maximum loss is the spread width minus the credit.",
                keyCharacteristics = listOf("Generates immediate income (credit)", "Time decay works in your favor", "Break-even is short strike minus credit received"),
                warnings = listOf("Requires margin equal to maximum loss", "Assignment risk on short put if stock drops")
            ),
            StrategyType.BEAR_CALL_SPREAD to EducationContent(
                whenToUse = "Use when you expect the stock to stay flat or decline moderately.",
                riskExplanation = "Maximum profit is the credit received. Maximum loss is the spread width minus the credit.",
                keyCharacteristics = listOf("Generates immediate income (credit)", "Time decay works in your favor", "Break-even is short strike plus credit received"),
                warnings = listOf("Requires margin equal to maximum loss", "Assignment risk on short call if stock rises")
            ),
            StrategyType.IRON_CONDOR to EducationContent(
                whenToUse = "Use when you expect low volatility and the stock to trade within a range.",
                riskExplanation = "Maximum profit is the net credit received. Maximum loss is the width of either spread minus the net credit.",
                keyCharacteristics = listOf("Four-legged strategy combining two credit spreads", "Profits from time decay and decreasing volatility", "Two break-even points creating a profit zone"),
                warnings = listOf("Requires significant buying power/margin", "Can lose on both sides in high volatility", "Commission costs higher due to four legs")
            ),
            StrategyType.BUTTERFLY_SPREAD to EducationContent(
                whenToUse = "Use when you expect the underlying to stay near a specific price (the middle strike) with low volatility.",
                riskExplanation = "Maximum loss is the net debit paid to enter the spread. Maximum profit is the difference between the middle and outer strikes minus the net debit.",
                keyCharacteristics = listOf(
                    "Low-cost way to profit from low volatility",
                    "Three strike prices create a profit 'tent' at the middle strike",
                    "Max profit at exactly the middle strike price",
                    "Two break-even points define the profit zone"
                ),
                warnings = listOf(
                    "Profit potential is capped at the middle strike",
                    "Time decay works against you if stock doesn't move",
                    "Narrow profit zone means precision matters"
                )
            )
        )
    }

    fun getDefinition(type: StrategyType): StrategyDefinition =
        strategies[type] ?: throw IllegalArgumentException("Unknown strategy type: $type")

    fun listAll(): List<StrategyDefinition> = strategies.values.toList()

    fun getEducation(type: StrategyType): EducationContent =
        educationContent[type] ?: throw IllegalArgumentException("No education content for: $type")
}
