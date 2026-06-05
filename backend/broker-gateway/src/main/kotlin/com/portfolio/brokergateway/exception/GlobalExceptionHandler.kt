package com.portfolio.brokergateway.exception

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

    @ExceptionHandler(BrokerGatewayException::class)
    fun handleGatewayException(ex: BrokerGatewayException, request: HttpServletRequest): ProblemDetail {
        val status = when (ex) {
            is ConnectionNotFoundException -> HttpStatus.NOT_FOUND
            is BrokerAuthenticationException -> HttpStatus.UNAUTHORIZED
            is BrokerConnectionException -> HttpStatus.BAD_GATEWAY
            is BrokerRateLimitException -> HttpStatus.TOO_MANY_REQUESTS
            is BrokerOrderRejectedException -> HttpStatus.UNPROCESSABLE_ENTITY
            is BrokerUnsupportedOperationException -> HttpStatus.NOT_IMPLEMENTED
            is BrokerDataException -> HttpStatus.BAD_GATEWAY
        }

        log.warn("Gateway error [{}] {}: {} at {}", status.value(), ex.errorCode, ex.message, request.requestURI)

        return ProblemDetail.forStatusAndDetail(status, ex.message).apply {
            title = status.reasonPhrase
            instance = URI.create(request.requestURI)
            setProperty("code", ex.errorCode)
            setProperty("timestamp", OffsetDateTime.now())
            if (ex is BrokerRateLimitException && ex.retryAfterSeconds != null) {
                setProperty("retryAfterSeconds", ex.retryAfterSeconds)
            }
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
