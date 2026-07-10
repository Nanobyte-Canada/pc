# Portfolio Construction App

A full-stack application for constructing and analyzing investment portfolios using public ETFs and mutual funds. Built for individual investors who want to build diversified portfolios, track drift from target allocations, and monitor performance across connected brokerage accounts.

## What It Does

- **Portfolio Construction** — Define target allocations using ETFs and mutual funds, then track how your actual holdings compare
- **Broker Integration** — Connect brokerage accounts via the broker-gateway service to sync positions automatically
- **Look-Through Analysis** — Decompose ETFs into underlying stock holdings to see true sector, geographic, and risk exposure
- **Drift & Rebalancing** — Monitor portfolio drift from targets and generate trade orders to rebalance
- **Instrument Screener** — Browse and filter 190k+ instruments across stocks, ETFs, mutual funds, preferred stocks, indices, and bonds
- **Dashboard** — Customizable widget-based dashboard with portfolio value, performance, risk metrics, and activity feeds
- **Market Data** — Real-time market data streaming via IBKR with WebSocket delivery
- **Options Trading** — Multi-leg options strategies (spreads, iron condors, covered calls) with P&L and Greeks calculations
- **Wheel Strategy** — Automated cash-secured put and covered call wheel writing with candidate scoring

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Kotlin 2.0 + Spring Boot 3.3 + JDK 21 |
| Frontend | React 18 + TypeScript 5.6 + Vite 5 |
| Database | PostgreSQL 16 + Flyway migrations |
| Cache | Redis 7 |
| Broker Gateway | broker-gateway (Spring Boot, multi-adapter for Questrade/Wealthsimple/IBKR) |
| Market Data | IBKR TWS API (real-time) |
| Data Sources | EODHD, Alpha Vantage |
| Containerization | Docker + Docker Compose |

## Project Structure

backend/common/portfolio/     — Shared math/domain library (BlackScholes, Greeks, TradingCalendar)
backend/portfolio/            — Main Spring Boot API (port 8080)
backend/broker-gateway/       — Broker gateway abstraction layer (port 8084)
backend/ingestion/            — Data ingestion microservice (port 8081)
backend/market-data/          — Market data + IBKR + WebSocket streaming (port 8082)
backend/strategy/             — Strategy engine + wheel writer (port 8083)
frontend/                     — React SPA (port 3000)
config/                       — Environment template (.env.example)
docs/reference/               — Technical reference documentation
docs/business-context.html    — Architecture and module overview
scripts/                      — Operational scripts (IBKR restart, SDLC automation)
.archive/                     — Completed design specs and plans
```

## Quick Start

```bash
# 1. Set up environment
cp config/.env.example .env
# Edit .env — fill in API keys (SNAPTRADE, EODHD, etc.)

# 2. Start all services
docker compose up --build

# 3. Access the app
# Frontend:  http://localhost:3000
# Backend:   http://localhost:8080
# Health:    http://localhost:8080/health
```

## Development

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

## Environment Variables

Copy `config/.env.example` to `.env` at the project root. Key variables:

| Variable | Description |
|----------|-------------|
| `BROKER_GATEWAY_URL` / `BROKER_GATEWAY_API_KEY` | Broker gateway service connection |
| `EODHD_API_KEY` | Market data provider |
| `IBKR_HOST` / `IBKR_PORT` | Interactive Brokers TWS/Gateway connection |
| `BROKER_ENCRYPTION_KEY` | AES-256 key for token encryption |
| `JWT_SIGNING_KEY` | HS512 signing key (min 64 chars) |

## Operational Scripts

### IBKR Service Restart (`scripts/restart-ibkr.sh`)

On-demand restart for IB Gateway and market-data containers across UAT and Production environments.

```bash
# Restart all services (IB Gateway + both market-data)
./scripts/restart-ibkr.sh --env all

# Restart only UAT market-data (skip IB Gateway to avoid disrupting Prod)
./scripts/restart-ibkr.sh --env uat --skip-gateway

# Restart only Production market-data
./scripts/restart-ibkr.sh --env prod --skip-gateway
```

### IBKR Auto-Restart Monitor (`scripts/auto-restart-ibkr.sh`)

Automated health monitoring with automatic restart for unhealthy services. Designed to run via cron.

```bash
# Interactive mode (verbose output)
./scripts/auto-restart-ibkr.sh

# Cron mode (quiet, suitable for scheduled execution)
*/5 * * * * /opt/portfolio/scripts/auto-restart-ibkr.sh --quiet
```

Features:
- Checks IB Gateway TCP connectivity (port 14001) and market-data HTTP health endpoints
- Auto-restarts unhealthy services with a 5-minute cooldown to prevent restart loops
- Uses `--skip-gateway` when only market-data needs restarting to avoid cross-environment disruption

## License

Private — All rights reserved
