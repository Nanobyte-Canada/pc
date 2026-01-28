package com.portfolio.controller

import com.portfolio.dto.request.EtfFilterRequest
import com.portfolio.dto.response.AvailableDatesResponseDto
import com.portfolio.dto.response.EtfDto
import com.portfolio.dto.response.EtfHoldingsResponseDto
import com.portfolio.dto.response.PagedResponseDto
import com.portfolio.repository.EtfRepository
import com.portfolio.service.HoldingsService
import com.portfolio.service.ScreenerService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/etfs")
class EtfController(
    private val screenerService: ScreenerService,
    private val etfRepository: EtfRepository,
    private val holdingsService: HoldingsService
) {
    @GetMapping
    fun getEtfs(
        @RequestParam(required = false) issuer: String?,
        @RequestParam(required = false) assetClass: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) symbolContains: String?,
        @RequestParam(required = false) nameContains: String?,
        @RequestParam(required = false) maxExpenseRatio: Double?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "symbol:asc") sort: String
    ): ResponseEntity<PagedResponseDto<EtfDto>> {
        val filter = EtfFilterRequest(
            issuer = issuer,
            assetClass = assetClass,
            status = status,
            symbolContains = symbolContains,
            nameContains = nameContains,
            maxExpenseRatio = maxExpenseRatio
        )
        val pageable = createPageable(page, size, sort)
        val result = screenerService.filterEtfs(filter, pageable)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}")
    fun getEtfById(@PathVariable id: Long): ResponseEntity<EtfDto> {
        val etf = etfRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(EtfDto.from(etf))
    }

    @GetMapping("/{id}/holdings")
    fun getEtfHoldings(
        @PathVariable id: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) asOfDate: LocalDate?
    ): ResponseEntity<EtfHoldingsResponseDto> {
        val holdings = holdingsService.getEtfHoldings(id, asOfDate)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(holdings)
    }

    @GetMapping("/{id}/holdings/dates")
    fun getEtfHoldingsDates(@PathVariable id: Long): ResponseEntity<AvailableDatesResponseDto> {
        return ResponseEntity.ok(holdingsService.getEtfHoldingsDates(id))
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
