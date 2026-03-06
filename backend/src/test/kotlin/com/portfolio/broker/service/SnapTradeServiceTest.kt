package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import com.portfolio.broker.config.SnapTradeConfig
import com.portfolio.broker.security.TokenEncryptionService
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SnapTradeServiceTest {

    private lateinit var service: SnapTradeService
    private lateinit var config: SnapTradeConfig
    private lateinit var userRepository: UserRepository
    private lateinit var encryptionService: TokenEncryptionService

    @BeforeEach
    fun setup() {
        config = SnapTradeConfig(
            clientId = "test-client-id",
            consumerKey = "test-consumer-key",
            redirectUri = "http://localhost:3000/brokers/connections"
        )
        userRepository = mockk()
        encryptionService = mockk()

        service = SnapTradeService(config, userRepository, encryptionService)
    }

    @Test
    fun `ensureUserRegistered returns existing info when user already registered`() {
        val user = User(
            id = 1L,
            email = "test@example.com",
            snaptradeUserId = "1",
            snaptradeUserSecretEncrypted = "encrypted-secret"
        )

        every { encryptionService.decrypt("encrypted-secret") } returns "user-secret-123"

        val result = service.ensureUserRegistered(user)

        assertEquals("1", result.userId)
        assertEquals("user-secret-123", result.userSecret)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `config has expected values`() {
        assertEquals("test-client-id", config.clientId)
        assertEquals("test-consumer-key", config.consumerKey)
        assertEquals("http://localhost:3000/brokers/connections", config.redirectUri)
    }

    @Test
    fun `SnapTradeUserInfo data class works correctly`() {
        val info = SnapTradeService.SnapTradeUserInfo(
            userId = "user-123",
            userSecret = "secret-456"
        )

        assertEquals("user-123", info.userId)
        assertEquals("secret-456", info.userSecret)
    }
}
