#!/bin/bash
set -euo pipefail

# Update a GitHub Projects v2 card's Status field to a new lane.
# The agent calls this after completing its phase to move the card
# to the next lane (e.g., planner moves card from "Triaging" to "Scope Review").
#
# Requires: GH_TOKEN env var, PROJECT_ID env var
# Usage: ./update-card-status.sh --issue <NUMBER> --lane <LANE_NAME> [--repo <OWNER/REPO>]
#
# Example: ./update-card-status.sh --issue 38 --lane "Scope Review" --repo saurabhbilakhia/pc

usage() {
  echo "Usage: $0 --issue <NUMBER> --lane <LANE_NAME> [--repo <OWNER/REPO>]"
  echo ""
  echo "Move a GitHub Projects v2 card to a new Status lane."
  echo ""
  echo "  --issue   GitHub issue number linked to the card"
  echo "  --lane    Target lane name (e.g., 'Scope Review', 'Planning', 'Executing')"
  echo "  --repo    Repository in OWNER/REPO format (default: from GITHUB_REPOSITORY env var)"
  echo ""
  echo "Required env vars:"
  echo "  GH_TOKEN      GitHub token with project:write scope"
  echo "  PROJECT_ID    Projects v2 board node ID (e.g., PVT_kwHOAC8ohc4BbvSP)"
}

die() { usage; exit 1; }

ISSUE=""
LANE=""
REPO="${GITHUB_REPOSITORY:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --issue) ISSUE="$2"; shift 2 ;;
    --lane) LANE="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
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

if [ -z "$ITEM_ID" ]; then
  echo "Error: No project card found for issue #$ISSUE in repo $REPO"
  echo "Make sure the issue is added to the Projects board."
  exit 1
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
