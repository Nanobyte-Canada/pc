package com.portfolio.strategy.engine

import com.portfolio.common.domain.OptionType
import com.portfolio.strategy.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class StrategyCalculatorTest {

    private lateinit var calculator: StrategyCalculator

    @BeforeEach
    fun setUp() {
        calculator = StrategyCalculator()
    }

    // --- Net Debit/Credit Tests ---

    @Test
    fun `calculate bull call spread net debit`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1,
                bid = BigDecimal("3"), ask = BigDecimal("3.20"), mid = BigDecimal("3.10")),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1,
                bid = BigDecimal("1"), ask = BigDecimal("1.10"), mid = BigDecimal("1.05"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))

        // Net debit = -3.10 + 1.05 = -2.05
        assertEquals(0, BigDecimal("-2.05").compareTo(result.netDebitCredit))
    }

    @Test
    fun `calculate iron condor net credit`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1,
                bid = BigDecimal("1"), ask = BigDecimal("1.10"), mid = BigDecimal("1.05")),
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("60"), expiry, 1,
                bid = BigDecimal("0.30"), ask = BigDecimal("0.40"), mid = BigDecimal("0.35")),
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("45"), expiry, 1,
                bid = BigDecimal("0.80"), ask = BigDecimal("0.90"), mid = BigDecimal("0.85")),
            Leg(LegAction.BUY, OptionType.PUT, BigDecimal("40"), expiry, 1,
                bid = BigDecimal("0.20"), ask = BigDecimal("0.30"), mid = BigDecimal("0.25"))
        )
        val result = calculator.calculate(legs, BigDecimal("50"))

        // Net credit = 1.05 - 0.35 + 0.85 - 0.25 = 1.30
        assertEquals(0, BigDecimal("1.30").compareTo(result.netDebitCredit))
    }

    @Test
    fun `calculate bear put spread net debit`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.PUT, BigDecimal("50"), expiry, 1,
                mid = BigDecimal("3")),
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("45"), expiry, 1,
                mid = BigDecimal("1"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))

        // Net debit = -3 + 1 = -2
        assertEquals(0, BigDecimal("-2.00").compareTo(result.netDebitCredit))
    }

    // --- P&L Curve Tests ---

    @Test
    fun `PnL curve has correct number of points`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        assertEquals(100, result.pnlCurve.size)
    }

    @Test
    fun `PnL curve prices span correct range around spot`() {
        val spotPrice = BigDecimal("100")
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("100"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("5"))
        )
        val result = calculator.calculate(legs, spotPrice)

        // Range should be spot * (1 - 0.20) to spot * (1 + 0.20) = 80 to 120
        val firstPrice = result.pnlCurve.first().underlyingPrice
        val lastPrice = result.pnlCurve.last().underlyingPrice
        assertEquals(0, BigDecimal("80.00").compareTo(firstPrice))
        assertEquals(0, BigDecimal("120.00").compareTo(lastPrice))
    }

    @Test
    fun `PnL prices are strictly increasing`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        for (i in 1 until result.pnlCurve.size) {
            assertTrue(
                result.pnlCurve[i].underlyingPrice > result.pnlCurve[i - 1].underlyingPrice,
                "Price at index $i should be greater than at ${i - 1}"
            )
        }
    }

    @Test
    fun `max profit is non-negative for credit spread`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("45"), expiry, 1,
                mid = BigDecimal("2")),
            Leg(LegAction.BUY, OptionType.PUT, BigDecimal("40"), expiry, 1,
                mid = BigDecimal("0.50"))
        )
        val result = calculator.calculate(legs, BigDecimal("50"))
        assertTrue(result.maxProfit >= BigDecimal.ZERO)
    }

    @Test
    fun `max loss is non-negative (absolute value)`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1,
                mid = BigDecimal("2")),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1,
                mid = BigDecimal("0.50"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        assertTrue(result.maxLoss >= BigDecimal.ZERO)
    }

    // --- Break-Even Tests ---

    @Test
    fun `break even prices are computed for vertical spread`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1,
                mid = BigDecimal("2")),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1,
                mid = BigDecimal("0.50"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        // Net debit = -2 + 0.50 = -1.50
        // Break-even should be around 51.50 (50 + 1.50)
        assertTrue(result.breakEvenPrices.isNotEmpty())
        // Break-even for a bull call spread = lower strike + net debit = 50 + 1.50 = 51.50
        assertTrue(result.breakEvenPrices.any { it == BigDecimal("51.50") })
    }

    @Test
    fun `single long call has break even`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        // Break-even for long call = strike + premium = 50 + 3 = 53
        assertTrue(result.breakEvenPrices.isNotEmpty())
    }

    // --- Dollar Value Tests ---

    @Test
    fun `dollar value fields are computed correctly`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 2,
                mid = BigDecimal("3")),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 2,
                mid = BigDecimal("1"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        // netDebitCredit = -3 + 1 = -2
        // quantity = 2 (first leg), contractMultiplier = 100
        // netDebitCreditDollars = -2 * 100 * 2 = -400
        assertEquals(0, BigDecimal("-400").compareTo(result.netDebitCreditDollars))
    }

    @Test
    fun `max profit dollars computed correctly`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("45"), expiry, 1,
                mid = BigDecimal("2")),
            Leg(LegAction.BUY, OptionType.PUT, BigDecimal("40"), expiry, 1,
                mid = BigDecimal("0.50"))
        )
        val result = calculator.calculate(legs, BigDecimal("50"))
        // Max profit for bull put spread = net credit * 100 * quantity = 1.50 * 100 * 1 = 150
        assertEquals(0, BigDecimal("150").compareTo(result.maxProfitDollars))
    }

    @Test
    fun `max loss dollars computed correctly`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("45"), expiry, 1,
                mid = BigDecimal("2")),
            Leg(LegAction.BUY, OptionType.PUT, BigDecimal("40"), expiry, 1,
                mid = BigDecimal("0.50"))
        )
        val result = calculator.calculate(legs, BigDecimal("50"))
        // Max loss for bull put spread = (spread width - net credit) * 100 * quantity = (5 - 1.50) * 100 = 350
        assertEquals(0, BigDecimal("350").compareTo(result.maxLossDollars))
    }

    @Test
    fun `dollar values scale with quantity`() {
        val expiry = LocalDate.now().plusDays(30)
        val legsQty1 = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1, mid = BigDecimal("3")),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1, mid = BigDecimal("1"))
        )
        val legsQty3 = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 3, mid = BigDecimal("3")),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 3, mid = BigDecimal("1"))
        )
        val result1 = calculator.calculate(legsQty1, BigDecimal("52"))
        val result3 = calculator.calculate(legsQty3, BigDecimal("52"))

        // netDebitCredit should be the same regardless of quantity
        assertEquals(0, result1.netDebitCredit.compareTo(result3.netDebitCredit))
        // Dollar values should scale by 3x
        assertEquals(0, result1.netDebitCreditDollars.multiply(BigDecimal("3")).compareTo(result3.netDebitCreditDollars))
    }

    // --- Greeks Tests ---

    @Test
    fun `net delta is computed for bull call spread`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), expiry, 1,
                delta = BigDecimal("0.6")),
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("55"), expiry, 1,
                delta = BigDecimal("0.3"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        // Net delta = 0.6 - 0.3 = 0.30
        assertEquals(0, BigDecimal("0.30").compareTo(result.netGreeks.delta))
    }

    @Test
    fun `net delta for bear put spread`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.PUT, BigDecimal("50"), expiry, 1,
                delta = BigDecimal("-0.6")),
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("45"), expiry, 1,
                delta = BigDecimal("-0.3"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        // Net delta = -0.6 - (-0.3) = -0.6 + 0.3 = -0.30
        assertEquals(0, BigDecimal("-0.30").compareTo(result.netGreeks.delta))
    }

    @Test
    fun `gamma is non-zero for option legs`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                delta = BigDecimal("0.6"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        assertTrue(result.netGreeks.gamma > BigDecimal.ZERO)
    }

    @Test
    fun `gamma is zero when spot price is zero`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                delta = BigDecimal("0.6"))
        )
        val result = calculator.calculate(legs, BigDecimal("0"))
        assertEquals(0, BigDecimal("0").compareTo(result.netGreeks.gamma))
    }

    @Test
    fun `theta and vega are zero (not yet implemented)`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                delta = BigDecimal("0.6"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        assertEquals(0, BigDecimal("0").compareTo(result.netGreeks.theta))
        assertEquals(0, BigDecimal("0").compareTo(result.netGreeks.vega))
    }

    @Test
    fun `stock leg delta is scaled by quantity over 100`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.BUY, null, BigDecimal("50"), expiry, 100,
                delta = BigDecimal("1"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        // Stock delta for 100 shares = 1 * (100/100) = 1.00
        assertEquals(0, BigDecimal("1.00").compareTo(result.netGreeks.delta))
    }

    // --- Risk/Reward Tests ---

    @Test
    fun `risk reward ratio is computed`() {
        val expiry = LocalDate.now().plusDays(30)
        val legs = listOf(
            Leg(LegAction.SELL, OptionType.PUT, BigDecimal("45"), expiry, 1,
                mid = BigDecimal("2")),
            Leg(LegAction.BUY, OptionType.PUT, BigDecimal("40"), expiry, 1,
                mid = BigDecimal("0.50"))
        )
        val result = calculator.calculate(legs, BigDecimal("50"))
        assertTrue(result.riskRewardRatio > BigDecimal.ZERO)
    }

    @Test
    fun `risk reward is zero when max loss is zero`() {
        // Single leg deep ITM call where max loss = 0 is hard to trigger
        // but test the division logic: if maxLoss is 0, ratio should be 0
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("0"))
        )
        val result = calculator.calculate(legs, BigDecimal("100"))
        // With zero premium, maxProfit = huge, maxLoss = 0 (can't lose on free option)
        assertEquals(0, BigDecimal("0").compareTo(result.riskRewardRatio))
    }

    // --- Edge Cases ---

    @Test
    fun `single leg calculation works`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        assertNotNull(result.pnlCurve)
        assertEquals(100, result.pnlCurve.size)
        assertTrue(result.maxProfit >= BigDecimal.ZERO)
        assertTrue(result.maxLoss >= BigDecimal.ZERO)
    }

    @Test
    fun `strategy type is null in calculation result`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        assertNull(result.strategyType)
    }

    @Test
    fun `probability of profit is null`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3"))
        )
        val result = calculator.calculate(legs, BigDecimal("52"))
        assertNull(result.probabilityOfProfit)
    }

    @Test
    fun `at the money call PnL is negative at low prices`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3"))
        )
        val result = calculator.calculate(legs, BigDecimal("50"))
        // At prices well below strike, PnL should be negative (paid premium, option worthless)
        val lowPricePnl = result.pnlCurve.first().pnl
        assertTrue(lowPricePnl < BigDecimal.ZERO)
    }

    @Test
    fun `at the money call PnL is positive at high prices`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("50"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3"))
        )
        val result = calculator.calculate(legs, BigDecimal("50"))
        // At prices well above strike, PnL should be positive
        val highPricePnl = result.pnlCurve.last().pnl
        assertTrue(highPricePnl > BigDecimal.ZERO)
    }
}
