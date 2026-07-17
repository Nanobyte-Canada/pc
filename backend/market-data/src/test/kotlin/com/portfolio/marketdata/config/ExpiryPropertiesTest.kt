package com.portfolio.marketdata.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpiryPropertiesTest {

    @Test
    fun `default values are correct`() {
        val props = ExpiryProperties()
        assertEquals("0 0 8 ? * MON", props.refresh.cron)
        assertEquals(90, props.cache.ttlDays)
        assertEquals(
            listOf("SOXL", "TECL", "TQQQ", "SPXU", "SPY", "QQQ", "XLF", "NVDA", "AVGO"),
            props.refresh.symbols
        )
    }

    @Test
    fun `custom values are applied`() {
        val props = ExpiryProperties(
            refresh = ExpiryProperties.Refresh(
                cron = "0 0 12 ? * WED",
                symbols = listOf("AAPL", "MSFT")
            ),
            cache = ExpiryProperties.Cache(ttlDays = 30)
        )
        assertEquals("0 0 12 ? * WED", props.refresh.cron)
        assertEquals(listOf("AAPL", "MSFT"), props.refresh.symbols)
        assertEquals(30, props.cache.ttlDays)
    }
}
