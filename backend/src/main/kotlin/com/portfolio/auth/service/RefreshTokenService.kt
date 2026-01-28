package com.portfolio.auth.service

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.entity.RefreshToken
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.RefreshTokenRepository
import com.portfolio.auth.security.SecureTokenGenerator
import com.portfolio.auth.security.TokenPair
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val secureTokenGenerator: SecureTokenGenerator,
    private val authConfig: AuthConfig
) {

    /**
     * Create a new refresh token for a user
     */
    @Transactional
    fun createRefreshToken(
        user: User,
        deviceInfo: String? = null,
        ipAddress: String? = null
    ): TokenPair {
        val tokenPair = secureTokenGenerator.generateRefreshToken()
        val expiresAt = OffsetDateTime.now().plus(authConfig.jwt.refreshTokenExpiration)

        val refreshToken = RefreshToken(
            user = user,
            tokenHash = tokenPair.hash,
            deviceInfo = deviceInfo,
            ipAddress = ipAddress,
            expiresAt = expiresAt
        )

        refreshTokenRepository.save(refreshToken)
        return tokenPair
    }

    /**
     * Validate a refresh token and return the associated user
     */
    @Transactional(readOnly = true)
    fun validateRefreshToken(token: String): RefreshToken? {
        val tokenHash = secureTokenGenerator.hashToken(token)
        val refreshToken = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
            ?: return null

        if (!refreshToken.isValid()) {
            return null
        }

        return refreshToken
    }

    /**
     * Rotate a refresh token - invalidate old one and create new one
     */
    @Transactional
    fun rotateRefreshToken(
        oldToken: String,
        deviceInfo: String? = null,
        ipAddress: String? = null
    ): TokenPair? {
        val tokenHash = secureTokenGenerator.hashToken(oldToken)
        val existingToken = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
            ?: return null

        if (!existingToken.isValid()) {
            return null
        }

        // Revoke the old token
        existingToken.revoke("rotated")
        refreshTokenRepository.save(existingToken)

        // Create a new token
        return createRefreshToken(existingToken.user, deviceInfo, ipAddress)
    }

    /**
     * Revoke a specific refresh token
     */
    @Transactional
    fun revokeRefreshToken(token: String, reason: String = "logout"): Boolean {
        val tokenHash = secureTokenGenerator.hashToken(token)
        val refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: return false

        refreshToken.revoke(reason)
        refreshTokenRepository.save(refreshToken)
        return true
    }

    /**
     * Revoke all refresh tokens for a user (e.g., on password change)
     */
    @Transactional
    fun revokeAllUserTokens(userId: Long, reason: String = "password_changed") {
        refreshTokenRepository.revokeAllByUserId(userId, OffsetDateTime.now(), reason)
    }

    /**
     * Clean up expired and revoked tokens
     */
    @Transactional
    fun cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked()
    }
}
