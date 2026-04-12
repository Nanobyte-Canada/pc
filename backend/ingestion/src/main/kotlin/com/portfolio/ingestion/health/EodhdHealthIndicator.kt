package com.portfolio.ingestion.health

import com.portfolio.ingestion.config.IngestionProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class EodhdHealthIndicator(
    private val props: IngestionProperties
) : HealthIndicator {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = WebClient.builder().baseUrl(props.eodhd.baseUrl).build()

    override fun health(): Health {
        return try {
            val response = client.get()
                .uri("/exchanges-list/?api_token=${props.eodhd.apiKey}&fmt=json")
                .retrieve()
                .bodyToMono(String::class.java)
                .block(Duration.ofSeconds(5))

            if (!response.isNullOrBlank() && response.startsWith("[")) {
                Health.up().withDetail("status", "reachable").build()
            } else {
                Health.down().withDetail("response", "unexpected format").build()
            }
        } catch (e: Exception) {
            log.warn("EODHD health check failed: {}", e.message)
            Health.down().withDetail("error", e.message ?: "Connection failed").build()
        }
    }
}
