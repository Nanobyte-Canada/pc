package com.portfolio.common.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object TradingCalendar {

    private val ET_ZONE = ZoneId.of("America/New_York")
    private val MARKET_OPEN_TIME = LocalTime.of(9, 30)
    private val MARKET_CLOSE_TIME = LocalTime.of(16, 0)

    fun daysToExpiry(expiry: LocalDate): Int {
        val today = LocalDate.now()
        return ChronoUnit.DAYS.between(today, expiry).toInt()
    }

    fun daysToExpiry(from: LocalDate, expiry: LocalDate): Int {
        return ChronoUnit.DAYS.between(from, expiry).toInt()
    }

    fun isMarketOpen(): Boolean {
        val now = LocalDateTime.now(ET_ZONE)
        return isMarketOpen(now)
    }

    fun isMarketOpen(dateTime: LocalDateTime): Boolean {
        val date = dateTime.toLocalDate()
        val time = dateTime.toLocalTime()

        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return false
        }

        return time.isAfter(MARKET_OPEN_TIME) && time.isBefore(MARKET_CLOSE_TIME)
    }

    fun nextTradingDay(): LocalDate {
        return nextTradingDay(LocalDate.now())
    }

    fun nextTradingDay(from: LocalDate): LocalDate {
        var next = from.plusDays(1)
        while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
            next = next.plusDays(1)
        }
        return next
    }

    fun previousTradingDay(): LocalDate {
        return previousTradingDay(LocalDate.now())
    }

    fun previousTradingDay(from: LocalDate): LocalDate {
        var prev = from.minusDays(1)
        while (prev.dayOfWeek == DayOfWeek.SATURDAY || prev.dayOfWeek == DayOfWeek.SUNDAY) {
            prev = prev.minusDays(1)
        }
        return prev
    }

    fun isWeekend(date: LocalDate): Boolean {
        return date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }

    fun isWeekday(date: LocalDate): Boolean {
        return !isWeekend(date)
    }

    fun timeToExpiry(expiry: LocalDate): Double {
        val days = daysToExpiry(expiry)
        return days / 365.0
    }

    fun timeToExpiry(from: LocalDate, expiry: LocalDate): Double {
        val days = daysToExpiry(from, expiry)
        return days / 365.0
    }
}
