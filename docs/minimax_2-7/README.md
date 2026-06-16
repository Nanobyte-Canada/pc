# minimax_2-7 — Technical Documentation

> Technical reference documentation for the minimax_2-7 repository.
> Generated: June 2026

---

## Table of Contents

1. [README](README.md) — Repository overview, tech stack, project layout, quick start
2. [Architecture](ARCHITECTURE.md) — System diagrams, inter-service communication, data flows
3. [Overview](OVERVIEW.md) — Module/feature inventory for all services
4. [Backend Analysis](BACKEND-ANALYSIS.md) — Deep dive into each backend microservice
5. [Frontend Analysis](FRONTEND-ANALYSIS.md) — Pages, components, services, stores, dead code
6. [Infrastructure Analysis](INFRASTRUCTURE-ANALYSIS.md) — Docker, CI/CD, monitoring, security
7. [Redundancies and Gaps](REDUNDANCIES-AND-GAPS.md) — Dead code, unused deps, disconnected modules

---

## Repository Overview

**Project:** minimax_2-7 — Portfolio Construction App
**Type:** Full-stack microservices application
**Purpose:** Investment portfolio construction and analysis using public ETFs and mutual funds, with broker integration via SnapTrade, look-through ETF analysis, drift monitoring, and options trading.

### Key Capabilities

- **Portfolio Construction** — Define target allocations using ETFs and mutual funds, track actual holdings vs targets
- **Broker Integration** — Connect brokerage accounts via SnapTrade to sync positions automatically
- **Look-Through Analysis** — Decompose ETFs into underlying stock holdings for true sector/geographic/risk exposure
- **Drift & Rebalancing** — Monitor portfolio drift from targets and generate trade orders to rebalance
- **Instrument Screener** — Browse and filter 190k+ instruments across stocks, ETFs, mutual funds, preferred stocks, indices, and bonds
- **Dashboard** — Customizable widget-based dashboard with portfolio value, performance, risk metrics, and activity feeds
- **Market Data** — Real-time market data streaming via IBKR with WebSocket delivery
- **Options Trading** — Multi-leg options strategies (spreads, iron condors, covered calls) with P&L and Greeks calculations
- **Wheel Strategy** — Automated cash-secured put and covered call wheel writing with candidate scoring

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Kotlin 2.0 + Spring Boot 3.3 + JDK 21 |
| Frontend | React 18 + TypeScript 5.6 + Vite 5 |
| Database | PostgreSQL 16 + Flyway migrations |
| Cache | Redis 7 |
| Broker SDK | SnapTrade |
| Market Data | IBKR TWS API (real-time) |
| Data Sources | EODHD, Alpha Vantage |
| Containerization | Docker + Docker Compose |

### Project Structure

```
pc/
├── backend/           # 5 microservices + 1 shared library (Kotlin/Spring Boot)
│   ├── common/        — Shared math/domain library (BlackScholes, Greeks, TradingCalendar)
│   ├── portfolio/     — Main Spring Boot API (port 8080)
│   ├── ingestion/     — Data ingestion microservice (port 8081)
│   ├── market-data/   — Market data + IBKR + WebSocket streaming (port 8082)
│   ├── strategy/      — Strategy engine + wheel writer (port 8083)
│   └── broker-gateway/— Unified broker adapter (port 8084)
├── frontend/          — React 18 SPA (port 3000)
├── deploy/            — Docker Compose, monitoring, scripts
├── docs/              — Documentation (big_pickle/, reference/, superpowers/)
├── config/            — Environment templates
├── scripts/           — Utility scripts
├── screenshots/       — UI screenshots
├── .github/           — CI/CD workflows
└── docker-compose.yml # Local dev orchestration
```