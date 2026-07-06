package com.portfolio.auth.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Configuration
@ConfigurationProperties(prefix = "auth")
class AuthConfig {
    @Value("\${app.environment:local}")
    var appEnvironment: String = "local"

    var jwt: JwtConfig = JwtConfig()
    var password: PasswordConfig = PasswordConfig()
    var email: EmailConfig = EmailConfig()
    var oauth2: OAuth2Config = OAuth2Config()
    var cors: CorsConfig = CorsConfig()

    private val logger = LoggerFactory.getLogger(AuthConfig::class.java)

    /**
     * Validates that Google OAuth credentials are present when running in a non-local environment.
     * Emits a prominent WARN log when either clientId or clientSecret is blank,
     * including the greppable marker [AUTH_GOOGLE_CREDENTIALS_MISSING].
     * In local profile, missing credentials are silently accepted.
     */
    @PostConstruct
    fun validateGoogleCredentials() {
        if (appEnvironment == "local") return

        val missing = mutableListOf<String>()
        if (oauth2.google.clientId.isBlank()) missing.add("GOOGLE_CLIENT_ID")
        if (oauth2.google.clientSecret.isBlank()) missing.add("GOOGLE_CLIENT_SECRET")

        if (missing.isNotEmpty()) {
            logger.warn(
                "AUTH_GOOGLE_CREDENTIALS_MISSING: Google OAuth credentials are blank " +
                "in '${appEnvironment}' profile. Missing: ${missing.joinToString(", ")}. " +
                "Set ${missing.joinToString(" and ")} environment variable(s)."
            )
        }
    }

    @Bean
    fun googleOAuthWebClient(): WebClient {
        return WebClient.builder().build()
    }
}

class JwtConfig {
    var signingKey: String = ""
    var accessTokenExpiration: Duration = Duration.ofMinutes(15)
    var refreshTokenExpiration: Duration = Duration.ofDays(7)
    var issuer: String = "portfolio-app"
}

class PasswordConfig {
    var minLength: Int = 12
    var maxFailedAttempts: Int = 5
    var lockoutDuration: Duration = Duration.ofMinutes(30)
}

class EmailConfig {
    var provider: String = "console"
    var from: String = "noreply@portfolio.local"
    var verificationExpiry: Duration = Duration.ofHours(24)
    var resetExpiry: Duration = Duration.ofHours(6)
}

class OAuth2Config {
    var google: GoogleOAuthConfig = GoogleOAuthConfig()
}

class GoogleOAuthConfig {
    var clientId: String = ""
    var clientSecret: String = ""
    var tokenUrl: String = "https://oauth2.googleapis.com/token"
    var userinfoUrl: String = "https://www.googleapis.com/oauth2/v3/userinfo"
}

class CorsConfig {
    var allowedOrigins: String = "http://localhost:3000"
}
