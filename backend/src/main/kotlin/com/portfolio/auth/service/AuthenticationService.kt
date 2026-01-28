package com.portfolio.auth.service

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.dto.*
import com.portfolio.auth.entity.*
import com.portfolio.auth.exception.*
import com.portfolio.auth.repository.*
import com.portfolio.auth.security.JwtTokenProvider
import com.portfolio.auth.security.SecureTokenGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

data class ClientInfo(
    val ipAddress: String?,
    val userAgent: String?,
    val baseUrl: String
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
    val roles: List<String>
)

@Service
class AuthenticationService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordService: PasswordService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val secureTokenGenerator: SecureTokenGenerator,
    private val emailService: EmailService,
    private val auditService: AuditService,
    private val authConfig: AuthConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Register a new user with email and password
     */
    @Transactional
    fun signup(request: SignupRequest, clientInfo: ClientInfo): SignupResponse {
        val email = request.email.lowercase().trim()

        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw EmailAlreadyExistsException()
        }

        // Validate password strength
        val validation = passwordService.validatePasswordStrength(request.password)
        if (!validation.isValid) {
            throw InvalidPasswordException(validation.errors.first())
        }

        // Create user
        val user = User(
            email = email,
            passwordHash = passwordService.hashPassword(request.password),
            name = request.name?.trim(),
            emailVerified = false
        )
        val savedUser = userRepository.save(user)

        // Assign default USER role
        val userRole = roleRepository.findByName(Role.USER)
            ?: throw IllegalStateException("USER role not found")
        val userRoleAssignment = UserRole(user = savedUser, role = userRole)
        userRoleRepository.save(userRoleAssignment)

        // Create email verification token
        val tokenPair = secureTokenGenerator.generateToken()
        val verificationToken = EmailVerificationToken(
            user = savedUser,
            tokenHash = tokenPair.hash,
            expiresAt = OffsetDateTime.now().plus(authConfig.email.verificationExpiry)
        )
        emailVerificationTokenRepository.save(verificationToken)

        // Send verification email
        emailService.sendVerificationEmail(savedUser, tokenPair.token, clientInfo.baseUrl)

        // Audit log
        auditService.logSignup(savedUser, clientInfo.ipAddress, clientInfo.userAgent)

        return SignupResponse(
            message = "Account created. Please check your email to verify your account.",
            userId = savedUser.id
        )
    }

    /**
     * Authenticate user with email and password
     */
    @Transactional
    fun login(request: LoginRequest, clientInfo: ClientInfo): AuthTokens {
        val email = request.email.lowercase().trim()

        // Find user
        val user = userRepository.findByEmailWithRoles(email)
            ?: throw InvalidCredentialsException()

        // Check if account is locked
        if (user.isLocked()) {
            auditService.logLogin(user, clientInfo.ipAddress, clientInfo.userAgent, false, "account_locked")
            throw AccountLockedException(user.lockedUntil!!)
        }

        // Verify password
        if (user.passwordHash == null || !passwordService.verifyPassword(request.password, user.passwordHash!!)) {
            handleFailedLogin(user, clientInfo)
            throw InvalidCredentialsException()
        }

        // Check email verification
        if (!user.emailVerified) {
            throw EmailNotVerifiedException()
        }

        // Success - reset failed attempts and update login info
        user.failedLoginAttempts = 0
        user.lockedUntil = null
        user.lastLoginAt = OffsetDateTime.now()
        user.lastLoginIp = clientInfo.ipAddress
        userRepository.save(user)

        // Generate tokens
        val roles = user.getRoleNames()
        val accessToken = jwtTokenProvider.generateAccessToken(user, roles)
        val refreshTokenPair = refreshTokenService.createRefreshToken(
            user = user,
            deviceInfo = clientInfo.userAgent,
            ipAddress = clientInfo.ipAddress
        )

        // Audit log
        auditService.logLogin(user, clientInfo.ipAddress, clientInfo.userAgent)

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshTokenPair.token,
            user = user,
            roles = roles
        )
    }

    /**
     * Logout - revoke refresh token
     */
    @Transactional
    fun logout(refreshToken: String, user: User, clientInfo: ClientInfo) {
        refreshTokenService.revokeRefreshToken(refreshToken, "logout")
        auditService.logLogout(user, clientInfo.ipAddress, clientInfo.userAgent)
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    fun refreshAccessToken(refreshToken: String, clientInfo: ClientInfo): AuthTokens {
        // Validate and rotate refresh token
        val newRefreshTokenPair = refreshTokenService.rotateRefreshToken(
            oldToken = refreshToken,
            deviceInfo = clientInfo.userAgent,
            ipAddress = clientInfo.ipAddress
        ) ?: throw InvalidTokenException("Invalid or expired refresh token")

        // Get user from the old token (before rotation)
        val tokenHash = secureTokenGenerator.hashToken(refreshToken)
        val oldToken = refreshTokenService.validateRefreshToken(refreshToken)

        // Since we rotated, we need to fetch user differently
        val user = userRepository.findByIdWithRoles(
            refreshTokenService.validateRefreshToken(newRefreshTokenPair.token)?.user?.id
                ?: throw InvalidTokenException("Invalid refresh token")
        ) ?: throw InvalidTokenException("User not found")

        if (user.isLocked() || user.status != UserStatus.ACTIVE) {
            throw AccountLockedException(user.lockedUntil ?: OffsetDateTime.now())
        }

        val roles = user.getRoleNames()
        val accessToken = jwtTokenProvider.generateAccessToken(user, roles)

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = newRefreshTokenPair.token,
            user = user,
            roles = roles
        )
    }

    /**
     * Request password reset
     */
    @Transactional
    fun forgotPassword(request: ForgotPasswordRequest, clientInfo: ClientInfo): MessageResponse {
        val email = request.email.lowercase().trim()

        // Always return success to prevent email enumeration
        val user = userRepository.findByEmail(email)
        if (user != null) {
            // Create password reset token
            val tokenPair = secureTokenGenerator.generateToken()
            val resetToken = PasswordResetToken(
                user = user,
                tokenHash = tokenPair.hash,
                expiresAt = OffsetDateTime.now().plus(authConfig.email.resetExpiry)
            )
            passwordResetTokenRepository.save(resetToken)

            // Send reset email
            emailService.sendPasswordResetEmail(user, tokenPair.token, clientInfo.baseUrl)

            // Audit log
            auditService.logPasswordResetRequest(user, clientInfo.ipAddress, clientInfo.userAgent)
        }

        return MessageResponse("If an account exists with this email, a password reset link has been sent.")
    }

    /**
     * Reset password with token
     */
    @Transactional
    fun resetPassword(request: ResetPasswordRequest, clientInfo: ClientInfo): MessageResponse {
        val tokenHash = secureTokenGenerator.hashToken(request.token)
        val resetToken = passwordResetTokenRepository.findByTokenHashWithUser(tokenHash)
            ?: throw InvalidTokenException("Invalid or expired reset token")

        if (!resetToken.isValid()) {
            throw InvalidTokenException("Reset token has expired")
        }

        // Validate new password
        val validation = passwordService.validatePasswordStrength(request.newPassword)
        if (!validation.isValid) {
            throw InvalidPasswordException(validation.errors.first())
        }

        val user = resetToken.user

        // Update password
        user.passwordHash = passwordService.hashPassword(request.newPassword)
        userRepository.save(user)

        // Mark token as used
        resetToken.markUsed()
        passwordResetTokenRepository.save(resetToken)

        // Revoke all refresh tokens
        refreshTokenService.revokeAllUserTokens(user.id, "password_reset")

        // Audit log
        auditService.logPasswordResetComplete(user, clientInfo.ipAddress, clientInfo.userAgent)

        return MessageResponse("Password has been reset successfully. Please log in with your new password.")
    }

    /**
     * Verify email with token
     */
    @Transactional
    fun verifyEmail(token: String, clientInfo: ClientInfo): MessageResponse {
        val tokenHash = secureTokenGenerator.hashToken(token)
        val verificationToken = emailVerificationTokenRepository.findByTokenHashWithUser(tokenHash)
            ?: throw InvalidTokenException("Invalid or expired verification token")

        if (!verificationToken.isValid()) {
            throw InvalidTokenException("Verification token has expired")
        }

        val user = verificationToken.user

        // Mark email as verified
        user.emailVerified = true
        user.emailVerifiedAt = OffsetDateTime.now()
        userRepository.save(user)

        // Mark token as used
        verificationToken.markUsed()
        emailVerificationTokenRepository.save(verificationToken)

        // Audit log
        auditService.logEmailVerification(user, clientInfo.ipAddress, clientInfo.userAgent)

        return MessageResponse("Email verified successfully. You can now log in.")
    }

    /**
     * Resend verification email
     */
    @Transactional
    fun resendVerificationEmail(request: ResendVerificationRequest, clientInfo: ClientInfo): MessageResponse {
        val email = request.email.lowercase().trim()

        val user = userRepository.findByEmail(email)
        if (user != null && !user.emailVerified) {
            // Create new verification token
            val tokenPair = secureTokenGenerator.generateToken()
            val verificationToken = EmailVerificationToken(
                user = user,
                tokenHash = tokenPair.hash,
                expiresAt = OffsetDateTime.now().plus(authConfig.email.verificationExpiry)
            )
            emailVerificationTokenRepository.save(verificationToken)

            // Send verification email
            emailService.sendVerificationEmail(user, tokenPair.token, clientInfo.baseUrl)
        }

        return MessageResponse("If the email exists and is not verified, a new verification link has been sent.")
    }

    /**
     * Change password for authenticated user
     */
    @Transactional
    fun changePassword(userId: Long, request: ChangePasswordRequest, clientInfo: ClientInfo): MessageResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException() }

        // Verify current password
        if (user.passwordHash == null || !passwordService.verifyPassword(request.currentPassword, user.passwordHash!!)) {
            throw InvalidCredentialsException()
        }

        // Validate new password
        val validation = passwordService.validatePasswordStrength(request.newPassword)
        if (!validation.isValid) {
            throw InvalidPasswordException(validation.errors.first())
        }

        // Update password
        user.passwordHash = passwordService.hashPassword(request.newPassword)
        user.updatedAt = OffsetDateTime.now()
        userRepository.save(user)

        // Revoke all refresh tokens to force re-login on all devices
        refreshTokenService.revokeAllUserTokens(user.id, "password_change")

        // Audit log
        auditService.logPasswordChange(user, clientInfo.ipAddress, clientInfo.userAgent)

        return MessageResponse("Password changed successfully. Please log in again.")
    }

    /**
     * Update user profile
     */
    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest, clientInfo: ClientInfo): User {
        val user = userRepository.findByIdWithIdentities(userId)
            ?: throw UserNotFoundException()

        // Update fields if provided
        request.name?.let { user.name = it.trim().ifEmpty { null } }
        request.avatarUrl?.let { user.avatarUrl = it.trim().ifEmpty { null } }
        user.updatedAt = OffsetDateTime.now()

        val savedUser = userRepository.save(user)

        // Audit log
        auditService.logProfileUpdate(user, clientInfo.ipAddress, clientInfo.userAgent)

        return savedUser
    }

    /**
     * Handle failed login attempt
     */
    private fun handleFailedLogin(user: User, clientInfo: ClientInfo) {
        user.failedLoginAttempts++

        if (user.failedLoginAttempts >= authConfig.password.maxFailedAttempts) {
            user.lockedUntil = OffsetDateTime.now().plus(authConfig.password.lockoutDuration)
            auditService.logUserLock(user, "max_failed_attempts", clientInfo.ipAddress)
            emailService.sendAccountLockedEmail(user)
        }

        userRepository.save(user)
        auditService.logLogin(user, clientInfo.ipAddress, clientInfo.userAgent, false, "invalid_password")
    }
}
