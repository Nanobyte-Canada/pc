package com.portfolio.auth.service

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.entity.OAuthState
import com.portfolio.auth.entity.User
import com.portfolio.auth.entity.UserIdentity
import com.portfolio.auth.entity.UserRole
import com.portfolio.auth.entity.Role
import com.portfolio.auth.repository.OAuthStateRepository
import com.portfolio.auth.repository.RoleRepository
import com.portfolio.auth.repository.UserIdentityRepository
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.repository.UserRoleRepository
import com.portfolio.auth.security.SecureTokenGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime

data class GoogleUserProfile(
    val sub: String,
    val email: String,
    val name: String?,
    val picture: String?
)

private data class GoogleTokenResponse(val accessToken: String)

class GoogleOAuthException(message: String) : RuntimeException(message)

@Service
class GoogleOAuthService(
    private val oauthStateRepository: OAuthStateRepository,
    private val userIdentityRepository: UserIdentityRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val secureTokenGenerator: SecureTokenGenerator,
    private val auditService: AuditService,
    private val authConfig: AuthConfig,
    private val webClient: WebClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val STATE_EXPIRY_MINUTES = 10L
    }

    @Transactional
    fun initiateGoogleLogin(): String {
        val tokenPair = secureTokenGenerator.generateStateToken()

        val oauthState = OAuthState(
            stateHash = tokenPair.hash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().plusMinutes(STATE_EXPIRY_MINUTES)
        )
        oauthStateRepository.save(oauthState)

        val googleConfig = authConfig.oauth2.google
        val params = mapOf(
            "client_id" to googleConfig.clientId,
            "redirect_uri" to googleConfig.redirectUri,
            "response_type" to "code",
            "scope" to "openid email profile",
            "state" to tokenPair.token,
            "access_type" to "offline",
            "prompt" to "consent"
        )

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }

        return "$GOOGLE_AUTH_URL?$queryString"
    }

    @Transactional
    fun handleCallback(code: String, state: String): GoogleUserProfile {
        val stateHash = secureTokenGenerator.hashToken(state)
        val oauthState = oauthStateRepository.findByStateHash(stateHash)
            ?: throw GoogleOAuthException("Invalid state token")

        if (!oauthState.isValid()) {
            throw GoogleOAuthException("State token has expired or already been used")
        }

        oauthState.markUsed()
        oauthStateRepository.save(oauthState)

        val googleConfig = authConfig.oauth2.google
        val tokenResponse = exchangeCodeForTokens(
            code = code,
            clientId = googleConfig.clientId,
            clientSecret = googleConfig.clientSecret,
            redirectUri = googleConfig.redirectUri
        )

        return fetchUserProfile(tokenResponse.accessToken)
    }

    @Transactional
    fun findOrCreateUser(profile: GoogleUserProfile, ipAddress: String?): User {
        val existingIdentity = userIdentityRepository.findByProviderAndProviderUserIdWithUser(
            UserIdentity.PROVIDER_GOOGLE,
            profile.sub
        )

        if (existingIdentity != null) {
            existingIdentity.providerEmail = profile.email
            existingIdentity.providerName = profile.name
            existingIdentity.providerAvatarUrl = profile.picture
            existingIdentity.updatedAt = OffsetDateTime.now()
            userIdentityRepository.save(existingIdentity)

            val user = existingIdentity.user
            user.lastLoginAt = OffsetDateTime.now()
            user.lastLoginIp = ipAddress
            userRepository.save(user)
            return user
        }

        val existingUser = userRepository.findByEmail(profile.email.lowercase())
        if (existingUser != null) {
            linkGoogleIdentity(existingUser, profile)
            existingUser.emailVerified = true
            existingUser.emailVerifiedAt = existingUser.emailVerifiedAt ?: OffsetDateTime.now()
            existingUser.lastLoginAt = OffsetDateTime.now()
            existingUser.lastLoginIp = ipAddress
            if (existingUser.avatarUrl == null && profile.picture != null) {
                existingUser.avatarUrl = profile.picture
            }
            userRepository.save(existingUser)
            auditService.logOAuthLink(existingUser, UserIdentity.PROVIDER_GOOGLE, ipAddress, null)
            return existingUser
        }

        // Create new user
        return createNewUser(profile, ipAddress)
    }

    private fun createNewUser(profile: GoogleUserProfile, ipAddress: String?): User {
        val user = User(
            email = profile.email.lowercase(),
            emailVerified = true,
            emailVerifiedAt = OffsetDateTime.now(),
            passwordHash = null,
            name = profile.name,
            avatarUrl = profile.picture
        )
        val savedUser = userRepository.save(user)

        val identity = UserIdentity(
            user = savedUser,
            provider = UserIdentity.PROVIDER_GOOGLE,
            providerUserId = profile.sub,
            providerEmail = profile.email,
            providerName = profile.name,
            providerAvatarUrl = profile.picture
        )
        userIdentityRepository.save(identity)

        val userRole = roleRepository.findByName(Role.USER)
            ?: throw IllegalStateException("USER role not found in database")
        userRoleRepository.save(UserRole(user = savedUser, role = userRole))

        auditService.logSignup(savedUser, ipAddress, null)
        logger.info("Created new user via Google OAuth: ${savedUser.email}")

        return savedUser
    }

    private fun linkGoogleIdentity(user: User, profile: GoogleUserProfile) {
        val identity = UserIdentity(
            user = user,
            provider = UserIdentity.PROVIDER_GOOGLE,
            providerUserId = profile.sub,
            providerEmail = profile.email,
            providerName = profile.name,
            providerAvatarUrl = profile.picture
        )
        userIdentityRepository.save(identity)
        logger.info("Linked Google identity to existing user: ${user.email}")
    }

    private fun exchangeCodeForTokens(
        code: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String
    ): GoogleTokenResponse {
        val response = webClient.post()
            .uri(authConfig.oauth2.google.tokenUrl)
            .body(BodyInserters.fromFormData("code", code)
                .with("client_id", clientId)
                .with("client_secret", clientSecret)
                .with("redirect_uri", redirectUri)
                .with("grant_type", "authorization_code"))
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw GoogleOAuthException("Failed to exchange authorization code")

        val accessToken = response["access_token"] as? String
            ?: throw GoogleOAuthException("No access token in response")

        return GoogleTokenResponse(accessToken = accessToken)
    }

    private fun fetchUserProfile(accessToken: String): GoogleUserProfile {
        val response = webClient.get()
            .uri(authConfig.oauth2.google.userinfoUrl)
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw GoogleOAuthException("Failed to fetch user profile")

        return GoogleUserProfile(
            sub = response["sub"] as? String ?: throw GoogleOAuthException("No sub in Google profile"),
            email = response["email"] as? String ?: throw GoogleOAuthException("No email in Google profile"),
            name = response["name"] as? String,
            picture = response["picture"] as? String
        )
    }
}
