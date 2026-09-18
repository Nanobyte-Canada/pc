---
name: ui-test-impact-analyst
description: Use when analyzing code changes for test impact, or when the impact gate flags a PR that needs analysis
---

# UI Test Impact Analyst

## Overview

This skill guides the analysis of code changes to determine their impact on the UI test suite. It helps identify which tests need to run, classifies changes as style-only vs behavioral, and produces actionable impact reports.

**Announce at start:** "I'm using the ui-test-impact-analyst skill to analyze test impact."

## Policy Document

**Read before acting:** `docs/testing/agents/ui-test-impact-analyst.md` contains the full policy rules. These rules are non-negotiable — follow them exactly.

## When to Use

- A PR changes frontend code and you need to determine which tests are impacted
- The deterministic impact report exists and needs human-interpretable context
- A developer asks "do my changes break any tests?"
- The impact gate flags a feature change with no corresponding plan/test update
- You need to classify a change as style-only (CSS, layout) vs behavioral

## Workflow

### Step 1: Read the deterministic impact report

1. Run `npx tsx e2e/scripts/analyze-test-impact.ts --base <base-ref>` to produce the impact report
2. The script reads `specs/ui/manifest.json` and the git diff to map changed files to features and scenarios
3. Review the output: changed files, impacted features, matched scenarios, and matched source files

### Step 2: Classify the change type

For each changed file, determine the change classification:

| Classification | Criteria | Test action |
|----------------|----------|-------------|
| **style-only** | CSS class changes, color/font adjustments, spacing, responsive breakpoints, no logic change | No test execution required; report as low-impact |
| **layout-only** | Component reordering, flex/grid adjustments, no behavior change | Visual regression tests only |
| **behavioral** | State management, event handlers, API calls, validation logic, routing changes | Full test suite for impacted features |
| **infrastructure** | Build config, dependency updates, tooling changes | Depends on scope; analyze carefully |

**How to classify:**

1. Read the git diff for the changed file
2. Check if changes affect: state variables, event handlers, API calls, validation, routing, or business logic
3. If the change is purely presentational (CSS classes, inline styles, layout utilities), classify as **style-only**
4. If uncertain, classify as **behavioral** (err on the side of caution)

### Step 3: Map changes to features and scenarios

1. For each behavioral change, cross-reference the changed file against the manifest
2. Look up which features list the changed file in `source_files` or `components`
3. Identify all scenario IDs associated with those features
4. Note the test layer for each impacted scenario (component vs browser)

### Step 4: Check for plan gaps

1. For each impacted feature, verify that a plan exists at `specs/ui/<area>/<feature>.md`
2. If a behavioral change affects a feature with no plan, flag it as a gap
3. If a behavioral change affects a feature whose plan is `status: draft`, note that the plan may need updating
4. If the impact gate reports a missing test for a critical journey, escalate

### Step 5: Produce the impact summary

Format the output as:

```
## Impact Analysis — PR #<number>

### Changed files
- `frontend/src/components/...` — behavioral (state change)
- `frontend/src/styles/...` — style-only (CSS classes)

### Impacted features
| Feature | Scenarios | Layer | Action |
|---------|-----------|-------|--------|
| AUTH-LOGIN | AUTH-001, AUTH-002 | browser | Run regression |
| PORTFOLIO | PORT-001 | component | Run component tests |

### Gaps
- [ ] Feature X has behavioral changes but no plan
- [ ] Feature Y has critical scenarios but tests are not automated

### Recommendation
Run: `npx playwright test --grep "@AUTH-001|@AUTH-002|@PORT-001"`
```

### Step 6: Suggest test updates (if needed)

If the impact reveals that a changed behavior has no corresponding test:

1. Suggest what test scenarios should be added or updated
2. Reference the plan's scenario categories (negative, boundary, etc.)
3. Do NOT write or modify tests — suggest only; tests are written by the planner or developer

## Write Boundaries

You may create or modify files in:
- `specs/ui/**` — test plans and manifest
- `docs/testing/**` — documentation and generated reports

You may NOT modify:
- `.github/workflows/` — CI/CD workflows
- `.github/CODEOWNERS` — ownership rules
- `e2e/tests/visual/**/*-snapshots/**` — visual regression baselines
- `e2e/a11y-baseline.json` — accessibility baseline exceptions
- Retry, timeout, or concurrency configuration in any file

## Classification Examples

### Style-only (no tests needed)

```diff
- <Button className="bg-blue-500">
+ <Button className="bg-blue-600">
```

```diff
- <Card className="p-4">
+ <Card className="p-6">
```

### Behavioral (tests needed)

```diff
- const handleSubmit = () => { navigate('/dashboard'); }
+ const handleSubmit = async () => {
+   await api.submit(data);
+   navigate('/dashboard');
+ }
```

```diff
- if (amount < 0) return;
+ if (amount < 0) throw new Error('Invalid amount');
```

### Ambiguous (treat as behavioral)

```diff
- <div className="hidden">{error && <ErrorBanner />}</div>
+ <div className="hidden">{error && <ErrorBanner message={error} />}</div>
```

When uncertain, classify as behavioral. False positives waste test time; false negatives miss real bugs.

## Forbidden Patterns

- Classifying a behavioral change as style-only without evidence
- Skipping impact analysis because "it's a small change"
- Modifying test assertions or expected outcomes
- Removing or weakening test coverage without owner approval
