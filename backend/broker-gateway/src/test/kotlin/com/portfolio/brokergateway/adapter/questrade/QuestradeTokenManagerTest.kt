package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.BrokerCredentials
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestradeTokenManagerTest {

    private val config = QuestradeConfig(enabled = true)
    private val manager = QuestradeTokenManager(config)

    @Test
    fun `isTokenExpired returns true when token is past expiry`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "token", refreshToken = "refresh",
            apiServerUrl = "https://api05.iq.questrade.com/",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 - 100
        )
        assertTrue(manager.isTokenExpired(creds))
    }

    @Test
    fun `isTokenExpired returns true when within 60s of expiry`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "token", refreshToken = "refresh",
            apiServerUrl = "https://api05.iq.questrade.com/",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 30
        )
        assertTrue(manager.isTokenExpired(creds))
    }

    @Test
    fun `isTokenExpired returns false when token has time remaining`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "token", refreshToken = "refresh",
            apiServerUrl = "https://api05.iq.questrade.com/",
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 600
        )
        assertFalse(manager.isTokenExpired(creds))
    }
}
