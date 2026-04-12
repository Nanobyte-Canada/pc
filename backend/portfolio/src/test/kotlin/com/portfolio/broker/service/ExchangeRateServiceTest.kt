package com.portfolio.broker.service

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExchangeRateServiceTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var service: ExchangeRateService

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        service = ExchangeRateService(
            webClientBuilder = WebClient.builder(),
            baseUrl = mockServer.url("/").toString()
        )
    }

    @AfterEach
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun `returns ONE for CAD without making HTTP call`() {
        val rate = service.getRate("CAD", LocalDate.of(2024, 6, 15))

        assertEquals(BigDecimal.ONE, rate)
        assertEquals(0, mockServer.requestCount)
    }

    @Test
    fun `returns correct rate for USD on business day`() {
        val json = """
        {
          "observations": [
            {
              "d": "2024-06-14",
              "FXUSDCAD": {"v": "1.3725"}
            }
          ]
        }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))

        val rate = service.getRate("USD", LocalDate.of(2024, 6, 14))

        assertEquals(0, BigDecimal("1.3725").compareTo(rate))
    }

    @Test
    fun `walks back to find rate on weekend`() {
        // Saturday and Sunday return empty observations, Friday returns rate
        val emptyJson = """{"observations": []}"""
        val fridayJson = """
        {
          "observations": [
            {
              "d": "2024-06-14",
              "FXUSDCAD": {"v": "1.3700"}
            }
          ]
        }
        """.trimIndent()

        // Saturday (June 15) -> empty
        mockServer.enqueue(MockResponse().setBody(emptyJson).setHeader("Content-Type", "application/json"))
        // Friday (June 14) -> has rate
        mockServer.enqueue(MockResponse().setBody(fridayJson).setHeader("Content-Type", "application/json"))

        val rate = service.getRate("USD", LocalDate.of(2024, 6, 15))

        assertEquals(0, BigDecimal("1.3700").compareTo(rate))
        assertEquals(2, mockServer.requestCount)
    }

    @Test
    fun `returns null for unsupported currency`() {
        val rate = service.getRate("XYZ", LocalDate.of(2024, 6, 15))

        assertNull(rate)
        assertEquals(0, mockServer.requestCount)
    }

    @Test
    fun `returns null when API fails for all dates`() {
        // Enqueue 5 error responses (one for each walk-back attempt)
        repeat(5) {
            mockServer.enqueue(MockResponse().setResponseCode(500))
        }

        val rate = service.getRate("USD", LocalDate.of(2024, 6, 15))

        assertNull(rate)
    }

    @Test
    fun `caches results for same currency and date`() {
        val json = """
        {
          "observations": [
            {
              "d": "2024-06-14",
              "FXEURCAD": {"v": "1.4800"}
            }
          ]
        }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))

        val date = LocalDate.of(2024, 6, 14)
        val rate1 = service.getRate("EUR", date)
        val rate2 = service.getRate("EUR", date)

        assertEquals(0, BigDecimal("1.4800").compareTo(rate1))
        assertEquals(0, BigDecimal("1.4800").compareTo(rate2))
        // Only one HTTP call thanks to caching
        assertEquals(1, mockServer.requestCount)
    }
}
