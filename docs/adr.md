# Architecture Decision Records (ADR)

Append-only log of architecture decisions for the Portfolio Construction App.

**Rules:**

- One entry per decision, numbered sequentially (`ADR-NNNN`).
- **Never delete past entries.** To reverse a decision, add a new entry and mark the old one `Superseded by ADR-XXXX`.
- Any change to compose files, ports, networks, CI/CD workflows, or DB schema requires a new entry here in the same commit/PR.

Entry format:

```markdown
## ADR-NNNN: <Title>
**Status:** Accepted | **Date:** YYYY-MM-DD
**Context:** ...
**Decision:** ...
**Consequences:** ...
```

---

## Historical decisions (backfill)

Brief records backfilled from the design specs in `docs/superpowers/specs/`. See the linked spec for full context.

## ADR-0001: Seamless Broker Sync & Post-Connection Flow
**Status:** Accepted | **Date:** 2026-04-19
**Context:** Brokerage accounts needed to sync into the portfolio automatically after connection, without manual imports.
**Decision:** Adopted the seamless broker sync design (`docs/superpowers/specs/2026-04-19-seamless-broker-sync-design.md`) — automatic position/activity sync and a guided post-connection flow.
**Consequences:** Broker data flows into the app on connect; later work built the broker-gateway abstraction on top of this model.

## ADR-0002: Broker Gateway Service Design
**Status:** Accepted | **Date:** 2026-04-23
**Context:** Multiple brokers (Questrade, Wealthsimple, IBKR) with different APIs had to be integrated without coupling the core portfolio service to each vendor.
**Decision:** Extracted a dedicated broker-gateway service (`docs/superpowers/specs/2026-04-23-broker-gateway-design.md`) with a multi-adapter architecture behind a single gateway API.
**Consequences:** The portfolio service talks to one gateway (`BROKER_GATEWAY_URL` + `GATEWAY_API_KEY`); new brokers are added as adapters, not core changes.

## ADR-0003: Portfolio View + Wheel Order Flow
**Status:** Accepted | **Date:** 2026-05-28
**Context:** The wheel strategy needed ordering capabilities and the portfolio view needed to integrate with the wheel workflow.
**Decision:** Adopted the combined portfolio-view and wheel-order design (`docs/superpowers/specs/2026-05-28-portfolio-and-wheel-order.md`).
**Consequences:** Unified ordering path for wheel trades; set up the screen sequence refined in ADR-0004.

## ADR-0004: Wheel Strategy Screens 1–3
**Status:** Accepted | **Date:** 2026-05-29
**Context:** The cash-secured put / covered call wheel workflow needed a guided, three-step trader experience.
**Decision:** Adopted the three-screen wheel design (`docs/superpowers/specs/2026-05-29-wheel-screen1-positions-design.md`, `-screen2-quotes-design.md`, `-screen3-order-design.md`): Screen 1 positions & financials, Screen 2 options quotes & live feed, Screen 3 order submission.
**Consequences:** Wheel candidates flow positions → live quotes → order in one coherent UI flow.

## ADR-0005: IBKR Gateway Connection & Market Data Streaming
**Status:** Superseded by ADR-0016 (market data) and ADR-0020 (removal) | **Date:** 2026-05-30
**Context:** Real-time market data and broker connectivity were initially built on Interactive Brokers.
**Decision:** Adopted the IBKR gateway connection design (`docs/superpowers/specs/2026-05-30-ibkr-gateway-connection-design.md`) — shared IB Gateway containers for TWS/Gateway connectivity and streaming market data.
**Consequences:** Served as the original market-data path; later superseded by Questrade (ADR-0016) and the IB Gateway containers were removed entirely (ADR-0020).

## ADR-0006: Home Server Deployment
**Status:** Accepted | **Date:** 2026-05-30
**Context:** The app needed a self-hosted deployment target reachable from the internet without a public IP.
**Decision:** Adopted the home server deployment design (`docs/superpowers/specs/2026-05-30-home-server-deployment-design.md`) — Docker Compose deploys to a home server exposed via Cloudflare Tunnel.
**Consequences:** `portfolio.nanobyte.ca` and `uatportfolio.nanobyte.ca` are served from the home server; deploys run over SSH through cloudflared (see deploy workflows).

## ADR-0007: HashiCorp Vault Secret Manager
**Status:** Accepted | **Date:** 2026-05-31
**Context:** Secrets were scattered across environment files and CI secrets with no audit or rotation story.
**Decision:** Adopted HashiCorp Vault (`docs/superpowers/specs/2026-05-31-vault-secret-manager-design.md`) with AppRole authentication and a two-tier layout: `secret/portfolio/common` + `secret/portfolio/{env}`.
**Consequences:** All deploy/CI secrets come from Vault at deploy time; workflows validate required secrets (e.g., `GH_PROJECT_TOKEN`, `GOOGLE_CLIENT_ID/SECRET`) before deploying.

## ADR-0008: CI/CD Cleanup and SSH Hardening
**Status:** Accepted | **Date:** 2026-06-05
**Context:** CI/CD had accumulated drift and SSH access needed hardening.
**Decision:** Adopted the cleanup/hardening design (`docs/superpowers/specs/2026-06-05-cicd-cleanup-and-hardening-design.md`).
**Consequences:** Standardized build/deploy workflows; SSH to the server goes through the Cloudflare Tunnel with a dedicated deploy key.

## ADR-0009: Options Chain Performance Optimization
**Status:** Accepted | **Date:** 2026-06-10
**Context:** Options chain loading was too slow for interactive wheel trading.
**Decision:** Adopted the options chain performance design (`docs/superpowers/specs/2026-06-10-options-chain-performance-design.md`).
**Consequences:** Chain panels load fast enough for live trading workflows; further caching added later in ADR-0015.

## ADR-0010: Autonomous SDLC Pipeline
**Status:** Accepted | **Date:** 2026-06-22
**Context:** Feature delivery (planning → implementation → testing → deployment) was fully manual.
**Decision:** Adopted the autonomous SDLC pipeline design (`docs/superpowers/specs/2026-06-22-autonomous-sdlc-pipeline-design.md`) — AI agents driven off a GitHub Projects board.
**Consequences:** Issue labels trigger agent work automatically; see ADR-0011/0012/0022 for the completed operating model.

## ADR-0011: Autonomous SDLC Pipeline Completion
**Status:** Accepted | **Date:** 2026-06-28
**Context:** The initial SDLC pipeline needed completion and stabilization after its first iteration.
**Decision:** Adopted the pipeline completion design (`docs/superpowers/specs/2026-06-28-autonomous-sdlc-pipeline-completion-design.md`).
**Consequences:** Full planner → builder → tester → deployer loop operational end to end.

## ADR-0012: SDLC Card Movement Pipeline
**Status:** Accepted | **Date:** 2026-07-02
**Context:** Board cards needed deterministic movement (In Review → Done) tied to actual work state, not manual bookkeeping.
**Decision:** Adopted the card movement pipeline design (`docs/superpowers/specs/2026-07-02-sdlc-card-movement-pipeline-design.md`).
**Consequences:** Card status transitions are automated via `scripts/update-card-status.sh` and workflow hooks.

## ADR-0013: Multi-Repo SDLC Onboarding
**Status:** Accepted | **Date:** 2026-07-03
**Context:** The SDLC pipeline was pc-specific and needed to generalize to other repos (e.g., investclub).
**Decision:** Adopted the multi-repo onboarding design (`docs/superpowers/specs/2026-07-03-multi-repo-sdlc-onboarding-design.md`) — shared agents/config bootstrapped from `nanobyte-services`, per-repo Vault tiers.
**Consequences:** Any repo can opt into the pipeline via an `APP_NAME` variable and Vault secrets; this also motivated shared centralized infra (ADR-0017).

## ADR-0014: UAT Email/Password Login
**Status:** Accepted | **Date:** 2026-07-10
**Context:** Testing agents needed to authenticate against UAT without Google OAuth interactivity.
**Decision:** Adopted the UAT email/password login design (`docs/superpowers/specs/2026-07-10-uat-email-password-login-design.md`); the UAT frontend image is built with `VITE_AUTH_METHOD=both`.
**Consequences:** UAT supports both Google and email/password auth; prod remains Google-only.

## ADR-0015: Options Expiry Redis Cache
**Status:** Accepted | **Date:** 2026-07-17
**Context:** Options expiry/chain lookups hit upstream providers repeatedly for slowly-changing data.
**Decision:** Adopted the options expiry Redis cache design (`docs/superpowers/specs/2026-07-17-options-expiry-redis-cache-design.md`).
**Consequences:** Expiry data is cached in Redis, cutting provider load and chain load times.

## ADR-0016: Questrade Market Data Migration
**Status:** Accepted — supersedes the market-data portion of ADR-0005 | **Date:** 2026-08-24
**Context:** IB Gateway-based market data was fragile and operationally expensive; Questrade provides real-time US/OPRA data via API.
**Decision:** Adopted the Questrade migration design (`docs/superpowers/specs/2026-08-24-questrade-market-data-migration-design.md`) — refresh-token auth in the market-data service, WebSocket streaming to the frontend.
**Consequences:** Market data no longer depends on IB Gateway; paved the way for full IB Gateway removal (ADR-0020).

---

## 2026-09-02 — Shared infrastructure migration

Detailed records for the infrastructure migration (commit `6b73ca4`) and related decisions.

## ADR-0017: Migration to Centralized Shared Postgres/Redis
**Status:** Accepted | **Date:** 2026-09-02
**Context:** pc embedded its own Postgres and Redis in its deploy compose files. This was the root cause of cross-app coupling: pc UAT and investclub UAT both claimed host ports 25432/26379, and every app carried its own DB/Redis lifecycle.
**Decision:** All pc compose files now consume centralized Postgres/Redis from the `nanobyte-services` shared stack by container hostname — prod references `prod-postgres` / `prod-redis`, UAT references `uat-postgres` / `uat-redis` — joined over external Docker networks: `prod-internal-network` + `infra-prod-network` (prod) and `uat-internal-network` + `infra-uat-network` (UAT). The embedded DB/Redis services, volumes, and port mappings were removed from `deploy/prod/docker-compose.yml` and `deploy/uat/docker-compose.yml`.
**Consequences:** No host-port collisions between apps; DB/Redis lifecycle, backups, and upgrades are owned centrally by nanobyte-services. Post-migration health: backend + frontend healthy in both envs; strategy, ingestion, market-data, and broker-gateway report unhealthy in both envs — these are **pre-existing** conditions (external API keys/dependencies, e.g. Questrade tokens) and **not a migration regression**.

## ADR-0018: Container Naming Convention
**Status:** Accepted | **Date:** 2026-09-02
**Context:** Container names were inconsistent, making cross-service references, monitoring, and operational scripts error-prone.
**Decision:** All pc containers follow `{env}-portfolio-{service}`: `prod-portfolio-frontend`, `prod-portfolio-backend`, `prod-portfolio-ingestion`, `prod-portfolio-market-data`, `prod-portfolio-strategy`, `prod-portfolio-broker-gateway`, and the `uat-portfolio-*` equivalents.
**Consequences:** Predictable names for inter-service URLs (e.g., `BROKER_GATEWAY_URL=http://prod-portfolio-broker-gateway:8084`) and for ops tooling that targets containers by name.

## ADR-0019: Environment Port Scheme
**Status:** Accepted | **Date:** 2026-09-02
**Context:** Host port allocation across apps on the shared server was ad hoc, causing conflicts.
**Decision:** Standard scheme: `1xxxx` = prod, `2xxxx` = uat, with a 100-port gap reserved between apps. pc allocations — prod: frontend 10000, backend 10080, ingestion 10081, market-data 10082, strategy 10083, broker-gateway 10084; UAT: 20000, 20080–20084. Additionally, **every compose file must set a top-level `name:`** (e.g., `name: portfolio-prod`) so project names never collide.
**Consequences:** Public URLs map deterministically via the Cloudflare Tunnel on the server: `portfolio.nanobyte.ca` → `localhost:10000`, `uatportfolio.nanobyte.ca` → `localhost:20000`. New apps must pick a non-overlapping 100-port block.

## ADR-0020: IB Gateway Removal
**Status:** Accepted | **Date:** 2026-09-02
**Context:** The shared IB Gateway containers (`shared-ib-gateway`) were dead weight — market data had already migrated to Questrade (ADR-0016) and nothing served traffic through them. The broker-gateway service historically depended on them for IBKR connectivity.
**Decision:** Removed the shared-ib-gateway containers from the server (2026-09-02). The broker-gateway's former IBKR dependency is noted as historical; live broker/market-data paths are Questrade and Wealthsimple.
**Consequences:** IBKR restart/monitor scripts are obsolete and removed from docs; market-data and broker operations no longer involve TWS/Gateway sessions.

## ADR-0021: CI/CD — UAT Auto-Deploy and Manual Prod Deploy
**Status:** Accepted | **Date:** 2026-09-02
**Context:** Deploys were fully manual (`workflow_dispatch`) for both environments; UAT should track main automatically while prod keeps a deliberate human trigger.
**Decision:** The **Build & Push Images** workflow builds 5 backend images (`portfolio-backend`, `-ingestion`, `-market-data`, `-strategy`, `-broker-gateway`) plus 2 frontend variants — prod (`portfolio-frontend`, `VITE_API_URL=https://portfolio.nanobyte.ca`) and UAT (`portfolio-frontend-uat`, `VITE_API_URL=https://uatportfolio.nanobyte.ca` + `VITE_AUTH_METHOD=both`) — tagged `main-<short-sha>` + `latest`. UAT deploys automatically via `workflow_run` when the build workflow completes successfully (plus manual dispatch fallback); prod deploys only via `deploy-prod.yml` (`workflow_dispatch` only, `environment: prod` protection). Secrets come from Vault paths `secret/portfolio/common` + `secret/portfolio/{env}`; workflows fail fast if `GH_PROJECT_TOKEN`, `GOOGLE_CLIENT_ID`, or `GOOGLE_CLIENT_SECRET` are missing. Compose files and `.env` land under `/opt/portfolio/{prod,uat}` on the server.
**Consequences:** Merge to main → build → automatic UAT deploy of that exact SHA (`workflow_run.head_sha`). Prod is always an explicit, tagged, manually-triggered release.

## ADR-0022: Autonomous SDLC Operating Model
**Status:** Accepted | **Date:** 2026-09-02
**Context:** The autonomous pipeline (ADR-0010/0011) needed a stable, safe operating model as it runs daily.
**Decision:** `sdlc-agent.yml` runs planner / builder / tester / deployer agents (opencode, bootstrapped from `nanobyte-services`) against the GitHub Projects v2 "Nanobyte SDLC" board, triggered by lane labels (Scoping, Planning, Executing, Testing, Publish) and PR/dispatch events on a self-hosted runner. The **tester auto-merges PRs** before testing proceeds; the **deployer only deploys prod after a human moves the card to "Publish"** — prod deploys are never fully autonomous. Agent secrets come from Vault tiers `sdlc/common`, `{APP_NAME}/common`, and `{APP_NAME}/{uat,prod}`.
**Consequences:** Card state drives automation; a human gate protects production; the same model is shared across repos (ADR-0013).
