package com.portfolio.ingestion.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

/**
 * Redis-backed hash cache for change detection in the ingestion pipeline.
 *
 * Stores SHA-256 hashes of API response payloads. If the hash of a new payload
 * matches the stored hash, the data hasn't changed and enrichment can be skipped.
 *
 * Key pattern: ingestion:{entityType}:{key}:hash
 * TTL: 36 hours (buffer beyond the 24h nightly run cycle)
 *
 * Fallback: If Redis is unavailable, logs a warning and returns true (treat as changed).
 * This ensures enrichment always runs on Redis failure — occasional unnecessary work
 * is safer than silent skips.
 */
@Service
class IngestionHashCacheService(private val redis: StringRedisTemplate) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val TTL = Duration.ofHours(36)

    fun computeHash(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns true if the payload has changed since the last run (or if Redis is unavailable).
     * Also stores the new hash in Redis when a change is detected.
     *
     * @param entityType e.g. "stock" or "etf"
     * @param key        e.g. ticker symbol or ETF symbol
     * @param newPayload the raw JSON string to hash
     */
    fun isChanged(entityType: String, key: String, newPayload: String): Boolean {
        val newHash = computeHash(newPayload)
        return try {
            val redisKey = "ingestion:$entityType:$key:hash"
            val cached = redis.opsForValue().get(redisKey)
            if (cached == newHash) {
                log.debug("Hash unchanged for {}:{} — skipping enrichment", entityType, key)
                false
            } else {
                redis.opsForValue().set(redisKey, newHash, TTL)
                true
            }
        } catch (e: Exception) {
            log.warn("Redis unavailable for hash check ({}:{}); treating as changed: {}", entityType, key, e.message)
            true
        }
    }
}
