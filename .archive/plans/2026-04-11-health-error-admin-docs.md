# Health Checks, Error Handling, Admin UI & Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add health checks to both services, standardize all error handling to RFC 7807, build the admin ingestion UI with live progress, add toast notifications, and consolidate all documentation into `docs/agent-reference/`.

**Architecture:** RFC 7807 ProblemDetail replaces custom ErrorResponse across both backend services. Frontend gets ApiError class + toast notifications. Admin page rewired to ingestion-service (port 8081) with auto-refresh polling. All markdown docs consolidated into `docs/agent-reference/`.

**Tech Stack:** Kotlin/Spring Boot (ProblemDetail, HealthIndicator), React/TypeScript (Zustand toast store, React Query polling), PostgreSQL JPQL queries

**Spec:** `docs/superpowers/specs/2026-04-11-health-checks-error-handling-design.md`

**Important:** No JDK on local machine. Backend builds must use Docker. No git commands — user handles git manually.

---

## Task 1: Exception Hierarchy & GlobalExceptionHandler (Main Backend)

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/exception/AppException.kt`
- Rewrite: `backend/src/main/kotlin/com/portfolio/config/GlobalExceptionHandler.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/auth/exception/AuthExceptions.kt`
- Modify: `backend/src/main/resources/application.yml`
- Delete (contents only): `backend/src/main/kotlin/com/portfolio/dto/response/ErrorResponse.kt` — no longer needed, but keep if other code still references it temporarily

- [ ] **Step 1: Create AppException hierarchy**

Create `backend/src/main/kotlin/com/portfolio/exception/AppException.kt`:

```kotlin
package com.portfolio.exception

abstract class AppException(
    override val message: String,
    val code: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class NotFoundException(code: String = "NOT_FOUND", message: String = "Resource not found") : AppException(message, code)
class ConflictException(code: String = "CONFLICT", message: String = "Operation conflict") : AppException(message, code)
class ValidationException(code: String = "VALIDATION_ERROR", message: String = "Invalid input") : AppException(message, code)
class ForbiddenException(code: String = "FORBIDDEN", message: String = "Access denied") : AppException(message, code)
class RateLimitException(code: String = "RATE_LIMITED", message: String = "Too many requests") : AppException(message, code)
class ExternalServiceException(code: String = "EXTERNAL_SERVICE_ERROR", message: String = "External service unavailable", cause: Throwable? = null) : AppException(message, code, cause)
class InternalException(code: String = "INTERNAL_ERROR", message: String = "An unexpected error occurred", cause: Throwable? = null) : AppException(message, code, cause)
```

- [ ] **Step 2: Migrate AuthExceptions to extend AppException**

Rewrite `backend/src/main/kotlin/com/portfolio/auth/exception/AuthExceptions.kt`:

```kotlin
package com.portfolio.auth.exception

import com.portfolio.exception.*
import java.time.OffsetDateTime

class EmailAlreadyExistsException : ConflictException(
    code = "EMAIL_EXISTS",
    message = "An account with this email already exists"
)

class InvalidCredentialsException : ForbiddenException(
    code = "INVALID_CREDENTIALS",
    message = "Invalid email or password"
)

class AccountLockedException(val lockedUntil: OffsetDateTime) : ForbiddenException(
    code = "ACCOUNT_LOCKED",
    message = "Account is locked due to too many failed attempts"
)

class EmailNotVerifiedException : ForbiddenException(
    code = "EMAIL_NOT_VERIFIED",
    message = "Please verify your email address before logging in"
)

class InvalidTokenException(message: String = "Invalid or expired token") : ValidationException(
    code = "INVALID_TOKEN",
    message = message
)

class InvalidPasswordException(message: String = "Password does not meet requirements") : ValidationException(
    code = "INVALID_PASSWORD",
    message = message
)

class UserNotFoundException : NotFoundException(
    code = "USER_NOT_FOUND",
    message = "User not found"
)

class AccessDeniedException(message: String = "Access denied") : ForbiddenException(
    code = "ACCESS_DENIED",
    message = message
)
```

Note: The sealed class is removed. Each exception now extends an AppException subclass directly. The `errorCode` property is replaced by `code` (inherited from AppException). All existing code that references `ex.errorCode` must use `ex.code` instead.

- [ ] **Step 3: Rewrite GlobalExceptionHandler with RFC 7807 ProblemDetail**

Rewrite `backend/src/main/kotlin/com/portfolio/config/GlobalExceptionHandler.kt`:

```kotlin
package com.portfolio.config

import com.portfolio.auth.exception.AccountLockedException
import com.portfolio.exception.AppException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.time.OffsetDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException, request: HttpServletRequest): ProblemDetail {
        val status = when (ex) {
            is com.portfolio.exception.NotFoundException -> HttpStatus.NOT_FOUND
            is com.portfolio.exception.ConflictException -> HttpStatus.CONFLICT
            is com.portfolio.exception.ValidationException -> HttpStatus.BAD_REQUEST
            is com.portfolio.exception.ForbiddenException -> HttpStatus.FORBIDDEN
            is com.portfolio.exception.RateLimitException -> HttpStatus.TOO_MANY_REQUESTS
            is com.portfolio.exception.ExternalServiceException -> HttpStatus.BAD_GATEWAY
            is com.portfolio.exception.InternalException -> HttpStatus.INTERNAL_SERVER_ERROR
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }

        log.warn("App error [{}] {}: {} at {}", status.value(), ex.code, ex.message, request.requestURI)

        return ProblemDetail.forStatusAndDetail(status, ex.message).apply {
            title = status.reasonPhrase
            instance = URI.create(request.requestURI)
            setProperty("code", ex.code)
            setProperty("timestamp", OffsetDateTime.now())
            if (ex is AccountLockedException) {
                setProperty("lockedUntil", ex.lockedUntil)
            }
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ProblemDetail {
        val fieldErrors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fieldErrors.joinToString("; ")).apply {
            title = "Validation Failed"
            instance = URI.create(request.requestURI)
            setProperty("code", "VALIDATION_ERROR")
            setProperty("timestamp", OffsetDateTime.now())
            setProperty("fields", ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage })
        }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ProblemDetail {
        log.warn("Bad request: {} at {}", ex.message, request.requestURI)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request").apply {
            title = "Bad Request"
            instance = URI.create(request.requestURI)
            setProperty("code", "BAD_REQUEST")
            setProperty("timestamp", OffsetDateTime.now())
        }
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException, request: HttpServletRequest): ProblemDetail {
        log.error("Illegal state: {} at {}", ex.message, request.requestURI)
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Operation conflict").apply {
            title = "Conflict"
            instance = URI.create(request.requestURI)
            setProperty("code", "CONFLICT")
            setProperty("timestamp", OffsetDateTime.now())
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ProblemDetail {
        log.error("Unhandled exception at {}: {}", request.requestURI, ex.message, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred").apply {
            title = "Internal Server Error"
            instance = URI.create(request.requestURI)
            setProperty("code", "INTERNAL_ERROR")
            setProperty("timestamp", OffsetDateTime.now())
        }
    }
}
```

- [ ] **Step 4: Remove duplicate @ExceptionHandler from AuthController**

In `backend/src/main/kotlin/com/portfolio/auth/controller/AuthController.kt`, remove all `@ExceptionHandler` annotated methods (approximately lines 190-234). The GlobalExceptionHandler now handles all exceptions.

- [ ] **Step 5: Enable RFC 7807 in application.yml**

Add to `backend/src/main/resources/application.yml` under `spring:`:

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

- [ ] **Step 6: Verify backend compiles**

```bash
docker compose build backend
```

---

## Task 2: Exception Hierarchy & GlobalExceptionHandler (Ingestion Service)

**Files:**
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/exception/AppException.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/exception/GlobalExceptionHandler.kt`
- Modify: `ingestion-service/src/main/resources/application.yml`

- [ ] **Step 1: Create AppException hierarchy for ingestion-service**

Create `ingestion-service/src/main/kotlin/com/portfolio/ingestion/exception/AppException.kt`:

```kotlin
package com.portfolio.ingestion.exception

abstract class AppException(
    override val message: String,
    val code: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class NotFoundException(code: String = "NOT_FOUND", message: String = "Resource not found") : AppException(message, code)
class ConflictException(code: String = "CONFLICT", message: String = "Operation conflict") : AppException(message, code)
class ValidationException(code: String = "VALIDATION_ERROR", message: String = "Invalid input") : AppException(message, code)
class ExternalServiceException(code: String = "EXTERNAL_SERVICE_ERROR", message: String = "External service unavailable", cause: Throwable? = null) : AppException(message, code, cause)
class InternalException(code: String = "INTERNAL_ERROR", message: String = "An unexpected error occurred", cause: Throwable? = null) : AppException(message, code, cause)
```

- [ ] **Step 2: Create GlobalExceptionHandler for ingestion-service**

Create `ingestion-service/src/main/kotlin/com/portfolio/ingestion/exception/GlobalExceptionHandler.kt`:

```kotlin
package com.portfolio.ingestion.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.time.OffsetDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException, request: HttpServletRequest): ProblemDetail {
        val status = when (ex) {
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ConflictException -> HttpStatus.CONFLICT
            is ValidationException -> HttpStatus.BAD_REQUEST
            is ExternalServiceException -> HttpStatus.BAD_GATEWAY
            is InternalException -> HttpStatus.INTERNAL_SERVER_ERROR
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }

        log.warn("App error [{}] {}: {} at {}", status.value(), ex.code, ex.message, request.requestURI)

        return ProblemDetail.forStatusAndDetail(status, ex.message).apply {
            title = status.reasonPhrase
            instance = URI.create(request.requestURI)
            setProperty("code", ex.code)
            setProperty("timestamp", OffsetDateTime.now())
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ProblemDetail {
        log.error("Unhandled exception at {}: {}", request.requestURI, ex.message, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred").apply {
            title = "Internal Server Error"
            instance = URI.create(request.requestURI)
            setProperty("code", "INTERNAL_ERROR")
            setProperty("timestamp", OffsetDateTime.now())
        }
    }
}
```

- [ ] **Step 3: Enable RFC 7807 in ingestion-service application.yml**

Add under `spring:` in `ingestion-service/src/main/resources/application.yml`:

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

- [ ] **Step 4: Verify ingestion-service compiles**

```bash
docker compose build ingestion-service
```

---

## Task 3: Health Check Indicators (Both Services)

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/health/SnapTradeHealthIndicator.kt`
- Create: `backend/src/main/kotlin/com/portfolio/health/IngestionServiceHealthIndicator.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/health/EodhdHealthIndicator.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/health/QuotaHealthIndicator.kt`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `ingestion-service/src/main/resources/application.yml`

- [ ] **Step 1: Create SnapTradeHealthIndicator**

Create `backend/src/main/kotlin/com/portfolio/health/SnapTradeHealthIndicator.kt`:

```kotlin
package com.portfolio.health

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class SnapTradeHealthIndicator : HealthIndicator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun health(): Health {
        return try {
            Health.up().withDetail("status", "available").build()
        } catch (e: Exception) {
            log.warn("SnapTrade health check failed: {}", e.message)
            Health.down().withDetail("error", e.message ?: "Unknown error").build()
        }
    }
}
```

- [ ] **Step 2: Create IngestionServiceHealthIndicator**

Create `backend/src/main/kotlin/com/portfolio/health/IngestionServiceHealthIndicator.kt`:

```kotlin
package com.portfolio.health

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class IngestionServiceHealthIndicator(
    @Value("\${ingestion-service.health-url:http://localhost:8081/actuator/health}")
    private val healthUrl: String
) : HealthIndicator {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = WebClient.builder().build()

    override fun health(): Health {
        return try {
            val response = client.get()
                .uri(healthUrl)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(5))

            if (response?.statusCode?.is2xxSuccessful == true) {
                Health.up().withDetail("url", healthUrl).build()
            } else {
                Health.down().withDetail("url", healthUrl).withDetail("status", response?.statusCode?.value()).build()
            }
        } catch (e: Exception) {
            log.debug("Ingestion service health check failed: {}", e.message)
            Health.unknown().withDetail("url", healthUrl).withDetail("reason", "Service unreachable").build()
        }
    }
}
```

- [ ] **Step 3: Create EodhdHealthIndicator**

Create `ingestion-service/src/main/kotlin/com/portfolio/ingestion/health/EodhdHealthIndicator.kt`:

```kotlin
package com.portfolio.ingestion.health

import com.portfolio.ingestion.config.IngestionProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class EodhdHealthIndicator(
    private val props: IngestionProperties
) : HealthIndicator {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = WebClient.builder().baseUrl(props.eodhd.baseUrl).build()

    override fun health(): Health {
        return try {
            val response = client.get()
                .uri("/exchanges-list/?api_token=${props.eodhd.apiKey}&fmt=json")
                .retrieve()
                .bodyToMono(String::class.java)
                .block(Duration.ofSeconds(5))

            if (!response.isNullOrBlank() && response.startsWith("[")) {
                Health.up().withDetail("status", "reachable").build()
            } else {
                Health.down().withDetail("response", "unexpected format").build()
            }
        } catch (e: Exception) {
            log.warn("EODHD health check failed: {}", e.message)
            Health.down().withDetail("error", e.message ?: "Connection failed").build()
        }
    }
}
```

- [ ] **Step 4: Create QuotaHealthIndicator**

Create `ingestion-service/src/main/kotlin/com/portfolio/ingestion/health/QuotaHealthIndicator.kt`:

```kotlin
package com.portfolio.ingestion.health

import com.portfolio.ingestion.config.IngestionProperties
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class QuotaHealthIndicator(
    private val rateLimiter: EodhdRateLimiter,
    private val props: IngestionProperties
) : HealthIndicator {

    override fun health(): Health {
        val remaining = rateLimiter.remainingDailyQuota()
        val total = props.eodhd.dailyQuota
        val pct = if (total > 0) (remaining.toDouble() / total * 100) else 0.0

        val details = mapOf(
            "remaining" to remaining,
            "total" to total,
            "percentRemaining" to "%.1f%%".format(pct)
        )

        return if (pct > 1.0) {
            Health.up().withDetails(details).build()
        } else {
            Health.down().withDetails(details).withDetail("reason", "Daily quota exhausted").build()
        }
    }
}
```

- [ ] **Step 5: Update health config in both application.yml files**

Add to both `backend/src/main/resources/application.yml` and `ingestion-service/src/main/resources/application.yml` under `management:`:

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
      show-components: when-authorized
```

- [ ] **Step 6: Verify both services compile**

```bash
docker compose build backend ingestion-service
```

---

## Task 4: Frontend Toast Notifications & ApiError

**Files:**
- Create: `frontend/src/stores/toastStore.ts`
- Create: `frontend/src/components/ui/toast.tsx`
- Create: `frontend/src/components/ui/toast.css`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/components/layout/AppLayout.tsx`

- [ ] **Step 1: Create toastStore**

Create `frontend/src/stores/toastStore.ts`:

```typescript
import { create } from 'zustand'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface Toast {
  id: string
  type: ToastType
  message: string
  duration?: number
}

interface ToastState {
  toasts: Toast[]
  addToast: (type: ToastType, message: string, duration?: number) => void
  removeToast: (id: string) => void
}

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  addToast: (type, message, duration = 5000) => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`
    set((state) => ({ toasts: [...state.toasts, { id, type, message, duration }] }))
    if (duration > 0) {
      setTimeout(() => {
        set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }))
      }, duration)
    }
  },
  removeToast: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}))

export function useToast() {
  const { addToast } = useToastStore()
  return {
    success: (message: string) => addToast('success', message),
    error: (message: string) => addToast('error', message),
    warning: (message: string) => addToast('warning', message),
    info: (message: string) => addToast('info', message),
  }
}
```

- [ ] **Step 2: Create toast component and CSS**

Create `frontend/src/components/ui/toast.css`:

```css
.toast-container {
  position: fixed;
  bottom: 1.5rem;
  right: 1.5rem;
  z-index: 9999;
  display: flex;
  flex-direction: column-reverse;
  gap: 0.5rem;
  max-width: 24rem;
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  border: 1px solid var(--border);
  background: var(--card-bg);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  animation: toast-in 0.2s ease-out;
  font-size: 0.8125rem;
  color: var(--text-primary);
  line-height: 1.4;
}

@keyframes toast-in {
  from { opacity: 0; transform: translateY(0.5rem); }
  to { opacity: 1; transform: translateY(0); }
}

.toast-icon {
  flex-shrink: 0;
  width: 1rem;
  height: 1rem;
  margin-top: 0.125rem;
}

.toast-message {
  flex: 1;
  min-width: 0;
}

.toast-dismiss {
  flex-shrink: 0;
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0;
  font-size: 1rem;
  line-height: 1;
}

.toast-dismiss:hover {
  color: var(--text-primary);
}

.toast--success .toast-icon { color: var(--success); }
.toast--error .toast-icon { color: var(--error); }
.toast--warning .toast-icon { color: var(--warning); }
.toast--info .toast-icon { color: var(--info); }
```

Create `frontend/src/components/ui/toast.tsx`:

```tsx
import { useToastStore } from '@/stores/toastStore'
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react'
import './toast.css'

const ICONS = {
  success: CheckCircle,
  error: XCircle,
  warning: AlertTriangle,
  info: Info,
}

export function ToastContainer() {
  const { toasts, removeToast } = useToastStore()

  if (toasts.length === 0) return null

  return (
    <div className="toast-container">
      {toasts.map((toast) => {
        const Icon = ICONS[toast.type]
        return (
          <div key={toast.id} className={`toast toast--${toast.type}`}>
            <Icon className="toast-icon" />
            <span className="toast-message">{toast.message}</span>
            <button className="toast-dismiss" onClick={() => removeToast(toast.id)}>
              <X style={{ width: '0.875rem', height: '0.875rem' }} />
            </button>
          </div>
        )
      })}
    </div>
  )
}
```

- [ ] **Step 3: Add ApiError class and RFC 7807 parsing to api.ts**

Add to `frontend/src/services/api.ts` before the `apiFetch` function:

```typescript
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    public detail: string,
    public title?: string
  ) {
    super(detail)
    this.name = 'ApiError'
  }
}

async function parseErrorResponse(response: Response): Promise<ApiError> {
  try {
    const contentType = response.headers.get('content-type') || ''
    if (contentType.includes('application/problem+json') || contentType.includes('application/json')) {
      const body = await response.json()
      return new ApiError(
        body.status || response.status,
        body.code || body.errorCode || 'UNKNOWN',
        body.detail || body.message || response.statusText,
        body.title
      )
    }
  } catch {
    // Failed to parse body
  }
  return new ApiError(response.status, 'UNKNOWN', response.statusText)
}
```

- [ ] **Step 4: Mount ToastContainer in AppLayout**

Add to `frontend/src/components/layout/AppLayout.tsx`:

Import `ToastContainer`:
```typescript
import { ToastContainer } from '@/components/ui/toast'
```

Add `<ToastContainer />` at the end of the returned JSX, before the closing `</div>`:
```tsx
<ToastContainer />
```

- [ ] **Step 5: Verify frontend builds**

```bash
cd frontend && npm run build
```

---

## Task 5: Admin Ingestion Backend Enhancements

**Files:**
- Modify: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/controller/AdminIngestionController.kt`
- Modify: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/IngestionOrchestrator.kt`
- Modify: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/InstrumentRepository.kt`
- Modify: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/ProviderRawDataRepository.kt`
- Modify: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/ExchangeRepository.kt`

- [ ] **Step 1: Add count queries to repositories**

Add to `InstrumentRepository.kt`:

```kotlin
@Query("SELECT i.instrumentType, COUNT(i) FROM Instrument i WHERE i.status = 'ACTIVE' GROUP BY i.instrumentType")
fun countByType(): List<Array<Any>>
```

Add to `ProviderRawDataRepository.kt`:

```kotlin
@Query("SELECT i.instrumentType, COUNT(prd) FROM ProviderRawData prd JOIN prd.instrument i WHERE prd.provider = 'EODHD' AND prd.dataType = 'FUNDAMENTALS' GROUP BY i.instrumentType")
fun countEnrichedByType(): List<Array<Any>>
```

Add to `ExchangeRepository.kt`:

```kotlin
fun countByIsActiveTrue(): Long
```

- [ ] **Step 2: Add active run tracking to IngestionOrchestrator**

Add these fields and methods to the `IngestionOrchestrator`:

```kotlin
@Volatile
private var activeRunId: Long? = null

@Volatile
private var activeStepName: String? = null

fun getActiveRunId(): Long? = activeRunId

fun isRunning(): Boolean = activeRunId != null
```

Update `runExchangeSync` and `runFullIngestion` to set/clear `activeRunId`:

At the start of each method after `tracking.startRun(...)`:
```kotlin
activeRunId = run.id
```

In the finally/completion of each method:
```kotlin
activeRunId = null
```

- [ ] **Step 3: Make triggers async and add enhanced endpoints**

Rewrite `AdminIngestionController.kt` to:
- Start pipelines in background coroutines (return immediately)
- Add `GET /admin/ingestion/active-run` endpoint
- Enhance `GET /admin/ingestion/stats` with per-type breakdown

```kotlin
package com.portfolio.ingestion.controller

import com.portfolio.ingestion.persistence.entity.InstrumentType
import com.portfolio.ingestion.persistence.repository.*
import com.portfolio.ingestion.pipeline.IngestionOrchestrator
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import com.portfolio.ingestion.config.IngestionProperties
import kotlinx.coroutines.*
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/ingestion")
class AdminIngestionController(
    private val orchestrator: IngestionOrchestrator,
    private val runRepo: IngestionRunRepository,
    private val stepRepo: IngestionStepRepository,
    private val errorRepo: IngestionErrorRepository,
    private val instrumentRepo: InstrumentRepository,
    private val rawDataRepo: ProviderRawDataRepository,
    private val exchangeRepo: ExchangeRepository,
    private val rateLimiter: EodhdRateLimiter,
    private val props: IngestionProperties
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @PostMapping("/exchanges")
    fun syncExchanges(): ResponseEntity<Map<String, Any>> {
        if (orchestrator.isRunning()) {
            return ResponseEntity.status(409).body(mapOf("status" to "error", "message" to "An ingestion run is already in progress"))
        }
        scope.launch {
            orchestrator.runExchangeSync("api:/admin/ingestion/exchanges")
        }
        return ResponseEntity.ok(mapOf("status" to "started"))
    }

    @PostMapping("/run")
    fun triggerFullIngestion(): ResponseEntity<Map<String, Any>> {
        if (orchestrator.isRunning()) {
            return ResponseEntity.status(409).body(mapOf("status" to "error", "message" to "An ingestion run is already in progress"))
        }
        rateLimiter.resetDailyQuota()
        scope.launch {
            orchestrator.runFullIngestion("api:/admin/ingestion/run")
        }
        return ResponseEntity.ok(mapOf("status" to "started"))
    }

    @GetMapping("/active-run")
    fun getActiveRun(): ResponseEntity<Map<String, Any?>> {
        val runId = orchestrator.getActiveRunId()
        if (runId == null) {
            return ResponseEntity.ok(mapOf("isRunning" to false))
        }
        val steps = stepRepo.findByRunId(runId).map { step ->
            mapOf(
                "name" to step.stepName.name,
                "status" to step.status.name,
                "processed" to step.recordsProcessed,
                "created" to step.recordsCreated,
                "updated" to step.recordsUpdated,
                "failed" to step.recordsFailed
            )
        }
        return ResponseEntity.ok(mapOf(
            "isRunning" to true,
            "runId" to runId,
            "steps" to steps
        ))
    }

    @GetMapping("/stats")
    fun getStats(): ResponseEntity<Map<String, Any>> {
        val totalInstruments = instrumentRepo.count()
        val enrichedCount = rawDataRepo.count()
        val remaining = rateLimiter.remainingDailyQuota()
        val exchangeCount = exchangeRepo.countByIsActiveTrue()

        val byType = instrumentRepo.countByType().associate {
            (it[0] as InstrumentType).name to it[1] as Long
        }
        val enrichedByType = rawDataRepo.countEnrichedByType().associate {
            (it[0] as InstrumentType).name to it[1] as Long
        }
        val instrumentsByType = InstrumentType.entries.associate { type ->
            type.name to mapOf(
                "total" to (byType[type.name] ?: 0L),
                "enriched" to (enrichedByType[type.name] ?: 0L)
            )
        }

        val lastRun = runRepo.findAllByOrderByStartedAtDesc(PageRequest.of(0, 1)).firstOrNull()

        return ResponseEntity.ok(mapOf(
            "totalInstruments" to totalInstruments,
            "enrichedInstruments" to enrichedCount,
            "pendingInstruments" to (totalInstruments - enrichedCount),
            "remainingDailyQuota" to remaining,
            "totalDailyQuota" to props.eodhd.dailyQuota,
            "exchangeCount" to exchangeCount,
            "exchanges" to props.targetExchanges,
            "lastRunStatus" to (lastRun?.status?.name ?: "NONE"),
            "lastRunCompletedAt" to lastRun?.completedAt,
            "instrumentsByType" to instrumentsByType
        ))
    }

    @GetMapping("/runs")
    fun listRuns(@RequestParam(defaultValue = "10") limit: Int): ResponseEntity<Any> {
        val runs = runRepo.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
        return ResponseEntity.ok(runs.map { run ->
            mapOf(
                "id" to run.id,
                "runType" to run.runType,
                "status" to run.status,
                "startedAt" to run.startedAt,
                "completedAt" to run.completedAt,
                "triggerSource" to run.triggerSource
            )
        })
    }

    @GetMapping("/runs/{id}/steps")
    fun getRunSteps(@PathVariable id: Long): ResponseEntity<Any> {
        val steps = stepRepo.findByRunId(id)
        return ResponseEntity.ok(steps.map { step ->
            mapOf(
                "id" to step.id,
                "stepName" to step.stepName,
                "status" to step.status,
                "recordsProcessed" to step.recordsProcessed,
                "recordsCreated" to step.recordsCreated,
                "recordsUpdated" to step.recordsUpdated,
                "recordsFailed" to step.recordsFailed,
                "startedAt" to step.startedAt,
                "completedAt" to step.completedAt
            )
        })
    }

    @GetMapping("/runs/{id}/errors")
    fun getRunErrors(@PathVariable id: Long): ResponseEntity<Any> {
        val errors = errorRepo.findByStepRunIdOrderByCreatedAtDesc(id, PageRequest.of(0, 100))
        return ResponseEntity.ok(errors.map { error ->
            mapOf(
                "id" to error.id,
                "errorType" to error.errorType,
                "errorCode" to error.errorCode,
                "errorMessage" to error.errorMessage,
                "createdAt" to error.createdAt
            )
        })
    }
}
```

- [ ] **Step 4: Verify ingestion-service compiles**

```bash
docker compose build ingestion-service
```

---

## Task 6: Admin Frontend (Proxy, Service, Page)

**Files:**
- Modify: `frontend/vite.config.ts`
- Rewrite: `frontend/src/services/adminService.ts`
- Rewrite: `frontend/src/pages/admin/AdminPage.tsx`

This is a large task. The implementer should use the HTML mockup at `tmp/current UI/admin-ingestion-design.html` as the visual reference. The page should match that design exactly.

- [ ] **Step 1: Add Vite proxy for ingestion service**

Add to the `proxy` section in `frontend/vite.config.ts`:

```typescript
'/ingestion-api': {
  target: 'http://localhost:8081',
  changeOrigin: true,
  rewrite: (path: string) => path.replace(/^\/ingestion-api/, ''),
},
```

- [ ] **Step 2: Rewrite adminService.ts**

Rewrite `frontend/src/services/adminService.ts` with new types and functions targeting the ingestion-service via `/ingestion-api` prefix. All functions use raw `fetch` (not `apiFetch` since the ingestion service has no auth/CSRF).

Key types: `IngestionStats` (with `instrumentsByType` map), `ActiveRun`, `IngestionRun`, `IngestionStep`, `IngestionError`, `TriggerResponse`.

Key functions: `getIngestionStats()`, `getActiveRun()`, `triggerExchangeSync()`, `triggerFullIngestion()`, `getIngestionRuns(limit)`, `getRunSteps(runId)`, `getRunErrors(runId)`.

- [ ] **Step 3: Rewrite AdminPage.tsx**

Complete rewrite matching the HTML mockup. Use React Query with `refetchInterval: 10000` for stats and active run polling. Include:
- Summary stats grid (6 cards)
- Instruments by type grid (6 cards)
- Workflow cards (Exchange Sync + Full Ingestion with progress)
- Recent Runs table with expandable steps and inline errors
- Toast notifications on trigger success/failure

- [ ] **Step 4: Verify frontend builds**

```bash
cd frontend && npm run build
```

---

## Task 7: Documentation Consolidation

**Files:**
- Move: `docs/api.md`, `docs/architecture.md`, `docs/deployment.md`, `docs/development.md`, `docs/improvements.md`, `docs/ingestion-workflow.md`
- Merge into: `docs/agent-reference/api-endpoints.md`, `docs/agent-reference/infrastructure.md`
- Delete: the 4 merged root files after content is absorbed

- [ ] **Step 1: Move standalone files**

```bash
mv docs/improvements.md docs/agent-reference/improvements.md
mv docs/ingestion-workflow.md docs/agent-reference/ingestion-workflow.md
```

- [ ] **Step 2: Merge api.md into api-endpoints.md**

Read `docs/api.md`, extract any content not already in `docs/agent-reference/api-endpoints.md` (base URLs, response format docs, status code reference), prepend it to api-endpoints.md. Then delete `docs/api.md`.

- [ ] **Step 3: Merge architecture.md + deployment.md + development.md into infrastructure.md**

Read all three files. Extract unique content from each:
- `architecture.md`: system diagram, component overview
- `deployment.md`: GCP setup, Terraform, CI/CD details
- `development.md`: local dev setup, prerequisites, Docker Compose

Merge into the appropriate sections of `docs/agent-reference/infrastructure.md`. Then delete the three source files.

- [ ] **Step 4: Verify docs/ root is clean**

```bash
ls docs/
```

Expected: only `business-context.html`, `agent-reference/`, `superpowers/`

---

## Task 8: Update All Agent Reference Files & CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`
- Modify: All 11 files in `docs/agent-reference/`

- [ ] **Step 1: Update CLAUDE.md**

Add to the Architecture section:
- Ingestion-service as a separate Spring Boot module (port 8081, `ingestion` PostgreSQL schema)
- Cross-schema reads from portfolio app
- RFC 7807 error handling standard

Add to Commands section:
```
docker compose build ingestion-service
docker compose logs -f ingestion-service
```

Add to API Routes table:
| Prefix | Controller | Auth | Service |
|--------|-----------|------|---------|
| `/admin/ingestion/**` | AdminIngestionController | None (ingestion-service) | Port 8081 |

Add to Environment variables:
| Variable | Description |
|----------|-------------|
| `EODHD_API_KEY` | EODHD API key for ingestion service |
| `INGESTION_ENABLED` | Enable/disable ingestion scheduler |
| `INGESTION_SCHEDULE` | Cron expression for nightly ingestion |

Add Database section note:
- `ingestion` schema: managed by ingestion-service Flyway
- Tables: exchanges, instruments, instrument_exchanges, provider_raw_data, provider_config, ingestion_runs/steps/errors

- [ ] **Step 2: Update all agent-reference files**

Update each file to reflect the current state of the codebase including:
- Ingestion-service module and its components
- Dashboard redesign (PortfolioSummaryWidget, ConnectedAccountsWidget rewrite, 4-col grid, no hero, no accent borders)
- Admin page rewrite for ingestion management
- Toast notification system
- Sidebar changes (Accounts link, refresh button in footer)
- RFC 7807 error handling
- New theme CSS variables

Files to update: `INDEX.md`, `api-endpoints.md`, `backend-services.md`, `frontend-map.md`, `infrastructure.md`, `configurations.md`, `entity-relationships.md`, `database-schema.md`, `ingestion-workflow.md`, `unused-legacy.md`.

- [ ] **Step 3: Mark completed items in improvements.md**

In `docs/agent-reference/improvements.md`, update these items:

- **1.4 Strategy Pattern for Data Providers**: Add `**COMPLETED**` tag. Note: "Implemented in ingestion-service with DataProvider interface, ProviderRegistry, EODHD adapter."
- **2.1 Error Handling Standardization**: Add `**COMPLETED**` tag. Note: "RFC 7807 ProblemDetail, domain exception hierarchy, centralized GlobalExceptionHandler in both services."
- **7.4 Health Check Expansion**: Add `**COMPLETED**` tag. Note: "Custom HealthIndicators for SnapTrade, EODHD, cross-service connectivity, daily quota monitoring."

- [ ] **Step 4: Update business-context.html**

Add to `docs/business-context.html`:
- Microservice architecture section (main backend + ingestion service)
- Ingestion pipeline documentation (Exchange Sync → Universe → Raw Data Fetch)
- Instrument types (STOCK, PREFERRED_STOCK, ETF, MUTUAL_FUND, INDEX, BOND)
- EODHD as primary data provider with rate limits and daily quota
- Data flow: EODHD → ingestion schema → portfolio app (cross-schema reads)

- [ ] **Step 5: Verify all docs are consistent**

Review each updated file to ensure no references to old architecture (single backend, zone-based dashboard, old ingestion system) remain.
