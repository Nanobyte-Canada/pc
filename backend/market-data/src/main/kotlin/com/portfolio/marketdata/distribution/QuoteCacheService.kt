package com.portfolio.marketdata.distribution

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.portfolio.common.domain.OptionsChain
import com.portfolio.common.domain.Quote
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class QuoteCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    @Value("\${cache.quote-ttl-seconds:60}") private val quoteTtlSeconds: Long,
    @Value("\${cache.chain-ttl-seconds:300}") private val chainTtlSeconds: Long
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    companion object {
        private const val QUOTE_PREFIX = "quote:"
        private const val CHAIN_PREFIX = "chain:"
    }

    fun cacheQuote(quote: Quote) {
        val key = QUOTE_PREFIX + quote.symbol
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(quote), quoteTtlSeconds, TimeUnit.SECONDS)
    }

    fun getQuote(symbol: String): Quote? {
        val json = redisTemplate.opsForValue().get(QUOTE_PREFIX + symbol) ?: return null
        return try { objectMapper.readValue(json, Quote::class.java) } catch (e: Exception) { null }
    }

    fun cacheChain(underlying: String, chain: OptionsChain) {
        val key = CHAIN_PREFIX + underlying
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(chain), chainTtlSeconds, TimeUnit.SECONDS)
    }

    fun getChain(underlying: String): OptionsChain? {
        val json = redisTemplate.opsForValue().get(CHAIN_PREFIX + underlying) ?: return null
        return try { objectMapper.readValue(json, OptionsChain::class.java) } catch (e: Exception) { null }
    }
}
