# Service Alias Collision on Shared Docker Networks — Portfolio Construction App Design

Status: Proposed — pending owner review
Date: 2026-09-19
Incident: UI Tests — Deployed run [35420097091](https://github.com/Nanobyte-Canada/pc/actions/runs/35420097091) (~50–60 % of authenticated tests failing/flaky)
Affected environments: `portfolio-uat` (active incident), `portfolio-prod` (latent, identical configuration)
Related decisions: ADR-0017 (shared Postgres/Redis over external networks), ADR-0018 (container naming), ADR-0030/0031 (deployed UI test workflow)

## 1. Overview and purpose

This document specifies the fix for a confirmed production-class incident: the `pc` UAT stack exposes a generic Docker network alias (`backend`, `frontend`, `ingestion-service`, …) that collides with the same alias in the separately deployed `investclub` UAT stack on shared external networks. The `pc` frontend's nginx round-robins between the two backends, so roughly half of all browser API calls are answered by the wrong app, which rejects them with `403 Invalid CORS request`.

The same collision exists latent in production. This document records the evidence, the root cause, the decision to rename compose service keys to app-prefixed unique names, the follow-up security and test-hygiene work, and the verification plan.

## 2. Incident evidence (verified)

### 2.1 Failure signature

The deployed UAT browser suite (run `35420097091`) showed ~50–60 % of authenticated tests failing or flaky. Failure captures show the login page with:

```
Unexpected token 'I', "Invalid CORS request" is not valid JSON
```

Intermittency across every authenticated suite (regression, accessibility, visual; all browsers) is the signature of a per-request coin flip, not a deterministic app defect.

### 2.2 Docker network aliases (root-cause artifact)

The following was captured from the live host. Both `pc` and `investclub` backends carry the generic alias `backend` on the shared networks; `pc`'s frontend carries `frontend`:

```
/uat-portfolio-backend  | infra-uat-network=[uat-portfolio-backend backend] uat-internal-network=[uat-portfolio-backend backend]
/uat-investclub-backend | infra-uat-network=[uat-investclub-backend backend] uat-internal-network=[uat-investclub-backend backend investclub-backend]
/uat-portfolio-frontend | uat-internal-network=[uat-portfolio-frontend frontend]
```

The container names (`uat-portfolio-backend`, `uat-investclub-backend`) are unique and correct. The collision is created by the compose **service keys**, which Docker Compose attaches as network aliases.

### 2.3 nginx resolution

`frontend/nginx.conf` proxies every API path to generic aliases:

| nginx location | Upstream | Line |
|---|---|---|
| `/api/` | `http://backend:8080` | 29 |
| `/auth/` | `http://backend:8080` | 38 |
| `/health` | `http://backend:8080` | 47 |
| `/actuator/` | `http://backend:8080` | 52 |
| `/ws/quotes` | `http://market-data-service:8082` | 58 |
| `/ingestion-api/` | `http://ingestion-service:8081/` | 71 |
| `/market-data-api/` | `http://market-data-service:8082/` | 80 |
| `/strategy-api/` | `http://strategy-service:8083/` | 90 |

nginx resolves `backend` at startup of the first proxy request to **both** backend IPs and round-robins between them. Requests landing on `uat-investclub-backend` are rejected by that app's CORS configuration (`CORS_ALLOWED_ORIGINS=https://uatinvestclub.nanobyte.ca`), which is correct for investclub's own origin.

### 2.4 Reproduction

- From the public URL (`https://uatportfolio.nanobyte.ca`): 5 consecutive login requests alternated `403 Invalid CORS request` and `403 {"detail":"Invalid email or password"}`.
- Directly against the frontend's nginx on the host port (bypassing the Cloudflare tunnel): 6 requests alternated the same two bodies. This proves the round-robin is inside the Docker stack (nginx DNS resolution), not in the tunnel.
- Live CORS environments verified correct per app:
  - `uat-portfolio-backend: CORS_ALLOWED_ORIGINS=https://uatportfolio.nanobyte.ca`
  - `uat-investclub-backend: CORS_ALLOWED_ORIGINS=https://uatinvestclub.nanobyte.ca` (investclub is the rejecter of the portfolio origin, not misconfigured).

### 2.5 Disproven hypotheses

- **Stale containers.** Verified on the host: zero non-running containers; all 40+ containers running; single listener per port. The stale-container hypothesis was disproven — the fix is alias disambiguation, not cleanup.
- **Tunnel/Cloudflare issue.** Direct host-port requests reproduce the alternation (section 2.4).
- **Backend CORS misconfiguration.** Both backends' CORS values are correct for their own origins.

## 3. Root cause

Docker Compose assigns each service a network alias equal to its compose service key. The UAT stacks of `pc` and `investclub` are separate compose projects that join the same external networks (`uat-internal-network`, `infra-uat-network`). Both projects name their API service key `backend`, so the shared network DNS returns both container IPs for `backend`. Container naming invariant #2 (`{env}-portfolio-{service}`) does not apply to service keys, which is why this slipped through.

Every API call handled by the pc frontend independently selects one of the two IPs: requests to the portfolio backend succeed (or return a legitimate auth error), requests to the investclub backend return `403 Invalid CORS request` (plain text), which the SPA then fails to parse as JSON. Login is also affected because `/auth/**` uses the same upstream, producing the observed ~50 % failure rate.

Production has the identical configuration: `prod-portfolio-backend` and `prod-investclub-backend` both alias `backend` on `prod-internal-network` and `infra-prod-network`, and `prod-portfolio-frontend` shares that network. Prod users are at the same risk with every deploy.

## 4. Requirements

| ID | Requirement |
|---|---|
| R1 | **Unambiguous resolution in all environments.** Every nginx upstream in `frontend/nginx.conf` must resolve to exactly one address: the `pc` service intended, in UAT, prod, and local dev. |
| R2 | **No identity changes.** Container names (`uat-portfolio-*`, `prod-portfolio-*`, `portfolio-*`), host ports (20000/20080–20084, 10000/10080–10084), compose project names, image names, and network memberships must not change (AGENTS.md invariants #2 and #3). |
| R3 | **One static config, all environments.** The same committed `frontend/nginx.conf` must work unchanged in UAT, prod, and local compose; the fix must be applied to `deploy/uat/docker-compose.yml`, `deploy/prod/docker-compose.yml`, and the root `docker-compose.yml`. (Local dev nuance: local dev runs the Vite dev server (`target: development`, proxied via `VITE_PROXY_*` env in the root compose), so nginx.conf is exercised only in the prod/uat images; the local-compose rename is handled through the `VITE_PROXY_*` values.) |
| R4 | **No user-visible behavior change** other than eliminating the intermittent `403 Invalid CORS request`. Proxy paths, headers, websocket upgrade, and path rewriting stay as they are. |
| R5 | **Security follow-up.** The UAT test-account credentials exposed in CI artifacts and in the committed V76 migration must be rotated across both Vault and the database; the prod seeded account was found and **deleted on 2026-09-19** (verified 0 rows remain); the UAT account's credential rotation remains; retention for authenticated UAT test artifacts must be reduced. |
| R6 | **Test-hygiene follow-up.** After the fix, the deployed suite must be re-run and the known vacuous passes and non-auth failures adjudicated: tighten assertions that match sidebar elements or the login heading; scope or sanction mobile-only controls; resolve or keep documented the `DASH-OVERVIEW-002` dashboard/positions discrepancy. |
| R7 | **Verifiability.** Each environment must be verifiable with unambiguous commands: network-alias inspection, a repeated login probe that can never return `Invalid CORS request`, and a post-deploy suite re-run. |

## 5. Discovery baseline and reference audit

The fix must not break any existing consumer of the old service keys. The following audit was performed against the repository and the shared `nanobyte-services` infrastructure repository.

### 5.1 In-repo references that must change

| Location | Reference | Action |
|---|---|---|
| `deploy/uat/docker-compose.yml:5,51,85,120,155,192` | service keys `backend`, `ingestion-service`, `market-data-service`, `strategy-service`, `broker-gateway-service`, `frontend` | rename |
| `deploy/uat/docker-compose.yml:199-201` | `frontend.depends_on.backend` | rename key |
| `deploy/prod/docker-compose.yml:5,51,85,120,155,192` | same service keys | rename |
| `deploy/prod/docker-compose.yml:199-201` | `frontend.depends_on.backend` | rename key |
| `docker-compose.yml:34,77,107,140,171,206` | same service keys (local stack) | rename |
| `docker-compose.yml:49` | `BROKER_GATEWAY_URL: http://broker-gateway-service:8084` | rename host |
| `docker-compose.yml:152-153` | `MARKET_DATA_SERVICE_URL`, `PORTFOLIO_SERVICE_URL` generic hosts | rename host |
| `docker-compose.yml:214-217` | `VITE_PROXY_*` generic hosts | rename host |
| `docker-compose.yml:225-229` | `frontend.depends_on` list | rename keys |
| `frontend/nginx.conf:29,38,47,52,58,71,80,90` | proxy upstreams (section 2.3) | rename hosts |
| `README.md:36-43` | service table | add new compose keys |
| `README.md:98-100` | `docker compose exec backend/market-data-service/strategy-service` | rename services |
| `docs/reference/infrastructure.md:72-77,82,97,250,269-277,471` | local stack service table (all six rows), dependency prose, exec/log examples, IB-gateway prose | rename |
| `docs/reference/frontend-map.md:990-992` | Vite proxy target table | rename |
| `docs/reference/configurations.md:182,396` | `BROKER_GATEWAY_URL` default (`http://broker-gateway-service:8084`) | rename host |
| `docs/reference/backend-services.md:531` | `broker-gateway.url` default | rename host |
| `docs/reference/database-schema.md:1577` | `docker compose exec backend` example | rename |
| `docs/reference/INDEX.md:52,94` | `docker compose exec backend` examples | rename |
| `docs/runbooks/google-oauth-provider-unavailable.md:98` | nginx snippet `proxy_pass http://backend:8080` | rename host |
| `docs/runbooks/google-oauth-provider-unavailable.md:152` | `docker compose exec prod-backend` (pre-existing wrong container name) | fix to `prod-portfolio-backend` |
| `.github/workflows/deploy.yml:177`, `deploy-prod.yml:155` | `docker compose up -d` (all services; no service-key reference) | add `--remove-orphans` for rename reconciliation |
| `.github/workflows/ui-tests-deployed.yml` (13 upload steps), `ui-visual-baseline-update.yml:92` | artifact retention 14 days | reduce authenticated UAT retention (R5) |
| `e2e/tests/regression/*.spec.ts` | vacuous/mobile assertions (R6) | tighten or sanction |
| `AGENTS.md` invariant #2 | container naming only | add service-key naming convention |

### 5.2 In-repo references intentionally unchanged

| Location | Reference | Reason |
|---|---|---|
| `backend/{ingestion,market-data,strategy,broker-gateway}/src/main/resources/application.yml:3` | `spring.application.name: *-service` (portfolio's is already `portfolio-backend`) | Spring application name, not Docker DNS; changing it would alter metric/log identity for no benefit |
| `scripts/integration-test.sh`, `scripts/broker-sync-test.sh` | host ports; `docker compose exec -T postgres` | the `postgres` service key is not renamed; no generic aliases |
| `config/.env.example`, `deploy/{prod,uat}/.env.example` | environment variable names | no service aliases |
| `docs/business-context.html` | diagrams and port references | contains no generic service aliases |
| `docs/superpowers/plans/**`, `docs/superpowers/specs/**` (pre-2026-09-19) | historical compose snippets | append-only records; never rewritten |

**Historical-records exemption:** dated historical plan records under `docs/plans/**` (e.g., `docs/plans/2026-06-15-fix-options-chain-prod.md:20,23,24` references `market-data-service`) are exempt from the rename audit; only live configuration and current docs are renamed.
| `docs/reference/configurations.md:422-429` | `spring.application.name: ingestion-service` | Spring name, not compose key |

### 5.3 External references (shared infrastructure and sibling app)

| Location | Reference | Finding |
|---|---|---|
| `nanobyte-services/monitoring/prometheus/prometheus.yml` | scrape targets `host.docker.internal:10080-10084`, `:20080-20084` | **No dependency** on Docker DNS aliases; static labels (`service: backend`, …) are defined in the scrape config, not derived from compose |
| `nanobyte-services/monitoring/promtail/promtail-config.yml:22-23` | Loki `service` label from `com_docker_compose_service` | Label values change (`backend` → `portfolio-backend`); no alert rule or dashboard in `nanobyte-services` queries the old values (log alerts filter by `container_name`, unchanged) |
| `nanobyte-services/monitoring/grafana/provisioning/alerting/rules.yml` | Prometheus label selectors and `container_name=~"(prod\|uat)-.*"` log filters | **No change required** |
| `investclub` compose (sibling repo) | service key `backend` still aliases `backend` | After this change, investclub's nginx resolves `backend` to its own backend only, fixing investclub's own ~50 % failure rate as a side effect |

## 6. Options analysis

### 6.1 Option A (recommended) — app-prefixed compose service keys

Rename the compose service keys in all three compose files:

| Old service key | New service key | Container name (unchanged) |
|---|---|---|
| `backend` | `portfolio-backend` | `{env}-portfolio-backend` / `portfolio-backend` (local) |
| `ingestion-service` | `portfolio-ingestion` | `{env}-portfolio-ingestion` / `portfolio-ingestion` |
| `market-data-service` | `portfolio-market-data` | `{env}-portfolio-market-data` / `portfolio-market-data` |
| `strategy-service` | `portfolio-strategy` | `{env}-portfolio-strategy` / `portfolio-strategy` |
| `broker-gateway-service` | `portfolio-broker-gateway` | `{env}-portfolio-broker-gateway` / `portfolio-broker-gateway` |
| `frontend` | `portfolio-frontend` | `{env}-portfolio-frontend` / `portfolio-frontend` |

Then `frontend/nginx.conf` proxies to `portfolio-backend`, `portfolio-ingestion`, `portfolio-market-data`, and `portfolio-strategy`. Because UAT, prod, and local networks are separate, the **same static nginx.conf works everywhere** — the name resolves within whichever stack the container is attached to. (Local dev nuance: local dev runs the Vite dev server (`target: development`, proxied via `VITE_PROXY_*` env in the root compose), so nginx.conf is exercised only in the prod/uat images; the local-compose rename is handled through the `VITE_PROXY_*` values.)

Advantages: removes the collision at the source; fixes investclub's resolution as a side effect; no build machinery; aligns service-key naming with the existing `portfolio-*` image and container naming; satisfies R1–R4 with one mechanism.

Costs: touches three compose files and several docs; the first deploy after the rename must reconcile old and new service identities (section 8.3); Loki `service` label values change (verified harmless, section 5.3).

### 6.2 Option B (fallback) — nginx build-arg templating

Keep generic compose service keys; template `nginx.conf` at image build time: add `ARG BACKEND_HOST` (and one per service) to `frontend/Dockerfile`, substitute the values into the nginx config (or use nginx `envsubst` templates), and pass `uat-portfolio-backend`/`prod-portfolio-backend` from `build.yml` for the two image variants, with local names in the root compose.

Advantages: changes only `pc`; no compose service identity churn; no Loki label change.

Disadvantages: fixes `pc` only — investclub remains broken by pc's alias; adds build-arg plumbing for six upstreams across two image variants plus local dev; nginx config becomes a template, so the deployed artifact no longer matches the committed file one-to-one; does not address Issue 3 (cross-app hygiene) at all.

### 6.3 Recommendation

**Option A.** It is the only option that fixes both apps, keeps the nginx configuration static and identical across environments, and requires no build-time machinery. The audit in section 5 found no external consumer of the generic aliases (`docker compose up -d` uses all services; Prometheus scrapes host ports; alerts filter on container names). Option B remains documented as the fallback if the Option A audit is later found to be incomplete.

## 7. Shared-network convention (for the infrastructure runbook)

Apps sharing `infra-{env}-network` / `{env}-internal-network` must follow:

1. Compose service keys must be globally unique and app-prefixed (`portfolio-*`, `investclub-*`); never `backend`, `frontend`, `api`, `db`, or other generic names.
2. Cross-service URLs inside a stack should target the explicit container name (already the practice in `deploy/*/docker-compose.yml`) or the unique app-prefixed alias; never a generic alias.
3. Any container joining a shared external network should be checked with the alias inspection command (section 11) after deploys that change service topology.
4. The recommendation should be sent to the owners of sibling stacks (investclub) for the same treatment.

## 8. Design of the recommended fix

### 8.1 Compose rename

Apply the section 6.1 mapping to all three compose files. Container names, ports, `name:` project keys, images, networks, resource limits, logging, and healthchecks are untouched. Internal environment URLs that already use container names (`BROKER_GATEWAY_URL`, `PORTFOLIO_SERVICE_URL`, `MARKET_DATA_SERVICE_URL` in the deployed files) need no change; the local compose file's generic URLs are updated to the new keys.

### 8.2 nginx update

Replace upstream host names only (four `backend` occurrences; one each for `ingestion-service`, `strategy-service`; two for `market-data-service`). No other nginx directives change. The frontend image must be rebuilt and redeployed for the new config to take effect (nginx.conf is baked into the image).

### 8.3 Deployment ordering and reconciliation

- The compose rename and the nginx change must land in the **same commit/PR**, so the image built for a commit always matches the compose file deployed with that commit's tag.
- Build → Deploy ordering is already correct in CI (`Build & Push Images` completes before `Deploy` runs via `workflow_run`); no manual tag mixing is allowed.
- **First deploy after the rename:** because the service key changes while `container_name` stays the same, Compose may refuse to adopt the existing container and fail with a name conflict. The deterministic recovery, if the first renamed deploy fails with a container-name conflict, is a one-time `docker compose down --remove-orphans` on the host followed by re-dispatch (external networks persist; downtime is one to three minutes). Deploy workflows additionally gain `--remove-orphans` so future service removals do not leave orphans.
- Apply UAT first (automatic on merge), verify (section 11), then prod (manual dispatch). Rollback is redeploying the previous `main-<sha>` tag after reverting the compose/nginx change.

## 9. Side findings (must be tracked with this change)

### 9.1 Security — test credentials exposed (CI artifacts and committed migration)

Two independent exposures affect the same UAT test account (`test-admin@nanobyte.ca`, single account per ADR-0030/0031):

1. **CI failure artifacts.** Uploaded artifacts contain `error-context.md` DOM snapshots of the login page with the typed test-account email and password. This is confirmed in the incident artifacts; `screenshot` masking does not apply to Playwright DOM error-context captures.
2. **Committed credential.** `backend/portfolio/src/main/resources/db/migration/V76__uat_seed_test_admin_user.sql:5` contains the account's cleartext password in a comment (and the Argon2id hash); the built copy under `backend/portfolio/build/resources/main/db/migration/` (build output; not in git) contains it too. The credential is in git history and cannot be un-exposed by editing files.
3. **Rotation is not Vault-only.** The login password is the Argon2id hash stored in the `users` table; `APP_TEST_ADMIN_PASSWORD` in Vault (`secret/portfolio/uat`) is only the value supplied to the tests. Rotating the Vault value without updating the database hash (or vice versa) breaks authentication. There is no HTTP endpoint that changes the password (`AuthenticationService.changePassword` has no controller mapping), so rotation requires a database update plus the Vault update.
4. **Production exposure — remediated 2026-09-19.** V76 runs unconditionally, and prod had deployed past it: the seeded admin existed in the production database. With owner approval the row was suspended, its `user_roles` links removed, then deleted; 0 rows remain (verified 2026-09-19). UAT's row remains ACTIVE and is required by the test suite; its rotation is Task 9.

Actions:

1. Generate a new password and a matching Argon2id hash (parameters `m=65536,t=3,p=4`, 16-byte salt, 32-byte hash), update the `users` row for `test-admin@nanobyte.ca` in the UAT database, and put the same new password in Vault at `secret/portfolio/uat`. The old value is compromised and must never be used again.
2. Completed 2026-09-19: prod row suspended, role links removed, deleted; verified 0 rows remain. No further action.
3. Reduce retention for authenticated UAT test artifacts from 14 days to 3 days: all upload steps in `ui-tests-deployed.yml` and the baseline-update workflow. The read-only production smoke artifacts (no credentials) stay at 30 days.
4. Do not edit V76 in place — Flyway checksums make editing an applied migration unsafe. Record that future seed migrations must not contain cleartext credentials and must be environment-scoped (follow-up, out of scope here).
5. Record in `docs/testing/operations.md` that Playwright error-context DOM captures cannot be value-masked, that retention limits are the control, and that credential rotation spans both Vault and the database.

### 9.2 Test hygiene and revalidation

After the alias fix, re-run the deployed suite and adjudicate:

| Item | Evidence | Disposition |
|---|---|---|
| `DASH-OVERVIEW-002` (skip) | Dashboard shows C$ 0 / "No positions" while `/brokers/positions` shows 32 positions | Investigate dashboard aggregation vs broker positions; if confirmed app bug, file issue and keep the sanctioned skip referencing it; if resolved by the alias fix, remove the skip |
| `DASH-OVERVIEW-004` (mobile-chrome failure) | `.account-nav__pills` is `display:none` below 769 px (`AccountNavBar.css:190-198`); mobile uses `.account-nav__trigger` + sheet | Keep desktop assertion; sanction a skip for viewports < 769 px, or add a mobile-specific scenario in a later change |
| `WHEEL-CAL-002` (chromium failure) | Grid is `<table class="wcg-table">` (`WheelCalendarGrid.tsx:70`); `getByRole('table')` is not matching at assertion time on the deployed page (likely render timing; the element has implicit role `table`) | Tighten to `.wcg-table`; if still failing, inspect artifact for conditional rendering and fix the wait or sanction the skip with evidence |
| `WHEEL-CAL-004` (mobile-chrome failure) | "Add Ticker" button in `WheelTopTickers.tsx:113` | Inspect artifact; if the control is off-screen, `scrollIntoViewIfNeeded()` before asserting; if absent on mobile by design, sanction the skip |
| `ANALYTICS-SECT-002/003` (vacuous pass) | Locator `canvas, svg, …` matches sidebar icons; `AnalyticsPage.tsx:40-45` renders the empty state whenever the in-memory analysis store is empty, which is always true on fresh navigation | Tighten to `.chart-container canvas` and convert to sanctioned skips with the in-memory-store reason, consistent with `ANALYTICS-SECT-001/004` |
| `RPT-CONTRIB-002` (vacuous pass) | Same broad locator; reporting charts use `.chart-container` (`ContributionsChart.tsx:58`) and load from the API | Tighten to `.chart-container canvas` |
| `PORT-MGMT-001` (vacuous pass) | `h1` with `hasText: 'Portfolio'` matches the login heading "Your portfolio, one dashboard." (`LoginPage.tsx:65-67`) | Harden to an exact-match level-1 heading (`name: 'Portfolio', exact: true`) |

## 10. Risks and mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| **Deployment ordering** — new compose deployed with an old frontend image (still proxying `backend`), or old compose with a new image | High | Compose and nginx change in the same commit; CI tags images per SHA and Deploy consumes the same SHA; verify with the section 11 probes immediately after deploy |
| **Compose container-name conflict on first renamed deploy** — explicit `container_name` cannot be adopted by a renamed service | High | One-time `docker compose down --remove-orphans` on the host before the first renamed deploy; add `--remove-orphans` to both deploy workflows; confirm with `docker compose ps` that each container appears exactly once. (Docker was unavailable in the authoring environment, so this behavior was not reproduced locally; the reconciliation path is deliberately conservative.) |
| **Monitoring references** — Prometheus scrape targets or Loki queries break | Low | Audited: Prometheus targets are host ports with static labels; log alerts filter by unchanged container names. Loki `service` label values change with the compose key; verify Grafana/Loki after deploy |
| **investclub behavior change** — its nginx no longer round-robins to pc | Low (positive) | Expected outcome: investclub's success rate rises to 100 %. Communicate the convention (section 7) to the sibling-stack owner |
| **Local dev breakage** — stale muscle memory and scripts (`docker compose exec backend …`) | Low | README and reference docs updated in the same PR; local compose URLs updated |
| **Historical docs become stale** — old plans still show generic names | Low | Accepted; records are append-only and ADR-0032 explains the change |
| **Credential exposure** — password in CI artifacts, committed in V76, and possibly seeded in prod | Critical | Immediate rotation across Vault and the UAT database; prod account removed 2026-09-19 (verified); UAT rotation pending; retention to 3 days; never edit the applied V76 migration |
| **Summary vs detail data discrepancy (`DASH-OVERVIEW-002`)** | Medium | Evidence-gated investigation in the hygiene task; owner decides bug vs premise |

## 11. Verification plan

Static (deploy host or CI, non-executing parse):

```bash
docker compose -f docker-compose.yml config -q
docker compose -f deploy/uat/docker-compose.yml config -q
docker compose -f deploy/prod/docker-compose.yml config -q
```

Live UAT (after the automatic deploy following merge):

1. **Alias inspection** — no pc container carries `backend`/`frontend`; pc containers carry `portfolio-*` aliases.
2. **Login probe** — repeated requests through nginx never return `Invalid CORS request` (exact command in the plan).
3. **Proxy route probes** — `/ingestion-api/`, `/market-data-api/`, `/strategy-api/` health endpoints return `200`.
4. **Suite re-run** — authenticated failures disappear; only the adjudicated hygiene items remain, then their tasks close them.
5. **investclub probe** — its login endpoint no longer alternates with a pc rejection.
6. **Prod** — after manual prod deploy: alias inspection on `prod-portfolio-*`, the same login probe against `localhost:10000` with `Origin: https://portfolio.nanobyte.ca`, and a manually dispatched read-only production smoke run.

## 12. Out of scope and open questions

1. **investclub compose rename** — recommended (section 7) but owned by the investclub repository; pc cannot fix their `backend` alias. Owner to forward the convention.
2. **`DASH-OVERVIEW-002`** — whether the dashboard/positions discrepancy is an app data bug requires owner review; the hygiene task is evidence-gated.
3. **`DASH-OVERVIEW-004` / `WHEEL-CAL-*` dispositions** — desktop-only scoping versus additional mobile scenarios is an owner decision after the post-fix run.
4. **Artifact retention value** — 3 days is proposed; owner may choose a different value for authenticated UAT artifacts.
5. **Password rotation mechanics** — the owner must confirm the rotation scope (UAT database hash + Vault). The committed credential cannot be removed from history; rotation is the only mitigation. (Prod disposition resolved: account deleted 2026-09-19.)
6. **Retention of the old V76 migration** — it must not be edited (Flyway checksum); a follow-up decision is needed on whether future environments get a scrub or a checksum-safe replacement.
