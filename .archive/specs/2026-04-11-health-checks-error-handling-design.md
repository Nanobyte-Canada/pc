# Health Checks, Error Handling, Admin UI & Documentation

## Overview

Four improvements applied across the entire repository:

1. **Health Check Expansion** — Custom Spring Boot Actuator `HealthIndicator` beans for external dependencies
2. **Error Handling Standardization** — RFC 7807 Problem Details everywhere, unified exception hierarchy, toast notifications for users
3. **Admin Ingestion UI** — Rewrite admin page to manage the new ingestion-service (triggers, live progress, stats, runs/errors)
4. **Documentation Update** — Update CLAUDE.md and business-context with microservice architecture and all recent changes

---

## 1. Health Check Expansion

### Main Backend (port 8080)

Auto-configured (already working): Database, Redis.

Custom health indicators to add:
- **SnapTradeHealthIndicator** — Calls SnapTrade API status check. UP/DOWN based on response.
- **IngestionServiceHealthIndicator** — Pings `http://ingestion-service:8081/actuator/health`. Graceful fallback: UNKNOWN if ingestion service is not deployed.

### Ingestion Service (port 8081)

Auto-configured (already working): Database (ingestion schema), Redis.

Custom health indicators to add:
- **EodhdHealthIndicator** — Lightweight call to `GET /api/exchanges-list/` with 5s timeout. UP if 200 with non-empty body.
- **QuotaHealthIndicator** — Reads `EodhdRateLimiter.remainingDailyQuota()`. UP if > 1% remaining, DOWN if exhausted. Includes remaining count in details.

### Configuration (both services)

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
      show-components: when-authorized
```

---

## 2. Error Handling Standardization

### 2.1 Domain Exception Hierarchy

Both services define the same exception hierarchy:

```
AppException (abstract, RuntimeException)
  ├── message: String
  └── code: String (machine-readable error code)

NotFoundException          → HTTP 404
ConflictException          → HTTP 409
ValidationException        → HTTP 400
ForbiddenException         → HTTP 403
RateLimitException         → HTTP 429
ExternalServiceException   → HTTP 502
InternalException          → HTTP 500
```

### 2.2 Backend Changes — Main Backend

**Replace existing GlobalExceptionHandler:**
- Current: `backend/src/main/kotlin/com/portfolio/config/GlobalExceptionHandler.kt` returns custom `ErrorResponse` DTO
- New: Rewrite to use Spring's `ProblemDetail` class for RFC 7807 responses
- Handle: All `AppException` subclasses, `AuthException` subclasses (migrate to extend AppException), `SnapTradeApiException`, `TokenEncryptionException`, `MethodArgumentNotValidException`, `AccessDeniedException`, catch-all

**Remove duplicate handlers:**
- `AuthController.kt` lines 190-234: Remove per-controller `@ExceptionHandler` methods

**Migrate existing exceptions:**
- `AuthException` hierarchy (8 exceptions in `auth/exception/AuthExceptions.kt`): Refactor to extend `AppException` with appropriate subclass. E.g., `InvalidCredentialsException` extends `ForbiddenException`, `EmailAlreadyExistsException` extends `ConflictException`, `UserNotFoundException` extends `NotFoundException`.
- `SnapTradeApiException` → handled in GlobalExceptionHandler, mapped to `ExternalServiceException`
- `TokenEncryptionException` → handled in GlobalExceptionHandler, mapped to `InternalException`

**Replace raw throws across 95 throw statements:**
- `throw IllegalArgumentException(...)` → `throw ValidationException(code, message)`
- `throw IllegalStateException(...)` → `throw ConflictException(code, message)` or `throw InternalException(code, message)` depending on context
- Audit all services: BrokerService, DashboardDataService, PortfolioGroupService, TradingService, etc.

### 2.3 Backend Changes — Ingestion Service

- Create exception hierarchy in `com.portfolio.ingestion.exception/`
- Create `GlobalExceptionHandler` with `@RestControllerAdvice`
- Enable `spring.mvc.problemdetails.enabled: true`

### 2.4 RFC 7807 Response Format

All error responses conform to:
```json
{
  "type": "about:blank",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Instrument with ISIN US0378331005 not found",
  "instance": "/admin/ingestion/instruments/12345",
  "code": "INSTRUMENT_NOT_FOUND",
  "timestamp": "2026-04-11T10:30:00Z"
}
```

### 2.5 Frontend — RFC 7807 Error Parsing

**Update `services/api.ts`:**
- Detect RFC 7807 responses (content-type contains `application/problem+json`)
- Parse `ProblemDetail` response and extract `detail` field for user-facing messages
- Create a typed `ApiError` class that carries `code`, `detail`, `status`, `title`
- Replace generic `throw new Error(...)` with `throw new ApiError(...)`

**Update all service files to use ApiError:**
- `brokerService.ts` — replace generic `new Error()` throws
- `notificationService.ts` — replace generic `new Error()` throws
- `adminService.ts` — replace generic `new Error()` throws
- `dashboardWidgetService.ts` — replace generic `new Error()` throws
- Auth services already have `AuthError` — align with `ApiError` pattern

### 2.6 Frontend — Toast Notification System

**Problem found:** No toast/popup error notification system exists. Errors only display inline in forms. API failures outside auth pages are silently swallowed.

**Solution:** Add a lightweight toast notification component:
- Create `components/ui/toast.tsx` and `toast.css`
- Create a `useToast()` hook backed by a Zustand store (`stores/toastStore.ts`)
- Support types: `success`, `error`, `warning`, `info`
- Auto-dismiss after 5 seconds, manual dismiss button
- Position: bottom-right
- Render toast container in `AppLayout.tsx`

**Integration points:**
- `apiFetch` in `api.ts`: On non-auth API errors (not 401), show toast with error detail
- React Query `onError` callbacks: Show toast for mutation failures
- Admin page: Show toast on ingestion trigger success/failure

---

## 3. Admin Ingestion UI

**Design reference:** `tmp/current UI/admin-ingestion-design.html`

Replace the old admin ingestion UI (5 workflows on port 8080) with a new one targeting the ingestion-service on port 8081. Auto-refreshes every 10 seconds.

### 3.1 Backend — Enhanced Stats Endpoint

Update `GET /admin/ingestion/stats` on the ingestion-service to return:
```json
{
  "totalInstruments": 12847,
  "enrichedInstruments": 8231,
  "pendingInstruments": 4616,
  "remainingDailyQuota": 72450,
  "totalDailyQuota": 100000,
  "exchangeCount": 5,
  "exchanges": ["US", "TO", "V", "INDX", "GBOND"],
  "lastRunStatus": "COMPLETED",
  "lastRunCompletedAt": "2026-04-10T22:28:14Z",
  "instrumentsByType": {
    "STOCK": { "total": 8421, "enriched": 5102 },
    "ETF": { "total": 2847, "enriched": 2103 },
    "MUTUAL_FUND": { "total": 1234, "enriched": 891 },
    "PREFERRED_STOCK": { "total": 189, "enriched": 72 },
    "INDEX": { "total": 124, "enriched": 48 },
    "BOND": { "total": 32, "enriched": 15 }
  }
}
```

Add repository queries: count instruments by type, count enriched by type, count active exchanges, find latest run.

### 3.2 Backend — Active Run Status Endpoint

Add `GET /admin/ingestion/active-run` to support live progress polling:
```json
{
  "isRunning": true,
  "runId": 43,
  "currentStep": "RAW_DATA_FETCH",
  "steps": [
    { "name": "UNIVERSE_SYNC", "status": "COMPLETED", "processed": 12847, "created": 23, "updated": 12824, "failed": 0 },
    { "name": "RAW_DATA_FETCH", "status": "RUNNING", "processed": 3450, "updated": 2800, "skipped": 620, "failed": 12 }
  ],
  "totalTarget": 10000,
  "processedSoFar": 3450
}
```

Requires the orchestrator to track active run state (run ID + progress counters accessible from the controller).

### 3.3 Backend — Async Ingestion Triggers

Currently `POST /admin/ingestion/run` blocks until completion (30+ minutes). Change to:
- Start the pipeline in a background coroutine
- Return immediately with `{ "status": "started", "runId": 43 }`
- The UI polls `/admin/ingestion/active-run` for progress

### 3.4 Frontend — Vite Proxy

Add proxy for the ingestion service in `frontend/vite.config.ts`:
```ts
'/ingestion-api': {
  target: 'http://localhost:8081',
  changeOrigin: true,
  rewrite: (path) => path.replace(/^\/ingestion-api/, ''),
}
```

### 3.5 Frontend — Rewrite adminService.ts

Replace all functions with new ingestion-service endpoints using `/ingestion-api` prefix. Use raw `fetch` (no CSRF needed for ingestion service). Key functions:
- `getIngestionStats()` — enhanced stats with per-type breakdown
- `getActiveRun()` — live progress polling
- `triggerExchangeSync()` — POST /exchanges (async)
- `triggerFullIngestion()` — POST /run (async, returns runId)
- `getIngestionRuns(limit)` — GET /runs
- `getRunSteps(runId)` — GET /runs/{id}/steps
- `getRunErrors(runId)` — GET /runs/{id}/errors

### 3.6 Frontend — Rewrite AdminPage.tsx

Complete rewrite matching the approved HTML mockup:
- **Auto-refresh**: `useQuery` with `refetchInterval: 10000` for stats and active run
- **Summary stats**: 6 cards (total instruments, enriched, pending, quota with progress bar, exchanges, last run)
- **Instruments by Type**: 6 cards showing per-type counts + enriched
- **Workflows**: 2 cards (Exchange Sync with trigger button, Full Ingestion with live progress bar + step list)
- **Recent Runs**: Table with expandable rows showing step detail counters and inline error rows
- Toast notifications on trigger success/failure

---

## 4. Documentation Consolidation & Update

### 4.1 Consolidate docs/ into agent-reference/

The `docs/` root has duplicate markdown files that overlap with `docs/agent-reference/`. Consolidate all markdown into `agent-reference/` as the single source of truth.

**Merge and delete:**

| Root File | Action | Target |
|-----------|--------|--------|
| `docs/api.md` | Merge into → delete | `docs/agent-reference/api-endpoints.md` (api.md is a subset) |
| `docs/architecture.md` | Merge into → delete | `docs/agent-reference/infrastructure.md` (architecture is a subset) |
| `docs/deployment.md` | Merge into → delete | `docs/agent-reference/infrastructure.md` (deployment overlaps) |
| `docs/development.md` | Merge into → delete | `docs/agent-reference/infrastructure.md` (dev setup overlaps) |

**Move as-is (no overlap):**

| Root File | Action | Target |
|-----------|--------|--------|
| `docs/improvements.md` | Move → | `docs/agent-reference/improvements.md` |
| `docs/ingestion-workflow.md` | Move → | `docs/agent-reference/ingestion-workflow.md` |

**Keep in docs/ root:**
- `docs/business-context.html` — HTML format, different purpose
- `docs/superpowers/` — specs and plans directory (not agent-reference)

After consolidation, `docs/` root contains only `business-context.html`, `agent-reference/`, and `superpowers/`.

### 4.2 Update All Agent Reference Files

After merge, update every file to reflect current state (ingestion-service, dashboard redesign, error handling):

| File | Updates Needed |
|------|----------------|
| `INDEX.md` | Update file list, add ingestion-service quick reference tasks |
| `api-endpoints.md` | Merge api.md content, add ingestion-service endpoints (port 8081), add RFC 7807 error format |
| `backend-services.md` | Add ingestion-service module, package structure, pipeline components, provider adapters |
| `frontend-map.md` | Dashboard redesign (PortfolioSummaryWidget, ConnectedAccountsWidget rewrite, no hero), admin page rewrite, toast component, sidebar changes |
| `infrastructure.md` | Merge architecture.md + deployment.md + development.md content, add ingestion-service container, Vite proxy, microservice diagram |
| `configurations.md` | Add ingestion-service application.yml, EODHD config, new env vars |
| `entity-relationships.md` | Add ingestion schema entities (Exchange, Instrument, InstrumentExchange, ProviderRawData, ProviderConfig, tracking tables) |
| `database-schema.md` | Add ingestion schema tables, V1 migration in ingestion-service |
| `ingestion-workflow.md` | Rewrite for new microservice pipeline: Exchange Sync → Universe Discovery → Raw Data Fetch. Remove old backend ingestion references. |
| `improvements.md` | Mark completed items (see 4.4 below) |
| `unused-legacy.md` | Review for any newly dead code from dashboard redesign |

### 4.3 CLAUDE.md

Add/update:
- **Architecture section:** Add ingestion-service as second module (port 8081, ingestion schema)
- **Backend package structure:** Add ingestion-service directory tree
- **API Routes table:** Add ingestion service admin endpoints
- **Commands section:** Add `docker compose build ingestion-service`, `docker compose logs -f ingestion-service`
- **Database section:** Document ingestion schema, cross-schema reads
- **Error handling section:** Document RFC 7807 standard, exception hierarchy
- **Environment variables:** Add `EODHD_API_KEY`, `INGESTION_ENABLED`, `INGESTION_SCHEDULE`

### 4.4 improvements.md — Mark Completed Items

| Item | Status |
|------|--------|
| 1.4 Strategy Pattern for Data Providers | **COMPLETED** — ingestion-service `DataProvider` interface, `ProviderRegistry`, EODHD adapter |
| 2.1 Error Handling Standardization | **COMPLETED** — RFC 7807 ProblemDetail, exception hierarchy, GlobalExceptionHandler |
| 4.1 Table Cleanup | **COMPLETED** (already marked) |
| 7.4 Health Check Expansion | **COMPLETED** — Custom HealthIndicators for SnapTrade, EODHD, cross-service, quota |
| 10.1 Remove Dead Code | **COMPLETED** (already marked) |

Add note referencing ingestion-service module and dashboard redesign as additional completed work.

### 4.5 Business Context

Update `docs/business-context.html`:
- Add microservice architecture (main backend + ingestion service)
- Document ingestion pipeline, instrument types, EODHD provider, data flow

---

## Files to Create/Modify

### Main Backend
| Action | File | Purpose |
|--------|------|---------|
| Create | `backend/.../exception/AppException.kt` | Exception hierarchy (AppException, NotFoundException, etc.) |
| Rewrite | `backend/.../config/GlobalExceptionHandler.kt` | RFC 7807 ProblemDetail responses |
| Modify | `backend/.../auth/exception/AuthExceptions.kt` | Extend AppException subclasses |
| Modify | `backend/.../auth/controller/AuthController.kt` | Remove duplicate @ExceptionHandler methods |
| Create | `backend/.../health/SnapTradeHealthIndicator.kt` | SnapTrade health check |
| Create | `backend/.../health/IngestionServiceHealthIndicator.kt` | Cross-service health check |
| Modify | `backend/.../resources/application.yml` | Health config, problemdetails enabled |
| Audit | All services with `throw` statements | Replace IllegalArgumentException/IllegalStateException with typed exceptions |

### Ingestion Service
| Action | File | Purpose |
|--------|------|---------|
| Create | `ingestion-service/.../exception/AppException.kt` | Exception hierarchy |
| Create | `ingestion-service/.../exception/GlobalExceptionHandler.kt` | RFC 7807 handler |
| Create | `ingestion-service/.../health/EodhdHealthIndicator.kt` | EODHD API health check |
| Create | `ingestion-service/.../health/QuotaHealthIndicator.kt` | Daily quota health check |
| Modify | `ingestion-service/.../controller/AdminIngestionController.kt` | Enhanced stats, active run, async triggers |
| Modify | `ingestion-service/.../pipeline/IngestionOrchestrator.kt` | Expose active run state for progress polling |
| Modify | `ingestion-service/.../persistence/repository/InstrumentRepository.kt` | Count queries by type |
| Modify | `ingestion-service/.../persistence/repository/ProviderRawDataRepository.kt` | Count enriched by type |
| Modify | `ingestion-service/.../resources/application.yml` | Health config, problemdetails enabled |

### Frontend
| Action | File | Purpose |
|--------|------|---------|
| Modify | `frontend/vite.config.ts` | Add `/ingestion-api` proxy to port 8081 |
| Modify | `frontend/src/services/api.ts` | RFC 7807 parsing, ApiError class |
| Modify | `frontend/src/services/brokerService.ts` | Use ApiError |
| Modify | `frontend/src/services/notificationService.ts` | Use ApiError |
| Rewrite | `frontend/src/services/adminService.ts` | New ingestion-service endpoints + ApiError |
| Rewrite | `frontend/src/pages/admin/AdminPage.tsx` | Full admin UI with stats, workflows, progress, runs |
| Create | `frontend/src/components/ui/toast.tsx` | Toast notification component |
| Create | `frontend/src/components/ui/toast.css` | Toast styling |
| Create | `frontend/src/stores/toastStore.ts` | Toast state management |
| Modify | `frontend/src/components/layout/AppLayout.tsx` | Mount toast container |

### Documentation — Consolidation
| Action | File | Purpose |
|--------|------|---------|
| Merge + Delete | `docs/api.md` → `docs/agent-reference/api-endpoints.md` | Consolidate API docs |
| Merge + Delete | `docs/architecture.md` → `docs/agent-reference/infrastructure.md` | Consolidate architecture/infra |
| Merge + Delete | `docs/deployment.md` → `docs/agent-reference/infrastructure.md` | Consolidate deployment |
| Merge + Delete | `docs/development.md` → `docs/agent-reference/infrastructure.md` | Consolidate dev setup |
| Move | `docs/improvements.md` → `docs/agent-reference/improvements.md` | Consolidate location |
| Move | `docs/ingestion-workflow.md` → `docs/agent-reference/ingestion-workflow.md` | Consolidate location |

### Documentation — Updates (all in docs/agent-reference/)
| Action | File | Purpose |
|--------|------|---------|
| Modify | `CLAUDE.md` | Add ingestion-service, error handling, new env vars |
| Modify | `docs/agent-reference/INDEX.md` | Updated file list, ingestion quick reference |
| Modify | `docs/agent-reference/api-endpoints.md` | Merged api.md + ingestion-service endpoints + RFC 7807 |
| Modify | `docs/agent-reference/infrastructure.md` | Merged arch + deploy + dev + ingestion-service container |
| Modify | `docs/agent-reference/backend-services.md` | Add ingestion-service module and pipeline |
| Modify | `docs/agent-reference/frontend-map.md` | Dashboard redesign, admin rewrite, toast, sidebar |
| Modify | `docs/agent-reference/configurations.md` | Add ingestion-service config, new env vars |
| Modify | `docs/agent-reference/entity-relationships.md` | Add ingestion schema entities |
| Modify | `docs/agent-reference/database-schema.md` | Add ingestion schema tables |
| Modify | `docs/agent-reference/ingestion-workflow.md` | Rewrite for new microservice pipeline |
| Modify | `docs/agent-reference/improvements.md` | Mark 1.4, 2.1, 7.4 as COMPLETED |
| Modify | `docs/agent-reference/unused-legacy.md` | Review for dead code from dashboard redesign |
| Modify | `docs/business-context.html` | Microservice architecture, data providers |

## Verification

1. `docker compose build backend ingestion-service` — both compile
2. `npm run build` from frontend/ — no TypeScript errors
3. Health endpoints: `GET :8080/actuator/health` and `GET :8081/actuator/health` show all components
4. Error responses: All API errors return RFC 7807 ProblemDetail format
5. Toast notifications: API errors show user-visible toast in bottom-right
6. Documentation: CLAUDE.md reflects current architecture including ingestion-service
7. Agent reference files: All 9 stale files updated to reflect microservice architecture and dashboard redesign
8. improvements.md: Items 1.4, 2.1, 7.4 marked as COMPLETED
