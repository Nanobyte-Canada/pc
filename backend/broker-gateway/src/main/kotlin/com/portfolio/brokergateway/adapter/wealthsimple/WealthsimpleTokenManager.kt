// adapter/wealthsimple/WealthsimpleTokenManager.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class WealthsimpleTokenManager(
    private val config: WealthsimpleConfig,
    private val webClientBuilder: WebClient.Builder = WebClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun refreshTokens(credentials: BrokerCredentials.WealthsimpleCredentials): BrokerCredentials.WealthsimpleCredentials {
        val body = mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to credentials.refreshToken,
            "client_id" to config.clientId
        )

        log.info("Refreshing Wealthsimple tokens")

        val response: JsonNode = try {
            webClientBuilder.build()
                .post().uri(config.authUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerAuthenticationException(
                    "Empty response from Wealthsimple token refresh", BrokerType.WEALTHSIMPLE)
        } catch (e: BrokerAuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw BrokerAuthenticationException(
                "Wealthsimple token refresh failed: ${e.message}", BrokerType.WEALTHSIMPLE, cause = e)
        }

        val accessToken = response.get("access_token")?.asText()
            ?: throw BrokerAuthenticationException("No access_token in Wealthsimple response", BrokerType.WEALTHSIMPLE)
        val newRefreshToken = response.get("refresh_token")?.asText()
            ?: throw BrokerAuthenticationException("No refresh_token in Wealthsimple response", BrokerType.WEALTHSIMPLE)
        val expiresIn = response.get("expires_in")?.asLong() ?: 3600L

        log.info("Wealthsimple tokens refreshed")

        return credentials.copy(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn
        )
    }

    fun isTokenExpired(credentials: BrokerCredentials.WealthsimpleCredentials): Boolean {
        val nowEpoch = System.currentTimeMillis() / 1000
        return nowEpoch >= credentials.expiresAtEpochSeconds - 60
    }
}
