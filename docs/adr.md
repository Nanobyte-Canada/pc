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

## ADR-0023: Monitoring/Vault/Backup Ownership Fully Migrated to nanobyte-services
**Status:** Accepted | **Date:** 2026-09-16
**Context:** Following ADR-0017 (shared DB/Redis), the remaining platform infrastructure has fully moved to the `nanobyte-services` repo, which now ships the complete monitoring stack (Prometheus, Grafana, Loki, Promtail, cAdvisor, node-exporter, postgres-exporter, redis-exporter, Uptime Kuma), the shared Vault (container `vault`, host port `127.0.0.1:18200:8200`, reachable at `https://vault.nanobyte.ca` via tunnel), nightly `backup.sh` (pg_dumpall + Vault snapshot, 7-day retention, written to `/opt/backups`), and `bootstrap-server.sh` for server provisioning. This left duplicated, drifting copies in pc: `deploy/monitoring/` (including an untracked `webhook-proxy/` and uncommitted alert fixes), `deploy/scripts/backup.sh`, `deploy/scripts/vault-init.sh`, and `deploy/uat/init-guc.sh`. Two of these were independently obsolete: `init-guc.sh` seeded the UAT admin conditionally on the `app.environment` PostgreSQL GUC, which was never set — Flyway `V75` was a no-op and `V76__uat_seed_test_admin_user.sql` now seeds the UAT test admin unconditionally, so no init script is needed (and none was ported to nanobyte-services). `vault-init.sh` targeted the retired `portfolio-vault` container, which no longer exists in pc. During pre-flight, the uncommitted monitoring improvements in `deploy/monitoring/` (real Grafana datasource UIDs replacing the stale `prometheus`/`loki` values, a `job="node-exporter"` filter on the disk-usage query, faster alert timing, and a Grafana webhook → Slack formatter service) were evaluated for porting; **the user chose to abandon them all (2026-09-16)** — nanobyte-services' direct-to-Slack contact point is the accepted replacement, and fixing its alerts later is out of scope for pc. A stash (`pre-cleanup: uncommitted monitoring fixes + webhook-proxy`) preserves the abandoned work for recovery.
**Decision:** Delete `deploy/monitoring/` (including the untracked `webhook-proxy/`), `deploy/scripts/backup.sh`, `deploy/scripts/vault-init.sh`, and `deploy/uat/init-guc.sh` from pc. nanobyte-services is the single owner of monitoring, Vault, backups, and server bootstrap; pc retains only its application services, CI/CD workflows, and local dev stack. `deploy/cloudflared/config.yml` and `deploy/scripts/setup-cloudflared-tunnel.sh` remain in pc — they are the only home for the tunnel config (nanobyte-services has no cloudflared files), and the routed host ports are unchanged by the migration (Grafana 13000, Uptime Kuma 13001, Vault `127.0.0.1:18200:8200`).
**Consequences:** pc CI/CD is unaffected — all workflows authenticate to `https://vault.nanobyte.ca` (Cloudflare tunnel URL) via AppRole, not to any pc-managed container. Docs (`README.md`, `AGENTS.md`, `docs/reference/infrastructure.md`, `deploy/scripts/setup-server.sh`) no longer reference pc-owned monitoring/Vault/backup infra. Future alerts/config fixes belong to nanobyte-services; the abandoned pc-side alert fixes live in the git stash if ever needed.

## ADR-0024: Adopt UI Testing Platform (Revision 2 Model)
**Status:** Accepted | **Date:** 2026-09-17
**Context:** The portfolio construction app has 16 pages, ~97 components, and ~15 Vitest tests but no systematic UI testing strategy. There is no traceability from requirements to tests, no CI gates for browser tests, and no accessibility or visual regression testing. The existing single Playwright spec targets localhost only.
**Decision:** Adopt the UI testing platform defined in `docs/superpowers/specs/2026-09-17-ui-testing-platform-design.md` across six phases:
1. **Phase 0 — Discovery:** Source documents, discovery report, critical journeys, legacy disposition, CODEOWNERS, operations decisions.
2. **Phase 1 — Foundation:** e2e package, Playwright config targeting UAT, environment safety contract, Vitest expansion, CI workflow skeletons, documentation.
3. **Phase 2 — Authentication Pilot:** Auth fixtures, page objects, login browser tests, auth guard/session tests, login component tests.
4. **Phase 3 — Coverage & Gates:** Manifest/route discovery scripts, impact analyzer, coverage builder, browser tests for all critical journeys, full CI workflows.
5. **Phase 4 — Accessibility & Visual:** Axe fixture, accessibility scans, visual regression with Git LFS baselines, baseline update workflow.
6. **Phase 5 — Impact Refinement:** Import-graph analyzer, AST style-only classification, agent skills.
7. **Phase 6 — Expansion:** Remaining journey tests, component tests, legacy retirement, full browser matrix, operations finalization.
**Consequences:** New `e2e/` package at repo root with Playwright, axe-core, TypeScript tooling. Two new CI workflows: `ui-tests-pr.yml` (spec validation + route coverage on PRs) and `ui-tests-deployed.yml` (browser tests after UAT deploy). All 15 existing Vitest tests rewritten with scenario IDs. Existing single e2e spec relocated to `e2e/legacy/` and rewritten. `frontend/playwright.config.ts` retired (replaced by `e2e/playwright.config.ts`). CODEOWNERS added for `specs/ui/`, `e2e/`, `frontend/src/**/*.test.*`, `docs/testing/`. Three on-demand OpenCode skills created (planner, impact analyst, failure analyst). Browser tests never run locally — CI-only against deployed UAT.

---

## 2026-09-17 — UI Testing Platform phase implementations

Detailed records for each phase's workflow and tooling changes.

## ADR-0025: UI Testing Platform Phase 1 — Foundation
**Status:** Accepted | **Date:** 2026-09-17
**Context:** Phase 0 discovery identified the need for a systematic UI testing strategy. Phase 1 establishes the baseline infrastructure — package structure, environment safety, CI workflows, and documentation — so all subsequent phases can build on a stable foundation.
**Decision:** Implement the Phase 1 foundation of the UI testing platform:
- Introduce `VITE_APP_ENVIRONMENT` environment marker so frontend code and tests can distinguish UAT from production at build/runtime.
- Create two initial CI workflow skeletons: `ui-tests-pr.yml` (spec validation + route coverage on PRs) and `ui-tests-deployed.yml` (browser tests after UAT deploy).
- Enforce deploy/test serialization: UAT deploys are gated on the build workflow completing successfully, and browser tests only run against a fully deployed environment — never locally.
- Expand Vitest suite from ~15 to 154 tests across 19 files with scenario IDs for traceability.
- Initialize `e2e/` package at repo root with Playwright, axe-core, and TypeScript tooling.
- Add documentation under `docs/testing/` and CODEOWNERS rules for `specs/ui/`, `e2e/`, and `frontend/src/**/*.test.*`.
**Consequences:** Subsequent phases (2–6) build on this foundation without rework. CI validates every PR and every UAT deploy. The environment marker prevents accidental cross-environment test execution.

## ADR-0026: UI Testing Platform Phase 3 — Full CI Gates
**Status:** Accepted | **Date:** 2026-09-17
**Context:** Phase 2 completed the authentication pilot. Phase 3 expands browser test coverage to all critical journeys and adds full CI gates that block merges on regressions.
**Decision:** Implement full CI gates for the UI testing platform:
- PR gate runs impact analysis, test-change lint, route coverage check, and Vitest suite on every pull request.
- Coverage history is persisted on a dedicated `test-reports` branch (JSON artifacts) for trend analysis.
- Production pre-flight gate validates that the UAT environment is healthy and reachable before any prod deploy workflow proceeds.
**Consequences:** Regressions are caught before merge. Coverage trends are trackable over time. Prod deploys are gated on UAT health, reducing the risk of shipping broken UIs.

## ADR-0027: UI Testing Platform Phase 4 — Visual Baselines
**Status:** Accepted | **Date:** 2026-09-17
**Context:** Accessibility and visual regression testing were missing entirely. Phase 4 introduces both with automated gates.
**Decision:** Implement visual regression and accessibility baselines:
- Visual regression uses Playwright screenshot comparison against Git LFS-managed baseline images.
- A weekly reliability measurement job quantifies flakiness and visual diff stability over time.
- A baseline update workflow (`ui-visual-baseline-update.yml`) allows authorized users to regenerate baselines after intentional UI changes.
- Axe-core accessibility scans run on critical pages with a configurable baseline threshold.
**Consequences:** Visual and accessibility regressions are caught automatically. Baseline updates are explicit, auditable operations. Git LFS keeps the repo size manageable.

## ADR-0028: UI Testing Platform Phase 5 — Impact Refinement
**Status:** Accepted | **Date:** 2026-09-17
**Context:** Phase 3's initial impact analysis used simple file-level heuristics. Phase 5 refines this to import-graph analysis for more accurate test selection.
**Decision:** Implement impact analysis v2:
- Import-graph analyzer traces TypeScript/React component imports to determine which tests are affected by a given change, replacing file-level heuristics.
- Gate metrics collected over 30 days of measurement inform thresholds and exclusions for the impact analyzer.
- Agent skills (planner, impact analyst, failure analyst) are created as OpenCode skills to assist developers with test planning and failure triage.
**Consequences:** Fewer irrelevant tests run on PRs, reducing CI time. Agent skills provide structured guidance for common testing workflows.

## ADR-0029: UI Testing Platform Phase 6 — Legacy Retirement
**Status:** Accepted | **Date:** 2026-09-17
**Context:** After Phases 1–5, the new UI testing platform achieves parity with and exceeds the coverage of legacy test artifacts. Phase 6 retires the old and expands to full browser coverage.
**Decision:** Complete the UI testing platform:
- Retire legacy test artifacts (old Playwright config, `frontend/playwright.config.ts`, legacy e2e specs) after confirming parity with new platform coverage.
- Expand browser matrix to chromium, firefox, webkit, and mobile-chrome to validate cross-browser compatibility.
- Finalize the operations handbook under `docs/testing/` covering test authoring, debugging, baseline updates, and CI pipeline behavior.
**Consequences:** Single source of truth for UI testing. Cross-browser coverage reduces production surprises. Operations handbook enables onboarding without tribal knowledge.

## ADR-0030: Repair and re-target the deployed UI test workflow
**Status:** Accepted | **Date:** 2026-09-18 | **Deciders:** @saurabhbilakhia
**Context:** `ui-tests-deployed.yml` failed on every trigger with 0 jobs and instant failure. Root cause: the `secrets` context was used in a job-level `if` (notify-failure), which makes the workflow file invalid at parse time; GitHub rejected the file on every push regardless of trigger. A secondary latent flaw: the workflow triggered on "Build & Push Images" completion in parallel with the Deploy workflow, so tests would run before UAT was updated.
**Decision:**
- Trigger re-targeted to `workflow_run` on "Deploy" (conclusion success, branch main), serializing the chain Build → Deploy → UI tests, per design spec §5.3 and the investment-club-platform reference pattern.
- Guard on test jobs: `workflow_dispatch` OR (upstream conclusion success AND `workflow_run.event == 'workflow_run'` AND `head_branch == 'main'`) — excludes prod dispatch deploys.
- Manual `workflow_dispatch` with `suite` (all|smoke) and `update_visual_baselines` inputs; the baseline commit step runs only on explicit dispatch.
- Tag model enforced: `@smoke`, `@regression`, `@a11y`, `@visual` — CI greps must always match ≥1 test.
- `@playwright/test` pinned exactly 1.60.0 to match the container `mcr.microsoft.com/playwright:v1.60.0-noble` (the lockfile had floated to 1.63.0).
- Artifact uploads aligned to actual output dirs (`e2e/results/`, `e2e/playwright-report/`); both gitignored.
- Authenticated suites receive `APP_TEST_ADMIN_EMAIL`/`APP_TEST_ADMIN_PASSWORD` from GitHub secrets (single admin test account used for all suites; no committed seed accounts).
- `deploy.yml` gains a `verify-deploy` sentinel job that fails the run when the deploy job is skipped, preventing downstream tests from firing against a stale UAT after a failed build.
**Consequences:** Deployed browser suites now execute automatically after each UAT deploy. The first visual run requires a one-time baseline bootstrap via dispatch. Accessibility violations are triaged through the baseline file rather than hard failure.

---

## ADR-0031: Source UI Test Credentials from Vault via AppRole

**Date:** 2026-09-19
**Status:** Accepted
**Deciders:** @saurabhbilakhia

### Context

The deployed UI test workflow sourced `APP_TEST_ADMIN_EMAIL`/`APP_TEST_ADMIN_PASSWORD` from GitHub repository secrets — which were never provisioned, so the authenticated suites skipped gracefully on every run. The credentials actually live in HashiCorp Vault KV v2 at `secret/portfolio/uat`, matching the repository secrets policy (`secret/portfolio/common` + `secret/portfolio/{env}`).

### Decision

- The three authenticated test jobs (regression, accessibility, visual) in `ui-tests-deployed.yml` authenticate to Vault with AppRole using the existing `VAULT_ROLE_ID`/`VAULT_SECRET_ID` GitHub secrets (same pattern as `deploy.yml`), read the KV secret at `secret/data/portfolio/uat`, and export `APP_TEST_ADMIN_EMAIL`/`APP_TEST_ADMIN_PASSWORD` to `$GITHUB_ENV`.
- `VAULT_ADDR` (`https://vault.nanobyte.ca`) is set at workflow level.
- JSON parsing uses `node` (`jq` is not guaranteed in the Playwright container).
- The fetch step fails fast with an actionable error if authentication fails or the keys are missing/empty in Vault.
- No GitHub secrets are required for the test credentials.

### Consequences

- Credential rotation happens in Vault only: `vault kv put secret/portfolio/uat APP_TEST_ADMIN_EMAIL=<...> APP_TEST_ADMIN_PASSWORD=<...>`.
- The authenticated suites run for real once Vault contains the keys — no GitHub-side provisioning step.
- The fetch step is duplicated across the three jobs (jobs are isolated).
- Vault token and credentials stay within the job environment; values are never echoed to logs.

## ADR-0032: Disambiguate Docker network aliases across shared environments

**Status:** Accepted
**Date:** 2026-09-19

**Context:** Docker Compose attaches each service key as a network alias. `pc` and `investclub` both deploy a service key `backend` onto the shared external networks `uat-internal-network` / `infra-uat-network` (and the prod equivalents). The `pc` frontend's nginx resolves `backend` to both backends and round-robins, so roughly half of all portfolio API calls were answered by investclub's backend, which correctly rejects the portfolio origin with `403 Invalid CORS request`. The deployed UI test suite (run 35420097091) showed ~50–60% authenticated failures/flakiness. Prod carries the identical latent collision.

**Decision:** Rename compose service keys in `deploy/uat/docker-compose.yml`, `deploy/prod/docker-compose.yml`, and the root `docker-compose.yml`: `backend` → `portfolio-backend`, `ingestion-service` → `portfolio-ingestion`, `market-data-service` → `portfolio-market-data`, `strategy-service` → `portfolio-strategy`, `broker-gateway-service` → `portfolio-broker-gateway`, `frontend` → `portfolio-frontend`. `frontend/nginx.conf` proxies to the app-prefixed names, so one static config is correct in every environment. Container names, host ports, compose project names, images, and network memberships are unchanged (invariants #2 and #3 preserved). Compose service keys (and therefore network aliases) must be app-prefixed and globally unique; never use generic names (`backend`, `frontend`, `api`, `db`) on shared external networks. `deploy.yml` and `deploy-prod.yml` run `docker compose up -d --remove-orphans` so service removals do not leave stale containers; the first renamed deploy additionally requires a one-time `docker compose down --remove-orphans` on each host because explicit `container_name` values cannot be adopted by a renamed service. Cross-service URLs use the full container name or the unique `portfolio-*` alias; Prometheus scrapes by host port and Grafana log alerts filter by unchanged container names, so monitoring is unaffected. Loki's `service` label (derived from the compose service key by Promtail) changes value from `backend` to `portfolio-backend`; no current query depends on the old value. The alias fix also restores investclub's frontend resolution (its nginx stops receiving `pc` backend IPs); sibling stacks on the shared networks are advised to adopt the same convention.

**Consequences:** Portfolio UAT and prod resolve every upstream unambiguously; intermittent `403 Invalid CORS request` from cross-app aliasing is eliminated. The first deploy after the rename requires a maintenance window and a `down`/`up` reconciliation; later deploys are unchanged. Historical documents that show the old service keys are append-only records and are not rewritten. This decision also corrects ADR-0031's consequence that credential rotation happens in Vault only — rotation spans both Vault and the database (see the incident record, spec §9.1).
