package com.portfolio.strategy.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

@Service
class BrokerGatewayClient(
    @Value("\${broker-gateway.url:http://localhost:8084}")
    private val brokerGatewayUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient = WebClient.builder().baseUrl(brokerGatewayUrl).build()

    fun placeComboOrder(
        connectionId: Long,
        accountId: String,
        legs: List<Map<String, Any?>>,
        orderType: String,
        limitPrice: BigDecimal,
        timeInForce: String
    ): Map<String, Any?> {
        val body = mapOf(
            "legs" to legs,
            "orderType" to orderType,
            "limitPrice" to limitPrice,
            "timeInForce" to timeInForce
        )

        return try {
            webClient.post()
                .uri(
                    "/api/v1/gateway/connections/{connectionId}/accounts/{accountId}/combo-orders",
                    connectionId.toString(), accountId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
                ?: mapOf("status" to "ERROR", "message" to "No response from broker gateway")
        } catch (e: Exception) {
            log.error("Failed to place combo order via broker-gateway: {}", e.message)
            mapOf(
                "status" to "ERROR",
                "message" to "Failed to communicate with broker gateway: ${e.message}"
            )
        }
    }
}
