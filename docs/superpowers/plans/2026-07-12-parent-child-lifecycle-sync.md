# Parent-Child Issue Lifecycle Synchronization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure child issues travel with the parent through the entire lifecycle (Testing → Ready to Publish → Done) and prevent duplicate PRs for child issues.

**Architecture:** Add child card guards to build agent, and child sync logic to tester and deployer agents. Three files need modification.

**Tech Stack:** Markdown agent instructions, bash scripts, GitHub CLI (gh)

## Global Constraints

- Agent instructions are in `sdlc/opencode/agents/` directory
- Use `gh` CLI for all GitHub operations
- Use `update-card-status.sh` for card moves — do NOT attempt raw GraphQL mutations
- Child issues are identified by "Parent: #N" in issue body
- All changes must be backward compatible with existing issues

---

## File Structure

| File | Change | Responsibility |
|------|--------|----------------|
| `sdlc/opencode/agents/build.md` | Add child card check at step 0 | Prevent child cards from creating duplicate PRs |
| `sdlc/opencode/agents/tester.md` | Add child sync after test pass/fail | Move children with parent through Testing |
| `sdlc/opencode/agents/deployer.md` | Add child sync after deploy success/fail | Move children with parent through Done |

---

## Task 1: Update Build Agent - Add Child Card Guard

**Files:**
- Modify: `sdlc/opencode/agents/build.md:20-30`

**Interfaces:**
- Consumes: None (standalone change)
- Produces: Child cards will exit early without creating PRs

- [ ] **Step 1: Read current build.md**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/build.md | head -30
```

Expected: Shows current "If PHASE == executing" section starting at line 20

- [ ] **Step 2: Add child card check at step 0**

Insert the following block at the beginning of "## If PHASE == "executing"" section, before step 1:

```markdown
0. **CRITICAL: Check if this is a child card**
   ```bash
   ISSUE_BODY=$(gh issue view $ISSUE_NUMBER --json body --jq '.body')
   if echo "$ISSUE_BODY" | grep -qP 'Parent:\s*#\d+'; then
     echo "Issue #$ISSUE_NUMBER is a child card — skipping execution"
     echo "Child cards are executed as part of the parent issue."
     echo "The parent build agent will create PR and move all children through lifecycle."
     exit 0
   fi
   ```
```

- [ ] **Step 3: Verify the change**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/build.md | grep -A 10 "CRITICAL: Check if this is a child card"
```

Expected: Shows the new child card check block

- [ ] **Step 4: Commit**

```bash
cd /tmp/nanobyte-services
git add sdlc/opencode/agents/build.md
git commit -m "fix(build): add child card guard to prevent duplicate PRs

Child cards now exit early without creating PRs or branches.
Only parent issues will be built, creating one PR for all children."
```

---

## Task 2: Update Tester Agent - Add Child Sync on Test Pass

**Files:**
- Modify: `sdlc/opencode/agents/tester.md:80-100`

**Interfaces:**
- Consumes: None (standalone change)
- Produces: Children move to Ready to Publish when parent tests pass

- [ ] **Step 1: Read current tester.md**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/tester.md | grep -A 20 "If all smoke tests pass"
```

Expected: Shows current test pass section

- [ ] **Step 2: Add child sync logic after test pass**

Replace the section after "If all smoke tests pass" with:

```markdown
9. If all smoke tests pass:
   ```bash
   # Reset Testing Visit Count on success
   gh project item-edit --project-id $PROJECT_ID --id $ITEM_ID --field "Testing Visit Count" --value 0

   gh issue comment $ISSUE_NUMBER --body "## Smoke Test Results

   All N smoke tests passed against UAT ($IMAGE_TAG).

   | Test | Status |
   |------|--------|
   | GET /ready → 200 | PASS |
   | GET /health → 200 | PASS |
   ..."

   # Move all child cards to Ready to Publish (they move with parent)
   CHILD_CARDS=$(gh issue list --repo "$GITHUB_REPOSITORY" --state all --json number,body --jq \
     ".[] | select(.body | test(\"Parent: #$ISSUE_NUMBER\\\\b\")) | .number")
   
   for CHILD in $CHILD_CARDS; do
     bash scripts/update-card-status.sh --issue $CHILD --lane "Ready to Publish" --repo $GITHUB_REPOSITORY
   done

   # Move parent card to Ready to Publish
   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Ready to Publish" --repo $GITHUB_REPOSITORY
   ```
```

- [ ] **Step 3: Verify the change**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/tester.md | grep -A 25 "Move all child cards to Ready to Publish"
```

Expected: Shows the new child sync logic

- [ ] **Step 4: Commit**

```bash
cd /tmp/nanobyte-services
git add sdlc/opencode/agents/tester.md
git commit -m "fix(tester): add child sync on test pass

When parent tests pass, all child cards move to Ready to Publish
together with the parent."
```

---

## Task 3: Update Tester Agent - Add Child Sync on Test Fail

**Files:**
- Modify: `sdlc/opencode/agents/tester.md:100-120`

**Interfaces:**
- Consumes: None (standalone change)
- Produces: Children move to Executing when parent tests fail

- [ ] **Step 1: Read current tester.md fail section**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/tester.md | grep -A 20 "If any smoke test fails"
```

Expected: Shows current test fail section

- [ ] **Step 2: Add child sync logic after test fail**

Replace the section after "If any smoke test fails" with:

```markdown
9. If any smoke test fails:
   ```bash
   # Post failure comment (visit count was already incremented on entry):
   gh issue comment $ISSUE_NUMBER --body "## Test Failures

   M of N smoke tests failed against UAT ($IMAGE_TAG).
   Testing Visit Count: $NEW_COUNT

   | Test | Status | Details |
   |------|--------|---------|
   | GET /ready → 200 | FAIL | Expected status UP, got DOWN |
   ..."

   # Move all child cards back to Executing (they move with parent)
   CHILD_CARDS=$(gh issue list --repo "$GITHUB_REPOSITORY" --state all --json number,body --jq \
     ".[] | select(.body | test(\"Parent: #$ISSUE_NUMBER\\\\b\")) | .number")
   
   for CHILD in $CHILD_CARDS; do
     bash scripts/update-card-status.sh --issue $CHILD --lane "Executing" --repo $GITHUB_REPOSITORY
   done

   # Move parent card back to Executing
   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Executing" --repo $GITHUB_REPOSITORY
   ```
```

- [ ] **Step 3: Verify the change**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/tester.md | grep -A 20 "Move all child cards back to Executing"
```

Expected: Shows the new child sync logic for test fail

- [ ] **Step 4: Commit**

```bash
cd /tmp/nanobyte-services
git add sdlc/opencode/agents/tester.md
git commit -m "fix(tester): add child sync on test fail

When parent tests fail, all child cards move back to Executing
together with the parent."
```

---

## Task 4: Update Deployer Agent - Add Child Sync on Deploy Success

**Files:**
- Modify: `sdlc/opencode/agents/deployer.md:30-45`

**Interfaces:**
- Consumes: None (standalone change)
- Produces: Children move to Done and close when parent deploys successfully

- [ ] **Step 1: Read current deployer.md success section**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/deployer.md | grep -A 15 "If deploy passes"
```

Expected: Shows current deploy success section

- [ ] **Step 2: Add child sync logic after deploy success**

Replace the section after "If deploy passes" with:

```markdown
4. If deploy passes:
   ```bash
   gh issue comment $ISSUE_NUMBER --body "## Deployed to Production

   Image: $IMAGE_TAG
   URL: https://portfolio.nanobyte.ca"

   # Find and move all child cards to Done
   CHILD_CARDS=$(gh issue list --repo "$GITHUB_REPOSITORY" --state all --json number,body --jq \
     ".[] | select(.body | test(\"Parent: #$ISSUE_NUMBER\\\\b\")) | .number")
   
   for CHILD in $CHILD_CARDS; do
     bash scripts/update-card-status.sh --issue $CHILD --lane "Done" --repo $GITHUB_REPOSITORY
     gh issue close $CHILD --repo $GITHUB_REPOSITORY
   done

   # Move parent card to Done
   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Done" --repo $GITHUB_REPOSITORY
   gh issue close $ISSUE_NUMBER --repo $GITHUB_REPOSITORY
   ```
```

- [ ] **Step 3: Verify the change**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/deployer.md | grep -A 20 "Find and move all child cards to Done"
```

Expected: Shows the new child sync logic for deploy success

- [ ] **Step 4: Commit**

```bash
cd /tmp/nanobyte-services
git add sdlc/opencode/agents/deployer.md
git commit -m "fix(deployer): add child sync on deploy success

When parent deploys successfully, all child cards move to Done
and close together with the parent."
```

---

## Task 5: Update Deployer Agent - Add Child Sync on Deploy Failure

**Files:**
- Modify: `sdlc/opencode/agents/deployer.md:45-60`

**Interfaces:**
- Consumes: None (standalone change)
- Produces: Children move to Blocked when parent deploy fails

- [ ] **Step 1: Read current deployer.md failure section**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/deployer.md | grep -A 15 "If deploy fails"
```

Expected: Shows current deploy failure section

- [ ] **Step 2: Add child sync logic after deploy failure**

Replace the section after "If deploy fails" with:

```markdown
5. If deploy fails:
   ```bash
   gh issue comment $ISSUE_NUMBER --body "## Deploy Failed

   Image: $IMAGE_TAG
   Error: <details>"

   # Move all child cards to Blocked (they move with parent)
   CHILD_CARDS=$(gh issue list --repo "$GITHUB_REPOSITORY" --state all --json number,body --jq \
     ".[] | select(.body | test(\"Parent: #$ISSUE_NUMBER\\\\b\")) | .number")
   
   for CHILD in $CHILD_CARDS; do
     bash scripts/update-card-status.sh --issue $CHILD --lane "Blocked" --repo $GITHUB_REPOSITORY
   done

   # Move parent card to Blocked
   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Blocked" --repo $GITHUB_REPOSITORY
   ```
```

- [ ] **Step 3: Verify the change**

```bash
cat /tmp/nanobyte-services/sdlc/opencode/agents/deployer.md | grep -A 15 "Move all child cards to Blocked"
```

Expected: Shows the new child sync logic for deploy failure

- [ ] **Step 4: Commit**

```bash
cd /tmp/nanobyte-services
git add sdlc/opencode/agents/deployer.md
git commit -m "fix(deployer): add child sync on deploy failure

When parent deploy fails, all child cards move to Blocked
together with the parent."
```

---

## Task 6: Create Pull Request

**Files:**
- None (git operations only)

**Interfaces:**
- Consumes: All previous commits
- Produces: PR with all changes

- [ ] **Step 1: Create branch and push**

```bash
cd /tmp/nanobyte-services
git checkout -b fix/parent-child-lifecycle-sync
git push -u origin fix/parent-child-lifecycle-sync
```

Expected: Branch created and pushed to remote

- [ ] **Step 2: Create PR**

```bash
gh pr create --title "fix: parent-child issue lifecycle synchronization" --body "## Summary

This PR fixes the issue where child cards got stuck in Testing lane while the parent was in Ready to Publish. It also prevents duplicate PRs from being created for child issues.

### Changes

#### build.md
- Add child card check at step 0
- Child cards now exit early without creating PRs or branches
- Only parent issues will be built, creating one PR for all children

#### tester.md
- Add child sync logic after test pass/fail
- When parent tests pass, all child cards move to Ready to Publish
- When parent tests fail, all child cards move back to Executing

#### deployer.md
- Add child sync logic after deploy success/failure
- When parent deploys successfully, all child cards move to Done and close
- When parent deploy fails, all child cards move to Blocked

### Expected Lifecycle (After Changes)

\`\`\`
Parent Issue Created
    ↓
Planner creates child issues → All in Plan Review
    ↓
Parent moves to Executing → Children stay in Plan Review
    ↓
Build agent processes parent → Creates PR for parent
    ↓
Parent moves to Testing → Children move to Testing
    ↓
Tester agent processes parent → Runs smoke tests
    ↓
Tests pass → Parent moves to Ready to Publish → Children move to Ready to Publish
    ↓
Human approves → Parent moves to Publish
    ↓
Deployer agent deploys → Parent moves to Done → Children move to Done + Close
\`\`\`

### Testing

- [ ] Build agent skips child cards (no duplicate PRs)
- [ ] Tester syncs children on test pass (Ready to Publish)
- [ ] Tester syncs children on test fail (Executing)
- [ ] Deployer syncs children on deploy success (Done + Close)
- [ ] Deployer syncs children on deploy failure (Blocked)

### Related Issues

- Fixes stuck child issues in Testing lane
- Prevents duplicate PRs for child issues
- Ensures child issues travel with parent through lifecycle" --base main --head fix/parent-child-lifecycle-sync
```

Expected: PR created successfully

- [ ] **Step 3: Verify PR**

```bash
gh pr view --json number,title,url
```

Expected: Shows PR number and URL

---

## Self-Review Checklist

- [ ] **Spec coverage:** All requirements from spec are covered
  - Build agent child guard ✓ (Task 1)
  - Tester agent child sync on pass ✓ (Task 2)
  - Tester agent child sync on fail ✓ (Task 3)
  - Deployer agent child sync on success ✓ (Task 4)
  - Deployer agent child sync on failure ✓ (Task 5)

- [ ] **Placeholder scan:** No TBD, TODO, or vague requirements

- [ ] **Type consistency:** All bash scripts use consistent variable names
  - `$ISSUE_NUMBER` for parent issue
  - `$CHILD_CARDS` for child issues list
  - `$GITHUB_REPOSITORY` for repo

- [ ] **File paths:** All file paths are exact and correct

- [ ] **Commands:** All bash commands are complete and executable

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-12-parent-child-lifecycle-sync.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
