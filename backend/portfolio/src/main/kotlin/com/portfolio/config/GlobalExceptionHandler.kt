package com.portfolio.config

import com.portfolio.auth.exception.AccountLockedException
import com.portfolio.exception.*
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.time.OffsetDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException, request: HttpServletRequest): ProblemDetail {
        val status = when (ex) {
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ConflictException -> HttpStatus.CONFLICT
            is ValidationException -> HttpStatus.BAD_REQUEST
            is ForbiddenException -> HttpStatus.FORBIDDEN
            is RateLimitException -> HttpStatus.TOO_MANY_REQUESTS
            is ExternalServiceException -> HttpStatus.BAD_GATEWAY
            is InternalException -> HttpStatus.INTERNAL_SERVER_ERROR
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }

        log.warn("App error [{}] {}: {} at {}", status.value(), ex.code, ex.message, request.requestURI)

        return ProblemDetail.forStatusAndDetail(status, ex.message).apply {
            title = status.reasonPhrase
            instance = URI.create(request.requestURI)
            setProperty("code", ex.code)
            setProperty("timestamp", OffsetDateTime.now())
            if (ex is AccountLockedException) {
                setProperty("lockedUntil", ex.lockedUntil)
            }
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ProblemDetail {
        val fieldErrors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fieldErrors.joinToString("; ")).apply {
            title = "Validation Failed"
            instance = URI.create(request.requestURI)
            setProperty("code", "VALIDATION_ERROR")
            setProperty("timestamp", OffsetDateTime.now())
            setProperty("fields", ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage })
        }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ProblemDetail {
        log.warn("Bad request: {} at {}", ex.message, request.requestURI)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request").apply {
            title = "Bad Request"
            instance = URI.create(request.requestURI)
            setProperty("code", "BAD_REQUEST")
            setProperty("timestamp", OffsetDateTime.now())
        }
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException, request: HttpServletRequest): ProblemDetail {
        log.error("Illegal state: {} at {}", ex.message, request.requestURI)
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Operation conflict").apply {
            title = "Conflict"
            instance = URI.create(request.requestURI)
            setProperty("code", "CONFLICT")
            setProperty("timestamp", OffsetDateTime.now())
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ProblemDetail {
        log.error("Unhandled exception at {}: {}", request.requestURI, ex.message, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred").apply {
            title = "Internal Server Error"
            instance = URI.create(request.requestURI)
            setProperty("code", "INTERNAL_ERROR")
            setProperty("timestamp", OffsetDateTime.now())
        }
    }
}
