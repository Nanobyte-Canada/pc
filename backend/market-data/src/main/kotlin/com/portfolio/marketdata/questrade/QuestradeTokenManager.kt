package com.portfolio.marketdata.questrade

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class AccessToken(val token: String, val apiServer: String, val expiresAtEpochSeconds: Long)

@Component
class QuestradeTokenManager(
    private val properties: QuestradeProperties,
    private val redisTemplate: RedisTemplate<String, String>,
    restClientBuilder: RestClient.Builder = RestClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilder.build()
    @Volatile private var current: AccessToken? = null

    companion object { const val REDIS_KEY = "questrade:refresh-token" }

    fun getValidAccessToken(): AccessToken {
        current?.let { if (!isExpired(it)) return it }
        return forceRefresh()
    }

    fun forceRefresh(): AccessToken {
        val refreshToken = currentRefreshToken()
        val authUrl = if (properties.usePractice) properties.practiceAuthUrl else properties.authUrl
        val node: JsonNode = restClient.post()
            .uri(authUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("grant_type=refresh_token&refresh_token=$refreshToken")
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw IllegalStateException("Empty Questrade token response")
        val access = node.get("access_token")?.asText()
            ?: throw IllegalStateException("No access_token in Questrade response")
        val refreshed = node.get("refresh_token")?.asText()
            ?: throw IllegalStateException("No refresh_token in Questrade response")
        val apiServer = node.get("api_server")?.asText()
            ?: throw IllegalStateException("No api_server in Questrade response")
        val expiresIn = node.path("expires_in").asLong(1800L).takeIf { it > 0 } ?: 1800L
        persistRotation(refreshed)
        current = AccessToken(access, apiServer, System.currentTimeMillis() / 1000 + expiresIn)
        log.info("Questrade token refreshed (practice={}), expires_in={}s", properties.usePractice, expiresIn)
        return current!!
    }

    fun isExpired(t: AccessToken): Boolean =
        System.currentTimeMillis() / 1000 >= t.expiresAtEpochSeconds - 60

    private fun currentRefreshToken(): String =
        try { redisTemplate.opsForValue().get(REDIS_KEY) } catch (_: Exception) { null }
            ?.takeIf { it.isNotBlank() }
            ?: properties.refreshToken.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No Questrade refresh token configured")

    private fun persistRotation(newToken: String) {
        try { redisTemplate.opsForValue().set(REDIS_KEY, newToken) }
        catch (e: Exception) { log.error("Failed to persist rotated Questrade refresh token", e) }
    }
}
