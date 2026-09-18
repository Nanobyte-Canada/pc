---
name: ui-test-planner
description: Use when authoring or updating UI test plans, or when the impact gate flags a feature change with no plan or test update
---

# UI Test Planner

## Overview

This skill guides the creation and maintenance of UI test plans in `specs/ui/`. Plans define scenarios for a feature, map them to requirements, and drive traceability through the manifest and coverage system.

**Announce at start:** "I'm using the ui-test-planner skill to plan test scenarios."

## Policy Document

**Read before acting:** `docs/testing/agents/ui-test-planner.md` contains the full policy rules. These rules are non-negotiable — follow them exactly.

## When to Use

- Authoring a new feature plan in `specs/ui/<area>/<feature>.md`
- Adding scenarios to an existing draft plan
- Updating a plan after a feature change flagged by the impact gate
- Retiring scenarios that no longer apply

## Plan Template

Every plan must follow the template at `specs/ui/_template.md`. The template defines:

- **Front matter**: `feature_id`, `feature`, `owner`, `status`, `priority`, `critical_journeys`, `requirement_refs`, `routes`, `roles`, `flags`, `components`, `source_overrides`, `tags`, `last_reviewed`, `review`, `next_id`
- **Scenario sections**: Given/When/Then format, scenario metadata (Priority, Type, Layer, Target, Automation)
- **Retired scenarios table**: date, reason, replacement ID

## Workflow

### Step 1: Identify the feature and area

1. Determine which feature area the plan belongs to (authentication, dashboard, portfolio, etc.)
2. Check if a plan already exists at `specs/ui/<area>/<feature>.md`
3. If it exists and is `status: approved`, you can only edit the scenario table — all other front matter changes require human approval

### Step 2: Resolve requirement references

1. Read `docs/testing/requirements-index.json` to find existing `RQ-*` markers
2. Every plan MUST have at least one entry in `requirement_refs`
3. If no matching requirement exists, flag this as a gap — requirements are added to design specs (`docs/superpowers/specs/*.md`) by humans, not by agents
4. Format: `requirement_refs: [RQ-AUTH-001, RQ-AUTH-002]`

### Step 3: Assign scenario IDs

IDs follow the pattern `<FEATURE-ID>-<NNN>` where:
- `<FEATURE-ID>` matches the `feature_id` in front matter (e.g., `AUTH`, `DASH`, `OPT`)
- `<NNN>` is a zero-padded sequential number starting from the plan's `next_id`

**Rules:**
- IDs are allocated from `next_id`, which only increases
- IDs are never reused
- A material intent change retires the old scenario and allocates a new ID
- Example: if `next_id: 003`, new scenarios get `AUTH-003`, `AUTH-004`, `AUTH-005`, and `next_id` updates to `006`

### Step 4: Write scenario content

For each scenario, provide:

1. **Metadata row**: Priority (critical/high/normal), Type (happy-path/negative/boundary/navigation/accessibility/visual/authorization), Layer (component/browser/manual), Target (`deployed` — the only supported target), Automation (yes/no/pending)
2. **Given/When/Then** blocks with concrete steps
3. **Expected results** tied to verifiable behavior
4. **Requirement-to-test mapping**: which `RQ-*` IDs this scenario satisfies

### Step 5: Update the retired table

If retiring a scenario:
1. Add it to the retired table with date, reason, and replacement ID
2. Never delete a scenario from the active table — retire it instead
3. Retired IDs are never reused

### Step 6: Validate before saving

Run validation mentally against these checks:
- [ ] `feature_id` is uppercase, matches the folder and filename
- [ ] `owner` matches an entry in `.github/CODEOWNERS`
- [ ] All `requirement_refs` resolve against `docs/testing/requirements-index.json`
- [ ] Scenario IDs are unique within the plan
- [ ] `next_id` is one more than the highest allocated ID
- [ ] Every scenario has a non-empty `requirement_refs` mapping row
- [ ] `flags: []` (no flag system exists)
- [ ] `status` is `draft` for new plans
- [ ] No scenario weakens or removes an assertion from another scenario
- [ ] Critical scenarios have `priority: critical` and map to a critical journey

## Write Boundaries

You may create or modify files in:
- `specs/ui/**` — test plans and manifest
- `docs/testing/**` — documentation and generated reports

You may NOT modify:
- Workflows (`.github/workflows/`)
- CODEOWNERS (`.github/CODEOWNERS`)
- Visual regression baselines
- Allowlists, retry/timeout configuration
- Any file outside the write boundaries

## Status Rules

- `draft → approved` is **human-only**. You create drafts; humans approve them.
- You may move `approved → draft` if the plan needs material rework, but document why in the PR description.
- You may move any status to `retired` with a reason.

## Quality Checklist

Before saving a plan, verify:

1. **Scenario coverage**: Every critical journey the feature participates in has at least one critical scenario
2. **Negative coverage**: Every required field has a negative scenario for invalid input
3. **Boundary coverage**: Numeric and length constraints have boundary scenarios
4. **Navigation coverage**: Every route in `routes` has a navigation scenario
5. **Role coverage**: Every role in `roles` has an authorization scenario (or `feature-absent` with reason)
6. **No placeholders**: No TBD, TODO, or "implement later" in any scenario

## Example Scenario

```markdown
### Scenario: AUTH-001 — Successful email/password login

| Field | Value |
|-------|-------|
| Priority | critical |
| Type | happy-path |
| Layer | browser |
| Target | deployed |
| Automation | yes |

**Given** a user with valid email and password credentials
**When** the user navigates to `/login` and submits the login form
**Then** the user is redirected to `/dashboard`
**And** the session cookie is set
**And** the user's name appears in the sidebar

| Requirement | Test |
|-------------|------|
| RQ-AUTH-001 | e2e/tests/smoke/login.spec.ts |
```
