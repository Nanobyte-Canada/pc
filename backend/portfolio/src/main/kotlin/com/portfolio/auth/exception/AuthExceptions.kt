package com.portfolio.auth.exception

import com.portfolio.exception.*
import java.time.OffsetDateTime

class EmailAlreadyExistsException : ConflictException(
    code = "EMAIL_EXISTS",
    message = "An account with this email already exists"
)

class InvalidCredentialsException : ForbiddenException(
    code = "INVALID_CREDENTIALS",
    message = "Invalid email or password"
)

class AccountLockedException(val lockedUntil: OffsetDateTime) : ForbiddenException(
    code = "ACCOUNT_LOCKED",
    message = "Account is locked due to too many failed attempts"
)

class EmailNotVerifiedException : ForbiddenException(
    code = "EMAIL_NOT_VERIFIED",
    message = "Please verify your email address before logging in"
)

class InvalidTokenException(message: String = "Invalid or expired token") : ValidationException(
    code = "INVALID_TOKEN",
    message = message
)

class InvalidPasswordException(message: String = "Password does not meet requirements") : ValidationException(
    code = "INVALID_PASSWORD",
    message = message
)

class UserNotFoundException : NotFoundException(
    code = "USER_NOT_FOUND",
    message = "User not found"
)

class AccessDeniedException(message: String = "Access denied") : ForbiddenException(
    code = "ACCESS_DENIED",
    message = message
)
