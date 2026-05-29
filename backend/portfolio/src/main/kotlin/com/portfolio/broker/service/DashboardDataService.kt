package com.portfolio.broker.service

import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.AccountAnalytics
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.entity.InstrumentType
import com.portfolio.broker.entity.OrderStatus
import com.portfolio.broker.repository.*
import com.portfolio.dto.request.PortfolioPositionRequest
import com.portfolio.service.CountryRegionLookupService
import com.portfolio.service.IngestionInstrumentLookupService
import com.portfolio.service.LookThroughService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Service
class DashboardDataService(
    private val positionRepository: BrokerPositionRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val activityRepository: BrokerActivityRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val tradeOrderRepository: TradeOrderRepository,
    private val portfolioGroupAccountRepository: PortfolioGroupAccountRepository,
    private val instrumentLookup: IngestionInstrumentLookupService,
    private val countryRegionLookup: CountryRegionLookupService,
    private val lookThroughService: LookThroughService,
    private val driftCalculationService: DriftCalculationService,
    private val positionFetchService: PositionFetchService,
    private val cashService: DashboardCashService,
    private val exposureService: DashboardExposureService,
    private val riskService: DashboardRiskService,
    private val analyticsRepository: AccountAnalyticsRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ========== Delegated methods ==========

    fun getCash(userId: Long, connectionId: Long? = null) = cashService.getCash(userId, connectionId)

    fun getSectorExposure(userId: Long, connectionId: Long? = null): SectorExposureResponse {
        val snapshots = getSnapshots(userId, connectionId)
        if (snapshots.isNotEmpty()) {
            return aggregateSectorExposure(snapshots)
        }
        return exposureService.getSectorExposure(userId, connectionId)
    }

    fun getGeographyExposure(userId: Long, connectionId: Long? = null): GeographyExposureResponse {
        val snapshots = getSnapshots(userId, connectionId)
        if (snapshots.isNotEmpty()) {
            return aggregateGeographyExposure(snapshots)
        }
        return exposureService.getGeographyExposure(userId, connectionId)
    }

    fun getRiskProfile(userId: Long, connectionId: Long? = null): RiskProfileResponse {
        val snapshots = getSnapshots(userId, connectionId)
        if (snapshots.isNotEmpty()) {
            return aggregateRiskProfile(snapshots)
        }
        return riskService.getRiskProfile(userId, connectionId)
    }

    // ========== IRR (Internal Rate of Return) ==========

    fun getIrrData(userId: Long, connectionId: Long? = null): DashboardIrrResponse {
        val connections = connectionRepository.findByUserIdAndStatusWithBroker(userId, ConnectionStatus.ACTIVE)
            .let { conns -> if (connectionId != null) conns.filter { it.id == connectionId } else conns }

        val today = LocalDate.now()
        val mc = MathContext.DECIMAL64

        val portfolioIrr = calculatePortfolioIrr(connections, today, mc)

        val totalPortfolioValue = connections.sumOf { it.totalValue ?: BigDecimal.ZERO }
        val allConnectionIds = connections.map { it.id }

        val cashFlowTypes = setOf("TRANSFER_IN", "TRANSFER_OUT", "TRANSFER", "CONTRIBUTION", "WITHDRAWAL", "DEPOSIT")
        val depositTypes = setOf("TRANSFER_IN", "CONTRIBUTION", "DEPOSIT")
        val withdrawalTypes = setOf("TRANSFER_OUT", "WITHDRAWAL")

        val allCashFlows = activityRepository.findByConnectionIdInAndTradeDateBetween(
            allConnectionIds, LocalDate.of(2000, 1, 1), today
        ).filter { it.type.uppercase() in cashFlowTypes && it.amount.abs() > BigDecimal.ZERO }

        val netDeposits = allCashFlows.sumOf { act ->
            val amt = act.amountCad ?: act.amount
            when {
                act.type.uppercase() in depositTypes -> amt.abs()
                act.type.uppercase() in withdrawalTypes -> amt.abs().negate()
                act.type.uppercase() == "TRANSFER" -> amt
                else -> amt
            }
        }

        val portfolioTotalReturn = if (allCashFlows.isNotEmpty()) totalPortfolioValue - netDeposits else null
        val portfolioTotalReturnPct = if (netDeposits > BigDecimal.ZERO && portfolioTotalReturn != null)
            portfolioTotalReturn.divide(netDeposits, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)).setScale(4, RoundingMode.HALF_UP)
        else null

        val dividendTypes = setOf("DIVIDEND", "DISTRIBUTION", "REI")
        val last12mDividends = activityRepository.findByConnectionIdInAndTradeDateBetween(
            allConnectionIds, today.minusYears(1), today
        ).filter { it.type.uppercase() in dividendTypes }
            .sumOf { (it.amountCad ?: it.amount).abs() }

        val portfolioDividendYield = if (totalPortfolioValue > BigDecimal.ZERO)
            last12mDividends.divide(totalPortfolioValue, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)).setScale(4, RoundingMode.HALF_UP)
        else null

        val accountResults = connections.map { conn ->
            val analytics = analyticsRepository.findByConnectionId(conn.id)

            AccountIrrDto(
                connectionId = conn.id,
                brokerName = conn.brokerName ?: conn.connectionType,
                accountName = conn.accountName,
                irr = if (connectionId != null) portfolioIrr else analytics?.xirr,
                totalReturn = if (connectionId != null) portfolioTotalReturn?.setScale(2, RoundingMode.HALF_UP) else analytics?.totalReturn,
                totalReturnPct = if (connectionId != null) portfolioTotalReturnPct else analytics?.totalReturnPct,
                dividendYield = if (connectionId != null) portfolioDividendYield else analytics?.dividendYield,
                startDate = null,
                endDate = today.toString()
            )
        }

        return DashboardIrrResponse(
            portfolioIrr = portfolioIrr,
            portfolioTotalReturn = portfolioTotalReturn?.setScale(2, RoundingMode.HALF_UP),
            portfolioTotalReturnPct = portfolioTotalReturnPct,
            portfolioDividendYield = portfolioDividendYield,
            accounts = accountResults
        )
    }

    private fun calculatePortfolioIrr(
        connections: List<com.portfolio.broker.entity.BrokerConnection>,
        today: LocalDate,
        mc: MathContext
    ): BigDecimal? {
        if (connections.isEmpty()) return null

        val endingValue = connections.sumOf { it.totalValue ?: BigDecimal.ZERO }
        if (endingValue <= BigDecimal.ZERO) return null

        val cashFlowTypes = setOf("TRANSFER_IN", "TRANSFER_OUT", "TRANSFER", "CONTRIBUTION", "WITHDRAWAL", "DEPOSIT")
        val connectionIds = connections.map { it.id }
        val allActivities = activityRepository.findByConnectionIdInAndTradeDateBetween(
            connectionIds, LocalDate.of(2000, 1, 1), today
        ).filter { it.type.uppercase() in cashFlowTypes && it.amount.abs() > BigDecimal.ZERO }

        if (allActivities.isEmpty()) return null

        val startDate = allActivities.minOf { it.tradeDate }

        return calculateIrr(BigDecimal.ZERO, endingValue, startDate, today, allActivities, mc)
    }

    private fun calculateIrr(
        startingValue: BigDecimal,
        endingValue: BigDecimal,
        startDate: LocalDate,
        endDate: LocalDate,
        activities: List<com.portfolio.broker.entity.BrokerActivity>,
        mc: MathContext
    ): BigDecimal? {
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate)
        if (totalDays <= 0) return null

        val depositTypes = setOf("TRANSFER_IN", "CONTRIBUTION", "DEPOSIT")
        val withdrawalTypes = setOf("TRANSFER_OUT", "WITHDRAWAL")

        data class CashFlow(val date: LocalDate, val amount: BigDecimal)

        val cashFlows = activities.map { act ->
            val amt = act.amountCad ?: act.amount
            val signedAmount = when {
                act.type.uppercase() in depositTypes -> amt.abs()
                act.type.uppercase() in withdrawalTypes -> amt.abs().negate()
                act.type.uppercase() == "TRANSFER" -> amt
                else -> amt
            }
            CashFlow(act.tradeDate, signedAmount)
        }

        val minRate = BigDecimal("-0.99")
        val maxRate = BigDecimal("10.0")
        var rate = BigDecimal("0.10")
        var converged = false

        for (iteration in 0 until 50) {
            val onePlusRCheck = BigDecimal.ONE + rate
            if (onePlusRCheck <= BigDecimal.ZERO) {
                rate = BigDecimal("-0.50")
                continue
            }

            var npv = startingValue.negate()
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

            val newRate = (rate - npv.divide(dnpv, 8, RoundingMode.HALF_UP)).coerceIn(minRate, maxRate)
            if ((newRate - rate).abs() < BigDecimal("0.0001")) {
                rate = newRate
                converged = true
                break
            }
            rate = newRate
        }

        if (!converged && rate.abs() > BigDecimal("5.0")) return null

        return rate.multiply(BigDecimal(100)).setScale(4, RoundingMode.HALF_UP)
    }

    // ========== Summary (Portfolio Value + Positions + Holdings) ==========

    fun getSummary(userId: Long, connectionId: Long? = null): DashboardSummaryResponse {
        val positions = if (connectionId != null) {
            positionRepository.findCurrentPositionsByConnectionId(connectionId)
        } else {
            positionRepository.findCurrentPositionsByUserIdFromActiveConnections(userId)
        }

        // Portfolio Value from connection.totalValue (FX-converted total, includes cash)
        val connections = if (connectionId != null) {
            connectionRepository.findById(connectionId).map { listOf(it) }.orElse(emptyList())
        } else {
            connectionRepository.findByUserIdAndStatus(userId, ConnectionStatus.ACTIVE)
        }
        val portfolioValue = connections.sumOf { it.totalValue ?: BigDecimal.ZERO }

        // Cash from balance snapshots
        val cashResponse = getCash(userId, connectionId)
        val cashValue = cashResponse.totalCashCAD

        // Investment = Portfolio Value - Cash
        val investmentValue = portfolioValue - cashValue

        // Day P&L from positions
        val anyDayPnlAvailable = positions.any { it.dayPnl != null }
        val totalDayPnl = if (anyDayPnlAvailable) {
            positions.sumOf { it.dayPnl ?: BigDecimal.ZERO }
        } else null
        val totalDayPnlPercent = if (totalDayPnl != null && portfolioValue > BigDecimal.ZERO && totalDayPnl != BigDecimal.ZERO) {
            val previousValue = portfolioValue - totalDayPnl
            if (previousValue > BigDecimal.ZERO) {
                (totalDayPnl / previousValue * BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
        } else if (totalDayPnl != null) BigDecimal.ZERO else null

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

        // Holdings look-through count (use investmentValue for weight calculations)
        val warnings = mutableListOf<String>()
        val positionsTotalValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        val (holdingsCount, holdingsWarnings) = calculateHoldingsCount(positions, positionsTotalValue)
        warnings.addAll(holdingsWarnings)

        return DashboardSummaryResponse(
            portfolioValue = PortfolioValueDto(
                totalValue = portfolioValue.setScale(2, RoundingMode.HALF_UP),
                investmentValue = investmentValue.setScale(2, RoundingMode.HALF_UP),
                cashValue = cashValue.setScale(2, RoundingMode.HALF_UP),
                totalChange = totalDayPnl?.setScale(2, RoundingMode.HALF_UP),
                totalChangePercent = totalDayPnlPercent,
                currency = "CAD"
            ),
            positionsSummary = positionsSummary,
            holdingsCount = holdingsCount,
            warnings = warnings
        )
    }

    private fun calculateHoldingsCount(
        positions: List<com.portfolio.broker.entity.BrokerPosition>,
        totalValue: BigDecimal
    ): Pair<HoldingsCountDto, List<String>> {
        if (positions.isEmpty() || totalValue <= BigDecimal.ZERO) {
            return Pair(HoldingsCountDto(0, 0, 0, 0, 0, BigDecimal.ZERO), emptyList())
        }

        val lookThroughPositions = mutableListOf<PortfolioPositionRequest>()
        var etfsDecomposed = 0
        val directStockSymbols = mutableSetOf<String>()

        // Batch-fetch all position tickers from ingestion schema
        val allTickers = positions.mapNotNull { pos ->
            if (pos.instrumentType == InstrumentType.STOCK || pos.instrumentType == InstrumentType.ETF) pos.symbol else null
        }.toSet()
        val instrumentsByTicker = instrumentLookup.findByTickers(allTickers)

        for (position in positions) {
            val weight = (position.currentValue ?: BigDecimal.ZERO)
                .divide(totalValue, 8, RoundingMode.HALF_UP).toDouble()
            if (weight <= 0) continue

            when (position.instrumentType) {
                InstrumentType.STOCK -> {
                    directStockSymbols.add(position.symbol)
                    val instrument = instrumentsByTicker[position.symbol.uppercase()]
                    if (instrument != null) {
                        lookThroughPositions.add(PortfolioPositionRequest("STOCK", instrument.id, weight))
                    }
                }
                InstrumentType.ETF -> {
                    val instrument = instrumentsByTicker[position.symbol.uppercase()]
                    if (instrument != null) {
                        lookThroughPositions.add(PortfolioPositionRequest("ETF", instrument.id, weight))
                        etfsDecomposed++
                    }
                }
                else -> { /* skip mutual funds, options, bonds, cash for look-through */ }
            }
        }

        if (lookThroughPositions.isEmpty()) {
            return Pair(HoldingsCountDto(
                directStocks = directStockSymbols.size,
                lookThroughStocks = 0,
                totalUniqueHoldings = directStockSymbols.size,
                etfsDecomposed = 0,
                mutualFundsDecomposed = 0,  // always 0, mutual funds removed
                coveragePercent = BigDecimal(100)
            ), emptyList())
        }

        return try {
            Pair(run {
                val result = lookThroughService.computeLookThroughWithQuality(lookThroughPositions, LocalDate.now())
                HoldingsCountDto(
                    directStocks = directStockSymbols.size,
                    lookThroughStocks = result.exposures.size,
                    totalUniqueHoldings = result.exposures.size,
                    etfsDecomposed = etfsDecomposed,
                    mutualFundsDecomposed = 0,
                    coveragePercent = result.quality.coveragePercent
                )
            }, emptyList())
        } catch (e: Exception) {
            log.warn("Failed to calculate look-through holdings count", e)
            Pair(HoldingsCountDto(
                directStocks = directStockSymbols.size,
                lookThroughStocks = 0,
                totalUniqueHoldings = directStockSymbols.size,
                etfsDecomposed = 0,
                mutualFundsDecomposed = 0,  // always 0, mutual funds removed
                coveragePercent = BigDecimal.ZERO
            ), listOf("Holdings count may be incomplete due to a calculation error"))
        }
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

        // Commissions come from both COMMISSION-typed activities and per-trade fee columns on BUY/SELL
        val explicitCommissions = feeActivities.filter { it.type.uppercase() == "COMMISSION" }.sumOf { it.amount.abs() }
        val tradeTypes = setOf("BUY", "SELL")
        val perTradeFees = activities.filter { it.type.uppercase() in tradeTypes }
            .sumOf { it.fee?.abs() ?: BigDecimal.ZERO }
        val totalCommissions = explicitCommissions + perTradeFees

        // Compute MER from ETF expense ratios
        val managementExpensePerMonth = computeWeightedMER(userId, connectionId)

        val monthlyBreakdown = feeActivities
            .groupBy { YearMonth.from(it.tradeDate).toString() }
            .map { (month, acts) ->
                MonthlyFeeDto(
                    month = month,
                    fees = acts.filter { it.type.uppercase() == "FEE" }.sumOf { it.amount.abs() },
                    commissions = acts.filter { it.type.uppercase() == "COMMISSION" }.sumOf { it.amount.abs() }
                )
            }.sortedBy { it.month }

        val annualMER = (managementExpensePerMonth * BigDecimal(12)).setScale(2, RoundingMode.HALF_UP)
        val total = totalFees + totalCommissions + annualMER

        return FeesResponse(
            last12Months = FeesTotalDto(
                totalFees = totalFees.setScale(2, RoundingMode.HALF_UP),
                totalCommissions = totalCommissions.setScale(2, RoundingMode.HALF_UP),
                totalManagementExpense = annualMER,
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
            return DividendCalendarResponse(targetMonth.toString(), BigDecimal.ZERO, BigDecimal.ZERO, emptyList())
        }

        val startDate = targetMonth.atDay(1)
        val endDate = targetMonth.atEndOfMonth()
        val activities = activityRepository.findByConnectionIdInAndTradeDateBetween(connectionIds, startDate, endDate)

        val dividendTypes = setOf("DIVIDEND", "DISTRIBUTION", "REI")
        val dividendActivities = activities.filter { it.type.uppercase() in dividendTypes }

        val entries = dividendActivities.map { act ->
            DividendEntryDto(
                date = act.tradeDate,
                symbol = act.symbol,
                amount = act.amount.abs(),
                currency = act.currency,
                accountName = act.connection.accountName,
                type = act.type.uppercase()
            )
        }.sortedBy { it.date }

        val totalDividends = entries.filter { it.type != "REI" }.sumOf { it.amount }
        val totalReinvestments = entries.filter { it.type == "REI" }.sumOf { it.amount }

        return DividendCalendarResponse(
            month = targetMonth.toString(),
            totalDividends = totalDividends.setScale(2, RoundingMode.HALF_UP),
            totalReinvestments = totalReinvestments.setScale(2, RoundingMode.HALF_UP),
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

        val holdings = result.exposures.values.map { exposure ->
            val instrument = exposure.instrument
            val countryCode = instrument.country
            val countryName = if (countryCode != null) countryRegionLookup.getCountryName(countryCode) else null

            LookThroughHoldingDto(
                symbol = instrument.ticker,
                name = instrument.name,
                effectiveWeight = exposure.effectiveWeight.setScale(6, RoundingMode.HALF_UP),
                sector = instrument.gicsSectorCode?.let { LookThroughService.GICS_SECTOR_NAMES[it] },
                industryGroup = instrument.gicsIndustryGroupCode?.let { LookThroughService.GICS_INDUSTRY_GROUP_NAMES[it] },
                country = countryName,
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

            val portfolioValue = conn.totalValue ?: BigDecimal.ZERO
            val cash = cashService.getTotalCashFromSnapshot(conn.id)
            val investmentValue = portfolioValue - cash

            DashboardAccountDto(
                connectionId = conn.id,
                brokerName = conn.brokerName ?: conn.connectionType ?: "Unknown",
                brokerLogoUrl = conn.brokerLogoUrl,
                accountName = conn.accountName,
                accountType = conn.accountMetaType ?: conn.accountType,
                accountNumber = (conn.accountNumberActual ?: conn.accountNumber)?.let { maskAccountNumber(it) },
                status = conn.status.name,
                totalValue = portfolioValue.setScale(2, RoundingMode.HALF_UP),
                investmentValue = investmentValue.setScale(2, RoundingMode.HALF_UP),
                cash = cash.setScale(2, RoundingMode.HALF_UP),
                buyingPower = cashService.getBuyingPowerFromSnapshot(conn.id)?.setScale(2, RoundingMode.HALF_UP),
                positionsCount = conn.positionsCount ?: 0,
                lastFetchedAt = conn.lastPositionsFetchedAt,
                linkedGroup = linkedGroup,
                modelPortfolioId = conn.modelPortfolio?.id,
                modelPortfolioName = conn.modelPortfolio?.name,
                needsSetup = linkedGroup == null && conn.modelPortfolio == null
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
                log.warn("Failed to refresh connection {}: {}", conn.id, e.message)
            }
        }
        return RefreshAllResponse(
            connectionsRefreshed = count,
            message = "Refreshed data for $count accounts"
        )
    }

    // ========== GICS Backfill ==========
    // NOTE: backfillStockGicsCodes() has been removed. GICS codes are now read
    // from the ingestion schema JSONB payload at query time, not stored on old entities.

    // ========== MER Calculation ==========

    private fun computeWeightedMER(userId: Long, connectionId: Long?): BigDecimal {
        val positions = getPositions(userId, connectionId)
        val totalValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        if (totalValue <= BigDecimal.ZERO) return BigDecimal.ZERO

        var weightedExpenseRatio = BigDecimal.ZERO

        for (pos in positions) {
            if (pos.instrumentType != InstrumentType.ETF) continue
            val posValue = pos.currentValue ?: BigDecimal.ZERO
            if (posValue <= BigDecimal.ZERO) continue

            val expenseRatio = instrumentLookup.getEtfExpenseRatio(pos.symbol) ?: continue

            val weight = posValue.divide(totalValue, 8, RoundingMode.HALF_UP)
            weightedExpenseRatio += weight * expenseRatio
        }

        // MER per month = totalValue * weightedExpenseRatio / 12
        return (totalValue * weightedExpenseRatio / BigDecimal(12)).setScale(2, RoundingMode.HALF_UP)
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
        // Batch-fetch all relevant tickers from ingestion schema
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

    private fun maskAccountNumber(accountNumber: String): String {
        if (accountNumber.length <= 4) return "****"
        return "****" + accountNumber.takeLast(4)
    }

    // ========== Snapshot-based analytics (pre-computed) ==========

    private fun getSnapshots(userId: Long, connectionId: Long?): List<AccountAnalytics> {
        return if (connectionId != null) {
            listOfNotNull(analyticsRepository.findByConnectionId(connectionId))
        } else {
            analyticsRepository.findAllByUserId(userId)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun aggregateSectorExposure(snapshots: List<AccountAnalytics>): SectorExposureResponse {
        if (snapshots.size == 1) {
            return mapSnapshotToSectorResponse(snapshots.first().sectorExposure)
        }
        // Value-weighted merge across accounts
        val totalValue = snapshots.sumOf { it.totalValue }
        if (totalValue <= BigDecimal.ZERO) return SectorExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO)

        val merged = mutableMapOf<String, Pair<String, BigDecimal>>() // code -> (name, weight)
        var weightedCoverage = BigDecimal.ZERO

        for (snap in snapshots) {
            val accountProportion = snap.totalValue.divide(totalValue, 8, RoundingMode.HALF_UP)
            val sectors = snap.sectorExposure["sectors"] as? List<Map<String, Any?>> ?: continue
            val coverage = (snap.sectorExposure["coveragePercent"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
            weightedCoverage += coverage.multiply(accountProportion)

            for (sector in sectors) {
                val code = sector["code"]?.toString() ?: continue
                val name = sector["name"]?.toString() ?: "Unknown"
                val weight = (sector["weight"] as? Number)?.let { BigDecimal(it.toString()) } ?: continue
                val scaledWeight = weight.multiply(accountProportion)
                val existing = merged[code]
                merged[code] = Pair(name, (existing?.second ?: BigDecimal.ZERO) + scaledWeight)
            }
        }

        val sectors = merged.entries.sortedByDescending { it.value.second }.map { (code, pair) ->
            SectorExposureDto(code, pair.first, pair.second.setScale(4, RoundingMode.HALF_UP), emptyList())
        }

        return SectorExposureResponse(sectors, weightedCoverage.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapSnapshotToSectorResponse(data: Map<String, Any?>): SectorExposureResponse {
        val sectors = (data["sectors"] as? List<Map<String, Any?>>)?.map { s ->
            val igs = (s["industryGroups"] as? List<Map<String, Any?>>)?.map { ig ->
                IndustryGroupExposureDto(
                    code = ig["code"]?.toString() ?: "",
                    name = ig["name"]?.toString() ?: "",
                    weight = (ig["weight"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
                )
            } ?: emptyList()
            SectorExposureDto(
                sectorCode = s["code"]?.toString() ?: "",
                sectorName = s["name"]?.toString() ?: "",
                weight = (s["weight"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
                industryGroups = igs
            )
        } ?: emptyList()
        val coverage = (data["coveragePercent"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
        return SectorExposureResponse(sectors, coverage, BigDecimal.ZERO)
    }

    @Suppress("UNCHECKED_CAST")
    private fun aggregateGeographyExposure(snapshots: List<AccountAnalytics>): GeographyExposureResponse {
        if (snapshots.size == 1) {
            return mapSnapshotToGeographyResponse(snapshots.first().geographyExposure)
        }
        val totalValue = snapshots.sumOf { it.totalValue }
        if (totalValue <= BigDecimal.ZERO) return GeographyExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO)

        val merged = mutableMapOf<String, BigDecimal>()
        var weightedCoverage = BigDecimal.ZERO

        for (snap in snapshots) {
            val accountProportion = snap.totalValue.divide(totalValue, 8, RoundingMode.HALF_UP)
            val regions = snap.geographyExposure["regions"] as? List<Map<String, Any?>> ?: continue
            val coverage = (snap.geographyExposure["coveragePercent"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
            weightedCoverage += coverage.multiply(accountProportion)

            for (region in regions) {
                val name = region["name"]?.toString() ?: continue
                val weight = (region["weight"] as? Number)?.let { BigDecimal(it.toString()) } ?: continue
                merged[name] = (merged[name] ?: BigDecimal.ZERO) + weight.multiply(accountProportion)
            }
        }

        val regions = merged.entries.sortedByDescending { it.value }.map { (name, weight) ->
            RegionExposureDto(name, weight.setScale(4, RoundingMode.HALF_UP), emptyList())
        }

        return GeographyExposureResponse(regions, weightedCoverage.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapSnapshotToGeographyResponse(data: Map<String, Any?>): GeographyExposureResponse {
        val regions = (data["regions"] as? List<Map<String, Any?>>)?.map { r ->
            val countries = (r["countries"] as? List<Map<String, Any?>>)?.map { c ->
                CountryExposureDto(
                    code = c["code"]?.toString() ?: "",
                    name = c["name"]?.toString() ?: "",
                    weight = (c["weight"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
                )
            } ?: emptyList()
            RegionExposureDto(
                name = r["name"]?.toString() ?: "",
                weight = (r["weight"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
                countries = countries
            )
        } ?: emptyList()
        val coverage = (data["coveragePercent"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
        return GeographyExposureResponse(regions, coverage, BigDecimal.ZERO)
    }

    @Suppress("UNCHECKED_CAST")
    private fun aggregateRiskProfile(snapshots: List<AccountAnalytics>): RiskProfileResponse {
        if (snapshots.size == 1) {
            return mapSnapshotToRiskResponse(snapshots.first().riskProfile)
        }
        // For multi-account, use the largest account's risk as approximation
        // (true aggregation would require recomputing from merged holdings)
        val largest = snapshots.maxByOrNull { it.totalValue } ?: return RiskProfileResponse(0, "LOW", RiskFactorsDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyMap()))
        return mapSnapshotToRiskResponse(largest.riskProfile)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapSnapshotToRiskResponse(data: Map<String, Any?>): RiskProfileResponse {
        val score = (data["score"] as? Number)?.toInt() ?: 0
        val level = data["level"]?.toString() ?: "LOW"
        val factors = data["factors"] as? Map<String, Any?> ?: emptyMap()
        return RiskProfileResponse(
            riskScore = score,
            riskLevel = level,
            factors = RiskFactorsDto(
                concentrationHHI = (factors["concentrationHHI"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
                top10Concentration = (factors["top10Concentration"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
                sectorConcentrationHHI = (factors["sectorConcentrationHHI"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
                geographicConcentration = (factors["geographicConcentration"] as? Number)?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
                assetTypeDistribution = emptyMap()
            )
        )
    }
}
