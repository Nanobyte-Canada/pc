# Repo Reorganization Design Spec

**Date:** 2026-04-12
**Status:** Approved
**Scope:** Directory restructure, security fixes, documentation cleanup, CLAUDE.md rewrite

---

## Problem

The repo has grown organically through multiple phases (microservice extraction, CI/CD, ingestion service, agent-reference docs, design specs). This has led to:
- Root-level clutter (4 .env files, 2 docker-compose files, multiple config files)
- Backend services at different directory levels (`backend/` and `ingestion-service/`)
- Documentation scattered across `docs/agent-reference/`, `docs/superpowers/`, `memory/`, and `CLAUDE.md`
- Secrets committed to git (`.env.local` with SnapTrade credentials, hardcoded EODHD API key in docker-compose.yml)
- CLAUDE.md at 297 lines with heavy overlap with agent-reference docs
- Stale/completed design specs mixed with active documentation

## Constraints

- Docker Compose and CI/CD must work after restructure
- No build system changes (independent Gradle builds, not multi-module)
- Frontend internal structure unchanged
- Only running locally right now (dev/prod env files not needed)
- Git history will retain old paths (unavoidable)

---

## Target Directory Structure

```
pc/
├── backend/
│   ├── portfolio/                  # Main Spring Boot app
│   │   ├── src/main/kotlin/com/portfolio/
│   │   ├── src/main/resources/
│   │   ├── src/test/
│   │   ├── certs/
│   │   ├── gradle/
│   │   ├── build.gradle.kts
│   │   ├── settings.gradle.kts
│   │   ├── Dockerfile
│   │   ├── Dockerfile.prebuilt
│   │   └── gradlew, gradlew.bat
│   └── ingestion/                  # Data ingestion microservice
│       ├── src/main/kotlin/com/portfolio/ingestion/
│       ├── src/main/resources/
│       ├── src/test/
│       ├── gradle/
│       ├── build.gradle.kts
│       ├── settings.gradle.kts
│       ├── Dockerfile
│       └── gradlew, gradlew.bat
├── frontend/                       # React/TypeScript — unchanged internally
├── infra/                          # Terraform + nginx — unchanged
├── scripts/                        # Deployment scripts — unchanged
├── config/
│   └── .env.example                # Env var template (empty secrets)
├── docs/
│   ├── reference/                  # Agent reference documentation
│   │   ├── INDEX.md
│   │   ├── api-endpoints.md
│   │   ├── backend-services.md
│   │   ├── configurations.md
│   │   ├── database-schema.md
│   │   ├── entity-relationships.md
│   │   ├── frontend-map.md
│   │   ├── improvements.md
│   │   ├── infrastructure.md
│   │   ├── ingestion-workflow.md
│   │   └── unused-legacy.md
│   └── business-context.html       # Business context reference
├── .archive/                       # Completed design specs and plans
│   ├── plans/                      # Historical implementation plans
│   └── specs/                      # Historical design specifications
├── CLAUDE.md                       # Agent instructions (~100 lines)
├── README.md                       # Project README (updated paths)
├── docker-compose.yml              # Local dev stack
├── docker-compose.vps.yml          # VPS deployment stack
├── .gitignore                      # Updated exclusions
└── .gitattributes
```

## What Moves Where

| Source | Destination | Action |
|--------|------------|--------|
| `backend/*` (all contents) | `backend/portfolio/` | Move |
| `ingestion-service/*` (all contents) | `backend/ingestion/` | Move |
| `docs/agent-reference/` | `docs/reference/` | Rename |
| `docs/superpowers/plans/*` | `.archive/plans/` | Move |
| `docs/superpowers/specs/*` | `.archive/specs/` | Move |
| `.env.example` | `config/.env.example` | Move |
| `.env.local` | (deleted from git) | git rm + gitignore |
| `.env.dev` | (deleted) | git rm |
| `.env.prod` | (deleted) | git rm |
| `memory/MEMORY.md` | (deleted) | git rm |

## What Gets Deleted

- `memory/` directory — redundant with Claude Code auto-memory
- `.env.local` — contains committed secrets (SnapTrade creds, encryption key)
- `.env.dev`, `.env.prod` — not needed for local-only development
- `docs/superpowers/` — empty after moving plans/specs to .archive/
- `ingestion-service/` — empty after move to backend/ingestion/

---

## File Updates Required

### docker-compose.yml

| Change | Old | New |
|--------|-----|-----|
| Backend build context | `./backend` | `./backend/portfolio` |
| Ingestion build context | `./ingestion-service` | `./backend/ingestion` |
| Frontend volume: src | `./frontend/src:/app/src:ro` | unchanged |
| EODHD default value | `696998d31abc34.90450552` | empty `${EODHD_API_KEY:-}` |

### docker-compose.vps.yml

| Change | Old | New |
|--------|-----|-----|
| Backend build/image context | references to `backend` | `backend/portfolio` |
| Ingestion build/image context | references to `ingestion-service` | `backend/ingestion` |

### .github/workflows/ (ci.yml, deploy.yml, deploy-vps.yml)

Update all path references:
- `backend/` → `backend/portfolio/`
- `ingestion-service/` → `backend/ingestion/`
- Any `./gradlew` commands that specify working directory

### .gitignore additions

```
# Local environment files (contain secrets)
.env
.env.local
config/.env.local
```

Note: `.archive/` is tracked in git. Completed specs/plans are moved there via `git mv` after implementation.

### CLAUDE.md — Rewrite to ~100 lines

Structure:
1. **Project identity** — 1-2 lines: what this is
2. **Local environment constraint** — no JDK on host, Docker-only
3. **Commands** — docker compose, npm, validation inside containers
4. **Key constraints** — no Tailwind, Flyway-only, apiFetch(), MockK not Mockito
5. **Pre-planning review** — read docs/reference/ files before planning
6. **Post-implementation** — update affected docs, move completed specs to .archive/
7. **Quality bar** — build, test, lint, no secrets
8. **References** — pointers to docs/reference/ for architecture, services, schema, etc.

### docs/reference/ — Path updates

All 11 files need `backend/` references updated to `backend/portfolio/` and `ingestion-service/` to `backend/ingestion/`. Key files affected:
- `INDEX.md` — directory structure overview
- `backend-services.md` — package paths
- `infrastructure.md` — Docker/CI paths
- `configurations.md` — env var locations
- `database-schema.md` — migration file paths
- `frontend-map.md` — no backend paths, minimal changes

### docs/business-context.html — Path updates

Update any directory structure references.

### README.md — Path updates

Update getting-started commands and directory references.

---

## Security Remediation

1. **git rm** `.env.local` (contains SnapTrade Client ID, Consumer Key, Broker Encryption Key)
2. **Remove** hardcoded EODHD API key from docker-compose.yml default value
3. **Add** `.env`, `.env.local`, `config/.env.local` to `.gitignore`
4. **Note in README**: credentials were previously committed; rotate SnapTrade keys and broker encryption key
5. **Audit**: scan for any other hardcoded secrets in source code

---

## Local Development Workflow (post-restructure)

```bash
# First time setup
cp config/.env.example .env
# Edit .env — fill in SNAPTRADE_CLIENT_ID, EODHD_API_KEY, etc.

# Start everything
docker compose up --build

# Backend validation (inside container)
docker compose exec backend ./gradlew test

# Frontend development
cd frontend && npm run dev

# Frontend validation
npm run build && npm run lint && npm run test:run
```

---

## Verification Plan

After restructure:
1. `docker compose up --build` — all 4 services start (redis, postgres, backend, ingestion)
2. `docker compose exec backend ./gradlew test` — backend tests pass
3. `cd frontend && npm run build` — frontend builds
4. `cd frontend && npm run lint` — no new lint errors
5. Health check: `curl http://localhost:8080/health` returns 200
6. `git diff --stat` — review all changes
7. `git log --all --diff-filter=D -- '*.env*'` — confirm secret-containing files removed
8. No `.env.local` in the repo after commit

---

## Post-Implementation Instruction (add to CLAUDE.md)

> After implementing a plan from `docs/superpowers/`, move the completed spec and plan files to `.archive/` via `git mv`.
