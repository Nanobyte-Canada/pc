---
name: ui-test-failure-analyst
description: Use when analyzing test failures after a UAT regression, or when deciding whether to quarantine a flaky test
---

# UI Test Failure Analyst

## Overview

This skill guides the analysis of test failures to identify root causes and classify them into actionable categories. It helps distinguish between genuine product bugs, test defects, environment issues, and data problems.

**Announce at start:** "I'm using the ui-test-failure-analyst skill to analyze test failures."

## Policy Document

**Read before acting:** `docs/testing/agents/ui-test-failure-analyst.md` contains the full policy rules. These rules are non-negotiable — follow them exactly.

## When to Use

- A post-deploy UAT regression shows red tests
- A test fails intermittently across multiple runs
- You need to decide whether to quarantine a test
- A developer asks "why did this test fail?"
- You need to triage a batch of failures from a CI run

## Workflow

### Step 1: Gather failure evidence

1. Read the Playwright HTML report or JSON report from the CI run
2. For each failure, collect:
   - Test name and scenario ID
   - Error message and stack trace
   - Screenshot (if captured)
   - Trace file (if captured)
   - Run context (run ID, branch, timestamps)
3. Check the JUnit XML for timing information

### Step 2: Classify the failure

Apply this classification in order — stop at the first match:

| Class | Signal | Confidence | Action |
|-------|--------|------------|--------|
| **environment** | Network timeout, DNS failure, connection refused, container crash, UAT is down, SSL errors, Cloudflare blocks | High | Retry after environment recovery; no code change needed |
| **data** | "not found" for test-scoped data, assertion on specific values that depend on prior runs, stale state from a previous test, data collision despite namespacing | Medium-High | Check run-context namespacing, verify test isolation; may need data reset |
| **flaky** | Intermittent on same code (passes on re-run), timing-dependent, race condition in UI, network latency variance | Medium | Quarantine with expiry; investigate root cause |
| **test-defect** | Incorrect assertion, wrong locator, missing setup/teardown, test depends on implementation detail, brittle selector | High | Fix the test; do not quarantine |
| **product-bug** | Consistent failure on unchanged code, correct assertion catches real broken behavior, regression from recent change | High | Report as bug; do not quarantine |

### Step 3: Analyze root cause

For each failure class, dig deeper:

**Environment:**
- Check if the UAT environment was available during the run
- Check if a deploy was in progress during the test execution
- Check network logs for timeouts or DNS resolution failures
- Check if the test uses a service that was temporarily down

**Data:**
- Check if the run-context generated unique identities
- Check if another test run created conflicting data
- Check if the test depends on data that was cleaned up
- Check API responses for 404/409/422 errors on test data

**Flaky:**
- Check the last 10 runs for this test — is it consistently green or intermittently red?
- Check if the failure involves timing (wait for element, animation, debounce)
- Check if the failure involves network requests that may vary in latency
- Check if the failure involves async state updates

**Test-defect:**
- Read the test code against the plan scenario — does the assertion match the expected behavior?
- Check if the locator is tied to an implementation detail (CSS class, DOM structure) that changed
- Check if the test setup creates the right preconditions
- Check if the test is testing the right thing

**Product-bug:**
- Read the error against the plan scenario — is the assertion correct?
- Check if a recent commit changed the behavior being tested
- Verify the behavior manually against UAT if possible

### Step 4: Produce the failure analysis report

Format the output as:

```
## Failure Analysis — Run #<id>

### Summary
- Total failures: N
- Environment: X
- Data: X
- Flaky: X
- Test defect: X
- Product bug: X

### Per-failure analysis

#### <test-name> (<scenario-id>)
- **Class:** product-bug
- **Confidence:** high
- **Evidence:** Screenshot shows missing element; error says "element not found"; consistent on re-run
- **Root cause:** Recent commit changed the component API; test assertion is correct
- **Recommendation:** File bug, do not quarantine
```

### Step 5: Quarantine decision (if applicable)

For **flaky** or **data** failures, decide whether to quarantine:

1. Check `specs/ui/quarantine.json` for existing entries
2. If the test is already quarantined, check if the expiry has passed
3. If quarantining a new test, add an entry with:
   - `test`: the test file and scenario ID
   - `reason`: why it was quarantined
   - `owner`: who is responsible for fixing it
   - `expiry`: date when quarantine expires (max 30 days)
   - `created`: date of quarantine
4. Quarantined critical scenarios are visible in the dashboard but do not gate deploys
5. Expired quarantine entries fail the weekly job and must be resolved

### Step 6: Report to owner

1. Attach the failure analysis to the PR or Slack notification
2. If the failure is a product bug, ensure it is tracked (issue or bug label)
3. If the failure is a test defect, suggest the fix but do not modify the test
4. If the failure is quarantined, document the timeline for resolution

## Write Boundaries

You may create or modify files in:
- `specs/ui/**` — quarantine registry, test plans
- `docs/testing/**` — documentation, triage records

You may NOT modify:
- `.github/workflows/` — CI/CD workflows
- `.github/CODEOWNERS` — ownership rules
- `e2e/tests/visual/**/*-snapshots/**` — visual regression baselines
- `e2e/a11y-baseline.json` — accessibility baseline exceptions
- Test code (`e2e/tests/**`, `frontend/**/*.test.*`) — you analyze, you don't fix
- Retry, timeout, or concurrency configuration

## Quarantine Rules

- Maximum quarantine duration: 30 days
- Quarantined tests must have an owner assigned
- Quarantined critical scenarios are reported as "visible, not gating" in the dashboard
- Expired quarantine entries fail the weekly reliability job
- A quarantined test must not be re-quarantined without owner approval after expiry

## Forbidden Patterns

- Quarantining a test without evidence of flakiness (need at least 2 intermittent failures)
- Quarantining a test that is a genuine product bug
- Removing a quarantine entry without owner approval
- Modifying test assertions to make a failing test pass
- Ignoring environment failures without checking if the environment was healthy
