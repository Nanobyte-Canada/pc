# Code Quality Improvements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Sprint 1 (P0 quick wins) and Sprint 2 (caching, error quality, service decomposition, structured logging) from the code quality review spec.

**Architecture:** Layer-by-layer improvements — backend error handling infrastructure first, then frontend resilience, then performance/caching, then observability. Each task produces a self-contained, testable change.

**Tech Stack:** Kotlin/Spring Boot, React/TypeScript, Redis, Logback

**Spec:** `docs/superpowers/specs/2026-03-21-code-quality-architecture-review.md`

---

## File Structure

### New Files
| File | Responsibility |
|------|---------------|
| `backend/src/main/kotlin/com/portfolio/config/GlobalExceptionHandler.kt` | Centralized `@RestControllerAdvice` for all exception types |
| `backend/src/main/kotlin/com/portfolio/dto/response/ErrorResponse.kt` | Standardized API error response DTO |
| `backend/src/main/kotlin/com/portfolio/broker/service/DashboardCashService.kt` | Cash/buying power logic extracted from DashboardDataService |
| `backend/src/main/kotlin/com/portfolio/broker/service/DashboardExposureService.kt` | Sector/geography exposure logic extracted from DashboardDataService |
| `backend/src/main/kotlin/com/portfolio/broker/service/DashboardRiskService.kt` | Risk profile logic extracted from DashboardDataService |
| `backend/src/test/kotlin/com/portfolio/config/GlobalExceptionHandlerTest.kt` | Unit tests for global exception handler |
| `backend/src/main/kotlin/com/portfolio/service/CachedLookupService.kt` | Separate `@Service` for `@Cacheable` lookups (avoids Spring proxy self-invocation) |
| `frontend/src/components/ui/ErrorBoundary.tsx` | Reusable React Error Boundary component |
| `frontend/src/components/ui/ErrorBoundary.css` | Error boundary styling |
| `frontend/src/components/ui/ErrorBoundary.test.tsx` | Error boundary tests |

### Modified Files
| File | Change |
|------|--------|
| `backend/.../broker/controller/BrokerController.kt` | Remove debug-post endpoint (lines 51-66) |
| `backend/.../broker/service/DashboardDataService.kt` | Delegate to sub-services, add quality metadata to catch blocks |
| `backend/.../config/CacheConfig.kt` | Switch from ConcurrentMapCacheManager to RedisCacheManager |
| `backend/.../broker/service/ExchangeRateService.kt` | Replace ConcurrentHashMap with `@Cacheable` |
| `backend/src/main/resources/logback-spring.xml` | Add structured JSON logging for non-local profiles |
| `frontend/src/App.tsx` | Replace static imports with `React.lazy()` + `Suspense` |
| `frontend/src/components/dashboard/DashboardGrid.tsx` | Wrap widget zones with `ErrorBoundary` |

---

## Sprint 1: P0 Quick Wins

### Task 1: Remove Debug Endpoint from BrokerController

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt:51-66`

- [ ] **Step 1: Remove the debug-post endpoint**

Delete lines 51-66 (the comment and entire method):

```kotlin
// DELETE THIS BLOCK:
// Debug endpoint for testing POST requests
@PostMapping("/debug-post")
fun debugPost(request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
    val auth = SecurityContextHolder.getContext().authentication
    return ResponseEntity.ok(mapOf(
        "method" to request.method,
        "path" to request.servletPath,
        "authenticated" to (auth != null && auth.isAuthenticated),
        "principalType" to auth?.principal?.javaClass?.simpleName,
        "cookiesReceived" to (request.cookies?.map { it.name } ?: emptyList<String>()),
        "csrfHeader" to request.getHeader("X-XSRF-TOKEN"),
        "contentType" to request.contentType,
        "origin" to request.getHeader("Origin")
    ))
}
```

- [ ] **Step 2: Remove unused imports if any**

Check if `SecurityContextHolder` or `HttpServletRequest` are still used elsewhere in the file. If only used by the debug endpoint, remove the import.

- [ ] **Step 3: Verify build**

Run: `docker compose exec backend ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt
git commit -m "fix: remove debug-post endpoint exposing security context"
```

---

### Task 2: Create Standardized Error Response DTO

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/dto/response/ErrorResponse.kt`

- [ ] **Step 1: Create the ErrorResponse data class**

```kotlin
package com.portfolio.dto.response

import java.time.OffsetDateTime

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val errorCode: String? = null,
    val field: String? = null,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val path: String? = null
)
```

- [ ] **Step 2: Verify build**

Run: `docker compose exec backend ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/dto/response/ErrorResponse.kt
git commit -m "feat: add standardized ErrorResponse DTO for API errors"
```

---

### Task 3: Create Global Exception Handler

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/config/GlobalExceptionHandler.kt`
- Create: `backend/src/test/kotlin/com/portfolio/config/GlobalExceptionHandlerTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.portfolio.config

import com.portfolio.auth.exception.*
import com.portfolio.dto.response.ErrorResponse
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalExceptionHandlerTest {

    private lateinit var handler: GlobalExceptionHandler
    private lateinit var request: HttpServletRequest

    @BeforeEach
    fun setup() {
        handler = GlobalExceptionHandler()
        request = mockk()
        every { request.requestURI } returns "/api/v1/test"
    }

    @Test
    fun `handles AuthException with correct status and error code`() {
        val exception = InvalidCredentialsException()
        val response = handler.handleAuthException(exception, request)
        assertEquals(401, response.statusCode.value())
        val body = response.body!!
        assertEquals("INVALID_CREDENTIALS", body.errorCode)
        assertEquals("/api/v1/test", body.path)
    }

    @Test
    fun `handles AccountLockedException with 423 status`() {
        val exception = AccountLockedException(java.time.OffsetDateTime.now().plusMinutes(30))
        val response = handler.handleAuthException(exception, request)
        assertEquals(423, response.statusCode.value())
        assertEquals("ACCOUNT_LOCKED", response.body!!.errorCode)
    }

    @Test
    fun `handles EmailAlreadyExistsException with 409 status`() {
        val exception = EmailAlreadyExistsException()
        val response = handler.handleAuthException(exception, request)
        assertEquals(409, response.statusCode.value())
        assertEquals("EMAIL_EXISTS", response.body!!.errorCode)
    }

    @Test
    fun `handles IllegalArgumentException with 400 status`() {
        val exception = IllegalArgumentException("bad input")
        val response = handler.handleIllegalArgument(exception, request)
        assertEquals(400, response.statusCode.value())
        assertEquals("bad input", response.body!!.message)
    }

    @Test
    fun `handles generic Exception with 500 status`() {
        val exception = RuntimeException("unexpected")
        val response = handler.handleGenericException(exception, request)
        assertEquals(500, response.statusCode.value())
        assertEquals("INTERNAL_ERROR", response.body!!.errorCode)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `docker compose exec backend ./gradlew test --tests "com.portfolio.config.GlobalExceptionHandlerTest"`
Expected: FAIL — class does not exist

- [ ] **Step 3: Write the GlobalExceptionHandler**

```kotlin
package com.portfolio.config

import com.portfolio.auth.exception.*
import com.portfolio.dto.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AuthException::class)
    fun handleAuthException(
        ex: AuthException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val status = when (ex) {
            is InvalidCredentialsException -> HttpStatus.UNAUTHORIZED
            is AccountLockedException -> HttpStatus.LOCKED
            is EmailAlreadyExistsException -> HttpStatus.CONFLICT
            is EmailNotVerifiedException -> HttpStatus.FORBIDDEN
            is InvalidTokenException -> HttpStatus.UNAUTHORIZED
            is InvalidPasswordException -> HttpStatus.BAD_REQUEST
            is UserNotFoundException -> HttpStatus.NOT_FOUND
            is AccessDeniedException -> HttpStatus.FORBIDDEN
        }

        log.warn("Auth error [{}]: {} at {}", ex.errorCode, ex.message, request.requestURI)

        return ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = ex.message,
                errorCode = ex.errorCode,
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: {} at {}", ex.message, request.requestURI)

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                status = 400,
                error = "Bad Request",
                message = ex.message ?: "Invalid request",
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(
        ex: IllegalStateException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Illegal state: {} at {}", ex.message, request.requestURI)

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                status = 409,
                error = "Conflict",
                message = ex.message ?: "Operation conflict",
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception at {}: {}", request.requestURI, ex.message, ex)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = 500,
                error = "Internal Server Error",
                message = "An unexpected error occurred",
                errorCode = "INTERNAL_ERROR",
                path = request.requestURI
            )
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `docker compose exec backend ./gradlew test --tests "com.portfolio.config.GlobalExceptionHandlerTest"`
Expected: All 5 tests PASS

- [ ] **Step 5: Remove duplicate local @ExceptionHandler methods**

**Important:** The `AuthController` has local `@ExceptionHandler` methods that return `AuthErrorResponse` with auth-specific fields (`lockedUntil`, `field`). **Keep those** — they handle the auth-specific error contract that the frontend depends on. The global handler serves as a fallback for auth exceptions not caught locally.

Remove only the **duplicated `IllegalArgumentException` and `IllegalStateException` handlers** from these controllers:
- `BrokerController.kt` (lines ~287-306) — also delete the inline `data class ErrorResponse(val error: String, val message: String)` at line ~309, which conflicts with the new `com.portfolio.dto.response.ErrorResponse`
- `TradingController.kt` — remove local `IllegalArgumentException` handler if present
- `PerformanceController.kt` — remove local `IllegalArgumentException` handler if present
- `NotificationController.kt` — remove local handler if present
- `PortfolioGroupController.kt` — remove local handler if present

For each controller, verify imports are still needed after removal.

- [ ] **Step 6: Verify full test suite**

Run: `docker compose exec backend ./gradlew test`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/config/GlobalExceptionHandler.kt \
       backend/src/test/kotlin/com/portfolio/config/GlobalExceptionHandlerTest.kt \
       backend/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt
git commit -m "feat: add @RestControllerAdvice global exception handler with standardized error responses"
```

---

### Task 4: Create React Error Boundary Component

**Files:**
- Create: `frontend/src/components/ui/ErrorBoundary.tsx`
- Create: `frontend/src/components/ui/ErrorBoundary.css`
- Create: `frontend/src/components/ui/ErrorBoundary.test.tsx`

- [ ] **Step 1: Write the test**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ErrorBoundary } from './ErrorBoundary'

const ThrowingComponent = () => {
  throw new Error('Test explosion')
}

const WorkingComponent = () => <div>All good</div>

describe('ErrorBoundary', () => {
  // Suppress console.error for expected errors
  const originalError = console.error
  beforeEach(() => { console.error = vi.fn() })
  afterEach(() => { console.error = originalError })

  it('renders children when no error', () => {
    render(
      <ErrorBoundary>
        <WorkingComponent />
      </ErrorBoundary>
    )
    expect(screen.getByText('All good')).toBeDefined()
  })

  it('renders fallback when child throws', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>
    )
    expect(screen.getByText(/something went wrong/i)).toBeDefined()
  })

  it('renders custom fallback when provided', () => {
    render(
      <ErrorBoundary fallback={<div>Custom error</div>}>
        <ThrowingComponent />
      </ErrorBoundary>
    )
    expect(screen.getByText('Custom error')).toBeDefined()
  })

  it('recovers when retry is clicked', () => {
    const { rerender } = render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>
    )
    expect(screen.getByText(/something went wrong/i)).toBeDefined()
    fireEvent.click(screen.getByText(/try again/i))
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `frontend/`): `npm run test:run -- --reporter verbose ErrorBoundary`
Expected: FAIL — module not found

- [ ] **Step 3: Create ErrorBoundary.css**

```css
.error-boundary-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  text-align: center;
  color: var(--text-secondary);
  min-height: 120px;
}

.error-boundary-fallback h3 {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary);
}

.error-boundary-fallback p {
  margin: 0 0 1rem;
  font-size: 0.85rem;
}

.error-boundary-retry {
  padding: 0.4rem 1rem;
  font-size: 0.8rem;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-secondary);
  color: var(--text-primary);
  cursor: pointer;
  transition: background 0.15s;
}

.error-boundary-retry:hover {
  background: var(--bg-tertiary);
}
```

- [ ] **Step 4: Create ErrorBoundary.tsx**

```tsx
import { Component, type ErrorInfo, type ReactNode } from 'react'
import './ErrorBoundary.css'

interface ErrorBoundaryProps {
  children: ReactNode
  fallback?: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary caught:', error, errorInfo)
  }

  handleRetry = () => {
    this.setState({ hasError: false })
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback
      }
      return (
        <div className="error-boundary-fallback">
          <h3>Something went wrong</h3>
          <p>This section failed to load.</p>
          <button className="error-boundary-retry" onClick={this.handleRetry}>
            Try again
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run (from `frontend/`): `npm run test:run -- --reporter verbose ErrorBoundary`
Expected: All 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/ui/ErrorBoundary.tsx \
       frontend/src/components/ui/ErrorBoundary.css \
       frontend/src/components/ui/ErrorBoundary.test.tsx
git commit -m "feat: add reusable ErrorBoundary component with retry support"
```

---

### Task 5: Add Error Boundaries to DashboardGrid

**Files:**
- Modify: `frontend/src/components/dashboard/DashboardGrid.tsx`

- [ ] **Step 1: Add ErrorBoundary import**

At the top of `DashboardGrid.tsx`, add:

```tsx
import { ErrorBoundary } from '../ui/ErrorBoundary'
```

- [ ] **Step 2: Wrap each widget zone with ErrorBoundary**

Wrap the content inside each Suspense boundary with an ErrorBoundary. The pattern for each widget in Zone A (lines ~116-118) changes from:

```tsx
<Suspense fallback={<Skeleton />}>
  <WidgetComponent ... />
</Suspense>
```

to:

```tsx
<ErrorBoundary>
  <Suspense fallback={<Skeleton />}>
    <WidgetComponent ... />
  </Suspense>
</ErrorBoundary>
```

Apply this pattern to:
- Zone A widgets (line ~116)
- Zone B widgets (line ~156)
- Zone C ConnectedAccountsComponent (line ~134)
- Zone D PositionsHoldingsTabs (line ~166) — also add a Suspense here since it's currently missing

- [ ] **Step 3: Verify frontend build**

Run (from `frontend/`): `npm run build`
Expected: Build succeeds with no errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/dashboard/DashboardGrid.tsx
git commit -m "feat: wrap dashboard widget zones with ErrorBoundary for crash isolation"
```

---

### Task 6: Add Lazy Loading to Routes

**Files:**
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Replace static page imports with React.lazy**

Replace lines 1-39 of App.tsx. Change the import section:

```tsx
import { useEffect, useCallback, lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { ProtectedRoute } from './components/auth/ProtectedRoute'
import { SessionTimeoutWarning } from './components/auth/SessionTimeoutWarning'
import { useAuthStore } from './stores/authStore'
import { useSessionManager } from './hooks/useSessionManager'

// Auth pages - lazy loaded
const LoginPage = lazy(() => import('./pages/auth/LoginPage').then(m => ({ default: m.LoginPage })))
const SignupPage = lazy(() => import('./pages/auth/SignupPage').then(m => ({ default: m.SignupPage })))
const ForgotPasswordPage = lazy(() => import('./pages/auth/ForgotPasswordPage').then(m => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('./pages/auth/ResetPasswordPage').then(m => ({ default: m.ResetPasswordPage })))
const VerifyEmailPage = lazy(() => import('./pages/auth/VerifyEmailPage').then(m => ({ default: m.VerifyEmailPage })))

// Protected pages - lazy loaded
const DashboardPage = lazy(() => import('./pages/DashboardPage').then(m => ({ default: m.DashboardPage })))
const PortfolioBuilderPage = lazy(() => import('./pages/PortfolioBuilderPage').then(m => ({ default: m.PortfolioBuilderPage })))
const PortfolioGroupsPage = lazy(() => import('./pages/PortfolioGroupsPage').then(m => ({ default: m.PortfolioGroupsPage })))
const PortfolioGroupDetailPage = lazy(() => import('./pages/PortfolioGroupDetailPage').then(m => ({ default: m.PortfolioGroupDetailPage })))
const StockScreenerPage = lazy(() => import('./pages/StockScreenerPage').then(m => ({ default: m.StockScreenerPage })))
const EtfScreenerPage = lazy(() => import('./pages/EtfScreenerPage').then(m => ({ default: m.EtfScreenerPage })))
const StockDetailPage = lazy(() => import('./pages/StockDetailPage').then(m => ({ default: m.StockDetailPage })))
const EtfDetailPage = lazy(() => import('./pages/EtfDetailPage').then(m => ({ default: m.EtfDetailPage })))
const AnalyticsPage = lazy(() => import('./pages/AnalyticsPage').then(m => ({ default: m.AnalyticsPage })))
const ProfilePage = lazy(() => import('./pages/ProfilePage').then(m => ({ default: m.ProfilePage })))
const AdminPage = lazy(() => import('./pages/admin/AdminPage').then(m => ({ default: m.AdminPage })))
const UnauthorizedPage = lazy(() => import('./pages/UnauthorizedPage').then(m => ({ default: m.UnauthorizedPage })))

// Broker pages - lazy loaded
const BrokerConnectionsPage = lazy(() => import('./pages/BrokerConnectionsPage').then(m => ({ default: m.BrokerConnectionsPage })))
const BrokerPositionsPage = lazy(() => import('./pages/BrokerPositionsPage').then(m => ({ default: m.BrokerPositionsPage })))
const PositionDetailsPage = lazy(() => import('./pages/PositionDetailsPage').then(m => ({ default: m.PositionDetailsPage })))
const ReportingPage = lazy(() => import('./pages/ReportingPage').then(m => ({ default: m.ReportingPage })))
const AccountDetailPage = lazy(() => import('./pages/AccountDetailPage').then(m => ({ default: m.AccountDetailPage })))
```

**Note:** The `.then(m => ({ default: m.ComponentName }))` pattern is needed because the pages use named exports, not default exports. If any page already uses `export default`, simplify to just `lazy(() => import('./pages/PageName'))`.

- [ ] **Step 2: Wrap Routes with Suspense**

Wrap the `<Routes>` block (around line 74) with a Suspense boundary:

```tsx
<Suspense fallback={<div className="page-loading">Loading...</div>}>
  <Routes>
    {/* ... existing routes unchanged ... */}
  </Routes>
</Suspense>
```

- [ ] **Step 3: Verify frontend build**

Run (from `frontend/`): `npm run build`
Expected: Build succeeds. Check `dist/assets/` for multiple JS chunks (code splitting working).

- [ ] **Step 4: Verify chunk splitting**

Run (from `frontend/`): `ls -la dist/assets/*.js | wc -l`
Expected: More than 1 JS file (was 1 before, now should be many)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx
git commit -m "perf: add React.lazy code splitting for all page routes"
```

---

## Sprint 2: Caching, Error Quality, Decomposition, Logging

### Task 7: Upgrade CacheManager to Redis

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/config/CacheConfig.kt`

- [ ] **Step 1: Read current CacheConfig**

Read `backend/src/main/kotlin/com/portfolio/config/CacheConfig.kt` to confirm current state.

- [ ] **Step 2: Replace ConcurrentMapCacheManager with RedisCacheManager**

```kotlin
package com.portfolio.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(GenericJackson2JsonRedisSerializer())
            )
            .disableCachingNullValues()

        val cacheConfigurations = mapOf(
            // Reference data — rarely changes, long TTL
            "gics-hierarchy" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "gics-sectors" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "gicsLookup" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "countries" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "exchanges" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "regions" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "regions-simple" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "country-region-map" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "country-name-map" to defaultConfig.entryTtl(Duration.ofHours(24)),
            // Exchange rates — changes daily
            "exchange-rates" to defaultConfig.entryTtl(Duration.ofHours(6)),
            // Look-through — deterministic per position set, moderate TTL
            "look-through" to defaultConfig.entryTtl(Duration.ofMinutes(30)),
            // ETF sector allocations — semi-static
            "etf-sector-allocations" to defaultConfig.entryTtl(Duration.ofHours(12))
        )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}
```

- [ ] **Step 3: Verify build**

Run: `docker compose exec backend ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Verify Redis connectivity**

Run: `docker compose up --build -d`
Expected: All services start. Check backend logs for cache-related errors:
Run: `docker compose logs backend | grep -i "cache\|redis" | head -20`
Expected: No errors. Redis connection established.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/config/CacheConfig.kt
git commit -m "feat: switch from in-memory to Redis cache with TTL-based eviction"
```

---

### Task 8: Add @Cacheable to ExchangeRateService

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/ExchangeRateService.kt`

- [ ] **Step 1: Read current ExchangeRateService**

Read the full file to understand the current ConcurrentHashMap caching.

- [ ] **Step 2: Replace ConcurrentHashMap with @Cacheable**

Remove the `private val cache = ConcurrentHashMap<...>()` field (line ~31).

Replace the `getRate()` method (line ~84-86) to use `@Cacheable`:

```kotlin
@Cacheable(value = ["exchange-rates"], key = "#currency + '-' + #date")
fun getRate(currency: String, date: LocalDate): BigDecimal? {
    val upper = currency.uppercase()
    if (upper == "CAD") return BigDecimal.ONE
    val series = SERIES_MAP[upper] ?: return null
    return fetchRateWithWalkback(series, date)
}
```

Remove the `cache.getOrPut(...)` wrapping from the method body.

- [ ] **Step 3: Verify build and tests**

Run: `docker compose exec backend ./gradlew test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/service/ExchangeRateService.kt
git commit -m "feat: replace in-memory exchange rate cache with Redis @Cacheable"
```

---

### Task 9: Add @Cacheable to ETF Sector Allocations

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/service/CachedLookupService.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/service/LookThroughService.kt`

**Important:** Spring's proxy-based AOP means `@Cacheable` on a method called from within the same class has no effect. The cached method MUST live in a separate `@Service` class.

- [ ] **Step 1: Create CachedLookupService**

```kotlin
package com.portfolio.service

import com.portfolio.entity.SectorAllocationFactset
import com.portfolio.repository.SectorAllocationFactsetRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class CachedLookupService(
    private val sectorAllocationFactsetRepository: SectorAllocationFactsetRepository
) {
    @Cacheable(value = ["etf-sector-allocations"], key = "#etfId")
    fun getSectorAllocations(etfId: Long): List<SectorAllocationFactset> {
        return sectorAllocationFactsetRepository.findLatestByEtfId(etfId)
    }
}
```

- [ ] **Step 2: Inject CachedLookupService into LookThroughService**

In `LookThroughService.kt`, add `CachedLookupService` as a constructor parameter:

```kotlin
@Service
class LookThroughService(
    // ... existing dependencies
    private val cachedLookupService: CachedLookupService
)
```

Replace direct repository calls to `sectorAllocationFactsetRepository.findLatestByEtfId(etf.id)` (line ~402) with `cachedLookupService.getSectorAllocations(etf.id)`.

- [ ] **Step 3: Verify build and tests**

Run: `docker compose exec backend ./gradlew test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/service/CachedLookupService.kt \
       backend/src/main/kotlin/com/portfolio/service/LookThroughService.kt
git commit -m "feat: add Redis caching for ETF sector allocation lookups via CachedLookupService"
```

---

### Task 10: Add Data Quality Envelope for Silent Catches

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/dto/response/DataQualityEnvelope.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/DashboardDataService.kt`

- [ ] **Step 1: Update silent catch in calculateHoldingsCount (lines ~158-178)**

Change from:

```kotlin
} catch (e: Exception) {
    log.warn("Failed to calculate look-through holdings count", e)
    HoldingsCountDto(...)
}
```

To return quality metadata (store a warning flag in the response). Since `getSummary()` assembles the `DashboardSummaryResponse`, add a `warnings: List<String>` field to the response DTO and populate it when a catch fires:

```kotlin
val warnings = mutableListOf<String>()
// ...
} catch (e: Exception) {
    log.warn("Failed to calculate look-through holdings count", e)
    warnings.add("Holdings count may be incomplete due to a calculation error")
    HoldingsCountDto(...)
}
// Then pass warnings into the response:
return DashboardSummaryResponse(..., warnings = warnings)
```

- [ ] **Step 2: Apply same pattern to getSectorExposure (lines ~250-255)**

```kotlin
} catch (e: Exception) {
    log.warn("Failed to compute sector exposure", e)
    return SectorExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO,
        warnings = listOf("Sector exposure data unavailable due to a calculation error"))
}
```

- [ ] **Step 3: Apply same pattern to getGeographyExposure (lines ~332-337)**

```kotlin
} catch (e: Exception) {
    log.warn("Failed to compute geography exposure", e)
    return GeographyExposureResponse(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO,
        warnings = listOf("Geography exposure data unavailable due to a calculation error"))
}
```

- [ ] **Step 4: Add `warnings` field to relevant response DTOs**

In the DTO files for `DashboardSummaryResponse`, `SectorExposureResponse`, and `GeographyExposureResponse`, add:

```kotlin
val warnings: List<String> = emptyList()
```

This is backward-compatible — existing responses will serialize with an empty warnings array.

- [ ] **Step 5: Verify build and tests**

Run: `docker compose exec backend ./gradlew test`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/service/DashboardDataService.kt
git commit -m "feat: add data quality warnings to dashboard responses instead of silent catches"
```

---

### Task 11: Decompose DashboardDataService

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/broker/service/DashboardCashService.kt`
- Create: `backend/src/main/kotlin/com/portfolio/broker/service/DashboardExposureService.kt`
- Create: `backend/src/main/kotlin/com/portfolio/broker/service/DashboardRiskService.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/DashboardDataService.kt`

- [ ] **Step 1: Extract DashboardCashService**

Move these methods from DashboardDataService:
- `getCash()` (line ~183)
- `getTotalCashFromSnapshot()` (private helper, line ~710)
- `getBuyingPowerFromSnapshot()` (private helper, line ~733)
- The shared `cashTypeRef` field (line ~42)

```kotlin
package com.portfolio.broker.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
// ... other necessary imports

@Service
class DashboardCashService(
    private val connectionRepository: BrokerConnectionRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cashTypeRef = object : TypeReference<Map<String, BigDecimal>>() {}

    fun getCash(userId: Long, connectionId: Long? = null): DashboardCashResponse {
        // Move method body from DashboardDataService
    }

    fun getTotalCashFromSnapshot(connections: List<BrokerConnection>): BigDecimal {
        // Move method body from DashboardDataService
    }

    fun getBuyingPowerFromSnapshot(connections: List<BrokerConnection>): BigDecimal {
        // Move method body from DashboardDataService
    }
}
```

- [ ] **Step 2: Extract DashboardExposureService**

Move these methods from DashboardDataService:
- `getSectorExposure()` (line ~238)
- `getGeographyExposure()` (line ~320)
- Related private helpers for sector/geography aggregation

```kotlin
@Service
class DashboardExposureService(
    private val positionRepository: BrokerPositionRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val lookThroughService: LookThroughService,
    private val stockRepository: StockRepository,
    private val countryRepository: CountryRepository
) {
    fun getSectorExposure(userId: Long, connectionId: Long? = null): SectorExposureResponse { ... }
    fun getGeographyExposure(userId: Long, connectionId: Long? = null): GeographyExposureResponse { ... }
}
```

- [ ] **Step 3: Extract DashboardRiskService**

Move `getRiskProfile()` (line ~392) and its private helpers:

```kotlin
@Service
class DashboardRiskService(
    private val positionRepository: BrokerPositionRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val lookThroughService: LookThroughService,
    private val stockRepository: StockRepository
) {
    fun getRiskProfile(userId: Long, connectionId: Long? = null): RiskProfileResponse { ... }
}
```

- [ ] **Step 4: Update DashboardDataService to delegate**

DashboardDataService keeps: `getSummary()`, `getOpenOrders()`, `getFees()`, `getDividendCalendar()`, `getHoldings()`, `getAccounts()`, `refreshAll()`. It delegates to the new sub-services where needed:

```kotlin
@Service
class DashboardDataService(
    // ... existing dependencies
    private val cashService: DashboardCashService,
    private val exposureService: DashboardExposureService,
    private val riskService: DashboardRiskService
) {
    // getSummary() calls cashService.getTotalCashFromSnapshot() instead of internal method
    // getCash() delegates to cashService.getCash()
    // getSectorExposure() delegates to exposureService.getSectorExposure()
    // etc.
}
```

- [ ] **Step 5: Update DashboardController to inject new services if needed**

If the controller calls DashboardDataService for sector/geo/risk, it can either:
- Continue calling DashboardDataService (which delegates) — no controller changes needed
- Or inject the sub-services directly for those endpoints

Prefer option A (delegation) to minimize controller changes.

- [ ] **Step 6: Verify build and tests**

Run: `docker compose exec backend ./gradlew test`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/service/DashboardCashService.kt \
       backend/src/main/kotlin/com/portfolio/broker/service/DashboardExposureService.kt \
       backend/src/main/kotlin/com/portfolio/broker/service/DashboardRiskService.kt \
       backend/src/main/kotlin/com/portfolio/broker/service/DashboardDataService.kt
git commit -m "refactor: decompose DashboardDataService into focused sub-services (cash, exposure, risk)"
```

---

### Task 12: Add Structured Logging

**Files:**
- Modify: `backend/src/main/resources/logback-spring.xml`

- [ ] **Step 1: Read current logback-spring.xml**

Read the file to confirm current minimal state (15 lines).

- [ ] **Step 2: Add structured JSON logging for non-local profiles**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Console appender for local development -->
    <springProfile name="local">
        <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="com.portfolio" level="DEBUG"/>
    </springProfile>

    <!-- JSON appender for dev/prod (structured logging for log aggregation) -->
    <springProfile name="dev,prod">
        <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>requestId</includeMdcKeyName>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON_CONSOLE"/>
        </root>
        <logger name="com.portfolio" level="INFO"/>
    </springProfile>

    <!-- Suppress noisy libraries -->
    <logger name="org.hibernate.SQL" level="WARN"/>
    <logger name="org.springframework.security" level="WARN"/>

    <!-- MDC filter for skipping logs -->
    <turboFilter class="ch.qos.logback.classic.turbo.MDCFilter">
        <MDCKey>skipLog</MDCKey>
        <Value>true</Value>
        <OnMatch>DENY</OnMatch>
    </turboFilter>
</configuration>
```

- [ ] **Step 3: Add logstash-logback-encoder dependency**

In `backend/build.gradle.kts`, add:

```kotlin
implementation("net.logstash.logback:logstash-logback-encoder:7.4")
```

- [ ] **Step 4: Verify build**

Run: `docker compose exec backend ./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/logback-spring.xml \
       backend/build.gradle.kts
git commit -m "feat: add structured JSON logging for dev/prod profiles with logstash encoder"
```

---

## Verification Checklist

After all tasks are complete:

- [ ] `docker compose exec backend ./gradlew test` — all backend tests pass
- [ ] `npm run test:run` (from `frontend/`) — all frontend tests pass
- [ ] `npm run lint` (from `frontend/`) — zero warnings
- [ ] `npm run build` (from `frontend/`) — build succeeds, multiple JS chunks in dist/
- [ ] `docker compose up --build` — full stack starts and runs
- [ ] No secrets committed
- [ ] No new Flyway migrations needed (no schema changes in this plan)
