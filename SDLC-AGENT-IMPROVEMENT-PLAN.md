# SDLC Agent Improvement Plan

## Problem Summary

Issue #122 (Wheel Strategy UI) spawned **110 descendant issues** nested **10 levels deep**, making it impossible to review or manage. The root cause is missing guards in the planner agent that allow recursive decomposition.

### Current State Visualization

```
#122 Wheel Strategy UI (110 descendants, 10 levels deep)
├── #123 KPI Cards Simplification
├── #124 Filter Ticker List
├── #125 Top Liquid Options Watchlist
│   └── #132 Create WheelWatchlist component
│       └── #144 WheelWatchlist: React component
│           └── (continues...)
└── #126 Side Panel UI Refactor
    ├── #134 Remove CSP/CC Buttons
    ├── #135 Extend ChainPanelContext
    ├── #136 Refactor WheelChainPanel
    │   └── (continues...)
    └── #137 Wire WheelPage Click Handlers
        ├── #159 Wire Click Handlers
        │   ├── #180 Update handleEmptySlotClick
        │   ├── #181 Implement handleAddTickerClick
        │   │   └── #199 Implement callback
        │   │       └── #211 Unit tests
        │   │           ├── #223 Create WheelPage.test.tsx
        │   │           │   └── #228 (duplicate!)
        │   │           └── #224 Verify mutual exclusivity
        │   │               ├── #229 Handler mutual exclusivity tests
        │   │               └── #230 Panel rendering mutual exclusivity
        │   │                   ├── #232 Write rendering state unit tests
        │   │                   │   ├── #234 Test WheelChainPanel
        │   │                   │   ├── #235 Test OrderPanel
        │   │                   │   └── #236 Test neither panel renders
        │   │                   └── #233 Defensive edge-case test
        │   │                       ├── #237 Write defensive test
        │   │                       └── #238 Run full test suite
        │   └── #210 Extend ChainPanelContext
        └── #160 Update Panel Rendering
            ├── #182 Update WheelChainPanel rendering
            │   └── (continues...)
            └── #183 Remove handleCCSlotClick
                └── (continues...)
```

---

## Root Cause Analysis

### Missing Guards in `planner.md`

| Guard | Current | Required |
|-------|---------|----------|
| **Parent check** | ❌ None | Must check if issue is already a child |
| **Depth limit** | ❌ None | Max 2 levels (parent → child) |
| **Child count limit** | ❌ None | Max 5-7 children per parent |
| **Granularity guidance** | ❌ None | Tasks should be 1-3 days of work |
| **Scope threshold** | ❌ None | Don't decompose issues < 2 days |

### Build Agent Issue

The build agent treats child cards as independent planning units:
```bash
# From build.md - this line treats children as planning candidates
CHILD_CARDS=$(gh issue list --repo "$GITHUB_REPOSITORY" --state all --json number,body --jq \
  ".[] | select(.body | test(\"Parent: #$ISSUE_NUMBER\\\\b\")) | .number")
```

When a child card enters "Planning" lane, the planner runs on it and creates MORE children.

### Lane Distribution Problem

Current status of 110 descendant issues:
- **Plan Review**: 67 (correct for planning phase)
- **Testing**: 13 (❌ should not be here during planning)
- **Unknown/No Status**: 10 (❌ never processed)
- **Backlog**: 3 (❌ should not be here)
- **Done**: 17

**Rule: Child tasks must ONLY be in Scoping or Planning lanes**
- **Scoping**: If scope needs to be determined or open questions exist
- **Plan Review**: If scope is approved and waiting for execution
- Never in: Backlog, No Status, Testing, Executing, Blocked, Ready to Publish

---

## Solution Plan

### Phase 0: Enforce Child Task Lane Restrictions (CRITICAL)

**File**: `Nanobyte-Services/sdlc/opencode/agents/planner.md`

Add at the beginning of the file, after the frontmatter:

```markdown
## Child Task Lane Rules (CRITICAL)

Child tasks (issues with "Parent: #N" in body) have STRICT lane restrictions:

### Allowed lanes for child tasks
- **Scoping**: When scope needs to be determined or open questions exist
- **Plan Review**: When scope is approved, waiting for execution

### Forbidden lanes for child tasks
- ❌ Backlog
- ❌ No Status / Unknown
- ❌ Testing
- ❌ Executing
- ❌ Blocked
- ❌ Ready to Publish
- ❌ Publish
- ❌ Done

### When creating child issues
After creating each child issue, IMMEDIATELY set its status to "Plan Review":
```bash
# Get the child issue's project item ID
CHILD_ITEM_ID=$(gh api graphql -f query="
query {
  node(id: \"$PROJECT_ID\") {
    ... on ProjectV2 {
      items(first: 100) {
        nodes {
          id
          content {
            ... on Issue { number repository { nameWithOwner } }
          }
        }
      }
    }
  }
}" --jq ".data.node.items.nodes[] | select(.content.number == $CHILD_ISSUE_NUMBER and .content.repository.nameWithOwner == \"$GITHUB_REPOSITORY\") | .id")

# Set status to Plan Review
gh project item-edit --project-id $PROJECT_ID --id $CHILD_ITEM_ID --field "Status" --option-id $(gh api graphql -f query="
query {
  node(id: \"$PROJECT_ID\") {
    ... on ProjectV2 {
      fields(first: 20) {
        nodes {
          ... on ProjectV2SingleSelectField {
            name
            options { id name }
          }
        }
      }
    }
  }
}" --jq '.data.node.fields.nodes[] | select(.name == "Status") | .options[] | select(.name == "Plan Review") | .id')
```

### When child tasks need scoping
If a child task has open questions or unclear scope:
1. Set status to "Scoping" (NOT Backlog)
2. Post a comment explaining what needs clarification
3. The scoping agent will pick it up

### NEVER put child tasks in these lanes
- **Backlog** = "we'll do this someday" (child tasks are part of active work)
- **No Status** = "forgotten" (child tasks must always have a status)
- **Testing** = "code written, needs verification" (child tasks don't go here directly)
```

### Phase 1: Add Parent Check to Planner Agent

**File**: `Nanobyte-Services/sdlc/opencode/agents/planner.md`

Add at the beginning of "On start" section:

```markdown
## On start

0. **CRITICAL: Check if this is a child card**
   ```bash
   ISSUE_BODY=$(gh issue view $ISSUE_NUMBER --json body --jq '.body')
   if echo "$ISSUE_BODY" | grep -qP 'Parent:\s*#\d+'; then
     echo "Issue #$ISSUE_NUMBER is a child card — skipping planning"
     echo "Child cards should not be decomposed further."
     echo "If this card needs breakdown, escalate to the parent issue."
     exit 0
   fi
   ```

1. Read the approved scope from the issue's Scope Document comment
   ...
```

### Phase 2: Add Depth and Count Limits

**File**: `Nanobyte-Services/sdlc/opencode/agents/planner.md`

Add validation before creating child cards:

```markdown
## Before creating child cards

### Validate decomposition is warranted
- If the issue's scope is < 2 days of work, do NOT create children
- If the issue already has children (check with `gh issue list --search "parent:#ISSUE_NUMBER"`), do NOT create more

### Enforce limits
- **Maximum children per parent**: 5
- **Maximum nesting depth**: 2 levels (parent → child only)
- **Minimum task size**: 1 day of work

### If limits would be exceeded
Post a comment explaining why further decomposition is not recommended:
```bash
gh issue comment $ISSUE_NUMBER --body "## Planning Note

This issue's scope is appropriately sized for direct execution.
Creating child cards would exceed the recommended limits:
- Maximum 5 children per parent
- Maximum 2 levels of nesting

Recommendation: Move to Executing lane for direct implementation."
```
Then move to "Plan Review" for human approval.
```

### Phase 3: Add Granularity Guidance

**File**: `Nanobyte-Services/sdlc/opencode/agents/planner.md`

Add a new section:

```markdown
## Decomposition Guidelines

### When to create child cards
- Issue involves 3+ distinct modules/features
- Total scope exceeds 5 days of work
- Clear dependency boundaries exist between parts

### When NOT to create child cards
- Issue is focused on a single module/feature
- Total scope is < 3 days of work
- Changes are tightly coupled (can't be done independently)

### Child card sizing
Each child card should represent:
- **1-3 days of focused work**
- **One clear deliverable**
- **Independent test criteria**

### Anti-patterns to avoid
- Creating a child card for each file change
- Creating a child card for each function/class
- Breaking "write tests" into separate cards from "implement feature"
- Creating cards for code cleanup that should be part of implementation
```

### Phase 4: Add Parent-Child Visibility to Board

**Option A: Use GitHub's Native Fields**

The board already has "Parent issue" and "Sub-issues progress" fields. Ensure they're being populated:

```bash
# In planner.md, after creating child issues:
# Set parent field on child card
gh project item-edit --project-id $PROJECT_ID --id $CHILD_ITEM_ID --field "Parent issue" --value "$ISSUE_NUMBER"

# Update sub-issue progress on parent
TOTAL_CHILDREN=$(gh issue list --search "parent:#$ISSUE_NUMBER" --json number --jq 'length')
gh project item-edit --project-id $PROJECT_ID --id $PARENT_ITEM_ID --field "Sub-issues progress" --value "0/$TOTAL_CHILDREN"
```

**Option B: Add Custom Field for Child Count**

Add a "Child Count" number field to the board:

```bash
# Create field via API
gh api graphql -f query='
mutation {
  createProjectV2Field(input: {
    projectId: "'"$PROJECT_ID"'"
    dataType: NUMBER
    name: "Child Count"
  }) {
    projectV2Field { id name }
  }
}'
```

Then update in planner.md:
```bash
# After creating children
CHILD_COUNT=$(gh issue list --search "parent:#$ISSUE_NUMBER" --json number --jq 'length')
gh project item-edit --project-id $PROJECT_ID --id $ITEM_ID --field "Child Count" --value $CHILD_COUNT
```

**Option C: Add Parent Label for Visual Grouping**

Add labels to child issues for visual filtering:

```bash
# In planner.md, after creating child issue
gh issue edit $CHILD_ISSUE_NUMBER --repo $GITHUB_REPOSITORY --add-label "child-of-$ISSUE_NUMBER"
```

### Phase 5: Fix Build Agent Child Handling

**File**: `Nanobyte-Services/sdlc/opencode/agents/build.md`

The build agent should NOT process child cards that are in Planning lane. Add check:

```markdown
## Find child cards (step 2 - modified)

2. Find all child cards of this parent:
   ```bash
   CHILD_CARDS=$(gh issue list --repo "$GITHUB_REPOSITORY" --state all --json number,body --jq \
     ".[] | select(.body | test(\"Parent: #$ISSUE_NUMBER\\\\b\")) | .number")
   ```
   
   **CRITICAL: Filter out child cards in Planning/Scoping lanes**
   ```bash
   # Only process children that are in Executing, Testing, or Ready to Publish
   VALID_CHILD_CARDS=""
   for CHILD in $CHILD_CARDS; do
     CHILD_STATUS=$(bash scripts/update-card-status.sh --issue $CHILD --get-status --repo $GITHUB_REPOSITORY)
     if [[ "$CHILD_STATUS" == "Executing" || "$CHILD_STATUS" == "Testing" || "$CHILD_STATUS" == "Ready to Publish" ]]; then
       VALID_CHILD_CARDS="$VALID_CHILD_CARDS $CHILD"
     fi
   done
   CHILD_CARDS="$VALID_CHILD_CARDS"
   ```
   
   If no child cards exist, treat this issue as the only card (single-card mode).
```

### Phase 5B: Add Child Lane Guard to Build Agent

**File**: `Nanobyte-Services/sdlc/opencode/agents/build.md`

Add a new section to prevent moving child tasks to wrong lanes:

```markdown
## Child Task Lane Restrictions (CRITICAL)

When processing child tasks, enforce these lane rules:

### Child tasks can only move to:
- **Executing** → When implementation starts
- **Testing** → When ALL children are complete and parent moves to Testing
- **Blocked** → When blocked by dependency or error

### Child tasks must NEVER be moved to:
- ❌ Backlog
- ❌ No Status
- ❌ Ready to Publish (only parent goes here)
- ❌ Done (only parent goes here)

### When moving a child to Testing
**DO NOT move individual children to Testing prematurely.**

Instead:
1. Keep all children in "Executing" until ALL are complete
2. When Sub-issue Progress = 100%, move the PARENT to Testing
3. Then move ALL children to Testing at once

```bash
# WRONG: Moving individual child to Testing
# bash scripts/update-card-status.sh --issue $CHILD_ISSUE --lane "Testing"

# RIGHT: Move all children when parent is ready
for CHILD in $CHILD_CARDS; do
  bash scripts/update-card-status.sh --issue $CHILD --lane "Testing" --repo $GITHUB_REPOSITORY
done
bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Testing" --repo $GITHUB_REPOSITORY
```

### When a child has open questions
If a child task needs scoping or has open questions:
1. Set status to "Scoping" (NOT Backlog)
2. Post a comment explaining what needs clarification
3. The scoping agent will pick it up
```

### Phase 6: Add Status Sync Logic

**File**: `Nanobyte-Services/sdlc/opencode/agents/build.md`

When moving a parent to Testing, ensure children are in correct state:

```markdown
## Move to Testing (step 7 - modified)

7. When moving parent to Testing:
   ```bash
   # Move all child cards to Testing (they should already be there)
   for CHILD in $CHILD_CARDS; do
     bash scripts/update-card-status.sh --issue $CHILD --lane "Testing" --repo $GITHUB_REPOSITORY
   done
   
   # Move parent card to Testing
   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Testing" --repo $GITHUB_REPOSITORY
   ```

   **Do NOT move individual child cards to Testing prematurely**
   - Child cards should stay in Executing until ALL children are complete
   - Only then move the parent (and all children) to Testing
```

---

## Implementation Checklist

### High Priority (Do First)

- [ ] Add child lane restrictions to `planner.md` (Phase 0)
- [ ] Add parent check to `planner.md` (Phase 1)
- [ ] Add depth/count limits to `planner.md` (Phase 2)
- [ ] Fix build agent child filtering (Phase 5)
- [ ] Add child lane guard to `build.md` (Phase 5B)

### Medium Priority

- [ ] Add granularity guidance to `planner.md` (Phase 3)
- [ ] Ensure Parent issue field is populated (Phase 4A)
- [ ] Add status sync logic to `build.md` (Phase 6)

### Low Priority

- [ ] Add Child Count custom field (Phase 4B)
- [ ] Add parent labels for visual grouping (Phase 4C)
- [ ] Update documentation in `CONTRIBUTING.md`

---

## Testing the Changes

### Test Case 1: Child Card Should Not Decompose
1. Create issue with "Parent: #122" in body
2. Add "Planning" label
3. Verify planner agent exits with message "child card — skipping planning"

### Test Case 2: Depth Limit Enforcement
1. Create parent issue with 3 existing children
2. Try to plan the parent
3. Verify planner creates at most 5 total children

### Test Case 3: Build Agent Ignores Planning Children
1. Parent issue has 3 children: 2 in Executing, 1 in Planning
2. Build agent runs on parent
3. Verify it only processes the 2 Executing children

### Test Case 4: Visual Parent-Child Relationship
1. Create parent issue
2. Plan and create 3 child issues
3. Verify "Sub-issues progress" field shows "0/3"
4. Verify board view shows parent with progress indicator

### Test Case 5: Child Lane Restrictions (CRITICAL)
1. Create parent issue, plan it, create 3 children
2. Verify all children are in "Plan Review" lane (NOT Backlog, NOT No Status)
3. Move parent to Executing
4. Build agent processes children
5. Verify children move to "Executing" (NOT Testing individually)
6. Complete all children
7. Verify children move to "Testing" only when parent moves
8. Verify NO child is in Backlog, No Status, or Ready to Publish

---

## Rollback Plan

If changes cause issues:

1. Revert planner.md changes
2. Revert build.md changes
3. Manually close excessive child issues:
   ```bash
   # Find all descendants of #122
   gh issue list --search "parent:#122" --json number --jq '.[].number' | \
     xargs -I {} gh issue close {} --repo Nanobyte-Canada/pc
   ```

---

## Success Metrics

After implementing changes:

1. **No issue should have more than 5 direct children**
2. **No issue should be nested more than 2 levels deep**
3. **All child cards should show parent relationship on board**
4. **Build agent should only process children in Executing/Testing lanes**
5. **Planning phase should complete in < 5 minutes per issue**
6. **ZERO child tasks in Backlog, No Status, or Testing during planning phase**
7. **All child tasks must be in Scoping or Plan Review lanes during planning**

---

## Appendix: Current State Cleanup

To fix the existing 110-child mess in issue #122:

### Option A: Close all descendants, keep top-level children
```bash
# Close all descendants except direct children of #122
gh issue list --search "parent:#122" --json number,body --jq '.[] | select(.body | test("Parent: #122")) | .number' | \
  while read CHILD; do
    # Check if this child has its own children
    GRANDCHILDREN=$(gh issue list --search "parent:#$CHILD" --json number --jq 'length')
    if [ "$GRANDCHILDREN" -gt 0 ]; then
      echo "Closing #$CHILD (has $GRANDCHILDREN grandchildren)"
      gh issue close $CHILD --repo Nanobyte-Canada/pc
    fi
  done
```

### Option B: Re-plan from scratch
1. Close all descendants of #122
2. Move #122 back to Scoping
3. Re-run planner with new guards
