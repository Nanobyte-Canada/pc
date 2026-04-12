package com.portfolio.broker.service

import com.portfolio.broker.dto.*
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
class DashboardExposureService(
    private val positionRepository: BrokerPositionRepository,
    private val instrumentLookup: IngestionInstrumentLookupService,
    private val countryRegionLookup: CountryRegionLookupService,
    private val lookThroughService: LookThroughService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getSectorExposure(userId: Long, connectionId: Long? = null): SectorExposureResponse {
        val positions = getPositions(userId, connectionId)
        val totalValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        if (positions.isEmpty() || totalValue <= BigDecimal.ZERO) {
            return SectorExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO)
        }

        val lookThroughPositions = buildLookThroughPositions(positions, totalValue)
        if (lookThroughPositions.isEmpty()) {
            return SectorExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal(100))
        }

        val result = try {
            lookThroughService.computeLookThroughWithQuality(lookThroughPositions, LocalDate.now())
        } catch (e: Exception) {
            log.warn("Failed to compute sector exposure", e)
            return SectorExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO,
                warnings = listOf("Sector exposure data unavailable due to a calculation error"))
        }

        // Aggregate by sector -> industry group
        data class SectorAccumulator(
            var weight: BigDecimal = BigDecimal.ZERO,
            val industryGroups: MutableMap<String, Pair<String, BigDecimal>> = mutableMapOf() // code -> (name, weight)
        )

        val sectorMap = mutableMapOf<String, SectorAccumulator>() // sectorCode -> accumulator
        val sectorNames = mutableMapOf<String, String>()
        var unmappedWeight = BigDecimal.ZERO

        for ((_, exposure) in result.exposures) {
            val sectorCode = exposure.instrument.gicsSectorCode
            if (sectorCode != null) {
                val sectorName = LookThroughService.GICS_SECTOR_NAMES[sectorCode] ?: "Unknown"
                sectorNames[sectorCode] = sectorName
                val acc = sectorMap.getOrPut(sectorCode) { SectorAccumulator() }
                acc.weight += exposure.effectiveWeight
                val igCode = exposure.instrument.gicsIndustryGroupCode
                if (igCode != null) {
                    val igName = LookThroughService.GICS_INDUSTRY_GROUP_NAMES[igCode] ?: "Unknown"
                    val existing = acc.industryGroups[igCode]
                    acc.industryGroups[igCode] = Pair(igName, (existing?.second ?: BigDecimal.ZERO) + exposure.effectiveWeight)
                }
            } else {
                unmappedWeight += exposure.effectiveWeight
            }
        }

        // Also add ETF direct sector exposures for ETFs without holdings data
        for ((_, etfSector) in result.etfDirectSectorExposures) {
            for ((gicsCode, alloc) in etfSector.sectorAllocations) {
                val effectiveWeight = etfSector.portfolioWeight * alloc
                val sectorName = LookThroughService.GICS_SECTOR_NAMES[gicsCode] ?: "Unknown"
                sectorNames[gicsCode] = sectorName
                val acc = sectorMap.getOrPut(gicsCode) { SectorAccumulator() }
                acc.weight += effectiveWeight
            }
        }

        val sectors = sectorMap.entries.map { (code, acc) ->
            SectorExposureDto(
                sectorCode = code,
                sectorName = sectorNames[code] ?: "Unknown",
                weight = acc.weight.setScale(4, RoundingMode.HALF_UP),
                industryGroups = acc.industryGroups.entries.map { (igCode, pair) ->
                    IndustryGroupExposureDto(
                        code = igCode,
                        name = pair.first,
                        weight = pair.second.setScale(4, RoundingMode.HALF_UP)
                    )
                }.sortedByDescending { it.weight }
            )
        }.sortedByDescending { it.weight }

        return SectorExposureResponse(
            sectors = sectors,
            coveragePercent = result.quality.coveragePercent,
            unmappedWeight = unmappedWeight.setScale(4, RoundingMode.HALF_UP)
        )
    }

    fun getGeographyExposure(userId: Long, connectionId: Long? = null): GeographyExposureResponse {
        val positions = getPositions(userId, connectionId)
        val totalValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        if (positions.isEmpty() || totalValue <= BigDecimal.ZERO) {
            return GeographyExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO)
        }

        val lookThroughPositions = buildLookThroughPositions(positions, totalValue)
        if (lookThroughPositions.isEmpty()) {
            return GeographyExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal(100))
        }

        val result = try {
            lookThroughService.computeLookThroughWithQuality(lookThroughPositions, LocalDate.now())
        } catch (e: Exception) {
            log.warn("Failed to compute geography exposure", e)
            return GeographyExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO,
                warnings = listOf("Geography exposure data unavailable due to a calculation error"))
        }

        data class RegionAccumulator(
            var weight: BigDecimal = BigDecimal.ZERO,
            val countries: MutableMap<String, Pair<String, BigDecimal>> = mutableMapOf()
        )
        val regionMap = mutableMapOf<String, RegionAccumulator>()
        var unmappedWeight = BigDecimal.ZERO

        for ((_, exposure) in result.exposures) {
            val countryCode = exposure.instrument.country
            if (countryCode == null) {
                unmappedWeight += exposure.effectiveWeight
                continue
            }

            val regionName = countryRegionLookup.getRegionForCountry(countryCode)
            val countryName = countryRegionLookup.getCountryName(countryCode)

            val acc = regionMap.getOrPut(regionName) { RegionAccumulator() }
            acc.weight += exposure.effectiveWeight
            val existing = acc.countries[countryCode]
            acc.countries[countryCode] = Pair(
                countryName,
                (existing?.second ?: BigDecimal.ZERO) + exposure.effectiveWeight
            )
        }

        val regions = regionMap.entries.map { (name, acc) ->
            RegionExposureDto(
                name = name,
                weight = acc.weight.setScale(4, RoundingMode.HALF_UP),
                countries = acc.countries.entries.map { (code, pair) ->
                    CountryExposureDto(
                        code = code,
                        name = pair.first,
                        weight = pair.second.setScale(4, RoundingMode.HALF_UP)
                    )
                }.sortedByDescending { it.weight }
            )
        }.sortedByDescending { it.weight }

        return GeographyExposureResponse(
            regions = regions,
            coveragePercent = result.quality.coveragePercent,
            unmappedWeight = unmappedWeight.setScale(4, RoundingMode.HALF_UP)
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
