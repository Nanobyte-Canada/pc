#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/../.."

mkdir -p /tmp/review

cat > /tmp/review/structural-findings.txt << 'EOF'
[HIGH] Migration Naming: Migration file 'bad.sql' fails naming convention
Location: bad.sql
Suggestion: Rename to V##__description.sql
---
SUMMARY: 1 HIGH, 0 LOW
EOF

cat > /tmp/review/code-findings.txt << 'EOF'
[HIGH] Breaking API: AccountController changed response format
Location: AccountController.kt:42
Suggestion: Update docs to match actual behavior
---
[LOW] Missing Docstring: new public function lacks KDoc
Location: AccountService.kt:88
Suggestion: Add KDoc
---
SUMMARY: 1 HIGH, 1 LOW
EOF

cat > /tmp/review/doc-findings.txt << 'EOF'
[LOW] Missing Docstring: new public function lacks KDoc
Location: AccountService.kt:88
Suggestion: Add KDoc
---
SUMMARY: 0 HIGH, 1 LOW
EOF

GITHUB_OUTPUT=/tmp/review/gh_output bash scripts/aggregate.sh

OUTPUT=$(cat /tmp/review/aggregated-output.txt)

echo "$OUTPUT" | grep -q "SUMMARY: 2 HIGH, 1 LOW" && echo "PASS: dedup works" || { echo "FAIL: unexpected count"; exit 1; }
echo "$OUTPUT" | grep -q "HIGH Findings" && echo "PASS: has HIGH section" || { echo "FAIL: missing HIGH section"; exit 1; }
echo "$OUTPUT" | grep -q "LOW Findings" && echo "PASS: has LOW section" || { echo "FAIL: missing LOW section"; exit 1; }

grep -q "high_count=2" /tmp/review/gh_output && echo "PASS: GITHUB_OUTPUT has high_count" || { echo "FAIL: missing high_count"; exit 1; }
grep -q "low_count=1" /tmp/review/gh_output && echo "PASS: GITHUB_OUTPUT has low_count" || { echo "FAIL: missing low_count"; exit 1; }

echo ""
echo "PASS: all aggregate tests pass"

# Test zero findings edge case
rm -f /tmp/review/*-findings.txt
GITHUB_OUTPUT=/tmp/review/gh_output_zero bash scripts/aggregate.sh
OUTPUT=$(cat /tmp/review/aggregated-output.txt)
echo "$OUTPUT" | grep -q "No findings reported" && echo "PASS: zero findings handled" || { echo "FAIL: zero findings not handled"; exit 1; }
grep -q "high_count=0" /tmp/review/gh_output_zero && echo "PASS: zero high_count" || { echo "FAIL: missing zero high_count"; exit 1; }

echo ""
echo "PASS: all aggregate tests pass (including zero findings)"
