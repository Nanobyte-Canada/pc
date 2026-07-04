package com.portfolio.auth.controller

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.config.CorsConfig
import com.portfolio.auth.config.JwtConfig
import com.portfolio.auth.security.TokenPair
import com.portfolio.auth.service.AuthenticationService
import com.portfolio.auth.service.GoogleOAuthException
import com.portfolio.auth.service.GoogleOAuthService
import com.portfolio.auth.service.GoogleUserProfile
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.UserRepository
import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClientResponseException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthControllerTest {

    private lateinit var controller: AuthController
    private lateinit var authenticationService: AuthenticationService
    private lateinit var userRepository: UserRepository
    private lateinit var authConfig: AuthConfig
    private lateinit var googleOAuthService: GoogleOAuthService
    private lateinit var httpRequest: HttpServletRequest
    private lateinit var httpResponse: HttpServletResponse

    private val frontendUrl = "https://portfolio.nanobyte.ca"

    @BeforeEach
    fun setup() {
        authenticationService = mockk()
        userRepository = mockk()
        authConfig = mockk()
        googleOAuthService = mockk()
        httpRequest = mockk(relaxed = true)
        httpResponse = mockk(relaxed = true)

        // Set up auth config to return the expected frontend URL
        val corsConfig = CorsConfig().apply {
            allowedOrigins = frontendUrl
        }
        val jwtConfig = JwtConfig().apply {
            accessTokenExpiration = java.time.Duration.ofMinutes(15)
            refreshTokenExpiration = java.time.Duration.ofDays(7)
        }
        every { authConfig.cors } returns corsConfig
        every { authConfig.jwt } returns jwtConfig

        // Common HTTP request stubs
        every { httpRequest.getHeader("X-Forwarded-For") } returns "192.168.1.1"
        every { httpRequest.getHeader("User-Agent") } returns "Mozilla/5.0"
        every { httpRequest.remoteAddr } returns "192.168.1.1"
        every { httpRequest.scheme } returns "https"
        every { httpRequest.serverName } returns "api.nanobyte.ca"
        every { httpRequest.serverPort } returns 443

        controller = AuthController(
            authenticationService = authenticationService,
            userRepository = userRepository,
            authConfig = authConfig,
            googleOAuthService = googleOAuthService,
            appEnvironment = "local"
        )
    }

    @Test
    fun `googleCallback returns auth_failed when Google returns an error parameter`() {
        val response = controller.googleCallback(
            code = "some-code",
            state = "some-state",
            error = "access_denied",
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=auth_failed"))
    }

    @Test
    fun `googleCallback returns auth_failed when code or state is missing`() {
        val response = controller.googleCallback(
            code = null,
            state = "some-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=auth_failed"))
    }

    @Test
    fun `googleCallback returns auth_failed when GoogleOAuthException is thrown`() {
        val exception = GoogleOAuthException("google_error:invalid_grant - Malformed auth code")
        every { googleOAuthService.handleCallback(any(), any()) } throws exception

        val response = controller.googleCallback(
            code = "invalid-code",
            state = "valid-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=auth_failed"))
    }

    @Test
    fun `googleCallback returns auth_failed when WebClientResponseException is thrown`() {
        // WebClientResponseException can still escape if thrown from outside
        // GoogleOAuthService or in edge cases — the controller should catch it
        val exception = WebClientResponseException.create(
            400,
            "Bad Request",
            org.springframework.http.HttpHeaders.EMPTY,
            ByteArray(0),
            null
        )
        every { googleOAuthService.handleCallback(any(), any()) } throws exception

        val response = controller.googleCallback(
            code = "bad-code",
            state = "valid-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=auth_failed"))
    }

    @Test
    fun `googleCallback returns provider_unavailable for unexpected RuntimeException`() {
        val exception = RuntimeException("Unexpected database error")
        every { googleOAuthService.handleCallback(any(), any()) } throws exception

        val response = controller.googleCallback(
            code = "valid-code",
            state = "valid-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=provider_unavailable"))
    }

    @Test
    fun `googleCallback returns provider_unavailable for unexpected checked exception`() {
        val exception = IllegalStateException("Unexpected internal state")
        every { googleOAuthService.handleCallback(any(), any()) } throws exception

        val response = controller.googleCallback(
            code = "valid-code",
            state = "valid-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=provider_unavailable"))
    }

    @Test
    fun `googleCallback redirects to frontend on successful login`() {
        val profile = GoogleUserProfile(
            sub = "google-123",
            email = "test@example.com",
            name = "Test User",
            picture = "https://example.com/avatar.jpg"
        )
        val user = User(id = 1L, email = "test@example.com", emailVerified = true)

        every { googleOAuthService.handleCallback(any(), any()) } returns profile
        every { googleOAuthService.findOrCreateUser(any(), any()) } returns user
        every { userRepository.findRoleNamesByUserId(user.id) } returns listOf("USER")
        every { authenticationService.generateAccessToken(any(), any()) } returns "access-token"
        every { authenticationService.createRefreshToken(any(), any()) } returns TokenPair(
            token = "refresh-token",
            hash = "hashed-refresh-token"
        )

        val response = controller.googleCallback(
            code = "valid-code",
            state = "valid-state",
            error = null,
            httpRequest = httpRequest,
            httpResponse = httpResponse
        )

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertEquals(frontendUrl, response.headers.location.toString())
    }
}
