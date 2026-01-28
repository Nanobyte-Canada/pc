package com.portfolio.controller

import com.portfolio.dto.request.MutualFundFilterRequest
import com.portfolio.dto.response.AvailableDatesResponseDto
import com.portfolio.dto.response.MutualFundDto
import com.portfolio.dto.response.MutualFundHoldingsResponseDto
import com.portfolio.dto.response.PagedResponseDto
import com.portfolio.repository.MutualFundRepository
import com.portfolio.service.HoldingsService
import com.portfolio.service.ScreenerService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/mutual-funds")
class MutualFundController(
    private val screenerService: ScreenerService,
    private val mutualFundRepository: MutualFundRepository,
    private val holdingsService: HoldingsService
) {
    @GetMapping
    fun getMutualFunds(
        @RequestParam(required = false) issuer: String?,
        @RequestParam(required = false) fundType: String?,
        @RequestParam(required = false) assetClass: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) symbolContains: String?,
        @RequestParam(required = false) nameContains: String?,
        @RequestParam(required = false) maxExpenseRatio: Double?,
        @RequestParam(required = false) maxMinimumInvestment: Double?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "symbol:asc") sort: String
    ): ResponseEntity<PagedResponseDto<MutualFundDto>> {
        val filter = MutualFundFilterRequest(
            issuer = issuer,
            fundType = fundType,
            assetClass = assetClass,
            status = status,
            symbolContains = symbolContains,
            nameContains = nameContains,
            maxExpenseRatio = maxExpenseRatio,
            maxMinimumInvestment = maxMinimumInvestment
        )
        val pageable = createPageable(page, size, sort)
        val result = screenerService.filterMutualFunds(filter, pageable)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}")
    fun getMutualFundById(@PathVariable id: Long): ResponseEntity<MutualFundDto> {
        val fund = mutualFundRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(MutualFundDto.from(fund))
    }

    @GetMapping("/{id}/holdings")
    fun getMutualFundHoldings(
        @PathVariable id: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) asOfDate: LocalDate?
    ): ResponseEntity<MutualFundHoldingsResponseDto> {
        val holdings = holdingsService.getMutualFundHoldings(id, asOfDate)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(holdings)
    }

    @GetMapping("/{id}/holdings/dates")
    fun getMutualFundHoldingsDates(@PathVariable id: Long): ResponseEntity<AvailableDatesResponseDto> {
        return ResponseEntity.ok(holdingsService.getMutualFundHoldingsDates(id))
    }

    private fun createPageable(page: Int, size: Int, sort: String): PageRequest {
        val clampedSize = size.coerceIn(1, 100)
        val parts = sort.split(":")
        val field = parts.getOrElse(0) { "symbol" }
        val direction = if (parts.getOrElse(1) { "asc" }.equals("desc", ignoreCase = true)) {
            Sort.Direction.DESC
        } else {
            Sort.Direction.ASC
        }
        return PageRequest.of(page, clampedSize, Sort.by(direction, field))
    }
}
