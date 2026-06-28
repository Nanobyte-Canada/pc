# Autonomous SDLC Pipeline — Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close all gaps between the original SDLC pipeline design and the actual implementation, making the planner → build → review loop pipeline fully operational end-to-end across both repos.

**Architecture (actual — label-sync):** GitHub Projects v2 card move → `projects_v2_item` webhook → Cloudflare Worker (HMAC verify → KV dedupe → GraphQL resolve item → sync issue labels) → issue gains lane-name label → `issues:[labeled]` webhook → consumer repo `sdlc-agent.yml` → `opencode run --agent`. The review loop uses `repository_dispatch` directly (fired by `review-loop.yml`, not the Worker).

**Tech Stack:** GitHub Actions (self-hosted runner), Cloudflare Workers (TypeScript, Vitest), opencode CLI (`opencode run --agent --model --format json --dangerously-skip-permissions`), GitHub Projects v2 (GraphQL API), `gh` CLI, Bash shell scripts.

**Spec:** `docs/superpowers/specs/2026-06-28-autonomous-sdlc-pipeline-completion-design.md`

## Global Constraints

- **Two repos:** `nanobyte-services` (`Nanobyte-Canada/nanobyte-services`, path `/home/sbilakhia/Documents/dev/repos/nanobyte-services`) and `pc` (`Nanobyte-Canada/pc`, path `/home/sbilakhia/Documents/dev/repos/pc`). Each task specifies which repo.
- **Branch strategy:** feature branch off `main` → PR to `main`. Never commit directly to `main`.
- **Shell scripts:** `set -euo pipefail`, POSIX-compatible where possible.
- **Worker tests:** `cd sdlc/worker && npx vitest run` — must pass before commit.
- **No new dependencies:** use existing `gh` CLI, `jq`, `vitest`, Web Crypto API.
- **Agent format:** markdown with frontmatter `name`, `model`, `mode: subagent`, `description` + instructions.
- **PR target:** PRs go to `main` in each repo. Use `gh pr create --base main`.
- **Prerequisite:** PR [#1 in nanobyte-services](https://github.com/Nanobyte-Canada/nanobyte-services/pull/1) (Worker GitHub API compliance + lane-label routing) should be merged to `main` before starting Task 2.

---

## File Structure

### `nanobyte-services` repo

| File | Action | Responsibility |
|------|--------|----------------|
| `sdlc/worker/src/dispatch.ts` | **Delete** | Obsolete — `fireDispatch` no longer called by `index.ts` |
| `sdlc/worker/src/routing.ts` | **Delete** | Dead code — routing now lives in consumer repo's `sdlc-agent.yml` |
| `sdlc/worker/test/routing.test.ts` | **Delete** | Tests dead code |
| `sdlc/README.md` | **Modify** | Update architecture diagram + deploy steps for label-sync |
| `sdlc/opencode/agents/build.md` | **Modify** | Add PR checkout + `LAST_REVIEWED_SHA` to review-fix phase |
| `sdlc/tests/test-dispatch.sh` | **Modify** | Update description to clarify it tests review-loop dispatch path |

### `pc` repo

| File | Action | Responsibility |
|------|--------|----------------|
| `.github/workflows/pr-review.yml` | **Modify** | Add delta-only review + fix HEAD_SHA/REVIEW_ROUND env vars |
| `.github/workflows/review-loop.yml` | **Modify** | Add Blocked card move + fix issue_number=0 |

---

## Phase 1 — Infrastructure Verification + Setup

> These tasks verify each piece of Stage 3 infrastructure. Each step checks if the piece exists and runs setup only if missing. Tasks are sequential (some depend on earlier outputs like Project ID).

### Task 1: Verify GitHub App + Cloudflare Worker + self-hosted runner

**Files:**
- No files created or modified — verification only

**Interfaces:**
- Produces: confirmation that the GitHub App, Worker, and runner are operational (or setup tasks identified)

- [ ] **Step 1: Check for the SDLC GitHub App**

```bash
# List app installations accessible to the authenticated user
gh api /user/installations --jq '.installations[] | select(.app_slug | test("sdlc|SDLC|orchestrat"; "i")) | {app_slug, id, app_url}' 2>/dev/null || echo "NO_APP_FOUND"
```

If output is `NO_APP_FOUND` or empty, the GitHub App needs to be created. Follow the manual steps from the original plan (Task 9 Step 1 in `2026-06-22-autonomous-sdlc-pipeline.md`):
1. Go to https://github.com/settings/apps (or org settings → GitHub Apps)
2. Click "New GitHub App"
3. Set: name `SDLC Orchestrator`, homepage URL `https://github.com/Nanobyte-Canada/nanobyte-services`, webhook URL = Worker URL (from Step 2), webhook secret = a strong random string
4. Repository permissions: Issues RW, Pull requests RW, Contents RW, Actions RW, Metadata R
5. Organization permissions: Projects RW
6. Subscribe to events: `issues`, `issue_comment`, `pull_request`, `projects_v2_item`
7. Create the App, generate a private key, install on the org

Record: App ID, private key path, webhook secret (needed for Worker deployment in Step 2).

- [ ] **Step 2: Check if the Cloudflare Worker is deployed**

```bash
# Replace <your-subdomain> with your Cloudflare Workers subdomain
WORKER_URL="https://sdlc-webhook-receiver.<your-subdomain>.workers.dev"
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$WORKER_URL" 2>/dev/null || echo "000")
echo "Worker HTTP status: $HTTP_STATUS"
```

If `HTTP_STATUS` is `000` or `404`, the Worker is not deployed. Deploy it:

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services/sdlc/worker
npm install
npx wrangler kv namespace create DELIVERY_KV
# Update wrangler.toml with the KV namespace ID from the output
npx wrangler secret put WEBHOOK_SECRET    # Enter the GitHub App webhook secret from Step 1
npx wrangler secret put GITHUB_TOKEN      # Enter a GitHub PAT with repo + project scopes
npx wrangler deploy
```

Expected: `Published sdlc-webhook-receiver (x.xx sec)` with the Worker URL.

If `HTTP_STATUS` is `405` (Method Not Allowed), the Worker is deployed and responding correctly (it only accepts POST).

- [ ] **Step 3: Check if the self-hosted runner is online**

```bash
gh api orgs/Nanobyte-Canada/actions/runners --jq '.runners[] | select(.labels[].name == "self-hosted") | {name, status, busy}' 2>/dev/null || echo "NO_RUNNER_FOUND"
```

If output is `NO_RUNNER_FOUND` or empty, register a runner. On the home Debian server:

```bash
# Get a registration token (run locally with gh CLI authenticated)
gh api -X POST orgs/Nanobyte-Canada/actions/runners/registration-token --jq '.token'

# On the home server:
cd /tmp
git clone --depth 1 https://github.com/Nanobyte-Canada/nanobyte-services.git
cd nanobyte-services/sdlc/runner
chmod +x setup.sh
./setup.sh --token <TOKEN_FROM_ABOVE> --org Nanobyte-Canada
```

Expected: `{"name": "home-runner", "status": "online", "busy": false}`

- [ ] **Step 4: Record infrastructure status**

Write down the status of each piece:
- GitHub App: ✅/❌ (App ID: ___)
- Cloudflare Worker: ✅/❌ (URL: ___)
- Self-hosted runner: ✅/❌ (name: ___)

If any piece is missing and cannot be set up now, note it as a blocker for Phase 4 (e2e validation) but continue with Phase 2 (code fixes).

---

### Task 2: Verify Projects v2 board + secrets

**Files:**
- No files created or modified — verification only

**Interfaces:**
- Produces: `SDLC_PROJECT_ID` (the Projects v2 board node ID, needed for card moves)
- Produces: confirmation that all secrets are set in the `pc` repo

- [ ] **Step 1: Check for the Projects v2 board**

```bash
# Check user-level projects
gh api graphql -f query='{ user(login: "Nanobyte-Canada") { projectsV2(first: 10) { nodes { title id } } } }' --jq '.data.user.projectsV2.nodes[] | select(.title | test("SDLC|Pipeline"; "i")) | {title, id}' 2>/dev/null || echo "NO_USER_PROJECT"

# Check org-level projects
gh api graphql -f query='{ organization(login: "Nanobyte-Canada") { projectsV2(first: 10) { nodes { title id } } } }' --jq '.data.organization.projectsV2.nodes[] | select(.title | test("SDLC|Pipeline"; "i")) | {title, id}' 2>/dev/null || echo "NO_ORG_PROJECT"
```

If no board is found, create it:

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
chmod +x sdlc/scripts/create-board.sh
./sdlc/scripts/create-board.sh --owner Nanobyte-Canada --title "SDLC Pipeline"
```

Expected: `Board created successfully. Project ID: PVT_xxxxxxxx`

Record the Project ID — this becomes the `SDLC_PROJECT_ID` secret.

- [ ] **Step 2: Verify the board has 13 lanes + 5 custom fields**

```bash
# Replace PROJECT_ID with the ID from Step 1
PROJECT_ID="PVT_xxxxxxxx"
gh api graphql -f query="
query {
  node(id: \"$PROJECT_ID\") {
    ... on ProjectV2 {
      fields(first: 20) {
        nodes {
          ... on ProjectV2SingleSelectField {
            name
            options { name }
          }
        }
      }
    }
  }
}" --jq '.data.node.fields.nodes[] | select(.name == "Status") | .options[].name'
```

Expected: 13 lane names — `Backlog`, `Triaging`, `Scope Review`, `Planning`, `Plan Review`, `Executing`, `In Review`, `Blocked`, `Testing`, `Bug Fixing`, `Ready to Publish`, `Publishing`, `Done`.

If lanes are missing, re-run `create-board.sh` or manually add the missing lanes via the Projects UI.

- [ ] **Step 3: Check secrets in the pc repo**

```bash
gh secret list --repo Nanobyte-Canada/pc
```

Expected: at minimum `OPENCODE_AUTH_JSON`, `NANOBYTE_SERVICES_TOKEN`, `SDLC_PROJECT_ID` should be listed.

- [ ] **Step 4: Set any missing secrets**

For `OPENCODE_AUTH_JSON` (opencode-go API key from opencode.ai/auth):

```bash
echo '{"opencode-go":{"type":"api","key":"YOUR_API_KEY"}}' | gh secret set OPENCODE_AUTH_JSON --repo Nanobyte-Canada/pc
```

For `NANOBYTE_SERVICES_TOKEN` (a GitHub PAT with `repo` scope on `Nanobyte-Canada/nanobyte-services` — needed because the repo is private and `sdlc-agent.yml` clones it at job start):

```bash
echo "YOUR_GITHUB_PAT_WITH_REPO_SCOPE" | gh secret set NANOBYTE_SERVICES_TOKEN --repo Nanobyte-Canada/pc
```

For `SDLC_PROJECT_ID` (the Projects v2 board node ID from Step 1):

```bash
echo "PVT_xxxxxxxx" | gh secret set SDLC_PROJECT_ID --repo Nanobyte-Canada/pc
```

Expected: `✓ Set Actions secret <NAME> for Nanobyte-Canada/pc` for each.

- [ ] **Step 5: Record infrastructure status**

Write down:
- Projects v2 board: ✅/❌ (Project ID: ___)
- Secrets: OPENCODE_AUTH_JSON ✅/❌, NANOBYTE_SERVICES_TOKEN ✅/❌, SDLC_PROJECT_ID ✅/❌

---

## Phase 2 — Code Gap Fixes (Parallel)

> **Lane A** (nanobyte-services) and **Lane B** (pc) can run in parallel — different repos, no write conflicts.
> Within each lane, tasks are sequential where noted.

### Lane A — nanobyte-services

---

### Task 3: Remove dead Worker code

**Files:**
- Delete: `sdlc/worker/src/dispatch.ts`
- Delete: `sdlc/worker/src/routing.ts`
- Delete: `sdlc/worker/test/routing.test.ts`

**Interfaces:**
- No consumers — `index.ts` only imports `verifySignature` from `verify.ts` and `isDuplicate` from `dedupe.ts`.
- `routing.yml` stays (documentation of lane → agent → model mapping).

- [ ] **Step 1: Verify no imports of dispatch.ts or routing.ts in Worker src**

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
rg "from './dispatch'" sdlc/worker/src/
rg "from './routing'" sdlc/worker/src/
```

Expected: no matches. `index.ts` should only import from `./verify` and `./dedupe`.

- [ ] **Step 2: Create a feature branch**

```bash
git checkout main && git pull
git checkout -b chore/remove-dead-worker-code
```

- [ ] **Step 3: Delete the three files**

```bash
git rm sdlc/worker/src/dispatch.ts sdlc/worker/src/routing.ts sdlc/worker/test/routing.test.ts
```

- [ ] **Step 4: Run remaining Worker tests**

```bash
cd sdlc/worker
npx vitest run
```

Expected: PASS — 2 test files, 7 tests total:
```
 ✓ test/dedupe.test.ts (3 tests)
 ✓ test/verify.test.ts (4 tests)

 Test Files  2 passed (2)
      Tests  7 passed (7)
```

No `routing.test.ts` should appear.

- [ ] **Step 5: Commit and push**

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
git commit -m "chore: remove dead Worker code (dispatch.ts, routing.ts, routing.test.ts)

The label-sync architecture removed the only callers of fireDispatch
and routeEvent. index.ts now only imports verifySignature + isDuplicate.
routing.yml stays as documentation of the lane → agent → model mapping."
git push -u origin chore/remove-dead-worker-code
```

- [ ] **Step 6: Create PR**

```bash
gh pr create --title "chore: remove dead Worker code" --body "Removes \`dispatch.ts\`, \`routing.ts\`, and \`routing.test.ts\` — dead code after the label-sync switch. \`index.ts\` only imports \`verifySignature\` + \`isDuplicate\`. Worker tests: 7 passed (verify + dedupe)." --base main
```

Expected: PR URL returned. Do not merge yet — merge all Lane A PRs together in Phase 3.

---

### Task 4: Update README.md for label-sync architecture

**Files:**
- Modify: `sdlc/README.md`

**Interfaces:**
- No code interfaces — documentation only.

- [ ] **Step 1: Create a feature branch**

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
git checkout main && git pull
git checkout -b docs/update-readme-label-sync
```

- [ ] **Step 2: Update the architecture diagram**

In `sdlc/README.md`, find the architecture block (lines 7-21):

```
## Architecture

```
GitHub Projects v2 board (swim lanes)
  │ projects_v2_item webhook (card moved)
  ▼
GitHub App → Cloudflare Worker (HMAC verify + dedupe + route)
  │ repository_dispatch
  ▼
Self-hosted GitHub Actions runner (home server)
  │ opencode run --agent <phase-agent> --model <phase-model>
  ▼
Agent does work → raises PR → existing build.yml + pr-review.yml run
  │ autonomous review loop (max 3 rounds → Blocked)
  ▼
Auto-merge → card moves to next phase
```
```

Replace with:

````markdown
## Architecture

```
GitHub Projects v2 board (swim lanes)
  │ projects_v2_item webhook (card moved)
  ▼
GitHub App → Cloudflare Worker (HMAC verify + dedupe + GraphQL resolve + sync labels)
  │ issue gains lane-name label (e.g., "Triaging")
  ▼
issues:[labeled] webhook → consumer repo sdlc-agent.yml
  │ opencode run --agent <phase-agent> --model <phase-model>
  ▼
Agent does work → raises PR → existing build.yml + pr-review.yml run
  │ autonomous review loop (max 3 rounds → Blocked)
  ▼
Auto-merge → card moves to next phase
```
````

- [ ] **Step 3: Update the Worker deploy step**

Find step 2 (lines 33-43):

```markdown
### 2. Deploy the Cloudflare Worker

```bash
cd sdlc/worker
npm install
npx wrangler kv namespace create DELIVERY_KV
# Update wrangler.toml with the KV namespace ID
npx wrangler secret put WEBHOOK_SECRET    # GitHub App webhook secret
npx wrangler secret put GITHUB_TOKEN      # GitHub App installation token or PAT
npx wrangler deploy --var ROUTING_CONFIG:"$(cat ../routing.yml)"
```
```

Replace with:

```markdown
### 2. Deploy the Cloudflare Worker

```bash
cd sdlc/worker
npm install
npx wrangler kv namespace create DELIVERY_KV
# Update wrangler.toml with the KV namespace ID
npx wrangler secret put WEBHOOK_SECRET    # GitHub App webhook secret
npx wrangler secret put GITHUB_TOKEN      # PAT with repo + project scopes
npx wrangler deploy
```

The Worker syncs issue labels to match the board's Status field. No routing config is deployed — lane → agent → model routing lives in each consumer repo's `sdlc-agent.yml`.
```

- [ ] **Step 4: Update the Files table**

Find the Files table (lines 102-111):

```markdown
## Files

| Path | Purpose |
|------|---------|
| `opencode/` | Shared opencode config (agents, skills, opencode.json) |
| `routing.yml` | Lane → phase → agent → model mapping |
| `worker/` | Cloudflare Worker (webhook receiver) |
| `runner/` | Self-hosted runner setup scripts |
| `scripts/` | Board creation + utility scripts |
| `tests/` | Integration tests |
```

Replace with:

```markdown
## Files

| Path | Purpose |
|------|---------|
| `opencode/` | Shared opencode config (agents, skills, opencode.json) |
| `routing.yml` | Lane → phase → agent → model mapping (documentation — routing is implemented in consumer repo workflows) |
| `worker/` | Cloudflare Worker (webhook receiver: verify → dedupe → resolve → sync labels) |
| `runner/` | Self-hosted runner setup scripts |
| `scripts/` | Board creation + card-status update scripts |
| `tests/` | Integration tests |
```

- [ ] **Step 5: Update the Staged rollout section**

Find the staged rollout section (lines 96-100):

```markdown
## Staged rollout

- **Stage 1:** Label an issue `agent-ready` to trigger the planner (no App/Worker/runner needed)
- **Stage 2:** Register a self-hosted runner (eliminates 6h limit + minute billing)
- **Stage 3:** Full control plane (GitHub App + Worker + Projects board)
```

Replace with:

```markdown
## Staged rollout

- **Stage 1:** Label an issue `agent-ready` to trigger the planner (no App/Worker/runner needed)
- **Stage 2:** Register a self-hosted runner (eliminates 6h limit + minute billing)
- **Stage 3:** Full control plane (GitHub App + Worker + Projects board). Card moves sync lane-name labels to issues, which trigger the consumer repo's agent workflow.
```

- [ ] **Step 6: Commit and push**

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
git add sdlc/README.md
git commit -m "docs: update README for label-sync architecture

Replace repository_dispatch diagram with label-sync flow. Remove
obsolete ROUTING_CONFIG deploy flag. Update Files table to reflect
routing.yml as documentation-only and add card-status script."
git push -u origin docs/update-readme-label-sync
```

- [ ] **Step 7: Create PR**

```bash
gh pr create --title "docs: update README for label-sync architecture" --body "Updates architecture diagram, deploy steps, and file table to reflect the label-sync architecture. Removes obsolete \`ROUTING_CONFIG\` deploy flag." --base main
```

Expected: PR URL returned. Do not merge yet.

---

### Task 5: Enhance build agent review-fix phase

**Files:**
- Modify: `sdlc/opencode/agents/build.md:12-39` (Inputs section + review-fix phase)

**Interfaces:**
- Consumes: `PR_NUMBER`, `LAST_REVIEWED_SHA`, `REVIEW_ROUND` env vars (passed by `sdlc-agent.yml` lines 138-140)
- Produces: build agent that correctly checks out the PR branch and focuses on delta findings

- [ ] **Step 1: Create a feature branch**

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
git checkout main && git pull
git checkout -b fix/build-agent-review-fix-enhancements
```

- [ ] **Step 2: Add LAST_REVIEWED_SHA to the Inputs section**

In `sdlc/opencode/agents/build.md`, find line 16:

```markdown
- REVIEW_ROUND: (review-fix only) current round number
```

After it, add:

```markdown
- LAST_REVIEWED_SHA: (review-fix only) the commit SHA that was last reviewed — use this to focus on delta changes only
```

- [ ] **Step 3: Replace the review-fix phase section**

Find the "If PHASE == review-fix" section (lines 32-39):

```markdown
## If PHASE == "review-fix"
1. Read the PR's review findings comment: `gh pr view $PR_NUMBER --comments`
2. Parse the last-reviewed-sha from the hidden HTML comment
3. For each finding:
   - RULE 1: NEVER blindly revert a flagged change — fix it properly OR add a comment justifying the approach and leave the change
   - RULE 2: If the same (category, location) appeared in the previous round, escalate — don't repeat the same fix
4. Push commits to the PR branch
5. The PR push will re-trigger build.yml + pr-review.yml (delta-only review)
```

Replace with:

```markdown
## If PHASE == "review-fix"
1. Read the PR's review findings comment: `gh pr view $PR_NUMBER --comments`
2. Check out the PR branch: `gh pr checkout $PR_NUMBER`
3. The `LAST_REVIEWED_SHA` env var contains the SHA of the last reviewed commit.
   Use `git diff $LAST_REVIEWED_SHA...HEAD` to see what changed since the last review.
4. For each finding in the review comment:
   - RULE 1: NEVER blindly revert a flagged change — fix it properly OR add a comment justifying the approach and leave the change
   - RULE 2: If the same (category, location) appeared in a previous round (check `REVIEW_ROUND` env var), escalate — don't repeat the same fix
   - Focus on findings that apply to code changed since `LAST_REVIEWED_SHA` — findings on unchanged code were accepted in a prior round
5. Run tests (Docker for backend, npm for frontend)
6. Commit and push to the PR branch: `git push origin HEAD:$(git branch --show-current)`
7. The PR push will re-trigger build.yml + pr-review.yml (delta-only review for rounds 2+)
```

- [ ] **Step 4: Commit and push**

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
git add sdlc/opencode/agents/build.md
git commit -m "fix: build agent review-fix phase — add PR checkout + LAST_REVIEWED_SHA

The review-fix phase was missing:
- gh pr checkout (agent had no explicit instruction to check out the PR branch)
- LAST_REVIEWED_SHA usage (agent should focus on delta since last review)
- Explicit push instruction (push to the PR branch, not main)
- Clarification that findings on unchanged code were accepted in prior rounds"
git push -u origin fix/build-agent-review-fix-enhancements
```

- [ ] **Step 5: Create PR**

```bash
gh pr create --title "fix: build agent review-fix phase enhancements" --body "Adds PR checkout (\`gh pr checkout\`), \`LAST_REVIEWED_SHA\` delta awareness, explicit push instructions, and clarification about findings on unchanged code to the build agent's review-fix phase." --base main
```

Expected: PR URL returned. Do not merge yet.

---

### Task 6: Update test-dispatch.sh description

**Files:**
- Modify: `sdlc/tests/test-dispatch.sh:1-8` (header comment only)

- [ ] **Step 1: Create a feature branch**

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
git checkout main && git pull
git checkout -b docs/clarify-test-dispatch-description
```

- [ ] **Step 2: Update the header comment**

In `sdlc/tests/test-dispatch.sh`, find the header comment (lines 1-8):

```bash
#!/bin/bash
set -euo pipefail

# Integration test: verify repository_dispatch fires a workflow in a test repo.
# Requires: GH_TOKEN env var, a test repo with the sdlc-agent.yml workflow.
# Usage: ./test-dispatch.sh --repo <OWNER/TEST-REPO>
```

Replace with:

```bash
#!/bin/bash
set -euo pipefail

# Integration test: verify repository_dispatch fires the sdlc-agent.yml workflow.
# This tests the REVIEW LOOP dispatch path (review-loop.yml fires repository_dispatch
# to re-trigger the build agent for review-fix rounds), NOT the Worker path (which
# uses label-sync to sync issue labels, not repository_dispatch).
# Requires: GH_TOKEN env var, a test repo with the sdlc-agent.yml workflow.
# Usage: ./test-dispatch.sh --repo <OWNER/TEST-REPO>
```

- [ ] **Step 3: Commit and push**

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
git add sdlc/tests/test-dispatch.sh
git commit -m "docs: clarify test-dispatch.sh tests review-loop dispatch, not Worker

The Worker now uses label-sync (not repository_dispatch). This test
still validates the review-loop's repository_dispatch path, which
re-triggers the build agent for review-fix rounds."
git push -u origin docs/clarify-test-dispatch-description
```

- [ ] **Step 4: Create PR**

```bash
gh pr create --title "docs: clarify test-dispatch.sh description" --body "Updates the header comment to clarify this tests the review-loop dispatch path, not the Worker label-sync path." --base main
```

Expected: PR URL returned. Do not merge yet.

---

### Lane B — PC repo

---

### Task 7: Fix pr-review.yml — review state tracking + delta-only review

> **This is the highest-priority task.** Three bugs in `pr-review.yml` break the review loop's state tracking. Without these fixes, the review loop can never track rounds or SHAs, and delta-only review is impossible.

**Files:**
- Modify: `pc/.github/workflows/pr-review.yml:80-96` (Generate PR diff step)
- Modify: `pc/.github/workflows/pr-review.yml:120-123` (Aggregator step)

**Interfaces:**
- Consumes: `aggregate.sh` reads `HEAD_SHA` and `REVIEW_ROUND` env vars (lines 167-168 of aggregate.sh)
- Produces: correct `<!-- review-round: N -->` and `<!-- last-reviewed-sha: <SHA> -->` hidden HTML comments in review output

- [ ] **Step 1: Create a feature branch**

```bash
cd /home/sbilakhia/Documents/dev/repos/pc
git checkout main && git pull
git checkout -b fix/review-loop-state-tracking
```

- [ ] **Step 2: Replace the "Generate PR diff" step**

In `pc/.github/workflows/pr-review.yml`, find the "Generate PR diff" step (lines 80-96):

```yaml
      - name: Generate PR diff
        if: steps.classify.outputs.skip_review != 'true'
        run: |
          mkdir -p /tmp/review
          BASE_SHA="${{ github.event.pull_request.base.sha }}"
          HEAD_SHA="${{ github.event.pull_request.head.sha }}"
          MAX_SIZE=${{ inputs.max_diff_bytes }}

          git diff "$BASE_SHA...$HEAD_SHA" > /tmp/review/pr_diff.diff

          FULL_SIZE=$(wc -c < /tmp/review/pr_diff.diff)
          if [ "$FULL_SIZE" -gt "$MAX_SIZE" ]; then
            head -c "$MAX_SIZE" /tmp/review/pr_diff.diff > /tmp/review/pr_diff_truncated.diff
            echo "" >> /tmp/review/pr_diff_truncated.diff
            echo "... (diff truncated at ${MAX_SIZE} bytes from ${FULL_SIZE} bytes)" >> /tmp/review/pr_diff_truncated.diff
            mv /tmp/review/pr_diff_truncated.diff /tmp/review/pr_diff.diff
          fi
```

Replace with:

```yaml
      - name: Generate PR diff
        id: generate-diff
        if: steps.classify.outputs.skip_review != 'true'
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
        run: |
          mkdir -p /tmp/review
          BASE_SHA="${{ github.event.pull_request.base.sha }}"
          HEAD_SHA="${{ github.event.pull_request.head.sha }}"
          MAX_SIZE=${{ inputs.max_diff_bytes }}

          # Read the previous review comment to extract round + last-reviewed-sha
          PREV_COMMENT=$(gh pr view "$PR_NUMBER" --json comments --jq \
            '[.comments[] | select(.body | contains("SUMMARY:"))] | last | .body' \
            2>/dev/null || echo "")

          PREV_ROUND=0
          PREV_SHA=""
          if [ -n "$PREV_COMMENT" ] && [ "$PREV_COMMENT" != "null" ]; then
            PREV_ROUND=$(echo "$PREV_COMMENT" | grep -oP '(?<=<!-- review-round: )[0-9]+' || echo "0")
            PREV_SHA=$(echo "$PREV_COMMENT" | grep -oP '(?<=<!-- last-reviewed-sha: )[a-f0-9]+' || echo "")
          fi

          REVIEW_ROUND=$((PREV_ROUND + 1))

          # Delta-only review for rounds 2+ (if we have a valid previous SHA)
          if [ -n "$PREV_SHA" ] && [ "$REVIEW_ROUND" -gt 1 ]; then
            echo "Reviewing delta since $PREV_SHA (round $REVIEW_ROUND)"
            git diff "$PREV_SHA...$HEAD_SHA" > /tmp/review/pr_diff.diff
          else
            echo "Reviewing full diff (round $REVIEW_ROUND)"
            git diff "$BASE_SHA...$HEAD_SHA" > /tmp/review/pr_diff.diff
          fi

          FULL_SIZE=$(wc -c < /tmp/review/pr_diff.diff)
          if [ "$FULL_SIZE" -gt "$MAX_SIZE" ]; then
            head -c "$MAX_SIZE" /tmp/review/pr_diff.diff > /tmp/review/pr_diff_truncated.diff
            echo "" >> /tmp/review/pr_diff_truncated.diff
            echo "... (diff truncated at ${MAX_SIZE} bytes from ${FULL_SIZE} bytes)" >> /tmp/review/pr_diff_truncated.diff
            mv /tmp/review/pr_diff_truncated.diff /tmp/review/pr_diff.diff
          fi

          echo "review_round=$REVIEW_ROUND" >> $GITHUB_OUTPUT
          echo "head_sha=$HEAD_SHA" >> $GITHUB_OUTPUT
```

- [ ] **Step 3: Replace the Aggregator step**

Find the "Stage 5 — Aggregator" step (lines 120-123):

```yaml
      - name: Stage 5 — Aggregator
        id: aggregate
        if: steps.classify.outputs.skip_review != 'true'
        run: bash scripts/aggregate.sh
```

Replace with:

```yaml
      - name: Stage 5 — Aggregator
        id: aggregate
        if: steps.classify.outputs.skip_review != 'true'
        env:
          HEAD_SHA: ${{ steps.generate-diff.outputs.head_sha }}
          REVIEW_ROUND: ${{ steps.generate-diff.outputs.review_round }}
        run: bash scripts/aggregate.sh
```

- [ ] **Step 4: Verify the workflow YAML is valid**

```bash
cd /home/sbilakhia/Documents/dev/repos/pc
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/pr-review.yml'))" && echo "YAML valid"
```

Expected: `YAML valid`

- [ ] **Step 5: Verify the env var chain is correct**

Trace the data flow to confirm consistency:

1. `generate-diff` step outputs `review_round` and `head_sha` to `$GITHUB_OUTPUT` ✓
2. Aggregator step reads them via `${{ steps.generate-diff.outputs.head_sha }}` and `${{ steps.generate-diff.outputs.review_round }}` ✓
3. Aggregator step passes them as env vars `HEAD_SHA` and `REVIEW_ROUND` to `aggregate.sh` ✓
4. `aggregate.sh` line 167 reads `${REVIEW_ROUND:-1}` and line 168 reads `${HEAD_SHA:-unknown}` ✓

```bash
# Verify aggregate.sh reads these env vars
grep -n 'REVIEW_ROUND\|HEAD_SHA' scripts/aggregate.sh
```

Expected:
```
167:  echo "<!-- review-round: ${REVIEW_ROUND:-1} -->"
168:  echo "<!-- last-reviewed-sha: ${HEAD_SHA:-unknown} -->"
```

- [ ] **Step 6: Commit and push**

```bash
cd /home/sbilakhia/Documents/dev/repos/pc
git add .github/workflows/pr-review.yml
git commit -m "fix: pr-review.yml review state tracking + delta-only review

Three bugs fixed:
1. HEAD_SHA was not set in the Aggregator step env, so aggregate.sh
   always emitted 'last-reviewed-sha: unknown' — the review-loop could
   never track what was already reviewed.
2. REVIEW_ROUND was not set, so aggregate.sh always emitted
   'review-round: 1' — the max-3-rounds blocking never triggered.
3. The diff was always BASE...HEAD (full diff). Now reads the previous
   review comment's last-reviewed-sha and generates a delta diff
   (PREV_SHA...HEAD) for rounds 2+, preventing re-flagging accepted code."
git push -u origin fix/review-loop-state-tracking
```

- [ ] **Step 7: Create PR**

```bash
gh pr create --title "fix: review loop state tracking + delta-only review" --body "Fixes three bugs in \`pr-review.yml\` that break the autonomous review loop:

1. **HEAD_SHA not in Aggregator env** — \`aggregate.sh\` always emitted \`last-reviewed-sha: unknown\`
2. **REVIEW_ROUND not in Aggregator env** — \`aggregate.sh\` always emitted \`review-round: 1\`, so max-3-rounds blocking never triggered
3. **No delta-only review** — diff was always \`BASE...HEAD\`. Now reads the previous review comment's \`last-reviewed-sha\` and generates \`PREV_SHA...HEAD\` for rounds 2+

The \`generate-diff\` step now reads the previous review comment, extracts the round and SHA, and outputs them for the Aggregator step." --base main
```

Expected: PR URL returned. Do not merge yet.

---

### Task 8: Complete review-loop.yml — Blocked card move + issue number resolution

**Files:**
- Modify: `pc/.github/workflows/review-loop.yml:24-29` (add bootstrap step after checkout)
- Modify: `pc/.github/workflows/review-loop.yml:30-57` (add issue number resolution to Get PR step)
- Modify: `pc/.github/workflows/review-loop.yml:105-121` (Block step — add card move)
- Modify: `pc/.github/workflows/review-loop.yml:123-143` (Re-dispatch step — fix issue_number)

**Interfaces:**
- Consumes: `update-card-status.sh` from `nanobyte-services/sdlc/scripts/` (bootstrapped at job start)
- Consumes: `SDLC_PROJECT_ID` secret for GraphQL card moves
- Consumes: `NANOBYTE_SERVICES_TOKEN` secret for cloning the private nanobyte-services repo
- Produces: card moved to "Blocked" lane when review loop exhausted; correct `issue_number` in re-dispatch

- [ ] **Step 1: Create a feature branch**

```bash
cd /home/sbilakhia/Documents/dev/repos/pc
git checkout main && git pull
git checkout -b fix/review-loop-blocked-card-and-issue-number
```

- [ ] **Step 2: Add a bootstrap step after the checkout step**

In `pc/.github/workflows/review-loop.yml`, find the checkout step (lines 25-28):

```yaml
      - name: Checkout repo
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
```

After it (before the "Get PR number" step), add:

```yaml
      - name: Bootstrap shared scripts
        env:
          NANOBYTE_TOKEN: ${{ secrets.NANOBYTE_SERVICES_TOKEN }}
        run: |
          rm -rf /tmp/nbs
          git clone --depth 1 https://x-access-token:${NANOBYTE_TOKEN}@github.com/${{ github.repository_owner }}/nanobyte-services /tmp/nbs
          mkdir -p scripts
          cp /tmp/nbs/sdlc/scripts/update-card-status.sh scripts/
          chmod +x scripts/update-card-status.sh
```

- [ ] **Step 3: Add issue number resolution to the "Get PR number" step**

Find the "Get PR number and check both workflows completed" step (lines 30-57). After line 34 (`echo "number=$PR_NUMBER" >> $GITHUB_OUTPUT`), add:

```bash
          # Resolve the linked issue number from the PR body (e.g., "Closes #42")
          PR_BODY=$(gh pr view "$PR_NUMBER" --json body --jq '.body' 2>/dev/null || echo "")
          ISSUE_NUM=$(echo "$PR_BODY" | grep -oP '(?:Closes|Resolves|Fixes) #\K[0-9]+' | head -1 || echo "0")
          if [ -z "$ISSUE_NUM" ] || [ "$ISSUE_NUM" = "0" ]; then
            # Fallback: check PR's linked issues via API
            ISSUE_NUM=$(gh api repos/${{ github.repository }}/pulls/$PR_NUMBER --jq '.body' 2>/dev/null | grep -oP '(?:Closes|Resolves|Fixes) #\K[0-9]+' | head -1 || echo "0")
          fi
          echo "issue_number=${ISSUE_NUM:-0}" >> $GITHUB_OUTPUT
```

The full "Get PR number" step should now look like:

```yaml
      - name: Get PR number and check both workflows completed
        id: pr
        run: |
          PR_NUMBER="${{ github.event.workflow_run.pull_requests[0].number }}"
          echo "number=$PR_NUMBER" >> $GITHUB_OUTPUT

          # Resolve the linked issue number from the PR body (e.g., "Closes #42")
          PR_BODY=$(gh pr view "$PR_NUMBER" --json body --jq '.body' 2>/dev/null || echo "")
          ISSUE_NUM=$(echo "$PR_BODY" | grep -oP '(?:Closes|Resolves|Fixes) #\K[0-9]+' | head -1 || echo "0")
          if [ -z "$ISSUE_NUM" ] || [ "$ISSUE_NUM" = "0" ]; then
            # Fallback: check PR's linked issues via API
            ISSUE_NUM=$(gh api repos/${{ github.repository }}/pulls/$PR_NUMBER --jq '.body' 2>/dev/null | grep -oP '(?:Closes|Resolves|Fixes) #\K[0-9]+' | head -1 || echo "0")
          fi
          echo "issue_number=${ISSUE_NUM:-0}" >> $GITHUB_OUTPUT

          # Check that both build and pr-review have completed for this PR
          # Query the workflow run status via GitHub API
          HEAD_SHA="${{ github.event.workflow_run.head_sha }}"
          BUILD_STATUS=$(gh api repos/${{ github.repository }}/actions/runs \
            --jq ".workflow_runs[] | select(.head_sha == \"$HEAD_SHA\" and .name == \"build\") | .conclusion" \
            | head -1)
          REVIEW_STATUS=$(gh api repos/${{ github.repository }}/actions/runs \
            --jq ".workflow_runs[] | select(.head_sha == \"$HEAD_SHA\" and .name == \"PR Review (Reusable)\") | .conclusion" \
            | head -1)

          echo "build_status=$BUILD_STATUS" >> $GITHUB_OUTPUT
          echo "review_status=$REVIEW_STATUS" >> $GITHUB_OUTPUT

          # If either is still in_progress, exit early — wait for the other
          if [ "$BUILD_STATUS" = "null" ] || [ -z "$BUILD_STATUS" ] || \
             [ "$REVIEW_STATUS" = "null" ] || [ -z "$REVIEW_STATUS" ]; then
            echo "both_completed=false" >> $GITHUB_OUTPUT
          else
            echo "both_completed=true" >> $GITHUB_OUTPUT
          fi
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

- [ ] **Step 4: Replace the Block step to add card move**

Find the "Block if max rounds exceeded" step (lines 105-121):

```yaml
      - name: Block if max rounds exceeded
        if: |
          steps.pr.outputs.both_completed == 'true' &&
          (steps.findings.outputs.high_count != '0' || steps.findings.outputs.low_count != '0') &&
          steps.findings.outputs.round >= '3'
        run: |
          PR_NUMBER="${{ steps.pr.outputs.number }}"
          # Post a Blocked comment
          gh pr comment $PR_NUMBER --body "## Blocked: Review Loop Exhausted (3 rounds)

          The build agent could not resolve all findings after 3 review rounds.
          Unresolved findings: ${{ steps.findings.outputs.high_count }} HIGH, ${{ steps.findings.outputs.low_count }} LOW.

          Please review the findings above and provide guidance. Move the card back to \`Executing\` after updating." --repo ${{ github.repository }}
          # TODO: move Projects card to "Blocked" lane via GraphQL
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

Replace with:

```yaml
      - name: Block if max rounds exceeded
        if: |
          steps.pr.outputs.both_completed == 'true' &&
          (steps.findings.outputs.high_count != '0' || steps.findings.outputs.low_count != '0') &&
          steps.findings.outputs.round >= '3'
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          PROJECT_ID: ${{ secrets.SDLC_PROJECT_ID }}
          GITHUB_REPOSITORY: ${{ github.repository }}
        run: |
          PR_NUMBER="${{ steps.pr.outputs.number }}"
          ISSUE_NUMBER="${{ steps.pr.outputs.issue_number }}"
          # Post a Blocked comment
          gh pr comment $PR_NUMBER --body "## Blocked: Review Loop Exhausted (3 rounds)

          The build agent could not resolve all findings after 3 review rounds.
          Unresolved findings: ${{ steps.findings.outputs.high_count }} HIGH, ${{ steps.findings.outputs.low_count }} LOW.

          Please review the findings above and provide guidance. Move the card back to \`Executing\` after updating." --repo ${{ github.repository }}
          # Move the Projects card to "Blocked" lane
          if [ "$ISSUE_NUMBER" != "0" ] && [ -n "$PROJECT_ID" ]; then
            bash scripts/update-card-status.sh --issue "$ISSUE_NUMBER" --lane "Blocked" --repo ${{ github.repository }}
          else
            echo "WARNING: Could not move card to Blocked — issue_number=$ISSUE_NUMBER, PROJECT_ID set=${PROJECT_ID:+yes}"
          fi
```

- [ ] **Step 5: Fix issue_number=0 in the re-dispatch step**

Find the "Re-dispatch build agent to fix findings" step (lines 123-143). Find line 138:

```bash
            -f 'client_payload[issue_number]=0' \
```

Replace with:

```bash
            -f 'client_payload[issue_number]=${{ steps.pr.outputs.issue_number }}' \
```

- [ ] **Step 6: Verify the workflow YAML is valid**

```bash
cd /home/sbilakhia/Documents/dev/repos/pc
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/review-loop.yml'))" && echo "YAML valid"
```

Expected: `YAML valid`

- [ ] **Step 7: Verify the issue_number data flow**

Trace the data flow:

1. `pr` step outputs `issue_number` to `$GITHUB_OUTPUT` ✓ (Step 3)
2. Block step reads `${{ steps.pr.outputs.issue_number }}` ✓ (Step 4)
3. Re-dispatch step sends `client_payload[issue_number]=${{ steps.pr.outputs.issue_number }}` ✓ (Step 5)
4. `sdlc-agent.yml` reads `issue_number` from client payload (line 118) ✓
5. `sdlc-agent.yml` passes `ISSUE_NUMBER` env var to the agent (line 136) ✓
6. `build.md` agent uses `$ISSUE_NUMBER` for card moves ✓ (Task 5)

- [ ] **Step 8: Commit and push**

```bash
cd /home/sbilakhia/Documents/dev/repos/pc
git add .github/workflows/review-loop.yml
git commit -m "fix: review-loop Blocked card move + issue number resolution

1. Add bootstrap step for update-card-status.sh (from nanobyte-services).
2. Resolve linked issue number from PR body ('Closes #NNN') for card
   moves and re-dispatch.
3. Move Projects card to 'Blocked' lane when review loop exhausts 3
   rounds (was a TODO).
4. Pass actual issue_number in re-dispatch instead of hardcoded 0."
git push -u origin fix/review-loop-blocked-card-and-issue-number
```

- [ ] **Step 9: Create PR**

```bash
gh pr create --title "fix: review-loop Blocked card move + issue number resolution" --body "Completes \`review-loop.yml\`:

1. **Bootstrap step** — clones \`nanobyte-services\` and copies \`update-card-status.sh\`
2. **Issue number resolution** — parses \`Closes #NNN\` from PR body for card moves and re-dispatch
3. **Blocked card move** — calls \`update-card-status.sh --lane Blocked\` when review loop exhausts 3 rounds (was a TODO)
4. **Fix issue_number=0** — passes resolved issue number in re-dispatch instead of hardcoded 0" --base main
```

Expected: PR URL returned. Do not merge yet.

---

## Phase 3 — Integration

> Merge all Phase 2 PRs and verify the bootstrap chain works. Depends on all Phase 2 tasks being reviewed and approved.

### Task 9: Merge all PRs and verify bootstrap chain

**Files:**
- No files created or modified — merge + verification only

- [ ] **Step 1: Merge Lane A PRs (nanobyte-services)**

Merge in this order (dead code removal first, then docs, then agent fix):

```bash
# Task 3: Remove dead Worker code
gh pr merge <PR_NUMBER_TASK_3> --squash --repo Nanobyte-Canada/nanobyte-services

# Task 4: Update README
gh pr merge <PR_NUMBER_TASK_4> --squash --repo Nanobyte-Canada/nanobyte-services

# Task 5: Build agent review-fix enhancements
gh pr merge <PR_NUMBER_TASK_5> --squash --repo Nanobyte-Canada/nanobyte-services

# Task 6: test-dispatch.sh description
gh pr merge <PR_NUMBER_TASK_6> --squash --repo Nanobyte-Canada/nanobyte-services
```

Expected: each merge succeeds. Pull main after each merge:

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
git checkout main && git pull
```

- [ ] **Step 2: Merge Lane B PRs (pc)**

```bash
# Task 7: pr-review.yml state tracking + delta-only review
gh pr merge <PR_NUMBER_TASK_7> --squash --repo Nanobyte-Canada/pc

# Task 8: review-loop.yml Blocked card + issue number
gh pr merge <PR_NUMBER_TASK_8> --squash --repo Nanobyte-Canada/pc
```

Expected: each merge succeeds. Pull main after each merge:

```bash
cd /home/sbilakhia/Documents/dev/repos/pc
git checkout main && git pull
```

- [ ] **Step 3: Verify nanobyte-services main is clean**

```bash
cd /home/sbilakhia/Documents/dev/repos/nanobyte-services
git status
```

Expected: `nothing to commit, working tree clean` on `main`.

Verify dead code is gone:

```bash
ls sdlc/worker/src/
```

Expected: `dedupe.ts  index.ts  verify.ts` (no `dispatch.ts`, no `routing.ts`).

Verify Worker tests still pass:

```bash
cd sdlc/worker && npx vitest run
```

Expected: 7 tests pass (2 files: verify + dedupe).

- [ ] **Step 4: Verify pc main is clean**

```bash
cd /home/sbilakhia/Documents/dev/repos/pc
git status
```

Expected: `nothing to commit, working tree clean` on `main`.

- [ ] **Step 5: Verify the bootstrap chain works**

Trigger `sdlc-agent.yml` via `workflow_dispatch` to verify it can clone `nanobyte-services` and copy shared config + scripts:

```bash
gh workflow run sdlc-agent.yml --repo Nanobyte-Canada/pc \
  -f issue_number=39 \
  -f label=Planning \
  -f phase=planning \
  -f agent=planner \
  -f model=opencode-go/glm-5.2
```

Wait for the run to start:

```bash
sleep 10
gh run list --workflow sdlc-agent.yml --repo Nanobyte-Canada/pc --limit 1
```

Expected: a run with status `in_progress` or `queued`.

Monitor the run:

```bash
gh run watch <RUN_ID> --repo Nanobyte-Canada/pc
```

If the bootstrap step fails, check the logs:

```bash
gh run view <RUN_ID> --repo Nanobyte-Canada/pc --log
```

Common failures:
- `NANOBYTE_SERVICES_TOKEN` secret not set or expired → re-set it (Task 2 Step 4)
- `nanobyte-services` repo not accessible with the token → verify token has `repo` scope
- `OPENCODE_AUTH_JSON` secret not set → re-set it (Task 2 Step 4)

---

## Phase 4 — End-to-End Validation

> Prove the full pipeline works: issue → plan → PR → review → merge/block. Depends on Phase 1 (infrastructure) and Phase 3 (integration) being complete.

### Task 10: Test the planner agent end-to-end

**Files:**
- No files created or modified — validation only

- [ ] **Step 1: Create a test issue**

```bash
ISSUE_NUMBER=$(gh issue create --repo Nanobyte-Canada/pc \
  --title "Test: SDLC planner agent e2e" \
  --body "Add a health check endpoint to the portfolio service that returns 200 OK with service version. This is a test issue for the autonomous SDLC pipeline." \
  --json number --jq '.number')
echo "Created issue #$ISSUE_NUMBER"
```

Expected: issue created with a number (note it).

- [ ] **Step 2: Add the issue to the Projects board**

Manually add the issue to the SDLC Pipeline board (Backlog lane) via the GitHub Projects UI, or via GraphQL:

```bash
# Get the project ID (if not already known from Phase 1)
PROJECT_ID=$(gh api graphql -f query='{ organization(login: "Nanobyte-Canada") { projectsV2(first: 10) { nodes { title id } } } }' --jq '.data.organization.projectsV2.nodes[] | select(.title == "SDLC Pipeline") | .id')
echo "Project ID: $PROJECT_ID"

# Add the issue to the project
# This requires the issue's node ID
ISSUE_NODE_ID=$(gh issue view $ISSUE_NUMBER --repo Nanobyte-Canada/pc --json id --jq '.id')
gh api graphql -f query="
mutation {
  addProjectV2ItemById(input: { projectId: \"$PROJECT_ID\", contentId: \"$ISSUE_NODE_ID\" }) {
    item { id }
  }
}"
```

Expected: item node ID returned.

- [ ] **Step 3: Move the card to "Triaging"**

Via the Projects UI, move the card from Backlog to Triaging. This triggers:
1. `projects_v2_item` webhook → Worker → syncs "Triaging" label to the issue
2. `issues:[labeled]` webhook → `sdlc-agent.yml` triggers → planner agent runs

Verify the label was synced:

```bash
sleep 5
gh issue view $ISSUE_NUMBER --repo Nanobyte-Canada/pc --json labels --jq '.labels[].name'
```

Expected: `Triaging` appears in the label list.

Verify the workflow triggered:

```bash
gh run list --workflow sdlc-agent.yml --repo Nanobyte-Canada/pc --limit 1
```

Expected: a run triggered by `issues:[labeled]`.

- [ ] **Step 4: Wait for the planner to complete and post a scope document**

```bash
# Poll for up to 10 minutes
for i in $(seq 1 60); do
  sleep 10
  STATUS=$(gh run list --workflow sdlc-agent.yml --repo Nanobyte-Canada/pc --limit 1 --json conclusion --jq '.[0].conclusion' 2>/dev/null || echo "")
  if [ "$STATUS" = "success" ] || [ "$STATUS" = "failure" ]; then
    echo "Run completed with status: $STATUS"
    break
  fi
  echo "Waiting... ($((i*10))s elapsed)"
done
```

If `STATUS` is `failure`, check the logs:

```bash
gh run view --log --repo Nanobyte-Canada/pc --log-failed
```

If `STATUS` is `success`, verify the scope document:

```bash
gh issue view $ISSUE_NUMBER --repo Nanobyte-Canada/pc --comments
```

Expected: a comment with header `## Scope Document (latest — updated <date>)` containing:
- `### Problem statement`
- `### Proposed scope`
- `### Explicitly out of scope`
- `### Affected modules`
- `### Open questions`

- [ ] **Step 5: Verify the card moved to "Scope Review"**

```bash
# Query the project for the card's current Status
gh api graphql -f query="
query {
  node(id: \"$PROJECT_ID\") {
    ... on ProjectV2 {
      items(first: 100) {
        nodes {
          content { ... on Issue { number } }
          fieldValueByName(name: \"Status\") { ... on ProjectV2ItemFieldSingleSelectValue { name } }
        }
      }
    }
  }
}" --jq ".data.node.items.nodes[] | select(.content.number == $ISSUE_NUMBER) | .fieldValueByName.name"
```

Expected: `Scope Review`

If the card didn't move, the planner agent's `update-card-status.sh` call may have failed. Check the workflow logs for the agent's output.

- [ ] **Step 6: Clean up or continue**

If the scope document looks good, you can continue to test the Planning phase by moving the card to "Planning" (approving the scope). Or close the test issue and clean up:

```bash
gh issue close $ISSUE_NUMBER --repo Nanobyte-Canada/pc --comment "Test complete — closing."
```

---

### Task 11: Test the review loop end-to-end

**Files:**
- No files created or modified — validation only

> This test creates a PR with a deliberate issue (missing docstring) and verifies the review loop runs, tracks rounds, and either auto-merges or blocks after 3 rounds.

- [ ] **Step 1: Create a test branch with a deliberate issue**

```bash
cd /home/sbilakhia/Documents/dev/repos/pc
git checkout main && git pull
git checkout -b test/review-loop-e2e

# Create a file with a deliberate missing-docstring issue
cat > src/test-review-loop.kt << 'EOF'
fun undocumentedFunction(): String {
    return "test"
}
EOF

git add src/test-review-loop.kt
git commit -m "test: deliberate missing docstring for review loop e2e test"
git push -u origin test/review-loop-e2e
```

- [ ] **Step 2: Open a PR**

```bash
PR_NUMBER=$(gh pr create \
  --title "Test: Review loop e2e" \
  --body "Test PR for the review loop. Closes #0

  This PR has a deliberate missing-docstring issue to trigger review findings." \
  --repo Nanobyte-Canada/pc \
  --json number --jq '.number')
echo "Created PR #$PR_NUMBER"
```

Note: `Closes #0` is intentional — there's no real issue to close. The review loop's issue resolution will parse this as `0`, which is fine for this test (we're testing the review loop, not card moves).

- [ ] **Step 3: Wait for build + pr-review to complete**

```bash
# Poll for up to 15 minutes
for i in $(seq 1 90); do
  sleep 10
  BUILD_STATUS=$(gh api repos/Nanobyte-Canada/pc/actions/runs \
    --jq ".workflow_runs[] | select(.head_branch == \"test/review-loop-e2e\" and .name == \"build\") | .conclusion" \
    | head -1)
  REVIEW_STATUS=$(gh api repos/Nanobyte-Canada/pc/actions/runs \
    --jq ".workflow_runs[] | select(.head_branch == \"test/review-loop-e2e\" and .name == \"PR Review (Reusable)\") | .conclusion" \
    | head -1)
  if [ -n "$BUILD_STATUS" ] && [ "$BUILD_STATUS" != "null" ] && \
     [ -n "$REVIEW_STATUS" ] && [ "$REVIEW_STATUS" != "null" ]; then
    echo "Build: $BUILD_STATUS, Review: $REVIEW_STATUS"
    break
  fi
  echo "Waiting... ($((i*10))s elapsed)"
done
```

- [ ] **Step 4: Verify the review comment has correct hidden HTML comments**

```bash
COMMENT=$(gh pr view $PR_NUMBER --repo Nanobyte-Canada/pc --json comments --jq \
  '[.comments[] | select(.body | contains("SUMMARY:"))] | last | .body')

echo "$COMMENT" | grep '<!-- review-round:'
echo "$COMMENT" | grep '<!-- last-reviewed-sha:'
```

Expected:
```
<!-- review-round: 1 -->
<!-- last-reviewed-sha: <40-char-hex-sha> -->
```

If `last-reviewed-sha` is `unknown`, the `HEAD_SHA` env var fix (Task 7) didn't work. Check the pr-review.yml workflow logs.

If `review-round` is missing, the `REVIEW_ROUND` env var fix (Task 7) didn't work.

- [ ] **Step 5: Verify the review loop triggered**

```bash
# Check for review-loop workflow runs
gh run list --workflow review-loop.yml --repo Nanobyte-Canada/pc --limit 3
```

Expected: at least one `review-loop.yml` run.

If the review loop didn't trigger, check:
- `workflow_run` event was fired (build + pr-review must both complete)
- The `if` condition in `review-loop.yml` passed

- [ ] **Step 6: Verify the review loop re-dispatched the build agent (if findings)**

If the review found findings (HIGH or LOW count > 0), the review loop should re-dispatch the build agent:

```bash
# Check for sdlc-agent.yml runs triggered by repository_dispatch
gh run list --workflow sdlc-agent.yml --repo Nanobyte-Canada/pc --limit 3 --json event,conclusion --jq '.[] | select(.event == "repository_dispatch") | {event, conclusion}'
```

Expected: a run with `event: repository_dispatch`.

- [ ] **Step 7: Wait for the review loop to terminate (up to 30 minutes)**

```bash
MAX_WAIT=1800  # 30 minutes
ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT ]; do
  sleep 30
  ELAPSED=$((ELAPSED + 30))

  # Check for a "Blocked" comment
  BLOCKED=$(gh pr view $PR_NUMBER --repo Nanobyte-Canada/pc --json comments --jq \
    '[.comments[] | select(.body | contains("Blocked: Review Loop Exhausted"))] | length')

  if [ "$BLOCKED" -gt 0 ]; then
    echo "PASS: review loop terminated with Blocked comment after 3 rounds"

    # Verify the round counter never exceeded 3
    ROUNDS=$(gh pr view $PR_NUMBER --repo Nanobyte-Canada/pc --json comments --jq \
      '[.comments[] | .body | scan("<!-- review-round: ([0-9]+) -->") | .[0]] | max // "0"')
    echo "Max round was: $ROUNDS"
    if [ "$ROUNDS" -le 3 ]; then
      echo "PASS: max round was $ROUNDS (<= 3)"
    else
      echo "FAIL: round counter exceeded 3 (was $ROUNDS)"
    fi
    break
  fi

  # Check if PR was merged (shouldn't be — it has findings)
  MERGED=$(gh pr view $PR_NUMBER --repo Nanobyte-Canada/pc --json merged --jq '.merged')
  if [ "$MERGED" = "true" ]; then
    echo "FAIL: PR was auto-merged despite having findings"
    break
  fi

  echo "Waiting... ($((ELAPSED))s elapsed)"
done
```

Expected: `PASS: review loop terminated with Blocked comment after 3 rounds` and `PASS: max round was 3 (<= 3)`.

- [ ] **Step 8: Clean up**

```bash
# Close the PR without merging
gh pr close $PR_NUMBER --repo Nanobyte-Canada/pc --delete-branch

# Delete the test file from main (if it was merged — it shouldn't be)
git checkout main && git pull
```

---

### Task 12: Document validation results

**Files:**
- No files created or modified — documentation only

- [ ] **Step 1: Record the results of Tasks 10-11**

Create a summary of what worked and what didn't:

```
## E2E Validation Results

### Task 10: Planner agent
- Issue created: #___
- Card added to board: ✅/❌
- Card moved to Triaging: ✅/❌
- Label synced (Triaging): ✅/❌
- Workflow triggered: ✅/❌
- Planner completed: ✅/❌
- Scope document posted: ✅/❌
- Card moved to Scope Review: ✅/❌

### Task 11: Review loop
- PR created: #___
- Build completed: ✅/❌ (status: ___)
- PR review completed: ✅/❌ (status: ___)
- Review comment has review-round: ✅/❌ (value: ___)
- Review comment has last-reviewed-sha: ✅/❌ (value: ___)
- Review loop triggered: ✅/❌
- Build agent re-dispatched: ✅/❌
- Blocked comment posted: ✅/❌
- Max round <= 3: ✅/❌ (value: ___)
- Card moved to Blocked: ✅/❌
```

- [ ] **Step 2: Create issues for any failures**

For each ❌ in the results, create a GitHub issue describing the failure:

```bash
gh issue create --repo Nanobyte-Canada/pc \
  --title "SDLC pipeline: <failure description>" \
  --body "## Failure
<what failed>

## Steps to reproduce
<from the validation task>

## Logs
<gh run view --log output>

## Expected
<what should have happened>"
```

Add the issue to the SDLC Pipeline board for triage.

---

## Plan Self-Review

### Spec coverage

| Spec gap | Covered by |
|----------|------------|
| N1: dispatch.ts obsolete | Task 3 |
| N2: routing.ts dead code | Task 3 |
| N3: README.md stale | Task 4 |
| N4: build.md review-fix gaps | Task 5 |
| N5: test-dispatch.sh stale | Task 6 |
| P1: HEAD_SHA not in Aggregator | Task 7 Step 3 |
| P2: REVIEW_ROUND not in Aggregator | Task 7 Step 3 |
| P3: No delta-only review | Task 7 Step 2 |
| P4: Card not moved to Blocked | Task 8 Step 4 |
| P5: issue_number=0 | Task 8 Step 5 |
| I1-I5: Infrastructure verification | Tasks 1-2 |
| E2E validation | Tasks 10-12 |

### Placeholder scan

- No TBDs, TODOs, or "implement later" in the plan.
- All code blocks contain complete, copy-pasteable code.
- All commands have expected outputs.
- `<PR_NUMBER_TASK_N>` placeholders in Task 9 are intentional — the PR numbers are created in Tasks 3-8 and noted by the implementer.

### Type consistency

- `REVIEW_ROUND` and `HEAD_SHA` env var names match between `pr-review.yml` (Task 7) and `aggregate.sh` (existing lines 167-168).
- `issue_number` field name matches between `review-loop.yml` re-dispatch (Task 8 Step 5) and `sdlc-agent.yml` client payload parsing (existing line 118).
- `LAST_REVIEWED_SHA` env var name matches between `sdlc-agent.yml` (existing line 140), `build.md` (Task 5), and `review-loop.yml` re-dispatch (existing line 141).
- `SDLC_PROJECT_ID` secret name matches between `review-loop.yml` (Task 8 Step 4) and `sdlc-agent.yml` (existing line 141).
- `NANOBYTE_SERVICES_TOKEN` secret name matches between `review-loop.yml` (Task 8 Step 2) and `sdlc-agent.yml` (existing line 52).

### Dependencies

- **Task 1-2 (Phase 1):** No dependencies. Can start immediately.
- **Task 3 (dead code removal):** Depends on PR #1 being merged (removes files that PR #1 modifies). If PR #1 is already merged, no dependency.
- **Tasks 4, 5, 6 (nanobyte-services docs/agent/test):** Independent of each other. Can run in parallel after Task 3.
- **Tasks 7, 8 (pc workflow fixes):** Independent of each other. Can run in parallel.
- **Lane A (Tasks 3-6) and Lane B (Tasks 7-8):** Different repos, no write conflicts. Can run in parallel.
- **Task 9 (integration):** Depends on all Phase 2 tasks (3-8) being merged.
- **Tasks 10-12 (e2e validation):** Depend on Task 9 (integration) and Tasks 1-2 (infrastructure).

### Parallelization summary

```
Phase 1:  Task 1 → Task 2  (sequential — Task 2 needs Project ID from Task 1)
Phase 2:  Lane A (Tasks 3→4, 5, 6)  ┐
          Lane B (Tasks 7, 8)        ┘  parallel
Phase 3:  Task 9  (depends on all Phase 2)
Phase 4:  Task 10, Task 11  (can run in parallel after Phase 3)
          Task 12  (depends on 10 + 11)
```
