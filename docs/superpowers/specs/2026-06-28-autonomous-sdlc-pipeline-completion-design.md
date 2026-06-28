# Autonomous SDLC Pipeline — Completion Design Spec

**Date:** 2026-06-28
**Status:** Design approved; pending implementation plan
**Predecessor:** `2026-06-22-autonomous-sdlc-pipeline-design.md` (original design)
**Replaces:** `2026-06-28-autonomous-sdlc-pipeline-gaps.md` (preliminary gap-closure plan, superseded by this spec + its implementation plan)

---

## 1. Goal

Close all gaps between the original SDLC pipeline design and the actual implementation, making the **planner → build → review loop** pipeline fully operational end-to-end. The tester, bugfixer, and deployer agents remain stubs for future spec cycles.

---

## 2. Current State Assessment

### What's implemented

| Component | Status | Location |
|-----------|--------|----------|
| Shared opencode config (agents, skills, opencode.json) | ✅ Complete | `nanobyte-services/sdlc/opencode/` |
| Cloudflare Worker (verify → dedupe → GraphQL resolve → label-sync) | ✅ Complete | `nanobyte-services/sdlc/worker/` |
| Self-hosted runner setup scripts | ✅ Complete | `nanobyte-services/sdlc/runner/` |
| Projects v2 board creation script | ✅ Complete | `nanobyte-services/sdlc/scripts/create-board.sh` |
| Card status update script | ✅ Complete (extra, not in original plan) | `nanobyte-services/sdlc/scripts/update-card-status.sh` |
| Worker CI (vitest + shellcheck) | ✅ Complete | `nanobyte-services/.github/workflows/sdlc-ci.yml` |
| Integration tests (dispatch, planner dry-run, review loop) | ✅ Complete | `nanobyte-services/sdlc/tests/` |
| Consumer workflow (sdlc-agent.yml) | ✅ Complete (evolved beyond plan) | `pc/.github/workflows/sdlc-agent.yml` |
| Review loop workflow | ✅ Complete (with TODOs) | `pc/.github/workflows/review-loop.yml` |
| Aggregate.sh hidden HTML comments | ✅ Complete | `pc/scripts/aggregate.sh:166-168` |
| Planner agent | ✅ Complete (enhanced with update-card-status.sh) | `nanobyte-services/sdlc/opencode/agents/planner.md` |
| Build agent | ✅ Complete (enhanced, but review-fix phase has gaps) | `nanobyte-services/sdlc/opencode/agents/build.md` |
| Tester/bugfixer/deployer agents | ✅ Stubs (per this spec's scope decision) | `nanobyte-services/sdlc/opencode/agents/{tester,bugfixer,deployer}.md` |

### Architecture evolution: `repository_dispatch` → `label-sync`

The original design used `repository_dispatch`: the Worker parsed `routing.yml`, mapped lane → agent → model, and fired `repository_dispatch` to the target repo. The implementation evolved to **label-sync**: the Worker resolves the project item via GraphQL, then syncs the issue's labels to match the board's Status field. The consumer repo's `sdlc-agent.yml` triggers on `issues:[labeled]` and owns the lane → agent → model routing via a `case` statement.

**Why:** Org-level Projects boards span multiple repos. Label-sync lets the Worker be repo-agnostic (just mirror labels), while each consumer repo owns its routing. Adding a new consumer repo requires zero Worker changes.

**Consequence:** `dispatch.ts` and `routing.ts` in the Worker are dead code (not imported by `index.ts`). The review loop still uses `repository_dispatch` directly (fired by `review-loop.yml`, not the Worker).

### What's NOT verified

The Stage 3 infrastructure state is unknown. The code is committed, but we don't know if the GitHub App, Cloudflare Worker deployment, self-hosted runner, Projects v2 board, or secrets have been provisioned. The plan must verify each piece and include setup fallbacks.

---

## 3. Gap Inventory

### nanobyte-services gaps

| # | Gap | File(s) | Severity | Impact |
|---|-----|---------|----------|--------|
| N1 | `dispatch.ts` obsolete — `fireDispatch` not imported by `index.ts` | `sdlc/worker/src/dispatch.ts` | Medium | Dead code confuses maintainers |
| N2 | `routing.ts` + `routing.test.ts` dead code — not imported by `index.ts` | `sdlc/worker/src/routing.ts`, `sdlc/worker/test/routing.test.ts` | Medium | CI tests dead code; `routing.yml` stays as documentation |
| N3 | `README.md` stale — shows `repository_dispatch` architecture, obsolete `ROUTING_CONFIG` deploy flag | `sdlc/README.md` | Medium | Misleads new contributors |
| N4 | `build.md` review-fix phase missing PR checkout + `LAST_REVIEWED_SHA` usage | `sdlc/opencode/agents/build.md:32-39` | Medium | Agent may not checkout correct branch; can't focus on delta |
| N5 | `test-dispatch.sh` description stale — says "repository_dispatch fires workflow" without clarifying it tests the review-loop path, not the Worker | `sdlc/tests/test-dispatch.sh:1-8` | Low | Cosmetic |

### PC repo gaps

| # | Gap | File(s) | Severity | Impact |
|---|-----|---------|----------|--------|
| P1 | `HEAD_SHA` not set in Aggregator step env → `aggregate.sh` emits `last-reviewed-sha: unknown` | `pc/.github/workflows/pr-review.yml:120-123` | **High** | Review loop can't track what was reviewed |
| P2 | `REVIEW_ROUND` not set in Aggregator step env → `aggregate.sh` emits `review-round: 1` always | `pc/.github/workflows/pr-review.yml:120-123` | **High** | Max-3-rounds blocking never triggers |
| P3 | No delta-only review — diff is always `BASE...HEAD`, re-flags accepted code in rounds 2+ | `pc/.github/workflows/pr-review.yml:80-96` | **High** | Wastes tokens, re-flags accepted code |
| P4 | `review-loop.yml` TODO: card not moved to "Blocked" lane when review exhausted | `pc/.github/workflows/review-loop.yml:119` | Medium | Human must manually move card |
| P5 | `review-loop.yml` re-dispatch sends `issue_number=0` | `pc/.github/workflows/review-loop.yml:138` | Medium | Build agent can't move card in review-fix phase |

### Infrastructure gaps (unknown state — verify + fallback)

| # | Component | Verification method |
|---|-----------|-------------------|
| I1 | GitHub App ("SDLC Orchestrator") | `gh api /user/installations` or org apps list |
| I2 | Cloudflare Worker deployment | `curl` the Worker URL |
| I3 | Self-hosted runner online | `gh api orgs/{org}/actions/runners` |
| I4 | Projects v2 board with 13 lanes | GraphQL query for projectsV2 |
| I5 | Secrets in pc repo (OPENCODE_AUTH_JSON, NANOBYTE_SERVICES_TOKEN, SDLC_PROJECT_ID) | `gh secret list` |

---

## 4. Design

### Phase 1 — Infrastructure Verification + Setup

**Goal:** Ensure all Stage 3 infrastructure is operational. Each step verifies existence and runs setup only if missing.

**Approach:** Verify-and-fallback. Each infrastructure piece is checked with a CLI command. If it exists, skip. If missing, run the setup steps from the original plan (Tasks 4-9 of `2026-06-22-autonomous-sdlc-pipeline.md`).

**Steps:**

1. **Verify/create GitHub App** — Check for an app with webhook URL matching the Worker. If missing, create it per the original plan's Task 9 Step 1 (manual UI steps). Record the App ID, private key path, and webhook secret.

2. **Verify/deploy Cloudflare Worker** — `curl` the Worker URL. If not deployed, run `wrangler deploy` from `sdlc/worker/`. Requires `WEBHOOK_SECRET` and `GITHUB_TOKEN` secrets set via `wrangler secret put`.

3. **Verify/register self-hosted runner** — `gh api orgs/{org}/actions/runners --jq '.runners[] | select(.labels[].name == "self-hosted")'`. If no runner, run `sdlc/runner/setup.sh --token <TOKEN> --org <ORG>` on the home server.

4. **Verify/create Projects v2 board** — GraphQL query for org/user projects. If missing, run `sdlc/scripts/create-board.sh --owner <ORG> --title "SDLC Pipeline"`. Record the Project ID.

5. **Verify/set secrets** — `gh secret list --repo Nanobyte-Canada/pc`. Check for `OPENCODE_AUTH_JSON`, `NANOBYTE_SERVICES_TOKEN`, `SDLC_PROJECT_ID`. Set any missing ones.

**Output:** All infrastructure verified operational, or setup tasks identified and executed.

### Phase 2 — Code Gap Fixes (parallel)

**Goal:** Close all 10 code-level gaps across both repos.

**Lane A — nanobyte-services (5 gaps: N1-N5):**

- **N1+N2: Remove dead Worker code.** Delete `dispatch.ts`, `routing.ts`, `routing.test.ts`. Run `vitest` to confirm remaining 7 tests pass. `routing.yml` stays as documentation.
- **N3: Update README.** Replace architecture diagram (label-sync flow), remove `ROUTING_CONFIG` deploy flag, update Files table, update staged rollout description.
- **N4: Enhance build.md review-fix phase.** Add `gh pr checkout $PR_NUMBER`, document `LAST_REVIEWED_SHA` usage for delta-focused fixes, add explicit push instruction, clarify that findings on unchanged code were accepted in prior rounds.
- **N5: Update test-dispatch.sh description.** Clarify it tests the review-loop dispatch path, not the Worker label-sync path.

**Lane B — PC repo (5 gaps: P1-P5):**

- **P1+P2+P3: Fix pr-review.yml.** Add `id: generate-diff` to the "Generate PR diff" step. Read the previous review comment to extract `review-round` and `last-reviewed-sha`. Compute `REVIEW_ROUND = prev_round + 1`. For rounds 2+, generate `git diff PREV_SHA...HEAD` instead of `BASE...HEAD`. Output `review_round` and `head_sha` to `$GITHUB_OUTPUT`. Pass these as env vars to the Aggregator step.
- **P4+P5: Complete review-loop.yml.** Add a bootstrap step for `update-card-status.sh` (clone nanobyte-services, copy script). Resolve the linked issue number from the PR body (`Closes #NNN` pattern). In the Block step, call `update-card-status.sh --issue $ISSUE_NUMBER --lane "Blocked"`. In the re-dispatch step, pass the resolved issue number instead of `0`.

**Parallelization:** Lane A and Lane B touch different repos with no write conflicts. They can run simultaneously.

### Phase 3 — Integration

**Goal:** Merge all Phase 2 PRs and verify the bootstrap chain works.

**Steps:**

1. Merge all PRs from Phase 2 Lane A into `nanobyte-services/main`.
2. Merge all PRs from Phase 2 Lane B into `pc/main`.
3. Verify `sdlc-agent.yml`'s bootstrap step can clone `nanobyte-services` and copy `opencode/` + `scripts/update-card-status.sh` successfully.
4. Run `sdlc-agent.yml` via `workflow_dispatch` with a test issue number to verify the full bootstrap → agent execution chain works.

### Phase 4 — End-to-End Validation

**Goal:** Prove the full pipeline works: issue → plan → PR → review → merge/block.

**Test scenario:**

1. Create a test issue in `pc` with a simple feature description.
2. Add the issue to the Projects board (Backlog lane).
3. Move the card to "Triaging" → Worker syncs "Triaging" label → `sdlc-agent.yml` triggers → planner agent runs → posts Scope Document comment → moves card to "Scope Review".
4. Move the card to "Planning" → planner agent runs → posts Module Plan comment → moves card to "Plan Review".
5. Move the card to "Executing" → build agent runs → creates feature branch → implements → opens PR → moves card to "In Review".
6. `build.yml` + `pr-review.yml` run on the PR → review loop triggers.
7. If clean: auto-merge → card moves to next phase.
8. If findings: build agent re-dispatched (round 2) → fixes → re-review → repeat up to 3 rounds → Blocked comment + card moved to "Blocked" lane.

**Validation checks at each step:**
- Workflow triggered (check `gh run list`)
- Agent posted expected comment (check `gh issue/issue view --comments`)
- Card moved to expected lane (check via GraphQL or board UI)
- Review comment has correct `<!-- review-round: N -->` and `<!-- last-reviewed-sha: <SHA> -->` hidden comments

**Failure diagnostics:** Each step includes `gh run view --log` commands for debugging common failures (auth issues, missing secrets, agent errors, Worker misconfiguration).

---

## 5. Testing Strategy

### Unit tests (existing, unchanged)
- Worker HMAC verification: `sdlc/worker/test/verify.test.ts` (4 tests)
- Worker KV dedupe: `sdlc/worker/test/dedupe.test.ts` (3 tests)
- After dead code removal: 7 tests total (routing.test.ts deleted)

### Integration tests (existing)
- `test-dispatch.sh`: verifies `repository_dispatch` fires `sdlc-agent.yml` (review-loop path)
- `test-planner-dry-run.sh`: verifies planner posts a valid Scope Document
- `test-review-loop.sh`: verifies review loop terminates at 3 rounds with Blocked comment

### End-to-end validation (Phase 4)
- Full pipeline test: issue → plan → PR → review → merge/block
- Manual verification at each swim lane transition

### What's NOT tested automatically
- GitHub App webhook delivery (requires live webhook)
- Cloudflare Worker deployment (requires `wrangler` auth)
- Self-hosted runner registration (requires server access)
- Projects v2 board creation (requires `gh` auth with project scope)

These are verified manually in Phase 1 and Phase 4.

---

## 6. Error Handling

### Agent failures
- If an agent fails (non-zero exit), the workflow run fails → GitHub Actions shows the error
- The card stays in its current lane (agent didn't move it)
- Human can re-trigger by removing and re-adding the label

### Review loop failures
- If `build.yml` fails: review loop sees `conclusion: failure` → treats as findings → re-dispatches build agent
- If `pr-review.yml` fails: review loop sees `conclusion: failure` → same behavior
- If re-dispatch fails (API error): the `gh api` command fails → workflow step fails → visible in Actions log

### Worker failures
- Invalid HMAC: returns 401, logged to Cloudflare
- Duplicate delivery: returns 200 (OK), logged
- GraphQL resolution failure: returns 200 (OK), logged — card move is lost (human can re-move the card)
- Label sync failure: returns 200 (OK), logged — issue label not updated (human can manually add the label)

### Infrastructure failures
- Worker offline: webhooks not processed → card moves don't sync labels → agents don't trigger. Human can manually add labels to trigger agents.
- Runner offline: workflows queue → run when runner comes back online
- Missing secrets: workflow fails at the auth step → visible in Actions log

---

## 7. Scope Boundaries

### In scope
- All 10 code-level gaps (N1-N5, P1-P5)
- Infrastructure verification + setup fallbacks
- End-to-end validation of planner → build → review loop
- Dead code removal, README update, agent enhancement

### Out of scope
- Full specs for tester, bugfixer, deployer agents (remain stubs)
- UAT environment setup
- Production deployment pipeline changes
- Monitoring/observability (Grafana dashboards for the pipeline)
- Multi-repo consumer onboarding (beyond verifying the pattern works for `pc`)
- GitHub App installation token generation (static PAT works for MVP)
