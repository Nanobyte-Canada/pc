# PC Repo SDLC Flow Redesign Update Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the pc repository's SDLC workflow to match the redesigned 11-lane pipeline from nanobyte-services.

**Architecture:** The pc repo's `.opencode/agents/` is empty — agents are bootstrapped at runtime from nanobyte-services. The only file that needs updating is `.github/workflows/sdlc-agent.yml` (the label-triggered dispatch workflow) and a stale comment in `scripts/update-card-status.sh`. Model references are already updated to `opencode/deepseek-v4-flash-free`.

**Tech Stack:** GitHub Actions YAML, Bash

## Global Constraints

- Model: `opencode/deepseek-v4-flash-free` (already set — do not change)
- New 11 lanes: Backlog, Scoping, Scope Review, Planning, Plan Review, Executing, Testing, Blocked, Ready to Publish, Publish, Done
- Agent-triggering lanes only: Scoping, Planning, Executing, Testing, Publish
- Human-gated lanes (Scope Review, Plan Review, Blocked, Ready to Publish, Done, Backlog) do NOT trigger the workflow
- Bugfixer agent removed — build agent handles bug-fix mode
- `agent-ready` label stays in the gate (non-lane trigger for manual dispatch)

---

## File Structure

| File | Responsibility |
|------|----------------|
| `.github/workflows/sdlc-agent.yml` | Label-triggered agent dispatch — update lane labels and case block |
| `scripts/update-card-status.sh` | Card-mover script — fix stale comment only (runtime copy comes from nanobyte-services) |

---

### Task 1: Update the `if` Gate — New Lane Labels

**Files:**
- Modify: `.github/workflows/sdlc-agent.yml:47`

**Interfaces:**
- Consumes: Nothing
- Produces: Workflow triggers on correct lane labels

- [ ] **Step 1: Update the lane label list in the `if` gate**

Replace line 47:
```yaml
      (github.event_name == 'issues' && contains(fromJson('["Triaging","Planning","Executing","Testing","Bug Fixing","Publishing","agent-ready"]'), github.event.label.name)) ||
```
with:
```yaml
      (github.event_name == 'issues' && contains(fromJson('["Scoping","Planning","Executing","Testing","Publish","agent-ready"]'), github.event.label.name)) ||
```

This removes `Triaging`, `Bug Fixing`, and `Publishing`, and adds `Scoping` and `Publish`.

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/sdlc-agent.yml
git commit -m "fix(sdlc): update workflow trigger gate to new 11-lane labels"
```

---

### Task 2: Update the Case Block — Rename Lanes, Add Scoping Agent, Remove Bugfixer

**Files:**
- Modify: `.github/workflows/sdlc-agent.yml:167-198`

**Interfaces:**
- Consumes: Task 1 (gate already updated)
- Produces: Correct agent/model/phase dispatched per lane label

- [ ] **Step 1: Replace the `Triaging` case with `Scoping`**

Replace lines 168-172:
```yaml
              Triaging)
                echo "agent=planner" >> $GITHUB_OUTPUT
                echo "model=opencode/deepseek-v4-flash-free" >> $GITHUB_OUTPUT
                echo "phase=triaging" >> $GITHUB_OUTPUT
                ;;
```
with:
```yaml
              Scoping)
                echo "agent=scoping" >> $GITHUB_OUTPUT
                echo "model=opencode/deepseek-v4-flash-free" >> $GITHUB_OUTPUT
                echo "phase=scoping" >> $GITHUB_OUTPUT
                ;;
```

Key changes: lane `Triaging` → `Scoping`, agent `planner` → `scoping`, phase `triaging` → `scoping`.

- [ ] **Step 2: Remove the `Bug Fixing` case entirely**

Delete lines 188-192:
```yaml
              Bug\ Fixing)
                echo "agent=bugfixer" >> $GITHUB_OUTPUT
                echo "model=opencode/deepseek-v4-flash-free" >> $GITHUB_OUTPUT
                echo "phase=bugfix" >> $GITHUB_OUTPUT
                ;;
```

The bugfixer agent was deleted from nanobyte-services. The build agent now handles bug-fix mode internally (when Sub-issue Progress = 100% and Testing Visit Count > 0, it reads test failures and fixes them).

- [ ] **Step 3: Rename `Publishing` case to `Publish`**

Replace line 193:
```yaml
              Publishing)
```
with:
```yaml
              Publish)
```

- [ ] **Step 4: Verify the full case block**

After edits, the case block (lines 167-198) should read:
```yaml
            case "$LABEL" in
              Scoping)
                echo "agent=scoping" >> $GITHUB_OUTPUT
                echo "model=opencode/deepseek-v4-flash-free" >> $GITHUB_OUTPUT
                echo "phase=scoping" >> $GITHUB_OUTPUT
                ;;
              Planning)
                echo "agent=planner" >> $GITHUB_OUTPUT
                echo "model=opencode/deepseek-v4-flash-free" >> $GITHUB_OUTPUT
                echo "phase=planning" >> $GITHUB_OUTPUT
                ;;
              Executing)
                echo "agent=build" >> $GITHUB_OUTPUT
                echo "model=opencode/deepseek-v4-flash-free" >> $GITHUB_OUTPUT
                echo "phase=execution" >> $GITHUB_OUTPUT
                ;;
              Testing)
                echo "agent=tester" >> $GITHUB_OUTPUT
                echo "model=opencode/deepseek-v4-flash-free" >> $GITHUB_OUTPUT
                echo "phase=testing" >> $GITHUB_OUTPUT
                ;;
              Publish)
                echo "agent=deployer" >> $GITHUB_OUTPUT
                echo "model=opencode/deepseek-v4-flash-free" >> $GITHUB_OUTPUT
                echo "phase=publish" >> $GITHUB_OUTPUT
                ;;
            esac
```

5 cases: Scoping, Planning, Executing, Testing, Publish. No Bug Fixing. No bugfixer agent.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/sdlc-agent.yml
git commit -m "fix(sdlc): rename Triaging→Scoping, remove Bugfixer, rename Publishing→Publish in dispatch cases"
```

---

### Task 3: Fix Stale Comment in update-card-status.sh

**Files:**
- Modify: `scripts/update-card-status.sh:6`

**Interfaces:**
- Consumes: Nothing
- Produces: Correct documentation in local script copy

Note: This script is overwritten at runtime by the nanobyte-services copy (line 133 of sdlc-agent.yml), but the local copy should still be accurate for developers reading it.

- [ ] **Step 1: Update the comment**

Replace line 6:
```bash
# to the next lane (e.g., planner moves card from "Triaging" to "Scope Review").
```
with:
```bash
# to the next lane (e.g., scoping agent moves card from "Scoping" to "Scope Review").
```

- [ ] **Step 2: Commit**

```bash
git add scripts/update-card-status.sh
git commit -m "docs(sdlc): fix stale Triaging reference in update-card-status.sh comment"
```

---

### Task 4: Add Child Card Guard (Optional Optimization)

**Files:**
- Modify: `.github/workflows/sdlc-agent.yml` (add a step after checkout)

**Interfaces:**
- Consumes: Task 1 (gate updated)
- Produces: Child cards skip CI runs, saving runner minutes

Note: The tester agent already has a built-in early-exit for child cards (checks issue body for `Parent: #N`). This workflow-level guard is an optimization to avoid even starting the runner for child cards.

- [ ] **Step 1: Add child card skip step**

After the "Checkout repo" step (after line 58), add:
```yaml
      - name: Skip child cards
        if: github.event_name == 'issues' && contains(github.event.issue.body, 'Parent: #')
        run: echo "Child card — skipping agent dispatch"; exit 0
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/sdlc-agent.yml
git commit -m "perf(sdlc): skip CI runs for child cards at workflow level"
```

---

### Task 5: Verify and Push

**Files:**
- None (verification only)

- [ ] **Step 1: Verify no stale references remain**

Run:
```bash
grep -rn "Triaging\|Bug Fixing\|Publishing\|bugfixer\|triaging\|bug_fixing\|bugfix" .github/workflows/sdlc-agent.yml scripts/update-card-status.sh
```
Expected: No output (all stale references removed).

- [ ] **Step 2: Verify YAML syntax**

Run:
```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/sdlc-agent.yml'))" && echo "YAML valid"
```
Expected: `YAML valid`

- [ ] **Step 3: Create branch and push**

```bash
git checkout -b fix/sdlc-flow-redesign-update
git push -u origin fix/sdlc-flow-redesign-update
```

- [ ] **Step 4: Create PR**

```bash
gh pr create --base main --head fix/sdlc-flow-redesign-update \
  --title "fix(sdlc): update workflow to 11-lane redesign" \
  --body "Updates sdlc-agent.yml to match the nanobyte-services SDLC redesign:
- Triaging → Scoping (new scoping agent)
- Bug Fixing → removed (build agent handles bug-fix mode)
- Publishing → Publish
- Adds child card guard to skip CI for child issues

See: nanobyte-services/sdlc/CONSUMER-REPO-UPDATES.md"
```
