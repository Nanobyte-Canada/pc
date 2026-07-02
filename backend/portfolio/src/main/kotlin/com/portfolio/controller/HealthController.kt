package com.portfolio.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "UP",
                timestamp = Instant.now().toString()
            )
        )
    }

    /**
     * Readiness probe — signals the service is ready to accept traffic.
     * Mirrors the `/health` liveness endpoint: returns a static `UP` status
     * with the current timestamp. No dependency probing is performed.
     *
     * @return HTTP 200 with [HealthResponse] containing `status = "UP"` and current timestamp.
     */
    @GetMapping("/ready")
    fun ready(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "UP",
                timestamp = Instant.now().toString()
            )
        )
    }
}

data class HealthResponse(
    val status: String,
    val timestamp: String
)
