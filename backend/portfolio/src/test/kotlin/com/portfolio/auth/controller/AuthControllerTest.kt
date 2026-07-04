package com.portfolio.auth.controller

import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.config.CorsConfig
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuthenticationService
import com.portfolio.auth.service.GoogleOAuthException
import com.portfolio.auth.service.GoogleOAuthService
import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthControllerTest {

    private lateinit var controller: AuthController
    private lateinit var authenticationService: AuthenticationService
    private lateinit var userRepository: UserRepository
    private lateinit var authConfig: AuthConfig
    private lateinit var googleOAuthService: GoogleOAuthService
    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var listAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: LogbackLogger

    @BeforeEach
    fun setup() {
        authenticationService = mockk()
        userRepository = mockk()
        authConfig = mockk()
        googleOAuthService = mockk()
        request = mockk(relaxed = true)
        response = mockk(relaxed = true)

        val corsConfig = CorsConfig().apply {
            allowedOrigins = "http://localhost:3000"
        }
        every { authConfig.cors } returns corsConfig

        controller = AuthController(
            authenticationService = authenticationService,
            userRepository = userRepository,
            authConfig = authConfig,
            googleOAuthService = googleOAuthService,
            appEnvironment = "test"
        )

        // Set up log capture for verifying AUTH_CALLBACK_UNEXPECTED marker
        logger = LoggerFactory.getLogger(AuthController::class.java) as LogbackLogger
        listAppender = ListAppender<ILoggingEvent>()
        listAppender.start()
        logger.addAppender(listAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(listAppender)
        listAppender.stop()
    }

    /**
     * Test that GoogleOAuthException (with parsed google_error code) maps to auth_failed redirect.
     */
    @Test
    fun `googleCallback redirects with auth_failed when GoogleOAuthException is thrown`() {
        every { googleOAuthService.handleCallback(any(), any()) } throws GoogleOAuthException("google_error:invalid_grant")

        val result = controller.googleCallback(
            code = "test-code",
            state = "test-state",
            error = null,
            httpRequest = request,
            httpResponse = response
        )

        assertEquals(HttpStatus.FOUND, result.statusCode)
        val location = result.headers.location
        assertTrue(location.toString().contains("error=auth_failed"),
            "Expected redirect to contain error=auth_failed but got $location")
    }

    /**
     * Test that WebClientResponseException (defense-in-depth catch) maps to auth_failed redirect.
     */
    @Test
    fun `googleCallback redirects with auth_failed when WebClientResponseException is thrown`() {
        val exception = WebClientResponseException.create(
            400, "Bad Request", HttpHeaders(), ByteArray(0), null
        )
        every { googleOAuthService.handleCallback(any(), any()) } throws exception

        val result = controller.googleCallback(
            code = "test-code",
            state = "test-state",
            error = null,
            httpRequest = request,
            httpResponse = response
        )

        assertEquals(HttpStatus.FOUND, result.statusCode)
        val location = result.headers.location
        assertTrue(location.toString().contains("error=auth_failed"),
            "Expected redirect to contain error=auth_failed but got $location")
    }

    /**
     * Test that a generic RuntimeException maps to provider_unavailable redirect
     * and the log contains the AUTH_CALLBACK_UNEXPECTED marker for ops triage.
     */
    @Test
    fun `googleCallback redirects with provider_unavailable and logs AUTH_CALLBACK_UNEXPECTED when RuntimeException is thrown`() {
        every { googleOAuthService.handleCallback(any(), any()) } throws RuntimeException("Something unexpected broke")

        val result = controller.googleCallback(
            code = "test-code",
            state = "test-state",
            error = null,
            httpRequest = request,
            httpResponse = response
        )

        assertEquals(HttpStatus.FOUND, result.statusCode)
        val location = result.headers.location
        assertTrue(location.toString().contains("error=provider_unavailable"),
            "Expected redirect to contain error=provider_unavailable but got $location")

        // Verify that the log message contains the AUTH_CALLBACK_UNEXPECTED marker
        val logMessages = listAppender.list.map { it.formattedMessage }
        assertTrue(logMessages.any { it.contains("AUTH_CALLBACK_UNEXPECTED") },
            "Expected log to contain AUTH_CALLBACK_UNEXPECTED marker")
    }

    /**
     * Test that explicit error param (e.g., access_denied from Google) maps to auth_failed.
     */
    @Test
    fun `googleCallback redirects with auth_failed when error param is present`() {
        val result = controller.googleCallback(
            code = null,
            state = null,
            error = "access_denied",
            httpRequest = request,
            httpResponse = response
        )

        assertEquals(HttpStatus.FOUND, result.statusCode)
        val location = result.headers.location
        assertTrue(location.toString().contains("error=auth_failed"),
            "Expected redirect to contain error=auth_failed but got $location")
    }

    /**
     * Test that a missing code param results in auth_failed redirect.
     */
    @Test
    fun `googleCallback redirects with auth_failed when code is missing`() {
        val result = controller.googleCallback(
            code = null,
            state = "test-state",
            error = null,
            httpRequest = request,
            httpResponse = response
        )

        assertEquals(HttpStatus.FOUND, result.statusCode)
        val location = result.headers.location
        assertTrue(location.toString().contains("error=auth_failed"),
            "Expected redirect to contain error=auth_failed but got $location")
    }
}
