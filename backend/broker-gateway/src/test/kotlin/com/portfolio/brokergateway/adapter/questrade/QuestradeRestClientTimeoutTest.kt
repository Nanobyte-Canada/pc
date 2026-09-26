package com.portfolio.brokergateway.adapter.questrade

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class QuestradeRestClientTimeoutTest {

    private lateinit var server: MockWebServer

    @AfterEach
    fun tearDown() {
        if (::server.isInitialized) server.shutdown()
    }

    @Test
    fun `get aborts when the broker never responds within the response timeout`() {
        server = MockWebServer()
        // First request in a JVM pays one-time HTTP-stack init (classloading/JIT),
        // so warm it up outside the timed section — otherwise it distorts the elapsed time.
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
        server.enqueue(MockResponse().setBodyDelay(3, TimeUnit.SECONDS).setBody("{}"))
        server.start()

        // the client builds its own connector from responseTimeoutMs (Step 3);
        // keep the existing get(apiServerUrl, accessToken, path) parameter order
        val client = QuestradeRestClient(responseTimeoutMs = 500)

        runCatching { client.get(server.url("/").toString().trimEnd('/'), "warmup", "/v1/accounts") }

        val started = System.nanoTime()
        val result = runCatching {
            client.get(server.url("/").toString().trimEnd('/'), "token", "/v1/accounts")
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertThat(result.isFailure).isTrue()
        assertThat(elapsedMs).isLessThan(2500) // aborted well before the 3s body
    }
}
