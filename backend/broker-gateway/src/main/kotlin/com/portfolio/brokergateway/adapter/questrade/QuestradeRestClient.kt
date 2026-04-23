package com.portfolio.brokergateway.adapter.questrade

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import com.portfolio.brokergateway.exception.BrokerConnectionException
import com.portfolio.brokergateway.exception.BrokerDataException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class QuestradeRestClient(
    private val webClientBuilder: WebClient.Builder = WebClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(apiServerUrl: String, accessToken: String, path: String): JsonNode {
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

    fun post(apiServerUrl: String, accessToken: String, path: String, body: Any): JsonNode {
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

    private fun buildClient(apiServerUrl: String, accessToken: String): WebClient {
        return webClientBuilder
            .baseUrl(apiServerUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .build()
    }

    private fun handleError(e: WebClientResponseException, path: String): Nothing {
        log.error("Questrade API error {} on {}: {}", e.statusCode, path, e.responseBodyAsString)
        when {
            e.statusCode == HttpStatusCode.valueOf(401) ->
                throw BrokerAuthenticationException("Questrade auth failed on $path", BrokerType.QUESTRADE)
            e.statusCode == HttpStatusCode.valueOf(429) ->
                throw com.portfolio.brokergateway.exception.BrokerRateLimitException(
                    "Questrade rate limit hit on $path", BrokerType.QUESTRADE)
            else ->
                throw BrokerDataException("Questrade error ${e.statusCode} on $path: ${e.responseBodyAsString}",
                    BrokerType.QUESTRADE, e)
        }
    }
}
