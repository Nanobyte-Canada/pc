# Design: UAT Email/Password Login for Testing Agents

**Date:** 2026-07-10
**Status:** Draft
**Approach:** Conditional email/password login (Approach 1)

---

## Problem

During SDLC lifecycle testing, browser automation agents (Playwright/Selenium) cannot test the user interface because:
1. Google OAuth blocks automated logins with bot detection (CAPTCHA, phone verification, device checks)
2. No alternative login method exists in the UI

**Goal:** Provide testing agents with static credentials that grant admin access to the UI, while keeping changes isolated to UAT environment only.

---

## Current State

### Backend (Already Implemented)
- `AuthenticationService.login()` accepts `LoginRequest(email, password)` and returns `AuthTokens` — **this method exists but is not exposed via any endpoint**
- `LoginRequest` DTO with email/password validation
- `PasswordService` with Argon2id hashing and password strength validation
- `User` entity with `passwordHash`, `emailVerified`, `failedLoginAttempts`, `lockedUntil`
- Role system: `USER` and `ADMIN` roles with frontend route guards

### Frontend (Needs Changes)
- `LoginPage.tsx` renders only "Continue with Google" button
- `authService.ts` has `initiateGoogleLogin()` but no `login()` function
- Vite supports environment variables via `VITE_*` prefix

### Security Config
- `/auth/login` is not currently in `permitAll()` — falls under `anyRequest().authenticated()`
- CSRF exemptions exist for `/auth/refresh`, `/auth/google`, `/auth/google/callback`

---

## Design

### 1. Backend: Add Login Endpoint

**File:** `backend/portfolio/src/main/kotlin/com/portfolio/auth/controller/AuthController.kt`

Add `POST /auth/login` endpoint:

```kotlin
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

**File:** `backend/portfolio/src/main/kotlin/com/portfolio/auth/config/SecurityConfig.kt`

Add to `permitAll()`:
```kotlin
.requestMatchers(
    "/auth/refresh",
    "/auth/login",        // ADD THIS
    "/auth/google",
    "/auth/google/callback",
    "/auth/logout",
).permitAll()
```

Add to CSRF exemptions:
```kotlin
.ignoringRequestMatchers(
    "/auth/refresh",
    "/auth/login",        // ADD THIS
    "/auth/google",
    "/auth/google/callback",
    "/health",
    "/ready",
    "/actuator/**",
    "/api/**"
)
```

### 2. Frontend: Add Login Function

**File:** `frontend/src/services/authService.ts`

Add `login()` function:

```typescript
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

### 3. Frontend: Conditional Login Form

**File:** `frontend/src/pages/auth/LoginPage.tsx`

Add environment-based conditional rendering:

```typescript
const AUTH_METHOD = import.meta.env.VITE_AUTH_METHOD || 'google';

// In render:
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

### 4. Environment Configuration

**File:** `deploy/uat/docker-compose.yml` (frontend service)

Add environment variable:
```yaml
frontend:
  image: ghcr.io/nanobyte-canada/portfolio-frontend-uat:${IMAGE_TAG}
  container_name: uat-frontend
  environment:
    - VITE_AUTH_METHOD=both    # Show both Google and email/password
```

**File:** `deploy/prod/docker-compose.yml` (frontend service)

Add explicit Google-only (or omit to default):
```yaml
frontend:
  image: ghcr.io/nanobyte-canada/portfolio-frontend:${IMAGE_TAG}
  container_name: prod-frontend
  environment:
    - VITE_AUTH_METHOD=google  # Google only (default)
```

### 5. Test User Seeding

**Option A: SQL Migration (Recommended)**

Create `V30__uat_test_admin_user.sql` that:
1. Inserts a test user with email from Vault env var
2. Hashes password using Argon2id (pre-computed hash stored in Vault)
3. Assigns ADMIN role
4. Sets `email_verified = true`

```sql
-- Only run in UAT environment
-- Credentials sourced from: vault:secret/data/uat/test-admin

INSERT INTO users (email, password_hash, name, email_verified, email_verified_at, status, created_at, updated_at)
VALUES (
    '${TEST_ADMIN_EMAIL}',           -- From Vault
    '${TEST_ADMIN_PASSWORD_HASH}',   -- From Vault (Argon2id hash)
    'Test Admin',
    true,
    NOW(),
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;

-- Assign ADMIN role
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = '${TEST_ADMIN_EMAIL}'
  AND r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
```

**Option B: Init Script**

Add a startup script that runs after DB migration to seed the test user if not exists.

### 6. Vault Integration

Store test credentials in Vault at path `secret/data/uat/test-admin`:
```json
{
  "test_admin_email": "test-admin@yourdomain.com",
  "test_admin_password": "YourSecurePassword123!",
  "test_admin_password_hash": "$argon2id$v=19$m=65536,t=3,p=4$..."
}
```

Docker-compose reads from Vault and injects as environment variables.

---

## Security Considerations

1. **UAT Only:** `VITE_AUTH_METHOD` defaults to `google` — prod shows email/password form only if explicitly configured
2. **Backend Endpoint:** `/auth/login` is always available in code but only useful if:
   - A user exists with a password hash (Google-only users have `password_hash = null`)
   - The frontend shows the form
3. **No Production Impact:** Google-only users cannot log in via email/password (no password hash stored)
4. **Test User Isolation:** Test admin user is seeded only in UAT database, not prod
5. **Password Hashing:** Test user password uses same Argon2id hashing as any user
6. **Rate Limiting:** Existing `handleFailedLogin()` logic applies — account locks after N failed attempts

---

## Files to Modify

| File | Change |
|------|--------|
| `backend/.../auth/controller/AuthController.kt` | Add `POST /auth/login` endpoint |
| `backend/.../auth/config/SecurityConfig.kt` | Add `/auth/login` to permitAll and CSRF exemptions |
| `frontend/src/services/authService.ts` | Add `login()` function |
| `frontend/src/pages/auth/LoginPage.tsx` | Add conditional email/password form |
| `frontend/src/pages/auth/AuthPages.css` | Add styles for email/password form |
| `deploy/uat/docker-compose.yml` | Add `VITE_AUTH_METHOD=both` env var |
| `deploy/prod/docker-compose.yml` | Add `VITE_AUTH_METHOD=google` env var |
| `backend/.../db/migration/V30__uat_test_admin_user.sql` | Seed test admin user |

---

## Testing Plan

1. **Local Dev:** `VITE_AUTH_METHOD=google` → only Google button shows
2. **UAT:** `VITE_AUTH_METHOD=both` → both Google and email/password forms show
3. **Prod:** `VITE_AUTH_METHOD=google` → only Google button shows
4. **UAT Login:** Enter test admin credentials → redirected to dashboard with admin access
5. **UAT Login (invalid):** Enter wrong password → error message, failed attempt logged
6. **Prod Login:** Email/password form not visible → cannot attempt email login

---

## Success Criteria

- [ ] Testing agent can log in via email/password in UAT
- [ ] Test user has ADMIN role and can access all UI features
- [ ] Email/password form is hidden in production
- [ ] No changes to Google OAuth flow for real users
- [ ] Test credentials stored in Vault, not in code
- [ ] Account lockout logic applies to test user (prevents brute force)
