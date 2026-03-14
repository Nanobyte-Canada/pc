package com.portfolio.broker.controller

import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.dto.DashboardResponse
import com.portfolio.broker.service.DashboardService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("isAuthenticated()")
class DashboardController(
    private val dashboardService: DashboardService
) {
    @GetMapping
    fun getDashboard(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DashboardResponse> {
        val dashboard = dashboardService.getDashboard(principal.id)
        return ResponseEntity.ok(dashboard)
    }
}
