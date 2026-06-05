package com.portfolio.strategy.model

import java.math.BigDecimal

data class CalculationResult(
    val strategyType: StrategyType?,
    val netDebitCredit: BigDecimal,
    val maxProfit: BigDecimal,
    val maxLoss: BigDecimal,
    val breakEvenPrices: List<BigDecimal>,
    val riskRewardRatio: BigDecimal,
    val probabilityOfProfit: BigDecimal?,
    val pnlCurve: List<PnlPoint>,
    val netGreeks: NetGreeks
)

data class PnlPoint(
    val underlyingPrice: BigDecimal,
    val pnl: BigDecimal
)

data class NetGreeks(
    val delta: BigDecimal,
    val gamma: BigDecimal,
    val theta: BigDecimal,
    val vega: BigDecimal
)
