# UI Testing Operations

## Approvers

| Area | Approver | Role |
|------|----------|------|
| Test plans (`specs/ui/**`) | `@saurabhbilakhia` | Plan approver |
| E2E tests (`e2e/**`), component tests, scripts | `@saurabhbilakhia` | Test owner |

Process for adding approvers: document in CODEOWNERS and this file; requires owner sign-off.

## Alert Channel

Slack notifications via `slackapi/slack-github-action@v2.0.0` using `secrets.SLACK_WEBHOOK_URL`. Alerts include failing scenario and journey IDs with links to the dashboard and run.

- **Webhook URL:** Configured in GitHub repository secrets (`SLACK_WEBHOOK_URL`). No hardcoded URL in codebase.
- **Fires on:** `ui-tests-deployed.yml` (any suite failure), `ui-tests-prod-smoke.yml` (prod smoke failure), `build.yml` and `deploy*.yml` (infra failures).
- **Payload format:** Includes environment (`uat`/`prod`), run link, and failure summary.

## Dashboard

Test reports are published to the `test-reports` branch via `e2e/scripts/publish-history.ts`.

- **Branch:** `test-reports` (orphan branch, no dependency on main).
- **Files:** `history.json` (append-only time series) and `dashboard.md` (rendered table of last 30 runs).
- **Metrics per entry:** timestamp, commit, branch, total tests, passed, failed, skipped, pass rate, flaky rate.
- **Access:** `git checkout test-reports && cat docs/testing/dashboard.md` or view via GitHub branch selector.

## Review Cadence

- **Weekly triage:** Review failed tests, flaky test quarantine, new exclusions. Invoke failure analyst skill for unresolved failures. Review `history.json` trend for pass-rate regression.
- **Per-release:** Manual accessibility review of critical journeys. Verify visual snapshots against latest UAT deployment. Run `check-expiries.ts` to catch stale test data.
- **Quarterly:** Review critical journey list, update coverage targets, revisit plan review windows. Prune `history.json` entries older than 90 days if file exceeds 500 entries.

## Environment Safety

- Browser tests only run against https://uatportfolio.nanobyte.ca (UAT)
- Production smoke suite is read-only
- Tests must never modify production data
- Environment safety contract enforced by `app.fixture.ts`: `BASE_URL` must be in committed allowlist, page must expose `<meta name="app-environment" content="uat">`, production markers must be absent

## Secrets

- `APP_TEST_ADMIN_EMAIL` / `APP_TEST_ADMIN_PASSWORD`: UAT admin test account credentials (single account used for all authenticated UI suites; has both admin and user access)
- Stored in GitHub repository secrets
- No committed seed accounts

## Manual UAT Reset

Reset triggers when test-scoped data accumulates beyond manageable volume. For this personal-portfolio app, data volume is low; thresholds are conservative.

### Data-Volume Threshold

| Metric | Threshold | Action |
|--------|-----------|--------|
| UAT DB row count (test portfolios) | > 500 rows | Reset test data |
| Stale test accounts (older than 30 days) | > 10 accounts | Prune accounts |
| Artifacts (screenshots, traces) | > 1 GB on disk | Clear old artifacts |
| `history.json` entries | > 500 entries | Prune to last 90 days |

### Reset Steps

1. **Pre-check:** Verify no active test runs are in progress (check GitHub Actions).
2. **Data cleanup:** Run UAT data cleanup (remove test-namespaced portfolios, orders, positions created by `APP_TEST_ADMIN_EMAIL`).
3. **Credential refresh:** If the admin test account password is stale, regenerate and update `secrets.APP_TEST_ADMIN_PASSWORD` in GitHub.
4. **Baseline rebuild:** Re-run visual baseline workflow (`ui-visual-baseline-update.yml`) to capture fresh snapshots.
5. **Verification:** Run full regression suite against clean UAT to confirm no data-dependent failures.
6. **Log:** Record the reset in the team Slack channel with date and reason.

## Gate Thresholds

Recommended thresholds for CI quality gates. These are initial values based on 30-day measurement window; adjust after first quarter of data.

| Gate | Threshold | Rationale |
|------|-----------|-----------|
| Smoke pass rate | ≥ 95% | Smoke tests are critical-path; any failure blocks deploy |
| Regression pass rate | ≥ 90% | Allows for known-flaky quarantine without blocking |
| Accessibility pass rate | ≥ 100% | Zero-tolerance for a11y regressions on critical journeys |
| Flaky rate | ≤ 5% | Above this, quarantine review is triggered |
| Visual diff count | ≤ 2 per release | Excessive diffs indicate either UI drift or stale baselines |
| Prod smoke pass rate | 100% | No exceptions for production smoke suite |

## Escalation Path

| Issue Type | Owner | Action |
|------------|-------|--------|
| Flaky tests (intermittent failure) | `@saurabhbilakhia` | Quarantine with `@flaky` tag, file issue, investigate root cause within 1 sprint |
| Environment issues (UAT down, auth broken) | `@saurabhbilakhia` | Check UAT deployment status, verify Cloudflare Tunnel, restart services if needed |
| Genuine bugs (test caught real regression) | `@saurabhbilakhia` | File bug with failing scenario ID and journey ID, link to test run and trace |
| CI infrastructure (workflow failures) | `@saurabhbilakhia` | Check GitHub Actions status, verify secrets, review runner logs |
| Visual baseline drift | `@saurabhbilakhia` | Re-run baseline workflow, review diff manually, update snapshots if intentional |

**Escalation SLA:** All issues triaged within 48 hours. Blocking issues (smoke failure, prod regression) addressed same day.
