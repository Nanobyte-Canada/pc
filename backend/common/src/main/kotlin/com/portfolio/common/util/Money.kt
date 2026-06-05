package com.portfolio.common.util

import java.math.BigDecimal
import java.math.RoundingMode

fun BigDecimal.toCents(): BigDecimal {
    return this.setScale(2, RoundingMode.HALF_UP)
}

fun BigDecimal.roundTo(scale: Int): BigDecimal {
    return this.setScale(scale, RoundingMode.HALF_UP)
}

fun BigDecimal.approxEquals(other: BigDecimal, tolerance: BigDecimal = BigDecimal("0.01")): Boolean {
    return (this - other).abs() <= tolerance
}

fun BigDecimal.toCurrencyString(): String {
    return "$${this.toCents()}"
}

fun BigDecimal.safeDivide(divisor: BigDecimal, scale: Int = 4, default: BigDecimal = BigDecimal.ZERO): BigDecimal {
    return if (divisor.compareTo(BigDecimal.ZERO) == 0) {
        default
    } else {
        this.divide(divisor, scale, RoundingMode.HALF_UP)
    }
}

fun BigDecimal.percentageChange(newValue: BigDecimal): BigDecimal {
    if (this.compareTo(BigDecimal.ZERO) == 0) {
        return BigDecimal.ZERO
    }
    return ((newValue - this) / this * BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
}

fun Int.bpsToDecimal(): BigDecimal {
    return BigDecimal(this).divide(BigDecimal("10000"), 6, RoundingMode.HALF_UP)
}

fun BigDecimal.isPositive(): Boolean = this > BigDecimal.ZERO

fun BigDecimal.isNegative(): Boolean = this < BigDecimal.ZERO

fun BigDecimal.isZero(): Boolean = this.compareTo(BigDecimal.ZERO) == 0
