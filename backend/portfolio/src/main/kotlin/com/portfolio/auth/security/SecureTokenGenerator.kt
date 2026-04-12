package com.portfolio.auth.security

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

@Component
class SecureTokenGenerator {

    private val secureRandom = SecureRandom()

    /**
     * Generate a cryptographically secure random token
     * Returns both the raw token (to send to user) and its hash (to store)
     */
    fun generateToken(length: Int = 32): TokenPair {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hash = hashToken(token)
        return TokenPair(token = token, hash = hash)
    }

    /**
     * Hash a token for storage using SHA-256
     */
    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify a token against its stored hash
     */
    fun verifyToken(token: String, storedHash: String): Boolean {
        val computedHash = hashToken(token)
        return constantTimeEquals(computedHash, storedHash)
    }

    /**
     * Generate a refresh token (longer, more entropy)
     */
    fun generateRefreshToken(): TokenPair {
        return generateToken(48)
    }

    /**
     * Generate an OAuth state token
     */
    fun generateStateToken(): TokenPair {
        return generateToken(32)
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}

data class TokenPair(
    val token: String,  // Send to user
    val hash: String    // Store in database
)
