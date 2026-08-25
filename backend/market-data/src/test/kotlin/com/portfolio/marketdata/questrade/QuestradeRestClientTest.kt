package com.portfolio.marketdata.questrade

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestradeRestClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: QuestradeRestClient

    // Map-backed Redis simulation so persistRotation() is visible to subsequent reads,
    // matching real Redis semantics used by QuestradeTokenManager.
    private val redisStore = HashMap<String, String>()

    private val tokenBody = "{\"access_token\":\"AT\",\"expires_in\":1800,\"refresh_token\":\"R2\",\"api_server\":\"https://api01.iq.questrade.com/\"}"
    private val tokenBodyRotated = "{\"access_token\":\"AT2\",\"expires_in\":1800,\"refresh_token\":\"R3\",\"api_server\":\"https://api01.iq.questrade.com/\"}"
    private val searchBody = "{\"symbols\":[{\"symbolId\":34987,\"symbol\":\"SPY\",\"description\":\"SPDR S&P 500 ETF\",\"securityType\":\"Stock\",\"listingExchange\":\"ARCA\",\"isQuotable\":true,\"currency\":\"USD\",\"hasOptions\":true}]}"
    private val chainBody = "{\"optionChain\":[{\"expiryDate\":\"2026-08-28T00:00:00.000000-04:00\",\"description\":\"SPY\",\"listingExchange\":\"OPRA\",\"optionExerciseType\":\"American\",\"chainPerRoot\":[{\"optionRoot\":\"SPY\",\"multiplier\":100,\"chainPerStrikePrice\":[{\"strikePrice\":600,\"callSymbolId\":76915281,\"putSymbolId\":76915626}]}]}]}"
    private val optQuotesBody = "{\"optionQuotes\":[{\"underlying\":\"SPY\",\"underlyingId\":34987,\"symbol\":\"SPY28Aug26C600.00\",\"symbolId\":76915281,\"bidPrice\":162.65,\"askPrice\":165.37,\"lastTradePrice\":166.75,\"volume\":0,\"openInterest\":26,\"volatility\":93.08136,\"delta\":0.988226,\"gamma\":0.000366,\"theta\":-0.252878,\"vega\":0.027186,\"rho\":0.080913,\"delay\":0}]}"
    private val stockQuotesBody = "{\"quotes\":[{\"symbol\":\"SPY\",\"symbolId\":34987,\"tier\":\"\",\"bidPrice\":763.56,\"bidSize\":81,\"askPrice\":763.62,\"askSize\":772,\"lastTradePrice\":763.59,\"volume\":34215,\"delay\":0}]}"

    @BeforeEach
    fun setup() {
        // One shared builder: the mock server must intercept BOTH the token manager's
        // auth calls and the rest client's API calls (see Task 5 review lesson).
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        @Suppress("UNCHECKED_CAST")
        val ops = mock(ValueOperations::class.java) as ValueOperations<String, String>
        @Suppress("UNCHECKED_CAST")
        val redis = mock(RedisTemplate::class.java) as RedisTemplate<String, String>
        `when`(redis.opsForValue()).thenReturn(ops)
        redisStore.clear()
        redisStore[QuestradeTokenManager.REDIS_KEY] = "RT"
        `when`(ops.get(eq(QuestradeTokenManager.REDIS_KEY))).thenAnswer { redisStore[QuestradeTokenManager.REDIS_KEY] }
        `when`(ops.set(eq(QuestradeTokenManager.REDIS_KEY), anyString())).thenAnswer {
            redisStore[it.getArgument<String>(0)] = it.getArgument<String>(1)
            null
        }
        val tokenMgr = QuestradeTokenManager(QuestradeProperties(refreshToken = "SEED"), redis, builder)
        client = QuestradeRestClient(tokenMgr, builder)
    }

    private fun expectAuth() {
        server.expect(requestTo("https://login.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=RT"))
            .andRespond(withSuccess(tokenBody, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `search parses symbol`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/symbols/search?prefix=SPY"))
            .andExpect(header("Authorization", "Bearer AT"))
            .andRespond(withSuccess(searchBody, MediaType.APPLICATION_JSON))
        val r = client.searchSymbol("SPY")
        assertEquals(34987, r.first().symbolId)
        assertTrue(r.first().hasOptions)
    }

    @Test
    fun `chain parses optionChain key`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/symbols/34987/options"))
            .andRespond(withSuccess(chainBody, MediaType.APPLICATION_JSON))
        val chain = client.getOptionChain(34987)
        assertEquals(1, chain.size)
        assertEquals("SPY", chain[0].chainPerRoot[0].optionRoot)
        assertEquals(76915281, chain[0].chainPerRoot[0].chainPerStrikePrice[0].callSymbolId)
    }

    @Test
    fun `option quotes parse greeks`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/markets/quotes/options"))
            .andRespond(withSuccess(optQuotesBody, MediaType.APPLICATION_JSON))
        val q = client.getOptionQuotes(listOf(76915281)).first()
        assertEquals(0.988226, q.delta!!, 1e-9)
        assertEquals(26L, q.openInterest)
    }

    @Test
    fun `stock quotes parse`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/markets/quotes?ids=34987"))
            .andRespond(withSuccess(stockQuotesBody, MediaType.APPLICATION_JSON))
        val q = client.getStockQuotes(listOf(34987)).first()
        assertEquals(763.56, q.bidPrice!!, 1e-9)
    }

    @Test
    fun `invalid token retries once after re-exchange`() {
        expectAuth()
        server.expect(requestTo("https://api01.iq.questrade.com/v1/markets/quotes?ids=34987"))
            .andExpect(header("Authorization", "Bearer AT"))
            .andRespond(withSuccess("{\"code\":1017,\"message\":\"Access token is invalid\"}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://login.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=R2"))
            .andRespond(withSuccess(tokenBodyRotated, MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api01.iq.questrade.com/v1/markets/quotes?ids=34987"))
            .andExpect(header("Authorization", "Bearer AT2"))
            .andRespond(withSuccess(stockQuotesBody, MediaType.APPLICATION_JSON))
        val q = client.getStockQuotes(listOf(34987)).first()
        assertEquals(34987, q.symbolId)
        server.verify()
    }
}
