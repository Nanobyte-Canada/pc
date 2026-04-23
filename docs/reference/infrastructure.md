# Infrastructure Reference

Complete infrastructure reference for the Portfolio Construction App. Covers architecture, Docker, CI/CD, Terraform, Nginx, scripts, and environment files.

---

## Architecture Overview

### System Components (GCP Production)

```
                                    +-----------------------------------+
                                    |    HTTPS Load Balancer            |
                                    |    (Global, TLS termination)      |
                                    +----------------+------------------+
                                                     |
                            +------------------------+------------------------+
                            |                                                 |
                            v                                                 v
                +------------------------+                       +------------------------+
                |   Cloud Storage        |                       |     Cloud Run          |
                |   (Static Frontend)    |                       |   (Backend API)        |
                |                        |                       |                        |
                |   - React SPA build    |                       | +------------------+   |
                |   - index.html         |                       | |  Spring Boot     |   |
                |   - JS/CSS assets      |                       | |  Application     |   |
                |   - Cloud CDN          |                       | +--------+---------+   |
                +------------------------+                       |          |           |
                                                                 | +--------v---------+   |
                                                                 | | Cloud SQL Auth   |   |
                                                                 | | Proxy (sidecar)  |   |
                                                                 | +--------+---------+   |
                                                                 +-----------+-------------+
                                                                             |
                                                                             v
                                                                 +------------------------+
                                                                 |     Cloud SQL          |
                                                                 |    (PostgreSQL 16)     |
                                                                 |                        |
                                                                 |   - Private IP only    |
                                                                 |   - Auto backups       |
                                                                 |   - Point-in-time      |
                                                                 +------------------------+
```

### Request Flow

**Frontend requests:**
1. User accesses `https://portfolio.example.com`
2. HTTPS Load Balancer terminates TLS
3. Request routed to Cloud Storage backend bucket
4. Cloud CDN serves cached content (or fetches from bucket)
5. React SPA loads in browser

**API requests:**
1. Frontend makes request to `/api/v1/...`
2. HTTPS Load Balancer routes to Cloud Run backend service
3. Cloud Run container handles request
4. If database access needed, Cloud SQL Auth Proxy sidecar connects to Cloud SQL
5. Response returned through load balancer

### GCP Resources

| Resource | Purpose | Configuration |
|----------|---------|---------------|
| **Cloud Run** | Backend API hosting | Autoscaling 0-10 instances, 1 vCPU, 512MB |
| **Cloud SQL** | PostgreSQL database | Private IP, automated backups |
| **Cloud Storage** | Static frontend hosting | Standard class, public access |
| **HTTPS Load Balancer** | Traffic routing + TLS | Global, managed SSL certificate |
| **Cloud CDN** | Frontend caching | Automatic with load balancer |
| **Secret Manager** | Credential storage | Database passwords, API keys |
| **Artifact Registry** | Docker images | Container registry |
| **Workload Identity Pool** | CI/CD authentication | GitHub Actions to GCP |
| **VPC Network** | Private networking | For Cloud SQL private IP |
| **VPC Connector** | Cloud Run to VPC | Serverless VPC access |

### Environment Separation

Separate GCP Projects strategy:

```
portfolio-dev (GCP Project)
  Cloud Run: portfolio-backend
  Cloud SQL: portfolio-db-dev (db-f1-micro)
  Cloud Storage: portfolio-frontend-dev
  Artifact Registry: portfolio

portfolio-prod (GCP Project)
  Cloud Run: portfolio-backend
  Cloud SQL: portfolio-db-prod (db-custom-2-4096)
  Cloud Storage: portfolio-frontend-prod
  Artifact Registry: portfolio
```

Benefits: blast radius isolation, environment-specific IAM, separate billing, clear security boundaries.

### Network Security
- Cloud SQL accessible only via private IP
- Cloud Run uses VPC Connector for database access
- Cloud SQL Auth Proxy handles authentication and encryption
- GitHub Actions uses Workload Identity Federation (no long-lived keys)
- HTTPS Load Balancer with managed SSL certificates

### Scalability
- **Cloud Run:** Autoscaling 0-10 instances, concurrency-based, cold start mitigated by min instance in prod
- **Cloud SQL:** Vertical scaling, read replicas available, HikariCP connection pooling
- **Cloud Storage + CDN:** Effectively unlimited scale, global edge caching

---

## Docker

### backend/portfolio/Dockerfile -- Full Multi-Stage Build

Two-stage build for local development and CI.

**Stage 1: Build** (`gradle:8.10-jdk21-alpine`)
- Copies `build.gradle.kts`, `settings.gradle.kts`, and `gradle/` first for dependency caching
- Runs `gradle dependencies --no-daemon` to warm cache (tolerates failures with `|| true`)
- Copies `src/` and builds with `gradle build -x test --no-daemon` (tests skipped, run separately)

**Stage 2: Runtime** (`eclipse-temurin:21-jre`)
- Installs CA certificates and HTTP tools (`apt-get install ca-certificates curl wget`)
- Imports custom CA certs from `certs/*.crt` into JVM truststore via `keytool`
- Creates non-root user `appuser:appgroup` (UID/GID 1001)
- Copies `app.jar` from build stage
- Health check: `curl --fail --silent http://localhost:8080/health` (30s interval, 3s timeout, 30s start period, 3 retries)
- Entrypoint: `java -jar app.jar`
- Exposes port 8080

### backend/portfolio/Dockerfile.prebuilt -- VPS Runtime-Only

Simplified runtime image for VPS deployments where `app.jar` is pre-built by CI.

- Base: `eclipse-temurin:21-jre`
- No build stage -- expects `app.jar` in build context
- Creates non-root user `appuser:appgroup` (UID/GID 1001)
- Same health check as full Dockerfile
- No CA certificate installation (VPS uses Nginx for TLS termination)

### backend/broker-gateway/Dockerfile -- Full Multi-Stage Build

Two-stage build for the broker gateway microservice.

**Stage 1: Build** (`gradle:8.10-jdk21-alpine`)
- Copies `build.gradle.kts`, `settings.gradle.kts`, and `gradle/` first for dependency caching
- Runs `gradle dependencies --no-daemon` to warm cache (tolerates failures with `|| true`)
- Copies `src/` and builds with `gradle build -x test --no-daemon`

**Stage 2: Runtime** (`eclipse-temurin:21-jre`)
- Creates non-root user `appuser:appgroup` (UID/GID 1001)
- Copies `app.jar` from build stage
- Health check: `curl --fail --silent http://localhost:8084/actuator/health` (30s interval, 3s timeout, 30s start period, 3 retries)
- Entrypoint: `java -jar app.jar`
- Exposes port 8084

### frontend/Dockerfile -- Full Multi-Stage Build

Three-stage build.

**Stage 1: Development** (`node:20-alpine`)
- `npm ci` for deterministic installs
- Runs Vite dev server on `0.0.0.0:3000` (used by docker-compose.yml local dev)

**Stage 2: Build** (`node:20-alpine`)
- Accepts `VITE_API_URL` build arg for backend URL injection
- Runs `npm run build` producing `dist/`

**Stage 3: Production** (`nginx:alpine`)
- Copies `dist/` from build stage to `/usr/share/nginx/html`
- Copies `nginx.conf` for SPA routing
- Health check: `wget --no-verbose --tries=1 --spider http://localhost:80/` (30s interval, 3s timeout, 5s start period)
- Exposes port 80

### frontend/Dockerfile.prebuilt -- VPS Runtime-Only

Minimal Nginx image for VPS deployments where `dist/` is pre-built by CI.

- Base: `nginx:alpine`
- Copies pre-built `dist/` and `nginx.conf`
- Health check: `wget` to `http://localhost:80/nginx-health`
- Exposes port 80

### docker-compose.yml -- Local Development

Full-stack local development environment.

| Service | Image | Ports | Notes |
|---------|-------|-------|-------|
| redis | `redis:7-alpine` | 6379:6379 | Health check via `redis-cli ping` |
| postgres | `postgres:16-alpine` | 5432:5432 | Volume `postgres_data`, health check via `pg_isready` |
| backend | Build from `./backend/portfolio/Dockerfile` | 8080:8080, 5005:5005 | Debug port 5005, JAVA_TOOL_OPTIONS for JDWP, `restart: unless-stopped` |
| ingestion-service | Build from `./backend/ingestion/Dockerfile` | 8081:8081 | Separate ingestion microservice with own `ingestion` DB schema, depends on postgres + redis, `restart: unless-stopped` |
| market-data-service | Build from `./backend` context, `market-data/Dockerfile` | 8082:8082 | IBKR market data + WebSocket streaming, `market_data` DB schema, depends on postgres + redis |
| strategy-service | Build from `./backend` context, `strategy/Dockerfile` | 8083:8083 | Strategy engine + wheel writer, `strategy` DB schema, depends on postgres + redis |
| broker-gateway-service | Build from `./backend/broker-gateway/Dockerfile` | 8084:8084 | Broker data gateway (IBKR, Questrade, Wealthsimple), `broker_gateway` DB schema, depends on postgres + redis |
| frontend | Build from `./frontend/Dockerfile` target=development | 3000:3000 | Bind mounts `src/`, `public/`, `index.html` as read-only for hot reload |

**Key configuration:**
- Network: `portfolio-network` (bridge driver)
- Backend depends on postgres + redis (both `service_healthy`)
- Frontend depends on backend, ingestion-service, market-data-service, strategy-service
- Profile: `SPRING_PROFILES_ACTIVE=local`
- Backend health check: `wget http://localhost:8080/health` (30s interval, 30s start period)
- Ingestion health check: `wget http://localhost:8081/actuator/health` (30s interval, 60s start period)
- Market data health check: `wget http://localhost:8082/actuator/health` (30s interval, 60s start period)
- Strategy health check: `wget http://localhost:8083/actuator/health` (30s interval, 60s start period)
- Broker gateway health check: `wget http://localhost:8084/actuator/health` (30s interval, 60s start period)
- All backend services have `restart: unless-stopped` for Docker DNS race condition resilience
- All services depend on postgres + redis (both `service_healthy`)
- Environment variables use defaults: `POSTGRES_DB=portfolio`, `POSTGRES_USER=portfolio`, `POSTGRES_PASSWORD=portfolio`
- Vite dev server proxies `/ingestion-api` to `http://localhost:8081` (ingestion service), rewrites path prefix
- Vite dev server proxies `/market-data-api` to `http://localhost:8082` (market data service), rewrites path prefix
- Vite dev server proxies `/strategy-api` to `http://localhost:8083` (strategy service), rewrites path prefix
- Vite dev server proxies `/ws/quotes` to `ws://localhost:8082` (WebSocket for real-time quotes)
- Market-data and strategy services use Gradle composite builds with shared `backend/common/` module

### docker-compose.vps.yml -- VPS Production-Like

Production-like setup for Hostinger VPS (devpc.nanobyte.ca). No Redis service.

| Service | Image | Ports | Notes |
|---------|-------|-------|-------|
| postgres | `postgres:16-alpine` | None exposed | `restart: unless-stopped`, `POSTGRES_PASSWORD` required (no default) |
| backend | Build from `./backend/portfolio/Dockerfile` | 127.0.0.1:8080:8080 | Loopback-only binding, 60s start period |
| frontend | Build from `./frontend/Dockerfile` | 127.0.0.1:3000:80 | Loopback-only binding, Nginx serves on port 80 |

**Key differences from local:**
- All ports bound to `127.0.0.1` (Nginx reverse proxy handles external traffic)
- `restart: unless-stopped` on all services
- Profile: `SPRING_PROFILES_ACTIVE=dev`
- No Redis (caching disabled in dev profile)
- `POSTGRES_PASSWORD` has no default (must be provided via `.env`)
- Backend health check uses `curl` instead of `wget`

---

## CI/CD Workflows

### .github/workflows/ci.yml -- Continuous Integration

**Triggers:**
- Pull request to `main` or `development`
- Push to `main`
- Callable via `workflow_call` (used by deploy workflows)

**Inputs (workflow_call):**
- `skip-docker-builds` (boolean, default false) -- skip Docker image build jobs
- `vite-api-url` (string, default '') -- override VITE_API_URL for frontend build

**Jobs:**

| Job | Runner | Steps |
|-----|--------|-------|
| `backend-test` | ubuntu-latest | Checkout, JDK 21 (temurin), Gradle setup with GHA cache, `./gradlew test`, upload test results, `./gradlew build -x test`, upload `app.jar` |
| `backend-docker` | ubuntu-latest | Docker Buildx, build image `portfolio-backend:test` (no push), GHA cache. Skipped if `skip-docker-builds=true`. Needs `backend-test` |
| `frontend-test` | ubuntu-latest | Checkout, Node 20, `npm ci`, `npm run lint`, `npm run test:run`, `npm run build`, upload `dist/` |
| `frontend-docker` | ubuntu-latest | Docker Buildx, build image `portfolio-frontend:test` target=production (no push), GHA cache. Skipped if `skip-docker-builds=true`. Needs `frontend-test` |

**Artifacts uploaded:**
- `backend-test-results` -- test reports (always, even on failure)
- `backend-jar` -- `backend/portfolio/build/libs/app.jar`
- `frontend-build` -- `frontend/dist/`

### .github/workflows/deploy.yml -- GCP Cloud Run Deployment

**Triggers:**
- Push to `main`
- Manual dispatch with environment choice (`dev` or `prod`)

**Permissions:** `id-token: write`, `contents: read`

**Environment variables:**
- `PROJECT_ID_DEV=portfolio-dev`, `PROJECT_ID_PROD=portfolio-prod`
- `REGION=us-central1`
- `BACKEND_SERVICE_NAME=portfolio-backend`
- `ARTIFACT_REGISTRY=us-central1-docker.pkg.dev`

**Jobs:**

| Job | Dependencies | Steps |
|-----|-------------|-------|
| `test` | None | Calls `ci.yml` via `workflow_call` |
| `deploy-backend` | `test` | Auth via Workload Identity Federation, configure Docker for Artifact Registry, build+push image (tagged with `sha` and `latest`), deploy to Cloud Run with `APP_VERSION` and `APP_ENVIRONMENT` env vars |
| `deploy-frontend` | `test`, `deploy-backend` | Auth via WIF, Node 20, `npm ci && npm run build` with `VITE_API_URL`, upload to Cloud Storage bucket, invalidate CDN cache |

**Secrets required:**
- `GCP_PROJECT_NUMBER` -- for Workload Identity Provider path

**Workload Identity Provider path:**
```
projects/<GCP_PROJECT_NUMBER>/locations/global/workloadIdentityPools/github-pool/providers/github-provider
```

### .github/workflows/deploy-vps.yml -- VPS Deployment

**Triggers:**
- Push to `development` branch
- Manual dispatch

**Jobs:**

| Job | Dependencies | Steps |
|-----|-------------|-------|
| `test` | None | Calls `ci.yml` with `skip-docker-builds: true`, `vite-api-url: https://devpc.nanobyte.ca` |
| `deploy` | `test` | Download artifacts, create tarball with prebuilt Dockerfiles, SCP to VPS, SSH deploy, rollback on failure |

**Deployment process (SSH script):**
1. Verify `deploy.tar.gz` exists on VPS
2. Backup current deployment to `previous/`
3. Extract tarball to `current/`
4. Verify critical files: `docker-compose.vps.yml`, `backend/portfolio/app.jar`, `frontend/dist/`
5. Create `.env` file from GitHub Secrets
6. `docker compose -f docker-compose.vps.yml down`
7. `docker compose -f docker-compose.vps.yml build`
8. `docker compose -f docker-compose.vps.yml up -d`
9. Wait up to 30s for backend health check
10. Clean up old Docker images

**Rollback on failure:**
- If `previous/` directory exists, restore it and run `docker compose up -d`

**Secrets required:**
- `VPS_HOST` -- VPS hostname/IP
- `VPS_USER` -- SSH username
- `VPS_SSH_PRIVATE_KEY` -- SSH private key
- `POSTGRES_PASSWORD` -- database password
- `JWT_SIGNING_KEY` -- JWT signing key
- `EODHD_API_KEY` -- EODHD API key
- `ALPHA_VANTAGE_API_KEY` -- AlphaVantage API key
- `BROKER_ENCRYPTION_KEY` -- AES-256 encryption key
- `SNAPTRADE_CLIENT_ID` -- SnapTrade client ID
- `SNAPTRADE_CONSUMER_KEY` -- SnapTrade consumer key
- `GOOGLE_CLIENT_ID` -- Google OAuth client ID
- `GOOGLE_CLIENT_SECRET` -- Google OAuth client secret

---

## Terraform

All modules use `hashicorp/google ~> 5.0`. Located in `infra/terraform/`.

### modules/cloud-sql/ -- PostgreSQL on Cloud SQL

Provisions a Cloud SQL PostgreSQL 16 instance with private networking.

| Resource | Details |
|----------|---------|
| `google_sql_database_instance.postgres` | PostgreSQL 16, private IP only (`ipv4_enabled = false`), deletion protection in prod |
| `google_sql_database.database` | Default name: `portfolio` |
| `google_sql_user.user` | Default name: `portfolio`, random 32-char password |
| `random_password.db_password` | 32 chars with special characters |
| `google_secret_manager_secret` | Stores DB password in Secret Manager |

**Backup configuration:**
- Enabled, starts at 03:00 UTC
- Point-in-time recovery: prod only
- Retention: 30 days (prod), 7 days (dev)
- Maintenance window: Sunday 4 AM UTC

**Query Insights:** Enabled, 1024 char query length, application tags recorded.

**Variables:** `project_id`, `region` (default `us-central1`), `instance_name`, `database_name`, `database_user`, `tier` (default `db-f1-micro`), `vpc_network_id`, `environment`

**Outputs:** `connection_name`, `private_ip_address`, `database_name`, `database_user`, `password_secret_id`

### modules/cloud-storage/ -- Frontend Static Hosting

Provisions a GCS bucket for SPA hosting.

| Resource | Details |
|----------|---------|
| `google_storage_bucket.frontend` | SPA routing via `not_found_page = "index.html"`, CORS for GET/HEAD, uniform bucket-level access |
| `google_storage_bucket_iam_member.public_access` | `roles/storage.objectViewer` for `allUsers` |

**Configuration:**
- Versioning: prod only
- Lifecycle: delete archived objects after 30 days
- CORS: `origin = ["*"]`, methods GET/HEAD, max age 3600s

**Variables:** `project_id`, `bucket_name`, `location` (default `US`), `environment`

**Outputs:** `bucket_name`, `bucket_url`, `website_url`

### modules/cloud-run/ -- Backend Service

Deploys Spring Boot backend as a Cloud Run v2 service with Cloud SQL Auth Proxy sidecar.

| Resource | Details |
|----------|---------|
| `google_cloud_run_v2_service.backend` | Main backend container + Cloud SQL Auth Proxy sidecar (`gcr.io/cloud-sql-connectors/cloud-sql-proxy:2.8.0`) |
| `google_service_account.cloud_run_sa` | Dedicated service account |
| `google_project_iam_member.cloud_sql_client` | `roles/cloudsql.client` |
| `google_secret_manager_secret_iam_member` | `roles/secretmanager.secretAccessor` for DB password |

**Container configuration:**
- Port: 8080
- Resources: 1 CPU, 512Mi memory
- Startup probe: `/health` (10s initial delay, 10s period, 3 failures)
- Liveness probe: `/health` (30s period, 3 failures)
- VPC access: private ranges only via VPC connector
- Scaling: configurable min (default 0) and max (default 10) instances
- Traffic: 100% to latest revision

**Environment variables injected:**
- `SPRING_PROFILES_ACTIVE`, `APP_ENVIRONMENT`, `DATABASE_URL`, `DATABASE_USERNAME`
- `DATABASE_PASSWORD` from Secret Manager

### modules/load-balancer/ -- Global HTTPS Load Balancer

Routes traffic to Cloud Storage (frontend) and Cloud Run (backend).

| Resource | Details |
|----------|---------|
| `google_compute_global_address` | Reserved global external IP |
| `google_compute_managed_ssl_certificate` | Google-managed SSL for domain |
| `google_compute_backend_bucket` | Frontend bucket with Cloud CDN enabled |
| `google_compute_backend_service` | Backend Cloud Run NEG, HTTPS protocol, 30s timeout, full logging |
| `google_compute_url_map` | Routes `/api/*` and `/health` to Cloud Run, everything else to bucket |
| `google_compute_target_https_proxy` | HTTPS proxy with SSL certificate |
| `google_compute_global_forwarding_rule` (HTTPS) | Port 443 forwarding |
| `google_compute_url_map` (redirect) | HTTP-to-HTTPS 301 redirect |
| `google_compute_target_http_proxy` (redirect) | HTTP proxy for redirect |
| `google_compute_global_forwarding_rule` (HTTP) | Port 80 forwarding for redirect |

**CDN policy:**
- Cache mode: `CACHE_ALL_STATIC`
- Default TTL: 3600s, Max TTL: 86400s
- Negative caching enabled
- Serve while stale: 86400s

### modules/workload-identity/ -- GitHub Actions OIDC Auth

Enables keyless authentication from GitHub Actions to GCP via OIDC.

| Resource | Details |
|----------|---------|
| `google_service_account.github_actions` | Service account for CI/CD |
| `google_iam_workload_identity_pool.github` | Pool ID: `github-pool` |
| `google_iam_workload_identity_pool_provider.github` | Provider ID: `github-provider`, OIDC issuer: `https://token.actions.githubusercontent.com` |
| `google_service_account_iam_member` | `roles/iam.workloadIdentityUser` for repo principal |

**Attribute mapping:**
- `google.subject` = `assertion.sub`
- `attribute.actor`, `attribute.repository`, `attribute.repository_owner`, `attribute.repository_id`
- Condition: `assertion.repository_owner == '<github_org>'`

**Granted roles:**
- `roles/run.admin` -- Cloud Run management
- `roles/storage.admin` -- Cloud Storage management
- `roles/artifactregistry.writer` -- Docker image push
- `roles/iam.serviceAccountUser` -- Service account impersonation

### environments/dev/main.tf -- Dev Environment

- Terraform >= 1.5.0
- VPC: `portfolio-vpc` with subnet `10.0.0.0/24`, private Google access
- VPC connector: `portfolio-connector` at `10.8.0.0/28`
- Cloud SQL: `portfolio-db-dev`, tier `db-f1-micro`
- Cloud Storage: `portfolio-frontend-dev-${project_id}`
- Workload Identity: configured
- Cloud Run: commented out (requires initial image push first)
- State backend: GCS commented out (`portfolio-terraform-state-dev`)

### environments/prod/main.tf -- Prod Environment

Same structure as dev with production adjustments:
- Cloud SQL: `portfolio-db-prod`, tier `db-custom-2-4096` (2 vCPU, 4GB RAM)
- Cloud Storage: `portfolio-frontend-prod-${project_id}`
- Cloud Run and Load Balancer modules: commented out (require domain verification)
- Additional variable: `domain` for the application domain name
- State backend: GCS commented out (`portfolio-terraform-state-prod`)

---

## GCP Deployment Setup

### Prerequisites

1. **GCP Projects**: Create `portfolio-dev` and `portfolio-prod` projects
2. **Enable APIs** in each project:
   ```bash
   gcloud services enable \
       run.googleapis.com \
       sqladmin.googleapis.com \
       compute.googleapis.com \
       artifactregistry.googleapis.com \
       secretmanager.googleapis.com \
       iam.googleapis.com \
       cloudresourcemanager.googleapis.com \
       vpcaccess.googleapis.com
   ```
3. **Create Artifact Registry**:
   ```bash
   gcloud artifacts repositories create portfolio \
       --repository-format=docker \
       --location=us-central1 \
       --description="Portfolio application images"
   ```

### Initial Terraform Setup

```bash
cd infra/terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
terraform apply
```

Get the Workload Identity Provider name after apply:
```bash
terraform output workload_identity_provider
```

### Monitoring

- **Cloud Run:** Logs in Cloud Console, metrics for CPU/memory/request count/latency, automatic error reporting
- **Cloud SQL:** Query insights enabled, performance metrics in Cloud Console

**Recommended alerts:**
1. Cloud Run error rate > 1%
2. Cloud Run latency p99 > 5s
3. Cloud SQL CPU > 80%
4. Cloud SQL connections > 80% of max

### Rollback Procedures

**Cloud Run:**
```bash
gcloud run revisions list --service portfolio-backend
gcloud run services update-traffic portfolio-backend --to-revisions REVISION_NAME=100
```

**Frontend:**
```bash
gcloud storage cp -r gs://portfolio-frontend-backup/* gs://portfolio-frontend/
```

### Troubleshooting

| Issue | Steps |
|-------|-------|
| Cloud Run not starting | Check logs: `gcloud run logs read portfolio-backend`, verify env vars, check Secret Manager access |
| Database connection failed | Verify Cloud SQL Auth Proxy sidecar running, check VPC Connector status, verify `roles/cloudsql.client` on service account |
| Frontend 404 errors | Verify index.html in bucket, check nginx.conf SPA routing, verify bucket permissions |

---

## Nginx

### infra/nginx/devpc.nanobyte.ca.conf -- VPS Reverse Proxy

Full Nginx configuration for the VPS deployment at `devpc.nanobyte.ca`.

**HTTP server (port 80):** 301 redirect to HTTPS.

**HTTPS server (port 443):**
- SSL: Let's Encrypt certificates at `/etc/letsencrypt/live/devpc.nanobyte.ca/`
- HTTP/2 enabled

**Security headers:**
- `X-Frame-Options: SAMEORIGIN`
- `X-Content-Type-Options: nosniff`
- `X-XSS-Protection: 1; mode=block`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `Referrer-Policy: strict-origin-when-cross-origin`

**Gzip:** Enabled for text, CSS, XML, JavaScript, JSON. Min length 1024.

**Client body size:** 10M max.

**Proxy locations:**

| Location | Upstream | Notes |
|----------|----------|-------|
| `/api/` | `http://127.0.0.1:8080/api/` | 60s connect/send/read timeout |
| `/auth/` | `http://127.0.0.1:8080/auth/` | 60s timeouts |
| `/admin/` | `http://127.0.0.1:8080/admin/` | 60s timeouts |
| `/health` | `http://127.0.0.1:8080/health` | Minimal headers |
| `/actuator/` | `http://127.0.0.1:8080/actuator/` | Monitoring |
| `/` | `http://127.0.0.1:3000/` | Frontend SPA, WebSocket upgrade support |

All proxy locations set `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto` headers.

### frontend/nginx.conf -- Frontend Container

Serves static files inside the frontend Docker container.

- Root: `/usr/share/nginx/html`
- Gzip: enabled for text, CSS, XML, JavaScript
- Security headers: `X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`
- Static asset caching: 1 year with `Cache-Control: public, immutable` for js, css, images, fonts
- SPA routing: `try_files $uri $uri/ /index.html`
- Health endpoint: `/nginx-health` returns 200 "healthy\n" (access log off)

---

## Scripts

All scripts located in `scripts/`.

### setup-vps.sh -- Idempotent VPS Preparation

**Usage:** `./setup-vps.sh` (run as non-root user with sudo privileges)

**Steps:**
1. Updates system packages (`apt-get update && upgrade`)
2. Creates `/opt/portfolio` deployment directory
3. Installs Docker (if not present) via `get.docker.com`, adds user to docker group
4. Installs Docker Compose plugin (if not present)
5. Installs Nginx (if not present)
6. Installs Certbot with Nginx plugin (if not present)
7. Installs curl (for health checks)
8. Configures UFW firewall: allows ports 22 (SSH), 80 (HTTP), 443 (HTTPS)

**Post-run instructions printed:**
- Copy Nginx config to `/etc/nginx/sites-available/`
- Enable site with symlink to `sites-enabled/`
- Obtain SSL certificate via `certbot --nginx`
- Test and reload Nginx

### setup-nginx-ssl.sh -- Automated Nginx + Let's Encrypt SSL

**Usage:** `sudo bash /tmp/setup-nginx-ssl.sh your-email@example.com`

**Prerequisites:** Domain must point to VPS IP, Docker containers must be running.

**Steps:**
1. Install nginx, certbot, python3-certbot-nginx, curl
2. Configure UFW (ports 80, 443)
3. Create initial HTTP-only nginx config for Certbot verification
4. Enable site, remove default, reload nginx
5. Obtain SSL certificate via `certbot --nginx --non-interactive`
6. Replace nginx config with full SSL version (security headers, gzip, proxy locations)
7. Test and reload nginx
8. Verify auto-renewal with `certbot renew --dry-run`
9. Run health checks against `https://devpc.nanobyte.ca`

### vps-diagnose.sh -- Diagnostic Tool

**Usage:** `ssh user@vps 'bash -s' < scripts/vps-diagnose.sh`

**Checks performed:**
1. Directory structure (`/opt/portfolio`, `/opt/portfolio/current`)
2. Docker compose files found
3. Tarball presence and contents
4. Docker and Docker Compose installation
5. Running containers (names, status, ports)
6. Docker compose project status
7. `.env` file existence and keys defined
8. Backend health check (`http://localhost:8080/health`)
9. Frontend health check (`http://localhost:3000`)

### vps-fix-deployment.sh -- Manual Deployment Recovery

**Usage:** `ssh user@vps 'bash -s' < scripts/vps-fix-deployment.sh`

**Steps:**
1. Extract tarball if present (backs up current to previous)
2. Verify required files exist
3. Check `.env` file (prints template if missing)
4. Stop existing containers, rebuild with `--no-cache`, start
5. Wait 30 seconds, run health check
6. Print backend logs on failure

---

## Environment Files

### config/.env.example -- Template

Reference template with all configurable variables and placeholder values. Located at `config/.env.example` in the repository root.

| Variable | Default |
|----------|---------|
| `POSTGRES_DB` | `portfolio` |
| `POSTGRES_USER` | `portfolio` |
| `POSTGRES_PASSWORD` | `changeme` |
| `APP_VERSION` | `0.0.1-SNAPSHOT` |
| `APP_ENVIRONMENT` | `local` |
| `SPRING_PROFILES_ACTIVE` | `local` |
| `VITE_API_URL` | `http://localhost:8080` |
| `SNAPTRADE_CLIENT_ID` | (empty) |
| `SNAPTRADE_CONSUMER_KEY` | (empty) |
| `SNAPTRADE_REDIRECT_URI` | `http://localhost:3000/brokers/connections` |
| `BROKER_ENCRYPTION_KEY` | (empty) |

### Environment Files (Consolidated)

Previously `.env.local`, `.env.dev`, and `.env.prod` were at the repository root. These have been consolidated into `config/.env.example` which serves as the single template. Per-environment values are now configured via deployment pipelines (GitHub Secrets for VPS, GCP Secret Manager for production).

Key environment differences:
- **Local**: Default `portfolio` password for database, SnapTrade test credentials
- **VPS (devpc.nanobyte.ca)**: `VITE_API_URL=https://devpc.nanobyte.ca`, secrets from GitHub Secrets
- **Production (GCP)**: `VITE_API_URL=https://api.portfolio.example.com`, `SPRING_PROFILES_ACTIVE=prod`, secrets from GCP Secret Manager

---

## Local Development

### Prerequisites
- Docker and Docker Compose
- Node.js 20 (for frontend development)
- Git
- **No local JDK required** -- all backend compilation runs inside Docker containers

### Quick Start

```bash
git clone <repo-url>
cd portfolio-app
cp config/.env.example .env

# Start all services
docker compose up -d

# Verify
curl http://localhost:8080/health
curl http://localhost:8080/api/v1/version

# Open frontend
open http://localhost:3000
```

### Running Services Individually

```bash
# Database only
docker compose up -d postgres

# Backend (inside Docker -- no local JDK)
docker compose exec backend ./gradlew bootRun

# Frontend (local Node.js)
cd frontend
npm install
npm run dev
```

### Debugging

**Backend:** Docker Compose exposes debug port 5005 (JDWP). Connect your IDE remote debugger to `localhost:5005`.

**Frontend:** React Developer Tools browser extension + Vite-provided source maps.

### Common Tasks

| Task | Command |
|------|---------|
| Reset database | `docker compose down -v && docker compose up -d` |
| Run backend tests | `docker compose exec backend ./gradlew test` |
| Run specific test | `docker compose exec backend ./gradlew test --tests "HealthControllerTest"` |
| Run frontend tests | `cd frontend && npm run test:run` |
| Frontend lint | `cd frontend && npm run lint` |
| Frontend build | `cd frontend && npm run build` |
| Check outdated deps (backend) | `docker compose exec backend ./gradlew dependencyUpdates` |
| Check outdated deps (frontend) | `cd frontend && npm outdated` |
| Tail backend logs | `docker compose logs -f backend` |

### Code Style

**Backend (Kotlin):** Follow Kotlin coding conventions, use data classes for DTOs, prefer immutability, use meaningful names.

**Frontend (TypeScript):** ESLint rules enforced, functional components, TypeScript strict mode, React hooks best practices.

---

## Cross-References

- For detailed environment variable documentation and defaults, see [configurations.md](configurations.md)
- For database schema and migration details, see [database-schema.md](database-schema.md)
- For backend service architecture, see [backend-services.md](backend-services.md)
