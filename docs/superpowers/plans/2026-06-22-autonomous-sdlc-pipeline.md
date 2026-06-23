# Autonomous SDLC Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an autonomous SDLC system where GitHub Projects v2 swim lanes trigger opencode agents (via a GitHub App + Cloudflare Worker + self-hosted runner) to plan, implement, review, test, fix, and publish code — with the human reviewing only via card moves in the PM tool.

**Architecture:** GitHub Projects v2 board (13 swim lanes) → `projects_v2_item` webhook → GitHub App → Cloudflare Worker (HMAC verify + route + dedupe) → `repository_dispatch` → self-hosted GitHub Actions runner on home server → `opencode run --agent <phase-agent> --model <phase-model>` → agent does work, updates card, raises PR → existing `build.yml` + `pr-review.yml` pipelines run → autonomous review loop (max 3 rounds) → auto-merge. Shared config lives in `nanobyte-services` repo; consumer repos bootstrap it at job start.

**Tech Stack:** GitHub Actions (self-hosted runner), Cloudflare Workers (TypeScript, Vitest), opencode CLI (`opencode run --agent --model --format json --dangerously-skip-permissions`), GitHub Projects v2 (GraphQL API), `gh` CLI, Bash shell scripts.

**Spec:** `docs/superpowers/specs/2026-06-22-autonomous-sdlc-pipeline-design.md`

## Global Constraints

- **Provider:** `opencode-go` (auth via `auth.json` written from `OPENCODE_AUTH_JSON` secret at job start — proven pattern from existing `pr-review.yml:42-51`).
- **Models:** planner/tester/deployer use `opencode-go/glm-5.2` (cheap); build/bugfixer use `opencode-go/deepseek-v4-pro` (powerful coding).
- **Runner:** self-hosted, org-level, ephemeral mode, outbound-443 only (behind Cloudflare Tunnel), Docker available (no local JDK — backend tests run in Docker).
- **Branch strategy:** feature branch off `main` → PR to `main`.
- **Planning artifacts:** issue comments (edited in place), NOT repo files.
- **Review loop:** max 3 rounds → `Blocked` lane; no-blind-revert; delta-only review after round 1.
- **Agent format:** markdown with frontmatter `name`, `model`, `mode: subagent`, `description` + instructions (matches existing `.opencode/agents/pr-review.md`).
- **Skill format:** markdown with frontmatter `name`, `description` + instructions (matches existing `.opencode/skills/pr-review/SKILL.md`).
- **Shell scripts:** `set -euo pipefail`, POSIX-compatible where possible, `--dry-run` support (matches existing `scripts/*.sh` pattern).
- **Reusability:** shared config in `nanobyte-services/sdlc/`; consumer repos add one `repository_dispatch` workflow + optional `.opencode/` overrides.

---

## File Structure

### `nanobyte-services` repo (new, firm-wide shared services)

| File | Responsibility |
|------|----------------|
| `sdlc/opencode/opencode.json` | Centralized provider/model config (replaces scattered hardcoded models) |
| `sdlc/opencode/agents/planner.md` | Planner agent: triage scope, create module plans |
| `sdlc/opencode/agents/build.md` | Build agent: implement plan, raise PR, address review findings |
| `sdlc/opencode/agents/tester.md` | Tester agent stub (full spec in future) |
| `sdlc/opencode/agents/bugfixer.md` | Bug-fixer agent stub (full spec in future) |
| `sdlc/opencode/agents/deployer.md` | Deployer agent stub (full spec in future) |
| `sdlc/opencode/skills/planning/SKILL.md` | Planning skill: how to triage and plan |
| `sdlc/routing.yml` | Lane → phase → agent → model mapping |
| `sdlc/worker/src/index.ts` | Worker entry: receive webhook, orchestrate verify→dedupe→route→dispatch |
| `sdlc/worker/src/verify.ts` | HMAC-SHA256 signature verification (Web Crypto API) |
| `sdlc/worker/src/routing.ts` | Parse routing.yml, map webhook event → dispatch params |
| `sdlc/worker/src/dispatch.ts` | Fire `repository_dispatch` to target repo via GitHub API |
| `sdlc/worker/src/dedupe.ts` | KV-based webhook deduplication (X-GitHub-Delivery ID, 10-min TTL) |
| `sdlc/worker/test/verify.test.ts` | HMAC verification unit tests |
| `sdlc/worker/test/routing.test.ts` | Routing config parser unit tests |
| `sdlc/worker/test/dedupe.test.ts` | Dedupe unit tests |
| `sdlc/worker/wrangler.toml` | Cloudflare Worker config (KV namespace bindings) |
| `sdlc/worker/package.json` | Worker deps: vitest, @cloudflare/workers-types |
| `sdlc/worker/tsconfig.json` | TypeScript config for Worker |
| `sdlc/runner/setup.sh` | Self-hosted runner download + configure + install as systemd service |
| `sdlc/runner/ephemeral-supervisor.sh` | Systemd wrapper: provision fresh ephemeral runner per job |
| `sdlc/scripts/create-board.sh` | Create Projects v2 board with 13 lanes + 5 custom fields via GraphQL |
| `sdlc/tests/test-dispatch.sh` | Integration test: repository_dispatch fires workflow |
| `sdlc/tests/test-planner-dry-run.sh` | Integration test: planner agent produces scope comment |
| `sdlc/tests/test-review-loop.sh` | Integration test: review loop terminates at 3 rounds |
| `sdlc/README.md` | Setup guide for the SDLC orchestrator |
| `.github/workflows/sdlc-ci.yml` | CI: run Worker unit tests on PR |

### `pc` repo (this repo, consumer)

| File | Responsibility |
|------|----------------|
| `.github/workflows/sdlc-agent.yml` | `repository_dispatch` + `issues: [labeled]` → `opencode run --agent` |
| `.github/workflows/review-loop.yml` | `workflow_run` listener: re-trigger build agent on review findings, auto-merge or block |
| `scripts/llm-review.sh` | **Modify:** add `--since-sha` flag for delta-only review (rounds 2+) |
| `scripts/aggregate.sh` | **Modify:** append hidden HTML comments (round counter, last-reviewed-sha) to output |
| `scripts/tests/test-review-loop.sh` | New: review loop integration test |

---

## Stage 1 — MVP: Label-Triggered Planner Agent

Validates the opencode-in-CI loop. No App, no Worker, no self-hosted runner yet.

---

### Task 1: Create `nanobyte-services` repo + shared opencode config bundle

**Files:**
- Create: `nanobyte-services/` (new GitHub repo)
- Create: `nanobyte-services/sdlc/opencode/opencode.json`
- Create: `nanobyte-services/sdlc/opencode/agents/planner.md`
- Create: `nanobyte-services/sdlc/opencode/agents/build.md`
- Create: `nanobyte-services/sdlc/opencode/agents/tester.md`
- Create: `nanobyte-services/sdlc/opencode/agents/bugfixer.md`
- Create: `nanobyte-services/sdlc/opencode/agents/deployer.md`
- Create: `nanobyte-services/sdlc/opencode/skills/planning/SKILL.md`
- Create: `nanobyte-services/README.md`

**Interfaces:**
- Produces: `nanobyte-services/sdlc/opencode/` — the shared config bundle that consumer repos bootstrap via `git clone --depth 1` at job start. Agent definitions follow the frontmatter convention from `pc/.opencode/agents/pr-review.md`.

- [ ] **Step 1: Create the `nanobyte-services` repo on GitHub**

```bash
gh repo create nanobyte-services --private --description "Firm-wide shared services: SDLC orchestrator, IB gateway, Grafana" --clone
cd nanobyte-services
```

Expected: repo created and cloned to `./nanobyte-services/`.

- [ ] **Step 2: Create the directory structure**

```bash
mkdir -p sdlc/opencode/agents sdlc/opencode/skills/planning sdlc/worker/src sdlc/worker/test sdlc/runner sdlc/scripts sdlc/tests
```

- [ ] **Step 3: Write `sdlc/opencode/opencode.json`**

```json
{
  "provider": {
    "opencode-go": {
      "type": "api",
      "url": "https://api.opencode.ai"
    }
  },
  "model": {
    "default": "opencode-go/glm-5.2",
    "planner": "opencode-go/glm-5.2",
    "build": "opencode-go/deepseek-v4-pro",
    "tester": "opencode-go/glm-5.2",
    "bugfixer": "opencode-go/deepseek-v4-pro",
    "deployer": "opencode-go/glm-5.2",
    "pr-review": "opencode-go/deepseek-v4-pro"
  }
}
```

Note: verify the exact `opencode.json` schema against https://opencode.ai/docs/config/ during execution. The intent is to centralize provider + per-agent model config so it's not hardcoded across `review-config.yml`, `agents/*.md`, `llm-review.sh`, and `pr-review.yml`.

- [ ] **Step 4: Write `sdlc/opencode/agents/planner.md`**

```markdown
---
name: planner
model: opencode-go/glm-5.2
mode: subagent
description: Planning agent that triages issues into scope documents and breaks features into module plans with dependency annotations.
---

You are the Planning agent in an autonomous SDLC pipeline.
You run non-interactively in CI. You plan, you do NOT write implementation code or repo files.

## Inputs (env vars)
- ISSUE_NUMBER: the GitHub issue to plan
- GH_TOKEN: for gh CLI (read issue, post/edit comments, create child issues, update Projects board)
- PHASE: "triaging" or "planning"

## If PHASE == "triaging"
1. Read the issue: `gh issue view $ISSUE_NUMBER`
2. Read docs/reference/INDEX.md for codebase context + relevant reference docs
3. Post a Scope Document comment (problem, in-scope, out-of-scope, affected modules, open questions)
   - Header: "## Scope Document (latest — updated <date>)"
   - If a Scope Document comment already exists (rejection loop), EDIT it in place using `gh issue edit` or the GitHub API
4. Move card to "Scope Review" via GraphQL

## If PHASE == "planning"
1. Read the approved scope from the issue's Scope Document comment
2. Break into modules; for each: create child issue + post Plan comment on it
3. Annotate dependencies (Depends on / Parallel) in child issue bodies
4. Post Plan Summary comment on parent issue (edit in place if exists)
5. Move parent card to "Plan Review" via GraphQL

## Scope Document format
```
## Scope Document (latest — updated <date>)

### Problem statement
<1-2 sentences>

### Proposed scope
- <capability 1>
- <capability 2>

### Explicitly out of scope
- <not building X because...>

### Affected modules
- <module from docs/reference/backend-services.md or frontend-map.md>

### Open questions
- <anything ambiguous needing human input>
```

## Plan comment format (on child issues)
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

### Dependencies
Depends on: #NNN  (or "Parallel: yes")
```

## Rules
- NEVER write implementation code or create files in the repo — you plan only
- ALWAYS read existing code/docs before proposing changes
- Post scope/plans as issue comments, NOT as repo files
- Edit existing comments in place when revising (don't create new comment threads)
- If scope is unclear, post open questions and move card to "Scope Review"
```

- [ ] **Step 5: Write `sdlc/opencode/agents/build.md`**

```markdown
---
name: build
model: opencode-go/deepseek-v4-pro
mode: subagent
description: Build agent that implements a module plan, raises a PR, and addresses review findings in the autonomous review loop.
---

You are the Build agent in an autonomous SDLC pipeline.
You run non-interactively in CI.

## Inputs (env vars)
- ISSUE_NUMBER: the GitHub issue with the module plan
- GH_TOKEN: for gh CLI (read plan comment, create branch, push, open PR, read review findings)
- PHASE: "executing" or "review-fix"
- PR_NUMBER: (review-fix only) the PR to fix findings on
- REVIEW_ROUND: (review-fix only) current round number

## If PHASE == "executing"
1. Read the module plan from the issue's Plan comment: `gh issue view $ISSUE_NUMBER --comments`
2. Create a feature branch: `git checkout -b feat/<task-slug>`
3. Implement each checkbox task from the plan
4. Run tests (Docker for backend, npm for frontend)
5. Commit and push
6. Open a PR: `gh pr create --title "<title>" --body "Closes #$ISSUE_NUMBER"`
7. Move card to "In Review" via GraphQL

## If PHASE == "review-fix"
1. Read the PR's review findings comment: `gh pr view $PR_NUMBER --comments`
2. Parse the last-reviewed-sha from the hidden HTML comment
3. For each finding:
   - RULE 1: NEVER blindly revert a flagged change — fix it properly OR add a comment justifying the approach and leave the change
   - RULE 2: If the same (category, location) appeared in the previous round, escalate — don't repeat the same fix
4. Push commits to the PR branch
5. The PR push will re-trigger build.yml + pr-review.yml (delta-only review)

## Rules
- Follow CONTRIBUTING.md constraints (MockK not Mockito, no Tailwind, apiFetch(), Vitest, etc.)
- Run tests before pushing
- Write KDoc/JSDoc/docstrings for new public declarations (structural checker enforces this)
- Update documentation if the change affects README or reference docs
```

- [ ] **Step 6: Write stub agents for tester, bugfixer, deployer**

Write `sdlc/opencode/agents/tester.md`:

```markdown
---
name: tester
model: opencode-go/glm-5.2
mode: subagent
description: Tester agent that deploys to UAT and executes test plans. (Stub — full spec in future design cycle.)
---

You are the Tester agent in an autonomous SDLC pipeline.
You run non-interactively in CI.

## Inputs (env vars)
- ISSUE_NUMBER: the GitHub issue
- GH_TOKEN: for gh CLI

## On start
1. Read the module's acceptance criteria from the issue
2. Deploy to UAT (trigger deploy.yml with environment=uat)
3. Execute the test plan against the UAT deployment
4. If tests pass: move card to "Ready to Publish"
5. If tests fail: create bug issues with details, move card to "Bug Fixing"

Note: This is a stub. Full specification will be in a future design doc.
```

Write `sdlc/opencode/agents/bugfixer.md`:

```markdown
---
name: bugfixer
model: opencode-go/deepseek-v4-pro
mode: subagent
description: Bug-fixer agent that reviews bug reports, creates fix plans, and raises PRs. (Stub — full spec in future design cycle.)
---

You are the Bug-fixer agent in an autonomous SDLC pipeline.
You run non-interactively in CI.

## Inputs (env vars)
- ISSUE_NUMBER: the bug issue
- GH_TOKEN: for gh CLI

## On start
1. Read the bug report from the issue
2. Reproduce the bug
3. Create a fix plan
4. Implement the fix on a feature branch
5. Raise a PR (enters the autonomous review loop)

Note: This is a stub. Full specification will be in a future design doc.
```

Write `sdlc/opencode/agents/deployer.md`:

```markdown
---
name: deployer
model: opencode-go/glm-5.2
mode: subagent
description: Deployer agent that bumps version and deploys to production. (Stub — full spec in future design cycle.)
---

You are the Deployer agent in an autonomous SDLC pipeline.
You run non-interactively in CI.

## Inputs (env vars)
- ISSUE_NUMBER: the issue
- GH_TOKEN: for gh CLI

## On start
1. Bump the version
2. Trigger deploy.yml with environment=prod
3. Verify health checks pass
4. Move card to "Done"

Note: This is a stub. Full specification will be in a future design doc.
```

- [ ] **Step 7: Write `sdlc/opencode/skills/planning/SKILL.md`**

```markdown
---
name: planning
description: Triage a GitHub issue into a scope document and break features into module plans with dependency annotations. Use when planning work before implementation.
---

## What I do
- Read a GitHub issue and explore the codebase to understand what exists
- Draft a scope document (problem, in-scope, out-of-scope, affected modules, open questions)
- Break approved scope into modules, each with a plan and dependency annotation
- Post scope and plans as issue comments (edited in place, not repo files)
- Create child issues for multi-module features

## When to use
Triggered when a Projects card moves to "Triaging" or "Planning" lane.
Can be invoked manually: `opencode run --agent planner`

## Scope document structure
- Problem statement (1-2 sentences)
- Proposed scope (bullet list)
- Explicitly out of scope (prevents scope creep)
- Affected modules (from docs/reference/*)
- Open questions (for human input)

## Plan structure (per module)
- Acceptance criteria (checkboxes)
- Technical approach (files, tech stack)
- Tasks (checkboxes)
- Dependencies (Depends on #NNN or Parallel: yes)

## Rules
- NEVER write implementation code or repo files — planning only
- ALWAYS read existing code/docs before proposing changes
- Edit existing comments in place when revising
- Use `gh` CLI for all GitHub operations
```

- [ ] **Step 8: Write `README.md` and commit**

Write `README.md`:

```markdown
# nanobyte-services

Firm-wide shared services.

## Components

### SDLC Orchestrator (`sdlc/`)
Autonomous software development lifecycle pipeline. See `sdlc/README.md` for setup.

### Future components
- `ib-gateway/` — IBKR TWS API gateway
- `grafana/` — dashboards and alerting
```

```bash
git add -A
git commit -m "feat: create nanobyte-services repo with SDLC orchestrator opencode config

Shared opencode config bundle (agents + skills + opencode.json) for the
autonomous SDLC pipeline. Agents: planner (full), build (full), tester/
bugfixer/deployer (stubs). Planning skill. Consumed by repos via
git clone --depth 1 at CI job start."
git push -u origin main
```

---

### Task 2: Create the Stage 1 label-trigger workflow in `pc`

**Files:**
- Create: `.github/workflows/sdlc-agent.yml`

**Interfaces:**
- Consumes: `nanobyte-services/sdlc/opencode/` (bootstrapped at job start)
- Produces: `sdlc-agent.yml` workflow that runs `opencode run --agent planner` when an issue is labeled `agent-ready`. Stage 1 uses GitHub-hosted runner; Stage 2 switches to `self-hosted`; Stage 3 adds `repository_dispatch` trigger.

- [ ] **Step 1: Write `.github/workflows/sdlc-agent.yml`**

```yaml
name: SDLC Agent

on:
  issues:
    types: [labeled]
  repository_dispatch:
    types: [sdlc-phase]

jobs:
  run-agent:
    # Stage 1: GitHub-hosted runner. Stage 2 changes this to: self-hosted
    runs-on: ubuntu-latest
    permissions:
      contents: write
      issues: write
      pull-requests: write
    # Only run on agent-ready label (Stage 1) or repository_dispatch (Stage 3)
    if: |
      (github.event_name == 'issues' && github.event.label.name == 'agent-ready') ||
      github.event_name == 'repository_dispatch'
    steps:
      - name: Checkout repo
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Bootstrap shared opencode config
        run: |
          git clone --depth 1 https://github.com/${{ github.repository_owner }}/nanobyte-services /tmp/nbs
          # Copy shared config, then let repo-specific .opencode/ override
          cp -r /tmp/nbs/sdlc/opencode/* .opencode/ 2>/dev/null || true

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: "20"

      - name: Install opencode
        run: npm install -g opencode-ai

      - name: Configure opencode auth
        run: |
          mkdir -p ~/.local/share/opencode
          echo '${{ secrets.OPENCODE_AUTH_JSON }}' > ~/.local/share/opencode/auth.json

      - name: Determine agent + model + issue
        id: params
        run: |
          if [ "${{ github.event_name }}" = "issues" ]; then
            echo "agent=planner" >> $GITHUB_OUTPUT
            echo "model=opencode-go/glm-5.2" >> $GITHUB_OUTPUT
            echo "issue_number=${{ github.event.issue.number }}" >> $GITHUB_OUTPUT
            echo "phase=triaging" >> $GITHUB_OUTPUT
          else
            echo "agent=${{ github.event.client_payload.agent }}" >> $GITHUB_OUTPUT
            echo "model=${{ github.event.client_payload.model }}" >> $GITHUB_OUTPUT
            echo "issue_number=${{ github.event.client_payload.issue_number }}" >> $GITHUB_OUTPUT
            echo "phase=${{ github.event.client_payload.phase }}" >> $GITHUB_OUTPUT
          fi

      - name: Run phase agent
        run: |
          opencode run \
            --agent "${{ steps.params.outputs.agent }}" \
            --model "${{ steps.params.outputs.model }}" \
            --format json \
            --dangerously-skip-permissions \
            "Process issue #${{ steps.params.outputs.issue_number }} — phase: ${{ steps.params.outputs.phase }}"
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          ISSUE_NUMBER: ${{ steps.params.outputs.issue_number }}
          PHASE: ${{ steps.params.outputs.phase }}
          GITHUB_REPOSITORY: ${{ github.repository }}
```

- [ ] **Step 2: Add the `OPENCODE_AUTH_JSON` secret to the `pc` repo**

```bash
# Generate the auth.json content from your opencode-go API key
# (replace YOUR_API_KEY with your actual key from opencode.ai/auth)
echo '{"opencode-go":{"type":"api","key":"YOUR_API_KEY"}}' | gh secret set OPENCODE_AUTH_JSON --repo saurabhbilakhia/pc
```

Expected: `✓ Set Actions secret OPENCODE_AUTH_JSON for saurabhbilakhia/pc`

- [ ] **Step 3: Create the `agent-ready` label in `pc`**

```bash
gh label create agent-ready --color 0E8A16 --description "Trigger the SDLC planner agent" --repo saurabhbilakhia/pc
```

Expected: `✓ Created label agent-ready in saurabhbilakhia/pc`

- [ ] **Step 4: Commit and push**

```bash
git add .github/workflows/sdlc-agent.yml
git commit -m "feat: add SDLC agent workflow (Stage 1 — label-triggered planner)

Triggers opencode run --agent planner when an issue is labeled
'agent-ready'. Bootstraps shared opencode config from nanobyte-services.
Stage 1 uses GitHub-hosted runner; Stage 2 will switch to self-hosted."
git push
```

---

### Task 3: Test the planner agent end-to-end (Stage 1 validation)

**Files:**
- No new files — this is a validation task

- [ ] **Step 1: Create a test issue in `pc`**

```bash
gh issue create --repo saurabhbilakhia/pc --title "Test: SDLC planner agent" --body "Add a health check endpoint to the portfolio service that returns 200 OK with service version. This is a test issue for the autonomous SDLC pipeline."
```

Expected: issue created with a number (note it, e.g., `#42`).

- [ ] **Step 2: Label the issue `agent-ready`**

```bash
gh issue edit <ISSUE_NUMBER> --add-label agent-ready --repo saurabhbilakhia/pc
```

- [ ] **Step 3: Verify the workflow triggered**

```bash
gh run list --workflow sdlc-agent.yml --repo saurabhbilakhia/pc --limit 1
```

Expected: a run with status `in_progress` or `completed`.

- [ ] **Step 4: Verify the planner agent posted a scope comment**

```bash
gh issue view <ISSUE_NUMBER> --repo saurabhbilakhia/pc --comments
```

Expected: a comment with header `## Scope Document (latest — updated <date>)` containing problem statement, proposed scope, out of scope, affected modules, and open questions sections.

- [ ] **Step 5: If the run failed, check logs**

```bash
gh run view --log --repo saurabhbilakhia/pc
```

Common failures: `OPENCODE_AUTH_JSON` secret not set, `nanobyte-services` repo not accessible, opencode not installed. Fix and re-trigger by removing and re-adding the label.

---

## Stage 2 — Self-Hosted Runner

Move execution to the home Debian server. Kills the 6h wall + all minute billing. Docker available for containerized backend tests.

---

### Task 4: Write runner setup scripts

**Files:**
- Create: `nanobyte-services/sdlc/runner/setup.sh`
- Create: `nanobyte-services/sdlc/runner/ephemeral-supervisor.sh`

**Interfaces:**
- Produces: `setup.sh` — downloads, configures, and installs a GitHub Actions self-hosted runner as a systemd service. `ephemeral-supervisor.sh` — wraps the runner in ephemeral mode (one job per runner instance, fresh provision each time).

- [ ] **Step 1: Write `sdlc/runner/setup.sh`**

```bash
#!/bin/bash
set -euo pipefail

usage() {
  echo "Usage: $0 --token <TOKEN> [--org <ORG>] [--repo <OWNER/REPO>] [--ephemeral]"
  echo ""
  echo "Download, configure, and install a GitHub Actions self-hosted runner."
  echo ""
  echo "  --token      Registration token from GitHub (org or repo settings)"
  echo "  --org        Register at org level (serves all repos in the org)"
  echo "  --repo       Register at repo level (OWNER/REPO format)"
  echo "  --ephemeral  Configure for ephemeral mode (one job, then de-register)"
  echo ""
  echo "Exactly one of --org or --repo is required."
}

die() { usage; exit 1; }

TOKEN=""
ORG=""
REPO=""
EPHEMERAL=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --token) TOKEN="$2"; shift 2 ;;
    --org) ORG="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --ephemeral) EPHEMERAL=true; shift ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown: $1"; die ;;
  esac
done

[ -z "$TOKEN" ] && { echo "Error: --token is required"; die; }
[ -z "$ORG" ] && [ -z "$REPO" ] && { echo "Error: --org or --repo is required"; die; }
[ -n "$ORG" ] && [ -n "$REPO" ] && { echo "Error: --org and --repo are mutually exclusive"; die; }

RUNNER_DIR="$HOME/actions-runner"
mkdir -p "$RUNNER_DIR"
cd "$RUNNER_DIR"

# Download the latest runner package
if [ ! -f "runner.tar.gz" ]; then
  echo "Downloading runner package..."
  curl -o runner.tar.gz -L https://github.com/actions/runner/releases/download/v2.317.0/actions-runner-linux-x64-2.317.0.tar.gz
fi

echo "Extracting..."
tar xzf runner.tar.gz

# Configure
CONFIG_ARGS=""
if [ -n "$ORG" ]; then
  CONFIG_ARGS="--url https://github.com/$ORG"
elif [ -n "$REPO" ]; then
  CONFIG_ARGS="--url https://github.com/$REPO"
fi

if [ "$EPHEMERAL" = true ]; then
  CONFIG_ARGS="$CONFIG_ARGS --ephemeral"
fi

echo "Configuring runner..."
./config.sh --token "$TOKEN" $CONFIG_ARGS --name "home-runner" --labels "self-hosted,linux,x64,home-server"

# Install as systemd service
echo "Installing systemd service..."
sudo ./svc.sh install

echo "Starting service..."
sudo ./svc.sh start

echo "Runner installed and started. Check status with: sudo ./svc.sh status"
```

- [ ] **Step 2: Write `sdlc/runner/ephemeral-supervisor.sh`**

```bash
#!/bin/bash
set -euo pipefail

# Ephemeral runner supervisor: provisions a fresh runner for each job.
# The runner handles one job (--ephemeral --once), then exits.
# This script restarts it to provision a new instance.
# Intended to be run as a systemd service that restarts on exit.

RUNNER_DIR="$HOME/actions-runner"
cd "$RUNNER_DIR"

# Re-register with a fresh token each cycle
# Token must be fetched from GitHub API (requires GH_TOKEN env var)
if [ -z "${GH_TOKEN:-}" ]; then
  echo "Error: GH_TOKEN env var required for ephemeral supervisor" >&2
  exit 1
fi

ORG="${RUNNER_ORG:-saurabhbilakhia}"

# Fetch a new registration token
TOKEN=$(curl -s -X POST \
  -H "Authorization: token $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/orgs/$ORG/actions/runners/registration-token" \
  | jq -r '.token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "Error: failed to fetch registration token" >&2
  exit 1
fi

# Remove old config if present (fresh start each cycle)
rm -f .runner .credentials

# Configure as ephemeral
./config.sh --token "$TOKEN" --url "https://github.com/$ORG" \
  --name "home-runner-ephemeral" \
  --labels "self-hosted,linux,x64,home-server" \
  --ephemeral --unattended

# Run one job, then exit (systemd will restart this script)
./run.sh --once

# Exit code 0 = job completed; systemd restarts → fresh runner
# Exit code non-zero = error; systemd restarts after backoff
```

- [ ] **Step 3: Commit and push**

```bash
git add sdlc/runner/
git commit -m "feat: add self-hosted runner setup and ephemeral supervisor scripts

setup.sh: download, configure, install as systemd service.
ephemeral-supervisor.sh: provisions fresh runner per job (--ephemeral --once),
systemd restarts on exit for clean state between agent sessions."
git push
```

---

### Task 5: Register the runner and switch the workflow to self-hosted

**Files:**
- Modify: `pc/.github/workflows/sdlc-agent.yml:14` (change `runs-on: ubuntu-latest` → `runs-on: self-hosted`)

**Note:** This task requires access to the home Debian server. Steps 1-3 are manual (run on the home server); Steps 4-5 are in the `pc` repo.

- [ ] **Step 1: Get a registration token from GitHub**

```bash
# On your local machine (with gh CLI authenticated):
gh api -X POST orgs/saurabhbilakhia/actions/runners/registration-token --jq '.token'
```

Expected: a token string (valid for ~1 hour).

- [ ] **Step 2: Run setup.sh on the home server**

```bash
# SSH to the home server, then:
cd /tmp
git clone --depth 1 https://github.com/saurabhbilakhia/nanobyte-services.git
cd nanobyte-services/sdlc/runner
chmod +x setup.sh
./setup.sh --token <TOKEN_FROM_STEP_1> --org saurabhbilakhia
```

Expected: runner installed and started as a systemd service.

- [ ] **Step 3: Verify the runner is online**

```bash
gh api orgs/saurabhbilakhia/actions/runners --jq '.runners[] | select(.name == "home-runner") | {name, status, busy}'
```

Expected: `{"name": "home-runner", "status": "online", "busy": false}`

- [ ] **Step 4: Switch `sdlc-agent.yml` to self-hosted**

In `pc/.github/workflows/sdlc-agent.yml`, change:

```yaml
    # Stage 1: GitHub-hosted runner. Stage 2 changes this to: self-hosted
    runs-on: ubuntu-latest
```

to:

```yaml
    runs-on: self-hosted
```

- [ ] **Step 5: Commit, push, and verify**

```bash
git add .github/workflows/sdlc-agent.yml
git commit -m "feat: switch SDLC agent to self-hosted runner (Stage 2)

Moves execution from GitHub-hosted to the home Debian server.
Eliminates 6h job limit and Actions minute billing.
Docker available for containerized backend tests."
git push
```

Verify by labeling a new test issue `agent-ready` and confirming the run executes on `self-hosted`:

```bash
gh run list --workflow sdlc-agent.yml --repo saurabhbilakhia/pc --limit 1
gh run view <RUN_ID> --repo saurabhbilakhia/pc --json runnerName,conclusion
```

Expected: `runnerName` includes `home-runner`.

---

## Stage 3 — Full Control Plane: GitHub App + Cloudflare Worker + Projects Board

---

### Task 6: Write the Cloudflare Worker — HMAC verification + dedupe modules

**Files:**
- Create: `nanobyte-services/sdlc/worker/package.json`
- Create: `nanobyte-services/sdlc/worker/tsconfig.json`
- Create: `nanobyte-services/sdlc/worker/src/verify.ts`
- Create: `nanobyte-services/sdlc/worker/src/dedupe.ts`
- Create: `nanobyte-services/sdlc/worker/test/verify.test.ts`
- Create: `nanobyte-services/sdlc/worker/test/dedupe.test.ts`

**Interfaces:**
- `verifySignature(payload: string, signature: string, secret: string): Promise<boolean>` — verifies HMAC-SHA256
- `isDuplicate(deliveryId: string, kv: KVNamespace): Promise<boolean>` — checks/stores delivery ID in KV with 10-min TTL

- [ ] **Step 1: Write `package.json`**

```json
{
  "name": "sdlc-worker",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest",
    "deploy": "wrangler deploy",
    "dev": "wrangler dev"
  },
  "devDependencies": {
    "@cloudflare/workers-types": "^4.20240620.0",
    "typescript": "^5.5.0",
    "vitest": "^2.0.0",
    "wrangler": "^3.60.0"
  }
}
```

- [ ] **Step 2: Write `tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "bundler",
    "lib": ["ES2022"],
    "types": ["@cloudflare/workers-types", "vitest/globals"],
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  },
  "include": ["src/**/*", "test/**/*"]
}
```

- [ ] **Step 3: Write the failing test for `verify.ts`**

Create `test/verify.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import { verifySignature } from '../src/verify';

describe('verifySignature', () => {
  const secret = 'test-webhook-secret';

  it('accepts a valid HMAC-SHA256 signature', async () => {
    const payload = '{"action":"moved","project":{"id":1}}';
    // Compute valid signature using Web Crypto API
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
      'raw', encoder.encode(secret),
      { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
    );
    const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(payload));
    const signature = 'sha256=' + Array.from(new Uint8Array(sig))
      .map(b => b.toString(16).padStart(2, '0')).join('');

    const result = await verifySignature(payload, signature, secret);
    expect(result).toBe(true);
  });

  it('rejects an invalid signature', async () => {
    const payload = '{"action":"moved"}';
    const badSignature = 'sha256=deadbeef';
    const result = await verifySignature(payload, badSignature, secret);
    expect(result).toBe(false);
  });

  it('rejects a signature without the sha256= prefix', async () => {
    const payload = '{"action":"moved"}';
    const result = await verifySignature(payload, 'deadbeef', secret);
    expect(result).toBe(false);
  });

  it('rejects when secret does not match', async () => {
    const payload = '{"action":"moved"}';
    // Compute signature with wrong secret
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
      'raw', encoder.encode('wrong-secret'),
      { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
    );
    const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(payload));
    const signature = 'sha256=' + Array.from(new Uint8Array(sig))
      .map(b => b.toString(16).padStart(2, '0')).join('');

    const result = await verifySignature(payload, signature, secret);
    expect(result).toBe(false);
  });
});
```

- [ ] **Step 4: Run the test to verify it fails**

```bash
cd nanobyte-services/sdlc/worker
npm install
npx vitest run test/verify.test.ts
```

Expected: FAIL — `Cannot find module '../src/verify'`

- [ ] **Step 5: Implement `src/verify.ts`**

```typescript
/**
 * Verify a GitHub webhook HMAC-SHA256 signature using the Web Crypto API.
 * @param payload - The raw request body as a string
 * @param signature - The X-Hub-Signature-256 header value (format: "sha256=<hex>")
 * @param secret - The webhook secret
 * @returns true if the signature is valid
 */
export async function verifySignature(
  payload: string,
  signature: string,
  secret: string,
): Promise<boolean> {
  if (!signature.startsWith('sha256=')) {
    return false;
  }
  const expectedSig = signature.slice(7);

  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(payload));
  const computedHex = Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');

  return timingSafeEqual(expectedSig, computedHex);
}

/**
 * Constant-time string comparison to prevent timing attacks.
 */
function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let result = 0;
  for (let i = 0; i < a.length; i++) {
    result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return result === 0;
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
npx vitest run test/verify.test.ts
```

Expected: PASS — all 4 tests pass.

- [ ] **Step 7: Write the failing test for `dedupe.ts`**

Create `test/dedupe.test.ts`:

```typescript
import { describe, it, expect, vi } from 'vitest';
import { isDuplicate } from '../src/dedupe';

// Mock KV namespace
function createMockKV(): KVNamespace {
  const store = new Map<string, string>();
  return {
    get: vi.fn((key: string) => Promise.resolve(store.get(key) ?? null)),
    put: vi.fn((key: string, value: string) => {
      store.set(key, value);
      return Promise.resolve();
    }),
    delete: vi.fn((key: string) => {
      store.delete(key);
      return Promise.resolve();
    }),
    list: vi.fn(() => Promise.resolve({ list: [], done: true })),
  } as unknown as KVNamespace;
}

describe('isDuplicate', () => {
  it('returns false for a new delivery ID', async () => {
    const kv = createMockKV();
    const result = await isDuplicate('delivery-123', kv);
    expect(result).toBe(false);
  });

  it('returns true for a duplicate delivery ID', async () => {
    const kv = createMockKV();
    await isDuplicate('delivery-123', kv); // first call stores it
    const result = await isDuplicate('delivery-123', kv); // second call finds it
    expect(result).toBe(true);
  });

  it('stores the delivery ID with a TTL', async () => {
    const kv = createMockKV();
    await isDuplicate('delivery-456', kv);
    expect(kv.put).toHaveBeenCalledWith(
      'delivery-456',
      expect.any(String),
      expect.objectContaining({ expirationTtl: 600 }),
    );
  });
});
```

- [ ] **Step 8: Run the test to verify it fails**

```bash
npx vitest run test/dedupe.test.ts
```

Expected: FAIL — `Cannot find module '../src/dedupe'`

- [ ] **Step 9: Implement `src/dedupe.ts`**

```typescript
/**
 * Check if a webhook delivery has already been processed.
 * Uses KV with a 10-minute (600 second) TTL for automatic cleanup.
 * @param deliveryId - The X-GitHub-Delivery header value
 * @param kv - Cloudflare KV namespace
 * @returns true if this delivery ID has already been seen
 */
export async function isDuplicate(
  deliveryId: string,
  kv: KVNamespace,
): Promise<boolean> {
  const existing = await kv.get(`delivery:${deliveryId}`);
  if (existing !== null) {
    return true;
  }
  await kv.put(`delivery:${deliveryId}`, '1', {
    expirationTtl: 600, // 10 minutes
  });
  return false;
}
```

- [ ] **Step 10: Run all tests to verify they pass**

```bash
npx vitest run
```

Expected: PASS — all verify + dedupe tests pass.

- [ ] **Step 11: Commit**

```bash
git add sdlc/worker/package.json sdlc/worker/tsconfig.json sdlc/worker/src/verify.ts sdlc/worker/src/dedupe.ts sdlc/worker/test/
git commit -m "feat: add Cloudflare Worker HMAC verification and dedupe modules

verify.ts: HMAC-SHA256 signature verification via Web Crypto API with
constant-time comparison. dedupe.ts: KV-based webhook deduplication
with 10-min TTL. Both have unit tests."
git push
```

---

### Task 7: Write the Cloudflare Worker — routing + dispatch modules

**Files:**
- Create: `nanobyte-services/sdlc/worker/src/routing.ts`
- Create: `nanobyte-services/sdlc/worker/src/dispatch.ts`
- Create: `nanobyte-services/sdlc/worker/test/routing.test.ts`

**Interfaces:**
- `parseRoutingConfig(yamlText: string): RoutingConfig` — parses `routing.yml` into a typed object
- `routeEvent(event: WebhookEvent, config: RoutingConfig): DispatchParams | null` — maps a webhook event to dispatch parameters (or null if no dispatch needed)
- `fireDispatch(params: DispatchParams, token: string): Promise<Response>` — fires `repository_dispatch` to the target repo
- `DispatchParams` type: `{ repo: string; eventType: string; clientPayload: ClientPayload }`
- `ClientPayload` type: `{ issue_number: number; phase: string; agent: string; model: string; card_id?: string; action: string }`

- [ ] **Step 1: Write the failing test for `routing.ts`**

Create `test/routing.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import { parseRoutingConfig, routeEvent } from '../src/routing';

const ROUTING_YAML = `
lanes:
  Triaging:    { phase: planning,  agent: planner,  model: opencode-go/glm-5.2 }
  Planning:    { phase: planning,  agent: planner,  model: opencode-go/glm-5.2 }
  Executing:   { phase: execution, agent: build,    model: opencode-go/deepseek-v4-pro }
  Testing:     { phase: testing,   agent: tester,   model: opencode-go/glm-5.2 }
  Bug Fixing:  { phase: bugfix,    agent: bugfixer, model: opencode-go/deepseek-v4-pro }
  Publishing:  { phase: publish,   agent: deployer, model: opencode-go/glm-5.2 }
`;

describe('parseRoutingConfig', () => {
  it('parses lane mappings', () => {
    const config = parseRoutingConfig(ROUTING_YAML);
    expect(config.lanes['Triaging']).toEqual({
      phase: 'planning',
      agent: 'planner',
      model: 'opencode-go/glm-5.2',
    });
    expect(config.lanes['Executing']).toEqual({
      phase: 'execution',
      agent: 'build',
      model: 'opencode-go/deepseek-v4-pro',
    });
  });

  it('returns empty config for invalid YAML', () => {
    const config = parseRoutingConfig('not valid yaml: [');
    expect(config.lanes).toEqual({});
  });
});

describe('routeEvent', () => {
  const config = parseRoutingConfig(ROUTING_YAML);

  it('routes a projects_v2_item moved event to Triaging', () => {
    const event = {
      type: 'projects_v2_item',
      action: 'moved',
      laneName: 'Triaging',
      issueNumber: 42,
      repo: 'saurabhbilakhia/pc',
      cardId: 'PVTI_xxx',
    };
    const result = routeEvent(event, config);
    expect(result).not.toBeNull();
    expect(result!.clientPayload.agent).toBe('planner');
    expect(result!.clientPayload.phase).toBe('planning');
    expect(result!.clientPayload.issue_number).toBe(42);
    expect(result!.repo).toBe('saurabhbilakhia/pc');
  });

  it('returns null for human-gated lanes (Scope Review)', () => {
    const event = {
      type: 'projects_v2_item',
      action: 'moved',
      laneName: 'Scope Review',
      issueNumber: 42,
      repo: 'saurabhbilakhia/pc',
      cardId: 'PVTI_xxx',
    };
    const result = routeEvent(event, config);
    expect(result).toBeNull();
  });

  it('returns null for lanes not in routing config (Backlog)', () => {
    const event = {
      type: 'projects_v2_item',
      action: 'moved',
      laneName: 'Backlog',
      issueNumber: 42,
      repo: 'saurabhbilakhia/pc',
      cardId: 'PVTI_xxx',
    };
    const result = routeEvent(event, config);
    expect(result).toBeNull();
  });

  it('routes an issues labeled event (Stage 1 fallback)', () => {
    const event = {
      type: 'issues',
      action: 'labeled',
      labelName: 'agent-ready',
      issueNumber: 42,
      repo: 'saurabhbilakhia/pc',
    };
    const result = routeEvent(event, config);
    expect(result).not.toBeNull();
    expect(result!.clientPayload.agent).toBe('planner');
    expect(result!.clientPayload.phase).toBe('planning');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx vitest run test/routing.test.ts
```

Expected: FAIL — `Cannot find module '../src/routing'`

- [ ] **Step 3: Implement `src/routing.ts`**

```typescript
/**
 * Routing config types.
 */
export interface LaneMapping {
  phase: string;
  agent: string;
  model: string;
}

export interface RoutingConfig {
  lanes: Record<string, LaneMapping>;
}

export interface WebhookEvent {
  type: string;
  action: string;
  laneName?: string;
  labelName?: string;
  issueNumber: number;
  repo: string;
  cardId?: string;
}

export interface ClientPayload {
  issue_number: number;
  phase: string;
  agent: string;
  model: string;
  card_id?: string;
  action: string;
}

export interface DispatchParams {
  repo: string;
  eventType: string;
  clientPayload: ClientPayload;
}

/**
 * Parse a routing.yml config string into a typed object.
 * Uses a simple YAML parser (no dependency) — handles the flat
 * "lanes: { key: { phase, agent, model } }" structure.
 */
export function parseRoutingConfig(yamlText: string): RoutingConfig {
  const lanes: Record<string, LaneMapping> = {};
  try {
    // Simple parser for our specific YAML structure.
    // Matches: LaneName: { phase: X, agent: Y, model: Z }
    const laneRegex = /^(\s+)(\S+):\s*\{\s*phase:\s*(\S+),\s*agent:\s*(\S+),\s*model:\s*(\S+)\s*\}/gm;
    let match;
    while ((match = laneRegex.exec(yamlText)) !== null) {
      const laneName = match[2];
      lanes[laneName] = {
        phase: match[3],
        agent: match[4],
        model: match[5],
      };
    }
  } catch {
    // Return empty config on parse error
  }
  return { lanes };
}

/**
 * Map a webhook event to dispatch parameters.
 * Returns null if no dispatch is needed (human-gated lane, unknown lane, etc.)
 */
export function routeEvent(event: WebhookEvent, config: RoutingConfig): DispatchParams | null {
  // Stage 1 fallback: issues labeled "agent-ready"
  if (event.type === 'issues' && event.action === 'labeled' && event.labelName === 'agent-ready') {
    const triaging = config.lanes['Triaging'];
    if (!triaging) return null;
    return {
      repo: event.repo,
      eventType: 'sdlc-phase',
      clientPayload: {
        issue_number: event.issueNumber,
        phase: triaging.phase,
        agent: triaging.agent,
        model: triaging.model,
        action: 'triaging',
      },
    };
  }

  // Stage 3: projects_v2_item moved to a lane
  if (event.type === 'projects_v2_item' && event.action === 'moved' && event.laneName) {
    const mapping = config.lanes[event.laneName];
    if (!mapping) return null; // human-gated or unknown lane
    return {
      repo: event.repo,
      eventType: 'sdlc-phase',
      clientPayload: {
        issue_number: event.issueNumber,
        phase: mapping.phase,
        agent: mapping.agent,
        model: mapping.model,
        card_id: event.cardId,
        action: event.laneName.toLowerCase().replace(/\s+/g, '_'),
      },
    };
  }

  return null;
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx vitest run test/routing.test.ts
```

Expected: PASS — all 5 tests pass.

- [ ] **Step 5: Implement `src/dispatch.ts`**

```typescript
import type { DispatchParams } from './routing';

/**
 * Fire a repository_dispatch event to a target repo.
 * @param params - The dispatch parameters (repo, event type, client payload)
 * @param token - GitHub installation token or PAT with repo scope
 * @returns The fetch Response from the GitHub API
 */
export async function fireDispatch(
  params: DispatchParams,
  token: string,
): Promise<Response> {
  const [owner, repo] = params.repo.split('/');
  const url = `https://api.github.com/repos/${owner}/${repo}/dispatches`;

  return fetch(url, {
    method: 'POST',
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'X-GitHub-Api-Version': '2022-11-28',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      event_type: params.eventType,
      client_payload: params.clientPayload,
    }),
  });
}
```

- [ ] **Step 6: Run all tests**

```bash
npx vitest run
```

Expected: PASS — all verify + dedupe + routing tests pass.

- [ ] **Step 7: Commit**

```bash
git add sdlc/worker/src/routing.ts sdlc/worker/src/dispatch.ts sdlc/worker/test/routing.test.ts
git commit -m "feat: add Cloudflare Worker routing and dispatch modules

routing.ts: parses routing.yml, maps webhook events (projects_v2_item
moved, issues labeled) to dispatch params. Returns null for human-gated
lanes. dispatch.ts: fires repository_dispatch to target repo via
GitHub API. Both have unit tests."
git push
```

---

### Task 8: Write the Worker main entry + wrangler config + deploy

**Files:**
- Create: `nanobyte-services/sdlc/worker/src/index.ts`
- Create: `nanobyte-services/sdlc/worker/wrangler.toml`
- Create: `nanobyte-services/sdlc/routing.yml`

**Interfaces:**
- The Worker's `fetch` handler: receives webhook → verifies HMAC → dedupes → routes → fires dispatch → returns 200
- Consumes: `verify.ts`, `dedupe.ts`, `routing.ts`, `dispatch.ts`
- Environment bindings: `WEBHOOK_SECRET` (secret), `GITHUB_TOKEN` (secret), `ROUTING_CONFIG` (variable), `DELIVERY_KV` (KV namespace)

- [ ] **Step 1: Write `sdlc/routing.yml`**

```yaml
# Lane → phase → agent → model mapping
# Shared across repos; repos can override via their own .opencode/ config.
# Lanes NOT listed here (Scope Review, Plan Review, In Review, Blocked,
# Ready to Publish, Done, Backlog) are human-gated or pipeline-driven —
# no dispatch on entry.
lanes:
  Triaging:    { phase: planning,  agent: planner,  model: opencode-go/glm-5.2 }
  Planning:    { phase: planning,  agent: planner,  model: opencode-go/glm-5.2 }
  Executing:   { phase: execution, agent: build,    model: opencode-go/deepseek-v4-pro }
  Testing:     { phase: testing,   agent: tester,   model: opencode-go/glm-5.2 }
  Bug Fixing:  { phase: bugfix,    agent: bugfixer, model: opencode-go/deepseek-v4-pro }
  Publishing:  { phase: publish,   agent: deployer, model: opencode-go/glm-5.2 }
```

- [ ] **Step 2: Write `wrangler.toml`**

```toml
name = "sdlc-webhook-receiver"
main = "src/index.ts"
compatibility_date = "2024-06-01"

# KV namespace for webhook deduplication
[[kv_namespaces]]
binding = "DELIVERY_KV"
id = "YOUR_KV_NAMESPACE_ID"  # replace after: wrangler kv namespace create DELIVERY_KV

# Secrets (set via: wrangler secret put WEBHOOK_SECRET, wrangler secret put GITHUB_TOKEN)
# WEBHOOK_SECRET - GitHub App webhook secret
# GITHUB_TOKEN - GitHub App installation token (or PAT with repo scope)

# Variables (set via: wrangler deploy --var ROUTING_CONFIG:...)
# ROUTING_CONFIG - contents of routing.yml (or fetch from nanobyte-services repo at runtime)
```

- [ ] **Step 3: Write `src/index.ts`**

```typescript
import { verifySignature } from './verify';
import { isDuplicate } from './dedupe';
import { parseRoutingConfig, routeEvent, type WebhookEvent, type RoutingConfig } from './routing';
import { fireDispatch } from './dispatch';

export interface Env {
  WEBHOOK_SECRET: string;
  GITHUB_TOKEN: string;
  ROUTING_CONFIG: string;
  DELIVERY_KV: KVNamespace;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== 'POST') {
      return new Response('Method Not Allowed', { status: 405 });
    }

    const payload = await request.text();
    const signature = request.headers.get('X-Hub-Signature-256') ?? '';
    const deliveryId = request.headers.get('X-GitHub-Delivery') ?? '';
    const eventType = request.headers.get('X-GitHub-Event') ?? '';

    // 1. Verify HMAC signature
    const isValid = await verifySignature(payload, signature, env.WEBHOOK_SECRET);
    if (!isValid) {
      console.error('Invalid webhook signature');
      return new Response('Unauthorized', { status: 401 });
    }

    // 2. Dedupe by delivery ID
    if (deliveryId && await isDuplicate(deliveryId, env.DELIVERY_KV)) {
      console.log(`Duplicate delivery ${deliveryId} — skipping`);
      return new Response('OK (duplicate)', { status: 200 });
    }

    // 3. Parse the webhook payload
    const body = JSON.parse(payload);
    const action = body.action ?? '';

    // 4. Build the WebhookEvent from the payload
    const event = buildWebhookEvent(eventType, action, body);
    if (!event) {
      console.log(`Ignoring event ${eventType}/${action} — not routable`);
      return new Response('OK (ignored)', { status: 200 });
    }

    // 5. Route the event
    const config = parseRoutingConfig(env.ROUTING_CONFIG);
    const dispatchParams = routeEvent(event, config);
    if (!dispatchParams) {
      console.log(`No dispatch for ${eventType}/${action} → lane ${event.laneName ?? 'N/A'}`);
      return new Response('OK (no dispatch)', { status: 200 });
    }

    // 6. Fire repository_dispatch
    console.log(`Dispatching to ${dispatchParams.repo}: agent=${dispatchParams.clientPayload.agent}, phase=${dispatchParams.clientPayload.phase}`);
    const response = await fireDispatch(dispatchParams, env.GITHUB_TOKEN);
    if (!response.ok) {
      console.error(`Dispatch failed: ${response.status} ${await response.text()}`);
      return new Response('Dispatch Failed', { status: 502 });
    }

    return new Response('OK', { status: 200 });
  },
};

/**
 * Build a WebhookEvent from a GitHub webhook payload.
 * Returns null if the event is not one we route on.
 */
function buildWebhookEvent(type: string, action: string, body: any): WebhookEvent | null {
  if (type === 'projects_v2_item' && (action === 'moved' || action === 'edited')) {
    // The Projects v2 item webhook doesn't directly include the lane name
    // or issue number in the payload. We need to query the GraphQL API
    // to resolve the card's current lane and linked issue.
    // For now, extract what we can and note that lane resolution
    // requires a GraphQL query (to be implemented in the Worker).
    //
    // The payload includes: project_node_id, item_node_id, item_content_node_id
    // We need to query: item → content (issue) → number, and item → fieldValues → status
    //
    // This is a TODO for the implementation — the Worker needs to make
    // a GraphQL query to resolve the lane name and issue number.
    // For Stage 1, the label-based trigger doesn't need this.
    return null; // Will be implemented when GraphQL resolution is added
  }

  if (type === 'issues' && action === 'labeled') {
    const labelName = body.label?.name ?? '';
    if (labelName !== 'agent-ready') return null;
    return {
      type: 'issues',
      action: 'labeled',
      labelName,
      issueNumber: body.issue?.number ?? 0,
      repo: body.repository?.full_name ?? '',
    };
  }

  return null;
}
```

Note: The `projects_v2_item` event handler returns `null` for now because resolving the lane name + issue number from the webhook payload requires a GraphQL API query. This will be implemented in Task 9 (GitHub App + board setup). The `issues: labeled` path works immediately for Stage 1.

- [ ] **Step 4: Create the KV namespace and deploy**

```bash
cd nanobyte-services/sdlc/worker
npx wrangler kv namespace create DELIVERY_KV
# Note the ID from the output, update wrangler.toml with it
npx wrangler secret put WEBHOOK_SECRET
# Enter your GitHub App webhook secret
npx wrangler secret put GITHUB_TOKEN
# Enter a GitHub PAT or App installation token with repo scope
npx wrangler deploy --var ROUTING_CONFIG:"$(cat ../routing.yml)"
```

Expected: Worker deployed to `https://sdlc-webhook-receiver.<your-subdomain>.workers.dev`.

- [ ] **Step 5: Commit**

```bash
git add sdlc/worker/src/index.ts sdlc/worker/wrangler.toml sdlc/routing.yml
git commit -m "feat: add Cloudflare Worker main entry, wrangler config, routing.yml

Worker receives GitHub webhooks, verifies HMAC, dedupes via KV,
routes events to repository_dispatch. routing.yml maps lanes to
agents/models. projects_v2_item resolution requires GraphQL query
(to be implemented in board setup task). issues:labeled works now."
git push
```

---

### Task 9: Create the GitHub App + Projects board

**Files:**
- Create: `nanobyte-services/sdlc/scripts/create-board.sh`

**Note:** This task is partly manual (GitHub App creation is in the GitHub UI). The board creation is scripted via GraphQL.

- [ ] **Step 1: Create the GitHub App (manual, in GitHub UI)**

1. Go to https://github.com/settings/apps (or org settings → GitHub Apps)
2. Click "New GitHub App"
3. Set:
   - **GitHub App name:** `SDLC Orchestrator`
   - **Homepage URL:** `https://github.com/saurabhbilakhia/nanobyte-services`
   - **Webhook URL:** `https://sdlc-webhook-receiver.<your-subdomain>.workers.dev`
   - **Webhook secret:** (the same secret you set in `wrangler secret put WEBHOOK_SECRET`)
   - **Repository permissions:**
     - Issues: Read & write
     - Pull requests: Read & write
     - Contents: Read & write
     - Actions: Read & write
     - Metadata: Read-only
   - **Organization permissions:**
     - Projects: Read & write
   - **Subscribe to events:** `issues`, `issue_comment`, `pull_request`, `projects_v2_item`
4. Click "Create GitHub App"
5. Generate a private key (download the `.pem` file)
6. Install the App on your account/org ("All repositories" or selected repos including `pc`)

Note the App ID and the private key path — you'll need these to generate installation tokens.

- [ ] **Step 2: Write `sdlc/scripts/create-board.sh`**

```bash
#!/bin/bash
set -euo pipefail

# Create a GitHub Projects v2 board with the 13 SDLC lanes and 5 custom fields.
# Requires: gh CLI authenticated with 'project' scope.
# Usage: ./create-board.sh --owner <OWNER> --title "SDLC Pipeline"

usage() {
  echo "Usage: $0 --owner <OWNER> --title <TITLE>"
  echo ""
  echo "Create a GitHub Projects v2 board with SDLC swim lanes and custom fields."
}

die() { usage; exit 1; }

OWNER=""
TITLE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --owner) OWNER="$2"; shift 2 ;;
    --title) TITLE="$2"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown: $1"; die ;;
  esac
done

[ -z "$OWNER" ] && { echo "Error: --owner is required"; die; }
[ -z "$TITLE" ] && { echo "Error: --title is required"; die; }

# Create the project
PROJECT_ID=$(gh api graphql -f query="
mutation {
  createProjectV2(input: { ownerId: \"$OWNER\", title: \"$TITLE\" }) {
    projectV2 { id }
  }
}" --jq '.data.createProjectV2.projectV2.id')

echo "Created project with ID: $PROJECT_ID"

# Create the Status field (single-select) with 13 lanes
LANES='["Backlog","Triaging","Scope Review","Planning","Plan Review","Executing","In Review","Blocked","Testing","Bug Fixing","Ready to Publish","Publishing","Done"]'

STATUS_FIELD_ID=$(gh api graphql -f query="
mutation {
  createProjectV2Field(input: {
    projectId: \"$PROJECT_ID\"
    dataType: SINGLE_SELECT
    name: \"Status\"
    options: $(echo $LANES | jq -c 'map({name: ., color: GRAY, description: ""})')
  }) {
    projectV2Field { ... on ProjectV2SingleSelectField { id } }
  }
}" --jq '.data.createProjectV2Field.projectV2Field.id')

echo "Created Status field with ID: $STATUS_FIELD_ID"

# Create custom fields
for FIELD_DEF in \
  "Phase:SINGLE_SELECT:Planning,Execution,Review,Testing,BugFix,Publish" \
  "Module:TEXT:" \
  "Priority:SINGLE_SELECT:P0,P1,P2" \
  "PR:TEXT:" \
  "Agent Status:SINGLE_SELECT:idle,running,succeeded,failed,blocked"; do

  IFS=':' read -r NAME TYPE OPTIONS <<< "$FIELD_DEF"

  if [ "$TYPE" = "SINGLE_SELECT" ]; then
    OPTS=$(echo "$OPTIONS" | jq -cR 'split(",") | map({name: ., color: GRAY, description: ""})')
    gh api graphql -f query="
mutation {
  createProjectV2Field(input: {
    projectId: \"$PROJECT_ID\"
    dataType: SINGLE_SELECT
    name: \"$NAME\"
    options: $OPTS
  }) {
    projectV2Field { ... on ProjectV2SingleSelectField { id } }
  }
}" > /dev/null
  else
    gh api graphql -f query="
mutation {
  createProjectV2Field(input: {
    projectId: \"$PROJECT_ID\"
    dataType: $TYPE
    name: \"$NAME\"
  }) {
    projectV2Field { id }
  }
}" > /dev/null
  fi
  echo "Created field: $NAME ($TYPE)"
done

echo ""
echo "Board created successfully."
echo "Project ID: $PROJECT_ID"
echo "Add issues to the board and move cards to trigger the SDLC pipeline."
```

- [ ] **Step 3: Run the board creation script**

```bash
chmod +x sdlc/scripts/create-board.sh
./sdlc/scripts/create-board.sh --owner saurabhbilakhia --title "SDLC Pipeline"
```

Expected: board created with 13 lanes and 5 custom fields. Note the Project ID.

- [ ] **Step 4: Commit**

```bash
git add sdlc/scripts/create-board.sh
git commit -m "feat: add Projects v2 board creation script

create-board.sh: creates a GitHub Projects v2 board with 13 SDLC swim
lanes (Backlog through Done) and 5 custom fields (Phase, Module,
Priority, PR, Agent Status) via GraphQL API."
git push
```

---

### Task 10: Write the review-loop workflow + modify scripts for delta-only review

**Files:**
- Create: `pc/.github/workflows/review-loop.yml`
- Modify: `pc/scripts/llm-review.sh` (add `--since-sha` flag)
- Modify: `pc/scripts/aggregate.sh` (append hidden HTML comments)

**Interfaces:**
- `review-loop.yml`: `workflow_run` listener that fires after `build` + `pr-review` complete. Reads findings, decides: auto-merge, re-dispatch build agent, or block.
- `llm-review.sh --since-sha <SHA>`: reviews only `git diff <SHA>...HEAD` instead of `BASE...HEAD`
- `aggregate.sh`: appends `<!-- review-round: N -->` and `<!-- last-reviewed-sha: <SHA> -->` to the aggregated output

- [ ] **Step 1: Modify `scripts/aggregate.sh` to append hidden HTML comments**

In `pc/scripts/aggregate.sh`, after the `SUMMARY:` line (line 165), add hidden HTML comments. Replace the final block of the output section:

Find this block (around line 162-166):
```bash
  echo ""
  echo "---"
  echo ""
  echo "SUMMARY: $HIGH_COUNT HIGH, $LOW_COUNT LOW"
} > "$AGGREGATED_FILE"
```

Replace with:
```bash
  echo ""
  echo "---"
  echo ""
  echo "SUMMARY: $HIGH_COUNT HIGH, $LOW_COUNT LOW"
  # Hidden HTML comments for the review-loop workflow to parse
  echo "<!-- review-round: ${REVIEW_ROUND:-1} -->"
  echo "<!-- last-reviewed-sha: ${HEAD_SHA:-unknown} -->"
} > "$AGGREGATED_FILE"
```

- [ ] **Step 2: Modify `scripts/llm-review.sh` to support `--since-sha`**

In `pc/scripts/llm-review.sh`, add the `--since-sha` flag to the argument parser. After the `--dry-run)` case (around line 27), add:

```bash
    --since-sha) SINCE_SHA="$2"; shift 2 ;;
```

Add the variable initialization after `DRY_RUN=false` (around line 20):

```bash
SINCE_SHA=""
```

Then modify the diff generation section. After the existing `DIFF_FILE="/tmp/review/pr_diff.diff"` line (around line 41), add logic to use `--since-sha` if provided:

```bash
# If --since-sha is provided, review only the delta since that SHA
# (used in review loop rounds 2+ to prevent re-flagging accepted code)
if [ -n "$SINCE_SHA" ]; then
  echo "Reviewing delta since $SINCE_SHA (round 2+)"
  git diff "$SINCE_SHA...$HEAD_SHA" > "$DIFF_FILE"
else
  git diff "$BASE_SHA...$HEAD_SHA" > "$DIFF_FILE"
fi
```

Note: the existing workflow already generates the diff in a separate step and writes it to `/tmp/review/pr_diff.diff`. The `--since-sha` flag is used when the review-loop workflow re-runs the review on the delta only. The `llm-review.sh` script reads the pre-generated diff file, so the `--since-sha` logic applies to the workflow step that generates the diff, not to `llm-review.sh` directly. The workflow will pass `SINCE_SHA` as an env var and the diff-generation step will use it.

Actually, looking at the existing `pr-review.yml`, the diff is generated in the "Generate PR diff" step (lines 80-96), not in `llm-review.sh`. So the `--since-sha` support needs to be in the workflow, not the script. Let me correct: `llm-review.sh` reads `/tmp/review/pr_diff.diff` which is pre-generated. The `--since-sha` flag should be added to the diff-generation step in the review-loop workflow instead.

Revised approach: don't modify `llm-review.sh`. Instead, the `review-loop.yml` workflow will generate the delta diff and write it to `/tmp/review/pr_diff.diff` before calling the review scripts. This is cleaner — no script changes needed for the diff logic.

However, `aggregate.sh` still needs the hidden HTML comments. Keep the Step 1 modification to `aggregate.sh`.

- [ ] **Step 3: Write `.github/workflows/review-loop.yml`**

```yaml
name: SDLC Review Loop

on:
  workflow_run:
    workflows: ["build", "PR Review (Reusable)"]
    types: [completed]

# Concurrency: one review-loop per PR — don't double-dispatch
# when both build and pr-review complete
concurrency:
  group: review-loop-${{ github.event.workflow_run.pull_requests[0].number }}
  cancel-in-progress: false

jobs:
  review-loop:
    runs-on: self-hosted
    permissions:
      contents: write
      issues: write
      pull-requests: write
      actions: write
    # Only act when BOTH workflows have completed
    if: github.event.workflow_run.conclusion == 'success' || github.event.workflow_run.conclusion == 'failure'
    steps:
      - name: Checkout repo
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Get PR number and check both workflows completed
        id: pr
        run: |
          PR_NUMBER="${{ github.event.workflow_run.pull_requests[0].number }}"
          echo "number=$PR_NUMBER" >> $GITHUB_OUTPUT

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

      - name: Exit if not both completed
        if: steps.pr.outputs.both_completed != 'true'
        run: echo "Waiting for both workflows to complete. Exiting."

      - name: Parse review findings
        if: steps.pr.outputs.both_completed == 'true'
        id: findings
        run: |
          PR_NUMBER="${{ steps.pr.outputs.number }}"
          # Read the latest review comment from the PR
          COMMENT=$(gh pr view $PR_NUMBER --json comments --jq '
            [.comments[] | select(.body | contains("SUMMARY:"))] | last | .body
          ')
          if [ -z "$COMMENT" ] || [ "$COMMENT" = "null" ]; then
            echo "high_count=0" >> $GITHUB_OUTPUT
            echo "low_count=0" >> $GITHUB_OUTPUT
            echo "round=1" >> $GITHUB_OUTPUT
            echo "last_reviewed_sha=${{ github.event.workflow_run.head_sha }}" >> $GITHUB_OUTPUT
          else
            # Parse SUMMARY line
            SUMMARY=$(echo "$COMMENT" | grep '^SUMMARY:' | head -1)
            HIGH=$(echo "$SUMMARY" | grep -oE '[0-9]+ HIGH' | grep -oE '[0-9]+')
            LOW=$(echo "$SUMMARY" | grep -oE '[0-9]+ LOW' | grep -oE '[0-9]+')
            # Parse hidden HTML comments
            ROUND=$(echo "$COMMENT" | grep -oP '(?<=<!-- review-round: )[0-9]+' || echo "1")
            LAST_SHA=$(echo "$COMMENT" | grep -oP '(?<=<!-- last-reviewed-sha: )[a-f0-9]+' || echo "${{ github.event.workflow_run.head_sha }}")
            echo "high_count=${HIGH:-0}" >> $GITHUB_OUTPUT
            echo "low_count=${LOW:-0}" >> $GITHUB_OUTPUT
            echo "round=${ROUND:-1}" >> $GITHUB_OUTPUT
            echo "last_reviewed_sha=${LAST_SHA}" >> $GITHUB_OUTPUT
          fi
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Auto-merge if clean
        if: |
          steps.pr.outputs.both_completed == 'true' &&
          steps.findings.outputs.high_count == '0' &&
          steps.findings.outputs.low_count == '0'
        run: |
          PR_NUMBER="${{ steps.pr.outputs.number }}"
          echo "PR #$PR_NUMBER is clean — auto-merging"
          gh pr merge $PR_NUMBER --squash --auto --repo ${{ github.repository }}
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}

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

      - name: Re-dispatch build agent to fix findings
        if: |
          steps.pr.outputs.both_completed == 'true' &&
          (steps.findings.outputs.high_count != '0' || steps.findings.outputs.low_count != '0') &&
          steps.findings.outputs.round < '3'
        run: |
          PR_NUMBER="${{ steps.pr.outputs.number }}"
          ROUND=$(({{ steps.findings.outputs.round }} + 1))
          echo "Re-dispatching build agent for round $ROUND"
          # Fire repository_dispatch to re-trigger the build agent
          gh api repos/${{ github.repository }}/dispatches \
            -f event_type=sdlc-phase \
            -f 'client_payload[agent]=build' \
            -f 'client_payload[model]=opencode-go/deepseek-v4-pro' \
            -f 'client_payload[phase]=review-fix' \
            -f 'client_payload[issue_number]=0' \
            -f "client_payload[pr_number]=$PR_NUMBER" \
            -f "client_payload[review_round]=$ROUND" \
            -f "client_payload[last_reviewed_sha]=${{ steps.findings.outputs.last_reviewed_sha }}"
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/review-loop.yml scripts/aggregate.sh
git commit -m "feat: add autonomous review loop workflow + aggregate hidden comments

review-loop.yml: workflow_run listener that fires after build + pr-review
complete. Parses findings, auto-merges if clean, re-dispatches build agent
if findings remain (max 3 rounds), posts Blocked comment if exhausted.
aggregate.sh: appends hidden HTML comments (review-round, last-reviewed-sha)
for the loop to parse statelessly."
git push
```

---

### Task 11: Write integration tests + CI workflow

**Files:**
- Create: `nanobyte-services/sdlc/tests/test-dispatch.sh`
- Create: `nanobyte-services/sdlc/tests/test-planner-dry-run.sh`
- Create: `nanobyte-services/sdlc/tests/test-review-loop.sh`
- Create: `nanobyte-services/.github/workflows/sdlc-ci.yml`

- [ ] **Step 1: Write `sdlc/tests/test-dispatch.sh`**

```bash
#!/bin/bash
set -euo pipefail

# Integration test: verify repository_dispatch fires a workflow in a test repo.
# Requires: GH_TOKEN env var, a test repo with the sdlc-agent.yml workflow.
# Usage: ./test-dispatch.sh --repo <OWNER/TEST-REPO>

REPO=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="$2"; shift 2 ;;
    *) shift ;;
  esac
done

[ -z "$REPO" ] && { echo "Error: --repo is required"; exit 1; }
[ -z "${GH_TOKEN:-}" ] && { echo "Error: GH_TOKEN env var is required"; exit 1; }

echo "Testing repository_dispatch to $REPO..."

# Fire a dispatch
gh api repos/$REPO/dispatches \
  -f event_type=sdlc-phase \
  -f 'client_payload[agent]=planner' \
  -f 'client_payload[model]=opencode-go/glm-5.2' \
  -f 'client_payload[phase]=triaging' \
  -f 'client_payload[issue_number]=1' \
  -f 'client_payload[action]=triaging'

# Wait for the workflow to start (poll for up to 30 seconds)
for i in $(seq 1 15); do
  sleep 2
  RUN_ID=$(gh run list --workflow sdlc-agent.yml --repo $REPO --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || echo "")
  if [ -n "$RUN_ID" ] && [ "$RUN_ID" != "null" ]; then
    echo "PASS: workflow triggered, run ID $RUN_ID"
    exit 0
  fi
done

echo "FAIL: workflow did not trigger within 30 seconds"
exit 1
```

- [ ] **Step 2: Write `sdlc/tests/test-planner-dry-run.sh`**

```bash
#!/bin/bash
set -euo pipefail

# Integration test: verify the planner agent produces a scope comment.
# Requires: a test repo with the sdlc-agent.yml workflow + OPENCODE_AUTH_JSON secret.
# Usage: ./test-planner-dry-run.sh --repo <OWNER/TEST-REPO> --issue <NUMBER>

REPO=""
ISSUE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="$2"; shift 2 ;;
    --issue) ISSUE="$2"; shift 2 ;;
    *) shift ;;
  esac
done

[ -z "$REPO" ] && { echo "Error: --repo is required"; exit 1; }
[ -z "$ISSUE" ] && { echo "Error: --issue is required"; exit 1; }

echo "Testing planner agent on $REPO issue #$ISSUE..."

# Wait for the workflow to complete (poll for up to 10 minutes)
for i in $(seq 1 60); do
  sleep 10
  STATUS=$(gh run list --workflow sdlc-agent.yml --repo $REPO --limit 1 --json conclusion --jq '.[0].conclusion' 2>/dev/null || echo "")
  if [ "$STATUS" = "success" ] || [ "$STATUS" = "failure" ]; then
    break
  fi
done

if [ "$STATUS" != "success" ]; then
  echo "FAIL: workflow did not succeed (status: $STATUS)"
  gh run view --log --repo $REPO --log-failed 2>/dev/null || true
  exit 1
fi

# Check that a scope comment was posted
COMMENT=$(gh issue view $ISSUE --repo $REPO --json comments --jq \
  '[.comments[] | select(.body | contains("Scope Document"))] | last | .body' 2>/dev/null || echo "")

if [ -z "$COMMENT" ] || [ "$COMMENT" = "null" ]; then
  echo "FAIL: no Scope Document comment found on issue #$ISSUE"
  exit 1
fi

# Verify the scope comment has the required sections
for SECTION in "Problem statement" "Proposed scope" "Explicitly out of scope" "Affected modules"; do
  if ! echo "$COMMENT" | grep -q "$SECTION"; then
    echo "FAIL: Scope Document missing section: $SECTION"
    exit 1
  fi
done

echo "PASS: planner agent posted a valid Scope Document"
exit 0
```

- [ ] **Step 3: Write `sdlc/tests/test-review-loop.sh`**

```bash
#!/bin/bash
set -euo pipefail

# Integration test: verify the review loop terminates at 3 rounds.
# This is a simulation test — it creates a PR with known issues and
# verifies the loop doesn't exceed 3 rounds.
# Requires: a test repo with sdlc-agent.yml + review-loop.yml workflows.
# Usage: ./test-review-loop.sh --repo <OWNER/TEST-REPO>

REPO=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="$2"; shift 2 ;;
    *) shift ;;
  esac
done

[ -z "$REPO" ] && { echo "Error: --repo is required"; exit 1; }

echo "Testing review loop termination on $REPO..."

# Create a test branch with a deliberate issue (missing docstring)
BRANCH="test/review-loop-$(date +%s)"
git checkout -b "$BRANCH"
echo "fun undocumentedFunction() { }" > "src/test-loop.kt"
git add src/test-loop.kt
git commit -m "test: deliberate missing docstring for review loop test"
git push origin "$BRANCH"

# Open a PR
PR_NUMBER=$(gh pr create --title "Test: Review loop termination" \
  --body "Test PR for review loop. Should be blocked after 3 rounds." \
  --repo $REPO --json number --jq '.number')

echo "Created PR #$PR_NUMBER on branch $BRANCH"

# Wait for the review loop to run (poll for up to 30 minutes)
MAX_WAIT=1800  # 30 minutes
ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT ]; do
  sleep 30
  ELAPSED=$((ELAPSED + 30))

  # Check for a "Blocked" comment
  BLOCKED=$(gh pr view $PR_NUMBER --repo $REPO --json comments --jq \
    '[.comments[] | select(.body | contains("Blocked: Review Loop Exhausted"))] | length')

  if [ "$BLOCKED" -gt 0 ]; then
    echo "PASS: review loop terminated with Blocked comment after 3 rounds"
    # Verify the round counter never exceeded 3
    ROUNDS=$(gh pr view $PR_NUMBER --repo $REPO --json comments --jq \
      '[.comments[] | .body | scan("<!-- review-round: ([0-9]+) -->") | .[0]] | max // "0"')
    if [ "$ROUNDS" -le 3 ]; then
      echo "PASS: max round was $ROUNDS (<= 3)"
      exit 0
    else
      echo "FAIL: round counter exceeded 3 (was $ROUNDS)"
      exit 1
    fi
  fi

  # Check if PR was merged (shouldn't be — it has findings)
  MERGED=$(gh pr view $PR_NUMBER --repo $REPO --json merged --jq '.merged')
  if [ "$MERGED" = "true" ]; then
    echo "FAIL: PR was auto-merged despite having findings"
    exit 1
  fi
done

echo "FAIL: review loop did not terminate within 30 minutes"
exit 1
```

- [ ] **Step 4: Write `.github/workflows/sdlc-ci.yml`**

```yaml
name: SDLC CI

on:
  pull_request:
    paths:
      - 'sdlc/**'

jobs:
  worker-tests:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: sdlc/worker
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: "20"
      - run: npm install
      - run: npx vitest run

  shellcheck:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: ludeeus/action-shellcheck@master
        with:
          severity: warning
          paths: 'sdlc/**/*.sh sdlc/scripts/*.sh'
```

- [ ] **Step 5: Commit**

```bash
git add sdlc/tests/ .github/workflows/sdlc-ci.yml
git commit -m "test: add SDLC integration tests + CI workflow

test-dispatch.sh: verifies repository_dispatch fires a workflow.
test-planner-dry-run.sh: verifies planner agent posts a valid scope
document with all required sections.
test-review-loop.sh: verifies the review loop terminates at 3 rounds
with a Blocked comment.
sdlc-ci.yml: runs Worker unit tests + shellcheck on PRs touching sdlc/."
git push
```

---

### Task 12: Write the setup README

**Files:**
- Create: `nanobyte-services/sdlc/README.md`

- [ ] **Step 1: Write `sdlc/README.md`**

````markdown
# SDLC Orchestrator

Autonomous software development lifecycle pipeline. GitHub Projects v2 swim lanes trigger opencode agents to plan, implement, review, test, fix, and publish code.

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

## Setup

### 1. Create the GitHub App

1. Go to GitHub Settings → GitHub Apps → New GitHub App
2. Set webhook URL to your Cloudflare Worker URL
3. Set permissions: issues (RW), pull requests (RW), contents (RW), actions (RW), projects (RW), metadata (R)
4. Subscribe to: `issues`, `issue_comment`, `pull_request`, `projects_v2_item`
5. Install on your org ("All repositories" or selected repos)

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

### 3. Register the self-hosted runner

On your home server (Debian, outbound-443, behind Cloudflare Tunnel):

```bash
# Get a registration token
gh api -X POST orgs/<ORG>/actions/runners/registration-token --jq '.token'

# Run setup
cd sdlc/runner
./setup.sh --token <TOKEN> --org <ORG>
```

For ephemeral mode (recommended — fresh runner per job):

```bash
# Set GH_TOKEN env var for the supervisor
export GH_TOKEN=<PAT with admin:org scope>
# Run as a systemd service that restarts on exit
./ephemeral-supervisor.sh
```

### 4. Create the Projects board

```bash
cd sdlc/scripts
./create-board.sh --owner <ORG> --title "SDLC Pipeline"
```

This creates a board with 13 swim lanes and 5 custom fields.

### 5. Add the consumer workflow to a repo

In each repo that should use the SDLC pipeline, add `.github/workflows/sdlc-agent.yml` (see the `pc` repo for reference) and the `OPENCODE_AUTH_JSON` secret.

### 6. Add the `OPENCODE_AUTH_JSON` secret

```bash
echo '{"opencode-go":{"type":"api","key":"YOUR_KEY"}}' | \
  gh secret set OPENCODE_AUTH_JSON --repo <OWNER>/<REPO>
```

## Usage

1. Create a GitHub Issue with a rough feature description
2. Add it to the Projects board (Backlog lane)
3. Move the card to "Triaging" — the planner agent runs automatically
4. Review the Scope Document comment on the issue
5. Move the card to "Planning" (approve) or back to "Triaging" (reject with feedback)
6. Continue through the swim lanes — the pipeline handles the rest

## Staged rollout

- **Stage 1:** Label an issue `agent-ready` to trigger the planner (no App/Worker/runner needed)
- **Stage 2:** Register a self-hosted runner (eliminates 6h limit + minute billing)
- **Stage 3:** Full control plane (GitHub App + Worker + Projects board)

## Files

| Path | Purpose |
|------|---------|
| `opencode/` | Shared opencode config (agents, skills, opencode.json) |
| `routing.yml` | Lane → phase → agent → model mapping |
| `worker/` | Cloudflare Worker (webhook receiver) |
| `runner/` | Self-hosted runner setup scripts |
| `scripts/` | Board creation + utility scripts |
| `tests/` | Integration tests |
````

- [ ] **Step 2: Commit**

```bash
git add sdlc/README.md
git commit -m "docs: add SDLC orchestrator setup guide

Covers GitHub App creation, Worker deployment, runner registration,
board creation, consumer repo setup, and staged rollout."
git push
```

---

## Plan Self-Review

### Spec coverage

| Spec section | Covered by |
|--------------|------------|
| §2 Architecture (spine) | Tasks 1-12 (all stages) |
| §3 Board schema (13 lanes, 5 fields) | Task 9 (create-board.sh) |
| §4 Trigger layer (App + Worker) | Tasks 6-8 (Worker modules + deploy), Task 9 (App) |
| §5 Execution layer (runner + config) | Task 1 (opencode config), Task 4-5 (runner) |
| §6 Planning phase (triaging + planning) | Task 1 (planner agent), Task 3 (e2e test) |
| §7 Autonomous review loop | Task 10 (review-loop.yml + aggregate.sh) |
| §8 Error handling | Covered in agent definitions (failure → card status) + review-loop.yml (Blocked) |
| §9 Testing | Task 11 (integration tests + CI) |
| §10 Rollout (3 stages) | Tasks 1-3 (Stage 1), 4-5 (Stage 2), 6-12 (Stage 3) |
| §11 Existing infra reused | No changes to build.yml/pr-review.yml (wrapped, not modified); aggregate.sh modified for hidden comments |

### Placeholder scan

- The `projects_v2_item` handler in `index.ts` returns `null` with a note that GraphQL resolution is needed. This is intentional — it's a known gap for Stage 3 that requires querying the Projects GraphQL API to resolve lane name + issue number from the webhook payload. This should be implemented as part of Task 9 (or a follow-up task). **Action: this is a real gap, not a placeholder — the implementer should add the GraphQL resolution.**
- Agent stubs (tester, bugfixer, deployer) are explicitly stubs per the spec (future design cycles). Not placeholders.

### Type consistency

- `DispatchParams` / `ClientPayload` types defined in `routing.ts` and used in `dispatch.ts` and `index.ts` — consistent.
- `WebhookEvent` type defined in `routing.ts` and used in `index.ts` — consistent.
- `LaneMapping` / `RoutingConfig` types — consistent across `routing.ts` and tests.
- `Env` interface in `index.ts` matches `wrangler.toml` bindings — consistent.

### Gaps found during review

1. **`projects_v2_item` GraphQL resolution** — the Worker's `buildWebhookEvent` function returns `null` for `projects_v2_item` events because resolving the lane name + issue number requires a GraphQL API query. The implementer should add a `resolveProjectItem` function that queries the GraphQL API using the item node ID from the webhook payload to get: (a) the current Status field value (lane name), (b) the linked issue number, (c) the repo. This is the core of Stage 3 and should be implemented before the Worker can handle card moves.

2. **GitHub App installation token generation** — the Worker uses a static `GITHUB_TOKEN` secret. For production, it should generate installation tokens dynamically using the App's private key (JWT → installation token). This is a follow-up improvement; the static token works for MVP.
