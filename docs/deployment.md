# Deployment Guide

## Prerequisites

1. **GCP Projects**: Create two GCP projects
   - `portfolio-dev` for development
   - `portfolio-prod` for production

2. **Enable APIs**: In each project, enable:
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

## Initial Infrastructure Setup

### 1. Configure Terraform

```bash
cd infra/terraform/environments/dev

# Copy and fill in variables
cp terraform.tfvars.example terraform.tfvars

# Initialize Terraform
terraform init

# Review plan
terraform plan

# Apply infrastructure
terraform apply
```

### 2. Set Up Workload Identity Federation

The Terraform modules create the Workload Identity Pool. Get the provider name:

```bash
terraform output workload_identity_provider
```

### 3. Configure GitHub Secrets

In your GitHub repository, add these secrets:

| Secret | Value |
|--------|-------|
| `GCP_PROJECT_NUMBER` | Your GCP project number |

Add these in GitHub Environments (`dev` and `prod`).

## CI/CD Workflow

### Pull Request
1. Opens PR to `main`
2. CI workflow runs:
   - Backend tests (JDK 21, Gradle)
   - Frontend tests (Node 20, npm)
   - Docker build verification
3. PR blocked until all checks pass

### Deployment (main branch)
1. PR merged to `main`
2. Deploy workflow runs:
   - Authenticates via Workload Identity Federation
   - Builds backend Docker image
   - Pushes to Artifact Registry
   - Deploys to Cloud Run
   - Builds frontend
   - Uploads to Cloud Storage
   - Invalidates CDN cache

### Manual Deployment
Use workflow dispatch for specific environment:

1. Go to Actions → Deploy workflow
2. Click "Run workflow"
3. Select environment (dev/prod)

## Local Development

### Quick Start

```bash
# Start all services
docker-compose up -d

# Verify
curl http://localhost:8080/health
curl http://localhost:8080/api/v1/version

# Open frontend
open http://localhost:3000
```

### Running Without Docker

**Backend:**
```bash
# Start PostgreSQL only
docker-compose up -d postgres

# Run backend
cd backend
./gradlew bootRun
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

## Terraform Modules

### Cloud Run (`infra/terraform/modules/cloud-run`)
- Deploys Spring Boot container
- Configures Cloud SQL Auth Proxy sidecar
- Sets up health checks and scaling

### Cloud SQL (`infra/terraform/modules/cloud-sql`)
- Creates PostgreSQL instance
- Configures private IP networking
- Sets up automated backups
- Stores password in Secret Manager

### Cloud Storage (`infra/terraform/modules/cloud-storage`)
- Creates bucket for static frontend
- Configures public access
- Sets up SPA routing (404 → index.html)

### Load Balancer (`infra/terraform/modules/load-balancer`)
- HTTPS Load Balancer with managed SSL
- Routes `/api/*` to Cloud Run
- Routes `/*` to Cloud Storage
- HTTP → HTTPS redirect

### Workload Identity (`infra/terraform/modules/workload-identity`)
- Creates service account for GitHub Actions
- Configures OIDC provider for GitHub
- Grants necessary IAM permissions

## Monitoring

### Cloud Run
- View logs: Cloud Console → Cloud Run → Logs
- Metrics: CPU, memory, request count, latency
- Error reporting: Automatic stack trace collection

### Cloud SQL
- Query insights enabled
- Performance metrics in Cloud Console
- Automated alerting configurable

### Recommended Alerts
1. Cloud Run error rate > 1%
2. Cloud Run latency p99 > 5s
3. Cloud SQL CPU > 80%
4. Cloud SQL connections > 80% of max

## Rollback

### Cloud Run
```bash
# List revisions
gcloud run revisions list --service portfolio-backend

# Route traffic to previous revision
gcloud run services update-traffic portfolio-backend \
    --to-revisions REVISION_NAME=100
```

### Frontend
```bash
# Upload previous build
gcloud storage cp -r gs://portfolio-frontend-backup/* gs://portfolio-frontend/
```

## Troubleshooting

### Cloud Run Not Starting
1. Check logs: `gcloud run logs read portfolio-backend`
2. Verify environment variables
3. Check Secret Manager access

### Database Connection Failed
1. Verify Cloud SQL Auth Proxy sidecar is running
2. Check VPC Connector status
3. Verify service account has Cloud SQL Client role

### Frontend 404 Errors
1. Verify index.html exists in bucket
2. Check nginx.conf SPA routing
3. Verify bucket permissions
