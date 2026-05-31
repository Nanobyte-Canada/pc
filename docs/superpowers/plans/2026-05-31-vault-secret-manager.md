# Vault Secret Manager — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add HashiCorp Vault to the monitoring stack and update the deploy workflow to fetch secrets from Vault instead of relying on pre-existing `.env` files on the server.

**Architecture:** Vault runs as a Docker container in the monitoring stack (port 18200), exposed via Cloudflare Tunnel at `vault.nanobyte.ca`. GitHub Actions authenticates with AppRole to fetch secrets at deploy time, generates `.env`, SCPs it to the server, then runs `docker compose up`. Admin access is through the Vault web UI.

**Tech Stack:** HashiCorp Vault 1.17, Docker Compose, GitHub Actions, Cloudflare Tunnel, bash

---

### Task 1: Vault Server Configuration File

**Files:**
- Create: `deploy/monitoring/vault/config.hcl`

- [ ] **Step 1: Create the Vault config directory**

```bash
mkdir -p deploy/monitoring/vault
```

- [ ] **Step 2: Create `deploy/monitoring/vault/config.hcl`**

```hcl
ui = true

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1
}

storage "file" {
  path = "/vault/data"
}

disable_mlock = true

api_addr = "http://127.0.0.1:8200"
```

- [ ] **Step 3: Commit**

```bash
git add deploy/monitoring/vault/config.hcl
git commit -m "feat(vault): add Vault server configuration"
```

---

### Task 2: Add Vault Container to Monitoring Stack

**Files:**
- Modify: `deploy/monitoring/docker-compose.yml`

- [ ] **Step 1: Add vault service and volume to `deploy/monitoring/docker-compose.yml`**

Add the vault service block after the `redis-exporter` service (before the `networks:` section). Add `vault_data` to the `volumes:` section at the bottom.

After the `redis-exporter` service block (line 192), before `networks:`, add:

```yaml
  vault:
    image: hashicorp/vault:1.17
    container_name: portfolio-vault
    cap_add:
      - IPC_LOCK
    environment:
      - VAULT_ADDR=http://127.0.0.1:8200
      - VAULT_API_ADDR=http://127.0.0.1:8200
    volumes:
      - vault_data:/vault/data
      - ./vault/config.hcl:/vault/config/config.hcl:ro
    ports:
      - "18200:8200"
    command: vault server -config=/vault/config/config.hcl
    restart: unless-stopped
    networks:
      - monitoring-network
    deploy:
      resources:
        limits:
          memory: 512M
    healthcheck:
      test: ["CMD", "sh", "-c", "vault status -format=json 2>/dev/null || exit 0"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 10s
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
```

Add `vault_data` to the volumes section at the bottom of the file:

```yaml
volumes:
  prometheus_data:
  grafana_data:
  loki_data:
  uptime_kuma_data:
  vault_data:
```

Note: The healthcheck uses `|| exit 0` because `vault status` returns exit code 2 when sealed (which is a valid state — Vault is running but sealed). We don't want Docker to restart a sealed Vault.

- [ ] **Step 2: Commit**

```bash
git add deploy/monitoring/docker-compose.yml
git commit -m "feat(vault): add Vault container to monitoring stack"
```

---

### Task 3: Vault Initialization Script

**Files:**
- Create: `deploy/scripts/vault-init.sh`

This script is run once on the server after Vault starts for the first time. It initializes Vault, enables the KV v2 engine, AppRole auth, userpass auth, and creates the portfolio deploy policy and role.

- [ ] **Step 1: Create `deploy/scripts/vault-init.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Vault Initialization Script (run once)
#
# Prerequisites:
#   - Vault container is running: docker compose up -d vault
#   - Vault CLI is available (runs inside the container)
#
# Usage:
#   bash deploy/scripts/vault-init.sh
#
# After running:
#   1. Save the unseal keys and root token to your password manager
#   2. Unseal Vault with 3 of 5 keys via the web UI or CLI
#   3. Use the root token to log into the web UI for initial setup
#   4. Create a userpass admin account for day-to-day access
# ============================================================

VAULT_CONTAINER="portfolio-vault"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Check Vault is running
if ! docker inspect "${VAULT_CONTAINER}" &>/dev/null; then
    echo "ERROR: Vault container '${VAULT_CONTAINER}' is not running."
    echo "Start it with: cd /opt/portfolio/monitoring && docker compose up -d vault"
    exit 1
fi

vault_exec() {
    docker exec "${VAULT_CONTAINER}" vault "$@"
}

# Step 1: Initialize Vault
log "Initializing Vault..."
INIT_OUTPUT=$(docker exec "${VAULT_CONTAINER}" vault operator init -format=json)

echo ""
echo "========================================"
echo "  SAVE THESE CREDENTIALS SECURELY"
echo "  (password manager, NOT on this server)"
echo "========================================"
echo ""
echo "${INIT_OUTPUT}" | jq -r '
  "Unseal Key 1: \(.unseal_keys_b64[0])",
  "Unseal Key 2: \(.unseal_keys_b64[1])",
  "Unseal Key 3: \(.unseal_keys_b64[2])",
  "Unseal Key 4: \(.unseal_keys_b64[3])",
  "Unseal Key 5: \(.unseal_keys_b64[4])",
  "",
  "Root Token:   \(.root_token)"
'
echo ""
echo "========================================"
echo ""

ROOT_TOKEN=$(echo "${INIT_OUTPUT}" | jq -r '.root_token')

# Step 2: Unseal (need 3 of 5 keys)
log "Unsealing Vault..."
for i in 0 1 2; do
    KEY=$(echo "${INIT_OUTPUT}" | jq -r ".unseal_keys_b64[${i}]")
    docker exec "${VAULT_CONTAINER}" vault operator unseal "${KEY}" > /dev/null
done

log "Vault unsealed."

# Step 3: Enable KV v2 secrets engine
log "Enabling KV v2 secrets engine at secret/..."
docker exec -e VAULT_TOKEN="${ROOT_TOKEN}" "${VAULT_CONTAINER}" \
    vault secrets enable -path=secret kv-v2

# Step 4: Enable AppRole auth
log "Enabling AppRole auth method..."
docker exec -e VAULT_TOKEN="${ROOT_TOKEN}" "${VAULT_CONTAINER}" \
    vault auth enable approle

# Step 5: Enable userpass auth for admin UI access
log "Enabling userpass auth method..."
docker exec -e VAULT_TOKEN="${ROOT_TOKEN}" "${VAULT_CONTAINER}" \
    vault auth enable userpass

# Step 6: Create admin policy
log "Creating admin policy..."
docker exec -e VAULT_TOKEN="${ROOT_TOKEN}" "${VAULT_CONTAINER}" \
    vault policy write admin - <<'POLICY'
path "secret/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "sys/policies/*" {
  capabilities = ["read", "list"]
}
path "auth/*" {
  capabilities = ["read", "list"]
}
POLICY

# Step 7: Create portfolio deploy policy (read-only to portfolio secrets)
log "Creating portfolio-deploy policy..."
docker exec -e VAULT_TOKEN="${ROOT_TOKEN}" "${VAULT_CONTAINER}" \
    vault policy write portfolio-deploy - <<'POLICY'
path "secret/data/portfolio/*" {
  capabilities = ["read"]
}
path "secret/metadata/portfolio/*" {
  capabilities = ["read", "list"]
}
POLICY

# Step 8: Create AppRole for portfolio deployments
log "Creating portfolio-deploy AppRole..."
docker exec -e VAULT_TOKEN="${ROOT_TOKEN}" "${VAULT_CONTAINER}" \
    vault write auth/approle/role/portfolio-deploy \
    token_policies="portfolio-deploy" \
    token_ttl=10m \
    token_max_ttl=30m \
    secret_id_ttl=0

# Step 9: Get role_id
log "Fetching role_id..."
ROLE_ID=$(docker exec -e VAULT_TOKEN="${ROOT_TOKEN}" "${VAULT_CONTAINER}" \
    vault read -field=role_id auth/approle/role/portfolio-deploy/role-id)

# Step 10: Generate secret_id
log "Generating secret_id..."
SECRET_ID=$(docker exec -e VAULT_TOKEN="${ROOT_TOKEN}" "${VAULT_CONTAINER}" \
    vault write -field=secret_id -f auth/approle/role/portfolio-deploy/secret-id)

echo ""
echo "========================================"
echo "  APPROLE CREDENTIALS"
echo "  Add these to GitHub Actions secrets:"
echo "========================================"
echo ""
echo "  VAULT_ROLE_ID:   ${ROLE_ID}"
echo "  VAULT_SECRET_ID: ${SECRET_ID}"
echo ""
echo "========================================"

# Step 11: Enable audit logging
log "Enabling file audit log..."
docker exec -e VAULT_TOKEN="${ROOT_TOKEN}" "${VAULT_CONTAINER}" \
    vault audit enable file file_path=/vault/data/audit.log

log ""
log "=== Vault initialization complete ==="
log ""
log "Next steps:"
log "  1. Save unseal keys + root token to your password manager"
log "  2. Create admin user: vault write auth/userpass/users/<username> password=<password> policies=admin"
log "  3. Import secrets: vault kv put secret/portfolio/prod KEY1=val1 KEY2=val2 ..."
log "  4. Add VAULT_ROLE_ID and VAULT_SECRET_ID to GitHub Actions secrets"
log "  5. Add vault.nanobyte.ca to Cloudflare Tunnel + Access"
```

- [ ] **Step 2: Make executable**

```bash
chmod +x deploy/scripts/vault-init.sh
```

- [ ] **Step 3: Commit**

```bash
git add deploy/scripts/vault-init.sh
git commit -m "feat(vault): add one-time initialization script"
```

---

### Task 4: Add Vault Ingress to Cloudflare Tunnel

**Files:**
- Modify: `deploy/cloudflared/config.yml`

- [ ] **Step 1: Add vault.nanobyte.ca ingress rule**

In `deploy/cloudflared/config.yml`, add the Vault ingress rule before the catch-all `http_status:404` rule (line 81). Insert it after the Grafana rule (line 78):

```yaml
  # Vault secrets manager (Cloudflare Access protected)
  - hostname: vault.nanobyte.ca
    service: http://localhost:18200

  # Catch-all (required by cloudflared)
  - service: http_status:404
```

- [ ] **Step 2: Commit**

```bash
git add deploy/cloudflared/config.yml
git commit -m "feat(vault): add vault.nanobyte.ca to Cloudflare Tunnel ingress"
```

---

### Task 5: Update Deploy Workflow to Fetch Secrets from Vault

**Files:**
- Modify: `.github/workflows/deploy.yml`

This is the key change. The workflow currently assumes `.env` exists on the server and just updates `IMAGE_TAG`. The new flow authenticates to Vault, fetches all secrets, generates a complete `.env`, SCPs it to the server, then deploys.

- [ ] **Step 1: Replace the deploy workflow**

Replace the entire contents of `.github/workflows/deploy.yml`:

```yaml
name: Deploy

on:
  workflow_dispatch:
    inputs:
      environment:
        description: "Target environment"
        required: true
        type: choice
        options:
          - uat
          - prod
      tag:
        description: "Image tag to deploy (e.g., main-a1b2c3d)"
        required: true
        type: string

env:
  DEPLOY_PATH: /opt/portfolio
  VAULT_ADDR: https://vault.nanobyte.ca

jobs:
  deploy:
    name: Deploy to ${{ github.event.inputs.environment }}
    runs-on: ubuntu-latest
    environment: ${{ github.event.inputs.environment }}
    steps:
      - name: Validate tag format
        run: |
          if [[ ! "${{ github.event.inputs.tag }}" =~ ^main-[a-f0-9]{7,}$ ]]; then
            echo "::error::Invalid tag format. Expected: main-<short-sha> (e.g., main-a1b2c3d)"
            exit 1
          fi

      - name: Fetch secrets from Vault
        run: |
          ENV="${{ github.event.inputs.environment }}"

          # Authenticate with AppRole
          LOGIN_RESPONSE=$(curl -sf --request POST \
            --data "{\"role_id\":\"${{ secrets.VAULT_ROLE_ID }}\",\"secret_id\":\"${{ secrets.VAULT_SECRET_ID }}\"}" \
            ${VAULT_ADDR}/v1/auth/approle/login)

          VAULT_TOKEN=$(echo "${LOGIN_RESPONSE}" | jq -r '.auth.client_token')
          if [ -z "${VAULT_TOKEN}" ] || [ "${VAULT_TOKEN}" = "null" ]; then
            echo "::error::Failed to authenticate with Vault"
            exit 1
          fi

          # Fetch secrets for the target environment
          SECRETS_RESPONSE=$(curl -sf --header "X-Vault-Token: ${VAULT_TOKEN}" \
            ${VAULT_ADDR}/v1/secret/data/portfolio/${ENV})

          SECRETS=$(echo "${SECRETS_RESPONSE}" | jq -r '.data.data')
          if [ -z "${SECRETS}" ] || [ "${SECRETS}" = "null" ]; then
            echo "::error::Failed to fetch secrets from Vault for portfolio/${ENV}"
            exit 1
          fi

          # Generate .env file
          echo "${SECRETS}" | jq -r 'to_entries[] | "\(.key)=\(.value)"' > /tmp/deploy.env

          # Add IMAGE_TAG (not a secret, comes from workflow input)
          echo "IMAGE_TAG=${{ github.event.inputs.tag }}" >> /tmp/deploy.env

          echo "Secrets fetched: $(echo "${SECRETS}" | jq -r 'keys | length') keys"

      - name: Install cloudflared
        run: |
          curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
          sudo dpkg -i cloudflared.deb

      - name: Configure SSH via Cloudflare Tunnel
        run: |
          mkdir -p ~/.ssh
          echo "${{ secrets.DEPLOY_SSH_KEY }}" > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key

          cat >> ~/.ssh/config << EOF
          Host portfolio-server
            HostName ${{ secrets.SERVER_HOSTNAME }}
            User deploy
            IdentityFile ~/.ssh/deploy_key
            StrictHostKeyChecking no
            ProxyCommand cloudflared access ssh --hostname %h
          EOF

      - name: Deploy to ${{ github.event.inputs.environment }}
        run: |
          ENV="${{ github.event.inputs.environment }}"
          TAG="${{ github.event.inputs.tag }}"

          # Upload .env to server
          scp /tmp/deploy.env portfolio-server:${DEPLOY_PATH}/${ENV}/.env

          ssh portfolio-server << REMOTE_SCRIPT
            set -euo pipefail
            cd ${DEPLOY_PATH}/${ENV}

            echo "=== Deploying ${TAG} to ${ENV} ==="

            # Pull new images
            echo "Pulling images..."
            docker compose pull

            # Restart services
            echo "Starting services..."
            docker compose up -d

            # Wait for health checks
            echo "Waiting for health checks..."
            sleep 10

            # Check container status
            echo "Container status:"
            docker compose ps

            # Verify all containers are healthy/running
            UNHEALTHY=\$(docker compose ps --format json | jq -r 'select(.Health != "healthy" and .Health != "" and .State != "running") | .Name' 2>/dev/null || true)
            if [ -n "\$UNHEALTHY" ]; then
              echo "WARNING: Unhealthy containers: \$UNHEALTHY"
              docker compose logs --tail=50 \$UNHEALTHY
              exit 1
            fi

            echo "=== Deploy complete ==="
          REMOTE_SCRIPT

      - name: Cleanup
        if: always()
        run: rm -f /tmp/deploy.env

      - name: Post deploy summary
        if: success()
        run: |
          echo "### Deploy Successful :rocket:" >> "$GITHUB_STEP_SUMMARY"
          echo "" >> "$GITHUB_STEP_SUMMARY"
          echo "- **Environment:** ${{ github.event.inputs.environment }}" >> "$GITHUB_STEP_SUMMARY"
          echo "- **Tag:** \`${{ github.event.inputs.tag }}\`" >> "$GITHUB_STEP_SUMMARY"
          echo "- **Triggered by:** ${{ github.actor }}" >> "$GITHUB_STEP_SUMMARY"
          echo "- **Secrets:** Fetched from Vault" >> "$GITHUB_STEP_SUMMARY"

      - name: Notify Slack
        if: always()
        uses: slackapi/slack-github-action@v2.0.0
        with:
          webhook: ${{ secrets.SLACK_WEBHOOK_URL }}
          webhook-type: incoming-webhook
          payload: |
            {
              "text": "${{ job.status == 'success' && ':rocket:' || ':x:' }} Deploy *${{ github.event.inputs.tag }}* to *${{ github.event.inputs.environment }}* ${{ job.status }}\nTriggered by: ${{ github.actor }}\n<${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}|View Run>"
            }
```

Key changes from the original:
- New "Fetch secrets from Vault" step before SSH setup — authenticates with AppRole, fetches secrets, writes `/tmp/deploy.env`
- "Deploy" step SCPs the `.env` file to the server instead of `sed`-ing the existing one
- Added "Cleanup" step to remove `/tmp/deploy.env` on success or failure
- Removed the `sed` command that updated `IMAGE_TAG` in-place — `IMAGE_TAG` is now appended to the generated `.env`
- "Fetch secrets from Vault" runs before cloudflared install (no SSH needed for Vault API)

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "feat(vault): update deploy workflow to fetch secrets from Vault"
```

---

### Task 6: Add Vault Backup to Backup Script

**Files:**
- Modify: `deploy/scripts/backup.sh`

- [ ] **Step 1: Add Vault backup section**

At the end of `deploy/scripts/backup.sh`, before the final `log "=== Backups complete ==="` line (line 82), add:

```bash
# Vault data backup
VAULT_BACKUP_DIR="${BACKUP_DIR}/vault"
mkdir -p "${VAULT_BACKUP_DIR}"
VAULT_CONTAINER="portfolio-vault"

if docker inspect "${VAULT_CONTAINER}" &>/dev/null; then
    log "Backing up Vault data..."
    VAULT_VOLUME=$(docker inspect "${VAULT_CONTAINER}" --format '{{ range .Mounts }}{{ if eq .Destination "/vault/data" }}{{ .Name }}{{ end }}{{ end }}')
    if [ -n "${VAULT_VOLUME}" ]; then
        docker run --rm -v "${VAULT_VOLUME}:/data:ro" -v "${VAULT_BACKUP_DIR}:/backup" \
            alpine tar czf "/backup/vault-${DATE}.tar.gz" -C /data .
        local vault_size
        vault_size=$(du -h "${VAULT_BACKUP_DIR}/vault-${DATE}.tar.gz" | cut -f1)
        log "  Created: ${VAULT_BACKUP_DIR}/vault-${DATE}.tar.gz (${vault_size})"

        # Retain 30 days
        find "${VAULT_BACKUP_DIR}" -name "vault-*.tar.gz" -mtime +30 -delete
        log "  Cleaned Vault backups older than 30 days"
    else
        log "WARNING: Could not find Vault data volume"
    fi
else
    log "WARNING: Vault container not running, skipping Vault backup"
fi
```

- [ ] **Step 2: Commit**

```bash
git add deploy/scripts/backup.sh
git commit -m "feat(vault): add Vault data backup to backup script"
```

---

### Task 7: Update Documentation

**Files:**
- Modify: `docs/reference/infrastructure.md`
- Modify: `docs/reference/configurations.md`

- [ ] **Step 1: Update `docs/reference/infrastructure.md`**

Read the file first. Add a new section for Vault under the monitoring section. Include:
- Vault container details (image, port 18200, ~512MB RAM)
- `vault.nanobyte.ca` routing via Cloudflare Tunnel (Cloudflare Access protected)
- Secret organization: `secret/portfolio/{prod,uat}`, `secret/shared/`, `secret/{app}/`
- Auth methods: root token (initial only), userpass (admin UI), AppRole (CI/CD)
- Initialization: one-time via `deploy/scripts/vault-init.sh`
- Unseal: manual via web UI after server restart (store keys in password manager)
- Backup: daily volume backup, 30-day retention

Also update the port allocation table to include Vault at 18200.

Also add `VAULT_ROLE_ID` and `VAULT_SECRET_ID` to the GitHub Actions secrets table.

- [ ] **Step 2: Update `docs/reference/configurations.md`**

Read the file first. Add a "Secret Management" section documenting:
- Secrets are stored in HashiCorp Vault, not in `.env` files on the server
- Deploy workflow fetches secrets from Vault at deploy time via AppRole
- Admin access via `vault.nanobyte.ca` web UI
- GitHub Actions secrets needed: `VAULT_ROLE_ID`, `VAULT_SECRET_ID` (per environment)
- List all secret keys stored in Vault for the portfolio app (from the spec's section 3)

- [ ] **Step 3: Commit**

```bash
git add docs/reference/infrastructure.md docs/reference/configurations.md
git commit -m "docs: add Vault secret management to infrastructure and config reference"
```

---

### Task 8: Server-Side Setup and Testing

This task is manual — run on the home server after deploying the monitoring stack changes.

**Files:** None (server-side operations only)

- [ ] **Step 1: Deploy updated monitoring stack**

SSH to the server and pull the latest monitoring compose file:

```bash
cd /opt/portfolio/monitoring
# Copy the updated docker-compose.yml and vault/ directory from the repo
docker compose up -d vault
docker compose ps vault
```

Wait for the Vault container to start (check `docker compose logs vault`).

- [ ] **Step 2: Run Vault initialization**

```bash
bash /opt/portfolio/scripts/vault-init.sh
```

Save the output (unseal keys, root token, AppRole credentials) to your password manager.

- [ ] **Step 3: Create admin userpass account**

```bash
docker exec -e VAULT_TOKEN=<root-token> portfolio-vault \
    vault write auth/userpass/users/<your-username> password=<your-password> policies=admin
```

- [ ] **Step 4: Import portfolio secrets**

Using the Vault web UI at `vault.nanobyte.ca` (after adding Cloudflare Tunnel DNS + Access), or via CLI:

```bash
# Import prod secrets (copy values from existing /opt/portfolio/prod/.env)
docker exec -e VAULT_TOKEN=<root-token> portfolio-vault \
    vault kv put secret/portfolio/prod \
    POSTGRES_DB=<value> \
    POSTGRES_USER=<value> \
    POSTGRES_PASSWORD=<value> \
    JWT_SIGNING_KEY=<value> \
    GOOGLE_CLIENT_ID=<value> \
    GOOGLE_CLIENT_SECRET=<value> \
    BROKER_ENCRYPTION_KEY=<value> \
    GATEWAY_API_KEY=<value> \
    EODHD_API_KEY=<value> \
    IBKR_USERNAME=<value> \
    IBKR_PASSWORD=<value> \
    IBKR_VNC_PASSWORD=<value> \
    IBKR_CLIENT_ID=1 \
    IBKR_GATEWAY_CLIENT_ID=2 \
    CORS_ALLOWED_ORIGINS=https://portfolio.nanobyte.ca \
    BROKER_SYNC_ENABLED=true \
    BROKER_SYNC_CRON="0 20 16 * * *" \
    BROKER_SYNC_CRON_MORNING="0 0 6 * * *" \
    QUESTRADE_ENABLED=false \
    WEALTHSIMPLE_ENABLED=false

# Import UAT secrets (from /opt/portfolio/uat/.env)
docker exec -e VAULT_TOKEN=<root-token> portfolio-vault \
    vault kv put secret/portfolio/uat \
    POSTGRES_DB=<value> \
    POSTGRES_USER=<value> \
    POSTGRES_PASSWORD=<value> \
    ...
```

- [ ] **Step 5: Add GitHub Actions secrets**

Go to GitHub repo → Settings → Environments → `prod`:
- Add `VAULT_ROLE_ID` (from init script output)
- Add `VAULT_SECRET_ID` (from init script output)

Repeat for `uat` environment (same role_id, generate a separate secret_id if desired, or reuse).

- [ ] **Step 6: Add Cloudflare Tunnel DNS route**

```bash
cloudflared tunnel route dns portfolio-tunnel vault.nanobyte.ca
```

Set up Cloudflare Access policy for `vault.nanobyte.ca` (same as Grafana — email OTP or SSO).

- [ ] **Step 7: Test deploy to UAT**

Trigger the deploy workflow from GitHub Actions:
- Environment: `uat`
- Tag: (use the latest tag from `build.yml`)

Verify:
- Workflow fetches secrets from Vault successfully
- `.env` is written to the server
- Services start and pass health checks
- Application works at `uatportfolio.nanobyte.ca`

- [ ] **Step 8: Test deploy to prod**

Same as UAT but with `prod` environment. Verify at `portfolio.nanobyte.ca`.

- [ ] **Step 9: Remove plaintext `.env` files from server (optional)**

After both environments are verified working with Vault:

```bash
# Keep backups first
cp /opt/portfolio/prod/.env /opt/portfolio/backups/prod-env-backup.env
cp /opt/portfolio/uat/.env /opt/portfolio/backups/uat-env-backup.env

# The .env files will now be regenerated on every deploy from Vault
# They can be deleted after confirming deploys work without them pre-existing
```
