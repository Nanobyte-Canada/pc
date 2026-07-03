# Multi-Repo SDLC Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable multiple consumer repos to use the SDLC pipeline by moving from a hardcoded PROJECT_ID to KV-based lookup and restructuring Vault secrets into three tiers.

**Architecture:** Single Worker handles all repos via KV lookup (`repo:<full-name>` → `project_id`). sdlc-agent.yml fetches secrets from three Vault tiers (org-level → app-level → env-specific). Each consumer repo identifies itself via a single `APP_NAME` GitHub variable.

**Tech Stack:** TypeScript (Cloudflare Worker), Bash (scripts), YAML (GitHub Actions), Vitest (Worker tests)

## Global Constraints

- Worker tests: `cd sdlc/worker && npx vitest run` — must pass
- KV namespace `DELIVERY_KV` is already bound in wrangler.toml
- sdlc-agent.yml fetches Vault secrets using AppRole auth (VAULT_ROLE_ID + VAULT_SECRET_ID)
- The Worker is deployed as `sdlc-webhook-receiver` via `wrangler deploy`
- Two repos: `Nanobyte-Canada/pc` (workflows) and `Nanobyte-Canada/nanobyte-services` (Worker + agents)

---

### Task 1: Worker — Replace hardcoded PROJECT_ID with KV lookup

**Files:**
- Modify: `sdlc/worker/src/index.ts:329-334` (handlePullRequestMerged)

**Interfaces:**
- Consumes: `env.DELIVERY_KV` (existing KV binding), `body.repository.full_name` (webhook payload)
- Produces: `handlePullRequestMerged` looks up project ID from KV key `repo:<full_name>`

- [ ] **Step 1: Modify handlePullRequestMerged to use KV lookup**

In `sdlc/worker/src/index.ts`, replace:

```typescript
  // Move card to "Testing" lane
  const projectId = (env as any).PROJECT_ID;
  if (!projectId) {
    console.error('PROJECT_ID not configured — cannot move card');
    return new Response('OK (no project id)', { status: 200 });
  }
```

With:

```typescript
  // Look up this repo's project board ID from KV
  const repoFullName: string = body.repository?.full_name ?? '';
  const kvKey = `repo:${repoFullName}`;
  let projectId: string | undefined;

  try {
    const kvValue = await env.DELIVERY_KV.get(kvKey, 'json') as { project_id?: string } | null;
    projectId = kvValue?.project_id;
  } catch (err) {
    console.error(`KV lookup error for ${kvKey}: ${err}`);
  }

  if (!projectId) {
    console.error(`No project ID configured for ${repoFullName} — cannot move card`);
    return new Response('OK (no project id)', { status: 200 });
  }
```

- [ ] **Step 2: Commit**

```bash
git add sdlc/worker/src/index.ts
git commit -m "feat(worker): replace hardcoded PROJECT_ID with KV lookup for multi-repo support"
```

---

### Task 2: Worker Tests — Update handlePullRequestMerged tests for KV lookup

**Files:**
- Modify: `sdlc/worker/test/index.test.ts:220-227` and `sdlc/worker/test/index.test.ts:266-271` (update env objects in tests)

**Interfaces:**
- Consumes: `handlePullRequestMerged` now reads from `env.DELIVERY_KV` instead of `env.PROJECT_ID`
- Produces: Updated test env objects include `PROJECT_ID` in KV mock

The `createMockKV()` function already exists in the test file. The env objects in each test case need to include `PROJECT_ID` in the mocked KV store instead of as a direct env property.

- [ ] **Step 1: Update test env objects to include PROJECT_ID in KV**

Replace each test's env that has `PROJECT_ID: 'PVT_fake'` with KV-stored project ID.

In the three test cases that currently use `PROJECT_ID: 'PVT_fake'`, change:

```typescript
const env = {
  WEBHOOK_SECRET: 'secret',
  GITHUB_TOKEN: 'token',
  DELIVERY_KV: createMockKV(),
  PROJECT_ID: 'PVT_fake',
};
```

To:

```typescript
const mockKV = createMockKV();
// Pre-seed the KV store with the repo's project ID
mockKV.put('repo:Nanobyte-Canada/pc', JSON.stringify({ project_id: 'PVT_fake' }));

const env = {
  WEBHOOK_SECRET: 'secret',
  GITHUB_TOKEN: 'token',
  DELIVERY_KV: mockKV,
};
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd sdlc/worker && npx vitest run test/index.test.ts`
Expected: PASS — all 14 tests pass.

- [ ] **Step 3: Run full test suite**

Run: `cd sdlc/worker && npx vitest run`
Expected: PASS — all 21 tests pass (dedupe, verify, index).

- [ ] **Step 4: Commit**

```bash
git add sdlc/worker/test/index.test.ts
git commit -m "test(worker): update handlePullRequestMerged tests to use KV-stored PROJECT_ID"
```

---

### Task 3: wrangler.toml — Remove PROJECT_ID from vars

**Files:**
- Modify: `sdlc/worker/wrangler.toml:11-12`

**Interfaces:**
- Consumes: nothing (removing the hardcoded fallback)
- Produces: Cleaner wrangler config — PROJECT_ID is no longer needed as an env var

- [ ] **Step 1: Remove PROJECT_ID from [vars]**

In `sdlc/worker/wrangler.toml`, remove lines 11-12:

```toml
[vars]
PROJECT_ID = "PVT_kwDOEbgU584Bbxz4"  # Nanobyte SDLC board node ID
```

The file should look like:

```toml
name = "sdlc-webhook-receiver"
main = "src/index.ts"
compatibility_date = "2024-06-01"

# KV namespace for webhook deduplication
[[kv_namespaces]]
binding = "DELIVERY_KV"
id = "c661be33bc384f74999213a2f4e40024"

# Secrets (set via: wrangler secret put WEBHOOK_SECRET, wrangler secret put GITHUB_TOKEN)
# WEBHOOK_SECRET - GitHub App webhook secret
# GITHUB_TOKEN - PAT with repo + project scopes (for GraphQL + label API calls)
```

- [ ] **Step 2: Commit**

```bash
git add sdlc/worker/wrangler.toml
git commit -m "chore(worker): remove hardcoded PROJECT_ID from wrangler.toml (now in KV)"
```

---

### Task 4: sdlc-agent.yml — Three-tier Vault secret fetch

**Files:**
- Modify: `.github/workflows/sdlc-agent.yml:56-81` (Vault fetch step)

**Interfaces:**
- Consumes: `vars.APP_NAME` (GitHub variable, e.g., `portfolio`)
- Produces: Secrets from all three Vault tiers written to GITHUB_ENV

- [ ] **Step 1: Replace the single Vault fetch with three-tier fetch**

In `.github/workflows/sdlc-agent.yml`, replace the current "Fetch secrets from Vault" step (lines 56-81):

```yaml
      - name: Fetch secrets from Vault
        run: |
          echo "Authenticating to Vault..."
          VAULT_TOKEN=$(curl -sf \
            --request POST \
            --data "{\"role_id\": \"${{ secrets.VAULT_ROLE_ID }}\", \"secret_id\": \"${{ secrets.VAULT_SECRET_ID }}\"}" \
            "${VAULT_ADDR}/v1/auth/approle/login" | jq -r '.auth.client_token')

          if [ -z "$VAULT_TOKEN" ] || [ "$VAULT_TOKEN" = "null" ]; then
            echo "::error::Failed to authenticate to Vault"
            exit 1
          fi
          echo "Vault authentication successful"
```

With:

```yaml
      - name: Fetch secrets from Vault
        run: |
          echo "Authenticating to Vault..."
          VAULT_TOKEN=$(curl -sf \
            --request POST \
            --data "{\"role_id\": \"${{ secrets.VAULT_ROLE_ID }}\", \"secret_id\": \"${{ secrets.VAULT_SECRET_ID }}\"}" \
            "${VAULT_ADDR}/v1/auth/approle/login" | jq -r '.auth.client_token')

          if [ -z "$VAULT_TOKEN" ] || [ "$VAULT_TOKEN" = "null" ]; then
            echo "::error::Failed to authenticate to Vault"
            exit 1
          fi
          echo "Vault authentication successful"

          APP_NAME="${{ vars.APP_NAME }}"
          if [ -z "$APP_NAME" ]; then
            echo "::error::APP_NAME repository variable is not set — cannot fetch SDLC secrets"
            exit 1
          fi

          echo "Fetching SDLC pipeline secrets..."

          # Tier 1: Org-level shared secrets (same for all repos)
          echo "--- Tier 1: sdlc/common ---"
          JSON=$(curl -sf \
            --header "X-Vault-Token: ${VAULT_TOKEN}" \
            "${VAULT_ADDR}/v1/secret/data/sdlc/common" | jq -r '.data.data // "{}"')
          if [ "$JSON" != "{}" ]; then
            echo "$JSON" | jq -r 'to_entries[] | "\(.key)=\(.value)"' >> "$GITHUB_ENV"
            echo "Exported $(echo "$JSON" | jq -r 'keys | length') keys from sdlc/common"
          else
            echo "No secrets found at sdlc/common — skipping"
          fi

          # Tier 2: App-level shared secrets (repo-specific, shared across envs)
          echo "--- Tier 2: ${APP_NAME}/common ---"
          JSON=$(curl -sf \
            --header "X-Vault-Token: ${VAULT_TOKEN}" \
            "${VAULT_ADDR}/v1/secret/data/${APP_NAME}/common" | jq -r '.data.data // "{}"')
          if [ "$JSON" != "{}" ]; then
            echo "$JSON" | jq -r 'to_entries[] | "\(.key)=\(.value)"' >> "$GITHUB_ENV"
            echo "Exported $(echo "$JSON" | jq -r 'keys | length') keys from ${APP_NAME}/common"
          else
            echo "No secrets found at ${APP_NAME}/common — skipping"
          fi

          # Tier 3: Env-specific secrets (only needed for tester/deployer phases)
          PHASE="${{ steps.params.outputs.phase }}"
          if [ "$PHASE" = "testing" ] || [ "$PHASE" = "publish" ]; then
            if [ "$PHASE" = "publish" ]; then
              VAULT_ENV="prod"
            else
              VAULT_ENV="uat"
            fi
            echo "--- Tier 3: ${APP_NAME}/${VAULT_ENV} ---"
            JSON=$(curl -sf \
              --header "X-Vault-Token: ${VAULT_TOKEN}" \
              "${VAULT_ADDR}/v1/secret/data/${APP_NAME}/${VAULT_ENV}" | jq -r '.data.data // "{}"')
            if [ "$JSON" != "{}" ]; then
              echo "$JSON" | jq -r 'to_entries[] | "\(.key)=\(.value)"' >> "$GITHUB_ENV"
              echo "Exported $(echo "$JSON" | jq -r 'keys | length') keys from ${APP_NAME}/${VAULT_ENV}"
            else
              echo "No secrets found at ${APP_NAME}/${VAULT_ENV} — skipping"
            fi
          else
            echo "Phase is '$PHASE' — skipping env-specific secrets"
          fi
```

- [ ] **Step 2: Add APP_NAME variable validation**

Add a check at the top of the job to ensure `APP_NAME` is set:

In the `if` guard (line 45-49), or add a separate validation step before the Vault fetch. The simplest approach is to validate inside the Vault fetch step (already included in Step 1 above — `if [ -z "$APP_NAME" ]; then ... exit 1`).

- [ ] **Step 3: Validate YAML syntax**

Run:
```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/sdlc-agent.yml'))" && echo "YAML valid"
```
Expected: `YAML valid`

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/sdlc-agent.yml
git commit -m "feat(workflow): three-tier Vault secret fetch for multi-repo SDLC support"
```

---

---

### Task 5: Create Vault three-tier structure

**Files:**
- Add: `scripts/setup-sdlc-vault.sh` (in pc repo)
- Operation: Run against live Vault to create `sdlc/common`, `portfolio/common`, `portfolio/uat`, `portfolio/prod`

- [x] **Step 1: Create the setup script**
  Script: `pc/scripts/setup-sdlc-vault.sh`
  Reads existing `sdlc/pc` secrets, splits into three tiers. For env paths (`portfolio/uat`, `portfolio/prod`), merges with existing deploy secrets (doesn't overwrite).

- [ ] **Step 2: Run the script**
  Requires Vault AppRole credentials (`VAULT_ROLE_ID` + `VAULT_SECRET_ID` env vars).
  Run from the self-hosted runner (which has these configured) or any machine with Vault access:
  ```bash
  export VAULT_ROLE_ID="..." VAULT_SECRET_ID="..."
  ./scripts/setup-sdlc-vault.sh
  ```
  Or directly with a token:
  ```bash
  VAULT_TOKEN="hvs.xxx" ./scripts/setup-sdlc-vault.sh
  ```

---

### Task 6: Write KV entry for pc repo

- [x] **Step 1: Create the setup script**
  Script: `pc/scripts/setup-sdlc-kv.sh`
  Writes `repo:Nanobyte-Canada/pc` → `{"project_id":"PVT_kwDOEbgU584Bbxz4"}` to the `DELIVERY_KV` namespace.

- [ ] **Step 2: Run the script**
  Requires `wrangler` authenticated (Cloudflare API token with KV write permissions).
  Run from the `nanobyte-services/sdlc/worker` directory:
  ```bash
  ./scripts/setup-sdlc-kv.sh
  ```
  Or manually:
  ```bash
  cd nanobyte-services/sdlc/worker
  npx wrangler kv key put \
    "repo:Nanobyte-Canada/pc" \
    '{"project_id":"PVT_kwDOEbgU584Bbxz4"}' \
    --binding=DELIVERY_KV --remote
  ```
  No Worker redeploy needed — KV is live-read on every invocation.

---

## Verification

After all tasks are complete:

1. Run Worker tests: `cd sdlc/worker && npx vitest run` — expect 21/21 pass
2. Validate YAML: all workflow files parse correctly
3. Verify no remaining references to `env.PROJECT_ID` or `(env as any).PROJECT_ID` in index.ts
