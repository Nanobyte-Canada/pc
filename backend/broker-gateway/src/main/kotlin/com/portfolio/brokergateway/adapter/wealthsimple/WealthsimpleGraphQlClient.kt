// adapter/wealthsimple/WealthsimpleGraphQlClient.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import com.portfolio.brokergateway.exception.BrokerConnectionException
import com.portfolio.brokergateway.exception.BrokerDataException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class WealthsimpleGraphQlClient(
    private val config: WealthsimpleConfig,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val webClientBuilder: WebClient.Builder = WebClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(accessToken: String, operationName: String, query: String, variables: Map<String, Any?> = emptyMap()): JsonNode {
        val body = mapOf(
            "operationName" to operationName,
            "query" to query,
            "variables" to variables
        )

        return try {
            val client = webClientBuilder
                .baseUrl(config.graphqlUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-ws-api-version", "12")
                .defaultHeader("x-platform-os", "web")
                .defaultHeader("x-ws-locale", "en-CA")
                .defaultHeader("x-ws-profile", "trade")
                .build()

            val response = client.post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerDataException("Empty GraphQL response for $operationName", BrokerType.WEALTHSIMPLE)

            val errors = response.get("errors")
            if (errors != null && errors.size() > 0) {
                val msg = errors.firstOrNull()?.get("message")?.asText() ?: "Unknown GraphQL error"
                log.error("Wealthsimple GraphQL error on {}: {}", operationName, msg)
                throw BrokerDataException("GraphQL error on $operationName: $msg", BrokerType.WEALTHSIMPLE)
            }

            response.get("data") ?: throw BrokerDataException("No data in GraphQL response for $operationName", BrokerType.WEALTHSIMPLE)
        } catch (e: WebClientResponseException) {
            handleError(e, operationName)
        } catch (e: BrokerDataException) {
            throw e
        } catch (e: BrokerAuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw BrokerConnectionException("Failed to reach Wealthsimple API: ${e.message}", BrokerType.WEALTHSIMPLE, e)
        }
    }

    private fun handleError(e: WebClientResponseException, operationName: String): Nothing {
        log.error("Wealthsimple HTTP error {} on {}: {}", e.statusCode, operationName, e.responseBodyAsString)
        when {
            e.statusCode == HttpStatusCode.valueOf(401) -> {
                val needsOtp = e.headers.getFirst("x-wealthsimple-otp-required") != null
                throw BrokerAuthenticationException(
                    "Wealthsimple auth failed on $operationName" + if (needsOtp) " (2FA required)" else "",
                    BrokerType.WEALTHSIMPLE, needsReauth = true)
            }
            else -> throw BrokerDataException("Wealthsimple error ${e.statusCode} on $operationName", BrokerType.WEALTHSIMPLE, e)
        }
    }
}
