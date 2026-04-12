package com.portfolio.ingestion.tracking

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

@Service
class HashCacheService(
    private val redisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ttl = Duration.ofHours(36)

    fun computeHash(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun isUnchanged(key: String, newHash: String): Boolean {
        return try {
            val cached = redisTemplate.opsForValue().get("ingestion:hash:$key")
            cached == newHash
        } catch (e: Exception) {
            log.debug("Redis unavailable for hash check, treating as changed: {}", e.message)
            false
        }
    }

    fun storeHash(key: String, hash: String) {
        try {
            redisTemplate.opsForValue().set("ingestion:hash:$key", hash, ttl)
        } catch (e: Exception) {
            log.debug("Redis unavailable for hash store: {}", e.message)
        }
    }
}
