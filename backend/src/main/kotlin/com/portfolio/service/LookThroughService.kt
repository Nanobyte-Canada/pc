package com.portfolio.service

import com.portfolio.dto.request.PortfolioPositionRequest
import com.portfolio.dto.response.ExposureSourceDto
import com.portfolio.entity.Etf
import com.portfolio.entity.EtfHolding
import com.portfolio.entity.HoldingDataSource
import com.portfolio.entity.Stock
import com.portfolio.repository.EtfHoldingRepository
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.EtfSectorAllocationFactsetRepository
import com.portfolio.repository.StockRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class LookThroughExposure(
    val stockId: Long,
    val stock: Stock,
    val effectiveWeight: BigDecimal,
    val sources: MutableList<ExposureSourceDto>
)

data class UnresolvedExposure(
    val rawTicker: String?,
    val rawName: String?,
    val effectiveWeight: BigDecimal,
    val sourceType: String,
    val sourceInstrumentId: Long,
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
    val etfDirectSectorExposures: Map<Long, EtfSectorExposure>
)

data class EtfSectorExposure(
    val etfId: Long,
    val etfSymbol: String,
    val portfolioWeight: BigDecimal,
    val sectorAllocations: Map<String, BigDecimal>
)

@Service
class LookThroughService(
    private val stockRepository: StockRepository,
    private val etfRepository: EtfRepository,
    private val etfHoldingRepository: EtfHoldingRepository,
    private val sectorAllocationFactsetRepository: EtfSectorAllocationFactsetRepository
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

        const val MAX_NESTED_DEPTH = 2
    }

    fun computeLookThroughWithQuality(
        positions: List<PortfolioPositionRequest>,
        analysisDate: LocalDate
    ): LookThroughResult {
        val exposureMap = mutableMapOf<Long, LookThroughExposure>()
        val unresolvedExposures = mutableListOf<UnresolvedExposure>()
        val etfSectorExposures = mutableMapOf<Long, EtfSectorExposure>()

        var totalHoldingsCount = 0
        var resolvedCount = 0
        var resolvedWeight = BigDecimal.ZERO
        var unresolvedWeight = BigDecimal.ZERO

        for (position in positions) {
            val weight = BigDecimal(position.weight)

            when (position.instrumentType.uppercase()) {
                "STOCK" -> {
                    val stock = stockRepository.findById(position.instrumentId).orElse(null) ?: continue
                    addStockExposure(exposureMap, stock, weight, "DIRECT", null, null)
                    totalHoldingsCount++
                    resolvedCount++
                    resolvedWeight += weight
                }

                "ETF" -> {
                    val etf = etfRepository.findById(position.instrumentId).orElse(null) ?: continue

                    val sectorAllocs = extractEtfSectorAllocations(etf)
                    if (sectorAllocs.isNotEmpty()) {
                        etfSectorExposures[etf.id] = EtfSectorExposure(
                            etfId = etf.id,
                            etfSymbol = etf.symbol,
                            portfolioWeight = weight,
                            sectorAllocations = sectorAllocs
                        )
                    }

                    val holdings = etfHoldingRepository.findLatestHoldingsIncludingUnresolved(
                        position.instrumentId, analysisDate
                    )

                    for (holding in holdings) {
                        totalHoldingsCount++
                        val holdingWeight = getEffectiveWeight(holding) ?: continue
                        val effectiveContribution = weight * holdingWeight

                        when {
                            holding.stock != null -> {
                                addStockExposure(
                                    exposureMap, holding.stock!!, effectiveContribution,
                                    "ETF", position.instrumentId, etf.symbol
                                )
                                resolvedCount++
                                resolvedWeight += effectiveContribution
                            }
                            holding.heldEtf != null -> {
                                processNestedEtf(
                                    exposureMap, unresolvedExposures,
                                    holding.heldEtf!!, effectiveContribution,
                                    analysisDate, 1
                                )
                            }
                            else -> {
                                unresolvedExposures.add(UnresolvedExposure(
                                    rawTicker = holding.rawTicker,
                                    rawName = holding.rawName,
                                    effectiveWeight = effectiveContribution,
                                    sourceType = "ETF",
                                    sourceInstrumentId = position.instrumentId,
                                    sourceSymbol = etf.symbol
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

    private fun addStockExposure(
        exposureMap: MutableMap<Long, LookThroughExposure>,
        stock: Stock,
        weight: BigDecimal,
        sourceType: String,
        instrumentId: Long?,
        instrumentSymbol: String?
    ) {
        val exposure = exposureMap.getOrPut(stock.id) {
            LookThroughExposure(
                stockId = stock.id,
                stock = stock,
                effectiveWeight = BigDecimal.ZERO,
                sources = mutableListOf()
            )
        }

        exposure.sources.add(ExposureSourceDto(
            type = sourceType,
            instrumentId = instrumentId,
            instrumentSymbol = instrumentSymbol,
            contribution = weight.toDouble()
        ))

        exposureMap[stock.id] = exposure.copy(
            effectiveWeight = exposure.effectiveWeight + weight
        )
    }

    private fun processNestedEtf(
        exposureMap: MutableMap<Long, LookThroughExposure>,
        unresolvedExposures: MutableList<UnresolvedExposure>,
        nestedEtf: Etf,
        parentWeight: BigDecimal,
        analysisDate: LocalDate,
        depth: Int
    ) {
        if (depth >= MAX_NESTED_DEPTH) {
            unresolvedExposures.add(UnresolvedExposure(
                rawTicker = nestedEtf.symbol,
                rawName = nestedEtf.name,
                effectiveWeight = parentWeight,
                sourceType = "NESTED_ETF",
                sourceInstrumentId = nestedEtf.id,
                sourceSymbol = nestedEtf.symbol
            ))
            return
        }

        val holdings = etfHoldingRepository.findLatestHoldingsIncludingUnresolved(nestedEtf.id, analysisDate)

        for (holding in holdings) {
            val holdingWeight = getEffectiveWeight(holding) ?: continue
            val effectiveContribution = parentWeight * holdingWeight

            when {
                holding.stock != null -> {
                    addStockExposure(
                        exposureMap, holding.stock!!, effectiveContribution,
                        "NESTED_ETF", nestedEtf.id, nestedEtf.symbol
                    )
                }
                holding.heldEtf != null -> {
                    processNestedEtf(
                        exposureMap, unresolvedExposures,
                        holding.heldEtf!!, effectiveContribution,
                        analysisDate, depth + 1
                    )
                }
                else -> {
                    unresolvedExposures.add(UnresolvedExposure(
                        rawTicker = holding.rawTicker,
                        rawName = holding.rawName,
                        effectiveWeight = effectiveContribution,
                        sourceType = "NESTED_ETF",
                        sourceInstrumentId = nestedEtf.id,
                        sourceSymbol = nestedEtf.symbol
                    ))
                }
            }
        }
    }

    private fun getEffectiveWeight(holding: EtfHolding): BigDecimal? {
        return when (holding.dataSource) {
            HoldingDataSource.ETF_COM -> holding.etfcomWeight ?: holding.weight
            HoldingDataSource.ALPHA_VANTAGE -> holding.avWeight ?: holding.weight
            else -> holding.weight
        }
    }

    private fun extractEtfSectorAllocations(etf: Etf): Map<String, BigDecimal> {
        val factsetSectors = sectorAllocationFactsetRepository.findLatestByEtfId(etf.id)
        if (factsetSectors.isEmpty()) return emptyMap()

        val allocations = mutableMapOf<String, BigDecimal>()
        for (sector in factsetSectors) {
            val weight = sector.weight ?: continue
            if (weight <= BigDecimal.ZERO) continue
            val gicsCode = FACTSET_SECTOR_TO_GICS[sector.sectorName]
            if (gicsCode != null) {
                allocations[gicsCode] = (allocations[gicsCode] ?: BigDecimal.ZERO) + weight
            }
        }
        return allocations
    }
}
