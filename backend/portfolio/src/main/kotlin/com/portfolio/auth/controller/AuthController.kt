package com.portfolio.auth.controller

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.dto.*
import com.portfolio.auth.exception.*
import com.portfolio.auth.security.UserPrincipal
import com.portfolio.auth.service.AuthenticationService
import com.portfolio.auth.service.ClientInfo
import com.portfolio.auth.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Duration

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authenticationService: AuthenticationService,
    private val userRepository: UserRepository,
    private val authConfig: AuthConfig,
    @Value("\${app.environment:local}") private val appEnvironment: String
) {

    companion object {
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val REFRESH_TOKEN_COOKIE = "refresh_token"
    }

    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: SignupRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<SignupResponse> {
        val clientInfo = extractClientInfo(httpRequest)
        val response = authenticationService.signup(request, clientInfo)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<AuthResponse> {
        val clientInfo = extractClientInfo(httpRequest)
        val authTokens = authenticationService.login(request, clientInfo)

        // Set cookies
        setAuthCookies(httpResponse, authTokens.accessToken, authTokens.refreshToken)

        val userResponse = UserResponse.from(authTokens.user, authTokens.roles)
        return ResponseEntity.ok(AuthResponse(user = userResponse))
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

    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<MessageResponse> {
        val clientInfo = extractClientInfo(httpRequest)
        val response = authenticationService.forgotPassword(request, clientInfo)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @RequestBody request: ResetPasswordRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<MessageResponse> {
        val clientInfo = extractClientInfo(httpRequest)
        val response = authenticationService.resetPassword(request, clientInfo)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/verify-email")
    fun verifyEmail(
        @RequestParam token: String,
        httpRequest: HttpServletRequest
    ): ResponseEntity<MessageResponse> {
        val clientInfo = extractClientInfo(httpRequest)
        val response = authenticationService.verifyEmail(token, clientInfo)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/resend-verification")
    fun resendVerification(
        @Valid @RequestBody request: ResendVerificationRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<MessageResponse> {
        val clientInfo = extractClientInfo(httpRequest)
        val response = authenticationService.resendVerificationEmail(request, clientInfo)
        return ResponseEntity.ok(response)
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

    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<MessageResponse> {
        val clientInfo = extractClientInfo(httpRequest)
        val response = authenticationService.changePassword(principal.id, request, clientInfo)

        // Clear cookies to force re-login after password change
        clearAuthCookies(httpResponse)

        return ResponseEntity.ok(response)
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
