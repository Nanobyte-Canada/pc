package com.portfolio.auth.service

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.config.CorsConfig
import com.portfolio.auth.config.GoogleOAuthConfig
import com.portfolio.auth.config.OAuth2Config
import com.portfolio.auth.entity.OAuthState
import com.portfolio.auth.entity.Role
import com.portfolio.auth.entity.User
import com.portfolio.auth.entity.UserIdentity
import com.portfolio.auth.repository.OAuthStateRepository
import com.portfolio.auth.repository.RoleRepository
import com.portfolio.auth.repository.UserIdentityRepository
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.repository.UserRoleRepository
import com.portfolio.auth.security.SecureTokenGenerator
import com.portfolio.auth.security.TokenPair
import io.mockk.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoogleOAuthServiceTest {

    private lateinit var service: GoogleOAuthService
    private lateinit var oauthStateRepository: OAuthStateRepository
    private lateinit var userIdentityRepository: UserIdentityRepository
    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var userRoleRepository: UserRoleRepository
    private lateinit var secureTokenGenerator: SecureTokenGenerator
    private lateinit var auditService: AuditService
    private lateinit var authConfig: AuthConfig
    private lateinit var mockWebServer: MockWebServer
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setup() {
        oauthStateRepository = mockk()
        userIdentityRepository = mockk()
        userRepository = mockk()
        roleRepository = mockk()
        userRoleRepository = mockk()
        secureTokenGenerator = mockk()
        auditService = mockk()
        authConfig = mockk()

        mockWebServer = MockWebServer()
        mockWebServer.start()
        webClient = WebClient.builder().build()

        val mockBaseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val googleConfig = GoogleOAuthConfig().apply {
            clientId = "test-client-id"
            clientSecret = "test-client-secret"
            tokenUrl = "$mockBaseUrl/token"
            userinfoUrl = "$mockBaseUrl/userinfo"
        }
        val oauth2Config = OAuth2Config().apply {
            google = googleConfig
        }
        val corsConfig = CorsConfig().apply {
            allowedOrigins = "http://localhost:3000"
        }
        every { authConfig.oauth2 } returns oauth2Config
        every { authConfig.cors } returns corsConfig

        service = GoogleOAuthService(
            oauthStateRepository = oauthStateRepository,
            userIdentityRepository = userIdentityRepository,
            userRepository = userRepository,
            roleRepository = roleRepository,
            userRoleRepository = userRoleRepository,
            secureTokenGenerator = secureTokenGenerator,
            auditService = auditService,
            authConfig = authConfig,
            webClient = webClient
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `initiateGoogleLogin generates state and returns authorization URL`() {
        val tokenPair = TokenPair(token = "raw-state-token", hash = "hashed-state-token")
        every { secureTokenGenerator.generateStateToken() } returns tokenPair
        every { oauthStateRepository.save(any()) } answers { firstArg() }

        val authUrl = service.initiateGoogleLogin()

        assert(authUrl.contains("https://accounts.google.com/o/oauth2/v2/auth"))
        assert(authUrl.contains("client_id=test-client-id"))
        assert(authUrl.contains("redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fauth%2Fgoogle%2Fcallback"))
        assert(authUrl.contains("response_type=code"))
        assert(authUrl.contains("scope=openid+email+profile"))
        assert(authUrl.contains("state=raw-state-token"))
        assert(authUrl.contains("access_type=offline"))
        assert(authUrl.contains("prompt=consent"))

        verify {
            secureTokenGenerator.generateStateToken()
            oauthStateRepository.save(match { state ->
                state.stateHash == "hashed-state-token" &&
                state.provider == UserIdentity.PROVIDER_GOOGLE
            })
        }
    }

    @Test
    fun `handleCallback exchanges code for tokens and fetches user profile`() {
        val stateHash = "hashed-state"
        val oauthState = OAuthState(
            id = 1L,
            stateHash = stateHash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().plusMinutes(10)
        )

        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns oauthState
        every { oauthStateRepository.save(any()) } answers { firstArg() }

        // Mock token exchange response
        val tokenResponse = """
            {
                "access_token": "ya29.test-access-token",
                "expires_in": 3600,
                "token_type": "Bearer",
                "scope": "openid email profile",
                "refresh_token": "1//test-refresh-token"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse()
            .setBody(tokenResponse)
            .addHeader("Content-Type", "application/json"))

        // Mock userinfo response
        val userinfoResponse = """
            {
                "sub": "google-user-123",
                "email": "test@example.com",
                "name": "Test User",
                "picture": "https://example.com/avatar.jpg"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse()
            .setBody(userinfoResponse)
            .addHeader("Content-Type", "application/json"))

        val profile = service.handleCallback(code = "auth-code", state = "raw-state")

        assertEquals("google-user-123", profile.sub)
        assertEquals("test@example.com", profile.email)
        assertEquals("Test User", profile.name)
        assertEquals("https://example.com/avatar.jpg", profile.picture)

        verify { oauthStateRepository.save(match { it.usedAt != null }) }
    }

    @Test
    fun `handleCallback throws on invalid state`() {
        val stateHash = "hashed-state"
        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns null

        assertFailsWith<GoogleOAuthException> {
            service.handleCallback(code = "auth-code", state = "raw-state")
        }
    }

    @Test
    fun `handleCallback throws on expired state`() {
        val stateHash = "hashed-state"
        val expiredState = OAuthState(
            id = 1L,
            stateHash = stateHash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().minusMinutes(10)
        )

        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns expiredState

        assertFailsWith<GoogleOAuthException> {
            service.handleCallback(code = "auth-code", state = "raw-state")
        }
    }

    @Test
    fun `handleCallback throws on already-used state`() {
        val stateHash = "hashed-state"
        val usedState = OAuthState(
            id = 1L,
            stateHash = stateHash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().plusMinutes(10),
            usedAt = OffsetDateTime.now().minusMinutes(1)
        )

        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns usedState

        assertFailsWith<GoogleOAuthException> {
            service.handleCallback(code = "auth-code", state = "raw-state")
        }
    }

    @Test
    fun `findOrCreateUser returns existing user when identity exists`() {
        val profile = GoogleUserProfile(
            sub = "google-123",
            email = "test@example.com",
            name = "Test User",
            picture = "https://example.com/avatar.jpg"
        )

        val existingUser = User(
            id = 5L,
            email = "test@example.com",
            emailVerified = true
        )

        val existingIdentity = UserIdentity(
            id = 10L,
            user = existingUser,
            provider = UserIdentity.PROVIDER_GOOGLE,
            providerUserId = "google-123",
            providerEmail = "old@example.com"
        )

        every { userIdentityRepository.findByProviderAndProviderUserIdWithUser(
            UserIdentity.PROVIDER_GOOGLE,
            "google-123"
        ) } returns existingIdentity
        every { userIdentityRepository.save(any()) } answers { firstArg() }
        every { userRepository.save(any()) } answers { firstArg() }

        val result = service.findOrCreateUser(profile, "192.168.1.1")

        assertEquals(existingUser.id, result.id)
        verify {
            userIdentityRepository.save(match { identity ->
                identity.providerEmail == "test@example.com" &&
                identity.providerName == "Test User" &&
                identity.providerAvatarUrl == "https://example.com/avatar.jpg"
            })
        }
        verify { userRepository.save(match { it.id == existingUser.id }) }
    }

    @Test
    fun `findOrCreateUser links identity when user with same email exists`() {
        val profile = GoogleUserProfile(
            sub = "google-123",
            email = "test@example.com",
            name = "Test User",
            picture = "https://example.com/avatar.jpg"
        )

        val existingUser = User(
            id = 5L,
            email = "test@example.com",
            emailVerified = true,
            passwordHash = "existing-hash"
        )

        every { userIdentityRepository.findByProviderAndProviderUserIdWithUser(any(), any()) } returns null
        every { userRepository.findByEmail("test@example.com") } returns existingUser
        every { userIdentityRepository.save(any()) } answers { firstArg() }
        every { userRepository.save(any()) } answers { firstArg() }
        every { auditService.logOAuthLink(any(), any(), any(), any()) } just Runs

        val result = service.findOrCreateUser(profile, "192.168.1.1")

        assertEquals(existingUser.id, result.id)
        verify {
            userIdentityRepository.save(match { identity ->
                identity.user == existingUser &&
                identity.provider == UserIdentity.PROVIDER_GOOGLE &&
                identity.providerUserId == "google-123"
            })
            userRepository.save(match { it.emailVerified })
            auditService.logOAuthLink(existingUser, UserIdentity.PROVIDER_GOOGLE, "192.168.1.1", null)
        }
    }

    @Test
    fun `findOrCreateUser creates new user when no match found`() {
        val profile = GoogleUserProfile(
            sub = "google-123",
            email = "newuser@example.com",
            name = "New User",
            picture = "https://example.com/avatar.jpg"
        )

        val userRole = Role(id = 1L, name = Role.USER)

        every { userIdentityRepository.findByProviderAndProviderUserIdWithUser(any(), any()) } returns null
        every { userRepository.findByEmail("newuser@example.com") } returns null
        every { roleRepository.findByName(Role.USER) } returns userRole
        every { userRepository.save(any()) } answers {
            val u = firstArg<User>()
            u
        }
        every { userIdentityRepository.save(any()) } answers { firstArg() }
        every { userRoleRepository.save(any()) } answers { firstArg() }
        every { auditService.logSignup(any(), any(), any()) } just Runs

        val result = service.findOrCreateUser(profile, "192.168.1.1")

        assertNotNull(result)
        assertEquals("newuser@example.com", result.email)
        verify {
            userRepository.save(match { user ->
                user.email == "newuser@example.com" &&
                user.emailVerified == true &&
                user.passwordHash == null &&
                user.name == "New User" &&
                user.avatarUrl == "https://example.com/avatar.jpg"
            })
            userIdentityRepository.save(match { identity ->
                identity.provider == UserIdentity.PROVIDER_GOOGLE &&
                identity.providerUserId == "google-123"
            })
            userRoleRepository.save(match { ur ->
                ur.role == userRole
            })
            auditService.logSignup(any(), "192.168.1.1", null)
        }
    }

    // ====================
    // Error handling tests
    // ====================

    @Test
    fun `handleCallback throws GoogleOAuthException with google_error when token endpoint returns 400 redirect_uri_mismatch`() {
        val stateHash = "hashed-state"
        val oauthState = OAuthState(
            id = 1L,
            stateHash = stateHash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().plusMinutes(10)
        )

        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns oauthState
        every { oauthStateRepository.save(any()) } answers { firstArg() }

        // Google returns a structured error for redirect_uri_mismatch
        val errorBody = """
            {
                "error": "redirect_uri_mismatch",
                "error_description": "Bad Request"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(400)
            .setBody(errorBody)
            .addHeader("Content-Type", "application/json"))

        val exception = assertFailsWith<GoogleOAuthException> {
            service.handleCallback(code = "auth-code", state = "raw-state")
        }

        assertTrue(exception.message!!.contains("google_error:redirect_uri_mismatch"))
        assertTrue(exception.message!!.contains("Bad Request"))
    }

    @Test
    fun `handleCallback throws GoogleOAuthException when token endpoint returns 401 invalid_client`() {
        val stateHash = "hashed-state"
        val oauthState = OAuthState(
            id = 1L,
            stateHash = stateHash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().plusMinutes(10)
        )

        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns oauthState
        every { oauthStateRepository.save(any()) } answers { firstArg() }

        val errorBody = """
            {
                "error": "invalid_client",
                "error_description": "The OAuth client was not found."
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody(errorBody)
            .addHeader("Content-Type", "application/json"))

        val exception = assertFailsWith<GoogleOAuthException> {
            service.handleCallback(code = "auth-code", state = "raw-state")
        }

        assertTrue(exception.message!!.contains("google_error:invalid_client"))
    }

    @Test
    fun `handleCallback throws GoogleOAuthException when token endpoint returns 400 invalid_grant`() {
        val stateHash = "hashed-state"
        val oauthState = OAuthState(
            id = 1L,
            stateHash = stateHash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().plusMinutes(10)
        )

        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns oauthState
        every { oauthStateRepository.save(any()) } answers { firstArg() }

        val errorBody = """
            {
                "error": "invalid_grant",
                "error_description": "Malformed auth code."
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(400)
            .setBody(errorBody)
            .addHeader("Content-Type", "application/json"))

        val exception = assertFailsWith<GoogleOAuthException> {
            service.handleCallback(code = "invalid-code", state = "raw-state")
        }

        assertTrue(exception.message!!.contains("google_error:invalid_grant"))
        assertTrue(exception.message!!.contains("Malformed auth code"))
    }

    @Test
    fun `handleCallback throws GoogleOAuthException when token endpoint returns 500 with no JSON body`() {
        val stateHash = "hashed-state"
        val oauthState = OAuthState(
            id = 1L,
            stateHash = stateHash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().plusMinutes(10)
        )

        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns oauthState
        every { oauthStateRepository.save(any()) } answers { firstArg() }

        // 500 with no body — onStatus still fires, bodyToMono returns empty
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json"))

        val exception = assertFailsWith<GoogleOAuthException> {
            service.handleCallback(code = "auth-code", state = "raw-state")
        }

        // Falls back to "unknown" when no error field is present
        assertTrue(exception.message!!.contains("google_error:unknown"))
    }

    @Test
    fun `handleCallback throws GoogleOAuthException when userinfo endpoint returns 401`() {
        val stateHash = "hashed-state"
        val oauthState = OAuthState(
            id = 1L,
            stateHash = stateHash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().plusMinutes(10)
        )

        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns oauthState
        every { oauthStateRepository.save(any()) } answers { firstArg() }

        // First request: token exchange succeeds
        val tokenResponse = """
            {
                "access_token": "ya29.test-token",
                "expires_in": 3600,
                "token_type": "Bearer"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse()
            .setBody(tokenResponse)
            .addHeader("Content-Type", "application/json"))

        // Second request: userinfo returns 401
        val userinfoError = """
            {
                "error": "invalid_token",
                "error_description": "Invalid Credentials"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody(userinfoError)
            .addHeader("Content-Type", "application/json"))

        val exception = assertFailsWith<GoogleOAuthException> {
            service.handleCallback(code = "auth-code", state = "raw-state")
        }

        assertTrue(exception.message!!.contains("google_error:invalid_token"))
    }

    @Test
    fun `handleCallback throws GoogleOAuthException when token endpoint returns error without description`() {
        val stateHash = "hashed-state"
        val oauthState = OAuthState(
            id = 1L,
            stateHash = stateHash,
            provider = UserIdentity.PROVIDER_GOOGLE,
            expiresAt = OffsetDateTime.now().plusMinutes(10)
        )

        every { secureTokenGenerator.hashToken("raw-state") } returns stateHash
        every { oauthStateRepository.findByStateHash(stateHash) } returns oauthState
        every { oauthStateRepository.save(any()) } answers { firstArg() }

        // Error without error_description field
        val errorBody = """
            {
                "error": "invalid_request"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(400)
            .setBody(errorBody)
            .addHeader("Content-Type", "application/json"))

        val exception = assertFailsWith<GoogleOAuthException> {
            service.handleCallback(code = "auth-code", state = "raw-state")
        }

        // Should contain just google_error:invalid_request without a trailing " - "
        assertEquals("google_error:invalid_request", exception.message)
    }
}
