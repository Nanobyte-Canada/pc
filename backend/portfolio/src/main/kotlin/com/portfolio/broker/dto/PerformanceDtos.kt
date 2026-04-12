package com.portfolio.broker.dto

import com.portfolio.broker.entity.PortfolioSnapshot
import java.math.BigDecimal
import java.time.LocalDate

// ========== Response DTOs ==========

data class PerformanceSummaryDto(
    val twr: BigDecimal,
    val mwr: BigDecimal,
    val totalReturn: BigDecimal,
    val volatility: BigDecimal,
    val sharpeRatio: BigDecimal,
    val sortinoRatio: BigDecimal,
    val maxDrawdown: BigDecimal,
    val startingValue: BigDecimal,
    val endingValue: BigDecimal,
    val startDate: LocalDate,
    val endDate: LocalDate
)

data class ReturnPoint(
    val date: String,
    val cumulativeReturn: BigDecimal,
    val portfolioValue: BigDecimal
)

data class BenchmarkComparisonDto(
    val portfolioReturns: List<ReturnPoint>,
    val benchmarkReturns: List<ReturnPoint>,
    val alpha: BigDecimal
)

data class DrawdownPoint(
    val date: String,
    val drawdown: BigDecimal
)

data class PerformanceChartData(
    val summary: PerformanceSummaryDto,
    val cumulativeReturns: List<ReturnPoint>,
    val drawdowns: List<DrawdownPoint>,
    val benchmarkComparison: BenchmarkComparisonDto?
)

data class SnapshotDto(
    val id: Long,
    val snapshotDate: String,
    val totalValue: BigDecimal,
    val accuracy: BigDecimal?,
    val createdAt: String
)

// ========== Mapper ==========

fun PortfolioSnapshot.toDto() = SnapshotDto(
    id = id,
    snapshotDate = snapshotDate.toString(),
    totalValue = totalValue,
    accuracy = accuracy,
    createdAt = createdAt.toString()
)
