# PR Review Workflow Improvements — Design

**Date:** 2026-06-20
**Status:** Approved
**Goal:** Improve accuracy and precision of the automated PR review workflow (code quality, breaking changes, security, documentation).

---

## 1. Architecture Overview

The reusable workflow (`pr-review.yml`) remains the single source of truth, called by the trigger workflow (`pr-review-trigger.yml`). Improvements deliver four capabilities:

1. **Smart pre-filtering** — classify changed files; skip LLM review when only non-code files changed; exclude `.github/` from the reviewed diff.
2. **Context injection** — attach README.md and `docs/reference/` files via `--file` flags so the LLM knows API contracts, schema, patterns, and key constraints.
3. **Model upgrade** — switch to `opencode/deepseek-v4-pro` for higher review quality. Increase timeout from 15 to 20 minutes.
4. **Two-pass review** — Pass 1: code quality + breaking changes. Pass 2: security + documentation. Single `opencode run` invocation.

### Triggers

Unchanged: `pr-review-trigger.yml` triggers on PRs to `development` and `main`. It calls the reusable workflow with:
- `model`: `opencode/deepseek-v4-pro`
- `fail-on-high`: `true` (blocking)

---

## 2. Model & Authentication

- **Model**: `opencode/deepseek-v4-pro`
- **Timeout**: 20 minutes (up from 15)
- **Fail-on-high default**: `true` in the reusable workflow; the trigger workflow sets it explicitly
- **Auth**: No changes — same `OPENCODE_API_KEY` secret and `auth.json` mechanism

---

## 3. Diff Handling & Filtering

### File Classification

| Category | Patterns | Action |
|----------|----------|--------|
| **Code** | `*.kt`, `*.ts`, `*.tsx`, `*.java`, `*.css`, `*.sql`, `*.gradle.kts`, `*.xml`, `*.html`, `*.properties`, `*.yml`, `*.yaml` (non-`.github/`) | Include in review diff |
| **Skip** | `.github/**`, `*.md`, `*.png`, `*.jpg`, `*.svg`, `*.lock`, `package-lock.json` | Exclude from review |

### Flow

1. `git diff --name-only BASE...HEAD` → classify files
2. **If no code files changed**: skip LLM, post skip comment, exit clean
3. **Filter the diff**: `git diff BASE...HEAD -- . ':!.github' ':!*.png' ':!*.jpg'`
4. **Truncation guard**: If diff > 30KB, truncate with a note
5. **Empty diff check**: After filtering, if diff is 0 bytes, skip

### Edge Case: Only `.github/` Changes

If only workflow/CI files changed, the step posts a comment: "Skipping automated review — CI/config changes only." and exits with success.

---

## 4. Project Context Injection

Files attached via `--file` flags in this order:

1. `README.md` — project overview, tech stack, structure
2. `docs/reference/INDEX.md` — key constraints: No local JDK, No Tailwind CSS, use `apiFetch()`, MockK not Mockito, Vitest + Testing Library
3. `docs/reference/backend-services.md` — service names, DI patterns
4. `docs/reference/frontend-map.md` — component tree, hooks, stores
5. `docs/reference/database-schema.md` — full 53-table schema with columns
6. `docs/reference/api-endpoints.md` — REST API contracts
7. `docs/reference/configurations.md` — Spring profiles, env vars, feature flags
8. `CONTRIBUTING.md` — coding conventions (new file, see Section 7)
9. `config/.env.example` — environment variable names

All files are attached with defensive existence checks (skip if missing). The prompt instructs: "Use these for validation context, do not review the docs themselves."

### File Size Management

- `database-schema.md` is large (~80KB). Attach it fully — the LLM reads all context files and references what's relevant.
- All context files are attached unconditionally if they exist. If a specific file is missing, it's silently skipped.

---

## 5. Prompt Design

The prompt is written to a temp file and piped via stdin. Structure:

### Preamble

```
You are reviewing a PR for the Portfolio Construction App.
Tech: Kotlin 2.0/Spring Boot 3.3 + React 18/TypeScript 5.6/Vite 5
DB: PostgreSQL 16 + Flyway migrations. Redis 7. MockK for testing.
Key constraints from INDEX.md (attached):
  - No local JDK; No Tailwind CSS; use apiFetch() for API calls
  - Hibernate DDL=validate (schema managed by Flyway)
  - MockK not Mockito; Vitest + Testing Library for frontend
Context docs attached — use them to validate consistency, do not review the docs.
```

### Pass 1: Code Quality & Breaking Changes

```
=== PASS 1: CODE QUALITY & BREAKING CHANGES ===
- Logic bugs: null safety, edge cases, boundary conditions, error handling
- Breaking changes: API signatures, DB migrations, removed exports, schema changes
- Code duplication, SOLID violations, performance issues (N+1, sync in async)
- Test coverage: new logic without tests, deleted tests without replacement
- Check changed files against attached schema/api docs for consistency
```

### Pass 2: Security & Documentation

```
=== PASS 2: SECURITY & DOCUMENTATION ===
- Auth gaps, injection risks, secret/token exposure in code
- Missing or stale documentation relative to changes
- Missing KDoc/JSDoc for public APIs
- Violations of key constraints from INDEX.md or CONTRIBUTING.md
```

### Output Format

```
[HIGH] Category: Description
Location: file:line
Suggestion: fix or improvement
---
[LOW] Category: Description
Location: file:line
Suggestion: fix or improvement
---
SUMMARY: X HIGH, Y LOW
```

---

## 6. Review Execution & Gating

### Execution

Single `opencode run` invocation with both passes. All context files and the diff attached via `--file`. Prompt piped via stdin.

```bash
declare -a FILE_ARGS
for f in /tmp/pr_diff.diff README.md docs/reference/INDEX.md \
         docs/reference/backend-services.md docs/reference/frontend-map.md \
         docs/reference/database-schema.md docs/reference/api-endpoints.md \
         docs/reference/configurations.md CONTRIBUTING.md config/.env.example; do
  [ -f "$f" ] && FILE_ARGS+=(--file "$f")
done

cat /tmp/review_prompt.txt | opencode run --model "${{ inputs.model }}" \
  "${FILE_ARGS[@]}" 2>&1 | tee /tmp/review_output.txt
```

### Gating Matrix

| Scenario | Action |
|----------|--------|
| Only non-code files changed | Skip LLM, post skip comment, exit 0 |
| Diff empty after filtering | Skip LLM, post comment, exit 0 |
| 0 findings (HIGH+LOW = 0) on code diff | Warning annotation, no blocking |
| 1+ HIGH finding | Fail build (exit 1) with error annotation |
| Only LOW findings | Pass, findings posted as comment |
| No structured output from LLM | Error, retry once |
| Diff truncated | Warning appended to PR comment |

### PR Comment Format

Unchanged structure (severity table + truncated findings) with additions:
- Link to workflow run for full log
- "Diff truncated to 30KB" note if applicable
- "Skipped: no code changes" note for skip scenarios

---

## 7. CONTRIBUTING.md

New file at repo root. Content:

```markdown
# Contributing to Portfolio Construction App

## Tech Stack Constraints
- Backend: Kotlin 2.0.21, Spring Boot 3.3.5, JDK 21
- Frontend: React 18, TypeScript 5.6, Vite 5 (use apiFetch() for all API calls)
- DB: PostgreSQL 16, Flyway migrations, Hibernate DDL=validate
- Testing: MockK (backend), Vitest + Testing Library (frontend)
- No Tailwind CSS — use plain CSS with custom properties
- No local JDK — backend work runs inside Docker

## PR Checklist
- [ ] All existing tests pass locally (./gradlew test / npm run test:run)
- [ ] New tests for new logic
- [ ] No breaking API contract changes without documentation
- [ ] Flyway migration scripts follow V##__description.sql naming
- [ ] Frontend uses apiFetch() for all API calls
- [ ] No secrets or keys in code (use config/.env.example pattern)
- [ ] Lint passes (npm run lint)

## Code Review Guidelines
The automated PR review checks for:
- Logic bugs, null safety, edge cases, error handling
- Breaking API/schema changes
- Security: auth gaps, injection risks, secret exposure
- Test coverage gaps
- Documentation staleness
- Violations of the constraints above

Human reviewers should focus on design decisions, architecture, and business logic
that automated review cannot assess.
```

The existing workflow already checks for `CONTRIBUTING.md` at three paths (root, `.github/`, `docs/`) in the "Attach contributing.md if present" step — this file simply fills the empty slot.

---

## 8. File Changes Summary

| File | Change |
|------|--------|
| `.github/workflows/pr-review.yml` | Diff filtering, context injection, two-pass prompt, model default update, timeout increase, gating logic |
| `.github/workflows/pr-review-trigger.yml` | Model to `opencode/deepseek-v4-pro`, `fail-on-high: true` |
| `CONTRIBUTING.md` | New file with coding conventions |

---

## 9. Non-Goals (Out of Scope)

- Caching incremental review results across PR pushes
- Historical review comparison/trends
- Check-run blocking status (already achieved via `exit 1` in the workflow step)
- Language-specific prompt variations for Kotlin vs TypeScript
- Dependabot integration for auto-bumping `@v4` action versions
