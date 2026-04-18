package com.portfolio.common.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `toCents rounds to 2 decimal places`() {
        assertThat(BigDecimal("10.4567").toCents()).isEqualTo(BigDecimal("10.46"))
    }

    @Test
    fun `toCents rounds half up`() {
        assertThat(BigDecimal("10.455").toCents()).isEqualTo(BigDecimal("10.46"))
    }

    @Test
    fun `roundTo rounds to specified scale`() {
        val value = BigDecimal("10.12345")
        assertThat(value.roundTo(3)).isEqualTo(BigDecimal("10.123"))
        assertThat(value.roundTo(1)).isEqualTo(BigDecimal("10.1"))
    }

    @Test
    fun `approxEquals returns true for values within tolerance`() {
        assertThat(BigDecimal("10.00").approxEquals(BigDecimal("10.005"), BigDecimal("0.01"))).isTrue()
    }

    @Test
    fun `approxEquals returns false for values outside tolerance`() {
        assertThat(BigDecimal("10.00").approxEquals(BigDecimal("10.02"), BigDecimal("0.01"))).isFalse()
    }

    @Test
    fun `toCurrencyString formats with dollar sign and cents`() {
        assertThat(BigDecimal("123.456").toCurrencyString()).isEqualTo("$123.46")
    }

    @Test
    fun `safeDivide returns result when divisor is non-zero`() {
        assertThat(BigDecimal("10.00").safeDivide(BigDecimal("2.00"))).isEqualTo(BigDecimal("5.0000"))
    }

    @Test
    fun `safeDivide returns default when divisor is zero`() {
        assertThat(BigDecimal("10.00").safeDivide(BigDecimal.ZERO)).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `safeDivide returns custom default when divisor is zero`() {
        val default = BigDecimal("99.00")
        assertThat(BigDecimal("10.00").safeDivide(BigDecimal.ZERO, default = default)).isEqualTo(default)
    }

    @Test
    fun `percentageChange calculates correct percentage`() {
        assertThat(BigDecimal("100.00").percentageChange(BigDecimal("110.00"))).isEqualTo(BigDecimal("10.00"))
    }

    @Test
    fun `percentageChange handles negative change`() {
        assertThat(BigDecimal("100.00").percentageChange(BigDecimal("90.00"))).isEqualTo(BigDecimal("-10.00"))
    }

    @Test
    fun `percentageChange returns zero when original is zero`() {
        assertThat(BigDecimal.ZERO.percentageChange(BigDecimal("100.00"))).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `bpsToDecimal converts basis points correctly`() {
        assertThat(100.bpsToDecimal()).isEqualTo(BigDecimal("0.010000"))
        assertThat(50.bpsToDecimal()).isEqualTo(BigDecimal("0.005000"))
    }

    @Test
    fun `isPositive returns true for positive values`() {
        assertThat(BigDecimal("10.00").isPositive()).isTrue()
    }

    @Test
    fun `isPositive returns false for zero and negative`() {
        assertThat(BigDecimal.ZERO.isPositive()).isFalse()
        assertThat(BigDecimal("-10.00").isPositive()).isFalse()
    }

    @Test
    fun `isNegative returns true for negative values`() {
        assertThat(BigDecimal("-10.00").isNegative()).isTrue()
    }

    @Test
    fun `isZero returns true for zero`() {
        assertThat(BigDecimal.ZERO.isZero()).isTrue()
        assertThat(BigDecimal("0.00").isZero()).isTrue()
    }

    @Test
    fun `isZero returns false for non-zero values`() {
        assertThat(BigDecimal("10.00").isZero()).isFalse()
    }
}
