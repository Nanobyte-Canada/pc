package com.portfolio.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class VersionController(
    @Value("\${app.version:0.0.1-SNAPSHOT}") private val version: String,
    @Value("\${app.environment:local}") private val environment: String
) {

    @GetMapping("/version")
    fun version(): ResponseEntity<VersionResponse> {
        return ResponseEntity.ok(
            VersionResponse(
                version = version,
                environment = environment
            )
        )
    }
}

data class VersionResponse(
    val version: String,
    val environment: String
)
