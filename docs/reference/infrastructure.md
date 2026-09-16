# Infrastructure Reference

Complete infrastructure reference for the Portfolio Construction App. Covers architecture, Docker, CI/CD, Cloudflare Tunnel, scripts, and environment files.

---


## Docker

### backend/portfolio/Dockerfile -- Full Multi-Stage Build

Two-stage build for local development and CI.

**Stage 1: Build** (`gradle:8.10-jdk21-alpine`)
- Copies `build.gradle.kts`, `settings.gradle.kts`, and `gradle/` first for dependency caching
- Runs `gradle dependencies --no-daemon` to warm cache (tolerates failures with `|| true`)
- Copies `src/` and builds with `gradle build -x test --no-daemon` (tests skipped, run separately)

**Stage 2: Runtime** (`eclipse-temurin:21-jre`)
- Installs CA certificates and HTTP tools (`apt-get install ca-certificates curl wget`)
- Imports custom CA certs from `certs/*.crt` into JVM truststore via `keytool`
- Creates non-root user `appuser:appgroup` (UID/GID 1001)
- Copies `app.jar` from build stage
- Health check: `curl --fail --silent http://localhost:8080/health` (30s interval, 3s timeout, 30s start period, 3 retries)
- Entrypoint: `java -jar app.jar`
- Exposes port 8080


### backend/broker-gateway/Dockerfile -- Full Multi-Stage Build

Two-stage build for the broker gateway microservice.

**Stage 1: Build** (`gradle:8.10-jdk21-alpine`)
- Copies `build.gradle.kts`, `settings.gradle.kts`, and `gradle/` first for dependency caching
- Runs `gradle dependencies --no-daemon` to warm cache (tolerates failures with `|| true`)
- Copies `src/` and builds with `gradle build -x test --no-daemon`

**Stage 2: Runtime** (`eclipse-temurin:21-jre`)
- Creates non-root user `appuser:appgroup` (UID/GID 1001)
- Copies `app.jar` from build stage
- Health check: `curl --fail --silent http://localhost:8084/actuator/health` (30s interval, 3s timeout, 30s start period, 3 retries)
- Entrypoint: `java -jar app.jar`
- Exposes port 8084

### frontend/Dockerfile -- Full Multi-Stage Build

Three-stage build.

**Stage 1: Development** (`node:20-alpine`)
- `npm ci` for deterministic installs
- Runs Vite dev server on `0.0.0.0:3000` (used by docker-compose.yml local dev)

**Stage 2: Build** (`node:20-alpine`)
- Accepts `VITE_API_URL` build arg for backend URL injection
- Runs `npm run build` producing `dist/`

**Stage 3: Production** (`nginx:alpine`)
- Copies `dist/` from build stage to `/usr/share/nginx/html`
- Copies `nginx.conf` for SPA routing
- Health check: `wget --no-verbose --tries=1 --spider http://localhost:80/` (30s interval, 3s timeout, 5s start period)
- Exposes port 80


### docker-compose.yml -- Local Development

Full-stack local development environment.

| Service | Image | Ports | Notes |
|---------|-------|-------|-------|
| redis | `redis:7-alpine` | 6379:6379 | Health check via `redis-cli ping` |
| postgres | `postgres:16-alpine` | 5432:5432 | Volume `postgres_data`, health check via `pg_isready` |
| backend | Build from `./backend/portfolio/Dockerfile` | 8080:8080, 5005:5005 | Debug port 5005, JAVA_TOOL_OPTIONS for JDWP, `restart: unless-stopped` |
| ingestion-service | Build from `./backend/ingestion/Dockerfile` | 8081:8081 | Separate ingestion microservice with own `ingestion` DB schema, depends on postgres + redis, `restart: unless-stopped` |
| market-data-service | Build from `./backend` context, `market-data/Dockerfile` | 8082:8082 | IBKR market data + WebSocket streaming, `market_data` DB schema, depends on postgres + redis |
| strategy-service | Build from `./backend` context, `strategy/Dockerfile` | 8083:8083 | Strategy engine + wheel writer, `strategy` DB schema, depends on postgres + redis |
| broker-gateway-service | Build from `./backend/broker-gateway/Dockerfile` | 8084:8084 | Broker data gateway (IBKR, Questrade, Wealthsimple), `broker_gateway` DB schema, depends on postgres + redis |
| frontend | Build from `./frontend/Dockerfile` target=development | 3000:3000 | Bind mounts `src/`, `public/`, `index.html` as read-only for hot reload |

**Key configuration:**
- Network: `portfolio-network` (bridge driver)
- Backend depends on postgres + redis (both `service_healthy`)
- Frontend depends on backend, ingestion-service, market-data-service, strategy-service
- Profile: `SPRING_PROFILES_ACTIVE=local`
- Backend health check: `wget http://localhost:8080/health` (30s interval, 30s start period)
- Ingestion health check: `wget http://localhost:8081/actuator/health` (30s interval, 60s start period)
- Market data health check: `wget http://localhost:8082/actuator/health` (30s interval, 60s start period)
- Strategy health check: `wget http://localhost:8083/actuator/health` (30s interval, 60s start period)
- Broker gateway health check: `wget http://localhost:8084/actuator/health` (30s interval, 60s start period)
- All backend services have `restart: unless-stopped` for Docker DNS race condition resilience
- All services depend on postgres + redis (both `service_healthy`)
- Environment variables use defaults: `POSTGRES_DB=portfolio`, `POSTGRES_USER=portfolio`, `POSTGRES_PASSWORD=portfolio`
- Vite dev server proxies `/ingestion-api` to `http://localhost:8081` (ingestion service), rewrites path prefix
- Vite dev server proxies `/market-data-api` to `http://localhost:8082` (market data service), rewrites path prefix
- Vite dev server proxies `/strategy-api` to `http://localhost:8083` (strategy service), rewrites path prefix
- Vite dev server proxies `/ws/quotes` to `ws://localhost:8082` (WebSocket for real-time quotes)
- Market-data and strategy services use Gradle composite builds with shared `backend/common/` module
- `market-data-service` and `broker-gateway-service` include `extra_hosts: ["host.docker.internal:host-gateway"]` for connecting to IB Gateway/TWS running on the Docker host

### IB Gateway / TWS Prerequisites

To use IBKR integration locally:
1. Install and run IB Gateway (or TWS) on the Docker host
2. Configure IB Gateway to listen on port **4001** (the default API port for live; paper uses 4002)
3. Enable **API connections** in IB Gateway settings (Configure > Settings > API > Settings)
4. Check "Allow connections from localhost" (Docker containers reach the host via `host.docker.internal`)
5. Set `IBKR_HOST=host.docker.internal` in your `.env` file
6. Market-data-service uses client ID `1` (`IBKR_CLIENT_ID`), broker-gateway uses client ID `2` (`IBKR_GATEWAY_CLIENT_ID`)


## CI/CD Workflows

### .github/workflows/build.yml -- Build & Push Images

**Triggers:**
- Push to `main` branch (test + build + push)
- Pull request to `main` (test only, no image builds)

**Jobs:**

| Job | Runs On | Steps |
|-----|---------|-------|
| `test-backend` | PR + push | JDK 21, Gradle test for all 5 services (portfolio, ingestion, market-data, strategy, broker-gateway) |
| `test-frontend` | PR + push | Node 20, `npm ci`, lint, test, build |
| `build-and-push` | push only | Docker Buildx, build 7 images, push to GHCR with `main-<sha>` + `latest` tags, Slack notification |

**Images built (7):**
- `portfolio-backend`, `portfolio-ingestion`, `portfolio-market-data`, `portfolio-strategy`, `portfolio-broker-gateway`
- `portfolio-frontend` (prod, `VITE_API_URL=https://portfolio.nanobyte.ca`)
- `portfolio-frontend-uat` (UAT, `VITE_API_URL=https://uatportfolio.nanobyte.ca`)

### .github/workflows/deploy.yml -- Deploy to Home Server

**Triggers:** Manual workflow dispatch via GitHub UI

**Inputs:**
- `environment` (required): `prod` or `uat`
- `tag` (required): Docker image tag (e.g., `main-abc1234`)

**Steps:**
1. Validate tag format (`main-<short-sha>`)
2. Fetch secrets from Vault (`vault.nanobyte.ca`) via AppRole authentication
3. Generate `.env` file from Vault response, append `IMAGE_TAG`, validate key count
4. Install cloudflared and configure SSH via Cloudflare Tunnel (pinned host key)
5. SCP generated `.env` to server at `/opt/portfolio/{env}/.env`
6. SSH to server: `docker compose pull && docker compose up -d`, wait for health checks
7. Cleanup, post-deploy summary, Slack notification

**Rollback:** Re-run workflow with previous tag.

---

## Nginx

### frontend/nginx.conf -- Frontend Container

Serves static files inside the frontend Docker container.

- Root: `/usr/share/nginx/html`
- Gzip: enabled for text, CSS, XML, JavaScript
- Security headers: `X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`
- Static asset caching: 1 year with `Cache-Control: public, immutable` for js, css, images, fonts
- SPA routing: `try_files $uri $uri/ /index.html`
- Health endpoint: `/nginx-health` returns 200 "healthy\n" (access log off)

---

## Scripts

All scripts located in `scripts/` and `deploy/scripts/`.

### integration-test.sh -- Integration Test Suite

**Usage:** `bash scripts/integration-test.sh`

**Prerequisites:** All services must be running via `docker compose up -d`

**Tests performed:**
- Health checks for all backend services
- API endpoint validation
- Database connectivity
- WebSocket streaming
- Frontend build verification

---

## Environment Files

### config/.env.example -- Template

Reference template with all configurable variables and placeholder values. Located at `config/.env.example` in the repository root.

| Variable | Default |
|----------|---------|
| `POSTGRES_DB` | `portfolio` |
| `POSTGRES_USER` | `portfolio` |
| `POSTGRES_PASSWORD` | `changeme` |
| `APP_VERSION` | `0.0.1-SNAPSHOT` |
| `APP_ENVIRONMENT` | `local` |
| `SPRING_PROFILES_ACTIVE` | `local` |
| `VITE_API_URL` | `http://localhost:8080` |
| `SNAPTRADE_CLIENT_ID` | (empty) |
| `SNAPTRADE_CONSUMER_KEY` | (empty) |
| `SNAPTRADE_REDIRECT_URI` | `http://localhost:3000/brokers/connections` |
| `BROKER_ENCRYPTION_KEY` | (empty) |

### Environment Files (Consolidated)

Previously `.env.local`, `.env.dev`, and `.env.prod` were at the repository root. These have been consolidated into `config/.env.example` which serves as the single template. Per-environment values are now configured via HashiCorp Vault (fetched at deploy time).

Key environment differences:
- **Local**: Default `portfolio` password for database, `SPRING_PROFILES_ACTIVE=local`
- **UAT (uatportfolio.nanobyte.ca)**: `VITE_API_URL=https://uatportfolio.nanobyte.ca`, secrets from Vault
- **Production (portfolio.nanobyte.ca)**: `VITE_API_URL=https://portfolio.nanobyte.ca`, `SPRING_PROFILES_ACTIVE=prod`, secrets from Vault

---

## Local Development

### Prerequisites
- Docker and Docker Compose
- Node.js 20 (for frontend development)
- Git
- **No local JDK required** -- all backend compilation runs inside Docker containers

### Quick Start

```bash
git clone <repo-url>
cd portfolio-app
cp config/.env.example .env

# Start all services
docker compose up -d

# Verify
curl http://localhost:8080/health
curl http://localhost:8080/api/v1/version

# Open frontend
open http://localhost:3000
```

### Running Services Individually

```bash
# Database only
docker compose up -d postgres

# Backend (inside Docker -- no local JDK)
docker compose exec backend ./gradlew bootRun

# Frontend (local Node.js)
cd frontend
npm install
npm run dev
```

### Debugging

**Backend:** Docker Compose exposes debug port 5005 (JDWP). Connect your IDE remote debugger to `localhost:5005`.

**Frontend:** React Developer Tools browser extension + Vite-provided source maps.

### Common Tasks

| Task | Command |
|------|---------|
| Reset database | `docker compose down -v && docker compose up -d` |
| Run backend tests | `docker compose exec backend ./gradlew test` |
| Run specific test | `docker compose exec backend ./gradlew test --tests "HealthControllerTest"` |
| Run frontend tests | `cd frontend && npm run test:run` |
| Frontend lint | `cd frontend && npm run lint` |
| Frontend build | `cd frontend && npm run build` |
| Check outdated deps (backend) | `docker compose exec backend ./gradlew dependencyUpdates` |
| Check outdated deps (frontend) | `cd frontend && npm outdated` |
| Tail backend logs | `docker compose logs -f backend` |

### Code Style

**Backend (Kotlin):** Follow Kotlin coding conventions, use data classes for DTOs, prefer immutability, use meaningful names.

**Frontend (TypeScript):** ESLint rules enforced, functional components, TypeScript strict mode, React hooks best practices.

---

## Home Server Deployment

Production and UAT environments hosted on a dedicated home server. Monitoring, Vault, backups, shared Postgres/Redis, and server bootstrap are owned by the separate **nanobyte-services** repo; pc owns only its application services, CI/CD workflows, and local dev stack.

### Hardware

- **CPU:** AMD Ryzen 9 9950X (16 cores / 32 threads)
- **RAM:** 96GB DDR5
- **Storage:** 4TB NVMe SSD
- **OS:** Debian 13 (Trixie)
- **Location:** On-premises data center

### Directory Structure

```
/opt/portfolio/
├── prod/                 # Production environment
│   ├── docker-compose.yml
│   ├── .env
│   └── (service volumes)
├── uat/                  # User acceptance testing environment
│   ├── docker-compose.yml
│   ├── .env
│   └── (service volumes)
└── cloudflared/          # Cloudflare Tunnel daemon
    └── config.yml
```

**nanobyte-services-managed paths** (not under pc's `/opt/portfolio/`): monitoring stack at `/opt/nanobyte-services/monitoring/`, database backups at `/opt/backups`, shared infra stack under `/opt/nanobyte-services/infra/`.

### Docker Compose Stacks

Two pc Docker Compose stacks run simultaneously; shared Postgres/Redis, monitoring, and Vault come from nanobyte-services stacks (referenced by container hostname over external networks — see ADR-0017).

#### Production Stack (ports 10000-10084)

| Service | Image | Ports | Notes |
|---------|-------|-------|-------|
| `prod-frontend` | `ghcr.io/nanobyte-canada/portfolio-frontend:main-*` | 10000:80 | React SPA via Nginx |
| `prod-backend` | `ghcr.io/nanobyte-canada/portfolio-backend:main-*` | 10080:8080 | Main API service |
| `prod-ingestion` | `ghcr.io/nanobyte-canada/portfolio-ingestion:main-*` | 10081:8081 | Data ingestion service |
| `prod-market-data` | `ghcr.io/nanobyte-canada/portfolio-market-data:main-*` | 10082:8082 | IBKR + WebSocket streaming |
| `prod-strategy` | `ghcr.io/nanobyte-canada/portfolio-strategy:main-*` | 10083:8083 | Strategy engine |
| `prod-broker-gateway` | `ghcr.io/nanobyte-canada/portfolio-broker-gateway:main-*` | 10084:8084 | Broker adapter service |

Postgres (`prod-postgres`) and Redis (`prod-redis`) are provided by the shared nanobyte-services infra stack and joined over the external `prod-internal-network`/`infra-prod-network` networks — no host ports on pc's compose.

#### UAT Stack (ports 20000-20084)

| Service | Image | Ports | Notes |
|---------|-------|-------|-------|
| `uat-frontend` | `ghcr.io/nanobyte-canada/portfolio-frontend:main-*` | 20000:80 | React SPA via Nginx |
| `uat-backend` | `ghcr.io/nanobyte-canada/portfolio-backend:main-*` | 20080:8080 | Main API service |
| `uat-ingestion` | `ghcr.io/nanobyte-canada/portfolio-ingestion:main-*` | 20081:8081 | Data ingestion service |
| `uat-market-data` | `ghcr.io/nanobyte-canada/portfolio-market-data:main-*` | 20082:8082 | IBKR + WebSocket streaming |
| `uat-strategy` | `ghcr.io/nanobyte-canada/portfolio-strategy:main-*` | 20083:8083 | Strategy engine |
| `uat-broker-gateway` | `ghcr.io/nanobyte-canada/portfolio-broker-gateway:main-*` | 20084:8084 | Broker adapter service |

Postgres (`uat-postgres`) and Redis (`uat-redis`) are provided by the shared nanobyte-services infra stack and joined over the external `uat-internal-network`/`infra-uat-network` networks — no host ports on pc's compose.

The monitoring stack (Grafana, Uptime Kuma, Prometheus, Loki, exporters) and shared Vault are owned by nanobyte-services; see `https://grafana.nanobyte.ca` and `https://vault.nanobyte.ca`.

### Cloudflare Tunnel Routing

All external traffic routed through Cloudflare Tunnel (no exposed ports). Zero-trust network access.

> **Note:** The pc application routes (`portfolio.nanobyte.ca`, `uatportfolio.nanobyte.ca`) target pc's app services. The `status.nanobyte.ca`, `grafana.nanobyte.ca`, and `vault.nanobyte.ca` routes target services now owned and managed by **nanobyte-services** (Uptime Kuma, Grafana, shared Vault — host ports 13001, 13000, and 18200 are unchanged by the migration). Tunnel config lives in `deploy/cloudflared/config.yml`, generated by `deploy/scripts/setup-cloudflared-tunnel.sh`.

| Hostname | Target | Notes |
|----------|--------|-------|
| `portfolio.nanobyte.ca` | `http://localhost:10000` (frontend), `http://localhost:10080` (backend) | Production environment |
| `uatportfolio.nanobyte.ca` | `http://localhost:20000` (frontend), `http://localhost:20080` (backend) | UAT environment, Cloudflare Access protected |
| `status.nanobyte.ca` | `http://localhost:13001` | Uptime Kuma public status page |
| `grafana.nanobyte.ca` | `http://localhost:13000` | Grafana dashboards, Cloudflare Access protected |
| `vault.nanobyte.ca` | `http://localhost:18200` | HashiCorp Vault UI, Cloudflare Access protected |

### CI/CD Workflows

#### build.yml -- Build and Publish Images

**Triggers:** Push to `main` branch

**Steps:**
1. Run backend and frontend test jobs (`test-backend`, `test-frontend`)
2. Build 7 Docker images in parallel (backend, ingestion, market-data, strategy, broker-gateway, frontend, frontend-uat)
3. Tag images with `main-<sha>` (e.g., `main-abc1234`)
4. Push to GitHub Container Registry (`ghcr.io/nanobyte-canada/portfolio/*`)

**Artifacts:** Docker images published to `ghcr.io/nanobyte-canada/portfolio-{service}:main-{sha}`

#### deploy.yml -- Deploy to Home Server

**Triggers:** Manual workflow dispatch via GitHub UI

**Inputs:**
- `environment` (required): `prod` or `uat`
- `tag` (required): Docker image tag (e.g., `main-abc1234`)

**Steps:**
1. Validate tag format (`main-<short-sha>`)
2. Fetch secrets from Vault (`https://vault.nanobyte.ca`) via AppRole authentication
3. Generate `.env` file from Vault response, append `IMAGE_TAG`, validate key count
4. Install cloudflared and configure SSH via Cloudflare Tunnel
5. SCP generated `.env` to server at `${DEPLOY_PATH}/${ENV}/.env`
6. SSH to server: `docker compose pull && docker compose up -d`, wait for health checks
7. Cleanup: remove `/tmp/deploy.env` (always runs)
8. Post-deploy summary (includes "Secrets: Fetched from Vault")
9. Send Slack notification with deployment status

**Vault integration:**
- Vault address: `https://vault.nanobyte.ca`
- Auth method: AppRole (`VAULT_ROLE_ID` + `VAULT_SECRET_ID`)
- Secret path: `secret/data/portfolio/{environment}` (KV v2)
- Error handling: fails on null token, empty secrets, or low key count

**Secrets required:**
- `VAULT_ROLE_ID` -- Vault AppRole role ID
- `VAULT_SECRET_ID` -- Vault AppRole secret ID
- `DEPLOY_SSH_KEY` -- SSH private key for home server access
- `SERVER_HOSTNAME` -- Home server hostname (via Cloudflare Tunnel)
- `SLACK_WEBHOOK_URL` -- Slack channel webhook for notifications

**Rollback procedure:** Re-run workflow with previous tag

### Security

Zero-trust architecture with defense in depth.

#### Network Security

- **No exposed ports:** All inbound traffic via Cloudflare Tunnel only
- **UFW firewall:**
  - Default: deny all inbound
  - Allow SSH (port 22) from LAN only (`192.168.1.0/24`)
  - Allow outbound (unrestricted)
- **Cloudflare Access:** Applied to `uatportfolio.nanobyte.ca` and `grafana.nanobyte.ca` (email authentication)

#### Application Security

- **Non-root containers:** All Docker containers run as unprivileged users
- **Separate secrets:** Production and UAT environments have isolated `.env` files with unique credentials
- **Secret rotation:** Database passwords and JWT keys rotated quarterly via Ansible playbook
- **HTTPS only:** All external endpoints use TLS 1.3 (terminated at Cloudflare)

### Cloudflare Tunnel Setup

Cloudflare Tunnel runs as a systemd service (not a Docker container) to avoid circular dependency with Docker.

**Setup script:** `deploy/scripts/setup-cloudflared-tunnel.sh`

The script:
1. Authenticates to Cloudflare (manual browser step)
2. Creates the tunnel
3. Writes config to `/etc/cloudflared/config.yml`
4. Adds DNS routes for all hostnames
5. Installs as systemd service

**Config location:** `/etc/cloudflared/config.yml`

**SSH access:** `ssh.nanobyte.ca` routes to `localhost:22` via the tunnel. GitHub Actions uses `cloudflared access ssh` as a ProxyCommand with pinned host key verification.

> **Note:** pc is the only home for this tunnel config (nanobyte-services has no cloudflared files). Some routed services (Grafana, Uptime Kuma, Vault) are backed by nanobyte-services containers, but the tunnel routes and host ports are unchanged by that migration.

### IBKR Gateway Integration

Both production and UAT stacks include an IBKR Gateway container for live Interactive Brokers connectivity.

**Container:** `ghcr.io/gnzsnz/ib-gateway:10.30.1t`
- IBKR Gateway + IBC (automated login) + VNC server for remote access
- Handles IBKR's daily forced reconnect and automated re-login via IBC
- VNC port bound to `127.0.0.1` only (not exposed via Cloudflare Tunnel)
- VNC accessible locally for debugging the gateway UI

**Trading Modes:**
- **Production:** Live trading mode (port 4001 mapped to 14001)
- **UAT:** Paper trading mode (port 4002 mapped to 24002)
- **Local development:** Paper trading mode (port 4002 mapped to 4002)

**VNC Ports:**
- Production: `127.0.0.1:15900:5900` (localhost only)
- UAT: `127.0.0.1:25900:5900` (localhost only)
- Local: `127.0.0.1:5900:5900` (localhost only)

**Configuration:**
- Environment variables: `IBKR_USERNAME`, `IBKR_PASSWORD`, `TRADING_MODE` (live/paper)
- `market-data-service` and `broker-gateway-service` connect to gateway via `ib-gateway:4001` (prod) or `ib-gateway:4002` (UAT/local)
- IBC configuration handles timeout extensions and daily reconnect at 11:45 PM ET

### Port Reference Table

Complete port allocation for pc's application stacks (prod and uat). Shared infra, monitoring, and Vault ports are allocated by nanobyte-services (e.g., Grafana 13000, Uptime Kuma 13001, Vault `127.0.0.1:18200:8200`) and must not overlap pc's `1xxxx`/`2xxxx` ranges.

| Port Range | Stack | Service | Protocol |
|------------|-------|---------|----------|
| 10000 | Production | Frontend (Nginx) | HTTP |
| 10080 | Production | Backend API | HTTP |
| 10081 | Production | Ingestion service | HTTP |
| 10082 | Production | Market data service | HTTP |
| 10083 | Production | Strategy service | HTTP |
| 10084 | Production | Broker gateway | HTTP |
| 20000 | UAT | Frontend (Nginx) | HTTP |
| 20080 | UAT | Backend API | HTTP |
| 20081 | UAT | Ingestion service | HTTP |
| 20082 | UAT | Market data service | HTTP |
| 20083 | UAT | Strategy service | HTTP |
| 20084 | UAT | Broker gateway | HTTP |

**Note:** All ports are bound to `0.0.0.0`. UFW firewall protects all ports. Only Cloudflare Tunnel has local access to application ports.

### GitHub Secrets Required

| Secret | Purpose |
|--------|---------|
| `DEPLOY_SSH_KEY` | Ed25519 SSH private key for `deploy` user |
| `SERVER_HOSTNAME` | `ssh.nanobyte.ca` (Cloudflare Tunnel SSH hostname) |
| `SSH_KNOWN_HOSTS` | Server SSH host key fingerprint for MITM prevention |
| `VAULT_ROLE_ID` | Vault AppRole role ID for secret fetching |
| `VAULT_SECRET_ID` | Vault AppRole secret ID for secret fetching |
| `SLACK_WEBHOOK_URL` | Slack channel webhook for deploy notifications |

### Server Setup

Initial server provisioning automated via script at `deploy/scripts/setup-server.sh`.

**Setup script actions:**
1. Update system packages
2. Install Docker Engine and Docker Compose plugin
3. Install cloudflared (Cloudflare Tunnel daemon)
4. Create `deploy` user (added to docker group)
5. Create `/opt/portfolio/` directory structure (`prod`, `uat`, `cloudflared`, `scripts`)
6. Install UFW and apply firewall rules (SSH from LAN only)
7. Enable unattended security upgrades

Monitoring, Vault, backups, and shared infra are provisioned by nanobyte-services' `bootstrap-server.sh` — not by this script.

**Run setup:**
```bash
ssh user@nanobyte.ca 'bash -s' < deploy/scripts/setup-server.sh
```

**Verification:**
```bash
# Check services
systemctl status docker cloudflared

# Check firewall
sudo ufw status verbose

# Check directory structure
ls -lah /opt/portfolio/
```

### Deploy Procedure

Standard deployment workflow using GitHub Actions.

**1. Trigger build (automatic on push to main):**
```bash
git push origin main
# build.yml runs automatically
```

**2. Trigger deployment (manual):**
```bash
# Via GitHub CLI
gh workflow run deploy.yml -f environment=prod -f tag=main-abc1234

# Via GitHub UI
# Actions → deploy.yml → Run workflow → Select environment and tag
```

**3. Monitor deployment:**
```bash
# Watch workflow run
gh run watch

# Check Slack channel for notification
# Or SSH to server and check logs
ssh user@nanobyte.ca
docker compose -f /opt/portfolio/prod/docker-compose.yml logs -f
```

**4. Verify deployment:**
- Check `status.nanobyte.ca` for service health
- Check `grafana.nanobyte.ca` for metrics
- Smoke test application at `portfolio.nanobyte.ca`

**Rollback:**
```bash
# Find previous successful tag
gh run list --workflow=build.yml --status=success

# Re-deploy previous tag
gh workflow run deploy.yml -f environment=prod -f tag=main-xyz5678
```

---

## Cross-References

- For detailed environment variable documentation and defaults, see [configurations.md](configurations.md)
- For database schema and migration details, see [database-schema.md](database-schema.md)
- For backend service architecture, see [backend-services.md](backend-services.md)
