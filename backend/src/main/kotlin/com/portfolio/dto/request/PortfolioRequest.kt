package com.portfolio.dto.request

import java.time.LocalDate

data class PortfolioPositionRequest(
    val instrumentType: String,
    val instrumentId: Long,
    val weight: Double
)

data class PortfolioAnalyzeRequest(
    val positions: List<PortfolioPositionRequest>,
    val analysisDate: LocalDate? = null
)

data class PortfolioValidateRequest(
    val positions: List<PortfolioPositionRequest>
)

data class PortfolioNormalizeRequest(
    val positions: List<PortfolioPositionRequest>
)
