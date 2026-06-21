#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/../.."

cat > /tmp/review/classifier-output.json << 'JSON'
{
  "files": [
    {"path": "src/controller/AccountController.kt", "bucket": "code"},
    {"path": "src/controller/AccountController.kt", "bucket": "api_spec"},
    {"path": "docs/api-endpoints.md", "bucket": "docs"},
    {"path": "bad_migration.sql", "bucket": "migration"},
    {"path": "backend/db/migration/V73__add_metrics.sql", "bucket": "migration"}
  ],
  "buckets": {"code": 1, "tests": 0, "docs": 1, "api_spec": 1, "migration": 2, "config": 0, "generated": 0, "binary": 0},
  "doc_manifest_hits": [],
  "has_code": true,
  "has_docs": true,
  "has_structural_targets": true,
  "skip_review": false,
  "truncated": false,
  "diff_size_bytes": 500
}
JSON

mkdir -p src/controller
cat > src/controller/AccountController.kt << 'KT'
class ExistingClass {
    fun existingMethod() = 42
}

fun newFunction() {
    println("no docstring here")
}
KT

OUTPUT=$(bash scripts/structural.sh 2>&1)
echo "$OUTPUT"

if ! echo "$OUTPUT" | grep -q "bad_migration.sql"; then
  echo "FAIL: expected migration naming check to flag bad_migration.sql"
  exit 1
fi

if [ ! -s /tmp/review/structural-findings.txt ]; then
  echo "FAIL: structural-findings.txt is empty or missing"
  exit 1
fi

echo ""
echo "PASS: structural checker produces findings"
