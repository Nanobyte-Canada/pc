package com.portfolio.controller

import com.portfolio.dto.request.PortfolioAnalyzeRequest
import com.portfolio.dto.request.PortfolioNormalizeRequest
import com.portfolio.dto.request.PortfolioValidateRequest
import com.portfolio.dto.response.NormalizeResponseDto
import com.portfolio.dto.response.PortfolioAnalysisResponseDto
import com.portfolio.dto.response.ValidateResponseDto
import com.portfolio.service.PortfolioAnalysisService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/portfolio")
class PortfolioController(
    private val portfolioAnalysisService: PortfolioAnalysisService
) {
    @PostMapping("/analyze")
    fun analyzePortfolio(
        @RequestBody request: PortfolioAnalyzeRequest
    ): ResponseEntity<PortfolioAnalysisResponseDto> {
        val result = portfolioAnalysisService.analyze(request)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/validate")
    fun validatePortfolio(
        @RequestBody request: PortfolioValidateRequest
    ): ResponseEntity<ValidateResponseDto> {
        val result = portfolioAnalysisService.validate(request)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/normalize")
    fun normalizePortfolio(
        @RequestBody request: PortfolioNormalizeRequest
    ): ResponseEntity<NormalizeResponseDto> {
        val result = portfolioAnalysisService.normalize(request)
        return ResponseEntity.ok(result)
    }
}
