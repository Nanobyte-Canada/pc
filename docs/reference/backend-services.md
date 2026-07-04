# Backend Services Reference

> Complete backend services reference for AI coding agents.
> Generated from actual service source files.
>
> Cross-references: [API Endpoints](api-endpoints.md) | [Entity Relationships](entity-relationships.md) | [Database Schema](database-schema.md)

---

## Package Structure

```
com.portfolio
├── Application.kt
├── auth/
│   ├── config/
│   │   ├── AuthConfig.kt              # JWT, password, email, OAuth2, CORS settings
│   │   └── SecurityConfig.kt          # Spring Security filter chain, CORS, CSRF
│   ├── controller/
│   │   └── AuthController.kt
│   ├── security/
│   │   ├── JwtAuthenticationFilter.kt  # Extracts JWT from cookie/header
│   │   ├── JwtTokenProvider.kt         # JWT generation and validation (HS512)
│   │   ├── SecureTokenGenerator.kt     # SHA-256 token hashing, constant-time comparison
│   │   └── UserPrincipal.kt            # Spring Security UserDetails implementation
│   ├── entity/                         # User, RefreshToken, AuditLog, etc.
│   ├── repository/
│   └── service/
│       ├── AuditService.kt
│       ├── AuthenticationService.kt
│       ├── EmailService.kt
│       ├── PasswordService.kt
│       └── RefreshTokenService.kt
├── broker/
│   ├── client/
│   │   ├── BrokerGatewayClient.kt      # HTTP client for broker-gateway service
│   │   └── BrokerGatewayConfig.kt      # Gateway connection configuration
│   ├── config/
│   │   └── BrokerConfig.kt             # BrokerEncryptionConfig, BrokerSyncConfig, etc.
│   ├── controller/
│   │   ├── BrokerController.kt
│   │   ├── DashboardController.kt
│   │   ├── ModelPortfolioController.kt
│   │   ├── NotificationController.kt
│   │   ├── PerformanceController.kt
│   │   ├── PortfolioGroupController.kt
│   │   └── TradingController.kt
│   ├── dto/                            # BrokerDtos, DashboardDataDtos, PortfolioGroupDtos, TradingDtos
│   ├── entity/                         # BrokerConnection, BrokerPosition, TradeOrder, etc.
│   ├── repository/
│   ├── scheduler/
│   │   ├── AccountDataSyncScheduler.kt
│   │   └── RebalanceScheduler.kt
│   ├── security/
│   │   └── TokenEncryptionService.kt   # AES-256-GCM for broker tokens
│   └── service/
│       ├── AccountAnalyticsComputeService.kt
│       ├── ActivityIngestionService.kt
│       ├── BenchmarkService.kt
│       ├── BrokerService.kt
│       ├── DashboardCashService.kt
│       ├── DashboardDataService.kt
│       ├── DashboardExposureService.kt
│       ├── DashboardPreferenceService.kt
│       ├── DashboardRiskService.kt
│       ├── DriftCalculationService.kt
│       ├── ExchangeRateService.kt
│       ├── ModelPortfolioService.kt
│       ├── NotificationService.kt
│       ├── OrderExecutionService.kt
│       ├── PerformanceCalculationService.kt
│       ├── PortfolioGroupService.kt
│       ├── PortfolioSnapshotService.kt
│       ├── PositionFetchService.kt
│       ├── RebalanceService.kt
│       └── ReportingService.kt
├── config/
│   ├── CacheConfig.kt                 # Redis cache manager with per-cache TTLs
│   ├── GlobalExceptionHandler.kt      # @RestControllerAdvice
│   └── HealthCheckMdcFilter.kt        # Suppresses health-check request logging
├── controller/
│   ├── EtfController.kt
│   ├── HealthController.kt
│   ├── InstrumentController.kt
│   ├── PortfolioController.kt
│   ├── ReferenceDataController.kt
│   ├── StockController.kt
│   └── VersionController.kt
├── dto/                                # Core request/response DTOs
├── entity/                             # Stock, Etf, EtfHolding, GicsSector, Country, Region
├── ingestion/
│   ├── client/
│   │   └── EodhdClient.kt             # EODHD API HTTP client
│   ├── config/
│   │   ├── HttpClientConfig.kt        # WebClient beans (eodhd, alphaVantage, etfCom)
│   │   ├── IngestionConfig.kt         # Ingestion pipeline config properties
│   │   └── IngestionRedisConfig.kt    # Redis template for ingestion
│   ├── controller/
│   ├── entity/                         # IngestionRun, IngestionStep, IngestionError
│   ├── repository/
│   ├── scheduler/
│   │   └── NightlyIngestionScheduler.kt
│   └── service/
│       ├── EodhdIngestionService.kt
│       ├── IngestionHashCacheService.kt
│       ├── IngestionOrchestrator.kt
│       ├── IngestionTrackingService.kt
│       └── alphavantage/              # AVStockIngestionService
│       └── etfcom/                    # EtfComUniverseService, EtfComEnrichmentService
├── repository/                         # Core Spring Data JPA repositories
└── service/
    ├── CachedLookupService.kt
    ├── HoldingsService.kt
    ├── InstrumentSearchService.kt
    ├── LookThroughService.kt
    ├── PortfolioAnalysisService.kt
    ├── ReferenceDataService.kt
    ├── RiskMetricsService.kt
    └── ScreenerService.kt
```

---

## Auth Services

### AuthenticationService

**File:** `auth/service/AuthenticationService.kt`
**Dependencies:** `UserRepository`, `RoleRepository`, `UserRoleRepository`, `EmailVerificationTokenRepository`, `PasswordResetTokenRepository`, `PasswordService`, `JwtTokenProvider`, `RefreshTokenService`, `SecureTokenGenerator`, `EmailService`, `AuditService`, `AuthConfig`

| Method | Signature | Description |
|---|---|---|
| `signup` | `(request: SignupRequest, clientInfo: ClientInfo): SignupResponse` | Registers new user, assigns USER role, sends verification email |
| `login` | `(request: LoginRequest, clientInfo: ClientInfo): AuthTokens` | Validates credentials, checks lockout/verification, generates JWT + refresh token |
| `logout` | `(refreshToken: String, user: User, clientInfo: ClientInfo)` | Revokes refresh token, logs audit event |
| `refreshAccessToken` | `(refreshToken: String, clientInfo: ClientInfo): AuthTokens` | Rotates refresh token, generates new access token |
| `forgotPassword` | `(request: ForgotPasswordRequest, clientInfo: ClientInfo): MessageResponse` | Creates reset token, sends email (prevents enumeration) |
| `resetPassword` | `(request: ResetPasswordRequest, clientInfo: ClientInfo): MessageResponse` | Validates reset token, updates password, revokes all refresh tokens |
| `verifyEmail` | `(token: String, clientInfo: ClientInfo): MessageResponse` | Marks email as verified using token hash lookup |
| `resendVerificationEmail` | `(request: ResendVerificationRequest, clientInfo: ClientInfo): MessageResponse` | Creates new verification token and sends email |
| `changePassword` | `(userId: Long, request: ChangePasswordRequest, clientInfo: ClientInfo): MessageResponse` | Verifies current password, updates, revokes all refresh tokens |
| `updateProfile` | `(userId: Long, request: UpdateProfileRequest, clientInfo: ClientInfo): User` | Updates name and avatarUrl fields |

### GoogleOAuthService

**File:** `auth/service/GoogleOAuthService.kt`
**Dependencies:** `OAuthStateRepository`, `UserRepository`, `RoleRepository`, `UserRoleRepository`, `UserIdentityRepository`, `JwtTokenProvider`, `RefreshTokenService`, `SecureTokenGenerator`, `AuditService`, `AuthConfig`

| Method | Signature | Description |
|---|---|---|
| `initiateGoogleLogin` | `(): String` | Generates secure state token, saves to `oauth_states` table with SHA-256 hash, constructs Google authorization URL with client_id, redirect_uri, scopes (email, profile, openid). Returns authorization URL. |
| `handleCallback` | `(code: String, state: String, ipAddress: String?, userAgent: String?): AuthTokens` | Validates state token (checks hash match, expiry within 10 minutes, not already used). Exchanges authorization code for Google access token via HTTP POST. Fetches user profile from Google userinfo endpoint. Calls `findOrCreateUser()` to get/create User entity. Generates JWT and refresh token. Returns AuthTokens. |
| `findOrCreateUser` | `(profile: GoogleUserProfile, ipAddress: String?): User` | Looks up user by Google identity (provider='google', providerUserId=profile.sub). If identity found, updates identity fields (providerEmail, displayName, photoUrl, lastUsedAt) and returns linked user. If not found, checks for existing user with same email. If user with email exists, creates new Identity and links it. If no user exists, creates new User with emailVerified=true, passwordHash=null, assigns USER role, and creates Identity record. Returns User entity. |

#### Google OAuth Error Surfacing

`GoogleOAuthService.handleCallback()` and `AuthController.googleCallback()` surface Google OAuth errors using the following contracts:

**Error codes returned in `GoogleOAuthException` messages (format: `google_error:<code>`):**

| Error Code | Source | Description |
|---|---|---|
| `redirect_uri_mismatch` | Google token endpoint | The redirect_uri in the request does not match the registered redirect URI |
| `invalid_client` | Google token endpoint | OAuth client ID or secret is invalid |
| `invalid_grant` | Google token endpoint | Authorization code is invalid or expired |
| `invalid_token` | Google userinfo endpoint | Access token is invalid or expired |
| `<status_code>` | Fallback | Numeric HTTP status code when the error response body cannot be parsed |

**Log markers emitted by `AuthController`:**

| Log Marker | Log Level | Description |
|---|---|---|
| `AUTH_CALLBACK_UNEXPECTED` | ERROR | Unexpected exception during Google OAuth callback (generic catch-all for non-GoogleOAuthException, non-WebClientResponseException errors). Indicates infrastructure or unhandled runtime errors requiring ops investigation. |

### PasswordService

**File:** `auth/service/PasswordService.kt`
**Dependencies:** `AuthConfig`

| Method | Signature | Description |
|---|---|---|
| `hashPassword` | `(password: String): String` | Hashes with Argon2id (BouncyCastle), 64MB memory, 3 iterations, 4 parallelism |
| `verifyPassword` | `(password: String, storedHash: String): Boolean` | Constant-time comparison against Argon2id hash |
| `validatePasswordStrength` | `(password: String): PasswordValidationResult` | Checks min length, uppercase, lowercase, digit, special char, common patterns |

### RefreshTokenService

**File:** `auth/service/RefreshTokenService.kt`
**Dependencies:** `RefreshTokenRepository`, `SecureTokenGenerator`, `AuthConfig`

| Method | Signature | Description |
|---|---|---|
| `createRefreshToken` | `(user: User, deviceInfo: String?, ipAddress: String?): TokenPair` | Creates and stores hashed refresh token |
| `validateRefreshToken` | `(token: String): RefreshToken?` | Looks up by hash, checks validity |
| `rotateRefreshToken` | `(oldToken: String, deviceInfo: String?, ipAddress: String?): TokenPair?` | Revokes old, creates new (rotation) |
| `revokeRefreshToken` | `(token: String, reason: String): Boolean` | Revokes specific token |
| `revokeAllUserTokens` | `(userId: Long, reason: String)` | Revokes all tokens for user |
| `cleanupExpiredTokens` | `()` | Deletes expired/revoked tokens from DB |

### AuditService

**File:** `auth/service/AuditService.kt`
**Dependencies:** `AuditLogRepository`, `ObjectMapper`

| Method | Signature | Description |
|---|---|---|
| `log` | `(eventType, user?, ipAddress?, userAgent?, resourceType?, resourceId?, details?, success, errorMessage)` | Creates AuditLog entry |
| `logLogin` | `(user, ipAddress, userAgent, success, errorMessage?)` | AUTH_LOGIN / AUTH_FAILED_LOGIN |
| `logLogout` | `(user, ipAddress, userAgent)` | AUTH_LOGOUT |
| `logSignup` | `(user, ipAddress, userAgent)` | AUTH_SIGNUP |
| `logPasswordResetRequest` | `(user, ipAddress, userAgent)` | PASSWORD_RESET_REQUEST |
| `logPasswordResetComplete` | `(user, ipAddress, userAgent)` | PASSWORD_RESET_COMPLETE |
| `logEmailVerification` | `(user, ipAddress, userAgent)` | EMAIL_VERIFICATION |
| `logOAuthLink` | `(user, provider, ipAddress, userAgent)` | OAUTH_LINK |
| `logUserLock` | `(user, reason, ipAddress)` | USER_LOCK |
| `logPasswordChange` | `(user, ipAddress, userAgent)` | PASSWORD_CHANGE |
| `logProfileUpdate` | `(user, ipAddress, userAgent)` | PROFILE_UPDATE |

### EmailService

**File:** `auth/service/EmailService.kt`
**Dependencies:** `AuthConfig`

| Method | Signature | Description |
|---|---|---|
| `sendVerificationEmail` | `(user: User, token: String, baseUrl: String)` | Sends email verification link (console provider in dev) |
| `sendPasswordResetEmail` | `(user: User, token: String, baseUrl: String)` | Sends password reset link |
| `sendWelcomeEmail` | `(user: User)` | Sends welcome email after signup |
| `sendAccountLockedEmail` | `(user: User)` | Sends account lock notification |

---

## Broker Services

### BrokerService

**File:** `broker/service/BrokerService.kt`
**Dependencies:** `BrokerConnectionRepository`, `BrokerPositionRepository`, `BrokerBalanceRepository`, `UserRepository`, `BrokerGatewayClient`, `AuditService`, `ObjectMapper`

| Method | Signature | Description |
|---|---|---|
| `getUserConnections` | `(userId: Long): List<BrokerConnectionDto>` | Lists non-disconnected connections for user |
| `getUserConnectionEntities` | `(userId: Long): List<BrokerConnection>` | Lists raw connection entities |
| `getActiveConnections` | `(userId: Long): List<BrokerConnectionDto>` | Lists only ACTIVE connections |
| `getConnection` | `(connectionId: Long, userId: Long): BrokerConnection` | Gets single connection with auth check |
| `createGatewayConnection` | `(userId: Long, brokerType: String, credentials: Map<String, String>): List<BrokerConnection>` | Creates a new connection via the broker-gateway and creates one `BrokerConnection` per discovered account. Returns a list of connections for multi-account brokers. |
| `syncConnections` | `(userId: Long)` | Syncs accounts from the broker-gateway for all active connections. Validates per unique `gatewayConnectionId` to avoid redundant API calls. |
| `disconnectBroker` | `(authorizationId: String, userId: Long)` | Disconnects via broker-gateway and marks all accounts sharing the same gateway connection as DISCONNECTED |
| `getPositionsForConnection` | `(connectionId: Long, userId: Long): ConnectionPositionsResponse` | Returns positions with P&L summary for one connection |
| `getAggregatedPositions` | `(userId: Long): AggregatedPositionsResponse` | Aggregates positions across all active connections, grouped by symbol |
| `getBalanceHistory` | `(connectionId: Long, startDate: LocalDate, endDate: LocalDate): BalanceHistoryResponse` | Returns balance snapshots for date range |

### PositionFetchService

**File:** `broker/service/PositionFetchService.kt`
**Dependencies:** `BrokerConnectionRepository`, `BrokerPositionRepository`, `PositionFetchLogRepository`, `BrokerBalanceRepository`, `TradeOrderRepository`, `UserRepository`, `BrokerGatewayClient`, `AccountAnalyticsComputeService`, `AuditService`, `ObjectMapper`

| Method | Signature | Description |
|---|---|---|
| `triggerManualFetch` | `(connectionId: Long, userId: Long): PositionFetchLog` | Creates fetch log, triggers async fetch, returns immediately |
| `executeAsyncFetch` | `@Async (connectionId, fetchLogId, userId)` | Asynchronous position fetch wrapper |
| `executePositionFetch` | `(connectionId, fetchLogId, userId): PositionFetchLog` | Full fetch: positions, balance snapshot, order sync, analytics computation |

Internal responsibilities:
- Fetches positions, balances, and orders from the broker-gateway via `BrokerGatewayClient`
- Marks old positions as non-current before saving new ones
- Resolves instrument types (ETF/STOCK/OPTION) from gateway-provided data
- Saves balance snapshots (cash + buying power by currency in JSONB)
- Syncs broker orders into `trade_orders` table
- Triggers `AccountAnalyticsComputeService.computeForConnection()` after successful position sync to pre-compute analytics snapshots

### DashboardDataService

**File:** `broker/service/DashboardDataService.kt`
**Dependencies:** `BrokerPositionRepository`, `BrokerConnectionRepository`, `BrokerActivityRepository`, `BrokerBalanceRepository`, `TradeOrderRepository`, `PortfolioGroupAccountRepository`, `AccountAnalyticsRepository`, `StockRepository`, `EtfRepository`, `CountryRepository`, `LookThroughService`, `DriftCalculationService`, `PositionFetchService`, `DashboardCashService`, `DashboardExposureService`, `DashboardRiskService`

Orchestrates all dashboard widget data endpoints. Delegates to sub-services for cash, exposure, and risk. For sector exposure, geography exposure, and risk profile, reads from pre-computed `account_analytics` snapshots when available (with fallback to live computation). Aggregates analytics across multiple connections for multi-account dashboard views.

| Method | Signature | Description |
|---|---|---|
| `getIrrData` | `(userId: Long, connectionId: Long?): DashboardIrrResponse` | IRR per account and portfolio-wide, calculated via Newton-Raphson from balance snapshots and cash flow activities |
| `getSummary` | `(userId: Long, connectionId: Long?): DashboardSummaryResponse` | Portfolio value, day P&L, positions summary, look-through holdings count |
| `getCash` | `(userId, connectionId?): DashboardCashResponse` | Delegates to DashboardCashService |
| `getSectorExposure` | `(userId, connectionId?): SectorExposureResponse` | Reads from account_analytics snapshots (falls back to DashboardExposureService for live computation) |
| `getGeographyExposure` | `(userId, connectionId?): GeographyExposureResponse` | Reads from account_analytics snapshots (falls back to DashboardExposureService for live computation) |
| `getRiskProfile` | `(userId, connectionId?): RiskProfileResponse` | Reads from account_analytics snapshots (falls back to DashboardRiskService for live computation) |
| `getOpenOrders` | `(userId: Long): OpenOrdersResponse` | Fetches PENDING/SUBMITTED/PARTIALLY_FILLED orders |
| `getFees` | `(userId, connectionId?): FeesResponse` | Last 12 months fees, commissions, weighted MER |
| `getDividendCalendar` | `(userId, month?, connectionId?): DividendCalendarResponse` | Dividend/distribution entries for a month |
| `getHoldings` | `(userId, connectionId?): HoldingsTableResponse` | Look-through holdings with sources and coverage |
| `getAccounts` | `(userId: Long): DashboardAccountsResponse` | Connected accounts with values, linked groups, model portfolios |
| `refreshAll` | `(userId: Long): RefreshAllResponse` | Triggers position fetch for all active connections |
| `backfillStockGicsCodes` | `(): Int` | Admin: backfills GICS codes from AlphaVantage raw data |

### DashboardCashService

**File:** `broker/service/DashboardCashService.kt`
**Dependencies:** `BrokerConnectionRepository`, `BrokerBalanceRepository`, `ExchangeRateService`, `ObjectMapper`

| Method | Signature | Description |
|---|---|---|
| `getCash` | `(userId: Long, connectionId: Long?): DashboardCashResponse` | Aggregates cash and buying power by currency, FX-converts to CAD |
| `getTotalCashFromSnapshot` | `(connId: Long): BigDecimal` | Returns total cash in CAD from latest balance snapshot |
| `getBuyingPowerFromSnapshot` | `(connId: Long): BigDecimal?` | Returns buying power in CAD from latest snapshot |

### DashboardExposureService

**File:** `broker/service/DashboardExposureService.kt`
**Dependencies:** `BrokerPositionRepository`, `BrokerConnectionRepository`, `StockRepository`, `EtfRepository`, `CountryRepository`, `LookThroughService`

| Method | Signature | Description |
|---|---|---|
| `getSectorExposure` | `(userId: Long, connectionId: Long?): SectorExposureResponse` | Look-through sector analysis with industry group breakdown |
| `getGeographyExposure` | `(userId: Long, connectionId: Long?): GeographyExposureResponse` | Look-through geography analysis grouped by region |

### DashboardRiskService

**File:** `broker/service/DashboardRiskService.kt`
**Dependencies:** `BrokerPositionRepository`, `StockRepository`, `EtfRepository`, `CountryRepository`, `LookThroughService`

| Method | Signature | Description |
|---|---|---|
| `getRiskProfile` | `(userId: Long, connectionId: Long?): RiskProfileResponse` | Calculates risk score (0-100) from HHI, top-10, sector, geographic, and asset type concentration |

Risk scoring breakdown: concentrationHHI (0-25), top10Weight (0-20), sectorHHI (0-20), geographicConcentration (0-15), assetTypeDiversity (0-10), holdingsCount (0-10).

### DashboardPreferenceService

**File:** `broker/service/DashboardPreferenceService.kt`
**Dependencies:** `DashboardPreferenceRepository`, `UserRepository`

| Method | Signature | Description |
|---|---|---|
| `getPreferences` | `(userId, contextType, contextId?): DashboardPreferencesResponse` | Gets widget visibility/order preferences |
| `updatePreferences` | `(userId, request, contextType, contextId?): DashboardPreferencesResponse` | Replaces all preferences (delete + insert with flush) |
| `resetPreferences` | `(userId, contextType, contextId?): DashboardPreferencesResponse` | Deletes all preferences, returns empty (frontend defaults apply) |

### DriftCalculationService

**File:** `broker/service/DriftCalculationService.kt`
**Dependencies:** `PortfolioGroupRepository`, `PortfolioGroupAccountRepository`, `PortfolioTargetRepository`, `PortfolioExcludedAssetRepository`, `BrokerPositionRepository`, `BrokerBalanceRepository`, `ExchangeRateService`, `ObjectMapper`

| Method | Signature | Description |
|---|---|---|
| `calculateDrift` | `(groupId: Long): DriftAnalysisResponse` | Full drift analysis: holdings vs targets, excluded assets, new assets, accuracy |
| `calculateAccuracy` | `(groupId: Long): BigDecimal` | Quick accuracy: 100 - mean(abs(drift%)), clamped >= 0 |
| `calculateTotalValue` | `(groupId: Long): BigDecimal` | Sum of positions + cash across linked accounts |
| `getRebalanceProgress` | `(connection: BrokerConnection): RebalanceProgressDto` | Shows actual vs target for model portfolio applied to a connection |

### RebalanceService

**File:** `broker/service/RebalanceService.kt`
**Dependencies:** `PortfolioGroupAccountRepository`, `PortfolioGroupSettingsRepository`, `BrokerPositionRepository`, `BrokerBalanceRepository`, `BrokerConnectionRepository`, `ExchangeRateService`, `DriftCalculationService`, `ObjectMapper`

| Method | Signature | Description |
|---|---|---|
| `calculateRebalanceTrades` | `(groupId: Long): RebalanceTradesResponse` | Generates buy/sell trades to rebalance a portfolio group. Respects sellToRebalance and keepCurrenciesSeparate settings. Minimum trade amount: $10. |
| `calculateTradesForAccount` | `(connection: BrokerConnection): PendingOrdersResponse` | Generates orders for a single account to match its applied model portfolio. Liquidates non-model positions first, then adjusts/creates model positions. FX-converts all to CAD. |

### PortfolioGroupService

**File:** `broker/service/PortfolioGroupService.kt`
**Dependencies:** `PortfolioGroupRepository`, `PortfolioTargetRepository`, `PortfolioGroupAccountRepository`, `PortfolioGroupSettingsRepository`, `PortfolioExcludedAssetRepository`, `BrokerConnectionRepository`, `DriftCalculationService`

| Method | Signature | Description |
|---|---|---|
| `createGroup` | `(userId, request): PortfolioGroupDetailDto` | Creates group with default settings, optional targets and accounts |
| `getGroup` | `(groupId, userId): PortfolioGroupDetailDto` | Gets group detail with accuracy and total value |
| `listGroups` | `(userId): PortfolioGroupsListResponse` | Lists all groups with summaries |
| `updateGroup` | `(groupId, userId, request): PortfolioGroupDetailDto` | Updates name/description |
| `deleteGroup` | `(groupId, userId)` | Deletes group |
| `setTargets` | `(groupId, userId, request): List<TargetAllocationDto>` | Replaces all targets (validates total <= 100%) |
| `addTarget` | `(groupId, userId, input): TargetAllocationDto` | Adds single target (checks duplicates, total) |
| `removeTarget` | `(groupId, userId, symbol)` | Removes target by symbol |
| `linkAccount` | `(groupId, userId, connectionId): LinkedAccountDto` | Links broker connection to group |
| `unlinkAccount` | `(groupId, userId, connectionId)` | Unlinks connection from group |
| `getSettings` | `(groupId, userId): PortfolioGroupSettingsDto` | Gets rebalance/trading settings |
| `updateSettings` | `(groupId, userId, request): PortfolioGroupSettingsDto` | Updates settings (rebalance frequency, thresholds, flags) |
| `getExcludedAssets` | `(groupId, userId): List<ExcludedAssetDto>` | Lists excluded assets |
| `addExcludedAsset` | `(groupId, userId, symbol): ExcludedAssetDto` | Adds asset to exclusion list |
| `removeExcludedAsset` | `(groupId, userId, symbol)` | Removes from exclusion list |
| `getGroupEntity` | `(groupId, userId): PortfolioGroup` | Auth-checked entity fetch |

Static method: `computeNextRebalanceDate(frequency, from): LocalDate` -- computes next rebalance date for MONTHLY/QUARTERLY/SEMI_ANNUALLY/ANNUALLY.

### ModelPortfolioService

**File:** `broker/service/ModelPortfolioService.kt`
**Dependencies:** `ModelPortfolioRepository`, `ModelPortfolioAllocationRepository`, `BrokerConnectionRepository`, `StockRepository`, `EtfRepository`, `LookThroughService`

| Method | Signature | Description |
|---|---|---|
| `listAll` | `(userId: Long): ModelPortfoliosListResponse` | Lists system + user-owned models |
| `getById` | `(id, userId): ModelPortfolioDetailDto` | Gets model with allocations |
| `create` | `(userId, request): ModelPortfolioDetailDto` | Creates custom model (validates allocations sum <= 100%) |
| `update` | `(id, userId, request): ModelPortfolioDetailDto` | Updates name/description/allocations (cannot modify system models) |
| `delete` | `(id, userId)` | Deletes model (cannot delete system models) |
| `applyToAccounts` | `(userId, modelId, connectionIds)` | Sets model_portfolio on connections |
| `getAnalysis` | `(id, userId): ModelAnalysisDto` | Look-through analysis: sector exposure, geography, risk score |

### PerformanceCalculationService

**File:** `broker/service/PerformanceCalculationService.kt`
**Dependencies:** `PortfolioSnapshotRepository`, `PortfolioCashFlowRepository`, `BenchmarkReturnRepository`, `BenchmarkService` (lazy)

| Method | Signature | Description |
|---|---|---|
| `getPerformanceSummary` | `(groupId, startDate, endDate): PerformanceSummaryDto` | TWR, MWR, volatility, Sharpe, Sortino, max drawdown |
| `calculateTWR` | `(groupId, startDate, endDate): BigDecimal` | Time-weighted return with sub-period linking at cash flow dates |
| `calculateMWR` | `(groupId, startDate, endDate): BigDecimal` | Money-weighted return via Newton-Raphson IRR approximation |
| `calculateMaxDrawdown` | `(snapshots): BigDecimal` | Peak-to-trough drawdown percentage |
| `getCumulativeReturns` | `(groupId, startDate, endDate): List<ReturnPoint>` | Daily cumulative return series |
| `getDrawdowns` | `(groupId, startDate, endDate): List<DrawdownPoint>` | Daily drawdown series |
| `getBenchmarkComparison` | `(groupId, benchmarkSymbol, startDate, endDate): BenchmarkComparisonDto` | Portfolio vs benchmark with alpha. Supports `MODEL:{id}` format for model benchmarks. |
| `getPerformanceChart` | `(groupId, startDate, endDate, benchmark?): PerformanceChartData` | Combined summary + returns + drawdowns + benchmark comparison |

Constants: `RISK_FREE_RATE = 4%` annual, used for Sharpe/Sortino.

### BenchmarkService

**File:** `broker/service/BenchmarkService.kt`
**Dependencies:** `BenchmarkReturnRepository`, `ModelPortfolioRepository`

| Method | Signature | Description |
|---|---|---|
| `getBenchmarkReturns` | `(symbol, startDate, endDate): List<BenchmarkReturn>` | Fetches benchmark price data |
| `saveBenchmarkReturn` | `(symbol, date, closePrice, previousClose?)` | Saves benchmark data point |
| `getSupportedBenchmarks` | `(): List<Pair<String, String>>` | Returns SPY and XIU |
| `getModelPortfolioBenchmarkReturns` | `(modelId, startDate, endDate): List<ReturnPoint>` | Calculates theoretical cumulative returns for a model portfolio using weighted constituent returns |

### PortfolioSnapshotService

**File:** `broker/service/PortfolioSnapshotService.kt`
**Dependencies:** `PortfolioSnapshotRepository`, `PortfolioGroupRepository`, `DriftCalculationService`, `ObjectMapper`

| Method | Signature | Description |
|---|---|---|
| `takeSnapshot` | `(group, date?): SnapshotDto` | Captures portfolio state (value, positions JSON, cash JSON, accuracy) |
| `takeSnapshotsForAllGroups` | `(date?)` | Takes snapshots for all groups (idempotent per date) |
| `getSnapshots` | `(groupId, startDate, endDate): List<SnapshotDto>` | Retrieves historical snapshots |

### OrderExecutionService

**File:** `broker/service/OrderExecutionService.kt`
**Dependencies:** `TradeOrderRepository`, `BrokerConnectionRepository`, `PortfolioGroupService`, `BrokerGatewayClient`

| Method | Signature | Description |
|---|---|---|
| `executeTradesForGroup` | `(user, request): ExecuteTradesResponse` | Executes batch trades: creates TradeOrder records, submits via BrokerGatewayClient, tracks success/failure |
| `executeSingleTrade` | `(user, groupId, tradeInput): TradeOrderDto` | Executes a single trade (wraps executeTradesForGroup) |
| `getOrdersForGroup` | `(userId, groupId): OrderStatusResponse` | Lists orders for a portfolio group |
| `getOrdersForBatch` | `(userId, batchId: UUID): OrderStatusResponse` | Lists orders by batch ID |
| `cancelOrder` | `(user, orderId): TradeOrderDto` | Cancels order locally and via BrokerGatewayClient |

### AccountAnalyticsComputeService

**File:** `broker/service/AccountAnalyticsComputeService.kt`
**Dependencies:** `AccountAnalyticsRepository`, `BrokerConnectionRepository`, `BrokerPositionRepository`, `LookThroughService`, `ExchangeRateService`, `ObjectMapper`

Pre-computes analytics for a brokerage connection on each position sync. Normalizes multi-currency positions to CAD, computes sector/geography exposure (totaling 100% with "Unknown" bucket), risk profile (composite 0-100 score), weighted MER, and look-through holdings list. Upserts results into the `account_analytics` table (one snapshot per connection).

| Method | Signature | Description |
|---|---|---|
| `computeForConnection` | `(connectionId: Long)` | Computes and upserts analytics snapshot for a single connection. Called by PositionFetchService after successful position sync. |

Internal responsibilities:
- Fetches current positions for the connection and normalizes all values to CAD using ExchangeRateService
- Computes sector exposure via LookThroughService, ensuring all weights total 100% (unresolved weight goes to "Unknown" bucket)
- Computes geography exposure via LookThroughService with region-level aggregation
- Computes composite risk score (0-100) from concentration metrics
- Calculates weighted MER across all ETF/fund positions
- Persists snapshot as JSONB in account_analytics (upsert by connection_id)

### ActivityIngestionService

**File:** `broker/service/ActivityIngestionService.kt`
**Dependencies:** `BrokerConnectionRepository`, `BrokerActivityRepository`, `BrokerBalanceRepository`, `BrokerGatewayClient`, `UserRepository`, `ObjectMapper`, `ExchangeRateService`

| Method | Signature | Description |
|---|---|---|
| `syncActivitiesForConnection` | `(connectionId: Long): Int` | If no activities exist, fetches full history (configurable, default 25 years). Otherwise incremental sync from latest date (overlaps by 1 day). Activity type normalization is done by the broker-gateway; computes CAD amounts locally. |
| `syncBalanceForConnection` | `(connectionId: Long)` | Fetches balance from broker-gateway and saves snapshot |
| `syncAllConnections` | `()` | Syncs activities + balances for all non-disconnected connections |

Activity type normalization (done by gateway): BUY, SELL, DIVIDEND, TRANSFER_IN, TRANSFER_OUT, FEE, COMMISSION, INTEREST, OPTIONEXPIRATION, OPTIONASSIGNMENT, OPTIONEXERCISE.

### ReportingService

**File:** `broker/service/ReportingService.kt`
**Dependencies:** `BrokerConnectionRepository`, `BrokerActivityRepository`, `BrokerBalanceRepository`, `ObjectMapper`

| Method | Signature | Description |
|---|---|---|
| `getPerformanceReport` | `(userId, startDate?, endDate?, connectionIds?, granularity?): ReportingPerformanceResponse` | Contributions/withdrawals by period, total value history, dividend history by symbol, KPIs |
| `getActivitiesReport` | `(userId, page, size, startDate?, endDate?, connectionIds?, type?): ActivitiesResponse` | Paginated filtered activities across connections |

### NotificationService

**File:** `broker/service/NotificationService.kt`
**Dependencies:** `NotificationRepository`, `NotificationPreferenceRepository`

| Method | Signature | Description |
|---|---|---|
| `createNotification` | `(user, type, title, message, link?, metadata?): NotificationDto` | Creates notification record |
| `getNotifications` | `(userId, unreadOnly, page, size): NotificationsResponse` | Paginated notifications with unread count |
| `getUnreadCount` | `(userId): Long` | Count of unread notifications |
| `markAsRead` | `(userId, notificationId): NotificationDto` | Marks single notification as read |
| `markAllAsRead` | `(userId): Int` | Marks all user notifications as read |
| `getPreferences` | `(userId): NotificationPreferenceDto` | Gets notification preferences |
| `updatePreferences` | `(user, request): NotificationPreferenceDto` | Updates notification preferences |

### ExchangeRateService

**File:** `broker/service/ExchangeRateService.kt`
**Dependencies:** `WebClient.Builder`, `@Value("${exchange-rate.base-url}")`

| Method | Signature | Description |
|---|---|---|
| `getRate` | `@Cacheable("exchange-rates") (currency: String, date: LocalDate): BigDecimal?` | Fetches FX rate to CAD from Bank of Canada Valet API. Walks back up to 5 days for weekends/holidays. Returns ONE for CAD, null for unsupported currencies. |

Supports 28 currencies via BoC series mapping (USD, EUR, GBP, JPY, AUD, CHF, CNY, HKD, MXN, NOK, NZD, SEK, SGD, BRL, INR, KRW, ZAR, TRY, TWD, DKK, SAR, MYR, PLN, RUB, THB, PEN, IDR, COP).

### BrokerGatewayClient

**File:** `broker/client/BrokerGatewayClient.kt`
**Dependencies:** `BrokerGatewayConfig`, `ObjectMapper`

HTTP client for communicating with the broker-gateway microservice (port 8084). Authenticates via API key header. All broker data operations (positions, activities, balances, orders) are routed through this client instead of directly calling broker SDKs.

| Method | Signature | Description |
|---|---|---|
| `createConnection` | `(userId, brokerType, credentials): GatewayConnectionDto` | Registers a new broker connection in the gateway |
| `getAccounts` | `(gatewayConnectionId): List<GatewayAccountDto>` | Lists accounts for a gateway connection |
| `getPositions` | `(gatewayConnectionId, accountId): List<GatewayPositionDto>` | Fetches positions for an account |
| `getBalances` | `(gatewayConnectionId, accountId): List<GatewayBalanceDto>` | Fetches account balances |
| `getActivities` | `(gatewayConnectionId, accountId, startDate?, endDate?): List<GatewayActivityDto>` | Fetches account activities |
| `getOrders` | `(gatewayConnectionId, accountId): List<GatewayOrderDto>` | Fetches account orders |
| `placeOrder` | `(gatewayConnectionId, accountId, orderRequest): GatewayOrderDto` | Places an order |
| `cancelOrder` | `(gatewayConnectionId, accountId, orderId)` | Cancels an order |
| `getOrderImpact` | `(gatewayConnectionId, accountId, orderRequest): OrderImpactResult` | Previews order impact (estimated cost, buying power effect). Supports options trading fields (`symbolId`, `primaryRoute`, `secondaryRoute`). |
| `deleteConnection` | `(gatewayConnectionId)` | Removes a gateway connection |
| `getGatewayHealth` | `(): GatewayHealthResponse` | Checks broker-gateway health status |

### BrokerGatewayConfig

**File:** `broker/client/BrokerGatewayConfig.kt`
**Prefix:** `broker-gateway`

| Property | Type | Default | Description |
|---|---|---|---|
| `broker-gateway.url` | String | `http://broker-gateway-service:8084` | Base URL for the broker-gateway service |
| `broker-gateway.api-key` | String | `dev-gateway-key` | API key for service-to-service auth |
| `broker-gateway.timeout` | Duration | `30s` | HTTP request timeout |

---

## Security Services

### TokenEncryptionService

**File:** `broker/security/TokenEncryptionService.kt`
**Dependencies:** `@Value("${broker.encryption.secret-key}")`

AES-256-GCM encryption for broker OAuth tokens. Uses 12-byte IV prepended to ciphertext, 128-bit auth tag.

| Method | Signature | Description |
|---|---|---|
| `encrypt` | `(plaintext: String): String` | Encrypts to Base64 (IV + ciphertext) |
| `decrypt` | `(encryptedToken: String): String` | Decrypts from Base64 |
| `generateKeyBase64` | `(): String` | Generates a new 256-bit key as Base64 |
| `validateConfiguration` | `(): Boolean` | Round-trip encrypt/decrypt test |

If no key is configured, generates an ephemeral key (with warning). Key must be exactly 32 bytes.

### JwtTokenProvider

**File:** `auth/security/JwtTokenProvider.kt`
**Dependencies:** `AuthConfig`

| Method | Signature | Description |
|---|---|---|
| `generateAccessToken` | `(user: User, roles: List<String>): String` | Creates HS512-signed JWT with sub, email, roles, type=access |
| `validateToken` | `(token: String): Jws<Claims>?` | Parses and validates JWT |
| `getUserIdFromToken` | `(token: String): Long?` | Extracts user ID from subject |
| `getRolesFromToken` | `(token: String): List<String>` | Extracts roles claim |
| `isAccessToken` | `(token: String): Boolean` | Checks type=access claim |

Signing key must be >= 64 characters for HS512.

### JwtAuthenticationFilter

**File:** `auth/security/JwtAuthenticationFilter.kt`
**Dependencies:** `JwtTokenProvider`, `UserRepository`

Extends `OncePerRequestFilter`. Extracts JWT from `access_token` cookie or `Authorization: Bearer` header. Sets `SecurityContextHolder` authentication with `UserPrincipal`.

### SecureTokenGenerator

**File:** `auth/security/SecureTokenGenerator.kt`

| Method | Signature | Description |
|---|---|---|
| `generateToken` | `(length: Int = 32): TokenPair` | Generates random token + SHA-256 hash |
| `hashToken` | `(token: String): String` | SHA-256 hash of token |
| `verifyToken` | `(token, storedHash): Boolean` | Constant-time hash comparison |
| `generateRefreshToken` | `(): TokenPair` | 48-byte random token |
| `generateStateToken` | `(): TokenPair` | 32-byte random token for OAuth state |

### UserPrincipal

**File:** `auth/security/UserPrincipal.kt`

Implements Spring Security `UserDetails`. Contains `id`, `email`, authorities (ROLE_USER, ROLE_ADMIN). Static factory: `from(user: User, roles: List<String>)`.

---

## Core Services

### LookThroughService

**File:** `broker/service/LookThroughService.kt`
**Dependencies:** `IngestionInstrumentLookupService`, `CountryRegionLookupService`

Decomposes ETFs into underlying stock holdings for true exposure analysis. Central to sector, geography, risk, and holdings calculations.

**V67 Rewrite:** Migrated from `stocks`/`etfs`/`etf_holdings` tables to `ingestion.instruments` + `ingestion.provider_raw_data` via cross-schema queries. No longer uses `StockRepository`, `EtfRepository`, `EtfHoldingRepository`, or `CachedLookupService`.

| Method | Signature | Description |
|---|---|---|
| `computeLookThroughWithQuality` | `(positions: List<PortfolioPositionRequest>, date: LocalDate): LookThroughResult` | Full look-through with quality metrics, unresolved exposures, and ETF direct sector allocations |

Key data structures:
- `LookThroughExposure`: ticker + effectiveWeight + sources + sector + country
- `LookThroughResult`: exposures + unresolvedExposures + quality + etfDirectSectorExposures
- `LookThroughQuality`: totalHoldingsCount, resolvedCount, unresolvedCount, coveragePercent

Contains static GICS sector/industry group name maps (`GICS_SECTOR_NAMES`, `GICS_INDUSTRY_GROUP_NAMES`) and sector mapping tables (`FACTSET_SECTOR_TO_GICS`, `AV_INDUSTRY_TO_GICS_IG`).

### PortfolioAnalysisService

**File:** `service/PortfolioAnalysisService.kt`
**Dependencies:** `LookThroughService`, `RiskMetricsService`, `ReferenceDataService`

| Method | Signature | Description |
|---|---|---|
| `analyze` | `(request: PortfolioAnalyzeRequest): PortfolioAnalysisResponseDto` | Full portfolio analysis: sector, geography, top holdings, risk, financial summary |
| `validate` | `(request: PortfolioValidateRequest): ValidateResponseDto` | Validates portfolio positions |
| `normalize` | `(request: PortfolioNormalizeRequest): NormalizeResponseDto` | Normalizes portfolio weights to 100% |

### RiskMetricsService

**File:** `broker/service/RiskMetricsService.kt`

| Method | Signature | Description |
|---|---|---|
| `computeEnhancedRiskMetrics` | `(exposures, sectorExposures): RiskMetricsDto` | HHI, top-10, sector concentration, beta-based or proxy volatility, dividend yield, P/E ratio |
| `computeRiskMetrics` | `(exposures, sectorExposures): RiskMetricsDto` | Legacy alias for computeEnhancedRiskMetrics |

Uses stock-level beta when coverage >= 50%, otherwise falls back to sector volatility proxies. Market volatility assumption: 18%.

### InstrumentScreenerService

**File:** `service/InstrumentScreenerService.kt`
**Dependencies:** `InstrumentScreenerRepository`, `InstrumentDetailRepository`

**V67 Rewrite:** Replaces `ScreenerService`, `InstrumentSearchService`, and `ReferenceDataService`. Reads from `ingestion.instruments` and `ingestion.provider_raw_data` via native SQL queries.

| Method | Signature | Description |
|---|---|---|
| `getInstruments` | `(type, filter, page, size, sort): Page<InstrumentScreenerItemDto>` | Paginated screener grid with dynamic filtering and sorting |
| `getInstrumentDetail` | `(type, ticker): InstrumentDetailDto` | Full instrument detail with parsed JSONB sections from provider_raw_data |
| `search` | `(query, types, limit): SearchResponseDto` | Multi-strategy search: ISIN -> exact ticker -> prefix -> name contains |
| `getReferenceValues` | `(type, field): List<String>` | Distinct values for dropdown filters (country, exchange, sector, issuer, etc.) |
| `getTypeCounts` | `(): Map<String, Long>` | Count of instruments by type |

### IngestionInstrumentLookupService

**File:** `service/IngestionInstrumentLookupService.kt`
**Dependencies:** `EntityManager` (direct SQL queries to `ingestion` schema)

Provides fast read-only access to ingestion schema data for portfolio features.

| Method | Signature | Description |
|---|---|---|
| `findByTicker` | `(ticker): IngestionInstrument?` | Fetch instrument by ticker from `ingestion.instruments` |
| `findEtfHoldings` | `(ticker): List<IngestionEtfHolding>` | Fetch ETF holdings from `ingestion.provider_raw_data` JSONB (EODHD holdings payload) |

Data classes: `IngestionInstrument`, `IngestionEtfHolding` (Kotlin data classes, not JPA entities).

### CountryRegionLookupService

**File:** `service/CountryRegionLookupService.kt`
**Dependencies:** `CountryRepository`, `RegionRepository`

All methods use `@Cacheable` with Redis.

| Method | Signature | Description |
|---|---|---|
| `getCountryToRegionMap` | `(): Map<String, String>` | Country code -> region name (cached 24h) |
| `getCountryNameMap` | `(): Map<String, String>` | Country code -> country name (cached 24h) |
| `getRegionForCountry` | `(countryCode): String` | Single country -> region lookup |
| `getCountryName` | `(countryCode): String` | Single country name lookup |

---

## Legacy Ingestion Services (V68 Cleanup)

The following services were removed in V67-V68 migration. Ingestion is now managed by the separate `ingestion-service` microservice.

**Removed services:** `IngestionOrchestrator`, `IngestionTrackingService`, `EodhdIngestionService`, `AVStockIngestionService`, `EtfComUniverseService`, `EtfComEnrichmentService`, `IngestionHashCacheService`

**Removed controllers:** `AdminIngestionController`, `StockIngestionController`, `StockEnrichmentController`, `EtfComController`

The portfolio app now reads instrument data via cross-schema SQL queries to `ingestion.instruments` and `ingestion.provider_raw_data` tables managed by the ingestion-service.

---

## Ingestion Client

### EodhdClient

**File:** `ingestion/client/EodhdClient.kt`
**Dependencies:** `@Qualifier("eodhdWebClient") WebClient`, `IngestionConfig`

| Method | Signature | Description |
|---|---|---|
| `getExchangeSymbols` | `(exchange: String): List<EodhdInstrumentDto>` | GET `/api/exchange-symbol-list/{EXCHANGE}` with rate limiting |
| `getFundamentals` | `(symbol: String, exchange: String): EodhdFundamentalsDto?` | GET `/api/fundamentals/{SYMBOL}.{EXCHANGE}` |

---

## Schedulers

### AccountDataSyncScheduler

**File:** `broker/scheduler/AccountDataSyncScheduler.kt`
**Condition:** `broker.sync.enabled=true`
**Dependencies:** `ActivityIngestionService`, `PositionFetchService`, `BrokerConnectionRepository`, `PortfolioSnapshotService`

| Schedule | Cron | Method | Description |
|---|---|---|---|
| Nightly sync | `${broker.sync.cron:0 30 22 * * *}` (default: 10:30 PM) | `runNightlySync()` | Syncs activities + balances for all connections, refreshes positions for active connections, takes daily portfolio snapshots |

### RebalanceScheduler

**File:** `broker/scheduler/RebalanceScheduler.kt`
**Condition:** `broker.sync.enabled=true`
**Dependencies:** `PortfolioGroupRepository`, `PortfolioGroupSettingsRepository`, `RebalanceEventRepository`, `DriftCalculationService`, `RebalanceService`, `NotificationService`

| Schedule | Cron | Method | Description |
|---|---|---|---|
| Rebalance check | `${rebalance.check.cron:0 0 7 * * *}` (default: 7 AM) | `checkRebalanceNeeded()` | Checks all groups for scheduled rebalance (date-based) or accuracy threshold trigger. Creates notifications and RebalanceEvent records. |

Trigger types: `SCHEDULED` (next_rebalance_date <= today), `ACCURACY_DROP` (accuracy < threshold).

### NightlyIngestionScheduler

**File:** `ingestion/scheduler/NightlyIngestionScheduler.kt`
**Condition:** `ingestion.enabled=true`

| Schedule | Cron | Method | Description |
|---|---|---|---|
| Full ingestion | `${ingestion.schedule:0 0 22 * * *}` (default: 10 PM) | `runNightlyIngestion()` | Runs full ingestion pipeline (EODHD + AV + etf.com) |

---

## Configuration Classes

### AuthConfig

**File:** `auth/config/AuthConfig.kt`
**Prefix:** `auth`

| Property | Type | Default | Description |
|---|---|---|---|
| `auth.jwt.signingKey` | String | "" | HS512 signing key (min 64 chars) |
| `auth.jwt.accessTokenExpiration` | Duration | 15m | Access token lifetime |
| `auth.jwt.refreshTokenExpiration` | Duration | 7d | Refresh token lifetime |
| `auth.jwt.issuer` | String | "portfolio-app" | JWT issuer claim |
| `auth.password.minLength` | Int | 12 | Minimum password length |
| `auth.password.maxFailedAttempts` | Int | 5 | Account lockout threshold |
| `auth.password.lockoutDuration` | Duration | 30m | Lockout duration |
| `auth.email.provider` | String | "console" | Email provider (console = log only) |
| `auth.email.from` | String | "noreply@portfolio.local" | Sender address |
| `auth.email.verificationExpiry` | Duration | 24h | Email verification token TTL |
| `auth.email.resetExpiry` | Duration | 6h | Password reset token TTL |
| `auth.oauth2.google.clientId` | String | "" | Google OAuth client ID |
| `auth.oauth2.google.clientSecret` | String | "" | Google OAuth client secret |
| `auth.cors.allowedOrigins` | String | "http://localhost:3000" | Comma-separated CORS origins |

### SecurityConfig

**File:** `auth/config/SecurityConfig.kt`

Spring Security filter chain configuration:
- CSRF: CookieCsrfTokenRepository (HttpOnly=false), disabled for auth + /api/** + /health + /actuator/**
- CORS: Configured from AuthConfig, credentials allowed, max-age 3600s
- Sessions: Stateless (JWT-based)
- Public paths: /health, /api/v1/version, /auth/login|signup|refresh|forgot-password|reset-password|resend-verification|verify-email|logout, /actuator/health|info
- Admin paths: /api/v1/admin/**, /admin/** -> ROLE_ADMIN
- Authenticated: /auth/me, /auth/change-password, /auth/profile, /api/**
- JWT filter added before UsernamePasswordAuthenticationFilter

### BrokerEncryptionConfig

**File:** `broker/config/BrokerConfig.kt`
**Prefix:** `broker`

| Property | Type | Default | Description |
|---|---|---|---|
| `broker.encryption.secretKey` | String | "" | AES-256 key (Base64-encoded 32 bytes) |

### BrokerSyncConfig

**File:** `broker/config/BrokerConfig.kt`
**Prefix:** `broker.sync`

| Property | Type | Default | Description |
|---|---|---|---|
| `broker.sync.enabled` | Boolean | false | Enable data sync schedulers |
| `broker.sync.cron` | String | "0 30 22 * * *" | Nightly sync cron expression |
| `broker.sync.max-lookback-years` | Int | 25 | Max years of historical activity data to fetch on first sync |

### IngestionConfig

**File:** `ingestion/config/IngestionConfig.kt`
**Prefix:** `ingestion`

| Property | Type | Default | Description |
|---|---|---|---|
| `ingestion.enabled` | Boolean | true | Enable ingestion scheduler |
| `ingestion.schedule` | String | "0 0 22 * * *" | Nightly ingestion cron |
| `ingestion.exchanges.northAmerica` | List<String> | [US, TO, V] | Exchanges to ingest |
| `ingestion.eodhd.baseUrl` | String | "https://eodhd.com/api" | EODHD API base URL |
| `ingestion.eodhd.apiKey` | String | "" | EODHD API key |
| `ingestion.eodhd.rateLimitPerSecond` | Int | 5 | Rate limit |
| `ingestion.alphavantage.enabled` | Boolean | true | Enable AV enrichment |
| `ingestion.alphavantage.apiKey` | String | "" | AV API key |
| `ingestion.alphavantage.baseUrl` | String | "https://www.alphavantage.co/query" | AV base URL |
| `ingestion.alphavantage.rateLimit.requestsPerMinute` | Int | 75 | AV rate limit (premium tier) |
| `ingestion.alphavantage.batchSize` | Int | 50 | Stocks per batch |
| `ingestion.alphavantage.staleThresholdDays` | Int | 30 | Re-enrichment threshold |
| `ingestion.alphavantage.dailyQuota` | Int | -1 | Daily limit (-1 = unlimited) |
| `ingestion.etfcom.enabled` | Boolean | true | Enable etf.com enrichment |
| `ingestion.etfcom.baseUrl` | String | "https://api-prod.etf.com/v2/fund" | etf.com API URL |
| `ingestion.etfcom.batchSize` | Int | 25 | ETFs per batch |
| `ingestion.etfcom.staleThresholdDays` | Int | 7 | Re-enrichment threshold |
| `ingestion.etfcom.concurrency` | Int | 5 | Concurrent requests |
| `ingestion.etfcom.requestDelayMs` | Long | 200 | Inter-request delay |
| `ingestion.etfcom.interBatchDelayMs` | Long | 2000 | Inter-batch delay |

### CacheConfig

**File:** `config/CacheConfig.kt`

Redis cache manager with per-cache TTLs:
| Cache Name | TTL | Usage |
|---|---|---|
| `gics-hierarchy` | 24h | GICS hierarchy |
| `gics-sectors` | 24h | GICS sectors |
| `gicsLookup` | 24h | GICS code lookups |
| `countries` | 24h | Country data |
| `exchanges` | 24h | Exchange data |
| `regions` / `regions-simple` | 24h | Region data |
| `country-region-map` / `country-name-map` | 24h | Country mappings |
| `exchange-rates` | 6h | FX rates from BoC |
| `look-through` | 30m | Look-through computations |
| `etf-sector-allocations` | 12h | ETF sector allocations |

Default TTL: 1h. Serialization: GenericJackson2JsonRedisSerializer with Kotlin module (required for Kotlin data class deserialization). Null values not cached.

### HttpClientConfig

**File:** `ingestion/config/HttpClientConfig.kt`
**Dependencies:** `IngestionConfig`

Creates three WebClient beans:
| Bean | Base URL | Timeouts | Notes |
|---|---|---|---|
| `eodhdWebClient` | From `ingestion.eodhd.baseUrl` | 10s connect, 30s read/write | Standard timeouts |
| `alphaVantageWebClient` | From `ingestion.alphavantage.baseUrl` | 30s connect, 60s read/write | Extended timeouts, insecure TLS |
| `etfComWebClient` | From `ingestion.etfcom.baseUrl` | 10s connect, 30s read/write | Standard timeouts |

All clients: 16MB max in-memory buffer.

### GlobalExceptionHandler

**File:** `config/GlobalExceptionHandler.kt`

`@RestControllerAdvice` using RFC 7807 ProblemDetail responses. Handles the `AppException` hierarchy and auth exceptions:

| Exception | HTTP Status | Code |
|---|---|---|
| `NotFoundException` | 404 | NOT_FOUND |
| `ConflictException` | 409 | CONFLICT |
| `ValidationException` | 400 | VALIDATION_ERROR |
| `ForbiddenException` | 403 | FORBIDDEN |
| `RateLimitException` | 429 | RATE_LIMITED |
| `ExternalServiceException` | 502 | EXTERNAL_SERVICE_ERROR |
| `InternalException` | 500 | INTERNAL_ERROR |
| `InvalidCredentialsException` | 401 | from exception |
| `AccountLockedException` | 423 | from exception |
| `EmailAlreadyExistsException` | 409 | from exception |
| `EmailNotVerifiedException` | 403 | from exception |
| `InvalidTokenException` | 401 | from exception |
| `InvalidPasswordException` | 400 | from exception |
| `UserNotFoundException` | 404 | from exception |
| `AccessDeniedException` | 403 | from exception |
| `IllegalArgumentException` | 400 | -- |
| `IllegalStateException` | 409 | -- |
| `Exception` (catch-all) | 500 | INTERNAL_ERROR |

Auth exceptions extend the `AppException` hierarchy (e.g., `InvalidCredentialsException` extends `ForbiddenException`).

### HealthCheckMdcFilter

**File:** `config/HealthCheckMdcFilter.kt`

`OncePerRequestFilter` at `HIGHEST_PRECEDENCE`. Sets `MDC.put("skipLog", "true")` for `/health` requests to suppress access logging noise.

### IngestionRedisConfig

**File:** `ingestion/config/IngestionRedisConfig.kt`

Creates `StringRedisTemplate` bean for ingestion hash cache.

---

## Health Indicators

### IngestionServiceHealthIndicator

**File:** `health/IngestionServiceHealthIndicator.kt`

Custom Spring Boot `HealthIndicator` that checks cross-service connectivity to the ingestion microservice on port 8081. Reports UP/DOWN.

---

## Error Handling

### AppException Hierarchy (Backend)

**File:** `exception/AppException.kt`

Abstract base class for all domain exceptions. Each subclass maps to an HTTP status code in `GlobalExceptionHandler`.

| Exception | HTTP Status | Default Code |
|---|---|---|
| `NotFoundException` | 404 | `NOT_FOUND` |
| `ConflictException` | 409 | `CONFLICT` |
| `ValidationException` | 400 | `VALIDATION_ERROR` |
| `ForbiddenException` | 403 | `FORBIDDEN` |
| `RateLimitException` | 429 | `RATE_LIMITED` |
| `ExternalServiceException` | 502 | `EXTERNAL_SERVICE_ERROR` |
| `InternalException` | 500 | `INTERNAL_ERROR` |

All responses use RFC 7807 ProblemDetail format (`spring.mvc.problemdetails.enabled=true`).

---

## Ingestion Microservice (port 8081)

Separate Spring Boot application at `backend/ingestion/`. Manages its own database schema (`ingestion`) and Flyway migrations.

### Package Structure

```
com.portfolio.ingestion
├── IngestionServiceApplication.kt
├── config/
│   ├── IngestionProperties.kt        # @ConfigurationProperties for ingestion settings
│   ├── HttpClientConfig.kt           # WebClient beans for EODHD
│   └── RedisConfig.kt                # Redis template for hash cache
├── controller/
│   └── AdminIngestionController.kt   # REST API at /admin/ingestion/*
├── exception/
│   ├── AppException.kt               # Exception hierarchy (same pattern as backend)
│   └── GlobalExceptionHandler.kt     # RFC 7807 ProblemDetail handler
├── health/
│   ├── EodhdHealthIndicator.kt       # EODHD API health check
│   └── QuotaHealthIndicator.kt       # Daily quota monitoring
├── persistence/
│   ├── entity/
│   │   ├── Enums.kt                  # InstrumentType, RunType, RunStatus, StepName, StepStatus, ErrorType
│   │   ├── Exchange.kt
│   │   ├── Instrument.kt
│   │   ├── InstrumentExchange.kt
│   │   ├── ProviderRawData.kt
│   │   ├── ProviderConfig.kt
│   │   ├── IngestionRun.kt
│   │   ├── IngestionStep.kt
│   │   └── IngestionError.kt
│   └── repository/
│       ├── ExchangeRepository.kt
│       ├── InstrumentRepository.kt
│       ├── InstrumentExchangeRepository.kt
│       ├── ProviderRawDataRepository.kt
│       ├── ProviderConfigRepository.kt
│       ├── IngestionRunRepository.kt
│       ├── IngestionStepRepository.kt
│       └── IngestionErrorRepository.kt
├── pipeline/
│   ├── IngestionOrchestrator.kt      # Coordinates pipeline steps
│   ├── ExchangeSyncStep.kt           # Syncs exchange metadata
│   ├── UniverseSyncStep.kt           # Discovers instruments per exchange
│   ├── RawDataFetchStep.kt           # Fetches fundamentals data
│   └── FundamentalsBatchProcessor.kt # Batch processing with rate limiting
├── provider/
│   ├── DataProvider.kt               # Strategy interface
│   ├── ProviderCapability.kt         # Capability enum
│   ├── ProviderRegistry.kt           # Provider discovery
│   └── eodhd/
│       ├── EodhdClient.kt            # HTTP client for EODHD API
│       ├── EodhdDtos.kt              # EODHD response DTOs
│       ├── EodhdProvider.kt          # DataProvider implementation
│       └── EodhdRateLimiter.kt       # Token bucket rate limiter
├── scheduler/
│   └── IngestionScheduler.kt         # Nightly scheduled ingestion
└── tracking/
    ├── IngestionTrackingService.kt    # Run/step/error tracking
    └── HashCacheService.kt           # Redis hash-based change detection
```

### IngestionOrchestrator

**File:** `pipeline/IngestionOrchestrator.kt`
**Dependencies:** `ExchangeSyncStep`, `UniverseSyncStep`, `RawDataFetchStep`, `IngestionTrackingService`

Coordinates the ingestion pipeline. Tracks active run ID to prevent concurrent executions.

| Method | Signature | Description |
|---|---|---|
| `runExchangeSync` | `suspend (triggerSource: String)` | Runs exchange sync only |
| `runFullIngestion` | `suspend (triggerSource: String)` | Runs full pipeline: Universe Sync -> Raw Data Fetch |
| `isRunning` | `(): Boolean` | Returns true if a run is active |
| `getActiveRunId` | `(): Long?` | Returns active run ID or null |

### DataProvider Interface

**File:** `provider/DataProvider.kt`

Strategy pattern interface for data sources.

| Method | Signature | Description |
|---|---|---|
| `name` | `(): String` | Provider identifier |
| `capabilities` | `(): Set<ProviderCapability>` | Supported operations |
| `fetchExchanges` | `suspend (): List<RawExchange>` | Fetch exchange metadata |
| `fetchUniverse` | `suspend (exchange: String): List<RawInstrument>` | Fetch instruments for exchange |
| `fetchFundamentals` | `suspend (ticker: String, exchange: String): JsonNode?` | Fetch fundamentals data |

### EodhdRateLimiter

**File:** `provider/eodhd/EodhdRateLimiter.kt`

Token bucket rate limiter for EODHD API. Tracks daily quota (100k calls/day). Each fundamentals request costs 10 API calls.

### IngestionScheduler

**File:** `scheduler/IngestionScheduler.kt`
**Condition:** `ingestion.enabled=true`

| Schedule | Cron | Method | Description |
|---|---|---|---|
| Nightly ingestion | `${ingestion.schedule:0 0 22 * * *}` | `runNightlyIngestion()` | Runs full ingestion pipeline |

### Ingestion Service Enums

**File:** `persistence/entity/Enums.kt`

| Enum | Values |
|---|---|
| `InstrumentType` | STOCK, PREFERRED_STOCK, ETF, MUTUAL_FUND, INDEX, BOND |
| `InstrumentStatus` | ACTIVE, DELISTED, SUSPENDED, PENDING |
| `RunType` | SCHEDULED, MANUAL |
| `RunStatus` | RUNNING, COMPLETED, FAILED, PARTIAL |
| `StepName` | EXCHANGE_SYNC, UNIVERSE_SYNC, RAW_DATA_FETCH |
| `StepStatus` | RUNNING, COMPLETED, FAILED, SKIPPED |
| `ErrorType` | API_ERROR, PARSE_ERROR, DB_ERROR, RATE_LIMIT, VALIDATION_ERROR, DUPLICATE_ISIN, NOT_FOUND |

---

## Market Data Service (`backend/market-data/`)

**Port:** 8082 | **Schema:** `market_data` | **Package:** `com.portfolio.marketdata`

### IBKR Integration (`ibkr/`)

| Class | Type | Description |
|---|---|---|
| `IbkrClient` | Interface | Abstraction over IBKR TWS API: connect, disconnect, requestMarketData, cancelMarketData, requestOptionChain, requestContractDetails |
| `TwsIbkrClient` | `@Component` | Real TWS API client using EClientSocket + EReader. Connects to IB Gateway, handles ticks, contract resolution, snapshots, Greeks. Requests delayed data type as fallback. |
| `IbkrConnectionManager` | `@Component ApplicationRunner` | Auto-connects on startup, exponential backoff reconnection (5s-60s), health status tracking, periodic health check every 30s, broadcasts connection status to WebSocket clients via `QuoteWebSocketHandler` (injected with `@Lazy`) |
| `ContractResolver` | `@Component` | Multi-level contract ID resolution: Redis (24h TTL) → PostgreSQL → IBKR API. In-memory fallback. |
| `SubscriptionManager` | `@Component` | LRU eviction for subscription capacity (default 100). Contract pinning for open positions. |

### Processing (`processing/`)

| Class | Type | Description |
|---|---|---|
| `QuoteNormalizer` | `@Component` | Accumulates bid/ask/last/volume ticks, emits complete Quote objects |
| `OptionQuoteNormalizer` | `@Component` | Same pattern for option contracts |
| `GreeksCalculator` | `@Service` | Computes Greeks via Black-Scholes. Prefers IBKR Greeks when available. Configurable risk-free rate. |
| `OptionsChainBuilder` | `@Component` | Groups OptionQuotes by expiry and strike, pairs calls and puts |

### Distribution (`distribution/`)

| Class | Type | Description |
|---|---|---|
| `QuoteCacheService` | `@Service` | Redis caching: 5s TTL for quotes, 30s for chains. Jackson JSON serialization. |
| `QuoteWebSocketHandler` | `@Component TextWebSocketHandler` | WebSocket at /ws/quotes. Subscribe/unsubscribe stocks and options. Broadcast to subscribed sessions. `broadcastConnectionStatus()` sends IBKR connection state to all connected WebSocket clients. |

### Health (`api/controller/`)

| Class | Type | Description |
|---|---|---|
| `IbkrHealthController` | `@RestController` | `GET /api/v1/health/ibkr` -- Returns IBKR connection status (connected boolean, uptime, client ID) from `IbkrConnectionManager` |

### Streaming (`streaming/`)

| Class | Type | Description |
|---|---|---|
| `QuoteStreamingService` | `@Service` | Ref-counted stock quote streaming. Resolves contracts, subscribes to IBKR, normalizes ticks, caches and broadcasts. |
| `OptionStreamingService` | `@Service` | Ref-counted option streaming with Greeks enrichment from spot price cache. |

---

## Strategy Service (`backend/strategy/`)

**Port:** 8083 | **Schema:** `strategy` | **Package:** `com.portfolio.strategy`

### Strategy Engine (`engine/`)

| Class | Type | Description |
|---|---|---|
| `StrategyRegistry` | `@Component` | Registry of 7 strategy definitions with education content. Strategies: BULL_CALL_SPREAD, BEAR_PUT_SPREAD, BULL_PUT_SPREAD, BEAR_CALL_SPREAD, IRON_CONDOR, COVERED_CALL, PROTECTIVE_PUT |
| `StrategyCalculator` | `@Component` | P&L calculation engine: net debit/credit, P&L curve (100 points ±20%), break-even interpolation, risk/reward ratio, net Greeks |
| `LegValidator` | `@Component` | Validates leg combinations: no duplicates, same expiry for all option legs |
| `EducationEngine` | `@Component` | Static education content per strategy + dynamic warnings (short DTE, wide spreads, deep ITM, delta-neutral, long DTE) |

### Models (`model/`)

| Class | Description |
|---|---|
| `StrategyType` | Enum: 7 strategy types |
| `StrategyDefinition` | Display name, description, outlook, risk profile, leg templates |
| `Leg` | Action (BUY/SELL), option type, strike, expiry, quantity, bid/ask/mid/delta |
| `CalculationResult` | Net debit/credit, max profit/loss, break-evens, P&L curve, net Greeks |
| `EducationContent` | When to use, risk explanation, key characteristics, warnings |

---

## Broker Gateway Service (`backend/broker-gateway/`)

**Port:** 8084 | **Schema:** `broker_gateway` | **Package:** `com.portfolio.brokergateway`

### CredentialService

**File:** `service/CredentialService.kt`

Manages encrypted broker credentials for gateway connections. Provides automatic token refresh before API calls.

| Method | Signature | Description |
|---|---|---|
| `getCredentialsWithRefresh` | `(connection: GatewayConnection): BrokerCredentials` | Returns decrypted credentials, automatically refreshing expired OAuth tokens before returning. Used by `DataController` and `OrderController` to ensure valid tokens for every broker API call. |

### IBKR Adapter (`adapter/ibkr/`)

| Class | Type | Description |
|---|---|---|
| `IbkrConfig` | `@ConfigurationProperties(prefix = "broker-gateway.ibkr")` | IBKR settings: enabled, host, port, clientId (default 2), connectTimeoutMs, requestTimeoutMs, reconnectDelayMs (5s), maxReconnectDelayMs (60s), flexToken, flexQueryId |
| `IbkrDtoMappers` | `object` | Static mappers normalizing IBKR-specific values to unified enums: mapAccountType (Individual/Cash/Margin/TFSA/RRSP/FHSA/RESP/LIRA/LIF/RIF), mapInstrumentType (STK/OPT/BOND/FUND/CASH/CRYPTO), mapOrderStatus (PendingSubmit/Submitted/Filled/Cancelled/Inactive/Error), mapActivityType (BUY/BOT/SELL/SLD/DIV/DEP/WITH/COMM/INT/EXP/ASSIGN/EXER/SPLIT/CA), mapOptionRight (C/P) |
| `IbkrAdapter` | `@Component @ConditionalOnProperty("broker-gateway.ibkr.enabled")` | Production adapter implementing BrokerAdapter. Delegates to IbkrAccountClient for TWS operations. Uses IbkrDtoMappers for all type normalization. Creates IbkrConnectionManager on construction. Capabilities: orders, options, real-time data, historical activities via Flex Queries, no fractional shares. |
| `IbkrConnectionManager` | Class (non-Spring) | Socket lifecycle manager with exponential backoff reconnection. Initial delay 5s, doubles each attempt, capped at 60s. Resets delay on successful connection. Daemon thread executor. Health status via AtomicBoolean. Graceful shutdown with 5s termination wait. |
| `IbkrAccountClient` | Interface | TWS client abstraction: connect, disconnect, isConnected, getManagedAccounts, getAccountSummary, getPositions, getOpenOrders, getCompletedOrders, getExecutions, placeOrder(accountId, contract, orderSpec), cancelOrder(orderId) |
| `TwsIbkrAccountClient` | `@Component @ConditionalOnProperty("broker-gateway.ibkr.enabled")` | Concrete implementation of `IbkrAccountClient` using TWS API (`com.ib.client`). Connects to IB Gateway/TWS via `EClientSocket`, uses `CountDownLatch`-based request/response pattern for blocking calls. Supports managed accounts, positions, orders, executions, and order placement. |
| `IbkrPosition` | Data class | accountId, symbol, secType, exchange, currency, conId, quantity, averageCost, marketPrice?, marketValue?, unrealizedPnl?, strike?, expiry?, right? |
| `IbkrOrder` | Data class | orderId, symbol, secType, action, orderType, totalQuantity, filledQuantity?, limitPrice?, auxPrice?, status, timeInForce?, avgFillPrice?, currency?, submittedAt?, filledAt? |
| `IbkrExecution` | Data class | execId, symbol, secType, side, quantity, price, commission?, currency, time, accountId |
| `IbkrContract` | Data class | symbol, secType (default "STK"), exchange (default "SMART"), currency (default "USD") |
| `IbkrOrderSpec` | Data class | action, orderType, totalQuantity, limitPrice?, auxPrice?, timeInForce (default "DAY") |

### Health (`api/controller/` -- broker-gateway)

| Class | Type | Description |
|---|---|---|
| `HealthController` | `@RestController` | `GET /api/v1/gateway/health` -- Overall gateway health. `GET /api/v1/gateway/health/{brokerType}` -- Per-broker health. `GET /api/v1/gateway/health/ibkr` -- IBKR-specific health via `IbkrAdapter.getHealthStatus()`, returns `IbkrHealthResponse` (connected, accounts, uptime) |

### Questrade Adapter (`adapter/questrade/`)

| Class | Type | Description |
|---|---|---|
| `QuestradeConfig` | `@ConfigurationProperties(prefix = "broker-gateway.questrade")` | Questrade settings: enabled (default false), authUrl (default `https://login.questrade.com/oauth2/token`), practiceAuthUrl, usePractice (default false), rateLimitPerSecond (default 1) |
| `QuestradeDtoMappers` | `object` | Static mappers normalizing Questrade-specific values to unified enums: mapAccountType (TFSA/RRSP/FHSA/RESP/LIRA/LIF/RIF variants), mapInstrumentType, mapOrderStatus, mapOrderType, mapTimeInForce, mapOrderAction, mapActivityType |
| `QuestradeAdapter` | `@Component @ConditionalOnProperty("broker-gateway.questrade.enabled")` | Production adapter implementing BrokerAdapter. Delegates to QuestradeRestClient for HTTP operations and QuestradeTokenManager for authentication. Uses QuestradeDtoMappers for all type normalization. Implements `getOrderImpact()` for order preview via Questrade `/v1/accounts/:id/orders/impact` endpoint. |
| `QuestradeRestClient` | Class | WebClient-based HTTP client with get/post/delete methods. Error handling maps 401 responses to authentication errors and 429 responses to rate-limit errors. |
| `QuestradeTokenManager` | Class | OAuth token rotation manager handling Questrade's single-use refresh token model. Each token refresh returns a new refresh token and a dynamic `api_server` URL that subsequent API calls must use. |

### Wealthsimple Adapter (`adapter/wealthsimple/`)

| Class | Type | Description |
|---|---|---|
| `WealthsimpleConfig` | `@ConfigurationProperties(prefix = "broker-gateway.wealthsimple")` | Wealthsimple settings: enabled (default false), authUrl, graphqlUrl, clientId, orderRateLimitPerHour (default 7) |
| `WealthsimpleDtoMappers` | `object` | Static mappers normalizing Wealthsimple-specific values to unified enums: mapAccountType (ca_tfsa/ca_rrsp/ca_fhsa/ca_lira/ca_crypto), mapInstrumentType (equity/etf), mapOrderStatus, mapActivityType |
| `WealthsimpleAdapter` | `@Component @ConditionalOnProperty("broker-gateway.wealthsimple.enabled")` | Production adapter implementing BrokerAdapter. Uses WealthsimpleGraphQlClient for API operations (FetchAllAccounts, FetchAccountFinancials, FetchIdentityPositions, FetchActivityFeedItems, SoOrdersOrderCreate, SoOrdersOrderCancel), WealthsimpleTokenManager for authentication, and WealthsimpleRateLimiter for order throttling. Uses WealthsimpleDtoMappers for all type normalization. |
| `WealthsimpleGraphQlClient` | Class | Custom HTTP client for Wealthsimple's GraphQL API. Sends raw HTTP POST requests with WS-specific headers (x-ws-api-version, x-platform-os, x-ws-locale, x-ws-profile). Detects 2FA challenges via x-wealthsimple-otp-required response header. |
| `WealthsimpleTokenManager` | Class | Password-grant OAuth token manager. Handles token refresh and expiry detection for Wealthsimple's OAuth flow. |
| `WealthsimpleRateLimiter` | Class | Sliding-window rate limiter enforcing 7 trades per hour using ConcurrentLinkedDeque. Prevents exceeding Wealthsimple's order submission limits. |

---

## Common Module (`backend/common/`)

**Package:** `com.portfolio.common`

Shared Kotlin library (no Spring Boot). Used by market-data and strategy services via Gradle composite builds.

| Class | Package | Description |
|---|---|---|
| `BlackScholes` | `math` | Option pricing, all 5 Greeks (delta, gamma, theta, vega, rho), implied volatility (Newton-Raphson) |
| `TradingCalendar` | `util` | DTE calculation, market hours (9:30-16:00 ET), next/previous trading day, time-to-expiry for Black-Scholes |
| `Money` | `util` | BigDecimal extensions: toCents, roundTo, safeDivide, percentageChange, bpsToDecimal |
| `OptionType` | `domain` | Enum: CALL, PUT |
| `Greeks` | `domain` | Delta, gamma, theta, vega, rho + source (IBKR or BLACK_SCHOLES) |
| `Quote` | `domain` | Symbol, bid, ask, last, volume, timestamp + computed mid/spread |
| `OptionQuote` | `domain` | Underlying, option type, strike, expiry, bid/ask/last, volume, open interest, Greeks |
| `OptionsChain` | `domain` | Underlying, spot price, expirations map (expiry → strike → StrikeData) |
