package com.portfolio.brokergateway.adapter.questrade

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

data class DateWindow(val start: LocalDate, val endInclusive: LocalDate)

/**
 * Questrade's activities endpoint rejects ranges longer than 31 days (error 1003).
 * Plans contiguous, non-overlapping 30-day inclusive windows aligned to Eastern Time —
 * 30 (not 31) absorbs timezone/boundary slop. Offsets are computed per boundary so DST
 * is handled correctly.
 */
object QuestradeActivityWindows {

    const val MAX_WINDOW_DAYS = 30
    const val MAX_WINDOWS = 400
    val ZONE: ZoneId = ZoneId.of("America/Toronto")

    fun build(start: LocalDate, endInclusive: LocalDate, maxWindowDays: Int = MAX_WINDOW_DAYS): List<DateWindow> {
        require(maxWindowDays >= 1) { "maxWindowDays must be >= 1" }
        require(!start.isAfter(endInclusive)) { "start $start must not be after end $endInclusive" }
        val totalDays = ChronoUnit.DAYS.between(start, endInclusive) + 1
        val windowCount = ((totalDays + maxWindowDays - 1) / maxWindowDays).toInt()
        require(windowCount <= MAX_WINDOWS) {
            "Requested range $start..$endInclusive requires $windowCount windows, exceeding the $MAX_WINDOWS-window cap"
        }
        val windows = mutableListOf<DateWindow>()
        var windowStart = start
        while (!windowStart.isAfter(endInclusive)) {
            val windowEnd = minOf(windowStart.plusDays((maxWindowDays - 1).toLong()), endInclusive)
            windows += DateWindow(windowStart, windowEnd)
            windowStart = windowEnd.plusDays(1)
        }
        return windows
    }

    fun formatStart(date: LocalDate): String = format(date, LocalTime.MIDNIGHT)

    fun formatEnd(date: LocalDate): String = format(date, LocalTime.of(23, 59, 59))

    private fun format(date: LocalDate, time: LocalTime): String =
        OffsetDateTime.of(date, time, ZONE.rules.getOffset(date.atTime(time)))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
