package com.portfolio.strategy.api.dto

import com.portfolio.strategy.model.EducationContent
import java.math.BigDecimal

data class StrategyListResponse(
    val name: String,
    val displayName: String,
    val description: String,
    val outlook: String,
    val riskProfile: String,
    val legCount: Int
)

data class StrategyInfoResponse(
    val name: String,
    val displayName: String,
    val description: String,
    val outlook: String,
    val riskProfile: String,
    val legCount: Int,
    val education: EducationContent
)

data class CalculateRequest(
    val legs: List<LegRequest>,
    val spotPrice: BigDecimal
)

data class LegRequest(
    val action: String,
    val optionType: String?,
    val strike: BigDecimal,
    val expiry: String?,
    val quantity: Int = 1,
    val bid: BigDecimal = BigDecimal.ZERO,
    val ask: BigDecimal = BigDecimal.ZERO,
    val mid: BigDecimal = BigDecimal.ZERO,
    val delta: BigDecimal = BigDecimal.ZERO
)

data class CalculateResponse(
    val netDebitCredit: BigDecimal,
    val maxProfit: BigDecimal,
    val maxLoss: BigDecimal,
    val breakEvenPrices: List<BigDecimal>,
    val riskRewardRatio: BigDecimal,
    val probabilityOfProfit: BigDecimal?,
    val pnlCurve: List<PnlPointDto>,
    val netGreeks: NetGreeksDto,
    val warnings: List<String>
)

data class PnlPointDto(
    val underlyingPrice: BigDecimal,
    val pnl: BigDecimal
)

data class NetGreeksDto(
    val delta: BigDecimal,
    val gamma: BigDecimal,
    val theta: BigDecimal,
    val vega: BigDecimal
)

data class SuggestRequest(
    val outlook: String
)
