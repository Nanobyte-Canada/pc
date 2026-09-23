package com.portfolio.brokergateway.adapter.questrade

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuestradeActivityWindowsTest {

    @Test
    fun `single window for a 30-day inclusive span`() {
        val windows = QuestradeActivityWindows.build(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30))
        assertEquals(listOf(DateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30))), windows)
    }

    @Test
    fun `31-day span splits into 30 plus 1`() {
        val windows = QuestradeActivityWindows.build(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        assertEquals(
            listOf(
                DateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30)),
                DateWindow(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31))
            ),
            windows
        )
    }

    @Test
    fun `windows are contiguous, non-overlapping and cover a 75-day span`() {
        val start = LocalDate.of(2026, 7, 1)
        val end = LocalDate.of(2026, 9, 13) // 75 days inclusive
        val windows = QuestradeActivityWindows.build(start, end)
        assertEquals(3, windows.size)
        assertEquals(start, windows.first().start)
        assertEquals(end, windows.last().endInclusive)
        windows.zipWithNext().forEach { (a, b) -> assertEquals(a.endInclusive.plusDays(1), b.start) }
    }

    @Test
    fun `rejects spans requiring more than 400 windows`() {
        assertFailsWith<IllegalArgumentException> {
            QuestradeActivityWindows.build(LocalDate.of(1900, 1, 1), LocalDate.of(2026, 1, 1))
        }
    }

    @Test
    fun `rejects an inverted span`() {
        assertFailsWith<IllegalArgumentException> {
            QuestradeActivityWindows.build(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1))
        }
    }

    @Test
    fun `boundaries use the offset of their own timestamp across DST`() {
        // DST 2026: starts Sun Mar 8, ends Sun Nov 1
        assertEquals("2026-03-01T00:00:00-05:00", QuestradeActivityWindows.formatStart(LocalDate.of(2026, 3, 1)))
        assertEquals("2026-03-30T23:59:59-04:00", QuestradeActivityWindows.formatEnd(LocalDate.of(2026, 3, 30)))
        assertEquals("2026-11-01T00:00:00-04:00", QuestradeActivityWindows.formatStart(LocalDate.of(2026, 11, 1)))
        assertEquals("2026-11-01T23:59:59-05:00", QuestradeActivityWindows.formatEnd(LocalDate.of(2026, 11, 1)))
        assertEquals("2026-11-02T23:59:59-05:00", QuestradeActivityWindows.formatEnd(LocalDate.of(2026, 11, 2)))
    }
}
