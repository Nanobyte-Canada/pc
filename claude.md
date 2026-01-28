You are a senior full-stack + DevOps engineer. Create a practical, step-by-step plan and an initial project stub (repo structure + minimal runnable skeleton) for an application that helps with portfolio construction using public ETFs and Mutual Funds.

Scope (important)

- For now, do NOT implement portfolio logic. Only create a foundation/stub for:
    - backend
    - frontend
    - database
    - tests
    - Docker container deployment 
    - CI/CD via GitHub Actions
    - environment separation (local/dev/prod)
    - GCP deployment architecture (Cloud Run + Cloud SQL + Cloud Storage + HTTPS Load Balancer)

Tech requirements
- Backend: Kotlin
- Frontend: React
- Database: PostgreSQL
- Tests:
    backend tests included
    frontend tests included
- Deployment:
    Docker containers
    GitHub Actions workflows for CI/CD
- Environments:
    local, dev, prod
    separate env variables per environment
- GCP target:
    backend on Cloud Run with autoscaling
    postgres on Cloud SQL
    frontend hosted on Cloud Storage
    Cloud Load Balancing in front (HTTPS)

What to produce (deliverables)

1. High-level architecture
- Components and how they connect (frontend → LB → backend; backend → Cloud SQL; static frontend → Cloud Storage, etc.)
- Local/dev/prod setup strategy.

2. Repository layout
- A monorepo layout is preferred unless you strongly justify multi-repo.
- Include folders for backend, frontend, infra, scripts, docs.

3. Backend stub (Kotlin)
- Use either Ktor or Spring Boot (choose one and justify).
- Minimal endpoints:
    GET /health returns 200 + basic info
    GET /api/v1/version
- Database connectivity stub (no real schema needed yet, but include migration tooling).
- Add configuration loading from env vars.

4. Frontend stub (React)
- Minimal page that calls /api/v1/version and displays it.
- Environment-based config (local/dev/prod).

5. Database stub (PostgreSQL)
- Local docker-compose service for Postgres.
- Migration tool (Flyway or Liquibase) wired in.

6. Docker & local development
- Dockerfiles for backend and frontend (or frontend build artifact + static hosting).
- docker-compose.yml for local: postgres + backend (+ frontend optional).
- Provide commands: build, run, test.

7. Testing
- Backend tests (unit + minimal integration test around health/version route).
- Frontend tests (basic render + mock API call).
- Show how tests run in CI.

8. CI/CD with GitHub Actions
- Workflows:
    PR checks: lint + unit tests + build for backend and frontend.
    Main branch: build Docker image(s), push to registry, deploy to GCP.
- Use secure auth to GCP (prefer Workload Identity Federation), not long-lived keys.
- Cache dependencies for speed.

9. GCP deployment plan
- What GCP resources are needed and why:
    Cloud Run service (backend)
    Cloud SQL instance (Postgres)
    Cloud Storage bucket (static frontend)
    HTTPS Load Balancer (routing + TLS)
    Secret Manager for secrets
    VPC Connector / Cloud SQL connector approach
- Explain environment separation (separate projects or separate resources/namespaces).

10. Provide concrete stub files
- Include minimal but valid content for key files:
    backend/build.gradle.kts (or Maven equivalent)
    backend/src/... with minimal app + routes
    frontend/package.json + minimal React app code
    docker-compose.yml
    backend/Dockerfile, frontend/Dockerfile (or alternative for static)
    .github/workflows/ci.yml and .github/workflows/deploy.yml
    .env.example plus .env.local, .env.dev, .env.prod templates (no secrets)
    README.md with setup instructions

11. Keep it minimal but runnable
- The repo should build and tests should pass from a fresh clone.
- Use placeholder values where needed.
- Avoid implementing portfolio features; just scaffolding.


Output format
- Start with a short architecture overview.
- Then present the repo tree.
- Then show the key file contents in fenced code blocks.
- End with “How to run locally” and “How CI/CD works” sections.

Constraints
- No proprietary dependencies.
- Keep secrets out of the repo.
- Prefer simple defaults and clear docs.