package com.portfolio.broker.controller

import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.DashboardContextType
import com.portfolio.broker.service.DashboardDataService
import com.portfolio.broker.service.DashboardPreferenceService
import com.portfolio.broker.service.DashboardService
import com.portfolio.broker.service.BrokerService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("isAuthenticated()")
class DashboardController(
    private val dashboardService: DashboardService,
    private val dashboardDataService: DashboardDataService,
    private val dashboardPreferenceService: DashboardPreferenceService,
    private val brokerService: BrokerService
) {
    // ========== Legacy endpoint (backward-compatible) ==========

    @GetMapping
    fun getDashboard(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DashboardResponse> {
        val dashboard = dashboardService.getDashboard(principal.id)
        return ResponseEntity.ok(dashboard)
    }

    // ========== Preferences ==========

    @GetMapping("/preferences")
    fun getPreferences(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "DASHBOARD") contextType: String,
        @RequestParam(required = false) contextId: Long?
    ): ResponseEntity<DashboardPreferencesResponse> {
        val ctx = DashboardContextType.valueOf(contextType)
        return ResponseEntity.ok(dashboardPreferenceService.getPreferences(principal.id, ctx, contextId))
    }

    @PutMapping("/preferences")
    fun updatePreferences(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: UpdateDashboardPreferencesRequest,
        @RequestParam(defaultValue = "DASHBOARD") contextType: String,
        @RequestParam(required = false) contextId: Long?
    ): ResponseEntity<DashboardPreferencesResponse> {
        val ctx = DashboardContextType.valueOf(contextType)
        return ResponseEntity.ok(dashboardPreferenceService.updatePreferences(principal.id, request, ctx, contextId))
    }

    @PostMapping("/preferences/reset")
    fun resetPreferences(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "DASHBOARD") contextType: String,
        @RequestParam(required = false) contextId: Long?
    ): ResponseEntity<DashboardPreferencesResponse> {
        val ctx = DashboardContextType.valueOf(contextType)
        return ResponseEntity.ok(dashboardPreferenceService.resetPreferences(principal.id, ctx, contextId))
    }

    // ========== Widget Data Endpoints ==========

    @GetMapping("/summary")
    fun getSummary(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) connectionId: Long?
    ): ResponseEntity<DashboardSummaryResponse> {
        return ResponseEntity.ok(dashboardDataService.getSummary(principal.id, connectionId))
    }

    @GetMapping("/cash")
    fun getCash(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) connectionId: Long?
    ): ResponseEntity<DashboardCashResponse> {
        return ResponseEntity.ok(dashboardDataService.getCash(principal.id, connectionId))
    }

    @GetMapping("/exposure/sector")
    fun getSectorExposure(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) connectionId: Long?
    ): ResponseEntity<SectorExposureResponse> {
        return ResponseEntity.ok(dashboardDataService.getSectorExposure(principal.id, connectionId))
    }

    @GetMapping("/exposure/geography")
    fun getGeographyExposure(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) connectionId: Long?
    ): ResponseEntity<GeographyExposureResponse> {
        return ResponseEntity.ok(dashboardDataService.getGeographyExposure(principal.id, connectionId))
    }

    @GetMapping("/risk-profile")
    fun getRiskProfile(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) connectionId: Long?
    ): ResponseEntity<RiskProfileResponse> {
        return ResponseEntity.ok(dashboardDataService.getRiskProfile(principal.id, connectionId))
    }

    @GetMapping("/orders/open")
    fun getOpenOrders(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<OpenOrdersResponse> {
        return ResponseEntity.ok(dashboardDataService.getOpenOrders(principal.id))
    }

    @GetMapping("/fees")
    fun getFees(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) connectionId: Long?
    ): ResponseEntity<FeesResponse> {
        return ResponseEntity.ok(dashboardDataService.getFees(principal.id, connectionId))
    }

    @GetMapping("/dividends")
    fun getDividends(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) month: String?,
        @RequestParam(required = false) connectionId: Long?
    ): ResponseEntity<DividendCalendarResponse> {
        return ResponseEntity.ok(dashboardDataService.getDividendCalendar(principal.id, month, connectionId))
    }

    @GetMapping("/positions")
    fun getPositions(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) connectionId: Long?
    ): ResponseEntity<AggregatedPositionsResponse> {
        return if (connectionId != null) {
            ResponseEntity.ok(brokerService.getPositionsForConnection(connectionId, principal.id).let { connResp ->
                AggregatedPositionsResponse(
                    asOfDate = connResp.asOfDate,
                    positions = connResp.positions.map { pos ->
                        AggregatedPositionDto(
                            symbol = pos.symbol,
                            securityName = pos.securityName,
                            instrumentType = pos.instrumentType,
                            totalQuantity = pos.quantity,
                            totalValue = pos.currentValue ?: java.math.BigDecimal.ZERO,
                            averageCost = pos.averageCost,
                            totalPnl = pos.totalPnl,
                            totalPnlPercent = pos.totalPnlPercent,
                            currency = pos.currency,
                            brokerBreakdown = emptyList()
                        )
                    },
                    aggregateSummary = connResp.summary.let { s ->
                        AggregateSummary(s.totalValue, s.totalCost, s.totalPnl, s.totalPnlPercent, 1, 1)
                    }
                )
            })
        } else {
            ResponseEntity.ok(brokerService.getAggregatedPositions(principal.id))
        }
    }

    @GetMapping("/holdings")
    fun getHoldings(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) connectionId: Long?
    ): ResponseEntity<HoldingsTableResponse> {
        return ResponseEntity.ok(dashboardDataService.getHoldings(principal.id, connectionId))
    }

    @GetMapping("/accounts")
    fun getAccounts(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DashboardAccountsResponse> {
        return ResponseEntity.ok(dashboardDataService.getAccounts(principal.id))
    }

    @PostMapping("/refresh")
    fun refreshAll(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RefreshAllResponse> {
        return ResponseEntity.ok(dashboardDataService.refreshAll(principal.id))
    }
}
