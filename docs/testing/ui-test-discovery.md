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
