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
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthControllerTest {

    private fun createController(
        googleOAuthService: GoogleOAuthService = mockk(),
        authConfig: AuthConfig = mockk(),
        appEnvironment: String = "local"
    ): AuthController {
        val frontendUrl = "https://portfolio.nanobyte.ca"
        val corsConfig = CorsConfig().apply {
            allowedOrigins = frontendUrl
        }
        every { authConfig.cors } returns corsConfig

        return AuthController(
            authenticationService = mockk(),
            userRepository = mockk(),
            authConfig = authConfig,
            googleOAuthService = googleOAuthService,
            appEnvironment = appEnvironment
        )
    }

    private fun mockRequest(): HttpServletRequest = mockk {
        every { scheme } returns "https"
        every { serverName } returns "portfolio.nanobyte.ca"
        every { serverPort } returns 443
        every { remoteAddr } returns "127.0.0.1"
        every { getHeader("X-Forwarded-For") } returns null
        every { getHeader("User-Agent") } returns "test-agent"
    }

    private fun mockResponse(): HttpServletResponse = mockk(relaxed = true)

    @Test
    fun `googleCallback redirects to auth_failed when error param is present`() {
        val controller = createController()
        val response = controller.googleCallback(
            code = null,
            state = null,
            error = "access_denied",
            httpRequest = mockRequest(),
            httpResponse = mockResponse()
        )
        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=auth_failed"))
    }

    @Test
    fun `googleCallback redirects to auth_failed when code param is missing`() {
        val controller = createController()
        val response = controller.googleCallback(
            code = null,
            state = "some-state",
            error = null,
            httpRequest = mockRequest(),
            httpResponse = mockResponse()
        )
        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=auth_failed"))
    }

    @Test
    fun `googleCallback redirects to auth_failed when state param is missing`() {
        val controller = createController()
        val response = controller.googleCallback(
            code = "some-code",
            state = null,
            error = null,
            httpRequest = mockRequest(),
            httpResponse = mockResponse()
        )
        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=auth_failed"))
    }

    @Test
    fun `googleCallback redirects to auth_failed when GoogleOAuthException is thrown`() {
        val googleOAuthService = mockk<GoogleOAuthService>()
        every { googleOAuthService.handleCallback("test-code", "test-state") }
            .throws(GoogleOAuthException("google_error:invalid_grant"))

        val controller = createController(googleOAuthService = googleOAuthService)
        val response = controller.googleCallback(
            code = "test-code",
            state = "test-state",
            error = null,
            httpRequest = mockRequest(),
            httpResponse = mockResponse()
        )
        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=auth_failed"))
    }

    @Test
    fun `googleCallback redirects to auth_failed when WebClientResponseException is thrown`() {
        val googleOAuthService = mockk<GoogleOAuthService>()
        val webClientEx = object : WebClientResponseException(
            400, "Bad Request", null, null, null as java.nio.charset.Charset?
        ) {}
        every { googleOAuthService.handleCallback("test-code", "test-state") }
            .throws(webClientEx)

        val controller = createController(googleOAuthService = googleOAuthService)
        val response = controller.googleCallback(
            code = "test-code",
            state = "test-state",
            error = null,
            httpRequest = mockRequest(),
            httpResponse = mockResponse()
        )
        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=auth_failed"))
    }

    @Test
    fun `googleCallback redirects to provider_unavailable when unexpected RuntimeException is thrown`() {
        val googleOAuthService = mockk<GoogleOAuthService>()
        every { googleOAuthService.handleCallback("test-code", "test-state") }
            .throws(RuntimeException("Unexpected DB connection failure"))

        val controller = createController(googleOAuthService = googleOAuthService)
        val response = controller.googleCallback(
            code = "test-code",
            state = "test-state",
            error = null,
            httpRequest = mockRequest(),
            httpResponse = mockResponse()
        )
        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertTrue(response.headers.location!!.toString().contains("error=provider_unavailable"))
    }

    @Test
    fun `googleLogin returns redirect to Google authorization URL`() {
        val googleOAuthService = mockk<GoogleOAuthService>()
        every { googleOAuthService.initiateGoogleLogin() } returns "https://accounts.google.com/o/oauth2/v2/auth?client_id=test"

        val controller = createController(googleOAuthService = googleOAuthService)
        val response = controller.googleLogin()

        assertEquals(HttpStatus.FOUND, response.statusCode)
        assertEquals(URI.create("https://accounts.google.com/o/oauth2/v2/auth?client_id=test"), response.headers.location)
    }
}
