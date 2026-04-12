package com.portfolio.service

import com.portfolio.dto.request.PortfolioAnalyzeRequest
import com.portfolio.dto.request.PortfolioNormalizeRequest
import com.portfolio.dto.request.PortfolioValidateRequest
import com.portfolio.dto.response.*
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class PortfolioAnalysisService(
    private val lookThroughService: LookThroughService,
    private val riskMetricsService: RiskMetricsService,
    private val countryRegionLookup: CountryRegionLookupService
) {

    fun analyze(request: PortfolioAnalyzeRequest): PortfolioAnalysisResponseDto {
        val analysisDate = request.analysisDate ?: LocalDate.now()

        // Validate positions
        val validation = validatePositions(request.positions.map {
            com.portfolio.dto.request.PortfolioPositionRequest(it.instrumentType, it.instrumentId, it.weight)
        })

        // Compute look-through exposures with quality metrics
        val lookThroughResult = lookThroughService.computeLookThroughWithQuality(request.positions, analysisDate)
        val exposures = lookThroughResult.exposures

        // Aggregate by sector (using direct ETF allocations when available)
        val sectorExposures = aggregateBySectorWithEtfDirect(exposures, lookThroughResult)

        // Aggregate by geography using database reference data
        val geographyExposures = aggregateByGeography(exposures, lookThroughResult.unresolvedExposures)

        // Get top holdings
        val topHoldings = getTopHoldings(exposures, 20)

        // Compute enhanced risk metrics
        val riskMetrics = riskMetricsService.computeEnhancedRiskMetrics(exposures, sectorExposures)

        // Build financial summary
        val financialSummary = buildFinancialSummary(exposures)

        // Build analysis quality metrics
        val analysisQuality = buildAnalysisQuality(lookThroughResult, exposures)

        // Build summary
        val summary = buildSummary(request, exposures, analysisDate)

        return PortfolioAnalysisResponseDto(
            summary = summary,
            validation = validation,
            sectorExposure = sectorExposures,
            geographyExposure = geographyExposures,
            topHoldings = topHoldings,
            riskMetrics = riskMetrics,
            financialSummary = financialSummary,
            analysisQuality = analysisQuality
        )
    }

    fun validate(request: PortfolioValidateRequest): ValidateResponseDto {
        val validation = validatePositions(request.positions)
        return ValidateResponseDto(
            isValid = validation.isValid,
            totalWeight = validation.totalWeight,
            errors = validation.errors,
            warnings = validation.warnings
        )
    }

    fun normalize(request: PortfolioNormalizeRequest): NormalizeResponseDto {
        val total = request.positions.sumOf { it.weight }

        val normalizedPositions = if (total > 0) {
            request.positions.map {
                NormalizedPositionDto(
                    instrumentType = it.instrumentType,
                    instrumentId = it.instrumentId,
                    originalWeight = it.weight,
                    normalizedWeight = (it.weight / total).toBigDecimal().setScale(6, RoundingMode.HALF_UP).toDouble()
                )
            }
        } else {
            request.positions.map {
                NormalizedPositionDto(
                    instrumentType = it.instrumentType,
                    instrumentId = it.instrumentId,
                    originalWeight = it.weight,
                    normalizedWeight = 0.0
                )
            }
        }

        return NormalizeResponseDto(
            originalTotal = total,
            normalizedPositions = normalizedPositions
        )
    }

    private fun validatePositions(
        positions: List<com.portfolio.dto.request.PortfolioPositionRequest>
    ): ValidationDto {
        val errors = mutableListOf<ValidationErrorDto>()
        val warnings = mutableListOf<String>()

        val totalWeight = positions.sumOf { it.weight }

        // Check if weights sum to 100%
        if (kotlin.math.abs(totalWeight - 1.0) > 0.0001) {
            errors.add(ValidationErrorDto(
                code = "WEIGHTS_SUM_MISMATCH",
                message = "Weights sum to ${(totalWeight * 100).toBigDecimal().setScale(1, RoundingMode.HALF_UP)}%, expected 100%"
            ))
        }

        // Check for negative weights
        positions.forEachIndexed { index, pos ->
            if (pos.weight < 0) {
                errors.add(ValidationErrorDto(
                    code = "NEGATIVE_WEIGHT",
                    message = "Position $index has negative weight"
                ))
            }
            if (pos.weight > 1) {
                errors.add(ValidationErrorDto(
                    code = "WEIGHT_EXCEEDS_100",
                    message = "Position $index has weight exceeding 100%"
                ))
            }
        }

        // Check for empty portfolio
        if (positions.isEmpty()) {
            errors.add(ValidationErrorDto(
                code = "EMPTY_PORTFOLIO",
                message = "Portfolio has no positions"
            ))
        }

        return ValidationDto(
            isValid = errors.isEmpty(),
            totalWeight = totalWeight,
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Aggregate by sector using direct ETF allocations when available,
     * falling back to look-through analysis.
     */
    private fun aggregateBySectorWithEtfDirect(
        exposures: Map<Long, LookThroughExposure>,
        lookThroughResult: LookThroughResult
    ): List<SectorExposureDto> {
        val sectorWeights = mutableMapOf<String, BigDecimal>()
        val sectorNames = mutableMapOf<String, String>()

        // First, add direct sector allocations from ETFs (if available)
        for ((_, etfExposure) in lookThroughResult.etfDirectSectorExposures) {
            for ((sectorCode, allocation) in etfExposure.sectorAllocations) {
                val contribution = etfExposure.portfolioWeight * allocation
                sectorWeights[sectorCode] = sectorWeights.getOrDefault(sectorCode, BigDecimal.ZERO) + contribution
                sectorNames[sectorCode] = LookThroughService.GICS_SECTOR_NAMES[sectorCode] ?: "Unknown"
            }
        }

        // Add unresolved holdings as "OTHER" sector
        val unresolvedWeight = lookThroughResult.unresolvedExposures.sumOf { it.effectiveWeight }
        if (unresolvedWeight > BigDecimal.ZERO) {
            sectorWeights["OTHER"] = unresolvedWeight
            sectorNames["OTHER"] = "Other / Unresolved"
        }

        return sectorWeights.map { (code, weight) ->
            SectorExposureDto(
                sectorCode = code,
                sectorName = sectorNames[code] ?: "Unknown",
                weight = weight.setScale(6, RoundingMode.HALF_UP).toDouble()
            )
        }.sortedByDescending { it.weight }
    }

    /**
     * Aggregate by geography using centralized reference data.
     * Includes unresolved holdings as "UNKNOWN" country in "Other" region.
     */
    private fun aggregateByGeography(
        exposures: Map<Long, LookThroughExposure>,
        unresolvedExposures: List<UnresolvedExposure>
    ): List<GeographyExposureDto> {
        val countryRegionMap = countryRegionLookup.getCountryToRegionMap()
        val countryNameMap = countryRegionLookup.getCountryNameMap()

        val geographyWeights = mutableMapOf<String, BigDecimal>()

        for (exposure in exposures.values) {
            val country = exposure.instrument.country ?: "UNKNOWN"
            geographyWeights[country] = geographyWeights.getOrDefault(country, BigDecimal.ZERO) + exposure.effectiveWeight
        }

        // Add unresolved holdings as "UNKNOWN" country
        val unresolvedWeight = unresolvedExposures.sumOf { it.effectiveWeight }
        if (unresolvedWeight > BigDecimal.ZERO) {
            geographyWeights["UNKNOWN"] = unresolvedWeight
        }

        return geographyWeights.map { (countryCode, weight) ->
            GeographyExposureDto(
                country = countryCode,
                countryName = if (countryCode == "UNKNOWN") "Unknown / Unresolved" else countryNameMap[countryCode] ?: countryCode,
                region = if (countryCode == "UNKNOWN") "Other" else countryRegionMap[countryCode] ?: "Other",
                weight = weight.setScale(6, RoundingMode.HALF_UP).toDouble()
            )
        }.sortedByDescending { it.weight }
    }

    private fun getTopHoldings(
        exposures: Map<Long, LookThroughExposure>,
        limit: Int
    ): List<TopHoldingDto> {
        return exposures.values
            .sortedByDescending { it.effectiveWeight }
            .take(limit)
            .map { exposure ->
                TopHoldingDto(
                    stockId = exposure.stockId,
                    ticker = exposure.instrument.ticker,
                    name = exposure.instrument.name,
                    effectiveWeight = exposure.effectiveWeight.setScale(6, RoundingMode.HALF_UP).toDouble(),
                    sources = exposure.sources
                )
            }
    }

    /**
     * Build financial summary.
     * Note: With the migration away from the old Stock entity, financial metrics (PE, beta, etc.)
     * are no longer directly accessible from the entity fields. This now returns a stub
     * that can be enhanced later with EODHD JSONB data from the ingestion schema.
     */
    private fun buildFinancialSummary(
        exposures: Map<Long, LookThroughExposure>
    ): FinancialSummaryDto? {
        if (exposures.isEmpty()) return null

        // Financial metrics are available from ingestion.provider_raw_data JSONB but would
        // require additional cross-schema queries. Return null metrics for now.
        return FinancialSummaryDto(
            weightedPeRatio = null,
            weightedDividendYield = null,
            weightedBeta = null,
            marketCapBreakdown = null,
            totalExpenseRatio = null
        )
    }

    /**
     * Build analysis quality metrics.
     */
    private fun buildAnalysisQuality(
        lookThroughResult: LookThroughResult,
        exposures: Map<Long, LookThroughExposure>
    ): AnalysisQualityDto {
        val quality = lookThroughResult.quality

        // With ingestion schema, enrichment coverage is based on whether
        // the instrument has EODHD fundamentals data (GICS codes present)
        val enrichedCount = exposures.values.count { exposure ->
            exposure.instrument.gicsSectorCode != null
        }
        val enrichmentCoverage = if (exposures.isNotEmpty()) {
            (enrichedCount.toDouble() / exposures.size * 100)
        } else 0.0

        // Determine data quality score
        val lookThroughCoverage = quality.coveragePercent.toDouble()
        val dataQualityScore = when {
            lookThroughCoverage >= 90 && enrichmentCoverage >= 80 -> "HIGH"
            lookThroughCoverage >= 70 && enrichmentCoverage >= 50 -> "MEDIUM"
            else -> "LOW"
        }

        return AnalysisQualityDto(
            lookThroughCoverage = lookThroughCoverage,
            enrichmentCoverage = enrichmentCoverage,
            unresolvedHoldingsCount = quality.unresolvedCount,
            unresolvedHoldingsWeight = quality.unresolvedWeight.toDouble(),
            dataQualityScore = dataQualityScore
        )
    }

    private fun buildSummary(
        request: PortfolioAnalyzeRequest,
        exposures: Map<Long, LookThroughExposure>,
        analysisDate: LocalDate
    ): PortfolioSummaryDto {
        val stockCount = request.positions.count { it.instrumentType.uppercase() == "STOCK" }
        val etfCount = request.positions.count { it.instrumentType.uppercase() == "ETF" }

        return PortfolioSummaryDto(
            totalPositions = request.positions.size,
            directStockCount = stockCount,
            etfCount = etfCount,
            lookThroughStockCount = exposures.size,
            analysisDate = analysisDate.toString()
        )
    }
}
