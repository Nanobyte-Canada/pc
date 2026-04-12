package com.portfolio.config

import com.portfolio.auth.exception.*
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
    fun `handles InvalidCredentialsException with 403 status and correct code`() {
        val exception = InvalidCredentialsException()
        val problem = handler.handleAppException(exception, request)
        assertEquals(403, problem.status)
        assertEquals("INVALID_CREDENTIALS", problem.properties?.get("code"))
        assertEquals("/api/v1/test", problem.instance?.toString())
    }

    @Test
    fun `handles AccountLockedException with 403 status and lockedUntil`() {
        val lockedUntil = java.time.OffsetDateTime.now().plusMinutes(30)
        val exception = AccountLockedException(lockedUntil)
        val problem = handler.handleAppException(exception, request)
        assertEquals(403, problem.status)
        assertEquals("ACCOUNT_LOCKED", problem.properties?.get("code"))
        assertEquals(lockedUntil, problem.properties?.get("lockedUntil"))
    }

    @Test
    fun `handles EmailAlreadyExistsException with 409 status`() {
        val exception = EmailAlreadyExistsException()
        val problem = handler.handleAppException(exception, request)
        assertEquals(409, problem.status)
        assertEquals("EMAIL_EXISTS", problem.properties?.get("code"))
    }

    @Test
    fun `handles UserNotFoundException with 404 status`() {
        val exception = UserNotFoundException()
        val problem = handler.handleAppException(exception, request)
        assertEquals(404, problem.status)
        assertEquals("USER_NOT_FOUND", problem.properties?.get("code"))
    }

    @Test
    fun `handles InvalidTokenException with 400 status`() {
        val exception = InvalidTokenException()
        val problem = handler.handleAppException(exception, request)
        assertEquals(400, problem.status)
        assertEquals("INVALID_TOKEN", problem.properties?.get("code"))
    }

    @Test
    fun `handles InvalidPasswordException with 400 status`() {
        val exception = InvalidPasswordException()
        val problem = handler.handleAppException(exception, request)
        assertEquals(400, problem.status)
        assertEquals("INVALID_PASSWORD", problem.properties?.get("code"))
    }

    @Test
    fun `handles IllegalArgumentException with 400 status`() {
        val exception = IllegalArgumentException("bad input")
        val problem = handler.handleIllegalArgument(exception, request)
        assertEquals(400, problem.status)
        assertEquals("bad input", problem.detail)
        assertEquals("BAD_REQUEST", problem.properties?.get("code"))
    }

    @Test
    fun `handles IllegalStateException with 409 status`() {
        val exception = IllegalStateException("conflict state")
        val problem = handler.handleIllegalState(exception, request)
        assertEquals(409, problem.status)
        assertEquals("conflict state", problem.detail)
        assertEquals("CONFLICT", problem.properties?.get("code"))
    }

    @Test
    fun `handles generic Exception with 500 status`() {
        val exception = RuntimeException("unexpected")
        val problem = handler.handleGeneric(exception, request)
        assertEquals(500, problem.status)
        assertEquals("INTERNAL_ERROR", problem.properties?.get("code"))
    }

    @Test
    fun `all ProblemDetail responses include timestamp`() {
        val exception = EmailAlreadyExistsException()
        val problem = handler.handleAppException(exception, request)
        assertNotNull(problem.properties?.get("timestamp"))
    }
}
