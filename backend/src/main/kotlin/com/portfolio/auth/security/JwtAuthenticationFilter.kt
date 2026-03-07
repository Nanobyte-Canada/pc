package com.portfolio.auth.security

import com.portfolio.auth.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        log.info("JWT Filter: {} {} | Cookies present: {} | Auth header present: {}",
            request.method, request.servletPath,
            request.cookies?.any { it.name == ACCESS_TOKEN_COOKIE } ?: false,
            request.getHeader(AUTHORIZATION_HEADER) != null)

        try {
            val token = extractToken(request)

            if (token == null) {
                log.info("No token found in request to {} {}", request.method, request.servletPath)
            } else if (!jwtTokenProvider.isAccessToken(token)) {
                log.info("Token is not a valid access token for {} {}", request.method, request.servletPath)
            } else {
                val userId = jwtTokenProvider.getUserIdFromToken(token)
                val roles = jwtTokenProvider.getRolesFromToken(token)

                if (userId != null) {
                    val user = userRepository.findByIdWithRoles(userId)

                    if (user == null) {
                        log.info("User not found for userId: {}", userId)
                    } else if (user.isLocked()) {
                        log.info("User {} is locked", userId)
                    } else {
                        val principal = UserPrincipal.from(user, roles)
                        val authentication = UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.authorities
                        )
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        SecurityContextHolder.getContext().authentication = authentication
                        log.info("Successfully authenticated user {} for {} {}", userId, request.method, request.servletPath)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Could not authenticate user: ${e.message}", e)
        }

        log.info("JWT Filter result: {} {} | Authenticated: {}",
            request.method, request.servletPath,
            SecurityContextHolder.getContext().authentication != null)

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        // First, try to get from cookie
        val cookies = request.cookies
        if (cookies != null) {
            val tokenCookie = cookies.find { it.name == ACCESS_TOKEN_COOKIE }
            if (tokenCookie != null) {
                return tokenCookie.value
            }
        }

        // Fallback to Authorization header
        val authHeader = request.getHeader(AUTHORIZATION_HEADER)
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length)
        }

        return null
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        // Skip filter for public endpoints
        return path.startsWith("/auth/") ||
               path == "/health" ||
               path == "/api/v1/version" ||
               path.startsWith("/actuator/") ||
               path.matches(Regex("/api/v1/brokers/[^/]+/callback"))  // OAuth callbacks
    }
}
