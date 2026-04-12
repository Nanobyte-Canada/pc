package com.portfolio.service

import com.portfolio.dto.request.PortfolioPositionRequest
import com.portfolio.dto.response.ExposureSourceDto
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Represents a resolved stock exposure in the look-through analysis.
 * Uses IngestionInstrument instead of the old Stock JPA entity.
 */
data class LookThroughExposure(
    val stockId: Long,
    val instrument: IngestionInstrument,
    val effectiveWeight: BigDecimal,
    val sources: MutableList<ExposureSourceDto>
)

data class UnresolvedExposure(
    val rawTicker: String?,
    val rawName: String?,
    val effectiveWeight: BigDecimal,
    val sourceType: String,
    val sourceSymbol: String
)

data class LookThroughQuality(
    val totalHoldingsCount: Int,
    val resolvedCount: Int,
    val unresolvedCount: Int,
    val resolvedWeight: BigDecimal,
    val unresolvedWeight: BigDecimal,
    val coveragePercent: BigDecimal
)

data class LookThroughResult(
    val exposures: Map<Long, LookThroughExposure>,
    val unresolvedExposures: List<UnresolvedExposure>,
    val quality: LookThroughQuality,
    val etfDirectSectorExposures: Map<String, EtfSectorExposure>
)

data class EtfSectorExposure(
    val etfTicker: String,
    val portfolioWeight: BigDecimal,
    val sectorAllocations: Map<String, BigDecimal>
)

/**
 * Look-through analysis service that decomposes ETFs into underlying stock holdings.
 * Uses cross-schema queries to ingestion.instruments / ingestion.provider_raw_data
 * via IngestionInstrumentLookupService instead of the old Stock/Etf/EtfHolding JPA entities.
 *
 * PortfolioPositionRequest now uses ticker-based resolution:
 * - instrumentType: "STOCK" or "ETF"
 * - instrumentId: ingestion.instruments.id (used for identification)
 * - weight: portfolio weight as a decimal
 */
@Service
class LookThroughService(
    private val instrumentLookup: IngestionInstrumentLookupService
) {
    companion object {
        val FACTSET_SECTOR_TO_GICS = mapOf(
            "Technology" to "45",
            "Information Technology" to "45",
            "Communication Services" to "50",
            "Telecommunications" to "50",
            "Consumer Discretionary" to "25",
            "Consumer Cyclical" to "25",
            "Consumer Staples" to "30",
            "Consumer Defensive" to "30",
            "Health Care" to "35",
            "Healthcare" to "35",
            "Industrials" to "20",
            "Utilities" to "55",
            "Materials" to "15",
            "Basic Materials" to "15",
            "Energy" to "10",
            "Financials" to "40",
            "Financial Services" to "40",
            "Real Estate" to "60"
        )

        val GICS_SECTOR_NAMES = mapOf(
            "10" to "Energy",
            "15" to "Materials",
            "20" to "Industrials",
            "25" to "Consumer Discretionary",
            "30" to "Consumer Staples",
            "35" to "Health Care",
            "40" to "Financials",
            "45" to "Information Technology",
            "50" to "Communication Services",
            "55" to "Utilities",
            "60" to "Real Estate"
        )

        val GICS_INDUSTRY_GROUP_NAMES = mapOf(
            "1010" to "Energy",
            "1510" to "Materials",
            "2010" to "Capital Goods",
            "2020" to "Commercial & Professional Services",
            "2030" to "Transportation",
            "2510" to "Automobiles & Components",
            "2520" to "Consumer Durables & Apparel",
            "2530" to "Consumer Services",
            "2540" to "Consumer Discretionary Distribution & Retail",
            "2550" to "Consumer Staples Distribution & Retail",
            "3010" to "Food, Beverage & Tobacco",
            "3020" to "Household & Personal Products",
            "3030" to "Food & Staples Retailing",
            "3510" to "Health Care Equipment & Services",
            "3520" to "Pharmaceuticals, Biotechnology & Life Sciences",
            "4010" to "Banks",
            "4020" to "Financial Services",
            "4030" to "Insurance",
            "4510" to "Software & Services",
            "4520" to "Technology Hardware & Equipment",
            "4530" to "Semiconductors & Semiconductor Equipment",
            "5010" to "Telecommunication Services",
            "5020" to "Media & Entertainment",
            "5510" to "Utilities",
            "6010" to "Equity Real Estate Investment Trusts (REITs)",
            "6020" to "Real Estate Management & Development"
        )

        val AV_INDUSTRY_TO_GICS_IG = mapOf(
            "LIFE SCIENCES TOOLS & SERVICES" to "3520",
            "PHARMACEUTICALS" to "3520",
            "BIOTECHNOLOGY" to "3520",
            "HEALTH CARE EQUIPMENT & SUPPLIES" to "3510",
            "HEALTH CARE PROVIDERS & SERVICES" to "3510",
            "SOFTWARE" to "4510",
            "IT SERVICES" to "4510",
            "INTERACTIVE MEDIA & SERVICES" to "5020",
            "ENTERTAINMENT" to "5020",
            "MEDIA" to "5020",
            "SEMICONDUCTORS & SEMICONDUCTOR EQUIPMENT" to "4530",
            "TECHNOLOGY HARDWARE, STORAGE & PERIPHERALS" to "4520",
            "ELECTRONIC EQUIPMENT, INSTRUMENTS & COMPONENTS" to "4520",
            "COMMUNICATIONS EQUIPMENT" to "4520",
            "BANKS" to "4010",
            "DIVERSIFIED FINANCIAL SERVICES" to "4020",
            "CAPITAL MARKETS" to "4020",
            "CONSUMER FINANCE" to "4020",
            "INSURANCE" to "4030",
            "AUTOMOBILES" to "2510",
            "AUTO COMPONENTS" to "2510",
            "HOUSEHOLD DURABLES" to "2520",
            "LEISURE PRODUCTS" to "2520",
            "TEXTILES, APPAREL & LUXURY GOODS" to "2520",
            "HOTELS, RESTAURANTS & LEISURE" to "2530",
            "DIVERSIFIED CONSUMER SERVICES" to "2530",
            "SPECIALTY RETAIL" to "2540",
            "INTERNET & DIRECT MARKETING RETAIL" to "2540",
            "DISTRIBUTORS" to "2540",
            "MULTILINE RETAIL" to "2540",
            "FOOD & STAPLES RETAILING" to "3030",
            "FOOD PRODUCTS" to "3010",
            "BEVERAGES" to "3010",
            "TOBACCO" to "3010",
            "HOUSEHOLD PRODUCTS" to "3020",
            "PERSONAL PRODUCTS" to "3020",
            "AEROSPACE & DEFENSE" to "2010",
            "BUILDING PRODUCTS" to "2010",
            "CONSTRUCTION & ENGINEERING" to "2010",
            "ELECTRICAL EQUIPMENT" to "2010",
            "INDUSTRIAL CONGLOMERATES" to "2010",
            "MACHINERY" to "2010",
            "TRADING COMPANIES & DISTRIBUTORS" to "2010",
            "COMMERCIAL SERVICES & SUPPLIES" to "2020",
            "PROFESSIONAL SERVICES" to "2020",
            "AIR FREIGHT & LOGISTICS" to "2030",
            "AIRLINES" to "2030",
            "MARINE" to "2030",
            "ROAD & RAIL" to "2030",
            "TRANSPORTATION INFRASTRUCTURE" to "2030",
            "OIL, GAS & CONSUMABLE FUELS" to "1010",
            "ENERGY EQUIPMENT & SERVICES" to "1010",
            "CHEMICALS" to "1510",
            "CONSTRUCTION MATERIALS" to "1510",
            "CONTAINERS & PACKAGING" to "1510",
            "METALS & MINING" to "1510",
            "PAPER & FOREST PRODUCTS" to "1510",
            "ELECTRIC UTILITIES" to "5510",
            "GAS UTILITIES" to "5510",
            "MULTI-UTILITIES" to "5510",
            "WATER UTILITIES" to "5510",
            "INDEPENDENT POWER AND RENEWABLE ELECTRICITY PRODUCERS" to "5510",
            "EQUITY REAL ESTATE INVESTMENT TRUSTS (REITS)" to "6010",
            "REAL ESTATE MANAGEMENT & DEVELOPMENT" to "6020",
            "DIVERSIFIED TELECOMMUNICATION SERVICES" to "5010",
            "WIRELESS TELECOMMUNICATION SERVICES" to "5010"
        )

        const val MAX_NESTED_DEPTH = 2
    }

    /**
     * Compute look-through exposure with quality metrics.
     * Accepts ticker-based positions and resolves them through the ingestion schema.
     */
    fun computeLookThroughWithQuality(
        positions: List<PortfolioPositionRequest>,
        @Suppress("UNUSED_PARAMETER") analysisDate: LocalDate
    ): LookThroughResult {
        val exposureMap = mutableMapOf<Long, LookThroughExposure>()
        val unresolvedExposures = mutableListOf<UnresolvedExposure>()
        val etfSectorExposures = mutableMapOf<String, EtfSectorExposure>()

        var totalHoldingsCount = 0
        var resolvedCount = 0
        var resolvedWeight = BigDecimal.ZERO
        var unresolvedWeight = BigDecimal.ZERO

        // Pre-fetch all instruments by ID for efficiency
        val instrumentsById = positions.associate { pos ->
            pos.instrumentId to instrumentLookup.findById(pos.instrumentId)
        }

        for (position in positions) {
            val weight = BigDecimal(position.weight)
            val instrument = instrumentsById[position.instrumentId] ?: continue

            when (position.instrumentType.uppercase()) {
                "STOCK" -> {
                    addExposure(exposureMap, instrument, weight, "DIRECT", null)
                    totalHoldingsCount++
                    resolvedCount++
                    resolvedWeight += weight
                }

                "ETF" -> {
                    // Get sector allocations for this ETF
                    val sectorAllocs = extractEtfSectorAllocations(instrument.ticker)
                    if (sectorAllocs.isNotEmpty()) {
                        etfSectorExposures[instrument.ticker] = EtfSectorExposure(
                            etfTicker = instrument.ticker,
                            portfolioWeight = weight,
                            sectorAllocations = sectorAllocs
                        )
                    }

                    // Get ETF holdings from JSONB
                    val holdings = instrumentLookup.getEtfHoldings(instrument.ticker)

                    for (holding in holdings) {
                        totalHoldingsCount++
                        val holdingWeight = holding.weight ?: continue
                        val effectiveContribution = weight * holdingWeight

                        when {
                            holding.resolvedInstrument != null && !holding.isEtf -> {
                                addExposure(
                                    exposureMap, holding.resolvedInstrument, effectiveContribution,
                                    "ETF", instrument.ticker
                                )
                                resolvedCount++
                                resolvedWeight += effectiveContribution
                            }
                            holding.resolvedInstrument != null && holding.isEtf -> {
                                processNestedEtf(
                                    exposureMap, unresolvedExposures,
                                    holding.resolvedInstrument, effectiveContribution,
                                    1
                                )
                            }
                            else -> {
                                unresolvedExposures.add(UnresolvedExposure(
                                    rawTicker = holding.ticker,
                                    rawName = holding.name,
                                    effectiveWeight = effectiveContribution,
                                    sourceType = "ETF",
                                    sourceSymbol = instrument.ticker
                                ))
                                unresolvedWeight += effectiveContribution
                            }
                        }
                    }
                }
            }
        }

        val totalWeight = resolvedWeight + unresolvedWeight
        val coveragePercent = if (totalWeight > BigDecimal.ZERO) {
            (resolvedWeight / totalWeight * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return LookThroughResult(
            exposures = exposureMap,
            unresolvedExposures = unresolvedExposures,
            quality = LookThroughQuality(
                totalHoldingsCount = totalHoldingsCount,
                resolvedCount = resolvedCount,
                unresolvedCount = unresolvedExposures.size,
                resolvedWeight = resolvedWeight.setScale(6, RoundingMode.HALF_UP),
                unresolvedWeight = unresolvedWeight.setScale(6, RoundingMode.HALF_UP),
                coveragePercent = coveragePercent
            ),
            etfDirectSectorExposures = etfSectorExposures
        )
    }

    fun computeLookThroughExposure(
        positions: List<PortfolioPositionRequest>,
        analysisDate: LocalDate
    ): Map<Long, LookThroughExposure> {
        return computeLookThroughWithQuality(positions, analysisDate).exposures
    }

    private fun addExposure(
        exposureMap: MutableMap<Long, LookThroughExposure>,
        instrument: IngestionInstrument,
        weight: BigDecimal,
        sourceType: String,
        instrumentSymbol: String?
    ) {
        val exposure = exposureMap.getOrPut(instrument.id) {
            LookThroughExposure(
                stockId = instrument.id,
                instrument = instrument,
                effectiveWeight = BigDecimal.ZERO,
                sources = mutableListOf()
            )
        }

        exposure.sources.add(ExposureSourceDto(
            type = sourceType,
            instrumentId = null,
            instrumentSymbol = instrumentSymbol,
            contribution = weight.toDouble()
        ))

        exposureMap[instrument.id] = exposure.copy(
            effectiveWeight = exposure.effectiveWeight + weight
        )
    }

    private fun processNestedEtf(
        exposureMap: MutableMap<Long, LookThroughExposure>,
        unresolvedExposures: MutableList<UnresolvedExposure>,
        nestedEtf: IngestionInstrument,
        parentWeight: BigDecimal,
        depth: Int
    ) {
        if (depth >= MAX_NESTED_DEPTH) {
            unresolvedExposures.add(UnresolvedExposure(
                rawTicker = nestedEtf.ticker,
                rawName = nestedEtf.name,
                effectiveWeight = parentWeight,
                sourceType = "NESTED_ETF",
                sourceSymbol = nestedEtf.ticker
            ))
            return
        }

        val holdings = instrumentLookup.getEtfHoldings(nestedEtf.ticker)

        for (holding in holdings) {
            val holdingWeight = holding.weight ?: continue
            val effectiveContribution = parentWeight * holdingWeight

            when {
                holding.resolvedInstrument != null && !holding.isEtf -> {
                    addExposure(
                        exposureMap, holding.resolvedInstrument, effectiveContribution,
                        "NESTED_ETF", nestedEtf.ticker
                    )
                }
                holding.resolvedInstrument != null && holding.isEtf -> {
                    processNestedEtf(
                        exposureMap, unresolvedExposures,
                        holding.resolvedInstrument, effectiveContribution,
                        depth + 1
                    )
                }
                else -> {
                    unresolvedExposures.add(UnresolvedExposure(
                        rawTicker = holding.ticker,
                        rawName = holding.name,
                        effectiveWeight = effectiveContribution,
                        sourceType = "NESTED_ETF",
                        sourceSymbol = nestedEtf.ticker
                    ))
                }
            }
        }
    }

    private fun extractEtfSectorAllocations(ticker: String): Map<String, BigDecimal> {
        val sectorAllocs = instrumentLookup.getEtfSectorAllocations(ticker)
        if (sectorAllocs.isEmpty()) return emptyMap()

        val allocations = mutableMapOf<String, BigDecimal>()
        for (sector in sectorAllocs) {
            if (sector.weight <= BigDecimal.ZERO) continue
            val gicsCode = FACTSET_SECTOR_TO_GICS[sector.sectorName]
            if (gicsCode != null) {
                allocations[gicsCode] = (allocations[gicsCode] ?: BigDecimal.ZERO) + sector.weight
            }
        }
        return allocations
    }
}
