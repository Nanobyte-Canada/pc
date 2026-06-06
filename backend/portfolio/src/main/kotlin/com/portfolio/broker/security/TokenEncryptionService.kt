package com.portfolio.broker.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Service for encrypting and decrypting broker OAuth tokens.
 * Uses AES-256-GCM encryption for authenticated encryption.
 *
 * For local development, uses a configured secret key.
 * For production, uses HashiCorp Vault for secret key management.
 */
@Service
class TokenEncryptionService(
    @Value("\${broker.encryption.secret-key:}")
    private val secretKeyBase64: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val GCM_IV_LENGTH = 12 // 96 bits
        private const val GCM_TAG_LENGTH = 128 // 128 bits
        private const val KEY_LENGTH = 32 // 256 bits
    }

    private val secretKey: SecretKey by lazy {
        if (secretKeyBase64.isBlank()) {
            log.warn("No encryption key configured, generating ephemeral key. Configure broker.encryption.secret-key for persistence.")
            generateKey()
        } else {
            try {
                val keyBytes = Base64.getDecoder().decode(secretKeyBase64)
                require(keyBytes.size == KEY_LENGTH) {
                    "Encryption key must be $KEY_LENGTH bytes (${KEY_LENGTH * 8} bits)"
                }
                SecretKeySpec(keyBytes, KEY_ALGORITHM)
            } catch (e: Exception) {
                log.error("Failed to decode encryption key: ${e.message}")
                throw IllegalStateException("Invalid encryption key configuration", e)
            }
        }
    }

    /**
     * Encrypts a plaintext token.
     * Returns Base64-encoded ciphertext with prepended IV.
     */
    fun encrypt(plaintext: String): String {
        require(plaintext.isNotBlank()) { "Cannot encrypt empty token" }

        try {
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Prepend IV to ciphertext
            val combined = ByteBuffer.allocate(iv.size + ciphertext.size)
                .put(iv)
                .put(ciphertext)
                .array()

            return Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            log.error("Encryption failed: ${e.message}")
            throw TokenEncryptionException("Failed to encrypt token", e)
        }
    }

    /**
     * Decrypts an encrypted token.
     * Expects Base64-encoded ciphertext with prepended IV.
     */
    fun decrypt(encryptedToken: String): String {
        require(encryptedToken.isNotBlank()) { "Cannot decrypt empty token" }

        try {
            val combined = Base64.getDecoder().decode(encryptedToken)

            require(combined.size > GCM_IV_LENGTH) {
                "Invalid encrypted token: too short"
            }

            val buffer = ByteBuffer.wrap(combined)
            val iv = ByteArray(GCM_IV_LENGTH)
            buffer.get(iv)

            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)

            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            log.error("Decryption failed: ${e.message}")
            throw TokenEncryptionException("Failed to decrypt token", e)
        }
    }

    /**
     * Generates a new random encryption key.
     * Useful for initial setup or key rotation.
     */
    fun generateKeyBase64(): String {
        val key = generateKey()
        return Base64.getEncoder().encodeToString(key.encoded)
    }

    private fun generateKey(): SecretKey {
        val keyBytes = ByteArray(KEY_LENGTH)
        secureRandom.nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    /**
     * Validates that the encryption service is properly configured.
     */
    fun validateConfiguration(): Boolean {
        return try {
            val testPlaintext = "test-token-validation"
            val encrypted = encrypt(testPlaintext)
            val decrypted = decrypt(encrypted)
            decrypted == testPlaintext
        } catch (e: Exception) {
            log.error("Encryption service validation failed: ${e.message}")
            false
        }
    }
}

class TokenEncryptionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
