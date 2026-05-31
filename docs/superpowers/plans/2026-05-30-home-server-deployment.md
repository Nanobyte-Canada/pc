# Home Server Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy the Portfolio Construction App to a home server (Debian 13) with prod and UAT environments, full observability, and CI/CD via GitHub Actions.

**Architecture:** Docker Compose stacks for prod, UAT, and monitoring behind a Cloudflare Tunnel. CI builds images to ghcr.io on merge to `main`. Manual deploy workflow pulls tagged images and restarts the target stack. Prometheus + Grafana + Loki provide observability on prod with Slack alerting.

**Tech Stack:** Docker Compose, Cloudflare Tunnel (`cloudflared`), Prometheus, Grafana, Loki, cAdvisor, Uptime Kuma, GitHub Actions, ghcr.io, Bash

---

## File Map

### New Files — Deployment Configs

| File | Responsibility |
|------|---------------|
| `deploy/prod/docker-compose.yml` | Prod stack — all 8 services pulling from ghcr.io |
| `deploy/prod/.env.example` | Prod environment variable template |
| `deploy/uat/docker-compose.yml` | UAT stack — same services, different ports/config |
| `deploy/uat/.env.example` | UAT environment variable template |
| `deploy/monitoring/docker-compose.yml` | Observability stack — Prometheus, Grafana, Loki, cAdvisor, Uptime Kuma, exporters |
| `deploy/monitoring/prometheus/prometheus.yml` | Prometheus scrape targets config |
| `deploy/monitoring/loki/loki-config.yml` | Loki storage and retention config |
| `deploy/monitoring/grafana/provisioning/datasources/datasources.yml` | Grafana auto-provisioned datasources (Prometheus + Loki) |
| `deploy/monitoring/grafana/provisioning/alerting/slack.yml` | Grafana Slack contact point + notification policy |
| `deploy/monitoring/grafana/provisioning/alerting/rules.yml` | Grafana alert rules (6 alert conditions) |
| `deploy/cloudflared/config.yml` | Cloudflare Tunnel routing rules |
| `deploy/scripts/setup-server.sh` | One-time server setup (Docker, cloudflared, UFW, deploy user) |
| `deploy/scripts/backup.sh` | Daily pg_dump backup script for cron |

### New Files — CI/CD

| File | Responsibility |
|------|---------------|
| `.github/workflows/build.yml` | CI: test + build + push images to ghcr.io |
| `.github/workflows/deploy.yml` | CD: manual trigger to deploy a tag to prod or UAT |

### New Files — Spring UAT Profile

| File | Responsibility |
|------|---------------|
| `backend/portfolio/src/main/resources/application-uat.yml` | UAT profile for portfolio service |
| `backend/ingestion/src/main/resources/application-uat.yml` | UAT profile for ingestion service |
| `backend/market-data/src/main/resources/application-uat.yml` | UAT profile for market-data service |
| `backend/strategy/src/main/resources/application-uat.yml` | UAT profile for strategy service |
| `backend/broker-gateway/src/main/resources/application-uat.yml` | UAT profile for broker-gateway service |

### Modified Files

| File | Change |
|------|--------|
| `backend/portfolio/src/main/resources/application.yml` | Add `prometheus` to actuator endpoints |
| `backend/ingestion/src/main/resources/application.yml` | Add `prometheus` to actuator endpoints |
| `backend/market-data/src/main/resources/application.yml` | Add `prometheus` to actuator endpoints |
| `backend/strategy/src/main/resources/application.yml` | Add `prometheus` to actuator endpoints |
| `backend/broker-gateway/src/main/resources/application.yml` | Add `prometheus` to actuator endpoints |
| `config/.env.example` | Add new env vars for prod/UAT |
| `docs/reference/infrastructure.md` | Update with home server deployment docs |
| `.gitignore` | Add deploy/**/.env (secrets must not be committed) |

---

## Task 1: Enable Prometheus Actuator Endpoint

All 5 backend services already have `micrometer-registry-prometheus` in their Gradle dependencies, but the `/actuator/prometheus` endpoint is not exposed. Add it to each service's `application.yml`.

**Files:**
- Modify: `backend/portfolio/src/main/resources/application.yml:71`
- Modify: `backend/ingestion/src/main/resources/application.yml:43`
- Modify: `backend/market-data/src/main/resources/application.yml:43`
- Modify: `backend/strategy/src/main/resources/application.yml:43`
- Modify: `backend/broker-gateway/src/main/resources/application.yml:43`

- [ ] **Step 1: Update portfolio service actuator endpoints**

In `backend/portfolio/src/main/resources/application.yml`, change line 71:
```yaml
# Before:
        include: health,info,metrics
# After:
        include: health,info,metrics,prometheus
```

- [ ] **Step 2: Update ingestion service actuator endpoints**

In `backend/ingestion/src/main/resources/application.yml`, change line 43:
```yaml
# Before:
        include: health,info,metrics
# After:
        include: health,info,metrics,prometheus
```

- [ ] **Step 3: Update market-data service actuator endpoints**

In `backend/market-data/src/main/resources/application.yml`, change line 43:
```yaml
# Before:
        include: health,info,metrics
# After:
        include: health,info,metrics,prometheus
```

- [ ] **Step 4: Update strategy service actuator endpoints**

In `backend/strategy/src/main/resources/application.yml`, change line 43:
```yaml
# Before:
        include: health,info,metrics
# After:
        include: health,info,metrics,prometheus
```

- [ ] **Step 5: Update broker-gateway service actuator endpoints**

In `backend/broker-gateway/src/main/resources/application.yml`, change line 43:
```yaml
# Before:
        include: health,info,metrics
# After:
        include: health,info,metrics,prometheus
```

- [ ] **Step 6: Verify locally**

Run the stack and confirm the endpoint responds:
```bash
docker compose up -d backend
# Wait for health check
curl http://localhost:8080/actuator/prometheus | head -20
```

Expected: Prometheus-formatted metrics output (lines like `jvm_memory_used_bytes{...} 12345`).

- [ ] **Step 7: Commit**

```bash
git add backend/portfolio/src/main/resources/application.yml \
  backend/ingestion/src/main/resources/application.yml \
  backend/market-data/src/main/resources/application.yml \
  backend/strategy/src/main/resources/application.yml \
  backend/broker-gateway/src/main/resources/application.yml
git commit -m "feat(observability): expose prometheus actuator endpoint on all services"
```

---

## Task 2: Create UAT Spring Profiles

Create `application-uat.yml` for each backend service. UAT uses DEBUG logging (like dev) and trusts forward headers (behind Cloudflare Tunnel / Nginx).

**Files:**
- Create: `backend/portfolio/src/main/resources/application-uat.yml`
- Create: `backend/ingestion/src/main/resources/application-uat.yml`
- Create: `backend/market-data/src/main/resources/application-uat.yml`
- Create: `backend/strategy/src/main/resources/application-uat.yml`
- Create: `backend/broker-gateway/src/main/resources/application-uat.yml`

- [ ] **Step 1: Create portfolio UAT profile**

Create `backend/portfolio/src/main/resources/application-uat.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: DEBUG
    org.springframework.web: INFO
    org.hibernate: WARN
```

- [ ] **Step 2: Create ingestion UAT profile**

Create `backend/ingestion/src/main/resources/application-uat.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: DEBUG
    org.springframework.web: INFO
    org.hibernate: WARN
```

- [ ] **Step 3: Create market-data UAT profile**

Create `backend/market-data/src/main/resources/application-uat.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: DEBUG
    org.springframework.web: INFO
    org.hibernate: WARN
```

- [ ] **Step 4: Create strategy UAT profile**

Create `backend/strategy/src/main/resources/application-uat.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: DEBUG
    org.springframework.web: INFO
    org.hibernate: WARN
```

- [ ] **Step 5: Create broker-gateway UAT profile**

Create `backend/broker-gateway/src/main/resources/application-uat.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: DEBUG
    org.springframework.web: INFO
    org.hibernate: WARN
```

- [ ] **Step 6: Commit**

```bash
git add backend/portfolio/src/main/resources/application-uat.yml \
  backend/ingestion/src/main/resources/application-uat.yml \
  backend/market-data/src/main/resources/application-uat.yml \
  backend/strategy/src/main/resources/application-uat.yml \
  backend/broker-gateway/src/main/resources/application-uat.yml
git commit -m "feat: add application-uat.yml spring profile for all services"
```

---

## Task 3: Update prod Spring profile for forward headers

The existing `application-prod.yml` only exists for the portfolio service and lacks `forward-headers-strategy: framework`, which is required since prod is behind Cloudflare Tunnel. Also add prod profiles for the other 4 services.

**Files:**
- Modify: `backend/portfolio/src/main/resources/application-prod.yml`
- Create: `backend/ingestion/src/main/resources/application-prod.yml`
- Create: `backend/market-data/src/main/resources/application-prod.yml`
- Create: `backend/strategy/src/main/resources/application-prod.yml`
- Create: `backend/broker-gateway/src/main/resources/application-prod.yml`

- [ ] **Step 1: Update portfolio prod profile**

In `backend/portfolio/src/main/resources/application-prod.yml`, add `forward-headers-strategy`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: WARN
    org.springframework.web: WARN
```

- [ ] **Step 2: Create ingestion prod profile**

Create `backend/ingestion/src/main/resources/application-prod.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: WARN
    org.springframework.web: WARN
```

- [ ] **Step 3: Create market-data prod profile**

Create `backend/market-data/src/main/resources/application-prod.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: WARN
    org.springframework.web: WARN
```

- [ ] **Step 4: Create strategy prod profile**

Create `backend/strategy/src/main/resources/application-prod.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: WARN
    org.springframework.web: WARN
```

- [ ] **Step 5: Create broker-gateway prod profile**

Create `backend/broker-gateway/src/main/resources/application-prod.yml`:
```yaml
server:
  forward-headers-strategy: framework

spring:
  jpa:
    show-sql: false

logging:
  level:
    com.portfolio: WARN
    org.springframework.web: WARN
```

- [ ] **Step 6: Commit**

```bash
git add backend/portfolio/src/main/resources/application-prod.yml \
  backend/ingestion/src/main/resources/application-prod.yml \
  backend/market-data/src/main/resources/application-prod.yml \
  backend/strategy/src/main/resources/application-prod.yml \
  backend/broker-gateway/src/main/resources/application-prod.yml
git commit -m "feat: add/update prod spring profiles with forward-headers-strategy for all services"
```

---

## Task 4: Create Prod Docker Compose Stack

Create the production `docker-compose.yml` that pulls pre-built images from ghcr.io with production hardening (memory limits, restart policies, log rotation, named volumes).

**Files:**
- Create: `deploy/prod/docker-compose.yml`
- Create: `deploy/prod/.env.example`

- [ ] **Step 1: Create deploy directory structure**

```bash
mkdir -p deploy/prod deploy/uat deploy/monitoring/prometheus deploy/monitoring/loki \
  deploy/monitoring/grafana/provisioning/datasources \
  deploy/monitoring/grafana/provisioning/alerting \
  deploy/cloudflared deploy/scripts
```

- [ ] **Step 2: Create prod docker-compose.yml**

Create `deploy/prod/docker-compose.yml`:
```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: prod-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "15432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 8G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - prod-network

  redis:
    image: redis:7-alpine
    container_name: prod-redis
    ports:
      - "16379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512M
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - prod-network

  backend:
    image: ghcr.io/saurabhbilakhia/portfolio-backend:${IMAGE_TAG}
    container_name: prod-backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      APP_VERSION: ${IMAGE_TAG}
      APP_ENVIRONMENT: prod
      BROKER_ENCRYPTION_KEY: ${BROKER_ENCRYPTION_KEY}
      BROKER_GATEWAY_URL: http://broker-gateway-service:8084
      BROKER_SYNC_ENABLED: ${BROKER_SYNC_ENABLED:-true}
      BROKER_SYNC_CRON: ${BROKER_SYNC_CRON:-0 20 16 * * *}
      BROKER_SYNC_CRON_MORNING: ${BROKER_SYNC_CRON_MORNING:-0 0 6 * * *}
      JWT_SIGNING_KEY: ${JWT_SIGNING_KEY}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      CORS_ALLOWED_ORIGINS: https://portfolio.nanobyte.ca
      GATEWAY_API_KEY: ${GATEWAY_API_KEY}
    ports:
      - "10080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - prod-network

  ingestion-service:
    image: ghcr.io/saurabhbilakhia/portfolio-ingestion:${IMAGE_TAG}
    container_name: prod-ingestion
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      EODHD_API_KEY: ${EODHD_API_KEY}
    ports:
      - "10081:8081"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - prod-network

  market-data-service:
    image: ghcr.io/saurabhbilakhia/portfolio-market-data:${IMAGE_TAG}
    container_name: prod-market-data
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      IBKR_HOST: ${IBKR_HOST:-}
      IBKR_PORT: ${IBKR_PORT:-4002}
      IBKR_CLIENT_ID: ${IBKR_CLIENT_ID:-1}
    ports:
      - "10082:8082"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - prod-network

  strategy-service:
    image: ghcr.io/saurabhbilakhia/portfolio-strategy:${IMAGE_TAG}
    container_name: prod-strategy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      MARKET_DATA_SERVICE_URL: http://market-data-service:8082
      PORTFOLIO_SERVICE_URL: http://backend:8080
    ports:
      - "10083:8083"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8083/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - prod-network

  broker-gateway-service:
    image: ghcr.io/saurabhbilakhia/portfolio-broker-gateway:${IMAGE_TAG}
    container_name: prod-broker-gateway
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      BROKER_ENCRYPTION_KEY: ${BROKER_ENCRYPTION_KEY}
      GATEWAY_API_KEY: ${GATEWAY_API_KEY}
      IBKR_GATEWAY_ENABLED: ${IBKR_GATEWAY_ENABLED:-false}
      IBKR_HOST: ${IBKR_HOST:-}
      IBKR_PORT: ${IBKR_PORT:-4002}
      IBKR_GATEWAY_CLIENT_ID: ${IBKR_GATEWAY_CLIENT_ID:-2}
      QUESTRADE_ENABLED: ${QUESTRADE_ENABLED:-false}
      WEALTHSIMPLE_ENABLED: ${WEALTHSIMPLE_ENABLED:-false}
    ports:
      - "10084:8084"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8084/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - prod-network

  frontend:
    image: ghcr.io/saurabhbilakhia/portfolio-frontend:${IMAGE_TAG}
    container_name: prod-frontend
    ports:
      - "10000:80"
    depends_on:
      - backend
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512M
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - prod-network

volumes:
  postgres_data:

networks:
  prod-network:
    driver: bridge
```

- [ ] **Step 3: Create prod .env.example**

Create `deploy/prod/.env.example`:
```bash
# === Image ===
IMAGE_TAG=main-latest

# === Database ===
POSTGRES_DB=portfolio
POSTGRES_USER=portfolio
POSTGRES_PASSWORD=CHANGE_ME_GENERATE_STRONG_PASSWORD

# === Auth ===
JWT_SIGNING_KEY=CHANGE_ME_MIN_64_CHARS_FOR_HS512
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# === Broker ===
BROKER_ENCRYPTION_KEY=CHANGE_ME_BASE64_32_BYTES_AES256
GATEWAY_API_KEY=CHANGE_ME_GENERATE_RANDOM_KEY
BROKER_SYNC_ENABLED=true
BROKER_SYNC_CRON=0 20 16 * * *
BROKER_SYNC_CRON_MORNING=0 0 6 * * *

# === IBKR ===
IBKR_HOST=
IBKR_PORT=4002
IBKR_CLIENT_ID=1
IBKR_GATEWAY_ENABLED=false
IBKR_GATEWAY_CLIENT_ID=2

# === Questrade / Wealthsimple ===
QUESTRADE_ENABLED=false
WEALTHSIMPLE_ENABLED=false

# === Data ===
EODHD_API_KEY=
```

- [ ] **Step 4: Commit**

```bash
git add deploy/prod/docker-compose.yml deploy/prod/.env.example
git commit -m "feat(deploy): add prod docker-compose stack with ghcr.io images"
```

---

## Task 5: Create UAT Docker Compose Stack

Same structure as prod, but with UAT ports (20xxx), UAT profile, and UAT CORS origin.

**Files:**
- Create: `deploy/uat/docker-compose.yml`
- Create: `deploy/uat/.env.example`

- [ ] **Step 1: Create UAT docker-compose.yml**

Create `deploy/uat/docker-compose.yml`:
```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: uat-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "25432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 8G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - uat-network

  redis:
    image: redis:7-alpine
    container_name: uat-redis
    ports:
      - "26379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512M
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - uat-network

  backend:
    image: ghcr.io/saurabhbilakhia/portfolio-backend:${IMAGE_TAG}
    container_name: uat-backend
    environment:
      SPRING_PROFILES_ACTIVE: uat
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      APP_VERSION: ${IMAGE_TAG}
      APP_ENVIRONMENT: uat
      BROKER_ENCRYPTION_KEY: ${BROKER_ENCRYPTION_KEY}
      BROKER_GATEWAY_URL: http://broker-gateway-service:8084
      BROKER_SYNC_ENABLED: ${BROKER_SYNC_ENABLED:-true}
      BROKER_SYNC_CRON: ${BROKER_SYNC_CRON:-0 20 16 * * *}
      BROKER_SYNC_CRON_MORNING: ${BROKER_SYNC_CRON_MORNING:-0 0 6 * * *}
      JWT_SIGNING_KEY: ${JWT_SIGNING_KEY}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      CORS_ALLOWED_ORIGINS: https://uatportfolio.nanobyte.ca
      GATEWAY_API_KEY: ${GATEWAY_API_KEY}
    ports:
      - "20080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - uat-network

  ingestion-service:
    image: ghcr.io/saurabhbilakhia/portfolio-ingestion:${IMAGE_TAG}
    container_name: uat-ingestion
    environment:
      SPRING_PROFILES_ACTIVE: uat
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      EODHD_API_KEY: ${EODHD_API_KEY}
    ports:
      - "20081:8081"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - uat-network

  market-data-service:
    image: ghcr.io/saurabhbilakhia/portfolio-market-data:${IMAGE_TAG}
    container_name: uat-market-data
    environment:
      SPRING_PROFILES_ACTIVE: uat
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      IBKR_HOST: ${IBKR_HOST:-}
      IBKR_PORT: ${IBKR_PORT:-4002}
      IBKR_CLIENT_ID: ${IBKR_CLIENT_ID:-1}
    ports:
      - "20082:8082"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - uat-network

  strategy-service:
    image: ghcr.io/saurabhbilakhia/portfolio-strategy:${IMAGE_TAG}
    container_name: uat-strategy
    environment:
      SPRING_PROFILES_ACTIVE: uat
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      MARKET_DATA_SERVICE_URL: http://market-data-service:8082
      PORTFOLIO_SERVICE_URL: http://backend:8080
    ports:
      - "20083:8083"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8083/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - uat-network

  broker-gateway-service:
    image: ghcr.io/saurabhbilakhia/portfolio-broker-gateway:${IMAGE_TAG}
    container_name: uat-broker-gateway
    environment:
      SPRING_PROFILES_ACTIVE: uat
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      DATABASE_USERNAME: ${POSTGRES_USER}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      BROKER_ENCRYPTION_KEY: ${BROKER_ENCRYPTION_KEY}
      GATEWAY_API_KEY: ${GATEWAY_API_KEY}
      IBKR_GATEWAY_ENABLED: ${IBKR_GATEWAY_ENABLED:-false}
      IBKR_HOST: ${IBKR_HOST:-}
      IBKR_PORT: ${IBKR_PORT:-4002}
      IBKR_GATEWAY_CLIENT_ID: ${IBKR_GATEWAY_CLIENT_ID:-2}
      QUESTRADE_ENABLED: ${QUESTRADE_ENABLED:-false}
      WEALTHSIMPLE_ENABLED: ${WEALTHSIMPLE_ENABLED:-false}
    ports:
      - "20084:8084"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8084/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 4G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - uat-network

  frontend:
    image: ghcr.io/saurabhbilakhia/portfolio-frontend:${IMAGE_TAG}
    container_name: uat-frontend
    ports:
      - "20000:80"
    depends_on:
      - backend
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512M
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - uat-network

volumes:
  postgres_data:

networks:
  uat-network:
    driver: bridge
```

- [ ] **Step 2: Create UAT .env.example**

Create `deploy/uat/.env.example`:
```bash
# === Image ===
IMAGE_TAG=main-latest

# === Database ===
POSTGRES_DB=portfolio_uat
POSTGRES_USER=portfolio_uat
POSTGRES_PASSWORD=CHANGE_ME_GENERATE_STRONG_PASSWORD

# === Auth ===
JWT_SIGNING_KEY=CHANGE_ME_MIN_64_CHARS_FOR_HS512_DIFFERENT_FROM_PROD
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# === Broker ===
BROKER_ENCRYPTION_KEY=CHANGE_ME_BASE64_32_BYTES_AES256_DIFFERENT_FROM_PROD
GATEWAY_API_KEY=CHANGE_ME_GENERATE_RANDOM_KEY_DIFFERENT_FROM_PROD
BROKER_SYNC_ENABLED=true
BROKER_SYNC_CRON=0 20 16 * * *
BROKER_SYNC_CRON_MORNING=0 0 6 * * *

# === IBKR ===
IBKR_HOST=
IBKR_PORT=4002
IBKR_CLIENT_ID=1
IBKR_GATEWAY_ENABLED=false
IBKR_GATEWAY_CLIENT_ID=2

# === Questrade / Wealthsimple ===
QUESTRADE_ENABLED=false
WEALTHSIMPLE_ENABLED=false

# === Data ===
EODHD_API_KEY=
```

- [ ] **Step 3: Commit**

```bash
git add deploy/uat/docker-compose.yml deploy/uat/.env.example
git commit -m "feat(deploy): add UAT docker-compose stack with ghcr.io images"
```

---

## Task 6: Create Monitoring Docker Compose Stack

The observability stack: Prometheus, Grafana, Loki, cAdvisor, Uptime Kuma, and the 3 exporters (node, postgres, redis). This stack connects to the prod network to scrape metrics.

**Files:**
- Create: `deploy/monitoring/docker-compose.yml`
- Create: `deploy/monitoring/prometheus/prometheus.yml`
- Create: `deploy/monitoring/loki/loki-config.yml`
- Create: `deploy/monitoring/grafana/provisioning/datasources/datasources.yml`

- [ ] **Step 1: Create monitoring docker-compose.yml**

Create `deploy/monitoring/docker-compose.yml`:
```yaml
services:
  prometheus:
    image: prom/prometheus:v2.53.0
    container_name: monitoring-prometheus
    ports:
      - "19090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.retention.time=30d"
      - "--web.enable-lifecycle"
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 2G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - monitoring-network
    extra_hosts:
      - "host.docker.internal:host-gateway"

  grafana:
    image: grafana/grafana:11.1.0
    container_name: monitoring-grafana
    ports:
      - "13000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:-admin}
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD}
      GF_SERVER_ROOT_URL: https://grafana.nanobyte.ca
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 1G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - monitoring-network

  loki:
    image: grafana/loki:3.1.0
    container_name: monitoring-loki
    ports:
      - "13100:3100"
    volumes:
      - ./loki/loki-config.yml:/etc/loki/local-config.yaml:ro
      - loki_data:/loki
    command: -config.file=/etc/loki/local-config.yaml
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 2G
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - monitoring-network

  cadvisor:
    image: gcr.io/cadvisor/cadvisor:v0.49.1
    container_name: monitoring-cadvisor
    ports:
      - "18080:8080"
    volumes:
      - /:/rootfs:ro
      - /var/run:/var/run:ro
      - /sys:/sys:ro
      - /var/lib/docker/:/var/lib/docker:ro
      - /dev/disk/:/dev/disk:ro
    privileged: true
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512M
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - monitoring-network

  uptime-kuma:
    image: louislam/uptime-kuma:1
    container_name: monitoring-uptime-kuma
    ports:
      - "13001:3001"
    volumes:
      - uptime_kuma_data:/app/data
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512M
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
    networks:
      - monitoring-network

  node-exporter:
    image: prom/node-exporter:v1.8.1
    container_name: monitoring-node-exporter
    ports:
      - "19100:9100"
    volumes:
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /:/rootfs:ro
    command:
      - "--path.procfs=/host/proc"
      - "--path.rootfs=/rootfs"
      - "--path.sysfs=/host/sys"
      - "--collector.filesystem.mount-points-exclude=^/(sys|proc|dev|host|etc)($$|/)"
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 256M
    networks:
      - monitoring-network

  postgres-exporter:
    image: prometheuscommunity/postgres-exporter:v0.15.0
    container_name: monitoring-postgres-exporter
    ports:
      - "19187:9187"
    environment:
      DATA_SOURCE_NAME: postgresql://${PROD_POSTGRES_USER}:${PROD_POSTGRES_PASSWORD}@host.docker.internal:15432/${PROD_POSTGRES_DB}?sslmode=disable
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 256M
    networks:
      - monitoring-network
    extra_hosts:
      - "host.docker.internal:host-gateway"

  redis-exporter:
    image: oliver006/redis_exporter:v1.61.0
    container_name: monitoring-redis-exporter
    ports:
      - "19121:9121"
    environment:
      REDIS_ADDR: host.docker.internal:16379
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 256M
    networks:
      - monitoring-network
    extra_hosts:
      - "host.docker.internal:host-gateway"

volumes:
  prometheus_data:
  grafana_data:
  loki_data:
  uptime_kuma_data:

networks:
  monitoring-network:
    driver: bridge
```

- [ ] **Step 2: Create prometheus.yml**

Create `deploy/monitoring/prometheus/prometheus.yml`:
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: "portfolio-backend"
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:10080"]
        labels:
          service: backend
          environment: prod

  - job_name: "portfolio-ingestion"
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:10081"]
        labels:
          service: ingestion
          environment: prod

  - job_name: "portfolio-market-data"
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:10082"]
        labels:
          service: market-data
          environment: prod

  - job_name: "portfolio-strategy"
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:10083"]
        labels:
          service: strategy
          environment: prod

  - job_name: "portfolio-broker-gateway"
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:10084"]
        labels:
          service: broker-gateway
          environment: prod

  - job_name: "cadvisor"
    static_configs:
      - targets: ["cadvisor:8080"]

  - job_name: "node-exporter"
    static_configs:
      - targets: ["node-exporter:9100"]

  - job_name: "postgres-exporter"
    static_configs:
      - targets: ["postgres-exporter:9187"]

  - job_name: "redis-exporter"
    static_configs:
      - targets: ["redis-exporter:9121"]
```

- [ ] **Step 3: Create loki-config.yml**

Create `deploy/monitoring/loki/loki-config.yml`:
```yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  retention_period: 30d

compactor:
  working_directory: /loki/compactor
  compaction_interval: 10m
  retention_enabled: true
  retention_delete_delay: 2h
  delete_request_store: filesystem
```

- [ ] **Step 4: Create Grafana datasources provisioning**

Create `deploy/monitoring/grafana/provisioning/datasources/datasources.yml`:
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: false
```

- [ ] **Step 5: Create monitoring .env.example**

Create `deploy/monitoring/.env.example`:
```bash
# === Grafana ===
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=CHANGE_ME_STRONG_PASSWORD

# === Postgres Exporter (connects to prod DB) ===
PROD_POSTGRES_DB=portfolio
PROD_POSTGRES_USER=portfolio
PROD_POSTGRES_PASSWORD=CHANGE_ME_SAME_AS_PROD
```

- [ ] **Step 6: Commit**

```bash
git add deploy/monitoring/
git commit -m "feat(deploy): add monitoring stack with Prometheus, Grafana, Loki, cAdvisor, Uptime Kuma"
```

---

## Task 7: Create Grafana Alerting Provisioning

Configure Grafana to send alerts to Slack for the 6 alert conditions defined in the spec.

**Files:**
- Create: `deploy/monitoring/grafana/provisioning/alerting/slack.yml`
- Create: `deploy/monitoring/grafana/provisioning/alerting/rules.yml`

- [ ] **Step 1: Create Slack contact point and notification policy**

Create `deploy/monitoring/grafana/provisioning/alerting/slack.yml`:
```yaml
apiVersion: 1

contactPoints:
  - orgId: 1
    name: slack-alerts
    receivers:
      - uid: slack-portfolio
        type: slack
        settings:
          url: "${SLACK_WEBHOOK_URL}"
          title: |
            {{ `{{ .Status | toUpper }}` }} - {{ `{{ .CommonLabels.alertname }}` }}
          text: |
            {{ `{{ range .Alerts }}` }}
            *Alert:* {{ `{{ .Labels.alertname }}` }}
            *Severity:* {{ `{{ .Labels.severity }}` }}
            *Summary:* {{ `{{ .Annotations.summary }}` }}
            *Description:* {{ `{{ .Annotations.description }}` }}
            {{ `{{ end }}` }}

policies:
  - orgId: 1
    receiver: slack-alerts
    group_by:
      - alertname
      - service
    group_wait: 30s
    group_interval: 5m
    repeat_interval: 4h
```

- [ ] **Step 2: Create alert rules**

Create `deploy/monitoring/grafana/provisioning/alerting/rules.yml`:
```yaml
apiVersion: 1

groups:
  - orgId: 1
    name: portfolio-alerts
    folder: Portfolio
    interval: 1m
    rules:
      - uid: service-down
        title: Service Down
        condition: C
        data:
          - refId: A
            relativeTimeRange:
              from: 120
              to: 0
            datasourceUid: prometheus
            model:
              expr: up{job=~"portfolio-.*"} == 0
              intervalMs: 15000
              maxDataPoints: 43200
          - refId: C
            relativeTimeRange:
              from: 120
              to: 0
            datasourceUid: __expr__
            model:
              type: threshold
              expression: A
              conditions:
                - evaluator:
                    type: gt
                    params: [0]
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.service }} is down"
          description: "{{ $labels.job }} has been unreachable for more than 1 minute."

      - uid: high-error-rate
        title: High Error Rate
        condition: C
        data:
          - refId: A
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: prometheus
            model:
              expr: |
                (
                  sum by (service) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
                  /
                  sum by (service) (rate(http_server_requests_seconds_count[5m]))
                ) * 100 > 5
              intervalMs: 15000
              maxDataPoints: 43200
          - refId: C
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: __expr__
            model:
              type: threshold
              expression: A
              conditions:
                - evaluator:
                    type: gt
                    params: [0]
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate on {{ $labels.service }}"
          description: "{{ $labels.service }} has > 5% error rate over the last 5 minutes."

      - uid: jvm-heap-critical
        title: JVM Heap Critical
        condition: C
        data:
          - refId: A
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: prometheus
            model:
              expr: |
                jvm_memory_used_bytes{area="heap"}
                /
                jvm_memory_max_bytes{area="heap"}
                * 100 > 85
              intervalMs: 15000
              maxDataPoints: 43200
          - refId: C
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: __expr__
            model:
              type: threshold
              expression: A
              conditions:
                - evaluator:
                    type: gt
                    params: [0]
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM heap usage critical on {{ $labels.instance }}"
          description: "JVM heap usage has been above 85% for more than 5 minutes."

      - uid: disk-usage-high
        title: Disk Usage High
        condition: C
        data:
          - refId: A
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: prometheus
            model:
              expr: |
                (1 - node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"}) * 100 > 80
              intervalMs: 15000
              maxDataPoints: 43200
          - refId: C
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: __expr__
            model:
              type: threshold
              expression: A
              conditions:
                - evaluator:
                    type: gt
                    params: [0]
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Disk usage above 80%"
          description: "Root filesystem usage has been above 80% for more than 5 minutes."

      - uid: db-pool-exhaustion
        title: Database Connection Pool Exhaustion
        condition: C
        data:
          - refId: A
            relativeTimeRange:
              from: 120
              to: 0
            datasourceUid: prometheus
            model:
              expr: |
                hikaricp_connections_idle < 2
              intervalMs: 15000
              maxDataPoints: 43200
          - refId: C
            relativeTimeRange:
              from: 120
              to: 0
            datasourceUid: __expr__
            model:
              type: threshold
              expression: A
              conditions:
                - evaluator:
                    type: gt
                    params: [0]
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "DB connection pool nearly exhausted on {{ $labels.instance }}"
          description: "HikariCP idle connections dropped below 2."

      - uid: container-restart-loop
        title: Container Restart Loop
        condition: C
        data:
          - refId: A
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: prometheus
            model:
              expr: |
                increase(container_restart_count{name=~"prod-.*"}[5m]) > 3
              intervalMs: 15000
              maxDataPoints: 43200
          - refId: C
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: __expr__
            model:
              type: threshold
              expression: A
              conditions:
                - evaluator:
                    type: gt
                    params: [0]
        for: 0s
        labels:
          severity: critical
        annotations:
          summary: "Container {{ $labels.name }} is in a restart loop"
          description: "Container has restarted more than 3 times in the last 5 minutes."
```

- [ ] **Step 3: Add SLACK_WEBHOOK_URL to monitoring .env.example**

Append to `deploy/monitoring/.env.example`:
```bash

# === Slack Alerting ===
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/CHANGE_ME
```

- [ ] **Step 4: Commit**

```bash
git add deploy/monitoring/grafana/provisioning/alerting/
git commit -m "feat(deploy): add Grafana alerting rules with Slack notifications"
```

---

## Task 8: Create Cloudflare Tunnel Configuration

Template config for `cloudflared` with routing rules for all subdomains.

**Files:**
- Create: `deploy/cloudflared/config.yml`

- [ ] **Step 1: Create cloudflared config.yml**

Create `deploy/cloudflared/config.yml`:
```yaml
# Cloudflare Tunnel configuration
# Replace TUNNEL_ID with the actual tunnel ID after running:
#   cloudflared tunnel create portfolio-tunnel
#
# After creating the tunnel, update the credentials-file path
# and add DNS records:
#   cloudflared tunnel route dns portfolio-tunnel portfolio.nanobyte.ca
#   cloudflared tunnel route dns portfolio-tunnel uatportfolio.nanobyte.ca
#   cloudflared tunnel route dns portfolio-tunnel status.nanobyte.ca
#   cloudflared tunnel route dns portfolio-tunnel grafana.nanobyte.ca

tunnel: TUNNEL_ID
credentials-file: /etc/cloudflared/TUNNEL_ID.json

ingress:
  # Prod backend API
  - hostname: portfolio.nanobyte.ca
    path: /api/.*
    service: http://localhost:10080

  # Prod backend auth endpoints
  - hostname: portfolio.nanobyte.ca
    path: /auth/.*
    service: http://localhost:10080

  # Prod backend health/actuator
  - hostname: portfolio.nanobyte.ca
    path: /health
    service: http://localhost:10080

  - hostname: portfolio.nanobyte.ca
    path: /actuator/.*
    service: http://localhost:10080

  # Prod backend WebSocket
  - hostname: portfolio.nanobyte.ca
    path: /ws/.*
    service: http://localhost:10080

  # Prod frontend (catch-all for portfolio.nanobyte.ca)
  - hostname: portfolio.nanobyte.ca
    service: http://localhost:10000

  # UAT backend API
  - hostname: uatportfolio.nanobyte.ca
    path: /api/.*
    service: http://localhost:20080

  # UAT backend auth endpoints
  - hostname: uatportfolio.nanobyte.ca
    path: /auth/.*
    service: http://localhost:20080

  # UAT backend health/actuator
  - hostname: uatportfolio.nanobyte.ca
    path: /health
    service: http://localhost:20080

  - hostname: uatportfolio.nanobyte.ca
    path: /actuator/.*
    service: http://localhost:20080

  # UAT backend WebSocket
  - hostname: uatportfolio.nanobyte.ca
    path: /ws/.*
    service: http://localhost:20080

  # UAT frontend (catch-all for uatportfolio.nanobyte.ca)
  - hostname: uatportfolio.nanobyte.ca
    service: http://localhost:20000

  # Uptime Kuma status page
  - hostname: status.nanobyte.ca
    service: http://localhost:13001

  # Grafana dashboards
  - hostname: grafana.nanobyte.ca
    service: http://localhost:13000

  # Catch-all (required by cloudflared)
  - service: http_status:404
```

- [ ] **Step 2: Commit**

```bash
git add deploy/cloudflared/config.yml
git commit -m "feat(deploy): add Cloudflare Tunnel routing config"
```

---

## Task 9: Create Server Setup Script

A one-time setup script to run on the Debian 13 server that installs Docker, cloudflared, UFW, and creates the deploy user and directory structure.

**Files:**
- Create: `deploy/scripts/setup-server.sh`

- [ ] **Step 1: Create setup-server.sh**

Create `deploy/scripts/setup-server.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Home Server Setup Script for Portfolio Construction App
# Run as root on a fresh Debian 13 server.
# Usage: sudo bash setup-server.sh
# ============================================================

echo "=== Portfolio Home Server Setup ==="

# --- 1. System updates ---
echo "[1/7] Updating system packages..."
apt-get update && apt-get upgrade -y

# --- 2. Install Docker ---
echo "[2/7] Installing Docker Engine..."
apt-get install -y ca-certificates curl gnupg

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable docker
systemctl start docker

echo "Docker version: $(docker --version)"

# --- 3. Install cloudflared ---
echo "[3/7] Installing cloudflared..."
curl -L --output /tmp/cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
dpkg -i /tmp/cloudflared.deb
rm /tmp/cloudflared.deb

echo "cloudflared version: $(cloudflared --version)"

# --- 4. Create deploy user ---
echo "[4/7] Creating deploy user..."
if id "deploy" &>/dev/null; then
    echo "User 'deploy' already exists, skipping."
else
    useradd -m -s /bin/bash -G docker deploy
    echo "User 'deploy' created and added to docker group."
fi

# --- 5. Create directory structure ---
echo "[5/7] Creating application directories..."
mkdir -p /opt/portfolio/{prod,uat,monitoring/prometheus,monitoring/loki,monitoring/grafana/provisioning/datasources,monitoring/grafana/provisioning/alerting,cloudflared,scripts,backups/prod,backups/uat}
chown -R deploy:deploy /opt/portfolio

# --- 6. Install and configure UFW ---
echo "[6/7] Configuring firewall (UFW)..."
apt-get install -y ufw

ufw default deny incoming
ufw default allow outgoing

# Allow SSH from local network only (adjust subnet as needed)
ufw allow from 192.168.0.0/16 to any port 22 proto tcp comment "SSH from LAN"
ufw allow from 10.0.0.0/8 to any port 22 proto tcp comment "SSH from LAN"

ufw --force enable
echo "UFW status:"
ufw status verbose

# --- 7. Enable unattended upgrades ---
echo "[7/7] Enabling unattended security upgrades..."
apt-get install -y unattended-upgrades apt-listchanges
dpkg-reconfigure -plow unattended-upgrades

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Next steps:"
echo "  1. SSH key setup:  ssh-copy-id deploy@this-server"
echo "  2. Tunnel setup:   sudo -u deploy cloudflared tunnel login"
echo "                     sudo -u deploy cloudflared tunnel create portfolio-tunnel"
echo "  3. Copy configs:   cp deploy files to /opt/portfolio/"
echo "  4. Create .env:    cp .env.example .env && edit with real values"
echo "  5. Start stacks:   cd /opt/portfolio/prod && docker compose up -d"
echo ""
```

- [ ] **Step 2: Make executable and commit**

```bash
chmod +x deploy/scripts/setup-server.sh
git add deploy/scripts/setup-server.sh
git commit -m "feat(deploy): add server setup script for Debian 13"
```

---

## Task 10: Create Database Backup Script

Daily `pg_dump` script that backs up both prod and UAT databases with 7-day daily and 30-day weekly retention.

**Files:**
- Create: `deploy/scripts/backup.sh`

- [ ] **Step 1: Create backup.sh**

Create `deploy/scripts/backup.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Database Backup Script
# Backs up prod and UAT PostgreSQL databases.
# Run daily via cron:
#   0 3 * * * /opt/portfolio/scripts/backup.sh >> /opt/portfolio/backups/backup.log 2>&1
# ============================================================

BACKUP_DIR="/opt/portfolio/backups"
DATE=$(date +%Y-%m-%d)
DAY_OF_WEEK=$(date +%u)  # 1=Monday, 7=Sunday
DAILY_RETENTION=7
WEEKLY_RETENTION=30

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

backup_db() {
    local env="$1"
    local port="$2"
    local db_user="$3"
    local db_name="$4"
    local container="${env}-postgres"
    local target_dir="${BACKUP_DIR}/${env}"

    mkdir -p "${target_dir}/daily" "${target_dir}/weekly"

    local daily_file="${target_dir}/daily/${db_name}-${DATE}.sql.gz"

    log "Backing up ${env} database (${db_name})..."

    docker exec "${container}" pg_dump -U "${db_user}" "${db_name}" | gzip > "${daily_file}"

    local size
    size=$(du -h "${daily_file}" | cut -f1)
    log "  Created: ${daily_file} (${size})"

    # Weekly backup on Sundays
    if [ "${DAY_OF_WEEK}" -eq 7 ]; then
        local weekly_file="${target_dir}/weekly/${db_name}-${DATE}.sql.gz"
        cp "${daily_file}" "${weekly_file}"
        log "  Weekly backup: ${weekly_file}"
    fi

    # Cleanup: remove daily backups older than retention period
    find "${target_dir}/daily" -name "*.sql.gz" -mtime +${DAILY_RETENTION} -delete
    log "  Cleaned daily backups older than ${DAILY_RETENTION} days"

    # Cleanup: remove weekly backups older than retention period
    find "${target_dir}/weekly" -name "*.sql.gz" -mtime +${WEEKLY_RETENTION} -delete
    log "  Cleaned weekly backups older than ${WEEKLY_RETENTION} days"
}

log "=== Starting database backups ==="

# Source env files to get credentials
if [ -f /opt/portfolio/prod/.env ]; then
    # shellcheck disable=SC1091
    source /opt/portfolio/prod/.env
    backup_db "prod" "15432" "${POSTGRES_USER}" "${POSTGRES_DB}"
else
    log "WARNING: /opt/portfolio/prod/.env not found, skipping prod backup"
fi

if [ -f /opt/portfolio/uat/.env ]; then
    # Save prod vars, load UAT
    PROD_USER="${POSTGRES_USER:-}"
    PROD_DB="${POSTGRES_DB:-}"
    # shellcheck disable=SC1091
    source /opt/portfolio/uat/.env
    backup_db "uat" "25432" "${POSTGRES_USER}" "${POSTGRES_DB}"
    # Restore prod vars
    POSTGRES_USER="${PROD_USER}"
    POSTGRES_DB="${PROD_DB}"
else
    log "WARNING: /opt/portfolio/uat/.env not found, skipping UAT backup"
fi

log "=== Backups complete ==="
```

- [ ] **Step 2: Make executable and commit**

```bash
chmod +x deploy/scripts/backup.sh
git add deploy/scripts/backup.sh
git commit -m "feat(deploy): add daily database backup script with retention"
```

---

## Task 11: Create CI Build Workflow

GitHub Actions workflow that runs on push/merge to `main`: tests all services, builds Docker images, tags them with `main-<short-sha>`, and pushes to ghcr.io.

**Files:**
- Create: `.github/workflows/build.yml`

- [ ] **Step 1: Create build.yml**

Create `.github/workflows/build.yml`:
```yaml
name: Build & Push Images

on:
  push:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ghcr.io/${{ github.repository_owner }}

jobs:
  test-backend:
    name: Backend Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: false

      - name: Test portfolio service
        run: ./gradlew :portfolio:test
        working-directory: backend

      - name: Test ingestion service
        run: ./gradlew :ingestion:test
        working-directory: backend

      - name: Test market-data service
        run: ./gradlew :market-data:test
        working-directory: backend

      - name: Test strategy service
        run: ./gradlew :strategy:test
        working-directory: backend

      - name: Test broker-gateway service
        run: ./gradlew :broker-gateway:test
        working-directory: backend

  test-frontend:
    name: Frontend Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: "20"
          cache: npm
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        run: npm ci
        working-directory: frontend

      - name: Lint
        run: npm run lint
        working-directory: frontend

      - name: Test
        run: npm run test:run
        working-directory: frontend

      - name: Type check and build
        run: npm run build
        working-directory: frontend
        env:
          VITE_API_URL: https://portfolio.nanobyte.ca

  build-and-push:
    name: Build & Push Docker Images
    needs: [test-backend, test-frontend]
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - name: Set image tag
        id: tag
        run: echo "tag=main-$(git rev-parse --short HEAD)" >> "$GITHUB_OUTPUT"

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      # Backend services
      - name: Build and push portfolio-backend
        uses: docker/build-push-action@v6
        with:
          context: ./backend/portfolio
          push: true
          tags: |
            ${{ env.IMAGE_PREFIX }}/portfolio-backend:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_PREFIX }}/portfolio-backend:latest
          cache-from: type=gha,scope=portfolio-backend
          cache-to: type=gha,mode=max,scope=portfolio-backend

      - name: Build and push portfolio-ingestion
        uses: docker/build-push-action@v6
        with:
          context: ./backend/ingestion
          push: true
          tags: |
            ${{ env.IMAGE_PREFIX }}/portfolio-ingestion:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_PREFIX }}/portfolio-ingestion:latest
          cache-from: type=gha,scope=portfolio-ingestion
          cache-to: type=gha,mode=max,scope=portfolio-ingestion

      - name: Build and push portfolio-market-data
        uses: docker/build-push-action@v6
        with:
          context: ./backend
          file: ./backend/market-data/Dockerfile
          push: true
          tags: |
            ${{ env.IMAGE_PREFIX }}/portfolio-market-data:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_PREFIX }}/portfolio-market-data:latest
          cache-from: type=gha,scope=portfolio-market-data
          cache-to: type=gha,mode=max,scope=portfolio-market-data

      - name: Build and push portfolio-strategy
        uses: docker/build-push-action@v6
        with:
          context: ./backend
          file: ./backend/strategy/Dockerfile
          push: true
          tags: |
            ${{ env.IMAGE_PREFIX }}/portfolio-strategy:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_PREFIX }}/portfolio-strategy:latest
          cache-from: type=gha,scope=portfolio-strategy
          cache-to: type=gha,mode=max,scope=portfolio-strategy

      - name: Build and push portfolio-broker-gateway
        uses: docker/build-push-action@v6
        with:
          context: ./backend
          file: ./backend/broker-gateway/Dockerfile
          push: true
          tags: |
            ${{ env.IMAGE_PREFIX }}/portfolio-broker-gateway:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_PREFIX }}/portfolio-broker-gateway:latest
          cache-from: type=gha,scope=portfolio-broker-gateway
          cache-to: type=gha,mode=max,scope=portfolio-broker-gateway

      # Frontend
      - name: Build and push portfolio-frontend
        uses: docker/build-push-action@v6
        with:
          context: ./frontend
          target: production
          push: true
          tags: |
            ${{ env.IMAGE_PREFIX }}/portfolio-frontend:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_PREFIX }}/portfolio-frontend:latest
          build-args: |
            VITE_API_URL=https://portfolio.nanobyte.ca
          cache-from: type=gha,scope=portfolio-frontend
          cache-to: type=gha,mode=max,scope=portfolio-frontend

      - name: Post build summary
        run: |
          echo "### Build Complete :white_check_mark:" >> "$GITHUB_STEP_SUMMARY"
          echo "" >> "$GITHUB_STEP_SUMMARY"
          echo "**Tag:** \`${{ steps.tag.outputs.tag }}\`" >> "$GITHUB_STEP_SUMMARY"
          echo "" >> "$GITHUB_STEP_SUMMARY"
          echo "| Image | Tag |" >> "$GITHUB_STEP_SUMMARY"
          echo "|-------|-----|" >> "$GITHUB_STEP_SUMMARY"
          echo "| portfolio-backend | ${{ steps.tag.outputs.tag }} |" >> "$GITHUB_STEP_SUMMARY"
          echo "| portfolio-ingestion | ${{ steps.tag.outputs.tag }} |" >> "$GITHUB_STEP_SUMMARY"
          echo "| portfolio-market-data | ${{ steps.tag.outputs.tag }} |" >> "$GITHUB_STEP_SUMMARY"
          echo "| portfolio-strategy | ${{ steps.tag.outputs.tag }} |" >> "$GITHUB_STEP_SUMMARY"
          echo "| portfolio-broker-gateway | ${{ steps.tag.outputs.tag }} |" >> "$GITHUB_STEP_SUMMARY"
          echo "| portfolio-frontend | ${{ steps.tag.outputs.tag }} |" >> "$GITHUB_STEP_SUMMARY"
          echo "" >> "$GITHUB_STEP_SUMMARY"
          echo "Deploy with: \`gh workflow run deploy.yml -f environment=prod -f tag=${{ steps.tag.outputs.tag }}\`" >> "$GITHUB_STEP_SUMMARY"

      - name: Notify Slack
        if: always()
        uses: slackapi/slack-github-action@v2.0.0
        with:
          webhook: ${{ secrets.SLACK_WEBHOOK_URL }}
          webhook-type: incoming-webhook
          payload: |
            {
              "text": "${{ job.status == 'success' && ':white_check_mark:' || ':x:' }} Build *${{ steps.tag.outputs.tag }}* ${{ job.status }}\n<${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}|View Run>"
            }
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "feat(ci): add build workflow to test, build, and push images to ghcr.io"
```

---

## Task 12: Create Deploy Workflow

Manual-trigger GitHub Actions workflow that SSHes into the home server and deploys a specific image tag to prod or UAT.

**Files:**
- Create: `.github/workflows/deploy.yml`

- [ ] **Step 1: Create deploy.yml**

Create `.github/workflows/deploy.yml`:
```yaml
name: Deploy

on:
  workflow_dispatch:
    inputs:
      environment:
        description: "Target environment"
        required: true
        type: choice
        options:
          - uat
          - prod
      tag:
        description: "Image tag to deploy (e.g., main-a1b2c3d)"
        required: true
        type: string

env:
  DEPLOY_PATH: /opt/portfolio

jobs:
  deploy:
    name: Deploy to ${{ github.event.inputs.environment }}
    runs-on: ubuntu-latest
    environment: ${{ github.event.inputs.environment }}
    steps:
      - name: Validate tag format
        run: |
          if [[ ! "${{ github.event.inputs.tag }}" =~ ^main-[a-f0-9]{7,}$ ]]; then
            echo "::error::Invalid tag format. Expected: main-<short-sha> (e.g., main-a1b2c3d)"
            exit 1
          fi

      - name: Install cloudflared
        run: |
          curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
          sudo dpkg -i cloudflared.deb

      - name: Configure SSH via Cloudflare Tunnel
        run: |
          mkdir -p ~/.ssh
          echo "${{ secrets.DEPLOY_SSH_KEY }}" > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key

          cat >> ~/.ssh/config << EOF
          Host portfolio-server
            HostName ${{ secrets.SERVER_HOSTNAME }}
            User deploy
            IdentityFile ~/.ssh/deploy_key
            StrictHostKeyChecking no
            ProxyCommand cloudflared access ssh --hostname %h
          EOF

      - name: Deploy to ${{ github.event.inputs.environment }}
        run: |
          ENV="${{ github.event.inputs.environment }}"
          TAG="${{ github.event.inputs.tag }}"

          ssh portfolio-server << REMOTE_SCRIPT
            set -euo pipefail
            cd ${DEPLOY_PATH}/${ENV}

            echo "=== Deploying ${TAG} to ${ENV} ==="

            # Update IMAGE_TAG in .env
            sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${TAG}/" .env

            # Pull new images
            echo "Pulling images..."
            docker compose pull

            # Restart services
            echo "Starting services..."
            docker compose up -d

            # Wait for health checks
            echo "Waiting for health checks..."
            sleep 10

            # Check container status
            echo "Container status:"
            docker compose ps

            # Verify all containers are healthy/running
            UNHEALTHY=\$(docker compose ps --format json | jq -r 'select(.Health != "healthy" and .Health != "" and .State != "running") | .Name' 2>/dev/null || true)
            if [ -n "\$UNHEALTHY" ]; then
              echo "WARNING: Unhealthy containers: \$UNHEALTHY"
              docker compose logs --tail=50 \$UNHEALTHY
              exit 1
            fi

            echo "=== Deploy complete ==="
          REMOTE_SCRIPT

      - name: Post deploy summary
        if: success()
        run: |
          echo "### Deploy Successful :rocket:" >> "$GITHUB_STEP_SUMMARY"
          echo "" >> "$GITHUB_STEP_SUMMARY"
          echo "- **Environment:** ${{ github.event.inputs.environment }}" >> "$GITHUB_STEP_SUMMARY"
          echo "- **Tag:** \`${{ github.event.inputs.tag }}\`" >> "$GITHUB_STEP_SUMMARY"
          echo "- **Triggered by:** ${{ github.actor }}" >> "$GITHUB_STEP_SUMMARY"

      - name: Notify Slack
        if: always()
        uses: slackapi/slack-github-action@v2.0.0
        with:
          webhook: ${{ secrets.SLACK_WEBHOOK_URL }}
          webhook-type: incoming-webhook
          payload: |
            {
              "text": "${{ job.status == 'success' && ':rocket:' || ':x:' }} Deploy *${{ github.event.inputs.tag }}* to *${{ github.event.inputs.environment }}* ${{ job.status }}\nTriggered by: ${{ github.actor }}\n<${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}|View Run>"
            }
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "feat(ci): add manual deploy workflow for prod and UAT environments"
```

---

## Task 13: Update .gitignore for Deploy Secrets

Ensure `.env` files in the deploy directory are never committed.

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Add deploy .env to .gitignore**

Append to `.gitignore`:
```
# Deploy environment files (contain secrets)
deploy/prod/.env
deploy/uat/.env
deploy/monitoring/.env
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: add deploy .env files to gitignore"
```

---

## Task 14: Update Infrastructure Documentation

Update `docs/reference/infrastructure.md` to document the home server deployment architecture.

**Files:**
- Modify: `docs/reference/infrastructure.md`

- [ ] **Step 1: Add home server deployment section**

Add a new section to `docs/reference/infrastructure.md` after the existing VPS section. The section should document:

- Home server hardware specs (AMD Ryzen 9 9950X, 96GB RAM, 4TB SSD, Debian 13)
- Directory structure (`/opt/portfolio/{prod,uat,monitoring,cloudflared,scripts}`)
- Docker Compose stacks (prod on ports 10xxx, UAT on ports 20xxx, monitoring on 1xxxx)
- Cloudflare Tunnel routing (portfolio.nanobyte.ca, uatportfolio.nanobyte.ca, status.nanobyte.ca, grafana.nanobyte.ca)
- Observability stack (Prometheus, Grafana, Loki, cAdvisor, Uptime Kuma + exporters)
- CI/CD flow (build.yml on merge to main → ghcr.io, deploy.yml manual trigger)
- Backup strategy (daily pg_dump, 7-day daily / 30-day weekly retention)
- Port reference table (all ports across all stacks)
- GitHub Secrets required (DEPLOY_SSH_KEY, SERVER_HOSTNAME, SLACK_WEBHOOK_URL)
- Server setup procedure (reference to setup-server.sh)
- Deploy procedure (`gh workflow run deploy.yml -f environment=prod -f tag=main-abc1234`)
- Rollback procedure (re-deploy previous tag)

- [ ] **Step 2: Commit**

```bash
git add docs/reference/infrastructure.md
git commit -m "docs: add home server deployment architecture to infrastructure reference"
```

---

## Task 15: Frontend VITE_API_URL Handling for Multi-Environment

The frontend currently hard-codes `VITE_API_URL` at build time. Since both prod and UAT are built from the same image, the frontend needs to determine the API URL at runtime based on its own hostname, or we need separate frontend images per environment.

**Decision: Separate frontend images per environment.** The build workflow will build two frontend images — one for prod (`VITE_API_URL=https://portfolio.nanobyte.ca`) and one for UAT (`VITE_API_URL=https://uatportfolio.nanobyte.ca`).

**Files:**
- Modify: `.github/workflows/build.yml`
- Modify: `deploy/uat/docker-compose.yml`

- [ ] **Step 1: Update build.yml to produce two frontend images**

In `.github/workflows/build.yml`, replace the single frontend build step with two:

```yaml
      # Frontend — prod
      - name: Build and push portfolio-frontend (prod)
        uses: docker/build-push-action@v6
        with:
          context: ./frontend
          target: production
          push: true
          tags: |
            ${{ env.IMAGE_PREFIX }}/portfolio-frontend:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_PREFIX }}/portfolio-frontend:latest
          build-args: |
            VITE_API_URL=https://portfolio.nanobyte.ca
          cache-from: type=gha,scope=portfolio-frontend-prod
          cache-to: type=gha,mode=max,scope=portfolio-frontend-prod

      # Frontend — UAT
      - name: Build and push portfolio-frontend-uat
        uses: docker/build-push-action@v6
        with:
          context: ./frontend
          target: production
          push: true
          tags: |
            ${{ env.IMAGE_PREFIX }}/portfolio-frontend-uat:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_PREFIX }}/portfolio-frontend-uat:latest
          build-args: |
            VITE_API_URL=https://uatportfolio.nanobyte.ca
          cache-from: type=gha,scope=portfolio-frontend-uat
          cache-to: type=gha,mode=max,scope=portfolio-frontend-uat
```

- [ ] **Step 2: Update UAT docker-compose.yml frontend image**

In `deploy/uat/docker-compose.yml`, change the frontend image:
```yaml
  frontend:
    image: ghcr.io/saurabhbilakhia/portfolio-frontend-uat:${IMAGE_TAG}
```

- [ ] **Step 3: Update build summary to include both frontend images**

Add `portfolio-frontend-uat` to the summary table in `build.yml`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/build.yml deploy/uat/docker-compose.yml
git commit -m "feat(ci): build separate frontend images for prod and UAT environments"
```

---

## Summary

| Task | Description | Key Files |
|------|-------------|-----------|
| 1 | Enable Prometheus actuator endpoint | 5 × `application.yml` |
| 2 | Create UAT Spring profiles | 5 × `application-uat.yml` |
| 3 | Update prod Spring profiles with forward headers | 5 × `application-prod.yml` |
| 4 | Create prod Docker Compose stack | `deploy/prod/docker-compose.yml`, `.env.example` |
| 5 | Create UAT Docker Compose stack | `deploy/uat/docker-compose.yml`, `.env.example` |
| 6 | Create monitoring Docker Compose stack | `deploy/monitoring/docker-compose.yml` + configs |
| 7 | Create Grafana alerting provisioning | `deploy/monitoring/grafana/provisioning/alerting/*` |
| 8 | Create Cloudflare Tunnel config | `deploy/cloudflared/config.yml` |
| 9 | Create server setup script | `deploy/scripts/setup-server.sh` |
| 10 | Create database backup script | `deploy/scripts/backup.sh` |
| 11 | Create CI build workflow | `.github/workflows/build.yml` |
| 12 | Create deploy workflow | `.github/workflows/deploy.yml` |
| 13 | Update .gitignore | `.gitignore` |
| 14 | Update infrastructure docs | `docs/reference/infrastructure.md` |
| 15 | Frontend multi-env image handling | `build.yml`, UAT `docker-compose.yml` |
