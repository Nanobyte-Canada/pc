package com.portfolio.strategy.engine

import com.portfolio.strategy.model.Leg
import org.springframework.stereotype.Component

@Component
class LegValidator {

    fun validate(legs: List<Leg>): ValidationResult {
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

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }
}

data class ValidationResult(val valid: Boolean, val errors: List<String>)
