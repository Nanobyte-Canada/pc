package com.portfolio.ingestion.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
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
