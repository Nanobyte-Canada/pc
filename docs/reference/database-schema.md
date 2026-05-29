# Database Schema Reference

Complete database schema reference for AI coding agents. All column definitions queried directly from the live PostgreSQL 16 database.

## Overview

- **Database**: PostgreSQL 16 (Alpine image)
- **Schema**: `public`
- **Tables**: 50
- **Views**: 1 (`v_aggregated_positions`)
- **Indexes**: 218 (including primary keys)
- **Foreign Keys**: 52
- **Migration tool**: Flyway (65 applied migrations, V1 through V73, gaps at V4, V5, V19, V20, V70, V71)
- **Hibernate DDL mode**: `validate` (schema managed exclusively by Flyway)
- **Migration files**: `backend/portfolio/src/main/resources/db/migration/V{N}__{description}.sql`

---

## Table Reference

### 1. Authentication Domain (9 tables)

#### users

User accounts. Central identity table referenced by most other domains.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| email | varchar(255) | NO | |
| email_verified | boolean | NO | false |
| email_verified_at | timestamptz | YES | |
| password_hash | varchar(255) | YES | |
| name | varchar(255) | YES | |
| avatar_url | varchar(500) | YES | |
| status | varchar(20) | NO | 'ACTIVE' |
| failed_login_attempts | integer | NO | 0 |
| locked_until | timestamptz | YES | |
| last_login_at | timestamptz | YES | |
| last_login_ip | varchar(45) | YES | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `email`
- **Indexes**: `idx_users_email`, `idx_users_email_verified` (partial: email_verified=false), `idx_users_status`
- **Notes**: `password_hash` is nullable because OAuth-only users have no password. Account lockout after 5 failed attempts (30min via `locked_until`). **V72:** Dropped `snaptrade_user_id` and `snaptrade_user_secret_encrypted` columns (SnapTrade replaced by broker-gateway).

#### roles

Authorization roles (ROLE_USER, ROLE_ADMIN).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| name | varchar(50) | NO | |
| description | varchar(255) | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `name`

#### user_roles

Join table linking users to roles.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| role_id | bigint | NO | |
| granted_by | bigint | YES | |
| granted_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(user_id, role_id)`
- **FKs**: `user_id -> users.id`, `role_id -> roles.id`, `granted_by -> users.id`
- **Indexes**: `idx_user_roles_user`, `idx_user_roles_role`

#### user_identities

OAuth provider identities (Google, etc.) linked to users.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| provider | varchar(50) | NO | |
| provider_user_id | varchar(255) | NO | |
| provider_email | varchar(255) | YES | |
| provider_name | varchar(255) | YES | |
| provider_avatar_url | varchar(500) | YES | |
| access_token_encrypted | varchar(1000) | YES | |
| refresh_token_encrypted | varchar(1000) | YES | |
| token_expires_at | timestamptz | YES | |
| raw_profile | jsonb | YES | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(provider, provider_user_id)`
- **FK**: `user_id -> users.id`
- **Indexes**: `idx_user_identities_provider`, `idx_user_identities_user`

#### refresh_tokens

JWT refresh tokens for session management.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| token_hash | varchar(64) | NO | |
| device_info | varchar(255) | YES | |
| ip_address | varchar(45) | YES | |
| expires_at | timestamptz | NO | |
| revoked_at | timestamptz | YES | |
| revoked_reason | varchar(100) | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `token_hash`
- **FK**: `user_id -> users.id`
- **Indexes**: `idx_refresh_tokens_hash`, `idx_refresh_tokens_user`, `idx_refresh_tokens_expires` (partial: revoked_at IS NULL)

#### email_verification_tokens

Tokens for verifying email addresses or email changes.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| token_hash | varchar(64) | NO | |
| new_email | varchar(255) | YES | |
| expires_at | timestamptz | NO | |
| used_at | timestamptz | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `token_hash`
- **FK**: `user_id -> users.id`
- **Indexes**: `idx_email_verification_tokens_hash`, `idx_email_verification_tokens_user`

#### password_reset_tokens

Tokens for password reset flows.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| token_hash | varchar(64) | NO | |
| expires_at | timestamptz | NO | |
| used_at | timestamptz | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `token_hash`
- **FK**: `user_id -> users.id`
- **Indexes**: `idx_password_reset_tokens_hash`, `idx_password_reset_tokens_user`

#### oauth_states

CSRF state tokens for OAuth2 authorization flows.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| state_hash | varchar(64) | NO | |
| provider | varchar(50) | NO | |
| redirect_uri | varchar(500) | YES | |
| code_verifier | varchar(128) | YES | |
| expires_at | timestamptz | NO | |
| used_at | timestamptz | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `state_hash`
- **Indexes**: `idx_oauth_states_hash`, `idx_oauth_states_expires`

#### audit_log

Security audit trail for login attempts, password changes, etc.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | YES | |
| event_type | varchar(50) | NO | |
| event_subtype | varchar(50) | YES | |
| ip_address | varchar(45) | YES | |
| user_agent | varchar(500) | YES | |
| resource_type | varchar(50) | YES | |
| resource_id | varchar(100) | YES | |
| details | jsonb | YES | |
| success | boolean | NO | true |
| error_message | varchar(500) | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **FK**: `user_id -> users.id`
- **Indexes**: `idx_audit_log_user`, `idx_audit_log_event`, `idx_audit_log_resource` (composite: resource_type, resource_id), `idx_audit_log_created`

---

### 2. Core Data Domain (2 tables + legacy)

**V68 Note:** Most core data tables (`stocks`, `etfs`, `etf_holdings`, GICS hierarchy, `data_sources`, `ingestion_batches`, `ingestion_runs/steps/errors`) were dropped in V68. The portfolio app now reads instrument data from the `ingestion` schema (see Ingestion Schema section). The `countries` and `regions` tables remain in the public schema for lookups.

#### stocks (DROPPED IN V68)

**Status:** This table was dropped in V68. Instrument data now lives in `ingestion.instruments`.

All equities and ETFs as ticker records. ETFs have a corresponding row in the `etfs` table.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| ticker | varchar(20) | NO | |
| name | varchar(255) | NO | |
| isin | varchar(12) | YES | |
| cusip | varchar(9) | YES | |
| sedol | varchar(7) | YES | |
| currency | varchar(3) | NO | 'USD' |
| country | varchar(3) | NO | 'USA' |
| status | varchar(20) | NO | 'ACTIVE' |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |
| exchange_code | varchar(20) | YES | |
| is_active | boolean | YES | true |
| source_last_seen_at | timestamptz | YES | |
| raw_eodhd_payload | jsonb | YES | |
| av_raw_payload | jsonb | YES | |
| av_ingestion_status | varchar(20) | YES | 'PENDING' |
| av_ingestion_last_attempt_at | timestamptz | YES | |
| av_ingestion_last_success_at | timestamptz | YES | |
| av_ingestion_retry_count | integer | YES | 0 |
| av_ingestion_error_code | varchar(50) | YES | |
| av_ingestion_error_message | varchar(500) | YES | |
| gics_sector_code | varchar(10) | YES | |
| gics_industry_group_code | varchar(10) | YES | |

- **PK**: `id`
- **Unique**: `ticker`, `isin`, `cusip`
- **Indexes**: `idx_stocks_ticker`, `idx_stocks_isin` (partial), `idx_stocks_cusip` (partial), `idx_stocks_status`, `idx_stocks_is_active` (partial), `idx_stocks_source_last_seen`, `idx_stocks_av_ingestion_status`
- **Size**: 30 MB, ~20,978 rows
- **Notes**: `gics_sector_code` and `gics_industry_group_code` are denormalized string references (no FK -- dropped in V55). The `av_*` columns track AlphaVantage enrichment pipeline status.

#### etfs (DROPPED IN V68)

**Status:** This table was dropped in V68. ETF data now lives in `ingestion.instruments` and `ingestion.provider_raw_data`.

ETF-specific metadata. Each ETF also has a row in `stocks`.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| symbol | varchar(20) | NO | |
| name | varchar(255) | NO | |
| isin | varchar(12) | YES | |
| cusip | varchar(9) | YES | |
| issuer | varchar(100) | YES | |
| currency | varchar(3) | NO | 'USD' |
| domicile | varchar(3) | NO | 'USA' |
| inception_date | date | YES | |
| asset_class | varchar(50) | YES | |
| status | varchar(20) | NO | 'ACTIVE' |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |
| is_active | boolean | YES | true |
| source_last_seen_at | timestamptz | YES | |
| etfcom_fund_id | integer | YES | |
| etfcom_asset_class | varchar(50) | YES | |
| etfcom_enrichment_status | varchar(20) | YES | 'PENDING' |
| etfcom_last_attempt_at | timestamptz | YES | |
| etfcom_last_success_at | timestamptz | YES | |
| etfcom_retry_count | integer | YES | 0 |
| etfcom_error_code | varchar(50) | YES | |
| etfcom_error_message | text | YES | |
| etfcom_raw_payload | jsonb | YES | |

- **PK**: `id`
- **Unique**: `symbol`, `isin`, `cusip`
- **Indexes**: `idx_etfs_symbol`, `idx_etfs_status`, `idx_etfs_is_active` (partial), `idx_etfs_issuer` (partial), `idx_etfs_asset_class` (partial), `idx_etfs_source_last_seen`, `idx_etfs_etfcom_fund_id`, `idx_etfs_etfcom_enrichment_status`
- **Size**: 67 MB, ~5,200 rows (largest table -- raw JSON payloads inflate size)
- **Notes**: The `etfcom_*` columns track ETF.com enrichment pipeline status. `etfcom_raw_payload` stores the full ETF.com API response.

#### etf_holdings (DROPPED IN V68)

**Status:** This table was dropped in V68. ETF holdings data is now extracted from `ingestion.provider_raw_data` JSONB payloads.

Links ETFs to their underlying stock/ETF holdings. Core to the look-through analysis feature.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| etf_id | bigint | NO | |
| stock_id | bigint | YES | |
| as_of_date | date | NO | |
| weight | numeric | YES | |
| shares | numeric | YES | |
| market_value | numeric | YES | |
| ingestion_batch_id | bigint | YES | |
| created_at | timestamptz | NO | now() |
| raw_ticker | varchar(50) | YES | |
| raw_name | varchar(255) | YES | |
| raw_isin | varchar(20) | YES | |
| raw_cusip | varchar(20) | YES | |
| resolution_status | varchar(20) | YES | 'RESOLVED' |
| holding_type | varchar(20) | YES | 'STOCK' |
| held_etf_id | bigint | YES | |
| rank | integer | YES | |
| source_section | varchar(20) | YES | 'EODHD' |
| raw_country | varchar(50) | YES | |
| is_valid_symbol | boolean | YES | |
| data_source | varchar(20) | YES | 'EODHD' |
| av_weight | numeric | YES | |
| av_last_updated_at | timestamptz | YES | |
| etfcom_weight | numeric | YES | |
| etfcom_last_updated_at | timestamptz | YES | |

- **PK**: `id`
- **Unique**: `(etf_id, stock_id, as_of_date)`
- **FKs**: `etf_id -> etfs.id`, `stock_id -> stocks.id`, `held_etf_id -> etfs.id`, `ingestion_batch_id -> ingestion_batches.id`
- **Indexes**: `idx_etf_holdings_etf`, `idx_etf_holdings_stock`, `idx_etf_holdings_date`, `idx_etf_holdings_etf_date`, `idx_etf_holdings_batch` (partial), `idx_etf_holdings_held_etf` (partial), `idx_etf_holdings_source`, `idx_etf_holdings_data_source` (composite: etf_id, data_source, as_of_date), `idx_etf_holdings_unresolved_new` (partial: resolution_status='UNRESOLVED')
- **Notes**: `held_etf_id` is used when a holding is itself an ETF (fund-of-funds). Multiple data sources (EODHD, AlphaVantage, ETF.com) can provide weights stored in separate columns. Used by `LookThroughService` -- see [backend-services.md](backend-services.md).

#### countries

Country reference data.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| code | varchar(3) | NO | |
| name | varchar(100) | NO | |
| region_id | bigint | NO | |
| alpha2_code | varchar(2) | YES | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `code`
- **FK**: `region_id -> regions.id`
- **Indexes**: `idx_countries_code`, `idx_countries_region_id`

#### regions

Geographic region reference data.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| code | varchar(20) | NO | |
| name | varchar(100) | NO | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `code`

#### data_sources (DROPPED IN V68)

**Status:** This table was dropped in V68. Provider configuration now managed in `ingestion.provider_config`.

Registry of data providers (EODHD, AlphaVantage, etc.).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| code | varchar(50) | NO | |
| name | varchar(100) | NO | |
| description | text | YES | |
| is_active | boolean | NO | true |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `code`

#### ingestion_batches (DROPPED IN V68)

**Status:** This table was dropped in V68. Ingestion tracking now managed in `ingestion.ingestion_runs/steps/errors`.

Tracks batches of data imported from providers.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| source_id | bigint | NO | |
| batch_date | date | NO | |
| ingested_at | timestamptz | NO | now() |
| record_count | integer | YES | |
| status | varchar(20) | NO | 'COMPLETED' |
| metadata | jsonb | YES | |

- **PK**: `id`
- **FK**: `source_id -> data_sources.id`
- **Indexes**: `idx_ingestion_batches_source`, `idx_ingestion_batches_date`, `idx_ingestion_batches_status`

#### gics_sectors (DROPPED IN V68)

**Status:** This table and the entire GICS hierarchy (`gics_industry_groups`, `gics_industries`, `gics_sub_industries`, `gics_sector_aliases`, `gics_sub_industry_aliases`) were dropped in V68. GICS data is now embedded in `ingestion.provider_raw_data` JSONB payloads.

GICS sector hierarchy -- top level (11 sectors: Technology, Healthcare, etc.).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| code | varchar(2) | NO | |
| name | varchar(100) | NO | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `code`
- **Indexes**: `idx_gics_sectors_code`

#### gics_industry_groups

GICS industry group level (25 groups).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| code | varchar(4) | NO | |
| name | varchar(100) | NO | |
| sector_id | bigint | NO | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `code`
- **FK**: `sector_id -> gics_sectors.id`
- **Indexes**: `idx_gics_industry_groups_code`, `idx_gics_industry_groups_sector`

#### gics_industries

GICS industry level (75 industries).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| code | varchar(6) | NO | |
| name | varchar(100) | NO | |
| industry_group_id | bigint | NO | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `code`
- **FK**: `industry_group_id -> gics_industry_groups.id`
- **Indexes**: `idx_gics_industries_code`, `idx_gics_industries_group`

#### gics_sub_industries

GICS sub-industry level -- most granular (164 sub-industries).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| code | varchar(8) | NO | |
| name | varchar(150) | NO | |
| industry_id | bigint | NO | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `code`
- **FK**: `industry_id -> gics_industries.id`
- **Indexes**: `idx_gics_sub_industries_code`, `idx_gics_sub_industries_industry`

#### gics_sector_aliases

Maps alternative sector names (from data providers) to canonical GICS sectors.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| alias_value | varchar(100) | NO | |
| gics_sector_id | bigint | NO | |
| source | varchar(30) | NO | 'SEEKING_ALPHA' |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(alias_value, source)`
- **FK**: `gics_sector_id -> gics_sectors.id`
- **Indexes**: `idx_sector_alias_lower` (on lower(alias_value)), `idx_sector_alias_source`

#### gics_sub_industry_aliases

Maps alternative sub-industry codes (from data providers) to canonical GICS sub-industries.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| alias_code | varchar(20) | NO | |
| alias_name | varchar(150) | YES | |
| gics_sub_industry_id | bigint | NO | |
| source | varchar(30) | NO | 'SEEKING_ALPHA' |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(alias_code, source)`
- **FK**: `gics_sub_industry_id -> gics_sub_industries.id`
- **Indexes**: `idx_sub_industry_alias_code`, `idx_sub_industry_alias_source`

---

### 3. Broker Integration Domain (9 tables)

#### brokers

Registry of supported brokerages.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| code | varchar(20) | NO | |
| name | varchar(100) | NO | |
| auth_type | varchar(20) | NO | |
| status | varchar(20) | NO | 'ACTIVE' |
| logo_url | varchar(500) | YES | |
| description | varchar(500) | YES | |
| oauth_config | jsonb | YES | |
| rate_limit_config | jsonb | YES | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `code`

#### broker_connections

Links a user's brokerage account to the system. Central to all brokerage data.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| account_id_external | varchar(100) | YES | |
| account_number | varchar(50) | YES | |
| account_type | varchar(50) | YES | |
| account_name | varchar(100) | YES | |
| status | varchar(20) | NO | 'PENDING' |
| last_positions_fetched_at | timestamptz | YES | |
| positions_count | integer | YES | 0 |
| total_value | numeric | YES | |
| connection_error_code | varchar(50) | YES | |
| connection_error_message | varchar(500) | YES | |
| metadata | jsonb | YES | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |
| last_activities_fetched_at | timestamptz | YES | |
| last_balance_fetched_at | timestamptz | YES | |
| account_number_actual | varchar(50) | YES | |
| account_meta_type | varchar(50) | YES | |
| broker_name | varchar(200) | YES | |
| broker_logo_url | varchar(500) | YES | |
| model_portfolio_id | bigint | YES | |
| model_accuracy | numeric | YES | |
| last_rebalanced_at | timestamp | YES | |
| connection_type | varchar(20) | YES | |
| gateway_connection_id | varchar(36) | YES | |

- **PK**: `id`
- **Unique**: `(user_id, account_id_external)`
- **FKs**: `user_id -> users.id`, `model_portfolio_id -> model_portfolios.id`
- **Indexes**: `idx_broker_connections_user`, `idx_broker_connections_status`, `idx_broker_connections_user_active` (partial: status='ACTIVE'), `idx_broker_connections_model`
- **Notes**: `status` values: PENDING, ACTIVE, DISABLED, ERROR. `connection_type` added in V62. `model_portfolio_id` links to the assigned model portfolio for drift calculation. `broker_name` and `broker_logo_url` are denormalized from the `brokers` table for display. **V72:** Added `gateway_connection_id` (references `broker_gateway.connections.id`). **V73:** Dropped `snaptrade_authorization_id` and `broker_id` columns (SnapTrade fully removed).

#### broker_positions

Current and historical security positions for brokerage accounts.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| connection_id | bigint | NO | |
| symbol | varchar(20) | NO | |
| symbol_id_external | varchar(50) | YES | |
| instrument_id | bigint | YES | |
| instrument_type | varchar(20) | YES | |
| security_name | varchar(255) | YES | |
| quantity | numeric | NO | |
| average_cost | numeric | YES | |
| current_price | numeric | YES | |
| current_value | numeric | YES | |
| day_pnl | numeric | YES | |
| total_pnl | numeric | YES | |
| total_pnl_percent | numeric | YES | |
| currency | varchar(3) | YES | 'CAD' |
| as_of_date | date | NO | |
| as_of_timestamp | timestamptz | YES | |
| is_current | boolean | NO | true |
| raw_payload | jsonb | YES | |
| created_at | timestamptz | NO | now() |
| strike_price | numeric | YES | |
| expiration_date | date | YES | |
| option_type | varchar(10) | YES | |
| underlying_symbol | varchar(20) | YES | |

- **PK**: `id`
- **FKs**: `connection_id -> broker_connections.id`
- **Indexes**: `idx_broker_positions_conn`, `idx_broker_positions_symbol`, `idx_broker_positions_date`, `idx_broker_positions_current` (partial: is_current=true)
- **Notes**: `is_current=true` marks the latest snapshot. Previous snapshots are kept for historical analysis. Option fields (`strike_price`, `expiration_date`, `option_type`, `underlying_symbol`) added in V43. **V67:** Dropped `instrument_id` FK column -- positions now identified by `symbol` string field only. Used by `v_aggregated_positions` view.

#### broker_activities

Transaction history (trades, dividends, fees) synced from brokerages.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| connection_id | bigint | NO | |
| external_id | varchar(100) | YES | |
| type | varchar(50) | NO | |
| symbol | varchar(20) | YES | |
| description | text | YES | |
| quantity | numeric | YES | |
| price | numeric | YES | |
| amount | numeric | NO | |
| fee | numeric | YES | |
| currency | varchar(3) | YES | 'CAD' |
| trade_date | date | NO | |
| settlement_date | date | YES | |
| account_name | varchar(100) | YES | |
| option_type | varchar(20) | YES | |
| raw_payload | jsonb | YES | |
| created_at | timestamptz | NO | now() |
| amount_cad | numeric | YES | |
| exchange_rate | numeric | YES | |

- **PK**: `id`
- **Unique**: `(connection_id, external_id)`
- **FK**: `connection_id -> broker_connections.id`
- **Indexes**: `idx_activities_conn_date` (composite, trade_date DESC), `idx_activities_symbol`, `idx_activities_type`
- **Size**: 6.2 MB, ~2,396 rows
- **Notes**: `type` values include BUY, SELL, DIVIDEND, FEE, INTEREST, TRANSFER, etc. `amount_cad` and `exchange_rate` added in V46 for multi-currency normalization. Used by `ActivityIngestionService` and `ReportingService` -- see [backend-services.md](backend-services.md).

#### broker_balance_snapshots

Daily account balance snapshots.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| connection_id | bigint | NO | |
| total_value | numeric | YES | |
| cash | jsonb | YES | |
| currency | varchar(3) | YES | 'CAD' |
| as_of_date | date | NO | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(connection_id, as_of_date)`
- **FK**: `connection_id -> broker_connections.id`
- **Indexes**: `idx_balance_conn_date` (composite, as_of_date DESC)
- **Notes**: `cash` is a JSON object with currency-keyed cash amounts (e.g., `{"CAD": 1500.00, "USD": 200.00}`).

#### position_fetch_log

Audit log for position sync operations. Tracks success/failure and timing.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| connection_id | bigint | NO | |
| user_id | bigint | NO | |
| fetch_type | varchar(20) | NO | |
| status | varchar(20) | NO | |
| started_at | timestamptz | NO | now() |
| completed_at | timestamptz | YES | |
| duration_ms | integer | YES | |
| positions_count | integer | YES | |
| total_value | numeric | YES | |
| error_code | varchar(50) | YES | |
| error_message | text | YES | |
| raw_response | jsonb | YES | |
| retry_count | integer | YES | 0 |
| triggered_by | varchar(50) | YES | |

- **PK**: `id`
- **FKs**: `connection_id -> broker_connections.id`, `user_id -> users.id`
- **Indexes**: `idx_position_fetch_log_conn`, `idx_position_fetch_log_user`, `idx_position_fetch_log_started`, `idx_position_fetch_log_status`, `idx_position_fetch_log_type_status` (composite)

#### snaptrade_status_checks (DROPPED IN V72)

**Status:** This table was dropped in V72 as part of the SnapTrade to broker-gateway migration.

Health check records for the SnapTrade API.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| status | varchar(20) | NO | |
| response_time_ms | integer | YES | |
| version | varchar(50) | YES | |
| error_message | text | YES | |
| raw_response | jsonb | YES | |
| checked_at | timestamptz | NO | now() |

- **PK**: `id`
- **Indexes**: `idx_snaptrade_status_checked_at` (DESC)

#### account_analytics

Pre-computed analytics snapshots per brokerage connection. One snapshot per connection, upserted on each position sync.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| connection_id | bigint | NO | |
| user_id | bigint | NO | |
| sector_exposure | jsonb | YES | |
| geography_exposure | jsonb | YES | |
| risk_profile | jsonb | YES | |
| holdings | jsonb | YES | |
| mer_weighted | numeric | YES | |
| total_value | numeric | YES | |
| coverage_percent | numeric | YES | |
| positions_count | integer | YES | |
| computed_at | timestamptz | NO | now() |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `connection_id`
- **FKs**: `connection_id -> broker_connections.id`, `user_id -> users.id`
- **Indexes**: `idx_account_analytics_connection` (unique), `idx_account_analytics_user`
- **Notes**: JSONB columns store pre-computed analytics: `sector_exposure` is an array of {sector, weight} objects totaling 100% (with "Unknown" bucket), `geography_exposure` is an array of {region, weight} objects, `risk_profile` is a composite risk score object (0-100), `holdings` is a list of look-through holdings. All values are normalized to CAD. Computed by `AccountAnalyticsComputeService` after each position sync -- see [backend-services.md](backend-services.md).

---

### 4. Portfolio Management Domain (10 tables)

#### portfolio_groups

User-defined groups of brokerage accounts for combined analysis and rebalancing.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| name | varchar(100) | NO | |
| description | text | YES | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |
| model_portfolio_id | bigint | YES | |
| benchmark_model_id | bigint | YES | |

- **PK**: `id`
- **Unique**: `(user_id, name)`
- **FKs**: `user_id -> users.id`, `model_portfolio_id -> model_portfolios.id`, `benchmark_model_id -> model_portfolios.id`
- **Indexes**: `idx_portfolio_groups_user`
- **Notes**: `model_portfolio_id` is the target allocation model for rebalancing. `benchmark_model_id` is the comparison benchmark for performance analysis.

#### portfolio_targets

Per-symbol target allocation percentages within a portfolio group.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| group_id | bigint | NO | |
| symbol | varchar(20) | NO | |
| target_percent | numeric | NO | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(group_id, symbol)`
- **FK**: `group_id -> portfolio_groups.id`
- **Indexes**: `idx_portfolio_targets_group`

#### portfolio_group_accounts

Join table linking brokerage accounts to portfolio groups.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| group_id | bigint | NO | |
| connection_id | bigint | NO | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(group_id, connection_id)`
- **FKs**: `group_id -> portfolio_groups.id`, `connection_id -> broker_connections.id`
- **Indexes**: `idx_group_accounts_group`, `idx_group_accounts_conn`

#### portfolio_group_settings

Configuration settings for each portfolio group.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| group_id | bigint | NO | |
| sell_to_rebalance | boolean | NO | false |
| keep_currencies_separate | boolean | NO | false |
| prevent_non_tradable_trades | boolean | NO | false |
| notify_new_assets | boolean | NO | true |
| retain_cash_for_exchange | boolean | NO | false |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |
| rebalance_frequency | varchar(20) | NO | 'MANUAL' |
| accuracy_threshold | numeric | NO | 90.00 |
| auto_execute | boolean | NO | false |
| last_rebalanced_at | timestamptz | YES | |
| next_rebalance_date | date | YES | |

- **PK**: `id`
- **Unique**: `group_id` (one settings row per group)
- **FK**: `group_id -> portfolio_groups.id`
- **Notes**: `rebalance_frequency` values: MANUAL, DAILY, WEEKLY, MONTHLY, QUARTERLY. `accuracy_threshold` is the minimum portfolio accuracy before triggering a drift alert. `auto_execute` controls whether rebalance orders are placed automatically. Added automation columns in V59.

#### portfolio_excluded_assets

Symbols to exclude from rebalancing within a portfolio group.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| group_id | bigint | NO | |
| symbol | varchar(20) | NO | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(group_id, symbol)`
- **FK**: `group_id -> portfolio_groups.id`
- **Indexes**: `idx_excluded_assets_group`

#### portfolio_snapshots

Daily snapshots of portfolio group value and composition for time-series analysis.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| group_id | bigint | NO | |
| snapshot_date | date | NO | |
| total_value | numeric | NO | |
| positions | jsonb | NO | |
| cash | jsonb | NO | |
| accuracy | numeric | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(group_id, snapshot_date)`
- **FK**: `group_id -> portfolio_groups.id`
- **Indexes**: `idx_portfolio_snapshots_group_id`, `idx_portfolio_snapshots_date`
- **Notes**: `positions` stores JSON array of position data at snapshot time. `cash` stores currency-keyed cash amounts. Used by `PerformanceCalculationService` for time-weighted return calculation -- see [backend-services.md](backend-services.md).

#### portfolio_cash_flows

Cash flow events (deposits/withdrawals) for portfolio performance calculation.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| group_id | bigint | NO | |
| flow_date | date | NO | |
| amount | numeric | NO | |
| flow_type | varchar(20) | NO | |
| currency | varchar(3) | NO | 'CAD' |
| source | varchar(50) | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **FK**: `group_id -> portfolio_groups.id`
- **Indexes**: `idx_portfolio_cash_flows_group_id`, `idx_portfolio_cash_flows_date`
- **Notes**: `flow_type` values: DEPOSIT, WITHDRAWAL. Used for accurate time-weighted return calculation by excluding cash flow effects.

#### model_portfolios

Template portfolios defining target allocation strategies.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| name | varchar(100) | NO | |
| description | text | YES | |
| risk_level | varchar(20) | NO | |
| is_system | boolean | NO | false |
| user_id | bigint | YES | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **FK**: `user_id -> users.id`
- **Indexes**: `idx_model_portfolios_user_id`
- **Notes**: `is_system=true` for built-in templates, `false` for user-created. `user_id` is null for system models. `risk_level` values: CONSERVATIVE, MODERATE, BALANCED, GROWTH, AGGRESSIVE.

#### model_portfolio_allocations

Individual ETF/stock allocations within a model portfolio.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| model_portfolio_id | bigint | NO | |
| symbol | varchar(20) | NO | |
| target_percent | numeric | NO | |
| asset_class | varchar(50) | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **FK**: `model_portfolio_id -> model_portfolios.id`
- **Indexes**: `idx_model_alloc_portfolio_id`
- **Notes**: `target_percent` values for all allocations in a model should sum to 100. `asset_class` is informational (e.g., "Equity", "Fixed Income", "Alternatives").

#### benchmark_returns

Daily price and return data for benchmark symbols (e.g., SPY, XIU.TO).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| symbol | varchar(20) | NO | |
| return_date | date | NO | |
| close_price | numeric | NO | |
| daily_return | numeric | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(symbol, return_date)`
- **Indexes**: `idx_benchmark_returns_symbol`, `idx_benchmark_returns_date`
- **Notes**: Used by `BenchmarkService` for performance comparison -- see [backend-services.md](backend-services.md).

---

### 5. Trading & Rebalancing Domain (2 tables)

#### trade_orders

Trade orders generated by rebalancing or placed manually.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| group_id | bigint | YES | |
| connection_id | bigint | NO | |
| batch_id | uuid | YES | |
| symbol | varchar(20) | NO | |
| action | varchar(4) | NO | |
| order_type | varchar(10) | NO | 'MARKET' |
| time_in_force | varchar(3) | NO | 'DAY' |
| requested_units | numeric | NO | |
| requested_price | numeric | NO | |
| requested_amount | numeric | NO | |
| limit_price | numeric | YES | |
| filled_units | numeric | YES | |
| filled_price | numeric | YES | |
| filled_amount | numeric | YES | |
| currency | varchar(3) | NO | 'CAD' |
| status | varchar(20) | NO | 'PENDING' |
| broker_order_id | varchar(255) | YES | |
| account_id_external | varchar(100) | YES | |
| error_message | text | YES | |
| error_code | varchar(50) | YES | |
| submitted_at | timestamptz | YES | |
| filled_at | timestamptz | YES | |
| cancelled_at | timestamptz | YES | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **FKs**: `user_id -> users.id`, `group_id -> portfolio_groups.id` (nullable), `connection_id -> broker_connections.id`
- **Indexes**: `idx_trade_orders_user_id`, `idx_trade_orders_group_id`, `idx_trade_orders_connection_id`, `idx_trade_orders_status`, `idx_trade_orders_batch_id`
- **Notes**: `action` values: BUY, SELL. `status` values: PENDING, SUBMITTED, PARTIAL, FILLED, CANCELLED, REJECTED, FAILED. `batch_id` groups orders from the same rebalance event. `group_id` is nullable (V61) to support broker-synced orders that are not associated with a portfolio group. `order_type` values: MARKET, LIMIT. `time_in_force` values: DAY, GTC.

#### rebalance_events

Audit log of rebalancing operations performed on portfolio groups.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| group_id | bigint | NO | |
| trigger_type | varchar(20) | NO | |
| accuracy_before | numeric | YES | |
| accuracy_after | numeric | YES | |
| trades_count | integer | NO | 0 |
| batch_id | uuid | YES | |
| status | varchar(20) | NO | 'COMPLETED' |
| notes | text | YES | |
| created_at | timestamptz | NO | now() |
| connection_id | bigint | YES | |

- **PK**: `id`
- **FKs**: `group_id -> portfolio_groups.id`, `connection_id -> broker_connections.id`
- **Indexes**: `idx_rebalance_events_group_id`, `idx_rebalance_events_connection`
- **Notes**: `trigger_type` values: MANUAL, SCHEDULED, THRESHOLD. `batch_id` links to the corresponding `trade_orders.batch_id`. `connection_id` added in V59 for per-account rebalancing.

---

### 6. Dashboard & Notifications Domain (3 tables)

#### dashboard_preferences

Per-user widget visibility and layout preferences for the dashboard.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| context_type | varchar(20) | NO | 'DASHBOARD' |
| context_id | bigint | YES | |
| widget_key | varchar(50) | NO | |
| is_visible | boolean | NO | true |
| sort_order | integer | NO | 0 |
| column_span | integer | NO | 1 |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(user_id, context_type, COALESCE(context_id, 0), widget_key)`
- **FK**: `user_id -> users.id`
- **Indexes**: `idx_dashboard_prefs_user`, `idx_dashboard_prefs_context` (composite: user_id, context_type, context_id)
- **Notes**: `context_type` supports different dashboard contexts (e.g., DASHBOARD, PORTFOLIO_GROUP). `context_id` is null for the main dashboard. Widget keys updated in V63 and V64. Dashboard redesigned in V65.

#### notifications

In-app notifications sent to users.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| type | varchar(30) | NO | |
| title | varchar(200) | NO | |
| message | text | NO | |
| link | varchar(500) | YES | |
| is_read | boolean | NO | false |
| metadata | jsonb | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **FK**: `user_id -> users.id`
- **Indexes**: `idx_notifications_user_id`, `idx_notifications_created_at`, `idx_notifications_user_unread` (partial: is_read=false)
- **Notes**: `type` values: DRIFT_ALERT, ORDER_FILLED, SYNC_FAILURE, NEW_ASSET, REBALANCE_REMINDER. `link` is an optional deep-link URL.

#### notification_preferences

Per-user notification channel and type preferences.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| user_id | bigint | NO | |
| email_enabled | boolean | NO | true |
| in_app_enabled | boolean | NO | true |
| drift_alerts | boolean | NO | true |
| drift_threshold | numeric | NO | 90.00 |
| order_alerts | boolean | NO | true |
| sync_failure_alerts | boolean | NO | true |
| new_asset_alerts | boolean | NO | true |
| rebalance_reminder | boolean | NO | false |
| reminder_frequency | varchar(20) | YES | 'WEEKLY' |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `user_id` (one row per user)
- **FK**: `user_id -> users.id`
- **Notes**: `drift_threshold` is the accuracy percentage below which drift alerts fire. `reminder_frequency` values: DAILY, WEEKLY, MONTHLY.

---

### 7. Ingestion Tracking Domain (DROPPED IN V68)

**Status:** All public schema ingestion tracking tables (`ingestion_runs`, `ingestion_steps`, `ingestion_errors`) were dropped in V68. Ingestion is now managed by the separate `ingestion-service` microservice with its own `ingestion` schema.

#### ingestion_runs (DROPPED IN V68)

Top-level tracking for data ingestion pipeline executions.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| run_type | varchar(20) | NO | |
| started_at | timestamptz | NO | now() |
| completed_at | timestamptz | YES | |
| status | varchar(20) | NO | 'RUNNING' |
| trigger_source | varchar(50) | YES | |

- **PK**: `id`
- **Indexes**: `idx_ingestion_runs_type`, `idx_ingestion_runs_status`, `idx_ingestion_runs_started` (DESC)
- **Notes**: `run_type` values: ETF_IMPORT, STOCK_IMPORT, HOLDINGS_IMPORT, ENRICHMENT. `status` values: RUNNING, COMPLETED, FAILED. `trigger_source` values: SCHEDULER, MANUAL, API.

#### ingestion_steps

Individual steps within an ingestion run.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| run_id | bigint | NO | |
| step_name | varchar(50) | NO | |
| started_at | timestamptz | NO | now() |
| completed_at | timestamptz | YES | |
| status | varchar(20) | NO | 'RUNNING' |
| records_processed | integer | YES | 0 |
| records_created | integer | YES | 0 |
| records_updated | integer | YES | 0 |
| records_failed | integer | YES | 0 |
| metadata | jsonb | YES | |

- **PK**: `id`
- **FK**: `run_id -> ingestion_runs.id`
- **Indexes**: `idx_ingestion_steps_run`, `idx_ingestion_steps_status`

#### ingestion_errors

Individual error records within ingestion steps.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| step_id | bigint | NO | |
| error_type | varchar(50) | NO | |
| error_code | varchar(50) | YES | |
| error_message | text | YES | |
| context | jsonb | YES | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **FK**: `step_id -> ingestion_steps.id`
- **Indexes**: `idx_ingestion_errors_step`, `idx_ingestion_errors_type`, `idx_ingestion_errors_created` (DESC)
- **Size**: 15 MB, ~58,814 rows (third largest table)
- **Notes**: `error_type` values include: DUPLICATE_ISIN, PARSE_ERROR, API_ERROR, RESOLUTION_FAILED, etc. `context` stores the raw data that caused the error.

---

### 8. Sector Allocations Domain (DROPPED IN V68)

#### etf_sector_allocations_factset (DROPPED IN V68)

**Status:** This table was dropped in V68. Sector allocation data is now embedded in `ingestion.provider_raw_data` JSONB payloads.

ETF sector allocations from FactSet data (via ETF.com enrichment).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigint | NO | sequence |
| etf_id | bigint | NO | |
| sector_name | varchar(100) | NO | |
| weight | numeric | YES | |
| as_of_date | date | NO | |
| created_at | timestamptz | NO | now() |

- **PK**: `id`
- **Unique**: `(etf_id, sector_name, as_of_date)`
- **FK**: `etf_id -> etfs.id`
- **Indexes**: `idx_etf_sector_factset_etf_id`
- **Notes**: Normalized row-per-sector design. Cached via Redis by `CachedLookupService` -- see [backend-services.md](backend-services.md).

---

### 9. System Domain (1 table)

#### flyway_schema_history

Flyway migration tracking table (managed automatically by Flyway).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| installed_rank | integer | NO | |
| version | varchar(50) | YES | |
| description | varchar(200) | NO | |
| type | varchar(20) | NO | |
| script | varchar(1000) | NO | |
| checksum | integer | YES | |
| installed_by | varchar(100) | NO | |
| installed_on | timestamp | NO | now() |
| execution_time | integer | NO | |
| success | boolean | NO | |

- **PK**: `installed_rank`
- **Indexes**: `flyway_schema_history_s_idx` (on success)
- **Notes**: Do not modify this table manually. Flyway uses it to track which migrations have been applied.

---

## Database View

### v_aggregated_positions

Aggregates current broker positions across all accounts per user, grouping by symbol.

```sql
SELECT
    bp.symbol,
    bp.security_name,
    bp.instrument_type,
    bp.currency,
    sum(bp.quantity) AS total_quantity,
    sum(bp.current_value) AS total_value,
    avg(bp.average_cost) AS weighted_avg_cost,
    sum(bp.total_pnl) AS total_pnl,
    CASE
        WHEN sum(bp.current_value - COALESCE(bp.total_pnl, 0)) > 0
        THEN (sum(bp.total_pnl) / sum(bp.current_value - COALESCE(bp.total_pnl, 0))) * 100
        ELSE 0
    END AS total_pnl_percent,
    count(DISTINCT bc.id) AS account_count,
    count(DISTINCT bc.gateway_connection_id) AS broker_count,
    bc.user_id
FROM broker_positions bp
JOIN broker_connections bc ON bp.connection_id = bc.id
WHERE bp.is_current = true AND bc.status = 'ACTIVE'
GROUP BY bp.symbol, bp.security_name, bp.instrument_type, bp.currency, bc.user_id;
```

**Columns returned**: symbol, security_name, instrument_type, currency, total_quantity, total_value, weighted_avg_cost, total_pnl, total_pnl_percent, account_count, broker_count (now counts distinct gateway_connection_id), user_id

**Filters**: Only current positions (`is_current=true`) from active connections (`status='ACTIVE'`).

---

## Foreign Key Relationships (50 total)

Listed as `source_table.column -> target_table.column`:

**From authentication tables:**
1. `audit_log.user_id -> users.id`
2. `email_verification_tokens.user_id -> users.id`
3. `password_reset_tokens.user_id -> users.id`
4. `refresh_tokens.user_id -> users.id`
5. `user_identities.user_id -> users.id`
6. `user_roles.user_id -> users.id`
7. `user_roles.role_id -> roles.id`
8. `user_roles.granted_by -> users.id`

**From core data tables:**
9. `countries.region_id -> regions.id`
10. `etf_holdings.etf_id -> etfs.id`
11. `etf_holdings.stock_id -> stocks.id`
12. `etf_holdings.held_etf_id -> etfs.id`
13. `etf_holdings.ingestion_batch_id -> ingestion_batches.id`
14. `etf_sector_allocations_factset.etf_id -> etfs.id`
15. `gics_industries.industry_group_id -> gics_industry_groups.id`
16. `gics_industry_groups.sector_id -> gics_sectors.id`
17. `gics_sector_aliases.gics_sector_id -> gics_sectors.id`
18. `gics_sub_industries.industry_id -> gics_industries.id`
19. `gics_sub_industry_aliases.gics_sub_industry_id -> gics_sub_industries.id`
20. `ingestion_batches.source_id -> data_sources.id`

**From broker tables:**
21. `broker_connections.user_id -> users.id`
22. `broker_connections.model_portfolio_id -> model_portfolios.id`
24. `broker_positions.connection_id -> broker_connections.id`
25. `broker_positions.instrument_id -> stocks.id`
26. `broker_activities.connection_id -> broker_connections.id`
27. `broker_balance_snapshots.connection_id -> broker_connections.id`
28. `position_fetch_log.connection_id -> broker_connections.id`
29. `position_fetch_log.user_id -> users.id`
30. `account_analytics.connection_id -> broker_connections.id`
31. `account_analytics.user_id -> users.id`

**From portfolio management tables:**
32. `portfolio_groups.user_id -> users.id`
33. `portfolio_groups.model_portfolio_id -> model_portfolios.id`
34. `portfolio_groups.benchmark_model_id -> model_portfolios.id`
35. `portfolio_targets.group_id -> portfolio_groups.id`
36. `portfolio_group_accounts.group_id -> portfolio_groups.id`
37. `portfolio_group_accounts.connection_id -> broker_connections.id`
38. `portfolio_group_settings.group_id -> portfolio_groups.id`
39. `portfolio_excluded_assets.group_id -> portfolio_groups.id`
40. `portfolio_snapshots.group_id -> portfolio_groups.id`
41. `portfolio_cash_flows.group_id -> portfolio_groups.id`
42. `model_portfolios.user_id -> users.id`
43. `model_portfolio_allocations.model_portfolio_id -> model_portfolios.id`
44. `benchmark_returns` -- no FKs (standalone)

**From trading tables:**
45. `trade_orders.user_id -> users.id`
46. `trade_orders.group_id -> portfolio_groups.id`
47. `trade_orders.connection_id -> broker_connections.id`
48. `rebalance_events.group_id -> portfolio_groups.id`
49. `rebalance_events.connection_id -> broker_connections.id`

**From dashboard/notification tables:**
50. `dashboard_preferences.user_id -> users.id`
51. `notifications.user_id -> users.id`
52. `notification_preferences.user_id -> users.id`

**From ingestion tracking tables:**
53. `ingestion_steps.run_id -> ingestion_runs.id`
54. `ingestion_errors.step_id -> ingestion_steps.id`

---

## Table Sizes

Queried from `pg_total_relation_size` (includes indexes and TOAST data):

| Table | Total Size | Row Estimate |
|-------|-----------|-------------|
| etfs | 67 MB | 5,200 |
| stocks | 30 MB | 20,978 |
| ingestion_errors | 15 MB | 58,814 |
| broker_activities | 6,224 kB | 2,396 |
| refresh_tokens | 416 kB | 442 |
| broker_positions | 368 kB | 795 |
| etf_holdings | 328 kB | 0 (truncated) |
| audit_log | 280 kB | 263 |
| broker_connections | 192 kB | 21 |
| position_fetch_log | 184 kB | 258 |
| ingestion_steps | 144 kB | 46 |
| trade_orders | 112 kB | 17 |
| users | 112 kB | 1 |
| gics_sub_industry_aliases | 112 kB | 164 |
| dashboard_preferences | 104 kB | 15 |
| gics_sub_industries | 104 kB | 164 |
| broker_balance_snapshots | 104 kB | 44 |
| etf_sector_allocations_factset | 88 kB | 4 |
| gics_industry_groups | 72 kB | 25 |
| countries | 72 kB | 53 |
| gics_industries | 72 kB | 75 |
| gics_sector_aliases | 72 kB | 29 |
| gics_sectors | 56 kB | 11 |
| data_sources | 48 kB | 7 |
| brokers | 48 kB | 3 |
| flyway_schema_history | 48 kB | 26 |
| model_portfolios | 48 kB | 0 |
| roles | 40 kB | 2 |
| regions | 40 kB | 6 |

All other tables: 24-40 kB each (empty or near-empty).

---

## Flyway Migration History

65 applied migrations from V1 through V73 (gaps at V4, V5, V19, V20, V70, V71).

| Version | Description | Notes |
|---------|------------|-------|
| V1 | init | Base schema: app_metadata table |
| V2 | core schema | stocks, etfs, etf_holdings, data_sources, ingestion_batches |
| V3 | gics seed data | GICS sector/industry/sub-industry hierarchy seed |
| V6 | ingestion extensions | Extended ingestion tracking columns |
| V7 | instrument identifiers | Added ISIN, CUSIP, SEDOL to stocks |
| V8 | holdings extensions | Extended etf_holdings with raw fields |
| V9 | ingestion observability | ingestion_runs, ingestion_steps, ingestion_errors tables |
| V10 | add duplicate isin error type | Added DUPLICATE_ISIN to error types |
| V11 | add eodhd holdings step | New ingestion step name for EODHD holdings |
| V12 | remove figi and holdings ingestion | Cleanup of unused FIGI fields |
| V13 | seekingalpha enrichment tracking | SA enrichment status columns (later dropped in V21) |
| V14 | fund sector allocations | fund_sector_allocations table (wide-column design) |
| V15 | extended holdings | Additional holding fields (rank, source_section, etc.) |
| V16 | gics aliases | gics_sector_aliases, gics_sub_industry_aliases tables |
| V17 | sa ingestion steps | Seeking Alpha ingestion step names |
| V18 | alpha vantage enrichment | AV enrichment fields on etf_holdings |
| V21 | drop seekingalpha columns | Removed SA-specific columns from stocks |
| V22 | stock enrichment additional fields | Extended stock metadata fields |
| V23 | countries regions tables | countries, regions tables and seed data |
| V24 | mutual fund holdings av fields | AV weight/date fields on etf_holdings |
| V25 | add av ingestion status | av_ingestion_* columns on stocks |
| V26 | add av ingestion step names | New step names for AV pipeline |
| V27 | increase decimal precision | Widened numeric precision on key columns |
| V28 | remove holdings weight constraints | Relaxed NOT NULL on etf_holdings.weight |
| V29 | authentication schema | users, roles, user_roles, refresh_tokens, oauth_states, user_identities, email_verification_tokens, password_reset_tokens, audit_log |
| V30 | broker integration schema | brokers, broker_connections, broker_positions, external_connections, external_connection_tokens, position_fetch_log |
| V31 | snaptrade migration | snaptrade_user_id/secret on users, snaptrade_authorization_id on broker_connections |
| V32 | etfcom enrichment | etfcom_* columns on etfs, etf_sector_allocations_factset table |
| V33 | fix etfcom step name constraint | Constraint fix for ingestion_steps |
| V34 | etf schema cleanup | Removed unused columns from etfs |
| V35 | snaptrade status checks | snaptrade_status_checks table |
| V36 | broker activities | broker_activities table |
| V37 | broker balance snapshots | broker_balance_snapshots table |
| V38 | portfolio groups | portfolio_groups, portfolio_targets, portfolio_group_accounts, portfolio_group_settings, portfolio_excluded_assets tables |
| V39 | trade orders | trade_orders table |
| V40 | notifications | notifications, notification_preferences tables |
| V41 | portfolio snapshots | portfolio_snapshots, benchmark_returns, portfolio_cash_flows tables |
| V42 | unique user account external | Added unique constraint on broker_connections (user_id, account_id_external) |
| V43 | option position fields | strike_price, expiration_date, option_type, underlying_symbol on broker_positions |
| V44 | account meta fields | account_number_actual, account_meta_type on broker_connections |
| V45 | connection broker fields | broker_name, broker_logo_url on broker_connections |
| V46 | activity cad fields | amount_cad, exchange_rate on broker_activities |
| V47 | dashboard preferences | dashboard_preferences table |
| V48 | fix ingestion step constraint | Relaxed step_name uniqueness constraint |
| V49 | stock deduplication remove exchange | Removed exchange-based duplicate stocks |
| V50 | remove mutual funds | Cleanup of mutual fund data |
| V51 | gics alpha vantage aliases | AV-specific GICS alias mappings |
| V52 | truncate and restart | Data reset (truncated ingestion tables) |
| V53 | drop etf enrichment columns | Removed unused enrichment columns from etfs |
| V54 | drop stock enrichment columns | Removed unused enrichment columns from stocks |
| V55 | drop stocks gics fk | Dropped FK from stocks to gics_sub_industries (now uses string codes) |
| V56 | backfill activity types | Normalized activity type values |
| V57 | add gics sector to stocks | Added gics_sector_code, gics_industry_group_code to stocks |
| V58 | model portfolios | model_portfolios, model_portfolio_allocations tables |
| V59 | rebalance automation | rebalance_events table, automation columns on portfolio_group_settings |
| V60 | account model linking | model_portfolio_id, model_accuracy, last_rebalanced_at on broker_connections |
| V61 | order sync support | Made trade_orders.group_id nullable for broker-synced orders |
| V62 | connection type column | connection_type on broker_connections |
| V63 | update dashboard widget keys | Renamed widget keys in dashboard_preferences |
| V64 | merge positions holdings widget | Combined positions + holdings into single widget |
| V65 | dashboard redesign | Updated dashboard widget layout and column spans |
| V66 | drop unused tables | Dropped external_connections, external_connection_tokens, app_metadata, fund_sector_allocations |
| V67 | drop broker positions instrument id fk | Dropped `instrument_id` FK column from `broker_positions` (V67 screener migration prep) |
| V68 | drop legacy screener tables | Dropped all public schema instrument/GICS/ingestion tables (stocks, etfs, etf_holdings, gics_*, data_sources, ingestion_batches, ingestion_runs/steps/errors, etf_sector_allocations_factset). Portfolio app now reads from `ingestion` schema. |
| V69 | account analytics | `account_analytics` table for pre-computed per-connection analytics snapshots (sector exposure, geography exposure, risk profile, holdings, weighted MER). UNIQUE constraint on `connection_id`, INDEX on `user_id`. |
| V72 | snaptrade to gateway migration | Added `gateway_connection_id` column to `broker_connections`. Dropped `snaptrade_status_checks` table. Dropped `snaptrade_user_id` and `snaptrade_user_secret_encrypted` columns from `users`. Part of SnapTrade to broker-gateway migration. |
| V73 | remove snaptrade columns | Dropped `snaptrade_authorization_id` and `broker_id` columns from `broker_connections`. Completes SnapTrade removal. |

**Next migration**: V74 (always check by running `ls backend/portfolio/src/main/resources/db/migration/` to confirm)

---

## Legacy/Unused Tables

All previously identified legacy tables (`external_connections`, `external_connection_tokens`, `app_metadata`, `fund_sector_allocations`) were dropped in V66. The corresponding JPA entity `FundSectorAllocation` and `FundSectorAllocationRepository` were also removed.

---

## Ingestion Schema (separate microservice)

The ingestion service manages its own PostgreSQL schema (`ingestion`) with independent Flyway migrations at `backend/ingestion/src/main/resources/db/migration/`. Both the main backend and ingestion service connect to the same PostgreSQL database but operate on different schemas.

**Schema:** `ingestion`
**Migration files:** `backend/ingestion/src/main/resources/db/migration/V{N}__{description}.sql`
**Current state:** V1 (initial schema)
**Tables:** 8

### ingestion.exchanges

Exchange reference data synced from EODHD.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | serial | NO | sequence |
| code | varchar(10) | NO | (unique) |
| name | varchar(200) | NO | |
| country | varchar(100) | YES | |
| currency | varchar(3) | YES | |
| operating_mic | varchar(10) | YES | |
| is_active | boolean | NO | true |
| created_at | timestamptz | NO | now() |

### ingestion.instruments

Canonical instrument records (one row per instrument globally).

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigserial | NO | sequence |
| ticker | varchar(20) | NO | |
| name | varchar(500) | NO | |
| instrument_type | varchar(20) | NO | |
| isin | varchar(12) | YES | (unique) |
| cusip | varchar(9) | YES | |
| currency | varchar(3) | YES | |
| country | varchar(3) | YES | |
| status | varchar(20) | NO | 'ACTIVE' |
| source_last_seen_at | timestamptz | YES | |
| created_at | timestamptz | NO | now() |
| updated_at | timestamptz | NO | now() |

- **Indexes:** `idx_instruments_ticker`, `idx_instruments_type`, `idx_instruments_status`
- **`instrument_type` values:** STOCK, PREFERRED_STOCK, ETF, MUTUAL_FUND, INDEX, BOND

### ingestion.instrument_exchanges

Many-to-many relationship between instruments and exchanges.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigserial | NO | sequence |
| instrument_id | bigint | NO | FK -> instruments.id |
| exchange_id | int | NO | FK -> exchanges.id |
| local_ticker | varchar(20) | YES | |
| is_primary | boolean | NO | false |

- **Unique:** `(instrument_id, exchange_id)`
- **Indexes:** `idx_ie_instrument`, `idx_ie_exchange`

### ingestion.provider_raw_data

Raw data from providers stored as JSONB. Latest only, overwritten on each fetch.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigserial | NO | sequence |
| instrument_id | bigint | NO | FK -> instruments.id |
| provider | varchar(50) | NO | |
| data_type | varchar(30) | NO | |
| raw_payload | jsonb | NO | |
| payload_hash | varchar(64) | YES | |
| fetched_at | timestamptz | NO | now() |

- **Unique:** `(instrument_id, provider, data_type)`
- **Indexes:** `idx_prd_instrument`, `idx_prd_provider_type`

### ingestion.provider_config

Provider configuration and daily quota tracking.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | serial | NO | sequence |
| provider_name | varchar(50) | NO | (unique) |
| enabled | boolean | NO | true |
| priority | int | NO | 0 |
| daily_quota | int | YES | |
| requests_used_today | int | NO | 0 |
| last_quota_reset | date | YES | |
| config_json | jsonb | YES | |

- **Seed data:** EODHD provider with 100,000 daily quota

### ingestion.ingestion_runs

Top-level tracking for ingestion pipeline executions.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigserial | NO | sequence |
| run_type | varchar(20) | NO | |
| started_at | timestamptz | NO | now() |
| completed_at | timestamptz | YES | |
| status | varchar(20) | NO | 'RUNNING' |
| trigger_source | varchar(100) | YES | |

- **`run_type` values:** SCHEDULED, MANUAL
- **`status` values:** RUNNING, COMPLETED, FAILED, PARTIAL

### ingestion.ingestion_steps

Individual steps within an ingestion run.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigserial | NO | sequence |
| run_id | bigint | NO | FK -> ingestion_runs.id |
| step_name | varchar(50) | NO | |
| started_at | timestamptz | NO | now() |
| completed_at | timestamptz | YES | |
| status | varchar(20) | NO | 'RUNNING' |
| records_processed | int | NO | 0 |
| records_created | int | NO | 0 |
| records_updated | int | NO | 0 |
| records_failed | int | NO | 0 |
| metadata | jsonb | YES | |

- **FK:** `run_id -> ingestion_runs.id (CASCADE)`
- **Index:** `idx_steps_run`
- **`step_name` values:** EXCHANGE_SYNC, UNIVERSE_SYNC, RAW_DATA_FETCH

### ingestion.ingestion_errors

Individual error records within ingestion steps.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | bigserial | NO | sequence |
| step_id | bigint | NO | FK -> ingestion_steps.id |
| error_type | varchar(30) | NO | |
| error_code | varchar(50) | YES | |
| error_message | text | YES | |
| context | jsonb | YES | |
| created_at | timestamptz | NO | now() |

- **FK:** `step_id -> ingestion_steps.id (CASCADE)`
- **Indexes:** `idx_errors_step`, `idx_errors_type`
- **`error_type` values:** API_ERROR, PARSE_ERROR, DB_ERROR, RATE_LIMIT, VALIDATION_ERROR, DUPLICATE_ISIN, NOT_FOUND

---

## Notes for Schema Changes

1. **Always use Flyway migrations** -- never modify Hibernate DDL mode (it is `validate`).
2. **Migration naming**: `V{N}__{description}.sql` (double underscore). Check the highest existing number and increment by 1.
3. **Test after schema changes**: `docker compose exec backend ./gradlew test` (no local JDK).
4. **JPA entity validation**: Hibernate will fail startup if entities do not match the schema. Every column in an entity must exist in the database and vice versa for mapped columns.
5. **Indexes**: Consider adding indexes for columns used in WHERE clauses, JOIN conditions, or ORDER BY. Use partial indexes for boolean filters.
6. **JSONB columns**: Used for flexible/nested data (cash amounts, raw API payloads, notification metadata). Query with PostgreSQL `->` and `->>` operators.

---

## Market Data Schema (`market_data`)

**Service:** market-data-service (port 8082)
**Migration:** `backend/market-data/src/main/resources/db/migration/V1__market_data_schema.sql`

### market_data.underlying_prices

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | BIGINT (IDENTITY) | NO | Primary key |
| ticker | VARCHAR(20) | NO | Symbol |
| price | NUMERIC(12,4) | NO | Price |
| volume | BIGINT | YES | Volume |
| observed_at | TIMESTAMPTZ | NO | Observation timestamp |

### market_data.option_quotes

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | BIGINT (IDENTITY) | NO | Primary key |
| ticker | VARCHAR(20) | NO | Underlying symbol |
| expiry | DATE | NO | Option expiry |
| strike | NUMERIC(12,4) | NO | Strike price |
| option_type | VARCHAR(4) | NO | CALL or PUT |
| bid/ask/mid | NUMERIC(12,4) | YES | Price fields |
| implied_volatility | NUMERIC(10,6) | YES | IV |
| delta/gamma/theta/vega | NUMERIC(10,6) | YES | Greeks |
| observed_at | TIMESTAMPTZ | NO | Observation timestamp |

### market_data.iv_observations

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | BIGINT (IDENTITY) | NO | Primary key |
| ticker | VARCHAR(20) | NO | Symbol |
| atm_iv | NUMERIC(10,6) | NO | ATM implied volatility |
| observed_date | DATE | NO | Date (unique with ticker) |

### market_data.contract_cache

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | BIGINT (IDENTITY) | NO | Primary key |
| symbol | VARCHAR(30) | NO | Symbol |
| con_id | INTEGER | NO | IBKR contract ID |
| sec_type | VARCHAR(10) | NO | STK or OPT |
| expiry | DATE | YES | Option expiry |
| strike | NUMERIC(12,4) | YES | Strike price |
| option_right | VARCHAR(4) | YES | C or P |
| cached_at | TIMESTAMPTZ | NO | Cache timestamp |

---

## Strategy Schema (`strategy`)

**Service:** strategy-service (port 8083)
**Migration:** `backend/strategy/src/main/resources/db/migration/V1__strategy_schema.sql`

### strategy.orders

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | BIGINT (IDENTITY) | NO | Primary key |
| user_id | BIGINT | NO | User reference |
| strategy_type | VARCHAR(30) | NO | Strategy enum name |
| underlying | VARCHAR(20) | NO | Underlying symbol |
| order_type | VARCHAR(20) | NO | LIMIT (default) |
| net_price | NUMERIC(12,4) | YES | Net price |
| quantity | INTEGER | NO | Contract quantity |
| status | VARCHAR(20) | NO | SUBMITTED/FILLED/CANCELLED |
| snaptrade_order_id | VARCHAR(100) | YES | SnapTrade order reference |
| created_at/updated_at | TIMESTAMPTZ | NO | Timestamps |

### strategy.order_legs

Order legs with fill prices. FK to orders(id) CASCADE.

### strategy.positions

Open/closed positions with P&L tracking. FK to orders for entry/exit.

### strategy.wheel_accounts / wheel_configs / wheel_recommendations / wheel_holdings

Wheel writer automation tables for CSP/CC strategy management.

---

## Broker Gateway Schema (`broker_gateway`)

**Service:** broker-gateway-service (port 8084)
**Migration:** `backend/broker-gateway/src/main/resources/db/migration/V1__broker_gateway_schema.sql`

### broker_gateway.connections

Stores encrypted broker credentials and connection metadata for IBKR, Questrade, and Wealthsimple integrations.

| Column | Type | Nullable | Default |
|--------|------|----------|---------|
| id | VARCHAR(36) | NO | (PK) |
| user_id | BIGINT | NO | |
| broker_type | VARCHAR(20) | NO | CHECK (IBKR, QUESTRADE, WEALTHSIMPLE) |
| status | VARCHAR(20) | NO | |
| credentials_encrypted | TEXT | YES | |
| accounts_json | JSONB | YES | |
| last_validated_at | TIMESTAMPTZ | YES | |
| last_refreshed_at | TIMESTAMPTZ | YES | |
| error_message | TEXT | YES | |
| created_at | TIMESTAMPTZ | NO | now() |
| updated_at | TIMESTAMPTZ | NO | now() |

- **PK**: `id`
- **Indexes**: `idx_connections_user_id` (user_id), `idx_connections_user_broker` (user_id, broker_type), `idx_connections_status` (status)
- **Notes**: `credentials_encrypted` is AES-256-GCM encrypted using `BROKER_ENCRYPTION_KEY`. `accounts_json` caches discovered account metadata as JSONB. `broker_type` is constrained to IBKR, QUESTRADE, or WEALTHSIMPLE via CHECK constraint.
