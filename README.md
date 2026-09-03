# Portfolio Construction App

A full-stack application for constructing and analyzing investment portfolios using public ETFs and mutual funds. Built for individual investors who want to build diversified portfolios, track drift from target allocations, and monitor performance across connected brokerage accounts.

## What It Does

- **Portfolio Construction** — Define target allocations using ETFs and mutual funds, then track how your actual holdings compare
- **Broker Integration** — Connect brokerage accounts via the broker-gateway service to sync positions automatically
- **Look-Through Analysis** — Decompose ETFs into underlying stock holdings to see true sector, geographic, and risk exposure
- **Drift & Rebalancing** — Monitor portfolio drift from targets and generate trade orders to rebalance
- **Instrument Screener** — Browse and filter 190k+ instruments across stocks, ETFs, mutual funds, preferred stocks, indices, and bonds
- **Dashboard** — Customizable widget-based dashboard with portfolio value, performance, risk metrics, and activity feeds
- **Market Data** — Real-time market data streaming via Questrade with WebSocket delivery
- **Options Trading** — Multi-leg options strategies (spreads, iron condors, covered calls) with P&L and Greeks calculations
- **Wheel Strategy** — Automated cash-secured put and covered call wheel writing with candidate scoring

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Kotlin 2.0 + Spring Boot 3.3 + JDK 21 |
| Frontend | React 18 + TypeScript 5.6 + Vite 5 |
| Database | PostgreSQL 16 + Flyway migrations (shared, centralized) |
| Cache | Redis 7 (shared, centralized) |
| Broker Gateway | broker-gateway (Spring Boot, multi-adapter for Questrade/Wealthsimple) |
| Market Data | Questrade API (refresh-token auth; real-time US/OPRA package required) |
| Data Sources | EODHD, Alpha Vantage |
| Containerization | Docker + Docker Compose |
| Secrets | HashiCorp Vault (AppRole) |
| CI/CD | GitHub Actions + GHCR + Cloudflare Tunnel |

## Services & Ports

Six services make up the app. Internal container ports are fixed; host ports follow the environment scheme (`1xxxx` = prod, `2xxxx` = uat).

| Service | Container (prod / uat) | Internal Port | Prod Host Port | UAT Host Port |
|---------|------------------------|---------------|----------------|---------------|
| frontend | `prod-portfolio-frontend` / `uat-portfolio-frontend` | 80 (local dev: 3000) | 10000 | 20000 |
| backend (portfolio API) | `prod-portfolio-backend` / `uat-portfolio-backend` | 8080 | 10080 | 20080 |
| ingestion | `prod-portfolio-ingestion` / `uat-portfolio-ingestion` | 8081 | 10081 | 20081 |
| market-data | `prod-portfolio-market-data` / `uat-portfolio-market-data` | 8082 | 10082 | 20082 |
| strategy | `prod-portfolio-strategy` / `uat-portfolio-strategy` | 8083 | 10083 | 20083 |
| broker-gateway | `prod-portfolio-broker-gateway` / `uat-portfolio-broker-gateway` | 8084 | 10084 | 20084 |

## Environments & URLs

| Environment | URL | Compose | Deploy Path |
|-------------|-----|---------|-------------|
| Production | https://portfolio.nanobyte.ca | `deploy/prod/docker-compose.yml` | `/opt/portfolio/prod` |
| UAT | https://uatportfolio.nanobyte.ca | `deploy/uat/docker-compose.yml` | `/opt/portfolio/uat` |

Both domains are served through a Cloudflare Tunnel on the home server: `portfolio.nanobyte.ca` → `localhost:10000`, `uatportfolio.nanobyte.ca` → `localhost:20000`.

**Shared infrastructure dependency:** prod and UAT no longer run their own databases — Postgres and Redis are centralized in the [nanobyte-services](https://github.com/nanobyte-canada/nanobyte-services) stack and referenced by container hostname (`prod-postgres`/`prod-redis`, `uat-postgres`/`uat-redis`) over the external `infra-prod-network`/`infra-uat-network` and `prod-internal-network`/`uat-internal-network` Docker networks. Never re-embed DB/Redis services in pc compose files. Every compose file must set a top-level `name:` (e.g., `name: portfolio-prod`).

Auth: prod uses Google OAuth only; UAT supports both Google and email/password (`VITE_AUTH_METHOD=both` in the UAT frontend image).

## Project Structure

```
backend/common/portfolio/     — Shared math/domain library (BlackScholes, Greeks, TradingCalendar)
backend/portfolio/            — Main Spring Boot API (port 8080)
backend/broker-gateway/       — Broker gateway abstraction layer (port 8084)
backend/ingestion/            — Data ingestion microservice (port 8081)
backend/market-data/          — Market data + Questrade + WebSocket streaming (port 8082)
backend/strategy/             — Strategy engine + wheel writer (port 8083)
frontend/                     — React SPA (port 3000 local; nginx on 80 in deployed images)
deploy/prod|uat/              — Server compose files (shared-infra based)
deploy/monitoring/            — Monitoring stack
config/                       — Environment template (.env.example)
scripts/                      — Operational scripts (broker sync test, integration test, SDLC Vault/board setup)
docs/                         — ADRs, reference docs, runbooks, design specs
.github/workflows/            — CI/CD (build, deploy, SDLC agent)
.archive/                     — Completed design specs and plans
```

## Quick Start (Local Dev)

The root `docker-compose.yml` is a standalone local stack — it runs its own Postgres and Redis and builds all services from source.

```bash
# 1. Set up environment
cp config/.env.example .env
# Edit .env — fill in API keys (EODHD, Questrade refresh token, JWT key, etc.)

# 2. Start all services
docker compose up --build

# 3. Access the app
# Frontend:  http://localhost:3000
# Backend:   http://localhost:8080
# Health:    http://localhost:8080/health
```

**No JDK is installed locally** — all backend work runs inside Docker containers.

```bash
# Backend tests (inside container)
docker compose exec backend ./gradlew test
docker compose exec market-data-service ./gradlew test
docker compose exec strategy-service ./gradlew test

# Frontend dev server (local npm)
cd frontend && npm run dev

# Frontend validation
npm run build && npm run lint && npm run test:run
```

## CI/CD

Workflows live in `.github/workflows/`:

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| **Build & Push Images** (`build.yml`) | push / PR to `main` | Runs backend + frontend tests, then on push builds 5 backend images + 2 frontend variants (prod and uat build-args) and pushes to GHCR tagged `main-<short-sha>` + `latest` |
| **Deploy** (`deploy.yml`) | auto after Build & Push Images succeeds (`workflow_run`), or manual dispatch | Deploys the built tag to **UAT** at `/opt/portfolio/uat` |
| **Deploy to Production** (`deploy-prod.yml`) | manual dispatch only (`environment: prod` protection) | Deploys a chosen tag to **prod** at `/opt/portfolio/prod` |
| **SDLC Agent** (`sdlc-agent.yml`) | board labels / PR events / dispatch | Runs planner/builder/tester/deployer agents against the GitHub Projects v2 board; tester auto-merges PRs, deployer only deploys prod after a human moves the card to "Publish" |

Manual deploy of a specific build:

```bash
gh workflow run deploy.yml -f environment=uat -f tag=main-a1b2c3d
gh workflow run deploy-prod.yml -f tag=main-a1b2c3d
```

Deploy secrets are fetched at deploy time from Vault (`secret/portfolio/common` + `secret/portfolio/{env}`); the workflows fail fast if `GH_PROJECT_TOKEN`, `GOOGLE_CLIENT_ID`, or `GOOGLE_CLIENT_SECRET` are missing.

## Environment Variables

Copy `config/.env.example` to `.env` at the project root. Key variables:

| Variable | Description |
|----------|-------------|
| `BROKER_GATEWAY_URL` / `BROKER_GATEWAY_API_KEY` | Broker gateway service connection |
| `GATEWAY_API_KEY` | Shared key the backend uses to call broker-gateway |
| `QUESTRADE_REFRESH_TOKEN` | Questrade market data / broker auth |
| `EODHD_API_KEY` | Market data provider (ingestion) |
| `BROKER_ENCRYPTION_KEY` | AES-256 key for token encryption |
| `JWT_SIGNING_KEY` | HS512 signing key (min 64 chars) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth sign-in |

On the server, these are generated from Vault rather than hand-maintained.

## Operational Scripts

- `scripts/broker-sync-test.sh` — end-to-end broker sync verification
- `scripts/integration-test.sh` — cross-service integration checks
- `scripts/setup-sdlc-vault.sh` / `scripts/setup-sdlc-kv.sh` — Vault bootstrap for the SDLC pipeline
- `scripts/update-card-status.sh` — SDLC board card status transitions (bootstrapped from nanobyte-services)

## Documentation

| Doc | Purpose |
|-----|---------|
| [docs/adr.md](docs/adr.md) | Architecture Decision Record log (append-only — update on every architectural change) |
| [docs/business-context.html](docs/business-context.html) | Architecture and module overview |
| [docs/reference/INDEX.md](docs/reference/INDEX.md) | Technical reference hub: backend services, DB schema, API endpoints, infrastructure, configurations, entity relationships, ingestion workflow, frontend map, improvements, unused legacy |
| [docs/runbooks/](docs/runbooks/) | Operational runbooks (Questrade cutover, Google OAuth provider unavailable) |
| [docs/superpowers/](docs/superpowers/) | Design specs and implementation plans |
| [AGENTS.md](AGENTS.md) | Working rules for AI agents (architecture invariants, docs contract, git rules) |

## License

Private — All rights reserved
