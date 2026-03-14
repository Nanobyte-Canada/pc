package com.portfolio.broker.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches foreign exchange rates from the Bank of Canada Valet API (free, no key required).
 *
 * Supports major currencies via BoC series names (e.g. FXUSDCAD for USD→CAD).
 * Includes walk-back logic (up to 5 days) for weekends/holidays and an in-memory cache
 * to avoid redundant calls during bulk syncs.
 */
@Service
class ExchangeRateService(
    webClientBuilder: WebClient.Builder,
    @Value("\${exchange-rate.base-url:https://www.bankofcanada.ca/valet}")
    baseUrl: String = "https://www.bankofcanada.ca/valet"
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient = webClientBuilder
        .baseUrl(baseUrl)
        .build()

    /** Cache keyed by (currency, date) to avoid repeated API calls during bulk syncs. */
    private val cache = ConcurrentHashMap<Pair<String, LocalDate>, BigDecimal?>()

    companion object {
        /** BoC Valet series names for currency→CAD conversion. */
        private val SERIES_MAP = mapOf(
            "USD" to "FXUSDCAD",
            "EUR" to "FXEURCAD",
            "GBP" to "FXGBPCAD",
            "JPY" to "FXJPYCAD",
            "AUD" to "FXAUDCAD",
            "CHF" to "FXCHFCAD",
            "CNY" to "FXCNYCAD",
            "HKD" to "FXHKDCAD",
            "MXN" to "FXMXNCAD",
            "NOK" to "FXNOKCAD",
            "NZD" to "FXNZDCAD",
            "SEK" to "FXSEKCAD",
            "SGD" to "FXSGDCAD",
            "BRL" to "FXBRLCAD",
            "INR" to "FXINRCAD",
            "KRW" to "FXKRWCAD",
            "ZAR" to "FXZARCAD",
            "TRY" to "FXTRYCAD",
            "TWD" to "FXTWDCAD",
            "DKK" to "FXDKKCAD",
            "SAR" to "FXSARCAD",
            "MYR" to "FXMYRCAD",
            "PLN" to "FXPLNCAD",
            "RUB" to "FXRUBCAD",
            "THB" to "FXTHBCAD",
            "PEN" to "FXPENCAD",
            "IDR" to "FXIDRCAD",
            "COP" to "FXCOPCAD"
        )
    }

    /**
     * Returns the exchange rate from [currency] to CAD on the given [date].
     *
     * - Returns [BigDecimal.ONE] for CAD (no API call).
     * - Returns `null` for unsupported currencies or when the API fails for all attempted dates.
     * - Walks back up to 5 days to handle weekends/holidays when BoC doesn't publish rates.
     */
    fun getRate(currency: String, date: LocalDate): BigDecimal? {
        val upper = currency.uppercase()
        if (upper == "CAD") return BigDecimal.ONE

        val series = SERIES_MAP[upper]
        if (series == null) {
            log.warn("No BoC series mapping for currency: {}", upper)
            return null
        }

        return cache.getOrPut(upper to date) {
            fetchRateWithWalkback(series, date)
        }
    }

    private fun fetchRateWithWalkback(series: String, date: LocalDate): BigDecimal? {
        // Try up to 5 previous days to handle weekends/holidays
        for (offset in 0L..4L) {
            val tryDate = date.minusDays(offset)
            val rate = fetchRate(series, tryDate)
            if (rate != null) return rate
        }
        log.warn("Could not find BoC rate for series {} near date {}", series, date)
        return null
    }

    private fun fetchRate(series: String, date: LocalDate): BigDecimal? {
        return try {
            val dateStr = date.toString()
            val response = webClient.get()
                .uri("/observations/{series}/json?start_date={start}&end_date={end}", series, dateStr, dateStr)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            @Suppress("UNCHECKED_CAST")
            val observations = response?.get("observations") as? List<Map<String, Any>> ?: return null
            if (observations.isEmpty()) return null

            val observation = observations.first()
            @Suppress("UNCHECKED_CAST")
            val seriesData = observation[series] as? Map<String, Any> ?: return null
            val value = seriesData["v"]?.toString() ?: return null

            BigDecimal(value)
        } catch (e: Exception) {
            log.debug("BoC API call failed for {} on {}: {}", series, date, e.message)
            null
        }
    }
}
