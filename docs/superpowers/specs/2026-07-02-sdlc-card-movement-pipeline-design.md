# SDLC Card Movement Pipeline: In Review to Done

**Date:** 2026-07-02
**Status:** Approved (design phase)
**Replaces:** pr-review.yml, review-loop.yml (repo-specific workflows)
**Repos affected:** `Nanobyte-Canada/pc`, `Nanobyte-Canada/nanobyte-services`

## Problem

The autonomous SDLC pipeline is operational from Triaging through In Review (PR merge), but the post-merge phases are not wired:

- **In Review to Testing**: After auto-merge, the card stays at "In Review" — no mechanism moves it forward.
- **Testing phase**: The tester agent is a stub. No UAT deployment, no smoke test execution.
- **Bug Fixing loop**: The bugfixer agent is a stub. No path from failed smoke tests back to Testing.
- **Ready to Publish to Publishing**: "Ready to Publish" is a human gate (correct), but "Publishing" dispatches a stub deployer agent.
- **Publishing to Done**: The deployer agent is a stub. No prod deploy, no card closure.

Additionally, the current PR review and review-loop are repo-specific workflows (`pr-review.yml`, `review-loop.yml`), which must be copied to every repo that wants the SDLC pipeline. This is not reusable.

## Design Goals

1. **Complete the card movement pipeline** from In Review through Testing, Bug Fixing, Ready to Publish, Publishing, to Done.
2. **Centralize all SDLC logic in `nanobyte-services`** — agents, Worker, scripts. Each repo only needs `sdlc-agent.yml`, `build.yml`, and `deploy.yml`.
3. **Follow the existing card-state-driven pattern**: agents move cards via `update-card-status.sh`, Worker syncs labels, `sdlc-agent.yml` dispatches agents based on labels.
4. **Make the pipeline reusable** for any repo that opts into the SDLC system.

## Architecture

### Existing Pattern (unchanged)

The SDLC pipeline is card-state-driven:

1. **Agent or workflow moves card** via `bash scripts/update-card-status.sh --issue N --lane "X" --repo OWNER/REPO`
2. **Worker detects card Status change** (`projects_v2_item` moved/edited event) → syncs issue labels (removes old SDLC labels, adds new lane label)
3. **sdlc-agent.yml fires** on `issues: [labeled]` if the new label is a dispatch lane
4. **Agent dispatched** → does work → moves card → repeat

**Dispatch lanes** (trigger agent): Triaging, Planning, Executing, Testing, Bug Fixing, Publishing
**Human-gate lanes** (no dispatch): Scope Review, Plan Review, In Review, Blocked, Ready to Publish, Done, Backlog

### What Changes

All SDLC logic moves to `nanobyte-services`. Repo-specific review workflows are replaced by agents.

| Current (repo-specific) | New (shared in nanobyte-services) |
|---|---|
| `pr-review.yml` (5-stage LLM review) | **reviewer agent** — structural checks + code review + doc review + posts findings |
| `review-loop.yml` (auto-merge/re-dispatch/block) | **reviewer agent** (same) — decides and executes merge/re-dispatch/block |
| `post-merge.yml` (move card to Testing) | **Worker** — handles `pull_request: [closed]` with `merged: true`, moves card to "Testing" |
| `scripts/aggregate.sh` | Logic absorbed into reviewer agent |
| `scripts/post-comment.sh` | Logic absorbed into reviewer agent |

### What Stays in Each Repo

- `sdlc-agent.yml` — adds `pull_request_target` trigger for reviewer agent. Everything else unchanged.
- `build.yml` — CI tests + Docker image build (repo-specific)
- `deploy.yml` — deploy to UAT/prod via Vault + SSH (repo-specific, already triggerable via `gh workflow run`)

## Complete Flow

```
Triaging → Scope Review → Planning → Plan Review → Executing → In Review
  (planner)   (human)      (planner)   (human)      (build)    (reviewer)
                                                                    │
                                                              PR merged
                                                                    │
                                                              Worker detects
                                                              pull_request: closed
                                                              merged=true
                                                                    │
                                                              Worker moves card
                                                              to "Testing"
                                                                    │
Testing ← Worker syncs "Testing" label ← sdlc-agent.yml ←─────────┘
    │
    ▼
tester agent
    │
    ├── wait for build.yml → get image tag → trigger deploy.yml(uat)
    ├── read smoke test plan from issue's Plan comment
    ├── run smoke tests against UAT
    │
    ├── PASS → move card to "Ready to Publish" (human gate)
    │
    └── FAIL → post "## Test Failures" comment
              → move card to "Bug Fixing"
                    │
                    ▼
              Worker syncs "Bug Fixing" label → sdlc-agent.yml → bugfixer agent
                    │
                    ├── read test failures from issue comment
                    ├── fix code on fix/ branch
                    ├── create PR "Implements #N"
                    ├── PR enters review flow (reviewer agent)
                    ├── reviewer auto-merges (or review-fix loop)
                    ├── PR merged → Worker moves card to "Testing"
                    └── tester re-runs smoke tests (LOOP)
                          ├── PASS → "Ready to Publish"
                          └── FAIL → "Bug Fixing" (loop continues)

Ready to Publish (human gate)
    │
    ▼
Human moves card to "Publishing"
    │
    ▼
Worker syncs "Publishing" label → sdlc-agent.yml → deployer agent
    │
    ├── get image tag → trigger deploy.yml(prod)
    ├── wait for deploy → verify health checks
    │
    ├── SUCCESS → move card to "Done" → close issue
    │
    └── FAILURE → post "## Deploy Failed" comment → move card to "Blocked"
```

## Component Designs

### 1. Reviewer Agent (`nanobyte-services/sdlc/opencode/agents/reviewer.md`)

Replaces `pr-review.yml` + `review-loop.yml`. Single agent that does the full review cycle.

**Frontmatter:**
```yaml
---
name: reviewer
model: opencode-go/glm-5.2
mode: primary
description: PR review agent that runs structural checks, LLM code/doc review, aggregates findings, and decides auto-merge/re-dispatch/block.
---
```

**Inputs (env vars):**
- `ISSUE_NUMBER` — linked issue (resolved from PR body)
- `PR_NUMBER` — the PR to review
- `REVIEW_ROUND` — current round (1, 2, 3...). Round 1 = new PR, rounds 2+ = after build agent fixes
- `LAST_REVIEWED_SHA` — SHA of last reviewed commit (for delta-only review on rounds 2+)
- `GH_TOKEN` — for gh CLI (read PR, post comments, merge, dispatch)
- `PROJECT_ID` — GitHub Projects v2 board node ID
- `GITHUB_REPOSITORY` — repo in OWNER/REPO format

**On start:**

0. **Check out the PR branch** (needed because `pull_request_target` checks out the base branch):
   ```bash
   gh pr checkout $PR_NUMBER
   ```

1. **Read the PR diff:**
   - Round 1: `gh pr diff $PR_NUMBER`
   - Rounds 2+: `git diff $LAST_REVIEWED_SHA...HEAD` (delta-only — ignore unchanged code)

2. **Classify changed files** — backend (Kotlin/Java), frontend (TS/JS), docs (Markdown), config (YAML/JSON)

3. **Run structural checks** (deterministic, fast):
   - Missing docstrings/KDoc/JSDoc on new public declarations (ast-grep)
   - README update needed when backend/frontend files changed
   - These are shell commands that produce structured findings

4. **Review code** (LLM):
   - Read full files for context (not just diff lines)
   - Check for: security issues, error handling, resource leaks, API design, test coverage
   - Focus on changed code; for rounds 2+, only review delta since `LAST_REVIEWED_SHA`

5. **Review documentation** (LLM):
   - Check README, API docs, reference docs match the code changes
   - Verify accuracy of documented endpoints, parameters, responses

6. **Aggregate all findings** into a single comment:
   ```
   ## PR Review

   | Severity | Count |
   |----------|-------|
   | :red_circle: **HIGH** | N |
   | :yellow_circle: **LOW** | M |

   ---
   ### Findings
   **1. <finding title>**
   Location: `<file>:<line>`
   Suggestion: <fix suggestion>
   ...
   ---
   <!-- SUMMARY: N HIGH, M LOW -->
   <!-- review-round: N -->
   <!-- last-reviewed-sha: <sha> -->
   ```

7. **Post the review comment** on the PR: `gh pr comment $PR_NUMBER --body "<comment>"`

**Decision logic:**

- **If 0 HIGH and 0 LOW (clean):**
  ```bash
  gh pr merge $PR_NUMBER --squash --auto --repo $GITHUB_REPOSITORY
  ```
  The `--auto` flag queues the merge for when CI checks pass. The Worker handles card movement when the merge actually happens.

- **If findings remain AND round < 3:**
  ```bash
  gh api repos/$GITHUB_REPOSITORY/dispatches \
    -f event_type=sdlc-phase \
    -f 'client_payload[agent]=build' \
    -f 'client_payload[model]=opencode-go/deepseek-v4-pro' \
    -f 'client_payload[phase]=review-fix' \
    -f "client_payload[issue_number]=$ISSUE_NUMBER" \
    -f "client_payload[pr_number]=$PR_NUMBER" \
    -f "client_payload[review_round]=$((REVIEW_ROUND + 1))" \
    -f "client_payload[last_reviewed_sha]=$CURRENT_SHA"
  ```

- **If findings remain AND round >= 3:**
  ```bash
  gh pr comment $PR_NUMBER --body "## Blocked: Review Loop Exhausted (3 rounds)..."
  bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Blocked" --repo $GITHUB_REPOSITORY
  ```

**Rules:**
- Delta-only review for rounds 2+ (ignore findings on code unchanged since `LAST_REVIEWED_SHA`)
- Never blindly revert flagged changes — fix properly or justify in the review comment
- Escalate if the same (category, location) appeared in a prior round — don't repeat the same finding
- Use `update-card-status.sh` for card moves — never raw GraphQL
- The `<!-- SUMMARY: -->` HTML comment is consumed by the reviewer agent itself on subsequent rounds to track round history

### 2. Tester Agent (`nanobyte-services/sdlc/opencode/agents/tester.md`)

**Frontmatter:**
```yaml
---
name: tester
model: opencode-go/glm-5.2
mode: primary
description: Tester agent that deploys to UAT, executes smoke test plans, and routes to Ready to Publish or Bug Fixing.
---
```

**Inputs:** `ISSUE_NUMBER`, `GH_TOKEN`, `PROJECT_ID`, `GITHUB_REPOSITORY`, `UAT_API_URL`

**On start:**

1. **Read the smoke test plan** from the issue's Plan comment:
   ```bash
   gh issue view $ISSUE_NUMBER --comments
   ```
   Extract the "### Smoke test plan" section from the Plan comment (posted by planner during Planning phase).

2. **Wait for build.yml to complete** (Docker image must be built before deploying):
   ```bash
   RUN_ID=$(gh run list --workflow=build.yml --branch=main --limit=1 --json databaseId --jq '.[0].databaseId')
   gh run watch $RUN_ID --exit-status
   ```
   If build.yml fails, treat as test failure (move to "Bug Fixing").

3. **Get the image tag:**
   ```bash
   IMAGE_TAG="main-$(git rev-parse --short HEAD)"
   ```

4. **Trigger UAT deploy:**
   ```bash
   gh workflow run deploy.yml -f environment=uat -f tag=$IMAGE_TAG
   ```

5. **Wait for deploy to complete:**
   ```bash
   # Find the deploy run that was just triggered
   sleep 5  # allow workflow to register
   DEPLOY_RUN_ID=$(gh run list --workflow=deploy.yml --limit=1 --json databaseId --jq '.[0].databaseId')
   gh run watch $DEPLOY_RUN_ID --exit-status
   ```
   If deploy fails, treat as test failure (move to "Bug Fixing").

6. **Execute smoke tests** against UAT:
   - For each smoke test in the plan, curl the UAT endpoint and verify the response
   - UAT base URL: `$UAT_API_URL` (env var configured per-repo in sdlc-agent.yml)
   - Example: `curl -fsS https://uat-api.nanobyte.ca/ready | jq -e '.status == "UP"'`
   - Record pass/fail for each smoke test

7. **If all smoke tests pass:**
   ```bash
   # Post results comment
   gh issue comment $ISSUE_NUMBER --body "## Smoke Test Results

   All N smoke tests passed against UAT ($IMAGE_TAG).

   | Test | Status |
   |------|--------|
   | GET /ready → 200 | PASS |
   | GET /health → 200 | PASS |
   ..."

   # Move card to Ready to Publish
   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Ready to Publish" --repo $GITHUB_REPOSITORY
   ```

8. **If any smoke test fails:**
   ```bash
   # Post failures comment
   gh issue comment $ISSUE_NUMBER --body "## Test Failures

   M of N smoke tests failed against UAT ($IMAGE_TAG).

   | Test | Status | Details |
   |------|--------|---------|
   | GET /ready → 200 | FAIL | Expected status UP, got DOWN |
   ..."

   # Move card to Bug Fixing
   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Bug Fixing" --repo $GITHUB_REPOSITORY
   ```

**Rules:**
- Smoke tests are read-only HTTP checks (no DB writes, no auth flows requiring browser interaction)
- If UAT deploy fails, treat as test failure — post the deploy error and move to "Bug Fixing"
- Post results as an issue comment for traceability (both pass and fail)
- Use `update-card-status.sh` for card moves

### 3. Bugfixer Agent (`nanobyte-services/sdlc/opencode/agents/bugfixer.md`)

**Frontmatter:**
```yaml
---
name: bugfixer
model: opencode-go/deepseek-v4-pro
mode: primary
description: Bug-fixer agent that reads test failures, fixes root cause, and raises a PR that re-enters the review loop.
---
```

**Inputs:** `ISSUE_NUMBER`, `GH_TOKEN`, `PROJECT_ID`, `GITHUB_REPOSITORY`

**On start:**

1. **Read the "## Test Failures" comment** on the issue:
   ```bash
   gh issue view $ISSUE_NUMBER --comments
   ```
   Extract the failing smoke tests, expected vs actual results, and any error details.

2. **Identify root cause** — read the relevant source files, trace the failure to the code

3. **Create a fix branch:**
   ```bash
   git checkout -b fix/<bug-slug>
   ```

4. **Implement the fix** — fix the root cause, not the symptom

5. **Run tests locally:**
   ```bash
   # Backend
   cd backend/portfolio && ./gradlew test
   # Frontend
   cd frontend && npm test
   ```

6. **Commit and push:**
   ```bash
   git add -A && git commit -m "fix: <description>"
   git push origin HEAD:fix/<bug-slug>
   ```

7. **Open a PR:**
   ```bash
   gh pr create --title "fix: <description>" --body "Implements #$ISSUE_NUMBER

   ## Summary
   Fixes smoke test failures:
   - <failure 1>
   - <failure 2>"
   ```
   Uses "Implements" not "Closes" — the issue must stay open for re-testing after merge.

8. **The PR enters the review flow** — reviewer agent reviews, auto-merges (or review-fix loop). After merge, the Worker moves the card to "Testing" and the tester re-runs smoke tests.

**Rules:**
- Fix the root cause, not the symptom
- If the failure is in the smoke test itself (wrong expectation), fix the test plan comment on the issue and justify in the PR
- Use `Implements #N` in PR body — never `Closes #N` (issue must stay open until "Done")
- Follow CONTRIBUTING.md constraints (MockK not Mockito, no Tailwind, apiFetch(), Vitest, etc.)
- Use `update-card-status.sh` for card moves — never raw GraphQL

### 4. Deployer Agent (`nanobyte-services/sdlc/opencode/agents/deployer.md`)

**Frontmatter:**
```yaml
---
name: deployer
model: opencode-go/glm-5.2
mode: primary
description: Deployer agent that deploys to production, verifies health checks, and closes the issue.
---
```

**Inputs:** `ISSUE_NUMBER`, `GH_TOKEN`, `PROJECT_ID`, `GITHUB_REPOSITORY`

**On start:**

1. **Get the image tag:**
   ```bash
   IMAGE_TAG="main-$(git rev-parse --short HEAD)"
   ```

2. **Trigger prod deploy:**
   ```bash
   gh workflow run deploy.yml -f environment=prod -f tag=$IMAGE_TAG
   ```

3. **Wait for deploy to complete:**
   ```bash
   sleep 5
   DEPLOY_RUN_ID=$(gh run list --workflow=deploy.yml --limit=1 --json databaseId --jq '.[0].databaseId')
   gh run watch $DEPLOY_RUN_ID --exit-status
   ```

4. **Verify health checks:**
   ```bash
   curl -fsS https://api.nanobyte.ca/health | jq -e '.status == "UP"'
   curl -fsS https://api.nanobyte.ca/ready | jq -e '.status == "UP"'
   ```

5. **If deploy + health checks pass:**
   ```bash
   # Post success comment
   gh issue comment $ISSUE_NUMBER --body "## Deployed to Production

   Image: $IMAGE_TAG
   Health checks: PASS
   URL: https://api.nanobyte.ca"

   # Move card to Done
   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Done" --repo $GITHUB_REPOSITORY

   # Close the issue (terminal state)
   gh issue close $ISSUE_NUMBER --repo $GITHUB_REPOSITORY
   ```

6. **If deploy fails or health checks fail:**
   ```bash
   # Post failure comment
   gh issue comment $ISSUE_NUMBER --body "## Deploy Failed

   Image: $IMAGE_TAG
   Error: <details>"

   # Move card to Blocked
   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Blocked" --repo $GITHUB_REPOSITORY
   ```

**Rules:**
- Only deploy to prod after human approval (card was moved to "Publishing" manually — this is the safety gate)
- Verify health checks before moving to "Done"
- Close the issue when moving to "Done" — this is the terminal state
- Use `update-card-status.sh` for card moves

### 5. Planner Agent Changes (`nanobyte-services/sdlc/opencode/agents/planner.md`)

During the **Planning** phase, the planner adds two new sections to the Plan comment on each child issue:

**Updated Plan comment format:**
```
## Module Plan: <module name>

### Acceptance criteria
- [ ] <criterion 1>

### Technical approach
- Files to change: <paths>
- Tech: <frameworks/libraries>

### Tasks
- [ ] <task 1>
- [ ] <task 2>

### CI test plan
- [ ] Unit: <test description> — <file path>
- [ ] Integration: <test description> — <file path>

### Smoke test plan
- [ ] <smoke test 1>: GET /endpoint → expect 200 + <response shape>
- [ ] <smoke test 2>: POST /endpoint with <body> → expect 201 + <response shape>

### Dependencies
Depends on: #NNN  (or "Parallel: yes")
```

**CI test plan:**
- Based on the technical approach, specifies what unit and integration tests the build agent should write
- These tests run in `build.yml` on PR/push (CI tests)
- Must cover each acceptance criterion

**Smoke test plan:**
- Based on the acceptance criteria, specifies what HTTP smoke tests the tester agent should run against UAT
- Read-only checks (curl endpoints, verify response shape/status)
- Must verify each acceptance criterion end-to-end
- HTTP-only (no DB writes, no auth flows requiring browser interaction)

**Rules added:**
- CI test plan must cover each acceptance criterion
- Smoke test plan must verify each acceptance criterion end-to-end
- Smoke tests are HTTP-only

### 6. Build Agent Changes (`nanobyte-services/sdlc/opencode/agents/build.md`)

**Change 1: PR body keyword "Closes" to "Implements"**

Current:
```bash
gh pr create --title "<title>" --body "Closes #$ISSUE_NUMBER"
```

Changed to:
```bash
gh pr create --title "<title>" --body "Implements #$ISSUE_NUMBER

## Summary
<brief description of what was implemented>"
```

The issue must stay open through Testing, Bug Fixing, and Publishing. `Closes #N` would auto-close the issue on merge, breaking card movement for subsequent phases. `Implements #N` creates a reference without auto-closing. The deployer closes the issue when moving to "Done".

**Change 2: Implement CI tests from the plan**

Updated Executing phase, step 3:
```
3. Implement each checkbox task from the plan, INCLUDING the CI test plan tasks
   - Write unit tests and integration tests as specified in the "### CI test plan" section
   - These tests run in build.yml on PR/push
```

The build agent already runs tests before pushing (step 4). The new CI tests are included in that run.

### 7. Worker Changes (`nanobyte-services/sdlc/worker/src/index.ts`)

**Change 1: Add `pull_request` event handling**

Add a new handler for `pull_request: [closed]` with `merged: true`:

```typescript
// Handle pull_request closed events (post-merge card movement)
if (eventType === 'pull_request' && action === 'closed' && body.pull_request?.merged) {
  return handlePullRequestMerged(body, env);
}
```

`handlePullRequestMerged` function:
1. Extract PR body from `body.pull_request.body`
2. Find linked issue number via regex: `(?:Implements|Closes|Resolves|Fixes) #(\d+)`
3. Get repo from `body.repository.full_name`
4. Move the issue's card to "Testing" via GraphQL (same mutation as `update-card-status.sh`):
   - Query project items for the issue
   - Find the Status field
   - Find the "Testing" option ID
   - Call `updateProjectV2ItemFieldValue` mutation
5. Sync labels via existing `syncIssueLabels` function (adds "Testing" label, removes old SDLC labels)

This centralizes post-merge card movement in the Worker (shared), not in a repo-specific workflow.

**Change 2: Add "Done" to `SDLC_LANE_LABELS`**

Current:
```typescript
const SDLC_LANE_LABELS = [
  'Backlog', 'Triaging', 'Scope Review', 'Planning', 'Plan Review',
  'Executing', 'In Review', 'Blocked', 'Testing', 'Bug Fixing',
  'Ready to Publish', 'Publishing',
];
```

Changed to:
```typescript
const SDLC_LANE_LABELS = [
  'Backlog', 'Triaging', 'Scope Review', 'Planning', 'Plan Review',
  'Executing', 'In Review', 'Blocked', 'Testing', 'Bug Fixing',
  'Ready to Publish', 'Publishing', 'Done',
];
```

Without "Done" in the array, the Worker would log "unknown Status" and not sync labels when a card reaches "Done". While "Done" is terminal (no dispatch needed), label sync is still useful for board consistency.

### 8. sdlc-agent.yml Changes (`pc/.github/workflows/sdlc-agent.yml`)

**Add `pull_request_target` trigger:**

```yaml
on:
  issues:
    types: [labeled]
  pull_request_target:
    types: [opened, synchronize]
  repository_dispatch:
    types: [sdlc-phase]
  workflow_dispatch:
    ...
```

**Update the `if` guard:**

```yaml
if: |
  (github.event_name == 'workflow_dispatch') ||
  (github.event_name == 'issues' && contains(fromJson('["Triaging","Planning","Executing","Testing","Bug Fixing","Publishing","agent-ready"]'), github.event.label.name)) ||
  github.event_name == 'repository_dispatch' ||
  github.event_name == 'pull_request_target'
```

**Add to the "Determine agent" step:**

```bash
if [ "${{ github.event_name }}" = "pull_request_target" ]; then
  PR_NUMBER=${{ github.event.pull_request.number }}
  echo "agent=reviewer" >> $GITHUB_OUTPUT
  echo "model=opencode-go/glm-5.2" >> $GITHUB_OUTPUT
  echo "phase=review" >> $GITHUB_OUTPUT
  echo "pr_number=$PR_NUMBER" >> $GITHUB_OUTPUT
  # Resolve ISSUE_NUMBER from PR body
  PR_BODY="${{ github.event.pull_request.body }}"
  ISSUE_NUM=$(echo "$PR_BODY" | grep -oP '(?:Implements|Closes|Resolves|Fixes) #\K[0-9]+' | head -1 || echo "0")
  echo "issue_number=$ISSUE_NUM" >> $GITHUB_OUTPUT
fi
```

**Review round tracking:** The reviewer agent reads the prior round from PR comments (`<!-- review-round: N -->`). For new PRs (`opened`), no prior comment exists → round 1. For synchronized PRs (`synchronize`), the agent reads the last review comment to determine the current round.

**Additional env var:** `UAT_API_URL` — the UAT endpoint base URL, passed to the tester agent. Configured as a repo secret or variable in sdlc-agent.yml:
```yaml
env:
  ...
  UAT_API_URL: ${{ vars.UAT_API_URL }}
```

**Security note:** `pull_request_target` runs the workflow with the base branch's permissions and secrets. This is safe because the reviewer agent only reads the PR and posts comments — it does not execute untrusted code from the PR. The `gh pr checkout` command fetches the PR code for reading, but the agent does not run the PR's code.

### 9. Files Deleted from `pc` Repo

- `.github/workflows/pr-review.yml` — replaced by reviewer agent
- `.github/workflows/review-loop.yml` — replaced by reviewer agent
- `scripts/aggregate.sh` — logic absorbed into reviewer agent
- `scripts/post-comment.sh` — logic absorbed into reviewer agent

## Reusability

A new repo opts into the SDLC pipeline by adding:
1. `sdlc-agent.yml` — dispatches agents (triggers on `issues: [labeled]`, `pull_request_target`, `repository_dispatch`, `workflow_dispatch`)
2. `build.yml` — repo-specific CI tests + Docker image build
3. `deploy.yml` — repo-specific deploy via Vault + SSH
4. GitHub secrets: `GH_PROJECT_TOKEN`, `SDLC_PROJECT_ID`, `NANOBYTE_SERVICES_TOKEN`, `OPENCODE_AUTH_JSON`, `VAULT_ROLE_ID`, `VAULT_SECRET_ID`

All agents, the Worker, and shared scripts come from `nanobyte-services` — cloned by `sdlc-agent.yml` at runtime. No repo-specific review workflows needed.

## Error Handling

| Scenario | Behavior |
|---|---|
| Reviewer agent crashes | PR stays in review, no merge. Human can re-trigger via `workflow_dispatch` |
| build.yml CI tests fail after merge | Card is in "Testing". Tester agent detects build failure, moves card to "Bug Fixing" |
| UAT deploy fails | Tester agent posts deploy error, moves card to "Bug Fixing" |
| Smoke tests fail | Tester posts failures, moves card to "Bug Fixing". Bugfixer creates fix PR → review loop → merge → re-test |
| Bug Fixing loop exceeds 3 review rounds | Reviewer agent moves card to "Blocked", posts "Review Loop Exhausted" comment |
| Prod deploy fails | Deployer posts error, moves card to "Blocked" |
| Health checks fail after prod deploy | Deployer posts error, moves card to "Blocked" |
| Worker can't find linked issue from PR body | Logs error, no card movement. Human can manually move card |
| Worker can't resolve project item | Logs error, no card movement. Human can manually move card |

## Testing Strategy

- **Reviewer agent**: Test with a sample PR containing known issues (missing docstring, README not updated). Verify findings are posted, decision logic works (clean → merge, findings → re-dispatch, 3 rounds → block).
- **Tester agent**: Test with a known-good UAT deployment. Verify smoke tests pass and card moves to "Ready to Publish". Test with a broken deployment to verify card moves to "Bug Fixing".
- **Bugfixer agent**: Test with a known test failure. Verify fix PR is created with "Implements #N" and enters the review loop.
- **Deployer agent**: Test with a known-good prod deployment. Verify health checks pass and card moves to "Done" + issue closed.
- **Worker pull_request handler**: Test with a merged PR containing "Implements #N". Verify card moves to "Testing" and labels sync.
- **End-to-end**: Move a card through the full pipeline from Triaging to Done, verifying each transition.
