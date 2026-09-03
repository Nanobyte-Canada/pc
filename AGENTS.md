# AGENTS.md — Working Rules for AI Agents

Rules for any AI agent, model, or automation working in this repository (pc — Portfolio Construction App). These apply to every contribution, regardless of which tool or model performs it.

## Purpose & Repo Map

Monorepo for a portfolio construction and analysis app (Kotlin/Spring Boot backend, React/TypeScript frontend) deployed to a home server via Docker Compose behind a Cloudflare Tunnel.

```
backend/common/portfolio/     — Shared math/domain library (BlackScholes, Greeks, TradingCalendar)
backend/portfolio/            — Main Spring Boot API (port 8080)
backend/broker-gateway/       — Broker gateway abstraction layer (port 8084)
backend/ingestion/            — Data ingestion microservice (port 8081)
backend/market-data/          — Market data + Questrade + WebSocket streaming (port 8082)
backend/strategy/             — Strategy engine + wheel writer (port 8083)
frontend/                     — React SPA (Vite; port 3000 local, nginx on 80 in deployed images)
deploy/prod|uat/              — Server compose files (consume shared infra from nanobyte-services)
deploy/monitoring/            — Monitoring stack
config/                       — Environment template (.env.example)
scripts/                      — Operational scripts (broker sync, integration tests, SDLC setup)
docs/                         — ADRs, reference docs, runbooks, design specs
.github/workflows/            — CI/CD (build.yml, deploy.yml, deploy-prod.yml, sdlc-agent.yml)
.archive/                     — Completed specs and plans
```

## Documentation Map

| Document | Purpose |
|----------|---------|
| [docs/adr.md](docs/adr.md) | **Append-only Architecture Decision Record log.** Update on every architectural change (compose, ports, networks, CI/CD, DB schema, service topology). Never delete past entries — supersede them. |
| [docs/business-context.html](docs/business-context.html) | Architecture and module overview. |
| [docs/reference/INDEX.md](docs/reference/INDEX.md) | Technical reference hub. Children: `backend-services.md`, `database-schema.md`, `api-endpoints.md`, `infrastructure.md`, `configurations.md`, `entity-relationships.md`, `ingestion-workflow.md`, `frontend-map.md`, `improvements.md`, `unused-legacy.md`. |
| [docs/runbooks/](docs/runbooks/) | Operational runbooks: `questrade-cutover.md`, `google-oauth-provider-unavailable.md`. |
| [docs/superpowers/specs/](docs/superpowers/specs/) | Design specs (`YYYY-MM-DD-<topic>-design.md`). |
| [docs/superpowers/plans/](docs/superpowers/plans/) | Implementation plans (`YYYY-MM-DD-<topic>.md`). |
| [README.md](README.md) | Human-facing overview: services, ports, environments, CI/CD usage, local dev. |

## Architecture Invariants

Do not violate these. If a task seems to require breaking one, stop and record a new ADR first (see contract below).

1. **Top-level `name:` in every compose file** — e.g., `name: portfolio-prod`, `name: portfolio-uat`. Prevents compose project-name collisions.
2. **Container naming: `{env}-portfolio-{service}`** — `prod-portfolio-frontend/backend/ingestion/market-data/strategy/broker-gateway` and `uat-portfolio-*`. Inter-service URLs use these names (e.g., `http://prod-portfolio-broker-gateway:8084`).
3. **Port scheme: `1xxxx` = prod, `2xxxx` = uat, 100-port gap between apps.** pc allocations: prod frontend 10000, backend 10080, ingestion 10081, market-data 10082, strategy 10083, broker-gateway 10084; UAT 20000, 20080–20084. Never allocate overlapping host ports.
4. **Shared infra DB/Redis by container hostname — never embed in deployed environments.** Postgres/Redis are centralized in the nanobyte-services stack: prod uses `prod-postgres`/`prod-redis`, UAT uses `uat-postgres`/`uat-redis`, joined over external networks `infra-prod-network`/`infra-uat-network` + `prod-internal-network`/`uat-internal-network`. Do not add Postgres/Redis services, volumes, or host port mappings to `deploy/prod/` or `deploy/uat/` compose files (the root `docker-compose.yml` is local-dev only and may embed its own). Embedding them caused cross-app port collisions — pc UAT and investclub UAT both claimed 25432/26379; see ADR-0017.
5. **Secrets come from Vault** — AppRole auth; paths `secret/portfolio/common` + `secret/portfolio/{env}`. Deploy workflows validate required secrets (`GH_PROJECT_TOKEN`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) and fail fast. Never hardcode secrets.
6. **Frontend has separate prod/uat image variants (build-args)** — `portfolio-frontend` (prod, `VITE_API_URL=https://portfolio.nanobyte.ca`) and `portfolio-frontend-uat` (`VITE_API_URL=https://uatportfolio.nanobyte.ca` + `VITE_AUTH_METHOD=both`). Prod is Google-only auth; UAT is `both`.
7. **Deploy topology** — UAT auto-deploys via `workflow_run` when Build & Push Images succeeds; prod deploys only manually via `deploy-prod.yml` with `environment: prod` protection. Images are tagged `main-<short-sha>`; deploy path `/opt/portfolio/{prod,uat}`.

## Documentation Maintenance Contract

**Any change to compose files, ports, networks, CI/CD workflows, or DB schema REQUIRES a new ADR entry in `docs/adr.md` in the same commit/PR.**

- New decision → append a new `ADR-NNNN` entry (next sequential number). Never rewrite or delete past entries; mark replaced decisions `Superseded by ADR-XXXX`.
- If the change alters the overview (services, ports, URLs, workflows, local dev), update `README.md` in the same commit.
- If it changes DB schema, update `docs/reference/database-schema.md`; if it changes infrastructure, update `docs/reference/infrastructure.md`.
- This contract applies to ANY agent or model, human or automated.

## Git Commit Rules

- **NEVER add `Co-Authored-By:` or any AI-attribution lines to commit messages.** No "Generated with", no agent signatures, no trailers identifying the tool or model. Commit messages are plain, imperative, and describe the change.

## Git Etiquette

- **Never push unless explicitly asked.** Commit locally and stop; the human decides when to push.
- Inspect `git status`/`git diff` before committing; stage only intended files.
- Do not force-push, amend published commits, or rewrite history.
