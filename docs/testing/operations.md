# UI Testing Operations

## Approvers

| Area | Approver | Role |
|------|----------|------|
| Test plans (`specs/ui/**`) | `@saurabhbilakhia` | Plan approver |
| E2E tests (`e2e/**`), component tests, scripts | `@saurabhbilakhia` | Test owner |

Process for adding approvers: document in CODEOWNERS and this file; requires owner sign-off.

## Alert Channel

Slack notifications via `slackapi/slack-github-action@v2.0.0` using `secrets.SLACK_WEBHOOK_URL`. Alerts include failing scenario and journey IDs with links to the dashboard and run.

## Dashboard

Test reports are published to the `test-reports` branch as `dashboard.md` (generated). No GitHub Pages dependency.

## Review Cadence

- **Weekly triage:** Review failed tests, flaky test quarantine, new exclusions. Invoke failure analyst skill for unresolved failures.
- **Per-release:** Manual accessibility review of critical journeys.
- **Quarterly:** Review critical journey list, update coverage targets, revisit plan review windows.

## Environment Safety

- Browser tests only run against https://uatportfolio.nanobyte.ca (UAT)
- Production smoke suite is read-only
- Tests must never modify production data
- Environment safety contract enforced by `app.fixture.ts`: `BASE_URL` must be in committed allowlist, page must expose `<meta name="app-environment" content="uat">`, production markers must be absent

## Secrets

- `E2E_USER_EMAIL` / `E2E_USER_PASSWORD`: UAT test account credentials
- Stored in GitHub repository secrets
- No committed seed accounts

## Manual UAT Reset

[Placeholder — finalized in Phase 6 after data-volume measurements]

Reset triggers when run-namespaced test data exceeds a threshold (to be measured). Procedure will be documented here once the data-volume threshold is agreed.
