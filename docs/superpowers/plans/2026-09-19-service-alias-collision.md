# Service Alias Collision Fix Implementation Plan — Portfolio Construction App

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the shared-network Docker alias collision that causes intermittent `403 Invalid CORS request` on portfolio UAT (and latent on prod) by renaming compose service keys to app-prefixed unique names across all three compose files and pointing `frontend/nginx.conf` at them, plus the required security rotation and test-hygiene follow-ups.

**Architecture:** Docker Compose service keys become Docker network aliases, so `pc` renames its keys to `portfolio-backend`, `portfolio-ingestion`, `portfolio-market-data`, `portfolio-strategy`, `portfolio-broker-gateway`, and `portfolio-frontend` in `deploy/uat/docker-compose.yml`, `deploy/prod/docker-compose.yml`, and the root `docker-compose.yml`. `frontend/nginx.conf` proxies to those names, so one static config works in every environment. Container names, host ports, compose project names, images, networks, healthchecks, and resources are unchanged. One ADR (ADR-0032) records the decision and the shared-network convention. A one-time `down --remove-orphans` on each host reconciles old service identities; deploy workflows gain `--remove-orphans` permanently.

**Tech Stack:** Docker Compose v2, nginx:alpine (baked into the frontend images), GitHub Actions, Playwright 1.60.0 (existing deployed suite), Vault KV v2 with AppRole, PostgreSQL `psql`, `argon2-cffi` for hash generation.

## Global Constraints

Copied from the design spec (`docs/superpowers/specs/2026-09-19-service-alias-collision-design.md`); every task implicitly includes these.

- Requirements: R1 unambiguous resolution in UAT/prod/local; R2 no container-name/port/image/network change; R3 one static nginx config applied to all three compose files; R4 no user-visible behavior change beyond eliminating the intermittent 403; R5 credential rotation (Vault **and** database) plus retention reduction; R6 test-hygiene adjudication; R7 live verifiability.
- **Never change** container names (`uat-portfolio-*`, `prod-portfolio-*`, `portfolio-*`), host ports (20000/20080–20084, 10000/10080–10084), compose `name:` keys (`portfolio-uat`, `portfolio-prod`), image references, networks, `depends_on` conditions, healthchecks, logging, or resource limits.
- The root compose `postgres`/`redis` service keys are **not** renamed; deployed DB/Redis are external hostnames and untouched.
- Secrets only from Vault (AppRole; `secret/portfolio/common` + `secret/portfolio/{env}`). Never commit, echo, or log credentials. The rotated password must not appear in shell history, CI logs, or commit messages.
- All test execution happens in CI or against deployed environments; no local test runs. Static `docker compose config -q` parsing on the deploy host is permitted (it executes nothing).
- Conventional Commits (`fix(deploy): …`, `test(e2e): …`, `docs: …`, `ci: …`). Never add AI attribution lines. Never push unless the owner asks; inside a task, push the feature branch when a step requires CI verification.
- ADR-0032 must be in the same PR. Never rewrite or delete past ADR entries; append only.
- Historical documents under `docs/superpowers/plans/**` and pre-existing `docs/superpowers/specs/**` are append-only records — do not edit them.
- The frontend image bakes `nginx.conf`; a new image build is required for proxy changes. Compose and nginx changes must be in the same commit/PR so a tag's image and compose file always match.
- Docker was unavailable in the authoring environment; the Compose rename reconciliation path (Tasks 6, 14, 15) is deliberately conservative.

## File Structure Map

```text
deploy/uat/docker-compose.yml        service keys (5,51,85,120,155,192) + depends_on (199-201)
deploy/prod/docker-compose.yml       service keys (5,51,85,120,155,192) + depends_on (199-201)
docker-compose.yml                   service keys + internal URLs + depends_on list
frontend/nginx.conf                  proxy_pass upstream host names only
.github/workflows/deploy.yml         docker compose up -d --remove-orphans (line 177)
.github/workflows/deploy-prod.yml    docker compose up -d --remove-orphans (line 155)
.github/workflows/ui-tests-deployed.yml          retention-days 14 -> 3 (13 steps)
.github/workflows/ui-visual-baseline-update.yml  retention-days 14 -> 3 (line 92)
docs/adr.md                          append ADR-0032
docs/testing/operations.md           alias diagnostic + credential rotation section
docs/runbooks/google-oauth-provider-unavailable.md  nginx snippet + exec container name
README.md                            exec commands + service-key note
docs/reference/infrastructure.md     local stack table, dependency prose, exec examples
docs/reference/frontend-map.md       proxy target table
docs/reference/configurations.md     BROKER_GATEWAY_URL defaults
docs/reference/backend-services.md   broker-gateway.url default
docs/reference/database-schema.md    exec example
docs/reference/INDEX.md              exec examples
AGENTS.md                            invariant #2 service-key convention
e2e/tests/regression/analytics-sectors.spec.ts        vacuous passes (002/003)
e2e/tests/regression/reporting-contributions.spec.ts  vacuous pass (002)
e2e/tests/regression/portfolio-management.spec.ts     vacuous pass (001)
e2e/tests/regression/dashboard-overview.spec.ts       DASH-OVERVIEW-002/004
e2e/tests/regression/wheel-calendar.spec.ts           WHEEL-CAL-002/004
```

---

## Phase 0 — Audit

### Task 1: Confirm the reference audit (read-only)

**Files:**
- Read: `deploy/uat/docker-compose.yml`, `deploy/prod/docker-compose.yml`, `docker-compose.yml`, `frontend/nginx.conf`, `.github/workflows/*.yml`, `docs/reference/*.md`, `docs/runbooks/google-oauth-provider-unavailable.md`, `README.md`, `AGENTS.md`

**Interfaces:**
- Consumes: spec section 5 (discovery baseline). Dated historical plan records under `docs/plans/**` (e.g., `docs/plans/2026-06-15-fix-options-chain-prod.md:20,23,24` references `market-data-service`) are exempt from this audit; only live configuration and current docs are renamed.
- Produces: confirmed audit evidence recorded in the PR description; the exhaustive before-state for the renames.

- [ ] **Step 1: Confirm the service keys and line numbers**

Run:
```bash
grep -nE '^  (backend|ingestion-service|market-data-service|strategy-service|broker-gateway-service|frontend):' deploy/uat/docker-compose.yml deploy/prod/docker-compose.yml
grep -nE '^  (backend|ingestion-service|market-data-service|strategy-service|broker-gateway-service|frontend):' docker-compose.yml
```

Expected: `deploy/uat` and `deploy/prod` each show 6 keys at lines 5, 51, 85, 120, 155, 192; the root file shows the same 6 keys (lines 34, 77, 107, 140, 171, 206).

- [ ] **Step 2: Confirm the generic internal references**

Run:
```bash
grep -n 'proxy_pass http://' frontend/nginx.conf
grep -n 'http://backend\|http://ingestion-service\|http://market-data-service\|http://strategy-service\|http://broker-gateway-service' docker-compose.yml
grep -nA2 'depends_on:' deploy/uat/docker-compose.yml deploy/prod/docker-compose.yml
```

Expected: 8 generic nginx upstreams (lines 29, 38, 47, 52, 58, 71, 80, 90); 7 generic URLs in the root compose (lines 49, 152–153, 214–217); both deployed files show `backend:` under `frontend.depends_on`.

- [ ] **Step 3: Confirm no external consumers depend on the aliases**

Run:
```bash
grep -rn 'docker compose up' .github/workflows/deploy.yml .github/workflows/deploy-prod.yml
grep -rn 'host.docker.internal' /home/sbilakhia/Documents/dev/repos/nanobyte-services/monitoring/prometheus/prometheus.yml
grep -rn 'service=' /home/sbilakhia/Documents/dev/repos/nanobyte-services/monitoring/grafana/provisioning/alerting/rules.yml
```

Expected: deploy workflows run `docker compose up -d` for all services (no service key); Prometheus targets host ports; Grafana rules use Prometheus labels and `container_name` filters, not Docker aliases.

- [ ] **Step 4: Record the audit in the PR description**

Paste the command outputs into the PR body under `## Reference audit`. No commit for this task.

---

## Phase 1 — The fix

### Task 2: Rename the UAT compose service keys

**Files:**
- Modify: `deploy/uat/docker-compose.yml` (keys at 5, 51, 85, 120, 155, 192; `depends_on` at 199-201)

**Interfaces:**
- Consumes: Task 1 audit.
- Produces: UAT service keys `portfolio-backend`, `portfolio-ingestion`, `portfolio-market-data`, `portfolio-strategy`, `portfolio-broker-gateway`, `portfolio-frontend`, consumed by Task 5's nginx config and Task 7's ADR.

- [ ] **Step 1: Rename the six service keys**

Apply exactly these replacements (two-space indentation at the top level of `services:`):

```yaml
  backend:                 ->   portfolio-backend:
  ingestion-service:       ->   portfolio-ingestion:
  market-data-service:     ->   portfolio-market-data:
  strategy-service:       ->   portfolio-strategy:
  broker-gateway-service: ->   portfolio-broker-gateway:
  frontend:                ->   portfolio-frontend:
```

Do not touch `container_name`, `image`, `ports`, `healthcheck`, `deploy`, `logging`, `networks`, or any environment variable that already uses a container name.

- [ ] **Step 2: Rename the `frontend` dependency key**

The `portfolio-frontend` service currently has:

```yaml
    depends_on:
      backend:
        condition: service_healthy
```

Change it to:

```yaml
    depends_on:
      portfolio-backend:
        condition: service_healthy
```

- [ ] **Step 3: Verify the rename**

Run:
```bash
grep -nE '^  (backend|ingestion-service|market-data-service|strategy-service|broker-gateway-service|frontend):' deploy/uat/docker-compose.yml || echo "OLD KEYS ABSENT"
grep -cE '^  portfolio-(backend|ingestion|market-data|strategy|broker-gateway|frontend):' deploy/uat/docker-compose.yml
grep -nA2 'depends_on:' deploy/uat/docker-compose.yml
```

Expected: `OLD KEYS ABSENT`; count `6`; `depends_on` shows `portfolio-backend:` with `condition: service_healthy`.

- [ ] **Step 4: Commit**

```bash
git add deploy/uat/docker-compose.yml
git commit -m "fix(deploy): rename UAT compose services to app-prefixed keys"
```

### Task 3: Rename the prod compose service keys

**Files:**
- Modify: `deploy/prod/docker-compose.yml` (keys at 5, 51, 85, 120, 155, 192; `depends_on` at 199-201)

**Interfaces:**
- Consumes: Task 1 audit.
- Produces: prod service keys matching UAT, consumed by Task 5 and Task 7.

- [ ] **Step 1: Rename the six service keys**

Apply exactly these replacements:

```yaml
  backend:                 ->   portfolio-backend:
  ingestion-service:       ->   portfolio-ingestion:
  market-data-service:     ->   portfolio-market-data:
  strategy-service:       ->   portfolio-strategy:
  broker-gateway-service: ->   portfolio-broker-gateway:
  frontend:                ->   portfolio-frontend:
```

Do not touch `container_name`, `image`, `ports`, `healthcheck`, `deploy`, `logging`, or `networks`.

- [ ] **Step 2: Rename the `frontend` dependency key**

Change:

```yaml
    depends_on:
      backend:
        condition: service_healthy
```

to:

```yaml
    depends_on:
      portfolio-backend:
        condition: service_healthy
```

- [ ] **Step 3: Verify the rename**

Run:
```bash
grep -nE '^  (backend|ingestion-service|market-data-service|strategy-service|broker-gateway-service|frontend):' deploy/prod/docker-compose.yml || echo "OLD KEYS ABSENT"
grep -cE '^  portfolio-(backend|ingestion|market-data|strategy|broker-gateway|frontend):' deploy/prod/docker-compose.yml
grep -nA2 'depends_on:' deploy/prod/docker-compose.yml
```

Expected: `OLD KEYS ABSENT`; count `6`; `portfolio-backend:` under `depends_on`.

- [ ] **Step 4: Commit**

```bash
git add deploy/prod/docker-compose.yml
git commit -m "fix(deploy): rename prod compose services to app-prefixed keys"
```

### Task 4: Rename the local (root) compose service keys and internal URLs

**Files:**
- Modify: `docker-compose.yml` (keys at 34, 77, 107, 140, 171, 206; URLs at 49, 152-153, 214-217; `depends_on` at 225-229)

**Interfaces:**
- Consumes: Task 1 audit.
- Produces: local stack service keys matching the deployed stacks; the same nginx config (Task 5) resolves in local networks.

- [ ] **Step 1: Rename the six service keys**

Apply exactly these replacements:

```yaml
  backend:                 ->   portfolio-backend:
  ingestion-service:       ->   portfolio-ingestion:
  market-data-service:     ->   portfolio-market-data:
  strategy-service:       ->   portfolio-strategy:
  broker-gateway-service: ->   portfolio-broker-gateway:
  frontend:                ->   portfolio-frontend:
```

Keep the `redis` and `postgres` service keys and the `postgres_data` volume unchanged.

- [ ] **Step 2: Update the internal URL in `portfolio-backend`**

Change line 49:

```yaml
      BROKER_GATEWAY_URL: http://broker-gateway-service:8084
```

to:

```yaml
      BROKER_GATEWAY_URL: http://portfolio-broker-gateway:8084
```

- [ ] **Step 3: Update the internal URLs in `portfolio-strategy`**

Change lines 152-153:

```yaml
      MARKET_DATA_SERVICE_URL: http://market-data-service:8082
      PORTFOLIO_SERVICE_URL: http://backend:8080
```

to:

```yaml
      MARKET_DATA_SERVICE_URL: http://portfolio-market-data:8082
      PORTFOLIO_SERVICE_URL: http://portfolio-backend:8080
```

- [ ] **Step 4: Update the Vite proxy URLs and the `depends_on` list**

Change lines 214-217:

```yaml
      VITE_PROXY_BACKEND_URL: http://backend:8080
      VITE_PROXY_INGESTION_URL: http://ingestion-service:8081
      VITE_PROXY_MARKET_DATA_URL: http://market-data-service:8082
      VITE_PROXY_STRATEGY_URL: http://strategy-service:8083
```

to:

```yaml
      VITE_PROXY_BACKEND_URL: http://portfolio-backend:8080
      VITE_PROXY_INGESTION_URL: http://portfolio-ingestion:8081
      VITE_PROXY_MARKET_DATA_URL: http://portfolio-market-data:8082
      VITE_PROXY_STRATEGY_URL: http://portfolio-strategy:8083
```

Change the `depends_on` list at 225-229 from `backend`, `ingestion-service`, `market-data-service`, `strategy-service` to `portfolio-backend`, `portfolio-ingestion`, `portfolio-market-data`, `portfolio-strategy`.

- [ ] **Step 5: Verify the root file**

Run:
```bash
grep -nE '^  (backend|ingestion-service|market-data-service|strategy-service|broker-gateway-service|frontend):' docker-compose.yml || echo "OLD KEYS ABSENT"
grep -n 'http://backend\|http://ingestion-service\|http://market-data-service\|http://strategy-service\|http://broker-gateway-service' docker-compose.yml || echo "NO GENERIC URLS"
grep -nA5 'depends_on:' docker-compose.yml
```

Expected: `OLD KEYS ABSENT`; `NO GENERIC URLS`; the `portfolio-frontend` dependency list uses `portfolio-*` names.

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml
git commit -m "fix(compose): rename local stack services to app-prefixed keys"
```

### Task 5: Point nginx at the app-prefixed aliases

**Files:**
- Modify: `frontend/nginx.conf` (lines 29, 38, 47, 52, 58, 71, 80, 90)

**Interfaces:**
- Consumes: Tasks 2–4 service keys.
- Produces: the static nginx config that works in UAT, prod, and local stacks; baked into the next frontend image build.

- [ ] **Step 1: Replace the upstream host names**

Apply exactly these replacements (host names only; paths, headers, and directives unchanged):

```nginx
proxy_pass http://backend:8080;              -> proxy_pass http://portfolio-backend:8080;      (4 occurrences: lines 29, 38, 47, 52)
proxy_pass http://market-data-service:8082;  -> proxy_pass http://portfolio-market-data:8082; (line 58)
proxy_pass http://ingestion-service:8081/;   -> proxy_pass http://portfolio-ingestion:8081/;  (line 71)
proxy_pass http://market-data-service:8082/; -> proxy_pass http://portfolio-market-data:8082/; (line 80)
proxy_pass http://strategy-service:8083/;    -> proxy_pass http://portfolio-strategy:8083/;   (line 90)
```

Do not change `location` blocks, `proxy_set_header` lines, websocket upgrade lines, timeouts, or `try_files`.

- [ ] **Step 2: Verify**

Run:
```bash
grep -n 'proxy_pass http://' frontend/nginx.conf
grep -nE 'http://(backend|ingestion-service|market-data-service|strategy-service):' frontend/nginx.conf || echo "NO GENERIC UPSTREAMS"
```

Expected: 8 lines, all host names prefixed with `portfolio-`; `NO GENERIC UPSTREAMS`.

- [ ] **Step 3: Commit**

```bash
git add frontend/nginx.conf
git commit -m "fix(frontend): proxy to app-prefixed service aliases"
```

### Task 6: Make first-deploy reconciliation deterministic

**Files:**
- Modify: `.github/workflows/deploy.yml:177`
- Modify: `.github/workflows/deploy-prod.yml:155`

**Interfaces:**
- Consumes: Tasks 2–5 (old service containers become orphans on the first renamed deploy).
- Produces: `docker compose up -d --remove-orphans` in both deploy workflows; the one-time host `down` remains in Tasks 14–15.

- [ ] **Step 1: Add `--remove-orphans` to the UAT deploy**

In `.github/workflows/deploy.yml`, change the remote script line:

```bash
            docker compose up -d
```

to:

```bash
            docker compose up -d --remove-orphans
```

- [ ] **Step 2: Add `--remove-orphans` to the prod deploy**

In `.github/workflows/deploy-prod.yml`, change the remote script line:

```bash
            docker compose up -d
```

to:

```bash
            docker compose up -d --remove-orphans
```

- [ ] **Step 3: Verify**

Run:
```bash
grep -n 'docker compose up -d' .github/workflows/deploy.yml .github/workflows/deploy-prod.yml
```

Expected: both lines read `docker compose up -d --remove-orphans`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/deploy.yml .github/workflows/deploy-prod.yml
git commit -m "ci(deploy): remove orphan containers during compose up"
```

### Task 7: Record the decision in ADR-0032

**Files:**
- Modify: `docs/adr.md` (append after ADR-0031, which ends at line 278)

**Interfaces:**
- Consumes: spec sections 3, 6.1, 7, and 8.3; Tasks 2–6.
- Produces: ADR-0032 documenting the service-alias change, the shared-network convention, and the workflow change (AGENTS.md docs contract).

- [ ] **Step 1: Confirm the next ADR number**

Run:
```bash
grep -n '^## ADR-' docs/adr.md | tail -1
```

Expected: `## ADR-0031: …`. If a later number exists (another PR landed first), use the next sequential number instead of 0032 throughout this task.

- [ ] **Step 2: Append the ADR entry**

Append the following entry verbatim (adjusting only the number if Step 1 found a later entry):

```markdown
## ADR-0032: Disambiguate Docker network aliases across shared environments

**Status:** Accepted
**Date:** 2026-09-19

**Context:** Docker Compose attaches each service key as a network alias. `pc` and `investclub` both deploy a service key `backend` onto the shared external networks `uat-internal-network` / `infra-uat-network` (and the prod equivalents). The `pc` frontend's nginx resolves `backend` to both backends and round-robins, so roughly half of all portfolio API calls were answered by investclub's backend, which correctly rejects the portfolio origin with `403 Invalid CORS request`. The deployed UI test suite (run 35420097091) showed ~50–60% authenticated failures/flakiness. Prod carries the identical latent collision.

**Decision:** Rename compose service keys in `deploy/uat/docker-compose.yml`, `deploy/prod/docker-compose.yml`, and the root `docker-compose.yml`: `backend` → `portfolio-backend`, `ingestion-service` → `portfolio-ingestion`, `market-data-service` → `portfolio-market-data`, `strategy-service` → `portfolio-strategy`, `broker-gateway-service` → `portfolio-broker-gateway`, `frontend` → `portfolio-frontend`. `frontend/nginx.conf` proxies to the app-prefixed names, so one static config is correct in every environment. Container names, host ports, compose project names, images, and network memberships are unchanged (invariants #2 and #3 preserved). Compose service keys (and therefore network aliases) must be app-prefixed and globally unique; never use generic names (`backend`, `frontend`, `api`, `db`) on shared external networks. `deploy.yml` and `deploy-prod.yml` run `docker compose up -d --remove-orphans` so service removals do not leave stale containers; the first renamed deploy additionally requires a one-time `docker compose down --remove-orphans` on each host because explicit `container_name` values cannot be adopted by a renamed service. Cross-service URLs use the full container name or the unique `portfolio-*` alias; Prometheus scrapes by host port and Grafana log alerts filter by unchanged container names, so monitoring is unaffected. Loki's `service` label (derived from the compose service key by Promtail) changes value from `backend` to `portfolio-backend`; no current query depends on the old value. The alias fix also restores investclub's frontend resolution (its nginx stops receiving `pc` backend IPs); sibling stacks on the shared networks are advised to adopt the same convention.

**Consequences:** Portfolio UAT and prod resolve every upstream unambiguously; intermittent `403 Invalid CORS request` from cross-app aliasing is eliminated. The first deploy after the rename requires a maintenance window and a `down`/`up` reconciliation; later deploys are unchanged. Historical documents that show the old service keys are append-only records and are not rewritten. This decision also corrects ADR-0031's consequence that credential rotation happens in Vault only — rotation spans both Vault and the database (see the incident record, spec §9.1).
```

- [ ] **Step 3: Verify**

Run:
```bash
grep -n '^## ADR-0032' docs/adr.md
grep -c '^## ADR-' docs/adr.md
```

Expected: the heading exists; the ADR count increased by exactly one.

- [ ] **Step 4: Commit**

```bash
git add docs/adr.md
git commit -m "docs(adr): record service alias disambiguation (ADR-0032)"
```

### Task 8: Reduce authenticated UAT artifact retention

**Files:**
- Modify: `.github/workflows/ui-tests-deployed.yml` (13 `retention-days: 14` steps)
- Modify: `.github/workflows/ui-visual-baseline-update.yml` (line 92)

**Interfaces:**
- Consumes: spec section 9.1 (security).
- Produces: 3-day retention for artifacts that can contain DOM snapshots of typed credentials.

- [ ] **Step 1: Apply the retention change**

Run:
```bash
sed -i 's/retention-days: 14/retention-days: 3/' .github/workflows/ui-tests-deployed.yml .github/workflows/ui-visual-baseline-update.yml
```

Do not change `.github/workflows/ui-tests-prod-smoke.yml` (retention 30; anonymous read-only checks, no credentials).

- [ ] **Step 2: Verify**

Run:
```bash
grep -c 'retention-days: 3' .github/workflows/ui-tests-deployed.yml
grep -c 'retention-days: 3' .github/workflows/ui-visual-baseline-update.yml
grep -rn 'retention-days: 14' .github/workflows/ || echo "NO 14-DAY RETENTION REMAINS"
```

Expected: `13`, `1`, `NO 14-DAY RETENTION REMAINS`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ui-tests-deployed.yml .github/workflows/ui-visual-baseline-update.yml
git commit -m "ci(ui-tests): shorten authenticated UAT artifact retention to 3 days"
```

---

## Phase 2 — Security (owner-run operations; no repo change except docs)

### Task 9: Rotate the exposed UAT test-account credential

**Files:**
- No repository file changes. Operational steps run against the UAT host and Vault. `V76__uat_seed_test_admin_user.sql` must **not** be edited (Flyway checksum).
- Related docs: Task 13 updates `docs/testing/operations.md`.

**Interfaces:**
- Consumes: spec section 9.1; the rotated password will be consumed by the next `ui-tests-deployed.yml` run via `secret/portfolio/uat`.
- Produces: a rotated `test-admin@nanobyte.ca` credential in the UAT database and Vault; a prod check for the same account.

- [ ] **Step 1: Owner decision gate**

Confirm with the owner before executing: (a) the new password is generated and stored only in Vault + the UAT database. (Prod disposition is closed — account deleted on 2026-09-19.) Do not proceed without confirmation.

- [ ] **Step 2: Confirm the completed prod remediation.**

Run on the prod host:

```bash
docker exec -i prod-postgres psql -U portfolio -d portfolio -tAc \
  "SELECT email, status, created_at FROM users WHERE email = 'test-admin@nanobyte.ca';"
```

Expected: **no rows** — this verifies the remediation executed on 2026-09-19 (row suspended, role links removed, deleted). Any row found here is a regression and is escalated immediately.

- [ ] **Step 3: Generate a new password and a matching Argon2id hash**

Run in a single SSH session on the UAT host (Docker and `uat-postgres` access are there; Steps 3–5 must share one session): `ssh portfolio-server`, then (the hash parameters must match the seeded format: `m=65536,t=3,p=4`, 16-byte salt, 32-byte hash):

```bash
NEW_PASSWORD="$(openssl rand -base64 24)"
HASH="$(printf '%s' "$NEW_PASSWORD" | docker run --rm -i python:3.12-alpine sh -c \
  'pip install -q argon2-cffi >/dev/null 2>&1; python -c "import sys; from argon2 import PasswordHasher; ph=PasswordHasher(time_cost=3,memory_cost=65536,parallelism=4,hash_len=32,salt_len=16); print(ph.hash(sys.stdin.read()))"')"
printf 'Hash generated (do not echo the password)\n'
```

The password stays in `NEW_PASSWORD` only; do not print it, and clear both variables at the end (`unset NEW_PASSWORD HASH`).

- [ ] **Step 4: Update the UAT database hash**

In the same SSH session (so `HASH` is still set; re-generate it if the session was lost; read the DB password from Vault if `psql` requires it; never hardcode):

```bash
docker exec -i uat-postgres psql -U portfolio -d portfolio -v ON_ERROR_STOP=1 \
  -c "UPDATE users SET password_hash = '$HASH', updated_at = NOW() WHERE email = 'test-admin@nanobyte.ca';"
```

Expected: `UPDATE 1`. If `0`, the account does not exist under that email — stop and re-check `APP_TEST_ADMIN_EMAIL` in Vault.

- [ ] **Step 5: Store the new password in Vault**

Authenticate the Vault CLI first (AppRole login or `vault login` at `https://vault.nanobyte.ca`), then:
```bash
vault kv patch secret/portfolio/uat \
  APP_TEST_ADMIN_EMAIL=test-admin@nanobyte.ca \
  APP_TEST_ADMIN_PASSWORD="$NEW_PASSWORD"
unset NEW_PASSWORD HASH
```

**Use `kv patch`, never `kv put`** — the secret carries other UAT keys (Postgres password, JWT signing key, Questrade tokens, gateway key, and more); `put` replaces the entire secret data and would wipe them, while `patch` merges.

If the Vault CLI is unavailable, use the Vault UI (`https://vault.nanobyte.ca`) at `secret/portfolio/uat` — the UI edits individual keys in place, so the other keys are preserved. Preserve the email value from the existing secret rather than retyping it.

- [ ] **Step 6: Verify at the next post-fix suite run**

The authenticated jobs in `ui-tests-deployed.yml` fetch `secret/portfolio/uat` at run time. After the fix deploy (Task 14), the automatic suite run is the verification: authenticated tests must log in successfully, proving DB hash, Vault value, and CI fetch agree. If they fail with `Invalid email or password`, re-check Steps 4–5 before any other diagnosis.

- [ ] **Step 7: No commit**

This task leaves no repo change. The rotation and prod disposition are recorded in the incident notes; `docs/testing/operations.md` gains the procedure in Task 13.

---

## Phase 3 — Test hygiene

### Task 10: Remove the vacuous passes

**Files:**
- Modify: `e2e/tests/regression/analytics-sectors.spec.ts` (tests 002/003)
- Modify: `e2e/tests/regression/reporting-contributions.spec.ts` (test 002)
- Modify: `e2e/tests/regression/portfolio-management.spec.ts` (test 001)

**Interfaces:**
- Consumes: spec section 9.2; `AnalyticsPage.tsx:40-45` renders the empty state whenever the in-memory analysis store is empty; `ContributionsChart.tsx:58` and `SectorChart.tsx:77`/`GeographyChart.tsx:85` use `.chart-container`; `LoginPage.tsx:65-67` has the heading `Your portfolio, one dashboard.`
- Produces: assertions that cannot pass on sidebar icons or the login heading.

- [ ] **Step 1: Replace the analytics chart tests**

In `e2e/tests/regression/analytics-sectors.spec.ts`, replace tests 002 and 003 with:

```ts
  test(scenario('ANALYTICS-SECT-002', 'sector exposure chart renders'), async ({ page }) => {
    test.skip(true, 'Sector chart requires an in-memory analysis run; AnalyticsPage.tsx:40 renders the empty state on fresh navigation (same premise as ANALYTICS-SECT-001). Locator is tightened to the AG Charts container for when the analysis flow becomes seedable.');
    const chart = page.locator('.chart-container canvas');
    await expect(chart).toBeVisible();
    // kept intentionally: assertion ready when the premise becomes testable
  });

  test(scenario('ANALYTICS-SECT-003', 'geography map is visible'), async ({ page }) => {
    test.skip(true, 'Geography chart requires an in-memory analysis run; AnalyticsPage.tsx:40 renders the empty state on fresh navigation (same premise as ANALYTICS-SECT-001). Locator is tightened to the AG Charts container for when the analysis flow becomes seedable.');
    const map = page.locator('.chart-container canvas');
    await expect(map).toBeVisible();
    // kept intentionally: assertion ready when the premise becomes testable
  });
```

- [ ] **Step 2: Tighten the reporting chart test**

In `e2e/tests/regression/reporting-contributions.spec.ts`, replace the locator in test 002:

```ts
    const chart = page.locator('canvas, svg, [data-testid*="chart"], [data-testid*="contribut"], .recharts-wrapper, .chart-container');
```

with:

```ts
    const chart = page.locator('.chart-container canvas').first();
```

Leave the rest of the test unchanged.

- [ ] **Step 3: Harden the portfolio heading assertion**

In `e2e/tests/regression/portfolio-management.spec.ts`, replace the assertion in test 001:

```ts
    await expect(page.locator('h1', { hasText: 'Portfolio' })).toBeVisible();
```

with:

```ts
    await expect(page.getByRole('heading', { level: 1, name: 'Portfolio', exact: true })).toBeVisible();
```

- [ ] **Step 4: Verify the edits are syntactically valid**

Run: `cd e2e && npx tsc --noEmit` (toolchain sanity only — the tsconfig does not include `tests/**`, so this does not type-check the edited specs). Additionally parse-check the edited files: `npx playwright test --list tests/regression/analytics-sectors.spec.ts tests/regression/reporting-contributions.spec.ts tests/regression/portfolio-management.spec.ts`. Expected: tests list without parse errors.

- [ ] **Step 5: Commit**

```bash
git add e2e/tests/regression/analytics-sectors.spec.ts e2e/tests/regression/reporting-contributions.spec.ts e2e/tests/regression/portfolio-management.spec.ts
git commit -m "test(e2e): tighten chart and heading assertions (remove vacuous passes)"
```

### Task 11: Adjudicate the non-auth failures

**Files:**
- Modify: `e2e/tests/regression/dashboard-overview.spec.ts` (test 004)
- Modify: `e2e/tests/regression/wheel-calendar.spec.ts` (tests 002/004)

**Interfaces:**
- Consumes: spec section 9.2; `AccountNavBar.css:190-196` hides `.account-nav__pills` below 769 px and mobile uses `.account-nav__trigger`; `WheelCalendarGrid.tsx:70` renders `<table class="wcg-table">`; `WheelTopTickers.tsx:113` renders the `Add Ticker` button.
- Produces: correct scoping for the desktop-only account switcher and calibrated wheel locators.

- [ ] **Step 1: Scope the account-switching test to desktop**

In `e2e/tests/regression/dashboard-overview.spec.ts`, at the start of test `DASH-OVERVIEW-004` (before the first locator), add:

```ts
    const width = page.viewportSize()?.width ?? 0;
    test.skip(width > 0 && width < 769, 'The account pill switcher is desktop-only (AccountNavBar.css:190-196 hides .account-nav__pills below 769px); mobile uses the account sheet');
```

Leave the desktop assertions unchanged.

- [ ] **Step 2: Calibrate the wheel calendar locator**

In `e2e/tests/regression/wheel-calendar.spec.ts`, in test `WHEEL-CAL-002`, replace:

```ts
    const grid = page.getByRole('table');
```

with:

```ts
    const grid = page.locator('.wcg-table');
```

- [ ] **Step 3: Calibrate the top-tickers interaction**

In the same file, in test `WHEEL-CAL-004`, replace:

```ts
    const tickersBar = page.getByRole('button', { name: 'Add Ticker' });
    await expect(tickersBar).toBeVisible();
```

with:

```ts
    const tickersBar = page.getByRole('button', { name: 'Add Ticker' });
    await tickersBar.scrollIntoViewIfNeeded();
    await expect(tickersBar).toBeVisible();
```

- [ ] **Step 4: Verify the edits are syntactically valid**

Run: `cd e2e && npx tsc --noEmit` (toolchain sanity only — the tsconfig does not include `tests/**`, so this does not type-check the edited specs). Additionally parse-check the edited files: `npx playwright test --list tests/regression/dashboard-overview.spec.ts tests/regression/wheel-calendar.spec.ts`. Expected: tests list without parse errors.

- [ ] **Step 5: Commit**

```bash
git add e2e/tests/regression/dashboard-overview.spec.ts e2e/tests/regression/wheel-calendar.spec.ts
git commit -m "test(e2e): scope desktop-only account switching and calibrate wheel locators"
```

- [ ] **Step 6: Evidence-gated follow-up after the post-deploy run**

After Task 14's suite run, inspect any remaining `WHEEL-CAL-002`/`WHEEL-CAL-004` failures:

```bash
gh run list --workflow=ui-tests-deployed.yml --limit 5
gh run download <run-id> -n regression-results -D /tmp/opencode/ui-regression
```

Decision rule: if the control is absent on mobile by design, convert the test to a sanctioned `test.skip(true, '<premise reason with evidence>')`; if it is present but obscured or late-rendering, keep the tightened locator and add a bounded wait (no fixed sleeps outside `negative-wait.ts`). Commit the follow-up as `test(e2e): adjudicate wheel calendar failures`.

### Task 12: Adjudicate `DASH-OVERVIEW-002` (dashboard vs positions)

**Files:**
- Modify: `e2e/tests/regression/dashboard-overview.spec.ts` (test 002) — only after the owner decision
- Possibly create: a bug issue via `gh issue create`

**Interfaces:**
- Consumes: spec section 9.2; the current sanctioned skip in `dashboard-overview.spec.ts:24-26`.
- Produces: either a re-enabled scenario or a skip that references a filed issue.

- [ ] **Step 1: Reproduce the discrepancy on UAT**

Log in to `https://uatportfolio.nanobyte.ca` with the UAT test admin and compare the dashboard's Positions section (C$ 0 / "No positions") with `/brokers/positions` (32 positions). Confirm the discrepancy still exists after the alias fix. No local runs; use the deployed app.

- [ ] **Step 2: Owner decision gate**

Ask the owner to classify the result:

- **App data bug:** file it and keep the skip with the issue link:

```bash
gh issue create --title "Dashboard positions empty while /brokers/positions returns positions" \
  --body "Observed on UAT: dashboard shows C$ 0 / No positions while /brokers/positions returns 32 positions. Discovered during service-alias incident 2026-09-19. Scenario: DASH-OVERVIEW-002."
```

Then update the skip message in test 002 to reference the issue number.

- **Alias-fix artifact:** if the discrepancy disappears after the fix, replace the skip with a real assertion on the dashboard positions grid:

```ts
    const grid = page.locator('.ag-root-wrapper, [role="grid"]').first();
    await expect(grid).toBeVisible();
```

- [ ] **Step 3: Commit**

```bash
git add e2e/tests/regression/dashboard-overview.spec.ts
git commit -m "test(e2e): adjudicate DASH-OVERVIEW-002 after alias fix"
```

If the owner decides to keep the skip unchanged and file the issue externally, this commit still updates the skip text with the issue reference.

---

## Phase 4 — Documentation

### Task 13: Document the diagnostic procedure and align service references

**Files:**
- Modify: `docs/testing/operations.md` (add a section under `## Secrets` or after `## Environment Safety`)
- Modify: `docs/runbooks/google-oauth-provider-unavailable.md:98,152`
- Modify: `README.md:96-100` (and add a service-key note under the Services & Ports table)
- Modify: `docs/reference/infrastructure.md:73-76,82,97,250,269-274`
- Modify: `docs/reference/frontend-map.md:990-992`
- Modify: `docs/reference/configurations.md:182,396`
- Modify: `docs/reference/backend-services.md:531`
- Modify: `docs/reference/database-schema.md:1577`
- Modify: `docs/reference/INDEX.md:52,94`
- Modify: `AGENTS.md` (invariant #2)

**Interfaces:**
- Consumes: Tasks 2–9 (new names and rotation procedure).
- Produces: the operational runbook for this class of incident and repo-wide reference consistency.

- [ ] **Step 1: Add the diagnostic section to `docs/testing/operations.md`**

Insert after the `## Environment Safety` section:

````markdown
## Diagnosing Service Alias Collisions

Symptom: intermittent `403 Invalid CORS request` (or requests answered by a different
application) on a deployed environment, roughly 50% of requests, varying per call.

1. Inspect Docker network aliases on the host. Every `pc` container must carry only
   `uat-portfolio-*`/`prod-portfolio-*` and `portfolio-*` aliases:

   ```bash
   for c in uat-portfolio-backend uat-portfolio-ingestion uat-portfolio-market-data \
            uat-portfolio-strategy uat-portfolio-broker-gateway uat-portfolio-frontend; do
     docker inspect "$c" --format '{{.Name}}{{range $k,$v := .NetworkSettings.Networks}} | {{$k}}=[{{join $v.Aliases " "}}]{{end}}'
   done
   ```

   Any generic alias (`backend`, `frontend`, `ingestion-service`, …) is a defect:
   another app on the same shared network may own it too.

2. Probe through the frontend nginx repeatedly; after a correct fix every response is
   JSON and none is `Invalid CORS request`:

   ```bash
   for i in $(seq 1 8); do
     curl -s -X POST http://localhost:20000/auth/login \
       -H "Origin: https://uatportfolio.nanobyte.ca" \
       -H "Content-Type: application/json" -d '{}' | head -c 42; echo
   done
   ```

   For prod use `http://localhost:10000/auth/login` and
   `Origin: https://portfolio.nanobyte.ca`.

3. Remediation: compose service keys (which become network aliases) must be
   app-prefixed and unique per shared network. Rename the offending service keys,
   update `frontend/nginx.conf`, rebuild the frontend image, and reconcile the deploy
   with `docker compose down --remove-orphans` once before `up -d`
   (`--remove-orphans` is now permanent in both deploy workflows).

4. The shared-network convention and the ADR for this fix: ADR-0032 and
   `docs/superpowers/specs/2026-09-19-service-alias-collision-design.md`.
````

- [ ] **Step 2: Add the credential-rotation note to the `## Secrets` section**

Append to the `APP_TEST_ADMIN_*` bullet list:

Correct the stale bullet that claims "No committed seed accounts": that is false given the committed V76 migration. Update that bullet to state the seed migration exists in git history (`V76__uat_seed_test_admin_user.sql`) and the account was removed from prod on 2026-09-19 (rotation pending).

```markdown
- Rotation spans **both** stores: the password hash in the `users` table of the UAT
  database and `APP_TEST_ADMIN_PASSWORD` in Vault must be changed together (there is
  no change-password endpoint). See ADR-0032's incident spec section 9.1 for the
  procedure. The seeded credential is also present in the committed
  `V76__uat_seed_test_admin_user.sql`; do not edit that applied migration.
- Playwright `error-context.md` failure captures include DOM snapshots with typed
  input values and cannot be masked by screenshot `mask` options. This is why
  authenticated UAT artifact retention is 3 days.
```

- [ ] **Step 3: Fix the OAuth runbook**

In `docs/runbooks/google-oauth-provider-unavailable.md`:
- Line 98: change `proxy_pass http://backend:8080;` to `proxy_pass http://portfolio-backend:8080;`
- Line 152: change `exec prod-backend` to `exec prod-portfolio-backend` (the previous value was not a valid container name).

- [ ] **Step 4: Update README**

In `README.md`:
- Lines 98-100: change the exec commands to `docker compose exec portfolio-backend ./gradlew test`, `... portfolio-market-data ./gradlew test`, `... portfolio-strategy ./gradlew test`.
- Under the Services & Ports table (after line 44, the blank line following the Services & Ports table), add: `Compose service keys are app-prefixed (`portfolio-backend`, `portfolio-ingestion`, `portfolio-market-data`, `portfolio-strategy`, `portfolio-broker-gateway`, `portfolio-frontend`) so Docker network aliases never collide with sibling apps on shared networks.`

- [ ] **Step 5: Align the reference docs**

In `docs/reference/infrastructure.md`:
- Lines 72-77: rename all six first-column entries (`backend`→`portfolio-backend`, `ingestion-service`→`portfolio-ingestion`, `market-data-service`→`portfolio-market-data`, `strategy-service`→`portfolio-strategy`, `broker-gateway-service`→`portfolio-broker-gateway`, `frontend`→`portfolio-frontend`).
- Line 82: `Frontend depends on portfolio-backend, portfolio-ingestion, portfolio-market-data, portfolio-strategy`.
- Line 97: rename the two service references to `portfolio-market-data` and `portfolio-broker-gateway`.
- Lines 250, 269-277: change `docker compose exec backend` to `docker compose exec portfolio-backend` and `docker compose logs -f backend` to `docker compose logs -f portfolio-backend`.
- Line 471: rename the bare `market-data-service`/`broker-gateway-service` prose references.

In `docs/reference/frontend-map.md` lines 990-992: `http://portfolio-market-data:8082`, `ws://portfolio-market-data:8082`, `http://portfolio-strategy:8083`.

In `docs/reference/configurations.md` lines 182 and 396: `http://portfolio-broker-gateway:8084`.

In `docs/reference/backend-services.md` line 531: `http://portfolio-broker-gateway:8084`.

In `docs/reference/database-schema.md` line 1577: `docker compose exec portfolio-backend ./gradlew test`.

In `docs/reference/INDEX.md` lines 52 and 94: `docker compose exec portfolio-backend ./gradlew test`.

Do not touch `docs/reference/configurations.md:422-429` (`spring.application.name`) — that is the Spring application name, not a compose key.

- [ ] **Step 6: Extend AGENTS.md invariant #2**

Append to invariant #2 (after the container-naming sentence):

```markdown
Compose service keys are app-prefixed (`portfolio-backend`, `portfolio-ingestion`, `portfolio-market-data`, `portfolio-strategy`, `portfolio-broker-gateway`, `portfolio-frontend`) and never generic (`backend`, `frontend`), because service keys become Docker network aliases and generic aliases collide across apps on shared external networks (ADR-0032).
```

- [ ] **Step 7: Verify no current (non-historical) doc references the old compose keys**

Run:

```bash
grep -rn 'docker compose exec backend\|http://backend:8080\|http://broker-gateway-service\|http://market-data-service\|http://ingestion-service\|http://strategy-service' \
  README.md AGENTS.md docs/testing docs/reference docs/runbooks || echo "NO STALE CURRENT REFERENCES"
```

Expected: `NO STALE CURRENT REFERENCES`. Historical hits under `docs/superpowers/**` are expected and must not be edited.

Also run: `grep -rnE '\b(ingestion-service|market-data-service|strategy-service|broker-gateway-service)\b|compose (exec|logs) (backend|frontend)\b' README.md AGENTS.md docs/testing docs/reference docs/runbooks` and adjudicate every hit (rename or justify). Expected: no unadjudicated hits.

- [ ] **Step 8: Commit**

```bash
git add docs/testing/operations.md docs/runbooks/google-oauth-provider-unavailable.md README.md \
  docs/reference/infrastructure.md docs/reference/frontend-map.md docs/reference/configurations.md \
  docs/reference/backend-services.md docs/reference/database-schema.md docs/reference/INDEX.md AGENTS.md
git commit -m "docs: add service alias diagnostic and align app-prefixed service references"
```

---

## Phase 5 — Verification

### Task 14: Verify UAT on the live stack

**Files:** none (deployment and probing only)

**Interfaces:**
- Consumes: all prior tasks; the owner's Vault rotation (Task 9).
- Produces: incident-closing evidence for UAT; the go/no-go for prod.

- [ ] **Step 1: Reconcile only if the first renamed deploy fails.**

Do **not** proactively run `down` after merging — the automatic deploy may reconcile cleanly via `--remove-orphans`. Watch it first (`gh run list --workflow=deploy.yml --limit 1`). Only if it fails with a container-name conflict, run on the UAT host:

```bash
ssh portfolio-server
cd /opt/portfolio/uat
docker compose down --remove-orphans
```

(the compose file on the host is now the new one; `down --remove-orphans` still removes the old-key containers because they carry the same `portfolio-uat` project label; external networks persist), then re-dispatch:

```bash
gh workflow run deploy.yml -f environment=uat -f tag=main-<sha>
```

- [ ] **Step 2: Wait for the deploy and the automatic UI suite**

```bash
gh run list --workflow=ui-tests-deployed.yml --limit 3
```

Expected: a run triggered by the successful Deploy, in progress or complete.

- [ ] **Step 3: Inspect aliases (root-cause closure)**

Run on the UAT host:

```bash
for c in uat-portfolio-backend uat-portfolio-ingestion uat-portfolio-market-data \
         uat-portfolio-strategy uat-portfolio-broker-gateway uat-portfolio-frontend; do
  docker inspect "$c" --format '{{.Name}}{{range $k,$v := .NetworkSettings.Networks}} | {{$k}}=[{{join $v.Aliases " "}}]{{end}}'
done
docker inspect uat-investclub-backend --format '{{.Name}}{{range $k,$v := .NetworkSettings.Networks}} | {{$k}}=[{{join $v.Aliases " "}}]{{end}}'
```

Expected: no `pc` container shows `backend` or `frontend`; each shows its container name plus `portfolio-*`; the investclub container still shows `backend` (its own, now uncontested).

- [ ] **Step 4: Run the login alternation probe**

Run on the UAT host (exact command from the incident report):

```bash
for i in $(seq 1 8); do curl -s -X POST http://localhost:20000/auth/login \
  -H "Origin: https://uatportfolio.nanobyte.ca" \
  -H "Content-Type: application/json" -d '{}' | head -c 42; echo; done
```

Expected: 8/8 lines are the portfolio backend's JSON body (e.g. `{"detail":"Invalid email or password"}` or another JSON error); **no line contains `Invalid CORS request`**.

Also probe the public URL to prove the tunnel end:

```bash
for i in $(seq 1 8); do curl -s -X POST https://uatportfolio.nanobyte.ca/auth/login \
  -H "Origin: https://uatportfolio.nanobyte.ca" \
  -H "Content-Type: application/json" -d '{}' | head -c 42; echo; done
```

- [ ] **Step 5: Probe the other proxy routes**

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:20000/ingestion-api/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:20000/market-data-api/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:20000/strategy-api/actuator/health
```

Expected: `200` from all three, proving each upstream resolves to pc's own service.

- [ ] **Step 6: Probe investclub (side-effect verification)**

```bash
for i in $(seq 1 8); do curl -s -X POST https://uatinvestclub.nanobyte.ca/auth/login \
  -H "Origin: https://uatinvestclub.nanobyte.ca" \
  -H "Content-Type: application/json" -d '{}' | head -c 42; echo; done
```

Expected: 8/8 responses come from investclub's backend (its own JSON/error body); no pc-origin rejection and no alternation.

- [ ] **Step 7: Read the suite result**

```bash
gh run view <ui-tests-run-id>
```

Expected: authenticated failures caused by CORS are gone. Remaining results must be only the items Task 10-12 addressed (sanctioned skips, tightened assertions) and any owner-decision items; any new failure is investigated before prod.

- [ ] **Step 8: Record the evidence**

Paste the outputs of Steps 3–7 into the PR/incident notes. No commit.

### Task 15: Deploy and verify prod

**Files:** none (deployment and probing only)

**Interfaces:**
- Consumes: Task 14 passing; spec section 8.3.
- Produces: prod alias-collision closure.

- [ ] **Step 1: Owner go/no-go**

Confirm UAT evidence is clean, then proceed. Prod deploy is manual with `environment: prod` protection.

- [ ] **Step 2: Reconcile the old prod service identities**

Run on the prod host:

```bash
ssh portfolio-server
cd /opt/portfolio/prod
docker compose down --remove-orphans
```

- [ ] **Step 3: Dispatch the prod deploy**

From the operator machine:

```bash
gh workflow run deploy-prod.yml -f tag=main-<sha>
```

Approve the `prod` environment when prompted if protection rules require it.

- [ ] **Step 4: Inspect prod aliases**

Run on the prod host:

```bash
for c in prod-portfolio-backend prod-portfolio-ingestion prod-portfolio-market-data \
         prod-portfolio-strategy prod-portfolio-broker-gateway prod-portfolio-frontend; do
  docker inspect "$c" --format '{{.Name}}{{range $k,$v := .NetworkSettings.Networks}} | {{$k}}=[{{join $v.Aliases " "}}]{{end}}'
done
```

Expected: no generic aliases; each container shows only its container name plus `portfolio-*`.

- [ ] **Step 5: Run the prod login probe**

Run on the prod host:

```bash
for i in $(seq 1 8); do curl -s -X POST http://localhost:10000/auth/login \
  -H "Origin: https://portfolio.nanobyte.ca" \
  -H "Content-Type: application/json" -d '{}' | head -c 42; echo; done
```

Expected: 8/8 JSON bodies; no `Invalid CORS request`.

- [ ] **Step 6: Run the production smoke suite (manual dispatch).**

The smoke workflow is `workflow_dispatch`-only; dispatch it after the prod deploy and confirm green:

```bash
gh workflow run ui-tests-prod-smoke.yml
gh run list --workflow=ui-tests-prod-smoke.yml --limit 3
```

Note: its container pin (`v1.52.0-noble`) predates the lockfile's `@playwright/test` 1.60.0 — if the run fails with a browser-executable error, align the container tag with the lockfile (`v1.60.0-noble`) before treating the result as evidence.

- [ ] **Step 7: Rollback plan (only if verification fails)**

A previous image tag does not revert the compose rename or the nginx config. Roll back by:
1. `git revert` the compose/nginx/ADR/workflow commits on `main`, or manually restore the previous compose file to `/opt/portfolio/prod/docker-compose.yml`.
2. `docker compose down --remove-orphans` on the host.
3. Dispatch `deploy-prod.yml` with the previous `main-<sha>` tag.

---

## Plan Self-Review

- **Spec coverage:** R1/R2/R3 → Tasks 2–5; R4 → Tasks 2–5 (host-name-only edits); R5 → Tasks 8–9 and the operations.md note in Task 13; R6 → Tasks 10–12; R7 → Tasks 14–15. ADR/spec references in Tasks 7 and 13.
- **Placeholders:** none — every edit shows the exact before/after text and every command shows the expected result.
- **Type/name consistency:** the six new service keys (`portfolio-backend`, `portfolio-ingestion`, `portfolio-market-data`, `portfolio-strategy`, `portfolio-broker-gateway`, `portfolio-frontend`) are identical across Tasks 2, 3, 4, 5, 7, 13, 14, and 15. Container names are never changed.
- **Known limitation:** Docker was unavailable during authoring; if `docker compose up -d --remove-orphans` (Task 6) reconciles the rename cleanly without the Task 14/15 `down` step, that is an acceptable simplification — the conservative path remains valid.
