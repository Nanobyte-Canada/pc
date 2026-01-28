package com.portfolio.controller

import com.portfolio.dto.response.InstrumentType
import com.portfolio.dto.response.SearchResponseDto
import com.portfolio.service.InstrumentSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/instruments")
class InstrumentController(
    private val searchService: InstrumentSearchService
) {
    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(required = false) type: String?,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<SearchResponseDto> {
        val types = parseTypes(type)
        val clampedLimit = limit.coerceIn(1, 50)
        val result = searchService.search(q, types, clampedLimit)
        return ResponseEntity.ok(result)
    }

    private fun parseTypes(type: String?): Set<InstrumentType> {
        if (type.isNullOrBlank() || type.equals("all", ignoreCase = true)) {
            return emptySet() // Empty means search all types
        }

        return type.split(",")
            .mapNotNull { t ->
                when (t.trim().uppercase()) {
                    "STOCK" -> InstrumentType.STOCK
                    "ETF" -> InstrumentType.ETF
                    "MUTUAL_FUND", "MF" -> InstrumentType.MUTUAL_FUND
                    else -> null
                }
            }
            .toSet()
    }
}
