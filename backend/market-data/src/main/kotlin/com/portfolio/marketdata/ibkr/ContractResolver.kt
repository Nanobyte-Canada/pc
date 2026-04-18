package com.portfolio.marketdata.ibkr

import com.portfolio.marketdata.db.entity.ContractCacheEntity
import com.portfolio.marketdata.db.repository.ContractCacheRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Component
class ContractResolver(
    private val ibkrClient: IbkrClient,
    private val contractCacheRepository: ContractCacheRepository,
    private val redisTemplate: RedisTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(ContractResolver::class.java)
    private val memoryCache = ConcurrentHashMap<String, OptionContractDetails>()

    companion object {
        private const val REDIS_KEY_PREFIX = "contract:"
        private val REDIS_TTL = Duration.ofHours(24)
    }

    fun resolve(
        symbol: String,
        secType: String,
        expiry: LocalDate? = null,
        strike: BigDecimal? = null,
        right: String? = null
    ): OptionContractDetails? {
        val cacheKey = buildCacheKey(symbol, secType, expiry, strike, right)

        getFromRedis(cacheKey)?.let { return it }
        getFromDatabase(symbol, secType, expiry, strike, right)?.let {
            cacheInRedis(cacheKey, it)
            return it
        }
        getFromIbkr(symbol, secType, expiry, strike, right)?.let {
            cacheInDatabase(it)
            cacheInRedis(cacheKey, it)
            return it
        }

        logger.warn("Contract not found: {}", cacheKey)
        return null
    }

    fun resolveMany(symbol: String, secType: String): List<OptionContractDetails> {
        return when (secType) {
            "OPT" -> {
                try {
                    ibkrClient.requestOptionChain(symbol).also { contracts ->
                        contracts.forEach { contract ->
                            val key = buildCacheKey(contract.symbol, contract.secType, contract.expiry, contract.strike, contract.right)
                            cacheInDatabase(contract)
                            cacheInRedis(key, contract)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to resolve option chain for {}", symbol, e)
                    emptyList()
                }
            }
            else -> resolve(symbol, secType)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun getFromRedis(cacheKey: String): OptionContractDetails? {
        return try {
            val json = redisTemplate.opsForValue().get(cacheKey) ?: return null
            parseContractJson(json)
        } catch (e: Exception) {
            logger.debug("Redis lookup failed for {}: {}", cacheKey, e.message)
            memoryCache[cacheKey]
        }
    }

    private fun getFromDatabase(symbol: String, secType: String, expiry: LocalDate?, strike: BigDecimal?, right: String?): OptionContractDetails? {
        return try {
            contractCacheRepository.findBySymbolAndSecTypeAndExpiryAndStrikeAndOptionRight(symbol, secType, expiry, strike, right)?.let { entity ->
                OptionContractDetails(entity.conId, entity.symbol, entity.secType, entity.exchange ?: "SMART", entity.expiry, entity.strike, entity.optionRight)
            }
        } catch (e: Exception) {
            logger.error("Database lookup failed", e)
            null
        }
    }

    private fun getFromIbkr(symbol: String, secType: String, expiry: LocalDate?, strike: BigDecimal?, right: String?): OptionContractDetails? {
        return try {
            ibkrClient.requestContractDetails(symbol, secType, expiry, strike, right).firstOrNull()
        } catch (e: Exception) {
            logger.error("IBKR contract request failed", e)
            null
        }
    }

    private fun cacheInRedis(cacheKey: String, contract: OptionContractDetails) {
        try {
            redisTemplate.opsForValue().set(cacheKey, serializeContract(contract), REDIS_TTL)
            memoryCache[cacheKey] = contract
        } catch (e: Exception) {
            logger.debug("Redis cache write failed for {}: {}", cacheKey, e.message)
            memoryCache[cacheKey] = contract
        }
    }

    private fun cacheInDatabase(contract: OptionContractDetails) {
        try {
            val existing = contractCacheRepository.findBySymbolAndSecTypeAndExpiryAndStrikeAndOptionRight(
                contract.symbol, contract.secType, contract.expiry, contract.strike, contract.right
            )
            if (existing == null) {
                contractCacheRepository.save(ContractCacheEntity(
                    symbol = contract.symbol, conId = contract.conId, secType = contract.secType,
                    exchange = contract.exchange, expiry = contract.expiry, strike = contract.strike,
                    optionRight = contract.right, cachedAt = Instant.now()
                ))
            }
        } catch (e: Exception) {
            logger.error("Database cache write failed", e)
        }
    }

    private fun buildCacheKey(symbol: String, secType: String, expiry: LocalDate?, strike: BigDecimal?, right: String?): String {
        return "$REDIS_KEY_PREFIX$symbol:$secType:${expiry ?: ""}:${strike ?: ""}:${right ?: ""}"
    }

    private fun serializeContract(contract: OptionContractDetails): String {
        return "${contract.conId}|${contract.symbol}|${contract.secType}|${contract.exchange}|${contract.expiry ?: ""}|${contract.strike ?: ""}|${contract.right ?: ""}"
    }

    private fun parseContractJson(json: String): OptionContractDetails? {
        return try {
            val parts = json.split("|")
            if (parts.size < 7) return null
            OptionContractDetails(parts[0].toInt(), parts[1], parts[2], parts[3],
                if (parts[4].isNotEmpty()) LocalDate.parse(parts[4]) else null,
                if (parts[5].isNotEmpty()) BigDecimal(parts[5]) else null,
                if (parts[6].isNotEmpty()) parts[6] else null)
        } catch (e: Exception) {
            logger.error("Failed to parse contract: {}", json, e)
            null
        }
    }
}
