package com.portfolio.ingestion.client.etfcom

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.dto.etfcom.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

sealed class EtfComApiResult<out T> {
    data class Success<T>(val data: T, val rawJson: String = "") : EtfComApiResult<T>()
    data object NotFound : EtfComApiResult<Nothing>()
    data class Error(val statusCode: Int?, val message: String) : EtfComApiResult<Nothing>()
}

@Component
class EtfComClient(
    private val etfComWebClient: WebClient,
    private val objectMapper: ObjectMapper,
    private val config: IngestionConfig,
    private val responseParser: EtfComResponseParser,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val requestCounter = Counter.builder("etfcom_api_requests_total").register(meterRegistry)
    private val successCounter = Counter.builder("etfcom_api_success_total").register(meterRegistry)
    private val errorCounter = Counter.builder("etfcom_api_error_total").register(meterRegistry)
    private val notFoundCounter = Counter.builder("etfcom_api_not_found_total").register(meterRegistry)
    private val rateLimitCounter = Counter.builder("etfcom_api_rate_limited_total").register(meterRegistry)
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

    fun fetchAllData(ticker: String, fundId: Int? = null): EtfComApiResult<EtfComEnrichmentData> {
        requestCounter.increment()
        val sample = Timer.start()
        val rawPayloads = mutableMapOf<String, String>()
        val fundIdStr = fundId?.toString() ?: "0"

        try {
            // 1. Fund Summary — if this returns NotFound or Error, short-circuit
            val summaryResult = fetchQuery(ticker, "fundSummaryData", fundIdStr)
            if (summaryResult is EtfComApiResult.NotFound) {
                notFoundCounter.increment()
                sample.stop(latencyTimer)
                return EtfComApiResult.NotFound
            }
            if (summaryResult is EtfComApiResult.Error) {
                errorCounter.increment()
                sample.stop(latencyTimer)
                return summaryResult
            }
            val summarySuccess = summaryResult as EtfComApiResult.Success
            rawPayloads["fundSummaryData"] = summarySuccess.rawJson
            val summary = responseParser.parseFundSummary(summarySuccess.data)

            // 2. Top Holdings
            val holdingsResult = fetchQuery(ticker, "topHoldings", fundIdStr)
            var holdings: EtfComTopHoldingsResponse? = null
            if (holdingsResult is EtfComApiResult.Success) {
                rawPayloads["topHoldings"] = holdingsResult.rawJson
                holdings = responseParser.parseTopHoldings(holdingsResult.data)
            }

            // 3. Sector Breakdown
            val sectorResult = fetchQuery(ticker, "sectorIndustryBreakdown", fundIdStr)
            var sectors: EtfComSectorBreakdownResponse? = null
            if (sectorResult is EtfComApiResult.Success) {
                rawPayloads["sectorIndustryBreakdown"] = sectorResult.rawJson
                sectors = responseParser.parseSectorBreakdown(sectorResult.data)
            }

            // 4. Performance
            val perfResult = fetchQuery(ticker, "performanceData", fundIdStr)
            var performance: EtfComPerformanceResponse? = null
            if (perfResult is EtfComApiResult.Success) {
                rawPayloads["performanceData"] = perfResult.rawJson
                performance = responseParser.parsePerformance(perfResult.data)
            }

            // 5. Portfolio Data
            val portfolioResult = fetchQuery(ticker, "fundPortfolioData", fundIdStr)
            var portfolio: EtfComPortfolioDataResponse? = null
            if (portfolioResult is EtfComApiResult.Success) {
                rawPayloads["fundPortfolioData"] = portfolioResult.rawJson
                portfolio = responseParser.parsePortfolioData(portfolioResult.data)
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

    private fun fetchQuery(ticker: String, queryType: String, fundId: String = "0"): EtfComApiResult<JsonNode> {
        val request = EtfComRequest(
            query = queryType,
            variables = EtfComRequestVariables(ticker = ticker, fund_id = fundId)
        )
        val retryConfig = config.etfcom.retry
        var attempt = 0
        var backoffMs = retryConfig.initialBackoffMs

        while (true) {
            try {
                Thread.sleep(config.etfcom.requestDelayMs)

                val rawJson = etfComWebClient.post()
                    .uri("/fund-details")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .block()

                if (rawJson.isNullOrBlank()) {
                    return EtfComApiResult.NotFound
                }

                log.debug("etf.com response for {} query {}: {}", ticker, queryType, rawJson.take(500))

                val rootNode = objectMapper.readTree(rawJson)
                // Extract inner data node from wrapper: data.{queryType}
                val innerNode = rootNode.path("data").path(queryType)
                if (innerNode.isMissingNode || innerNode.isNull) {
                    log.warn("etf.com response for {} query {} has no data.{} node", ticker, queryType, queryType)
                    return EtfComApiResult.NotFound
                }

                return EtfComApiResult.Success(data = innerNode, rawJson = rawJson)
            } catch (e: WebClientResponseException) {
                if (e.statusCode.value() == 429 && attempt < retryConfig.maxAttempts) {
                    attempt++
                    rateLimitCounter.increment()
                    log.warn("429 rate limited for {} query {}, retry {}/{} after {}ms",
                        ticker, queryType, attempt, retryConfig.maxAttempts, backoffMs)
                    Thread.sleep(backoffMs)
                    backoffMs = (backoffMs * retryConfig.multiplier).toLong()
                        .coerceAtMost(retryConfig.maxBackoffMs)
                    continue
                }
                return when {
                    e.statusCode.value() == 429 -> {
                        log.error("429 rate limited for {} query {} — exhausted {} retries", ticker, queryType, retryConfig.maxAttempts)
                        EtfComApiResult.Error(429, "Rate limited after ${retryConfig.maxAttempts} retries")
                    }
                    e.statusCode.is5xxServerError -> {
                        log.warn("etf.com server error {} for ticker {} query {}", e.statusCode.value(), ticker, queryType)
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
}
