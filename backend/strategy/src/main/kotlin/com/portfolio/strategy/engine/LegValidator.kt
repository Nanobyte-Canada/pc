package com.portfolio.strategy.engine

import com.portfolio.common.domain.OptionType
import com.portfolio.strategy.model.Leg
import com.portfolio.strategy.model.LegAction
import com.portfolio.strategy.model.StrategyType
import org.springframework.stereotype.Component

@Component
class LegValidator(private val registry: StrategyRegistry) {

    fun validate(legs: List<Leg>, strategyType: StrategyType? = null): ValidationResult {
        val errors = mutableListOf<String>()

        if (legs.isEmpty()) {
            errors.add("At least one leg is required")
            return ValidationResult(valid = false, errors = errors)
        }

        val duplicates = legs.groupBy { leg ->
            listOf(leg.strike.toString(), leg.optionType?.toString() ?: "STOCK", leg.action.toString(), leg.expiry?.toString() ?: "NO_EXPIRY").joinToString("-")
        }.filter { it.value.size > 1 }

        if (duplicates.isNotEmpty()) {
            errors.add("Duplicate legs found: same strike, type, action, and expiry")
        }

        val optionLegs = legs.filter { it.optionType != null }
        if (optionLegs.size > 1) {
            val expiries = optionLegs.mapNotNull { it.expiry }.distinct()
            if (expiries.size > 1) {
                errors.add("All option legs must have the same expiration date")
            }
        }

        if (strategyType == StrategyType.BUTTERFLY_SPREAD) {
            val allSameType = legs.map { it.optionType }.distinct().size == 1 &&
                legs.firstOrNull()?.optionType in setOf(OptionType.CALL, OptionType.PUT)
            if (!allSameType) {
                errors.add("Butterfly Spread requires all legs to be CALL or all legs to be PUT options")
            }
            val sellLegs = legs.filter { it.action == LegAction.SELL }
            val buyLegs = legs.filter { it.action == LegAction.BUY }
            if (sellLegs.size != 1 || sellLegs.first().quantity != 2) {
                errors.add("Butterfly Spread requires exactly 1 SELL leg with quantity 2")
            }
            if (buyLegs.size != 2 || buyLegs.any { it.quantity != 1 }) {
                errors.add("Butterfly Spread requires exactly 2 BUY legs with quantity 1")
            }
        }

        if (strategyType != null) {
            val definition = registry.getDefinition(strategyType)
            if (legs.size != definition.legCount) {
                errors.add("${definition.displayName} requires exactly ${definition.legCount} legs")
            }
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }
}

data class ValidationResult(val valid: Boolean, val errors: List<String>)
