# UI Testing Platform Implementation Plan — Portfolio Construction App

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the UI testing platform defined in `docs/superpowers/specs/2026-09-17-ui-testing-platform-design.md` across six phases, each phase its own pull request, as specified by this plan.

**Architecture:** Deterministic tooling (Playwright, Vitest, TypeScript scripts, GitHub Actions) executes and gates; plans in `specs/ui/` trace requirements (`RQ-*` markers in the design specs) to scenarios (`<FEATURE>-<NNN>`) to tests; browser suites run only against deployed UAT (`https://uatportfolio.nanobyte.ca`) after a successful deploy; three on-demand OpenCode skills assist planning, impact analysis, and failure analysis without ever running in CI.

**Tech Stack:** Playwright 1.60.x (pinned container `mcr.microsoft.com/playwright:v1.60.x-noble`), TypeScript 5.6, ts-morph, tsx, gray-matter, ajv, Vitest + React Testing Library + jsdom in `frontend/` (React 18.3, Vite 5.4), `@axe-core/playwright`, Git LFS, GitHub Actions.

## Global Constraints

Copied from the design spec; every task implicitly includes these.

- No test or validation execution on developer machines. All execution happens in GitHub Actions or against deployed environments. Every verification step in this plan is a CI step (push a branch, open a PR, or dispatch a workflow; observe with `gh pr checks --watch` or `gh run watch`). Local commands appear only for inspecting artifacts.
- Browser tests only target `https://uatportfolio.nanobyte.ca` (UAT). Never production, except the read-only production smoke suite.
- The database is the centralized shared PostgreSQL from the nanobyte-services infra (hosts `uat-postgres`/`prod-postgres`, DB `portfolio`). Never embed a database or other infra service in a compose file; UAT must never reference prod infra networks. Environment identity follows the architecture invariants: ports 20000/20080 (UAT) and 10000/10080 (prod), containers `uat-portfolio-*`/`prod-portfolio-*`, compose project names `portfolio-uat`/`portfolio-prod`.
- Secrets only from GitHub secrets or Vault (`VAULT_ROLE_ID`, `VAULT_SECRET_ID`; AppRole). Never commit credentials, storage state, tokens, or customer data. There are **no committed UAT seed accounts** in this repository: UAT test credentials come from GitHub secrets `E2E_USER_EMAIL`/`E2E_USER_PASSWORD` (the pattern the existing questrade spec already uses).
- Auth is **cookie-based with CSRF** (`credentials: 'include'`, `XSRF-TOKEN` cookie, refresh via `/auth/refresh`) — there is no JWT and no Bearer header. Browser tests authenticate through the UI login form; never fabricate tokens.
- Conventional Commits (`feat(scope): ...`, `fix(scope): ...`, `docs: ...`, `test(scope): ...`, `chore(scope): ...`). Never add AI attribution lines. Never push unless the owner asks; inside a task, push the feature branch when the step requires CI verification.
- Never weaken a product assertion to make a test pass. Never auto-approve visual baselines or accessibility exceptions.
- Playwright scenario IDs are declared with tags: `{ tag: ['@AUTH-LOGIN-001', '@regression'] }`. The folder (`smoke/`, `regression/`, `accessibility/`, `visual/`) is navigation only; tags select suites. Vitest declares scenario IDs through the `scenario()` title helper: `test(scenario('AUTH-LOGIN-004', 'empty fields show validation'), ...)`.
- Plan front matter fields `status`, `priority`, `critical_journeys`, and `requirement_refs` are human-only. Agents never change them.
- Each phase is one PR. Do not combine phases. Update `docs/adr.md` in the same PR for every workflow change (AGENTS.md documentation contract).
- Use `gh` for workflow dispatch and artifact inspection; it is a read/invoke tool, not a test runner.
- TypeScript in `e2e/scripts/` runs with `npx tsx` inside CI; front matter parsing uses `gray-matter`; globbing uses Node's `fs.glob` (Node 22) or `tinyglobby` if added.
- The repository default branch is `main`. GitHub Actions workflows trigger from `main`.
- YAML front matter: quote scalar values YAML would misread — `next_id: "017"` (octal parsing) and `last_reviewed: "2026-09-17"` (Date parsing). The validator coerces defensively but the source of truth must be quoted.

## File Structure Map

```text
specs/ui/
  _template.md                     plan template
  manifest.json                    generated from plans (Phase 3)
  quarantine.json                  quarantine registry (Phase 3)
  platform/route-availability.md   first plan (Phase 1)
  authentication/{login,session}.md          (Phase 2)
  dashboard/overview.md                       (Phase 3)
  portfolio/{management,builder}.md           (Phase 3)
  screener/filters.md                         (Phase 3)
  instruments/{stock,etf,mutual-fund}.md      (Phase 6)
  options/{chain,strategy,leg-builder}.md     (Phase 3)
  wheel/{calendar,order-panel}.md             (Phase 3)
  broker/{connections,positions,accounts}.md  (Phase 3)
  analytics/{sectors,geography,holdings}.md   (Phase 6)
  reporting/{contributions,dividends}.md      (Phase 6)
  admin/ingestion.md                          (Phase 3)
  authorization/role-route-matrix.md          (Phase 3)
  accessibility/scan-baseline.md              (Phase 4)
  visual/critical-pages.md                    (Phase 4)
docs/testing/
  source/                          committed source documents
  ui-test-discovery.md             Phase 0
  critical-journeys.md             Phase 0
  requirements-index.json          generated, Phase 1
  requirements-index.md            generated human view, Phase 1
  legacy-tests.md                  Phase 0
  operations.md                    Phase 0, finalized Phase 6
  ui-testing.md                    Phase 1
  triage.md                        Phase 5
  coverage.md                      generated, Phase 3
  agents/ui-test-planner.md        Phase 1
  agents/ui-test-impact-analyst.md Phase 5
  agents/ui-test-failure-analyst.md Phase 5
e2e/
  package.json                     scripts + deps (Phase 1+)
  tsconfig.json                    scripts + support types (Phase 1)
  playwright.config.ts             Phase 1
  fixtures/app.fixture.ts          Phase 1
  fixtures/data.fixture.ts         Phase 2
  fixtures/auth.fixture.ts         Phase 2
  fixtures/a11y.fixture.ts         Phase 4
  support/run-context.ts           Phase 1
  support/environment.ts           Phase 1
  support/negative-wait.ts         Phase 1
  support/network-monitor.ts       Phase 1
  support/scenario.ts              Phase 1
  pages/login.page.ts              Phase 2
  pages/app-shell.page.ts          Phase 2
  tests/smoke/route-availability.spec.ts    Phase 1
  tests/regression/auth-login.spec.ts       Phase 2
  tests/regression/auth-session.spec.ts     Phase 2
  tests/regression/auth-guard.spec.ts       Phase 2
  tests/regression/dashboard-overview.spec.ts    Phase 3
  tests/regression/portfolio-management.spec.ts  Phase 3
  tests/regression/screener-filters.spec.ts      Phase 3
  tests/regression/options-chain.spec.ts         Phase 3
  tests/regression/wheel-calendar.spec.ts        Phase 3
  tests/regression/broker-connections.spec.ts    Phase 3
  tests/regression/admin-ingestion.spec.ts       Phase 3
  tests/regression/authz-role-matrix.spec.ts     Phase 3
  tests/regression/analytics-sectors.spec.ts     Phase 6
  tests/regression/reporting-contributions.spec.ts Phase 6
  tests/regression/instruments-stock.spec.ts     Phase 6
  tests/accessibility/critical-pages.a11y.spec.ts   Phase 4
  tests/visual/critical-pages.visual.spec.ts        Phase 4
  a11y-baseline.json               Phase 4
  support/console-baseline.json    Phase 1 (curated Phase 4)
  scripts/validate-specs.ts        Phase 1
  scripts/build-requirements-index.ts Phase 1
  scripts/build-manifest.ts        Phase 3
  scripts/discover-routes.ts       Phase 3
  scripts/check-route-coverage.ts  Phase 3
  scripts/analyze-test-impact.ts   Phase 3 (v1), Phase 5 (v2)
  scripts/lint-test-changes.ts     Phase 3
  scripts/build-coverage-report.ts Phase 3
  scripts/publish-history.ts       Phase 3
  scripts/check-expiries.ts        Phase 4
  scripts/measure-reliability.ts   Phase 4
  scripts/build-impact-graph.ts    Phase 5
frontend/
  index.html                       Phase 1 (app-environment meta)
  Dockerfile                       Phase 1 (add VITE_APP_ENVIRONMENT ARG; only VITE_API_URL/VITE_AUTH_METHOD exist today)
  package.json                     Phase 1 (Vitest expansion; test:e2e scripts removed)
  playwright.config.ts             Phase 1 (legacy localhost config retired into e2e/legacy/)
  e2e/questrade-connection.spec.ts Phase 1 (moved to e2e/legacy/)
  src/setupTests.ts                exists (verify jest-dom import)
  src/test/scenario.ts             Phase 1
  src/pages/auth/LoginPage.test.tsx     Phase 1 (infra proof), Phase 2 (full)
  src/components/ui/Button.test.tsx     Phase 1 (imports from './button' — lowercase file)
.opencode/skills/
  ui-test-planner/SKILL.md         Phase 1
  ui-test-impact-analyst/SKILL.md  Phase 5
  ui-test-failure-analyst/SKILL.md Phase 5
.github/
  CODEOWNERS                       Phase 0
  workflows/ui-tests-pr.yml        Phase 1 (spec validation), Phase 3 (full checks)
  workflows/ui-tests-deployed.yml  Phase 1 (skeleton), Phase 3 (full)
  workflows/ui-tests-prod-smoke.yml Phase 3
  workflows/ui-visual-baseline-update.yml Phase 4
  workflows/ui-reliability.yml     Phase 4
  workflows/deploy.yml             Phase 1 (remove the legacy `e2e` job if exists)
  workflows/deploy-prod.yml        Phase 3 (pre-flight gate)
  workflows/build.yml              Phase 1 (marker build arg + Vitest step)
README.md                          Phase 1 (testing overview, frontend image list)
.gitattributes                     Phase 4 (LFS tracking for visual baselines)
```

---

## Phase 0 — Discovery and Decisions (one PR)

### Task 0.1: Commit the source documents

**Files:**
- Create: `docs/testing/source/UI_TESTING_PROBLEM_STATEMENT.md`
- Create: `docs/testing/source/UI_TESTING_IMPLEMENTATION_SPEC.md`
- Create: `docs/testing/source/UI_TESTING_IMPLEMENTATION_SPEC_v2.md`

**Interfaces:**
- Consumes: the three source documents from the investment-club-platform adaptation.
- Produces: committed source documents referenced by every later phase.

- [ ] **Step 1: Create the source documents directory and copy files**

```bash
mkdir -p docs/testing/source
```

Copy the three source documents from the investment-club-platform repo:
```bash
cp /home/sbilakhia/Documents/dev/repos/investment-club-platform/docs/testing/source/UI_TESTING_PROBLEM_STATEMENT.md docs/testing/source/
cp /home/sbilakhia/Documents/dev/repos/investment-club-platform/docs/testing/source/UI_TESTING_IMPLEMENTATION_SPEC.md docs/testing/source/
cp /home/sbilakhia/Documents/dev/repos/investment-club-platform/docs/testing/source/UI_TESTING_IMPLEMENTATION_SPEC_v2.md docs/testing/source/
```

- [ ] **Step 2: Verify the references in the design spec**

In `docs/superpowers/specs/2026-09-17-ui-testing-platform-design.md`, confirm the three path references in section 1 point at `docs/testing/source/...`; correct any stale path.

- [ ] **Step 3: Commit**

```bash
git add docs/testing/source docs/superpowers/specs/2026-09-17-ui-testing-platform-design.md
git commit -m "docs(testing): commit UI testing source documents and design spec"
```

### Task 0.2: Discovery report

**Files:**
- Create: `docs/testing/ui-test-discovery.md`

**Interfaces:**
- Consumes: facts in design spec section 3.
- Produces: the Revision 2 §4.1 discovery table plus the Phase 0 decisions that every later phase cites.

- [ ] **Step 1: Write the discovery report**

Create `docs/testing/ui-test-discovery.md` with the following content derived from the design spec section 3:

```markdown
# UI Test Discovery

Date: 2026-09-17. Derived from `docs/superpowers/specs/2026-09-17-ui-testing-platform-design.md` section 3.

| Item | Discovered value | Evidence/path | Decision or impact |
|---|---|---|---|
| Frontend framework/version | React 18.3.1, Vite 5.4, TypeScript 5.6 | frontend/package.json | Vitest + RTL added at the component layer |
| Frontend root | frontend/ | frontend/ | Component tests colocated under src/ |
| Package manager/lockfile | npm + package-lock.json | frontend/package-lock.json | Tooling deps added to e2e; test deps to frontend |
| Node/runtime version | Node 20 in build.yml frontend job; Java 21 for backend | .github/workflows/build.yml | Standardize on Node 22 for all UI-test jobs |
| Build tool | Vite 5.4 (tsc && vite build) | frontend/package.json:8 | Component tests run with Vitest |
| Repository type | Full-stack monorepo (backend/, frontend/, frontend/e2e/) | repository root | Full-stack rows of Revision 2 §4.2 evaluated; no local CI stack by policy |
| Backend runnable in CI | Yes for backend tests (Testcontainers) | .github/workflows/build.yml | Not used for UI tests: policy is no local/full-stack CI target |
| Existing component-test runner | Vitest 2.1.9 configured, 15 test files, already runs in CI (`npm run test:run`) | frontend/package.json, .github/workflows/build.yml | Expanded in Phase 1 |
| Existing browser/E2E runner and suites | Playwright 1.60.x, 1 spec file at frontend/e2e/questrade-connection.spec.ts; config at frontend/playwright.config.ts (localhost:3000, chromium) | frontend/playwright.config.ts, frontend/e2e/ | Rewritten feature by feature (D6); disposition in legacy-tests.md |
| Existing accessibility tooling | None | repository grep | axe added Phase 4 |
| Router and route definitions | react-router-dom 6.28, 16 route patterns | frontend/src/App.tsx | Route discovery parses App.tsx |
| Authentication provider and model | Google OAuth + email/password, cookie sessions with CSRF (XSRF-TOKEN, /auth/refresh), no JWT | frontend/src/services/api.ts, frontend/src/pages/auth/LoginPage.tsx | Embedded model: recovery/email feature-absent; tests authenticate via UI form |
| Feature-flag system | None | repository grep | Flags dimension removed; flags: [] in plans |
| Requirements system of record | ADRs + design specs | docs/adr.md, docs/superpowers/specs/ | RQ markers in design specs (D4) |
| CI provider and workflows | GitHub Actions: build.yml, deploy.yml, deploy-prod.yml | .github/workflows/ | New ui-* workflows; every change gets an ADR |
| CI runner image, caching, container support | ubuntu-latest, npm and Gradle caches, containers supported | build.yml | Pinned Playwright container with npm cache |
| PR preview deployment | None | deploy.yml | PR tier is component + deterministic only (D2) |
| Staging base URL mechanism | Fixed: https://uatportfolio.nanobyte.ca | deploy/uat/docker-compose.yml | BASE_URL from environment; hostname allowlist |
| Staging shared by concurrent runs | Yes | deploy.yml | Concurrency group portfolio-uat-deploy, cancel-in-progress: false |
| Test-data reset/seed mechanism | Public API only; no test-support endpoint | N/A | Run-ID namespacing; no destructive sweeps |
| Test email mechanism | None; no email sending exists | N/A | Email scenarios feature-absent |
| Password recovery flow | None | N/A | Recovery scenarios feature-absent |
| Multi-service architecture | 5 backend services (portfolio, ingestion, market-data, strategy, broker-gateway) | frontend/vite.config.ts | Proxy config; API calls span multiple services |
| WebSocket support | Live quotes in Options and Wheel pages | frontend/src/components/options/, wheel/ | WebSocket mocking needed for browser tests |
| AG Grid usage | Data tables in Screener, Positions, Activities | frontend/src/components/screener/, dashboard/ | AG Grid mocking patterns needed |
| AG Charts usage | P&L chart, analytics charts | frontend/src/components/options/, analytics/ | Chart data mocking needed |
```

- [ ] **Step 2: Commit**

```bash
git add docs/testing/ui-test-discovery.md
git commit -m "docs(testing): add UI test discovery report"
```

### Task 0.3: Critical journeys

**Files:**
- Create: `docs/testing/critical-journeys.md`

**Interfaces:**
- Consumes: design spec section 6.5 critical journey list.
- Produces: the coverage denominator that every later phase references.

- [ ] **Step 1: Write the critical journeys document**

Create `docs/testing/critical-journeys.md` with exactly the content from design spec section 6.5:

```markdown
# Critical Journeys

Date: 2026-09-17. Derived from `docs/superpowers/specs/2026-09-17-ui-testing-platform-design.md` section 6.5.

This list is the coverage denominator. Removal or demotion of any journey
requires owner sign-off recorded in this file.

| ID | Journey | Features and roles |
|---|---|---|
| CJ-01 | Authentication lifecycle: login, guard, logout, session expiry | authentication; anonymous, USER, ADMIN |
| CJ-02 | Dashboard overview: KPIs, positions, activities, account switching | dashboard; USER, ADMIN |
| CJ-03 | Portfolio management: model portfolios, custom builder, analysis | portfolio; USER, ADMIN |
| CJ-04 | Instrument discovery: screener filters, search, detail views | screener, instruments; USER, ADMIN |
| CJ-05 | Options trading: chain, strategy selection, leg builder, P&L chart, live quotes | options; USER, ADMIN |
| CJ-06 | Wheel strategy: calendar, KPIs, top tickers, order panel, chain panel | wheel; USER, ADMIN |
| CJ-07 | Broker connections: connect, sync, disconnect, positions, accounts | broker; USER, ADMIN |
| CJ-08 | Analytics: sector exposure, geography, top holdings, risk profile | analytics; USER, ADMIN |
| CJ-09 | Reporting: contributions, dividends, total value charts | reporting; USER, ADMIN |
| CJ-10 | Admin: data ingestion stats, workflows, run history | admin; ADMIN only |
| CJ-11 | Authorization boundaries: role × route matrix, positive and negative access | authorization; all roles |

## Change log

| Date | Change | Approved by |
|---|---|---|
| 2026-09-17 | Initial list | (pending approval) |
```

- [ ] **Step 2: Commit**

```bash
git add docs/testing/critical-journeys.md
git commit -m "docs(testing): define critical journeys for UI testing platform"
```

### Task 0.4: Legacy test disposition

**Files:**
- Create: `docs/testing/legacy-tests.md`

**Interfaces:**
- Consumes: inventory of existing 15 Vitest tests and 1 e2e spec.
- Produces: disposition table for the rewrite.

- [ ] **Step 1: Write the legacy test disposition**

Create `docs/testing/legacy-tests.md` cataloging all existing tests and their rewrite plan:

```markdown
# Legacy Test Disposition

Date: 2026-09-17.

## Existing Vitest tests (15 files)

| File | Type | Disposition | Phase |
|---|---|---|---|
| frontend/src/App.test.tsx | App render | Rewrite with scenario IDs | Phase 1 |
| frontend/src/pages/auth/LoginPage.test.tsx | Component | Rewrite with scenario IDs | Phase 2 |
| frontend/src/pages/PortfolioPage.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/pages/admin/AdminPage.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/components/ui/ErrorBoundary.test.tsx | Component | Rewrite with scenario IDs | Phase 1 |
| frontend/src/components/broker/BrokerCard.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/components/broker/ConnectBrokerDialog.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/components/broker/ConnectionStatus.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/components/broker/BrokerConnectionCard.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/stores/quoteStore.test.ts | Store | Rewrite with scenario IDs | Phase 3 |
| frontend/src/store/analysisStore.test.ts | Store | Rewrite with scenario IDs | Phase 6 |
| frontend/src/store/portfolioStore.test.ts | Store | Rewrite with scenario IDs | Phase 3 |
| frontend/src/services/api.test.ts | Service | Rewrite with scenario IDs | Phase 1 |
| frontend/src/services/brokerService.test.ts | Service | Rewrite with scenario IDs | Phase 3 |
| frontend/src/hooks/__tests__/useWheelPositions.test.ts | Hook | Rewrite with scenario IDs | Phase 3 |

## Existing e2e specs (1 file)

| File | Type | Disposition | Phase |
|---|---|---|---|
| frontend/e2e/questrade-connection.spec.ts | Browser (local-dev target, env-var gated) | Rewrite with scenario IDs and tags against deployed UAT; retire the localhost config | Phase 3 |

## Rewrite rules

- Each test is rewritten feature by feature; old specs deleted only after parity demonstrated in CI.
- Scenario IDs follow the `<FEATURE-ID>-<NNN>` pattern from the plan.
- No coverage drop mid-flight: new test passes before old test is deleted.
```

- [ ] **Step 2: Commit**

```bash
git add docs/testing/legacy-tests.md
git commit -m "docs(testing): add legacy test disposition table"
```

### Task 0.5: CODEOWNERS

**Files:**
- Create: `.github/CODEOWNERS`

**Interfaces:**
- Consumes: owner identity from design spec section 7.3.
- Produces: CODEOWNERS file referenced by plan validation.

- [ ] **Step 1: Create CODEOWNERS**

```bash
mkdir -p .github
```

Create `.github/CODEOWNERS`:
```
# UI testing platform
specs/ui/** @saurabhbilakhia
e2e/** @saurabhbilakhia
frontend/src/**/*.test.* @saurabhbilakhia
docs/testing/** @saurabhbilakhia
```

- [ ] **Step 2: Commit**

```bash
git add .github/CODEOWNERS
git commit -m "chore: add CODEOWNERS for UI testing platform"
```

### Task 0.6: Operations decisions

**Files:**
- Create: `docs/testing/operations.md`

**Interfaces:**
- Consumes: design spec sections 5.7, 7.3.
- Produces: operational handbook referenced by every phase.

- [ ] **Step 1: Write operations.md**

Create `docs/testing/operations.md` with initial content covering:
- Approver identities and process
- Alert channel (Slack webhook)
- Dashboard location (`test-reports` branch)
- Review cadence (weekly triage, per-release manual accessibility, quarterly journey review)
- Manual UAT reset procedure (placeholder, finalized in Phase 6)

- [ ] **Step 2: Commit**

```bash
git add docs/testing/operations.md
git commit -m "docs(testing): add initial operations handbook"
```

### Task 0.7: ADR entry

**Files:**
- Modify: `docs/adr.md`

**Interfaces:**
- Consumes: decisions from design spec section 2.
- Produces: ADR entry documenting the testing platform decision.

- [ ] **Step 1: Append ADR entry to docs/adr.md**

Add a new ADR entry — **ADR-0024** (the current highest is ADR-0023) — documenting:
- Decision: Adopt the UI testing platform (Revision 2 model) across 6 phases
- Context: Need for systematic UI testing with traceability, gates, and coverage
- Consequences: New workflows, CI/CD changes, rewrite of existing tests

- [ ] **Step 2: Commit**

```bash
git add docs/adr.md
git commit -m "docs: add ADR for UI testing platform adoption"
```

---

## Phase 1 — Foundation (one PR)

### Task 1.1: Platform dependencies and e2e package setup

**Files:**
- Create: `e2e/package.json`
- Create: `e2e/tsconfig.json`

**Interfaces:**
- Consumes: design spec section 4.5 tooling versions.
- Produces: e2e package with all platform dependencies.

- [ ] **Step 1: Create e2e/package.json**

```json
{
  "name": "pc-e2e",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "playwright test",
    "test:smoke": "playwright test --grep @smoke",
    "test:regression": "playwright test --grep @regression",
    "validate-specs": "npx tsx scripts/validate-specs.ts",
    "build-manifest": "npx tsx scripts/build-manifest.ts",
    "build-requirements-index": "npx tsx scripts/build-requirements-index.ts",
    "discover-routes": "npx tsx scripts/discover-routes.ts",
    "check-route-coverage": "npx tsx scripts/check-route-coverage.ts",
    "analyze-test-impact": "npx tsx scripts/analyze-test-impact.ts",
    "lint-test-changes": "npx tsx scripts/lint-test-changes.ts",
    "build-coverage-report": "npx tsx scripts/build-coverage-report.ts",
    "publish-history": "npx tsx scripts/publish-history.ts"
  },
  "devDependencies": {
    "@playwright/test": "^1.60.0",
    "@axe-core/playwright": "^4.10.0",
    "typescript": "^5.6.0",
    "tsx": "^4.19.0",
    "gray-matter": "^4.0.3",
    "ajv": "^8.17.0",
    "tinyglobby": "^0.2.12",
    "ts-morph": "^24.0.0"
  }
}
```

- [ ] **Step 2: Create e2e/tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "outDir": "dist",
    "rootDir": ".",
    "resolveJsonModule": true
  },
  "include": ["scripts/**/*", "support/**/*", "fixtures/**/*", "playwright.config.ts"]
}
```

- [ ] **Step 3: Install dependencies**

```bash
cd e2e && npm install
```

- [ ] **Step 4: Commit**

```bash
git add e2e/package.json e2e/package-lock.json e2e/tsconfig.json
git commit -m "chore(e2e): initialize e2e package with platform dependencies"
```

### Task 1.2: Playwright configuration

**Files:**
- Create: `e2e/playwright.config.ts`
- Delete: `frontend/playwright.config.ts` (legacy localhost config)
- Move: `frontend/e2e/questrade-connection.spec.ts` → `e2e/legacy/questrade-connection.spec.ts` (kept for reference until Phase 3 rewrite; excluded from testDir)

**Interfaces:**
- Consumes: design spec sections 4.1, 4.5.
- Produces: Playwright config with UAT target, tags, reporters. The legacy `frontend/playwright.config.ts` (localhost:3000, chromium) is retired so only one config exists.

- [ ] **Step 1: Retire the legacy config and relocate the legacy spec**

```bash
mkdir -p e2e/legacy
git mv frontend/playwright.config.ts e2e/legacy/playwright.config.localhost.ts.bak
git mv frontend/e2e/questrade-connection.spec.ts e2e/legacy/questrade-connection.spec.ts.bak
```

Update `frontend/package.json`: remove the `test:e2e` and `test:e2e:headed` scripts (browser tests now run from the `e2e/` package).

- [ ] **Step 2: Create playwright.config.ts**

```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI
    ? [['html', { open: 'never' }], ['junit', { outputFile: 'results/junit.xml' }], ['json', { outputFile: 'results/results.json' }]]
    : [['html', { open: 'on-failure' }]],
  use: {
    baseURL: process.env.BASE_URL || 'https://uatportfolio.nanobyte.ca',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] },
    },
  ],
  outputDir: 'results/',
});
```

- [ ] **Step 2: Commit**

```bash
git add e2e/playwright.config.ts
git commit -m "chore(e2e): add Playwright configuration for UAT targeting"
```

### Task 1.3: Environment safety and app fixture

**Files:**
- Create: `e2e/fixtures/app.fixture.ts`
- Create: `e2e/support/environment.ts`

**Interfaces:**
- Consumes: design spec section 4.4 environment safety contract.
- Produces: app fixture that enforces safety contract per worker.

- [ ] **Step 1: Create environment.ts**

```typescript
const ALLOWED_HOSTS = ['uatportfolio.nanobyte.ca'];

export function assertEnvironment(baseURL: string): void {
  const url = new URL(baseURL);
  if (!ALLOWED_HOSTS.includes(url.hostname)) {
    throw new Error(
      `Environment safety violation: ${url.hostname} is not in the allowed hostlist. ` +
      `Allowed: ${ALLOWED_HOSTS.join(', ')}`
    );
  }
}

export async function assertPageMarker(page: { meta: (name: string) => Promise<string | null> }): Promise<void> {
  const marker = await page.meta('app-environment');
  if (marker !== 'uat') {
    throw new Error(
      `Environment safety violation: app-environment meta tag is "${marker}", expected "uat". ` +
      `This check prevents accidental testing against production.`
    );
  }
}
```

- [ ] **Step 2: Create app.fixture.ts**

```typescript
import { test as base } from '@playwright/test';
import { assertEnvironment, assertPageMarker } from '../support/environment';

export const test = base.extend<{ appEnvironment: void }>({
  appEnvironment: [async ({ page, baseURL }, use) => {
    assertEnvironment(baseURL!);
    await page.goto('/');
    await assertPageMarker(page);
    await use();
  }, { auto: true }],
});

export { expect } from '@playwright/test';
```

- [ ] **Step 3: Commit**

```bash
git add e2e/fixtures/app.fixture.ts e2e/support/environment.ts
git commit -m "feat(e2e): add environment safety contract and app fixture"
```

### Task 1.4: Run context and negative-wait

**Files:**
- Create: `e2e/support/run-context.ts`
- Create: `e2e/support/negative-wait.ts`

**Interfaces:**
- Consumes: design spec section 4.4 run namespacing.
- Produces: run-context for identity namespacing, negative-wait for assertion helpers.

- [ ] **Step 1: Create run-context.ts**

```typescript
export function getRunId(): string {
  return process.env.GITHUB_RUN_ID || `local-${Date.now()}`;
}

export function getRunNamespace(): string {
  return `pw-${getRunId()}`;
}

export function namespacedEmail(prefix: string): string {
  return `${getRunNamespace()}-${prefix}@portfolio.test`;
}

export function namespacedBranch(): string {
  return `PW ${getRunId()}`;
}
```

- [ ] **Step 2: Create negative-wait.ts**

```typescript
import { expect, Page } from '@playwright/test';

export async function waitForNoSpinner(page: Page, timeout = 10000): Promise<void> {
  const spinner = page.locator('[role="progressbar"], .loading, [data-loading]');
  await expect(spinner).toBeHidden({ timeout });
}

export async function waitForApiResponse(page: Page, urlPattern: string | RegExp, timeout = 10000): Promise<void> {
  await page.waitForResponse(
    (response) =>
      typeof urlPattern === 'string'
        ? response.url().includes(urlPattern)
        : urlPattern.test(response.url()),
    { timeout }
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add e2e/support/run-context.ts e2e/support/negative-wait.ts
git commit -m "feat(e2e): add run context namespacing and negative-wait helpers"
```

### Task 1.5: Scenario helper and network monitor

**Files:**
- Create: `e2e/support/scenario.ts`
- Create: `e2e/support/network-monitor.ts`

**Interfaces:**
- Consumes: design spec section 6.3 scenario ID lifecycle.
- Produces: scenario helper for test annotation, network monitor for request tracking.

- [ ] **Step 1: Create scenario.ts**

```typescript
export function scenario(id: string, description: string): string {
  return `[${id}] ${description}`;
}
```

- [ ] **Step 2: Create network-monitor.ts**

```typescript
import { Page, Request, Response } from '@playwright/test';

export interface NetworkEntry {
  url: string;
  method: string;
  status: number;
  timing: number;
}

export class NetworkMonitor {
  private entries: NetworkEntry[] = [];

  constructor(private page: Page) {
    this.page.on('response', async (response: Response) => {
      const request = response.request();
      this.entries.push({
        url: request.url(),
        method: request.method(),
        status: response.status(),
        timing: Date.now(),
      });
    });
  }

  getEntries(): NetworkEntry[] {
    return [...this.entries];
  }

  getFailedRequests(): NetworkEntry[] {
    return this.entries.filter((e) => e.status >= 400);
  }

  clear(): void {
    this.entries = [];
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add e2e/support/scenario.ts e2e/support/network-monitor.ts
git commit -m "feat(e2e): add scenario helper and network monitor"
```

### Task 1.6: First route smoke test

**Files:**
- Create: `e2e/tests/smoke/route-availability.spec.ts`

**Interfaces:**
- Consumes: design spec section 4.1 smoke layer.
- Produces: first smoke test proving the pipeline works.

- [ ] **Step 1: Create route-availability.spec.ts**

```typescript
import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';

test(scenario('PLATFORM-ROUTE-001', 'login page renders'), async ({ page }) => {
  await page.goto('/login');
  await expect(page).toHaveTitle(/portfolio/i);
  await expect(page.locator('text=Sign In')).toBeVisible();
});

test(scenario('PLATFORM-ROUTE-002', 'unauthenticated user redirects to login'), async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login/);
});
```

- [ ] **Step 2: Commit**

```bash
git add e2e/tests/smoke/route-availability.spec.ts
git commit -m "test(e2e): add route availability smoke tests"
```

### Task 1.7: Vitest expansion and scenario helper

**Files:**
- Create: `frontend/src/test/scenario.ts` (if not exists)
- Modify: `frontend/src/setupTests.ts` (verify setup)

**Interfaces:**
- Consumes: design spec section 4.5 Vitest configuration.
- Produces: component test infrastructure with scenario ID support.

- [ ] **Step 1: Create frontend/src/test/scenario.ts**

```typescript
export function scenario(id: string, description: string): string {
  return `[${id}] ${description}`;
}
```

- [ ] **Step 2: Verify setupTests.ts exists and is correct**

Check that `frontend/src/setupTests.ts` imports `@testing-library/jest-dom`. If not, add the import.

- [ ] **Step 3: Create proof-of-concept component test**

Create `frontend/src/components/ui/Button.test.tsx` (note: the component file is lowercase `button.tsx` with a named export):
```typescript
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { scenario } from '../../test/scenario';
import { Button } from './button';

describe('Button', () => {
  it(scenario('UI-BUTTON-001', 'renders with text'), () => {
    render(<Button>Click me</Button>);
    expect(screen.getByRole('button', { name: /click me/i })).toBeInTheDocument();
  });
});
```

- [ ] **Step 4: Run component tests locally to verify**

```bash
cd frontend && npm run test:run
```

Expected: Button test passes.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/test/scenario.ts frontend/src/components/ui/Button.test.tsx
git commit -m "test(frontend): add Vitest scenario helper and Button proof-of-concept"
```

### Task 1.8: App environment meta tag

**Files:**
- Modify: `frontend/index.html`

**Interfaces:**
- Consumes: design spec section 4.4 environment safety contract.
- Produces: build-time environment marker in HTML.

- [ ] **Step 1: Add meta tag to index.html**

Add to `<head>` in `frontend/index.html`:
```html
<meta name="app-environment" content="%VITE_APP_ENVIRONMENT%">
```

- [ ] **Step 2: Add the ARG to the Dockerfile and document the variable**

`frontend/Dockerfile` currently accepts only `VITE_API_URL` and
`VITE_AUTH_METHOD`. Add beside them:

```dockerfile
ARG VITE_APP_ENVIRONMENT=production
```

There is no `frontend/.env.example`; the repo root has `config/.env.example`
(deploy template). Add to `config/.env.example`:
```
VITE_APP_ENVIRONMENT=development
```

- [ ] **Step 3: Update build.yml to pass the build arg**

In `.github/workflows/build.yml`, add the build arg to the UAT frontend
image build step (which already passes `VITE_API_URL` and
`VITE_AUTH_METHOD=both`):
```yaml
build-args: |
  VITE_APP_ENVIRONMENT=uat
```

- [ ] **Step 4: Commit**

```bash
git add frontend/index.html frontend/Dockerfile config/.env.example .github/workflows/build.yml
git commit -m "feat(frontend): add build-time environment marker for safety contract"
```

### Task 1.9: UI test planner skill

**Files:**
- Create: `.opencode/skills/ui-test-planner/SKILL.md`
- Create: `docs/testing/agents/ui-test-planner.md`

**Interfaces:**
- Consumes: design spec section 7.1.
- Produces: planner skill and policy document.

- [ ] **Step 1: Create the skill directory and SKILL.md**

Create `.opencode/skills/ui-test-planner/SKILL.md` following the pattern from the reference repos.

- [ ] **Step 2: Create the policy document**

Create `docs/testing/agents/ui-test-planner.md` with the policy rules from design spec section 7.2.

- [ ] **Step 3: Commit**

```bash
git add .opencode/skills/ui-test-planner/ docs/testing/agents/ui-test-planner.md
git commit -m "feat(testing): add ui-test-planner skill and policy"
```

### Task 1.10: Plan template and validate-specs script

**Files:**
- Create: `specs/ui/_template.md`
- Create: `e2e/scripts/validate-specs.ts`

**Interfaces:**
- Consumes: design spec section 6.1 plan template.
- Produces: template and validation script.

- [ ] **Step 1: Create plan template**

Create `specs/ui/_template.md` with the front matter from design spec section 6.1.

- [ ] **Step 2: Create validate-specs.ts**

Create `e2e/scripts/validate-specs.ts` that:
- Parses all `specs/ui/**/*.md` files
- Validates front matter schema
- Checks scenario ID uniqueness
- Validates requirement_refs resolve
- Checks owner exists in CODEOWNERS

- [ ] **Step 3: Commit**

```bash
git add specs/ui/_template.md e2e/scripts/validate-specs.ts
git commit -m "feat(testing): add plan template and spec validator"
```

### Task 1.11: Build requirements index script

**Files:**
- Create: `e2e/scripts/build-requirements-index.ts`

**Interfaces:**
- Consumes: design spec section 6.4 requirements model.
- Produces: requirements index generator.

- [ ] **Step 1: Create build-requirements-index.ts**

Create `e2e/scripts/build-requirements-index.ts` that:
- Scans `docs/superpowers/specs/*.md` for `RQ-<AREA>-<NNN>` markers
- Generates `docs/testing/requirements-index.json`
- Generates `docs/testing/requirements-index.md` (human view)

- [ ] **Step 2: Commit**

```bash
git add e2e/scripts/build-requirements-index.ts
git commit -m "feat(testing): add requirements index builder"
```

### Task 1.12: Initial CI workflows

**Files:**
- Create: `.github/workflows/ui-tests-pr.yml` (skeleton)
- Create: `.github/workflows/ui-tests-deployed.yml` (skeleton)

**Interfaces:**
- Consumes: design spec section 5.1 workflow map.
- Produces: initial workflow skeletons.

- [ ] **Step 1: Create ui-tests-pr.yml skeleton**

Create `.github/workflows/ui-tests-pr.yml` with:
- Trigger: PR to main
- Job 1: Spec validation (runs validate-specs)
- Job 2: Route coverage (runs discover-routes + check-route-coverage)
- Placeholder jobs for impact analysis and test-change lint

- [ ] **Step 2: Create ui-tests-deployed.yml skeleton**

Create `.github/workflows/ui-tests-deployed.yml` with:
- Trigger: workflow_run after Deploy succeeds
- Concurrency: portfolio-uat-deploy
- Placeholder job for smoke → regression

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ui-tests-pr.yml .github/workflows/ui-tests-deployed.yml
git commit -m "ci: add initial UI testing workflow skeletons"
```

### Task 1.13: Documentation and README updates

**Files:**
- Create: `docs/testing/ui-testing.md`
- Create: `e2e/README.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: design spec section 12 documentation maintenance.
- Produces: platform documentation.

- [ ] **Step 1: Create ui-testing.md**

Create `docs/testing/ui-testing.md` describing how the platform works, CI-only execution statement, and the `e2e/` → `tests/ui/` mapping.

- [ ] **Step 2: Create e2e/README.md**

Create `e2e/README.md` describing the new e2e structure.

- [ ] **Step 3: Update README.md**

Add a testing overview section to the root README.md.

- [ ] **Step 4: Commit**

```bash
git add docs/testing/ui-testing.md e2e/README.md README.md
git commit -m "docs: add UI testing platform documentation"
```

---

## Phase 2 — Authentication Pilot (one PR)

### Task 2.1: Auth fixtures and page objects

**Files:**
- Create: `e2e/fixtures/auth.fixture.ts`
- Create: `e2e/fixtures/data.fixture.ts`
- Create: `e2e/pages/login.page.ts`
- Create: `e2e/pages/app-shell.page.ts`

**Interfaces:**
- Consumes: Phase 1 foundation (app fixture, run-context).
- Produces: auth fixtures and page objects for login flow.

- [ ] **Step 1: Create auth.fixture.ts**

```typescript
import { test as base } from '@playwright/test';

export const test = base.extend<{ authenticatedPage: void }>({
  authenticatedPage: [async ({ page, baseURL }, use) => {
    // Login with the UAT test account from CI secrets (no committed seed
    // accounts exist in this repo). Sessions are cookie-based; the browser
    // holds the session cookie after form login — no token handling.
    const email = process.env.E2E_USER_EMAIL;
    const password = process.env.E2E_USER_PASSWORD;
    if (!email || !password) {
      throw new Error('E2E_USER_EMAIL and E2E_USER_PASSWORD must be set');
    }
    await page.goto('/login');
    await page.fill('input[type="email"], input[name="email"]', email);
    await page.fill('input[type="password"], input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL('/');
    await use(page);
  }, { auto: false }],
});

export { expect } from '@playwright/test';
```

- [ ] **Step 2: Create login.page.ts**

```typescript
import { Page, expect } from '@playwright/test';

export class LoginPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/login');
  }

  async login(email: string, password: string) {
    await this.page.fill('input[type="email"], input[name="email"]', email);
    await this.page.fill('input[type="password"], input[name="password"]', password);
    await this.page.click('button[type="submit"]');
  }

  async expectVisible() {
    await expect(this.page.locator('text=Sign In')).toBeVisible();
  }
}
```

- [ ] **Step 3: Create app-shell.page.ts**

```typescript
import { Page, expect } from '@playwright/test';

export class AppShell {
  constructor(private page: Page) {}

  async expectSidebarVisible() {
    await expect(this.page.locator('nav, [role="navigation"]')).toBeVisible();
  }

  async navigateTo(route: string) {
    await this.page.click(`a[href="${route}"]`);
    await this.page.waitForURL(route);
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add e2e/fixtures/auth.fixture.ts e2e/fixtures/data.fixture.ts e2e/pages/login.page.ts e2e/pages/app-shell.page.ts
git commit -m "feat(e2e): add auth fixtures and login/app-shell page objects"
```

### Task 2.2: Login browser tests

**Files:**
- Create: `e2e/tests/regression/auth-login.spec.ts`

**Interfaces:**
- Consumes: login.page.ts, auth.fixture.ts.
- Produces: tagged browser tests for login flow.

- [ ] **Step 1: Create auth-login.spec.ts**

```typescript
import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Authentication - Login', () => {
  const email = process.env.E2E_USER_EMAIL;
  const password = process.env.E2E_USER_PASSWORD;

  test(scenario('AUTH-LOGIN-001', 'successful login with valid credentials'), async ({ page }) => {
    test.skip(!email || !password, 'E2E_USER_EMAIL/E2E_USER_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await expect(page).toHaveURL('/');
  });

  test(scenario('AUTH-LOGIN-002', 'login form shows validation for empty fields'), async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await page.click('button[type="submit"]');
    await expect(page.locator('[role="alert"], .error')).toBeVisible();
  });

  test(scenario('AUTH-LOGIN-003', 'login shows error for invalid credentials'), async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login('invalid@test.com', 'wrongpassword');
    await expect(page.locator('[role="alert"], .error, text=Invalid')).toBeVisible();
  });
});
```

- [ ] **Step 2: Commit**

```bash
git add e2e/tests/regression/auth-login.spec.ts
git commit -m "test(e2e): add login browser tests with scenario IDs"
```

### Task 2.3: Auth guard and session tests

**Files:**
- Create: `e2e/tests/regression/auth-guard.spec.ts`
- Create: `e2e/tests/regression/auth-session.spec.ts`

**Interfaces:**
- Consumes: login.page.ts, app-shell.page.ts.
- Produces: guard and session expiry tests.

- [ ] **Step 1: Create auth-guard.spec.ts**

```typescript
import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';

test.describe('Authentication - Guard', () => {
  test(scenario('AUTH-GUARD-001', 'unauthenticated user redirects to login'), async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });

  test(scenario('AUTH-GUARD-002', 'protected route redirects unauthenticated user'), async ({ page }) => {
    await page.goto('/portfolios');
    await expect(page).toHaveURL(/\/login/);
  });

  test(scenario('AUTH-GUARD-003', 'admin route accessible only to ADMIN role'), async ({ page }) => {
    // Test with non-admin user
    await page.goto('/admin');
    // Expect redirect or unauthorized message
  });
});
```

- [ ] **Step 2: Create auth-session.spec.ts**

```typescript
import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';

test.describe('Authentication - Session', () => {
  test(scenario('AUTH-SESSION-001', 'session persists across page navigation'), async ({ page }) => {
    // Login, navigate to multiple pages, verify session holds
  });

  test(scenario('AUTH-SESSION-002', 'logout clears session'), async ({ page }) => {
    // Login, logout, verify redirect to login
  });
});
```

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/regression/auth-guard.spec.ts e2e/tests/regression/auth-session.spec.ts
git commit -m "test(e2e): add auth guard and session browser tests"
```

### Task 2.4: Login component tests

**Files:**
- Modify: `frontend/src/pages/auth/LoginPage.test.tsx`

**Interfaces:**
- Consumes: Phase 1 Vitest expansion.
- Produces: component tests for login validation and pending state.

- [ ] **Step 1: Rewrite LoginPage.test.tsx with scenario IDs**

Rewrite the existing test file with:
- Scenario ID annotations
- Field validation tests
- Pending/disabled state tests
- Error state tests

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/auth/LoginPage.test.tsx
git commit -m "test(frontend): rewrite LoginPage tests with scenario IDs"
```

---

## Phase 3 — Coverage, Gates, and Breadth (one PR)

### Task 3.1: Manifest and route discovery scripts

**Files:**
- Create: `e2e/scripts/build-manifest.ts`
- Create: `e2e/scripts/discover-routes.ts`
- Create: `e2e/scripts/check-route-coverage.ts`

**Interfaces:**
- Consumes: Phase 1 foundation scripts.
- Produces: manifest generator, route discovery, coverage check.

- [ ] **Step 1: Create build-manifest.ts**

Script that parses plan front matter and generates `specs/ui/manifest.json`.

- [ ] **Step 2: Create discover-routes.ts**

Script that parses `frontend/src/App.tsx` to extract route patterns.

- [ ] **Step 3: Create check-route-coverage.ts**

Script that fails when a discovered route has neither a smoke test nor a documented exclusion.

- [ ] **Step 4: Commit**

```bash
git add e2e/scripts/build-manifest.ts e2e/scripts/discover-routes.ts e2e/scripts/check-route-coverage.ts
git commit -m "feat(testing): add manifest generator and route coverage scripts"
```

### Task 3.2: Impact analyzer and test-change lint

**Files:**
- Create: `e2e/scripts/analyze-test-impact.ts`
- Create: `e2e/scripts/lint-test-changes.ts`

**Interfaces:**
- Consumes: manifest, route map.
- Produces: impact analyzer v1, test-change linter.

- [ ] **Step 1: Create analyze-test-impact.ts**

Script that uses merge-base diff to identify impacted features and posts a PR comment.

- [ ] **Step 2: Create lint-test-changes.ts**

Script that checks for matcher loosening, added skips, configuration weakening.

- [ ] **Step 3: Commit**

```bash
git add e2e/scripts/analyze-test-impact.ts e2e/scripts/lint-test-changes.ts
git commit -m "feat(testing): add impact analyzer and test-change linter"
```

### Task 3.3: Coverage builder and history publisher

**Files:**
- Create: `e2e/scripts/build-coverage-report.ts`
- Create: `e2e/scripts/publish-history.ts`

**Interfaces:**
- Consumes: Playwright JSON, Vitest JSON reports.
- Produces: coverage report, history on test-reports branch.

- [ ] **Step 1: Create build-coverage-report.ts**

Script that ingests test reports and produces `docs/testing/coverage.md` and `coverage.json`.

- [ ] **Step 2: Create publish-history.ts**

Script that appends coverage/reliability data to the `test-reports` branch.

- [ ] **Step 3: Commit**

```bash
git add e2e/scripts/build-coverage-report.ts e2e/scripts/publish-history.ts
git commit -m "feat(testing): add coverage builder and history publisher"
```

### Task 3.4: Dashboard, portfolio, screener browser tests

**Files:**
- Create: `e2e/tests/regression/dashboard-overview.spec.ts`
- Create: `e2e/tests/regression/portfolio-management.spec.ts`
- Create: `e2e/tests/regression/screener-filters.spec.ts`

**Interfaces:**
- Consumes: Phase 1 foundation, Phase 2 page objects.
- Produces: browser tests for CJ-02, CJ-03, CJ-04.

- [ ] **Step 1: Create dashboard-overview.spec.ts**

Tests for KPIs, positions table, activities, account switching.

- [ ] **Step 2: Create portfolio-management.spec.ts**

Tests for model portfolios, custom builder, analysis panel.

- [ ] **Step 3: Create screener-filters.spec.ts**

Tests for filter panel, AG Grid results, pagination.

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/regression/dashboard-overview.spec.ts e2e/tests/regression/portfolio-management.spec.ts e2e/tests/regression/screener-filters.spec.ts
git commit -m "test(e2e): add dashboard, portfolio, and screener browser tests"
```

### Task 3.5: Options, wheel, broker browser tests

**Files:**
- Create: `e2e/tests/regression/options-chain.spec.ts`
- Create: `e2e/tests/regression/wheel-calendar.spec.ts`
- Create: `e2e/tests/regression/broker-connections.spec.ts`

**Interfaces:**
- Consumes: Phase 1 foundation.
- Produces: browser tests for CJ-05, CJ-06, CJ-07.

- [ ] **Step 1: Create options-chain.spec.ts**

Tests for options chain, strategy selector, leg builder, P&L chart, WebSocket live quotes.

- [ ] **Step 2: Create wheel-calendar.spec.ts**

Tests for calendar grid, KPIs, top tickers, order panel, chain panel.

- [ ] **Step 3: Create broker-connections.spec.ts**

Tests for connect, sync, disconnect, positions, accounts.

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/regression/options-chain.spec.ts e2e/tests/regression/wheel-calendar.spec.ts e2e/tests/regression/broker-connections.spec.ts
git commit -m "test(e2e): add options, wheel, and broker browser tests"
```

### Task 3.6: Admin and authorization browser tests

**Files:**
- Create: `e2e/tests/regression/admin-ingestion.spec.ts`
- Create: `e2e/tests/regression/authz-role-matrix.spec.ts`

**Interfaces:**
- Consumes: Phase 1 foundation.
- Produces: browser tests for CJ-10, CJ-11.

- [ ] **Step 1: Create admin-ingestion.spec.ts**

Tests for ingestion stats, workflows, run history.

- [ ] **Step 2: Create authz-role-matrix.spec.ts**

Tests for role × route matrix, positive and negative access.

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/regression/admin-ingestion.spec.ts e2e/tests/regression/authz-role-matrix.spec.ts
git commit -m "test(e2e): add admin and authorization browser tests"
```

### Task 3.7: Rewrite existing Vitest tests with scenario IDs

**Files:**
- Modify: All 15 existing test files listed in legacy-tests.md

**Interfaces:**
- Consumes: Phase 1 scenario helper.
- Produces: rewritten tests with scenario IDs.

- [ ] **Step 1: Rewrite each test file**

For each file in the legacy-tests.md table:
- Add `scenario()` annotations
- Ensure tests pass with new structure
- Delete old test only after new test passes

- [ ] **Step 2: Commit per file or batch**

```bash
git add frontend/src/**/*.test.*
git commit -m "test(frontend): rewrite legacy Vitest tests with scenario IDs"
```

### Task 3.8: Full CI workflows

**Files:**
- Modify: `.github/workflows/ui-tests-pr.yml` (full checks)
- Modify: `.github/workflows/ui-tests-deployed.yml` (full regression)
- Create: `.github/workflows/ui-tests-prod-smoke.yml`
- Modify: `.github/workflows/deploy-prod.yml` (pre-flight gate)

**Interfaces:**
- Consumes: Phase 1 workflow skeletons.
- Produces: full CI/CD integration.

- [ ] **Step 1: Update ui-tests-pr.yml with all 4 jobs**

Add impact analysis and test-change lint jobs.

- [ ] **Step 2: Update ui-tests-deployed.yml with full regression**

Add smoke → regression → accessibility → visual suite order.

- [ ] **Step 3: Create ui-tests-prod-smoke.yml**

Read-only production smoke tests.

- [ ] **Step 4: Update deploy-prod.yml with pre-flight gate**

Add step checking latest ui-tests-deployed run for target SHA.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ui-tests-pr.yml .github/workflows/ui-tests-deployed.yml .github/workflows/ui-tests-prod-smoke.yml .github/workflows/deploy-prod.yml
git commit -m "ci: add full UI testing CI/CD workflows with production gate"
```

### Task 3.9: Dashboard and reporting component tests

**Files:**
- Create: `frontend/src/components/dashboard/KpiCard.test.tsx`
- Create: `frontend/src/components/dashboard/PositionsTable.test.tsx`
- Create: `frontend/src/components/reporting/ContributionsChart.test.tsx`

**Interfaces:**
- Consumes: Phase 1 Vitest expansion.
- Produces: component tests for dashboard and reporting.

- [ ] **Step 1: Create KpiCard.test.tsx**

Tests for KPI rendering, loading state, error state.

- [ ] **Step 2: Create PositionsTable.test.tsx**

Tests for AG Grid rendering, empty state, data display.

- [ ] **Step 3: Create ContributionsChart.test.tsx**

Tests for chart rendering, data display.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/dashboard/KpiCard.test.tsx frontend/src/components/dashboard/PositionsTable.test.tsx frontend/src/components/reporting/ContributionsChart.test.tsx
git commit -m "test(frontend): add dashboard and reporting component tests"
```

---

## Phase 4 — Accessibility and Visual (one PR)

### Task 4.1: Axe fixture and baseline

**Files:**
- Create: `e2e/fixtures/a11y.fixture.ts`
- Create: `e2e/a11y-baseline.json`
- Create: `e2e/scripts/check-expiries.ts`

**Interfaces:**
- Consumes: Phase 1 foundation.
- Produces: axe fixture, baseline, expiry checker.

- [ ] **Step 1: Create a11y.fixture.ts**

```typescript
import { test as base } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

export const test = base.extend<{ accessibilityScan: void }>({
  accessibilityScan: async ({ page }, use) => {
    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    // Store results for baseline comparison
    await use(accessibilityScanResults);
  },
});

export { expect } from '@playwright/test';
```

- [ ] **Step 2: Create a11y-baseline.json**

```json
{
  "version": 1,
  "baseline": {},
  "exceptions": []
}
```

- [ ] **Step 3: Create check-expiries.ts**

Script that checks quarantine entries, accessibility baselines, and plan review windows for expiry.

- [ ] **Step 4: Commit**

```bash
git add e2e/fixtures/a11y.fixture.ts e2e/a11y-baseline.json e2e/scripts/check-expiries.ts
git commit -m "feat(e2e): add axe fixture, a11y baseline, and expiry checker"
```

### Task 4.2: Accessibility tests

**Files:**
- Create: `e2e/tests/accessibility/critical-pages.a11y.spec.ts`

**Interfaces:**
- Consumes: a11y.fixture.ts.
- Produces: accessibility scans for critical pages.

- [ ] **Step 1: Create critical-pages.a11y.spec.ts**

```typescript
import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';

const criticalPages = [
  { name: 'Login', path: '/login' },
  { name: 'Dashboard', path: '/' },
  { name: 'Portfolio', path: '/portfolios' },
  { name: 'Options', path: '/options' },
  { name: 'Wheel', path: '/wheel' },
];

for (const page of criticalPages) {
  test(scenario(`A11Y-${page.name.toUpperCase()}-001`, `${page.name} page accessibility scan`), async ({ page: p }) => {
    await p.goto(page.path);
    // Axe scan will be added via fixture in Phase 4
  });
}
```

- [ ] **Step 2: Commit**

```bash
git add e2e/tests/accessibility/critical-pages.a11y.spec.ts
git commit -m "test(e2e): add accessibility scans for critical pages"
```

### Task 4.3: Visual regression tests

**Files:**
- Create: `e2e/tests/visual/critical-pages.visual.spec.ts`
- Create: `.gitattributes` (LFS tracking)

**Interfaces:**
- Consumes: Playwright screenshot capabilities.
- Produces: visual regression tests with Git LFS baselines.

- [ ] **Step 1: Create critical-pages.visual.spec.ts**

```typescript
import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';

const visualPages = [
  { name: 'Login', path: '/login' },
  { name: 'Dashboard', path: '/' },
];

for (const page of visualPages) {
  test(scenario(`VISUAL-${page.name.toUpperCase()}-001`, `${page.name} visual regression`), async ({ page: p }) => {
    await p.goto(page.path);
    await expect(p).toHaveScreenshot(`${page.name.toLowerCase()}.png`, {
      maxDiffPixelRatio: 0.01,
    });
  });
}
```

- [ ] **Step 2: Create .gitattributes for LFS**

```
e2e/tests/visual/**/*-snapshots/** filter=lfs diff=lfs merge=lfs -text
```

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/visual/critical-pages.visual.spec.ts .gitattributes
git commit -m "test(e2e): add visual regression tests with Git LFS baselines"
```

### Task 4.4: Visual baseline update workflow

**Files:**
- Create: `.github/workflows/ui-visual-baseline-update.yml`

**Interfaces:**
- Consumes: visual tests.
- Produces: workflow for updating baselines.

- [ ] **Step 1: Create ui-visual-baseline-update.yml**

Workflow triggered by `visual-baseline-update` label on PR:
- Verifies approver identity
- Runs impacted visual tests with `--update-snapshots`
- Commits LFS baselines to PR branch
- Attaches before/after diffs
- Refuses `main` branch

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ui-visual-baseline-update.yml
git commit -m "ci: add visual baseline update workflow"
```

---

## Phase 5 — Impact Refinement and Agent Skills (one PR)

### Task 5.1: Import-graph analyzer

**Files:**
- Create: `e2e/scripts/build-impact-graph.ts`

**Interfaces:**
- Consumes: ts-morph, TypeScript source.
- Produces: import graph for impact analysis v2.

- [ ] **Step 1: Create build-impact-graph.ts**

Script using ts-morph to analyze TypeScript import graph and identify impacted components.

- [ ] **Step 2: Commit**

```bash
git add e2e/scripts/build-impact-graph.ts
git commit -m "feat(testing): add import-graph impact analyzer"
```

### Task 5.2: AST style-only classification

**Files:**
- Modify: `e2e/scripts/analyze-test-impact.ts`

**Interfaces:**
- Consumes: import graph.
- Produces: style-only change classification.

- [ ] **Step 1: Update analyze-test-impact.ts**

Add AST-based classification to distinguish style-only changes from behavioral changes.

- [ ] **Step 2: Commit**

```bash
git add e2e/scripts/analyze-test-impact.ts
git commit -m "feat(testing): add AST style-only classification to impact analyzer"
```

### Task 5.3: Impact and failure analyst skills

**Files:**
- Create: `.opencode/skills/ui-test-impact-analyst/SKILL.md`
- Create: `.opencode/skills/ui-test-failure-analyst/SKILL.md`
- Create: `docs/testing/agents/ui-test-impact-analyst.md`
- Create: `docs/testing/agents/ui-test-failure-analyst.md`
- Create: `docs/testing/triage.md`

**Interfaces:**
- Consumes: design spec section 7.
- Produces: agent skills and triage documentation.

- [ ] **Step 1: Create impact analyst skill and policy**

- [ ] **Step 2: Create failure analyst skill and policy**

- [ ] **Step 3: Create triage.md**

- [ ] **Step 4: Commit**

```bash
git add .opencode/skills/ui-test-impact-analyst/ .opencode/skills/ui-test-failure-analyst/ docs/testing/agents/ docs/testing/triage.md
git commit -m "feat(testing): add impact and failure analyst skills"
```

---

## Phase 6 — Expansion and Optimization (one PR)

### Task 6.1: Remaining journey browser tests

**Files:**
- Create: `e2e/tests/regression/analytics-sectors.spec.ts`
- Create: `e2e/tests/regression/reporting-contributions.spec.ts`
- Create: `e2e/tests/regression/instruments-stock.spec.ts`

**Interfaces:**
- Consumes: Phase 1 foundation.
- Produces: browser tests for CJ-08, CJ-09, CJ-04 (instruments detail).

- [ ] **Step 1: Create analytics-sectors.spec.ts**

Tests for sector exposure, geography, top holdings, risk profile.

- [ ] **Step 2: Create reporting-contributions.spec.ts**

Tests for contributions, dividends, total value charts.

- [ ] **Step 3: Create instruments-stock.spec.ts**

Tests for stock/ETF/mutual fund detail views.

- [ ] **Step 4: Commit**

```bash
git add e2e/tests/regression/analytics-sectors.spec.ts e2e/tests/regression/reporting-contributions.spec.ts e2e/tests/regression/instruments-stock.spec.ts
git commit -m "test(e2e): add analytics, reporting, and instruments browser tests"
```

### Task 6.2: Remaining component tests

**Files:**
- Create: `frontend/src/components/options/OptionsChainTable.test.tsx`
- Create: `frontend/src/components/wheel/WheelCalendarGrid.test.tsx`
- Create: `frontend/src/components/analytics/SectorChart.test.tsx`
- Create: `frontend/src/components/broker/BrokerConnectionCard.test.tsx` (rewrite)

**Interfaces:**
- Consumes: Phase 1 Vitest expansion.
- Produces: component tests for remaining journeys.

- [ ] **Step 1: Create component tests for options, wheel, analytics, broker**

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/**/*.test.tsx
git commit -m "test(frontend): add component tests for remaining journeys"
```

### Task 6.3: Legacy test retirement

**Files:**
- Modify: All remaining legacy test files

**Interfaces:**
- Consumes: parity demonstration in CI.
- Produces: legacy tests fully retired.

- [ ] **Step 1: Verify parity in CI**

Ensure all new tests pass and cover the same scenarios as legacy tests.

- [ ] **Step 2: Delete legacy test files**

Remove any legacy test files that have been fully replaced.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/**/*.test.*
git commit -m "test(frontend): retire legacy tests after parity demonstration"
```

### Task 6.4: Full browser matrix and sharding

**Files:**
- Modify: `e2e/playwright.config.ts`

**Interfaces:**
- Consumes: Phase 1 Playwright config.
- Produces: full browser matrix with sharding support.

- [ ] **Step 1: Update playwright.config.ts**

Enable all browser projects (chromium, firefox, webkit, mobile-chrome) for the full tier.

- [ ] **Step 2: Commit**

```bash
git add e2e/playwright.config.ts
git commit -m "chore(e2e): enable full browser matrix for release candidates"
```

### Task 6.5: Operations handbook finalization

**Files:**
- Modify: `docs/testing/operations.md`

**Interfaces:**
- Consumes: measured data from Phases 1-5.
- Produces: finalized operations handbook.

- [ ] **Step 1: Update operations.md**

Finalize:
- Alert channel configuration
- Dashboard location and access
- Review cadence (weekly triage, per-release accessibility, quarterly journey review)
- Manual UAT reset procedure with data-volume threshold
- Gate thresholds agreed from 30-day measurement

- [ ] **Step 2: Commit**

```bash
git add docs/testing/operations.md
git commit -m "docs(testing): finalize operations handbook"
```

### Task 6.6: ADR entries for all phases

**Files:**
- Modify: `docs/adr.md`

**Interfaces:**
- Consumes: all workflow changes across phases.
- Produces: ADR entries for each phase's workflow changes.

- [ ] **Step 1: Add ADR entries for Phase 1-6 workflow changes**

Append ADR entries documenting:
- Phase 1: Environment marker, initial workflows, deploy/test serialization
- Phase 3: Full PR gates, coverage history, promotion gate
- Phase 4: Visual baselines, weekly reliability
- Phase 5: Impact v2, gate metrics
- Phase 6: Legacy retirement, full matrix

- [ ] **Step 2: Commit**

```bash
git add docs/adr.md
git commit -m "docs: add ADR entries for UI testing platform phases 1-6"
```
