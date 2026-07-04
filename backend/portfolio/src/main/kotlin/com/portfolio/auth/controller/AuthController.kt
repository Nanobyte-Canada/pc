package com.portfolio.auth.controller

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.dto.*
import com.portfolio.auth.exception.UserNotFoundException
import com.portfolio.auth.security.UserPrincipal
import com.portfolio.auth.service.AuthenticationService
import com.portfolio.auth.service.ClientInfo
import com.portfolio.auth.service.GoogleOAuthService
import com.portfolio.auth.service.GoogleOAuthException
import com.portfolio.auth.repository.UserRepository
import org.springframework.web.reactive.function.client.WebClientResponseException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.net.URLEncoder
import java.time.Duration

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authenticationService: AuthenticationService,
    private val userRepository: UserRepository,
    private val authConfig: AuthConfig,
    private val googleOAuthService: GoogleOAuthService,
    @Value("\${app.environment:local}") private val appEnvironment: String
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val REFRESH_TOKEN_COOKIE = "refresh_token"
    }

    @GetMapping("/google")
    fun googleLogin(): ResponseEntity<Void> {
        val authorizationUrl = googleOAuthService.initiateGoogleLogin()
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(authorizationUrl))
            .build()
    }

    @GetMapping("/google/callback")
    fun googleCallback(
        @RequestParam code: String?,
        @RequestParam state: String?,
        @RequestParam error: String?,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<Void> {
        val frontendUrl = authConfig.cors.allowedOrigins.split(",").first().trim()

        if (error != null) {
            logger.warn("Google OAuth error: $error")
            return redirectToFrontend(frontendUrl, "auth_failed")
        }

        if (code == null || state == null) {
            return redirectToFrontend(frontendUrl, "auth_failed")
        }

        return try {
            val clientInfo = extractClientInfo(httpRequest)
            val profile = googleOAuthService.handleCallback(code, state)
            val user = googleOAuthService.findOrCreateUser(profile, clientInfo.ipAddress)

            val roles = userRepository.findRoleNamesByUserId(user.id)
            val accessToken = authenticationService.generateAccessToken(user, roles)
            val refreshTokenPair = authenticationService.createRefreshToken(user, clientInfo)

            setAuthCookies(httpResponse, accessToken, refreshTokenPair.token)

            ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl))
                .build()
        } catch (e: WebClientResponseException) {
            logger.error("Google OAuth callback failed with HTTP ${e.statusCode}: ${e.message}")
            redirectToFrontend(frontendUrl, "auth_failed")
        } catch (e: GoogleOAuthException) {
            logger.error("Google OAuth callback failed: ${e.message}")
            redirectToFrontend(frontendUrl, "auth_failed")
        } catch (e: Exception) {
            logger.error("AUTH_CALLBACK_UNEXPECTED Unexpected error during Google OAuth callback", e)
            redirectToFrontend(frontendUrl, "provider_unavailable")
        }
    }

    @PostMapping("/logout")
    fun logout(
        @CookieValue(REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
        @AuthenticationPrincipal principal: UserPrincipal?,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<MessageResponse> {
        if (refreshToken != null && principal != null) {
            val user = userRepository.findById(principal.id).orElse(null)
            if (user != null) {
                val clientInfo = extractClientInfo(httpRequest)
                authenticationService.logout(refreshToken, user, clientInfo)
            }
        }

        // Clear cookies
        clearAuthCookies(httpResponse)

        return ResponseEntity.ok(MessageResponse("Logged out successfully"))
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<AuthResponse> {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponse(
                    user = UserResponse(0, "", null, null, false, emptyList(), emptyList(), null, java.time.OffsetDateTime.now()),
                    message = "No refresh token provided"
                ))
        }

        val clientInfo = extractClientInfo(httpRequest)
        val authTokens = authenticationService.refreshAccessToken(refreshToken, clientInfo)

        // Set new cookies
        setAuthCookies(httpResponse, authTokens.accessToken, authTokens.refreshToken)

        val userResponse = UserResponse.from(authTokens.user, authTokens.roles)
        return ResponseEntity.ok(AuthResponse(user = userResponse))
    }

    @GetMapping("/me")
    fun getCurrentUser(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<UserResponse> {
        val user = userRepository.findByIdWithIdentities(principal.id)
            ?: throw UserNotFoundException()

        val roles = userRepository.findRoleNamesByUserId(user.id)
        return ResponseEntity.ok(UserResponse.from(user, roles))
    }

    @PutMapping("/profile")
    fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
        httpRequest: HttpServletRequest
    ): ResponseEntity<UserResponse> {
        val clientInfo = extractClientInfo(httpRequest)
        val updatedUser = authenticationService.updateProfile(principal.id, request, clientInfo)
        val roles = userRepository.findRoleNamesByUserId(updatedUser.id)
        return ResponseEntity.ok(UserResponse.from(updatedUser, roles))
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun redirectToFrontend(frontendUrl: String, error: String): ResponseEntity<Void> {
        val encodedError = URLEncoder.encode(error, "UTF-8")
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("$frontendUrl/login?error=$encodedError"))
            .build()
    }

    private fun extractClientInfo(request: HttpServletRequest): ClientInfo {
        val ipAddress = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
        val userAgent = request.getHeader("User-Agent")
        val baseUrl = "${request.scheme}://${request.serverName}" +
            (if (request.serverPort != 80 && request.serverPort != 443) ":${request.serverPort}" else "")

        return ClientInfo(
            ipAddress = ipAddress,
            userAgent = userAgent,
            baseUrl = baseUrl
        )
    }

    private fun setAuthCookies(response: HttpServletResponse, accessToken: String, refreshToken: String) {
        // Use secure cookies for both prod and dev (both use HTTPS)
        val isSecure = appEnvironment == "prod" || appEnvironment == "dev"

        val accessCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, accessToken)
            .httpOnly(true)
            .secure(isSecure)  // Secure for HTTPS sites (prod and dev)
            .sameSite("Lax")  // Lax allows cookies on same-site navigation
            .path("/")
            .maxAge(authConfig.jwt.accessTokenExpiration)
            .build()

        val refreshCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
            .httpOnly(true)
            .secure(isSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(authConfig.jwt.refreshTokenExpiration)
            .build()

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString())
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString())
    }

    private fun clearAuthCookies(response: HttpServletResponse) {
        // Use secure cookies for both prod and dev (both use HTTPS)
        val isSecure = appEnvironment == "prod" || appEnvironment == "dev"

        val accessCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(isSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build()

        val refreshCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(isSecure)
            .sameSite("Lax")
            .path("/")  // Must match the path used in setAuthCookies
            .maxAge(Duration.ZERO)
            .build()

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString())
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString())
    }
}
