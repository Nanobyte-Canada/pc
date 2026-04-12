package com.portfolio.broker.service

import com.portfolio.broker.dto.RiskFactorsDto
import com.portfolio.broker.dto.RiskProfileResponse
import com.portfolio.broker.entity.InstrumentType
import com.portfolio.broker.repository.BrokerPositionRepository
import com.portfolio.dto.request.PortfolioPositionRequest
import com.portfolio.service.CountryRegionLookupService
import com.portfolio.service.IngestionInstrumentLookupService
import com.portfolio.service.LookThroughService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class DashboardRiskService(
    private val positionRepository: BrokerPositionRepository,
    private val instrumentLookup: IngestionInstrumentLookupService,
    private val countryRegionLookup: CountryRegionLookupService,
    private val lookThroughService: LookThroughService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getRiskProfile(userId: Long, connectionId: Long? = null): RiskProfileResponse {
        val positions = getPositions(userId, connectionId)
        val totalValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        if (positions.isEmpty() || totalValue <= BigDecimal.ZERO) {
            return RiskProfileResponse(0, "LOW", RiskFactorsDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyMap()
            ))
        }

        val lookThroughPositions = buildLookThroughPositions(positions, totalValue)
        val result = try {
            if (lookThroughPositions.isNotEmpty()) {
                lookThroughService.computeLookThroughWithQuality(lookThroughPositions, LocalDate.now())
            } else null
        } catch (e: Exception) {
            log.warn("Failed to compute look-through for risk profile", e)
            null
        }

        // Concentration HHI (sum of squared weights)
        val weights = if (result != null) {
            result.exposures.values.map { it.effectiveWeight }
        } else {
            positions.map {
                (it.currentValue ?: BigDecimal.ZERO).divide(totalValue, 8, RoundingMode.HALF_UP)
            }
        }
        val concentrationHHI = weights.sumOf { it * it }.setScale(4, RoundingMode.HALF_UP)

        // Top-10 concentration
        val top10Weight = weights.sortedDescending().take(10).fold(BigDecimal.ZERO) { acc, w -> acc + w }
            .setScale(4, RoundingMode.HALF_UP)

        // Sector concentration - computed from the same look-through result
        val sectorWeights = mutableMapOf<String, BigDecimal>()
        if (result != null) {
            for ((_, exposure) in result.exposures) {
                val sectorCode = exposure.instrument.gicsSectorCode ?: continue
                sectorWeights[sectorCode] = (sectorWeights[sectorCode] ?: BigDecimal.ZERO) + exposure.effectiveWeight
            }
            // Include ETF direct sector exposures
            for ((_, etfSector) in result.etfDirectSectorExposures) {
                for ((gicsCode, alloc) in etfSector.sectorAllocations) {
                    val effectiveWeight = etfSector.portfolioWeight * alloc
                    sectorWeights[gicsCode] = (sectorWeights[gicsCode] ?: BigDecimal.ZERO) + effectiveWeight
                }
            }
        }
        val sectorHHI = sectorWeights.values.sumOf { it * it }.setScale(4, RoundingMode.HALF_UP)

        // Geographic concentration - computed from the same look-through result
        val regionWeights = mutableMapOf<String, BigDecimal>()
        if (result != null) {
            for ((_, exposure) in result.exposures) {
                val countryCode = exposure.instrument.country ?: continue
                val regionName = countryRegionLookup.getRegionForCountry(countryCode)
                regionWeights[regionName] = (regionWeights[regionName] ?: BigDecimal.ZERO) + exposure.effectiveWeight
            }
        }
        val maxRegionWeight = regionWeights.values.maxOrNull() ?: BigDecimal.ZERO

        // Asset type distribution
        val byType = positions.groupBy { it.instrumentType ?: InstrumentType.OTHER }
        val assetTypeDist = byType.mapValues { (_, posGroup) ->
            posGroup.sumOf { it.currentValue ?: BigDecimal.ZERO }
                .divide(totalValue, 4, RoundingMode.HALF_UP)
        }.mapKeys { it.key.name }

        // Calculate risk score
        val heldCount = (result?.exposures?.size ?: positions.size).coerceAtLeast(1)
        var riskScore = 0
        riskScore += (concentrationHHI.toDouble() * 250).toInt().coerceIn(0, 25)         // HHI -> 0-25
        riskScore += (top10Weight.toDouble() * 20).toInt().coerceIn(0, 20)                // top10 -> 0-20
        riskScore += (sectorHHI.toDouble() * 200).toInt().coerceIn(0, 20)                 // sector HHI -> 0-20
        riskScore += (maxRegionWeight.toDouble() * 15).toInt().coerceIn(0, 15)            // geo -> 0-15
        riskScore += if (assetTypeDist.size <= 1) 10 else (10 - assetTypeDist.size * 2).coerceIn(0, 10) // diversity -> 0-10
        riskScore += (50.0 / heldCount).toInt().coerceIn(0, 10)                           // holdings count -> 0-10
        riskScore = riskScore.coerceIn(0, 100)

        val riskLevel = when {
            riskScore <= 25 -> "LOW"
            riskScore <= 40 -> "MODERATE_LOW"
            riskScore <= 55 -> "MODERATE"
            riskScore <= 75 -> "MODERATE_HIGH"
            else -> "HIGH"
        }

        return RiskProfileResponse(
            riskScore = riskScore,
            riskLevel = riskLevel,
            factors = RiskFactorsDto(
                concentrationHHI = concentrationHHI,
                top10Concentration = top10Weight,
                sectorConcentrationHHI = sectorHHI,
                geographicConcentration = maxRegionWeight.setScale(4, RoundingMode.HALF_UP),
                assetTypeDistribution = assetTypeDist
            )
        )
    }

    private fun getPositions(userId: Long, connectionId: Long?): List<com.portfolio.broker.entity.BrokerPosition> {
        return if (connectionId != null) {
            positionRepository.findCurrentPositionsByConnectionId(connectionId)
        } else {
            positionRepository.findCurrentPositionsByUserIdFromActiveConnections(userId)
        }
    }

    private fun buildLookThroughPositions(
        positions: List<com.portfolio.broker.entity.BrokerPosition>,
        totalValue: BigDecimal
    ): List<PortfolioPositionRequest> {
        val relevantTickers = positions.mapNotNull { pos ->
            if (pos.instrumentType == InstrumentType.STOCK || pos.instrumentType == InstrumentType.ETF) pos.symbol else null
        }.toSet()
        val instrumentsByTicker = instrumentLookup.findByTickers(relevantTickers)

        return positions.mapNotNull { pos ->
            val weight = (pos.currentValue ?: BigDecimal.ZERO)
                .divide(totalValue, 8, RoundingMode.HALF_UP).toDouble()
            if (weight <= 0) return@mapNotNull null

            when (pos.instrumentType) {
                InstrumentType.STOCK -> {
                    val instrument = instrumentsByTicker[pos.symbol.uppercase()] ?: return@mapNotNull null
                    PortfolioPositionRequest("STOCK", instrument.id, weight)
                }
                InstrumentType.ETF -> {
                    val instrument = instrumentsByTicker[pos.symbol.uppercase()] ?: return@mapNotNull null
                    PortfolioPositionRequest("ETF", instrument.id, weight)
                }
                else -> null
            }
        }
    }
}
