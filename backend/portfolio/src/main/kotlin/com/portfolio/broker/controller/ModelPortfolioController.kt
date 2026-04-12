package com.portfolio.broker.controller

import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.dto.*
import com.portfolio.broker.service.ModelPortfolioService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/model-portfolios")
@PreAuthorize("isAuthenticated()")
class ModelPortfolioController(
    private val modelPortfolioService: ModelPortfolioService
) {

    @GetMapping
    fun listModels(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ModelPortfoliosListResponse> {
        val response = modelPortfolioService.listAll(principal.id)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    fun getModel(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ModelPortfolioDetailDto> {
        val model = modelPortfolioService.getById(id, principal.id)
        return ResponseEntity.ok(model)
    }

    @GetMapping("/{id}/analysis")
    fun getAnalysis(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ModelAnalysisDto> {
        val analysis = modelPortfolioService.getAnalysis(id, principal.id)
        return ResponseEntity.ok(analysis)
    }

    @PostMapping
    fun createModel(
        @RequestBody request: CreateModelPortfolioRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ModelPortfolioDetailDto> {
        val model = modelPortfolioService.create(principal.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(model)
    }

    @PutMapping("/{id}")
    fun updateModel(
        @PathVariable id: Long,
        @RequestBody request: UpdateModelPortfolioRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ModelPortfolioDetailDto> {
        val model = modelPortfolioService.update(id, principal.id, request)
        return ResponseEntity.ok(model)
    }

    @DeleteMapping("/{id}")
    fun deleteModel(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        modelPortfolioService.delete(id, principal.id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/apply-to-accounts")
    fun applyToAccounts(
        @PathVariable id: Long,
        @RequestBody request: ApplyToAccountsRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        modelPortfolioService.applyToAccounts(principal.id, id, request.connectionIds)
        return ResponseEntity.ok().build()
    }
}
