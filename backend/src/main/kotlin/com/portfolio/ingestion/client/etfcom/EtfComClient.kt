package com.portfolio.ingestion.client.etfcom

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.ingestion.dto.etfcom.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

sealed class EtfComApiResult<out T> {
    data class Success<T>(val data: T) : EtfComApiResult<T>()
    data object NotFound : EtfComApiResult<Nothing>()
    data class Error(val statusCode: Int?, val message: String) : EtfComApiResult<Nothing>()
}

@Component
class EtfComClient(
    private val etfComWebClient: WebClient,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val requestCounter = Counter.builder("etfcom_api_requests_total").register(meterRegistry)
    private val successCounter = Counter.builder("etfcom_api_success_total").register(meterRegistry)
    private val errorCounter = Counter.builder("etfcom_api_error_total").register(meterRegistry)
    private val notFoundCounter = Counter.builder("etfcom_api_not_found_total").register(meterRegistry)
    private val latencyTimer = Timer.builder("etfcom_api_latency_seconds").register(meterRegistry)

    fun fetchAllTickers(): List<EtfComTickerDto> {
        log.info("Fetching all ETF tickers from etf.com")
        requestCounter.increment()

        return try {
            val sample = Timer.start()
            val result = etfComWebClient.get()
                .uri("/tickers")
                .retrieve()
                .bodyToMono(Array<EtfComTickerDto>::class.java)
                .block() ?: emptyArray()

            sample.stop(latencyTimer)
            successCounter.increment()
            log.info("Fetched {} tickers from etf.com", result.size)
            result.toList()
        } catch (e: Exception) {
            errorCounter.increment()
            log.error("Failed to fetch tickers from etf.com: {}", e.message, e)
            throw e
        }
    }

    fun fetchAllData(ticker: String): EtfComApiResult<EtfComEnrichmentData> {
        requestCounter.increment()
        val sample = Timer.start()
        val rawPayloads = mutableMapOf<String, String>()

        try {
            // 1. Fund Summary — if this returns NotFound, short-circuit
            val summaryResult = fetchQuery<EtfComFundSummaryResponse>(ticker, "fundSummaryData")
            if (summaryResult is EtfComApiResult.NotFound) {
                notFoundCounter.increment()
                sample.stop(latencyTimer)
                return EtfComApiResult.NotFound
            }
            val summary = (summaryResult as? EtfComApiResult.Success)?.data
            if (summaryResult is EtfComApiResult.Success) {
                rawPayloads["fundSummaryData"] = objectMapper.writeValueAsString(summary)
            }

            // 2. Top Holdings
            val holdingsResult = fetchQuery<EtfComTopHoldingsResponse>(ticker, "topHoldings")
            val holdings = (holdingsResult as? EtfComApiResult.Success)?.data
            if (holdingsResult is EtfComApiResult.Success) {
                rawPayloads["topHoldings"] = objectMapper.writeValueAsString(holdings)
            }

            // 3. Sector Breakdown
            val sectorResult = fetchQuery<EtfComSectorBreakdownResponse>(ticker, "sectorIndustryBreakdown")
            val sectors = (sectorResult as? EtfComApiResult.Success)?.data
            if (sectorResult is EtfComApiResult.Success) {
                rawPayloads["sectorIndustryBreakdown"] = objectMapper.writeValueAsString(sectors)
            }

            // 4. Performance
            val perfResult = fetchQuery<EtfComPerformanceResponse>(ticker, "performanceData")
            val performance = (perfResult as? EtfComApiResult.Success)?.data
            if (perfResult is EtfComApiResult.Success) {
                rawPayloads["performanceData"] = objectMapper.writeValueAsString(performance)
            }

            // 5. Portfolio Data
            val portfolioResult = fetchQuery<EtfComPortfolioDataResponse>(ticker, "fundPortfolioData")
            val portfolio = (portfolioResult as? EtfComApiResult.Success)?.data
            if (portfolioResult is EtfComApiResult.Success) {
                rawPayloads["fundPortfolioData"] = objectMapper.writeValueAsString(portfolio)
            }

            successCounter.increment()
            sample.stop(latencyTimer)

            return EtfComApiResult.Success(
                EtfComEnrichmentData(
                    summary = summary,
                    holdings = holdings,
                    sectors = sectors,
                    performance = performance,
                    portfolio = portfolio,
                    rawPayloads = rawPayloads
                )
            )
        } catch (e: Exception) {
            errorCounter.increment()
            sample.stop(latencyTimer)
            log.error("Error fetching enrichment data for ticker {}: {}", ticker, e.message)
            return EtfComApiResult.Error(null, e.message ?: "Unknown error")
        }
    }

    private inline fun <reified T> fetchQuery(ticker: String, queryType: String): EtfComApiResult<T> {
        val request = EtfComRequest(
            query = queryType,
            variables = EtfComRequestVariables(ticker = ticker)
        )

        return try {
            val response = etfComWebClient.post()
                .uri("/fund-details")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(T::class.java)
                .block()

            if (response == null) {
                EtfComApiResult.NotFound
            } else {
                EtfComApiResult.Success(response)
            }
        } catch (e: WebClientResponseException) {
            when {
                e.statusCode == HttpStatusCode.valueOf(500) -> {
                    log.debug("etf.com returned 500 for ticker {} query {} — treating as NotFound", ticker, queryType)
                    EtfComApiResult.NotFound
                }
                e.statusCode.is5xxServerError -> {
                    log.warn("etf.com server error for ticker {} query {}: {}", ticker, queryType, e.statusCode)
                    EtfComApiResult.Error(e.statusCode.value(), e.message ?: "Server error")
                }
                else -> {
                    log.warn("etf.com error for ticker {} query {}: {}", ticker, queryType, e.statusCode)
                    EtfComApiResult.Error(e.statusCode.value(), e.message ?: "Client error")
                }
            }
        }
    }
}
