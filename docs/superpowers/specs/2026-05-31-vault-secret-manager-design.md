# HashiCorp Vault Secret Manager — Design Spec

**Date:** 2026-05-31
**Status:** Draft

## Summary

Replace manual `.env` files on the home server with a self-hosted HashiCorp Vault instance. Vault provides a centralized web UI and API for managing secrets across all applications (4-8 apps) deployed on the home server. Deploy workflows fetch secrets from Vault at deploy time, eliminating the need to SSH into the server to edit environment files.

## Goals

- No SSH required for secret management — use Vault web UI at `vault.nanobyte.ca`
- Centralized secret store for all apps across all environments (prod/uat)
- Audit trail of secret access and modifications
- Fine-grained access control — each app's CI/CD can only read its own secrets
- Encrypted at rest — no plaintext `.env` files sitting on disk permanently
- Minimal infrastructure — single container, file storage backend, ~256MB RAM

## Architecture

```
Developer / Admin
    ↓ browser
vault.nanobyte.ca (Cloudflare Access protected)
    ↓
Vault Container (monitoring stack, port 18200)
    ↓ file storage backend
Docker volume (vault-data)

GitHub Actions (deploy.yml)
    ↓ AppRole auth (role_id + secret_id)
Vault API → fetch secrets for portfolio/{env}
    ↓
SSH to server → write .env → docker compose pull + up
    ↓
.env exists only during deployment (can be made ephemeral)
```

## Components

### 1. Vault Server Container

Added to `deploy/monitoring/docker-compose.yml`:

```yaml
vault:
  image: hashicorp/vault:1.17
  container_name: portfolio-vault
  cap_add:
    - IPC_LOCK
  environment:
    VAULT_ADDR: "http://127.0.0.1:8200"
    VAULT_API_ADDR: "http://127.0.0.1:8200"
  volumes:
    - vault-data:/vault/data
    - ./vault/config.hcl:/vault/config/config.hcl:ro
  ports:
    - "18200:8200"
  command: vault server -config=/vault/config/config.hcl
  restart: unless-stopped
  mem_limit: 512m
  healthcheck:
    test: ["CMD", "vault", "status", "-format=json"]
    interval: 30s
    timeout: 5s
    retries: 3
    start_period: 10s
```

### 2. Vault Configuration

File: `deploy/monitoring/vault/config.hcl`

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

TLS is disabled because Cloudflare Tunnel handles HTTPS termination. `disable_mlock = true` is required for Docker containers without `--privileged`.

### 3. Secret Organization

KV v2 secrets engine at `secret/`:

```
secret/
├── portfolio/
│   ├── prod
│   │   ├── POSTGRES_DB
│   │   ├── POSTGRES_USER
│   │   ├── POSTGRES_PASSWORD
│   │   ├── JWT_SIGNING_KEY
│   │   ├── GOOGLE_CLIENT_ID
│   │   ├── GOOGLE_CLIENT_SECRET
│   │   ├── BROKER_ENCRYPTION_KEY
│   │   ├── GATEWAY_API_KEY
│   │   ├── EODHD_API_KEY
│   │   ├── IBKR_USERNAME
│   │   ├── IBKR_PASSWORD
│   │   ├── IBKR_VNC_PASSWORD
│   │   ├── CORS_ALLOWED_ORIGINS
│   │   ├── BROKER_SYNC_CRON
│   │   └── BROKER_SYNC_CRON_MORNING
│   └── uat
│       ├── (same keys, UAT-specific values)
│       └── ...
├── shared/
│   └── (secrets used by multiple apps)
└── {future-app}/
    ├── prod
    └── uat
```

Each path stores all key-value pairs as a single Vault secret (one API call to fetch all).

### 4. Authentication

**Admin access (web UI):**
- Root token for initial setup only
- Create a named admin policy + userpass auth method for day-to-day use
- Access via `vault.nanobyte.ca` protected by Cloudflare Access (email/OTP gate)

**CI/CD access (GitHub Actions):**
- AppRole auth method
- Each app gets its own role with a scoped policy:

```hcl
# policy: portfolio-deploy
path "secret/data/portfolio/*" {
  capabilities = ["read"]
}
```

- `role_id` is not secret (identifies the app)
- `secret_id` is secret (rotatable, stored as GitHub Actions secret)
- GitHub secrets per app: `VAULT_ROLE_ID`, `VAULT_SECRET_ID`

### 5. Deploy Workflow Changes

Current `deploy.yml` assumes `.env` already exists on the server. New flow:

```yaml
- name: Fetch secrets from Vault
  env:
    VAULT_ADDR: https://vault.nanobyte.ca
    VAULT_ROLE_ID: ${{ secrets.VAULT_ROLE_ID }}
    VAULT_SECRET_ID: ${{ secrets.VAULT_SECRET_ID }}
  run: |
    # Authenticate with AppRole
    VAULT_TOKEN=$(curl -s --request POST \
      --data "{\"role_id\":\"$VAULT_ROLE_ID\",\"secret_id\":\"$VAULT_SECRET_ID\"}" \
      $VAULT_ADDR/v1/auth/approle/login | jq -r '.auth.client_token')

    # Fetch secrets for the target environment
    SECRETS=$(curl -s --header "X-Vault-Token: $VAULT_TOKEN" \
      $VAULT_ADDR/v1/secret/data/portfolio/${{ inputs.environment }} | jq -r '.data.data')

    # Generate .env content
    echo "$SECRETS" | jq -r 'to_entries[] | "\(.key)=\(.value)"' > /tmp/deploy.env

    # Add IMAGE_TAG (not a secret, comes from workflow input)
    echo "IMAGE_TAG=${{ inputs.tag }}" >> /tmp/deploy.env

- name: Deploy to server
  run: |
    # SCP the .env file to server
    scp /tmp/deploy.env portfolio-server:/opt/portfolio/${{ inputs.environment }}/.env

    # SSH and deploy
    ssh portfolio-server << 'EOF'
      cd /opt/portfolio/${{ inputs.environment }}
      docker compose pull
      docker compose up -d
      # ... health checks ...
    EOF

    # Clean up local copy
    rm -f /tmp/deploy.env
```

### 6. Cloudflare Tunnel

Add to `deploy/cloudflared/config.yml`:

```yaml
- hostname: vault.nanobyte.ca
  service: http://localhost:18200
```

Protect with Cloudflare Access policy (same as Grafana — email OTP or SSO).

### 7. Auto-Unseal on Restart

Vault seals itself when restarted. Options:

**Option A: Manual unseal (simplest, most secure)**
- Store unseal keys in a password manager (1Password, Bitwarden)
- After server restart, open `vault.nanobyte.ca` and enter unseal keys
- Server restarts are rare (planned maintenance only)

**Option B: Auto-unseal script (convenient)**
- Store unseal keys in a file on the server (`/root/.vault-unseal-keys`, mode 0600, owned by root)
- Systemd service or Docker healthcheck script that detects sealed state and unseals
- Less secure (keys on disk) but fully automated

**Recommendation:** Option A. Server restarts are infrequent, and the 30-second unseal via web UI is worth the security benefit.

### 8. Initialization Procedure

One-time setup (done once when Vault is first started):

1. `vault operator init` — generates 5 unseal keys + root token
2. Store unseal keys securely (password manager, NOT on server)
3. Unseal with 3 of 5 keys
4. Enable KV v2: `vault secrets enable -path=secret kv-v2`
5. Enable AppRole: `vault auth enable approle`
6. Create admin userpass: `vault auth enable userpass`
7. Write secrets from current `.env` files
8. Create app policies and AppRole roles
9. Store `role_id` + `secret_id` in GitHub Actions secrets

### 9. Port Allocation

Following the existing convention:

| Service | Port | Stack |
|---|---|---|
| Vault | 18200 | Monitoring (existing range 13000-19xxx) |

### 10. Backup

Vault data is stored in a Docker volume (`vault-data`). Add to the existing backup script:

```bash
# Backup Vault file storage
docker run --rm -v portfolio-vault-data:/data -v /opt/portfolio/backups/vault:/backup \
  alpine tar czf /backup/vault-$(date +%Y%m%d).tar.gz -C /data .
# Retain 30 days
find /opt/portfolio/backups/vault -name "vault-*.tar.gz" -mtime +30 -delete
```

## Migration Plan

1. Add Vault container to monitoring stack
2. Initialize Vault, store unseal keys securely
3. Import all current `.env` values into Vault
4. Create AppRole for portfolio app
5. Update `deploy.yml` to fetch from Vault instead of relying on pre-existing `.env`
6. Add `vault.nanobyte.ca` to Cloudflare Tunnel + Access
7. Test: deploy to UAT via GitHub Actions → verify secrets are fetched and services start
8. Test: deploy to prod
9. Remove plaintext `.env` files from server (keep `.env.example` templates in repo)
10. Document Vault setup for future apps

## Security Considerations

- Vault web UI behind Cloudflare Access — no direct internet exposure
- AppRole secret_ids are rotatable — can be cycled without changing Vault config
- Audit logging enabled — every secret read/write is logged
- `.env` files on server become ephemeral (written at deploy time, could be deleted after `docker compose up`)
- Unseal keys stored in password manager, not on server
- Each app's CI/CD can only read its own secret paths

## File Changes

### New files
| File | Purpose |
|---|---|
| `deploy/monitoring/vault/config.hcl` | Vault server configuration |
| `deploy/scripts/vault-init.sh` | One-time Vault initialization script |

### Modified files
| File | Change |
|---|---|
| `deploy/monitoring/docker-compose.yml` | Add Vault service |
| `deploy/cloudflared/config.yml` | Add `vault.nanobyte.ca` ingress |
| `.github/workflows/deploy.yml` | Add Vault secret fetching step |
| `deploy/scripts/backup.sh` | Add Vault backup |
| `docs/reference/infrastructure.md` | Document Vault setup |
| `docs/reference/configurations.md` | Update secret management docs |

### Removed (after migration verified)
| File | Reason |
|---|---|
| `deploy/prod/.env` (on server) | Replaced by Vault — generated at deploy time |
| `deploy/uat/.env` (on server) | Replaced by Vault — generated at deploy time |
