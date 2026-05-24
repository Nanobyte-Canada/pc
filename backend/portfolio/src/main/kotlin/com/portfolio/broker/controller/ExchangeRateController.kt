package com.portfolio.broker.controller

import com.portfolio.broker.dto.ExchangeRateResponse
import com.portfolio.broker.service.ExchangeRateService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/exchange-rates")
@PreAuthorize("isAuthenticated()")
class ExchangeRateController(
    private val exchangeRateService: ExchangeRateService
) {
    @GetMapping("/rate/{currency}")
    fun getRate(@PathVariable currency: String): ResponseEntity<ExchangeRateResponse> {
        val today = LocalDate.now()
        val rate = exchangeRateService.getRate(currency.uppercase(), today)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            ExchangeRateResponse(
                currency = currency.uppercase(),
                rateToCAD = rate,
                date = today
            )
        )
    }
}
