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
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.PrematureCloseException
import java.net.ConnectException
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
    private val webClient: WebClient,
    /**
     * Programmatic transaction boundary for the state validate+consume unit. Nullable so
     * plain unit tests can construct the service without Spring; in production Spring Boot
     * auto-configures a TransactionTemplate and injects it (nullable constructor params are
     * optional dependencies for Spring's Kotlin support).
     */
    private val transactionTemplate: TransactionTemplate? = null
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    companion object {
        private const val GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val STATE_EXPIRY_MINUTES = 10L

        /**
         * Total attempts per external HTTP call: initial attempt + 1 retry
         * (marker: GOOGLE_OAUTH_RETRY). Never more — the auth code is single-use.
         */
        private const val MAX_HTTP_ATTEMPTS = 2
        private const val RETRY_BACKOFF_MS = 250L
    }

    /** Resolves the Google OAuth redirect URI. Returns the explicit
     * [GoogleOAuthConfig.redirectUri] if non-blank, otherwise falls back to
     * `cors.allowedOrigins[0] + "/auth/google/callback"`. */
    private fun resolveRedirectUri(): String {
        val explicit = authConfig.oauth2.google.redirectUri
        if (explicit.isNotBlank()) return explicit
        return authConfig.cors.allowedOrigins.split(",").first().trim() + "/auth/google/callback"
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
        val redirectUri = resolveRedirectUri()
        val params = mapOf(
            "client_id" to googleConfig.clientId,
            "redirect_uri" to redirectUri,
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

    /**
     * Handles the OAuth callback WITHOUT holding a database transaction across the
     * external Google HTTP calls (marker: GOOGLE_OAUTH_TX_SPLIT).
     *
     * Deliberate phase separation:
     * 1. [consumeStateForCallback] — validate + consume the state token in its own short
     *    transactional unit (no external I/O inside).
     * 2. Token exchange + userinfo fetch — run OUTSIDE any transaction so a DB connection
     *    is never pinned across slow/unreachable upstream calls (root cause of recurring
     *    provider_unavailable sign-in failures under pool exhaustion).
     * 3. [findOrCreateUser] keeps its own @Transactional unit.
     */
    fun handleCallback(code: String, state: String): GoogleUserProfile {
        consumeStateForCallback(state)

        val googleConfig = authConfig.oauth2.google
        val redirectUri = resolveRedirectUri()
        val tokenResponse = exchangeCodeForTokens(
            code = code,
            clientId = googleConfig.clientId,
            clientSecret = googleConfig.clientSecret,
            redirectUri = redirectUri
        )

        return fetchUserProfile(tokenResponse.accessToken)
    }

    /**
     * Validates and consumes the OAuth state token in a single short transaction.
     * Exception semantics preserved: [GoogleOAuthException] for invalid/expired/used states,
     * which rolls the unit back and maps to `auth_failed` in the controller.
     */
    private fun consumeStateForCallback(state: String) {
        val consumeUnit = {
            val stateHash = secureTokenGenerator.hashToken(state)
            val oauthState = oauthStateRepository.findByStateHash(stateHash)
                ?: throw GoogleOAuthException("Invalid state token")

            if (!oauthState.isValid()) {
                throw GoogleOAuthException("State token has expired or already been used")
            }

            oauthState.markUsed()
            oauthStateRepository.save(oauthState)
        }
        transactionTemplate?.execute { consumeUnit() } ?: consumeUnit()
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

    /**
     * Parses a Google JSON error body into a [GoogleOAuthException] with a structured message.
     *
     * The message format is `google_error:<error_code>` when Google provides an `error` field,
     * optionally followed by ` — <error_description>`. When the JSON body lacks an `error` field
     * or cannot be parsed at all, the HTTP status code is included (e.g., `google_error:http_500`).
     *
     * @param body the raw response body string (may be empty or non-JSON)
     * @param statusCode the HTTP status code
     * @return a [GoogleOAuthException] that will be caught by the controller's `auth_failed` branch
     */
    private fun parseGoogleError(body: String, statusCode: Int): GoogleOAuthException {
        return try {
            val node = objectMapper.readTree(body)
            val error = node.get("error")?.asText()
            if (error != null) {
                val errorDescription = node.get("error_description")?.asText()
                val message = buildString {
                    append("google_error:$error")
                    if (!errorDescription.isNullOrBlank()) append(" — $errorDescription")
                }
                GoogleOAuthException(message)
            } else {
                GoogleOAuthException("google_error:http_$statusCode")
            }
        } catch (e: Exception) {
            GoogleOAuthException("google_error:http_$statusCode — unparseable response body")
        }
    }

    /**
     * Runs [action] with at most one retry (marker: GOOGLE_OAUTH_RETRY), and ONLY for
     * connect-phase/transient failures: [WebClientRequestException] whose cause chain
     * contains [ConnectException] (this includes io.netty.channel.ConnectTimeoutException,
     * which extends it) or [PrematureCloseException].
     *
     * Conservative by design: once ANY HTTP response has been received the request is never
     * retried — response errors surface as GoogleOAuthException/WebClientResponseException,
     * which are not retryable here, so the single-use authorization code can never be
     * replayed against Google.
     */
    private fun <T> withConnectRetry(action: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return action()
            } catch (e: RuntimeException) {
                if (attempt >= MAX_HTTP_ATTEMPTS || !isTransientConnectFailure(e)) throw e
                logger.warn(
                    "GOOGLE_OAUTH_RETRY: transient connect failure on attempt {}/{} ({}), retrying in {}ms",
                    attempt, MAX_HTTP_ATTEMPTS, e.javaClass.simpleName, RETRY_BACKOFF_MS, e
                )
                Thread.sleep(RETRY_BACKOFF_MS)
                attempt++
            }
        }
    }

    private fun isTransientConnectFailure(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            if (current is ConnectException || current is PrematureCloseException) return true
            current = current.cause
        }
        return false
    }

    private fun exchangeCodeForTokens(
        code: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String
    ): GoogleTokenResponse {
        val response = withConnectRetry {
            webClient.post()
                .uri(authConfig.oauth2.google.tokenUrl)
                .body(BodyInserters.fromFormData("code", code)
                    .with("client_id", clientId)
                    .with("client_secret", clientSecret)
                    .with("redirect_uri", redirectUri)
                    .with("grant_type", "authorization_code"))
                .retrieve()
                .onStatus({ it.isError }) { resp ->
                    resp.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .map { body -> throw parseGoogleError(body, resp.statusCode().value()) }
                }
                .bodyToMono(Map::class.java)
                .block()
        } ?: throw GoogleOAuthException("Failed to exchange authorization code")

        val accessToken = response["access_token"] as? String
            ?: throw GoogleOAuthException("No access token in response")

        return GoogleTokenResponse(accessToken = accessToken)
    }

    private fun fetchUserProfile(accessToken: String): GoogleUserProfile {
        val response = withConnectRetry {
            webClient.get()
                .uri(authConfig.oauth2.google.userinfoUrl)
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .onStatus({ it.isError }) { resp ->
                    resp.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .map { body -> throw parseGoogleError(body, resp.statusCode().value()) }
                }
                .bodyToMono(Map::class.java)
                .block()
        } ?: throw GoogleOAuthException("Failed to fetch user profile")

        return GoogleUserProfile(
            sub = response["sub"] as? String ?: throw GoogleOAuthException("No sub in Google profile"),
            email = response["email"] as? String ?: throw GoogleOAuthException("No email in Google profile"),
            name = response["name"] as? String,
            picture = response["picture"] as? String
        )
    }
}
