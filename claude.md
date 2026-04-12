# Portfolio Construction App

Monorepo for a portfolio construction and analysis application using public ETFs.
Backend (Kotlin/Spring Boot) + Frontend (React/TypeScript) + PostgreSQL + Redis.

## Local Environment Constraint

**No JDK/Java is installed on the local machine.** All backend compilation, testing, and validation must be done inside Docker containers. Never run `./gradlew` commands directly on the host.

## Commands

```bash
# Docker (local full stack)
docker compose up --build
docker compose down
docker compose logs -f backend

# Backend validation (inside Docker)
docker compose exec backend ./gradlew test
docker compose exec backend ./gradlew build

# Frontend (npm — run from frontend/)
npm install && npm run dev          # Dev server on :3000
npm run build                       # tsc + vite build
npm run test:run                    # Vitest single run
npm run lint                        # ESLint
```

## Key Constraints

- **No Tailwind** — plain CSS with CSS custom properties. Component styles in companion `.css` files.
- **Schema changes** — always use Flyway migrations. Never modify Hibernate DDL mode. Check highest existing V number and increment.
- **API calls (frontend)** — always use `apiFetch()` from `services/api.ts`. Never use raw `fetch`.
- **Tests (backend)** — use MockK for mocking, not Mockito.
- **New endpoints** — follow `/api/v1/` prefix pattern. Controllers return DTOs, never entities.
- **New entities** — create entity, repository, service, DTO, and Flyway migration.

## Pre-Planning Review (Mandatory)

Before planning any change:
1. Read `docs/reference/INDEX.md` for the documentation structure
2. Read the relevant reference files for the area being changed (see table below)
3. Check `docs/reference/unused-legacy.md` to avoid touching dead code
4. Review `docs/business-context.html` for architectural context

| Area | Reference File |
|------|---------------|
| Schema changes | `docs/reference/database-schema.md` |
| API changes | `docs/reference/api-endpoints.md` |
| Service logic | `docs/reference/backend-services.md` |
| Frontend work | `docs/reference/frontend-map.md` |
| Entity/DTO changes | `docs/reference/entity-relationships.md` |
| Infrastructure | `docs/reference/infrastructure.md` |
| Config/env vars | `docs/reference/configurations.md` |

## Post-Implementation (Mandatory)

After implementing any change:
1. Update all affected `docs/reference/` files (see trigger table below)
2. Update `docs/business-context.html` if architecture or features changed
3. Move completed spec and plan files to `.archive/` via `git mv`

| What Changed | Update These Files |
|---|---|
| New Flyway migration | `database-schema.md` |
| New/modified API endpoint | `api-endpoints.md` |
| New/modified service or scheduler | `backend-services.md` |
| New/modified component, hook, page | `frontend-map.md` |
| New/modified entity or DTO | `entity-relationships.md` |
| Environment variable or config | `configurations.md` |
| Docker, CI/CD, Terraform | `infrastructure.md` |
| Dead code removed | `unused-legacy.md` |
| Improvement implemented | `improvements.md` |

**Do not consider a task complete until documentation is updated.**

## Quality Bar

Before committing:
- `docker compose exec backend ./gradlew test` passes
- `npm run test:run` passes (from `frontend/`)
- `npm run lint` passes (from `frontend/`)
- `npm run build` succeeds (from `frontend/`)
- No secrets in committed files
- Flyway migration number does not conflict

## Directory Structure

```
backend/portfolio/     — Main Spring Boot app (port 8080)
backend/ingestion/     — Data ingestion microservice (port 8081)
frontend/              — React/TypeScript (Vite, port 3000)
config/                — Environment file template (.env.example)
docs/reference/        — Agent reference documentation (11 files)
docs/business-context.html — Architecture and module overview
.archive/              — Completed design specs and plans
```
