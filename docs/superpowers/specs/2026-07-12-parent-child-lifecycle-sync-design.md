# Design: Parent-Child Issue Lifecycle Synchronization

**Date**: 2026-07-12
**Status**: Approved
**Author**: Orchestrator

## Problem Statement

Child issues (#240, #241, #242) got stuck in Testing lane while the parent issue (#239) was in Ready to Publish. Additionally, duplicate PRs were created for both parent and child issues, causing confusion.

### Root Causes

1. **Build agent creates PRs for child issues**: When triggered for a child issue, the build agent creates a separate branch and PR instead of skipping execution.
2. **No lifecycle synchronization**: Child issues don't automatically travel with the parent through Testing → Ready to Publish → Done.
3. **Tester agent correctly skips child cards**: But this leaves child issues stranded in Testing lane.

## Solution: Hybrid Approach

### Change 1: Build Agent Child Guard

**File**: `sdlc/opencode/agents/build.md`

Add child card check at the start of "If PHASE == executing":

```markdown
## If PHASE == "executing"

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

1. Read the Execution Plan from the parent issue's Plan comment...
```

**Effect**: Child cards will never trigger the build agent. Only parent issues will be built, creating one PR for all children.

### Change 2: Tester Agent Child Sync

**File**: `sdlc/opencode/agents/tester.md`

Add child sync logic after smoke tests pass/fail:

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

**Effect**: Child cards always travel with parent through Testing → Ready to Publish or Testing → Executing.

### Change 3: Deployer Agent Child Sync

**File**: `sdlc/opencode/agents/deployer.md`

Add child sync logic after deploy success/failure:

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

**Effect**: Child cards are closed when parent is deployed to production.

## Summary of Changes

| Agent | Change | Effect |
|-------|--------|--------|
| **build.md** | Add child card check at step 0 | Child cards skip execution, no duplicate PRs |
| **tester.md** | Add child sync after test pass/fail | Children travel with parent through Testing |
| **deployer.md** | Add child sync after deploy success/fail | Children travel with parent through Done |

## Expected Lifecycle (After Changes)

```
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
```

## Testing Plan

### Test Case 1: Build Agent Skips Child Cards
1. Create parent issue with 2 child issues
2. Add "Executing" label to child issue
3. Verify build agent exits with message "child card — skipping execution"
4. Verify no PR is created for child issue

### Test Case 2: Tester Syncs Children on Pass
1. Parent issue in Testing with 2 children in Testing
2. Tester runs smoke tests and passes
3. Verify parent moves to Ready to Publish
4. Verify both children move to Ready to Publish

### Test Case 3: Tester Syncs Children on Fail
1. Parent issue in Testing with 2 children in Testing
2. Tester runs smoke tests and fails
3. Verify parent moves to Executing
4. Verify both children move to Executing

### Test Case 4: Deployer Syncs Children on Success
1. Parent issue in Publish with 2 children in Ready to Publish
2. Deployer deploys successfully
3. Verify parent moves to Done and closes
4. Verify both children move to Done and close

### Test Case 5: Deployer Syncs Children on Failure
1. Parent issue in Publish with 2 children in Ready to Publish
2. Deployer deploy fails
3. Verify parent moves to Blocked
4. Verify both children move to Blocked

## Rollback Plan

If changes cause issues:
1. Revert build.md changes (remove child card check)
2. Revert tester.md changes (remove child sync)
3. Revert deployer.md changes (remove child sync)
4. Manually move stuck child issues to correct lanes

## Success Metrics

After implementing changes:
1. **Zero duplicate PRs**: No PRs created for child issues
2. **Child issues travel with parent**: Children always in same lane as parent
3. **No stuck issues**: No child issues stuck in Testing when parent is in Ready to Publish
4. **Clean board**: All child issues move to Done when parent is deployed
