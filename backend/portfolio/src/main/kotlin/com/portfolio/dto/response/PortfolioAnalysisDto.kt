package com.portfolio.dto.response

import java.math.BigDecimal

data class PortfolioSummaryDto(
    val totalPositions: Int,
    val directStockCount: Int,
    val etfCount: Int,
    val lookThroughStockCount: Int,
    val analysisDate: String
)

data class ValidationDto(
    val isValid: Boolean,
    val totalWeight: Double,
    val errors: List<ValidationErrorDto>,
    val warnings: List<String>
)

data class ValidationErrorDto(
    val code: String,
    val message: String
)

data class SectorExposureDto(
    val sectorCode: String,
    val sectorName: String,
    val weight: Double
)

data class GeographyExposureDto(
    val country: String,
    val countryName: String,
    val region: String,
    val weight: Double
)

data class ExposureSourceDto(
    val type: String,
    val instrumentId: Long?,
    val instrumentSymbol: String?,
    val contribution: Double
)

data class TopHoldingDto(
    val stockId: Long,
    val ticker: String,
    val name: String,
    val effectiveWeight: Double,
    val sources: List<ExposureSourceDto>
)

data class RiskMetricsDto(
    val concentrationHHI: Double,
    val top10Concentration: Double,
    val sectorConcentrationHHI: Double,
    val estimatedVolatility: Double,
    val volatilitySource: String,
    // Enhanced metrics from Alpha Vantage data
    val weightedBeta: Double? = null,
    val portfolioDividendYield: Double? = null,
    val weightedPeRatio: Double? = null,
    val betaCoverage: Double? = null  // Percentage of portfolio with beta data
)

data class AnalysisQualityDto(
    val lookThroughCoverage: Double,      // % of fund holdings resolved to stocks
    val enrichmentCoverage: Double,        // % of stocks with Alpha Vantage data
    val unresolvedHoldingsCount: Int,
    val unresolvedHoldingsWeight: Double,
    val dataQualityScore: String           // HIGH, MEDIUM, LOW based on coverage
)

data class FinancialSummaryDto(
    val weightedPeRatio: Double?,
    val weightedDividendYield: Double?,
    val weightedBeta: Double?,
    val marketCapBreakdown: MarketCapBreakdownDto?,
    val totalExpenseRatio: Double?         // Weighted average expense ratio for funds
)

data class MarketCapBreakdownDto(
    val largeCap: Double,    // > $10B
    val midCap: Double,      // $2B - $10B
    val smallCap: Double,    // $300M - $2B
    val microCap: Double     // < $300M
)

data class PortfolioAnalysisResponseDto(
    val summary: PortfolioSummaryDto,
    val validation: ValidationDto,
    val sectorExposure: List<SectorExposureDto>,
    val geographyExposure: List<GeographyExposureDto>,
    val topHoldings: List<TopHoldingDto>,
    val riskMetrics: RiskMetricsDto,
    val financialSummary: FinancialSummaryDto? = null,
    val analysisQuality: AnalysisQualityDto? = null
)

data class ValidateResponseDto(
    val isValid: Boolean,
    val totalWeight: Double,
    val errors: List<ValidationErrorDto>,
    val warnings: List<String>
)

data class NormalizedPositionDto(
    val instrumentType: String,
    val instrumentId: Long,
    val originalWeight: Double,
    val normalizedWeight: Double
)

data class NormalizeResponseDto(
    val originalTotal: Double,
    val normalizedPositions: List<NormalizedPositionDto>
)
