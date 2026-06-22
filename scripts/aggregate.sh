#!/bin/bash
set -euo pipefail

usage() {
  echo "Usage: $0 [--config <path>]"
  echo ""
  echo "Aggregate findings from all stages, deduplicate, apply overrides, format output."
}

die() { usage; exit 1; }

CONFIG=".github/review-config.yml"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --config) CONFIG="$2"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown: $1"; die ;;
  esac
done

mkdir -p /tmp/review
FINDINGS_DIR="/tmp/review"
AGGREGATED_FILE="$FINDINGS_DIR/aggregated-output.txt"
> "$AGGREGATED_FILE"

ALL_FINDINGS=$(cat "$FINDINGS_DIR"/{structural,code,doc}-findings.txt 2>/dev/null || true)

if [ -z "$(echo "$ALL_FINDINGS" | grep -v '^SUMMARY:' | grep -v '^$')" ]; then
  {
  echo "## PR Review${PR_TITLE:+: $PR_TITLE}"
    echo ""
    echo "| Severity | Count |"
    echo "|----------|-------|"
    echo "| :red_circle: **HIGH** | 0 |"
    echo "| :yellow_circle: **LOW** | 0 |"
    echo ""
    echo "> :information_source: No findings reported. Consider a manual review for completeness."
  } > "$AGGREGATED_FILE"
  echo "SUMMARY: 0 HIGH, 0 LOW" >> "$AGGREGATED_FILE"
  [ -n "${GITHUB_OUTPUT:-}" ] && echo "high_count=0" >> "$GITHUB_OUTPUT" && echo "low_count=0" >> "$GITHUB_OUTPUT"
  echo "Aggregated: 0 HIGH, 0 LOW"
  cat "$AGGREGATED_FILE"
  exit 0
fi

declare -A SEEN
declare -A CATEGORIES
declare -A LOCATIONS
declare -A SUGGESTIONS
FINDINGS_LIST=()
INDEX=0

current_block=""
while IFS= read -r line; do
  if [ "$line" = "---" ] || [[ "$line" =~ ^SUMMARY: ]]; then
    if [ -n "$current_block" ]; then
      severity=$(echo "$current_block" | grep -oE '^\[(HIGH|LOW)\]' | head -1 | tr -d '[]')
      rest=$(echo "$current_block" | sed 's/^\[HIGH\] //;s/^\[LOW\] //')
      category_desc=$(echo "$rest" | head -1)
      location=$(echo "$current_block" | grep '^Location:' | sed 's/^Location: //')
      suggestion=$(echo "$current_block" | grep '^Suggestion:' | sed 's/^Suggestion: //')

      if [ -n "$severity" ]; then
        dedup_key="$category_desc|$location"
        if [ -z "${SEEN[$dedup_key]:-}" ]; then
          SEEN["$dedup_key"]="$severity"
          CATEGORIES[$INDEX]="$category_desc"
          LOCATIONS[$INDEX]="$location"
          SUGGESTIONS[$INDEX]="$suggestion"
          FINDINGS_LIST+=("$INDEX")
          INDEX=$((INDEX + 1))
        fi
      fi
    fi
    current_block=""
  else
    current_block+="$line"$'\n'
  fi
done <<< "$ALL_FINDINGS"

# Apply rule overrides from config
apply_override() {
  local severity="$1"
  local category="$2"

  if echo "$category" | grep -qi "missing.*docstring\|lacks.*doc\|lacks.*jsdoc\|lacks.*kdoc"; then
    if [ -f "$CONFIG" ] && grep -q "missing_docstring:" "$CONFIG" 2>/dev/null; then
      override=$(grep "missing_docstring:" "$CONFIG" | grep -oE '[a-zA-Z0-9_]+$' || true)
      [ -n "$override" ] && echo "$override" && return
    fi
  fi
  if echo "$category" | grep -qi "performance"; then
    if [ -f "$CONFIG" ] && grep -q "performance_concern:" "$CONFIG" 2>/dev/null; then
      override=$(grep "performance_concern:" "$CONFIG" | grep -oE '[a-zA-Z0-9_]+$' || true)
      [ -n "$override" ] && echo "$override" && return
    fi
  fi
  echo "$severity"
}

HIGH_FINDINGS=()
LOW_FINDINGS=()
for idx in "${FINDINGS_LIST[@]}"; do
  sev="${SEEN["${CATEGORIES[$idx]}|${LOCATIONS[$idx]}"]}"
  sev=$(apply_override "$sev" "${CATEGORIES[$idx]}")
  if [ "$sev" = "HIGH" ]; then
    HIGH_FINDINGS+=("$idx")
  else
    LOW_FINDINGS+=("$idx")
  fi
done

HIGH_COUNT=${#HIGH_FINDINGS[@]}
LOW_COUNT=${#LOW_FINDINGS[@]}

{
  echo "## PR Review"
  echo ""
  echo "| Severity | Count |"
  echo "|----------|-------|"
  echo "| :red_circle: **HIGH** | $HIGH_COUNT |"
  echo "| :yellow_circle: **LOW** | $LOW_COUNT |"

  if [ "$HIGH_COUNT" -gt 0 ]; then
    echo ""
    echo "---"
    echo ""
    echo "### :red_circle: HIGH Findings"
    echo ""
    local_counter=1
    for idx in "${HIGH_FINDINGS[@]}"; do
      echo "**$local_counter. ${CATEGORIES[$idx]}**"
      echo "Location: \`${LOCATIONS[$idx]}\`"
      echo "Suggestion: ${SUGGESTIONS[$idx]}"
      echo ""
      local_counter=$((local_counter + 1))
    done
  fi

  if [ "$LOW_COUNT" -gt 0 ]; then
    echo ""
    echo "---"
    echo ""
    echo "### :yellow_circle: LOW Findings"
    echo ""
    local_counter=1
    for idx in "${LOW_FINDINGS[@]}"; do
      echo "**$local_counter. ${CATEGORIES[$idx]}**"
      echo "Location: \`${LOCATIONS[$idx]}\`"
      echo "Suggestion: ${SUGGESTIONS[$idx]}"
      echo ""
      local_counter=$((local_counter + 1))
    done
  fi

  if [ "$HIGH_COUNT" -eq 0 ] && [ "$LOW_COUNT" -eq 0 ]; then
    echo ""
    echo "> :information_source: No findings reported. Consider a manual review for completeness."
  fi

  echo ""
  echo "---"
  echo ""
  echo "SUMMARY: $HIGH_COUNT HIGH, $LOW_COUNT LOW"
} > "$AGGREGATED_FILE"

[ -n "${GITHUB_OUTPUT:-}" ] && echo "high_count=$HIGH_COUNT" >> "$GITHUB_OUTPUT" && echo "low_count=$LOW_COUNT" >> "$GITHUB_OUTPUT"

echo "Aggregated: $HIGH_COUNT HIGH, $LOW_COUNT LOW"
cat "$AGGREGATED_FILE"
