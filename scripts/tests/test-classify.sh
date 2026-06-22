#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/../.."

cat > /tmp/review/test-changed-files.txt << 'EOF'
src/controller/AccountController.kt
docs/api-endpoints.md
frontend/src/hooks/useAccounts.ts
backend/portfolio/src/main/resources/db/migration/V73__add_portfolio_metrics.sql
README.md
EOF

OUTPUT=$(bash scripts/classify.sh --changed-files /tmp/review/test-changed-files.txt 2>&1)

echo "$OUTPUT"

if ! echo "$OUTPUT" | jq -e '.has_code == true' > /dev/null 2>&1; then
  echo "FAIL: expected has_code=true"
  exit 1
fi
if ! echo "$OUTPUT" | jq -e '.has_docs == true' > /dev/null 2>&1; then
  echo "FAIL: expected has_docs=true"
  exit 1
fi
if ! echo "$OUTPUT" | jq -e '.skip_review == false' > /dev/null 2>&1; then
  echo "FAIL: expected skip_review=false"
  exit 1
fi
if ! echo "$OUTPUT" | jq -e '.files | length >= 2' > /dev/null 2>&1; then
  echo "FAIL: expected at least 2 files"
  exit 1
fi

echo ""
echo "PASS: classifier produces valid output"
