package com.portfolio.broker.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import java.time.LocalDate

@Service
class BrokerGatewayClient(
    private val config: BrokerGatewayConfig,
    private val objectMapper: ObjectMapper,
    webClientBuilder: WebClient.Builder
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val timeout = Duration.ofMillis(config.timeoutMs)

    private val webClient = webClientBuilder
        .baseUrl(config.url)
        .defaultHeader("X-Gateway-Api-Key", config.apiKey)
        .build()

    fun createConnection(userId: Long, brokerType: String, credentials: Map<String, Any>): JsonNode {
        val body = mapOf("userId" to userId, "brokerType" to brokerType, "credentials" to credentials)
        return post("/api/v1/gateway/connections", body)
    }

    fun listConnections(userId: Long): JsonNode {
        return get("/api/v1/gateway/connections?userId=$userId")
    }

    fun getConnection(connectionId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId")
    }

    fun deleteConnection(connectionId: String) {
        delete("/api/v1/gateway/connections/$connectionId")
    }

    fun validateConnection(connectionId: String): JsonNode {
        return post("/api/v1/gateway/connections/$connectionId/validate", emptyMap<String, Any>())
    }

    fun refreshConnection(connectionId: String): JsonNode {
        return post("/api/v1/gateway/connections/$connectionId/refresh", emptyMap<String, Any>())
    }

    fun reconnectConnection(connectionId: String, credentials: Map<String, Any>): JsonNode {
        return post("/api/v1/gateway/connections/$connectionId/reconnect", mapOf("credentials" to credentials))
    }

    fun listAccounts(connectionId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId/accounts")
    }

    fun getBalances(connectionId: String, accountId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId/accounts/$accountId/balances")
    }

    fun getPositions(connectionId: String, accountId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId/accounts/$accountId/positions")
    }

    fun getActivities(connectionId: String, accountId: String, startDate: LocalDate?, endDate: LocalDate?): JsonNode {
        val params = mutableListOf<String>()
        startDate?.let { params.add("startDate=$it") }
        endDate?.let { params.add("endDate=$it") }
        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return get("/api/v1/gateway/connections/$connectionId/accounts/$accountId/activities$query")
    }

    fun getOrders(connectionId: String, accountId: String): JsonNode {
        return get("/api/v1/gateway/connections/$connectionId/accounts/$accountId/orders")
    }

    fun placeOrder(connectionId: String, accountId: String, order: Map<String, Any?>): JsonNode {
        return post("/api/v1/gateway/connections/$connectionId/accounts/$accountId/orders", order)
    }

    fun cancelOrder(connectionId: String, accountId: String, brokerOrderId: String) {
        delete("/api/v1/gateway/connections/$connectionId/accounts/$accountId/orders/$brokerOrderId")
    }

    fun getOrderImpact(connectionId: String, accountId: String, order: Map<String, Any?>): JsonNode {
        return post("/api/v1/gateway/connections/$connectionId/accounts/$accountId/orders/impact", order)
    }

    fun getHealth(): JsonNode {
        return get("/api/v1/gateway/health")
    }

    private fun get(path: String): JsonNode {
        return try {
            webClient.get().uri(path)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block(timeout) ?: objectMapper.createObjectNode()
        } catch (e: WebClientResponseException) {
            log.error("Gateway GET {} failed: {} {}", path, e.statusCode, e.responseBodyAsString)
            throw toGatewayApiException(e)
        }
    }

    private fun post(path: String, body: Any): JsonNode {
        return try {
            webClient.post().uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block(timeout) ?: objectMapper.createObjectNode()
        } catch (e: WebClientResponseException) {
            log.error("Gateway POST {} failed: {} {}", path, e.statusCode, e.responseBodyAsString)
            throw toGatewayApiException(e)
        }
    }

    private fun delete(path: String) {
        try {
            webClient.delete().uri(path)
                .retrieve()
                .toBodilessEntity()
                .block(timeout)
        } catch (e: WebClientResponseException) {
            log.error("Gateway DELETE {} failed: {} {}", path, e.statusCode, e.responseBodyAsString)
            throw toGatewayApiException(e)
        }
    }

    /**
     * Converts a [WebClientResponseException] from the gateway into a [GatewayApiException]
     * that preserves the gateway's error code and detail message from the ProblemDetail response body.
     */
    private fun toGatewayApiException(e: WebClientResponseException): GatewayApiException {
        val body = try {
            objectMapper.readTree(e.responseBodyAsString)
        } catch (_: Exception) {
            null
        }
        val detail = body?.get("detail")?.asText()
        val code = body?.get("code")?.asText()
        return GatewayApiException(
            gatewayStatusCode = e.statusCode.value(),
            gatewayErrorCode = code,
            gatewayDetail = detail,
            cause = e
        )
    }
}
