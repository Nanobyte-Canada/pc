package com.portfolio.broker.service

import com.portfolio.broker.entity.AccountAnalytics
import com.portfolio.broker.entity.InstrumentType
import com.portfolio.broker.repository.AccountAnalyticsRepository
import com.portfolio.broker.repository.BrokerActivityRepository
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import com.portfolio.broker.repository.BrokerPositionRepository
import com.portfolio.dto.request.PortfolioPositionRequest
import com.portfolio.service.CountryRegionLookupService
import com.portfolio.service.IngestionInstrumentLookupService
import com.portfolio.service.LookThroughResult
import com.portfolio.service.LookThroughService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Pre-computes analytics for a broker connection and persists the snapshot
 * in the account_analytics table. Triggered after position sync.
 */
@Service
class AccountAnalyticsComputeService(
    private val positionRepository: BrokerPositionRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val instrumentLookup: IngestionInstrumentLookupService,
    private val lookThroughService: LookThroughService,
    private val countryRegionLookup: CountryRegionLookupService,
    private val exchangeRateService: ExchangeRateService,
    private val analyticsRepository: AccountAnalyticsRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val activityRepository: BrokerActivityRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private data class WeightedPosition(
        val symbol: String,
        val instrumentType: InstrumentType?,
        val weight: BigDecimal // percentage 0-100
    )

    @Transactional
    fun computeForConnection(connectionId: Long): AccountAnalytics? {
        val connection = connectionRepository.findById(connectionId).orElse(null) ?: run {
            log.warn("Connection {} not found for analytics computation", connectionId)
            return null
        }
        val userId = connection.user.id

        val positions = positionRepository.findCurrentPositionsByConnectionId(connectionId)
        if (positions.isEmpty()) {
            log.debug("No positions for connection {}, storing empty analytics", connectionId)
            return upsertEmpty(connectionId, userId)
        }

        // Normalize all position values to CAD
        val today = LocalDate.now()
        val rateCache = mutableMapOf<String, BigDecimal>()

        val normalizedValues = positions.mapNotNull { pos ->
            val value = pos.currentValue ?: return@mapNotNull null
            if (value <= BigDecimal.ZERO) return@mapNotNull null
            val currency = pos.currency.uppercase()
            val rate = rateCache.getOrPut(currency) {
                exchangeRateService.getRate(currency, today) ?: BigDecimal.ONE
            }
            Triple(pos.symbol, pos.instrumentType, value.multiply(rate).setScale(2, RoundingMode.HALF_UP))
        }

        val totalValueCAD = normalizedValues.sumOf { it.third }
        if (totalValueCAD <= BigDecimal.ZERO) {
            return upsertEmpty(connectionId, userId)
        }

        // Compute position weights (% of total, sum = 100)
        val weighted = normalizedValues.map { (symbol, type, cadValue) ->
            WeightedPosition(
                symbol = symbol,
                instrumentType = type,
                weight = cadValue.divide(totalValueCAD, 8, RoundingMode.HALF_UP).multiply(BigDecimal(100))
            )
        }

        // Resolve tickers to ingestion instruments
        val relevantTickers = weighted
            .filter { it.instrumentType in setOf(InstrumentType.STOCK, InstrumentType.ETF, InstrumentType.MUTUAL_FUND) }
            .map { it.symbol }
            .toSet()
        val instrumentsByTicker = instrumentLookup.findByTickers(relevantTickers)

        // Build look-through positions for decomposition
        val lookThroughPositions = weighted.mapNotNull { wp ->
            val instrument = instrumentsByTicker[wp.symbol.uppercase()] ?: return@mapNotNull null
            when (wp.instrumentType) {
                InstrumentType.STOCK, InstrumentType.ETF ->
                    PortfolioPositionRequest(wp.instrumentType.name, instrument.id, wp.weight.toDouble() / 100.0)
                else -> null
            }
        }

        // Run look-through decomposition
        val lookThroughResult = try {
            if (lookThroughPositions.isNotEmpty()) {
                lookThroughService.computeLookThroughWithQuality(lookThroughPositions, today)
            } else null
        } catch (e: Exception) {
            log.warn("Look-through computation failed for connection {}: {}", connectionId, e.message)
            null
        }

        // Compute all analytics
        val sectorExposure = computeSectorExposure(weighted, lookThroughResult)
        val geographyExposure = computeGeographyExposure(lookThroughResult)
        val riskProfile = computeRiskProfile(lookThroughResult)
        val holdings = computeHoldings(lookThroughResult)
        val merWeighted = computeWeightedMER(weighted)
        val coveragePercent = lookThroughResult?.quality?.coveragePercent ?: BigDecimal.ZERO

        // Upsert
        val existing = analyticsRepository.findByConnectionId(connectionId)
        val analytics = existing ?: AccountAnalytics(connection = connection, userId = userId)
        analytics.sectorExposure = sectorExposure
        analytics.geographyExposure = geographyExposure
        analytics.riskProfile = riskProfile
        analytics.holdings = holdings
        analytics.merWeighted = merWeighted
        analytics.totalValue = totalValueCAD
        analytics.coveragePercent = coveragePercent
        analytics.positionsCount = positions.size
        analytics.xirr = computeXirr(connectionId)
        analytics.totalReturn = computeTotalReturn(connectionId)
        analytics.totalReturnPct = computeTotalReturnPct(connectionId)
        analytics.dividendYield = computeDividendYield(connectionId, totalValueCAD)
        analytics.computedAt = OffsetDateTime.now()
        analytics.updatedAt = OffsetDateTime.now()

        return analyticsRepository.save(analytics)
    }

    // -------------------------------------------------------------------------
    // Sector Exposure — weights stored as decimals (0-1), must sum to 1.0
    // -------------------------------------------------------------------------

    private fun computeSectorExposure(
        weighted: List<WeightedPosition>,
        result: LookThroughResult?
    ): Map<String, Any?> {
        data class IGAcc(val name: String, var weight: BigDecimal = BigDecimal.ZERO)
        data class SectorAcc(val name: String, var weight: BigDecimal = BigDecimal.ZERO, val igs: MutableMap<String, IGAcc> = mutableMapOf())

        val sectorMap = mutableMapOf<String, SectorAcc>()
        var mappedWeight = BigDecimal.ZERO

        if (result != null) {
            // Resolved holdings — effectiveWeight is already a decimal (0-1)
            for ((_, exposure) in result.exposures) {
                val w = exposure.effectiveWeight
                val sectorCode = exposure.instrument.gicsSectorCode
                if (sectorCode != null) {
                    val sectorName = LookThroughService.GICS_SECTOR_NAMES[sectorCode] ?: "Unknown"
                    val acc = sectorMap.getOrPut(sectorCode) { SectorAcc(sectorName) }
                    acc.weight += w
                    mappedWeight += w
                    val igCode = exposure.instrument.gicsIndustryGroupCode
                    if (igCode != null) {
                        val igName = LookThroughService.GICS_INDUSTRY_GROUP_NAMES[igCode] ?: "Unknown"
                        val igAcc = acc.igs.getOrPut(igCode) { IGAcc(igName) }
                        igAcc.weight += w
                    }
                }
            }

            // ETF direct sector fallback
            for ((_, etfSector) in result.etfDirectSectorExposures) {
                for ((gicsCode, alloc) in etfSector.sectorAllocations) {
                    val w = etfSector.portfolioWeight.multiply(alloc)
                    val sectorName = LookThroughService.GICS_SECTOR_NAMES[gicsCode] ?: "Unknown"
                    val acc = sectorMap.getOrPut(gicsCode) { SectorAcc(sectorName) }
                    acc.weight += w
                    mappedWeight += w
                }
            }
        }

        // Everything not mapped goes to Unknown
        val unmappedWeight = (BigDecimal.ONE - mappedWeight).coerceAtLeast(BigDecimal.ZERO)
        if (unmappedWeight > BigDecimal("0.0001")) {
            val acc = sectorMap.getOrPut("UNKNOWN") { SectorAcc("Unknown") }
            acc.weight += unmappedWeight
        }

        // Normalize: adjust largest sector so total = exactly 1.0
        val totalSectorWeight = sectorMap.values.sumOf { it.weight }
        if (totalSectorWeight > BigDecimal.ZERO) {
            val drift = BigDecimal.ONE - totalSectorWeight
            if (drift.abs() > BigDecimal("0.0001")) {
                val largest = sectorMap.maxByOrNull { it.value.weight }
                if (largest != null) largest.value.weight += drift
            }
        }

        val sectors = sectorMap.entries.sortedByDescending { it.value.weight }.map { (code, acc) ->
            mapOf(
                "code" to code,
                "name" to acc.name,
                "weight" to acc.weight.setScale(4, RoundingMode.HALF_UP),
                "industryGroups" to acc.igs.entries.sortedByDescending { it.value.weight }.map { (igCode, igAcc) ->
                    mapOf("code" to igCode, "name" to igAcc.name, "weight" to igAcc.weight.setScale(4, RoundingMode.HALF_UP))
                }
            )
        }

        return mapOf("sectors" to sectors, "coveragePercent" to mappedWeight.setScale(2, RoundingMode.HALF_UP))
    }

    // -------------------------------------------------------------------------
    // Geography Exposure — weights stored as decimals (0-1), must sum to 1.0
    // -------------------------------------------------------------------------

    private fun computeGeographyExposure(result: LookThroughResult?): Map<String, Any?> {
        data class CountryAcc(val name: String, var weight: BigDecimal = BigDecimal.ZERO)
        data class RegionAcc(val name: String, var weight: BigDecimal = BigDecimal.ZERO, val countries: MutableMap<String, CountryAcc> = mutableMapOf())

        val regionMap = mutableMapOf<String, RegionAcc>()
        var mappedWeight = BigDecimal.ZERO

        if (result != null) {
            for ((_, exposure) in result.exposures) {
                val w = exposure.effectiveWeight
                val countryCode = exposure.instrument.country
                if (countryCode != null) {
                    val regionName = countryRegionLookup.getRegionForCountry(countryCode)
                    val countryName = countryRegionLookup.getCountryName(countryCode)
                    val regionAcc = regionMap.getOrPut(regionName) { RegionAcc(regionName) }
                    regionAcc.weight += w
                    val countryAcc = regionAcc.countries.getOrPut(countryCode) { CountryAcc(countryName) }
                    countryAcc.weight += w
                    mappedWeight += w
                }
            }
        }

        val unmappedWeight = (BigDecimal.ONE - mappedWeight).coerceAtLeast(BigDecimal.ZERO)
        if (unmappedWeight > BigDecimal("0.0001")) {
            val acc = regionMap.getOrPut("Unknown") { RegionAcc("Unknown") }
            acc.weight += unmappedWeight
        }

        // Normalize: adjust largest region so total = exactly 1.0
        val totalRegionWeight = regionMap.values.sumOf { it.weight }
        if (totalRegionWeight > BigDecimal.ZERO) {
            val drift = BigDecimal.ONE - totalRegionWeight
            if (drift.abs() > BigDecimal("0.0001")) {
                val largest = regionMap.maxByOrNull { it.value.weight }
                if (largest != null) largest.value.weight += drift
            }
        }

        val regions = regionMap.entries.sortedByDescending { it.value.weight }.map { (_, acc) ->
            mapOf(
                "name" to acc.name,
                "weight" to acc.weight.setScale(4, RoundingMode.HALF_UP),
                "countries" to acc.countries.entries.sortedByDescending { it.value.weight }.map { (code, cAcc) ->
                    mapOf("code" to code, "name" to cAcc.name, "weight" to cAcc.weight.setScale(4, RoundingMode.HALF_UP))
                }
            )
        }

        return mapOf("regions" to regions, "coveragePercent" to mappedWeight.setScale(2, RoundingMode.HALF_UP))
    }

    // -------------------------------------------------------------------------
    // Risk Profile
    // -------------------------------------------------------------------------

    private fun computeRiskProfile(result: LookThroughResult?): Map<String, Any?> {
        if (result == null || result.exposures.isEmpty()) {
            return mapOf("score" to 0, "level" to "LOW", "factors" to emptyMap<String, Any>())
        }

        val weights = result.exposures.values.map { it.effectiveWeight }

        val concentrationHHI = weights.sumOf { it * it }.setScale(4, RoundingMode.HALF_UP)
        val top10Weight = weights.sortedDescending().take(10).fold(BigDecimal.ZERO) { acc, w -> acc + w }
            .setScale(4, RoundingMode.HALF_UP)

        val sectorWeights = mutableMapOf<String, BigDecimal>()
        for ((_, exposure) in result.exposures) {
            val code = exposure.instrument.gicsSectorCode ?: continue
            sectorWeights[code] = (sectorWeights[code] ?: BigDecimal.ZERO) + exposure.effectiveWeight
        }
        for ((_, etfSector) in result.etfDirectSectorExposures) {
            for ((gicsCode, alloc) in etfSector.sectorAllocations) {
                sectorWeights[gicsCode] = (sectorWeights[gicsCode] ?: BigDecimal.ZERO) + etfSector.portfolioWeight * alloc
            }
        }
        val sectorHHI = sectorWeights.values.sumOf { it * it }.setScale(4, RoundingMode.HALF_UP)

        val regionWeights = mutableMapOf<String, BigDecimal>()
        for ((_, exposure) in result.exposures) {
            val countryCode = exposure.instrument.country ?: continue
            val region = countryRegionLookup.getRegionForCountry(countryCode)
            regionWeights[region] = (regionWeights[region] ?: BigDecimal.ZERO) + exposure.effectiveWeight
        }
        val maxRegionWeight = regionWeights.values.maxOrNull() ?: BigDecimal.ZERO

        val heldCount = result.exposures.size.coerceAtLeast(1)
        var riskScore = 0
        riskScore += (concentrationHHI.toDouble() * 250).toInt().coerceIn(0, 25)
        riskScore += (top10Weight.toDouble() * 20).toInt().coerceIn(0, 20)
        riskScore += (sectorHHI.toDouble() * 200).toInt().coerceIn(0, 20)
        riskScore += (maxRegionWeight.toDouble() * 15).toInt().coerceIn(0, 15)
        riskScore += (50.0 / heldCount).toInt().coerceIn(0, 10)
        riskScore = riskScore.coerceIn(0, 100)

        val riskLevel = when {
            riskScore <= 25 -> "LOW"
            riskScore <= 40 -> "MODERATE_LOW"
            riskScore <= 55 -> "MODERATE"
            riskScore <= 75 -> "MODERATE_HIGH"
            else -> "HIGH"
        }

        return mapOf(
            "score" to riskScore,
            "level" to riskLevel,
            "factors" to mapOf(
                "concentrationHHI" to concentrationHHI,
                "top10Concentration" to top10Weight,
                "sectorConcentrationHHI" to sectorHHI,
                "geographicConcentration" to maxRegionWeight.setScale(4, RoundingMode.HALF_UP)
            )
        )
    }

    // -------------------------------------------------------------------------
    // Holdings List
    // -------------------------------------------------------------------------

    private fun computeHoldings(result: LookThroughResult?): List<Map<String, Any?>> {
        if (result == null) return emptyList()

        return result.exposures.entries.sortedByDescending { it.value.effectiveWeight }.map { (_, exposure) ->
            val sectorCode = exposure.instrument.gicsSectorCode
            val igCode = exposure.instrument.gicsIndustryGroupCode
            val countryCode = exposure.instrument.country

            mapOf(
                "symbol" to exposure.instrument.ticker,
                "name" to exposure.instrument.name,
                "effectiveWeight" to exposure.effectiveWeight.setScale(4, RoundingMode.HALF_UP),
                "sector" to (sectorCode?.let { LookThroughService.GICS_SECTOR_NAMES[it] }),
                "sectorCode" to sectorCode,
                "industryGroup" to (igCode?.let { LookThroughService.GICS_INDUSTRY_GROUP_NAMES[it] }),
                "industryGroupCode" to igCode,
                "country" to (countryCode?.let { countryRegionLookup.getCountryName(it) }),
                "countryCode" to countryCode,
                "region" to (countryCode?.let { countryRegionLookup.getRegionForCountry(it) }),
                "sources" to exposure.sources.map { source ->
                    mapOf(
                        "type" to source.type,
                        "instrumentSymbol" to source.instrumentSymbol,
                        "contribution" to BigDecimal.valueOf(source.contribution).setScale(4, RoundingMode.HALF_UP)
                    )
                }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Weighted MER
    // -------------------------------------------------------------------------

    private fun computeWeightedMER(weighted: List<WeightedPosition>): BigDecimal {
        var totalMER = BigDecimal.ZERO
        for (wp in weighted) {
            if (wp.instrumentType != InstrumentType.ETF && wp.instrumentType != InstrumentType.MUTUAL_FUND) continue
            val expenseRatio = instrumentLookup.getEtfExpenseRatio(wp.symbol) ?: continue
            // weight is percentage (0-100), expenseRatio is decimal (e.g., 0.0009)
            totalMER += wp.weight.divide(HUNDRED, 8, RoundingMode.HALF_UP).multiply(expenseRatio)
        }
        return totalMER.setScale(4, RoundingMode.HALF_UP)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun upsertEmpty(connectionId: Long, userId: Long): AccountAnalytics {
        val connection = connectionRepository.findById(connectionId).orElseThrow()
        val existing = analyticsRepository.findByConnectionId(connectionId)
        val analytics = existing ?: AccountAnalytics(connection = connection, userId = userId)
        analytics.sectorExposure = mapOf("sectors" to emptyList<Any>(), "coveragePercent" to BigDecimal.ZERO)
        analytics.geographyExposure = mapOf("regions" to emptyList<Any>(), "coveragePercent" to BigDecimal.ZERO)
        analytics.riskProfile = mapOf("score" to 0, "level" to "LOW", "factors" to emptyMap<String, Any>())
        analytics.holdings = emptyList()
        analytics.merWeighted = BigDecimal.ZERO
        analytics.totalValue = BigDecimal.ZERO
        analytics.coveragePercent = BigDecimal.ZERO
        analytics.positionsCount = 0
        analytics.xirr = null
        analytics.totalReturn = null
        analytics.totalReturnPct = null
        analytics.dividendYield = null
        analytics.computedAt = OffsetDateTime.now()
        analytics.updatedAt = OffsetDateTime.now()
        return analyticsRepository.save(analytics)
    }

    private fun getCashFlowActivities(connectionId: Long): List<com.portfolio.broker.entity.BrokerActivity> {
        val cashFlowTypes = setOf("TRANSFER_IN", "TRANSFER_OUT", "TRANSFER", "CONTRIBUTION", "WITHDRAWAL", "DEPOSIT")
        return activityRepository.findByConnectionIdAndTradeDateBetween(
            connectionId, LocalDate.of(2000, 1, 1), LocalDate.now()
        ).filter { it.type.uppercase() in cashFlowTypes && it.amount.abs() > BigDecimal.ZERO }
    }

    private fun signedCashFlow(act: com.portfolio.broker.entity.BrokerActivity): BigDecimal {
        val amt = act.amountCad ?: act.amount
        return when {
            act.type.uppercase() in DEPOSIT_TYPES -> amt.abs()
            act.type.uppercase() in WITHDRAWAL_TYPES -> amt.abs().negate()
            act.type.uppercase() == "TRANSFER" -> amt
            else -> amt
        }
    }

    private fun computeXirr(connectionId: Long): BigDecimal? {
        val today = LocalDate.now()
        val mc = java.math.MathContext.DECIMAL64
        val connection = connectionRepository.findById(connectionId).orElse(null) ?: return null
        val endingValue = connection.totalValue ?: return null
        if (endingValue <= BigDecimal.ZERO) return null

        val activities = getCashFlowActivities(connectionId)
        if (activities.isEmpty()) return null

        val startDate = activities.minOf { it.tradeDate }
        val totalDays = ChronoUnit.DAYS.between(startDate, today)
        if (totalDays <= 0) return null

        data class CashFlow(val date: LocalDate, val amount: BigDecimal)
        val cashFlows = activities.map { CashFlow(it.tradeDate, signedCashFlow(it)) }

        var rate = BigDecimal("0.10")
        for (iteration in 0 until 50) {
            var npv = BigDecimal.ZERO
            var dnpv = BigDecimal.ZERO

            for (cf in cashFlows) {
                val days = ChronoUnit.DAYS.between(startDate, cf.date)
                val t = BigDecimal(days).divide(BigDecimal(365), 8, RoundingMode.HALF_UP)
                val onePlusR = BigDecimal.ONE + rate
                if (onePlusR <= BigDecimal.ZERO) break
                val discount = BigDecimal(Math.pow(onePlusR.toDouble(), t.negate().toDouble()))
                npv += cf.amount.multiply(discount, mc)
                dnpv -= cf.amount.multiply(t).multiply(discount, mc).divide(onePlusR, 8, RoundingMode.HALF_UP)
            }

            val totalT = BigDecimal(totalDays).divide(BigDecimal(365), 8, RoundingMode.HALF_UP)
            val onePlusR = BigDecimal.ONE + rate
            if (onePlusR > BigDecimal.ZERO) {
                val termDiscount = BigDecimal(Math.pow(onePlusR.toDouble(), totalT.negate().toDouble()))
                npv += endingValue.multiply(termDiscount, mc)
                dnpv -= endingValue.multiply(totalT).multiply(termDiscount, mc)
                    .divide(onePlusR, 8, RoundingMode.HALF_UP)
            }

            if (dnpv.abs() < BigDecimal("0.0001")) break
            val newRate = rate - npv.divide(dnpv, 8, RoundingMode.HALF_UP)
            if ((newRate - rate).abs() < BigDecimal("0.0001")) {
                rate = newRate
                break
            }
            rate = newRate
        }

        val xirrPct = rate.multiply(BigDecimal(100)).setScale(4, RoundingMode.HALF_UP)
        return xirrPct.coerceIn(BigDecimal("-9999.9999"), BigDecimal("9999.9999"))
    }

    private fun computeTotalReturn(connectionId: Long): BigDecimal? {
        val connection = connectionRepository.findById(connectionId).orElse(null) ?: return null
        val currentValue = connection.totalValue ?: return null

        val activities = getCashFlowActivities(connectionId)
        val netDeposits = activities.sumOf { signedCashFlow(it) }

        return currentValue.subtract(netDeposits).setScale(2, RoundingMode.HALF_UP)
    }

    private fun computeTotalReturnPct(connectionId: Long): BigDecimal? {
        val connection = connectionRepository.findById(connectionId).orElse(null) ?: return null
        val currentValue = connection.totalValue ?: return null

        val activities = getCashFlowActivities(connectionId)
        val netDeposits = activities.sumOf { signedCashFlow(it) }

        if (netDeposits <= BigDecimal.ZERO) return null
        val totalReturn = currentValue.subtract(netDeposits)
        return totalReturn.divide(netDeposits, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100)).setScale(4, RoundingMode.HALF_UP)
    }

    private fun computeDividendYield(connectionId: Long, totalValueCAD: BigDecimal): BigDecimal? {
        if (totalValueCAD <= BigDecimal.ZERO) return null
        val today = LocalDate.now()
        val oneYearAgo = today.minusYears(1)

        val dividendTypes = setOf("DIVIDEND", "DISTRIBUTION", "REI")
        val dividends = activityRepository.findByConnectionIdAndTradeDateBetween(connectionId, oneYearAgo, today)
            .filter { it.type.uppercase() in dividendTypes }
        if (dividends.isEmpty()) return null

        val totalDividends = dividends.sumOf { (it.amountCad ?: it.amount).abs() }
        return totalDividends.divide(totalValueCAD, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100)).setScale(4, RoundingMode.HALF_UP)
    }

    companion object {
        private val HUNDRED = BigDecimal(100)
        private val DEPOSIT_TYPES = setOf("TRANSFER_IN", "CONTRIBUTION", "DEPOSIT")
        private val WITHDRAWAL_TYPES = setOf("TRANSFER_OUT", "WITHDRAWAL")
    }
}
