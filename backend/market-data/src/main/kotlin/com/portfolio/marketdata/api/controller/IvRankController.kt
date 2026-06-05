package com.portfolio.marketdata.api.controller

import com.portfolio.marketdata.api.dto.IvRankResponse
import com.portfolio.marketdata.db.repository.IvObservationRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/iv")
class IvRankController(
    private val ivObservationRepository: IvObservationRepository
) {

    @GetMapping("/{ticker}")
    fun getIvRank(@PathVariable ticker: String): ResponseEntity<IvRankResponse> {
        val today = LocalDate.now()
        val oneYearAgo = today.minusDays(365)

        val observations = ivObservationRepository.findByTickerAndObservedDateAfter(ticker, oneYearAgo)
        if (observations.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val currentObservation = ivObservationRepository.findByTickerAndObservedDate(ticker, today)
            ?: observations.maxByOrNull { it.observedDate }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val currentIv = currentObservation.atmIv
        val allIvs = observations.map { it.atmIv }.sorted()
        val countBelow = allIvs.count { it < currentIv }

        val ivRank = if (allIvs.isNotEmpty()) {
            BigDecimal(countBelow).divide(BigDecimal(allIvs.size), 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
        } else BigDecimal.ZERO

        return ResponseEntity.ok(IvRankResponse(
            ticker = ticker, currentIv = currentIv, ivRank = ivRank, ivPercentile = ivRank,
            periodStart = observations.minOfOrNull { it.observedDate } ?: oneYearAgo,
            periodEnd = observations.maxOfOrNull { it.observedDate } ?: today,
            observationCount = allIvs.size
        ))
    }
}
