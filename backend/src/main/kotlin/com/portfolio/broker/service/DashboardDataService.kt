package com.portfolio.broker.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.entity.InstrumentType
import com.portfolio.broker.entity.OrderStatus
import com.portfolio.broker.repository.*
import com.portfolio.dto.request.PortfolioPositionRequest
import com.portfolio.entity.Country
import com.portfolio.repository.CountryRepository
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.StockRepository
import com.portfolio.service.LookThroughService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

@Service
class DashboardDataService(
    private val positionRepository: BrokerPositionRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val activityRepository: BrokerActivityRepository,
    private val tradeOrderRepository: TradeOrderRepository,
    private val portfolioGroupAccountRepository: PortfolioGroupAccountRepository,
    private val stockRepository: StockRepository,
    private val etfRepository: EtfRepository,
    private val countryRepository: CountryRepository,
    private val lookThroughService: LookThroughService,
    private val driftCalculationService: DriftCalculationService,
    private val positionFetchService: PositionFetchService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cashTypeRef = object : TypeReference<Map<String, BigDecimal>>() {}

    // ========== Summary (Portfolio Value + Positions + Holdings) ==========

    fun getSummary(userId: Long, connectionId: Long? = null): DashboardSummaryResponse {
        val positions = if (connectionId != null) {
            positionRepository.findCurrentPositionsByConnectionId(connectionId)
        } else {
            positionRepository.findCurrentPositionsByUserIdFromActiveConnections(userId)
        }

        val totalValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        val totalDayPnl = positions.sumOf { it.dayPnl ?: BigDecimal.ZERO }
        val totalDayPnlPercent = if (totalValue > BigDecimal.ZERO && totalDayPnl != BigDecimal.ZERO) {
            val previousValue = totalValue - totalDayPnl
            if (previousValue > BigDecimal.ZERO) {
                (totalDayPnl / previousValue * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
        } else BigDecimal.ZERO

        // Positions summary by type
        val byType = positions.groupBy { it.instrumentType ?: InstrumentType.OTHER }
        val positionsSummary = PositionsSummaryDto(
            stocks = byType[InstrumentType.STOCK]?.size ?: 0,
            etfs = byType[InstrumentType.ETF]?.size ?: 0,
            mutualFunds = byType[InstrumentType.MUTUAL_FUND]?.size ?: 0,
            options = byType[InstrumentType.OPTION]?.size ?: 0,
            bonds = byType[InstrumentType.BOND]?.size ?: 0,
            cash = byType[InstrumentType.CASH]?.size ?: 0,
            other = byType[InstrumentType.OTHER]?.size ?: 0,
            total = positions.size
        )

        // Holdings look-through count
        val holdingsCount = calculateHoldingsCount(positions, totalValue)

        return DashboardSummaryResponse(
            portfolioValue = PortfolioValueDto(
                totalValue = totalValue.setScale(2, RoundingMode.HALF_UP),
                totalChange = totalDayPnl.setScale(2, RoundingMode.HALF_UP),
                totalChangePercent = totalDayPnlPercent,
                currency = "CAD"
            ),
            positionsSummary = positionsSummary,
            holdingsCount = holdingsCount
        )
    }

    private fun calculateHoldingsCount(
        positions: List<com.portfolio.broker.entity.BrokerPosition>,
        totalValue: BigDecimal
    ): HoldingsCountDto {
        if (positions.isEmpty() || totalValue <= BigDecimal.ZERO) {
            return HoldingsCountDto(0, 0, 0, 0, 0, BigDecimal.ZERO)
        }

        val lookThroughPositions = mutableListOf<PortfolioPositionRequest>()
        var etfsDecomposed = 0
        val directStockSymbols = mutableSetOf<String>()

        for (position in positions) {
            val weight = (position.currentValue ?: BigDecimal.ZERO)
                .divide(totalValue, 8, RoundingMode.HALF_UP).toDouble()
            if (weight <= 0) continue

            when (position.instrumentType) {
                InstrumentType.STOCK -> {
                    directStockSymbols.add(position.symbol)
                    val stock = stockRepository.findFirstByTickerIgnoreCase(position.symbol)
                    if (stock != null) {
                        lookThroughPositions.add(PortfolioPositionRequest("STOCK", stock.id, weight))
                    }
                }
                InstrumentType.ETF -> {
                    val etf = etfRepository.findBySymbolIgnoreCase(position.symbol)
                    if (etf != null) {
                        lookThroughPositions.add(PortfolioPositionRequest("ETF", etf.id, weight))
                        etfsDecomposed++
                    }
                }
                else -> { /* skip mutual funds, options, bonds, cash for look-through */ }
            }
        }

        if (lookThroughPositions.isEmpty()) {
            return HoldingsCountDto(
                directStocks = directStockSymbols.size,
                lookThroughStocks = 0,
                totalUniqueHoldings = directStockSymbols.size,
                etfsDecomposed = 0,
                mutualFundsDecomposed = 0,  // always 0, mutual funds removed
                coveragePercent = BigDecimal(100)
            )
        }

        return try {
            val result = lookThroughService.computeLookThroughWithQuality(lookThroughPositions, LocalDate.now())
            HoldingsCountDto(
                directStocks = directStockSymbols.size,
                lookThroughStocks = result.exposures.size,
                totalUniqueHoldings = result.exposures.size,
                etfsDecomposed = etfsDecomposed,
                mutualFundsDecomposed = 0,
                coveragePercent = result.quality.coveragePercent
            )
        } catch (e: Exception) {
            log.warn("Failed to calculate look-through holdings count", e)
            HoldingsCountDto(
                directStocks = directStockSymbols.size,
                lookThroughStocks = 0,
                totalUniqueHoldings = directStockSymbols.size,
                etfsDecomposed = 0,
                mutualFundsDecomposed = 0,  // always 0, mutual funds removed
                coveragePercent = BigDecimal.ZERO
            )
        }
    }

    // ========== Cash & Buying Power ==========

    fun getCash(userId: Long, connectionId: Long? = null): DashboardCashResponse {
        val connectionIds = getConnectionIds(userId, connectionId)
        if (connectionIds.isEmpty()) {
            return DashboardCashResponse(emptyList(), emptyList(), BigDecimal.ZERO)
        }

        val cashByDurrency = mutableMapOf<String, BigDecimal>()
        val buyingPowerByCurrency = mutableMapOf<String, BigDecimal>()

        for (connId in connectionIds) {
            val snapshot = balanceRepository.findLatestByConnectionId(connId) ?: continue
            val cashJson = snapshot.cash ?: continue

            try {
                val parsed = objectMapper.readValue(cashJson, cashTypeRef)
                // SnapTrade stores cash and buying_power in the JSONB
                for ((key, value) in parsed) {
                    if (key.startsWith("buying_power_")) {
                        val currency = key.removePrefix("buying_power_").uppercase()
                        buyingPowerByCurrency[currency] = (buyingPowerByCurrency[currency] ?: BigDecimal.ZERO) + value
                    } else if (key.startsWith("cash_")) {
                        val currency = key.removePrefix("cash_").uppercase()
                        cashByDurrency[currency] = (cashByDurrency[currency] ?: BigDecimal.ZERO) + value
                    } else if (key.length == 3 && key == key.uppercase()) {
                        // Simple currency code key (e.g., "CAD": 5000)
                        cashByDurrency[key] = (cashByDurrency[key] ?: BigDecimal.ZERO) + value
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to parse cash JSONB for connection {}", connId, e)
            }
        }

        val totalCashCAD = cashByDurrency.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }

        return DashboardCashResponse(
            availableCash = cashByDurrency.map { CurrencyAmountDto(it.key, it.value.setScale(2, RoundingMode.HALF_UP)) }
                .sortedByDescending { it.amount },
            buyingPower = buyingPowerByCurrency.map { CurrencyAmountDto(it.key, it.value.setScale(2, RoundingMode.HALF_UP)) }
                .sortedByDescending { it.amount },
            totalCashCAD = totalCashCAD.setScale(2, RoundingMode.HALF_UP)
        )
    }

    // ========== Sector Exposure ==========

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
            return SectorExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO)
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
            unmappedWeight += exposure.effectiveWeight
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

    // ========== Geography Exposure ==========

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
            return GeographyExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO)
        }

        // Cache country lookups
        val countryCache = mutableMapOf<String, Country?>()
        data class RegionAccumulator(
            var weight: BigDecimal = BigDecimal.ZERO,
            val countries: MutableMap<String, Pair<String, BigDecimal>> = mutableMapOf()
        )
        val regionMap = mutableMapOf<String, RegionAccumulator>()
        var unmappedWeight = BigDecimal.ZERO

        for ((_, exposure) in result.exposures) {
            val countryCode = exposure.stock.country
            val country = countryCache.getOrPut(countryCode) {
                countryRepository.findByCodeWithRegion(countryCode)
            }

            if (country == null) {
                unmappedWeight += exposure.effectiveWeight
                continue
            }

            val regionName = country.region.name
            val acc = regionMap.getOrPut(regionName) { RegionAccumulator() }
            acc.weight += exposure.effectiveWeight
            val existing = acc.countries[countryCode]
            acc.countries[countryCode] = Pair(
                country.name,
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

    // ========== Risk Profile ==========

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

        // Sector concentration (from sector exposure)
        val sectorExposure = getSectorExposure(userId, connectionId)
        val sectorHHI = sectorExposure.sectors.sumOf { it.weight * it.weight }.setScale(4, RoundingMode.HALF_UP)

        // Geographic concentration
        val geoExposure = getGeographyExposure(userId, connectionId)
        val maxRegionWeight = geoExposure.regions.maxOfOrNull { it.weight } ?: BigDecimal.ZERO

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

    // ========== Open Orders ==========

    fun getOpenOrders(userId: Long): OpenOrdersResponse {
        val openStatuses = listOf(OrderStatus.PENDING, OrderStatus.SUBMITTED, OrderStatus.PARTIALLY_FILLED)
        val orders = tradeOrderRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(userId, openStatuses)

        return OpenOrdersResponse(
            orders = orders.map { order ->
                OpenOrderDto(
                    id = order.id,
                    symbol = order.symbol,
                    action = order.action.name,
                    requestedUnits = order.requestedUnits,
                    requestedPrice = order.requestedPrice,
                    limitPrice = order.limitPrice,
                    status = order.status.name,
                    orderType = order.orderType.name,
                    accountName = order.connection.accountName,
                    createdAt = order.createdAt
                )
            },
            totalCount = orders.size
        )
    }

    // ========== Fees & Commission ==========

    fun getFees(userId: Long, connectionId: Long? = null): FeesResponse {
        val connectionIds = getConnectionIds(userId, connectionId)
        if (connectionIds.isEmpty()) {
            return FeesResponse(
                FeesTotalDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                emptyList(), BigDecimal.ZERO
            )
        }

        val startDate = LocalDate.now().minusMonths(12)
        val endDate = LocalDate.now()
        val activities = activityRepository.findByConnectionIdInAndTradeDateBetween(connectionIds, startDate, endDate)

        val feeTypes = setOf("FEE", "COMMISSION")
        val feeActivities = activities.filter { it.type.uppercase() in feeTypes }

        val totalFees = feeActivities.filter { it.type.uppercase() == "FEE" }.sumOf { it.amount.abs() }
        val totalCommissions = feeActivities.filter { it.type.uppercase() == "COMMISSION" }.sumOf { it.amount.abs() }

        val managementExpensePerMonth = BigDecimal.ZERO

        val monthlyBreakdown = feeActivities
            .groupBy { YearMonth.from(it.tradeDate).toString() }
            .map { (month, acts) ->
                MonthlyFeeDto(
                    month = month,
                    fees = acts.filter { it.type.uppercase() == "FEE" }.sumOf { it.amount.abs() },
                    commissions = acts.filter { it.type.uppercase() == "COMMISSION" }.sumOf { it.amount.abs() }
                )
            }.sortedBy { it.month }

        val total = totalFees + totalCommissions + managementExpensePerMonth

        return FeesResponse(
            last12Months = FeesTotalDto(
                totalFees = totalFees.setScale(2, RoundingMode.HALF_UP),
                totalCommissions = totalCommissions.setScale(2, RoundingMode.HALF_UP),
                totalManagementExpense = managementExpensePerMonth.setScale(2, RoundingMode.HALF_UP),
                total = total.setScale(2, RoundingMode.HALF_UP)
            ),
            monthlyBreakdown = monthlyBreakdown,
            managementExpensePerMonth = managementExpensePerMonth
        )
    }

    // ========== Dividend Calendar ==========

    fun getDividendCalendar(userId: Long, month: String?, connectionId: Long? = null): DividendCalendarResponse {
        val connectionIds = getConnectionIds(userId, connectionId)
        val targetMonth = if (month != null) YearMonth.parse(month) else YearMonth.now()

        if (connectionIds.isEmpty()) {
            return DividendCalendarResponse(targetMonth.toString(), BigDecimal.ZERO, emptyList())
        }

        val startDate = targetMonth.atDay(1)
        val endDate = targetMonth.atEndOfMonth()
        val activities = activityRepository.findByConnectionIdInAndTradeDateBetween(connectionIds, startDate, endDate)

        val dividendTypes = setOf("DIVIDEND", "DISTRIBUTION")
        val dividendActivities = activities.filter { it.type.uppercase() in dividendTypes }

        val entries = dividendActivities.map { act ->
            DividendEntryDto(
                date = act.tradeDate,
                symbol = act.symbol,
                amount = act.amount.abs(),
                currency = act.currency,
                accountName = act.connection.accountName
            )
        }.sortedBy { it.date }

        val totalDividends = entries.sumOf { it.amount }

        return DividendCalendarResponse(
            month = targetMonth.toString(),
            totalDividends = totalDividends.setScale(2, RoundingMode.HALF_UP),
            entries = entries
        )
    }

    // ========== Holdings Table (Look-Through) ==========

    fun getHoldings(userId: Long, connectionId: Long? = null): HoldingsTableResponse {
        val positions = getPositions(userId, connectionId)
        val totalValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        if (positions.isEmpty() || totalValue <= BigDecimal.ZERO) {
            return HoldingsTableResponse(emptyList(), 0, BigDecimal.ZERO)
        }

        val lookThroughPositions = buildLookThroughPositions(positions, totalValue)
        if (lookThroughPositions.isEmpty()) {
            return HoldingsTableResponse(emptyList(), 0, BigDecimal.ZERO)
        }

        val result = try {
            lookThroughService.computeLookThroughWithQuality(lookThroughPositions, LocalDate.now())
        } catch (e: Exception) {
            log.warn("Failed to compute holdings look-through", e)
            return HoldingsTableResponse(emptyList(), 0, BigDecimal.ZERO)
        }

        val countryCache = mutableMapOf<String, Country?>()

        val holdings = result.exposures.values.map { exposure ->
            val stock = exposure.stock
            val country = countryCache.getOrPut(stock.country) {
                countryRepository.findByCodeWithRegion(stock.country)
            }

            LookThroughHoldingDto(
                symbol = stock.ticker,
                name = stock.name,
                effectiveWeight = exposure.effectiveWeight.setScale(6, RoundingMode.HALF_UP),
                sector = null,
                industryGroup = null,
                country = country?.name,
                sources = exposure.sources.map { src ->
                    HoldingSourceDto(
                        type = src.type,
                        instrumentSymbol = src.instrumentSymbol,
                        contribution = BigDecimal(src.contribution).setScale(6, RoundingMode.HALF_UP)
                    )
                }
            )
        }.sortedByDescending { it.effectiveWeight }

        return HoldingsTableResponse(
            holdings = holdings,
            totalCount = holdings.size,
            coveragePercent = result.quality.coveragePercent
        )
    }

    // ========== Connected Accounts ==========

    fun getAccounts(userId: Long): DashboardAccountsResponse {
        val connections = connectionRepository.findByUserIdWithBroker(userId)
            .filter { it.status != ConnectionStatus.DISCONNECTED }

        val accounts = connections.map { conn ->
            val linkedGroupAccounts = portfolioGroupAccountRepository.findByConnectionId(conn.id)
            val linkedGroup = if (linkedGroupAccounts.isNotEmpty()) {
                val groupAccount = linkedGroupAccounts.first()
                val group = groupAccount.group
                val accuracy = try {
                    driftCalculationService.calculateAccuracy(group.id)
                } catch (e: Exception) {
                    BigDecimal.ZERO
                }
                LinkedGroupInfoDto(
                    id = group.id,
                    name = group.name,
                    accuracy = accuracy.setScale(1, RoundingMode.HALF_UP)
                )
            } else null

            DashboardAccountDto(
                connectionId = conn.id,
                brokerName = conn.broker?.code ?: conn.brokerName ?: "Unknown",
                brokerLogoUrl = conn.broker?.logoUrl ?: conn.brokerLogoUrl,
                accountName = conn.accountName,
                accountType = conn.accountMetaType ?: conn.accountType,
                accountNumber = conn.accountNumberActual?.let { maskAccountNumber(it) },
                status = conn.status.name,
                totalValue = conn.totalValue,
                positionsCount = conn.positionsCount ?: 0,
                lastFetchedAt = conn.lastPositionsFetchedAt,
                linkedGroup = linkedGroup,
                needsSetup = linkedGroup == null
            )
        }

        return DashboardAccountsResponse(accounts = accounts)
    }

    // ========== Refresh All ==========

    fun refreshAll(userId: Long): RefreshAllResponse {
        val connections = connectionRepository.findByUserIdAndStatus(userId, ConnectionStatus.ACTIVE)
        var count = 0
        for (conn in connections) {
            try {
                positionFetchService.triggerManualFetch(conn.id, userId)
                count++
            } catch (e: Exception) {
                log.warn("Failed to trigger fetch for connection {}: {}", conn.id, e.message)
            }
        }
        return RefreshAllResponse(
            connectionsRefreshed = count,
            message = "Refreshing data for $count accounts..."
        )
    }

    // ========== Helpers ==========

    private fun getConnectionIds(userId: Long, connectionId: Long?): List<Long> {
        return if (connectionId != null) {
            listOf(connectionId)
        } else {
            connectionRepository.findByUserIdAndStatus(userId, ConnectionStatus.ACTIVE).map { it.id }
        }
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
        return positions.mapNotNull { pos ->
            val weight = (pos.currentValue ?: BigDecimal.ZERO)
                .divide(totalValue, 8, RoundingMode.HALF_UP).toDouble()
            if (weight <= 0) return@mapNotNull null

            when (pos.instrumentType) {
                InstrumentType.STOCK -> {
                    val stock = stockRepository.findFirstByTickerIgnoreCase(pos.symbol) ?: return@mapNotNull null
                    PortfolioPositionRequest("STOCK", stock.id, weight)
                }
                InstrumentType.ETF -> {
                    val etf = etfRepository.findBySymbolIgnoreCase(pos.symbol) ?: return@mapNotNull null
                    PortfolioPositionRequest("ETF", etf.id, weight)
                }
                else -> null
            }
        }
    }

    private fun maskAccountNumber(accountNumber: String): String {
        if (accountNumber.length <= 4) return "****"
        return "****" + accountNumber.takeLast(4)
    }
}
