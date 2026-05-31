#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# HashiCorp Vault One-Time Initialization Script
# Initializes Vault, unseals, configures auth methods, policies,
# AppRole, and audit logging inside the portfolio-vault container.
# Usage: bash deploy/scripts/vault-init.sh
# ============================================================

CONTAINER="portfolio-vault"
VAULT_CMD="docker exec"

echo "=== Vault One-Time Initialization ==="
echo ""

# --- 1. Verify container is running ---
echo "[1/11] Checking that '${CONTAINER}' container is running..."
if ! docker inspect --format='{{.State.Running}}' "${CONTAINER}" 2>/dev/null | grep -q "true"; then
    echo "ERROR: Container '${CONTAINER}' is not running."
    echo "Start it first: docker compose up -d portfolio-vault"
    exit 1
fi
echo "  Container is running."

# --- 2. Initialize Vault ---
echo ""
echo "[2/11] Initializing Vault operator..."
INIT_OUTPUT=$(${VAULT_CMD} "${CONTAINER}" vault operator init -format=json)

echo ""
echo "=========================================="
echo "  VAULT INITIALIZATION OUTPUT"
echo "  SAVE THESE CREDENTIALS SECURELY!"
echo "=========================================="
echo "${INIT_OUTPUT}"
echo "=========================================="
echo ""

# Extract unseal keys and root token
UNSEAL_KEY_0=$(echo "${INIT_OUTPUT}" | grep -o '"unseal_keys_b64":\s*\[[^]]*\]' | grep -o '"[^"]*"' | sed -n '2p' | tr -d '"')
UNSEAL_KEY_1=$(echo "${INIT_OUTPUT}" | grep -o '"unseal_keys_b64":\s*\[[^]]*\]' | grep -o '"[^"]*"' | sed -n '3p' | tr -d '"')
UNSEAL_KEY_2=$(echo "${INIT_OUTPUT}" | grep -o '"unseal_keys_b64":\s*\[[^]]*\]' | grep -o '"[^"]*"' | sed -n '4p' | tr -d '"')
ROOT_TOKEN=$(echo "${INIT_OUTPUT}" | grep -o '"root_token":\s*"[^"]*"' | grep -o '"[^"]*"$' | tr -d '"')

if [ -z "${ROOT_TOKEN}" ]; then
    echo "ERROR: Failed to parse root token from init output."
    exit 1
fi

echo "  Root Token: ${ROOT_TOKEN}"
echo "  Unseal Key 1: ${UNSEAL_KEY_0}"
echo "  Unseal Key 2: ${UNSEAL_KEY_1}"
echo "  Unseal Key 3: ${UNSEAL_KEY_2}"
echo ""

# --- 3. Auto-unseal with first 3 keys ---
echo "[3/11] Unsealing Vault with 3 keys..."
${VAULT_CMD} "${CONTAINER}" vault operator unseal "${UNSEAL_KEY_0}" > /dev/null
echo "  Key 1/3 applied."
${VAULT_CMD} "${CONTAINER}" vault operator unseal "${UNSEAL_KEY_1}" > /dev/null
echo "  Key 2/3 applied."
${VAULT_CMD} "${CONTAINER}" vault operator unseal "${UNSEAL_KEY_2}" > /dev/null
echo "  Key 3/3 applied. Vault is unsealed."

# --- 4. Enable KV v2 secrets engine at secret/ ---
echo ""
echo "[4/11] Enabling KV v2 secrets engine at secret/..."
${VAULT_CMD} -e VAULT_TOKEN="${ROOT_TOKEN}" "${CONTAINER}" \
    vault secrets enable -path=secret -version=2 kv
echo "  KV v2 enabled at secret/."

# --- 5. Enable AppRole auth method ---
echo ""
echo "[5/11] Enabling AppRole auth method..."
${VAULT_CMD} -e VAULT_TOKEN="${ROOT_TOKEN}" "${CONTAINER}" \
    vault auth enable approle
echo "  AppRole auth enabled."

# --- 6. Enable userpass auth method ---
echo ""
echo "[6/11] Enabling userpass auth method..."
${VAULT_CMD} -e VAULT_TOKEN="${ROOT_TOKEN}" "${CONTAINER}" \
    vault auth enable userpass
echo "  Userpass auth enabled."

# --- 7. Create admin policy ---
echo ""
echo "[7/11] Creating 'admin' policy..."
${VAULT_CMD} -e VAULT_TOKEN="${ROOT_TOKEN}" "${CONTAINER}" \
    sh -c 'cat <<POLICY | vault policy write admin -
path "secret/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

path "sys/policies/*" {
  capabilities = ["read", "list"]
}

path "auth/*" {
  capabilities = ["read", "list"]
}
POLICY'
echo "  Policy 'admin' created."

# --- 8. Create portfolio-deploy policy ---
echo ""
echo "[8/11] Creating 'portfolio-deploy' policy..."
${VAULT_CMD} -e VAULT_TOKEN="${ROOT_TOKEN}" "${CONTAINER}" \
    sh -c 'cat <<POLICY | vault policy write portfolio-deploy -
path "secret/data/portfolio/*" {
  capabilities = ["read"]
}

path "secret/metadata/portfolio/*" {
  capabilities = ["read", "list"]
}
POLICY'
echo "  Policy 'portfolio-deploy' created."

# --- 9. Create AppRole role 'portfolio-deploy' ---
echo ""
echo "[9/11] Creating AppRole role 'portfolio-deploy'..."
${VAULT_CMD} -e VAULT_TOKEN="${ROOT_TOKEN}" "${CONTAINER}" \
    vault write auth/approle/role/portfolio-deploy \
        token_policies="portfolio-deploy" \
        token_ttl=10m \
        token_max_ttl=30m \
        secret_id_ttl=0
echo "  AppRole role 'portfolio-deploy' created (TTL=10m, Max TTL=30m, no secret_id expiry)."

# --- 10. Fetch role_id and generate secret_id ---
echo ""
echo "[10/11] Fetching role_id and generating secret_id..."

ROLE_ID=$(${VAULT_CMD} -e VAULT_TOKEN="${ROOT_TOKEN}" "${CONTAINER}" \
    vault read -field=role_id auth/approle/role/portfolio-deploy/role-id)

SECRET_ID=$(${VAULT_CMD} -e VAULT_TOKEN="${ROOT_TOKEN}" "${CONTAINER}" \
    vault write -field=secret_id -f auth/approle/role/portfolio-deploy/secret-id)

echo ""
echo "=========================================="
echo "  APPROLE CREDENTIALS"
echo "  SAVE THESE SECURELY!"
echo "=========================================="
echo "  Role ID:   ${ROLE_ID}"
echo "  Secret ID: ${SECRET_ID}"
echo "=========================================="
echo ""

# --- 11. Enable file audit logging ---
echo "[11/11] Enabling file audit logging..."
${VAULT_CMD} -e VAULT_TOKEN="${ROOT_TOKEN}" "${CONTAINER}" \
    vault audit enable file file_path=/vault/data/audit.log
echo "  Audit log enabled at /vault/data/audit.log."

# --- Done ---
echo ""
echo "=== Vault Initialization Complete ==="
echo ""
echo "Next steps:"
echo "  1. Store the unseal keys and root token in a secure location (password manager, HSM)"
echo "  2. Store the AppRole role_id and secret_id in your CI/CD secrets (e.g., GitHub Secrets)"
echo "  3. Create an admin user:  docker exec -e VAULT_TOKEN=<root_token> ${CONTAINER} vault write auth/userpass/users/<username> password=<password> policies=admin"
echo "  4. Populate secrets:      docker exec -e VAULT_TOKEN=<root_token> ${CONTAINER} vault kv put secret/portfolio/prod POSTGRES_PASSWORD=... JWT_SIGNING_KEY=..."
echo "  5. Revoke the root token once admin users are created:  docker exec -e VAULT_TOKEN=<root_token> ${CONTAINER} vault token revoke <root_token>"
echo "  6. Test AppRole login:    docker exec ${CONTAINER} vault write auth/approle/login role_id=<role_id> secret_id=<secret_id>"
echo ""
