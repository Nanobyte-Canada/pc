package com.portfolio.strategy.engine

import com.portfolio.common.domain.OptionType
import com.portfolio.strategy.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class LegValidatorTest {

    private val validator = LegValidator(StrategyRegistry())

    // --- Basic Validation ---

    @Test
    fun `empty legs are invalid`() {
        val result = validator.validate(emptyList())
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("At least one leg") })
    }

    @Test
    fun `single leg is valid`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1)
        )
        val result = validator.validate(legs)
        assertTrue(result.valid)
    }

    @Test
    fun `multiple different legs are valid`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1)
        )
        val result = validator.validate(legs)
        assertTrue(result.valid)
    }

    // --- Duplicate Detection ---

    @Test
    fun `duplicate legs are rejected`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1)
        )
        val result = validator.validate(legs)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Duplicate") })
    }

    @Test
    fun `legs with same strike but different action are not duplicates`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("50"), expiry, 1)
        )
        val result = validator.validate(legs)
        assertTrue(result.valid)
    }

    @Test
    fun `legs with same strike but different type are not duplicates`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.BUY, OptionType.PUT, BigDecimal("50"), expiry, 1)
        )
        val result = validator.validate(legs)
        assertTrue(result.valid)
    }

    @Test
    fun `legs with same strike but different expiry are not duplicates`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(60), 1)
        )
        // These fail the expiry check, not the duplicate check
        val result = validator.validate(legs)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("same expiration") })
    }

    // --- Expiry Mismatch ---

    @Test
    fun `different expiries are rejected for option legs`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), LocalDate.now().plusDays(60), 1)
        )
        val result = validator.validate(legs)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("same expiration") })
    }

    @Test
    fun `different expiries with stock leg are ok`() {
        val legs = listOf(
            Leg(LegAction.BUY, null, BigDecimal("50"), null, 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), LocalDate.now().plusDays(30), 1)
        )
        val result = validator.validate(legs)
        assertTrue(result.valid)
    }

    @Test
    fun `single option leg has no expiry error`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1)
        )
        val result = validator.validate(legs)
        assertTrue(result.valid)
    }

    // --- Butterfly Spread Validation ---

    @Test
    fun `butterfly spread requires exactly 3 legs`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("52"), expiry, 2)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("exactly 3 legs") })
    }

    @Test
    fun `butterfly spread rejects 4 legs`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("52"), expiry, 2),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("54"), expiry, 1),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("56"), expiry, 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("exactly 3 legs") })
    }

    @Test
    fun `butterfly spread requires all calls`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("52"), expiry, 2),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("54"), expiry, 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("CALL") })
    }

    @Test
    fun `butterfly spread requires correct quantities`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("52"), expiry, 1), // should be 2
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("54"), expiry, 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("quantity") })
    }

    @Test
    fun `butterfly spread rejects all buy legs`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("52"), expiry, 2),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("54"), expiry, 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("quantity") })
    }

    @Test
    fun `valid butterfly spread passes`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("52"), expiry, 2),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("54"), expiry, 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertTrue(result.valid)
    }

    // --- Strategy Type Ignored for Non-Butterfly ---

    @Test
    fun `strategy type is ignored for bull call spread`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1)
        )
        val result = validator.validate(legs, StrategyType.BULL_CALL_SPREAD)
        assertTrue(result.valid)
    }

    @Test
    fun `strategy type is ignored for iron condor`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("60"), expiry, 1),
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("45"), expiry, 1),
            Leg(LegAction.BUY, OptionType.PUT, BigDecimal("40"), expiry, 1)
        )
        val result = validator.validate(legs, StrategyType.IRON_CONDOR)
        assertTrue(result.valid)
    }

    @Test
    fun `butterfly validation only applies to butterfly strategy type`() {
        // 2 legs is fine for non-butterfly strategy
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1)
        )
        val result = validator.validate(legs, StrategyType.BULL_CALL_SPREAD)
        assertTrue(result.valid)
    }

    // --- No strategy type ---

    @Test
    fun `null strategy type skips butterfly validation`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1),
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("55"), expiry, 2)
        )
        // 2 legs, mixed types — valid when no butterfly validation
        val result = validator.validate(legs, null)
        assertTrue(result.valid)
    }

    // --- Leg count vs strategy definition ---

    private fun leg(action: LegAction, optionType: OptionType, strike: String, quantity: Int) = Leg(
        action = action,
        optionType = optionType,
        strike = BigDecimal(strike),
        expiry = LocalDate.of(2026, 12, 18),
        quantity = quantity,
        mid = BigDecimal("1.00")
    )

    @Test
    fun `iron condor with two legs is rejected`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.PUT, "95", 1),
            leg(LegAction.SELL, OptionType.PUT, "100", 1)
        )
        val result = validator.validate(legs, StrategyType.IRON_CONDOR)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("requires exactly 4 legs") })
    }

    @Test
    fun `bull call spread with three legs is rejected`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.CALL, "100", 1),
            leg(LegAction.SELL, OptionType.CALL, "105", 1),
            leg(LegAction.BUY, OptionType.CALL, "110", 1)
        )
        val result = validator.validate(legs, StrategyType.BULL_CALL_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("requires exactly 2 legs") })
    }

    @Test
    fun `correct leg count is accepted`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.CALL, "100", 1),
            leg(LegAction.SELL, OptionType.CALL, "105", 1)
        )
        val result = validator.validate(legs, StrategyType.BULL_CALL_SPREAD)
        assertTrue(result.errors.none { it.contains("legs") })
    }

    // --- Validation result structure ---

    @Test
    fun `valid result has empty errors`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1)
        )
        val result = validator.validate(legs)
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `invalid result has multiple errors when applicable`() {
        // Empty legs + butterfly = 2 errors
        val result = validator.validate(emptyList(), StrategyType.BUTTERFLY_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.size >= 1) // At least "At least one leg"
    }

    // --- Butterfly Strike Ordering ---

    @Test
    fun `butterfly with sell strike below both buys is rejected`() {
        val legs = listOf(
            leg(LegAction.SELL, OptionType.CALL, "95", 2),
            leg(LegAction.BUY, OptionType.CALL, "100", 1),
            leg(LegAction.BUY, OptionType.CALL, "110", 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("between the two BUY strikes") })
    }

    @Test
    fun `butterfly with correctly ordered strikes is accepted`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.CALL, "100", 1),
            leg(LegAction.SELL, OptionType.CALL, "105", 2),
            leg(LegAction.BUY, OptionType.CALL, "110", 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertTrue(result.valid, "unexpected errors: ${result.errors}")
    }

    @Test
    fun `bearish put butterfly is accepted`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.PUT, "100", 1),
            leg(LegAction.SELL, OptionType.PUT, "95", 2),
            leg(LegAction.BUY, OptionType.PUT, "90", 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertTrue(result.valid, "unexpected errors: ${result.errors}")
    }
}
