package com.portfolio.strategy.engine

import com.portfolio.common.domain.Greeks
import com.portfolio.common.domain.GreeksSource
import com.portfolio.common.domain.OptionType
import com.portfolio.common.math.BlackScholes
import com.portfolio.strategy.model.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class StrategyCalculator(
    @Value("\${risk-free-rate:0.05}") private val riskFreeRate: Double = 0.05
) {
    private companion object {
        private const val SCALE = 2
        private const val PNL_POINTS = 100
        private val PRICE_RANGE_PERCENT = BigDecimal("0.20")

        /** Flat volatility fallback, matching market-data's GreeksCalculator (`iv ?: 0.20`). */
        const val DEFAULT_IV = 0.20
    }

    private fun legGreeks(leg: Leg, spot: BigDecimal): Greeks? {
        val expiry = leg.expiry ?: return null
        val optionType = leg.optionType ?: return null
        if (spot <= BigDecimal.ZERO) return null
        val tte = ChronoUnit.DAYS.between(LocalDate.now(), expiry) / 365.0
        if (tte <= 0.0) return null

        // Leg carries no implied volatility, so Greeks are computed at a flat 20% IV
        // (same fallback market-data's GreeksCalculator uses). Wiring real IV requires
        // exposing impliedVolatility from the options-chain DTO — out of scope here.
        return Greeks(
            delta = BlackScholes.delta(spot, leg.strike, tte, riskFreeRate, DEFAULT_IV, optionType),
            gamma = BlackScholes.gamma(spot, leg.strike, tte, riskFreeRate, DEFAULT_IV),
            theta = BlackScholes.theta(spot, leg.strike, tte, riskFreeRate, DEFAULT_IV, optionType),
            vega = BlackScholes.vega(spot, leg.strike, tte, riskFreeRate, DEFAULT_IV),
            rho = BigDecimal.ZERO,
            source = GreeksSource.BLACK_SCHOLES
        )
    }

    fun calculate(legs: List<Leg>, spotPrice: BigDecimal): CalculationResult {
        val netDebitCredit = calculateNetDebitCredit(legs)
        val pnlCurve = generatePnlCurve(legs, spotPrice, netDebitCredit)
        val maxProfit = pnlCurve.maxOf { it.pnl }
        val maxLoss = pnlCurve.minOf { it.pnl }.abs()
        val breakEvenPrices = findBreakEvenPrices(pnlCurve)
        val riskRewardRatio = if (maxLoss > BigDecimal.ZERO) {
            maxProfit.divide(maxLoss, SCALE, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        val netGreeks = calculateNetGreeks(legs, spotPrice)

        val contractMultiplier = BigDecimal(100)
        val maxProfitDollars = maxProfit * contractMultiplier
        val maxLossDollars = maxLoss * contractMultiplier
        val netDebitCreditDollars = netDebitCredit * contractMultiplier

        return CalculationResult(
            strategyType = null, netDebitCredit = netDebitCredit,
            maxProfit = maxProfit, maxLoss = maxLoss,
            breakEvenPrices = breakEvenPrices, riskRewardRatio = riskRewardRatio,
            probabilityOfProfit = null, pnlCurve = pnlCurve, netGreeks = netGreeks,
            maxProfitDollars = maxProfitDollars, maxLossDollars = maxLossDollars,
            netDebitCreditDollars = netDebitCreditDollars
        )
    }

    private fun calculateNetDebitCredit(legs: List<Leg>): BigDecimal {
        return legs.sumOf { leg ->
            val quantity = BigDecimal(leg.quantity)
            when (leg.action) {
                LegAction.BUY -> leg.mid.negate() * quantity
                LegAction.SELL -> leg.mid * quantity
            }
        }.setScale(SCALE, RoundingMode.HALF_UP)
    }

    private fun generatePnlCurve(legs: List<Leg>, spotPrice: BigDecimal, netCost: BigDecimal): List<PnlPoint> {
        val minPrice = spotPrice * (BigDecimal.ONE - PRICE_RANGE_PERCENT)
        val maxPrice = spotPrice * (BigDecimal.ONE + PRICE_RANGE_PERCENT)
        val priceStep = (maxPrice - minPrice).divide(BigDecimal(PNL_POINTS - 1), 10, RoundingMode.HALF_UP)

        return (0 until PNL_POINTS).map { i ->
            val price = minPrice + (priceStep * BigDecimal(i))
            val pnl = calculatePnlAtPrice(legs, price, netCost)
            PnlPoint(price.setScale(SCALE, RoundingMode.HALF_UP), pnl)
        }
    }

    private fun calculatePnlAtPrice(legs: List<Leg>, underlyingPrice: BigDecimal, netCost: BigDecimal): BigDecimal {
        val totalValue = legs.sumOf { leg ->
            val intrinsicValue = calculateIntrinsicValue(leg, underlyingPrice)
            val multiplier = when (leg.action) {
                LegAction.BUY -> BigDecimal.ONE
                LegAction.SELL -> BigDecimal.ONE.negate()
            }
            intrinsicValue * multiplier
        }
        return (totalValue + netCost).setScale(SCALE, RoundingMode.HALF_UP)
    }

    private fun calculateIntrinsicValue(leg: Leg, underlyingPrice: BigDecimal): BigDecimal {
        if (leg.optionType == null) {
            val quantityMultiplier = BigDecimal(leg.quantity).divide(BigDecimal(100), SCALE, RoundingMode.HALF_UP)
            return underlyingPrice * quantityMultiplier
        }
        return (when (leg.optionType) {
            OptionType.CALL -> (underlyingPrice - leg.strike).max(BigDecimal.ZERO)
            OptionType.PUT -> (leg.strike - underlyingPrice).max(BigDecimal.ZERO)
        }) * BigDecimal(leg.quantity)
    }

    private fun findBreakEvenPrices(pnlCurve: List<PnlPoint>): List<BigDecimal> {
        val breakEvens = mutableListOf<BigDecimal>()
        for (i in 0 until pnlCurve.size - 1) {
            val current = pnlCurve[i]
            val next = pnlCurve[i + 1]
            if ((current.pnl <= BigDecimal.ZERO && next.pnl >= BigDecimal.ZERO) ||
                (current.pnl >= BigDecimal.ZERO && next.pnl <= BigDecimal.ZERO)
            ) {
                breakEvens.add(interpolateBreakEven(current, next))
            }
        }
        return breakEvens.distinct().sorted()
    }

    private fun interpolateBreakEven(p1: PnlPoint, p2: PnlPoint): BigDecimal {
        if (p1.pnl == p2.pnl) return p1.underlyingPrice
        val breakEven = p1.underlyingPrice + (BigDecimal.ZERO - p1.pnl)
            .multiply(p2.underlyingPrice - p1.underlyingPrice)
            .divide(p2.pnl - p1.pnl, 10, RoundingMode.HALF_UP)
        return breakEven.setScale(SCALE, RoundingMode.HALF_UP)
    }

    private fun calculateNetGreeks(legs: List<Leg>, spotPrice: BigDecimal): NetGreeks {
        var netDelta = BigDecimal.ZERO
        legs.forEach { leg ->
            val multiplier = when (leg.action) {
                LegAction.BUY -> BigDecimal.ONE
                LegAction.SELL -> BigDecimal.ONE.negate()
            }
            if (leg.optionType == null) {
                val stockDelta = leg.delta * BigDecimal(leg.quantity).divide(BigDecimal(100), SCALE, RoundingMode.HALF_UP)
                netDelta += stockDelta * multiplier
            } else {
                netDelta += leg.delta * multiplier
            }
        }
        var netGamma = BigDecimal.ZERO
        var netTheta = BigDecimal.ZERO
        var netVega = BigDecimal.ZERO

        legs.forEach { leg ->
            val multiplier = when (leg.action) {
                LegAction.BUY -> BigDecimal.ONE
                LegAction.SELL -> BigDecimal.ONE.negate()
            }
            val greeks = legGreeks(leg, spotPrice) ?: return@forEach
            netGamma += greeks.gamma * multiplier
            netTheta += greeks.theta * multiplier
            netVega += greeks.vega * multiplier
        }

        return NetGreeks(
            netDelta.setScale(SCALE, RoundingMode.HALF_UP),
            netGamma.setScale(SCALE, RoundingMode.HALF_UP),
            netTheta.setScale(SCALE, RoundingMode.HALF_UP),
            netVega.setScale(SCALE, RoundingMode.HALF_UP)
        )
    }
}
