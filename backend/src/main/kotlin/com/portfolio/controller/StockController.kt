package com.portfolio.controller

import com.portfolio.dto.request.StockFilterRequest
import com.portfolio.dto.response.PagedResponseDto
import com.portfolio.dto.response.StockDto
import com.portfolio.repository.StockRepository
import com.portfolio.service.ScreenerService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/stocks")
class StockController(
    private val screenerService: ScreenerService,
    private val stockRepository: StockRepository
) {
    @GetMapping
    fun getStocks(
        @RequestParam(required = false) sector: String?,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) exchange: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) tickerContains: String?,
        @RequestParam(required = false) nameContains: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "ticker:asc") sort: String
    ): ResponseEntity<PagedResponseDto<StockDto>> {
        val filter = StockFilterRequest(
            sector = sector,
            country = country,
            exchange = exchange,
            status = status,
            tickerContains = tickerContains,
            nameContains = nameContains
        )
        val pageable = createPageable(page, size, sort)
        val result = screenerService.filterStocks(filter, pageable)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}")
    fun getStockById(@PathVariable id: Long): ResponseEntity<StockDto> {
        val stock = stockRepository.findByIdWithGics(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(StockDto.from(stock))
    }

    private fun createPageable(page: Int, size: Int, sort: String): PageRequest {
        val clampedSize = size.coerceIn(1, 100)
        val parts = sort.split(":")
        val field = parts.getOrElse(0) { "ticker" }
        val direction = if (parts.getOrElse(1) { "asc" }.equals("desc", ignoreCase = true)) {
            Sort.Direction.DESC
        } else {
            Sort.Direction.ASC
        }
        return PageRequest.of(page, clampedSize, Sort.by(direction, field))
    }
}
