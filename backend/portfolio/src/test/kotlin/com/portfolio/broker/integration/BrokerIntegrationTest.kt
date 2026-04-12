package com.portfolio.broker.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BrokerIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("snaptrade.client-id") { "test-client-id" }
            registry.add("snaptrade.consumer-key") { "test-consumer-key" }
            registry.add("snaptrade.redirect-uri") { "http://localhost:3000/brokers/connections" }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `GET brokers requires authentication`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/brokers",
            Map::class.java
        )

        assertTrue(
            response.statusCode == HttpStatus.UNAUTHORIZED || response.statusCode == HttpStatus.FORBIDDEN,
            "Brokers endpoint should require authentication"
        )
    }

    @Test
    fun `GET brokers connections requires authentication`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/brokers/connections",
            Map::class.java
        )

        assertTrue(
            response.statusCode == HttpStatus.UNAUTHORIZED || response.statusCode == HttpStatus.FORBIDDEN,
            "Connections endpoint should require authentication"
        )
    }

    @Test
    fun `GET brokers positions requires authentication`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/brokers/positions",
            Map::class.java
        )

        assertTrue(
            response.statusCode == HttpStatus.UNAUTHORIZED || response.statusCode == HttpStatus.FORBIDDEN,
            "Positions endpoint should require authentication"
        )
    }

    @Test
    fun `POST connect requires authentication`() {
        val response = restTemplate.postForEntity(
            "http://localhost:$port/api/v1/brokers/connect",
            null,
            Map::class.java
        )

        assertTrue(
            response.statusCode == HttpStatus.UNAUTHORIZED || response.statusCode == HttpStatus.FORBIDDEN,
            "Connect endpoint should require authentication"
        )
    }
}
