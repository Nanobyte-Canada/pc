package com.portfolio.strategy.engine

import com.portfolio.strategy.model.EducationContent
import com.portfolio.strategy.model.Leg
import com.portfolio.strategy.model.LegAction
import com.portfolio.strategy.model.StrategyType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class EducationEngine(private val registry: StrategyRegistry) {

    fun getContent(type: StrategyType): EducationContent = registry.getEducation(type)

    fun generateWarnings(legs: List<Leg>, spotPrice: BigDecimal): List<String> {
        val warnings = mutableListOf<String>()
        if (legs.isEmpty()) return warnings

        val optionLegs = legs.filter { it.optionType != null }

        val shortestDte = optionLegs.mapNotNull { it.expiry?.let { e -> ChronoUnit.DAYS.between(LocalDate.now(), e) } }.minOrNull()
        if (shortestDte != null && shortestDte < 7) {
            warnings.add("Short DTE ($shortestDte days) — higher gamma risk and faster time decay")
        }

        if (optionLegs.size >= 2) {
            val strikes = optionLegs.map { it.strike }.sorted()
            if (strikes.size >= 2) {
                val spreadWidth = strikes.last() - strikes.first()
                val widthPercent = spreadWidth.divide(spotPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                if (widthPercent > BigDecimal("10")) {
                    warnings.add("Wide spread (\$${spreadWidth.setScale(2, RoundingMode.HALF_UP)} width) — lower capital efficiency")
                }
            }
        }

        optionLegs.forEach { leg ->
            val moneyness = when (leg.optionType) {
                com.portfolio.common.domain.OptionType.CALL -> if (spotPrice > leg.strike) (spotPrice - leg.strike).divide(spotPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100")) else BigDecimal.ZERO
                com.portfolio.common.domain.OptionType.PUT -> if (spotPrice < leg.strike) (leg.strike - spotPrice).divide(spotPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100")) else BigDecimal.ZERO
                else -> BigDecimal.ZERO
            }
            if (moneyness > BigDecimal("10")) {
                warnings.add("Deep ITM leg (${leg.optionType} at \$${leg.strike.setScale(2, RoundingMode.HALF_UP)}) — consider if intentional")
            }
        }

        val netDelta = legs.sumOf { leg ->
            val m = when (leg.action) { LegAction.BUY -> BigDecimal.ONE; LegAction.SELL -> BigDecimal.ONE.negate() }
            leg.delta * m
        }.abs()
        if (netDelta < BigDecimal("0.10") && optionLegs.isNotEmpty()) {
            warnings.add("Delta-neutral strategy — profits mainly from time decay and volatility changes")
        }

        val longestDte = optionLegs.mapNotNull { it.expiry?.let { e -> ChronoUnit.DAYS.between(LocalDate.now(), e) } }.maxOrNull()
        if (longestDte != null && longestDte > 90) {
            warnings.add("Long DTE ($longestDte days) — slower theta decay, more exposure to volatility changes")
        }

        return warnings
    }
}
