package com.portfolio.broker.controller

import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.dto.*
import com.portfolio.broker.service.DriftCalculationService
import com.portfolio.broker.service.PortfolioGroupService
import com.portfolio.broker.service.RebalanceService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/portfolio-groups")
@PreAuthorize("isAuthenticated()")
class PortfolioGroupController(
    private val portfolioGroupService: PortfolioGroupService,
    private val driftCalculationService: DriftCalculationService,
    private val rebalanceService: RebalanceService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ========== Group CRUD ==========

    @GetMapping
    fun listGroups(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PortfolioGroupsListResponse> {
        val response = portfolioGroupService.listGroups(principal.id)
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun createGroup(
        @RequestBody request: CreatePortfolioGroupRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PortfolioGroupDetailDto> {
        val group = portfolioGroupService.createGroup(principal.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(group)
    }

    @GetMapping("/{groupId}")
    fun getGroup(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PortfolioGroupDetailDto> {
        val group = portfolioGroupService.getGroup(groupId, principal.id)
        return ResponseEntity.ok(group)
    }

    @PutMapping("/{groupId}")
    fun updateGroup(
        @PathVariable groupId: Long,
        @RequestBody request: UpdatePortfolioGroupRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PortfolioGroupDetailDto> {
        val group = portfolioGroupService.updateGroup(groupId, principal.id, request)
        return ResponseEntity.ok(group)
    }

    @DeleteMapping("/{groupId}")
    fun deleteGroup(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        portfolioGroupService.deleteGroup(groupId, principal.id)
        return ResponseEntity.noContent().build()
    }

    // ========== Targets ==========

    @PutMapping("/{groupId}/targets")
    fun setTargets(
        @PathVariable groupId: Long,
        @RequestBody request: SetTargetsRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<TargetAllocationDto>> {
        val targets = portfolioGroupService.setTargets(groupId, principal.id, request)
        return ResponseEntity.ok(targets)
    }

    @PostMapping("/{groupId}/targets")
    fun addTarget(
        @PathVariable groupId: Long,
        @RequestBody input: TargetInput,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<TargetAllocationDto> {
        val target = portfolioGroupService.addTarget(groupId, principal.id, input)
        return ResponseEntity.status(HttpStatus.CREATED).body(target)
    }

    @DeleteMapping("/{groupId}/targets/{symbol}")
    fun removeTarget(
        @PathVariable groupId: Long,
        @PathVariable symbol: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        portfolioGroupService.removeTarget(groupId, principal.id, symbol)
        return ResponseEntity.noContent().build()
    }

    // ========== Account Linking ==========

    @PostMapping("/{groupId}/accounts")
    fun linkAccount(
        @PathVariable groupId: Long,
        @RequestBody request: LinkAccountRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<LinkedAccountDto> {
        val account = portfolioGroupService.linkAccount(groupId, principal.id, request.connectionId)
        return ResponseEntity.status(HttpStatus.CREATED).body(account)
    }

    @DeleteMapping("/{groupId}/accounts/{connectionId}")
    fun unlinkAccount(
        @PathVariable groupId: Long,
        @PathVariable connectionId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        portfolioGroupService.unlinkAccount(groupId, principal.id, connectionId)
        return ResponseEntity.noContent().build()
    }

    // ========== Drift & Rebalance ==========

    @GetMapping("/{groupId}/drift")
    fun getDriftAnalysis(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DriftAnalysisResponse> {
        portfolioGroupService.getGroupEntity(groupId, principal.id) // auth check
        val drift = driftCalculationService.calculateDrift(groupId)
        return ResponseEntity.ok(drift)
    }

    @GetMapping("/{groupId}/rebalance")
    fun getRebalanceTrades(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RebalanceTradesResponse> {
        portfolioGroupService.getGroupEntity(groupId, principal.id) // auth check
        val trades = rebalanceService.calculateRebalanceTrades(groupId)
        return ResponseEntity.ok(trades)
    }

    // ========== Settings ==========

    @GetMapping("/{groupId}/settings")
    fun getSettings(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PortfolioGroupSettingsDto> {
        val settings = portfolioGroupService.getSettings(groupId, principal.id)
        return ResponseEntity.ok(settings)
    }

    @PatchMapping("/{groupId}/settings")
    fun updateSettings(
        @PathVariable groupId: Long,
        @RequestBody request: UpdateSettingsRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PortfolioGroupSettingsDto> {
        val settings = portfolioGroupService.updateSettings(groupId, principal.id, request)
        return ResponseEntity.ok(settings)
    }

    // ========== Excluded Assets ==========

    @GetMapping("/{groupId}/excluded-assets")
    fun getExcludedAssets(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<ExcludedAssetDto>> {
        val assets = portfolioGroupService.getExcludedAssets(groupId, principal.id)
        return ResponseEntity.ok(assets)
    }

    @PostMapping("/{groupId}/excluded-assets")
    fun addExcludedAsset(
        @PathVariable groupId: Long,
        @RequestBody request: ExcludeAssetRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ExcludedAssetDto> {
        val asset = portfolioGroupService.addExcludedAsset(groupId, principal.id, request.symbol)
        return ResponseEntity.status(HttpStatus.CREATED).body(asset)
    }

    @DeleteMapping("/{groupId}/excluded-assets/{symbol}")
    fun removeExcludedAsset(
        @PathVariable groupId: Long,
        @PathVariable symbol: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        portfolioGroupService.removeExcludedAsset(groupId, principal.id, symbol)
        return ResponseEntity.noContent().build()
    }

    // ========== Exception Handlers ==========

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.badRequest().body(
            mapOf("error" to "BAD_REQUEST", "message" to (e.message ?: "Invalid request"))
        )
    }
}
