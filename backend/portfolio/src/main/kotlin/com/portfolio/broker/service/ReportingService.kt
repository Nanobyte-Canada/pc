package com.portfolio.broker.service

import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.BrokerActivityRepository
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

@Service
class ReportingService(
    private val connectionRepository: BrokerConnectionRepository,
    private val activityRepository: BrokerActivityRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getPerformanceReport(
        userId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
        connectionIds: List<Long>?,
        granularity: String? = null
    ): ReportingPerformanceResponse {
        val connections = getRelevantConnectionIds(userId, connectionIds)
        if (connections.isEmpty()) return emptyPerformanceResponse()

        val effectiveStart = startDate ?: LocalDate.now().minusYears(5)
        val effectiveEnd = endDate ?: LocalDate.now()

        val activities = activityRepository.findByConnectionIdInAndTradeDateBetween(
            connections, effectiveStart, effectiveEnd
        )

        val balanceSnapshots = balanceRepository.findByConnectionIdInAndAsOfDateBetween(
            connections, effectiveStart, effectiveEnd
        )

        // Determine effective granularity
        val effectiveGranularity = granularity ?: "MONTHLY"
        val periodKeyFn: (LocalDate) -> String = when (effectiveGranularity.uppercase()) {
            "YEARLY" -> { date -> date.year.toString() }
            "QUARTERLY" -> { date -> "${date.year}-Q${(date.monthValue - 1) / 3 + 1}" }
            else -> { date -> YearMonth.from(date).toString() }
        }

        // Contributions & Withdrawals — broad type sets for backward compat with pre-normalized data
        val contributionTypes = setOf("TRANSFER_IN", "CONTRIBUTION", "DEPOSIT", "EFT", "CONTRIBUTION_ROOM", "TRANSFERS")
        val withdrawalTypes = setOf("TRANSFER_OUT", "WITHDRAWAL", "WITHDRAWALS")

        val periodSummaries = activities
            .filter { it.type in contributionTypes || it.type in withdrawalTypes }
            .filter { (it.amountCad ?: it.amount).compareTo(BigDecimal.ZERO) != 0 }
            .groupBy { periodKeyFn(it.tradeDate) }
            .map { (period, acts) ->
                val contributions = acts.filter { it.type in contributionTypes }
                    .sumOf { (it.amountCad ?: it.amount).abs() }
                val withdrawals = acts.filter { it.type in withdrawalTypes }
                    .sumOf { (it.amountCad ?: it.amount).abs() }
                PeriodSummary(
                    period = period,
                    contributions = contributions,
                    withdrawals = withdrawals,
                    net = contributions - withdrawals
                )
            }
            .sortedBy { it.period }

        // Total Value History from balance snapshots — sample last snapshot per period
        val totalValueHistory = balanceSnapshots
            .groupBy { periodKeyFn(it.asOfDate) }
            .map { (period, snapshots) ->
                // Take the latest snapshot date within this period
                val latestDateSnapshots = snapshots
                    .groupBy { it.asOfDate }
                    .maxByOrNull { it.key }
                    ?.value ?: snapshots
                val totalValue = latestDateSnapshots.sumOf { it.totalValue ?: BigDecimal.ZERO }
                ValuePoint(date = period, totalValue = totalValue, costBasis = null)
            }
            .sortedBy { it.date }

        // Dividend History — broadened types
        val dividendTypes = setOf("DIVIDEND", "DISTRIBUTION")
        val dividendActivities = activities.filter { it.type in dividendTypes }
        val dividendHistory = dividendActivities
            .groupBy { periodKeyFn(it.tradeDate) }
            .map { (period, acts) ->
                val total = acts.sumOf { it.amount.abs() }
                val bySymbol = acts
                    .filter { it.symbol != null }
                    .groupBy { it.symbol!! }
                    .mapValues { (_, symbolActs) -> symbolActs.sumOf { it.amount.abs() } }
                DividendPeriod(period = period, total = total, bySymbol = bySymbol)
            }
            .sortedBy { it.period }

        // Total Dividends by Symbol
        val totalDividendsBySymbol = dividendActivities
            .filter { it.symbol != null }
            .groupBy { it.symbol!! }
            .map { (symbol, acts) -> SymbolDividend(symbol = symbol, total = acts.sumOf { it.amount.abs() }) }
            .sortedByDescending { it.total }

        // KPIs
        val netContributions = periodSummaries.sumOf { it.net }
        val monthCount = if (periodSummaries.isNotEmpty()) periodSummaries.size.toLong() else 1L
        val monthlyAvgContributions = netContributions.divide(BigDecimal(monthCount), 2, RoundingMode.HALF_UP)

        val latestValue = totalValueHistory.lastOrNull()?.totalValue ?: BigDecimal.ZERO
        val earliestValue = totalValueHistory.firstOrNull()?.totalValue ?: BigDecimal.ZERO
        val netChange = latestValue - earliestValue - netContributions

        val totalDividendIncome = dividendActivities.sumOf { it.amount.abs() }
        val dividendMonths = dividendHistory.size.toLong().coerceAtLeast(1)
        val avgMonthlyDividends = totalDividendIncome.divide(BigDecimal(dividendMonths), 2, RoundingMode.HALF_UP)

        val feeTypes = setOf("FEE", "COMMISSION")
        val feesAndCommissions = activities
            .filter { it.type in feeTypes }
            .sumOf { it.amount.abs() }

        return ReportingPerformanceResponse(
            contributionsWithdrawals = periodSummaries,
            totalValueHistory = totalValueHistory,
            dividendHistory = dividendHistory,
            totalDividendsBySymbol = totalDividendsBySymbol,
            kpis = PerformanceKpis(
                netContributions = netContributions,
                monthlyAvgContributions = monthlyAvgContributions,
                netChange = netChange,
                totalDividendIncome = totalDividendIncome,
                avgMonthlyDividends = avgMonthlyDividends,
                feesAndCommissions = feesAndCommissions
            )
        )
    }

    fun getActivitiesReport(
        userId: Long,
        page: Int,
        size: Int,
        startDate: LocalDate?,
        endDate: LocalDate?,
        connectionIds: List<Long>?,
        type: String?
    ): ActivitiesResponse {
        val connections = getRelevantConnectionIds(userId, connectionIds)
        if (connections.isEmpty()) return ActivitiesResponse(emptyList(), 0, page, size)

        val pageable = PageRequest.of(page, size)
        val result = activityRepository.findFilteredMultiConnection(
            connectionIds = connections,
            startDate = startDate,
            endDate = endDate,
            type = type,
            pageable = pageable
        )

        return ActivitiesResponse(
            activities = result.content.map { it.toActivityDto() },
            totalCount = result.totalElements,
            page = page,
            pageSize = size
        )
    }

    private fun getRelevantConnectionIds(userId: Long, requestedIds: List<Long>?): List<Long> {
        val userConnections = connectionRepository.findByUserId(userId)
            .filter { it.status != ConnectionStatus.DISCONNECTED }
            .map { it.id }

        return if (requestedIds != null) {
            requestedIds.filter { it in userConnections }
        } else {
            userConnections
        }
    }

    private fun emptyPerformanceResponse() = ReportingPerformanceResponse(
        contributionsWithdrawals = emptyList(),
        totalValueHistory = emptyList(),
        dividendHistory = emptyList(),
        totalDividendsBySymbol = emptyList(),
        kpis = PerformanceKpis(
            netContributions = BigDecimal.ZERO,
            monthlyAvgContributions = BigDecimal.ZERO,
            netChange = BigDecimal.ZERO,
            totalDividendIncome = BigDecimal.ZERO,
            avgMonthlyDividends = BigDecimal.ZERO,
            feesAndCommissions = BigDecimal.ZERO
        )
    )
}
