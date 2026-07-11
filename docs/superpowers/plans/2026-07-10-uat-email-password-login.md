# UAT Email/Password Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add conditional email/password login to UAT environment so browser automation agents can test the UI with static admin credentials.

**Architecture:** Expose existing `AuthenticationService.login()` via new `POST /auth/login` endpoint, conditionally render email/password form in frontend based on `VITE_AUTH_METHOD` env var, seed test admin user in UAT database.

**Tech Stack:** Kotlin/Spring Boot (backend), React/TypeScript/Vite (frontend), PostgreSQL (database), Flyway (migrations)

## Global Constraints

- Backend: Kotlin, Spring Boot 3.x, Gradle, Flyway migrations
- Frontend: React 18+, TypeScript, Vite, Zustand stores, CSS modules
- Testing: JUnit 5 + MockK (backend), Vitest + Testing Library (frontend)
- Database: PostgreSQL 16, Flyway versioned migrations
- Security: JWT tokens in HttpOnly cookies, CSRF via Spring Security, Argon2id password hashing

---

## File Structure

| File | Responsibility | Action |
|------|---------------|--------|
| `backend/.../auth/controller/AuthController.kt` | Add `POST /auth/login` endpoint | Modify |
| `backend/.../auth/config/SecurityConfig.kt` | Allow `/auth/login` without auth, exempt from CSRF | Modify |
| `frontend/src/services/authService.ts` | Add `login()` function | Modify |
| `frontend/src/pages/auth/LoginPage.tsx` | Add conditional email/password form | Modify |
| `frontend/src/pages/auth/AuthPages.css` | Add styles for email/password form | Modify |
| `deploy/uat/docker-compose.yml` | Add `VITE_AUTH_METHOD=both` env var | Modify |
| `deploy/prod/docker-compose.yml` | Add `VITE_AUTH_METHOD=google` env var | Modify |
| `backend/.../db/migration/V30__uat_test_admin_user.sql` | Seed test admin user | Create |

---

## Tasks

### Task 1: Backend — Add Login Endpoint to AuthController

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/auth/controller/AuthController.kt`
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/auth/controller/AuthControllerTest.kt`

**Interfaces:**
- Consumes: `AuthenticationService.login(LoginRequest, ClientInfo)` (already exists)
- Produces: `POST /auth/login` endpoint returning `AuthResponse` with cookies

- [ ] **Step 1: Write the failing test**

```kotlin
// backend/portfolio/src/test/kotlin/com/portfolio/auth/controller/AuthControllerTest.kt
package com.portfolio.auth.controller

import com.portfolio.auth.dto.LoginRequest
import com.portfolio.auth.service.AuthTokens
import com.portfolio.auth.service.AuthenticationService
import com.portfolio.auth.service.ClientInfo
import com.portfolio.auth.entity.User
import com.portfolio.auth.entity.UserStatus
import com.portfolio.auth.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.bean.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockBean
    lateinit var authenticationService: AuthenticationService

    @MockBean
    lateinit var userRepository: UserRepository

    @Test
    fun `POST auth login returns tokens for valid credentials`() {
        // Arrange
        val loginRequest = LoginRequest(email = "test@example.com", password = "password123")
        val user = User(
            id = 1L,
            email = "test@example.com",
            emailVerified = true,
            status = UserStatus.ACTIVE,
            passwordHash = "hashed"
        )
        val authTokens = AuthTokens(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            user = user,
            roles = listOf("USER")
        )

        whenever(authenticationService.login(any(), any())).thenReturn(authTokens)
        whenever(userRepository.findRoleNamesByUserId(1L)).thenReturn(listOf("USER"))

        // Act & Assert
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginRequest)
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.id") { value(1) }
            jsonPath("$.user.email") { value("test@example.com") }
        }
    }

    @Test
    fun `POST auth login returns 401 for invalid credentials`() {
        // Arrange
        val loginRequest = LoginRequest(email = "test@example.com", password = "wrong")
        whenever(authenticationService.login(any(), any()))
            .thenThrow(com.portfolio.auth.exception.InvalidCredentialsException())

        // Act & Assert
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginRequest)
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.portfolio.auth.controller.AuthControllerTest"`
Expected: FAIL with "No such method: POST /auth/login"

- [ ] **Step 3: Write minimal implementation**

```kotlin
// backend/portfolio/src/main/kotlin/com/portfolio/auth/controller/AuthController.kt
// Add after the googleCallback method (around line 96)

@PostMapping("/login")
fun login(
    @Valid @RequestBody request: LoginRequest,
    httpRequest: HttpServletRequest,
    httpResponse: HttpServletResponse
): ResponseEntity<AuthResponse> {
    val clientInfo = extractClientInfo(httpRequest)
    val authTokens = authenticationService.login(request, clientInfo)
    val roles = userRepository.findRoleNamesByUserId(authTokens.user.id)

    setAuthCookies(httpResponse, authTokens.accessToken, authTokens.refreshToken)

    val userResponse = UserResponse.from(authTokens.user, roles)
    return ResponseEntity.ok(AuthResponse(user = userResponse))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.portfolio.auth.controller.AuthControllerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/auth/controller/AuthController.kt \
        backend/portfolio/src/test/kotlin/com/portfolio/auth/controller/AuthControllerTest.kt
git commit -m "feat(auth): add POST /auth/login endpoint"
```

---

### Task 2: Backend — Update Security Config

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/auth/config/SecurityConfig.kt`

**Interfaces:**
- Consumes: None
- Produces: `/auth/login` accessible without authentication, CSRF exempt

- [ ] **Step 1: Add /auth/login to permitAll**

```kotlin
// backend/portfolio/src/main/kotlin/com/portfolio/auth/config/SecurityConfig.kt
// Line 58-71: Update requestMatchers

.authorizeHttpRequests { auth ->
    auth
        .requestMatchers("/health", "/ready", "/api/v1/version").permitAll()
        .requestMatchers(
            "/auth/refresh",
            "/auth/login",        // ADD THIS LINE
            "/auth/google",
            "/auth/google/callback",
            "/auth/logout",
        ).permitAll()
        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
        .requestMatchers("/api/v1/admin/**", "/admin/**").hasRole("ADMIN")
        .requestMatchers("/auth/me", "/auth/profile").authenticated()
        .requestMatchers("/api/**").authenticated()
        .anyRequest().authenticated()
}
```

- [ ] **Step 2: Add /auth/login to CSRF exemptions**

```kotlin
// backend/portfolio/src/main/kotlin/com/portfolio/auth/config/SecurityConfig.kt
// Line 39-47: Update ignoringRequestMatchers

.csrf { csrf ->
    csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .csrfTokenRequestHandler(csrfTokenRequestHandler)
        .ignoringRequestMatchers(
            "/auth/refresh",
            "/auth/login",        // ADD THIS LINE
            "/auth/google",
            "/auth/google/callback",
            "/health",
            "/ready",
            "/actuator/**",
            "/api/**"
        )
}
```

- [ ] **Step 3: Run existing tests to verify no regressions**

Run: `cd backend && ./gradlew test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/auth/config/SecurityConfig.kt
git commit -m "feat(auth): allow /auth/login without auth, exempt from CSRF"
```

---

### Task 3: Frontend — Add Login Function to AuthService

**Files:**
- Modify: `frontend/src/services/authService.ts`
- Test: `frontend/src/services/__tests__/authService.test.ts`

**Interfaces:**
- Consumes: `POST /auth/login` endpoint (Task 1)
- Produces: `login(email, password)` function returning `Promise<AuthResponse>`

- [ ] **Step 1: Write the failing test**

```typescript
// frontend/src/services/__tests__/authService.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { login } from '../authService';
import { apiFetch } from '../api';
import { useAuthStore } from '../../stores/authStore';

vi.mock('../api');
vi.mock('../../stores/authStore');

describe('authService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('login', () => {
    it('calls /auth/login with email and password', async () => {
      const mockResponse = {
        ok: true,
        json: vi.fn().mockResolvedValue({
          user: { id: 1, email: 'test@example.com', roles: ['ADMIN'] }
        })
      };
      vi.mocked(apiFetch).mockResolvedValue(mockResponse as any);
      vi.mocked(useAuthStore.getState).mockReturnValue({
        setUser: vi.fn()
      } as any);

      await login('test@example.com', 'password123');

      expect(apiFetch).toHaveBeenCalledWith('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email: 'test@example.com', password: 'password123' })
      });
    });

    it('throws AuthError on invalid credentials', async () => {
      const mockResponse = {
        ok: false,
        json: vi.fn().mockResolvedValue({
          error: 'invalid_credentials',
          message: 'Invalid email or password'
        })
      };
      vi.mocked(apiFetch).mockResolvedValue(mockResponse as any);

      await expect(login('test@example.com', 'wrong')).rejects.toThrow('Invalid email or password');
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/services/__tests__/authService.test.ts`
Expected: FAIL with "login is not a function"

- [ ] **Step 3: Write minimal implementation**

```typescript
// frontend/src/services/authService.ts
// Add after initiateGoogleLogin function (around line 62)

export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await apiFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    await handleAuthError(response);
  }

  const data: AuthResponse = await response.json();
  useAuthStore.getState().setUser(data.user);
  return data;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/services/__tests__/authService.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/services/authService.ts frontend/src/services/__tests__/authService.test.ts
git commit -m "feat(auth): add login() function to authService"
```

---

### Task 4: Frontend — Add Conditional Email/Password Form

**Files:**
- Modify: `frontend/src/pages/auth/LoginPage.tsx`
- Modify: `frontend/src/pages/auth/AuthPages.css`
- Test: `frontend/src/pages/auth/__tests__/LoginPage.test.tsx`

**Interfaces:**
- Consumes: `login(email, password)` function (Task 3), `VITE_AUTH_METHOD` env var
- Produces: Conditional email/password form UI

- [ ] **Step 1: Write the failing test**

```typescript
// frontend/src/pages/auth/__tests__/LoginPage.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LoginPage } from '../LoginPage';
import * as authService from '../../../services/authService';

vi.mock('../../../services/authService');
vi.mock('../../../stores/authStore', () => ({
  useIsAuthenticated: () => false,
}));

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset env var
    import.meta.env.VITE_AUTH_METHOD = 'both';
  });

  it('shows email/password form when VITE_AUTH_METHOD is "both"', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByPlaceholderText(/email/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in with email/i })).toBeInTheDocument();
  });

  it('hides email/password form when VITE_AUTH_METHOD is "google"', () => {
    import.meta.env.VITE_AUTH_METHOD = 'google';

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.queryByPlaceholderText(/email/i)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/password/i)).not.toBeInTheDocument();
  });

  it('calls login function on form submit', async () => {
    vi.mocked(authService.login).mockResolvedValue({
      user: { id: 1, email: 'test@example.com', roles: ['ADMIN'] }
    } as any);

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByPlaceholderText(/email/i), {
      target: { value: 'test@example.com' }
    });
    fireEvent.change(screen.getByPlaceholderText(/password/i), {
      target: { value: 'password123' }
    });
    fireEvent.click(screen.getByRole('button', { name: /sign in with email/i }));

    await waitFor(() => {
      expect(authService.login).toHaveBeenCalledWith('test@example.com', 'password123');
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/pages/auth/__tests__/LoginPage.test.tsx`
Expected: FAIL with "Unable to find placeholder text: /email/i"

- [ ] **Step 3: Write minimal implementation**

```typescript
// frontend/src/pages/auth/LoginPage.tsx
// Update imports at line 3

import { initiateGoogleLogin, login } from '../../services/authService';

// Add after ERROR_MESSAGES (around line 10)

const AUTH_METHOD = import.meta.env.VITE_AUTH_METHOD || 'google';

// Add state variables after existing state (around line 16)

const [email, setEmail] = useState('');
const [password, setPassword] = useState('');
const [loginError, setLoginError] = useState<string | null>(null);
const [isLoggingIn, setIsLoggingIn] = useState(false);

// Add handler function after handleGoogleLogin (around line 33)

const handleEmailLogin = async (e: React.FormEvent) => {
  e.preventDefault();
  setLoginError(null);
  setIsLoggingIn(true);

  try {
    await login(email, password);
    navigate('/', { replace: true });
  } catch (err: any) {
    setLoginError(err.message || 'Login failed. Please try again.');
  } finally {
    setIsLoggingIn(false);
  }
};

// Update JSX after Google button (around line 86)

            <button className="login-google-btn" onClick={handleGoogleLogin}>
              {/* ... existing Google button content ... */}
            </button>

            {AUTH_METHOD !== 'google' && (
              <>
                <div className="login-divider">
                  <span>or</span>
                </div>
                <form onSubmit={handleEmailLogin} className="login-form">
                  <input
                    type="email"
                    placeholder="Email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    className="login-input"
                  />
                  <input
                    type="password"
                    placeholder="Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    className="login-input"
                  />
                  {loginError && <div className="login-error">{loginError}</div>}
                  <button type="submit" disabled={isLoggingIn} className="login-email-btn">
                    {isLoggingIn ? 'Signing in...' : 'Sign in with Email'}
                  </button>
                </form>
              </>
            )}
```

- [ ] **Step 4: Add CSS styles**

```css
/* frontend/src/pages/auth/AuthPages.css */
/* Add after existing .login-google-btn styles */

.login-divider {
  display: flex;
  align-items: center;
  margin: 1.5rem 0;
  color: var(--text-muted);
  font-size: 0.875rem;
}

.login-divider::before,
.login-divider::after {
  content: '';
  flex: 1;
  border-bottom: 1px solid var(--border);
}

.login-divider span {
  padding: 0 1rem;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.login-input {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid var(--border);
  border-radius: 0.5rem;
  background: var(--bg);
  color: var(--text);
  font-size: 1rem;
  transition: border-color 0.2s;
}

.login-input:focus {
  outline: none;
  border-color: var(--primary);
}

.login-input::placeholder {
  color: var(--text-muted);
}

.login-email-btn {
  width: 100%;
  padding: 0.75rem 1rem;
  border: none;
  border-radius: 0.5rem;
  background: var(--primary);
  color: white;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s;
}

.login-email-btn:hover:not(:disabled) {
  background: var(--primary-hover);
}

.login-email-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/pages/auth/__tests__/LoginPage.test.tsx`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/auth/LoginPage.tsx frontend/src/pages/auth/AuthPages.css \
        frontend/src/pages/auth/__tests__/LoginPage.test.tsx
git commit -m "feat(auth): add conditional email/password login form"
```

---

### Task 5: Environment Configuration

**Files:**
- Modify: `deploy/uat/docker-compose.yml`
- Modify: `deploy/prod/docker-compose.yml`

**Interfaces:**
- Consumes: `VITE_AUTH_METHOD` env var (Task 4)
- Produces: Environment-specific auth method configuration

- [ ] **Step 1: Add VITE_AUTH_METHOD to UAT docker-compose**

```yaml
# deploy/uat/docker-compose.yml
# Line 265-284: Update frontend service

  frontend:
    image: ghcr.io/nanobyte-canada/portfolio-frontend-uat:${IMAGE_TAG}
    container_name: uat-frontend
    ports:
      - "20000:80"
    environment:
      - VITE_AUTH_METHOD=both    # ADD THIS LINE
    depends_on:
      backend:
        condition: service_healthy
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512M
    logging:
      driver: json-file
      options:
        max-size: 50m
        max-file: "5"
    networks:
      - uat-network
```

- [ ] **Step 2: Add VITE_AUTH_METHOD to PROD docker-compose**

```yaml
# deploy/prod/docker-compose.yml
# Line 265-284: Update frontend service

  frontend:
    image: ghcr.io/nanobyte-canada/portfolio-frontend:${IMAGE_TAG}
    container_name: prod-frontend
    ports:
      - "10000:80"
    environment:
      - VITE_AUTH_METHOD=google  # ADD THIS LINE
    depends_on:
      backend:
        condition: service_healthy
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512M
    logging:
      driver: json-file
      options:
        max-size: 50m
        max-file: "5"
    networks:
      - prod-network
```

- [ ] **Step 3: Verify no syntax errors**

Run: `docker-compose -f deploy/uat/docker-compose.yml config && docker-compose -f deploy/prod/docker-compose.yml config`
Expected: Valid YAML output

- [ ] **Step 4: Commit**

```bash
git add deploy/uat/docker-compose.yml deploy/prod/docker-compose.yml
git commit -m "feat(auth): add VITE_AUTH_METHOD env vars to docker-compose"
```

---

### Task 6: Database Migration — Seed Test Admin User

**Files:**
- Create: `backend/portfolio/src/main/resources/db/migration/V30__uat_test_admin_user.sql`

**Interfaces:**
- Consumes: `TEST_ADMIN_EMAIL` and `TEST_ADMIN_PASSWORD_HASH` env vars from Vault
- Produces: Test admin user with ADMIN role in UAT database

- [ ] **Step 1: Create the migration file**

```sql
-- backend/portfolio/src/main/resources/db/migration/V30__uat_test_admin_user.sql
-- UAT Test Admin User Seeding
-- Credentials sourced from Vault: secret/data/uat/test-admin
-- This migration is idempotent and safe to run in any environment

-- Insert test admin user (only if not exists)
INSERT INTO users (email, password_hash, name, email_verified, email_verified_at, status, created_at, updated_at)
VALUES (
    COALESCE(current_setting('app.test_admin_email', true), 'test-admin@localhost'),
    COALESCE(current_setting('app.test_admin_password_hash', true), '$argon2id$v=19$m=65536,t=3,p=4$placeholder'),
    'Test Admin',
    true,
    NOW(),
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;

-- Assign ADMIN role to test user (only if not exists)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = COALESCE(current_setting('app.test_admin_email', true), 'test-admin@localhost')
  AND r.name = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
```

- [ ] **Step 2: Verify migration syntax**

Run: `cd backend && ./gradlew flywayValidate`
Expected: Migration is valid

- [ ] **Step 3: Commit**

```bash
git add backend/portfolio/src/main/resources/db/migration/V30__uat_test_admin_user.sql
git commit -m "feat(auth): add UAT test admin user migration"
```

---

### Task 7: Integration Testing

**Files:**
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/auth/integration/LoginIntegrationTest.kt`

**Interfaces:**
- Consumes: All previous tasks
- Produces: End-to-end login flow verification

- [ ] **Step 1: Write integration test**

```kotlin
// backend/portfolio/src/test/kotlin/com/portfolio/auth/integration/LoginIntegrationTest.kt
package com.portfolio.auth.integration

import com.portfolio.auth.dto.LoginRequest
import com.portfolio.auth.entity.User
import com.portfolio.auth.entity.UserStatus
import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.service.PasswordService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat

@SpringBootTest
@AutoConfigureMockMvc
class LoginIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var passwordService: PasswordService

    @BeforeEach
    fun setup() {
        // Clean up test user
        userRepository.findByEmail("integration-test@example.com")?.let {
            userRepository.delete(it)
        }
    }

    @Test
    fun `full login flow with valid credentials`() {
        // Arrange: Create test user
        val user = User(
            email = "integration-test@example.com",
            passwordHash = passwordService.hashPassword("SecurePass123!"),
            emailVerified = true,
            status = UserStatus.ACTIVE
        )
        val savedUser = userRepository.save(user)

        // Act & Assert: Login
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest(email = "integration-test@example.com", password = "SecurePass123!")
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.email") { value("integration-test@example.com") }
            jsonPath("$.user.roles") { isArray() }
        }

        // Cleanup
        userRepository.delete(savedUser)
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `cd backend && ./gradlew test --tests "com.portfolio.auth.integration.LoginIntegrationTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/portfolio/src/test/kotlin/com/portfolio/auth/integration/LoginIntegrationTest.kt
git commit -m "test(auth): add login integration test"
```

---

## Verification Checklist

After completing all tasks, verify:

- [ ] `POST /auth/login` endpoint works with valid credentials
- [ ] `POST /auth/login` returns 401 for invalid credentials
- [ ] `/auth/login` is accessible without authentication
- [ ] `/auth/login` is exempt from CSRF
- [ ] Frontend shows email/password form when `VITE_AUTH_METHOD=both`
- [ ] Frontend hides email/password form when `VITE_AUTH_METHOD=google`
- [ ] Test admin user exists in UAT database with ADMIN role
- [ ] All existing tests pass
- [ ] No changes to Google OAuth flow
- [ ] Prod environment shows only Google login

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-10-uat-email-password-login.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
