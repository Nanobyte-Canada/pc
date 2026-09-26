package com.portfolio.brokergateway.adapter.questrade

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import com.portfolio.brokergateway.exception.BrokerConnectionException
import com.portfolio.brokergateway.exception.BrokerDataException
import com.portfolio.brokergateway.exception.BrokerRateLimitException
import com.portfolio.brokergateway.exception.BrokerTransientException
import io.netty.channel.ChannelOption
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.HttpClient
import java.time.Duration

class QuestradeRestClient(
    private val webClientBuilder: WebClient.Builder = WebClient.builder(),
    private val responseTimeoutMs: Long = 30_000,
    private val connectTimeoutMs: Int = 10_000,
    private val retryPolicy: HttpRetryPolicy = HttpRetryPolicy(),
    private val rateLimiter: QuestradeRateLimiter = QuestradeRateLimiter(1),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(apiServerUrl: String, accessToken: String, path: String): JsonNode {
        rateLimiter.acquire()
        var attempt = 1
        while (true) {
            try {
                return fetchOnce(apiServerUrl, accessToken, path)
            } catch (e: BrokerRateLimitException) {
                if (!retryPolicy.shouldRetry(attempt)) throw e
                Thread.sleep(retryPolicy.delayMs(attempt, e.retryAfterSeconds, retryPolicy.jitter()))
                attempt++
            } catch (e: BrokerTransientException) {
                if (!retryPolicy.shouldRetry(attempt)) throw e
                Thread.sleep(retryPolicy.delayMs(attempt, null, retryPolicy.jitter()))
                attempt++
            }
        }
    }

    fun post(apiServerUrl: String, accessToken: String, path: String, body: Any): JsonNode {
        rateLimiter.acquire() // pace only — never retry: order placement must not double-submit
        return try {
            val client = buildClient(apiServerUrl, accessToken)
            client.post().uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerDataException("Empty response from Questrade: $path", BrokerType.QUESTRADE)
        } catch (e: WebClientResponseException) {
            handleError(e, path)
        } catch (e: BrokerDataException) {
            throw e
        } catch (e: Exception) {
            throw BrokerConnectionException("Failed to reach Questrade API: ${e.message}", BrokerType.QUESTRADE, e)
        }
    }

    fun delete(apiServerUrl: String, accessToken: String, path: String): JsonNode? {
        rateLimiter.acquire() // pace only — never retry: order cancellation must not double-submit
        return try {
            val client = buildClient(apiServerUrl, accessToken)
            client.delete().uri(path)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block()
        } catch (e: WebClientResponseException) {
            handleError(e, path)
        } catch (e: Exception) {
            throw BrokerConnectionException("Failed to reach Questrade API: ${e.message}", BrokerType.QUESTRADE, e)
        }
    }

    private fun fetchOnce(apiServerUrl: String, accessToken: String, path: String): JsonNode {
        return try {
            val client = buildClient(apiServerUrl, accessToken)
            client.get().uri(path)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerDataException("Empty response from Questrade: $path", BrokerType.QUESTRADE)
        } catch (e: WebClientResponseException) {
            handleError(e, path)
        } catch (e: BrokerDataException) {
            throw e
        } catch (e: Exception) {
            throw BrokerConnectionException("Failed to reach Questrade API: ${e.message}", BrokerType.QUESTRADE, e)
        }
    }

    private fun buildClient(baseUrl: String, token: String): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(responseTimeoutMs))
        return webClientBuilder
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
    }

    private fun handleError(e: WebClientResponseException, path: String): Nothing {
        log.error("Questrade API error {} on {}: {}", e.statusCode, path, e.responseBodyAsString)
        val status = e.statusCode.value()
        when {
            status == 401 ->
                throw BrokerAuthenticationException("Questrade auth failed on $path", BrokerType.QUESTRADE)
            status == 429 ->
                throw BrokerRateLimitException(
                    "Questrade rate limit hit on $path", BrokerType.QUESTRADE,
                    retryAfterSeconds = e.headers.getFirst(HttpHeaders.RETRY_AFTER)?.trim()?.toIntOrNull()
                )
            retryPolicy.isRetryable(status) -> // 502/503/504 — transient, safe to retry GETs
                throw BrokerTransientException(
                    "Questrade transient error $status on $path: ${e.responseBodyAsString}",
                    BrokerType.QUESTRADE, e
                )
            else ->
                throw BrokerDataException("Questrade error ${e.statusCode} on $path: ${e.responseBodyAsString}",
                    BrokerType.QUESTRADE, e)
        }
    }
}
