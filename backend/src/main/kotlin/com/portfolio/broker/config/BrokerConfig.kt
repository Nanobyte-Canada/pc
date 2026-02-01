package com.portfolio.broker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@ConfigurationProperties(prefix = "broker")
data class BrokerConfig(
    val enabled: Boolean = true,
    val encryption: EncryptionConfig = EncryptionConfig(),
    val questrade: QuestradeConfig = QuestradeConfig(),
    val ibkr: IbkrConfig = IbkrConfig(),
    val snaptrade: SnapTradeConfig = SnapTradeConfig(),
    val scheduler: SchedulerConfig = SchedulerConfig()
)

data class EncryptionConfig(
    val secretKey: String = ""
)

data class QuestradeConfig(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val authorizationUrl: String = "https://login.questrade.com/oauth2/authorize",
    val tokenUrl: String = "https://login.questrade.com/oauth2/token",
    val scopes: List<String> = listOf("read_account"),
    val rateLimit: RateLimitConfig = RateLimitConfig(requestsPerSecond = 1.0, burstSize = 5)
)

data class IbkrConfig(
    val clientId: String = "",
    val privateKeyPath: String = "",
    val redirectUri: String = "",
    val authorizationUrl: String = "https://www.interactivebrokers.com/oauth2/authorize",
    val tokenUrl: String = "https://www.interactivebrokers.com/oauth2/token",
    val rateLimit: RateLimitConfig = RateLimitConfig(requestsPerSecond = 2.0, burstSize = 10)
)

data class SnapTradeConfig(
    val clientId: String = "",
    val consumerKey: String = "",
    val redirectUri: String = "",
    val baseUrl: String = "https://api.snaptrade.com",
    val rateLimit: RateLimitConfig = RateLimitConfig(requestsPerSecond = 1.0, burstSize = 3)
)

data class SchedulerConfig(
    val enabled: Boolean = true,
    val defaultFetchHour: Int = 6
)

data class RateLimitConfig(
    val requestsPerSecond: Double = 1.0,
    val burstSize: Int = 5
)

@Configuration
class BrokerWebClientConfig {

    @Bean
    fun questradeWebClient(): WebClient {
        return WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
            }
            .build()
    }
}
