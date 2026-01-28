package com.portfolio.auth.exception

import java.time.OffsetDateTime

sealed class AuthException(
    override val message: String,
    val errorCode: String
) : RuntimeException(message)

class EmailAlreadyExistsException : AuthException(
    message = "An account with this email already exists",
    errorCode = "EMAIL_EXISTS"
)

class InvalidCredentialsException : AuthException(
    message = "Invalid email or password",
    errorCode = "INVALID_CREDENTIALS"
)

class AccountLockedException(val lockedUntil: OffsetDateTime) : AuthException(
    message = "Account is locked due to too many failed attempts",
    errorCode = "ACCOUNT_LOCKED"
)

class EmailNotVerifiedException : AuthException(
    message = "Please verify your email address before logging in",
    errorCode = "EMAIL_NOT_VERIFIED"
)

class InvalidTokenException(message: String = "Invalid or expired token") : AuthException(
    message = message,
    errorCode = "INVALID_TOKEN"
)

class InvalidPasswordException(message: String = "Password does not meet requirements") : AuthException(
    message = message,
    errorCode = "INVALID_PASSWORD"
)

class UserNotFoundException : AuthException(
    message = "User not found",
    errorCode = "USER_NOT_FOUND"
)

class AccessDeniedException(message: String = "Access denied") : AuthException(
    message = message,
    errorCode = "ACCESS_DENIED"
)
