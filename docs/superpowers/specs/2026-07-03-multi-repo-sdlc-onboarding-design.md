# Multi-Repo SDLC Onboarding — Design Spec

**Date:** 2026-07-03
**Status:** Design approved
**Scope:** Vault secret structure, Worker KV-based project lookup, and onboarding process for adding consumer repos to the autonomous SDLC pipeline.
**Depends on:** [2026-07-02-sdlc-card-movement-pipeline-design.md](./2026-07-02-sdlc-card-movement-pipeline-design.md) (established card movement flow)

---

## 1. Goal

The SDLC pipeline currently operates only for the `pc` repo (Portfolio Construction). Secrets are stored at a single Vault path (`secret/data/sdlc/pc`) and the Worker uses a hardcoded `PROJECT_ID`. This design enables:

1. **New repos** to join the SDLC pipeline with minimal setup — a few Vault writes, one KV entry, and the standard workflow files.
2. **No repeated secrets** — org-level keys (`NANOBYTE_SERVICES_TOKEN`, `OPENCODE_AUTH_JSON`) live in one place.
3. **No Worker redeploy** when onboarding — project board mapping lives in KV, not environment variables.

### Repo roles

| Role | Repos | Responsibilities |
|------|-------|------------------|
| **Shared services** | `nanobyte-services` | Worker, agent specs, scripts. No SDLC board of its own. |
| **Consumer** | `pc`, `mobile-app`, `docs-site`, … | Has its own SDLC board + issues + PRs. Runs sdlc-agent.yml, build.yml, deploy.yml. |

---

## 2. Vault Secret Structure

Three tiers. The sdlc-agent.yml fetches in order (later tiers overwrite earlier keys).

### Tier 1: Org-level (shared by all consumer repos)

```
secret/data/sdlc/common
  NANOBYTE_SERVICES_TOKEN     # PAT to clone nanobyte-services at workflow runtime
  OPENCODE_AUTH_JSON           # opencode AI provider API key
```

One copy. Every consumer repo's sdlc-agent.yml fetches this path.

### Tier 2: App-level (shared across envs for one repo)

```
secret/data/portfolio/common
  GH_PROJECT_TOKEN             # PAT with repo + project scopes for this repo's board
  SDLC_PROJECT_ID              # This repo's Projects v2 board node ID
```

One copy per consumer repo. Contains everything that is the same whether deploying to UAT or PROD.

### Tier 3: Env-level (per environment, per repo)

```
secret/data/portfolio/uat
  (existing deploy secrets...)
  SDLC_API_URL                 # UAT API base URL (e.g., https://uat-api.nanobyte.ca)

secret/data/portfolio/prod
  (existing deploy secrets...)
  SDLC_API_URL                 # PROD API base URL (e.g., https://api.nanobyte.ca)
```

Per-repo, per-environment. Already exists for deploy secrets — adds one `SDLC_API_URL` key.

### Example: multi-repo layout

```
secret/data/sdlc/common                     # shared (one copy)
secret/data/portfolio/common|uat|prod       # pc repo
secret/data/mobile-app/common|uat|prod      # mobile-app repo
secret/data/docs-site/common|uat|prod       # docs-site repo
```

---

## 3. Fetch Logic in sdlc-agent.yml

The sdlc-agent.yml fetches all three tiers sequentially in the Vault step. Later keys overwrite earlier ones, so env-specific values take precedence.

```yaml
- name: Fetch secrets from Vault
  run: |
    # Tier 1: org-level shared secrets
    JSON=$(curl -sf --header "X-Vault-Token: ${VAULT_TOKEN}" \
      "${VAULT_ADDR}/v1/secret/data/sdlc/common" | jq -r '.data.data // "{}"')
    [ "$JSON" != "{}" ] && echo "$JSON" | jq -r 'to_entries[] | "\(.key)=\(.value)"' >> "$GITHUB_ENV"

    # Tier 2: app-level shared secrets
    JSON=$(curl -sf --header "X-Vault-Token: ${VAULT_TOKEN}" \
      "${VAULT_ADDR}/v1/secret/data/${{ vars.APP_NAME }}/common" | jq -r '.data.data // "{}"')
    [ "$JSON" != "{}" ] && echo "$JSON" | jq -r 'to_entries[] | "\(.key)=\(.value)"' >> "$GITHUB_ENV"

    # Tier 3: env-specific (only tester/deployer phases need this)
    if [ "${{ steps.params.outputs.phase }}" = "testing" ] || [ "${{ steps.params.outputs.phase }}" = "publish" ]; then
      ENV=$([ "${{ steps.params.outputs.phase }}" = "publish" ] && echo "prod" || echo "uat")
      JSON=$(curl -sf --header "X-Vault-Token: ${VAULT_TOKEN}" \
        "${VAULT_ADDR}/v1/secret/data/${{ vars.APP_NAME }}/${ENV}" | jq -r '.data.data // "{}"')
      [ "$JSON" != "{}" ] && echo "$JSON" | jq -r 'to_entries[] | "\(.key)=\(.value)"' >> "$GITHUB_ENV"
    fi
```

**Per-repo configuration:** A single GitHub repository variable `APP_NAME` (e.g., `portfolio`, `mobile-app`). Everything else — Vault paths, board references — derives from it.

---

## 4. Worker: KV-Based Project Lookup

The Worker currently uses `env.PROJECT_ID` (hardcoded in `wrangler.toml`). For multi-repo, the `handlePullRequestMerged` function looks up the correct project board from KV.

### KV schema

```
Key:   repo:<full-repo-name>
Value: {"project_id": "<PVT_project_id>"}
```

Examples:
```
repo:Nanobyte-Canada/pc           → {"project_id": "PVT_kwDOEbgU584Bbxz4"}
repo:Nanobyte-Canada/mobile-app   → {"project_id": "PVT_kwDOEbgU584Bxyz1"}
```

### Worker change

In `handlePullRequestMerged`, replace `const projectId = (env as any).PROJECT_ID` with:

```typescript
// Look up this repo's project ID from KV
const repoFullName = body.repository?.full_name ?? '';
const kvKey = `repo:${repoFullName}`;
const kvValue = await env.DELIVERY_KV.get(kvKey, 'json') as { project_id?: string } | null;
const projectId = kvValue?.project_id;

if (!projectId) {
  console.error(`No project ID configured for ${repoFullName} — cannot move card`);
  return new Response('OK (no project id)', { status: 200 });
}
```

### KV administration

Onboarding a repo requires one KV write. Can be done via:

```bash
npx wrangler kv:key put \
  --binding=DELIVERY_KV \
  "repo:Nanobyte-Canada/mobile-app" \
  '{"project_id":"PVT_kwDOEbgU584Bxyz1"}'
```

No Worker redeploy needed — KV is live-read on every invocation.

---

## 5. How to Onboard a New Consumer Repo

This is the step-by-step process for adding a repo (e.g., `Nanobyte-Canada/mobile-app`) to the SDLC pipeline.

### Prerequisites

- The shared infrastructure must be deployed and running:
  - Cloudflare Worker (`sdlc-webhook-receiver`) deployed
  - GitHub App installed on the org with `issues`, `pull_request`, `projects_v2_item` events
  - Self-hosted runner registered and online
  - `nanobyte-services` repo accessible via the `NANOBYTE_SERVICES_TOKEN`

### Steps

#### Step 1: Create the Projects v2 board

Run the board creation script to create a board with all 13 swim lanes and 5 custom fields:

```bash
cd nanobyte-services/sdlc/scripts
./create-board.sh --owner Nanobyte-Canada --title "Mobile App SDLC"
```

Note the returned `PROJECT_ID` (e.g., `PVT_kwDOEbgU584Bxyz1`).

#### Step 2: Add Vault secrets

Create three Vault paths under the app name:

```bash
# App-level shared secrets
vault kv put secret/mobile-app/common \
  GH_PROJECT_TOKEN="ghp_..." \
  SDLC_PROJECT_ID="PVT_kwDOEbgU584Bxyz1"

# UAT environment secrets
vault kv put secret/mobile-app/uat \
  SDLC_API_URL="https://uat-api.mobile.nanobyte.ca"

# PROD environment secrets
vault kv put secret/mobile-app/prod \
  SDLC_API_URL="https://api.mobile.nanobyte.ca"
```

The org-level `secret/data/sdlc/common` should already exist. If not:

```bash
vault kv put secret/sdlc/common \
  NANOBYTE_SERVICES_TOKEN="ghp_..." \
  OPENCODE_AUTH_JSON='{"opencode-go":{"type":"api","key":"..."}}'
```

#### Step 3: Register the project ID in Worker KV

```bash
npx wrangler kv:key put \
  --binding=DELIVERY_KV \
  "repo:Nanobyte-Canada/mobile-app" \
  '{"project_id":"PVT_kwDOEbgU584Bxyz1"}'
```

#### Step 4: Add GitHub repository variables and secrets

In the new repo's GitHub settings (Settings → Secrets and variables → Actions):

**Variables:**
| Name | Value |
|------|-------|
| `APP_NAME` | `mobile-app` |

**Secrets:**
| Name | Value |
|------|-------|
| `VAULT_ROLE_ID` | AppRole role ID for Vault authentication |
| `VAULT_SECRET_ID` | AppRole secret ID for Vault authentication |
| `DEPLOY_SSH_KEY` | SSH private key for deploy server access |
| `SSH_KNOWN_HOSTS` | Server SSH host key |
| `SERVER_HOSTNAME` | Server hostname for Cloudflare Tunnel SSH |
| `SLACK_WEBHOOK_URL` | (optional) Slack notification channel |

#### Step 5: Add workflow files to the repo

Copy the following files into the repo's `.github/workflows/`:

| File | Source | Customization needed |
|------|--------|---------------------|
| `sdlc-agent.yml` | Template from `nanobyte-services` or existing `pc` repo | None (uses `APP_NAME` variable, Vault, and tiered fetch) |
| `build.yml` | Repo-specific | Test commands, Docker images, push targets |
| `deploy.yml` | Repo-specific | Deploy target, compose file path, health check endpoints |

#### Step 6: Add an issue to the board and test

1. Create a test issue in the new repo
2. Add it to the SDLC board (appears in Backlog)
3. Move the card to Triaging
4. Verify the Worker receives the webhook → syncs labels → sdlc-agent.yml fires → planner agent posts a Scope Document

### Checklist summary

```
[ ] Projects v2 board created (PROJECT_ID: ______________)
[ ] Vault paths created:
    - <app>/common (GH_PROJECT_TOKEN, SDLC_PROJECT_ID)
    - <app>/uat (SDLC_API_URL)
    - <app>/prod (SDLC_API_URL)
    - sdlc/common (if not already present)
[ ] Worker KV entry added: repo:<org>/<repo> → {project_id}
[ ] GitHub variable APP_NAME set
[ ] GitHub secrets configured (Vault AppRole, SSH, etc.)
[ ] sdlc-agent.yml, build.yml, deploy.yml added to .github/workflows/
[ ] Test: create issue → add to board → move to Triaging → verify pipeline fires
```

---

## 6. Error Handling

| Scenario | Behavior |
|----------|----------|
| KV lookup for repo returns no entry | Worker logs error, returns OK (no card movement). Human creates KV entry and retries. |
| Vault path <app>/common missing | sdlc-agent.yml logs warning, continues with org-level secrets only |
| Vault path <app>/<env> missing | sdlc-agent.yml logs warning, tester/deployer agents get empty SDLC_API_URL |
| `APP_NAME` variable not set | sdlc-agent.yml fails at the Vault fetch step with clear error |
| Worker deployed before KV seeded | Card stays at In Review (no post-merge movement). Onboarding step 3 adds KV entry, subsequent PRs work. |

---

## 7. Files Changed

| File | Change |
|------|--------|
| `nanobyte-services/sdlc/worker/src/index.ts` | `handlePullRequestMerged`: replace hardcoded `env.PROJECT_ID` with KV lookup (`repo:<full_name>`) |
| `nanobyte-services/sdlc/worker/wrangler.toml` | Remove `[vars] PROJECT_ID` (no longer needed; could keep as fallback) |
| `nanobyte-services/sdlc/worker/test/index.test.ts` | Update `handlePullRequestMerged` tests to pass KV-mocked `DELIVERY_KV` with project ID |
| `.github/workflows/sdlc-agent.yml` (pc repo) | Replace single `sdlc/pc` Vault path with three-tier fetch (`sdlc/common` + `<app>/common` + `<app>/<env>`) |
| `docs/superpowers/specs/2026-07-03-multi-repo-sdlc-onboarding-design.md` | This document |

## 8. Future Possibilities (not in scope)

- **Self-service onboarding script**: `sdlc/scripts/onboard-repo.sh --repo <name> --project-id <PVT_>` that creates Vault paths, KV entry, and template workflow files in one command.
- **Board-per-repo validation**: Worker verifies that the incoming webhook's repo matches the KV-mapped board, logs a warning if mismatched.
