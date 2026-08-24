package com.portfolio.auth.config

import io.netty.channel.ChannelOption
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
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

    companion object {
        /** TCP connect timeout for Google OAuth endpoints (marker: GOOGLE_OAUTH_HTTP_CLIENT). */
        private const val GOOGLE_OAUTH_CONNECT_TIMEOUT_MS = 5000
        private val GOOGLE_OAUTH_RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(10)
    }

    /**
     * Dedicated WebClient for Google OAuth token/userinfo calls (singleton bean).
     *
     * Uses a named connection pool ("google-oauth") with bounded idle/lifetime so stale
     * or half-open connections to accounts.google.com / oauth2.googleapis.com are evicted
     * instead of failing sign-in (RCA marker: GOOGLE_OAUTH_HTTP_CLIENT):
     * - maxIdleTime 45s < Google's ~60s idle keep-alive window, avoiding reused dead sockets
     * - maxLifeTime 5min bounds long-lived connections behind NAT/LB churn
     * - evictInBackground 60s removes expired connections without waiting for a request
     * Explicit connect (5s) and response (10s) timeouts prevent callbacks from hanging.
     */
    @Bean
    fun googleOAuthWebClient(): WebClient {
        val connectionProvider = ConnectionProvider.builder("google-oauth")
            .maxIdleTime(Duration.ofSeconds(45))
            .maxLifeTime(Duration.ofMinutes(5))
            .evictInBackground(Duration.ofSeconds(60))
            .build()

        val httpClient = HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, GOOGLE_OAUTH_CONNECT_TIMEOUT_MS)
            .responseTimeout(GOOGLE_OAUTH_RESPONSE_TIMEOUT)

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
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
    /** Explicit redirect URI for Google OAuth. When set, this value is used verbatim.
     * When blank, falls back to CORS-derived URI (cors.allowedOrigins[0] + "/auth/google/callback"). */
    var redirectUri: String = ""
}

class CorsConfig {
    var allowedOrigins: String = "http://localhost:3000"
}
