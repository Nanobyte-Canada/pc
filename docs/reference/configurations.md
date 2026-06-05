# Configurations Reference

Complete configuration reference for the Portfolio Construction App. Documents every Spring property, environment variable, feature flag, and tunable parameter.

---

## Spring Configuration Files

### application.yml -- Base Configuration

The base configuration file at `backend/portfolio/src/main/resources/application.yml`. All properties use `${ENV_VAR:default}` syntax for environment variable binding.

```
spring.application.name: portfolio-backend
spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}
```

### application-local.yml -- Local Development Overrides

File: `backend/portfolio/src/main/resources/application-local.yml`

- Database: same defaults as base (`localhost:5432/portfolio`)
- SQL logging: disabled (`show-sql: false`)

**Logging levels:**
| Logger | Level |
|--------|-------|
| `com.portfolio` | DEBUG |
| `org.springframework.web` | DEBUG |
| `org.hibernate` | WARN |
| `org.hibernate.SQL` | OFF |
| `org.hibernate.type.descriptor.sql` | OFF |
| `org.hibernate.orm.jdbc.bind` | OFF |

### application-dev.yml -- UAT / Dev Overrides

File: `backend/portfolio/src/main/resources/application-dev.yml`

- `server.forward-headers-strategy: framework` (trust X-Forwarded-* headers from reverse proxy)
- SQL logging: disabled

**Logging levels:**
| Logger | Level |
|--------|-------|
| `com.portfolio` | DEBUG |
| `org.springframework.security` | DEBUG |
| `org.springframework.security.web.csrf` | TRACE |
| `org.springframework.security.web.FilterChainProxy` | DEBUG |
| `org.springframework.web.cors` | DEBUG |

### application-prod.yml -- Production Overrides

File: `backend/portfolio/src/main/resources/application-prod.yml`

- SQL logging: disabled

**Logging levels:**
| Logger | Level |
|--------|-------|
| `com.portfolio` | WARN |
| `org.springframework.web` | WARN |

---

## Complete Environment Variable Reference

Every environment variable referenced in `application.yml`, organized by category.

### Database

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `DATABASE_URL` | JDBC PostgreSQL connection string | `jdbc:postgresql://localhost:5432/portfolio` | All profiles |
| `DATABASE_USERNAME` | Database username | `portfolio` | All profiles |
| `DATABASE_PASSWORD` | Database password | `portfolio` | All profiles |

### Redis

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `REDIS_HOST` | Redis server hostname | `localhost` | All profiles |
| `REDIS_PORT` | Redis server port | `6379` | All profiles |

### Application

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `APP_VERSION` | Application version string | `0.0.1-SNAPSHOT` | All profiles |
| `APP_ENVIRONMENT` | Environment name (local/dev/prod) | `local` | All profiles |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `local` | All profiles |
| `PORT` | Server port | `8080` | All profiles |

### Authentication - JWT

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `JWT_SIGNING_KEY` | HS512 signing key (min 64 chars) | `dev-signing-key-must-be-at-least-64-chars-for-hs512-algorithm-local` | All profiles |
| `JWT_ACCESS_EXPIRATION` | Access token lifetime | `60m` | All profiles |
| `JWT_REFRESH_EXPIRATION` | Refresh token lifetime | `6h` | All profiles |

### Authentication - OAuth2

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID | (empty) | dev, prod |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret | (empty) | dev, prod |

### Authentication - Email

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `EMAIL_PROVIDER` | Email provider (console/smtp) | `console` | All profiles |
| `EMAIL_FROM` | From email address | `noreply@portfolio.local` | All profiles |

### CORS

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:3000` | All profiles |

### Ingestion - General

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `INGESTION_ENABLED` | Enable/disable ingestion pipeline | `true` | All profiles |
| `INGESTION_SCHEDULE` | Cron expression for ingestion | `0 0 22 * * *` (10 PM daily) | All profiles |

### Ingestion - EODHD

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `EODHD_API_KEY` | EODHD market data API key | `696998d31abc34.90450552` (demo key) | All profiles |

### Ingestion - AlphaVantage

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `AV_ENRICHMENT_ENABLED` | Enable AlphaVantage enrichment | `true` | All profiles |
| `ALPHA_VANTAGE_API_KEY` | AlphaVantage API key | `LC9XQHKOR17K6YHR` (demo key) | All profiles |
| `AV_BASE_URL` | AlphaVantage API base URL | `https://www.alphavantage.co/query` | All profiles |
| `AV_RATE_LIMIT_RPM` | Requests per minute limit | `75` | All profiles |
| `AV_RATE_LIMIT_BURST` | Burst capacity | `75` | All profiles |
| `AV_RETRY_MAX_ATTEMPTS` | Max retry attempts | `3` | All profiles |
| `AV_RETRY_INITIAL_BACKOFF` | Initial backoff (ms) | `60000` | All profiles |
| `AV_RETRY_MAX_BACKOFF` | Max backoff (ms) | `300000` | All profiles |
| `AV_RETRY_MULTIPLIER` | Backoff multiplier | `2.0` | All profiles |
| `AV_RETRY_JITTER` | Jitter factor | `0.1` | All profiles |
| `AV_BATCH_SIZE` | Batch size for processing | `75` | All profiles |
| `AV_STALE_THRESHOLD` | Days before data considered stale | `30` | All profiles |
| `AV_DAILY_QUOTA` | Daily API quota (-1 = unlimited) | `-1` | All profiles |

### Ingestion - ETF.com

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `ETFCOM_ENABLED` | Enable ETF.com enrichment | `true` | All profiles |
| `ETFCOM_BASE_URL` | ETF.com API base URL | `https://api-prod.etf.com/v2/fund` | All profiles |
| `ETFCOM_BATCH_SIZE` | Batch size | `20` | All profiles |
| `ETFCOM_STALE_THRESHOLD` | Days before data considered stale | `7` | All profiles |
| `ETFCOM_CONCURRENCY` | Concurrent requests | `5` | All profiles |
| `ETFCOM_REQUEST_DELAY_MS` | Delay between requests (ms) | `500` | All profiles |
| `ETFCOM_INTER_BATCH_DELAY_MS` | Delay between batches (ms) | `5000` | All profiles |
| `ETFCOM_RETRY_MAX_ATTEMPTS` | Max retry attempts | `3` | All profiles |
| `ETFCOM_RETRY_INITIAL_BACKOFF` | Initial backoff (ms) | `1000` | All profiles |
| `ETFCOM_RETRY_MAX_BACKOFF` | Max backoff (ms) | `30000` | All profiles |
| `ETFCOM_RETRY_MULTIPLIER` | Backoff multiplier | `2.0` | All profiles |
| `ETFCOM_RETRY_JITTER` | Jitter factor | `0.1` | All profiles |

### Broker Integration

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `BROKER_ENCRYPTION_KEY` | Base64-encoded 32-byte AES-256 key | (empty) | All profiles |
| `BROKER_SYNC_ENABLED` | Enable automated broker sync | `false` | All profiles |
| `BROKER_SYNC_CRON` | Cron for broker sync | `0 30 22 * * *` (10:30 PM) | All profiles |
| `BROKER_SYNC_MAX_LOOKBACK_YEARS` | Max years of historical activity data to fetch on first sync | `25` | All profiles |

### Broker Gateway (Portfolio Service)

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `BROKER_GATEWAY_URL` | URL for portfolio service to reach the broker gateway | `http://broker-gateway-service:8084` | Portfolio service |
| `GATEWAY_API_KEY` | Service-to-service authentication key for the broker gateway | `dev-gateway-key` | Portfolio service, Broker gateway service |
| `BROKER_GATEWAY_TIMEOUT` | HTTP request timeout for gateway calls | `30s` | Portfolio service |

### Frontend

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `VITE_API_URL` | Backend API base URL (build-time) | Varies by environment | Frontend build |

---

## Feature Flags

All feature flags default to values in `application.yml` and can be overridden via environment variables.

| Flag | Variable | Default | Purpose |
|------|----------|---------|---------|
| Ingestion pipeline | `INGESTION_ENABLED` | `true` | Master switch for the data ingestion scheduler |
| AlphaVantage enrichment | `AV_ENRICHMENT_ENABLED` | `true` | Enable stock data enrichment from AlphaVantage |
| ETF.com enrichment | `ETFCOM_ENABLED` | `true` | Enable ETF data enrichment from ETF.com |
| Broker sync | `BROKER_SYNC_ENABLED` | `false` | Automated position/balance sync from brokers |

---

## Rate Limiting

### EODHD
- **Rate limit:** 5 requests/second
- **Configuration:** `ingestion.eodhd.rate-limit-per-second` in application.yml
- **No retry configuration** -- simple rate limit only

### AlphaVantage
- **Rate limit:** 75 requests/minute, burst capacity 75
- **Retry:** Exponential backoff with jitter
  - Max attempts: 3
  - Initial backoff: 60,000 ms (1 min)
  - Max backoff: 300,000 ms (5 min)
  - Multiplier: 2.0
  - Jitter factor: 0.1
- **Batch size:** 75 per batch
- **Stale threshold:** 30 days
- **Daily quota:** Unlimited by default (-1)

### ETF.com
- **Batch size:** 20
- **Request delay:** 500 ms between requests
- **Inter-batch delay:** 5,000 ms between batches
- **Concurrency:** 5 concurrent requests
- **Retry:** Exponential backoff with jitter
  - Max attempts: 3
  - Initial backoff: 1,000 ms
  - Max backoff: 30,000 ms
  - Multiplier: 2.0
  - Jitter factor: 0.1
- **Stale threshold:** 7 days

---

## Security Configuration

### JWT
- **Algorithm:** HS512
- **Access token expiration:** 60 minutes (configurable via `JWT_ACCESS_EXPIRATION`)
- **Refresh token expiration:** 6 hours (configurable via `JWT_REFRESH_EXPIRATION`)
- **Issuer:** `portfolio-app`
- **Storage:** HttpOnly cookies for both access and refresh tokens
- **Signing key minimum:** 64 characters for HS512

### Password Policy
- **Minimum length:** 12 characters
- **Max failed attempts:** 5
- **Lockout duration:** 30 minutes
- **Hashing:** Argon2id via BouncyCastle

### Email Verification
- **Verification expiry:** 24 hours
- **Password reset expiry:** 6 hours
- **Provider:** Console (logs only) by default, configurable via `EMAIL_PROVIDER`

### CSRF
- **Implementation:** `CookieCsrfTokenRepository`
- **Cookie name:** `XSRF-TOKEN`
- **Header name:** `X-XSRF-TOKEN`
- **Disabled for:** Auth endpoints and `/api/**`

### CORS
- **Allowed origins:** Configurable via `CORS_ALLOWED_ORIGINS`
- **Default:** `http://localhost:3000`
- **Credentials:** Allowed

### Broker Token Encryption
- **Algorithm:** AES-256
- **Key format:** Base64-encoded 32-byte key
- **Environment variable:** `BROKER_ENCRYPTION_KEY`

---

## Database Connection Pool

HikariCP configuration from `spring.datasource.hikari` in `application.yml`:

| Property | Value | Description |
|----------|-------|-------------|
| `maximum-pool-size` | 10 | Maximum number of connections in the pool |
| `minimum-idle` | 2 | Minimum idle connections maintained |
| `idle-timeout` | 300,000 ms (5 min) | Max time a connection can sit idle |
| `connection-timeout` | 30,000 ms (30 sec) | Max time to wait for a connection from the pool |
| `initialization-fail-timeout` | -1 | Don't fail fast on startup; retry DB connection in background (tolerates transient DNS/network issues in Docker) |

### JPA/Hibernate
- **DDL auto:** `validate` (schema managed exclusively by Flyway)
- **Show SQL:** `false` (all profiles)
- **Dialect:** `org.hibernate.dialect.PostgreSQLDialect`
- **Driver:** `org.postgresql.Driver`

### Flyway
- **Enabled:** `true`
- **Locations:** `classpath:db/migration`
- **Baseline on migrate:** `true`
- **Repair on migrate:** `true`

---

## Redis Configuration

| Property | Value | Description |
|----------|-------|-------------|
| `spring.data.redis.host` | `${REDIS_HOST:localhost}` | Redis server hostname |
| `spring.data.redis.port` | `${REDIS_PORT:6379}` | Redis server port |

Redis is available in local development (docker-compose.yml provides `redis:7-alpine`) but NOT in the VPS deployment (docker-compose.vps.yml has no Redis service). The dev profile does not configure Redis separately, so Redis caching operations will fail silently or fall back when Redis is unavailable.

---

## Actuator / Management Endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

| Endpoint | Path | Access |
|----------|------|--------|
| Health | `/actuator/health` | Public (summary), Authenticated (details) |
| Info | `/actuator/info` | Authenticated |
| Metrics | `/actuator/metrics` | Authenticated |

Health details are shown only when the request is authenticated (`when-authorized`).

---

## Scheduled Tasks

| Task | Cron Variable | Default | Feature Flag |
|------|--------------|---------|-------------|
| Data ingestion | `INGESTION_SCHEDULE` | `0 0 22 * * *` (10 PM daily) | `INGESTION_ENABLED` |
| Broker position sync | `BROKER_SYNC_CRON` | `0 30 22 * * *` (10:30 PM daily) | `BROKER_SYNC_ENABLED` |

---

## Exchange Configuration

Configured under `ingestion.exchanges.north-america`:

| Exchange Code | Description |
|---------------|-------------|
| `US` | NYSE, NASDAQ |
| `TO` | Toronto Stock Exchange |
| `V` | TSX Venture Exchange |

---

## Profile Summary

| Property | local | dev (UAT) | prod |
|----------|-------|-----------|------|
| Spring profile | `local` | `dev` | `prod` |
| Forward headers | Not set | `framework` | Not set |
| App logging | DEBUG | DEBUG | WARN |
| Security logging | Not set | DEBUG/TRACE | Not set |
| Hibernate SQL | OFF | OFF | OFF |
| Redis | Available (Docker) | Available (Docker) | Available (Docker) |
| CORS origins | `http://localhost:3000` | `https://uatportfolio.nanobyte.ca` | `https://portfolio.nanobyte.ca` |

---

## config/.env.example

The `config/.env.example` file documents all required and optional environment variables for local development. Copy and rename it to `.env` for your environment.

```
# Database Configuration
POSTGRES_DB=portfolio
POSTGRES_USER=portfolio
POSTGRES_PASSWORD=changeme

# Application Configuration
APP_VERSION=0.0.1-SNAPSHOT
APP_ENVIRONMENT=local

# Spring Configuration
SPRING_PROFILES_ACTIVE=local

# Frontend Configuration
VITE_API_URL=http://localhost:8080

# Broker Gateway Configuration
BROKER_GATEWAY_URL=http://broker-gateway-service:8084
GATEWAY_API_KEY=dev-gateway-key
BROKER_GATEWAY_TIMEOUT=30s

# Broker Encryption Key (Base64-encoded 32-byte key for AES-256)
BROKER_ENCRYPTION_KEY=

# JWT Configuration (min 64 characters for HS512)
JWT_SIGNING_KEY=

# Google OAuth2 (optional - for Google Sign-In)
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# Data Ingestion API Keys
EODHD_API_KEY=
ALPHA_VANTAGE_API_KEY=

# CORS (comma-separated allowed origins)
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

---

## Ingestion Service Configuration

### application.yml (ingestion-service)

**File:** `backend/ingestion/src/main/resources/application.yml`

The ingestion service runs on port 8081 with its own `ingestion` PostgreSQL schema.

```yaml
spring.application.name: ingestion-service
server.port: ${PORT:8081}
spring.jpa.properties.hibernate.default_schema: ingestion
spring.flyway.schemas: ingestion
spring.flyway.default-schema: ingestion
spring.mvc.problemdetails.enabled: true
```

### Ingestion Service Environment Variables

| Variable | Description | Default | Used By |
|----------|-------------|---------|---------|
| `PORT` | Server port | `8081` | Ingestion service |
| `DATABASE_URL` | JDBC PostgreSQL connection string | `jdbc:postgresql://localhost:5432/portfolio` | Shared DB, `ingestion` schema |
| `DATABASE_USERNAME` | Database username | `portfolio` | Ingestion service |
| `DATABASE_PASSWORD` | Database password | `portfolio` | Ingestion service |
| `REDIS_HOST` | Redis server hostname | `localhost` | Ingestion service |
| `REDIS_PORT` | Redis server port | `6379` | Ingestion service |
| `INGESTION_ENABLED` | Enable/disable scheduled ingestion | `true` | Ingestion service |
| `INGESTION_SCHEDULE` | Cron expression for nightly ingestion | `0 0 22 * * *` | Ingestion service |
| `INGESTION_STALE_DAYS` | Days before data considered stale | `7` | Ingestion service |
| `EODHD_API_KEY` | EODHD API key | (empty) | Ingestion service |

### IngestionProperties

**File:** `backend/ingestion/.../config/IngestionProperties.kt`
**Prefix:** `ingestion`

| Property | Type | Default | Description |
|---|---|---|---|
| `ingestion.enabled` | Boolean | `true` | Master switch for ingestion scheduler |
| `ingestion.schedule` | String | `0 0 22 * * *` | Nightly ingestion cron |
| `ingestion.staleThresholdDays` | Int | `7` | Days before data re-fetch |
| `ingestion.targetExchanges` | List<String> | `[US, TO, V, INDX, GBOND]` | Exchanges to ingest |
| `ingestion.eodhd.baseUrl` | String | `https://eodhd.com/api` | EODHD API base URL |
| `ingestion.eodhd.apiKey` | String | `""` | EODHD API key |
| `ingestion.eodhd.rateLimitPerSecond` | Int | `5` | Rate limit |
| `ingestion.eodhd.dailyQuota` | Int | `100000` | Daily API call limit |
| `ingestion.eodhd.fundamentalsCost` | Int | `10` | API calls per fundamentals request |
| `ingestion.eodhd.batchSize` | Int | `500` | Instruments per batch |

---

## Market Data Service Configuration

**File:** `backend/market-data/src/main/resources/application.yml`
**Port:** 8082
**DB Schema:** `market_data`

### Market Data Service Environment Variables

| Variable | Description | Default | Used By |
|---|---|---|---|
| `DATABASE_URL` | JDBC connection string | `jdbc:postgresql://localhost:5432/portfolio` | Market data service |
| `DATABASE_USERNAME` | Database username | `portfolio` | Market data service |
| `DATABASE_PASSWORD` | Database password | `portfolio` | Market data service |
| `REDIS_HOST` | Redis hostname | `localhost` | Market data service |
| `REDIS_PORT` | Redis port | `6379` | Market data service |
| `IBKR_HOST` | Interactive Brokers TWS/Gateway host | (empty) | Market data service |
| `IBKR_PORT` | Interactive Brokers TWS/Gateway port | `4001` | Market data service |
| `IBKR_CLIENT_ID` | IBKR client connection ID | `1` | Market data service |

---

## Strategy Service Configuration

**File:** `backend/strategy/src/main/resources/application.yml`
**Port:** 8083
**DB Schema:** `strategy`

### Strategy Service Environment Variables

| Variable | Description | Default | Used By |
|---|---|---|---|
| `DATABASE_URL` | JDBC connection string | `jdbc:postgresql://localhost:5432/portfolio` | Strategy service |
| `DATABASE_USERNAME` | Database username | `portfolio` | Strategy service |
| `DATABASE_PASSWORD` | Database password | `portfolio` | Strategy service |
| `REDIS_HOST` | Redis hostname | `localhost` | Strategy service |
| `REDIS_PORT` | Redis port | `6379` | Strategy service |
| `MARKET_DATA_SERVICE_URL` | Market data service base URL | `http://localhost:8082` | Strategy service |
| `PORTFOLIO_SERVICE_URL` | Portfolio backend base URL | `http://localhost:8080` | Strategy service |

---

## Broker Gateway Service Configuration

**File:** `backend/broker-gateway/src/main/resources/application.yml`
**Port:** 8084
**DB Schema:** `broker_gateway`

### Broker Gateway Service Environment Variables

| Variable | Description | Default | Used By |
|---|---|---|---|
| `PORT` | Server port | `8084` | Broker gateway service |
| `DATABASE_URL` | JDBC connection string | `jdbc:postgresql://localhost:5432/portfolio` | Broker gateway service |
| `DATABASE_USERNAME` | Database username | `portfolio` | Broker gateway service |
| `DATABASE_PASSWORD` | Database password | `portfolio` | Broker gateway service |
| `REDIS_HOST` | Redis hostname | `localhost` | Broker gateway service |
| `REDIS_PORT` | Redis port | `6379` | Broker gateway service |
| `BROKER_ENCRYPTION_KEY` | AES-256-GCM key for encrypting broker credentials | (empty) | Broker gateway service |
| `GATEWAY_API_KEY` | Service-to-service authentication key | `dev-gateway-key` | Broker gateway service |
| `IBKR_GATEWAY_ENABLED` | Enable IBKR adapter | `false` | Broker gateway service |
| `IBKR_HOST` | TWS/IB Gateway host (shared with market-data) | (empty) | Broker gateway service |
| `IBKR_PORT` | TWS/IB Gateway port | `4001` | Broker gateway service |
| `IBKR_GATEWAY_CLIENT_ID` | TWS client ID for gateway (market-data uses `1`) | `2` | Broker gateway service |
| `IBKR_FLEX_TOKEN` | Flex Web Service token for historical transactions | (empty) | Broker gateway service |
| `IBKR_FLEX_QUERY_ID` | Pre-configured Flex Query ID | (empty) | Broker gateway service |
| `QUESTRADE_ENABLED` | Enable Questrade adapter | `false` | Broker gateway service |
| `QUESTRADE_AUTH_URL` | OAuth token endpoint | `https://login.questrade.com/oauth2/token` | Broker gateway service |
| `QUESTRADE_PRACTICE_AUTH_URL` | Practice/sandbox auth URL | (empty) | Broker gateway service |
| `QUESTRADE_USE_PRACTICE` | Use practice environment | `false` | Broker gateway service |
| `QUESTRADE_RATE_LIMIT` | Requests per second limit | `1` | Broker gateway service |
| `WEALTHSIMPLE_ENABLED` | Enable Wealthsimple adapter | `false` | Broker gateway service |
| `WEALTHSIMPLE_AUTH_URL` | OAuth token endpoint | (empty) | Broker gateway service |
| `WEALTHSIMPLE_GRAPHQL_URL` | GraphQL API URL | (empty) | Broker gateway service |
| `WEALTHSIMPLE_CLIENT_ID` | OAuth client ID (hardcoded from WS web app) | (empty) | Broker gateway service |
| `WS_ORDER_RATE_LIMIT` | Max orders per hour | `7` | Broker gateway service |

---

## Secret Management

Production and UAT secrets are stored in HashiCorp Vault at `vault.nanobyte.ca`. The deploy workflow fetches secrets at deploy time using AppRole authentication.

### Vault Secret Paths

| Path | Environment |
|------|-------------|
| `secret/portfolio/prod` | Production |
| `secret/portfolio/uat` | UAT |

### GitHub Actions Secrets (per environment)

| Secret | Purpose |
|--------|---------|
| `VAULT_ROLE_ID` | Vault AppRole role ID for authenticating to Vault |
| `VAULT_SECRET_ID` | Vault AppRole secret ID for authenticating to Vault |

### Keys Stored in Vault

The following keys are stored in Vault for each environment (`secret/portfolio/prod` and `secret/portfolio/uat`):

| Key | Description |
|-----|-------------|
| `POSTGRES_DB` | PostgreSQL database name |
| `POSTGRES_USER` | PostgreSQL username |
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `JWT_SIGNING_KEY` | HS512 JWT signing key (min 64 chars) |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret |
| `BROKER_ENCRYPTION_KEY` | Base64-encoded AES-256 key for broker credential encryption |
| `GATEWAY_API_KEY` | Service-to-service authentication key for broker gateway |
| `EODHD_API_KEY` | EODHD market data API key |
| `IBKR_USERNAME` | Interactive Brokers account username |
| `IBKR_PASSWORD` | Interactive Brokers account password |
| `IBKR_VNC_PASSWORD` | VNC password for IBKR Gateway remote access |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed CORS origins |
| `BROKER_SYNC_CRON` | Cron expression for evening broker sync |
| `BROKER_SYNC_CRON_MORNING` | Cron expression for morning broker sync |
| `BROKER_SYNC_ENABLED` | Enable/disable automated broker sync |
| `QUESTRADE_ENABLED` | Enable/disable Questrade broker adapter |
| `WEALTHSIMPLE_ENABLED` | Enable/disable Wealthsimple broker adapter |
| `IBKR_CLIENT_ID` | IBKR client connection ID for market-data service |
| `IBKR_GATEWAY_CLIENT_ID` | IBKR client connection ID for broker-gateway service |

---

## Cross-References

- For Docker and deployment infrastructure, see [infrastructure.md](infrastructure.md)
- For backend service configuration and behavior, see [backend-services.md](backend-services.md)
- For database schema and migration details, see [database-schema.md](database-schema.md)
