# Portfolio Construction App - Improvement Recommendations

> Comprehensive audit of the repository identifying opportunities for improvement across
> architecture, code quality, security, infrastructure, monitoring, and scalability.
> Organized by priority and effort level.

---

## Table of Contents

1. [Architecture & Design Patterns](#1-architecture--design-patterns)
2. [Backend Improvements](#2-backend-improvements)
3. [Frontend Improvements](#3-frontend-improvements)
4. [Database Design](#4-database-design)
5. [Security Hardening](#5-security-hardening)
6. [Infrastructure & DevOps](#6-infrastructure--devops)
7. [Monitoring, Alerting & Self-Healing](#7-monitoring-alerting--self-healing)
8. [Scalability & Performance](#8-scalability--performance)
9. [Technology Modernization](#9-technology-modernization)
10. [Code Cleanup & Technical Debt](#10-code-cleanup--technical-debt)

---

## 1. Architecture & Design Patterns

### 1.1 Domain-Driven Design Alignment
**Priority: Medium | Effort: High**

The current package structure mixes domain concerns. The `broker/` package contains portfolio management, trading, dashboard, notifications, and performance — concepts that are distinct bounded contexts.

**Current state:**
```
com.portfolio.broker/
  ├── controller/   # BrokerController, DashboardController, TradingController,
  │                 # PerformanceController, PortfolioGroupController, etc.
  ├── entity/       # BrokerConnection, PortfolioGroup, TradeOrder,
  │                 # DashboardPreference, Notification, etc.
  └── service/      # BrokerService, DashboardDataService, OrderExecutionService,
                    # PerformanceCalculationService, etc.
```

**Recommended restructure:**
```
com.portfolio/
  ├── auth/          # Authentication (already well-isolated)
  ├── broker/        # Pure brokerage: connections, positions, sync
  ├── portfolio/     # Groups, targets, drift, snapshots
  ├── trading/       # Orders, execution, rebalancing
  ├── dashboard/     # Widgets, preferences, data aggregation
  ├── performance/   # TWR calculation, benchmarks, charts
  ├── notification/  # Notifications, preferences
  ├── ingestion/     # Data pipeline (already well-isolated)
  ├── instrument/    # Stocks, ETFs, reference data
  └── shared/        # Cross-cutting: exchange rates, risk metrics, look-through
```

This aligns code with business domains, making it easier to reason about, test, and evolve independently.

### 1.2 Event-Driven Architecture for Async Operations
**Priority: Medium | Effort: Medium**

Several operations are currently synchronous but would benefit from event-driven patterns:

- **Position sync** — After a broker sync completes, downstream services (dashboard cache invalidation, drift recalculation, snapshot update) should react via events rather than being called sequentially.
- **Activity ingestion** — New activities could trigger portfolio cash flow updates, notification creation, and performance recalculation via domain events.
- **Rebalance triggers** — Instead of the scheduler polling every group, drift threshold crossings could emit events.

**Approach:** Start with Spring's `ApplicationEventPublisher` for in-process events. If inter-service communication becomes needed, introduce a message broker (Redis Streams or RabbitMQ).

### 1.3 Command/Query Separation (CQRS-lite)
**Priority: Low | Effort: Medium**

Some services mix read and write concerns heavily. For example, `DashboardDataService` aggregates data from multiple sources for reads, while `PortfolioGroupService` handles both CRUD and complex calculations. Separating query services from command services would improve testability and allow independent scaling of read-heavy paths.

### 1.4 Strategy Pattern for Data Providers
**Priority: Medium | Effort: Low** -- **COMPLETED**

Implemented in `ingestion-service` with `DataProvider` interface, `ProviderRegistry`, and EODHD adapter. The interface defines `name()`, `capabilities()`, `fetchExchanges()`, `fetchUniverse()`, and `fetchFundamentals()` methods. New providers can be added by implementing the interface and registering in the `ProviderRegistry`.

---

## 2. Backend Improvements

### 2.1 Error Handling Standardization
**Priority: High | Effort: Medium** -- **COMPLETED**

RFC 7807 ProblemDetail responses implemented in both services. Domain exception hierarchy (`AppException` -> `NotFoundException`, `ConflictException`, `ValidationException`, `ForbiddenException`, `RateLimitException`, `ExternalServiceException`, `InternalException`) centralized in `GlobalExceptionHandler` in both backend and ingestion-service. Auth exceptions refactored to extend `AppException` subclasses. Frontend has `ApiError` class and `parseErrorResponse()` in `services/api.ts` with toast notification system.

### 2.2 API Versioning Strategy
**Priority: Medium | Effort: Low**

Currently all endpoints use `/api/v1/`. There's no strategy for introducing breaking changes.

**Recommendations:**
- Document the versioning policy (URL-based as currently used is fine)
- Add API deprecation headers for endpoints being phased out
- Consider implementing content negotiation via `Accept` header as an alternative for minor versions

### 2.3 Pagination Consistency
**Priority: Medium | Effort: Low**

Some endpoints use Spring's `Pageable` with page/size parameters, others use custom pagination. The response format varies.

**Recommendations:**
- Standardize on a single `PagedResponse<T>` wrapper: `{ content: T[], page: int, size: int, totalElements: long, totalPages: int }`
- Ensure all list endpoints support pagination (some currently return unbounded lists)
- Add default page size limits (current max 100 is good, but not all endpoints enforce it)

### 2.4 Validation Enhancement
**Priority: Medium | Effort: Low**

Use `@Valid` and Bean Validation annotations consistently:
- Some request DTOs lack validation annotations
- Custom validators for complex business rules (e.g., portfolio weights summing to 100%)
- Add `@Size`, `@NotBlank`, `@Min`, `@Max` to all request DTOs

### 2.5 Coroutine Usage Optimization
**Priority: Low | Effort: Medium**

The project includes `kotlinx-coroutines` but usage could be expanded:
- `DashboardDataService` makes multiple independent data fetches that could run concurrently with `async/await`
- Position fetching across multiple connections could use `coroutineScope` for structured concurrency
- Consider adding `@Async` or coroutine dispatchers for scheduled tasks

### 2.6 Repository Query Optimization
**Priority: Medium | Effort: Low**

- Add `@EntityGraph` annotations to avoid N+1 queries on common fetch patterns
- Use projections/DTOs in JPQL queries instead of fetching full entities when only a few fields are needed
- Add query result caching for frequently accessed reference data (GICS hierarchy, countries)
- Consider using `@QueryHints` for read-only queries

### 2.7 Service Layer Testing
**Priority: High | Effort: Medium**

Expand test coverage:
- Most services have thin or no unit tests
- Add integration tests for critical flows (position sync, rebalance calculation, performance TWR)
- Add tests for edge cases (empty portfolios, single-position groups, currency conversion)
- Use Testcontainers for repository-level integration tests

---

## 3. Frontend Improvements

### 3.1 Component Composition & Reusability
**Priority: Medium | Effort: Medium**

Several dashboard widgets share similar patterns (data fetching, loading states, error display) but implement them independently.

**Recommendations:**
- Extract a `WidgetShell` component that handles loading/error/empty states
- Use render props or compound components for shared widget patterns
- Consider a `useWidget` hook that wraps React Query with standard widget behavior

### 3.2 Form Management
**Priority: Medium | Effort: Medium**

Forms (login, signup, portfolio builder, settings) use raw useState for form state. This leads to duplicated validation logic and inconsistent error display.

**Recommendations:**
- Adopt `react-hook-form` for form state management and validation
- Create reusable form field components that integrate with the form library
- Standardize client-side validation messages
- Add optimistic UI updates for mutations

### 3.3 Accessibility (a11y)
**Priority: High | Effort: Medium**

The application needs accessibility improvements:
- Add ARIA labels to interactive elements (especially icon-only buttons)
- Ensure keyboard navigation works for all interactive components
- Add proper focus management for modals and sheets
- Use semantic HTML elements (`<nav>`, `<main>`, `<aside>`, `<article>`)
- Ensure color contrast ratios meet WCAG 2.1 AA standards
- Add skip navigation links
- Test with screen readers (NVDA, VoiceOver)

### 3.4 Performance Optimization
**Priority: Medium | Effort: Low**

- **Code splitting**: Some pages import heavy dependencies (AG Grid, AG Charts) that could be lazy-loaded. Add `React.lazy()` for pages that use these libraries.
- **Memoization**: Dashboard widgets should use `React.memo()` and `useMemo()` to prevent unnecessary re-renders when other widgets update
- **Virtual scrolling**: Large position/holdings lists should use AG Grid's virtual scrolling (already available) consistently
- **Image optimization**: Broker logos should be lazy-loaded with proper dimensions

### 3.5 Error Boundary Coverage
**Priority: Medium | Effort: Low**

Currently there's one `ErrorBoundary` component, but it's not clear if it wraps all routes. Each major feature area (dashboard, broker, portfolio) should have its own error boundary to prevent cascade failures.

### 3.6 State Management Refinement
**Priority: Low | Effort: Low**

- The `portfolioStore` and `analysisStore` could potentially be merged or coordinated better
- Consider using React Query's built-in mutation state instead of Zustand for transient UI state
- Add devtools integration for Zustand stores in development

### 3.7 Testing Coverage
**Priority: High | Effort: High**

Current test coverage is thin (14 test files for ~100+ components):
- Add tests for all custom hooks (mock React Query)
- Add component tests for critical user flows (login, broker connection, portfolio creation)
- Add integration tests for key pages (dashboard rendering, position display)
- Consider adding end-to-end tests with Playwright or Cypress for critical paths

### 3.8 CSS Organization
**Priority: Low | Effort: Medium**

While the CSS custom properties system works, consider:
- Audit CSS custom properties for unused variables
- Ensure consistent naming convention across all CSS files
- Add CSS linting (stylelint) to the build pipeline
- Consider CSS modules for better scoping (prevents style leaks between components)

---

## 4. Database Design

### 4.1 Table Cleanup
**Priority: High | Effort: Low**

Remove unused tables (see [unused-legacy.md](unused-legacy.md)):
- `external_connections` and `external_connection_tokens` — no entity, no code, 0 rows
- `app_metadata` — no entity, no service, 1 row with unknown purpose
- `fund_sector_allocations` — entity exists but no service uses it; superseded by `etf_sector_allocations_factset`

Create a Flyway migration to drop these tables after confirming they're truly unused.

### 4.2 Index Optimization
**Priority: Medium | Effort: Low**

- **Duplicate indexes**: `countries` has both a UNIQUE constraint on `code` and a separate `idx_countries_code` index — the unique constraint already creates an index
- **Missing composite indexes**: `trade_orders` could benefit from `(user_id, status)` for filtering active orders
- **Partial index opportunities**: `broker_positions WHERE is_current = true` exists but similar patterns could be applied to `trade_orders WHERE status = 'PENDING'`
- Run `pg_stat_user_indexes` to identify unused indexes

### 4.3 Table Partitioning
**Priority: Low | Effort: High**

For tables that grow significantly:
- `broker_activities` (6.2MB, will grow) — partition by `trade_date` (monthly)
- `ingestion_errors` (15MB) — partition by ingestion run or date
- `broker_positions` — partition by `is_current` (hot/cold separation)
- `etf_holdings` — partition by `as_of_date`

### 4.4 Data Retention Policy
**Priority: Medium | Effort: Low**

Define and implement retention policies:
- `audit_log` — archive/delete entries older than 90 days
- `ingestion_errors` — purge resolved errors older than 30 days
- `position_fetch_log` — keep only last 30 days
- `refresh_tokens` — clean up expired tokens (scheduled job)
- `email_verification_tokens` / `password_reset_tokens` — clean up expired tokens

### 4.5 Database Constraints
**Priority: Medium | Effort: Low**

- Add CHECK constraints where business rules apply (e.g., `target_percent BETWEEN 0 AND 100`)
- Add NOT NULL constraints where fields should never be null
- Consider adding database-level triggers for auditing changes to critical tables

### 4.6 Query Performance
**Priority: Medium | Effort: Medium**

- Enable `pg_stat_statements` for query performance monitoring
- Add connection pooling with PgBouncer between application and database for production
- Consider materialized views for expensive aggregations (portfolio total values, sector allocations)
- Use `EXPLAIN ANALYZE` on slow queries identified through logging

---

## 5. Security Hardening

### 5.1 API Rate Limiting
**Priority: High | Effort: Medium**

No rate limiting exists on public-facing API endpoints. This is critical:
- Add rate limiting to auth endpoints (login, signup, forgot-password) — max 5-10 attempts per minute per IP
- Add general rate limiting to authenticated API endpoints — max 100-200 requests per minute per user
- Use `resilience4j-ratelimiter` (already a dependency) or Spring Cloud Gateway
- Return `429 Too Many Requests` with `Retry-After` header

### 5.2 Input Validation & Sanitization
**Priority: High | Effort: Medium**

- Audit all `@RequestBody` DTOs for proper validation annotations
- Sanitize string inputs to prevent XSS in stored data
- Validate path parameters and query parameters (symbols, connection IDs)
- Add input length limits to prevent payload attacks
- Validate file uploads if any are planned

### 5.3 CORS Tightening
**Priority: Medium | Effort: Low**

- Production CORS should list exact allowed origins, not wildcards
- Remove `credentials: true` if not needed for specific endpoints
- Add `Access-Control-Max-Age` to reduce preflight requests

### 5.4 Secret Management
**Priority: High | Effort: Medium**

- **Secret rotation**: Implement automated JWT signing key rotation
- **Encryption key management**: BROKER_ENCRYPTION_KEY should be rotated periodically with key versioning
- **Audit secrets in environment**: Ensure no secrets are hardcoded or in committed files
- Use Cloud Secret Manager (already imported as dependency) in production instead of environment variables
- Add secret scanning to CI pipeline (e.g., `trufflehog`, `gitleaks`)

### 5.5 Authentication Enhancements
**Priority: Medium | Effort: Medium**

- Add multi-factor authentication (TOTP via Google Authenticator)
- Implement session revocation (allow users to see and terminate active sessions)
- Add brute-force detection beyond simple lockout (IP-based blocking, CAPTCHA integration)
- Consider adding WebAuthn/passkey support for passwordless login
- Add audit logging for all security-sensitive operations (already partially done)

### 5.6 Security Headers
**Priority: Medium | Effort: Low**

Add/verify security headers in both Nginx and Spring Boot:
- `Content-Security-Policy` — restrict script sources, prevent XSS
- `Permissions-Policy` — disable unused browser features (camera, geolocation)
- `X-Content-Type-Options: nosniff` (already present in Nginx)
- `Strict-Transport-Security` with `includeSubDomains` (already present)
- `X-Frame-Options: DENY` for API endpoints (already `SAMEORIGIN` in Nginx)

### 5.7 Dependency Vulnerability Scanning
**Priority: High | Effort: Low**

- Add `org.owasp:dependency-check-gradle` plugin for backend
- Add `npm audit` to CI pipeline for frontend
- Consider Snyk or Dependabot for automated vulnerability alerts
- Pin dependency versions to avoid supply chain attacks

---

## 6. Infrastructure & DevOps

### 6.1 Container Orchestration Readiness
**Priority: Medium | Effort: High**

For production readiness:
- Add Kubernetes manifests (Deployments, Services, Ingress, ConfigMaps, Secrets)
- Implement health check endpoints that Kubernetes can probe:
  - `/health/live` (liveness — app is running)
  - `/health/ready` (readiness — app can serve traffic, DB connected)
  - `/health/startup` (startup — initial data loaded)
- Add resource limits and requests
- Implement graceful shutdown hooks

### 6.2 Blue/Green Deployments
**Priority: Medium | Effort: Medium**

Implement zero-downtime deployment:
- Implement blue/green with Nginx upstream switching
- Or use Docker Swarm for rolling updates
- Consider load balancer health check integration

### 6.3 Database Backup Automation
**Priority: High | Effort: Low**

- Verify automated daily PostgreSQL backups are running (pg_dump + upload to cloud storage)
- Test backup restoration monthly
- Add point-in-time recovery capability
- Document restoration procedure

### 6.4 Infrastructure as Code Improvements
**Priority: Medium | Effort: Low**

- Enable Terraform remote state backend (currently commented out)
- Add Terraform plan/apply automation via CI/CD
- Add `terraform validate` and `tflint` to CI pipeline
- Consider Terragrunt for DRY configuration across environments

### 6.5 CI/CD Pipeline Enhancements
**Priority: Medium | Effort: Medium**

- Add parallel test execution for faster CI
- Cache Docker layers more aggressively
- Add deployment approval gates for production
- Add smoke tests after deployment (hit key endpoints, verify responses)
- Add canary deployment support
- Consider adding performance regression tests to CI

### 6.6 Development Environment
**Priority: Low | Effort: Low**

- Add a `docker-compose.test.yml` for running integration tests locally
- Add pre-commit hooks for code formatting and linting
- Consider adding a dev container definition (`.devcontainer/`) for VS Code/Codespaces

---

## 7. Monitoring, Alerting & Self-Healing

### 7.1 Prometheus Alerting Rules
**Priority: High | Effort: Medium**

Define and implement alert rules for:
- **Application health**: Service down for > 1 minute
- **Error rates**: HTTP 5xx rate exceeds 5% of traffic over 5 minutes
- **Latency**: P95 response time exceeds 2 seconds
- **Database**: Connection pool exhaustion (active connections > 8 of 10)
- **External APIs**: SnapTrade/EODHD/AlphaVantage error rate spike
- **Disk/Memory**: Container memory usage > 80%
- **Queue depth**: Pending ingestion items growing

### 7.2 Grafana Dashboards
**Priority: Medium | Effort: Medium**

Create dashboards for:
- **Application Overview**: Request rate, error rate, latency percentiles, active users
- **Database**: Query duration, connection pool stats, slow queries, table sizes
- **Broker Integration**: SnapTrade API latency, sync success rate, position count
- **Ingestion Pipeline**: Run duration, items processed, errors by type, rate limiter state
- **Infrastructure**: Container CPU/memory, Redis hit rate, disk I/O

### 7.3 Structured Logging Enhancement
**Priority: Medium | Effort: Low**

Logstash encoder is already configured. Enhance:
- Add correlation IDs to all requests (MDC filter)
- Add user ID to all authenticated request logs
- Add operation duration logging for service methods
- Standardize log levels: ERROR (actionable failures), WARN (degraded but functioning), INFO (business events), DEBUG (technical details)
- Add log aggregation: ship logs to ELK Stack (Elasticsearch + Logstash + Kibana) or Loki + Grafana

### 7.4 Health Check Expansion
**Priority: High | Effort: Low** -- **COMPLETED**

Custom `HealthIndicator` beans implemented in both services. Backend: `SnapTradeHealthIndicator` (checks SnapTrade API connectivity), `IngestionServiceHealthIndicator` (checks cross-service connectivity to ingestion-service on port 8081). Ingestion service: `EodhdHealthIndicator` (checks EODHD API reachability), `QuotaHealthIndicator` (monitors daily API quota usage and reports DOWN when quota is exhausted).

### 7.5 Circuit Breaker Monitoring
**Priority: Medium | Effort: Low**

Resilience4j is configured but circuit breaker metrics should be:
- Exported to Prometheus (via Micrometer integration — already available)
- Visualized in Grafana
- Alerting when circuits open (indicates persistent downstream failure)

### 7.6 Dead Letter Queue for Ingestion
**Priority: Medium | Effort: Medium**

When ingestion items fail after retries:
- Store failed items in a dead letter table (partially done via `ingestion_errors`)
- Add an admin endpoint to view and retry failed items
- Add automated notification when DLQ depth exceeds threshold
- Add exponential backoff retry scheduler for DLQ items

### 7.7 Automated Error Notification
**Priority: High | Effort: Medium**

Implement notification channels for critical errors:
- **Slack webhook**: Send alerts for 5xx errors, circuit breaker opens, deployment failures
- **Email**: Daily digest of error summary, weekly system health report
- **PagerDuty/Opsgenie**: On-call rotation for production-critical alerts (optional)
- Add notification preferences per admin user (which alerts, which channels)

Implementation approach:
```
ErrorEvent → NotificationRouter → [SlackChannel, EmailChannel, PagerDutyChannel]
```

### 7.8 Self-Healing Patterns
**Priority: Medium | Effort: High**

Implement automated recovery:
- **Auto-restart**: Docker `restart: unless-stopped` (already configured for VPS)
- **Auto-reconnect**: Database connection pool auto-recovery (HikariCP handles this)
- **Stale connection cleanup**: Scheduled job to detect and reset stuck broker connections
- **Cache warming**: After restart, pre-warm Redis cache for active users
- **Ingestion recovery**: Auto-retry failed ingestion runs after a delay
- **Token cleanup**: Scheduled job to purge expired refresh/verification/reset tokens
- **Health-based routing**: If backend fails health checks, Nginx should show maintenance page
- **Auto-scaling**: Cloud Run already supports this; for VPS, consider Docker Swarm with replicas

---

## 8. Scalability & Performance

### 8.1 Horizontal Scaling Readiness
**Priority: Medium | Effort: Medium**

Current blockers for horizontal scaling:
- **Stateless**: Application is already stateless (JWT, no server sessions) — good
- **Redis sessions**: Already using Redis for caching — can be shared across instances
- **Scheduled tasks**: Schedulers will run on every instance. Use `@SchedulerLock` (ShedLock library) with Redis/DB backend to ensure single execution
- **File-based state**: None identified — good

### 8.2 Caching Strategy
**Priority: Medium | Effort: Medium**

Current Redis caching is limited. Expand to:
- **ETF holdings**: Cache by ETF ID + date (frequently accessed, rarely changes)
- **GICS hierarchy**: Cache indefinitely (static reference data)
- **User positions**: Cache with 30-second TTL (frequently polled by dashboard)
- **Instrument search results**: Cache common queries
- **Dashboard widget data**: Cache per-user widget responses with configurable TTL
- Use cache invalidation on data changes (position sync, activity ingestion)

### 8.3 CDN for Frontend Assets
**Priority: Medium | Effort: Low**

- Add Cloudflare or similar CDN in front of Nginx
- Configure aggressive caching for versioned assets (JS, CSS with content hashes)
- Set proper `Cache-Control` headers
- Leverage Cloudflare Tunnel integration

### 8.4 Database Read Replicas
**Priority: Low | Effort: High**

For high-traffic production:
- Configure PostgreSQL read replicas
- Configure Spring Data JPA `@Transactional(readOnly = true)` to route to replica
- Or use connection pool routing with separate read/write datasources

### 8.5 Async Processing with Message Queue
**Priority: Medium | Effort: High**

For operations that don't need synchronous response:
- **Position sync**: Queue sync requests, process asynchronously
- **Activity ingestion**: Process in background after trigger
- **Notification delivery**: Queue notifications for async delivery
- **Report generation**: Generate reports in background, notify when ready

Options: Redis Streams (minimal infra change), RabbitMQ, or cloud-based message queue.

### 8.6 API Response Optimization
**Priority: Medium | Effort: Low**

- Add HTTP compression (gzip) for API responses (Spring Boot: `server.compression.enabled=true`)
- Use DTOs consistently to avoid over-fetching (never return full entities)
- Add `ETag` and `If-None-Match` support for cacheable responses
- Consider GraphQL for the dashboard to reduce over-fetching of widget data

---

## 9. Technology Modernization

### 9.1 Spring Boot Upgrade Path
**Priority: Medium | Effort: Medium**

Current: Spring Boot 3.3.5. Latest stable: 3.4.x.
- 3.4 brings: structured logging support, improved Docker Compose integration, virtual threads support
- Plan migration: update Boot version, test, fix deprecations
- Consider enabling virtual threads for better I/O concurrency (Project Loom)

### 9.2 React 19 Readiness
**Priority: Low | Effort: Medium**

Current: React 18.3.1. React 19 brings:
- Server Components (if SSR is ever considered)
- Improved Suspense
- `use()` hook for promises
- Automatic memoization (React Compiler)
- Plan: wait for ecosystem stability (React Query, React Router compatibility)

### 9.3 Vite 6 Migration
**Priority: Low | Effort: Low**

Current: Vite 5.4.9. Vite 6 brings:
- Environment API for better SSR support
- Improved build performance
- Usually a smooth upgrade

### 9.4 AG Grid/Charts Updates
**Priority: Low | Effort: Low**

Current: AG Grid 32.3.3, AG Charts 10.3.3.
- Check for newer versions with bug fixes and performance improvements
- Note: major version upgrades may have breaking API changes

### 9.5 Kotlin 2.1+ Features
**Priority: Low | Effort: Low**

Current: Kotlin 2.0.21. Consider:
- Kotlin 2.1's context parameters (when stable)
- Multiplatform readiness (if mobile app is considered)
- K2 compiler improvements (already in 2.0)

### 9.6 OpenTelemetry Migration
**Priority: Medium | Effort: Medium**

Current: Micrometer + Prometheus. Consider:
- Migrate to OpenTelemetry for vendor-neutral observability
- Unified traces, metrics, and logs
- Better distributed tracing across services
- Spring Boot 3.x has excellent OpenTelemetry integration via Micrometer OTLP exporter

---

## 10. Code Cleanup & Technical Debt

### 10.1 Remove Dead Code
**Priority: High | Effort: Low** -- **COMPLETED**

All items from the initial audit have been resolved:
- Dropped orphan tables (`external_connections`, `external_connection_tokens`, `app_metadata`, `fund_sector_allocations`) via V66 migration
- Deleted `FundSectorAllocation` entity and repository
- Removed legacy `DashboardService`, its test, DTOs, and `GET /api/v1/dashboard` endpoint
- Deleted frontend `dashboardService.ts`, `useDashboard.ts`, and associated types
- Deleted 4 orphan dashboard container components and 3 unregistered widget components + CSS
- Updated `config/.env.example` with 6 previously undocumented environment variables

### 10.2 Standardize DTO Patterns
**Priority: Medium | Effort: Medium**

Current DTOs are scattered across packages with inconsistent naming:
- Some use `*Dto` suffix, others use `*Response`/`*Request`
- Some are in `dto/` subdirectories, others in `controller/` files
- Create a consistent naming convention and location strategy

### 10.3 Migration Cleanup
**Priority: Low | Effort: Low**

- V52 (`truncate_and_restart`) was a one-time data reset — mark as legacy
- V56 (`backfill_activity_types`) was a one-time backfill — mark as legacy
- These migrations should remain (Flyway needs them in history) but document their one-time nature

### 10.4 Test Infrastructure
**Priority: Medium | Effort: Medium**

- Create test fixtures/factories for commonly used entities
- Add test data builders for complex test scenarios
- Standardize on a single mocking approach (MockK is used but ensure consistency)
- Add contract tests for the SnapTrade adapter interface

### 10.5 Documentation Maintenance
**Priority: Low | Effort: Low**

- Keep CLAUDE.md updated as architecture evolves
- Add KDoc comments to complex service methods
- Add OpenAPI/Swagger documentation for API endpoints (SpringDoc)
- Add architecture decision records (ADRs) for significant choices

---

## Priority Summary

### Immediate (P0) — Do Now
| Item | Effort | Impact |
|------|--------|--------|
| API Rate Limiting (5.1) | Medium | Prevents abuse, security-critical |
| Security Headers (5.6) | Low | Quick security win |
| Dependency Scanning (5.7) | Low | Automated vulnerability detection |
| ~~Remove Dead Code (10.1)~~ | ~~Low~~ | ~~COMPLETED~~ |
| Database Backup Automation (6.3) | Low | Data safety |
| ~~Health Check Expansion (7.4)~~ | ~~Low~~ | ~~COMPLETED~~ |
| Error Notification (7.7) | Medium | Operational awareness |

### Short-term (P1) — Next Sprint
| Item | Effort | Impact |
|------|--------|--------|
| ~~Error Handling Standardization (2.1)~~ | ~~Medium~~ | ~~COMPLETED~~ |
| Input Validation (5.2) | Medium | Security |
| Prometheus Alerting (7.1) | Medium | Proactive monitoring |
| Structured Logging (7.3) | Low | Debugging efficiency |
| Frontend Accessibility (3.3) | Medium | Inclusivity, compliance |
| Frontend Test Coverage (3.7) | High | Reliability |
| Backend Test Coverage (2.7) | Medium | Reliability |

### Medium-term (P2) — Next Quarter
| Item | Effort | Impact |
|------|--------|--------|
| Domain Restructuring (1.1) | High | Long-term maintainability |
| Event-Driven Patterns (1.2) | Medium | Decoupling, scalability |
| Grafana Dashboards (7.2) | Medium | Operational visibility |
| Caching Strategy (8.2) | Medium | Performance |
| Blue/Green Deployments (6.2) | Medium | Zero-downtime deploys |
| Form Management (3.2) | Medium | UX, code quality |
| Spring Boot Upgrade (9.1) | Medium | Modern features |

### Long-term (P3) — Future
| Item | Effort | Impact |
|------|--------|--------|
| Kubernetes (6.1) | High | Cloud-native scalability |
| Table Partitioning (4.3) | High | Database scalability |
| Message Queue (8.5) | High | Async processing |
| OpenTelemetry (9.6) | Medium | Vendor-neutral observability |
| Self-Healing (7.8) | High | Autonomous operations |
| React 19 Migration (9.2) | Medium | Modern framework features |
