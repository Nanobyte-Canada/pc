# REST API Endpoints Reference

> Complete REST API reference for AI coding agents.
> Generated from actual controller source files.
>
> Cross-references: [Backend Services](backend-services.md) | [Entity Relationships](entity-relationships.md) | [Database Schema](database-schema.md)

---

## Base URLs

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080` |
| UAT | `https://uatportfolio.nanobyte.ca` |
| Production | `https://portfolio.nanobyte.ca` |

## Error Response Format

All errors follow a consistent format:

```json
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Resource not found",
  "path": "/api/v1/unknown"
}
```

### Common Status Codes

| Code | Description |
|------|-------------|
| `200` | Success |
| `201` | Created |
| `204` | No Content (successful delete) |
| `400` | Bad Request -- Invalid input |
| `401` | Unauthorized -- Authentication required |
| `403` | Forbidden -- Insufficient permissions |
| `404` | Not Found -- Resource doesn't exist |
| `409` | Conflict -- Resource in use or state conflict |
| `500` | Internal Server Error |
| `503` | Service Unavailable |

---

## Summary Table

| Controller | Base Path | Endpoints | Auth |
|---|---|---|---|
| [HealthController](#healthcontroller) | `/health` | 1 | Public |
| [VersionController](#versioncontroller) | `/api/v1/version` | 1 | Public |
| [AuthController](#authcontroller) | `/auth` | 11 | Mixed |
| [ScreenerController](#screenercontroller) | `/api/v1/screener` | 5 | Authenticated |
| [PortfolioController](#portfoliocontroller) | `/api/v1/portfolio` | 3 | Authenticated |
| [BrokerController](#brokercontroller) | `/api/v1/brokers` | 14 | Authenticated |
| [DashboardController](#dashboardcontroller) | `/api/v1/dashboard` | 15 | Authenticated |
| [ModelPortfolioController](#modelportfoliocontroller) | `/api/v1/model-portfolios` | 6 | Authenticated |
| [PerformanceController](#performancecontroller) | `/api/v1/portfolio-groups/{groupId}/performance` | 3 | Authenticated |
| [PortfolioGroupController](#portfoliogroupcontroller) | `/api/v1/portfolio-groups` | 14 | Authenticated |
| [TradingController](#tradingcontroller) | `/api/v1/trading` | 5 | Authenticated |
| [NotificationController](#notificationcontroller) | `/api/v1/notifications` | 5 | Authenticated |
**Total: 82 endpoints**

---

## HealthController

**File:** `controller/HealthController.kt`

| Method | Path | Auth | Service | Response |
|---|---|---|---|---|
| `GET` | `/health` | Public | (inline) | `HealthResponse` |

### `GET /health`
Returns application health status.
- **Response:** `HealthResponse { status: String, timestamp: String }`

---

## VersionController

**File:** `controller/VersionController.kt`
**Dependencies:** `@Value("${app.version}")`, `@Value("${app.environment}")`

| Method | Path | Auth | Service | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/version` | Public | (inline) | `VersionResponse` |

### `GET /api/v1/version`
Returns application version and environment.
- **Response:** `VersionResponse { version: String, environment: String }`

---

## AuthController

**File:** `auth/controller/AuthController.kt`
**Dependencies:** `AuthenticationService`, `UserRepository`, `AuthConfig`

| Method | Path | Auth | Service Method | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/auth/signup` | Public | `authenticationService.signup()` | `SignupRequest` | `SignupResponse` (201) |
| `POST` | `/auth/login` | Public | `authenticationService.login()` | `LoginRequest` | `AuthResponse` (sets cookies) |
| `POST` | `/auth/logout` | Public | `authenticationService.logout()` | (cookie: refresh_token) | `MessageResponse` |
| `POST` | `/auth/refresh` | Public | `authenticationService.refreshAccessToken()` | (cookie: refresh_token) | `AuthResponse` (sets cookies) |
| `POST` | `/auth/forgot-password` | Public | `authenticationService.forgotPassword()` | `ForgotPasswordRequest` | `MessageResponse` |
| `POST` | `/auth/reset-password` | Public | `authenticationService.resetPassword()` | `ResetPasswordRequest` | `MessageResponse` |
| `GET` | `/auth/verify-email` | Public | `authenticationService.verifyEmail()` | `?token=String` | `MessageResponse` |
| `POST` | `/auth/resend-verification` | Public | `authenticationService.resendVerificationEmail()` | `ResendVerificationRequest` | `MessageResponse` |
| `GET` | `/auth/me` | Authenticated | `userRepository.findByIdWithIdentities()` | -- | `UserResponse` |
| `POST` | `/auth/change-password` | Authenticated | `authenticationService.changePassword()` | `ChangePasswordRequest` | `MessageResponse` (clears cookies) |
| `PUT` | `/auth/profile` | Authenticated | `authenticationService.updateProfile()` | `UpdateProfileRequest` | `UserResponse` |

### Notes
- Login and refresh set `access_token` and `refresh_token` as HttpOnly cookies.
- Logout and change-password clear auth cookies.
- `secure` flag set when `app.environment` is `prod` or `dev`.
- `SameSite=Lax` on all cookies.

---

## ScreenerController

**File:** `controller/ScreenerController.kt`
**Dependencies:** `InstrumentScreenerService`

| Method | Path | Auth | Service Method | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/screener/{type}` | Authenticated | `screenerService.getInstruments()` | `PagedResponseDto<InstrumentScreenerItemDto>` |
| `GET` | `/api/v1/screener/detail/{type}/{ticker}` | Authenticated | `screenerService.getInstrumentDetail()` | `InstrumentDetailDto` |
| `GET` | `/api/v1/screener/search` | Authenticated | `screenerService.search()` | `SearchResponseDto` |
| `GET` | `/api/v1/screener/reference/{type}/{field}` | Authenticated | `screenerService.getReferenceValues()` | `List<String>` |
| `GET` | `/api/v1/screener/counts` | Authenticated | `screenerService.getTypeCounts()` | `Map<String, Long>` |

### `GET /api/v1/screener/{type}`
**Path params:** `type: String` -- Instrument type (STOCK, ETF, MUTUAL_FUND, PREFERRED_STOCK, INDEX, BOND)
**Query params:**
| Param | Type | Default | Description |
|---|---|---|---|
| `tickerContains` | String? | -- | Partial ticker match |
| `nameContains` | String? | -- | Partial name match |
| `country` | String? | -- | Country code filter |
| `exchange` | String? | -- | Exchange code filter |
| `sector` | String? | -- | Sector name filter |
| `issuer` | String? | -- | Issuer filter (ETFs/Mutual Funds) |
| `assetClass` | String? | -- | Asset class filter (ETFs/Mutual Funds) |
| `fundCategory` | String? | -- | Fund category filter (Mutual Funds) |
| `fundStyle` | String? | -- | Fund style filter (Mutual Funds) |
| `page` | Int | 0 | Page number |
| `size` | Int | 50 | Page size (clamped 1-100) |
| `sort` | String | `ticker:asc` | Sort field:direction |

### `GET /api/v1/screener/detail/{type}/{ticker}`
**Path params:** 
- `type: String` -- Instrument type
- `ticker: String` -- Instrument ticker symbol

### `GET /api/v1/screener/search`
**Query params:**
| Param | Type | Default | Description |
|---|---|---|---|
| `q` | String | (required) | Search query (ticker, name, ISIN) |
| `types` | String? | -- | Comma-separated instrument types to filter |
| `limit` | Int | 10 | Max results (clamped 1-50) |

### `GET /api/v1/screener/reference/{type}/{field}`
**Path params:**
- `type: String` -- Instrument type
- `field: String` -- Field name (country, exchange, sector, issuer, assetClass, fundCategory, fundStyle)

### `GET /api/v1/screener/counts`
Returns count of instruments by type from the ingestion schema.

---

## PortfolioController

**File:** `controller/PortfolioController.kt`
**Dependencies:** `PortfolioAnalysisService`

| Method | Path | Auth | Service Method | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/api/v1/portfolio/analyze` | Authenticated | `portfolioAnalysisService.analyze()` | `PortfolioAnalyzeRequest` | `PortfolioAnalysisResponseDto` |
| `POST` | `/api/v1/portfolio/validate` | Authenticated | `portfolioAnalysisService.validate()` | `PortfolioValidateRequest` | `ValidateResponseDto` |
| `POST` | `/api/v1/portfolio/normalize` | Authenticated | `portfolioAnalysisService.normalize()` | `PortfolioNormalizeRequest` | `NormalizeResponseDto` |

---

## BrokerController

**File:** `broker/controller/BrokerController.kt`
**Dependencies:** `BrokerService`, `PositionFetchService`, `BrokerGatewayClient`, `ActivityIngestionService`, `ReportingService`, `DriftCalculationService`, `RebalanceService`

| Method | Path | Auth | Service Method | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/brokers/connections/sync` | Authenticated | `brokerService.syncConnections()` | `ConnectionSyncResponse` |
| `GET` | `/api/v1/brokers/connections` | Authenticated | `brokerService.getUserConnections()` | `BrokerConnectionsResponse` |
| `POST` | `/api/v1/brokers/connect` | Authenticated | `brokerService.createGatewayConnection()` | `BrokerConnectionsResponse` |
| `GET` | `/api/v1/brokers/gateway/health` | Authenticated | `brokerGatewayClient.getGatewayHealth()` | `GatewayHealthResponse` |
| `DELETE` | `/api/v1/brokers/connections/{authorizationId}` | Authenticated | `brokerService.disconnectBroker()` | 204 No Content |
| `POST` | `/api/v1/brokers/connections/{connectionId}/fetch` | Authenticated | `positionFetchService.triggerManualFetch()` | `PositionFetchResponse` (202) |
| `GET` | `/api/v1/brokers/connections/{connectionId}/positions` | Authenticated | `brokerService.getPositionsForConnection()` | `ConnectionPositionsResponse` |
| `GET` | `/api/v1/brokers/positions` | Authenticated | `brokerService.getAggregatedPositions()` | `AggregatedPositionsResponse` |
| `GET` | `/api/v1/brokers/connections/{connectionId}/activities` | Authenticated | `reportingService.getActivitiesReport()` | `ActivitiesResponse` |
| `POST` | `/api/v1/brokers/connections/{connectionId}/sync-activities` | Authenticated | `activityIngestionService.syncActivitiesForConnection()` | `Map<String, Any>` |
| `GET` | `/api/v1/brokers/connections/{connectionId}/balance-history` | Authenticated | `brokerService.getBalanceHistory()` | `BalanceHistoryResponse` |

### Additional Broker Endpoints

| Method | Path | Auth | Service Method | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/brokers/reporting/performance` | Authenticated | `reportingService.getPerformanceReport()` | `ReportingPerformanceResponse` |
| `GET` | `/api/v1/brokers/reporting/activities` | Authenticated | `reportingService.getActivitiesReport()` | `ActivitiesResponse` |
| `GET` | `/api/v1/brokers/connections/{connectionId}/rebalance-progress` | Authenticated | `driftCalculationService.getRebalanceProgress()` | `RebalanceProgressDto` |
| `GET` | `/api/v1/brokers/connections/{connectionId}/pending-orders` | Authenticated | `rebalanceService.calculateTradesForAccount()` | `PendingOrdersResponse` |

### `POST /api/v1/brokers/connect`
**Request body:** `ConnectBrokerRequest { brokerType: String, credentials: Record<String, unknown> }`
**Response:** `BrokerConnectionsResponse` -- returns a list of `BrokerConnectionDto` (one per account discovered for the connection). Previously returned a single connection; now supports multi-account brokers.

### `GET /api/v1/brokers/connections/{connectionId}/activities`
**Query params:**
| Param | Type | Default |
|---|---|---|
| `page` | Int | 0 |
| `size` | Int | 50 |
| `startDate` | String? (ISO date) | -- |
| `endDate` | String? (ISO date) | -- |
| `type` | String? | -- |

### `GET /api/v1/brokers/connections/{connectionId}/balance-history`
**Query params:** `days: Int` (default 90)

### `GET /api/v1/brokers/reporting/performance`
**Query params:** `startDate: String?`, `endDate: String?`, `accounts: String?` (comma-separated IDs), `granularity: String?` (MONTHLY/QUARTERLY/YEARLY)

### `GET /api/v1/brokers/reporting/activities`
**Query params:** `page: Int`, `size: Int`, `startDate: String?`, `endDate: String?`, `accounts: String?`, `type: String?`

---

## DashboardController

**File:** `broker/controller/DashboardController.kt`
**Dependencies:** `DashboardDataService`, `DashboardPreferenceService`, `BrokerService`

| Method | Path | Auth | Service Method | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/dashboard/preferences` | Authenticated | `dashboardPreferenceService.getPreferences()` | `DashboardPreferencesResponse` |
| `PUT` | `/api/v1/dashboard/preferences` | Authenticated | `dashboardPreferenceService.updatePreferences()` | `DashboardPreferencesResponse` |
| `POST` | `/api/v1/dashboard/preferences/reset` | Authenticated | `dashboardPreferenceService.resetPreferences()` | `DashboardPreferencesResponse` |
| `GET` | `/api/v1/dashboard/irr` | Authenticated | `dashboardDataService.getIrrData()` | `DashboardIrrResponse` |
| `GET` | `/api/v1/dashboard/summary` | Authenticated | `dashboardDataService.getSummary()` | `DashboardSummaryResponse` |
| `GET` | `/api/v1/dashboard/cash` | Authenticated | `dashboardDataService.getCash()` | `DashboardCashResponse` |
| `GET` | `/api/v1/dashboard/exposure/sector` | Authenticated | `dashboardDataService.getSectorExposure()` | `SectorExposureResponse` |
| `GET` | `/api/v1/dashboard/exposure/geography` | Authenticated | `dashboardDataService.getGeographyExposure()` | `GeographyExposureResponse` |
| `GET` | `/api/v1/dashboard/risk-profile` | Authenticated | `dashboardDataService.getRiskProfile()` | `RiskProfileResponse` |
| `GET` | `/api/v1/dashboard/orders/open` | Authenticated | `dashboardDataService.getOpenOrders()` | `OpenOrdersResponse` |
| `GET` | `/api/v1/dashboard/fees` | Authenticated | `dashboardDataService.getFees()` | `FeesResponse` |
| `GET` | `/api/v1/dashboard/dividends` | Authenticated | `dashboardDataService.getDividendCalendar()` | `DividendCalendarResponse` |
| `GET` | `/api/v1/dashboard/positions` | Authenticated | `brokerService.getAggregatedPositions()` / `getPositionsForConnection()` | `AggregatedPositionsResponse` |
| `GET` | `/api/v1/dashboard/holdings` | Authenticated | `dashboardDataService.getHoldings()` | `HoldingsTableResponse` |
| `GET` | `/api/v1/dashboard/accounts` | Authenticated | `dashboardDataService.getAccounts()` | `DashboardAccountsResponse` |
| `POST` | `/api/v1/dashboard/refresh` | Authenticated | `dashboardDataService.refreshAll()` | `RefreshAllResponse` |
| `POST` | `/api/v1/dashboard/admin/backfill-gics` | Authenticated | `dashboardDataService.backfillStockGicsCodes()` | `Map<String, Int>` |

### Common Query Params for Widget Endpoints
Most dashboard widget endpoints accept: `connectionId: Long?` -- Filter to a specific broker connection. When null, aggregates across all active connections.

### `GET /api/v1/dashboard/preferences`
**Query params:** `contextType: String` (default `DASHBOARD`), `contextId: Long?`

### `PUT /api/v1/dashboard/preferences`
**Request body:** `UpdateDashboardPreferencesRequest { widgets: List<WidgetPreferenceInput> }`
**Query params:** `contextType: String` (default `DASHBOARD`), `contextId: Long?`

### `GET /api/v1/dashboard/summary`
**Response:** `DashboardSummaryResponse { portfolioValue: PortfolioValueDto, positionsSummary, holdingsCount }`
- `portfolioValue.investmentByCurrency`: Per-currency investment breakdown (e.g., `[{currency: "CAD", amount: 35000}, {currency: "USD", amount: 8000}]`). Computed from positions grouped by currency.

### `GET /api/v1/dashboard/cash`
**Response includes:** `totalBuyingPowerUSD: BigDecimal` — Raw USD buying power from broker balance snapshot (not FX-converted). Added alongside existing `totalBuyingPowerCAD`.

### `GET /api/v1/dashboard/irr`
Returns Internal Rate of Return (IRR) for individual accounts and portfolio-wide.
**Query params:** `connectionId: Long?` -- Filter to a specific broker connection.
**Response:** `DashboardIrrResponse { portfolioIrr: BigDecimal?, accounts: List<AccountIrrDto> }`

### `GET /api/v1/dashboard/dividends`
**Query params:** `month: String?` (format `YYYY-MM`, default current month), `connectionId: Long?`

---

## ModelPortfolioController

**File:** `broker/controller/ModelPortfolioController.kt`
**Dependencies:** `ModelPortfolioService`

| Method | Path | Auth | Service Method | Request Body | Response |
|---|---|---|---|---|---|
| `GET` | `/api/v1/model-portfolios` | Authenticated | `modelPortfolioService.listAll()` | -- | `ModelPortfoliosListResponse` |
| `GET` | `/api/v1/model-portfolios/{id}` | Authenticated | `modelPortfolioService.getById()` | -- | `ModelPortfolioDetailDto` |
| `GET` | `/api/v1/model-portfolios/{id}/analysis` | Authenticated | `modelPortfolioService.getAnalysis()` | -- | `ModelAnalysisDto` |
| `POST` | `/api/v1/model-portfolios` | Authenticated | `modelPortfolioService.create()` | `CreateModelPortfolioRequest` | `ModelPortfolioDetailDto` (201) |
| `PUT` | `/api/v1/model-portfolios/{id}` | Authenticated | `modelPortfolioService.update()` | `UpdateModelPortfolioRequest` | `ModelPortfolioDetailDto` |
| `DELETE` | `/api/v1/model-portfolios/{id}` | Authenticated | `modelPortfolioService.delete()` | -- | 204 No Content |
| `POST` | `/api/v1/model-portfolios/{id}/apply-to-accounts` | Authenticated | `modelPortfolioService.applyToAccounts()` | `ApplyToAccountsRequest` | 200 OK |

---

## PerformanceController

**File:** `broker/controller/PerformanceController.kt`
**Dependencies:** `PerformanceCalculationService`, `PortfolioGroupService`, `PortfolioSnapshotService`

| Method | Path | Auth | Service Method | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/portfolio-groups/{groupId}/performance` | Authenticated | `performanceCalculationService.getPerformanceSummary()` | `PerformanceSummaryDto` |
| `GET` | `/api/v1/portfolio-groups/{groupId}/performance/chart` | Authenticated | `performanceCalculationService.getPerformanceChart()` | `PerformanceChartData` |
| `GET` | `/api/v1/portfolio-groups/{groupId}/performance/snapshots` | Authenticated | `snapshotService.getSnapshots()` | `List<SnapshotDto>` |

### Common Query Params
| Param | Type | Default | Description |
|---|---|---|---|
| `startDate` | LocalDate? | (derived from period) | ISO date |
| `endDate` | LocalDate? | today | ISO date |
| `period` | String | `1Y` | One of: `1M`, `3M`, `6M`, `YTD`, `1Y`, `ALL` |

### `GET .../performance/chart`
Additional param: `benchmark: String?` -- Benchmark symbol (e.g., `SPY`, `XIU`) or `MODEL:{id}` for model portfolio benchmark.

---

## PortfolioGroupController

**File:** `broker/controller/PortfolioGroupController.kt`
**Dependencies:** `PortfolioGroupService`, `DriftCalculationService`, `RebalanceService`, `RebalanceEventRepository`

### Group CRUD

| Method | Path | Auth | Service Method | Request Body | Response |
|---|---|---|---|---|---|
| `GET` | `/api/v1/portfolio-groups` | Authenticated | `portfolioGroupService.listGroups()` | -- | `PortfolioGroupsListResponse` |
| `POST` | `/api/v1/portfolio-groups` | Authenticated | `portfolioGroupService.createGroup()` | `CreatePortfolioGroupRequest` | `PortfolioGroupDetailDto` (201) |
| `GET` | `/api/v1/portfolio-groups/{groupId}` | Authenticated | `portfolioGroupService.getGroup()` | -- | `PortfolioGroupDetailDto` |
| `PUT` | `/api/v1/portfolio-groups/{groupId}` | Authenticated | `portfolioGroupService.updateGroup()` | `UpdatePortfolioGroupRequest` | `PortfolioGroupDetailDto` |
| `DELETE` | `/api/v1/portfolio-groups/{groupId}` | Authenticated | `portfolioGroupService.deleteGroup()` | -- | 204 No Content |

### Targets

| Method | Path | Auth | Service Method | Request Body | Response |
|---|---|---|---|---|---|
| `PUT` | `/api/v1/portfolio-groups/{groupId}/targets` | Authenticated | `portfolioGroupService.setTargets()` | `SetTargetsRequest` | `List<TargetAllocationDto>` |
| `POST` | `/api/v1/portfolio-groups/{groupId}/targets` | Authenticated | `portfolioGroupService.addTarget()` | `TargetInput` | `TargetAllocationDto` (201) |
| `DELETE` | `/api/v1/portfolio-groups/{groupId}/targets/{symbol}` | Authenticated | `portfolioGroupService.removeTarget()` | -- | 204 No Content |

### Account Linking

| Method | Path | Auth | Service Method | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/api/v1/portfolio-groups/{groupId}/accounts` | Authenticated | `portfolioGroupService.linkAccount()` | `LinkAccountRequest` | `LinkedAccountDto` (201) |
| `DELETE` | `/api/v1/portfolio-groups/{groupId}/accounts/{connectionId}` | Authenticated | `portfolioGroupService.unlinkAccount()` | -- | 204 No Content |

### Drift and Rebalance

| Method | Path | Auth | Service Method | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/portfolio-groups/{groupId}/drift` | Authenticated | `driftCalculationService.calculateDrift()` | `DriftAnalysisResponse` |
| `GET` | `/api/v1/portfolio-groups/{groupId}/rebalance` | Authenticated | `rebalanceService.calculateRebalanceTrades()` | `RebalanceTradesResponse` |
| `GET` | `/api/v1/portfolio-groups/{groupId}/rebalance-history` | Authenticated | `rebalanceEventRepository.findByGroupId...` | `RebalanceHistoryResponse` |
| `POST` | `/api/v1/portfolio-groups/{groupId}/rebalance/trigger` | Authenticated | (inline - creates RebalanceEvent) | `RebalanceEventDto` (201) |

### Settings

| Method | Path | Auth | Service Method | Request Body | Response |
|---|---|---|---|---|---|
| `GET` | `/api/v1/portfolio-groups/{groupId}/settings` | Authenticated | `portfolioGroupService.getSettings()` | -- | `PortfolioGroupSettingsDto` |
| `PATCH` | `/api/v1/portfolio-groups/{groupId}/settings` | Authenticated | `portfolioGroupService.updateSettings()` | `UpdateSettingsRequest` | `PortfolioGroupSettingsDto` |

### Excluded Assets

| Method | Path | Auth | Service Method | Request Body | Response |
|---|---|---|---|---|---|
| `GET` | `/api/v1/portfolio-groups/{groupId}/excluded-assets` | Authenticated | `portfolioGroupService.getExcludedAssets()` | -- | `List<ExcludedAssetDto>` |
| `POST` | `/api/v1/portfolio-groups/{groupId}/excluded-assets` | Authenticated | `portfolioGroupService.addExcludedAsset()` | `ExcludeAssetRequest` | `ExcludedAssetDto` (201) |
| `DELETE` | `/api/v1/portfolio-groups/{groupId}/excluded-assets/{symbol}` | Authenticated | `portfolioGroupService.removeExcludedAsset()` | -- | 204 No Content |

---

## TradingController

**File:** `broker/controller/TradingController.kt`
**Dependencies:** `OrderExecutionService`, `UserRepository`

| Method | Path | Auth | Service Method | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/api/v1/trading/execute` | Authenticated | `orderExecutionService.executeTradesForGroup()` | `ExecuteTradesRequest` | `ExecuteTradesResponse` (201) |
| `POST` | `/api/v1/trading/groups/{groupId}/execute-single` | Authenticated | `orderExecutionService.executeSingleTrade()` | `TradeExecutionInput` | `TradeOrderDto` (201) |
| `GET` | `/api/v1/trading/groups/{groupId}/orders` | Authenticated | `orderExecutionService.getOrdersForGroup()` | -- | `OrderStatusResponse` |
| `GET` | `/api/v1/trading/batches/{batchId}` | Authenticated | `orderExecutionService.getOrdersForBatch()` | -- | `OrderStatusResponse` |
| `POST` | `/api/v1/trading/orders/{orderId}/cancel` | Authenticated | `orderExecutionService.cancelOrder()` | -- | `TradeOrderDto` |

### `GET /api/v1/trading/batches/{batchId}`
**Path params:** `batchId: UUID` -- Batch identifier from trade execution

---

## NotificationController

**File:** `broker/controller/NotificationController.kt`
**Dependencies:** `NotificationService`, `UserRepository`

| Method | Path | Auth | Service Method | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/notifications` | Authenticated | `notificationService.getNotifications()` | `NotificationsResponse` |
| `GET` | `/api/v1/notifications/count` | Authenticated | `notificationService.getUnreadCount()` | `Map { unreadCount: Long }` |
| `POST` | `/api/v1/notifications/{id}/read` | Authenticated | `notificationService.markAsRead()` | `NotificationDto` |
| `POST` | `/api/v1/notifications/read-all` | Authenticated | `notificationService.markAllAsRead()` | `Map { markedCount: Int }` |
| `GET` | `/api/v1/notifications/preferences` | Authenticated | `notificationService.getPreferences()` | `NotificationPreferenceDto` |
| `PATCH` | `/api/v1/notifications/preferences` | Authenticated | `notificationService.updatePreferences()` | `NotificationPreferenceDto` |

### `GET /api/v1/notifications`
**Query params:** `unreadOnly: Boolean` (default false), `page: Int` (default 0), `size: Int` (default 20)

### `PATCH /api/v1/notifications/preferences`
**Request body:** `UpdateNotificationPreferenceRequest { emailEnabled?, inAppEnabled?, driftAlerts?, driftThreshold?, orderAlerts?, syncFailureAlerts?, newAssetAlerts?, rebalanceReminder?, reminderFrequency? }`


---

## Security Configuration Summary

### Public Endpoints (no auth required)
- `GET /health`
- `GET /api/v1/version`
- `POST /auth/login`, `/auth/signup`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/resend-verification`
- `GET /auth/verify-email`, `/auth/logout`
- `GET /actuator/health`, `/actuator/info`

### Admin Endpoints (ROLE_ADMIN required)
- `/api/v1/admin/**`
- Note: Legacy `/admin/**` ingestion endpoints were removed in favor of the ingestion-service microservice

### Authenticated Endpoints (any logged-in user)
- All `/api/**` routes not listed above
- `/auth/me`, `/auth/change-password`, `/auth/profile`

### CSRF
- Disabled for: auth endpoints, `/health`, `/actuator/**`, `/api/**`
- Enabled for all other endpoints via `CookieCsrfTokenRepository`

### CORS
- Configurable via `CORS_ALLOWED_ORIGINS` environment variable
- Local development: `http://localhost:3000`
- UAT: `https://uatportfolio.nanobyte.ca`
- Production: `https://portfolio.nanobyte.ca`
- Credentials allowed

### Rate Limiting
Currently no global rate limiting is implemented. Resilience4j rate limiters are used for external API calls (EODHD, AlphaVantage).

---

## Error Response Format (RFC 7807)

Both the main backend and ingestion service return RFC 7807 ProblemDetail responses for all errors:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Resource not found",
  "instance": "/api/v1/brokers/connections/999"
}
```

Exception hierarchy mapping:

| Exception | HTTP Status | Code |
|---|---|---|
| `NotFoundException` | 404 | NOT_FOUND |
| `ConflictException` | 409 | CONFLICT |
| `ValidationException` | 400 | VALIDATION_ERROR |
| `ForbiddenException` | 403 | FORBIDDEN |
| `RateLimitException` | 429 | RATE_LIMITED |
| `ExternalServiceException` | 502 | EXTERNAL_SERVICE_ERROR |
| `InternalException` | 500 | INTERNAL_ERROR |

---

## Ingestion Service Endpoints (port 8081)

Separate Spring Boot application at `backend/ingestion/`. No authentication required (internal service). Frontend accesses via `/ingestion-api` Vite proxy which rewrites to port 8081.

### AdminIngestionController (Ingestion Service)

**File:** `backend/ingestion/.../controller/AdminIngestionController.kt`

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `POST` | `/admin/ingestion/exchanges` | None | Triggers async exchange sync. Returns 409 if already running. | `{ status: "started" }` |
| `POST` | `/admin/ingestion/run` | None | Triggers async full ingestion (Universe Sync + Raw Data Fetch). Resets daily quota. Returns 409 if running. | `{ status: "started" }` |
| `GET` | `/admin/ingestion/stats` | None | Instrument counts (total, enriched, pending), daily quota remaining, exchange count, per-instrument-type breakdowns, last run status. | `IngestionStats` |
| `GET` | `/admin/ingestion/active-run` | None | Active run status with step progress. `isRunning: false` when idle. | `{ isRunning, runId?, steps? }` |
| `GET` | `/admin/ingestion/runs` | None | Lists recent ingestion runs. Query param: `limit` (default 10). | `List<RunSummary>` |
| `GET` | `/admin/ingestion/runs/{id}/steps` | None | Lists steps for a specific run with record counts. | `List<StepDetail>` |
| `GET` | `/admin/ingestion/runs/{id}/errors` | None | Lists errors for a specific run (max 100). | `List<ErrorDetail>` |

---

## Market Data Service (Port 8082)

**Base URL:** `http://localhost:8082`

### Quote Endpoints

**Prefix:** `/api/v1/quotes`

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/quotes/{symbol}` | None | Get quote for symbol. Checks Redis cache, then DB, then IBKR. | `QuoteResponse` |

### Options Chain Endpoints

**Prefix:** `/api/v1/chains`

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/chains/{underlying}` | None | Get options chain for underlying. Cached 30s in Redis. | `OptionsChainResponse` |
| `GET` | `/api/v1/chains/{underlying}/greeks` | None | Get options chain with Black-Scholes computed Greeks. | `OptionsChainResponse` |

### IV Rank Endpoints

**Prefix:** `/api/v1/iv`

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/iv/{ticker}` | None | Get IV rank/percentile from last 365 days of observations. | `IvRankResponse` |

### IBKR Health

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/health/ibkr` | None | IBKR connection status (connected, uptime, client ID). | `IbkrHealthResponse` |

### WebSocket

| Endpoint | Protocol | Description |
|---|---|---|
| `/ws/quotes` | WebSocket | Real-time quote streaming. Subscribe/unsubscribe via JSON messages. |

**WebSocket Actions:**
- `{"action": "subscribe", "symbol": "SPY"}` -- subscribe to stock quotes
- `{"action": "unsubscribe", "symbol": "SPY"}` -- unsubscribe from stock quotes
- `{"action": "subscribe_option", "symbol": "SPY", "expiry": "2026-06-19", "strike": "450.00", "optionType": "CALL"}` -- subscribe to option
- `{"action": "unsubscribe_option", ...}` -- unsubscribe from option

---

## Broker Gateway Service (Port 8084)

**Base URL:** `http://localhost:8084`

Broker data gateway microservice for connecting to IBKR, Questrade, and Wealthsimple. Provides a unified API for account data and order execution across multiple brokerages.

### Connection Management

**Prefix:** `/api/v1/gateway/connections`

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/gateway/connections` | API Key | Register a new broker connection. | `ConnectionDto` |
| `GET` | `/api/v1/gateway/connections?userId={id}` | API Key | List all connections for a user. | `List<ConnectionDto>` |
| `GET` | `/api/v1/gateway/connections/{id}` | API Key | Get connection details by ID. | `ConnectionDto` |
| `DELETE` | `/api/v1/gateway/connections/{id}` | API Key | Remove a broker connection. | 204 No Content |
| `POST` | `/api/v1/gateway/connections/{id}/validate` | API Key | Test connectivity to the broker. | `ValidationResult` |
| `POST` | `/api/v1/gateway/connections/{id}/refresh` | API Key | Rotate OAuth tokens for the connection. | `ConnectionDto` |

### Data Retrieval

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/gateway/connections/{id}/accounts` | API Key | List accounts for a connection. | `List<AccountDto>` |
| `GET` | `/api/v1/gateway/connections/{id}/accounts/{accId}/balances` | API Key | Get account balances. | `List<BalanceDto>` |
| `GET` | `/api/v1/gateway/connections/{id}/accounts/{accId}/positions` | API Key | Get account positions. | `List<PositionDto>` |
| `GET` | `/api/v1/gateway/connections/{id}/accounts/{accId}/activities` | API Key | Get account transactions. | `List<ActivityDto>` |
| `GET` | `/api/v1/gateway/connections/{id}/accounts/{accId}/orders` | API Key | Get account orders. | `List<OrderDto>` |

**`GET .../activities` query params:**
| Param | Type | Description |
|---|---|---|
| `startDate` | String (ISO date) | Start of date range |
| `endDate` | String (ISO date) | End of date range |

**`GET .../orders` query params:**
| Param | Type | Description |
|---|---|---|
| `status` | String | Filter by order status |

### Order Execution

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/gateway/connections/{id}/accounts/{accId}/orders` | API Key | Place a new order. | `OrderDto` |
| `POST` | `/api/v1/gateway/connections/{id}/accounts/{accId}/orders/impact` | API Key | Preview order impact (estimated cost, buying power effect). Supports options via `symbolId`, `primaryRoute`, `secondaryRoute` fields. | `OrderImpactResult` |
| `DELETE` | `/api/v1/gateway/connections/{id}/accounts/{accId}/orders/{orderId}` | API Key | Cancel an existing order. | 204 No Content |

### Health

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/gateway/health` | None | Overall gateway health with per-broker status. | `GatewayHealthResponse` |
| `GET` | `/api/v1/gateway/health/{brokerType}` | None | Health status for a specific broker (IBKR, QUESTRADE, WEALTHSIMPLE). | `BrokerHealthResponse` |
| `GET` | `/api/v1/gateway/health/ibkr` | None | IBKR-specific health with connection status, managed accounts, and uptime. | `IbkrHealthResponse` |

---

## Strategy Service (Port 8083)

**Base URL:** `http://localhost:8083`

### Strategy Endpoints

**Prefix:** `/api/v1/strategies`

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/strategies` | None | List all 7 strategy definitions. | `List<StrategyListResponse>` |
| `GET` | `/api/v1/strategies/{name}` | None | Get strategy info with education content. | `StrategyInfoResponse` |
| `POST` | `/api/v1/strategies/calculate` | None | Calculate P&L, break-evens, Greeks for leg combination. | `CalculateResponse` |
| `POST` | `/api/v1/strategies/suggest` | None | Suggest strategies by market outlook (bullish/bearish/neutral). | `List<StrategyListResponse>` |
