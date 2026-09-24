package com.portfolio.brokergateway.adapter.questrade

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.brokergateway.adapter.BrokerType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestradeAdapterTest {

    private val config = QuestradeConfig(enabled = true)
    private val adapter = QuestradeAdapter(config)

    @Test
    fun `brokerType is QUESTRADE`() {
        assertEquals(BrokerType.QUESTRADE, adapter.brokerType)
    }

    @Test
    fun `capabilities reports Questrade features`() {
        val caps = adapter.capabilities()
        assertEquals(BrokerType.QUESTRADE, caps.brokerType)
        assertTrue(caps.supportsOrders)
        assertTrue(caps.isOfficialApi)
        assertEquals(false, caps.supportsFractionalShares)
        assertTrue(caps.supportedOrderTypes.size == 4)
    }

    @Test
    fun `activitiesPath renders each boundary with its own ET offset across DST`() {
        val path = adapter.activitiesPath(
            "12345",
            DateWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30))
        )
        assertEquals(
            "/v1/accounts/12345/activities?startTime=2026-03-01T00:00:00-05:00&endTime=2026-03-30T23:59:59-04:00",
            path
        )
    }

    @Test
    fun `fetchActivitiesWindowed merges all windows and sorts by trade date`() {
        val windows = listOf(
            DateWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30)),
            DateWindow(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 29))
        )
        val mapper = ObjectMapper()
        val responses = mapOf(
            windows[0] to mapper.readTree(
                """{"activities":[
                    {"tradeDate":"2026-03-10","type":"Trades","action":"Buy","symbol":"AAA","netAmount":-10,"currency":"CAD"},
                    {"tradeDate":"2026-03-02","type":"Trades","action":"Buy","symbol":"BBB","netAmount":-20,"currency":"CAD"}
                ]}"""
            ),
            windows[1] to mapper.readTree(
                """{"activities":[
                    {"tradeDate":"2026-04-01","type":"Dividends","symbol":"CCC","netAmount":5,"currency":"CAD"}
                ]}"""
            )
        )

        val merged = adapter.fetchActivitiesWindowed(windows) { responses.getValue(it) }

        assertEquals(
            listOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 1)),
            merged.map { it.tradeDate }
        )
    }
}
