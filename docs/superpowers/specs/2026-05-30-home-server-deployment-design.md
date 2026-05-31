# Home Server Deployment Design

## Overview

Deploy the Portfolio Construction App on a dedicated home server (AMD Ryzen 9 9950X, 96GB RAM, 4TB SSD, Debian 13) with production and UAT environments, full observability, and CI/CD automation. Accessible to users via Cloudflare Tunnel with no exposed ports.

## Goals

- Production-quality experience for 25-50 users, designed to scale to public
- Two isolated environments (prod + UAT) on the same physical server
- Full observability with Slack alerting on prod
- Automated CI builds with manual deployment triggers
- No ports exposed to the internet — all traffic via Cloudflare Tunnel

## Non-Goals

- Multi-node / Kubernetes orchestration (single server is sufficient)
- CDN for API responses (Cloudflare caches static assets only)
- Blue-green or canary deployments (simple restart with health checks is sufficient)

---

## 1. Server Setup & OS Configuration

### Base System (Debian 13)

- Docker Engine + Docker Compose plugin (official Docker repo)
- `cloudflared` daemon as a systemd service
- UFW firewall: deny all inbound, allow SSH from LAN only
- Unattended security upgrades enabled
- Dedicated non-root user `deploy` that runs Docker and owns application directories

### Directory Structure

```
/opt/portfolio/
├── prod/
│   ├── docker-compose.yml
│   ├── .env
│   └── data/              # postgres and redis persistent volumes
├── uat/
│   ├── docker-compose.yml
│   ├── .env
│   └── data/
├── monitoring/
│   ├── docker-compose.yml
│   ├── prometheus/
│   │   └── prometheus.yml
│   ├── grafana/
│   │   └── provisioning/  # datasources, dashboards, alerting
│   ├── loki/
│   │   └── loki-config.yml
│   └── data/
└── cloudflared/
    └── config.yml
```

Three independent Docker Compose stacks: `prod`, `uat`, and `monitoring`.

---

## 2. Cloudflare Tunnel & Routing

### Tunnel

One tunnel (`portfolio-tunnel`) running as a systemd service via `cloudflared`. Domain `nanobyte.ca` DNS managed through Cloudflare.

### Routing Rules

| Public Hostname | Internal Target | Purpose |
|---|---|---|
| `portfolio.nanobyte.ca` | `http://localhost:10000` | Prod frontend (Nginx) |
| `portfolio.nanobyte.ca/api/*` | `http://localhost:10080` | Prod backend API |
| `uatportfolio.nanobyte.ca` | `http://localhost:20000` | UAT frontend |
| `uatportfolio.nanobyte.ca/api/*` | `http://localhost:20080` | UAT backend API |
| `status.nanobyte.ca` | `http://localhost:13001` | Uptime Kuma status page |
| `grafana.nanobyte.ca` | `http://localhost:13000` | Grafana dashboards |

### Cloudflare Access (Zero Trust)

| Hostname | Access |
|---|---|
| `portfolio.nanobyte.ca` | Open — app handles auth via JWT |
| `uatportfolio.nanobyte.ca` | Restricted to approved user list |
| `status.nanobyte.ca` | Open — public status page |
| `grafana.nanobyte.ca` | Restricted to admin email only |

### SSL

Cloudflare handles SSL termination. Tunnel traffic is encrypted by default. No Let's Encrypt needed.

---

## 3. Docker Compose Stacks

### Prod Stack (`/opt/portfolio/prod/docker-compose.yml`)

| Service | Image | Port | Notes |
|---|---|---|---|
| frontend | `ghcr.io/<owner>/portfolio-frontend:<tag>` | 10000 | Nginx serving built SPA |
| backend | `ghcr.io/<owner>/portfolio-backend:<tag>` | 10080 | Main API |
| ingestion-service | `ghcr.io/<owner>/portfolio-ingestion:<tag>` | 10081 | Data pipeline |
| market-data-service | `ghcr.io/<owner>/portfolio-market-data:<tag>` | 10082 | IBKR WebSocket |
| strategy-service | `ghcr.io/<owner>/portfolio-strategy:<tag>` | 10083 | Strategy engine |
| broker-gateway-service | `ghcr.io/<owner>/portfolio-broker-gateway:<tag>` | 10084 | Broker adapters |
| postgres | `postgres:16-alpine` | 15432 | Persistent volume in `data/postgres` |
| redis | `redis:7-alpine` | 16379 | Persistent volume in `data/redis` |
| ib-gateway | `ghcr.io/gnzsnz/ib-gateway:10.30.1t` | 14001, 15900 | IBKR Gateway + IBC + VNC (live mode) |

- `SPRING_PROFILES_ACTIVE=prod`
- `CORS_ALLOWED_ORIGINS=https://portfolio.nanobyte.ca`

### UAT Stack (`/opt/portfolio/uat/docker-compose.yml`)

Identical services with UAT-specific configuration:

| Service | Port |
|---|---|
| frontend | 20000 |
| backend | 20080 |
| ingestion-service | 20081 |
| market-data-service | 20082 |
| strategy-service | 20083 |
| broker-gateway-service | 20084 |
| postgres | 25432 |
| redis | 26379 |
| ib-gateway | 24002, 25900 |

- `SPRING_PROFILES_ACTIVE=uat`
- `CORS_ALLOWED_ORIGINS=https://uatportfolio.nanobyte.ca`
- Separate JWT signing keys, DB credentials, encryption keys

### Production Hardening (Both Stacks)

- `restart: unless-stopped` on all containers
- Memory limits: 4GB per backend service, 8GB for Postgres, 512MB for frontend/Redis
- Health checks on all services
- Named volumes for database persistence
- No debug ports exposed
- Log driver: `json-file` with `max-size: 50m`, `max-file: 5`
- Container names prefixed with environment (e.g., `prod-backend`, `uat-backend`)

### New Spring Profile

A new `application-uat.yml` profile is needed alongside existing `local`, `dev`, and `prod` profiles, configured for the UAT environment.

---

## 4. Observability Stack (Prod Only)

### Services (`/opt/portfolio/monitoring/docker-compose.yml`)

| Service | Port | Purpose |
|---|---|---|
| Prometheus | 19090 | Scrapes metrics from prod services + exporters |
| Grafana | 13000 | Dashboards and Slack alerting |
| Loki | 13100 | Centralized log aggregation |
| cAdvisor | 18080 | Container-level CPU/memory/network metrics |
| Uptime Kuma | 13001 | Public status page + uptime monitoring |
| node_exporter | 19100 | Host-level metrics (CPU, disk, RAM, network) |
| postgres_exporter | 19187 | PostgreSQL metrics |
| redis_exporter | 19121 | Redis metrics |

### Log Collection

Loki collects logs via the **Loki Docker logging driver** plugin. Prod containers are configured with `logging.driver: loki` pointing at `http://localhost:13100`. UAT containers use default `json-file` logging (no Loki).

### Prometheus Scrape Targets

- 6 backend services via `/actuator/prometheus`
- cAdvisor for container resource metrics
- node_exporter for host metrics
- postgres_exporter for database metrics
- redis_exporter for cache metrics

### Grafana Dashboards (Pre-Provisioned)

- **JVM** — heap usage, GC activity, thread counts per service
- **API** — request rate, latency percentiles (p50/p95/p99), error rate
- **Infrastructure** — host CPU, RAM, disk, container resources
- **Database** — active connections, query performance, connection pool usage
- **Redis** — memory usage, hit rate, connected clients

### Slack Alerting Rules

| Alert | Condition |
|---|---|
| Service down | Health check failing > 1 minute |
| High error rate | > 5% 5xx responses over 5 minutes |
| JVM heap critical | > 85% for > 5 minutes |
| Disk usage high | > 80% |
| DB pool exhaustion | Available connections < 2 |
| Container restart loop | > 3 restarts in 5 minutes |

### Uptime Kuma Monitors

- `portfolio.nanobyte.ca` — prod frontend
- `portfolio.nanobyte.ca/actuator/health` — prod backend
- `uatportfolio.nanobyte.ca` — UAT frontend
- Public status page at `status.nanobyte.ca`

---

## 5. CI/CD Pipeline

### Workflow 1: Build (`ci.yml`) — On push/merge to `main`

1. Run all backend tests (Gradle, JDK 21)
2. Run frontend tests (Vitest) + lint + type check
3. Build Docker images for all 7 services (6 backend + 1 frontend)
4. Tag images with `main-<short-sha>` (e.g., `main-a1b2c3d`)
5. Push all images to ghcr.io
6. Post build status to Slack

### Workflow 2: Deploy (`deploy.yml`) — Manual trigger only

**Inputs:**

| Input | Options | Purpose |
|---|---|---|
| `environment` | `prod` / `uat` | Which stack to deploy |
| `tag` | Image tag (e.g., `main-a1b2c3d`) | Which build to deploy |

**Steps:**

1. SSH into the home server via Cloudflare Tunnel (service token)
2. Update `.env` file with selected image tag
3. `docker compose pull` — pull tagged images from ghcr.io
4. `docker compose up -d` — restart changed services
5. Wait for health checks to pass
6. Post deploy result to Slack (success/failure, environment, tag, who triggered)

### SSH Access for GitHub Actions

- `cloudflared` exposes an SSH endpoint through the tunnel
- GitHub Actions uses a deploy key (SSH key pair, private stored as GitHub Secret)
- `deploy` user has limited permissions — only `docker compose` commands in `/opt/portfolio/`

### Rollback

Re-trigger the deploy workflow with a previous known-good image tag. All images remain in ghcr.io.

---

## 6. Security

### Network

- No ports exposed to the internet — Cloudflare Tunnel only
- UFW: deny all inbound, allow SSH from LAN only
- Cloudflare DDoS mitigation and rate limiting
- Prod and UAT on separate Docker networks — no cross-talk

### Application

- JWT authentication on all API requests
- CORS restricted per environment
- CSRF protection
- Grafana behind Cloudflare Access (email-restricted)
- UAT behind Cloudflare Access (approved user list)

### Secrets

- All secrets in `.env` files on the server (not in git)
- CI/CD secrets stored in GitHub Secrets
- Each environment has unique JWT signing keys, DB passwords, encryption keys
- `.env.example` in repo documents required variables without values

### Container

- All app containers run as non-root (`appuser:appgroup`)
- No privileged containers except cAdvisor (needs host access for metrics)
- Read-only filesystem where possible
- Docker socket not exposed to application containers

### Backups

- Daily automated `pg_dump` for both prod and UAT databases
- Stored locally on a separate partition
- Optional push to cloud storage (S3 / Backblaze B2)
- Retention: 7 days for daily backups, 30 days for weekly
- Backup script runs as a cron job on the host

---

## Port Reference

### Prod (10xxx / 14xxx / 15xxx / 16xxx)

| Service | Port |
|---|---|
| Frontend | 10000 |
| Backend | 10080 |
| Ingestion | 10081 |
| Market Data | 10082 |
| Strategy | 10083 |
| Broker Gateway | 10084 |
| IBKR Gateway API | 14001 |
| IBKR Gateway VNC | 15900 |
| PostgreSQL | 15432 |
| Redis | 16379 |

### UAT (20xxx / 24xxx / 25xxx / 26xxx)

| Service | Port |
|---|---|
| Frontend | 20000 |
| Backend | 20080 |
| Ingestion | 20081 |
| Market Data | 20082 |
| Strategy | 20083 |
| Broker Gateway | 20084 |
| IBKR Gateway API | 24002 |
| IBKR Gateway VNC | 25900 |
| PostgreSQL | 25432 |
| Redis | 26379 |

### Monitoring (1xxxx)

| Service | Port |
|---|---|
| Prometheus | 19090 |
| Grafana | 13000 |
| Loki | 13100 |
| cAdvisor | 18080 |
| Uptime Kuma | 13001 |
| node_exporter | 19100 |
| postgres_exporter | 19187 |
| redis_exporter | 19121 |
