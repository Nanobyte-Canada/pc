package com.portfolio.auth.controller

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.config.CorsConfig
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.security.JwtAuthenticationFilter
import com.portfolio.auth.service.AuthenticationService
import com.portfolio.auth.service.GoogleOAuthException
import com.portfolio.auth.service.GoogleOAuthService
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.nio.charset.Charset

@WebMvcTest(controllers = [AuthController::class])
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(OutputCaptureExtension::class)
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @MockBean
    private lateinit var authConfig: AuthConfig

    @MockBean
    private lateinit var authenticationService: AuthenticationService

    @MockBean
    private lateinit var userRepository: UserRepository

    @MockBean
    private lateinit var googleOAuthService: GoogleOAuthService

    private fun corsConfig(): CorsConfig = CorsConfig().apply { allowedOrigins = "http://localhost:3000" }

    @Test
    fun `GoogleOAuthException maps to auth_failed redirect`() {
        `when`(authConfig.cors).thenReturn(corsConfig())
        `when`(googleOAuthService.handleCallback("test-code", "test-state"))
            .thenThrow(GoogleOAuthException("Invalid state"))

        mockMvc.perform(get("/auth/google/callback")
            .param("code", "test-code")
            .param("state", "test-state")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", containsString("/login?error=auth_failed")))
    }

    @Test
    fun `WebClientResponseException maps to auth_failed redirect`() {
        `when`(authConfig.cors).thenReturn(corsConfig())
        val webClientException = WebClientResponseException.create(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            HttpHeaders.EMPTY,
            byteArrayOf(),
            Charset.defaultCharset()
        )
        `when`(googleOAuthService.handleCallback("test-code", "test-state"))
            .thenThrow(webClientException)

        mockMvc.perform(get("/auth/google/callback")
            .param("code", "test-code")
            .param("state", "test-state")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", containsString("/login?error=auth_failed")))
    }

    @Test
    fun `generic RuntimeException maps to provider_unavailable with AUTH_CALLBACK_UNEXPECTED log`(output: CapturedOutput) {
        `when`(authConfig.cors).thenReturn(corsConfig())
        `when`(googleOAuthService.handleCallback("test-code", "test-state"))
            .thenThrow(RuntimeException("Unexpected failure"))

        mockMvc.perform(get("/auth/google/callback")
            .param("code", "test-code")
            .param("state", "test-state")
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", containsString("/login?error=provider_unavailable")))

        assertTrue(output.all.contains("AUTH_CALLBACK_UNEXPECTED"),
            "Log should contain AUTH_CALLBACK_UNEXPECTED marker, but got: ${output.all}")
    }
}
