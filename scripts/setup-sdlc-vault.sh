#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# setup-sdlc-vault.sh — Migrate pc repo secrets to three-tier Vault structure
#
# Reads the old sdlc/pc path, splits secrets into the correct tiers, and
# writes them to the new three-tier layout. Keeps existing deploy secrets
# in portfolio/uat and portfolio/prod intact (merges, doesn't overwrite).
#
# Prerequisites:
#   - VAULT_ADDR environment variable (or sourced from .env)
#   - VAULT_ROLE_ID + VAULT_SECRET_ID environment variables (AppRole creds)
#   - curl and jq installed
#
# Usage:
#   export VAULT_ROLE_ID="..." VAULT_SECRET_ID="..."
#   ./scripts/setup-sdlc-vault.sh
#
# Or with a VAULT_TOKEN directly:
#   VAULT_TOKEN="hvs..." ./scripts/setup-sdlc-vault.sh
# =============================================================================

VAULT_ADDR="${VAULT_ADDR:-https://vault.nanobyte.ca}"
APP_NAME="${APP_NAME:-portfolio}"

echo "=== SDLC Vault Migration ==="
echo "Vault:    ${VAULT_ADDR}"
echo "App name: ${APP_NAME}"
echo ""

# --- Step 1: Authenticate ---

if [ -n "${VAULT_TOKEN:-}" ]; then
  echo "Using provided VAULT_TOKEN"
else
  echo "Authenticating via AppRole..."
  VAULT_TOKEN=$(curl -sf \
    --request POST \
    --data "{\"role_id\": \"${VAULT_ROLE_ID}\", \"secret_id\": \"${VAULT_SECRET_ID}\"}" \
    "${VAULT_ADDR}/v1/auth/approle/login" | jq -r '.auth.client_token')

  if [ -z "$VAULT_TOKEN" ] || [ "$VAULT_TOKEN" = "null" ]; then
    echo "::error::Vault authentication failed"
    exit 1
  fi
  echo "Vault authentication successful"
fi

# Shared curl helper
vault_read() {
  curl -sf --header "X-Vault-Token: ${VAULT_TOKEN}" "${VAULT_ADDR}/v1/$1"
}
vault_write() {
  curl -sf --header "X-Vault-Token: ${VAULT_TOKEN}" \
    --header "Content-Type: application/json" \
    --request POST \
    --data "$2" \
    "${VAULT_ADDR}/v1/$1"
}

# --- Step 2: Read current sdlc/pc secrets (if they exist) ---

OLD_PATH="secret/data/sdlc/pc"
OLD_DATA="{}"
if vault_read "${OLD_PATH}" > /dev/null 2>&1; then
  OLD_DATA=$(vault_read "${OLD_PATH}" | jq -r '.data.data // "{}"')
  echo ""
  echo "Found existing sdlc/pc with keys: $(echo "$OLD_DATA" | jq -r 'keys | join(", ")')"
else
  echo "No existing sdlc/pc path found — starting fresh"
fi

# --- Step 3: Write Tier 1 — Org-level shared secrets ---

TIER1_PATH="secret/data/sdlc/common"
TIER1_KEYS='{"NANOBYTE_SERVICES_TOKEN":"","OPENCODE_AUTH_JSON":""}'

# Get existing Tier 1 secrets (merge with old data)
CURRENT_TIER1="{}"
if vault_read "${TIER1_PATH}" > /dev/null 2>&1; then
  CURRENT_TIER1=$(vault_read "${TIER1_PATH}" | jq -r '.data.data // "{}"')
fi

# Merge: start with CURRENT_TIER1, then overlay TIER1 keys from OLD_DATA
MERGED_TIER1=$(echo "$CURRENT_TIER1" | jq -c \
  --argjson old "$OLD_DATA" \
  --argjson keys "$TIER1_KEYS" '
  . as $current
  # For each expected key, extract its value from old data
  | reduce ($keys | to_entries[]) as {$key} ({};
      if $key | in($old) then .[$key] = $old[$key] else . end
    )
  # Overlay current values (they take precedence)
  | . + $current
')

if [ "$MERGED_TIER1" != "{}" ] && [ "$MERGED_TIER1" != "$CURRENT_TIER1" ]; then
  echo ""
  echo "--- Tier 1: ${TIER1_PATH} ---"
  echo "Writing keys: $(echo "$MERGED_TIER1" | jq -r 'keys | join(", ")')"
  vault_write "${TIER1_PATH}" "$(jq -n --argjson data "$MERGED_TIER1" '{data: $data}')" > /dev/null
  echo "Done ✓"
elif [ "$MERGED_TIER1" != "{}" ]; then
  echo ""
  echo "--- Tier 1: ${TIER1_PATH} --- already up to date"
else
  echo ""
  echo "--- Tier 1: ${TIER1_PATH} ---"
  echo "No org-level secrets found in sdlc/pc — skipping"
  echo "You need to add NANOBYTE_SERVICES_TOKEN and OPENCODE_AUTH_JSON manually:"
  echo "  vault kv put ${TIER1_PATH} NANOBYTE_SERVICES_TOKEN=\"...\" OPENCODE_AUTH_JSON='...'"
fi

# --- Step 4: Write Tier 2 — App-level shared secrets ---

TIER2_PATH="secret/data/${APP_NAME}/common"
TIER2_KEYS='{"GH_PROJECT_TOKEN":"","SDLC_PROJECT_ID":""}'

CURRENT_TIER2="{}"
if vault_read "${TIER2_PATH}" > /dev/null 2>&1; then
  CURRENT_TIER2=$(vault_read "${TIER2_PATH}" | jq -r '.data.data // "{}"')
fi

MERGED_TIER2=$(echo "$CURRENT_TIER2" | jq -c \
  --argjson old "$OLD_DATA" \
  --argjson keys "$TIER2_KEYS" '
  . as $current
  | reduce ($keys | to_entries[]) as {$key} ({};
      if $key | in($old) then .[$key] = $old[$key] else . end
    )
  | . + $current
')

echo ""
echo "--- Tier 2: ${TIER2_PATH} ---"
if [ "$MERGED_TIER2" != "{}" ]; then
  echo "Writing keys: $(echo "$MERGED_TIER2" | jq -r 'keys | join(", ")')"
  vault_write "${TIER2_PATH}" "$(jq -n --argjson data "$MERGED_TIER2" '{data: $data}')" > /dev/null
  echo "Done ✓"
else
  echo "No repo-level secrets found in sdlc/pc — you'll need to add them:"
  echo "  vault kv put ${TIER2_PATH} GH_PROJECT_TOKEN=\"...\" SDLC_PROJECT_ID=\"...\""
fi

# --- Step 5: Write Tier 3 — Env-specific secrets ---

# Map old key names to env-specific paths
# Old sdlc/pc may have UAT_API_URL and PROD_API_URL
# We merge these into the corresponding env paths

declare -A ENV_MAP
ENV_MAP=( ["uat"]="UAT_API_URL" ["prod"]="PROD_API_URL" )

for ENV in "${!ENV_MAP[@]}"; do
  TIER3_PATH="secret/data/${APP_NAME}/${ENV}"
  OLD_KEY="${ENV_MAP[$ENV]}"

  echo ""
  echo "--- Tier 3: ${TIER3_PATH} ---"

  # Get current secrets (deploy credentials etc.)
  CURRENT_TIER3="{}"
  if vault_read "${TIER3_PATH}" > /dev/null 2>&1; then
    CURRENT_TIER3=$(vault_read "${TIER3_PATH}" | jq -r '.data.data // "{}"')
    echo "Current keys: $(echo "$CURRENT_TIER3" | jq -r 'keys | join(", ")')"
  else
    echo "Path does not exist yet — will create"
  fi

  # Extract the old key if it exists
  NEW_VAL=$(echo "$OLD_DATA" | jq -r --arg key "$OLD_KEY" '.[$key] // empty')

  if [ -n "$NEW_VAL" ]; then
    # Merge: keep existing deploy secrets, add/overwrite the SDLC key
    MERGED=$(echo "$CURRENT_TIER3" | jq -c --arg key "$OLD_KEY" --arg val "$NEW_VAL" '. + {($key): $val}')
    vault_write "${TIER3_PATH}" "$(jq -n --argjson data "$MERGED" '{data: $data}')" > /dev/null
    echo "Merged ${OLD_KEY} into existing secrets ✓"
  else
    echo "No ${OLD_KEY} found in old sdlc/pc — skipping"
    echo "Add manually: vault kv patch ${TIER3_PATH} ${OLD_KEY}=\"https://...\""
  fi
done

echo ""
echo "=== Migration complete ==="
echo ""
echo "New structure:"
echo "  ${VAULT_ADDR}/v1/secret/data/sdlc/common        (Tier 1 — org-level)"
echo "  ${VAULT_ADDR}/v1/secret/data/${APP_NAME}/common  (Tier 2 — repo-level)"
echo "  ${VAULT_ADDR}/v1/secret/data/${APP_NAME}/uat     (Tier 3 — UAT)"
echo "  ${VAULT_ADDR}/v1/secret/data/${APP_NAME}/prod    (Tier 3 — PROD)"
echo ""
echo "Next: run setup-sdlc-kv.sh to register the project ID in Worker KV"
