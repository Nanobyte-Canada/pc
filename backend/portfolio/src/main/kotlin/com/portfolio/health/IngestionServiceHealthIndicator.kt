package com.portfolio.health

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class IngestionServiceHealthIndicator(
    @Value("\${ingestion-service.health-url:http://localhost:8081/actuator/health}")
    private val healthUrl: String
) : HealthIndicator {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = WebClient.builder().build()

    override fun health(): Health {
        return try {
            val response = client.get()
                .uri(healthUrl)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(5))

            if (response?.statusCode?.is2xxSuccessful == true) {
                Health.up().withDetail("url", healthUrl).build()
            } else {
                Health.down().withDetail("url", healthUrl).withDetail("status", response?.statusCode?.value()).build()
            }
        } catch (e: Exception) {
            log.debug("Ingestion service health check failed: {}", e.message)
            Health.unknown().withDetail("url", healthUrl).withDetail("reason", "Service unreachable").build()
        }
    }
}
