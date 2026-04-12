package com.portfolio.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@ConfigurationProperties(prefix = "auth")
class AuthConfig {
    var jwt: JwtConfig = JwtConfig()
    var password: PasswordConfig = PasswordConfig()
    var email: EmailConfig = EmailConfig()
    var oauth2: OAuth2Config = OAuth2Config()
    var cors: CorsConfig = CorsConfig()
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
}

class CorsConfig {
    var allowedOrigins: String = "http://localhost:3000"
}
