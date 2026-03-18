package com.portfolio.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        return ConcurrentMapCacheManager(
            "gics-hierarchy",
            "gics-sectors",
            "gicsLookup",
            "countries",
            "exchanges",
            "regions",
            "regions-simple",
            "country-region-map",
            "country-name-map"
        )
    }
}
