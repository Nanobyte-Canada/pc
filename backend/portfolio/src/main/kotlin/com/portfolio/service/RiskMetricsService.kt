package com.portfolio.service

import com.portfolio.dto.response.RiskMetricsDto
import com.portfolio.dto.response.SectorExposureDto
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class RiskMetricsService {

    // Sector volatility proxies (annualized) - used as fallback when beta is unavailable
    private val sectorVolatilityProxies = mapOf(
        "10" to 0.28,  // Energy - higher vol
        "15" to 0.22,  // Materials
        "20" to 0.18,  // Industrials
        "25" to 0.22,  // Consumer Discretionary
        "30" to 0.12,  // Consumer Staples - lower vol
        "35" to 0.18,  // Health Care
        "40" to 0.20,  // Financials
        "45" to 0.25,  // Information Technology - higher vol
        "50" to 0.24,  // Communication Services
        "55" to 0.14,  // Utilities - lower vol
        "60" to 0.16,  // Real Estate
        "OTHER" to 0.20 // Default for unresolved
    )

    // Threshold for using beta-based volatility (coverage percentage)
    private val BETA_COVERAGE_THRESHOLD = 0.50

    // Market volatility assumption for beta-based calculation
    private val MARKET_VOLATILITY = 0.18

    /**
     * Compute enhanced risk metrics using actual beta when available.
     */
    fun computeEnhancedRiskMetrics(
        exposures: Map<Long, LookThroughExposure>,
        sectorExposures: List<SectorExposureDto>
    ): RiskMetricsDto {
        val weights = exposures.values.map { it.effectiveWeight }

        // 1. Concentration HHI = sum of squared weights
        val concentrationHHI = weights.sumOf { it.pow(2) }

        // 2. Top 10 concentration
        val top10Concentration = weights
            .sortedDescending()
            .take(10)
            .fold(BigDecimal.ZERO) { acc, w -> acc + w }

        // 3. Sector concentration HHI
        val sectorConcentrationHHI = sectorExposures
            .map { BigDecimal(it.weight) }
            .sumOf { it.pow(2) }

        // 4. Compute weighted beta and beta-based volatility
        val (weightedBeta, betaCoverage) = computeWeightedBeta(exposures)

        // 5. Compute volatility - prefer beta-based when coverage is sufficient
        val (estimatedVolatility, volatilitySource) = if (betaCoverage >= BETA_COVERAGE_THRESHOLD && weightedBeta != null) {
            // Beta-based volatility: portfolio_vol ≈ portfolio_beta * market_vol
            val betaBasedVol = weightedBeta.toDouble() * MARKET_VOLATILITY
            Pair(betaBasedVol, "STOCK_BETA")
        } else {
            // Fallback to sector proxy
            Pair(computeProxyVolatility(sectorExposures), "CATEGORY_PROXY")
        }

        // 6. Compute dividend yield
        val portfolioDividendYield = computeWeightedDividendYield(exposures)

        // 7. Compute P/E ratio
        val weightedPeRatio = computeWeightedPeRatio(exposures)

        return RiskMetricsDto(
            concentrationHHI = concentrationHHI.setScale(6, RoundingMode.HALF_UP).toDouble(),
            top10Concentration = top10Concentration.setScale(6, RoundingMode.HALF_UP).toDouble(),
            sectorConcentrationHHI = sectorConcentrationHHI.setScale(6, RoundingMode.HALF_UP).toDouble(),
            estimatedVolatility = estimatedVolatility,
            volatilitySource = volatilitySource,
            weightedBeta = weightedBeta?.setScale(4, RoundingMode.HALF_UP)?.toDouble(),
            portfolioDividendYield = portfolioDividendYield?.setScale(4, RoundingMode.HALF_UP)?.toDouble(),
            weightedPeRatio = weightedPeRatio?.setScale(2, RoundingMode.HALF_UP)?.toDouble(),
            betaCoverage = betaCoverage
        )
    }

    /**
     * Legacy method for backward compatibility.
     */
    fun computeRiskMetrics(
        exposures: Map<Long, LookThroughExposure>,
        sectorExposures: List<SectorExposureDto>
    ): RiskMetricsDto {
        return computeEnhancedRiskMetrics(exposures, sectorExposures)
    }

    /**
     * Compute weighted average beta from stock-level data.
     * Returns the weighted beta and the coverage percentage (weight of stocks with beta data).
     *
     * Note: Beta is no longer available directly from the old Stock entity. This now returns
     * null/0 coverage. To restore beta-based risk metrics, add a cross-schema query to
     * ingestion.provider_raw_data for the Technicals.Beta JSONB field.
     */
    private fun computeWeightedBeta(exposures: Map<Long, LookThroughExposure>): Pair<BigDecimal?, Double> {
        // Beta data was previously accessed via Stock.avDecimal("Beta").
        // With the migration to ingestion schema, this would require a JSONB lookup.
        // Returning null to fall back to sector-proxy volatility.
        return Pair(null, 0.0)
    }

    /**
     * Compute weighted average dividend yield from stock-level data.
     * Note: Requires migration to cross-schema JSONB queries for full support.
     */
    private fun computeWeightedDividendYield(exposures: Map<Long, LookThroughExposure>): BigDecimal? {
        // Dividend yield was previously accessed via Stock.avDecimal("DividendYield").
        // With the migration to ingestion schema, this would require a JSONB lookup.
        return null
    }

    /**
     * Compute weighted average P/E ratio from stock-level data.
     * Note: Requires migration to cross-schema JSONB queries for full support.
     */
    private fun computeWeightedPeRatio(exposures: Map<Long, LookThroughExposure>): BigDecimal? {
        // P/E ratio was previously accessed via Stock.avDecimal("PERatio").
        // With the migration to ingestion schema, this would require a JSONB lookup.
        return null
    }

    /**
     * Compute proxy volatility based on sector mix.
     * Used as fallback when beta data is insufficient.
     */
    private fun computeProxyVolatility(sectorExposures: List<SectorExposureDto>): Double {
        if (sectorExposures.isEmpty()) return 0.18 // Default moderate volatility

        return sectorExposures.sumOf { sector ->
            val sectorVol = sectorVolatilityProxies[sector.sectorCode] ?: 0.18
            sector.weight * sectorVol
        }
    }
}
