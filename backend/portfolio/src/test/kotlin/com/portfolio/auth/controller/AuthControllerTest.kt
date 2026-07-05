package com.portfolio.auth.controller

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.config.CorsConfig
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.AuthenticationService
import com.portfolio.auth.service.GoogleOAuthException
import com.portfolio.auth.service.GoogleOAuthService
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthControllerTest {

    private lateinit var controller: AuthController
    private lateinit var authenticationService: AuthenticationService
    private lateinit var userRepository: UserRepository
    private lateinit var authConfig: AuthConfig
    private lateinit var googleOAuthService: GoogleOAuthService

    @BeforeEach
    fun setup() {
        authenticationService = mockk()
        userRepository = mockk()
        authConfig = mockk()
        googleOAuthService = mockk()

        controller = AuthController(
            authenticationService = authenticationService,
            userRepository = userRepository,
            authConfig = authConfig,
            googleOAuthService = googleOAuthService,
            appEnvironment = "test"
        )
    }

    @Test
    fun `googleCallback redirects to auth_failed on GoogleOAuthException`() {
        val corsConfig = CorsConfig().apply {
            allowedOrigins = "https://example.com,https://other.com"
        }
        every { authConfig.cors } returns corsConfig

        every { googleOAuthService.handleCallback("test-code", "test-state") } throws
            GoogleOAuthException("Invalid state token")

        val httpRequest = mockk<HttpServletRequest>(relaxed = true)
        val httpResponse = mockk<HttpServletResponse>(relaxed = true)

        val response = controller.googleCallback(
            code = "test-code",
            state = "test-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertNotNull(response)
        assertEquals(HttpStatus.FOUND, response.statusCode)
        val location = response.headers.location?.toString() ?: ""
        assertTrue(location.contains("error=auth_failed"), "Expected auth_failed but got: $location")
    }

    @Test
    fun `googleCallback redirects to auth_failed on WebClientResponseException`() {
        val corsConfig = CorsConfig().apply {
            allowedOrigins = "https://example.com"
        }
        every { authConfig.cors } returns corsConfig

        val webClientEx = org.springframework.web.reactive.function.client.WebClientResponseException.create(
            400, "Bad Request", org.springframework.http.HttpHeaders(), ByteArray(0), null
        )
        every { googleOAuthService.handleCallback("test-code", "test-state") } throws webClientEx

        val httpRequest = mockk<HttpServletRequest>(relaxed = true)
        val httpResponse = mockk<HttpServletResponse>(relaxed = true)

        val response = controller.googleCallback(
            code = "test-code",
            state = "test-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        val location = response.headers.location?.toString() ?: ""
        assertTrue(location.contains("error=auth_failed"), "Expected auth_failed but got: $location")
    }

    @Test
    fun `googleCallback redirects to provider_unavailable on generic Exception`() {
        val corsConfig = CorsConfig().apply {
            allowedOrigins = "https://example.com"
        }
        every { authConfig.cors } returns corsConfig

        every { googleOAuthService.handleCallback("test-code", "test-state") } throws
            RuntimeException("Something totally unexpected")

        val httpRequest = mockk<HttpServletRequest>(relaxed = true)
        val httpResponse = mockk<HttpServletResponse>(relaxed = true)

        val response = controller.googleCallback(
            code = "test-code",
            state = "test-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        val location = response.headers.location?.toString() ?: ""
        assertTrue(location.contains("error=provider_unavailable"), "Expected provider_unavailable but got: $location")
    }

    @Test
    fun `googleCallback redirects to auth_failed when error param is present`() {
        val corsConfig = CorsConfig().apply {
            allowedOrigins = "https://example.com"
        }
        every { authConfig.cors } returns corsConfig

        val httpRequest = mockk<HttpServletRequest>(relaxed = true)
        val httpResponse = mockk<HttpServletResponse>(relaxed = true)

        val response = controller.googleCallback(
            code = null,
            state = null,
            error = "access_denied",
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        val location = response.headers.location?.toString() ?: ""
        assertTrue(location.contains("error=auth_failed"), "Expected auth_failed but got: $location")
    }

    @Test
    fun `googleCallback redirects to auth_failed when code is null`() {
        val corsConfig = CorsConfig().apply {
            allowedOrigins = "https://example.com"
        }
        every { authConfig.cors } returns corsConfig

        val httpRequest = mockk<HttpServletRequest>(relaxed = true)
        val httpResponse = mockk<HttpServletResponse>(relaxed = true)

        val response = controller.googleCallback(
            code = null,
            state = "test-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        val location = response.headers.location?.toString() ?: ""
        assertTrue(location.contains("error=auth_failed"), "Expected auth_failed but got: $location")
    }

    @Test
    fun `googleLogin redirects to Google authorization URL`() {
        every { googleOAuthService.initiateGoogleLogin() } returns
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=test&redirect_uri=https://example.com/auth/google/callback"

        val response = controller.googleLogin()

        assertEquals(HttpStatus.FOUND, response.statusCode)
        val location = response.headers.location?.toString() ?: ""
        assertEquals(
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=test&redirect_uri=https://example.com/auth/google/callback",
            location
        )
    }
}
