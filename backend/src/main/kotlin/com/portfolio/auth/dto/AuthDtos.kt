package com.portfolio.auth.dto

import com.portfolio.auth.entity.User
import com.portfolio.auth.entity.UserIdentity
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

// ============================================================================
// Request DTOs
// ============================================================================

data class SignupRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 12, message = "Password must be at least 12 characters")
    val password: String,

    @field:Size(max = 255, message = "Name must be at most 255 characters")
    val name: String? = null
)

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class ForgotPasswordRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "Token is required")
    val token: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 12, message = "Password must be at least 12 characters")
    val newPassword: String
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "Current password is required")
    val currentPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 12, message = "Password must be at least 12 characters")
    val newPassword: String
)

data class ResendVerificationRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)

data class UpdateProfileRequest(
    @field:Size(max = 255, message = "Name must be at most 255 characters")
    val name: String? = null,

    @field:Size(max = 500, message = "Avatar URL must be at most 500 characters")
    val avatarUrl: String? = null
)

// ============================================================================
// Response DTOs
// ============================================================================

data class SignupResponse(
    val message: String,
    val userId: Long
)

data class AuthResponse(
    val user: UserResponse,
    val message: String? = null
)

data class MessageResponse(
    val message: String
)

data class UserResponse(
    val id: Long,
    val email: String,
    val name: String?,
    val avatarUrl: String?,
    val emailVerified: Boolean,
    val roles: List<String>,
    val identities: List<IdentityResponse>,
    val lastLoginAt: OffsetDateTime?,
    val createdAt: OffsetDateTime
) {
    companion object {
        fun from(user: User, roles: List<String> = emptyList()): UserResponse {
            return UserResponse(
                id = user.id,
                email = user.email,
                name = user.name,
                avatarUrl = user.avatarUrl,
                emailVerified = user.emailVerified,
                roles = roles.ifEmpty { user.getRoleNames() },
                identities = user.identities.map { IdentityResponse.from(it) },
                lastLoginAt = user.lastLoginAt,
                createdAt = user.createdAt
            )
        }
    }
}

data class IdentityResponse(
    val provider: String,
    val providerEmail: String?,
    val connectedAt: OffsetDateTime
) {
    companion object {
        fun from(identity: UserIdentity): IdentityResponse {
            return IdentityResponse(
                provider = identity.provider,
                providerEmail = identity.providerEmail,
                connectedAt = identity.createdAt
            )
        }
    }
}

// ============================================================================
// Error DTOs
// ============================================================================

data class AuthErrorResponse(
    val error: String,
    val message: String,
    val field: String? = null,
    val lockedUntil: OffsetDateTime? = null
)
