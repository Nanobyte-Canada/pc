# CI/CD Cleanup and SSH Hardening

**Date:** 2026-06-05
**Status:** Draft
**Branch:** `feature/multi-leg-options-strategy`

## Summary

Consolidate CI/CD to target the home server as the sole deployment target. Remove stale GCP and VPS workflows/configs. Harden the deploy pipeline's SSH connection with pinned host keys. Cloudflare Tunnel runs as a systemd service (migrated from Docker container).

## Context

The project historically had three deployment targets:
1. **GCP Cloud Run** — Terraform-managed, never fully activated
2. **Hostinger VPS** (`devpc.nanobyte.ca`) — retired
3. **Home server** (`nanobyte.ca`) — current and only active target

The GCP and VPS workflows are dead code. The `ci.yml` workflow is redundant with `build.yml` (which runs the same tests before building images). The deploy workflow uses `StrictHostKeyChecking no`, which is a MITM risk.

## Architecture (No Change)

```
Push to main
    │
    ▼
GitHub Actions (build.yml)
    ├── test-backend (5 services)
    ├── test-frontend (lint, test, build)
    └── build-and-push (7 images → GHCR)
           │
           ▼
Manual dispatch (deploy.yml)
    ├── Fetch secrets from Vault (AppRole)
    ├── SSH via Cloudflare Tunnel (systemd)
    ├── SCP .env to /opt/portfolio/{env}/
    └── docker compose pull && up -d
```

## Changes

### 1. Delete `ci.yml`

**File:** `.github/workflows/ci.yml`

This workflow served two purposes:
- PR validation (tests only)
- Reusable workflow called by the old VPS deploy

Both are now covered by `build.yml`. The PR validation use case is restored by adding a `pull_request` trigger to `build.yml` that skips image builds.

**What `ci.yml` does that `build.yml` doesn't:**
- Uploads `backend-jar` artifact — only needed for old SCP-based VPS deploy
- Uploads `frontend-build` artifact — same
- `workflow_call` with `skip-docker-builds` / `vite-api-url` inputs — VPS-specific
- Docker build test (no push) — `build.yml` does real builds+push instead
- Tests only the portfolio backend service — `build.yml` tests all 5 backend services

Nothing in `ci.yml` is needed going forward.

### 2. Update `build.yml` — Add PR trigger

Add `pull_request` trigger to `build.yml`. The `build-and-push` job already has `needs: [test-backend, test-frontend]`, so PRs will run tests only. The `build-and-push` job gets a condition to skip on PRs:

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test-backend:
    # ... unchanged

  test-frontend:
    # ... unchanged

  build-and-push:
    if: github.event_name == 'push'
    needs: [test-backend, test-frontend]
    # ... unchanged
```

### 3. Update `deploy.yml` — Harden SSH

Replace `StrictHostKeyChecking no` with pinned host key from `SSH_KNOWN_HOSTS` secret.

**Before:**
```yaml
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
```

**After:**
```yaml
- name: Configure SSH via Cloudflare Tunnel
  run: |
    mkdir -p ~/.ssh
    echo "${{ secrets.DEPLOY_SSH_KEY }}" > ~/.ssh/deploy_key
    chmod 600 ~/.ssh/deploy_key
    echo "${{ secrets.SSH_KNOWN_HOSTS }}" > ~/.ssh/known_hosts
    chmod 644 ~/.ssh/known_hosts

    cat >> ~/.ssh/config << EOF
    Host portfolio-server
      HostName ${{ secrets.SERVER_HOSTNAME }}
      User deploy
      IdentityFile ~/.ssh/deploy_key
      ProxyCommand cloudflared access ssh --hostname %h
    EOF
```

### 4. Update `deploy/scripts/setup-server.sh`

Add reference to `setup-cloudflared-tunnel.sh` in the "Next steps" output section. The tunnel setup was previously undocumented — now it's a dedicated script.

Update the "Next steps" section to:
```
Next steps:
  1. SSH key setup:  ssh-copy-id deploy@this-server
  2. Tunnel setup:   bash deploy/scripts/setup-cloudflared-tunnel.sh
  3. Copy configs:   cp deploy files to /opt/portfolio/
  4. Start monitoring: cd /opt/portfolio/monitoring && docker compose up -d
  5. Init Vault:     bash deploy/scripts/vault-init.sh
  6. Create .env:    populate secrets in Vault, then deploy via GitHub Actions
```

### 5. Update `docs/reference/infrastructure.md`

Remove these sections entirely:
- **Architecture Overview / GCP Production** — system components diagram, request flow, GCP resources table
- **Environment Separation** (GCP projects strategy)
- **Network Security** (Cloud SQL, VPC Connector references)
- **Scalability** (Cloud Run, Cloud SQL, Cloud Storage)
- **docker-compose.vps.yml** section
- **Terraform** — all modules (cloud-sql, cloud-storage, cloud-run, load-balancer, workload-identity), both environment configs
- **GCP Deployment Setup** — prerequisites, initial terraform setup, monitoring, rollback, troubleshooting
- **Nginx / infra/nginx/devpc.nanobyte.ca.conf** — VPS reverse proxy config
- **Scripts: setup-vps.sh, setup-nginx-ssl.sh, vps-diagnose.sh, vps-fix-deployment.sh** sections
- **Old GitHub Secrets table** (VPS-specific: `VPS_HOST`, `VPS_USER`, `VPS_SSH_PRIVATE_KEY`, etc.)

Keep and update:
- **Home Server Deployment** section (already accurate)
- **Docker** section (local dev compose, Dockerfiles)
- **Local Development** section
- **CI/CD Workflows** — update to reflect only `build.yml` and `deploy.yml`

Add:
- **Cloudflare Tunnel Setup** — reference to `setup-cloudflared-tunnel.sh`
- **SSH Access** — document the Cloudflare Tunnel SSH flow, `deploy` user, key-based auth

### 6. Update `docs/reference/configurations.md`

- Remove VPS profile (`application-dev.yml` for Hostinger VPS) references
- Remove old VPS-specific environment variables section
- Keep local/dev/prod profile documentation (dev profile is used on home server UAT)

### 7. New file: `deploy/scripts/setup-cloudflared-tunnel.sh`

Already created during this session. Installs cloudflared as a systemd service with:
- All HTTP ingress rules (portfolio, uatportfolio, status, grafana, vault)
- SSH ingress rule (`ssh.nanobyte.ca` → `localhost:22`)
- DNS route creation
- Config validation
- Systemd service installation

## GitHub Secrets (Final State)

| Secret | Purpose | Status |
|---|---|---|
| `DEPLOY_SSH_KEY` | SSH private key for `deploy` user | Active |
| `SERVER_HOSTNAME` | `ssh.nanobyte.ca` | Active |
| `SSH_KNOWN_HOSTS` | Server SSH host key fingerprint | Active |
| `VAULT_ROLE_ID` | Vault AppRole authentication | Active |
| `VAULT_SECRET_ID` | Vault AppRole authentication | Active |
| `SLACK_WEBHOOK_URL` | Deployment notifications | Active |

Future (not in this spec):
| `CF_ACCESS_CLIENT_ID` | Cloudflare Access service token | Deferred |
| `CF_ACCESS_CLIENT_SECRET` | Cloudflare Access service token | Deferred |

## Server Bootstrap Order

For a fresh server setup, the order is:

1. Run `deploy/scripts/setup-server.sh` (Docker, UFW, deploy user, directories)
2. Run `deploy/scripts/setup-cloudflared-tunnel.sh` (Cloudflare Tunnel as systemd)
3. Set up SSH key for `deploy` user
4. Copy monitoring configs to `/opt/portfolio/monitoring/`
5. Start monitoring stack: `docker compose up -d`
6. Initialize Vault: `bash vault-init.sh`
7. Populate secrets in Vault for prod and UAT
8. Store `VAULT_ROLE_ID`, `VAULT_SECRET_ID`, `DEPLOY_SSH_KEY`, `SERVER_HOSTNAME`, `SSH_KNOWN_HOSTS`, `SLACK_WEBHOOK_URL` in GitHub Secrets
9. First deploy via GitHub Actions: `gh workflow run deploy.yml -f environment=uat -f tag=main-<sha>`

## Out of Scope

- Cloudflare Access service token for SSH (deferred)
- Deploy user shell restriction / sudoers hardening (deferred)
- SSH key rotation script (deferred)
- Merging setup-server.sh and setup-cloudflared-tunnel.sh (deferred)
- Changes to application code, Docker images, or docker-compose files
