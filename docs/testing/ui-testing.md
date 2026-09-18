# UI Testing Platform

## Overview

The UI testing platform provides automated browser and component testing for the Portfolio Construction App. It is based on the **Revision 2 model** (`docs/testing/source/UI_TESTING_IMPLEMENTATION_SPEC_v2.md`), adapted for this repository's architecture and constraints.

## Execution model: CI-only

**All test execution happens in CI or against deployed environments. There is no local test execution workflow.** Developer machines run only linting, builds, and the Vitest component suite via `npm run test:run` in the `frontend/` directory — browser tests never run locally. This eliminates environment drift and local credential management.

- **PR checks**: Deterministic validations (spec/manifest validation, route coverage, impact analysis, test-change lint) plus Vitest component tests run on every pull request via `ui-tests-pr.yml`.
- **Post-deploy regression**: Browser tests run automatically against the deployed UAT environment after every successful UAT deploy via `ui-tests-deployed.yml`.

## Test layers

| Layer | Tooling | Execution target | Trigger |
|-------|---------|-----------------|---------|
| Component/interaction | Vitest + React Testing Library + jsdom (`frontend/`) | CI | Every PR |
| Browser smoke | Playwright, `@smoke` tag | Deployed UAT | Post-deploy |
| Browser regression | Playwright, `@regression` tag | Deployed UAT | Post-deploy |
| Accessibility | `@axe-core/playwright`, `@a11y` tag | Deployed UAT | Post-deploy |
| Visual regression | Playwright screenshots, `@visual` tag, Git LFS baselines | Deployed UAT | Post-deploy |
| Cross-browser full tier | Playwright projects (firefox, webkit, emulated mobile) | Deployed UAT | Manual dispatch |

## Repository structure

### `e2e/` — Playwright browser tests

The `e2e/` directory is the browser-test project root. It is an npm workspace with its own `package.json` and lockfile. The structure maps to Revision 2's `tests/ui/` but retains the `e2e/` name to minimize churn.

```
e2e/
  playwright.config.ts          # UAT target, tags, HTML/JUnit/JSON reporters, no webServer
  fixtures/
    app.fixture.ts              # Environment safety: host allowlist + meta tag assertion
  support/
    environment.ts              # Host allowlist and page marker checks
    run-context.ts              # Run-namespaced identities (pw-<runid>-user@portfolio.test)
    scenario.ts                 # Scenario ID annotation helper
    negative-wait.ts            # Deterministic negative waits (no fixed sleeps)
    network-monitor.ts          # Annotate-mode network monitoring
  pages/                        # Page Object Models
    *.page.ts
  tests/
    smoke/                      # Navigation folder only; @smoke tag is canonical
    regression/                 # @regression tag
    accessibility/              # @a11y tag
    visual/                     # @visual tag; snapshots stored in Git LFS
  a11y-baseline.json            # Known accessibility exceptions with owners and expiry
  scripts/                      # Platform tooling (validate-specs, build-manifest, etc.)
```

### `frontend/src/**/*.test.tsx` — Vitest component tests

Component and interaction tests live colocated with the source they test, using Vitest + React Testing Library + jsdom. These run in CI on every PR via `build.yml`.

## Test organization

Tests are organized by **tags**, not by folder. Folder structure is for navigation only; the Playwright tag is the canonical selector for suite execution.

| Tag | Purpose | Suite |
|-----|---------|-------|
| `@smoke` | Critical-path verification; must pass before any deploy proceeds | First tier |
| `@regression` | Full feature coverage including negative, boundary, and edge cases | Second tier |
| `@a11y` | Accessibility checks via axe-core | Post-deploy |
| `@visual` | Screenshot comparison against Git LFS baselines | Post-deploy |

## Scenario ID convention

Every test scenario has a stable ID in the format `<FEATURE>-<NNN>`, for example `LOGIN-001`, `DASH-003`, `WHEEL-012`.

- IDs are allocated from the owning plan's `next_id` counter, which only increases.
- IDs are **never reused**. A material intent change retires the old scenario and allocates a new one.
- Retired IDs are recorded in the plan's retired table with date and reason.
- The validator fails on: an ID in a test but not in any plan, a reused ID, or an active test referencing a retired ID.

Plans live in `specs/ui/<area>/<feature>.md` and define the scenario list, requirement refs, and metadata. The plan template is at `specs/ui/_template.md`.

## Traceability

```
docs/superpowers/specs/*.md            requirements (RQ-<AREA>-<NNN> markers)
        ↓ generated
docs/testing/requirements-index.json   id → spec path, anchor, title
        ↓ referenced by
specs/ui/<area>/<feature>.md           requirement_refs, scenarios <FEATURE>-<NNN>
        ↓ generated
specs/ui/manifest.json                 features → scenarios → layer/target → tests
        ↓ annotated by
e2e tests with @<SCENARIO-ID> tags     Playwright browser tests
frontend/**/*.test.tsx                 Vitest component tests
        ↓ joined by
docs/testing/coverage.md               RQ → feature → scenario → test → pass/fail
```

## How to add new tests

1. **Create or update a plan** in `specs/ui/<area>/<feature>.md` using the template at `specs/ui/_template.md`. Define scenarios with IDs from `next_id`.
2. **Write browser tests** under `e2e/tests/<category>/` using Playwright. Tag scenarios with `@<FEATURE>-<NNN>` and a suite tag (`@smoke`, `@regression`, etc.). Use the `test` export from `e2e/fixtures/app.fixture.ts` to get environment safety checks.
3. **Write component tests** colocated under `frontend/src/` using Vitest + React Testing Library.
4. **Run validation** to ensure specs, manifest, and requirements index are consistent (CI does this automatically on PRs).

For full details, see:
- [Design spec](../superpowers/specs/2026-09-17-ui-testing-platform-design.md) — architecture, phases, and explicit adaptations
- [Operations handbook](operations.md) — approvers, alerts, cadence, manual UAT reset
- [Critical journeys](critical-journeys.md) — the 11 journeys that form the coverage denominator

## CI workflows

| Workflow | File | Purpose |
|----------|------|---------|
| Build & Push Images | `build.yml` | Backend + frontend tests, image builds. Vitest runs here. |
| UI Tests (PR) | `ui-tests-pr.yml` | Deterministic PR checks: spec validation, route coverage, impact analysis, lint |
| UI Tests (Deployed) | `ui-tests-deployed.yml` | Post-deploy browser regression against UAT |
| Deploy | `deploy.yml` | UAT deployment (no e2e job — handled by `ui-tests-deployed.yml`) |
