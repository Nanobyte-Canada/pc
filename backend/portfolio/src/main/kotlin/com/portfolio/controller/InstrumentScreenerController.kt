package com.portfolio.controller

import com.portfolio.dto.request.InstrumentFilterRequest
import com.portfolio.dto.response.InstrumentDetailDto
import com.portfolio.dto.response.InstrumentScreenerDto
import com.portfolio.dto.response.PagedResponseDto
import com.portfolio.dto.response.ScreenerSearchResponseDto
import com.portfolio.service.InstrumentScreenerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/screener")
class InstrumentScreenerController(
    private val instrumentScreenerService: InstrumentScreenerService
) {

    /**
     * Search instruments across all types.
     * Mapped before /{type} to avoid Spring treating "search" as a type path variable.
     */
    @GetMapping("/search")
    fun searchInstruments(
        @RequestParam query: String,
        @RequestParam(required = false) types: List<String>?,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ScreenerSearchResponseDto> {
        val mappedTypes = types?.map { mapTypeParam(it) }
        val result = instrumentScreenerService.searchInstruments(query, mappedTypes, limit)
        return ResponseEntity.ok(result)
    }

    /**
     * Returns instrument counts grouped by type (for sidebar badges).
     */
    @GetMapping("/counts")
    fun getTypeCounts(): ResponseEntity<Map<String, Long>> {
        val counts = instrumentScreenerService.getTypeCounts()
        return ResponseEntity.ok(counts)
    }

    /**
     * Returns distinct values for a given field within an instrument type (for filter dropdowns).
     */
    @GetMapping("/reference/{type}/{field}")
    fun getReferenceData(
        @PathVariable type: String,
        @PathVariable field: String
    ): ResponseEntity<List<String>> {
        val instrumentType = mapTypeParam(type)
        val values = instrumentScreenerService.getDistinctValues(field, instrumentType)
        return ResponseEntity.ok(values)
    }

    /**
     * Returns detailed information for a single instrument.
     */
    @GetMapping("/detail/{type}/{ticker}")
    fun getInstrumentDetail(
        @PathVariable type: String,
        @PathVariable ticker: String
    ): ResponseEntity<InstrumentDetailDto> {
        val instrumentType = mapTypeParam(type)
        val detail = instrumentScreenerService.getInstrumentDetail(ticker, instrumentType)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail)
    }

    /**
     * Paginated, filterable screener grid data for a given instrument type.
     */
    @GetMapping("/{type}")
    fun getInstruments(
        @PathVariable type: String,
        @RequestParam(required = false) tickerContains: String?,
        @RequestParam(required = false) nameContains: String?,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) exchange: String?,
        @RequestParam(required = false) sector: String?,
        @RequestParam(required = false) issuer: String?,
        @RequestParam(required = false) assetClass: String?,
        @RequestParam(required = false) fundCategory: String?,
        @RequestParam(required = false) fundStyle: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "ticker") sortField: String,
        @RequestParam(defaultValue = "asc") sortDirection: String
    ): ResponseEntity<PagedResponseDto<InstrumentScreenerDto>> {
        val instrumentType = mapTypeParam(type)
        val filter = InstrumentFilterRequest(
            instrumentType = instrumentType,
            tickerContains = tickerContains,
            nameContains = nameContains,
            country = country,
            exchange = exchange,
            sector = sector,
            issuer = issuer,
            assetClass = assetClass,
            fundCategory = fundCategory,
            fundStyle = fundStyle
        )
        val result = instrumentScreenerService.filterInstruments(filter, page, size, sortField, sortDirection)
        return ResponseEntity.ok(result)
    }

    private fun mapTypeParam(type: String): String {
        return when (type.lowercase()) {
            "stocks", "stock" -> "STOCK"
            "etfs", "etf" -> "ETF"
            "mutual-funds", "mutual_funds", "mutualfunds" -> "MUTUAL_FUND"
            "preferred-stocks", "preferred_stocks" -> "PREFERRED_STOCK"
            "indices", "index" -> "INDEX"
            "bonds", "bond" -> "BOND"
            else -> type.uppercase()
        }
    }
}
