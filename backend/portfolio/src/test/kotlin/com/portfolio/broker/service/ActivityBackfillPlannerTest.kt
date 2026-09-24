package com.portfolio.broker.service

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class ActivityBackfillPlannerTest {

    private fun row(
        id: Long,
        connectionId: Long = 1L,
        amount: String = "-10.00",
        symbol: String? = "AAPL"
    ) = ActivityRowForBackfill(
        id = id, connectionId = connectionId, type = "BUY", symbol = symbol, description = "Buy",
        quantity = BigDecimal("10.000000"), price = BigDecimal("1.000000"), amount = BigDecimal(amount),
        fee = BigDecimal("1.0000"), currency = "CAD", tradeDate = LocalDate.of(2026, 3, 2),
        settlementDate = LocalDate.of(2026, 3, 4), optionType = null
    )

    @Test
    fun `keeps the earliest id and marks later duplicates for deletion`() {
        val plan = ActivityBackfillPlanner.plan(listOf(row(9), row(5)))
        assertEquals(listOf(9L), plan.duplicateIdsToDelete)
        assertEquals(setOf(5L), plan.fingerprintById.keys)
    }

    @Test
    fun `identical activities in different connections are not duplicates`() {
        val plan = ActivityBackfillPlanner.plan(listOf(row(1, connectionId = 1L), row(2, connectionId = 2L)))
        assertEquals(emptyList<Long>(), plan.duplicateIdsToDelete)
        assertEquals(setOf(1L, 2L), plan.fingerprintById.keys)
    }

    @Test
    fun `different amounts are not duplicates`() {
        val plan = ActivityBackfillPlanner.plan(listOf(row(1, amount = "-10.00"), row(2, amount = "-20.00")))
        assertEquals(emptyList<Long>(), plan.duplicateIdsToDelete)
        assertEquals(setOf(1L, 2L), plan.fingerprintById.keys)
    }
}
