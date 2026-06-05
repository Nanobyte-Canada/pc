package com.portfolio.common.util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TradingCalendarTest {

    @Test
    fun `daysToExpiry calculates correct number of days`() {
        val from = LocalDate.of(2024, 1, 1)
        val expiry = LocalDate.of(2024, 1, 31)
        assertThat(TradingCalendar.daysToExpiry(from, expiry)).isEqualTo(30)
    }

    @Test
    fun `daysToExpiry returns zero for same day`() {
        val date = LocalDate.of(2024, 1, 1)
        assertThat(TradingCalendar.daysToExpiry(date, date)).isEqualTo(0)
    }

    @Test
    fun `daysToExpiry returns negative for past dates`() {
        val from = LocalDate.of(2024, 1, 31)
        val expiry = LocalDate.of(2024, 1, 1)
        assertThat(TradingCalendar.daysToExpiry(from, expiry)).isEqualTo(-30)
    }

    @Test
    fun `isMarketOpen returns false for weekend - Saturday`() {
        val saturday = LocalDateTime.of(LocalDate.of(2024, 1, 6), LocalTime.of(10, 0))
        assertThat(TradingCalendar.isMarketOpen(saturday)).isFalse()
    }

    @Test
    fun `isMarketOpen returns false for weekend - Sunday`() {
        val sunday = LocalDateTime.of(LocalDate.of(2024, 1, 7), LocalTime.of(10, 0))
        assertThat(TradingCalendar.isMarketOpen(sunday)).isFalse()
    }

    @Test
    fun `isMarketOpen returns true during trading hours`() {
        val mondayMorning = LocalDateTime.of(LocalDate.of(2024, 1, 8), LocalTime.of(10, 0))
        assertThat(TradingCalendar.isMarketOpen(mondayMorning)).isTrue()
    }

    @Test
    fun `isMarketOpen returns false before market open`() {
        val mondayEarly = LocalDateTime.of(LocalDate.of(2024, 1, 8), LocalTime.of(9, 0))
        assertThat(TradingCalendar.isMarketOpen(mondayEarly)).isFalse()
    }

    @Test
    fun `isMarketOpen returns false after market close`() {
        val mondayEvening = LocalDateTime.of(LocalDate.of(2024, 1, 8), LocalTime.of(17, 0))
        assertThat(TradingCalendar.isMarketOpen(mondayEvening)).isFalse()
    }

    @Test
    fun `nextTradingDay skips Saturday and Sunday`() {
        val friday = LocalDate.of(2024, 1, 5)
        val nextDay = TradingCalendar.nextTradingDay(friday)
        assertThat(nextDay).isEqualTo(LocalDate.of(2024, 1, 8))
        assertThat(nextDay.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `nextTradingDay returns next day for weekday`() {
        val monday = LocalDate.of(2024, 1, 8)
        val nextDay = TradingCalendar.nextTradingDay(monday)
        assertThat(nextDay).isEqualTo(LocalDate.of(2024, 1, 9))
        assertThat(nextDay.dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
    }

    @Test
    fun `previousTradingDay skips Saturday and Sunday`() {
        val monday = LocalDate.of(2024, 1, 8)
        val prevDay = TradingCalendar.previousTradingDay(monday)
        assertThat(prevDay).isEqualTo(LocalDate.of(2024, 1, 5))
        assertThat(prevDay.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)
    }

    @Test
    fun `previousTradingDay returns previous day for weekday`() {
        val tuesday = LocalDate.of(2024, 1, 9)
        val prevDay = TradingCalendar.previousTradingDay(tuesday)
        assertThat(prevDay).isEqualTo(LocalDate.of(2024, 1, 8))
    }

    @Test
    fun `isWeekend returns true for Saturday and Sunday`() {
        assertThat(TradingCalendar.isWeekend(LocalDate.of(2024, 1, 6))).isTrue()
        assertThat(TradingCalendar.isWeekend(LocalDate.of(2024, 1, 7))).isTrue()
    }

    @Test
    fun `isWeekend returns false for weekdays`() {
        assertThat(TradingCalendar.isWeekend(LocalDate.of(2024, 1, 8))).isFalse()
    }

    @Test
    fun `isWeekday returns true for Monday through Friday`() {
        assertThat(TradingCalendar.isWeekday(LocalDate.of(2024, 1, 8))).isTrue()
        assertThat(TradingCalendar.isWeekday(LocalDate.of(2024, 1, 12))).isTrue()
    }

    @Test
    fun `isWeekday returns false for Saturday and Sunday`() {
        assertThat(TradingCalendar.isWeekday(LocalDate.of(2024, 1, 6))).isFalse()
        assertThat(TradingCalendar.isWeekday(LocalDate.of(2024, 1, 7))).isFalse()
    }

    @Test
    fun `timeToExpiry calculates correct fraction of year`() {
        val from = LocalDate.of(2024, 1, 1)
        val expiry = LocalDate.of(2024, 12, 31)
        val tte = TradingCalendar.timeToExpiry(from, expiry)
        assertThat(tte).isCloseTo(1.0, Offset.offset(0.01))
    }

    @Test
    fun `timeToExpiry for 30 days is approximately 0_082 years`() {
        val from = LocalDate.of(2024, 1, 1)
        val expiry = LocalDate.of(2024, 1, 31)
        val tte = TradingCalendar.timeToExpiry(from, expiry)
        assertThat(tte).isCloseTo(30.0 / 365.0, Offset.offset(0.001))
    }

    @Test
    fun `timeToExpiry for same day is zero`() {
        val date = LocalDate.of(2024, 1, 1)
        assertThat(TradingCalendar.timeToExpiry(date, date)).isEqualTo(0.0)
    }
}
