#!/bin/bash
set -euo pipefail

# Update a GitHub Projects v2 card's Status field to a new lane.
# The agent calls this after completing its phase to move the card
# to the next lane (e.g., planner moves card from "Triaging" to "Scope Review").
#
# Requires: GH_TOKEN env var, PROJECT_ID env var
# Usage: ./update-card-status.sh --issue <NUMBER> --lane <LANE_NAME> [--repo <OWNER/REPO>] [--cascade]
#
# Example: ./update-card-status.sh --issue 38 --lane "Scope Review" --repo saurabhbilakhia/pc
#
#   --cascade   Also move child cards (issues with "Parent: #<NUMBER>" in body)
#               to the same lane. Only meaningful for parent (non-child) issues.

usage() {
  echo "Usage: $0 --issue <NUMBER> --lane <LANE_NAME> [--repo <OWNER/REPO>] [--cascade]"
  echo ""
  echo "Move a GitHub Projects v2 card to a new Status lane."
  echo ""
  echo "  --issue   GitHub issue number linked to the card"
  echo "  --lane    Target lane name (e.g., 'Scope Review', 'Planning', 'Executing')"
  echo "  --repo    Repository in OWNER/REPO format (default: from GITHUB_REPOSITORY env var)"
  echo "  --cascade Also move child cards (issues with 'Parent: #<NUMBER>' in body)"
  echo ""
  echo "Required env vars:"
  echo "  GH_TOKEN      GitHub token with project:write scope"
  echo "  PROJECT_ID    Projects v2 board node ID (e.g., PVT_kwHOAC8ohc4BbvSP)"
}

die() { usage; exit 1; }

ISSUE=""
LANE=""
REPO="${GITHUB_REPOSITORY:-}"
CASCADE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --issue) ISSUE="$2"; shift 2 ;;
    --lane) LANE="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --cascade) CASCADE=true; shift ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown: $1"; die ;;
  esac
done

[ -z "$ISSUE" ] && { echo "Error: --issue is required"; die; }
[ -z "$LANE" ] && { echo "Error: --lane is required"; die; }
[ -z "$REPO" ] && { echo "Error: --repo is required (or set GITHUB_REPOSITORY env var)"; die; }
[ -z "${GH_TOKEN:-}" ] && { echo "Error: GH_TOKEN env var is required"; die; }
[ -z "${PROJECT_ID:-}" ] && { echo "Error: PROJECT_ID env var is required"; die; }

OWNER=$(echo "$REPO" | cut -d'/' -f1)

# Step 1: Find the project item (card) linked to this issue
# Query the project for items, find the one whose content matches our issue
ITEM_ID=$(gh api graphql -f query="
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
}" --jq ".data.node.items.nodes[] | select(.content.number == $ISSUE and .content.repository.nameWithOwner == \"$REPO\") | .id" 2>/dev/null || echo "")

# Step 1a: If the issue is not on the board, add it automatically
if [ -z "$ITEM_ID" ]; then
  echo "Issue #$ISSUE not found on project board — adding it..."
  ISSUE_NODE_ID=$(gh api "repos/$REPO/issues/$ISSUE" --jq '.node_id' 2>/dev/null || echo "")
  if [ -z "$ISSUE_NODE_ID" ]; then
    echo "Error: Could not resolve node_id for issue #$ISSUE"
    exit 1
  fi
  ITEM_ID=$(gh api graphql -f query="
mutation {
  addProjectV2ItemById(input: {projectId: \"$PROJECT_ID\", contentId: \"$ISSUE_NODE_ID\"}) {
    item { id }
  }
}" --jq '.data.addProjectV2ItemById.item.id' 2>/dev/null || echo "")
  if [ -z "$ITEM_ID" ]; then
    echo "Error: Failed to add issue #$ISSUE to project board"
    exit 1
  fi
  echo "Added issue #$ISSUE to project board (item: $ITEM_ID)"
fi

echo "Found card: $ITEM_ID for issue #$ISSUE"

# Step 2: Find the Status field and its options
# Query the project for the Status single-select field and its option IDs
FIELD_JSON=$(gh api graphql -f query="
query {
  node(id: \"$PROJECT_ID\") {
    ... on ProjectV2 {
      fields(first: 20) {
        nodes {
          ... on ProjectV2SingleSelectField {
            id
            name
            options { id name }
          }
        }
      }
    }
  }
}" 2>/dev/null)

STATUS_FIELD_ID=$(echo "$FIELD_JSON" | jq -r '.data.node.fields.nodes[] | select(.name == "Status") | .id')
OPTION_ID=$(echo "$FIELD_JSON" | jq -r --arg lane "$LANE" '.data.node.fields.nodes[] | select(.name == "Status") | .options[] | select(.name == $lane) | .id')

if [ -z "$STATUS_FIELD_ID" ] || [ "$STATUS_FIELD_ID" = "null" ]; then
  echo "Error: Status field not found in project $PROJECT_ID"
  exit 1
fi

if [ -z "$OPTION_ID" ] || [ "$OPTION_ID" = "null" ]; then
  echo "Error: Lane '$LANE' not found in Status field options"
  echo "Available lanes:"
  echo "$FIELD_JSON" | jq -r '.data.node.fields.nodes[] | select(.name == "Status") | .options[].name'
  exit 1
fi

echo "Moving card to: $LANE (option: $OPTION_ID)"

# Step 3: Update the card's Status field
gh api graphql -f query="
mutation {
  updateProjectV2ItemFieldValue(input: {
    projectId: \"$PROJECT_ID\"
    itemId: \"$ITEM_ID\"
    fieldId: \"$STATUS_FIELD_ID\"
    value: { singleSelectOptionId: \"$OPTION_ID\" }
  }) {
    projectV2Item { id }
  }
}" > /dev/null

echo "✓ Card moved to '$LANE'"

# Step 4: Cascade to child cards (if --cascade flag is set)
if [ "$CASCADE" = true ]; then
  echo "Looking for child cards (issues with 'Parent: #$ISSUE' in body)..."
  CHILDREN=$(gh issue list --repo "$REPO" --search "\"Parent: #$ISSUE\" in:body" --state all --json number --jq '.[].number' 2>/dev/null || echo "")
  if [ -z "$CHILDREN" ]; then
    echo "No child cards found"
  else
    for CHILD in $CHILDREN; do
      echo "  Moving child #$CHILD to '$LANE'..."

      # Check if child is already on the board
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
}" --jq ".data.node.items.nodes[] | select(.content.number == $CHILD and .content.repository.nameWithOwner == \"$REPO\") | .id" 2>/dev/null || echo "")

      if [ -z "$CHILD_ITEM_ID" ]; then
        # Add child to board
        CHILD_NODE_ID=$(gh api "repos/$REPO/issues/$CHILD" --jq '.node_id' 2>/dev/null || echo "")
        if [ -z "$CHILD_NODE_ID" ]; then
          echo "  ⚠ Could not resolve node_id for #$CHILD — skipping"
          continue
        fi
        CHILD_ITEM_ID=$(gh api graphql -f query="
mutation {
  addProjectV2ItemById(input: {projectId: \"$PROJECT_ID\", contentId: \"$CHILD_NODE_ID\"}) {
    item { id }
  }
}" --jq '.data.addProjectV2ItemById.item.id' 2>/dev/null || echo "")
        if [ -z "$CHILD_ITEM_ID" ]; then
          echo "  ⚠ Failed to add #$CHILD to board — skipping"
          continue
        fi
        echo "  Added #$CHILD to project board"
      fi

      # Move child to same lane
      gh api graphql -f query="
mutation {
  updateProjectV2ItemFieldValue(input: {
    projectId: \"$PROJECT_ID\"
    itemId: \"$CHILD_ITEM_ID\"
    fieldId: \"$STATUS_FIELD_ID\"
    value: { singleSelectOptionId: \"$OPTION_ID\" }
  }) {
    projectV2Item { id }
  }
}" > /dev/null

      echo "  ✓ Child #$CHILD moved to '$LANE'"
    done
  fi
fi
