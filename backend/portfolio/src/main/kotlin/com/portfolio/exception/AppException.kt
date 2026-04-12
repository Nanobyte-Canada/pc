package com.portfolio.exception

abstract class AppException(
    override val message: String,
    val code: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

open class NotFoundException(code: String = "NOT_FOUND", message: String = "Resource not found") : AppException(message, code)
open class ConflictException(code: String = "CONFLICT", message: String = "Operation conflict") : AppException(message, code)
open class ValidationException(code: String = "VALIDATION_ERROR", message: String = "Invalid input") : AppException(message, code)
open class ForbiddenException(code: String = "FORBIDDEN", message: String = "Access denied") : AppException(message, code)
open class RateLimitException(code: String = "RATE_LIMITED", message: String = "Too many requests") : AppException(message, code)
open class ExternalServiceException(code: String = "EXTERNAL_SERVICE_ERROR", message: String = "External service unavailable", cause: Throwable? = null) : AppException(message, code, cause)
open class InternalException(code: String = "INTERNAL_ERROR", message: String = "An unexpected error occurred", cause: Throwable? = null) : AppException(message, code, cause)
