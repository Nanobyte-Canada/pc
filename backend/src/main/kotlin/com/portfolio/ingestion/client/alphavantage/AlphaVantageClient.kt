package com.portfolio.ingestion.client.alphavantage

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.dto.alphavantage.AVOverviewResponse
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * HTTP client for Alpha Vantage API.
 * Provides access to stock overview and ETF profile data.
 *
 * Includes rate limiting, retry logic, and metrics.
 *
 * Supports both blocking and non-blocking (coroutine) modes:
 * - Use `getStockOverview()` for blocking calls
 * - Use `getStockOverviewAsync()` for non-blocking coroutine-based calls
 */
@Component
class AlphaVantageClient(
    private val config: IngestionConfig,
    private val rateLimiter: AlphaVantageRateLimiter,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    private val alphaVantageWebClient: WebClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient: WebClient = alphaVantageWebClient

    // Metrics
    private val requestsTotal = Counter.builder("av_api_requests_total")
        .description("Total Alpha Vantage API requests")
        .register(meterRegistry)

    private val rateLimitHits = Counter.builder("av_api_rate_limit_total")
        .description("Alpha Vantage rate limit responses")
        .register(meterRegistry)

    private val successTotal = Counter.builder("av_api_success_total")
        .description("Successful Alpha Vantage API responses")
        .register(meterRegistry)

    private val errorTotal = Counter.builder("av_api_error_total")
        .description("Failed Alpha Vantage API responses")
        .register(meterRegistry)

    private val apiLatency = Timer.builder("av_api_latency_seconds")
        .description("Alpha Vantage API request latency")
        .register(meterRegistry)

    /**
     * Fetches stock overview data from the OVERVIEW endpoint asynchronously.
     * Returns null if the symbol is not found or daily quota is exhausted.
     *
     * This is the preferred method for coroutine-based callers.
     */
    suspend fun getStockOverviewAsync(symbol: String): AVApiResult<AVOverviewResponse> {
        // Check rate limit
        if (!rateLimiter.acquireAsync()) {
            log.warn("Daily quota exhausted, cannot fetch overview for {}", symbol)
            return AVApiResult.QuotaExhausted("Daily API quota exhausted")
        }

        requestsTotal.increment()
        val startTime = System.nanoTime()

        return try {
            val response = webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .queryParam("function", "OVERVIEW")
                        .queryParam("symbol", symbol.uppercase())
                        .queryParam("apikey", config.alphavantage.apiKey)
                        .build()
                }
                .retrieve()
                .onStatus(HttpStatusCode::isError) { clientResponse ->
                    when (clientResponse.statusCode()) {
                        HttpStatus.TOO_MANY_REQUESTS -> {
                            rateLimitHits.increment()
                            Mono.error(AlphaVantageException("Rate limited", 429))
                        }
                        HttpStatus.NOT_FOUND -> {
                            Mono.error(AlphaVantageException("Symbol not found: $symbol", 404))
                        }
                        else -> {
                            clientResponse.createException()
                        }
                    }
                }
                .bodyToMono(AVOverviewResponse::class.java)
                .timeout(Duration.ofSeconds(30))
                .awaitSingleOrNull()  // Non-blocking await

            if (response == null) {
                errorTotal.increment()
                AVApiResult.NotFound(symbol)
            } else if (response.isRateLimited()) {
                rateLimitHits.increment()
                log.warn("Rate limited response for {}: {}", symbol, response.note ?: response.information)
                AVApiResult.RateLimited(response.note ?: response.information ?: "Rate limited")
            } else if (!response.isValid()) {
                log.debug("Empty or invalid response for symbol: {}", symbol)
                AVApiResult.NotFound(symbol)
            } else {
                successTotal.increment()
                log.debug("Successfully fetched overview for {}", symbol)
                AVApiResult.Success(response)
            }
        } catch (e: AlphaVantageException) {
            errorTotal.increment()
            when (e.statusCode) {
                404 -> AVApiResult.NotFound(symbol)
                429 -> AVApiResult.RateLimited("Rate limited")
                else -> AVApiResult.Error(e, e.statusCode)
            }
        } catch (e: WebClientResponseException) {
            errorTotal.increment()
            log.error("HTTP error fetching overview for {}: {} {}", symbol, e.statusCode, e.message)
            AVApiResult.Error(e, e.statusCode.value())
        } catch (e: Exception) {
            errorTotal.increment()
            log.error("Error fetching overview for {}: {}", symbol, e.message)
            AVApiResult.Error(e, null)
        } finally {
            apiLatency.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
        }
    }

    /**
     * Fetches stock overview data from the OVERVIEW endpoint.
     * Returns null if the symbol is not found or daily quota is exhausted.
     *
     * This is a blocking wrapper around getStockOverviewAsync for backward compatibility.
     */
    fun getStockOverview(symbol: String): AVApiResult<AVOverviewResponse> = runBlocking {
        getStockOverviewAsync(symbol)
    }

    /**
     * Returns the remaining daily quota.
     */
    fun remainingDailyQuota(): Int = rateLimiter.remainingDailyQuota()

    /**
     * Returns whether the daily quota is exhausted.
     */
    fun isDailyQuotaExhausted(): Boolean = rateLimiter.isDailyQuotaExhausted()

    /**
     * Checks if an error is retryable.
     */
    fun isRetryableError(result: AVApiResult<*>): Boolean = when (result) {
        is AVApiResult.RateLimited -> true
        is AVApiResult.Error -> result.statusCode in listOf(429, 500, 502, 503, 504)
        else -> false
    }
}

/**
 * Sealed class representing the result of an Alpha Vantage API call.
 */
sealed class AVApiResult<out T> {
    data class Success<T>(val data: T) : AVApiResult<T>()
    data class NotFound(val symbol: String) : AVApiResult<Nothing>()
    data class RateLimited(val message: String) : AVApiResult<Nothing>()
    data class QuotaExhausted(val message: String) : AVApiResult<Nothing>()
    data class Error(val exception: Exception, val statusCode: Int?) : AVApiResult<Nothing>()
}

/**
 * Custom exception for Alpha Vantage API errors.
 */
class AlphaVantageException(message: String, val statusCode: Int) : Exception(message)
