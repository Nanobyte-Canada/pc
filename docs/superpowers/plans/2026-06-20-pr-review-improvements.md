# PR Review Workflow Improvements — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve accuracy and precision of the automated PR review workflow by adding smart diff filtering, project context injection, a two-pass prompt, and upgrading to deepseek-v4-pro.

**Architecture:** Three files changed. `CONTRIBUTING.md` (new) codifies project conventions. `pr-review-trigger.yml` (small edit) switches model and enables blocking. `pr-review.yml` (major edit) adds file classification with skip logic, attaches project reference docs, rewrites the prompt into two focused passes, and improves post-review output.

**Tech Stack:** GitHub Actions YAML, Bash, Node.js (github-script), opencode-ai CLI

## Global Constraints

- Model: `opencode/deepseek-v4-pro`
- `fail-on-high` default: `true` in reusable workflow; trigger workflow sets it explicitly
- Exclude `.github/**`, `*.md`, `*.png`, `*.jpg`, `*.svg`, `*.lock`, `package-lock.json` from review
- Diff truncated at 30KB
- Timeout: 20 minutes
- Context files: README.md, docs/reference/INDEX.md, backend-services.md, frontend-map.md, database-schema.md, api-endpoints.md, configurations.md, CONTRIBUTING.md, config/.env.example — all defensively checked for existence
- Prompt: preamble → Pass 1 (code quality + breaking changes) → Pass 2 (security + documentation) → output format unchanged

---

### Task 1: Create CONTRIBUTING.md

**Files:**
- Create: `CONTRIBUTING.md`

**Interfaces:**
- Produces: `CONTRIBUTING.md` at repo root — consumed by existing "Attach contributing.md" step in `pr-review.yml` which checks root, `.github/`, and `docs/` paths

- [ ] **Step 1: Create the file**

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

- [ ] **Step 2: Commit**

```bash
git add CONTRIBUTING.md
git commit -m "docs: add CONTRIBUTING.md with coding conventions and review guidelines"
```

---

### Task 2: Update pr-review-trigger.yml — model and fail-on-high

**Files:**
- Modify: `.github/workflows/pr-review-trigger.yml`

**Interfaces:**
- Consumes: `pr-review.yml` reusable workflow (existing)
- Produces: Updated trigger workflow that passes `opencode/deepseek-v4-pro` and `fail-on-high: true`

- [ ] **Step 1: Edit model and fail-on-high values**

In `.github/workflows/pr-review-trigger.yml`, change lines 16-17:

From:
```yaml
      model: opencode/deepseek-v4-flash-free
      fail-on-high: false
```

To:
```yaml
      model: opencode/deepseek-v4-pro
      fail-on-high: true
```

Full file after edit:
```yaml
name: PR Review - Code, Docs & Security

on:
  pull_request:
    branches: [development, main]

permissions:
  contents: read
  pull-requests: write

jobs:
  review:
    name: OpenCode PR Review
    uses: ./.github/workflows/pr-review.yml
    with:
      model: opencode/deepseek-v4-pro
      fail-on-high: true
    secrets:
      api_key: ${{ secrets.OPENCODE_API_KEY }}
```

- [ ] **Step 2: Verify YAML syntax**

```bash
yamllint .github/workflows/pr-review-trigger.yml 2>&1 || true
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/pr-review-trigger.yml
git commit -m "feat(ci): switch PR review to deepseek-v4-pro with blocking fail-on-high"
```

---

### Task 3: Update pr-review.yml — diff filtering and skip logic

**Files:**
- Modify: `.github/workflows/pr-review.yml`
  - Replace "Gather PR context" step (lines 39-58)
  - Add "Post skip comment" step before "Attach contributing.md"

**Interfaces:**
- Consumes: BASE_SHA, HEAD_SHA from PR event
- Produces: `/tmp/pr_diff.diff` (filtered), `/tmp/changed_files.txt`, outputs: `skip`, `diff_size`, `changed_files`, `has_code_changes`

- [ ] **Step 1: Replace the "Gather PR context" step with file classification logic**

Replace lines 39-58 of `.github/workflows/pr-review.yml`:

Old content:
```yaml
      - name: Gather PR context
        id: context
        run: |
          BASE_SHA="${{ github.event.pull_request.base.sha || '' }}"
          HEAD_SHA="${{ github.event.pull_request.head.sha || '' }}"

          if [ -z "$BASE_SHA" ] || [ -z "$HEAD_SHA" ]; then
            echo "No PR event context. Required for diff generation."
            echo "skip=true" >> $GITHUB_OUTPUT
            exit 0
          fi

          git diff "$BASE_SHA...$HEAD_SHA" > /tmp/pr_diff.diff
          echo "diff_size=$(wc -c < /tmp/pr_diff.diff)" >> $GITHUB_OUTPUT
          echo "skip=false" >> $GITHUB_OUTPUT

          git diff --name-only "$BASE_SHA...$HEAD_SHA" > /tmp/changed_files.txt
          echo "changed_files=$(wc -l < /tmp/changed_files.txt)" >> $GITHUB_OUTPUT

          cat /tmp/changed_files.txt
```

New content:
```yaml
      - name: Gather PR context
        id: context
        run: |
          BASE_SHA="${{ github.event.pull_request.base.sha || '' }}"
          HEAD_SHA="${{ github.event.pull_request.head.sha || '' }}"

          if [ -z "$BASE_SHA" ] || [ -z "$HEAD_SHA" ]; then
            echo "No PR event context. Required for diff generation."
            echo "skip=true" >> $GITHUB_OUTPUT
            echo "has_code_changes=false" >> $GITHUB_OUTPUT
            exit 0
          fi

          git diff --name-only "$BASE_SHA...$HEAD_SHA" > /tmp/changed_files.txt
          echo "changed_files=$(wc -l < /tmp/changed_files.txt)" >> $GITHUB_OUTPUT

          echo "=== Changed files ==="
          cat /tmp/changed_files.txt

          CODE_PATTERN='\.(kt|ts|tsx|java|css|sql)$|\.gradle\.kts$|\.(xml|html|properties)$|^((?!\.github\/).)*\.(yml|yaml)$'
          CODE_FILES=$(grep -cE "$CODE_PATTERN" /tmp/changed_files.txt || true)

          echo ""
          echo "Code files found: $CODE_FILES"

          if [ "$CODE_FILES" -eq 0 ]; then
            echo "No code files changed. Skipping LLM review."
            echo "skip=true" >> $GITHUB_OUTPUT
            echo "has_code_changes=false" >> $GITHUB_OUTPUT
            exit 0
          fi

          git diff "$BASE_SHA...$HEAD_SHA" -- . ':!.github' ':!*.png' ':!*.jpg' ':!*.md' ':!*.lock' ':!package-lock.json' ':!*.svg' > /tmp/pr_diff.diff

          FULL_SIZE=$(wc -c < /tmp/pr_diff.diff)
          MAX_SIZE=30720
          if [ "$FULL_SIZE" -gt "$MAX_SIZE" ]; then
            head -c "$MAX_SIZE" /tmp/pr_diff.diff > /tmp/pr_diff_truncated.diff
            echo "" >> /tmp/pr_diff_truncated.diff
            echo "... (diff truncated at ${MAX_SIZE} bytes from ${FULL_SIZE} bytes)" >> /tmp/pr_diff_truncated.diff
            mv /tmp/pr_diff_truncated.diff /tmp/pr_diff.diff
            echo "has_truncated=true" >> $GITHUB_OUTPUT
          else
            echo "has_truncated=false" >> $GITHUB_OUTPUT
          fi

          echo "diff_size=$(wc -c < /tmp/pr_diff.diff)" >> $GITHUB_OUTPUT
          echo "skip=false" >> $GITHUB_OUTPUT
          echo "has_code_changes=true" >> $GITHUB_OUTPUT

          echo "Review diff size: $(wc -c < /tmp/pr_diff.diff) bytes"
```

- [ ] **Step 2: Add "Post skip comment" step for non-code PRs**

Insert this new step after the "Gather PR context" step (after line 58 in the original file, which becomes after the new context step) and before "Attach contributing.md":

```yaml
      - name: Post skip comment for non-code changes
        if: steps.context.outputs.has_code_changes == 'false'
        uses: actions/github-script@v7
        with:
          script: |
            await github.rest.issues.createComment({
              ...context.repo,
              issue_number: context.issue.number,
              body: '## OpenCode PR Review\n\nSkipping automated review — no code changes detected (docs, config, or CI files only).\n\nHuman review recommended if these changes affect behavior.'
            });
            console.log('Skip comment posted');
```

- [ ] **Step 3: Verify bash logic in the new "Gather PR context" step**

Run a local test to verify the regex and filtering work:

```bash
mkdir -p /tmp/test_pr_review
cd /tmp/test_pr_review
git init && git commit --allow-empty -m "initial"
touch src/foo.kt src/bar.ts docs/readme.md .github/workflows/test.yml config/app.properties
git add . && git commit -m "test"
BASE_SHA=$(git rev-parse HEAD~1) HEAD_SHA=$(git rev-parse HEAD)
# Test: code files should be detected
git diff --name-only "$BASE_SHA...$HEAD_SHA"
# Test: .github and .md excluded from diff
git diff "$BASE_SHA...$HEAD_SHA" -- . ':!.github' ':!*.md'
rm -rf /tmp/test_pr_review
```

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/pr-review.yml
git commit -m "feat(ci): add diff filtering, .github exclusion, and skip logic for non-code PRs"
```

---

### Task 4: Update pr-review.yml — context injection, two-pass prompt, model default

**Files:**
- Modify: `.github/workflows/pr-review.yml`
  - Change default model (line 10)
  - Remove "Attach contributing.md if present" step (lines 60-69) — now redundant
  - Increase timeout (line 86)
  - Rewrite "Run PR Review with OpenCode" step (lines 84-139)

**Interfaces:**
- Consumes: `/tmp/pr_diff.diff`, `inputs.model`, `secrets.api_key`, project docs from checkout
- Produces: `/tmp/review_output.txt` from LLM

- [ ] **Step 1: Remove the old "Attach contributing.md if present" step**

This step (lines 60-69 in original, now shifted by Task 3 additions) copied `CONTRIBUTING.md` to `/tmp/contributing.md` for attachment. Since Task 4 attaches `CONTRIBUTING.md` directly from the repo root, this step is dead code. Remove it entirely:

```yaml
      - name: Attach contributing.md if present
        if: steps.context.outputs.skip != 'true'
        run: |
          for f in CONTRIBUTING.md .github/CONTRIBUTING.md docs/CONTRIBUTING.md; do
            if [ -f "$f" ]; then
              cp "$f" /tmp/contributing.md
              echo "Attached $f"
              break
            fi
          done
```

Delete these 9 lines.

- [ ] **Step 2: Change default model**

In `.github/workflows/pr-review.yml`, line 10:

From:
```yaml
        default: 'opencode/deepseek-v4-flash-free'
```
To:
```yaml
        default: 'opencode/deepseek-v4-pro'
```

- [ ] **Step 3: Increase timeout**

In `.github/workflows/pr-review.yml`, line 86:

From:
```yaml
        timeout-minutes: 15
```
To:
```yaml
        timeout-minutes: 20
```

- [ ] **Step 4: Rewrite "Run PR Review with OpenCode" step**

Replace lines 84-139 of the original file (the entire "Run PR Review with OpenCode" step):

Old content (lines 84-139):
```yaml
      - name: Run PR Review with OpenCode
        if: steps.context.outputs.skip != 'true'
        timeout-minutes: 15
        run: |
          cat > /tmp/review_prompt.txt << 'PROMPT_EOF'
          You are an expert PR reviewer. Analyze the attached diff (and contributing.md if provided) across three dimensions:

          1. CODE QUALITY REVIEW:
          ...
```

New content:
```yaml
      - name: Run PR Review with OpenCode
        if: steps.context.outputs.skip != 'true'
        timeout-minutes: 20
        run: |
          cat > /tmp/review_prompt.txt << 'PROMPT_EOF'
          You are reviewing a PR for the Portfolio Construction App.
          Tech: Kotlin 2.0/Spring Boot 3.3 + React 18/TypeScript 5.6/Vite 5
          DB: PostgreSQL 16 + Flyway migrations. Redis 7. MockK for testing.
          Key constraints (see INDEX.md and CONTRIBUTING.md attached for details):
            - No local JDK — backend runs in Docker; No Tailwind CSS — use plain CSS
            - Hibernate DDL=validate — schema managed exclusively by Flyway
            - Frontend uses apiFetch() for all API calls
            - MockK not Mockito; Vitest + Testing Library for frontend tests
          Context docs are attached — use them to validate consistency, do not review the docs themselves.

          Review the attached PR diff across two passes. Complete Pass 1 first, then Pass 2.

          === PASS 1: CODE QUALITY & BREAKING CHANGES ===
          - Logic bugs: null safety, edge cases, boundary conditions, error handling
          - Breaking changes: API signatures, DB migrations, removed exports, schema changes — cross-reference the attached api-endpoints.md and database-schema.md
          - Code duplication, SOLID violations, performance (N+1 queries, sync in async paths)
          - Test coverage: new logic without tests, deleted tests without replacement
          - For Kotlin changes: verify against backend-services.md patterns; watch for MockK vs Mockito
          - For frontend changes: verify apiFetch() usage, check against frontend-map.md component patterns

          === PASS 2: SECURITY & DOCUMENTATION ===
          - Auth gaps, injection risks (SQL, XSS, command), secret/token exposure in code — check against config/.env.example for proper env var usage
          - Missing or stale documentation relative to code changes
          - Missing KDoc for public Kotlin APIs, JSDoc for public TypeScript APIs
          - Violations of constraints from INDEX.md or CONTRIBUTING.md (Tailwind usage, direct fetch instead of apiFetch, etc.)

          OUTPUT RULES:
          - Prefix each finding with [HIGH] or [LOW]
          - [HIGH] = breaking changes, security vulnerabilities, logic bugs, missing critical tests, performance issues, authorization gaps, constraint violations
          - [LOW] = style suggestions, minor doc gaps, refactoring opportunities, non-critical warnings

          FORMAT EACH FINDING AS:
          [SEVERITY] Category: Description
          Location: file:line (or N/A)
          Suggestion: how to fix or improve

          Separate findings with "---"
          End with exactly: SUMMARY: X HIGH, Y LOW
          PROMPT_EOF

          if [ ! -s /tmp/review_prompt.txt ]; then
            echo "::error::Review prompt file is missing or empty"
            exit 1
          fi

          declare -a FILE_ARGS
          for f in /tmp/pr_diff.diff README.md \
                   docs/reference/INDEX.md \
                   docs/reference/backend-services.md \
                   docs/reference/frontend-map.md \
                   docs/reference/database-schema.md \
                   docs/reference/api-endpoints.md \
                   docs/reference/configurations.md \
                   CONTRIBUTING.md \
                   config/.env.example; do
            [ -f "$f" ] && FILE_ARGS+=(--file "$f") && echo "Attached: $f" || echo "Skipped (missing): $f"
          done

          cat /tmp/review_prompt.txt | opencode run --model "${{ inputs.model }}" "${FILE_ARGS[@]}" 2>&1 | tee /tmp/review_output.txt
```

- [ ] **Step 5: Verify the prompt can be built without syntax errors**

```bash
bash -n << 'EOF'
cat > /tmp/review_prompt.txt << 'PROMPT_EOF'
test
PROMPT_EOF
echo "Prompt file creation works"
EOF
```

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/pr-review.yml
git commit -m "feat(ci): add project context injection, two-pass prompt, and deepseek-v4-pro default"
```

---

### Task 5: Update pr-review.yml — post-review output improvements

**Files:**
- Modify: `.github/workflows/pr-review.yml`
  - Update "Parse results" step to add 0-findings warning
  - Update "Post PR comment" step to add workflow run link, truncation note, extra context
  - Add step: "Warn on zero findings"

**Interfaces:**
- Consumes: `/tmp/review_output.txt`, `steps.parse.outputs.HIGH_COUNT`, `steps.parse.outputs.LOW_COUNT`, `steps.context.outputs.has_truncated`, `github.run_id`, `github.server_url`, `github.repository`
- Produces: Enhanced PR comment

- [ ] **Step 1: Add "Warn on zero findings" step**

Insert this new step between "Parse results" (ends at line 153) and "Post PR comment" (starts at line 155):

```yaml
      - name: Warn on zero findings
        if: steps.context.outputs.skip != 'true' && steps.parse.outputs.HIGH_COUNT == '0' && steps.parse.outputs.LOW_COUNT == '0'
        run: |
          echo "::warning::Review produced 0 findings on a code diff. This may indicate the review missed issues — consider a manual review."
```

- [ ] **Step 2: Update "Post PR comment" step to add workflow run link and truncation note**

Replace the "Post PR comment with review results" step (lines 155-193) with:

```yaml
      - name: Post PR comment with review results
        if: steps.context.outputs.skip != 'true'
        uses: actions/github-script@v7
        env:
          HIGH_COUNT: ${{ steps.parse.outputs.HIGH_COUNT }}
          LOW_COUNT: ${{ steps.parse.outputs.LOW_COUNT }}
          HAS_TRUNCATED: ${{ steps.context.outputs.has_truncated }}
          RUN_URL: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}
        with:
          script: |
            const fs = require('fs');
            const reviewText = fs.readFileSync('/tmp/review_output.txt', 'utf8').trim();
            const highCount = parseInt(process.env.HIGH_COUNT || '0');
            const lowCount = parseInt(process.env.LOW_COUNT || '0');
            const hasTruncated = process.env.HAS_TRUNCATED === 'true';
            const runUrl = process.env.RUN_URL;

            if (!reviewText) {
              console.log('No review output to post');
              return;
            }

            let prefix = '## OpenCode PR Review\n\n';
            prefix += '| Severity | Count |\n|----------|-------|\n';
            prefix += `| :red_circle: **HIGH** | ${highCount} |\n`;
            prefix += `| :yellow_circle: **LOW** | ${lowCount} |\n\n`;

            if (highCount > 0) {
              prefix += `> :warning: **${highCount} HIGH severity ${highCount === 1 ? 'issue' : 'issues'} found.**\n\n`;
            }

            if (hasTruncated) {
              prefix += '> :information_source: Diff was truncated to 30KB. Review may be incomplete for large PRs.\n\n';
            }

            if (highCount === 0 && lowCount === 0) {
              prefix += '> :information_source: No findings reported. Consider a manual review for completeness.\n\n';
            }

            const maxBodyLen = 60000;
            const truncated = reviewText.length > maxBodyLen
              ? reviewText.substring(0, maxBodyLen) + '\n\n...(output truncated)'
              : reviewText;

            const footer = `\n\n---\n<sub>[View workflow run](${runUrl})</sub>`;

            await github.rest.issues.createComment({
              ...context.repo,
              issue_number: context.issue.number,
              body: prefix + '---\n\n' + truncated + footer
            });

            console.log(`PR comment posted: ${highCount} HIGH, ${lowCount} LOW`);
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/pr-review.yml
git commit -m "feat(ci): add zero-findings warning, workflow run link, and truncation note to PR comment"
```

---

### Verification

After all tasks are complete, verify the entire workflow:

- [ ] **Check YAML syntax of all workflow files**

```bash
yamllint .github/workflows/pr-review.yml .github/workflows/pr-review-trigger.yml 2>&1 || true
```

- [ ] **Check that CONTRIBUTING.md exists and is readable**

```bash
[ -f CONTRIBUTING.md ] && echo "CONTRIBUTING.md exists" || echo "MISSING"
wc -l CONTRIBUTING.md
```

- [ ] **Check final state of all changed files**

```bash
git diff main -- .github/workflows/pr-review.yml .github/workflows/pr-review-trigger.yml CONTRIBUTING.md
```

- [ ] **Push to trigger a test run (optional, user action)**

```bash
git push origin improve-pr-review-workflows
# Then open a PR to trigger the workflow
```
