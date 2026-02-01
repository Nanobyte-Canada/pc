package com.portfolio.broker.security

import com.portfolio.broker.config.BrokerConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TokenEncryptionServiceTest {

    private lateinit var service: TokenEncryptionService
    private lateinit var config: BrokerConfig

    @BeforeEach
    fun setup() {
        config = mockk<BrokerConfig>()
        // AES-256 requires a 32-byte (256-bit) key, base64 encoded = 44 chars
        val testKey = "dGhpcyBpcyBhIDMyIGJ5dGUgdGVzdCBrZXkxMjM0NTY="
        every { config.encryptionKey } returns testKey

        service = TokenEncryptionService(config)
    }

    @Test
    fun `encrypt returns different value than plaintext`() {
        val plaintext = "secret-access-token-12345"

        val encrypted = service.encrypt(plaintext)

        assertNotEquals(plaintext, encrypted)
        assertTrue(encrypted.isNotBlank())
    }

    @Test
    fun `decrypt returns original plaintext`() {
        val plaintext = "secret-access-token-12345"

        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt produces different ciphertext for same plaintext due to random IV`() {
        val plaintext = "same-token-value"

        val encrypted1 = service.encrypt(plaintext)
        val encrypted2 = service.encrypt(plaintext)

        // Due to random IV, encrypting the same value twice should produce different ciphertexts
        assertNotEquals(encrypted1, encrypted2)

        // But both should decrypt to the same value
        assertEquals(plaintext, service.decrypt(encrypted1))
        assertEquals(plaintext, service.decrypt(encrypted2))
    }

    @Test
    fun `encrypt and decrypt handles empty string`() {
        val plaintext = ""

        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt handles special characters`() {
        val plaintext = "token=abc123!@#$%^&*()_+-={}[]|\\:\";<>?,./"

        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt handles unicode characters`() {
        val plaintext = "token-日本語-한국어-中文"

        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt handles long tokens`() {
        val plaintext = "x".repeat(10000)

        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }
}
