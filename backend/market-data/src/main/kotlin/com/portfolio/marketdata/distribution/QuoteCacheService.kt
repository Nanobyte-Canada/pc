package com.portfolio.marketdata.distribution

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.portfolio.common.domain.OptionsChain
import com.portfolio.common.domain.Quote
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Service
class QuoteCacheService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    companion object {
        private const val QUOTE_PREFIX = "quote:"
        private const val CHAIN_PREFIX = "chain:"
        private const val EXPIRATION_PREFIX = "expirations:"
        private const val QUOTE_TTL_SECONDS = 5L
        private const val CHAIN_TTL_SECONDS = 30L
        private const val EXPIRATION_TTL_HOURS = 24L
    }

    fun cacheQuote(quote: Quote) {
        val key = QUOTE_PREFIX + quote.symbol
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(quote), QUOTE_TTL_SECONDS, TimeUnit.SECONDS)
    }

    fun getQuote(symbol: String): Quote? {
        val json = redisTemplate.opsForValue().get(QUOTE_PREFIX + symbol) ?: return null
        return try { objectMapper.readValue(json, Quote::class.java) } catch (e: Exception) { null }
    }

    fun cacheChain(underlying: String, chain: OptionsChain) {
        val key = CHAIN_PREFIX + underlying
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(chain), CHAIN_TTL_SECONDS, TimeUnit.SECONDS)
    }

    fun getChain(underlying: String): OptionsChain? {
        val json = redisTemplate.opsForValue().get(CHAIN_PREFIX + underlying) ?: return null
        return try { objectMapper.readValue(json, OptionsChain::class.java) } catch (e: Exception) { null }
    }

    fun cacheExpirations(symbol: String, expirations: List<LocalDate>) {
        val key = EXPIRATION_PREFIX + symbol
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(expirations), EXPIRATION_TTL_HOURS, TimeUnit.HOURS)
    }

    fun getExpirations(symbol: String): List<LocalDate>? {
        val json = redisTemplate.opsForValue().get(EXPIRATION_PREFIX + symbol) ?: return null
        return try {
            objectMapper.readValue(json, object : TypeReference<List<LocalDate>>() {})
        } catch (_: Exception) { null }
    }
}
