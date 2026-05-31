package com.portfolio.brokergateway.adapter.questrade

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient

class QuestradeTokenManager(
    private val config: QuestradeConfig,
    private val webClientBuilder: WebClient.Builder = WebClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun refreshTokens(credentials: BrokerCredentials.QuestradeCredentials): BrokerCredentials.QuestradeCredentials {
        val authUrl = if (config.usePractice) config.practiceAuthUrl else config.authUrl
        val url = "$authUrl?grant_type=refresh_token&refresh_token=${credentials.refreshToken}"

        log.info("Refreshing Questrade tokens via {}", if (config.usePractice) "practice" else "production")

        val response: JsonNode = try {
            webClientBuilder.build()
                .get().uri(url)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block() ?: throw BrokerAuthenticationException(
                    "Empty response from Questrade token exchange", BrokerType.QUESTRADE)
        } catch (e: BrokerAuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw BrokerAuthenticationException(
                "Questrade token refresh failed: ${e.message}", BrokerType.QUESTRADE, cause = e)
        }

        val accessToken = response.get("access_token")?.asText()
            ?: throw BrokerAuthenticationException("No access_token in Questrade response", BrokerType.QUESTRADE)
        val newRefreshToken = response.get("refresh_token")?.asText()
            ?: throw BrokerAuthenticationException("No refresh_token in Questrade response", BrokerType.QUESTRADE)
        val apiServer = response.get("api_server")?.asText()
            ?: throw BrokerAuthenticationException("No api_server in Questrade response", BrokerType.QUESTRADE)
        val expiresInNode = response.get("expires_in")
        val expiresIn = when {
            expiresInNode == null || expiresInNode.isNull -> 1800L
            expiresInNode.isNumber -> expiresInNode.asLong()
            expiresInNode.isTextual -> expiresInNode.asText().toLongOrNull() ?: 1800L
            else -> 1800L
        }
        val safeExpiresIn = if (expiresIn <= 0) 1800L else expiresIn
        val expiresAtEpoch = System.currentTimeMillis() / 1000 + safeExpiresIn

        log.info("Questrade tokens refreshed, api_server={}, expires_in={}s, expiresAt={}",
            apiServer, safeExpiresIn, expiresAtEpoch)

        return BrokerCredentials.QuestradeCredentials(
            refreshToken = newRefreshToken,
            accessToken = accessToken,
            apiServerUrl = apiServer,
            expiresAtEpochSeconds = expiresAtEpoch,
            usePractice = credentials.usePractice
        )
    }

    fun isTokenExpired(credentials: BrokerCredentials.QuestradeCredentials): Boolean {
        val nowEpoch = System.currentTimeMillis() / 1000
        return nowEpoch >= credentials.expiresAtEpochSeconds - 60
    }
}
