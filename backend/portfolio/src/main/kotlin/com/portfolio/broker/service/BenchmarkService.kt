package com.portfolio.broker.service

import com.portfolio.broker.dto.ReturnPoint
import com.portfolio.broker.entity.BenchmarkReturn
import com.portfolio.broker.repository.BenchmarkReturnRepository
import com.portfolio.broker.repository.ModelPortfolioRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class BenchmarkService(
    private val benchmarkReturnRepository: BenchmarkReturnRepository,
    private val modelPortfolioRepository: ModelPortfolioRepository
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

    /**
     * Calculate theoretical cumulative returns for a model portfolio.
     * Uses weighted daily returns of constituent ETFs from benchmark_returns table.
     */
    fun getModelPortfolioBenchmarkReturns(
        modelId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ReturnPoint> {
        val model = modelPortfolioRepository.findById(modelId).orElse(null) ?: return emptyList()
        val allocations = model.allocations
        if (allocations.isEmpty()) return emptyList()

        // Normalize weights to decimal form
        val totalPercent = allocations.sumOf { it.targetPercent }
        if (totalPercent <= BigDecimal.ZERO) return emptyList()

        val weights = allocations.associate { alloc ->
            alloc.symbol to alloc.targetPercent.divide(totalPercent, 8, RoundingMode.HALF_UP)
        }

        // Fetch daily returns for each constituent
        val returnsBySymbol = mutableMapOf<String, Map<LocalDate, BigDecimal>>()
        for (symbol in weights.keys) {
            val returns = benchmarkReturnRepository.findBySymbolAndReturnDateBetweenOrderByReturnDateAsc(
                symbol, startDate, endDate
            )
            if (returns.isNotEmpty()) {
                returnsBySymbol[symbol] = returns.associate { it.returnDate to it.closePrice }
            }
        }

        if (returnsBySymbol.isEmpty()) return emptyList()

        // Collect all dates across all symbols
        val allDates = returnsBySymbol.values.flatMap { it.keys }.distinct().sorted()
        if (allDates.isEmpty()) return emptyList()

        // Calculate weighted cumulative return
        // For each symbol: cumReturn = (price_t / price_0) - 1
        // Model return = sum(weight_i * cumReturn_i)
        val startPrices = mutableMapOf<String, BigDecimal>()
        for ((symbol, pricesByDate) in returnsBySymbol) {
            val firstDate = allDates.firstOrNull { pricesByDate.containsKey(it) }
            if (firstDate != null) {
                startPrices[symbol] = pricesByDate[firstDate]!!
            }
        }

        return allDates.map { date ->
            var weightedReturn = BigDecimal.ZERO
            var coveredWeight = BigDecimal.ZERO

            for ((symbol, weight) in weights) {
                val prices = returnsBySymbol[symbol] ?: continue
                val currentPrice = prices[date] ?: continue
                val startPrice = startPrices[symbol] ?: continue
                if (startPrice <= BigDecimal.ZERO) continue

                val symbolReturn = (currentPrice - startPrice).divide(startPrice, 8, RoundingMode.HALF_UP)
                weightedReturn += weight * symbolReturn
                coveredWeight += weight
            }

            // Scale up if not all symbols have data
            val scaledReturn = if (coveredWeight > BigDecimal.ZERO && coveredWeight < BigDecimal.ONE) {
                weightedReturn.divide(coveredWeight, 8, RoundingMode.HALF_UP)
            } else weightedReturn

            ReturnPoint(
                date = date.toString(),
                cumulativeReturn = scaledReturn.multiply(BigDecimal(100)).setScale(4, RoundingMode.HALF_UP),
                portfolioValue = BigDecimal.ZERO // Not applicable for model benchmarks
            )
        }
    }
}
