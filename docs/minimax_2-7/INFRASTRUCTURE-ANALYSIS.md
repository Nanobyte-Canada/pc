# Infrastructure & DevOps Analysis

> CI/CD, Docker, monitoring, security, deployment topology, and infrastructure gaps.

---

## 1. Docker Infrastructure

### 1.1 Docker Compose Files

| File | Environment | Services | Purpose |
|------|-------------|----------|---------|
| `docker-compose.yml` | Local dev | 9 | Full stack with source builds |
| `deploy/prod/docker-compose.yml` | Production | 8 | Pre-built images from GHCR |
| `deploy/uat/docker-compose.yml` | UAT | 8 | Pre-built images from GHCR |
| `deploy/shared/docker-compose.yml` | Shared | 1 | Live IB Gateway |
| `deploy/monitoring/docker-compose.yml` | Monitoring | 12 | Prometheus, Grafana, Loki, Vault |

### 1.2 Redundancies

1. **5 near-identical Dockerfiles** — Each backend service has its own Dockerfile with ~90% duplication. Only differences: port number, `common` module copy, SSL cert import.

2. **3 near-identical docker-compose files** — `prod/docker-compose.yml` and `uat/docker-compose.yml` differ only in container names, ports, and env vars. Structure is identical.

3. **Frontend builds two images** — `portfolio-frontend` and `portfolio-frontend-uat` from the same Dockerfile with different build args, doubling frontend build time.

---

## 2. CI/CD Pipeline

### 2.1 CI: Build & Push (`build.yml`)

| Step | Description | Time (est.) |
|------|-------------|-------------|
| test-backend | Gradle test × 5 services (parallel) | ~5-10 min |
| test-frontend | npm ci → lint → test → build | ~3-5 min |
| build-and-push | Docker build × 7 images → push to GHCR | ~10-15 min |
| **Total** | | **~18-30 min** |

### 2.2 CD: Deploy (`deploy.yml`)

| Step | Description |
|------|-------------|
| Validate tag | Regex: `^main-[a-f0-9]{7,}$` |
| Fetch secrets | Vault AppRole auth → generate `.env` |
| SSH via cloudflared tunnel | ProxyCommand |
| SCP + SSH deploy | `docker compose pull && up -d` |
| Verify health | Check container status |
| Slack notification | |

### 2.3 CI/CD Gaps

1. **No smoke tests after deployment** — Checks container health but not endpoint responses
2. **No canary or blue/green** — In-place deployment may cause brief downtime
3. **No rollback strategy** — No automated rollback on failure
4. **No database migration safety** — Flyway runs on startup, could break during rolling update
5. **No performance regression tests** in CI

---

## 3. Monitoring Stack

### 3.1 Components

| Service | Port | Purpose |
|---------|------|---------|
| Prometheus | 19090 | Metrics collection (30d retention) |
| Grafana | 13000 | Dashboards + alerting |
| Loki | 13100 | Log aggregation |
| cAdvisor | 18080 | Container metrics |
| Uptime Kuma | 13001 | External uptime monitoring |
| Node Exporter | 19100 | Host metrics |
| Postgres Exporter | 19187/19188 | DB metrics (prod + uat) |
| Redis Exporter | 19121/19122 | Cache metrics (prod + uat) |
| Promtail | - | Docker log shipping |
| Vault | 18200 | Secrets management |

### 3.2 Gaps

1. **No pre-built Grafana dashboards** — Datasources provisioned but no dashboard JSONs
2. **No distributed tracing** — No OpenTelemetry or Jaeger
3. **No synthetic monitoring** — Only HTTP-level uptime checks

---

## 4. Security Analysis

### 4.1 Issues Found

| Issue | Severity | Location | Description |
|-------|----------|----------|-------------|
| Hardcoded WS client ID | **High** | `WealthsimpleConfig.kt` | Real client ID as default env var |
| API key not validated | **High** | `broker-gateway` | `GATEWAY_API_KEY` configured but no filter enforces it |
| No rate limiting | **Medium** | All services | Resilience4j present but not used |
| Wealthsimple unofficial API | **Medium** | `broker-gateway` | May break or violate TOS |
| No MFA | **Medium** | Auth system | No multi-factor authentication |

### 4.2 Security Measures in Place

| Measure | Status |
|---------|--------|
| JWT (HS512, HttpOnly cookies) | ✅ |
| CSRF token | ✅ |
| Google OAuth2 | ✅ |
| Argon2id password hashing | ✅ |
| AES-256-GCM encryption | ✅ |
| Role-based access | ✅ |
| Flyway DB migrations | ✅ |
| Non-root Docker user | ✅ |
| Cloudflare Tunnel (no open ports) | ✅ |
| Vault secrets management | ✅ |

---

## 5. Single Point of Failure Analysis

| Component | Risk | Mitigation |
|-----------|------|------------|
| VPS (single host) | Full outage on host failure | No current mitigation |
| PostgreSQL (single instance) | Data loss | Backups (assumed) |
| IB Gateway (single instance) | No live trading data | No backup data source |
| Cloudflare Tunnel | No public access if down | Local access only |
| EODHD (single provider) | No data ingestion | No backup provider |

---

## 6. Configuration Management

### 6.1 Environment Variables

Total documented in `config/.env.example`: **33**

| Category | Count | Examples |
|----------|-------|----------|
| Database | 6 | `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| Redis | 2 | `REDIS_HOST`, `REDIS_PORT` |
| JWT | 1 | `JWT_SIGNING_KEY` |
| Broker | 3 | `BROKER_GATEWAY_URL`, `GATEWAY_API_KEY`, `BROKER_ENCRYPTION_KEY` |
| IBKR | 4 | `IBKR_HOST`, `IBKR_PORT`, `IBKR_CLIENT_ID` |
| Questrade | 3 | `QUESTRADE_ENABLED`, `QUESTRADE_AUTH_URL` |
| Wealthsimple | 1 | `WEALTHSIMPLE_ENABLED` |
| Ingestion | 1 | `EODHD_API_KEY` |
| Frontend | 1 | `VITE_API_URL` |
| App | 3 | `APP_VERSION`, `APP_ENVIRONMENT`, `SPRING_PROFILES_ACTIVE` |
| CORS | 1 | `CORS_ALLOWED_ORIGINS` |

### 6.2 Optional Undocumented Variables (8 identified)

| Variable | Default | Service |
|----------|---------|---------|
| `IBKR_CONNECT_TIMEOUT` | 5000 | broker-gateway |
| `IBKR_REQUEST_TIMEOUT` | 30000 | broker-gateway |
| `IBKR_FLEX_TOKEN` | (empty) | broker-gateway |
| `IBKR_FLEX_QUERY_ID` | (empty) | broker-gateway |
| `QUESTRADE_PRACTICE_AUTH_URL` | (hardcoded) | broker-gateway |
| `QUESTRADE_USE_PRACTICE` | false | broker-gateway |
| `WS_ORDER_RATE_LIMIT` | 7 | broker-gateway |
| `IBKR_MAX_SUBSCRIPTIONS` | 100 | market-data |