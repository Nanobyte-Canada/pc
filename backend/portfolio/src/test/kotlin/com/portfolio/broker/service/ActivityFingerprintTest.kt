package com.portfolio.broker.service

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ActivityFingerprintTest {

    @Test
    fun `golden vector - fully populated trade`() {
        val fingerprint = ActivityFingerprint.of(
            type = "BUY", symbol = "AAPL", description = "Buy 10 AAPL",
            quantity = BigDecimal("10.000000"), price = BigDecimal("150.500000"),
            amount = BigDecimal("-1505.00"), fee = BigDecimal("1.0000"),
            currency = "USD", tradeDate = LocalDate.of(2026, 3, 2),
            settlementDate = LocalDate.of(2026, 3, 4), optionType = null
        )
        assertEquals("e23ae395c505668a37360d33d08196a2ff38fed00be4d76e3e2330bf0d6e4da6", fingerprint)
    }

    @Test
    fun `golden vector - null fields collapse to empty strings`() {
        val fingerprint = ActivityFingerprint.of(
            type = "DIVIDEND", symbol = null, description = null,
            quantity = null, price = null, amount = BigDecimal("12.34"), fee = null,
            currency = "CAD", tradeDate = LocalDate.of(2026, 1, 5),
            settlementDate = null, optionType = null
        )
        assertEquals("3abff73fcb7ff9f6b1abd724af73610663fb9b9b9f702439a3e6ebc5b2b0d154", fingerprint)
    }

    @Test
    fun `decimal scale does not change the fingerprint`() {
        val a = ActivityFingerprint.of(
            "BUY", "AAPL", "Buy 10 AAPL", BigDecimal("10"), BigDecimal("150.5"),
            BigDecimal("-1505"), BigDecimal("1"), "USD", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4), null
        )
        val b = ActivityFingerprint.of(
            "BUY", "AAPL", "Buy 10 AAPL", BigDecimal("10.000000"), BigDecimal("150.500000"),
            BigDecimal("-1505.00"), BigDecimal("1.0000"), "USD", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4), null
        )
        assertEquals(a, b)
        assertEquals("e23ae395c505668a37360d33d08196a2ff38fed00be4d76e3e2330bf0d6e4da6", a)
    }

    @Test
    fun `different amount produces a different fingerprint`() {
        val base = ActivityFingerprint.of(
            "BUY", "AAPL", null, null, null, BigDecimal("-10"), null, "CAD",
            LocalDate.of(2026, 3, 2), null, null
        )
        val other = ActivityFingerprint.of(
            "BUY", "AAPL", null, null, null, BigDecimal("-20"), null, "CAD",
            LocalDate.of(2026, 3, 2), null, null
        )
        assertNotEquals(base, other)
    }
}
