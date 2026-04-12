package com.portfolio.auth.service

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.entity.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val authConfig: AuthConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Send email verification email
     * Currently logs to console - replace with real email provider in production
     */
    fun sendVerificationEmail(user: User, token: String, baseUrl: String) {
        val verificationUrl = "$baseUrl/auth/verify-email?token=$token"

        if (authConfig.email.provider == "console") {
            logger.info("""
                |
                |========================================
                | EMAIL VERIFICATION
                |========================================
                | To: ${user.email}
                | From: ${authConfig.email.from}
                | Subject: Verify your email address
                |
                | Hello ${user.name ?: "there"},
                |
                | Please verify your email by clicking:
                | $verificationUrl
                |
                | This link expires in 24 hours.
                |========================================
                |
            """.trimMargin())
        } else {
            // TODO: Implement real email provider (SendGrid, SES, etc.)
            logger.warn("Email provider '${authConfig.email.provider}' not implemented, using console")
            sendVerificationEmail(user, token, baseUrl)
        }
    }

    /**
     * Send password reset email
     */
    fun sendPasswordResetEmail(user: User, token: String, baseUrl: String) {
        val resetUrl = "$baseUrl/reset-password?token=$token"

        if (authConfig.email.provider == "console") {
            logger.info("""
                |
                |========================================
                | PASSWORD RESET
                |========================================
                | To: ${user.email}
                | From: ${authConfig.email.from}
                | Subject: Reset your password
                |
                | Hello ${user.name ?: "there"},
                |
                | You requested a password reset. Click:
                | $resetUrl
                |
                | This link expires in 6 hours.
                | If you didn't request this, ignore it.
                |========================================
                |
            """.trimMargin())
        } else {
            logger.warn("Email provider '${authConfig.email.provider}' not implemented, using console")
            sendPasswordResetEmail(user, token, baseUrl)
        }
    }

    /**
     * Send welcome email after signup
     */
    fun sendWelcomeEmail(user: User) {
        if (authConfig.email.provider == "console") {
            logger.info("""
                |
                |========================================
                | WELCOME EMAIL
                |========================================
                | To: ${user.email}
                | From: ${authConfig.email.from}
                | Subject: Welcome to Portfolio App!
                |
                | Hello ${user.name ?: "there"},
                |
                | Welcome to Portfolio App!
                | Your account has been created.
                |========================================
                |
            """.trimMargin())
        }
    }

    /**
     * Send account locked notification
     */
    fun sendAccountLockedEmail(user: User) {
        if (authConfig.email.provider == "console") {
            logger.info("""
                |
                |========================================
                | ACCOUNT LOCKED
                |========================================
                | To: ${user.email}
                | From: ${authConfig.email.from}
                | Subject: Your account has been locked
                |
                | Hello ${user.name ?: "there"},
                |
                | Your account has been temporarily locked
                | due to too many failed login attempts.
                |
                | It will be unlocked in 30 minutes.
                | If this wasn't you, reset your password.
                |========================================
                |
            """.trimMargin())
        }
    }
}
