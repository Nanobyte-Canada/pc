# Cleanup Monitoring & Infra to nanobyte-services

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove all monitoring stack, Vault, backup, and DB init files from the pc repo now that they live in nanobyte-services. Update docs and scripts to reflect the new ownership.

**Architecture:** The monitoring stack (Prometheus, Grafana, Loki, Promtail, cAdvisor, node-exporter, postgres-exporter, redis-exporter, Uptime Kuma), Vault, nightly backups (`backup.sh`), and UAT Postgres init (`init-guc.sh`) have all moved to the nanobyte-services repo. The pc repo should only own its application services, CI/CD workflows, and local dev stack. All references to monitoring infra, Vault config, and backup scripts in pc are now redundant.

**Tech Stack:** Docker Compose, GitHub Actions, Grafana provisioning, Prometheus, Cloudflare Tunnel

## Global Constraints

- **Branch strategy: do ALL work on one branch (e.g. `chore/infra-cleanup`) and land it as a single PR.** AGENTS.md requires the ADR entry in the same commit/PR as the infra change — committing the deletion directly to `main` without the ADR would violate the contract.
- Never delete ADR entries — append new ones only
- No `Co-Authored-By` or AI attribution in commits
- Don't push unless explicitly asked
- Port scheme: `1xxxx` = prod, `2xxxx` = uat, 100-port gaps between apps
- Container naming: `{env}-portfolio-{service}`
- External networks: `infra-prod-network`, `infra-uat-network`, `prod-internal-network`, `uat-internal-network` (all `external: true`)
- **Next ADR number is ADR-0023** (current latest is ADR-0022 at `docs/adr.md:159`) — verify before appending

---

## File Structure

### Files to DELETE (redundant — all now in nanobyte-services)

| File | Reason |
|------|--------|
| `deploy/monitoring/` (entire directory, 10 files) | Full monitoring stack duplicated in nanobyte-services |
| `deploy/scripts/backup.sh` | Backup script duplicated in nanobyte-services `scripts/backup.sh` |
| `deploy/uat/init-guc.sh` | **Obsolete via Flyway V76**, not via nanobyte-services: `backend/portfolio/src/main/resources/db/migration/V76__uat_seed_test_admin_user.sql` seeds the UAT test admin unconditionally (its header notes V75 was a no-op because the `app.environment` PostgreSQL GUC was never set). `current_setting('app.environment')` appears only in V75. No nanobyte-services port needed. |
| `deploy/scripts/vault-init.sh` | Initializes `portfolio-vault` container which no longer exists in pc; Vault is now nanobyte-services' shared `vault` container, initialized by their bootstrap |

> ⚠️ **BLOCKER GATE (Task 0):** The working tree has **uncommitted changes** in `deploy/monitoring/` (datasourceUid fixes, `job="node-exporter"` filter, faster alert timing) plus an **untracked** `deploy/monitoring/webhook-proxy/`. Deleting `deploy/monitoring/` discards all of this permanently. Confirmed as of plan-writing: nanobyte-services does **not** contain the webhook-proxy and its `rules.yml` still uses `datasourceUid: prometheus` (lines 17, 49). **Task 0 requires an explicit user decision before any deletion.**

### Files to EDIT

| File | Change |
|------|--------|
| `deploy/scripts/setup-server.sh` | Remove monitoring + backups directory creation, post-setup monitoring/vault-init instructions; reword "Populate Vault" step (secrets now go into the shared Vault) |
| `docs/reference/infrastructure.md` | Remove monitoring stack sections, update port reference, remove backup/restore sections; reword "comprehensive monitoring" intro; remove `scripts/backup.sh` + `backups/` from directory tree; remove backup-cron line from Server Setup description |
| `README.md` | Remove `deploy/monitoring/` from directory listing |
| `AGENTS.md` | Remove `deploy/monitoring/` from directory listing |
| `docs/adr.md` | Append ADR-0023 documenting migration of monitoring infra to nanobyte-services |

### Files to KEEP (still needed)

| File | Reason |
|------|--------|
| `deploy/prod/docker-compose.yml` | App services reference centralized Postgres/Redis by hostname |
| `deploy/uat/docker-compose.yml` | App services reference centralized Postgres/Redis by hostname |
| `deploy/prod/.env.example` | App env vars (POSTGRES_DB, etc.) still needed |
| `deploy/uat/.env.example` | App env vars still needed |
| `config/.env.example` | Local dev env vars |
| Root `docker-compose.yml` | Local dev stack with its own Postgres/Redis |
| `deploy/cloudflared/config.yml` + `deploy/scripts/setup-cloudflared-tunnel.sh` | **Only home for the tunnel config** — nanobyte-services has no cloudflared files. Host ports are unchanged by the migration (Grafana 13000, Uptime Kuma 13001, Vault `127.0.0.1:18200:8200` in `infra/shared/docker-compose.yml:18`), so `config.yml:27-40` and the generated routes remain correct. A future cleanup must NOT delete these as "obviously redundant". |
| `.github/workflows/` (all 4) | CI/CD for app builds and deploys — Vault auth via `https://vault.nanobyte.ca` (tunnel) is unaffected |

### Historical docs — NO edits, left as record

`docs/adr.md` past entries, `docs/superpowers/specs/2026-05-31-vault-secret-manager-design.md`, `docs/superpowers/specs/2026-06-05-cicd-cleanup-and-hardening-design.md` (historical design specs — same policy as ADRs: never rewrite history).

---

## Tasks

### Task 0: Pre-flight gate — decide fate of uncommitted monitoring work ⛔ HARD GATE

**Files:**
- Read: `deploy/monitoring/` (uncommitted diffs)

**Interfaces:**
- Produces: an explicit user decision (port-to-nanobyte-services OR abandon) and a stash-based safety net. Task 1 must not start until both exist.

- [ ] **Step 1: Show the user exactly what will be lost**

```bash
git status deploy/monitoring/
git diff deploy/monitoring/docker-compose.yml deploy/monitoring/grafana/provisioning/alerting/rules.yml deploy/monitoring/grafana/provisioning/alerting/slack.yml
```

Summarize for the user:
- **datasourceUid fixes** (`prometheus`/`loki` → real Grafana UIDs `PBFA97CFB590B2093`/`P8E80F9AEF21F6940`) in `rules.yml` — nanobyte-services still uses the stale `datasourceUid: prometheus` form
- **`job="node-exporter"` filter** on the disk-usage query
- **Faster alert timing** (group_wait 1s / group_interval 1m / repeat 1m)
- **`webhook-proxy/`** (untracked, 2 files) — Grafana webhook → Slack formatter; nanobyte-services has no equivalent (its `slack.yml` posts directly to `${SLACK_WEBHOOK_URL}`)

- [ ] **Step 2: Get an explicit user decision (use the question tool)**

**RESOLVED 2026-09-16: User chose "Abandon all"** — delete everything as-is. nanobyte-services' direct-Slack contact point is the accepted replacement; its alerts needing their own fix later is accepted as out of scope for pc. Record this in the ADR context (Task 5).

- [ ] **Step 3: Create the safety net (always, regardless of decision)**

```bash
git stash push -u -m "pre-cleanup: uncommitted monitoring fixes + webhook-proxy" -- deploy/monitoring
git stash list | head -2
```

Expected: one new stash entry. Recovery if needed: `git stash pop`. This makes Task 1's deletion non-destructive.

- [ ] **Step 4: Record the decision**

Note the decision and date in the Task 5 (ADR) context. Do not proceed to Task 1 without Steps 2–3 complete.

---

### Task 1: Delete redundant files

**Files:**
- Delete: `deploy/monitoring/` (entire directory tree; already stashed in Task 0)
- Delete: `deploy/scripts/backup.sh`
- Delete: `deploy/scripts/vault-init.sh`
- Delete: `deploy/uat/init-guc.sh`

- [ ] **Step 1: Remove the files**

```bash
rm -rf deploy/monitoring/
rm deploy/scripts/backup.sh deploy/scripts/vault-init.sh deploy/uat/init-guc.sh
```

- [ ] **Step 2: Verify nothing references the deleted files**

```bash
grep -rn --exclude-dir=.git --include="*.yml" --include="*.yaml" --include="*.sh" --include="*.md" "deploy/monitoring" .
grep -rn --exclude-dir=.git --include="*.yml" --include="*.yaml" --include="*.sh" --include="*.md" "backup\.sh" .
grep -rn --exclude-dir=.git --include="*.yml" --include="*.yaml" --include="*.sh" --include="*.md" "init-guc" .
grep -rn --exclude-dir=.git --include="*.yml" --include="*.yaml" --include="*.sh" --include="*.md" "vault-init" .
```

Expected hits (all cleaned up by Tasks 2–4, or historical):
- `docs/adr.md` — historical record, never edited
- `docs/reference/infrastructure.md` — cleaned in Task 4
- `deploy/scripts/setup-server.sh` — cleaned in Task 2
- `README.md`, `AGENTS.md` — cleaned in Task 3
- `docs/superpowers/specs/2026-05-31-vault-secret-manager-design.md`, `docs/superpowers/specs/2026-06-05-cicd-cleanup-and-hardening-design.md` — historical specs, left as-is
- this plan file
No hits in CI/CD workflows or compose files.

- [ ] **Step 3: Stage explicitly (no `git add -A`) and commit**

```bash
git rm -r --cached deploy/monitoring 2>/dev/null; git add -u deploy/ && git add deploy/uat/init-guc.sh 2>/dev/null
git commit -m "chore: remove monitoring stack, backup, vault-init, and init-guc (moved to nanobyte-services)"
```

Simpler equivalent (preferred):
```bash
git add -A deploy/
git commit -m "chore: remove monitoring stack, backup, vault-init, and init-guc (moved to nanobyte-services)"
```
Verify with `git status` that ONLY intended deletions are staged (plan file and unrelated changes must not be swept in).

---

### Task 2: Update `deploy/scripts/setup-server.sh`

**Files:**
- Modify: `deploy/scripts/setup-server.sh`

- [ ] **Step 1: Read the current file**

```bash
cat deploy/scripts/setup-server.sh
```

- [ ] **Step 2: Trim the `mkdir -p` (line ~56)**

Remove `monitoring/...` entries AND the `backups/prod,backups/uat` entries (backups are now nanobyte-services' concern — their cron writes to `/opt/backups`). Keep prod/uat app dirs.

- [ ] **Step 3: Rewrite the post-setup instructions (lines ~85-88)**

Remove these `echo` lines:
```
3. Copy configs:   cp deploy/monitoring/* to /opt/portfolio/monitoring/
4. Start monitoring: cd /opt/portfolio/monitoring && docker compose up -d
5. Init Vault:     bash deploy/scripts/vault-init.sh
```
Renumber remaining steps. Reword the "Populate Vault" step to say secrets go into the **shared Vault** (nanobyte-services `infra/shared`, same `https://vault.nanobyte.ca` endpoint).

- [ ] **Step 4: Verify the script still parses**

```bash
bash -n deploy/scripts/setup-server.sh
```

- [ ] **Step 5: Commit**

```bash
git add deploy/scripts/setup-server.sh
git commit -m "chore: remove monitoring and backup setup from server bootstrap (managed by nanobyte-services)"
```

---

### Task 3: Update `README.md` and `AGENTS.md`

**Files:**
- Modify: `README.md` (line ~69)
- Modify: `AGENTS.md` (line ~18)

One commit for both repo-map edits (trivial, related changes).

- [ ] **Step 1: README.md — remove the monitoring line**

Remove the line: `deploy/monitoring/            — Monitoring stack` from the repo map.

- [ ] **Step 2: AGENTS.md — remove the monitoring line**

Remove the line: `deploy/monitoring/            — Monitoring stack`

- [ ] **Step 3: Verify no other stale references in either file**

```bash
grep -n "monitoring\|backup\|vault-init\|init-guc" README.md AGENTS.md
```
README's "shared infrastructure dependency" paragraph (line ~54) already documents the nanobyte-services split — keep it. README line ~150's `setup-sdlc-vault.sh` reference is NOT stale (targets `https://vault.nanobyte.ca`) — leave alone.

- [ ] **Step 4: Commit**

```bash
git add README.md AGENTS.md
git commit -m "docs: drop monitoring stack from repo maps (managed by nanobyte-services)"
```

---

### Task 4: Update `docs/reference/infrastructure.md`

**Files:**
- Modify: `docs/reference/infrastructure.md` (736 lines)

- [ ] **Step 1: Read the full file**

- [ ] **Step 2: Reword the intro (line ~288)**

"dedicated home server with comprehensive monitoring" → monitoring is now a shared platform concern (nanobyte-services).

- [ ] **Step 3: Update "Directory Structure" (lines ~288-325)**

Remove `/opt/portfolio/monitoring/` entries, `scripts/backup.sh`, and `backups/` from the tree. Monitoring dirs are now at `/opt/nanobyte-services/monitoring/`; backups at `/opt/backups`.

- [ ] **Step 4: Update "Docker Compose Stacks" tables (lines ~327-357)**

Remove `prod-postgres`, `prod-redis`, `uat-postgres`, `uat-redis`, and IBKR Gateway entries. Keep only application services.

- [ ] **Step 5: Remove "Monitoring Stack" table (lines ~359-372)**

- [ ] **Step 6: Update "Cloudflare Tunnel Routing" (lines ~374-384) AND "Cloudflare Tunnel Setup" (lines ~500-515)**

Keep both sections — the routes and the `setup-cloudflared-tunnel.sh` reference remain valid (host ports unchanged). Add a note that the routed services are managed by nanobyte-services. Do NOT remove the setup script reference.

- [ ] **Step 7: Remove "Observability" section (lines ~434-478)**

- [ ] **Step 8: Remove "Backups" section (lines ~542-576)**

- [ ] **Step 9: Remove "Vault Secret Management" section (lines ~578-602)**

Includes the `deploy/scripts/vault-init.sh` reference at line ~587. Keep CI/CD Vault auth docs (`https://vault.nanobyte.ca` via AppRole — unaffected).

- [ ] **Step 10: Update "Port Reference Table" (lines ~604-641)**

Remove monitoring and Postgres/Redis host-port rows **actually present in pc's table** (e.g. 18200, 19187, 15432, 16379, 25432, 26379, and any monitoring ports listed — verify against the file; pc's table does not contain 19188). Keep app ports (10000-10084, 20000-20084).

- [ ] **Step 11: Update "Server Setup" section (lines ~655-670)**

Remove the "Install Prometheus exporters" item AND the "Create backup cron jobs" item (the script does neither — pre-existing doc drift). Confirm the section matches the script post-Task 2.

- [ ] **Step 12: Commit**

```bash
git add docs/reference/infrastructure.md
git commit -m "docs: update infrastructure.md for monitoring/vault/backup move to nanobyte-services"
```

---

### Task 5: Append ADR-0023

**Files:**
- Modify: `docs/adr.md` (append after ADR-0022 at line ~159)

- [ ] **Step 1: Verify the last ADR number**

```bash
grep -n "^## ADR-00" docs/adr.md | tail -3
```
Expected: ADR-0022 is highest → new entry is **ADR-0023**.

- [ ] **Step 2: Append the entry**

Content:
- **Title:** Monitoring/Vault/Backup ownership fully migrated to nanobyte-services
- **Decision:** Delete `deploy/monitoring/` (incl. webhook-proxy), `deploy/scripts/backup.sh`, `deploy/scripts/vault-init.sh`, `deploy/uat/init-guc.sh` from pc. nanobyte-services is the single owner of monitoring, Vault, backups, and server bootstrap.
- **Context:** Follows ADR-0017 (shared DB/Redis). nanobyte-services now ships the full monitoring stack, shared Vault (container `vault`, port 18200), `backup.sh` (pg_dumpall + Vault snapshot, 7-day retention), and `bootstrap-server.sh`. `init-guc.sh` is obsolete because Flyway V76 seeds the UAT test admin unconditionally (V75 was a no-op — the `app.environment` GUC was never set). `vault-init.sh` targeted the retired `portfolio-vault` container. Also record the Task 0 decision on the uncommitted alert fixes (ported to nanobyte-services vs abandoned).
- **Consequences:** pc CI/CD is unaffected — workflows authenticate to `https://vault.nanobyte.ca` (Cloudflare tunnel URL), not to any pc-managed container. `deploy/cloudflared/config.yml` and `setup-cloudflared-tunnel.sh` stay in pc (only home for tunnel config; host ports unchanged). pc repo now owns only app services, CI/CD, and local dev stack.

- [ ] **Step 3: Commit**

```bash
git add docs/adr.md
git commit -m "docs: ADR-0023 monitoring/vault/backup ownership moved to nanobyte-services"
```

---

### Task 6: Final verification

- [ ] **Step 1: Verify no dangling references in pc**

```bash
grep -rn --exclude-dir=.git --include="*.yml" --include="*.yaml" --include="*.sh" --include="*.md" --include="*.json" "deploy/monitoring\|backup\.sh\|init-guc\|vault-init\|webhook-proxy" . | grep -v "docs/superpowers/plans/"
```
Expected: Only `docs/adr.md` (historical) and the two historical specs. No hits in workflows, compose files, or active scripts.

- [ ] **Step 2: Verify Vault auth path in workflows is untouched and correct**

```bash
grep -n "VAULT_ADDR" .github/workflows/*.yml
```
Expected: `https://vault.nanobyte.ca` in deploy.yml:24, deploy-prod.yml:13, sdlc-agent.yml:34 — unchanged, tunnel-based, unaffected by this cleanup.

- [ ] **Step 3: Verify no monitoring services in app compose files**

```bash
grep -n "prometheus\|grafana\|loki\|cadvisor\|node-exporter\|postgres-exporter\|redis-exporter\|uptime-kuma\|promtail\|webhook-proxy" deploy/prod/docker-compose.yml deploy/uat/docker-compose.yml
```
Expected: No hits.

- [ ] **Step 4: Verify app compose files still parse**

```bash
docker compose -f deploy/prod/docker-compose.yml config -q && docker compose -f deploy/uat/docker-compose.yml config -q
```
Expected: No errors (warnings about unset env vars are acceptable).

- [ ] **Step 5: Verify local dev stack unaffected**

```bash
grep -n "postgres\|redis" docker-compose.yml | head -10
```
Expected: Local dev Postgres/Redis still present.

- [ ] **Step 6: Verify nanobyte-services contains the replacements (cross-repo completeness)**

```bash
ls /home/sbilakhia/Documents/dev/repos/nanobyte-services/scripts/backup.sh \
   /home/sbilakhia/Documents/dev/repos/nanobyte-services/scripts/bootstrap-server.sh \
   /home/sbilakhia/Documents/dev/repos/nanobyte-services/monitoring/docker-compose.yml \
   /home/sbilakhia/Documents/dev/repos/nanobyte-services/infra/shared/docker-compose.yml \
   /home/sbilakhia/Documents/dev/repos/nanobyte-services/infra/prod/docker-compose.yml \
   /home/sbilakhia/Documents/dev/repos/nanobyte-services/infra/uat/docker-compose.yml
```
Expected: All exist. Note: `init-guc.sh` is intentionally NOT ported (Flyway V76 handles seeding) — recorded in ADR-0023.

- [ ] **Step 7: Review git state**

```bash
git status && git log --oneline -8
```
Expected: Clean tree; commits show Task 0 stash decision → deletions → script/docs → ADR-0023, all on the feature branch.
