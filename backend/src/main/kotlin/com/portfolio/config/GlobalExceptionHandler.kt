package com.portfolio.config

import com.portfolio.auth.exception.*
import com.portfolio.dto.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AuthException::class)
    fun handleAuthException(
        ex: AuthException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val status = when (ex) {
            is InvalidCredentialsException -> HttpStatus.UNAUTHORIZED
            is AccountLockedException -> HttpStatus.LOCKED
            is EmailAlreadyExistsException -> HttpStatus.CONFLICT
            is EmailNotVerifiedException -> HttpStatus.FORBIDDEN
            is InvalidTokenException -> HttpStatus.UNAUTHORIZED
            is InvalidPasswordException -> HttpStatus.BAD_REQUEST
            is UserNotFoundException -> HttpStatus.NOT_FOUND
            is AccessDeniedException -> HttpStatus.FORBIDDEN
        }

        log.warn("Auth error [{}]: {} at {}", ex.errorCode, ex.message, request.requestURI)

        return ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = ex.message,
                errorCode = ex.errorCode,
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: {} at {}", ex.message, request.requestURI)

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                status = 400,
                error = "Bad Request",
                message = ex.message ?: "Invalid request",
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(
        ex: IllegalStateException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Illegal state: {} at {}", ex.message, request.requestURI)

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                status = 409,
                error = "Conflict",
                message = ex.message ?: "Operation conflict",
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception at {}: {}", request.requestURI, ex.message, ex)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = 500,
                error = "Internal Server Error",
                message = "An unexpected error occurred",
                errorCode = "INTERNAL_ERROR",
                path = request.requestURI
            )
        )
    }
}
