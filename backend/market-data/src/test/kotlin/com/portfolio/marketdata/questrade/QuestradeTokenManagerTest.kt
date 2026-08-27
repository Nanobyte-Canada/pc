package com.portfolio.marketdata.questrade

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestradeTokenManagerTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var ops: ValueOperations<String, String>
    private lateinit var mgr: QuestradeTokenManager

    private val body = "{\"access_token\":\"AT1\",\"token_type\":\"Bearer\",\"expires_in\":1800," +
        "\"refresh_token\":\"RT_NEW\",\"api_server\":\"https://api01.iq.questrade.com/\"}"

    @BeforeEach
    fun setup() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        @Suppress("UNCHECKED_CAST")
        ops = mock(ValueOperations::class.java) as ValueOperations<String, String>
        @Suppress("UNCHECKED_CAST")
        val redis = mock(RedisTemplate::class.java) as RedisTemplate<String, String>
        `when`(redis.opsForValue()).thenReturn(ops)
        `when`(ops.get(QuestradeTokenManager.REDIS_KEY)).thenReturn(null)
        mgr = QuestradeTokenManager(QuestradeProperties(refreshToken = "RT_SEED"), redis, builder)
    }

    @Test
    fun `exchanges seed token and persists rotation`() {
        server.expect(requestTo("https://login.questrade.com/oauth2/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        val t = mgr.forceRefresh()
        assertEquals("AT1", t.token)
        assertEquals("https://api01.iq.questrade.com/", t.apiServer)
        verify(ops).set(QuestradeTokenManager.REDIS_KEY, "RT_NEW")
    }

    @Test
    fun `prefers redis token over env seed`() {
        `when`(ops.get(QuestradeTokenManager.REDIS_KEY)).thenReturn("RT_REDIS")
        server.expect(requestTo("https://login.questrade.com/oauth2/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        mgr.forceRefresh()
        server.verify()
    }

    @Test
    fun `expiry respects 60s buffer`() {
        val t = AccessToken("x", "y", System.currentTimeMillis() / 1000 + 30)
        assertTrue(mgr.isExpired(t))
    }
}
