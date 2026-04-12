package com.portfolio.broker.service

import com.portfolio.broker.dto.*
import com.portfolio.broker.repository.BenchmarkReturnRepository
import com.portfolio.broker.repository.PortfolioCashFlowRepository
import com.portfolio.broker.repository.PortfolioSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.sqrt

@Service
class PerformanceCalculationService(
    private val snapshotRepository: PortfolioSnapshotRepository,
    private val cashFlowRepository: PortfolioCashFlowRepository,
    private val benchmarkReturnRepository: BenchmarkReturnRepository,
    @Lazy private val benchmarkService: BenchmarkService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mc = MathContext.DECIMAL64
    private val RISK_FREE_RATE = BigDecimal("0.04") // 4% annual

    fun getPerformanceSummary(groupId: Long, startDate: LocalDate, endDate: LocalDate): PerformanceSummaryDto {
        val snapshots = snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            groupId, startDate, endDate
        )

        if (snapshots.size < 2) {
            return emptyPerformanceSummary(startDate, endDate)
        }

        val startingValue = snapshots.first().totalValue
        val endingValue = snapshots.last().totalValue
        val totalReturn = if (startingValue > BigDecimal.ZERO) {
            (endingValue - startingValue).divide(startingValue, 6, RoundingMode.HALF_UP).multiply(BigDecimal(100))
        } else BigDecimal.ZERO

        val twr = calculateTWR(groupId, startDate, endDate)
        val mwr = calculateMWR(groupId, startDate, endDate)
        val volatility = calculateVolatility(snapshots)
        val sharpeRatio = calculateSharpeRatio(twr, volatility)
        val sortinoRatio = calculateSortinoRatio(snapshots, twr)
        val maxDrawdown = calculateMaxDrawdown(snapshots)

        return PerformanceSummaryDto(
            twr = twr,
            mwr = mwr,
            totalReturn = totalReturn,
            volatility = volatility,
            sharpeRatio = sharpeRatio,
            sortinoRatio = sortinoRatio,
            maxDrawdown = maxDrawdown,
            startingValue = startingValue,
            endingValue = endingValue,
            startDate = startDate,
            endDate = endDate
        )
    }

    fun calculateTWR(groupId: Long, startDate: LocalDate, endDate: LocalDate): BigDecimal {
        val snapshots = snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            groupId, startDate, endDate
        )
        if (snapshots.size < 2) return BigDecimal.ZERO

        val cashFlows = cashFlowRepository.findByGroupIdAndFlowDateBetweenOrderByFlowDateAsc(
            groupId, startDate, endDate
        )
        val flowsByDate = cashFlows.groupBy { it.flowDate }

        // Calculate sub-period returns, splitting at cash flow dates
        var cumulativeReturn = BigDecimal.ONE

        for (i in 1 until snapshots.size) {
            val prevValue = snapshots[i - 1].totalValue
            val currValue = snapshots[i].totalValue
            val flowDate = snapshots[i].snapshotDate

            // Sum cash flows on this date
            val dayFlows = flowsByDate[flowDate]?.sumOf { it.amount } ?: BigDecimal.ZERO

            if (prevValue + dayFlows > BigDecimal.ZERO) {
                val periodReturn = (currValue - dayFlows).divide(prevValue, 8, RoundingMode.HALF_UP)
                cumulativeReturn = cumulativeReturn.multiply(periodReturn, mc)
            }
        }

        return (cumulativeReturn - BigDecimal.ONE).multiply(BigDecimal(100)).setScale(4, RoundingMode.HALF_UP)
    }

    fun calculateMWR(groupId: Long, startDate: LocalDate, endDate: LocalDate): BigDecimal {
        val snapshots = snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            groupId, startDate, endDate
        )
        if (snapshots.size < 2) return BigDecimal.ZERO

        val cashFlows = cashFlowRepository.findByGroupIdAndFlowDateBetweenOrderByFlowDateAsc(
            groupId, startDate, endDate
        )

        val startingValue = snapshots.first().totalValue
        val endingValue = snapshots.last().totalValue
        val totalDays = endDate.toEpochDay() - startDate.toEpochDay()
        if (totalDays <= 0) return BigDecimal.ZERO

        // Simple IRR approximation using Newton-Raphson
        var rate = BigDecimal("0.10") // Initial guess: 10%

        for (iteration in 0 until 50) {
            var npv = -startingValue
            var dnpv = BigDecimal.ZERO // derivative

            for (cf in cashFlows) {
                val days = cf.flowDate.toEpochDay() - startDate.toEpochDay()
                val t = BigDecimal(days).divide(BigDecimal(365), 8, RoundingMode.HALF_UP)
                val onePlusR = BigDecimal.ONE + rate
                if (onePlusR <= BigDecimal.ZERO) break

                val discount = onePlusR.pow(t.negate().toInt().coerceIn(-10, 0))
                npv += cf.amount.multiply(discount, mc)
                dnpv -= cf.amount.multiply(t).multiply(discount, mc).divide(onePlusR, 8, RoundingMode.HALF_UP)
            }

            // Add terminal value
            val totalT = BigDecimal(totalDays).divide(BigDecimal(365), 8, RoundingMode.HALF_UP)
            val onePlusR = BigDecimal.ONE + rate
            if (onePlusR > BigDecimal.ZERO) {
                val termDiscount = onePlusR.pow(totalT.negate().toInt().coerceIn(-10, 0))
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

        return rate.multiply(BigDecimal(100)).setScale(4, RoundingMode.HALF_UP)
    }

    private fun calculateVolatility(snapshots: List<com.portfolio.broker.entity.PortfolioSnapshot>): BigDecimal {
        if (snapshots.size < 3) return BigDecimal.ZERO

        val dailyReturns = mutableListOf<Double>()
        for (i in 1 until snapshots.size) {
            val prev = snapshots[i - 1].totalValue.toDouble()
            val curr = snapshots[i].totalValue.toDouble()
            if (prev > 0) {
                dailyReturns.add((curr - prev) / prev)
            }
        }

        if (dailyReturns.isEmpty()) return BigDecimal.ZERO

        val mean = dailyReturns.average()
        val variance = dailyReturns.map { (it - mean) * (it - mean) }.average()
        val dailyStdDev = sqrt(variance)
        val annualizedVol = dailyStdDev * sqrt(252.0) * 100

        return BigDecimal(annualizedVol).setScale(4, RoundingMode.HALF_UP)
    }

    private fun calculateSharpeRatio(twr: BigDecimal, volatility: BigDecimal): BigDecimal {
        if (volatility <= BigDecimal.ZERO) return BigDecimal.ZERO
        return (twr - RISK_FREE_RATE.multiply(BigDecimal(100))).divide(volatility, 4, RoundingMode.HALF_UP)
    }

    private fun calculateSortinoRatio(
        snapshots: List<com.portfolio.broker.entity.PortfolioSnapshot>,
        twr: BigDecimal
    ): BigDecimal {
        if (snapshots.size < 3) return BigDecimal.ZERO

        val dailyReturns = mutableListOf<Double>()
        for (i in 1 until snapshots.size) {
            val prev = snapshots[i - 1].totalValue.toDouble()
            val curr = snapshots[i].totalValue.toDouble()
            if (prev > 0) dailyReturns.add((curr - prev) / prev)
        }

        val negativeReturns = dailyReturns.filter { it < 0 }
        if (negativeReturns.isEmpty()) return BigDecimal.ZERO

        val downVariance = negativeReturns.map { it * it }.average()
        val downsideDeviation = sqrt(downVariance) * sqrt(252.0) * 100

        if (downsideDeviation <= 0.0) return BigDecimal.ZERO

        return (twr - RISK_FREE_RATE.multiply(BigDecimal(100)))
            .divide(BigDecimal(downsideDeviation), 4, RoundingMode.HALF_UP)
    }

    fun calculateMaxDrawdown(snapshots: List<com.portfolio.broker.entity.PortfolioSnapshot>): BigDecimal {
        if (snapshots.size < 2) return BigDecimal.ZERO

        var peak = snapshots.first().totalValue.toDouble()
        var maxDD = 0.0

        for (snapshot in snapshots) {
            val value = snapshot.totalValue.toDouble()
            if (value > peak) peak = value
            val drawdown = if (peak > 0) (peak - value) / peak else 0.0
            if (drawdown > maxDD) maxDD = drawdown
        }

        return BigDecimal(maxDD * 100).setScale(4, RoundingMode.HALF_UP)
    }

    fun getCumulativeReturns(groupId: Long, startDate: LocalDate, endDate: LocalDate): List<ReturnPoint> {
        val snapshots = snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            groupId, startDate, endDate
        )
        if (snapshots.isEmpty()) return emptyList()

        val startValue = snapshots.first().totalValue.toDouble()
        return snapshots.map { s ->
            val cumReturn = if (startValue > 0) {
                ((s.totalValue.toDouble() - startValue) / startValue) * 100
            } else 0.0

            ReturnPoint(
                date = s.snapshotDate.toString(),
                cumulativeReturn = BigDecimal(cumReturn).setScale(4, RoundingMode.HALF_UP),
                portfolioValue = s.totalValue
            )
        }
    }

    fun getDrawdowns(groupId: Long, startDate: LocalDate, endDate: LocalDate): List<DrawdownPoint> {
        val snapshots = snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            groupId, startDate, endDate
        )
        if (snapshots.isEmpty()) return emptyList()

        var peak = snapshots.first().totalValue.toDouble()
        return snapshots.map { s ->
            val value = s.totalValue.toDouble()
            if (value > peak) peak = value
            val drawdown = if (peak > 0) ((peak - value) / peak) * -100 else 0.0

            DrawdownPoint(
                date = s.snapshotDate.toString(),
                drawdown = BigDecimal(drawdown).setScale(4, RoundingMode.HALF_UP)
            )
        }
    }

    fun getBenchmarkComparison(
        groupId: Long,
        benchmarkSymbol: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): BenchmarkComparisonDto {
        val portfolioReturns = getCumulativeReturns(groupId, startDate, endDate)

        // Support model portfolio benchmarks via "MODEL:{id}" format
        val benchmarkReturns = if (benchmarkSymbol.startsWith("MODEL:")) {
            val modelId = benchmarkSymbol.removePrefix("MODEL:").toLongOrNull()
            if (modelId != null) {
                benchmarkService.getModelPortfolioBenchmarkReturns(modelId, startDate, endDate)
            } else emptyList()
        } else {
            val benchmarkData = benchmarkReturnRepository.findBySymbolAndReturnDateBetweenOrderByReturnDateAsc(
                benchmarkSymbol, startDate, endDate
            )
            if (benchmarkData.isNotEmpty()) {
                val startPrice = benchmarkData.first().closePrice.toDouble()
                benchmarkData.map { b ->
                    val cumReturn = if (startPrice > 0) {
                        ((b.closePrice.toDouble() - startPrice) / startPrice) * 100
                    } else 0.0

                    ReturnPoint(
                        date = b.returnDate.toString(),
                        cumulativeReturn = BigDecimal(cumReturn).setScale(4, RoundingMode.HALF_UP),
                        portfolioValue = b.closePrice
                    )
                }
            } else emptyList()
        }

        // Alpha: portfolio return - benchmark return
        val portfolioTotalReturn = portfolioReturns.lastOrNull()?.cumulativeReturn ?: BigDecimal.ZERO
        val benchmarkTotalReturn = benchmarkReturns.lastOrNull()?.cumulativeReturn ?: BigDecimal.ZERO
        val alpha = portfolioTotalReturn - benchmarkTotalReturn

        return BenchmarkComparisonDto(
            portfolioReturns = portfolioReturns,
            benchmarkReturns = benchmarkReturns,
            alpha = alpha.setScale(4, RoundingMode.HALF_UP)
        )
    }

    fun getPerformanceChart(
        groupId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        benchmarkSymbol: String? = null
    ): PerformanceChartData {
        val summary = getPerformanceSummary(groupId, startDate, endDate)
        val cumulativeReturns = getCumulativeReturns(groupId, startDate, endDate)
        val drawdowns = getDrawdowns(groupId, startDate, endDate)

        val benchmarkComparison = benchmarkSymbol?.let {
            getBenchmarkComparison(groupId, it, startDate, endDate)
        }

        return PerformanceChartData(
            summary = summary,
            cumulativeReturns = cumulativeReturns,
            drawdowns = drawdowns,
            benchmarkComparison = benchmarkComparison
        )
    }

    private fun emptyPerformanceSummary(startDate: LocalDate, endDate: LocalDate) = PerformanceSummaryDto(
        twr = BigDecimal.ZERO,
        mwr = BigDecimal.ZERO,
        totalReturn = BigDecimal.ZERO,
        volatility = BigDecimal.ZERO,
        sharpeRatio = BigDecimal.ZERO,
        sortinoRatio = BigDecimal.ZERO,
        maxDrawdown = BigDecimal.ZERO,
        startingValue = BigDecimal.ZERO,
        endingValue = BigDecimal.ZERO,
        startDate = startDate,
        endDate = endDate
    )
}
