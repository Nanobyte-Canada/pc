package com.portfolio.ingestion.exception

abstract class AppException(
    override val message: String,
    val code: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class NotFoundException(code: String = "NOT_FOUND", message: String = "Resource not found") : AppException(message, code)
class ConflictException(code: String = "CONFLICT", message: String = "Operation conflict") : AppException(message, code)
class ValidationException(code: String = "VALIDATION_ERROR", message: String = "Invalid input") : AppException(message, code)
class ExternalServiceException(code: String = "EXTERNAL_SERVICE_ERROR", message: String = "External service unavailable", cause: Throwable? = null) : AppException(message, code, cause)
class InternalException(code: String = "INTERNAL_ERROR", message: String = "An unexpected error occurred", cause: Throwable? = null) : AppException(message, code, cause)
