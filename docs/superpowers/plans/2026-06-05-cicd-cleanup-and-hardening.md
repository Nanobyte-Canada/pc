# CI/CD Cleanup and SSH Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate CI/CD to the home server, remove stale GCP/VPS workflows, and harden the deploy SSH connection with pinned host keys.

**Architecture:** Delete `ci.yml`, add PR trigger to `build.yml` (test-only on PRs, build+push on main), replace `StrictHostKeyChecking no` in `deploy.yml` with pinned `SSH_KNOWN_HOSTS`, update setup script and reference docs.

**Tech Stack:** GitHub Actions, Cloudflare Tunnel, Docker, HashiCorp Vault

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Delete | `.github/workflows/ci.yml` | Redundant CI workflow (replaced by `build.yml`) |
| Modify | `.github/workflows/build.yml:1-6` | Add `pull_request` trigger |
| Modify | `.github/workflows/build.yml:77-79` | Add `if` condition to skip builds on PRs |
| Modify | `.github/workflows/deploy.yml:84-97` | Replace `StrictHostKeyChecking no` with pinned host key |
| Modify | `deploy/scripts/setup-server.sh:82-89` | Update "Next steps" to reference tunnel script |
| Modify | `docs/reference/infrastructure.md` | Remove GCP/VPS/Terraform sections |
| Modify | `docs/reference/configurations.md` | Remove VPS profile references |

---

### Task 1: Delete `ci.yml`

**Files:**
- Delete: `.github/workflows/ci.yml`

- [ ] **Step 1: Delete the file**

```bash
git rm .github/workflows/ci.yml
```

- [ ] **Step 2: Verify no other workflow references it**

```bash
grep -r "ci.yml" .github/workflows/
```

Expected: No results. The old `deploy-vps.yml` (already deleted) was the only caller via `workflow_call`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "chore: remove redundant ci.yml workflow

build.yml already runs all backend and frontend tests before
building images. ci.yml was only needed for the retired VPS deploy."
```

---

### Task 2: Add PR trigger to `build.yml`

**Files:**
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: Add `pull_request` trigger**

In `.github/workflows/build.yml`, change the `on:` block from:

```yaml
on:
  push:
    branches: [main]
```

To:

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

- [ ] **Step 2: Add condition to skip image builds on PRs**

In the `build-and-push` job, add an `if` condition. Change:

```yaml
  build-and-push:
    name: Build & Push Docker Images
    needs: [test-backend, test-frontend]
```

To:

```yaml
  build-and-push:
    name: Build & Push Docker Images
    if: github.event_name == 'push'
    needs: [test-backend, test-frontend]
```

- [ ] **Step 3: Verify the YAML is valid**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))" && echo "Valid YAML"
```

If python/yaml not available, visually verify indentation is correct.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "feat(ci): add PR trigger to build.yml, skip image builds on PRs

PRs to main now run test-backend and test-frontend jobs.
The build-and-push job only runs on push to main."
```

---

### Task 3: Harden SSH in `deploy.yml`

**Files:**
- Modify: `.github/workflows/deploy.yml`

- [ ] **Step 1: Replace the SSH config block**

In `.github/workflows/deploy.yml`, replace the "Configure SSH via Cloudflare Tunnel" step (lines 84-97).

Change from:

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

To:

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

Key changes:
- Added `SSH_KNOWN_HOSTS` written to `~/.ssh/known_hosts`
- Removed `StrictHostKeyChecking no`

- [ ] **Step 2: Verify the YAML is valid**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/deploy.yml'))" && echo "Valid YAML"
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "fix(deploy): replace StrictHostKeyChecking no with pinned host key

SSH connections now verify the server's host key against the
SSH_KNOWN_HOSTS secret, preventing MITM attacks."
```

---

### Task 4: Update `setup-server.sh` next steps

**Files:**
- Modify: `deploy/scripts/setup-server.sh:79-90`

- [ ] **Step 1: Replace the "Next steps" section**

In `deploy/scripts/setup-server.sh`, replace lines 79-90.

Change from:

```bash
echo ""
echo "=== Setup Complete ==="
echo ""
echo "Next steps:"
echo "  1. SSH key setup:  ssh-copy-id deploy@this-server"
echo "  2. Tunnel setup:   sudo -u deploy cloudflared tunnel login"
echo "                     sudo -u deploy cloudflared tunnel create portfolio-tunnel"
echo "  3. Copy configs:   cp deploy files to /opt/portfolio/"
echo "  4. Create .env:    cp .env.example .env && edit with real values"
echo "  5. Start stacks:   cd /opt/portfolio/prod && docker compose up -d"
echo ""
```

To:

```bash
echo ""
echo "=== Setup Complete ==="
echo ""
echo "Next steps:"
echo "  1. Tunnel setup:   sudo bash deploy/scripts/setup-cloudflared-tunnel.sh"
echo "  2. SSH key setup:  generate ed25519 keypair, add pubkey to /home/deploy/.ssh/authorized_keys"
echo "  3. Copy configs:   cp deploy/monitoring/* to /opt/portfolio/monitoring/"
echo "  4. Start monitoring: cd /opt/portfolio/monitoring && docker compose up -d"
echo "  5. Init Vault:     bash deploy/scripts/vault-init.sh"
echo "  6. Populate Vault:  store prod/uat secrets in Vault"
echo "  7. GitHub Secrets:  add DEPLOY_SSH_KEY, SERVER_HOSTNAME, SSH_KNOWN_HOSTS,"
echo "                      VAULT_ROLE_ID, VAULT_SECRET_ID, SLACK_WEBHOOK_URL"
echo "  8. First deploy:   gh workflow run deploy.yml -f environment=uat -f tag=main-<sha>"
echo ""
```

- [ ] **Step 2: Commit**

```bash
git add deploy/scripts/setup-server.sh
git commit -m "docs: update setup-server.sh next steps for Cloudflare Tunnel workflow"
```

---

### Task 5: Update `infrastructure.md`

**Files:**
- Modify: `docs/reference/infrastructure.md`

This is the largest change — removing ~400 lines of GCP/VPS content while keeping the home server sections.

- [ ] **Step 1: Remove GCP and VPS sections**

Remove these sections from `docs/reference/infrastructure.md`:

1. **"Architecture Overview / System Components (GCP Production)"** — the GCP diagram, request flow, GCP resources table, environment separation, network security, scalability (lines 9-108)
2. **"docker-compose.vps.yml"** section (lines 229-248)
3. **"CI/CD Workflows / .github/workflows/deploy.yml -- GCP Cloud Run Deployment"** section (lines 278-301)
4. **"CI/CD Workflows / .github/workflows/deploy-vps.yml -- VPS Deployment"** section (lines 303-347)
5. **"Terraform"** — entire section including all modules and environments (lines 350-481)
6. **"GCP Deployment Setup"** — entire section including prerequisites, setup, monitoring, rollback, troubleshooting (lines 486-558)
7. **"Nginx / infra/nginx/devpc.nanobyte.ca.conf"** section (lines 563-597)
8. **"Scripts: setup-vps.sh, setup-nginx-ssl.sh, vps-diagnose.sh, vps-fix-deployment.sh"** descriptions (lines 618-669)

- [ ] **Step 2: Update the CI/CD Workflows section**

Replace the existing `ci.yml` and GCP/VPS deploy workflow descriptions with updated content reflecting only `build.yml` and `deploy.yml`:

```markdown
### .github/workflows/build.yml -- Build & Push Images

**Triggers:**
- Push to `main` branch (test + build + push)
- Pull request to `main` (test only, no image builds)

**Jobs:**

| Job | Runs On | Steps |
|-----|---------|-------|
| `test-backend` | PR + push | JDK 21, Gradle test for all 5 services (portfolio, ingestion, market-data, strategy, broker-gateway) |
| `test-frontend` | PR + push | Node 20, `npm ci`, lint, test, build |
| `build-and-push` | push only | Docker Buildx, build 7 images, push to GHCR with `main-<sha>` + `latest` tags, Slack notification |

**Images built (7):**
- `portfolio-backend`, `portfolio-ingestion`, `portfolio-market-data`, `portfolio-strategy`, `portfolio-broker-gateway`
- `portfolio-frontend` (prod, `VITE_API_URL=https://portfolio.nanobyte.ca`)
- `portfolio-frontend-uat` (UAT, `VITE_API_URL=https://uatportfolio.nanobyte.ca`)

### .github/workflows/deploy.yml -- Deploy to Home Server

**Triggers:** Manual workflow dispatch via GitHub UI

**Inputs:**
- `environment` (required): `prod` or `uat`
- `tag` (required): Docker image tag (e.g., `main-abc1234`)

**Steps:**
1. Validate tag format (`main-<short-sha>`)
2. Fetch secrets from Vault (`vault.nanobyte.ca`) via AppRole authentication
3. Generate `.env` file from Vault response, append `IMAGE_TAG`, validate key count
4. Install cloudflared and configure SSH via Cloudflare Tunnel (pinned host key)
5. SCP generated `.env` to server at `/opt/portfolio/{env}/.env`
6. SSH to server: `docker compose pull && docker compose up -d`, wait for health checks
7. Cleanup, post-deploy summary, Slack notification

**Rollback:** Re-run workflow with previous tag.
```

- [ ] **Step 3: Add Cloudflare Tunnel Setup section**

Add after the "Home Server Deployment / Security" section:

```markdown
### Cloudflare Tunnel Setup

Cloudflare Tunnel runs as a systemd service (not a Docker container) to avoid circular dependency with Docker.

**Setup script:** `deploy/scripts/setup-cloudflared-tunnel.sh`

The script:
1. Authenticates to Cloudflare (manual browser step)
2. Creates the tunnel
3. Writes config to `/etc/cloudflared/config.yml`
4. Adds DNS routes for all hostnames
5. Installs as systemd service

**Config location:** `/etc/cloudflared/config.yml`

**SSH access:** `ssh.nanobyte.ca` routes to `localhost:22` via the tunnel. GitHub Actions uses `cloudflared access ssh` as a ProxyCommand with pinned host key verification.
```

- [ ] **Step 4: Update the GitHub Secrets table**

Replace the existing GitHub Secrets table in the "Home Server Deployment" section with:

```markdown
### GitHub Secrets Required

| Secret | Purpose |
|--------|---------|
| `DEPLOY_SSH_KEY` | Ed25519 SSH private key for `deploy` user |
| `SERVER_HOSTNAME` | `ssh.nanobyte.ca` (Cloudflare Tunnel SSH hostname) |
| `SSH_KNOWN_HOSTS` | Server SSH host key fingerprint for MITM prevention |
| `VAULT_ROLE_ID` | Vault AppRole role ID for secret fetching |
| `VAULT_SECRET_ID` | Vault AppRole secret ID for secret fetching |
| `SLACK_WEBHOOK_URL` | Slack channel webhook for deploy notifications |
```

- [ ] **Step 5: Commit**

```bash
git add docs/reference/infrastructure.md
git commit -m "docs: remove GCP/VPS from infrastructure reference, update CI/CD docs

Home server is now the sole deployment target. Removed Terraform,
Cloud Run, VPS, and old Nginx sections. Updated workflow docs to
reflect build.yml and deploy.yml only."
```

---

### Task 6: Update `configurations.md`

**Files:**
- Modify: `docs/reference/configurations.md`

- [ ] **Step 1: Remove VPS-specific profile reference**

In `docs/reference/configurations.md`, update the `application-dev.yml` section header and description.

Change:

```markdown
### application-dev.yml -- VPS Dev Overrides

File: `backend/portfolio/src/main/resources/application-dev.yml`

- `server.forward-headers-strategy: framework` (trust X-Forwarded-* headers from Nginx)
```

To:

```markdown
### application-dev.yml -- UAT / Dev Overrides

File: `backend/portfolio/src/main/resources/application-dev.yml`

- `server.forward-headers-strategy: framework` (trust X-Forwarded-* headers from reverse proxy)
```

- [ ] **Step 2: Update the Profile Summary table**

Change the `dev` column header from referencing VPS:

Change:

```markdown
| Property | local | dev | prod |
|----------|-------|-----|------|
| Spring profile | `local` | `dev` | `prod` |
| Forward headers | Not set | `framework` | Not set |
| App logging | DEBUG | DEBUG | WARN |
| Security logging | Not set | DEBUG/TRACE | Not set |
| Hibernate SQL | OFF | OFF | OFF |
| Redis | Available (Docker) | Not available | Not configured |
| CORS origins | `http://localhost:3000` | `https://devpc.nanobyte.ca` | Configured per deployment |
```

To:

```markdown
| Property | local | dev (UAT) | prod |
|----------|-------|-----------|------|
| Spring profile | `local` | `dev` | `prod` |
| Forward headers | Not set | `framework` | Not set |
| App logging | DEBUG | DEBUG | WARN |
| Security logging | Not set | DEBUG/TRACE | Not set |
| Hibernate SQL | OFF | OFF | OFF |
| Redis | Available (Docker) | Available (Docker) | Available (Docker) |
| CORS origins | `http://localhost:3000` | `https://uatportfolio.nanobyte.ca` | `https://portfolio.nanobyte.ca` |
```

- [ ] **Step 3: Remove old environment files reference**

If there are any remaining references to `devpc.nanobyte.ca` or the old VPS environment, update them to reflect the home server hostnames.

- [ ] **Step 4: Commit**

```bash
git add docs/reference/configurations.md
git commit -m "docs: update configurations reference for home server deployment

Renamed dev profile from 'VPS Dev' to 'UAT / Dev'. Updated CORS
origins and Redis availability to reflect home server setup."
```

---

### Task 7: Final verification

- [ ] **Step 1: Verify no remaining references to old targets**

```bash
grep -r "devpc.nanobyte.ca" docs/ .github/ deploy/ --include="*.md" --include="*.yml" --include="*.sh"
grep -r "deploy-vps" docs/ .github/ deploy/ --include="*.md" --include="*.yml" --include="*.sh"
grep -r "cloud-run\|cloud_run\|cloudrun" docs/ .github/ deploy/ --include="*.md" --include="*.yml" --include="*.sh"
grep -r "terraform" docs/ .github/ deploy/ --include="*.md" --include="*.yml" --include="*.sh"
```

Expected: No results (or only historical references in archived specs).

- [ ] **Step 2: Verify workflow YAML files are valid**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))" && echo "build.yml: Valid"
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/deploy.yml'))" && echo "deploy.yml: Valid"
```

- [ ] **Step 3: Verify only expected workflow files remain**

```bash
ls .github/workflows/
```

Expected: `build.yml` and `deploy.yml` only.

- [ ] **Step 4: Commit any remaining fixes**

If grep found stale references, fix them and commit:

```bash
git add -A
git commit -m "chore: clean up remaining GCP/VPS references"
```

---

## Task Summary

| Task | Description | Files | Estimated Time |
|------|-------------|-------|---------------|
| 1 | Delete `ci.yml` | 1 deleted | 2 min |
| 2 | Add PR trigger to `build.yml` | 1 modified | 3 min |
| 3 | Harden SSH in `deploy.yml` | 1 modified | 3 min |
| 4 | Update `setup-server.sh` next steps | 1 modified | 3 min |
| 5 | Update `infrastructure.md` | 1 modified | 15 min |
| 6 | Update `configurations.md` | 1 modified | 5 min |
| 7 | Final verification | 0 | 5 min |
| **Total** | | **5 modified, 1 deleted** | **~35 min** |
