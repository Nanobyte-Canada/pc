package com.portfolio.service

import com.portfolio.dto.request.PortfolioAnalyzeRequest
import com.portfolio.dto.request.PortfolioNormalizeRequest
import com.portfolio.dto.request.PortfolioValidateRequest
import com.portfolio.dto.response.*
import com.portfolio.entity.AVEnrichmentStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class PortfolioAnalysisService(
    private val lookThroughService: LookThroughService,
    private val riskMetricsService: RiskMetricsService,
    private val referenceDataService: ReferenceDataService
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

        // If we have ETF direct allocations, we may not need lookthrough for those ETFs
        // For now, we'll use lookthrough for all resolved exposures to get complete picture
        for (exposure in exposures.values) {
            val sector = exposure.stock.gicsSubIndustry?.industry?.industryGroup?.sector
            if (sector != null) {
                sectorWeights[sector.code] = sectorWeights.getOrDefault(sector.code, BigDecimal.ZERO) + exposure.effectiveWeight
                sectorNames[sector.code] = sector.name
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
        val countryRegionMap = referenceDataService.getCountryToRegionMap()
        val countryNameMap = referenceDataService.getCountryNameMap()

        val geographyWeights = mutableMapOf<String, BigDecimal>()

        for (exposure in exposures.values) {
            val country = exposure.stock.country
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
                    ticker = exposure.stock.ticker,
                    name = exposure.stock.name,
                    effectiveWeight = exposure.effectiveWeight.setScale(6, RoundingMode.HALF_UP).toDouble(),
                    sources = exposure.sources
                )
            }
    }

    /**
     * Build financial summary from Alpha Vantage enrichment data.
     */
    private fun buildFinancialSummary(
        exposures: Map<Long, LookThroughExposure>
    ): FinancialSummaryDto? {
        if (exposures.isEmpty()) return null

        var weightedPeSum = BigDecimal.ZERO
        var peWeightSum = BigDecimal.ZERO
        var weightedDividendSum = BigDecimal.ZERO
        var dividendWeightSum = BigDecimal.ZERO
        var weightedBetaSum = BigDecimal.ZERO
        var betaWeightSum = BigDecimal.ZERO

        var largeCapWeight = BigDecimal.ZERO
        var midCapWeight = BigDecimal.ZERO
        var smallCapWeight = BigDecimal.ZERO
        var microCapWeight = BigDecimal.ZERO

        val tenBillion = BigDecimal("10000000000")
        val twoBillion = BigDecimal("2000000000")
        val threeHundredMillion = BigDecimal("300000000")

        for (exposure in exposures.values) {
            val stock = exposure.stock
            val weight = exposure.effectiveWeight

            // P/E ratio
            stock.avPeRatio?.let { pe ->
                if (pe > BigDecimal.ZERO && pe < BigDecimal(1000)) {
                    weightedPeSum += weight * pe
                    peWeightSum += weight
                }
            }

            // Dividend yield
            stock.avDividendYield?.let { dy ->
                if (dy >= BigDecimal.ZERO) {
                    weightedDividendSum += weight * dy
                    dividendWeightSum += weight
                }
            }

            // Beta
            stock.avBeta?.let { beta ->
                if (beta > BigDecimal.ZERO && beta < BigDecimal(10)) {
                    weightedBetaSum += weight * beta
                    betaWeightSum += weight
                }
            }

            // Market cap breakdown
            stock.avMarketCap?.let { marketCap ->
                when {
                    marketCap >= tenBillion -> largeCapWeight += weight
                    marketCap >= twoBillion -> midCapWeight += weight
                    marketCap >= threeHundredMillion -> smallCapWeight += weight
                    else -> microCapWeight += weight
                }
            }
        }

        val weightedPe = if (peWeightSum > BigDecimal.ZERO) {
            (weightedPeSum / peWeightSum).setScale(2, RoundingMode.HALF_UP).toDouble()
        } else null

        val weightedDividend = if (dividendWeightSum > BigDecimal.ZERO) {
            (weightedDividendSum / dividendWeightSum).setScale(4, RoundingMode.HALF_UP).toDouble()
        } else null

        val weightedBeta = if (betaWeightSum > BigDecimal.ZERO) {
            (weightedBetaSum / betaWeightSum).setScale(2, RoundingMode.HALF_UP).toDouble()
        } else null

        val totalMarketCapWeight = largeCapWeight + midCapWeight + smallCapWeight + microCapWeight
        val marketCapBreakdown = if (totalMarketCapWeight > BigDecimal.ZERO) {
            MarketCapBreakdownDto(
                largeCap = (largeCapWeight / totalMarketCapWeight * BigDecimal(100)).setScale(1, RoundingMode.HALF_UP).toDouble(),
                midCap = (midCapWeight / totalMarketCapWeight * BigDecimal(100)).setScale(1, RoundingMode.HALF_UP).toDouble(),
                smallCap = (smallCapWeight / totalMarketCapWeight * BigDecimal(100)).setScale(1, RoundingMode.HALF_UP).toDouble(),
                microCap = (microCapWeight / totalMarketCapWeight * BigDecimal(100)).setScale(1, RoundingMode.HALF_UP).toDouble()
            )
        } else null

        return FinancialSummaryDto(
            weightedPeRatio = weightedPe,
            weightedDividendYield = weightedDividend,
            weightedBeta = weightedBeta,
            marketCapBreakdown = marketCapBreakdown,
            totalExpenseRatio = null // TODO: Calculate from ETF/MF positions
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

        // Calculate enrichment coverage (% of stocks with Alpha Vantage data)
        val enrichedCount = exposures.values.count { exposure ->
            exposure.stock.avEnrichmentStatus == AVEnrichmentStatus.SUCCESS
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
        val mfCount = request.positions.count { it.instrumentType.uppercase() == "MUTUAL_FUND" }

        return PortfolioSummaryDto(
            totalPositions = request.positions.size,
            directStockCount = stockCount,
            etfCount = etfCount,
            mutualFundCount = mfCount,
            lookThroughStockCount = exposures.size,
            analysisDate = analysisDate.toString()
        )
    }
}
