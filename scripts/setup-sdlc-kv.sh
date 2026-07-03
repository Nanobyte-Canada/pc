#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# setup-sdlc-kv.sh — Register pc repo's project ID in Worker KV
#
# Writes the project board ID for the pc repo to the DELIVERY_KV namespace,
# enabling the sdlc-webhook-receiver Worker to find it by repo name.
#
# Prerequisites:
#   - wrangler CLI authenticated (npx wrangler whoami)
#   - wrangler.toml with [[kv_namespaces]] binding DELIVERY_KV
#   - Run from the nanobyte-services/sdlc/worker directory
#
# Usage:
#   ./scripts/setup-sdlc-kv.sh
# =============================================================================

WORKER_DIR="/home/sbilakhia/Documents/dev/repos/nanobyte-services/sdlc/worker"
REPO="Nanobyte-Canada/pc"
PROJECT_ID="${1:-PVT_kwDOEbgU584Bbxz4}"

echo "=== SDLC KV Setup ==="
echo "Repo:       ${REPO}"
echo "Project ID: ${PROJECT_ID}"
echo ""

if [ ! -d "$WORKER_DIR" ]; then
  echo "::error::Worker directory not found: ${WORKER_DIR}"
  echo "Clone nanobyte-services first:"
  echo "  git clone git@github.com:Nanobyte-Canada/nanobyte-services.git"
  exit 1
fi

# Verify wrangler is authenticated
if ! npx wrangler whoami > /dev/null 2>&1; then
  echo "::error::wrangler not authenticated. Run: npx wrangler login"
  exit 1
fi

echo "Writing KV entry:"
echo "  Key:   repo:${REPO}"
echo "  Value: {\"project_id\":\"${PROJECT_ID}\"}"
echo ""

cd "$WORKER_DIR"
npx wrangler kv key put \
  "repo:${REPO}" \
  "$(printf '{"project_id":"%s"}' "$PROJECT_ID")" \
  --binding=DELIVERY_KV \
  --remote

echo ""
echo "=== KV write complete ==="
echo ""
echo "Verify with:"
echo "  npx wrangler kv key get \"repo:${REPO}\" --binding=DELIVERY_KV"
echo ""
echo "The Worker does NOT need redeploying — KV is live-read on every call."
echo ""
echo "Next: the three-tier Vault paths need to be seeded. Run:"
echo "  ./scripts/setup-sdlc-vault.sh"
