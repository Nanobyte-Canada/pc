package com.portfolio.config

import com.portfolio.auth.exception.*
import com.portfolio.dto.response.ErrorResponse
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalExceptionHandlerTest {

    private lateinit var handler: GlobalExceptionHandler
    private lateinit var request: HttpServletRequest

    @BeforeEach
    fun setup() {
        handler = GlobalExceptionHandler()
        request = mockk()
        every { request.requestURI } returns "/api/v1/test"
    }

    @Test
    fun `handles AuthException with correct status and error code`() {
        val exception = InvalidCredentialsException()
        val response = handler.handleAuthException(exception, request)
        assertEquals(401, response.statusCode.value())
        val body = response.body!!
        assertEquals("INVALID_CREDENTIALS", body.errorCode)
        assertEquals("/api/v1/test", body.path)
    }

    @Test
    fun `handles AccountLockedException with 423 status`() {
        val exception = AccountLockedException(java.time.OffsetDateTime.now().plusMinutes(30))
        val response = handler.handleAuthException(exception, request)
        assertEquals(423, response.statusCode.value())
        assertEquals("ACCOUNT_LOCKED", response.body!!.errorCode)
    }

    @Test
    fun `handles EmailAlreadyExistsException with 409 status`() {
        val exception = EmailAlreadyExistsException()
        val response = handler.handleAuthException(exception, request)
        assertEquals(409, response.statusCode.value())
        assertEquals("EMAIL_EXISTS", response.body!!.errorCode)
    }

    @Test
    fun `handles IllegalArgumentException with 400 status`() {
        val exception = IllegalArgumentException("bad input")
        val response = handler.handleIllegalArgument(exception, request)
        assertEquals(400, response.statusCode.value())
        assertEquals("bad input", response.body!!.message)
    }

    @Test
    fun `handles generic Exception with 500 status`() {
        val exception = RuntimeException("unexpected")
        val response = handler.handleGenericException(exception, request)
        assertEquals(500, response.statusCode.value())
        assertEquals("INTERNAL_ERROR", response.body!!.errorCode)
    }
}
