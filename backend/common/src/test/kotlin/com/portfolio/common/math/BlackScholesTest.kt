package com.portfolio.common.math

import com.portfolio.common.domain.OptionType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.math.abs

class BlackScholesTest {

    @Test
    fun `price - ATM call option`() {
        val price = BlackScholes.price(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.CALL
        )
        assertThat(price.toDouble()).isCloseTo(10.45, Offset.offset(0.01))
    }

    @Test
    fun `price - ATM put option`() {
        val price = BlackScholes.price(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.PUT
        )
        assertThat(price.toDouble()).isCloseTo(5.57, Offset.offset(0.01))
    }

    @Test
    fun `price - ITM call option`() {
        val price = BlackScholes.price(
            spot = BigDecimal("110.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.CALL
        )
        assertThat(price.toDouble()).isGreaterThan(10.45)
    }

    @Test
    fun `price - OTM put option`() {
        val price = BlackScholes.price(
            spot = BigDecimal("110.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.PUT
        )
        assertThat(price.toDouble()).isLessThan(5.57)
    }

    @Test
    fun `delta - ATM call is between 0 and 1`() {
        val delta = BlackScholes.delta(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.CALL
        )
        assertThat(delta.toDouble()).isBetween(0.0, 1.0)
        assertThat(delta.toDouble()).isCloseTo(0.6368, Offset.offset(0.01))
    }

    @Test
    fun `delta - ATM put is between -1 and 0`() {
        val delta = BlackScholes.delta(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.PUT
        )
        assertThat(delta.toDouble()).isBetween(-1.0, 0.0)
        assertThat(delta.toDouble()).isCloseTo(-0.3632, Offset.offset(0.01))
    }

    @Test
    fun `delta - deep ITM call approaches 1`() {
        val delta = BlackScholes.delta(
            spot = BigDecimal("150.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.CALL
        )
        assertThat(delta.toDouble()).isGreaterThan(0.95)
    }

    @Test
    fun `delta - deep OTM call approaches 0`() {
        val delta = BlackScholes.delta(
            spot = BigDecimal("50.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.CALL
        )
        assertThat(delta.toDouble()).isLessThan(0.05)
    }

    @Test
    fun `gamma - is always positive`() {
        val gamma = BlackScholes.gamma(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20
        )
        assertThat(gamma.toDouble()).isGreaterThan(0.0)
    }

    @Test
    fun `gamma - ATM has highest gamma`() {
        val gammaATM = BlackScholes.gamma(BigDecimal("100.00"), BigDecimal("100.00"), 1.0, 0.05, 0.20)
        val gammaITM = BlackScholes.gamma(BigDecimal("120.00"), BigDecimal("100.00"), 1.0, 0.05, 0.20)
        val gammaOTM = BlackScholes.gamma(BigDecimal("80.00"), BigDecimal("100.00"), 1.0, 0.05, 0.20)

        assertThat(gammaATM.toDouble()).isGreaterThan(gammaITM.toDouble())
        assertThat(gammaATM.toDouble()).isGreaterThan(gammaOTM.toDouble())
    }

    @Test
    fun `theta - call theta is typically negative`() {
        val theta = BlackScholes.theta(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.CALL
        )
        assertThat(theta.toDouble()).isLessThan(0.0)
    }

    @Test
    fun `vega - is always positive`() {
        val vega = BlackScholes.vega(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20
        )
        assertThat(vega.toDouble()).isGreaterThan(0.0)
    }

    @Test
    fun `rho - call rho is positive`() {
        val rho = BlackScholes.rho(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.CALL
        )
        assertThat(rho.toDouble()).isGreaterThan(0.0)
    }

    @Test
    fun `rho - put rho is negative`() {
        val rho = BlackScholes.rho(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = 0.20,
            optionType = OptionType.PUT
        )
        assertThat(rho.toDouble()).isLessThan(0.0)
    }

    @Test
    fun `impliedVolatility - solver converges to original volatility`() {
        val originalVol = 0.20
        val marketPrice = BlackScholes.price(
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            volatility = originalVol,
            optionType = OptionType.CALL
        )

        val impliedVol = BlackScholes.impliedVolatility(
            marketPrice = marketPrice,
            spot = BigDecimal("100.00"),
            strike = BigDecimal("100.00"),
            timeToExpiry = 1.0,
            riskFreeRate = 0.05,
            optionType = OptionType.CALL
        )

        assertThat(impliedVol).isCloseTo(originalVol, Offset.offset(0.0001))
    }

    @Test
    fun `putCallParity - C minus P equals S minus discounted K`() {
        val spot = BigDecimal("100.00")
        val strike = BigDecimal("100.00")
        val timeToExpiry = 1.0
        val rate = 0.05
        val vol = 0.20

        val callPrice = BlackScholes.price(spot, strike, timeToExpiry, rate, vol, OptionType.CALL)
        val putPrice = BlackScholes.price(spot, strike, timeToExpiry, rate, vol, OptionType.PUT)

        val lhs = callPrice.subtract(putPrice).toDouble()
        val rhs = spot.toDouble() - strike.toDouble() * Math.exp(-rate * timeToExpiry)

        assertThat(abs(lhs - rhs)).isLessThan(0.01)
    }

    @Test
    fun `price - near expiry has lower time value`() {
        val longExpiry = BlackScholes.price(BigDecimal("100.00"), BigDecimal("100.00"), 1.0, 0.05, 0.20, OptionType.CALL)
        val shortExpiry = BlackScholes.price(BigDecimal("100.00"), BigDecimal("100.00"), 0.1, 0.05, 0.20, OptionType.CALL)
        assertThat(shortExpiry.toDouble()).isLessThan(longExpiry.toDouble())
    }

    @Test
    fun `price - higher volatility increases option value`() {
        val lowVol = BlackScholes.price(BigDecimal("100.00"), BigDecimal("100.00"), 1.0, 0.05, 0.15, OptionType.CALL)
        val highVol = BlackScholes.price(BigDecimal("100.00"), BigDecimal("100.00"), 1.0, 0.05, 0.30, OptionType.CALL)
        assertThat(highVol.toDouble()).isGreaterThan(lowVol.toDouble())
    }
}
