package com.portfolio.broker.service

import com.portfolio.broker.entity.BenchmarkReturn
import com.portfolio.broker.repository.BenchmarkReturnRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class BenchmarkService(
    private val benchmarkReturnRepository: BenchmarkReturnRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        val SUPPORTED_BENCHMARKS = listOf(
            "SPY" to "S&P 500",
            "XIU" to "TSX Composite"
        )
    }

    fun getBenchmarkReturns(symbol: String, startDate: LocalDate, endDate: LocalDate): List<BenchmarkReturn> {
        return benchmarkReturnRepository.findBySymbolAndReturnDateBetweenOrderByReturnDateAsc(
            symbol, startDate, endDate
        )
    }

    @Transactional
    fun saveBenchmarkReturn(symbol: String, date: LocalDate, closePrice: BigDecimal, previousClose: BigDecimal?) {
        val dailyReturn = if (previousClose != null && previousClose > BigDecimal.ZERO) {
            (closePrice - previousClose).divide(previousClose, 8, RoundingMode.HALF_UP)
        } else null

        val benchmarkReturn = BenchmarkReturn(
            symbol = symbol,
            returnDate = date,
            closePrice = closePrice,
            dailyReturn = dailyReturn
        )

        benchmarkReturnRepository.save(benchmarkReturn)
        log.debug("Saved benchmark return for {} on {}: price={}, return={}",
            symbol, date, closePrice, dailyReturn)
    }

    fun getSupportedBenchmarks(): List<Pair<String, String>> = SUPPORTED_BENCHMARKS
}
