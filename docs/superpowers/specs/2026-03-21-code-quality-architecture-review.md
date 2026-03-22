# Code Quality, Architecture & Scalability Review

**Date:** 2026-03-21
**Repository:** Portfolio Construction App (Monorepo)
**Scope:** Full codebase — backend, frontend, infrastructure, cross-cutting concerns
**Codebase Size:** ~33K lines of source (18.4K Kotlin, 14.4K TypeScript), 213 Kotlin files, 164 TypeScript files

---

## 1. Executive Summary

**Overall Score: 8.2 / 10**

| Dimension | Score | Verdict |
|-----------|-------|---------|
| Architecture | 8.5/10 | Clean layered design, domain-driven packages, proper separation of concerns |
| Code Quality | 8.0/10 | Idiomatic Kotlin/TypeScript, consistent patterns, minor duplication |
| Security | 9.0/10 | AES-256-GCM encryption, JWT HS512, CSRF, Argon2id, non-root containers |
| Type Safety | 9.0/10 | Strict TypeScript, Kotlin `-Xjsr305=strict`, discriminated unions |
| DevOps/Infra | 8.5/10 | Multi-stage Docker, reusable CI, Terraform IaC, auto-rollback |
| Maintainability | 8.5/10 | Self-documenting code, consistent conventions, excellent CLAUDE.md |
| Scalability | 7.5/10 | Good foundations, needs caching strategy and lazy loading |
| Testing | 7.0/10 | Strong backend unit tests (25 files), frontend coverage thin (9 files) |

This is a well-engineered codebase with strong architectural decisions and consistent coding patterns. The backend is particularly mature. The main gaps are in test coverage (frontend), error handling completeness (both layers), caching strategy (backend), and observability.

---

## 2. Backend Audit

### 2.1 Controllers & API Design — 8.5/10

**Strengths:**
- Consistent `ResponseEntity<T>` returns with proper HTTP semantics
- `@PreAuthorize("isAuthenticated()")` applied at class level on all protected controllers
- `@AuthenticationPrincipal UserPrincipal` for clean user context extraction
- Clean REST naming conventions across all endpoints (`/api/v1/` prefix)
- Optional `connectionId` parameter pattern for single-account drill-down

**Findings:**
- **Debug endpoint in production:** `BrokerController` has `/brokers/debug-post` (lines 53-66) that exposes security context information. Must be removed or guarded with `@Profile("dev")`.
- **Hardcoded error responses:** `AuthController` line ~92 has hardcoded 401 response structure instead of using a standardized error DTO factory.

### 2.2 Service Layer — 8.0/10

**Strengths:**
- Sophisticated business logic well-encapsulated: LookThrough decomposition, drift calculation, risk scoring, time-weighted returns
- Proper `@Async` for external API calls (PositionFetchService)
- Consistent BigDecimal discipline with `RoundingMode.HALF_UP` and `setScale(2)`
- Coroutines used for AlphaVantage API calls with `awaitSingleOrNull`
- Resilience4j circuit breakers and rate limiting on external APIs

**Findings:**
- **Silent exception swallowing:** `DashboardDataService` lines ~158-178, ~250-254, ~332-336 catch exceptions with `log.warn()` and return fallback data. Users cannot tell if data is incomplete or stale. Should return a response envelope with quality/completeness metadata.
- **JSONB parsing duplication:** `ObjectMapper.readValue()` with `TypeReference` repeated 5+ times across `DashboardDataService` (lines ~197-210, ~716-729, ~740-752). Extract to a `CashJsonParser` utility.
- **Magic numbers:** Risk scoring formula (lines ~466-471) has hard-coded numeric constants. Extract to named constants or configuration.
- **God service:** `DashboardDataService` at 896 lines handles summary, cash, sectors, geography, risk, holdings, accounts, performance. Should be decomposed into focused sub-services.

### 2.3 Repository & Data Access — 9.0/10

**Strengths:**
- `JOIN FETCH` queries preventing N+1 across all major repositories
- Aggregation queries: `sumCurrentValueByConnectionId`, `countCurrentPositionsByConnectionId`
- Proper `@Modifying` operations: `markAllNonCurrent()`, `deleteByConnectionId()`
- Named queries with clear intent

**Findings:**
- First country/GICS lookup in DashboardDataService loops still hits DB despite `mutableMapOf` caching (line ~632-638). Consider batch-loading all countries upfront.

### 2.4 Entity & DTO Design — 8.5/10

**Strengths:**
- `FetchType.LAZY` on collections (BrokerConnection positions)
- Smart helper methods on entities: `isActive()`, `needsReauth()`, `markAsExpired()`, `clearError()`
- Sealed exception classes (`AuthException`) for type-safe error handling with error codes
- Companion object factories on DTOs: `StockDetailDto.from(stock)`
- Null-safe extraction chains with `takeIf` (Stock entity lines ~95-98)

**Findings:**
- JSONB `metadata` column on `BrokerConnection` (lines 75-77) has no schema validation. Consider dedicated fields for known metadata or JsonNode validation.
- `InstrumentDto.kt` at 532 lines is very large. Consider splitting into separate `StockDto.kt` and `EtfDto.kt` files.

### 2.5 Configuration & Dependency Injection — 9.0/10

**Strengths:**
- Environment variable substitution with defaults: `${REDIS_HOST:localhost}`
- Rich config sections for auth, ingestion, broker with type-safe `@ConfigurationProperties`
- `-Xjsr305=strict` for Java interop null safety
- Modern dependency versions: Spring Boot 3.3.5, Kotlin 2.0.21, JDK 21
- Constructor injection throughout (Kotlin primary constructor pattern)

**Findings:**
- No significant issues.

---

## 3. Frontend Audit

### 3.1 API Layer & Data Fetching — 9.0/10

**Strengths:**
- Centralized `apiFetch()` in `services/api.ts` (139 lines) handles CSRF, credentials, auto 401 retry
- Mutex-protected token refresh (lines 11-45) prevents concurrent 401 retry races
- Query key factory pattern in all hooks with hierarchical keys:
  ```typescript
  brokerKeys = {
    all: ['brokers'],
    connections: () => [...brokerKeys.all, 'connections'],
    positions: (id) => [...brokerKeys.connections(), id, 'positions'],
  }
  ```
- Appropriate cache TTLs: 30s for connections, 1m for positions
- Delayed invalidation (`setTimeout`) for async backend operations
- Automatic cache invalidation on mutations via `queryClient.invalidateQueries()`

**Findings:**
- No fetch timeout configuration — long-running requests hang indefinitely
- No request cancellation mechanism for component unmounts (AbortController)
- Retry header reconstruction (lines ~87-91) is manual instead of reusing the CSRF helper

### 3.2 State Management — 9.0/10

**Strengths:**
- 5 focused Zustand stores — no global state bloat
  - `stores/`: authStore, themeStore, sidebarStore
  - `store/`: analysisStore, portfolioStore
- Persist middleware with `partialize` to exclude transient state (`isLoading`, `csrfToken`)
- Selector hooks exported for fine-grained subscriptions
- Clean separation: React Query for server state, Zustand for client state only
- Weight normalization logic in portfolioStore (lines 52-61) is well-tested

**Findings:**
- Theme DOM manipulation lives inside the store (themeStore lines 10-16). Could be extracted to a custom hook, but this is minor.

### 3.3 Component Architecture — 7.5/10

**Strengths:**
- Shadcn-style UI primitives (`button.tsx`, `badge.tsx`, `dialog.tsx`) with `forwardRef` and variant props
- Zone-based dashboard layout (A: left 2/3, B: right 1/3, C: accounts, D: full-width)
- Suspense boundaries per widget zone in `DashboardGrid.tsx`
- `ProtectedRoute` with role-based access control and location state handling
- Session timeout warning with throttled activity tracking (30s throttle, 6hr timeout, 5min warning)

**Findings:**
- **No React Error Boundaries:** Widget failures in `DashboardGrid` cascade to the entire page. Each widget zone needs an error boundary.
- **No lazy loading:** All 15+ routes imported eagerly in `App.tsx`. Use `React.lazy()` + `Suspense` for code splitting.
- **Fragile async timing:** `setTimeout(2000)` in `DashboardGrid.tsx` (line ~96) for cache invalidation. Should use polling, websocket, or optimistic updates.
- **Hardcoded widget config:** `PositionsSummaryWidget.tsx` lines 6-14 has inline icon/color mappings. Should be config-driven.
- **Complex logic in component:** DashboardGrid preference merging logic (lines 57-67) should be extracted to a custom hook.

### 3.4 Type System & CSS/Styling — 9.0/10

**Strengths:**
- `strict: true` with `noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch`
- Discriminated unions for connection statuses, instrument types, widget keys
- Exhaustive `Record<StatusType, string>` mappings prevent missing cases
- CSS custom properties for theming with variant system on buttons (5 variants, 4 sizes)
- Responsive grid with named areas and mobile-first breakpoints (768px, 1024px, 1200px)
- `cn()` utility (clsx) for class composition
- Zero-warnings ESLint policy (`--max-warnings 0`)

**Findings:**
- ESLint config could be stricter: no import ordering rule, no `no-explicit-any` rule
- Some type interfaces duplicated across files (e.g., `CurrencyAmount` pattern)

---

## 4. Infrastructure Audit

### 4.1 Docker & Containerization — 8.5/10

**Strengths:**
- Multi-stage builds: Gradle dependency caching layer in backend Dockerfile (lines 1-16)
- Non-root user (UID 1001) with `appgroup`/`appuser` in backend runtime
- Alpine images for PostgreSQL and Redis
- Nginx production stage with gzip, SPA routing, security headers, immutable asset cache headers
- Health checks on all services with `depends_on: service_healthy` conditions
- Bridge network isolation

**Findings:**
- Debug port 5005 exposed in docker-compose.yml — acceptable for dev but should not exist in production compose
- No production-specific docker-compose override file visible

### 4.2 CI/CD Pipelines — 8.5/10

**Strengths:**
- Reusable `workflow_call` pattern: CI shared across both deploy workflows (DRY)
- Gradle + npm caching in CI with read-only cache on PRs
- Parallel frontend/backend test jobs
- VPS auto-rollback on failed deployment with diagnostic output (lines 213-242)
- Artifact preservation: test results, JAR, frontend dist all uploaded

**Findings:**
- **No post-deployment health check** in GCP deploy workflow — deployment considered successful without verification
- Secrets written to `.env` file via echo on VPS deploy (lines 144-154) — consider Docker secrets
- No code coverage reports published in CI

### 4.3 Terraform / IaC — 8.0/10

**Strengths:**
- Modular structure: `cloud-run`, `workload-identity` modules
- Startup/liveness probes configured (30s timeout, 10s period, 3 failures)
- Cloud SQL proxy sidecar (v2.8.0) with `--private-ip`
- Keyless OIDC auth via Workload Identity Federation (GitHub OIDC provider)
- Least-privilege IAM roles per service account

**Findings:**
- No environment-specific Terraform configurations visible
- Hard-coded secret IDs in variables

### 4.4 Database & Migrations — 9.0/10

**Strengths:**
- 53 Flyway migration files (V1 through V57, gaps at V4/V5/V19/V20)
- `CHECK` constraints on enum columns (auth_type, status)
- `TIMESTAMPTZ` used consistently throughout
- `DECIMAL(7,4)` for percentage precision
- `COMMENT` on tables for schema documentation
- Indexes on foreign keys, composite unique constraints
- `updated_at` triggers for automatic timestamp maintenance
- Seed data included in migrations (brokers: QUESTRADE, IBKR, WEALTHSIMPLE)

**Findings:**
- No rollback procedures documented for any migration
- V52 is a data-destructive truncate (appropriate for dev reset but risky)

---

## 5. Cross-Cutting Concerns

### 5.1 Security — 9.0/10

**Strengths:**
- AES-256-GCM with random IV per encryption, Base64 key with length validation
- JWT HS512 with 64-char minimum key enforcement, HttpOnly cookies
- Access token: 60min, refresh token: 6hr
- Argon2id password hashing via BouncyCastle with account lockout (5 failures / 30min)
- CSRF via `CookieCsrfTokenRepository`, frontend sends `X-XSRF-TOKEN` header
- Stateless sessions (no server-side session state)
- Non-root Docker containers
- Google OAuth2 integration
- Sealed exception classes prevent information leakage

**Findings:**
- Debug endpoint `/brokers/debug-post` exposes security context info
- CSRF disabled for `/api/**` — intentional but should be documented with rationale
- No rate limiting on auth endpoints visible (lockout helps but doesn't prevent credential stuffing at scale)

### 5.2 Error Handling & Resilience — 6.5/10

**Strengths:**
- Sealed `AuthException` hierarchy with error codes (8 specific exception types)
- Resilience4j circuit breakers on external APIs
- SnapTrade adapter graceful degradation (empty lists on failure)
- `AlphaVantageRateLimiter` with quota management and walk-back for weekends/holidays
- `MAX_NESTED_DEPTH = 2` recursion guard in LookThroughService

**Findings:**
- **No `@ControllerAdvice` global exception handler** — errors propagate as Spring default error pages with no standardized format
- **No React Error Boundaries** — component failures cascade to entire page
- DashboardDataService silently catches exceptions (~3 locations) returning fallback data with no indication to users
- No structured error response envelope across the API (different controllers return errors differently)

### 5.3 Testing — 7.0/10

**Strengths:**
- MockK (Kotlin-native) for unit tests — better null handling than Mockito
- Testcontainers (PostgreSQL) for integration tests with `@DynamicPropertySource`
- Comprehensive drift calculation tests with edge cases (perfect balance, underweight, excluded assets, empty portfolio)
- Coroutines testing with `runBlocking`
- Zustand store tests with `toBeCloseTo` for floating-point precision
- Proper mock isolation with `beforeEach`/`afterEach`

**Findings:**
- **25 backend test files vs 9 frontend test files** — significant coverage gap
- No integration tests for React Query hooks
- No E2E test suite (Playwright/Cypress)
- No code coverage metrics in CI (JaCoCo or c8/istanbul)
- API integration tests cover only 3 basic smoke endpoints

### 5.4 Performance & Caching — 7.0/10

**Strengths:**
- Hikari connection pooling configured
- `FetchType.LAZY` on entity collections
- `JOIN FETCH` in repository queries preventing N+1
- `ConcurrentHashMap` cache in ExchangeRateService
- Async position fetching via `@Async`
- Rate limiting on external APIs (AlphaVantage, EODHD)
- Batch limits: AlphaVantage client limits batch to 75

**Findings:**
- **Redis deployed but barely used** — only ingestion hash cache. No `@Cacheable`/`@CacheEvict` annotations anywhere.
- LookThrough calculations are deterministic and called frequently but never cached
- No lazy loading on frontend routes (15+ pages loaded eagerly in App.tsx)
- No bundle analysis or code splitting strategy
- Large dashboard calculations run in single transactions (potential lock contention)

### 5.5 Observability & Logging — 6.0/10

**Strengths:**
- Micrometer + Prometheus metrics configured
- Health, info, metrics actuator endpoints exposed
- SLF4J logging via `LoggerFactory.getLogger(javaClass)` throughout backend

**Findings:**
- **No `logback-spring.xml`** — no structured logging configuration for production
- No JSON log format for log aggregation services
- No per-environment log level configuration visible
- No APM integration (Datadog, New Relic, Sentry)
- Frontend has minimal logging (console.error only)
- No distributed tracing (correlation IDs across requests)

---

## 6. Strengths to Preserve

These patterns are working well and should be replicated as the codebase grows:

1. **DTO boundary discipline** — Entities never leak to the API. Companion object factories (`from(entity)`) keep conversion clean and testable.
2. **React Query + Zustand separation** — Server state in React Query, client state in Zustand. No ownership confusion.
3. **Query key factories** — Hierarchical keys prevent cache collisions and enable targeted invalidation.
4. **Mutex-protected token refresh** — Prevents concurrent 401 retry races. Sophisticated and correct.
5. **Sealed exception classes** — Type-safe error handling with error codes. Prevents catch-all `Exception` anti-patterns.
6. **Repository JOIN FETCH** — Explicit eager loading where needed, lazy by default. N+1 mostly avoided.
7. **Multi-stage Docker builds** — Dependency caching layer, non-root runtime, Alpine base images.
8. **Reusable CI workflows** — `workflow_call` prevents duplication across deploy workflows.
9. **Flyway migration quality** — CHECK constraints, comments, indexes, triggers, proper types.
10. **CSS custom properties + variant system** — Consistent theming without framework lock-in. Shadcn-style primitives are reusable.

---

## 7. Prioritized Improvement Roadmap

**Effort legend:** Trivial = < 1 hour | S = 1-2 days | M = 3-5 days | L = 1-2 weeks

### P0 — Critical (fix immediately)

| # | Item | Effort | Impact | Layer |
|---|------|--------|--------|-------|
| 1 | Add `@ControllerAdvice` global exception handler with standardized error DTO | S | High | Backend |
| 2 | Add React Error Boundaries around dashboard widget zones | S | High | Frontend |
| 3 | Remove or `@Profile("dev")` guard `/brokers/debug-post` endpoint | Trivial | High | Security |

### P1 — Important (next sprint)

| # | Item | Effort | Impact | Layer |
|---|------|--------|--------|-------|
| 4 | Add lazy loading (`React.lazy` + `Suspense`) to all routes in App.tsx | S | Medium | Frontend |
| 5 | Expand frontend test coverage — hooks, key components, services (~30+ files) | L | High | Testing |
| 6 | Add `@Cacheable` on LookThrough, exchange rates, and reference data lookups | M | High | Performance |
| 7 | Replace silent exception catches in DashboardDataService with quality-metadata response envelope | M | Medium | Error Handling |

### P2 — Moderate (next 2-3 sprints)

| # | Item | Effort | Impact | Layer |
|---|------|--------|--------|-------|
| 8 | Add JaCoCo (backend) + c8 (frontend) coverage reports to CI | S | Medium | Testing |
| 9 | Decompose DashboardDataService (896 lines) into focused sub-services | M | Medium | Maintainability |
| 10 | Add `logback-spring.xml` with JSON format + per-environment log levels | S | Medium | Observability |
| 11 | Add rate limiting on auth endpoints (`/auth/login`, `/auth/signup`) | S | Medium | Security |
| 12 | Extract JSONB parsing duplication into a shared utility | S | Low | Maintainability |
| 13 | Add post-deployment health check to GCP deploy workflow | S | Medium | DevOps |

### P3 — Minor (backlog)

| # | Item | Effort | Impact | Layer |
|---|------|--------|--------|-------|
| 14 | Extract magic numbers in risk scoring to named constants or config | S | Low | Maintainability |
| 15 | Add E2E test suite (Playwright) for critical flows | L | Medium | Testing |
| 16 | Add Sentry or similar for frontend error tracking | S | Medium | Observability |
| 17 | Move VPS deploy secrets from `.env` echo to Docker secrets | M | Low | Security |
| 18 | Add stricter ESLint rules (import ordering, `no-explicit-any`) | S | Low | Code Quality |
| 19 | Split InstrumentDto (532 lines) into separate Stock/ETF DTO files | S | Low | Maintainability |
| 20 | Add request timeout + cancellation (AbortController) to `apiFetch()` | S | Low | Resilience |

### Suggested Sprint Grouping

- **Sprint 1 (Quick wins):** Items 1-4 — all small effort, high/medium impact, immediately improves reliability
- **Sprint 2 (Caching + Error quality):** Items 6-7, 9-10 — performance and maintainability step-change
- **Sprint 3 (Test coverage):** Items 5, 8, 15 — largest effort but highest long-term confidence gain
- **Sprint 4 (Hardening):** Items 11, 13, 16, 17 — production readiness polish

---

## Appendix: Key Files Referenced

| File | Lines | Role |
|------|-------|------|
| `backend/.../DashboardDataService.kt` | 896 | Largest service, orchestrates all widget data |
| `backend/.../SecurityConfig.kt` | 111 | Spring Security configuration |
| `backend/.../TokenEncryptionService.kt` | ~150 | AES-256-GCM broker token encryption |
| `backend/.../JwtTokenProvider.kt` | 110 | JWT generation and validation |
| `backend/.../AuthExceptions.kt` | 49 | Sealed exception hierarchy |
| `backend/.../BrokerPositionRepository.kt` | ~50 | JOIN FETCH query patterns |
| `backend/.../DriftCalculationService.kt` | 217 | Drift and accuracy calculation |
| `frontend/src/services/api.ts` | 139 | Centralized API abstraction |
| `frontend/src/hooks/useBrokerConnections.ts` | 162 | React Query hook patterns |
| `frontend/src/stores/authStore.ts` | 93 | Auth state management |
| `frontend/src/components/dashboard/DashboardGrid.tsx` | 180 | Widget zone layout |
| `frontend/src/App.tsx` | 127 | Router and route guards |
| `docker-compose.yml` | ~100 | Local development stack |
| `.github/workflows/ci.yml` | ~120 | Reusable CI workflow |
| `backend/src/main/resources/application.yml` | 136 | Backend configuration |
