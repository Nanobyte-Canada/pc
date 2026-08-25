package com.portfolio.marketdata.questrade

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamFrameParserTest {
    @Test
    fun `parses stock quote frame and synthesizes ticks`() {
        val frame = "{\"quotes\":[{\"symbol\":\"SPY\",\"symbolId\":34987,\"bidPrice\":763.52,\"askPrice\":763.6,\"lastTradePrice\":763.51,\"volume\":34389}]}"
        val (stocks, options) = parseQuoteFrame(frame)
        assertEquals(1, stocks.size); assertTrue(options.isEmpty())
        assertEquals(listOf(1 to 763.52, 2 to 763.6, 4 to 763.51, 8 to 34389.0), synthesizeTicks(stocks[0].second))
    }

    @Test
    fun `parses option quote frame`() {
        val frame = "{\"optionQuotes\":[{\"underlying\":\"SPY\",\"underlyingId\":34987,\"symbolId\":76915281,\"bidPrice\":162.65,\"askPrice\":165.37,\"lastTradePrice\":166.75,\"volume\":0}]}"
        val (stocks, options) = parseQuoteFrame(frame)
        assertTrue(stocks.isEmpty()); assertEquals(76915281, options[0].first)
        assertEquals(listOf(1 to 162.65, 2 to 165.37, 4 to 166.75), synthesizeTicks(options[0].second))
    }

    @Test
    fun `ignores success frame`() {
        val (stocks, options) = parseQuoteFrame("{\"success\":true}")
        assertTrue(stocks.isEmpty() && options.isEmpty())
    }
}
