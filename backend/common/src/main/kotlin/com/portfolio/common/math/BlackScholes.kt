package com.portfolio.common.math

import com.portfolio.common.domain.OptionType
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

object BlackScholes {

    private const val SQRT_2PI = 2.506628274631000502415765284811

    fun price(
        spot: BigDecimal,
        strike: BigDecimal,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double,
        optionType: OptionType
    ): BigDecimal {
        if (timeToExpiry <= 0.0) {
            return when (optionType) {
                OptionType.CALL -> (spot - strike).max(BigDecimal.ZERO)
                OptionType.PUT -> (strike - spot).max(BigDecimal.ZERO)
            }
        }

        val S = spot.toDouble()
        val K = strike.toDouble()
        val T = timeToExpiry
        val r = riskFreeRate
        val sigma = volatility

        val d1 = d1(S, K, T, r, sigma)
        val d2 = d2(d1, sigma, T)

        val price = when (optionType) {
            OptionType.CALL -> {
                S * cumulativeNormal(d1) - K * exp(-r * T) * cumulativeNormal(d2)
            }
            OptionType.PUT -> {
                K * exp(-r * T) * cumulativeNormal(-d2) - S * cumulativeNormal(-d1)
            }
        }

        return BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP)
    }

    fun delta(
        spot: BigDecimal,
        strike: BigDecimal,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double,
        optionType: OptionType
    ): BigDecimal {
        if (timeToExpiry <= 0.0) {
            return when (optionType) {
                OptionType.CALL -> if (spot > strike) BigDecimal.ONE else BigDecimal.ZERO
                OptionType.PUT -> if (spot < strike) BigDecimal.ONE.negate() else BigDecimal.ZERO
            }
        }

        val S = spot.toDouble()
        val K = strike.toDouble()
        val T = timeToExpiry
        val r = riskFreeRate
        val sigma = volatility

        val d1 = d1(S, K, T, r, sigma)

        val delta = when (optionType) {
            OptionType.CALL -> cumulativeNormal(d1)
            OptionType.PUT -> cumulativeNormal(d1) - 1.0
        }

        return BigDecimal.valueOf(delta).setScale(6, RoundingMode.HALF_UP)
    }

    fun gamma(
        spot: BigDecimal,
        strike: BigDecimal,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double
    ): BigDecimal {
        if (timeToExpiry <= 0.0) {
            return BigDecimal.ZERO
        }

        val S = spot.toDouble()
        val K = strike.toDouble()
        val T = timeToExpiry
        val r = riskFreeRate
        val sigma = volatility

        val d1 = d1(S, K, T, r, sigma)
        val gamma = normalDensity(d1) / (S * sigma * sqrt(T))

        return BigDecimal.valueOf(gamma).setScale(6, RoundingMode.HALF_UP)
    }

    fun theta(
        spot: BigDecimal,
        strike: BigDecimal,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double,
        optionType: OptionType
    ): BigDecimal {
        if (timeToExpiry <= 0.0) {
            return BigDecimal.ZERO
        }

        val S = spot.toDouble()
        val K = strike.toDouble()
        val T = timeToExpiry
        val r = riskFreeRate
        val sigma = volatility

        val d1 = d1(S, K, T, r, sigma)
        val d2 = d2(d1, sigma, T)

        val theta = when (optionType) {
            OptionType.CALL -> {
                val term1 = -(S * normalDensity(d1) * sigma) / (2 * sqrt(T))
                val term2 = -r * K * exp(-r * T) * cumulativeNormal(d2)
                term1 + term2
            }
            OptionType.PUT -> {
                val term1 = -(S * normalDensity(d1) * sigma) / (2 * sqrt(T))
                val term2 = r * K * exp(-r * T) * cumulativeNormal(-d2)
                term1 + term2
            }
        }

        val thetaPerDay = theta / 365.0

        return BigDecimal.valueOf(thetaPerDay).setScale(6, RoundingMode.HALF_UP)
    }

    fun vega(
        spot: BigDecimal,
        strike: BigDecimal,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double
    ): BigDecimal {
        if (timeToExpiry <= 0.0) {
            return BigDecimal.ZERO
        }

        val S = spot.toDouble()
        val K = strike.toDouble()
        val T = timeToExpiry
        val r = riskFreeRate
        val sigma = volatility

        val d1 = d1(S, K, T, r, sigma)
        val vega = S * normalDensity(d1) * sqrt(T)

        val vegaPer1Pct = vega / 100.0

        return BigDecimal.valueOf(vegaPer1Pct).setScale(6, RoundingMode.HALF_UP)
    }

    fun rho(
        spot: BigDecimal,
        strike: BigDecimal,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double,
        optionType: OptionType
    ): BigDecimal {
        if (timeToExpiry <= 0.0) {
            return BigDecimal.ZERO
        }

        val S = spot.toDouble()
        val K = strike.toDouble()
        val T = timeToExpiry
        val r = riskFreeRate
        val sigma = volatility

        val d1 = d1(S, K, T, r, sigma)
        val d2 = d2(d1, sigma, T)

        val rho = when (optionType) {
            OptionType.CALL -> K * T * exp(-r * T) * cumulativeNormal(d2)
            OptionType.PUT -> -K * T * exp(-r * T) * cumulativeNormal(-d2)
        }

        val rhoPer1Pct = rho / 100.0

        return BigDecimal.valueOf(rhoPer1Pct).setScale(6, RoundingMode.HALF_UP)
    }

    fun impliedVolatility(
        marketPrice: BigDecimal,
        spot: BigDecimal,
        strike: BigDecimal,
        timeToExpiry: Double,
        riskFreeRate: Double,
        optionType: OptionType,
        maxIterations: Int = 100,
        tolerance: Double = 1e-6
    ): Double {
        var sigma = 0.5

        for (i in 0 until maxIterations) {
            val calculatedPrice = price(spot, strike, timeToExpiry, riskFreeRate, sigma, optionType)
            val diff = calculatedPrice.subtract(marketPrice).toDouble()

            if (abs(diff) < tolerance) {
                return sigma
            }

            val vegaValue = vega(spot, strike, timeToExpiry, riskFreeRate, sigma).toDouble() * 100.0

            if (abs(vegaValue) < 1e-10) {
                break
            }

            sigma -= diff / vegaValue
            sigma = sigma.coerceIn(0.001, 5.0)
        }

        return sigma
    }

    private fun d1(S: Double, K: Double, T: Double, r: Double, sigma: Double): Double {
        return (ln(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * sqrt(T))
    }

    private fun d2(d1: Double, sigma: Double, T: Double): Double {
        return d1 - sigma * sqrt(T)
    }

    private fun cumulativeNormal(x: Double): Double {
        if (x < -10.0) return 0.0
        if (x > 10.0) return 1.0

        val t = 1.0 / (1.0 + 0.2316419 * abs(x))
        val d = 0.3989423 * exp(-x * x / 2.0)

        val prob = d * t * (0.3193815 +
                t * (-0.3565638 +
                        t * (1.781478 +
                                t * (-1.821256 +
                                        t * 1.330274))))

        return if (x > 0.0) 1.0 - prob else prob
    }

    private fun normalDensity(x: Double): Double {
        return exp(-x * x / 2.0) / SQRT_2PI
    }
}
