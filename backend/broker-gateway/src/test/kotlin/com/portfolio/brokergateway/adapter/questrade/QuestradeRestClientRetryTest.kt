package com.portfolio.brokergateway.adapter.questrade

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class QuestradeRestClientRetryTest {

    private lateinit var server: MockWebServer

    @AfterEach
    fun tearDown() { if (::server.isInitialized) server.shutdown() }

    @Test
    fun `retries a 429 honoring Retry-After then succeeds`() {
        server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody("{}"))
        // MockWebServer sends no Content-Type by default; without it Jackson can't decode the body
        server.enqueue(MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json").setBody("""{"accounts":[]}"""))
        server.start()
        val client = QuestradeRestClient(
            responseTimeoutMs = 5000,
            retryPolicy = HttpRetryPolicy(maxAttempts = 3, baseDelayMs = 10, capDelayMs = 60_000),
        )

        val result = client.get(server.url("/").toString().trimEnd('/'), "token", "/v1/accounts")

        assertThat(result).isNotNull()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `gives up after max attempts on persistent 502`() {
        server = MockWebServer()
        repeat(3) { server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway")) }
        server.start()
        val client = QuestradeRestClient(
            responseTimeoutMs = 5000,
            retryPolicy = HttpRetryPolicy(maxAttempts = 3, baseDelayMs = 10, capDelayMs = 60_000),
        )

        val result = runCatching { client.get(server.url("/").toString().trimEnd('/'), "token", "/v1/accounts") }

        assertThat(result.isFailure).isTrue()
        assertThat(server.requestCount).isEqualTo(3)
    }
}
