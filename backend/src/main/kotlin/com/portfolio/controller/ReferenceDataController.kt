package com.portfolio.controller

import com.portfolio.dto.response.CountryDto
import com.portfolio.dto.response.ExchangeDto
import com.portfolio.dto.response.GicsSectorDto
import com.portfolio.dto.response.GicsSectorSimpleDto
import com.portfolio.service.ReferenceDataService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reference")
class ReferenceDataController(
    private val referenceDataService: ReferenceDataService
) {
    @GetMapping("/gics")
    fun getGicsHierarchy(): ResponseEntity<List<GicsSectorDto>> {
        return ResponseEntity.ok(referenceDataService.getGicsHierarchy())
    }

    @GetMapping("/gics/sectors")
    fun getGicsSectors(): ResponseEntity<List<GicsSectorSimpleDto>> {
        return ResponseEntity.ok(referenceDataService.getGicsSectors())
    }

    @GetMapping("/countries")
    fun getCountries(): ResponseEntity<List<CountryDto>> {
        return ResponseEntity.ok(referenceDataService.getCountries())
    }

    @GetMapping("/exchanges")
    fun getExchanges(): ResponseEntity<List<ExchangeDto>> {
        return ResponseEntity.ok(referenceDataService.getExchanges())
    }
}
