package com.portfolio.strategy.api.dto

import com.portfolio.strategy.model.EducationContent
import java.math.BigDecimal

data class StrategyListResponse(
    val name: String,
    val displayName: String,
    val description: String,
    val outlook: String,
    val riskProfile: String,
    val legCount: Int,
    val legTemplates: List<LegTemplateDto>
)

data class StrategyInfoResponse(
    val name: String,
    val displayName: String,
    val description: String,
    val outlook: String,
    val riskProfile: String,
    val legCount: Int,
    val legTemplates: List<LegTemplateDto>,
    val education: EducationContent
)

data class LegTemplateDto(
    val action: String,
    val optionType: String?,
    val strikeOffset: String,
    val quantity: Int
)

data class CalculateRequest(
    val legs: List<LegRequest>,
    val spotPrice: BigDecimal,
    val strategyType: String? = null
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
    val delta: BigDecimal = BigDecimal.ZERO,
    val symbol: String? = null
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
    val warnings: List<String>,
    val maxProfitDollars: BigDecimal = BigDecimal.ZERO,
    val maxLossDollars: BigDecimal = BigDecimal.ZERO,
    val netDebitCreditDollars: BigDecimal = BigDecimal.ZERO
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

data class TradeRequest(
    val strategyType: String,
    val legs: List<LegRequest>,
    val spotPrice: BigDecimal,
    val connectionId: Long,
    val accountId: String,
    val orderType: String = "LIMIT",
    val limitPrice: BigDecimal,
    val timeInForce: String = "DAY"
)

data class TradeResponse(
    val orderId: String?,
    val status: String,
    val message: String,
    val legs: List<LegResult>
)

data class LegResult(
    val action: String,
    val symbol: String,
    val quantity: Int,
    val status: String
)
