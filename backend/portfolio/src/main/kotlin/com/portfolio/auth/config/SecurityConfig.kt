package com.portfolio.auth.config

import com.portfolio.auth.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val authConfig: AuthConfig
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val csrfTokenRequestHandler = CsrfTokenRequestAttributeHandler()
        csrfTokenRequestHandler.setCsrfRequestAttributeName("_csrf")

        http
            // Configure CSRF
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfTokenRequestHandler)
                    .ignoringRequestMatchers(
                        "/auth/login",
                        "/auth/signup",
                        "/auth/refresh",
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        "/auth/resend-verification",
                        "/auth/google",
                        "/auth/google/callback",
                        "/health",
                        "/actuator/**",
                        "/api/**"
                    )
            }
            // Configure CORS
            .cors { cors ->
                cors.configurationSource(corsConfigurationSource())
            }
            // Stateless session (JWT-based)
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            // Authorization rules
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health", "/api/v1/version").permitAll()
                    .requestMatchers(
                        "/auth/login",
                        "/auth/signup",
                        "/auth/refresh",
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        "/auth/resend-verification",
                        "/auth/google",
                        "/auth/google/callback",
                        "/auth/verify-email",
                        "/auth/logout",
                    ).permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/v1/admin/**", "/admin/**").hasRole("ADMIN")
                    .requestMatchers("/auth/me", "/auth/change-password", "/auth/profile").authenticated()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated()
            }
            // Exception handling
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler { _, response, _ ->
                        response.sendError(HttpStatus.FORBIDDEN.value(), "Access denied")
                    }
            }
            // Add JWT filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = authConfig.cors.allowedOrigins.split(",").map { it.trim() }
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.exposedHeaders = listOf("X-CSRF-TOKEN")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
