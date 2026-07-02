#!/bin/bash
set -euo pipefail

usage() {
  echo "Usage: $0 [--aggregated <path>] [--dry-run]"
  echo ""
  echo "Post aggregated review findings as a PR comment."
  echo ""
  echo "  --aggregated  Path to aggregated output (default: /tmp/review/aggregated-output.txt)"
  echo "  --dry-run     Print the comment without posting"
}

die() { usage; exit 1; }

AGGREGATED_FILE="/tmp/review/aggregated-output.txt"
DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --aggregated) AGGREGATED_FILE="$2"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown: $1"; die ;;
  esac
done

if [ ! -f "$AGGREGATED_FILE" ]; then
  echo "No aggregated output at $AGGREGATED_FILE — nothing to post" >&2
  exit 0
fi

COMMENT_BODY=$(cat "$AGGREGATED_FILE")
COMMENT_BODY=$(echo "$COMMENT_BODY" | sed 's/^SUMMARY:/<!-- SUMMARY:/; s/$/ -->/')

PR_NUMBER="${GITHUB_EVENT_NUMBER:-}"
if [ -z "$PR_NUMBER" ] && [ -f "${GITHUB_EVENT_PATH:-}" ]; then
  PR_NUMBER=$(jq -r '.pull_request.number // .issue.number // empty' "$GITHUB_EVENT_PATH" 2>/dev/null || echo "")
fi

if [ -z "$PR_NUMBER" ]; then
  echo "No PR number found — cannot post comment. Output saved to $AGGREGATED_FILE" >&2
  echo "$COMMENT_BODY"
  exit 0
fi

RUN_URL="${GITHUB_SERVER_URL:-}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
FULL_COMMENT="${COMMENT_BODY}

---

<sub>[View workflow run](${RUN_URL})</sub>"

if [ "$DRY_RUN" = true ]; then
  echo "=== DRY RUN: Would post to PR #$PR_NUMBER ==="
  echo "$FULL_COMMENT"
  echo "=== END DRY RUN ==="
  exit 0
fi

if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "No GITHUB_TOKEN set — cannot post comment. Output saved to $AGGREGATED_FILE" >&2
  echo "$FULL_COMMENT"
  exit 0
fi

API_URL="${GITHUB_API_URL:-https://api.github.com}"
curl -s -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Content-Type: application/json" \
  "${API_URL}/repos/${GITHUB_REPOSITORY}/issues/${PR_NUMBER}/comments" \
  -d "$(jq -n --arg body "$FULL_COMMENT" '{body: $body}')"

echo ""
echo "Comment posted to PR #$PR_NUMBER"
