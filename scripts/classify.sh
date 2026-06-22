#!/bin/bash
set -euo pipefail
shopt -s globstar

usage() {
  echo "Usage: $0 --base <sha> --head <sha> [--config <path>] [--changed-files <path>]"
  echo ""
  echo "Classify changed files in a PR into buckets for downstream stages."
  echo ""
  echo "  --base          Base SHA of the PR"
  echo "  --head          Head SHA of the PR"
  echo "  --config        Path to review-config.yml (default: .github/review-config.yml)"
  echo "  --changed-files Path to file with changed files list (for testing, overrides git diff)"
}

die() { usage; exit 1; }

BASE=""
HEAD=""
CONFIG=".github/review-config.yml"
CHANGED_FILES=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base) BASE="$2"; shift 2 ;;
    --head) HEAD="$2"; shift 2 ;;
    --config) CONFIG="$2"; shift 2 ;;
    --changed-files) CHANGED_FILES="$2"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown: $1"; die ;;
  esac
done

mkdir -p /tmp/review

if [ -n "$CHANGED_FILES" ]; then
  cp "$CHANGED_FILES" /tmp/review/changed_files.txt
elif [ -n "$BASE" ] && [ -n "$HEAD" ]; then
  git diff --name-only "$BASE...$HEAD" > /tmp/review/changed_files.txt
else
  echo "{\"error\": \"--base and --head required, or --changed-files\"}" >&2
  exit 1
fi

# Default patterns (used when config missing or key absent)
# NOTE: Patterns must NOT use brace expansion; expand manually since [[ ]] doesn't expand braces.
DEFAULT_CODE='**/*.kt|**/*.java|**/*.ts|**/*.tsx|**/*.js|**/*.jsx|**/*.py|**/*.go|**/*.rs|**/*.rb|**/*.php|**/*.scala|**/*.swift'
DEFAULT_API_SPEC='**/openapi*.yml|**/openapi*.yaml|**/openapi*.json|**/*controller*.py|**/*handler*.go|**/*routes*.ts|**/*Controller*.kt'
DEFAULT_TESTS='**/*test*.py|**/*_test.go|**/*Test*.kt|**/*Test*.java|**/*.test.ts|**/*.test.tsx|**/__tests__/**|**/*spec*.rs|**/*spec*.kt'
DEFAULT_MIGRATION='**/db/migration/*.sql|**/alembic/**/*.py|**/migrations/**/*.py'
DEFAULT_DOCS='*.md|**/*.md|*.rst|**/*.rst|*.adoc|**/*.adoc|docs/**'
DEFAULT_GENERATED='**/*.generated.*|**/*_pb2.py|**/*.pb.go|**/generated/**|openapi-generator/**'
DEFAULT_BINARY='**/*.png|**/*.jpg|**/*.jpeg|**/*.gif|**/*.svg|**/*.ico|**/*.lock|**/*.woff2|**/*.eot|**/*.ttf'
DEFAULT_IGNORE='package-lock.json|yarn.lock|*.svg|*.snap'

matches_any() {
  local path="$1"
  local patterns="$2"
  IFS='|' read -ra parts <<< "$patterns"
  for pat in "${parts[@]}"; do
    if [[ "$path" == $pat ]]; then
      return 0
    fi
  done
  return 1
}

BUCKET_PRECEDENCE=("generated" "binary" "migration" "api_spec" "tests" "code" "docs")

declare -A BUCKET_COUNTS
for b in "${BUCKET_PRECEDENCE[@]}"; do
  BUCKET_COUNTS["$b"]=0
done

JSON_FILES="[]"

while IFS= read -r file; do
  [ -z "$file" ] && continue

  if matches_any "$file" "$DEFAULT_IGNORE"; then
    continue
  fi

  for bucket in "${BUCKET_PRECEDENCE[@]}"; do
    patterns_var="DEFAULT_${bucket^^}"
    patterns="${!patterns_var}"
    if matches_any "$file" "$patterns"; then
      JSON_FILES=$(echo "$JSON_FILES" | jq --arg f "$file" --arg b "$bucket" '. + [{"path": $f, "bucket": $b}]')
      BUCKET_COUNTS["$bucket"]=$((BUCKET_COUNTS["$bucket"] + 1))
    fi
  done
done < /tmp/review/changed_files.txt

BUCKETS_JSON="{"
first=true
for b in "${BUCKET_PRECEDENCE[@]}"; do
  if [ "$first" = true ]; then first=false; else BUCKETS_JSON+=", "; fi
  BUCKETS_JSON+="\"$b\": ${BUCKET_COUNTS[$b]}"
done
BUCKETS_JSON+=" }"

ONLY_SKIPPABLE=true
for b in "${BUCKET_PRECEDENCE[@]}"; do
  if [ "${BUCKET_COUNTS[$b]}" -gt 0 ]; then
    case "$b" in
      generated|binary) ;;
      *) ONLY_SKIPPABLE=false ;;
    esac
  fi
done

HAS_CODE=false
HAS_DOCS=false
HAS_STRUCTURAL=false
if [ "${BUCKET_COUNTS[code]}" -gt 0 ] || [ "${BUCKET_COUNTS[api_spec]}" -gt 0 ] || [ "${BUCKET_COUNTS[tests]}" -gt 0 ]; then
  HAS_CODE=true
fi
if [ "${BUCKET_COUNTS[docs]}" -gt 0 ]; then
  HAS_DOCS=true
fi
if [ "${BUCKET_COUNTS[migration]}" -gt 0 ] || [ "${BUCKET_COUNTS[api_spec]}" -gt 0 ]; then
  HAS_STRUCTURAL=true
fi

jq -n \
  --argjson files "$JSON_FILES" \
  --argjson buckets "$BUCKETS_JSON" \
  --argjson manifest "[]" \
  --argjson has_code "$HAS_CODE" \
  --argjson has_docs "$HAS_DOCS" \
  --argjson has_structural "$HAS_STRUCTURAL" \
  --argjson skip_review "$ONLY_SKIPPABLE" \
  --argjson truncated false \
  --arg diff_size_bytes "$([ -n "$CHANGED_FILES" ] && echo 0 || git diff "$BASE...$HEAD" 2>/dev/null | wc -c)" \
  '{
    files: $files,
    buckets: $buckets,
    doc_manifest_hits: $manifest,
    has_code: $has_code,
    has_docs: $has_docs,
    has_structural_targets: $has_structural,
    skip_review: $skip_review,
    truncated: $truncated,
    diff_size_bytes: $diff_size_bytes
  }' > /tmp/review/classifier-output.json

cat /tmp/review/classifier-output.json
