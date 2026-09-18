# UI Testing Platform — Implementation Specification for an Existing Repository

## 0. Instructions to the implementing AI agent

You are implementing a maintainable UI testing platform in an existing application repository. Treat this document as the target specification, but adapt paths and commands to the repository after completing the discovery phase.

Before editing:

1. Read the repository's `README`, contribution instructions, `AGENTS.md` files, package manifests, lockfiles, existing test configuration, CI workflows, application routing, authentication implementation, and environment configuration.
2. Inspect the current working tree. Preserve unrelated and user-authored changes.
3. Identify the frontend root, package manager, Node version, build tool, CI provider, deployment model, preview/staging URL mechanism, and existing test frameworks.
4. Reuse compatible existing tooling. Do not replace an established runner, package manager, formatter, reporter, or CI convention without a documented incompatibility.
5. Produce a short discovery report before material implementation. Record assumptions and unresolved blockers.
6. If authentication, test-data creation, email capture, or the deployed test URL cannot be controlled safely, stop and ask for the missing information. Do not point destructive tests at production.

Implementation constraints:

- Default target: React with TypeScript, Playwright Test, and GitHub Actions.
- If the repository differs, preserve the objectives and adapt the mechanics.
- Pin dependencies through the existing lockfile. Do not invent or hard-code a package version in documentation or scripts.
- Never commit real credentials, session files, access tokens, customer data, or test mailbox secrets.
- Never weaken a product assertion merely to make a failing test pass.
- Never update visual baselines automatically in CI.
- Never run state-changing tests against production unless the repository has an explicitly approved, isolated production-test tenant.
- Keep every test independently repeatable and safe to retry.
- Prefer user-facing accessible locators. Use `data-testid` only when no stable semantic locator exists.
- Do not use fixed sleeps. Wait for observable UI, URL, response, or state conditions.
- Make small, reviewable commits if the execution environment permits commits. Otherwise provide a clear change summary.

---

## 1. Purpose

Build a UI testing platform that:

1. Converts feature requirements into structured, reviewable test plans.
2. Executes multiple UI tests for every important feature and state.
3. detects when UI changes likely require test-plan or test-suite updates.
4. Supports AI-assisted test planning, generation, and repair under human-review guardrails.
5. Runs automatically in the existing CI/CD process.
6. Reports functional, visual, accessibility, route, and requirement coverage.
7. Produces sufficient artifacts to diagnose a failure without rerunning it locally.

This is a UI testing initiative. Backend unit testing, load testing, penetration testing, and full API contract-test redesign are out of scope except where small API helpers or mocks are required to make UI tests deterministic.

---

## 2. Success criteria

The implementation is successful when all of the following are true:

- A developer can run a documented UI smoke suite locally with one command.
- A developer can run the full UI suite locally with one command.
- The CI pipeline automatically runs the appropriate UI tests for pull requests and deployments.
- A failed CI test uploads an HTML report, trace, screenshot, and machine-readable result.
- Each critical user story has a stable requirement ID mapped to at least one executable test.
- Each application route is either covered by a route smoke test or explicitly excluded with a reason.
- Authentication tests cover the agreed login, signup, email, recovery, logout, and session behaviours.
- Tests cover supported desktop and mobile viewports.
- Automated accessibility checks run on agreed critical pages and interactive states.
- The repository detects likely UI changes that contain neither a test-plan update nor a test update, and reports them clearly.
- AI-generated changes appear as ordinary diffs and require the same review as application code.
- The complete PR suite meets the agreed execution-time budget and does not rely on arbitrary sleep calls.

Initial target service levels, configurable after baseline measurement:

| Metric | Initial target |
|---|---:|
| Critical requirement coverage | 100% |
| Known application route disposition | 100% covered or explicitly excluded |
| PR smoke-suite reliability | At least 99% pass rate across unchanged-code reruns |
| PR smoke-suite duration | 10 minutes or less |
| Full regression duration | 30 minutes or less with sharding when needed |
| Critical accessibility scans | No unapproved serious or critical violations |
| Failure diagnostics | Trace and screenshot available for every failed browser test |

Do not impose arbitrary frontend line-coverage thresholds until the repository has a measured baseline. Requirement and state coverage are the primary UI quality measures.

---

## 3. Scope

### 3.1 In scope

- Page and route smoke tests
- End-to-end user journeys
- Form validation and boundary cases
- Loading, empty, error, disabled, and success states
- Navigation, redirects, browser back/forward, and refresh behaviour
- Authentication and authorization-visible UI behaviour
- Responsive desktop and mobile layouts
- Cross-browser execution for the supported browser matrix
- Automated accessibility scans
- High-value visual regression tests
- Browser console and failed-network-request monitoring
- Test data, session, and email-test fixtures
- Test-plan-to-test traceability
- UI change-impact detection
- CI execution, gating, artifacts, and reports
- AI-assisted planning, generation, and constrained repair

### 3.2 Out of scope

- Exhaustive pixel snapshots of every page and component
- Replacing backend unit/integration tests
- Performance/load testing beyond optional basic page timing diagnostics
- Security penetration testing
- Testing third-party identity-provider internals
- Testing third-party email delivery infrastructure beyond confirming that the application requested/sent the expected message into an approved test inbox
- Fully autonomous modification of requirements or expected business outcomes
- Stateful destructive production tests

---

## 4. Required discovery output

Create `docs/testing/ui-test-discovery.md` with the following completed table before broad implementation:

| Item | Discovered value | Evidence/path | Decision or impact |
|---|---|---|---|
| Frontend framework/version | | | |
| Frontend root | | | |
| Package manager/lockfile | | | |
| Node/runtime version | | | |
| Build tool | | | |
| Existing component-test runner | | | |
| Existing browser/E2E runner | | | |
| Existing accessibility tooling | | | |
| Router and route definitions | | | |
| Authentication provider | | | |
| CI provider and workflows | | | |
| PR preview deployment mechanism | | | |
| Staging base URL mechanism | | | |
| Test-data reset/seed mechanism | | | |
| Test email mechanism | | | |
| Supported browsers | | | |
| Supported viewports | | | |
| Existing reporting destination | | | |
| Secret-management mechanism | | | |
| UI source path patterns | | | |
| Existing code-owner/reviewer rules | | | |

Also inventory:

- All user-accessible routes and required roles
- Critical business workflows
- Forms and their validation sources
- Reusable interactive components such as dialogs, menus, grids, date pickers, file uploaders, and notifications
- External integrations that must be mocked or sandboxed
- Existing test IDs and accessibility labels

If the repository has no formal list of supported browsers or viewports, propose one and mark it for owner approval rather than assuming it silently.

---

## 5. Target architecture

### 5.1 Testing layers

Implement complementary layers instead of expressing every condition through slow end-to-end tests.

| Layer | Tooling | Main responsibility | Typical trigger |
|---|---|---|---|
| Component/interaction | Existing runner, otherwise Vitest + React Testing Library | Component states, field rules, conditional rendering, callbacks | Every PR |
| Browser smoke | Playwright Test | Critical paths and route availability | Every PR |
| Browser regression | Playwright Test | Complete workflows, failures, permissions, navigation | Staging/main and scheduled |
| Accessibility | `@axe-core/playwright` and existing component tools | Automatically detectable accessibility violations | Every PR for changed critical UI; full scheduled run |
| Visual regression | Playwright screenshots or existing approved service | High-value stable visual states | Staging/main and scheduled |
| Email verification | Approved test inbox API or local mail catcher | Signup, verification, and password-recovery messages | Non-production browser suite |

### 5.2 Source-of-truth hierarchy

The order of authority is:

1. Approved product requirement and acceptance criteria
2. Structured feature test plan under `specs/ui/`
3. Executable automated tests
4. Current UI implementation

When they disagree, do not automatically make the higher-level artifact match the lower-level artifact. Report the inconsistency.

### 5.3 Proposed repository structure

Adapt this structure to existing conventions:

```text
docs/
  testing/
    ui-testing.md
    ui-test-discovery.md
    coverage.md
specs/
  ui/
    _template.md
    manifest.json
    authentication/
      login.md
      signup.md
      password-recovery.md
tests/
  ui/
    fixtures/
      app.fixture.ts
      auth.fixture.ts
      data.fixture.ts
      email.fixture.ts
      accessibility.fixture.ts
    pages/
      login.page.ts
      signup.page.ts
    smoke/
    regression/
    accessibility/
    visual/
    support/
      environment.ts
      network-monitor.ts
      requirement-annotation.ts
    auth.setup.ts
playwright.config.ts
scripts/
  ui-tests/
    validate-specs.ts
    discover-routes.ts
    check-route-coverage.ts
    analyze-test-impact.ts
    build-coverage-report.ts
.github/
  workflows/
    ui-tests-pr.yml
    ui-tests-deployed.yml
```

If tests already live beside features or in another test package, retain that convention and document the mapping.

---

## 6. Structured feature test plans

### 6.1 Test-plan template

Create `specs/ui/_template.md` containing:

```markdown
---
feature_id: FEATURE-ID
feature: Human-readable feature name
owner: Team or owner
status: draft | approved | retired
priority: critical | high | normal
routes:
  - /example
roles:
  - anonymous
components:
  - ExampleForm
tags:
  - smoke
  - accessibility
last_reviewed: YYYY-MM-DD
---

# Feature name

## Objective

## Preconditions

## Test data

## Assumptions and dependencies

## Scenarios

### FEATURE-ID-001 — Scenario name

Priority: critical
Type: happy-path | negative | boundary | navigation | accessibility | visual
Automation: automated | manual | not-applicable

Given ...
When ...
Then ...

Steps:
1. ...

Expected results:
- ...

## Exclusions

## Requirement-to-test mapping

| Scenario ID | Automated test file/title | Status |
|---|---|---|
```

### 6.2 Required scenario categories

For each relevant feature, the plan author or planning agent must consider:

- Primary successful journey
- Alternate successful journeys
- Required fields
- Invalid formats
- Minimum and maximum lengths/values
- Whitespace and normalization
- Duplicate submission
- Server-side validation
- Unauthorized and forbidden behaviour
- Loading and disabled state
- Empty state
- Recoverable server/network failure
- Navigation and redirects
- Refresh and browser-history behaviour
- Keyboard navigation and focus
- Screen-reader name/role/value
- Responsive layout
- Supported roles
- Session expiration
- Localization/time-zone behaviour where applicable
- Analytics events only if they are an approved product requirement

Do not generate irrelevant combinatorial cases. Prioritize business risk and ensure each test has a distinct reason to exist.

### 6.3 Machine-readable manifest

Create `specs/ui/manifest.json` or generate it deterministically from plan front matter. It must record, at minimum:

```json
{
  "schemaVersion": 1,
  "features": [
    {
      "id": "AUTH-LOGIN",
      "plan": "specs/ui/authentication/login.md",
      "priority": "critical",
      "routes": ["/login"],
      "sourcePatterns": ["src/features/auth/**", "src/routes/login/**"],
      "testPatterns": ["tests/ui/**/login*.spec.ts"]
    }
  ]
}
```

Validate this file in CI. IDs must be unique, referenced files must exist, and every critical feature must have at least one matching executable test.

---

## 7. Playwright implementation requirements

### 7.1 Configuration

Configure Playwright to support:

- A base URL from `PLAYWRIGHT_BASE_URL` or an equivalent repository convention
- Local application startup through `webServer` when appropriate
- CI retries for suspected infrastructure flakiness; zero retries locally by default
- Trace capture on first retry or failure
- Screenshot capture on failure
- Video retention on failure when storage policy permits
- HTML and JUnit reporters in CI
- Output paths that are easy to upload as CI artifacts
- Named projects for browser and viewport profiles
- Environment validation before tests begin
- Conservative CI worker counts initially, followed by sharding when stable

Do not hide failures with excessive retries. A test that passes only after retry must appear as flaky in reporting.

### 7.2 Browser projects

Use a two-tier matrix:

- PR tier: primary supported desktop browser and one agreed mobile viewport for critical smoke tests.
- Full tier: all supported browser engines and viewports.

Derive the exact matrix from product support requirements. Do not assume that every browser must run on every PR.

### 7.3 Locator policy

Use this preference order:

1. `getByRole` with accessible name
2. `getByLabel`
3. `getByPlaceholder` or `getByText` when semantically appropriate
4. Stable application-specific locator contract such as `data-testid`
5. CSS locator only for non-semantic structure that cannot reasonably be exposed otherwise

Prohibited without written justification:

- Positional selectors such as `nth-child`
- Styling-class selectors
- Long DOM-path selectors
- Exact selectors generated from transient framework markup

### 7.4 Assertion policy

Assert user-visible outcomes, not implementation details. Examples:

- URL or route changed to the intended destination
- Heading or destination landmark is visible
- Validation message is associated with the field
- Submit button has the correct enabled/disabled state
- Success notification is visible
- Expected record appears in the UI
- Approved email is captured with expected recipient, subject, and link purpose
- Unauthorized controls are absent or disabled as required

Do not make a navigation test pass by asserting only that a click occurred.

### 7.5 Isolation and repeatability

- Each test must create or request its own data identity.
- Use API/setup helpers to establish preconditions unless the setup UI itself is under test.
- Reset or uniquely namespace mutable data.
- Avoid ordering dependencies between tests.
- Avoid shared accounts when tests can change password, role, preferences, or session state.
- Persist authenticated browser state only when it is read-only and safe to share within the test worker.
- Clean up test-created data through an approved, scoped mechanism.

### 7.6 Network and console monitoring

Create reusable monitoring that can fail or annotate tests for:

- Uncaught page errors
- Unexpected console errors
- Unexpected HTTP 5xx responses
- Failed requests to application-owned endpoints

Provide an explicit allowlist for known benign third-party failures. Do not globally ignore console errors or failed requests.

### 7.7 Page objects and fixtures

Use page objects for stable interactions and element access, not for hiding assertions about business outcomes. Use fixtures for:

- Environment validation
- Test users and roles
- Authenticated storage state
- Data creation/reset
- Email inbox creation and polling
- Accessibility scanner configuration
- Network/console monitoring

Avoid one oversized base page or fixture that every test must inherit.

---

## 8. Authentication reference test plan

The agent must confirm actual requirements before implementing these behaviours. Use the following as the initial scenario inventory.

### 8.1 Login

| ID | Scenario | Expected outcome | Priority |
|---|---|---|---|
| AUTH-LOGIN-001 | Valid credentials | User reaches approved post-login route and authenticated landmark is visible | Critical |
| AUTH-LOGIN-002 | Invalid password | Approved generic authentication error; user remains logged out | Critical |
| AUTH-LOGIN-003 | Unknown account | Approved error without unintended account enumeration | High |
| AUTH-LOGIN-004 | Empty fields | Client validation; no login request | High |
| AUTH-LOGIN-005 | Invalid email format | Format validation according to product requirement | Normal |
| AUTH-LOGIN-006 | Submit with Enter | Same result as clicking Login | High |
| AUTH-LOGIN-007 | Login button pending state | Duplicate submissions prevented | High |
| AUTH-LOGIN-008 | Authentication service failure | Recoverable generic service message | High |
| AUTH-LOGIN-009 | Sign-up link | Correct sign-up route and heading | Critical |
| AUTH-LOGIN-010 | Forgot-password link | Correct recovery route and heading | Critical |
| AUTH-LOGIN-011 | Authenticated user visits login | Approved redirect behaviour | Normal |
| AUTH-LOGIN-012 | Keyboard-only interaction | Logical focus order, visible focus, successful submission | High |
| AUTH-LOGIN-013 | Password field | Masked value and approved reveal-control behaviour | High |
| AUTH-LOGIN-014 | Mobile viewport | No clipped fields/actions; form remains operable | High |

### 8.2 Signup

Assumed current fields: Name, Email, and Password. The discovery report must confirm labels, validation rules, consent requirements, and whether password confirmation is present.

| ID | Scenario | Expected outcome | Priority |
|---|---|---|---|
| AUTH-SIGNUP-001 | Valid new user | Account is created and approved next step occurs | Critical |
| AUTH-SIGNUP-002 | Verification/welcome email | Correct message arrives in isolated test inbox | Critical |
| AUTH-SIGNUP-003 | Existing email | Approved existing-account/recovery guidance is displayed | Critical |
| AUTH-SIGNUP-004 | Missing required field | Field-specific validation and no submission | High |
| AUTH-SIGNUP-005 | Invalid email | Approved format validation | High |
| AUTH-SIGNUP-006 | Password policy boundary | Each documented rule is enforced and communicated | High |
| AUTH-SIGNUP-007 | Leading/trailing whitespace | Name/email normalization matches requirements | Normal |
| AUTH-SIGNUP-008 | Duplicate click or slow response | Only one account-creation attempt is accepted | High |
| AUTH-SIGNUP-009 | Backend validation error | Field or form error is displayed correctly | High |
| AUTH-SIGNUP-010 | Service unavailable | Recoverable error; form data handling follows requirement | High |
| AUTH-SIGNUP-011 | Login link | Returns to login route | Normal |
| AUTH-SIGNUP-012 | Keyboard and accessibility | Labels, focus, errors, and announcement behaviour are valid | High |
| AUTH-SIGNUP-013 | Mobile viewport | Full signup flow remains operable | High |

### 8.3 Password recovery, session, and logout

Cover:

- Request recovery for registered and unregistered addresses according to anti-enumeration requirements
- Capture recovery email and validate link purpose without leaking the token
- Expired, reused, and invalid reset links
- Password policy during reset
- Successful reset invalidates old credentials/sessions as required
- Logout removes the session and protects authenticated routes
- Session expiry redirects or prompts as required without silent data loss
- Browser refresh retains or rejects authentication according to requirements

### 8.4 Email-test requirements

- Use a local mail catcher for containerized/local environments or an approved test-mailbox service for deployed environments.
- Allocate a unique inbox or alias per test.
- Poll with a bounded timeout; do not use a fixed sleep.
- Assert recipient, message type, subject pattern, and the intended link host/path.
- Do not write verification or reset tokens to normal CI logs.
- Do not send automated test mail to real users.

---

## 9. Component and interaction tests

If the repository already uses a component-test framework, extend it. Otherwise add Vitest and React Testing Library only if compatible with the build system.

Prioritize component tests for:

- Field-level validation permutations
- Conditional fields and progressive disclosure
- Loading, disabled, error, empty, and success states
- Dialog open/close/focus behaviour
- Complex tables/grids, filters, sorting, and pagination
- Reusable date, currency, percentage, and select inputs
- Permission-controlled controls
- Error boundaries

Do not duplicate the entire component test matrix in browser E2E tests. Browser tests should prove that the integrated workflow and a representative set of validations work.

---

## 10. Accessibility testing

Integrate `@axe-core/playwright` or the repository's existing equivalent.

Requirements:

- Scan critical pages after they reach their stable initial state.
- Scan interactive states that are absent initially, including open menus, dialogs, validation errors, and expanded sections.
- Use the product's approved WCAG target.
- Store known exceptions in a narrow, reviewable baseline with issue references, owners, and expiry/review dates.
- Do not disable broad rules or exclude large page sections merely to get a green build.
- Treat serious and critical new violations as blocking after the initial baseline is accepted.
- Document that automation cannot detect all accessibility problems and retain a manual checklist for keyboard, focus, screen-reader announcements, zoom, and reflow.

---

## 11. Visual regression testing

Use visual assertions selectively for stable, high-value states:

- Login and signup pages
- Main application shell/navigation
- Critical dashboards or forms
- Reusable complex components
- Empty, error, and permission states that are visually significant

Controls for deterministic snapshots:

- Run in a pinned Playwright container or equivalent stable environment.
- Freeze or inject clock-dependent values.
- Use deterministic data.
- Disable animation and transition effects.
- Mask approved dynamic regions.
- Stabilize fonts and viewport sizes.
- Keep separate baselines by browser/platform only where required.
- Require human review for baseline updates.

Do not use a full-page screenshot as the only assertion for functional behaviour.

---

## 12. Coverage model and reports

### 12.1 Required coverage dimensions

Generate a report containing:

| Dimension | Definition |
|---|---|
| Requirement coverage | Approved scenario IDs mapped to executable tests |
| Critical requirement coverage | Critical scenario IDs mapped to passing tests |
| Route coverage | Known routes exercised, excluded, or missing |
| Role coverage | Required role-route/workflow combinations exercised |
| Browser/viewport coverage | Tests executed by supported project profile |
| Accessibility coverage | Pages and interactive states scanned |
| Visual coverage | Approved visual states with baselines |
| Frontend code coverage | Statements/branches/functions/lines where technically reliable |

### 12.2 Requirement annotations

Each Playwright test must declare scenario IDs through the test title, tags, or annotations. Preferred example:

```ts
test('AUTH-LOGIN-001 valid credentials redirect to dashboard', async ({ page }) => {
  // test body
});
```

The coverage builder must:

- Parse approved scenario IDs from the plan/manifest.
- Parse scenario IDs from discovered tests/results.
- Reject duplicate or unknown IDs unless explicitly supported.
- List missing, passing, failing, skipped, and manual scenarios.
- Fail CI when a critical approved automated scenario is missing.
- Publish Markdown/JSON output and, if useful, include a CI step summary.

### 12.3 Route inventory

Implement route discovery using the least fragile available source:

1. Exported route configuration, if available
2. Framework route/file conventions
3. A maintained route manifest

Dynamic routes must use safe fixture values. System, callback, debug, and unsupported routes may be excluded only with a documented reason.

### 12.4 Frontend code coverage

If practical, instrument the development/test build and collect browser coverage. Merge it with component-test coverage only when source maps and instrumentation are compatible. Treat browser code coverage as diagnostic initially because route chunks, generated code, and browser-specific behaviour can distort the result.

---

## 13. UI change-impact detection

### 13.1 Deterministic PR check

Implement `scripts/ui-tests/analyze-test-impact.ts` or an equivalent script. It must:

1. Determine the merge-base and changed files.
2. Match changed paths against each feature's `sourcePatterns`.
3. Identify directly changed plans and tests.
4. Produce a report of impacted features.
5. Fail or warn according to this policy:

| Condition | Default result |
|---|---|
| Critical UI feature changed; neither plan nor test changed | Fail with actionable message |
| Normal UI feature changed; neither plan nor test changed | Warning during rollout, configurable to fail later |
| Plan changed; mapped test absent | Fail for approved automated critical scenario |
| Test changed; requirement ID unknown | Fail |
| Only styles changed | Require visual-impact disposition; do not force functional-test rewrite |
| Non-UI files changed | No UI test-maintenance failure |

Allow an explicit PR disposition such as `ui-test-impact: none` only when accompanied by a reason. Make the mechanism compatible with repository governance; do not create an easy silent bypass.

### 13.2 Change classification

Classify changes into:

- Behaviour/requirement
- Form schema or validation
- Navigation/routing
- Accessibility semantics
- Styling/layout
- Test-only
- Infrastructure/configuration
- No UI impact

The classification drives suggested tests; it does not rewrite tests by itself.

---

## 14. AI-assisted test maintenance

### 14.1 Agent roles

Define repository prompts or agent instructions for:

1. **UI Test Planner** — reads requirements, explores an approved test environment, and creates/updates structured plans.
2. **UI Test Generator** — turns approved scenarios into Playwright/component tests and verifies locators against the live test UI.
3. **UI Test Impact Analyst** — compares the code diff, plans, routes, schemas, and mapped tests, then proposes required changes.
4. **UI Test Failure Analyst** — uses traces, screenshots, console output, and diffs to classify product defect, test defect, data defect, or environment defect.
5. **Constrained Test Healer** — may propose locator, waiting, or fixture repairs; it may not alter expected business behaviour without explicit approval.

Where compatible, initialize Playwright's official planner/generator/healer agent definitions for the approved coding-agent environment. Keep locally customized policy in a separate repository-owned instruction file so regeneration does not overwrite it.

### 14.2 Required AI input

An AI update request must include:

- Requirement/issue text and acceptance criteria
- Relevant feature plan
- Code diff or changed-file list
- Existing mapped tests
- Approved environment and seed fixture
- Locator and assertion policies
- Explicit boundaries on files it may change

### 14.3 AI output contract

The agent must output:

- Change classification
- Impacted scenario IDs
- Added, changed, retired, and unchanged scenarios
- Code changes made
- Tests executed and results
- Remaining assumptions or manual checks
- Explicit notice of any expected-outcome change

### 14.4 Healing policy

The AI may automatically propose, but not silently merge:

- Replacement of a locator with an equivalent accessible locator
- Removal of fixed sleep in favour of an observable wait
- Update to a fixture or deterministic test-data helper
- Correction of a test setup path

The AI must require explicit review for:

- Expected text or outcome changes
- Removed assertions or scenarios
- Authorization or role changes
- Error-handling changes
- Visual baseline changes
- Accessibility-rule suppressions
- Increased retries or timeouts
- Test skips/quarantines

When the agent cannot distinguish an application regression from an outdated test, it must preserve the failing test and report the ambiguity.

---

## 15. CI/CD integration

Adapt job names to the existing pipeline. Preserve existing deployment logic.

### 15.1 Pull-request checks

Run in this order, parallelizing compatible jobs:

1. Validate feature plans and manifest.
2. Run UI change-impact analysis.
3. Run component/interaction tests.
4. Build the application using test-safe configuration.
5. Run primary-browser smoke tests against either a locally started app or the PR preview URL.
6. Run accessibility checks for critical/changed UI.
7. Publish JUnit, HTML, trace, screenshot, and coverage artifacts.

Configure the critical smoke result as a required status check after the suite is stable.

### 15.2 Deployed-environment regression

After successful preview or staging deployment:

- Obtain the deployment URL from the CI event/output; do not hard-code it.
- Validate that the target identifies itself as an approved test environment.
- Seed isolated test data.
- Run the full browser regression suite.
- Run visual tests in the stable execution environment.
- Upload reports even when tests fail.
- Clean up only resources created by the run.
- Block promotion when an approved critical test fails.

### 15.3 Scheduled suite

Run nightly or on an agreed schedule:

- Full supported browser matrix
- Desktop and mobile projects
- Full accessibility suite
- Visual regression suite
- Quarantined-test observation
- Flaky-test trend output

### 15.4 Production smoke tests

Production tests must be read-only unless an approved isolated tenant exists. Limit them to:

- Application availability
- Static/public navigation
- Approved synthetic login, if permitted
- Critical read-only page rendering

Never exercise real signup, password reset, email delivery, data mutation, payment, or external side effects in production by default.

### 15.5 Artifacts and retention

Publish:

- Playwright HTML report
- JUnit XML
- Coverage JSON/Markdown
- Trace and screenshot for failures
- Video for failures where enabled
- Visual diff artifacts
- Test-impact report

Do not upload authenticated storage-state files, secrets, raw email tokens, or customer data. Use the organization's retention policy; if absent, propose a short default such as 14–30 days for ordinary test artifacts.

---

## 16. Test data and environment requirements

### 16.1 Environment safety contract

Before state-changing tests run, verify an environment marker such as:

- Expected hostname suffix, and
- Explicit server-provided environment value, and
- Presence of approved test tenant/account identifiers

Abort if the target appears to be production or cannot be identified.

### 16.2 Seed/reset interface

Prefer a scoped test-support API or fixture mechanism capable of:

- Creating unique users by role/state
- Marking email verified/unverified
- Creating minimal feature-specific data
- Expiring sessions or tokens when required
- Deleting or expiring resources created by the current run

Protect it with test-environment authentication and ensure it is unavailable in production. If adding a backend test-support endpoint is prohibited, use approved database fixtures or existing test factories without embedding database logic directly in UI tests.

### 16.3 Secrets

Use CI environment secrets for:

- Test-user bootstrap credentials
- Test-support API authentication
- Test mailbox access
- Preview/staging authentication

Mask secrets and tokens in logs. Ensure traces and screenshots cannot capture sensitive production information.

---

## 17. Flakiness and quarantine policy

A flaky test is a defect in the test system and must remain visible.

- Track retry-pass tests separately from first-attempt passes.
- Do not add arbitrary timeouts as the first response.
- Classify the cause: selector, synchronization, data collision, environment, service dependency, or product race.
- Quarantine only when the test blocks delivery and an issue, owner, reason, and expiry date are recorded.
- Quarantined critical requirements must remain visible in coverage as not actively gating.
- Fail the scheduled job if quarantine has expired or the count exceeds the approved limit.

---

## 18. Implementation phases

### Phase 0 — Discovery and design confirmation

Deliverables:

- Completed discovery report
- Route and critical-workflow inventory
- Confirmed environment, test-data, email, browser, and viewport decisions
- Compatibility decision for existing test tooling
- Implementation gap list

Exit criteria:

- No unresolved safety question about the target environment or test data
- Owners have confirmed critical authentication behaviour

### Phase 1 — Playwright foundation

Deliverables:

- Playwright dependency/configuration integrated with existing package management
- Local commands for smoke/full/debug execution
- Base fixtures and environment validation
- HTML/JUnit reporting and failure artifacts
- One stable route smoke test
- Documentation for local execution

Exit criteria:

- Smoke command passes locally and in CI
- Intentional failure produces all required diagnostic artifacts

### Phase 2 — Authentication pilot

Deliverables:

- Login, signup, recovery, logout, and session plans
- Page objects/fixtures where justified
- Critical functional tests
- Test-email integration
- Authentication accessibility and responsive tests
- Requirement mapping

Exit criteria:

- All approved critical authentication scenarios are automated or explicitly marked manual with rationale
- Tests can run repeatedly without manual cleanup

### Phase 3 — Coverage and CI gates

Deliverables:

- Manifest validation
- Requirement coverage generator
- Route coverage inventory/check
- PR smoke workflow
- Post-deployment full regression workflow
- Scheduled cross-browser workflow
- Required-check configuration instructions

Exit criteria:

- Missing critical scenario automation fails CI
- A failed UI test blocks the intended deployment stage
- Reports are accessible from the CI run

### Phase 4 — Accessibility and visual regression

Deliverables:

- Shared axe fixture and approved baseline process
- Critical state scans
- Stable visual test environment
- Initial reviewed visual baselines
- Baseline-update documentation

Exit criteria:

- New serious/critical accessibility violations fail according to policy
- Visual differences require human baseline approval

### Phase 5 — Change-impact and AI workflows

Deliverables:

- Deterministic changed-file impact analyzer
- PR impact summary
- Agent prompts/instructions for planning, generation, impact analysis, and constrained healing
- A demonstrated signup-field-change exercise

Exit criteria:

- Changing signup fields identifies the signup feature and its mapped plan/tests
- The agent proposes appropriate plan/test changes
- Expected business outcomes cannot be changed silently

### Phase 6 — Expansion and optimization

Deliverables:

- Remaining critical features onboarded by risk
- Stable sharding/parallelization
- Flaky-test trend reporting
- Documented ownership and maintenance process

Exit criteria:

- All critical feature plans meet the coverage target
- Runtime and reliability targets are met or exceptions are approved

---

## 19. Required package scripts or equivalent commands

Add commands using the repository's naming convention. Suggested semantics:

```json
{
  "scripts": {
    "test:ui": "playwright test",
    "test:ui:smoke": "playwright test --grep @smoke",
    "test:ui:debug": "playwright test --debug",
    "test:ui:report": "playwright show-report",
    "test:ui:a11y": "playwright test --grep @a11y",
    "test:ui:visual": "playwright test --grep @visual",
    "test:ui:validate-specs": "<repository-appropriate command>",
    "test:ui:impact": "<repository-appropriate command>",
    "test:ui:coverage": "<repository-appropriate command>"
  }
}
```

Do not overwrite existing scripts with different meanings. Prefer tags/annotations that are supported by the installed Playwright version.

---

## 20. Agent implementation checklist

The implementing agent must complete and report each item:

- [ ] Repository and CI discovery completed
- [ ] Existing user changes preserved
- [ ] Assumptions documented
- [ ] Test target safety check implemented
- [ ] Playwright installed/configured or existing runner justified
- [ ] Local commands documented
- [ ] Feature-plan template created
- [ ] Manifest schema and validation created
- [ ] Authentication plans created from confirmed requirements
- [ ] Login tests implemented
- [ ] Signup tests implemented
- [ ] Email test implemented using safe inbox
- [ ] Recovery/logout/session tests implemented as applicable
- [ ] Accessibility checks implemented
- [ ] Responsive project implemented
- [ ] Failure network/console monitoring implemented
- [ ] Required reports/artifacts implemented
- [ ] Requirement coverage implemented
- [ ] Route coverage implemented
- [ ] Change-impact analysis implemented
- [ ] PR workflow implemented
- [ ] Deployed regression workflow implemented
- [ ] Scheduled/full matrix workflow implemented or documented for follow-up
- [ ] AI maintenance instructions implemented
- [ ] Intentional failure path verified and restored
- [ ] Full relevant test suite executed
- [ ] Known limitations and follow-up work documented

---

## 21. Definition of done for every new UI feature

A UI feature is complete only when:

1. It has approved acceptance criteria and stable scenario IDs.
2. Its plan covers relevant success, failure, boundary, navigation, accessibility, role, and responsive cases.
3. Critical scenarios are automated at the appropriate test layer.
4. Routes, source paths, and test paths are mapped in the manifest.
5. Test data is isolated and repeatable.
6. Tests use stable, accessible locators.
7. CI executes the appropriate suite.
8. Failure artifacts are produced.
9. Coverage reports include the feature.
10. Any manual-only scenario has a rationale and owner.

---

## 22. Acceptance test for automatic maintenance

Demonstrate the workflow with this controlled change:

> Change signup from First Name, Last Name, Email, User ID, and Password to Name, Email, and Password.

The implementation passes this demonstration when:

- The impact analyzer identifies signup as affected.
- The change is classified as form schema/validation and behaviour impact.
- The existing signup plan is flagged for update.
- Old field scenarios and locators are identified.
- The proposed plan replaces First Name/Last Name/User ID cases with Name cases.
- Duplicate-email, password-policy, email-delivery, navigation, accessibility, error-state, and responsive scenarios remain unless the approved requirement changes them.
- Generated test changes are presented as reviewable diffs.
- Removed scenarios are explicitly listed rather than silently deleted.
- Tests execute against the approved preview/staging environment.
- A product regression remains a failure; the healer does not rewrite the expected outcome.

---

## 23. Final implementation report format

At completion, return:

### Summary

- What was implemented
- Which requirements are satisfied

### Repository discoveries

- Stack, paths, CI, environments, and existing conventions

### Files changed

- File/path and purpose

### Commands executed

- Install, validation, test, and build commands with results

### Coverage

- Requirement, route, browser/viewport, accessibility, visual, and code coverage status

### CI behaviour

- Triggers, gates, artifacts, and secrets/configuration still required

### Assumptions and limitations

- Anything not verified or requiring owner input

### Recommended next features

- Ordered by business risk and reuse of the authentication foundation

---

## 24. Inputs the repository owner should provide when available

The agent should proceed with discovery, but request these inputs when the repository cannot answer them:

1. Approved post-login destination and authentication error wording/anti-enumeration policy
2. Signup fields and complete validation/password rules
3. Authentication provider and approved way to create test users
4. Approved test/staging environment and environment-identification marker
5. Test-data seed/reset mechanism
6. Test mailbox or mail-catcher mechanism
7. Supported browsers and viewport breakpoints
8. Critical workflows and roles beyond authentication
9. CI runtime and artifact-retention limits
10. Whether AI-generated test patches may be opened automatically or must remain local suggestions

