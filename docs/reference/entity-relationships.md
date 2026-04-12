# Entity Relationships & DTO Reference -- AI Agent Reference

> Backend: Kotlin 2.0.21 + Spring Boot 3.3.5 + JPA/Hibernate
> All entities use `GenerationType.IDENTITY` for IDs
> Hibernate DDL mode: `validate` (schema managed by Flyway migrations)
> Cross-reference: [database-schema.md](./database-schema.md) | [backend-services.md](./backend-services.md)

---

## Table of Contents

1. [Entity Relationship Diagram](#entity-relationship-diagram)
2. [Auth Entities](#auth-entities)
3. [Broker Entities](#broker-entities)
4. [Core Entities](#core-entities)
5. [GICS Entities](#gics-entities)
6. [Auth DTOs](#auth-dtos)
7. [Broker DTOs](#broker-dtos)
8. [Core DTOs](#core-dtos)
9. [SnapTrade Adapter DTOs](#snaptrade-adapter-dtos)

---

## Entity Relationship Diagram

```
                         +-----------+
                         |   Region  |
                         +-----------+
                              |1
                              |
                              |*
                         +-----------+
                         |  Country  |
                         +-----------+

    +-----------+           +-----------+           +-----------+
    |   Role    |*---------*|   User    |1---------*| UserRole  |
    +-----------+           +-----------+           +-----------+
                              |1   |1   |1
                              |    |    |
                    +---------+    |    +----------+
                    |              |               |
                    |*             |*              |*
              +----------+  +-----------+   +---------------+
              |UserIdent.|  |RefreshTkn |   | AuditLog      |
              +----------+  +-----------+   +---------------+
                    |1
                    |
    +---------------+----------------+------------------+
    |               |                |                   |
    |*              |*               |*                  |*
+--------+    +-----------+   +----------+        +----------+
|EmailVer|    |PwdReset   |   |OAuthState|        |Notif.Pref|
+--------+    +-----------+   +----------+        +----------+

User -->1:* BrokerConnection
User -->1:* ModelPortfolio
User -->1:* PortfolioGroup
User -->1:* TradeOrder
User -->1:* Notification
User -->1:* DashboardPreference

                         +------------------+
                         |      Broker      |
                         +------------------+
                               |1
                               |
                               |*
                    +--------------------+
                    | BrokerConnection   |1---------*+------------------+
                    +--------------------+           | BrokerPosition   |
                      |1   |1   |1   |1             +------------------+
                      |    |    |    |                       |*
                      |    |    |    |               +------------------+
                      |*   |*   |*   |*             | PositionFetchLog |
                +------+ +---+ +---+ +--------+    +------------------+
                |BrkAct| |Bal| |Trd|  |         |
                +------+ |Snp| |Ord|  |ModelPort|
                         +---+ +---+  +---------+
                                          |1
                                          |
                                          |*
                                  +------------------+
                                  |ModelPortAllocatn |
                                  +------------------+

PortfolioGroup (user) -->
  |-- 1:* PortfolioTarget
  |-- 1:* PortfolioGroupAccount (links to BrokerConnection)
  |-- 1:1 PortfolioGroupSettings
  |-- 1:* PortfolioExcludedAsset
  |-- 1:* PortfolioSnapshot
  |-- 1:* PortfolioCashFlow
  |-- 1:* RebalanceEvent
  |-- *:1 ModelPortfolio (nullable)
  |-- *:1 ModelPortfolio (benchmarkModel, nullable)

TradeOrder --> User, PortfolioGroup (nullable), BrokerConnection

                    +--------+
                    |  Stock  |
                    +--------+
                       |1
                       |
                +------+-------+
                |*             |*
          +-----------+  +-----------+
          | EtfHolding|  |BrokerPos  |
          +-----------+  +-----------+
                |*
                |1
          +-----------+
          |    Etf    |
          +-----------+
                |1
                |
                |*
     +-----------------------+
     |EtfSectorAllocFactset |
     +-----------------------+

DataSource -->1:* IngestionBatch -->1:* EtfHolding
BenchmarkReturn (standalone, no FK relationships)
SnapTradeStatusCheck (standalone, no FK relationships)
```

---

## Auth Entities

### User

**File:** `auth/entity/User.kt`
**Table:** `users`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| email | String | email | unique, not null, length=255 |
| emailVerified | Boolean | email_verified | not null, default false |
| emailVerifiedAt | OffsetDateTime? | email_verified_at | nullable |
| passwordHash | String? | password_hash | nullable, length=255 |
| name | String? | name | nullable, length=255 |
| avatarUrl | String? | avatar_url | nullable, length=500 |
| status | UserStatus | status | @Enumerated(STRING), not null, length=20 |
| failedLoginAttempts | Int | failed_login_attempts | not null, default 0 |
| lockedUntil | OffsetDateTime? | locked_until | nullable |
| lastLoginAt | OffsetDateTime? | last_login_at | nullable |
| lastLoginIp | String? | last_login_ip | nullable, length=45 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |
| snaptradeUserId | String? | snaptrade_user_id | nullable, length=255 |
| snaptradeUserSecretEncrypted | String? | snaptrade_user_secret_encrypted | TEXT |

**Relationships:**
- `identities` -> `UserIdentity` (OneToMany, mappedBy="user", CASCADE ALL, orphanRemoval, LAZY)
- `userRoles` -> `UserRole` (OneToMany, mappedBy="user", CASCADE ALL, orphanRemoval, LAZY)

**Enum -- UserStatus:** `ACTIVE`, `INACTIVE`, `SUSPENDED`, `DELETED`

**Helper methods:** `isLocked()`, `hasPassword()`, `getRoleNames()`

---

### Role

**File:** `auth/entity/Role.kt`
**Table:** `roles`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| name | String | name | unique, not null, length=50 |
| description | String? | description | nullable, length=255 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Constants:** `Role.USER = "USER"`, `Role.ADMIN = "ADMIN"`

---

### UserRole

**File:** `auth/entity/UserRole.kt`
**Table:** `user_roles`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| role | Role | role_id | @ManyToOne(EAGER), not null |
| grantedBy | User? | granted_by | @ManyToOne(LAZY), nullable |
| grantedAt | OffsetDateTime | granted_at | not null, updatable=false |

---

### UserIdentity

**File:** `auth/entity/UserIdentity.kt`
**Table:** `user_identities`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| provider | String | provider | not null, length=50 |
| providerUserId | String | provider_user_id | not null, length=255 |
| providerEmail | String? | provider_email | nullable, length=255 |
| providerName | String? | provider_name | nullable, length=255 |
| providerAvatarUrl | String? | provider_avatar_url | nullable, length=500 |
| accessTokenEncrypted | String? | access_token_encrypted | nullable, length=1000 |
| refreshTokenEncrypted | String? | refresh_token_encrypted | nullable, length=1000 |
| tokenExpiresAt | OffsetDateTime? | token_expires_at | nullable |
| rawProfile | String? | raw_profile | jsonb, @JdbcTypeCode(JSON) |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Constants:** `PROVIDER_GOOGLE = "google"`, `PROVIDER_GITHUB = "github"`

---

### RefreshToken

**File:** `auth/entity/RefreshToken.kt`
**Table:** `refresh_tokens`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| tokenHash | String | token_hash | unique, not null, length=64 |
| deviceInfo | String? | device_info | nullable, length=255 |
| ipAddress | String? | ip_address | nullable, length=45 |
| expiresAt | OffsetDateTime | expires_at | not null |
| revokedAt | OffsetDateTime? | revoked_at | nullable |
| revokedReason | String? | revoked_reason | nullable, length=100 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Methods:** `isValid()`, `revoke(reason)`

---

### EmailVerificationToken

**File:** `auth/entity/EmailVerificationToken.kt`
**Table:** `email_verification_tokens`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| tokenHash | String | token_hash | unique, not null, length=64 |
| newEmail | String? | new_email | nullable, length=255 |
| expiresAt | OffsetDateTime | expires_at | not null |
| usedAt | OffsetDateTime? | used_at | nullable |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Methods:** `isValid()`, `markUsed()`

---

### PasswordResetToken

**File:** `auth/entity/PasswordResetToken.kt`
**Table:** `password_reset_tokens`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| tokenHash | String | token_hash | unique, not null, length=64 |
| expiresAt | OffsetDateTime | expires_at | not null |
| usedAt | OffsetDateTime? | used_at | nullable |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Methods:** `isValid()`, `markUsed()`

---

### OAuthState

**File:** `auth/entity/OAuthState.kt`
**Table:** `oauth_states`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| stateHash | String | state_hash | unique, not null, length=64 |
| provider | String | provider | not null, length=50 |
| redirectUri | String? | redirect_uri | nullable, length=500 |
| codeVerifier | String? | code_verifier | nullable, length=128 |
| expiresAt | OffsetDateTime | expires_at | not null |
| usedAt | OffsetDateTime? | used_at | nullable |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Methods:** `isValid()`, `markUsed()`

---

### AuditLog

**File:** `auth/entity/AuditLog.kt`
**Table:** `audit_log`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User? | user_id | @ManyToOne(LAZY), nullable |
| eventType | AuditEventType | event_type | @Enumerated(STRING), not null, length=50 |
| eventSubtype | String? | event_subtype | nullable, length=50 |
| ipAddress | String? | ip_address | nullable, length=45 |
| userAgent | String? | user_agent | nullable, length=500 |
| resourceType | String? | resource_type | nullable, length=50 |
| resourceId | String? | resource_id | nullable, length=100 |
| details | String? | details | jsonb, @JdbcTypeCode(JSON) |
| success | Boolean | success | not null, default true |
| errorMessage | String? | error_message | nullable, length=500 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Enum -- AuditEventType:** `AUTH_LOGIN`, `AUTH_LOGOUT`, `AUTH_SIGNUP`, `AUTH_FAILED_LOGIN`, `PASSWORD_RESET_REQUEST`, `PASSWORD_RESET_COMPLETE`, `PASSWORD_CHANGE`, `EMAIL_VERIFICATION`, `EMAIL_CHANGE`, `PROFILE_UPDATE`, `OAUTH_LINK`, `OAUTH_UNLINK`, `ROLE_GRANT`, `ROLE_REVOKE`, `USER_LOCK`, `USER_UNLOCK`, `USER_SUSPEND`, `BROKER_CONNECT`, `BROKER_DISCONNECT`, `BROKER_FETCH_POSITIONS`, `BROKER_FETCH_ERROR`

---

## Broker Entities

### Broker

**File:** `broker/entity/Broker.kt`
**Table:** `brokers`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| code | String | code | unique, not null, length=20 |
| name | String | name | not null, length=100 |
| authType | BrokerAuthType | auth_type | @Enumerated(STRING), not null, length=20 |
| status | BrokerStatus | status | @Enumerated(STRING), not null, length=20 |
| logoUrl | String? | logo_url | nullable, length=500 |
| description | String? | description | nullable, length=500 |
| oauthConfig | String? | oauth_config | jsonb, @JdbcTypeCode(JSON) |
| rateLimitConfig | String? | rate_limit_config | jsonb, @JdbcTypeCode(JSON) |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Enum -- BrokerAuthType:** `OAUTH2`, `API_KEY`, `AGGREGATOR`
**Enum -- BrokerStatus:** `ACTIVE`, `INACTIVE`, `MAINTENANCE`

---

### BrokerConnection

**File:** `broker/entity/BrokerConnection.kt`
**Table:** `broker_connections`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| broker | Broker? | broker_id | @ManyToOne(LAZY), nullable |
| snaptradeAuthorizationId | String? | snaptrade_authorization_id | nullable, length=255 |
| accountIdExternal | String? | account_id_external | nullable, length=100 |
| accountNumber | String? | account_number | nullable, length=50 |
| accountType | String? | account_type | nullable, length=50 |
| accountName | String? | account_name | nullable, length=100 |
| accountNumberActual | String? | account_number_actual | nullable, length=50 |
| accountMetaType | String? | account_meta_type | nullable, length=50 |
| brokerName | String? | broker_name | nullable, length=200 |
| brokerLogoUrl | String? | broker_logo_url | nullable, length=500 |
| status | ConnectionStatus | status | @Enumerated(STRING), not null, length=20 |
| lastPositionsFetchedAt | OffsetDateTime? | last_positions_fetched_at | nullable |
| positionsCount | Int | positions_count | default 0 |
| totalValue | BigDecimal? | total_value | precision=18, scale=2 |
| connectionErrorCode | String? | connection_error_code | nullable, length=50 |
| connectionErrorMessage | String? | connection_error_message | nullable, length=500 |
| metadata | String? | metadata | jsonb, @JdbcTypeCode(JSON) |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| lastActivitiesFetchedAt | OffsetDateTime? | last_activities_fetched_at | nullable |
| lastBalanceFetchedAt | OffsetDateTime? | last_balance_fetched_at | nullable |
| updatedAt | OffsetDateTime | updated_at | not null |
| connectionType | String? | connection_type | nullable, length=20 |
| modelPortfolio | ModelPortfolio? | model_portfolio_id | @ManyToOne(LAZY), nullable |
| modelAccuracy | BigDecimal? | model_accuracy | nullable |
| lastRebalancedAt | OffsetDateTime? | last_rebalanced_at | nullable |

**Relationships:**
- `positions` -> `BrokerPosition` (OneToMany, mappedBy="connection", CASCADE ALL, orphanRemoval, LAZY)
- `fetchLogs` -> `PositionFetchLog` (OneToMany, mappedBy="connection", CASCADE ALL, orphanRemoval, LAZY)

**Enum -- ConnectionStatus:** `PENDING`, `ACTIVE`, `EXPIRED`, `ERROR`, `DISCONNECTED`

**Methods:** `isActive()`, `needsReauth()`, `markAsExpired(msg?)`, `markAsError(code, msg)`, `clearError()`

---

### BrokerPosition

**File:** `broker/entity/BrokerPosition.kt`
**Table:** `broker_positions`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| connection | BrokerConnection | connection_id | @ManyToOne(LAZY), not null |
| symbol | String | symbol | not null, length=20 |
| symbolIdExternal | String? | symbol_id_external | nullable, length=50 |
| instrumentType | InstrumentType? | instrument_type | @Enumerated(STRING), nullable, length=20 |
| securityName | String? | security_name | nullable, length=255 |
| quantity | BigDecimal | quantity | not null, precision=18, scale=6 |
| averageCost | BigDecimal? | average_cost | precision=18, scale=6 |
| currentPrice | BigDecimal? | current_price | precision=18, scale=6 |
| currentValue | BigDecimal? | current_value | precision=18, scale=2 |
| dayPnl | BigDecimal? | day_pnl | precision=18, scale=2 |
| totalPnl | BigDecimal? | total_pnl | precision=18, scale=2 |
| totalPnlPercent | BigDecimal? | total_pnl_percent | precision=10, scale=4 |
| currency | String | currency | length=3, default "CAD" |
| asOfDate | LocalDate | as_of_date | not null |
| asOfTimestamp | OffsetDateTime? | as_of_timestamp | nullable |
| strikePrice | BigDecimal? | strike_price | precision=18, scale=6 |
| expirationDate | LocalDate? | expiration_date | nullable |
| optionType | String? | option_type | nullable, length=10 |
| underlyingSymbol | String? | underlying_symbol | nullable, length=20 |
| isCurrent | Boolean | is_current | not null, default true |
| rawPayload | String? | raw_payload | jsonb, @JdbcTypeCode(JSON) |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Enum -- InstrumentType (broker):** `STOCK`, `ETF`, `MUTUAL_FUND`, `OPTION`, `BOND`, `CASH`, `OTHER`

---

### BrokerActivity

**File:** `broker/entity/BrokerActivity.kt`
**Table:** `broker_activities`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| connection | BrokerConnection | connection_id | @ManyToOne(LAZY), not null |
| externalId | String? | external_id | nullable, length=100 |
| type | String | type | not null, length=50 |
| symbol | String? | symbol | nullable, length=20 |
| description | String? | description | TEXT |
| quantity | BigDecimal? | quantity | precision=18, scale=6 |
| price | BigDecimal? | price | precision=18, scale=6 |
| amount | BigDecimal | amount | not null, precision=18, scale=2 |
| fee | BigDecimal? | fee | precision=18, scale=4 |
| currency | String | currency | length=3, default "CAD" |
| tradeDate | LocalDate | trade_date | not null |
| settlementDate | LocalDate? | settlement_date | nullable |
| accountName | String? | account_name | nullable, length=100 |
| optionType | String? | option_type | nullable, length=20 |
| amountCad | BigDecimal? | amount_cad | precision=18, scale=2 |
| exchangeRate | BigDecimal? | exchange_rate | precision=18, scale=6 |
| rawPayload | String? | raw_payload | jsonb, @JdbcTypeCode(JSON) |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

---

### BrokerBalanceSnapshot

**File:** `broker/entity/BrokerBalanceSnapshot.kt`
**Table:** `broker_balance_snapshots`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| connection | BrokerConnection | connection_id | @ManyToOne(LAZY), not null |
| totalValue | BigDecimal? | total_value | precision=18, scale=2 |
| cash | String? | cash | jsonb, @JdbcTypeCode(JSON) |
| currency | String | currency | length=3, default "CAD" |
| asOfDate | LocalDate | as_of_date | not null |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

---

### TradeOrder

**File:** `broker/entity/TradeOrder.kt`
**Table:** `trade_orders`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| group | PortfolioGroup? | group_id | @ManyToOne(LAZY), nullable |
| connection | BrokerConnection | connection_id | @ManyToOne(LAZY), not null |
| batchId | UUID? | batch_id | nullable |
| symbol | String | symbol | not null, length=20 |
| action | OrderAction | action | @Enumerated(STRING), not null, length=4 |
| orderType | OrderType | order_type | @Enumerated(STRING), not null, length=10 |
| timeInForce | TimeInForce | time_in_force | @Enumerated(STRING), not null, length=3 |
| requestedUnits | BigDecimal | requested_units | not null, precision=18, scale=6 |
| requestedPrice | BigDecimal | requested_price | not null, precision=18, scale=6 |
| requestedAmount | BigDecimal | requested_amount | not null, precision=18, scale=2 |
| limitPrice | BigDecimal? | limit_price | precision=18, scale=6 |
| filledUnits | BigDecimal? | filled_units | precision=18, scale=6 |
| filledPrice | BigDecimal? | filled_price | precision=18, scale=6 |
| filledAmount | BigDecimal? | filled_amount | precision=18, scale=2 |
| currency | String | currency | not null, length=3, default "CAD" |
| status | OrderStatus | status | @Enumerated(STRING), not null, length=20 |
| brokerOrderId | String? | broker_order_id | nullable, length=255 |
| accountIdExternal | String? | account_id_external | nullable, length=100 |
| errorMessage | String? | error_message | TEXT |
| errorCode | String? | error_code | nullable, length=50 |
| submittedAt | OffsetDateTime? | submitted_at | nullable |
| filledAt | OffsetDateTime? | filled_at | nullable |
| cancelledAt | OffsetDateTime? | cancelled_at | nullable |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Enum -- OrderStatus:** `PENDING`, `SUBMITTED`, `FILLED`, `PARTIALLY_FILLED`, `REJECTED`, `CANCELLED`, `FAILED`
**Enum -- OrderAction:** `BUY`, `SELL`
**Enum -- OrderType:** `MARKET`, `LIMIT`
**Enum -- TimeInForce:** `DAY`, `GTC`

---

### ModelPortfolio

**File:** `broker/entity/ModelPortfolio.kt`
**Table:** `model_portfolios`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| name | String | name | not null, length=100 |
| description | String? | description | TEXT |
| riskLevel | RiskLevel | risk_level | @Enumerated(STRING), not null, length=20 |
| isSystem | Boolean | is_system | not null, default false |
| user | User? | user_id | @ManyToOne(LAZY), nullable |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Relationships:**
- `allocations` -> `ModelPortfolioAllocation` (OneToMany, mappedBy="modelPortfolio", CASCADE ALL, orphanRemoval, LAZY)

**Enum -- RiskLevel:** `LOW`, `MODERATE`, `HIGH`, `EXTRA_HIGH`

---

### ModelPortfolioAllocation

**File:** `broker/entity/ModelPortfolioAllocation.kt`
**Table:** `model_portfolio_allocations`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| modelPortfolio | ModelPortfolio | model_portfolio_id | @ManyToOne(LAZY), not null |
| symbol | String | symbol | not null, length=20 |
| targetPercent | BigDecimal | target_percent | not null, precision=7, scale=4 |
| assetClass | String? | asset_class | nullable, length=50 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

---

### PortfolioGroup

**File:** `broker/entity/PortfolioGroup.kt`
**Table:** `portfolio_groups`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| name | String | name | not null, length=100 |
| description | String? | description | TEXT |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |
| modelPortfolio | ModelPortfolio? | model_portfolio_id | @ManyToOne(LAZY), nullable |
| benchmarkModel | ModelPortfolio? | benchmark_model_id | @ManyToOne(LAZY), nullable |

**Relationships:**
- `targets` -> `PortfolioTarget` (OneToMany, mappedBy="group", CASCADE ALL, orphanRemoval, LAZY)
- `linkedAccounts` -> `PortfolioGroupAccount` (OneToMany, mappedBy="group", CASCADE ALL, orphanRemoval, LAZY)
- `settings` -> `PortfolioGroupSettings` (OneToOne, mappedBy="group", CASCADE ALL, orphanRemoval, LAZY)
- `excludedAssets` -> `PortfolioExcludedAsset` (OneToMany, mappedBy="group", CASCADE ALL, orphanRemoval, LAZY)

---

### PortfolioGroupAccount

**File:** `broker/entity/PortfolioGroupAccount.kt`
**Table:** `portfolio_group_accounts`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| group | PortfolioGroup | group_id | @ManyToOne(LAZY), not null |
| connection | BrokerConnection | connection_id | @ManyToOne(LAZY), not null |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

---

### PortfolioGroupSettings

**File:** `broker/entity/PortfolioGroupSettings.kt`
**Table:** `portfolio_group_settings`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| group | PortfolioGroup | group_id | @OneToOne(LAZY), not null |
| sellToRebalance | Boolean | sell_to_rebalance | not null, default false |
| keepCurrenciesSeparate | Boolean | keep_currencies_separate | not null, default false |
| preventNonTradableTrades | Boolean | prevent_non_tradable_trades | not null, default false |
| notifyNewAssets | Boolean | notify_new_assets | not null, default true |
| retainCashForExchange | Boolean | retain_cash_for_exchange | not null, default false |
| rebalanceFrequency | RebalanceFrequency | rebalance_frequency | @Enumerated(STRING), not null, length=20 |
| accuracyThreshold | BigDecimal | accuracy_threshold | not null, precision=5, scale=2, default 90.00 |
| autoExecute | Boolean | auto_execute | not null, default false |
| lastRebalancedAt | OffsetDateTime? | last_rebalanced_at | nullable |
| nextRebalanceDate | LocalDate? | next_rebalance_date | nullable |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Enum -- RebalanceFrequency:** `MANUAL`, `MONTHLY`, `QUARTERLY`, `SEMI_ANNUALLY`, `ANNUALLY`

---

### PortfolioTarget

**File:** `broker/entity/PortfolioTarget.kt`
**Table:** `portfolio_targets`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| group | PortfolioGroup | group_id | @ManyToOne(LAZY), not null |
| symbol | String | symbol | not null, length=20 |
| targetPercent | BigDecimal | target_percent | not null, precision=7, scale=4 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

---

### PortfolioExcludedAsset

**File:** `broker/entity/PortfolioExcludedAsset.kt`
**Table:** `portfolio_excluded_assets`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| group | PortfolioGroup | group_id | @ManyToOne(LAZY), not null |
| symbol | String | symbol | not null, length=20 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

---

### PortfolioSnapshot

**File:** `broker/entity/PortfolioSnapshot.kt`
**Table:** `portfolio_snapshots`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| group | PortfolioGroup | group_id | @ManyToOne(LAZY), not null |
| snapshotDate | LocalDate | snapshot_date | not null |
| totalValue | BigDecimal | total_value | not null, precision=18, scale=2 |
| positions | String | positions | not null, JSONB |
| cash | String | cash | not null, JSONB |
| accuracy | BigDecimal? | accuracy | precision=5, scale=2 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

---

### PortfolioCashFlow

**File:** `broker/entity/PortfolioCashFlow.kt`
**Table:** `portfolio_cash_flows`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| group | PortfolioGroup | group_id | @ManyToOne(LAZY), not null |
| flowDate | LocalDate | flow_date | not null |
| amount | BigDecimal | amount | not null, precision=18, scale=2 |
| flowType | CashFlowType | flow_type | @Enumerated(STRING), not null, length=20 |
| currency | String | currency | not null, length=3, default "CAD" |
| source | String? | source | nullable, length=50 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Enum -- CashFlowType:** `CONTRIBUTION`, `WITHDRAWAL`, `DIVIDEND`

---

### BenchmarkReturn

**File:** `broker/entity/BenchmarkReturn.kt`
**Table:** `benchmark_returns`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| symbol | String | symbol | not null, length=20 |
| returnDate | LocalDate | return_date | not null |
| closePrice | BigDecimal | close_price | not null, precision=18, scale=6 |
| dailyReturn | BigDecimal? | daily_return | precision=12, scale=8 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

No foreign key relationships (standalone entity).

---

### Notification

**File:** `broker/entity/Notification.kt`
**Table:** `notifications`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| type | NotificationType | type | @Enumerated(STRING), not null, length=30 |
| title | String | title | not null, length=200 |
| message | String | message | not null, TEXT |
| link | String? | link | nullable, length=500 |
| isRead | Boolean | is_read | not null, default false |
| metadata | String? | metadata | JSONB |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Enum -- NotificationType:** `DRIFT_ALERT`, `ORDER_FILLED`, `ORDER_REJECTED`, `ORDER_FAILED`, `SYNC_FAILURE`, `NEW_ASSET`, `REBALANCE_REMINDER`, `SYSTEM`

---

### NotificationPreference

**File:** `broker/entity/NotificationPreference.kt`
**Table:** `notification_preferences`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @OneToOne(LAZY), not null |
| emailEnabled | Boolean | email_enabled | not null, default true |
| inAppEnabled | Boolean | in_app_enabled | not null, default true |
| driftAlerts | Boolean | drift_alerts | not null, default true |
| driftThreshold | BigDecimal | drift_threshold | not null, precision=5, scale=2, default 90.00 |
| orderAlerts | Boolean | order_alerts | not null, default true |
| syncFailureAlerts | Boolean | sync_failure_alerts | not null, default true |
| newAssetAlerts | Boolean | new_asset_alerts | not null, default true |
| rebalanceReminder | Boolean | rebalance_reminder | not null, default false |
| reminderFrequency | String | reminder_frequency | length=20, default "WEEKLY" |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

---

### DashboardPreference

**File:** `broker/entity/DashboardPreference.kt`
**Table:** `dashboard_preferences`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| user | User | user_id | @ManyToOne(LAZY), not null |
| contextType | DashboardContextType | context_type | @Enumerated(STRING), not null, length=20 |
| contextId | Long? | context_id | nullable |
| widgetKey | WidgetKey | widget_key | @Enumerated(STRING), not null, length=50 |
| isVisible | Boolean | is_visible | not null, default true |
| sortOrder | Int | sort_order | not null, default 0 |
| columnSpan | Int | column_span | not null, default 1 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Enum -- WidgetKey:** `PORTFOLIO_VALUE`, `AVAILABLE_CASH`, `BUYING_POWER`, `RISK_PROFILE`, `SECTOR_EXPOSURE`, `GEOGRAPHY_EXPOSURE`, `REBALANCING_PROGRESS`, `PENDING_ORDERS`, `OPEN_ORDERS`, `FEES_COMMISSION`, `DIVIDEND_CALENDAR`, `POSITIONS_TABLE`, `HOLDINGS_TABLE`, `CONNECTED_ACCOUNTS`, `POSITIONS_HOLDINGS`, `PORTFOLIO_SUMMARY`, `POSITIONS_SUMMARY` (legacy), `HOLDINGS_COUNT` (legacy), `REFRESH_BUTTON` (legacy)

**Enum -- DashboardContextType:** `DASHBOARD`, `ACCOUNT`

---

### RebalanceEvent

**File:** `broker/entity/RebalanceEvent.kt`
**Table:** `rebalance_events`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| group | PortfolioGroup | group_id | @ManyToOne(LAZY), not null |
| triggerType | RebalanceTriggerType | trigger_type | @Enumerated(STRING), not null, length=20 |
| accuracyBefore | BigDecimal? | accuracy_before | precision=5, scale=2 |
| accuracyAfter | BigDecimal? | accuracy_after | precision=5, scale=2 |
| tradesCount | Int | trades_count | not null, default 0 |
| batchId | UUID? | batch_id | nullable |
| status | RebalanceStatus | status | @Enumerated(STRING), not null, length=20 |
| notes | String? | notes | TEXT |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Enum -- RebalanceTriggerType:** `SCHEDULED`, `ACCURACY_DROP`, `MANUAL`
**Enum -- RebalanceStatus:** `COMPLETED`, `FAILED`, `SKIPPED`, `PENDING_APPROVAL`

---

### SnapTradeStatusCheck

**File:** `broker/entity/SnapTradeStatusCheck.kt`
**Table:** `snaptrade_status_checks`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| status | SnapTradeApiStatus | status | @Enumerated(STRING), not null, length=20 |
| responseTimeMs | Int? | response_time_ms | nullable |
| version | String? | version | nullable |
| errorMessage | String? | error_message | TEXT |
| rawResponse | String? | raw_response | jsonb, @JdbcTypeCode(JSON) |
| checkedAt | OffsetDateTime | checked_at | not null |

**Enum -- SnapTradeApiStatus:** `ONLINE`, `DEGRADED`, `OFFLINE`, `UNKNOWN`

No foreign key relationships (standalone entity).

---

### PositionFetchLog

**File:** `broker/entity/PositionFetchLog.kt`
**Table:** `position_fetch_log`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| connection | BrokerConnection | connection_id | @ManyToOne(LAZY), not null |
| user | User | user_id | @ManyToOne(LAZY), not null |
| fetchType | PositionFetchType | fetch_type | @Enumerated(STRING), not null, length=20 |
| status | FetchStatus | status | @Enumerated(STRING), not null, length=20 |
| startedAt | OffsetDateTime | started_at | not null |
| completedAt | OffsetDateTime? | completed_at | nullable |
| durationMs | Int? | duration_ms | nullable |
| positionsCount | Int? | positions_count | nullable |
| totalValue | BigDecimal? | total_value | precision=18, scale=2 |
| errorCode | String? | error_code | nullable, length=50 |
| errorMessage | String? | error_message | TEXT |
| rawResponse | String? | raw_response | jsonb, @JdbcTypeCode(JSON) |
| retryCount | Int | retry_count | default 0 |
| triggeredBy | String? | triggered_by | nullable, length=50 |

**Enum -- PositionFetchType:** `MANUAL`, `INITIAL`
**Enum -- FetchStatus:** `PENDING`, `IN_PROGRESS`, `SUCCESS`, `FAILED`, `PARTIAL`, `CANCELLED`

**Methods:** `markSuccess(count, value)`, `markFailed(code, msg)`, `markPartial(count, msg)`

---

## Core Entities (V68 Cleanup)

**V68 Note:** The following entities (`Stock`, `Etf`, `EtfHolding`, all GICS entities, `DataSource`, `IngestionBatch`) were removed in V68. The portfolio app now reads instrument data from the `ingestion` schema via `IngestionInstrumentLookupService`. The entity files are retained in the codebase as legacy/reference but are no longer used by any active service.

### Stock (REMOVED IN V68)

**File:** `entity/Stock.kt`
**Table:** `stocks` (DROPPED IN V68)

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| ticker | String | ticker | not null, length=20 |
| name | String | name | not null, length=255 |
| isin | String? | isin | nullable, length=12 |
| cusip | String? | cusip | nullable, length=9 |
| sedol | String? | sedol | nullable, length=7 |
| currency | String | currency | not null, length=3, default "USD" |
| country | String | country | not null, length=3, default "USA" |
| status | SecurityStatus | status | @Enumerated(STRING), not null, length=20 |
| exchangeCode | String? | exchange_code | nullable, length=20 |
| isActive | Boolean | is_active | default true |
| sourceLastSeenAt | OffsetDateTime? | source_last_seen_at | nullable |
| rawEodhdPayload | String? | raw_eodhd_payload | jsonb, @JdbcTypeCode(JSON) |
| avIngestionStatus | AVIngestionStatus | av_ingestion_status | @Enumerated(STRING), length=20 |
| avIngestionLastAttemptAt | OffsetDateTime? | av_ingestion_last_attempt_at | nullable |
| avIngestionLastSuccessAt | OffsetDateTime? | av_ingestion_last_success_at | nullable |
| avIngestionRetryCount | Int | av_ingestion_retry_count | default 0 |
| avIngestionErrorCode | String? | av_ingestion_error_code | nullable, length=50 |
| avIngestionErrorMessage | String? | av_ingestion_error_message | nullable, length=500 |
| avRawPayload | JsonNode? | av_raw_payload | jsonb, @JdbcTypeCode(JSON) |
| gicsSectorCode | String? | gics_sector_code | nullable, length=10 |
| gicsIndustryGroupCode | String? | gics_industry_group_code | nullable, length=10 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Enum -- SecurityStatus:** `ACTIVE`, `DELISTED`, `SUSPENDED`, `PENDING`
**Enum -- AVIngestionStatus:** `PENDING`, `SUCCESS`, `FAILED_RETRYABLE`, `FAILED_PERMANENT`, `STALE`

**Helper methods:** `avField(key)`, `avDecimal(key)`

---

### Etf (REMOVED IN V68)

**File:** `entity/Etf.kt`
**Table:** `etfs` (DROPPED IN V68)

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| symbol | String | symbol | not null, length=20 |
| name | String | name | not null, length=255 |
| isin | String? | isin | nullable, length=12 |
| cusip | String? | cusip | nullable, length=9 |
| issuer | String? | issuer | nullable, length=100 |
| currency | String | currency | not null, length=3, default "USD" |
| domicile | String | domicile | not null, length=3, default "USA" |
| inceptionDate | LocalDate? | inception_date | nullable |
| assetClass | String? | asset_class | nullable, length=50 |
| status | SecurityStatus | status | @Enumerated(STRING), not null, length=20 |
| isActive | Boolean | is_active | default true |
| sourceLastSeenAt | OffsetDateTime? | source_last_seen_at | nullable |
| etfcomFundId | Int? | etfcom_fund_id | nullable |
| etfcomAssetClass | String? | etfcom_asset_class | nullable, length=50 |
| etfcomEnrichmentStatus | EtfComEnrichmentStatus | etfcom_enrichment_status | @Enumerated(STRING), length=20 |
| etfcomLastAttemptAt | OffsetDateTime? | etfcom_last_attempt_at | nullable |
| etfcomLastSuccessAt | OffsetDateTime? | etfcom_last_success_at | nullable |
| etfcomRetryCount | Int | etfcom_retry_count | default 0 |
| etfcomErrorCode | String? | etfcom_error_code | nullable, length=50 |
| etfcomErrorMessage | String? | etfcom_error_message | TEXT |
| etfcomRawPayload | JsonNode? | etfcom_raw_payload | jsonb, @JdbcTypeCode(JSON) |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Enum -- EtfComEnrichmentStatus:** `PENDING`, `SUCCESS`, `FAILED_RETRYABLE`, `FAILED_PERMANENT`, `STALE`

---

### EtfHolding (REMOVED IN V68)

**File:** `entity/EtfHolding.kt`
**Table:** `etf_holdings` (DROPPED IN V68)

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| etf | Etf | etf_id | @ManyToOne(LAZY), not null |
| stock | Stock? | stock_id | @ManyToOne(LAZY), nullable |
| heldEtf | Etf? | held_etf_id | @ManyToOne(LAZY), nullable |
| asOfDate | LocalDate | as_of_date | not null |
| weight | BigDecimal? | weight | precision=8, scale=6 |
| shares | BigDecimal? | shares | precision=18, scale=4 |
| marketValue | BigDecimal? | market_value | precision=18, scale=2 |
| rawTicker | String? | raw_ticker | nullable, length=50 |
| rawName | String? | raw_name | nullable, length=255 |
| rawIsin | String? | raw_isin | nullable, length=20 |
| rawCusip | String? | raw_cusip | nullable, length=20 |
| rawCountry | String? | raw_country | nullable, length=50 |
| resolutionStatus | ResolutionStatus | resolution_status | @Enumerated(STRING), length=20 |
| holdingType | HoldingType | holding_type | @Enumerated(STRING), length=20 |
| rank | Int? | rank | nullable |
| sourceSection | HoldingSourceSection | source_section | @Enumerated(STRING), length=20 |
| isValidSymbol | Boolean? | is_valid_symbol | nullable |
| dataSource | HoldingDataSource | data_source | @Enumerated(STRING), length=20 |
| avWeight | BigDecimal? | av_weight | precision=18, scale=6 |
| avLastUpdatedAt | OffsetDateTime? | av_last_updated_at | nullable |
| etfcomWeight | BigDecimal? | etfcom_weight | precision=18, scale=6 |
| etfcomLastUpdatedAt | OffsetDateTime? | etfcom_last_updated_at | nullable |
| ingestionBatch | IngestionBatch? | ingestion_batch_id | @ManyToOne(LAZY), nullable |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Enum -- ResolutionStatus:** `RESOLVED`, `UNRESOLVED`, `PARTIAL`
**Enum -- HoldingType:** `STOCK`, `ETF`, `MUTUAL_FUND`, `UNKNOWN`
**Enum -- HoldingSourceSection:** `TOP_TEN`, `EODHD`
**Enum -- HoldingDataSource:** `EODHD`, `ALPHA_VANTAGE`, `MANUAL`, `ETF_COM`

---

### Country

**File:** `entity/Country.kt`
**Table:** `countries`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| code | String | code | not null, length=3 |
| name | String | name | not null, length=100 |
| alpha2Code | String? | alpha2_code | nullable, length=2 |
| region | Region | region_id | @ManyToOne(LAZY), not null |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

---

### Region

**File:** `entity/Region.kt`
**Table:** `regions`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| code | String | code | not null, length=20 |
| name | String | name | not null, length=100 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Relationships:**
- `countries` -> `Country` (OneToMany, mappedBy="region", LAZY)

---

### DataSource (REMOVED IN V68)

**File:** `entity/DataSource.kt`
**Table:** `data_sources` (DROPPED IN V68)

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| code | String | code | not null, length=50 |
| name | String | name | not null, length=100 |
| description | String? | description | TEXT |
| isActive | Boolean | is_active | not null, default true |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

---

### IngestionBatch (REMOVED IN V68)

**File:** `entity/IngestionBatch.kt`
**Table:** `ingestion_batches` (DROPPED IN V68)

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| source | DataSource | source_id | @ManyToOne(LAZY), not null |
| batchDate | LocalDate | batch_date | not null |
| ingestedAt | OffsetDateTime | ingested_at | not null |
| recordCount | Int? | record_count | nullable |
| status | IngestionStatus | status | @Enumerated(STRING), not null, length=20 |
| metadata | String? | metadata | JSONB |

**Enum -- IngestionStatus:** `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`

---

### EtfSectorAllocationFactset (REMOVED IN V68)

**File:** `entity/EtfSectorAllocationFactset.kt`
**Table:** `etf_sector_allocations_factset` (DROPPED IN V68)

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| etf | Etf | etf_id | @ManyToOne(LAZY), not null |
| sectorName | String | sector_name | not null, length=100 |
| weight | BigDecimal? | weight | precision=18, scale=6 |
| asOfDate | LocalDate | as_of_date | not null |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**Unique constraint:** `(etf_id, sector_name, as_of_date)`

---

## GICS Entities (REMOVED IN V68)

**V68 Note:** All GICS entities and tables were dropped in V68. GICS data is now embedded in `ingestion.provider_raw_data` JSONB payloads.

### GicsSector (REMOVED IN V68)

**File:** `entity/gics/GicsSector.kt`
**Table:** `gics_sectors` (DROPPED IN V68)

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| code | String | code | not null, length=2 |
| name | String | name | not null, length=100 |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Relationships:**
- `industryGroups` -> `GicsIndustryGroup` (OneToMany, mappedBy="sector", LAZY)

### GicsIndustryGroup

**File:** `entity/gics/GicsIndustryGroup.kt`
**Table:** `gics_industry_groups`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| code | String | code | not null, length=4 |
| name | String | name | not null, length=100 |
| sector | GicsSector | sector_id | @ManyToOne(LAZY), not null |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Relationships:**
- `industries` -> `GicsIndustry` (OneToMany, mappedBy="industryGroup", LAZY)

### GicsIndustry

**File:** `entity/gics/GicsIndustry.kt`
**Table:** `gics_industries`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| code | String | code | not null, length=6 |
| name | String | name | not null, length=100 |
| industryGroup | GicsIndustryGroup | industry_group_id | @ManyToOne(LAZY), not null |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

**Relationships:**
- `subIndustries` -> `GicsSubIndustry` (OneToMany, mappedBy="industry", LAZY)

### GicsSubIndustry

**File:** `entity/gics/GicsSubIndustry.kt`
**Table:** `gics_sub_industries`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| code | String | code | not null, length=8 |
| name | String | name | not null, length=150 |
| industry | GicsIndustry | industry_id | @ManyToOne(LAZY), not null |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |
| updatedAt | OffsetDateTime | updated_at | not null |

### GicsSectorAlias

**File:** `entity/gics/GicsSectorAlias.kt`
**Table:** `gics_sector_aliases`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| aliasValue | String | alias_value | not null, length=100 |
| gicsSector | GicsSector | gics_sector_id | @ManyToOne(LAZY), not null |
| source | String | source | not null, length=30, default "SEEKING_ALPHA" |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

### GicsSubIndustryAlias

**File:** `entity/gics/GicsSubIndustryAlias.kt`
**Table:** `gics_sub_industry_aliases`

| Field | Kotlin Type | Column | Annotations |
|-------|------------|--------|-------------|
| id | Long | id | @Id @GeneratedValue(IDENTITY) |
| aliasCode | String | alias_code | not null, length=20 |
| aliasName | String? | alias_name | nullable, length=150 |
| gicsSubIndustry | GicsSubIndustry | gics_sub_industry_id | @ManyToOne(LAZY), not null |
| source | String | source | not null, length=30, default "SEEKING_ALPHA" |
| createdAt | OffsetDateTime | created_at | not null, updatable=false |

**GICS hierarchy:** GicsSector (2-digit) -> GicsIndustryGroup (4-digit) -> GicsIndustry (6-digit) -> GicsSubIndustry (8-digit)

---

## Auth DTOs

**File:** `auth/dto/AuthDtos.kt`

### Request DTOs

| DTO | Fields | Validation |
|-----|--------|------------|
| `SignupRequest` | email, password, name? | @Email, @Size(min=12) on password |
| `LoginRequest` | email, password | @Email, @NotBlank |
| `ForgotPasswordRequest` | email | @Email |
| `ResetPasswordRequest` | token, newPassword | @Size(min=12) on newPassword |
| `ChangePasswordRequest` | currentPassword, newPassword | @Size(min=12) on newPassword |
| `ResendVerificationRequest` | email | @Email |
| `UpdateProfileRequest` | name?, avatarUrl? | @Size(max=255), @Size(max=500) |

### Response DTOs

| DTO | Fields | Maps From |
|-----|--------|-----------|
| `SignupResponse` | message, userId | -- |
| `AuthResponse` | user: UserResponse, message? | -- |
| `MessageResponse` | message | -- |
| `UserResponse` | id, email, name?, avatarUrl?, emailVerified, roles[], identities[], lastLoginAt?, createdAt | User entity via `UserResponse.from(user)` |
| `IdentityResponse` | provider, providerEmail?, connectedAt | UserIdentity entity via `IdentityResponse.from(identity)` |
| `AuthErrorResponse` | error, message, field?, lockedUntil? | -- |

---

## Broker DTOs

### broker/dto/BrokerDtos.kt

**Request DTOs:**

| DTO | Fields |
|-----|--------|
| `ConnectBrokerRequest` | broker?, reconnectAuthId?, connectionType? ("read"\|"trade"\|"trade-if-available") |

**Response DTOs:**

| DTO | Fields | Maps From |
|-----|--------|-----------|
| `BrokerDto` | id?, code?, name, slug?, status?, logoUrl?, description?, url?, openUrl?, enabled?, maintenanceMode?, isDegraded?, allowsTrading?, allowsFractionalUnits?, hasReporting?, isRealTimeConnection?, brokerageType?, authTypes? | Broker entity via `Broker.toDto()`, or SnapTrade API |
| `BrokerAuthTypeDto` | type ("read"\|"trade"), authType ("OAUTH"\|"SCRAPE"\|"UNOFFICIAL_API") | SnapTrade API |
| `BrokerConnectionDto` | id, broker: BrokerDto, snaptradeAuthorizationId?, accountNumber?, accountType?, accountName?, accountNumberActual?, accountMetaType?, status, lastPositionsFetchedAt?, positionsCount, totalValue?, errorMessage?, createdAt, modelPortfolioId?, modelPortfolioName? | BrokerConnection entity via `BrokerConnection.toDto()` |
| `BrokerPositionDto` | id, symbol, securityName?, instrumentType?, quantity, averageCost?, currentPrice?, currentValue?, totalPnl?, totalPnlPercent?, currency, strikePrice?, expirationDate?, optionType?, underlyingSymbol? | BrokerPosition entity via `BrokerPosition.toDto()` |
| `AggregatedPositionDto` | symbol, securityName?, instrumentType?, totalQuantity, totalValue, averageCost?, totalPnl?, totalPnlPercent?, currency, brokerBreakdown[] | Aggregated from BrokerPosition entities |
| `SnapTradeStatusDto` | status, responseTimeMs?, version?, uptimePercent24h, lastChecked | SnapTradeStatusCheck entities |
| `BrokerActivityDto` | id, type, symbol?, description?, quantity?, price?, amount, fee?, currency, tradeDate, settlementDate?, accountName?, optionType? | BrokerActivity entity via `BrokerActivity.toActivityDto()` |
| `BalanceSnapshotDto` | totalValue?, cash: Map<String, BigDecimal>, currency, asOfDate | BrokerBalanceSnapshot entity |
| `ReportingPerformanceResponse` | contributionsWithdrawals[], totalValueHistory[], dividendHistory[], totalDividendsBySymbol[], kpis | Computed from BrokerActivity entities |

**Wrapper DTOs:** `BrokersResponse`, `BrokerConnectionsResponse`, `ConnectBrokerResponse`, `PositionFetchResponse`, `ConnectionPositionsResponse`, `AggregatedPositionsResponse`, `ConnectionSyncResponse`, `ActivitiesResponse`, `BalanceHistoryResponse`

### broker/dto/DashboardDataDtos.kt

| DTO | Fields | Maps From |
|-----|--------|-----------|
| `PortfolioValueDto` | totalValue, investmentValue, cashValue, totalChange?, totalChangePercent?, currency | Computed from BrokerPosition + BrokerBalanceSnapshot |
| `PositionsSummaryDto` | stocks, etfs, mutualFunds, options, bonds, cash, other, total | Counted from BrokerPosition entities |
| `HoldingsCountDto` | directStocks, lookThroughStocks, totalUniqueHoldings, etfsDecomposed, mutualFundsDecomposed, coveragePercent | Computed via LookThroughService |
| `DashboardSummaryResponse` | portfolioValue, positionsSummary, holdingsCount, warnings[] | Composite |
| `DashboardCashResponse` | availableCash[], buyingPower[], totalCashCAD, totalBuyingPowerCAD | BrokerBalanceSnapshot entities |
| `SectorExposureResponse` | sectors[], coveragePercent, unmappedWeight, warnings[] | Computed via LookThroughService |
| `GeographyExposureResponse` | regions[], coveragePercent, unmappedWeight, warnings[] | Computed via LookThroughService |
| `RiskProfileResponse` | riskScore, riskLevel, factors: RiskFactorsDto | Computed from positions |
| `OpenOrdersResponse` | orders[], totalCount | TradeOrder entities |
| `FeesResponse` | last12Months, monthlyBreakdown[], managementExpensePerMonth | BrokerActivity entities |
| `DividendCalendarResponse` | month, totalDividends, entries[] | BrokerActivity entities |
| `HoldingsTableResponse` | holdings[], totalCount, coveragePercent | LookThroughService |
| `DashboardAccountsResponse` | accounts[] | BrokerConnection entities |

### broker/dto/ModelPortfolioDtos.kt

**Request DTOs:**

| DTO | Fields |
|-----|--------|
| `CreateModelPortfolioRequest` | name, description?, riskLevel, allocations[] |
| `UpdateModelPortfolioRequest` | name?, description?, riskLevel?, allocations[]? |
| `ModelAllocationInput` | symbol, targetPercent, assetClass? |
| `ApplyToAccountsRequest` | connectionIds[] |

**Response DTOs:**

| DTO | Fields | Maps From |
|-----|--------|-----------|
| `ModelPortfolioSummaryDto` | id, name, description?, riskLevel, isSystem, allocationCount, totalPercent | ModelPortfolio via `ModelPortfolio.toSummaryDto()` |
| `ModelPortfolioDetailDto` | id, name, description?, riskLevel, isSystem, allocations[] | ModelPortfolio via `ModelPortfolio.toDetailDto()` |
| `ModelAllocationDto` | id, symbol, targetPercent, assetClass? | ModelPortfolioAllocation via `.toDto()` |
| `RebalanceProgressDto` | connectionId, modelName, accuracy, entries[] | Computed from BrokerPosition vs ModelPortfolioAllocation |
| `PendingOrdersResponse` | connectionId, orders[], totalAmount, cashRemaining, cashWarning?, totalSellAmount, totalBuyAmount | Computed rebalance orders |
| `ModelAnalysisDto` | modelId, sectorExposure[], geographyExposure[], riskScore, riskLevel, holdings[] | Computed from model allocations |

### broker/dto/NotificationDtos.kt

**Request DTOs:**

| DTO | Fields |
|-----|--------|
| `UpdateNotificationPreferenceRequest` | emailEnabled?, inAppEnabled?, driftAlerts?, driftThreshold?, orderAlerts?, syncFailureAlerts?, newAssetAlerts?, rebalanceReminder?, reminderFrequency? |

**Response DTOs:**

| DTO | Fields | Maps From |
|-----|--------|-----------|
| `NotificationDto` | id, type, title, message, link?, isRead, metadata?, createdAt | Notification via `Notification.toDto()` |
| `NotificationsResponse` | notifications[], unreadCount, totalCount | -- |
| `NotificationPreferenceDto` | emailEnabled, inAppEnabled, driftAlerts, driftThreshold, orderAlerts, syncFailureAlerts, newAssetAlerts, rebalanceReminder, reminderFrequency | NotificationPreference via `.toDto()` |

### broker/dto/PerformanceDtos.kt

| DTO | Fields | Maps From |
|-----|--------|-----------|
| `PerformanceSummaryDto` | twr, mwr, totalReturn, volatility, sharpeRatio, sortinoRatio, maxDrawdown, startingValue, endingValue, startDate, endDate | Computed from PortfolioSnapshot |
| `ReturnPoint` | date, cumulativeReturn, portfolioValue | Computed |
| `BenchmarkComparisonDto` | portfolioReturns[], benchmarkReturns[], alpha | Computed from BenchmarkReturn |
| `DrawdownPoint` | date, drawdown | Computed |
| `PerformanceChartData` | summary, cumulativeReturns[], drawdowns[], benchmarkComparison? | Composite |
| `SnapshotDto` | id, snapshotDate, totalValue, accuracy?, createdAt | PortfolioSnapshot via `.toDto()` |

### broker/dto/PortfolioGroupDtos.kt

**Request DTOs:**

| DTO | Fields |
|-----|--------|
| `CreatePortfolioGroupRequest` | name, description?, targets[]?, accountIds[]? |
| `UpdatePortfolioGroupRequest` | name?, description? |
| `SetTargetsRequest` | targets: TargetInput[] |
| `TargetInput` | symbol, targetPercent |
| `LinkAccountRequest` | connectionId |
| `UpdateSettingsRequest` | sellToRebalance?, keepCurrenciesSeparate?, preventNonTradableTrades?, notifyNewAssets?, retainCashForExchange?, rebalanceFrequency?, accuracyThreshold?, autoExecute? |
| `ExcludeAssetRequest` | symbol |

**Response DTOs:**

| DTO | Fields | Maps From |
|-----|--------|-----------|
| `PortfolioGroupSummaryDto` | id, name, description?, accountCount, targetCount, totalValue, accuracy | PortfolioGroup + computed |
| `PortfolioGroupDetailDto` | id, name, description?, targets[], linkedAccounts[], settings, excludedAssets[], totalValue, accuracy | PortfolioGroup + children |
| `TargetAllocationDto` | id, symbol, targetPercent | PortfolioTarget via `.toDto()` |
| `LinkedAccountDto` | connectionId, accountName?, accountNumber?, accountType?, totalValue?, status | PortfolioGroupAccount via `.toLinkedAccountDto()` |
| `PortfolioGroupSettingsDto` | sellToRebalance, keepCurrenciesSeparate, preventNonTradableTrades, notifyNewAssets, retainCashForExchange, rebalanceFrequency, accuracyThreshold, autoExecute, lastRebalancedAt?, nextRebalanceDate? | PortfolioGroupSettings via `.toDto()` |
| `DriftAnalysisResponse` | groupId, groupName, accuracy, totalValue, cash, holdings[], excludedAssets[], newAssets[] | DriftCalculationService |
| `RebalanceTradesResponse` | groupId, trades[], cashRemaining, resultingAccuracy | DriftCalculationService |
| `RebalanceEventDto` | id, groupId, triggerType, accuracyBefore?, accuracyAfter?, tradesCount, batchId?, status, notes?, createdAt | RebalanceEvent via `.toDto()` |

### broker/dto/TradingDtos.kt

**Request DTOs:**

| DTO | Fields |
|-----|--------|
| `ExecuteTradesRequest` | groupId, trades: TradeExecutionInput[], orderType (default "MARKET"), timeInForce (default "DAY") |
| `TradeExecutionInput` | symbol, action ("BUY"\|"SELL"), units, price, amount, currency (default "CAD"), connectionId, limitPrice? |

**Response DTOs:**

| DTO | Fields | Maps From |
|-----|--------|-----------|
| `TradeOrderDto` | id, groupId?, connectionId, batchId?, symbol, action, orderType, timeInForce, requestedUnits/Price/Amount, limitPrice?, filledUnits/Price/Amount?, currency, status, brokerOrderId?, accountName?, errorMessage?, errorCode?, submittedAt?, filledAt?, cancelledAt?, createdAt | TradeOrder via `TradeOrder.toDto()` |
| `ExecuteTradesResponse` | batchId, orders[], submittedCount, failedCount | -- |
| `OrderStatusResponse` | orders[], totalCount | -- |

---

## Core DTOs

### dto/request/PortfolioRequest.kt

| DTO | Fields |
|-----|--------|
| `PortfolioPositionRequest` | instrumentType: String, instrumentId: Long, weight: Double |
| `PortfolioAnalyzeRequest` | positions[], analysisDate? |
| `PortfolioValidateRequest` | positions[] |
| `PortfolioNormalizeRequest` | positions[] |

### dto/request/ScreenerRequest.kt

| DTO | Fields |
|-----|--------|
| `StockFilterRequest` | sector?, country?, status?, tickerContains?, nameContains? |
| `EtfFilterRequest` | issuer?, assetClass?, status?, symbolContains?, nameContains?, maxExpenseRatio? |

### dto/response/PortfolioAnalysisDto.kt

| DTO | Fields | Description |
|-----|--------|-------------|
| `PortfolioAnalysisResponseDto` | summary, validation, sectorExposure[], geographyExposure[], topHoldings[], riskMetrics, financialSummary?, analysisQuality? | Full analysis response |
| `PortfolioSummaryDto` | totalPositions, directStockCount, etfCount, lookThroughStockCount, analysisDate | Analysis summary |
| `ValidationDto` | isValid, totalWeight, errors[], warnings[] | Validation results |
| `SectorExposureDto` | sectorCode, sectorName, weight | Sector allocation |
| `GeographyExposureDto` | country, countryName, region, weight | Geography allocation |
| `TopHoldingDto` | stockId, ticker, name, effectiveWeight, sources[] | Look-through top holding |
| `RiskMetricsDto` | concentrationHHI, top10Concentration, sectorConcentrationHHI, estimatedVolatility, volatilitySource, weightedBeta?, portfolioDividendYield?, weightedPeRatio?, betaCoverage? | Risk analytics |
| `FinancialSummaryDto` | weightedPeRatio?, weightedDividendYield?, weightedBeta?, marketCapBreakdown?, totalExpenseRatio? | Financial summary |
| `AnalysisQualityDto` | lookThroughCoverage, enrichmentCoverage, unresolvedHoldingsCount, unresolvedHoldingsWeight, dataQualityScore (HIGH\|MEDIUM\|LOW) | Data quality info |
| `ValidateResponseDto` | isValid, totalWeight, errors[], warnings[] | Standalone validation |
| `NormalizeResponseDto` | originalTotal, normalizedPositions[] | Normalization result |

### dto/response/InstrumentDto.kt

| DTO | Fields |
|-----|--------|
| `SearchResultDto` | id, type (STOCK\|ETF), ticker, name, exchange?, matchType (IDENTIFIER_EXACT\|TICKER_EXACT\|TICKER_PREFIX\|NAME_CONTAINS), status?, isActive? |
| `SearchResponseDto` | data[], meta: SearchMetaDto |
| `StockDto` | id, ticker, exchange?, name, isin?, cusip?, sedol?, currency, country, sector?, status |
| `StockDetailDto` | id, ticker, name, currency, country, isin?, avIngestionStatus, sections[] |
| `EtfDetailDto` | id, symbol, name, issuer?, assetClass?, inceptionDate?, enrichmentStatus, summary[], description?, portfolio[], performance[], topHoldings[], holdingsCount?, sectorBreakdown? |
| `EtfDto` | id, symbol, name, isin?, cusip?, issuer?, currency, domicile, inceptionDate?, assetClass?, status |

### dto/response/HoldingsDto.kt

| DTO | Fields |
|-----|--------|
| `HoldingDto` | stockId?, ticker, name, weight?, shares?, marketValue?, sector?, country?, isResolved, rawTicker?, rawName?, dataSource?, holdingType?, rank?, resolutionStatus? |
| `EtfHoldingsResponseDto` | etfId, etfSymbol, asOfDate, holdingsCount, holdings[], metadata? |
| `HoldingsMetadataDto` | resolvedCount, unresolvedCount, resolvedPercent, primaryDataSource?, hasEnrichmentData |

### dto/response/PageDto.kt

| DTO | Fields |
|-----|--------|
| `PageMetaDto` | page, size, totalElements, totalPages |
| `PagedResponseDto<T>` | data: List<T>, meta: PageMetaDto |

### dto/response/ReferenceDataDto.kt

| DTO | Fields |
|-----|--------|
| `GicsSectorDto` | code, name, industryGroups[] |
| `GicsIndustryGroupDto` | code, name, industries[] |
| `GicsIndustryDto` | code, name, subIndustries[] |
| `GicsSubIndustryDto` | code, name |
| `GicsSectorSimpleDto` | code, name |
| `CountryDto` | code, name, regionCode?, regionName? |
| `RegionDto` | code, name, countries[] |
| `ExchangeDto` | code, name |

### dto/response/ErrorResponse.kt

| DTO | Fields |
|-----|--------|
| `ErrorResponse` | status, error, message, errorCode?, field?, timestamp, path? |

---

## SnapTrade Adapter DTOs

**File:** `broker/adapter/SnapTradeDtos.kt`

These DTOs map raw SnapTrade SDK responses into internal representations.

| DTO | Fields | SnapTrade API Source |
|-----|--------|---------------------|
| `SnapTradeAccountDto` | id (UUID?), brokerageAuthorization (UUID?), number?, name?, institutionName?, currency?, metaType?, metaAccountNumber?, rawType?, balance?, balanceCurrency? | Account listing |
| `SnapTradeConnectionDto` | id (UUID?), disabled?, brokerageName?, brokerLogoUrl?, type? | Brokerage authorization |
| `SnapTradePositionDto` | symbol?, symbolId?, symbolDescription?, symbolTypeCode?, currencyCode?, units?, price?, averagePurchasePrice? | Account positions |
| `SnapTradeOptionPositionDto` | symbol?, strikePrice?, expirationDate?, optionType? (CALL\|PUT), underlyingSymbol?, units?, price?, averagePurchasePrice?, currencyCode? | Option positions |
| `SnapTradeHoldingsDto` | totalValue?, totalValueCurrency?, positions[], balances[] | Account holdings |
| `SnapTradeBalanceDto` | currency?, cash?, buyingPower? | Account balances |
| `SnapTradeActivityDto` | id?, type?, symbol?, description?, units?, price?, amount?, fee?, currency?, tradeDate?, settlementDate?, optionType?, rawJson? | Account activities |
| `SnapTradeBrokerageDto` | id (UUID?), name?, slug?, displayName?, logoUrl?, description?, url?, openUrl?, enabled?, maintenanceMode?, isDegraded?, allowsTrading?, allowsFractionalUnits?, hasReporting?, isRealTimeConnection?, brokerageType? | Brokerage listing |
| `SnapTradeBrokerageAuthTypeDto` | id (UUID?), type? ("read"\|"trade"), authType? ("OAUTH"\|"SCRAPE"\|"UNOFFICIAL_API"), brokerageId? | Brokerage auth types |
| `SnapTradeOrderDto` | brokerageOrderId?, status?, symbol?, action?, units?, price? | Order placement response |
| `SnapTradeAccountOrderDto` | brokerageOrderId?, status?, symbol?, action?, totalQuantity?, openQuantity?, filledQuantity?, executionPrice?, limitPrice?, stopPrice?, orderType?, timeInForce?, timePlaced?, timeUpdated?, timeExecuted?, currency? | Account orders listing |
| `SnapTradeApiStatusDto` | online, version? | API status check |

**Exception class:** `SnapTradeApiException` with `errorCode: Int?` and constant `ERROR_PERSONAL_KEY_SLOT_OCCUPIED = 1012`.

---

## Ingestion Service Entities (ingestion schema)

These entities belong to the separate `ingestion-service` module and live in the `ingestion` PostgreSQL schema. They are NOT part of the main backend's JPA context.

### Exchange

**File:** `backend/ingestion/.../persistence/entity/Exchange.kt`
**Table:** `ingestion.exchanges`

| Field | Kotlin Type | Column |
|-------|------------|--------|
| id | Int | id |
| code | String | code (unique) |
| name | String | name |
| country | String? | country |
| currency | String? | currency |
| operatingMic | String? | operating_mic |
| isActive | Boolean | is_active (default true) |
| createdAt | OffsetDateTime | created_at |

### Instrument

**File:** `backend/ingestion/.../persistence/entity/Instrument.kt`
**Table:** `ingestion.instruments`

| Field | Kotlin Type | Column |
|-------|------------|--------|
| id | Long | id |
| ticker | String | ticker |
| name | String | name |
| instrumentType | InstrumentType | instrument_type |
| isin | String? | isin (unique) |
| cusip | String? | cusip |
| currency | String? | currency |
| country | String? | country |
| status | InstrumentStatus | status (default ACTIVE) |
| sourceLastSeenAt | OffsetDateTime? | source_last_seen_at |
| createdAt | OffsetDateTime | created_at |
| updatedAt | OffsetDateTime | updated_at |

**Enum -- InstrumentType:** `STOCK`, `PREFERRED_STOCK`, `ETF`, `MUTUAL_FUND`, `INDEX`, `BOND`
**Enum -- InstrumentStatus:** `ACTIVE`, `DELISTED`, `SUSPENDED`, `PENDING`

### InstrumentExchange

**File:** `backend/ingestion/.../persistence/entity/InstrumentExchange.kt`
**Table:** `ingestion.instrument_exchanges`

| Field | Kotlin Type | Column |
|-------|------------|--------|
| id | Long | id |
| instrument | Instrument | instrument_id (FK) |
| exchange | Exchange | exchange_id (FK) |
| localTicker | String? | local_ticker |
| isPrimary | Boolean | is_primary (default false) |

**Unique constraint:** `(instrument_id, exchange_id)`

### ProviderRawData

**File:** `backend/ingestion/.../persistence/entity/ProviderRawData.kt`
**Table:** `ingestion.provider_raw_data`

| Field | Kotlin Type | Column |
|-------|------------|--------|
| id | Long | id |
| instrument | Instrument | instrument_id (FK) |
| provider | String | provider |
| dataType | String | data_type |
| rawPayload | String | raw_payload (JSONB) |
| payloadHash | String? | payload_hash |
| fetchedAt | OffsetDateTime | fetched_at |

**Unique constraint:** `(instrument_id, provider, data_type)`

### ProviderConfig

**File:** `backend/ingestion/.../persistence/entity/ProviderConfig.kt`
**Table:** `ingestion.provider_config`

| Field | Kotlin Type | Column |
|-------|------------|--------|
| id | Int | id |
| providerName | String | provider_name (unique) |
| enabled | Boolean | enabled (default true) |
| priority | Int | priority (default 0) |
| dailyQuota | Int? | daily_quota |
| requestsUsedToday | Int | requests_used_today (default 0) |
| lastQuotaReset | LocalDate? | last_quota_reset |
| configJson | String? | config_json (JSONB) |

### IngestionRun (Ingestion Service)

**File:** `backend/ingestion/.../persistence/entity/IngestionRun.kt`
**Table:** `ingestion.ingestion_runs`

| Field | Kotlin Type | Column |
|-------|------------|--------|
| id | Long | id |
| runType | RunType | run_type |
| startedAt | OffsetDateTime | started_at |
| completedAt | OffsetDateTime? | completed_at |
| status | RunStatus | status (default RUNNING) |
| triggerSource | String? | trigger_source |

**Enum -- RunType:** `SCHEDULED`, `MANUAL`
**Enum -- RunStatus:** `RUNNING`, `COMPLETED`, `FAILED`, `PARTIAL`

### IngestionStep (Ingestion Service)

**File:** `backend/ingestion/.../persistence/entity/IngestionStep.kt`
**Table:** `ingestion.ingestion_steps`

| Field | Kotlin Type | Column |
|-------|------------|--------|
| id | Long | id |
| run | IngestionRun | run_id (FK) |
| stepName | StepName | step_name |
| startedAt | OffsetDateTime | started_at |
| completedAt | OffsetDateTime? | completed_at |
| status | StepStatus | status (default RUNNING) |
| recordsProcessed | Int | records_processed (default 0) |
| recordsCreated | Int | records_created (default 0) |
| recordsUpdated | Int | records_updated (default 0) |
| recordsFailed | Int | records_failed (default 0) |
| metadata | String? | metadata (JSONB) |

**Enum -- StepName:** `EXCHANGE_SYNC`, `UNIVERSE_SYNC`, `RAW_DATA_FETCH`
**Enum -- StepStatus:** `RUNNING`, `COMPLETED`, `FAILED`, `SKIPPED`

### IngestionError (Ingestion Service)

**File:** `backend/ingestion/.../persistence/entity/IngestionError.kt`
**Table:** `ingestion.ingestion_errors`

| Field | Kotlin Type | Column |
|-------|------------|--------|
| id | Long | id |
| step | IngestionStep | step_id (FK) |
| errorType | ErrorType | error_type |
| errorCode | String? | error_code |
| errorMessage | String? | error_message |
| context | String? | context (JSONB) |
| createdAt | OffsetDateTime | created_at |

**Enum -- ErrorType:** `API_ERROR`, `PARSE_ERROR`, `DB_ERROR`, `RATE_LIMIT`, `VALIDATION_ERROR`, `DUPLICATE_ISIN`, `NOT_FOUND`
