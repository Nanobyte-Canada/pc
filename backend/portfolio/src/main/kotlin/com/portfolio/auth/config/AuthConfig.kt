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
    var jwt: JwtConfig = JwtConfig()
    var password: PasswordConfig = PasswordConfig()
    var email: EmailConfig = EmailConfig()
    var oauth2: OAuth2Config = OAuth2Config()
    var cors: CorsConfig = CorsConfig()

    @Value("\${app.environment:local}")
    private lateinit var appEnvironment: String

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun googleOAuthWebClient(): WebClient {
        return WebClient.builder().build()
    }

    @PostConstruct
    fun validateGoogleCredentials() {
        if (appEnvironment == "local") return
        val google = oauth2.google
        if (google.clientId.isBlank() || google.clientSecret.isBlank()) {
            logger.warn("AUTH_GOOGLE_CREDENTIALS_MISSING: GOOGLE_CLIENT_ID and/or GOOGLE_CLIENT_SECRET are not set. Google OAuth sign-in will fail with auth_failed until configured.")
        }
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
    var redirectUri: String? = null
}

class CorsConfig {
    var allowedOrigins: String = "http://localhost:3000"
}
