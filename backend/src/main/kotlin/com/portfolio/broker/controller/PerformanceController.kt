package com.portfolio.broker.controller

import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.dto.*
import com.portfolio.broker.service.PerformanceCalculationService
import com.portfolio.broker.service.PortfolioGroupService
import com.portfolio.broker.service.PortfolioSnapshotService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/portfolio-groups/{groupId}/performance")
@PreAuthorize("isAuthenticated()")
class PerformanceController(
    private val performanceCalculationService: PerformanceCalculationService,
    private val portfolioGroupService: PortfolioGroupService,
    private val snapshotService: PortfolioSnapshotService
) {
    @GetMapping
    fun getPerformanceSummary(
        @PathVariable groupId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false, defaultValue = "1Y") period: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PerformanceSummaryDto> {
        portfolioGroupService.getGroupEntity(groupId, principal.id) // auth check
        val (start, end) = resolveDateRange(startDate, endDate, period)
        val summary = performanceCalculationService.getPerformanceSummary(groupId, start, end)
        return ResponseEntity.ok(summary)
    }

    @GetMapping("/chart")
    fun getPerformanceChart(
        @PathVariable groupId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false, defaultValue = "1Y") period: String,
        @RequestParam(required = false) benchmark: String?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PerformanceChartData> {
        portfolioGroupService.getGroupEntity(groupId, principal.id) // auth check
        val (start, end) = resolveDateRange(startDate, endDate, period)
        val chartData = performanceCalculationService.getPerformanceChart(groupId, start, end, benchmark)
        return ResponseEntity.ok(chartData)
    }

    @GetMapping("/snapshots")
    fun getSnapshots(
        @PathVariable groupId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false, defaultValue = "1Y") period: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<SnapshotDto>> {
        portfolioGroupService.getGroupEntity(groupId, principal.id) // auth check
        val (start, end) = resolveDateRange(startDate, endDate, period)
        val snapshots = snapshotService.getSnapshots(groupId, start, end)
        return ResponseEntity.ok(snapshots)
    }

    private fun resolveDateRange(
        startDate: LocalDate?,
        endDate: LocalDate?,
        period: String
    ): Pair<LocalDate, LocalDate> {
        val end = endDate ?: LocalDate.now()
        val start = startDate ?: when (period.uppercase()) {
            "1M" -> end.minusMonths(1)
            "3M" -> end.minusMonths(3)
            "6M" -> end.minusMonths(6)
            "YTD" -> LocalDate.of(end.year, 1, 1)
            "1Y" -> end.minusYears(1)
            "ALL" -> end.minusYears(10)
            else -> end.minusYears(1)
        }
        return Pair(start, end)
    }

}
