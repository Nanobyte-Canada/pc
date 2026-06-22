#!/bin/bash
set -euo pipefail

usage() {
  echo "Usage: $0 [--manifest <path>] [--config <path>]"
  echo ""
  echo "Run structural checks (no LLM calls). Reads classifier output."
  echo ""
  echo "  --manifest   Path to classifier output JSON (default: /tmp/review/classifier-output.json)"
  echo "  --config     Path to review-config.yml (default: .github/review-config.yml)"
}

die() { usage; exit 1; }

MANIFEST="/tmp/review/classifier-output.json"
CONFIG=".github/review-config.yml"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest) MANIFEST="$2"; shift 2 ;;
    --config) CONFIG="$2"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown: $1"; die ;;
  esac
done

mkdir -p /tmp/review
FINDINGS_FILE="/tmp/review/structural-findings.txt"
> "$FINDINGS_FILE"

if [ ! -f "$MANIFEST" ]; then
  echo "No classifier output at $MANIFEST — skipping structural checks" >&2
  echo "SUMMARY: 0 HIGH, 0 LOW" > "$FINDINGS_FILE"
  exit 0
fi

CLASSIFICATION=$(cat "$MANIFEST")
HAS_STRUCTURAL=$(echo "$CLASSIFICATION" | jq -r '.has_structural_targets')
HAS_CODE=$(echo "$CLASSIFICATION" | jq -r '.has_code')

if [ "$HAS_STRUCTURAL" != "true" ] && [ "$HAS_CODE" != "true" ]; then
  echo "SUMMARY: 0 HIGH, 0 LOW" > "$FINDINGS_FILE"
  exit 0
fi

HIGH_COUNT=0
LOW_COUNT=0

write_finding() {
  local severity="$1"
  local category="$2"
  local description="$3"
  local location="$4"
  local suggestion="$5"

  {
    echo "[$severity] $category: $description"
    echo "Location: $location"
    echo "Suggestion: $suggestion"
    echo "---"
  } >> "$FINDINGS_FILE"

  if [ "$severity" = "HIGH" ]; then
    HIGH_COUNT=$((HIGH_COUNT + 1))
  else
    LOW_COUNT=$((LOW_COUNT + 1))
  fi
}

CHANGED_PATHS=$(echo "$CLASSIFICATION" | jq -r '.files[].path' | sort -u)
CODE_PATHS=$(echo "$CLASSIFICATION" | jq -r '.files[] | select(.bucket == "code" or .bucket == "api_spec" or .bucket == "tests") | .path' | sort -u)
DOC_PATHS=$(echo "$CLASSIFICATION" | jq -r '.files[] | select(.bucket == "docs") | .path' | sort -u)
MIGRATION_PATHS=$(echo "$CLASSIFICATION" | jq -r '.files[] | select(.bucket == "migration") | .path' | sort -u)
API_SPEC_PATHS=$(echo "$CLASSIFICATION" | jq -r '.files[] | select(.bucket == "api_spec") | .path' | sort -u)

while IFS= read -r mpath; do
  [ -z "$mpath" ] && continue
  filename=$(basename "$mpath")
  if ! echo "$filename" | grep -qE '^V[0-9]+__.*\.sql$'; then
    write_finding "HIGH" "Migration Naming" \
      "Migration file '$filename' does not follow V##__description.sql naming convention" \
      "$mpath" \
      "Rename to V##__description.sql (e.g. V74__add_index.sql)"
  fi
done <<< "$MIGRATION_PATHS"

while IFS= read -r cpath; do
  [ -z "$cpath" ] && continue
  [ ! -f "$cpath" ] && continue

  ext="${cpath##*.}"
  content=$(head -100 "$cpath" 2>/dev/null || true)

  case "$ext" in
    kt)
      prev=""
      while IFS= read -r line; do
        if echo "$line" | grep -qE '^\s*(fun |class |interface |object )'; then
          if ! echo "$prev" | grep -q '/\*\*'; then
            ident=$(echo "$line" | grep -oE '(fun |class |interface |object )\w+' | head -1) || true
            [ -z "$ident" ] && { prev="$line"; continue; }
            write_finding "LOW" "Missing Docstring" \
              "New public declaration '$ident' lacks KDoc comment" \
              "$cpath:1" \
              "Add a KDoc comment /** ... */ before the declaration"
          fi
        fi
        prev="$line"
      done <<< "$content"
      ;;
    ts|tsx)
      prev=""
      while IFS= read -r line; do
        if echo "$line" | grep -qE '^\s*(export (function|const|class|interface|type) |function )'; then
          if ! echo "$prev" | grep -q '/\*\*'; then
            ident=$(echo "$line" | grep -oE '(function |const |class |interface |type )\w+' | head -1) || true
            write_finding "LOW" "Missing Docstring" \
              "New public declaration '$ident' lacks JSDoc comment" \
              "$cpath:1" \
              "Add a JSDoc comment /** ... */ before the declaration"
          fi
        fi
        prev="$line"
      done <<< "$content"
      ;;
    py)
      prev=""
      while IFS= read -r line; do
        if echo "$line" | grep -qE '^\s*(def |class )'; then
          if ! echo "$prev" | grep -qE '^\s*("""|#|def |class )'; then
            ident=$(echo "$line" | grep -oE '(def |class )\w+' | head -1) || true
            if [ -n "$ident" ]; then
              write_finding "LOW" "Missing Docstring" \
                "New public declaration '$ident' lacks docstring" \
                "$cpath:1" \
                "Add a docstring explaining its purpose"
            fi
          fi
        fi
        prev="$line"
      done <<< "$content"
      ;;
  esac
done <<< "$CODE_PATHS"

if ! echo "$DOC_PATHS" | grep -q "README.md"; then
  seen_dirs=""
  while IFS= read -r cpath; do
    [ -z "$cpath" ] && continue
    top_dir=$(echo "$cpath" | cut -d/ -f1)
    if echo "$seen_dirs" | grep -q "^$top_dir$"; then
      continue
    fi
    seen_dirs="$seen_dirs"$'\n'"$top_dir"
    dir_count=$(echo "$CODE_PATHS" | grep -c "^$top_dir/" || true)
    if [ "$dir_count" -ge 3 ]; then
      write_finding "LOW" "README Update Needed" \
        "Multiple files changed in '$top_dir/' but README.md was not updated" \
        "$top_dir/" \
        "Consider updating README.md to reflect the changes"
      break
    fi
  done <<< "$CODE_PATHS"
fi

if [ -n "$API_SPEC_PATHS" ] && [ -z "$DOC_PATHS" ]; then
  first_api=$(echo "$API_SPEC_PATHS" | head -1)
  write_finding "HIGH" "API Spec Drift" \
    "API-related file changed but no documentation files were updated" \
    "$first_api" \
    "Update the corresponding documentation files to reflect the API changes"
fi

if [ "$HIGH_COUNT" -eq 0 ] && [ "$LOW_COUNT" -eq 0 ]; then
  echo "SUMMARY: 0 HIGH, 0 LOW" > "$FINDINGS_FILE"
else
  echo "SUMMARY: $HIGH_COUNT HIGH, $LOW_COUNT LOW" >> "$FINDINGS_FILE"
fi

cat "$FINDINGS_FILE"
