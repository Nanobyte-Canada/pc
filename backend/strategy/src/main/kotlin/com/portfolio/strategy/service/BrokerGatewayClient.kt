package com.portfolio.strategy.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

@Service
/** HTTP client for atomic combo orders in the broker-gateway service. */
class BrokerGatewayClient(
    @Value("\${broker-gateway.url:http://localhost:8084}")
    private val brokerGatewayUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient = WebClient.builder().baseUrl(brokerGatewayUrl).build()

    /** Places one atomic multi-leg order and returns the gateway result. */
    fun placeComboOrder(
        connectionId: Long,
        accountId: String,
        legs: List<Map<String, Any?>>,
        orderType: String,
        limitPrice: BigDecimal,
        timeInForce: String
    ): Map<String, Any?> {
        val body: Map<String, Any?> = mapOf(
            "legs" to legs,
            "orderType" to orderType,
            "limitPrice" to limitPrice,
            "timeInForce" to timeInForce
        )

        return try {
            @Suppress("UNCHECKED_CAST")
            val response = webClient.post()
                .uri(
                    "/api/v1/gateway/connections/{connectionId}/accounts/{accountId}/combo-orders",
                    connectionId.toString(), accountId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any?>
            response ?: mapOf("status" to "ERROR", "message" to "No response from broker gateway")
        } catch (e: Exception) {
            log.error("Failed to place combo order via broker-gateway: {}", e.message)
            mapOf(
                "status" to "ERROR",
                "message" to "Failed to communicate with broker gateway: ${e.message}"
            )
        }
    }
}
