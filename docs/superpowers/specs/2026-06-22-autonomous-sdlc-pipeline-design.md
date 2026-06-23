# Autonomous SDLC Pipeline — Design Spec

**Date:** 2026-06-22
**Branch:** `feat/autonomous-sdlc-pipeline`
**Status:** Design approved (all 6 sections); pending implementation plan
**Scope:** Orchestration spine + Planning phase. Execution, Review, Testing, Bug Fix, and Publish phases are sketched as lane definitions here and will be fully specified in subsequent specs.

---

## 1. Goal

Build a fully autonomous software development and project-management system that manages the SDLC from planning to execution to review to testing to bug fixing to publishing. The user manages and reviews tasks in a project-management tool (GitHub Projects v2) via swim lanes; the LLM (opencode) does the work. The design is reusable across repositories.

### Decisions locked during brainstorming

| Decision | Choice |
|----------|--------|
| Control-plane approach | Approach B (staged): label-trigger MVP → self-hosted runner → GitHub App + Cloudflare Worker |
| PM tool | GitHub Projects v2 (swim lanes = SDLC phases) |
| First spec scope | Orchestration spine + Planning phase; subsequent phases get their own specs |
| Human approval mechanism | Card move = approval; move back = rejection with feedback |
| Task entry point | GitHub Issue + card move to `Triaging` triggers Planning |
| Branch strategy | Feature branch off `main` → PR to `main` |
| Review loop | Autonomous (no human in `In Review`); max 3 rounds → `Blocked` |
| Planning artifacts | Issue comments (edited in place), NOT repo files |
| Shared-services repo | `nanobyte-services` (firm-wide: SDLC orchestrator, IB gateway, Grafana, etc.) |

---

## 2. Architecture Overview (the Spine)

```
You (human)                        GitHub Projects v2 board
  │  create Issue, add to board        (swim lanes = SDLC phases)
  │  move card → "Triaging"             │
  ▼                                     │ projects_v2_item webhook (action: moved)
  Review outputs ◄──────────────────────┤
  │  move card → approve/reject         │
  ▼                                     ▼
  GitHub App (nanobyte-services) ──► Cloudflare Worker
  subscribes: projects_v2_item,        validates HMAC-SHA256
  issues, pull_request                 resolves target repo + phase
                                       │ repository_dispatch (client_payload: issue, phase, repo)
                                       ▼
  Self-hosted GitHub Actions runner (home Debian box, outbound-443, behind Cloudflare Tunnel)
  │  on: repository_dispatch
  │  checks out repo, bootstraps shared opencode config from nanobyte-services
  │  writes auth.json from secret
  │  opencode run --agent <phase-agent> --model <phase-model> \
  │           --format json --dangerously-skip-permissions
  ▼
  opencode agent does the work (planner drafts scope; build codes; etc.)
  │  commits to feat/<task-slug> branch, opens PR (gh CLI)
  │  updates Projects card via GraphQL (Agent Status, move lane)
  ▼
  Existing pipelines kick in (unchanged):
  │  build.yml        → tests on PR (5 backend Gradle + frontend lint/test/build)
  │  pr-review.yml    → 5-stage LLM review (classify → structural → code → docs → aggregate)
  │  post-comment.sh  → posts review findings on PR
  ▼
  Autonomous review loop (In Review lane):
  │  build agent addresses all HIGH/LOW findings, loops until clean, auto-merges
  │  max 3 rounds → Blocked lane if unresolved
  ▼
  Card auto-moves to next phase (built-in Projects Workflow on merge/close)
```

### Repo split

| Repo | Holds | Why |
|------|-------|-----|
| **`nanobyte-services`** (new, firm-wide) | `sdlc/` (GitHub App, Cloudflare Worker, routing config, shared opencode config bundle, runner setup scripts, self-tests), `ib-gateway/` (future), `grafana/` (future), other shared services | One shared-services repo serving all repos; versioned independently; installed org-wide |
| **`pc`** (this repo, consumer) | Tiny `on: repository_dispatch` listener workflow, repo-specific `.github/review-config.yml`, existing `build.yml`/`pr-review.yml`/`deploy.yml`, repo-specific `.opencode/` overrides, this design spec | Repo-specific CI/review/deploy stays with the repo |

**Runtime connection:** the self-hosted runner job (triggered by `repository_dispatch` in `pc`) bootstraps by fetching the shared opencode config bundle from `nanobyte-services` (`git clone --depth 1`), then layers repo-specific `.opencode/` overrides on top. The Cloudflare Worker reads a routing config (repo → `repository_dispatch` workflow → phase agent) so adding a consumer repo is a config entry, not code.

### Staged rollout

- **Stage 1 (MVP, ship in a day):** Label `agent-ready` on an issue → `on: issues: [labeled]` workflow on GitHub-hosted runner → `opencode run --agent planner`. Validates the opencode-in-CI loop. No App/Worker/runner yet.
- **Stage 2 (move execution home):** Register org-level self-hosted runner on the Debian box (ephemeral mode, Docker available). Switch `runs-on: self-hosted`. Kills the 6h wall + all minute billing.
- **Stage 3 (full control plane):** Create GitHub App + Cloudflare Worker in `nanobyte-services`. Moving a card fires `projects_v2_item` → Worker → `repository_dispatch`. The board is the control plane. Drop the label bridge.

### Why this shape

- One GitHub App covers every repo — ~zero per-repo trigger files (portability).
- Self-hosted runner: no time limit (5-day job cap), zero minute cost, Docker for containerized backend tests (this repo has no local JDK — tests run in Docker).
- Reuses existing `build.yml` (testing gate) and `pr-review.yml` (review phase) unchanged — the spine wraps them, doesn't replace them.
- Per-agent model switching: `planner` on a cheap/fast model, `build` on a powerful coding model, `review` already uses `deepseek-v4-pro`.

---

## 3. Projects Board Schema (swim lanes = SDLC phases)

The board is the control plane. Each swim lane is a phase state. Card moves fire `projects_v2_item` (action: `moved`) → trigger the next agent or wait for the human.

### Swim lanes (Status field, single-select — 13 lanes)

| Lane | Meaning | Who acts next | Trigger on entry |
|------|---------|---------------|------------------|
| `Backlog` | New issue added, not yet triaged | — (human) | none |
| `Triaging` | Planning agent clarifies scope + breaks into modules | **planner agent** | `repository_dispatch` → `opencode run --agent planner` |
| `Scope Review` | Agent posted draft scope; **human reviews** | **human** | none (wait for card move) |
| `Planning` | Human approved scope; planner drafts per-module plans | **planner agent** | `repository_dispatch` → `opencode run --agent planner` |
| `Plan Review` | Agent posted module plans; **human reviews** | **human** | none |
| `Executing` | Human approved plans; build agent implements + raises PR | **build agent** | `repository_dispatch` → `opencode run --agent build` |
| `In Review` | PR open; `build.yml` tests + `pr-review.yml` 5-stage review run; build agent addresses all HIGH/LOW findings; loops until clean; **auto-merges** | **pipelines + build agent** (no human) | PR events drive this |
| `Blocked` | Build agent couldn't resolve findings after 3 review rounds | **human** | none |
| `Testing` | Merged to main; deploy to UAT + test plan execution | **tester agent** | `repository_dispatch` → `opencode run --agent tester` |
| `Bug Fixing` | Tests found bugs; bug-fixer agent plans + fixes + PRs | **bug-fixer agent** | `repository_dispatch` → `opencode run --agent bugfixer` |
| `Ready to Publish` | Tests pass; **human approves deploy to prod** | **human** | none |
| `Publishing` | Deploy to prod + version bump | **deploy agent** (or existing `deploy.yml`) | `repository_dispatch` → deploy |
| `Done` | Shipped | — | built-in Projects Workflow auto-moves on merge/close |

### Custom fields

- `Phase` (single-select: Planning/Execution/Review/Testing/BugFix/Publish) — groups lanes for reporting/filtering.
- `Module` (single-select or text) — which module this task belongs to (set during triage).
- `Priority` (single-select: P0/P1/P2) — human ordering.
- `PR` (text) — linked PR number, set by the build agent when it opens one.
- `Agent Status` (single-select: idle/running/succeeded/failed/blocked) — agent updates via GraphQL so the human can see what's happening without reading logs.

### Rejection loop

If the human disagrees at `Scope Review` or `Plan Review`, they add a comment with feedback and move the card **back** to `Triaging`/`Planning`. The `moved` webhook re-triggers the agent with the feedback attached (agent reads issue comments). Same pattern for `Bug Fixing` ← `Testing` failures, and `Executing` ← `Blocked` (after human guidance).

### Parent/child issue model

One issue = one task. For multi-module features, the planner creates **child issues** (GitHub linked issues) during triage, each with its own card. The parent issue's card tracks overall progress; child cards flow through lanes independently and in parallel (respecting dependency annotations).

---

## 4. Trigger Layer (GitHub App + Cloudflare Worker)

Lives in `nanobyte-services/sdlc/`. This is the bridge between the Projects board and the self-hosted runner.

### GitHub App

- Installed org-wide ("All repositories" or selected repos).
- Subscribes to: `projects_v2_item` (action: `moved`, `edited`), `issues`, `issue_comment`, `pull_request`.
- Webhook secret: HMAC-SHA256 signed payloads (`X-Hub-Signature-256`).
- Permissions: `issues: write`, `pull_requests: write`, `contents: write`, `projects: write`, `actions: write` (to fire `repository_dispatch`), `metadata: read`.
- Generates installation tokens per-repo at runtime (no long-lived PATs).

### Cloudflare Worker (webhook receiver)

```
GitHub webhook → Cloudflare Worker (public HTTPS endpoint)
  │
  ├─ 1. Verify HMAC-SHA256 signature (reject if invalid)
  ├─ 2. Idempotency check: dedupe by X-GitHub-Delivery ID (KV store, 10-min TTL)
  ├─ 3. Route: parse event → look up routing config
  │     projects_v2_item.moved → { repo, phase, issue_number } from card metadata
  │     issues.labeled "agent-ready" → { repo, phase: "triaging", issue_number }  (Stage 1)
  │     pull_request.closed/merged → { repo, phase: "testing" }
  ├─ 4. Fire repository_dispatch to the target repo:
  │     POST /repos/{owner}/{repo}/dispatches
  │     client_payload: { issue_number, phase, repo, card_id, action, agent, model, prompt }
  └─ 5. Return 200 (acknowledge; the runner does the actual work async)
```

### Routing config (`nanobyte-services/sdlc/routing.yml`)

```yaml
# Lane → phase → agent mapping (shared across repos; repos can override)
lanes:
  Triaging:    { phase: planning,  agent: planner,  model: opencode-go/glm-5.2 }
  Planning:    { phase: planning,  agent: planner,  model: opencode-go/glm-5.2 }
  Executing:   { phase: execution, agent: build,    model: opencode-go/deepseek-v4-pro }
  Testing:     { phase: testing,   agent: tester,   model: opencode-go/glm-5.2 }
  Bug Fixing:  { phase: bugfix,    agent: bugfixer, model: opencode-go/deepseek-v4-pro }
  Publishing:  { phase: publish,   agent: deployer, model: opencode-go/glm-5.2 }
# Lanes NOT here (Scope Review, Plan Review, In Review, Blocked, Ready to Publish, Done, Backlog)
# are human-gated or pipeline-driven — no dispatch on entry.
```

### Why a Worker (not a home-server endpoint)

- Cloudflare Workers are always-on, globally distributed, free tier generous (100K req/day), no server to maintain.
- The home server has no exposed ports (Cloudflare Tunnel only) — receiving webhooks directly would need a Tunnel route + auth, more attack surface.
- The Worker is a thin, stateless validator/router — all heavy work happens on the self-hosted runner.

### Portability

Adding a new consumer repo: (1) install the App on it, (2) add a tiny `on: repository_dispatch` workflow in that repo, (3) optionally add repo overrides to `routing.yml`. No changes to the App or Worker code.

---

## 5. Execution Layer (self-hosted runner + opencode agents)

### Self-hosted runner

- Registered at **org level** → serves all repos.
- **Outbound-443 only** — polls GitHub, no inbound ports. Works behind Cloudflare Tunnel.
- **Ephemeral mode** (`--ephemeral --once`): handles one job, then de-registers; a systemd supervisor provisions a fresh runner per job. Eliminates state leakage between agent sessions.
- **Docker available** — critical for this repo (no local JDK; backend tests run in Docker).
- **5-day job limit, zero minute billing.**

### The `repository_dispatch` workflow (in `pc`, ~30 lines)

```yaml
on:
  repository_dispatch:
    types: [sdlc-phase]
  issues:
    types: [labeled]  # Stage 1 fallback

jobs:
  run-agent:
    runs-on: self-hosted
    steps:
      - name: Bootstrap shared opencode config
        run: |
          git clone --depth 1 https://github.com/$OWNER/nanobyte-services /tmp/nbs
          cp -r /tmp/nbs/sdlc/opencode/* .opencode/
      - name: Write opencode auth
        run: echo '${{ secrets.OPENCODE_AUTH_JSON }}' > ~/.local/share/opencode/auth.json
      - name: Checkout repo
        uses: actions/checkout@v4
      - name: Run phase agent
        run: |
          opencode run \
            --agent ${{ github.event.client_payload.agent }} \
            --model ${{ github.event.client_payload.model }} \
            --format json \
            --dangerously-skip-permissions \
            "${{ github.event.client_payload.prompt }}"
        env:
          PHASE: ${{ github.event.client_payload.phase }}
          ISSUE_NUMBER: ${{ github.event.client_payload.issue_number }}
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Shared opencode config bundle (`nanobyte-services/sdlc/opencode/`)

- `opencode.json` — centralizes provider/model config (replaces scattered hardcoded models across `review-config.yml`, `agents/pr-review.md`, `llm-review.sh`, `pr-review.yml`).
- `agents/` — canonical agent definitions: `planner.md`, `build.md`, `tester.md`, `bugfixer.md`, `deployer.md` (plus the existing `pr-review.md`).
- `skills/` — canonical skills for each SDLC phase (plus the existing `pr-review` skill).
- `permissions/` — permission scoping per agent.

### Per-agent model switching (from `routing.yml`)

- `planner` → cheap/fast model (`opencode-go/glm-5.2`) — planning is reasoning-heavy but not code-generation-heavy.
- `build` → powerful coding model (`opencode-go/deepseek-v4-pro`) — code generation needs the best.
- `tester` → cheap model — test plans + execution commands.
- `bugfixer` → powerful coding model — debugging needs reasoning + code.
- `deployer` → cheap model — version bump + deploy commands.

### Repo-specific overrides (`pc/.opencode/`)

The bootstrap step copies shared config first, then repo-specific `.opencode/` files override on top (e.g., `pc` could add a trading-specific skill or override the model for `build`). The existing `pc/.opencode/agents/pr-review.md` + `pc/.opencode/skills/pr-review/` stay as-is (repo-specific review config).

### Agent → board feedback

Every agent updates the Projects card via GraphQL (`gh project item-edit --field "Agent Status" --value "running"/"succeeded"/"failed"`) so the human can see what's happening from the board without reading logs. On completion, the agent moves the card to the next lane (or back on failure).

---

## 6. Planning Phase (the first concrete phase)

The only phase fully specified in this spec. Covers two agent runs (Triaging + Planning) separated by human review. **Scope and plans live as issue comments in the PM tool, not as repo files.**

### 6a. Triaging lane — planner agent clarifies scope

**Trigger:** card moved to `Triaging` → `repository_dispatch` → `opencode run --agent planner`.

**The planner agent does:**
1. Reads the GitHub Issue body (the human's rough feature description).
2. Explores the codebase using `docs/reference/INDEX.md` as its nav hub — reads relevant reference docs (architecture, DB schema, API endpoints, frontend map) to understand what exists.
3. Posts a **Scope Document as a comment** on the issue, structured as:
   - **Problem statement** — what problem this solves (1-2 sentences).
   - **Proposed scope** — what's in scope (bullet list of capabilities).
   - **Explicitly out of scope** — what's NOT being built (prevents scope creep).
   - **Affected modules** — which parts of the codebase this touches (from `docs/reference/backend-services.md`, `frontend-map.md`).
   - **Open questions** — anything ambiguous that needs human input.
   - Comment header: `## Scope Document (latest — updated <date>)` so it's easy to find.
4. Updates card: `Agent Status` → `succeeded`, moves card to `Scope Review`.

**Human review:** open the issue from the card, read the scope comment. If good, move card to `Planning`. If not, add a comment with feedback and move card back to `Triaging`.

**On rejection:** the planner re-reads the issue comments (including the feedback), **edits the same Scope Document comment in place** (one current version, not a thread of revisions), and moves the card back to `Scope Review`.

### 6b. Planning lane — planner agent creates per-module plans

**Trigger:** card moved to `Planning` → `repository_dispatch` → `opencode run --agent planner`.

**The planner agent does:**
1. Reads the approved scope (from the Scope Document comment on the issue).
2. Breaks the work into **modules/features** — each module becomes a child issue.
3. For each module:
   - Creates a **child GitHub Issue** with: module description + acceptance criteria in the body.
   - Posts a **Plan comment** on the child issue with: technical approach (files to change, tech to use), checkbox task list, dependencies.
   - Adds the child issue to the Projects board in `Backlog` lane, linked to the parent.
4. **Identifies parallelization** — annotates each child issue body with `Depends on: #NNN` or `Parallel: yes` so the execution phase knows which modules can run concurrently.
5. Posts a **Plan Summary comment** on the parent issue: lists all child issues, their dependencies, and a suggested execution order (parallel groups vs. sequential).
6. Updates parent card: `Agent Status` → `succeeded`, moves to `Plan Review`.

**Human review:** read the plan summary on the parent issue + the plan comments on child issues. If good, move parent card to `Executing`. If not, comment + move back to `Planning`.

**On rejection:** planner edits the Plan Summary comment + affected child issue plan comments in place, incorporating the feedback.

### 6c. Why comments, not repo files

| Aspect | Comments on issues (chosen) | Files in repo (rejected) |
|--------|-----------------------------|--------------------------|
| Where the human reviews | Projects board / issue view (single pane of glass) | Browse repo files or open PRs |
| Revision loop | Agent edits comment in place — one current version | Agent edits file, pushes commit — repo noise during planning |
| Machine-readable for build agent | `gh issue view <num> --comments` — build agent reads plan from the child issue comment | Build agent reads file from repo |
| History | GitHub preserves edit history on comments | Git history |
| Repo cleanliness | No spec/plan files cluttering the repo during planning | Files created before any code exists |

**Build agent consumption:** when the Execution phase starts, the build agent reads the plan from the child issue's plan comment via `gh issue view <child_num> --comments`. No file needed.

**Exception — when code IS written:** during Execution, the build agent writes actual code + documentation updates to the repo and raises a PR as normal. Files in the repo are for *implemented* work, not for planning artifacts.

### 6d. The planner agent definition (`nanobyte-services/sdlc/opencode/agents/planner.md`)

```markdown
---
name: planner
model: opencode-go/glm-5.2
mode: subagent
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
   - If a Scope Document comment already exists (rejection loop), EDIT it in place
4. Move card to "Scope Review" via GraphQL

## If PHASE == "planning"
1. Read the approved scope from the issue's Scope Document comment
2. Break into modules; for each: create child issue + post Plan comment on it
3. Annotate dependencies (Depends on / Parallel) in child issue bodies
4. Post Plan Summary comment on parent issue (edit in place if exists)
5. Move parent card to "Plan Review" via GraphQL

## Rules
- NEVER write implementation code or create files in the repo — you plan only
- ALWAYS read existing code/docs before proposing changes
- Post scope/plans as issue comments, NOT as repo files
- Edit existing comments in place when revising (don't create new comment threads)
- If scope is unclear, post open questions and move card to "Scope Review"
```

### 6e. Phases after Planning (preview — not specified here)

When the human moves the parent card to `Executing`, the Worker dispatches the **build agent** for each child issue (respecting dependencies — parallel groups run concurrently on the self-hosted runner). The build agent consumes a plan from the child issue's plan comment, executes checkbox tasks, raises a PR, and the autonomous review loop runs. The Execution, Testing, Bug Fix, and Publish phases each get their own spec in subsequent design cycles.

---

## 7. Autonomous Review Loop (`In Review` lane)

Fully autonomous — no human in this lane. The build agent raised a PR; now pipelines + the build agent close the loop.

### Orchestration: how the build agent is re-triggered each round

The review workflows (`build.yml` + `pr-review.yml`) run on PR events, but the build agent runs on `repository_dispatch`. They can't synchronously wait for each other within one job. The bridge is a **`workflow_run` listener**:

```
PR opened (by build agent in Executing lane)
  │  card → "In Review", round counter = 0
  ▼
  build.yml + pr-review.yml run on the PR (triggered by pull_request event)
  │  aggregator posts findings comment, writes last-reviewed-sha to a PR comment
  │  tagged with a hidden HTML comment marker: <!-- last-reviewed-sha: <sha> -->
  │
  ▼  both workflows complete
  A `workflow_run` listener workflow in pc fires (with a concurrency group keyed
  on the PR number so it doesn't double-dispatch when both workflows complete):
    on:
      workflow_run:
        workflows: [build, pr-review]
        types: [completed]
    concurrency:
      group: review-loop-${{ github.event.workflow_run.pull_requests[0].number }}
      cancel-in-progress: false
    steps:
      - Check that BOTH build and pr-review have completed (query workflow run status);
        if only one is done, exit early and wait for the other
      - Read the PR's findings comment + last-reviewed-sha
      - If high_count==0 && low_count==0 → gh pr merge --squash --auto, card → "Testing", stop
      - If round >= 3 → post "Blocked" comment, card → "Blocked", stop
      - Else → fire repository_dispatch { agent: build, phase: "review-fix",
          issue_number, pr_number, round: round+1, last_reviewed_sha }
  │
  ▼  findings exist, round < 3
  Build agent runs (repository_dispatch), reads findings from PR comment:
    │  RULE 1: never blindly revert a flagged change — either fix it properly
    │          OR, if the reviewer is wrong, add a comment justifying the approach
    │          and leave the change (don't revert)
    │  RULE 2: track findings across rounds — if the same (category, location)
    │          appears in 2 consecutive rounds, escalate it, don't repeat the same fix
  │  pushes commits to the PR branch
  │  card Agent Status → "running", round counter → round+1
  ▼
  PR push triggers build.yml + pr-review.yml again — BUT only on the delta
  (diff since last-reviewed-sha, not BASE...HEAD) to prevent re-flagging accepted code.
  │  aggregator posts updated findings comment, updates last-reviewed-sha
  │
  ▼  workflows complete → workflow_run listener fires again (loop)
```

**Round counter storage:** the round number is tracked as a hidden HTML comment in the findings comment (`<!-- review-round: N -->`) so the `workflow_run` listener can parse it statelessly across runs.

**Last-reviewed-sha storage:** written as a hidden HTML comment in the same findings comment (`<!-- last-reviewed-sha: <sha> -->`). The `llm-review.sh` enhancement reads this to compute `git diff <last-reviewed-sha>...HEAD` on rounds 2+.

### Three anti-thrash mechanisms

1. **Max 3 rounds → Blocked** — hard stop prevents infinite loops.
2. **No-blind-revert rule** — agent must fix or justify, never simply revert a flagged change. Kills the revert ping-pong (build agent reverts → reviewer flags the revert → build agent re-applies → reviewer flags again).
3. **Delta-only review after round 1** — reviewer evaluates only what changed since the last clean state (`git diff <last-reviewed-sha>...HEAD` instead of `BASE...HEAD` on rounds 2+). Prevents re-flagging already-accepted code. Small enhancement to `llm-review.sh`.

---

## 8. Error Handling

| Failure | Detection | Recovery |
|---------|-----------|----------|
| Agent crashes / opencode exits non-zero | Workflow job fails | Agent sets `Agent Status` → `failed` on card (via a `finally:` step), posts a failure comment with the last 20 lines of logs. Card stays in current lane. Human sees red status on the board and can re-trigger by moving the card out and back in. |
| Agent loops on review (thrash) | Max 3 rounds counter | Agent posts "Blocked" comment with unresolved findings, moves card to `Blocked` lane. Human gives guidance, moves card back to `Executing`. |
| Worker receives invalid webhook | HMAC verification fails | Worker returns 401, logs to Cloudflare, no dispatch fired. |
| Duplicate webhook delivery | `X-GitHub-Delivery` ID in KV (10-min TTL) | Worker returns 200, no dispatch. Prevents double-runs. |
| Self-hosted runner offline | GitHub marks runner offline | Workflow queues until runner returns. Slack alert via Uptime Kuma runner-health check. |
| Agent can't create child issues / move card | `gh` CLI exits non-zero | Agent posts a failure comment, sets `Agent Status` → `failed`. Card stays put. |
| opencode API key invalid / rate-limited | opencode exits with auth error | Same as crash — `failed` status + failure comment. Human rotates the key in org secrets. |

**Principle:** every failure surfaces on the board (card status + comment) so the human never needs to read CI logs unless they choose to. The board is the single pane of glass.

---

## 9. Testing the System Itself

The SDLC pipeline is itself software and needs tests. Following the existing `scripts/tests/` pattern.

| Test | Type | What it validates |
|------|------|-------------------|
| `test-routing.yml` | Unit | Worker routing config parses correctly; lane → phase → agent mapping is valid |
| `test-webhook-signature.sh` | Unit | HMAC verification accepts valid signatures, rejects invalid ones |
| `test-dedupe.sh` | Unit | Duplicate `X-GitHub-Delivery` IDs are rejected within TTL |
| `test-dispatch.sh` | Integration | `repository_dispatch` fires the workflow in a test repo and the runner picks it up |
| `test-planner-dry-run.sh` | Integration | Planner agent runs against a fixture issue, produces a scope comment, doesn't crash. Uses a sandbox repo. |
| `test-review-loop.sh` | Integration | Simulate a PR with findings → build agent addresses → re-review → verify loop terminates at 3 rounds |

Tests live in `nanobyte-services/sdlc/tests/` and run in a `nanobyte-services` CI workflow on PR.

---

## 10. Rollout Plan

### Stage 1 — MVP (ship in a day, this repo only)

- Add `on: issues: [labeled]` workflow to `pc` with `if: github.event.label.name == 'agent-ready'`.
- Runs on GitHub-hosted runner (temporary — no self-hosted runner yet).
- `opencode run --agent planner` on the labeled issue.
- Validates: opencode runs in CI, reads the issue, produces a scope comment.
- No App, no Worker, no Projects integration yet — just the label trigger.

### Stage 2 — Move execution home (this repo)

- Register org-level self-hosted runner on the Debian box (ephemeral mode, Docker).
- Switch `runs-on: self-hosted` in the workflow.
- Validates: runner works behind Cloudflare Tunnel, Docker tests pass, no minute billing.

### Stage 3 — Full control plane (nanobyte-services repo)

- Create `nanobyte-services` repo with `sdlc/` component.
- Build the GitHub App + Cloudflare Worker + routing config.
- Create the Projects board with the 13 lanes + custom fields.
- Switch from label trigger to `repository_dispatch` triggered by card moves.
- Add the shared opencode config bundle; `pc` consumes it via bootstrap.
- Validates: move a card → agent runs → card moves through lanes autonomously.

---

## 11. Existing Infrastructure Reused (not rebuilt)

| Existing | Role in the new system |
|----------|------------------------|
| `scripts/{classify,structural,llm-review,aggregate,post-comment}.sh` + `pr-review.yml` | The `In Review` lane's review engine (unchanged) |
| `.opencode/agents/pr-review.md` + `.opencode/skills/pr-review/SKILL.md` | Established the agent/skill authoring pattern; new agents follow it |
| `docs/reference/*` (10 files + `INDEX.md`) | Agent-consumable codebase context (planner reads it during triage) |
| `docs/superpowers/{specs,plans}` convention | (Used for this spec; planning artifacts themselves move to issue comments) |
| `CONTRIBUTING.md` constraints | Machine-readable-ish rules the review pipeline enforces; reusable as agent guardrails |
| `build.yml` test jobs | The testing gate on PRs (unchanged) |
| `deploy.yml` | The publish/deploy phase (already supports uat + prod + Vault + health checks + Slack) |
| `pr-review-trigger.yml.template` pattern | Template for the `repository_dispatch` listener workflow portability |

---

## 12. Gaps Addressed by This Design

| Gap (from codebase exploration) | Addressed by |
|--------------------------------|--------------|
| No planning agent/skill | `planner` agent + Planning phase (§6) |
| No GitHub Projects board | 13-lane board schema (§3) |
| No issue/PR templates | (Out of scope for this spec; future work) |
| No `opencode.json` (config scattered) | Centralized in `nanobyte-services/sdlc/opencode/opencode.json` (§5) |
| No auto-promote deploy gate | `Publishing` lane + deploy agent (future spec) |
| No `AGENTS.md` | (Out of scope; shared opencode config replaces this need) |
| Provider/model hardcoded in multiple places | Centralized in `opencode.json` + `routing.yml` (§4, §5) |
| No autonomous review loop | `In Review` lane with 3 anti-thrash mechanisms (§7) |
