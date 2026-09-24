# Test Failure Triage

This document describes the triage workflow for failed UI tests, the quarantine process, escalation paths, and the weekly triage cadence.

## Triage Workflow

### Step 1: Collect failure evidence

After a CI run or UAT regression, collect:

1. **Playwright report** — HTML report with screenshots, traces, and error details
2. **JUnit XML** — timing and pass/fail status per test
3. **JSON report** — machine-readable results with flaky status
4. **CI logs** — environment setup, network issues, deploy status
5. **Run context** — run ID, branch, timestamp, worker assignment

### Step 2: Classify each failure

Use the failure analyst skill (`ui-test-failure-analyst`) or follow the classification rules in `docs/testing/agents/ui-test-failure-analyst.md`. Apply in order:

1. **Environment** — was the infrastructure available?
2. **Data** — is there a test data collision?
3. **Flaky** — does it pass on re-run?
4. **Test defect** — is the test wrong?
5. **Product bug** — is the application wrong?

### Step 3: Decide action

| Classification | Action | Who |
|----------------|--------|-----|
| Environment | Retry after recovery; check infra status | Automated on next deploy |
| Data | Reset test data; check namespacing | Test owner |
| Flaky | Quarantine (if evidence supports) | Test owner with failure analyst |
| Test defect | Fix the test | Developer |
| Product bug | File bug; block deploy if critical | Developer; owner if critical journey |

### Step 4: Document

Record the triage decision in the PR description, Slack notification, or weekly triage report. Include:

- Test name and scenario ID
- Classification and confidence
- Evidence summary
- Action taken or recommended
- Owner assignment

## Quarantine Process

### Creating a quarantine entry

Add to `specs/ui/quarantine.json`:

```json
{
  "test": "e2e/tests/smoke/login.spec.ts AUTH-001",
  "reason": "Intermittent timeout on UAT; passes on re-run in 80% of attempts",
  "owner": "@saurabhbilakhia",
  "created": "2026-09-18",
  "expiry": "2026-10-18",
  "status": "active"
}
```

### Quarantine rules

- Maximum duration: 30 days
- Every entry must have an owner
- Critical scenarios remain visible in the dashboard but do not gate deploys
- Expired entries fail the weekly reliability job
- A quarantined test must not be re-quarantined without owner approval after expiry

### Removing a quarantine entry

1. Fix the root cause (flaky test, environment issue, or data problem)
2. Remove the entry from `quarantine.json`
3. Verify the test passes 3 consecutive runs
4. Document the fix in the PR description

## Escalation Path

### Immediate escalation (do not wait for triage)

- Product bug affecting a critical journey (CJ-01 through CJ-11)
- Environment failure persists for 2+ consecutive deploys
- More than 3 tests in the same feature quarantined simultaneously
- Failure classification confidence below 50%

### Escalation recipients

| Issue | Escalate to |
|-------|-------------|
| Product bug (any journey) | `@saurabhbilakhia` via PR label `bug` |
| Product bug (critical journey) | `@saurabhbilakhia` via Slack alert + PR label `critical-bug` |
| Quarantine expiry | `@saurabhbilakhia` via weekly triage report |
| Environment failure | `@saurabhbilakhia` via Slack alert |

## Failure Analysis Records

### Run 35962962845 — UI Tests — UAT (Deployed) — 2026-09-24

- **Context:** main @ `89f4e8e` (first regression run including #272's strategy tests) — 38 failed / 174 passed / 96 skipped
- **Environment check first:** UAT healthy (smoke green, `strategy-api` health UP, 174 regression passed, all error-contexts show rendered logged-in pages) — environment and data ruled out

| Tests | Failures | Classification | Confidence | Action |
|-------|----------|----------------|------------|--------|
| STRAT-001/003/004/005/006, OPT-CHAIN-003, TRADE-005, TRADE-010 | 32 | **test defect** — asserted strategy cards / trade UI without chain load; product only renders them after a successful chain load (`OptionsPage.tsx:76-79,183`), contradicting the contract in `strategies.spec.ts:23-28` | High | Fixed in PR #275 (existing chain-load helpers + market-data skip). Not quarantined — test defects are fixed, not quarantined |
| TRADE-008 | 4 | **test defect** — GET with query params on a POST-only endpoint (`@PostMapping("/suggest")`); GET fell into `GET /{name}` → 400 | High | Fixed in PR #275 (POST with JSON body). Related latent frontend bug fixed in same PR |
| DASH-OVERVIEW-002 (firefox + webkit) | 2 | **tentative flaky** — AG Grid not visible within 5000ms; chromium passed same run with same data; passed on prior UAT runs | Medium (single failing run — below the ≥2-intermittent-failure quarantine threshold) | **Watch next 2 UAT runs.** If it recurs → quarantine with owner `@saurabhbilakhia` + expiry ≤30 days, then investigate grid init timing. If not → close as one-off |

- **Quarantine changes:** none (no test met the evidence bar)
- **Product bugs:** none found

## Weekly Triage Cadence

Every week (suggested: Monday morning), the test owner performs:

### 1. Review failures from the past week

- Read the latest CI run results (PR checks + post-deploy regression)
- Classify any unclassified failures
- Check if quarantined tests have been resolved

### 2. Check quarantine status

- Read `specs/ui/quarantine.json`
- Flag any entries approaching expiry (within 7 days)
- Escalate expired entries that have not been resolved

### 3. Review reliability metrics

- Check the dashboard on the `test-reports` branch
- Review the rolling 30-day reliability window (target: 99% pass rate on unchanged code)
- Note any new flaky tests that appeared this week

### 4. Update the triage report

Record decisions in `docs/testing/triage.md` or as a PR comment:

```markdown
## Weekly Triage — 2026-09-22

### Failures reviewed
- AUTH-001: flaky (timeout on UAT) → quarantined until 2026-10-06
- PORT-003: product bug → filed as #123
- DASH-002: environment (UAT deploy in progress) → no action needed

### Quarantine status
- AUTH-001: active, expires 2026-10-06
- WHEEL-005: expired → escalated to @saurabhbilakhia

### Reliability
- 30-day pass rate: 98.7% (target: 99%)
- New flaky: WHEEL-005 (2 failures in 10 runs)
```

### 5. Communicate

- Post the triage summary to Slack if there are critical items
- Create issues for unresolved product bugs
- Update the dashboard with any quarantine changes

## Data Hygiene

Test data accumulates on UAT over time. Monitor:

- Count of run-namespaced users (where the API exposes creation time)
- Age distribution of test data
- Disk usage trends

When data volume exceeds the threshold (to be measured in Phase 6), trigger a manual UAT reset per the procedure in `docs/testing/operations.md`.
