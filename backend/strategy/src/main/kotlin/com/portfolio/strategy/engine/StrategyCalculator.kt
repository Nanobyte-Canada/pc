package com.portfolio.strategy.engine

import com.portfolio.common.domain.OptionType
import com.portfolio.strategy.model.*
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class StrategyCalculator {

    companion object {
        private const val SCALE = 2
        private const val PNL_POINTS = 100
        private val PRICE_RANGE_PERCENT = BigDecimal("0.20")
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
        val netGreeks = calculateNetGreeks(legs)

        return CalculationResult(
            strategyType = null, netDebitCredit = netDebitCredit,
            maxProfit = maxProfit, maxLoss = maxLoss,
            breakEvenPrices = breakEvenPrices, riskRewardRatio = riskRewardRatio,
            probabilityOfProfit = null, pnlCurve = pnlCurve, netGreeks = netGreeks
        )
    }

    private fun calculateNetDebitCredit(legs: List<Leg>): BigDecimal {
        return legs.sumOf { leg ->
            when (leg.action) {
                LegAction.BUY -> leg.mid.negate()
                LegAction.SELL -> leg.mid
            }
        }.setScale(SCALE, RoundingMode.HALF_UP)
    }

    private fun generatePnlCurve(legs: List<Leg>, spotPrice: BigDecimal, netCost: BigDecimal): List<PnlPoint> {
        val minPrice = spotPrice * (BigDecimal.ONE - PRICE_RANGE_PERCENT)
        val maxPrice = spotPrice * (BigDecimal.ONE + PRICE_RANGE_PERCENT)
        val priceStep = (maxPrice - minPrice) / BigDecimal(PNL_POINTS - 1)

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
        return when (leg.optionType) {
            OptionType.CALL -> (underlyingPrice - leg.strike).max(BigDecimal.ZERO)
            OptionType.PUT -> (leg.strike - underlyingPrice).max(BigDecimal.ZERO)
        }
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
        val breakEven = p1.underlyingPrice + (BigDecimal.ZERO - p1.pnl) * (p2.underlyingPrice - p1.underlyingPrice) / (p2.pnl - p1.pnl)
        return breakEven.setScale(SCALE, RoundingMode.HALF_UP)
    }

    private fun calculateNetGreeks(legs: List<Leg>): NetGreeks {
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
        return NetGreeks(netDelta.setScale(SCALE, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}
