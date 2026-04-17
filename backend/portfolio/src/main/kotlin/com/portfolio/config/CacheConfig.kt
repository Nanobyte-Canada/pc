package com.portfolio.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        val cacheMapper = ObjectMapper().apply {
            registerKotlinModule()
            activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfBaseType(Any::class.java)
                    .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
            )
        }

        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(GenericJackson2JsonRedisSerializer(cacheMapper))
            )
            .disableCachingNullValues()

        val cacheConfigurations = mapOf(
            // Reference data — rarely changes, long TTL
            "gics-hierarchy" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "gics-sectors" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "gicsLookup" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "countries" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "exchanges" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "regions" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "regions-simple" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "country-region-map" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "country-name-map" to defaultConfig.entryTtl(Duration.ofHours(24)),
            // Exchange rates — changes daily
            "exchange-rates" to defaultConfig.entryTtl(Duration.ofHours(6)),
            // Look-through — deterministic per position set, moderate TTL
            "look-through" to defaultConfig.entryTtl(Duration.ofMinutes(30)),
            // ETF sector allocations — semi-static
            "etf-sector-allocations" to defaultConfig.entryTtl(Duration.ofHours(12))
        )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}
