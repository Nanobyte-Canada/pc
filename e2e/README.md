# e2e — Playwright Browser Tests

This package contains the Playwright browser test suite for the Portfolio Construction App. It runs exclusively against the deployed UAT environment — **browser tests do not run locally**.

## What this package contains

- **Playwright browser tests** organized by tag (`@smoke`, `@regression`, `@a11y`, `@visual`)
- **Fixtures** for environment safety (host allowlist, meta tag assertion)
- **Support utilities** for run-context namespacing, negative waits, and network monitoring
- **Page Object Models** for reusable page interactions
- **Platform tooling scripts** for spec validation, manifest generation, coverage reporting, and impact analysis

## Dependencies

```bash
cd e2e
npm install
```

Dependencies include:
- `@playwright/test` 1.60.x — browser automation
- `@axe-core/playwright` — accessibility testing
- `typescript`, `tsx` — TypeScript tooling
- `gray-matter` — plan front matter parsing
- `ajv` — manifest schema validation
- `ts-morph` — TypeScript import graph analysis
- `tinyglobby` — file globbing

Playwright browsers are installed separately:
```bash
npx playwright install --with-deps chromium
```

## How to run tests

**Tests are not designed to run locally.** They target the deployed UAT environment at `https://uatportfolio.nanobyte.ca` and rely on the environment safety contract (host allowlist + `<meta name="app-environment" content="uat">` marker).

In CI, tests run automatically:
- **PR checks** (`ui-tests-pr.yml`): deterministic validations + component tests
- **Post-deploy** (`ui-tests-deployed.yml`): browser smoke, regression, accessibility, and visual suites against UAT

If you need to debug locally against UAT, set the base URL and credentials:
```bash
BASE_URL=https://uatportfolio.nanobyte.ca npx playwright test --grep @smoke
```

Note: this requires valid UAT credentials provided via `E2E_USER_EMAIL` / `E2E_USER_PASSWORD` secrets.

## File structure

```
e2e/
  playwright.config.ts            # Config: UAT target, tag-based projects, reporters
  package.json                    # npm workspace with platform scripts
  tsconfig.json                   # TypeScript config
  fixtures/
    app.fixture.ts                # Core fixture: environment safety (auto-applied)
  support/
    environment.ts                # Host allowlist and page marker assertion
    run-context.ts                # Run-namespaced identities for data isolation
    scenario.ts                   # Scenario ID annotation helper
    negative-wait.ts              # Deterministic waits (no fixed sleeps)
    network-monitor.ts            # Annotate-mode network request monitoring
  pages/
    *.page.ts                     # Page Object Models
  tests/
    smoke/                        # @smoke — critical path verification
    regression/                   # @regression — full feature coverage
    accessibility/                # @a11y — axe-core checks
    visual/                       # @visual — screenshot comparison (Git LFS baselines)
  a11y-baseline.json              # Known accessibility exceptions with owners and expiry
  scripts/
    validate-specs.ts             # Validates plan structure and scenario IDs
    build-manifest.ts             # Generates specs/ui/manifest.json from plans
    build-requirements-index.ts   # Generates docs/testing/requirements-index.json
    discover-routes.ts            # Parses frontend/src/App.tsx for route definitions
    check-route-coverage.ts       # Fails when a route has no smoke test or exclusion
    analyze-test-impact.ts        # Determines impacted features from a diff
    lint-test-changes.ts          # Catches assertion weakening and config changes
    build-coverage-report.ts      # Produces coverage.md from test reports
    publish-history.ts            # Appends to test-reports branch for dashboard
```

## How to add new spec files

1. Create a test plan in `specs/ui/<area>/<feature>.md` using the template at `specs/ui/_template.md`. Assign scenario IDs from the plan's `next_id`.
2. Create a spec file under `e2e/tests/<category>/` (category is for navigation; tags are canonical).
3. Import `test` and `expect` from `../fixtures/app.fixture` (not directly from `@playwright/test`) to get environment safety checks.
4. Tag each test with the scenario ID and a suite tag:
   ```typescript
   test.describe('LOGIN-001 — Valid email/password login', { tag: ['@LOGIN-001', '@smoke'] }, () => {
     // ...
   });
   ```
5. Use accessible locators first (roles, labels, text). Add `data-testid` only where no stable semantic locator exists.
6. Run `npm run validate-specs` to verify the plan and IDs are consistent before pushing.
