#!/bin/bash
set -euo pipefail

usage() {
  echo "Usage: $0 --type <code|docs> [--model <model>] [--config <path>] [--dry-run]"
  echo ""
  echo "Run LLM-based review (code quality or documentation drift)."
  echo ""
  echo "  --type       Review type: 'code' or 'docs'"
  echo "  --model      LLM model identifier (default: opencode/deepseek-v4-pro)"
  echo "  --config     Path to review-config.yml (default: .github/review-config.yml)"
  echo "  --dry-run    Print the prompt and context without calling the LLM"
}

die() { usage; exit 1; }

TYPE=""
MODEL="opencode/deepseek-v4-pro"
CONFIG=".github/review-config.yml"
DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --type) TYPE="$2"; shift 2 ;;
    --model) MODEL="$2"; shift 2 ;;
    --config) CONFIG="$2"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown: $1"; die ;;
  esac
done

if [ "$TYPE" != "code" ] && [ "$TYPE" != "docs" ]; then
  echo "Error: --type must be 'code' or 'docs'" >&2
  exit 1
fi

mkdir -p /tmp/review
CLASSIFICATION_FILE="/tmp/review/classifier-output.json"
OUTPUT_FILE="/tmp/review/${TYPE}-findings.txt"
DIFF_FILE="/tmp/review/pr_diff.diff"

if [ ! -f "$CLASSIFICATION_FILE" ]; then
  echo "No classifier output found — skipping $TYPE review" >&2
  echo "SUMMARY: 0 HIGH, 0 LOW" > "$OUTPUT_FILE"
  exit 0
fi

if [ ! -f "$DIFF_FILE" ]; then
  echo "No diff found at $DIFF_FILE — skipping $TYPE review" >&2
  echo "SUMMARY: 0 HIGH, 0 LOW" > "$OUTPUT_FILE"
  exit 0
fi

CLASSIFICATION=$(cat "$CLASSIFICATION_FILE")
REPO_NAME="${GITHUB_REPOSITORY:-unknown/repo}"
CHANGED_PATHS=$(echo "$CLASSIFICATION" | jq -r '.files[].path' | sort -u)
CODE_PATHS=$(echo "$CLASSIFICATION" | jq -r '.files[] | select(.bucket == "code" or .bucket == "api_spec" or .bucket == "tests") | .path' | sort -u)
DOC_PATHS=$(echo "$CLASSIFICATION" | jq -r '.files[] | select(.bucket == "docs") | .path' | sort -u)

# Truncate diff if needed
MAX_DIFF_BYTES=${MAX_DIFF_BYTES:-30720}
DIFF_SIZE=$(wc -c < "$DIFF_FILE")
TRUNCATED=false
if [ "$DIFF_SIZE" -gt "$MAX_DIFF_BYTES" ]; then
  head -c "$MAX_DIFF_BYTES" "$DIFF_FILE" > "${DIFF_FILE}.truncated"
  echo "" >> "${DIFF_FILE}.truncated"
  echo "... (diff truncated at ${MAX_DIFF_BYTES} bytes from ${DIFF_SIZE} bytes)" >> "${DIFF_FILE}.truncated"
  mv "${DIFF_FILE}.truncated" "$DIFF_FILE"
  TRUNCATED=true
fi

# Build prompt
PROMPT_FILE="/tmp/review/${TYPE}-prompt.txt"

if [ "$TYPE" = "code" ]; then
  cat > "$PROMPT_FILE" << PROMPT
You are reviewing a PR for ${REPO_NAME}.
The following files changed in this PR:
${CHANGED_PATHS}

Review the attached PR diff across two passes. Complete Pass 1 first, then Pass 2.

=== PASS 1: CODE QUALITY & BREAKING CHANGES ===
- Logic bugs, null safety, edge cases, boundary conditions, error handling
- Breaking changes: API signatures, exported symbols, schema changes
- Code duplication, SOLID violations, performance (N+1 queries, sync in async paths)
- Test coverage: new logic without tests, deleted tests without replacement
- Cross-reference changed files against attached reference docs for consistency

=== PASS 2: SECURITY & DOCUMENTATION ===
- Auth gaps, injection risks (SQL, XSS, command), secret/token exposure
- Missing or stale documentation relative to changes
- Missing docstrings for public APIs
- Constraint violations from reference docs or CONTRIBUTING.md

OUTPUT RULES:
- Prefix each finding with [HIGH] or [LOW]
- [HIGH] = breaking changes, security, logic bugs, missing critical tests, constraint violations
- [LOW] = style, minor doc gaps, refactoring opportunities, non-critical warnings

FORMAT EACH FINDING AS:
[SEVERITY] Category: Description
Location: file:line (or N/A)
Suggestion: how to fix or improve

Separate findings with "---"
End with exactly: SUMMARY: X HIGH, Y LOW
PROMPT
else
  cat > "$PROMPT_FILE" << PROMPT
You are reviewing documentation changes for a PR in ${REPO_NAME}.
The PR changes include:
- Code files: ${CODE_PATHS}
- Doc files: ${DOC_PATHS}

Review the documentation for semantic drift relative to the code changes:

1. README staleness: Does the README still accurately reflect the project?
2. Architecture consistency: Do architectural docs contradict the code changes?
3. Outdated examples: Do code examples in docs still match the changed API?
4. Missing docs: Does this PR introduce features/concepts that lack documentation?
5. Doc-code contradiction: Does any doc describe behavior that disagrees with the implementation?

OUTPUT RULES:
[SEVERITY] Category: Description
Location: file:line (or N/A)
Suggestion: how to fix or improve

Separate findings with "---"
End with: SUMMARY: X HIGH, Y LOW
PROMPT
fi

# Gather reference docs
declare -a FILE_ARGS
FILE_ARGS+=(--file "$DIFF_FILE")
for doc in "README.md" "CONTRIBUTING.md"; do
  [ -f "$doc" ] && FILE_ARGS+=(--file "$doc")
done

if [ "$DRY_RUN" = true ]; then
  echo "=== DRY RUN: $TYPE review ==="
  echo "Model: $MODEL"
  echo "Prompt file: $PROMPT_FILE"
  echo "Prompt content:"
  cat "$PROMPT_FILE"
  echo ""
  echo "Files attached: ${FILE_ARGS[*]}"
  echo "=== END DRY RUN ==="
  echo "SUMMARY: 0 HIGH, 0 LOW" > "$OUTPUT_FILE"
  exit 0
fi

# Only attempt LLM call if opencode is available
if command -v opencode &>/dev/null; then
  cat "$PROMPT_FILE" | opencode run --model "$MODEL" "${FILE_ARGS[@]}" 2>&1 | tee "$OUTPUT_FILE"
else
  echo "Warning: opencode not found — skipping $TYPE review. Install with: npm install -g opencode-ai" >&2
  echo "SUMMARY: 0 HIGH, 0 LOW" > "$OUTPUT_FILE"
fi
