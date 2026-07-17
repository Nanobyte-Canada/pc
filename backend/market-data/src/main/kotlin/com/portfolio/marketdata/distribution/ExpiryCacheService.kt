package com.portfolio.marketdata.distribution

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.marketdata.config.ExpiryProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Service
class ExpiryCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val properties: ExpiryProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val KEY_PREFIX = "expiry:"
    }

    fun cacheExpiry(symbol: String, expirations: List<LocalDate>) {
        val key = "$KEY_PREFIX$symbol"
        val json = objectMapper.writeValueAsString(expirations)
        val ttlDays = properties.cache.ttlDays
        redisTemplate.opsForValue().set(key, json, ttlDays, TimeUnit.DAYS)
        log.debug("Cached {} expirations for {} with TTL {} days", expirations.size, symbol, ttlDays)
    }

    fun getExpiry(symbol: String): List<LocalDate>? {
        val key = "$KEY_PREFIX$symbol"
        val json = redisTemplate.opsForValue().get(key) ?: return null
        return try {
            objectMapper.readValue(json, objectMapper.typeFactory.constructCollectionType(List::class.java, LocalDate::class.java))
        } catch (e: Exception) {
            log.warn("Failed to deserialize expirations for {}: {}", symbol, e.message)
            null
        }
    }
}
