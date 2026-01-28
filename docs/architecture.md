# Architecture Overview

## System Components

```
                                    ┌─────────────────────────────────┐
                                    │    HTTPS Load Balancer          │
                                    │    (Global, TLS termination)    │
                                    └───────────────┬─────────────────┘
                                                    │
                            ┌───────────────────────┴───────────────────────┐
                            │                                               │
                            ▼                                               ▼
                ┌───────────────────────┐                       ┌───────────────────────┐
                │   Cloud Storage       │                       │     Cloud Run         │
                │   (Static Frontend)   │                       │   (Backend API)       │
                │                       │                       │                       │
                │   - React SPA build   │                       │ ┌─────────────────┐   │
                │   - index.html        │                       │ │  Spring Boot    │   │
                │   - JS/CSS assets     │                       │ │  Application    │   │
                │   - Cloud CDN         │                       │ └────────┬────────┘   │
                └───────────────────────┘                       │          │            │
                                                                │ ┌────────▼────────┐   │
                                                                │ │ Cloud SQL Auth  │   │
                                                                │ │ Proxy (sidecar) │   │
                                                                │ └────────┬────────┘   │
                                                                └──────────┼────────────┘
                                                                           │
                                                                           ▼
                                                                ┌───────────────────────┐
                                                                │     Cloud SQL         │
                                                                │    (PostgreSQL 16)    │
                                                                │                       │
                                                                │   - Private IP only   │
                                                                │   - Auto backups      │
                                                                │   - Point-in-time     │
                                                                └───────────────────────┘
```

## Request Flow

### Frontend Requests
1. User accesses `https://portfolio.example.com`
2. HTTPS Load Balancer terminates TLS
3. Request routed to Cloud Storage backend bucket
4. Cloud CDN serves cached content (or fetches from bucket)
5. React SPA loads in browser

### API Requests
1. Frontend makes request to `/api/v1/version`
2. HTTPS Load Balancer routes to Cloud Run backend service
3. Cloud Run container handles request
4. If database access needed, Cloud SQL Auth Proxy sidecar connects to Cloud SQL
5. Response returned through load balancer

## GCP Resources

| Resource | Purpose | Configuration |
|----------|---------|---------------|
| **Cloud Run** | Backend API hosting | Autoscaling 0-10 instances, 1 vCPU, 512MB |
| **Cloud SQL** | PostgreSQL database | Private IP, automated backups |
| **Cloud Storage** | Static frontend hosting | Standard class, public access |
| **HTTPS Load Balancer** | Traffic routing + TLS | Global, managed SSL certificate |
| **Cloud CDN** | Frontend caching | Automatic with load balancer |
| **Secret Manager** | Credential storage | Database passwords, API keys |
| **Artifact Registry** | Docker images | Container registry |
| **Workload Identity Pool** | CI/CD authentication | GitHub Actions → GCP |
| **VPC Network** | Private networking | For Cloud SQL private IP |
| **VPC Connector** | Cloud Run ↔ VPC | Serverless VPC access |

## Environment Separation

### Strategy: Separate GCP Projects

```
portfolio-dev (GCP Project)
├── Cloud Run: portfolio-backend
├── Cloud SQL: portfolio-db-dev (db-f1-micro)
├── Cloud Storage: portfolio-frontend-dev
└── Artifact Registry: portfolio

portfolio-prod (GCP Project)
├── Cloud Run: portfolio-backend
├── Cloud SQL: portfolio-db-prod (db-custom-2-4096)
├── Cloud Storage: portfolio-frontend-prod
└── Artifact Registry: portfolio
```

### Benefits
- Complete blast radius isolation
- Environment-specific IAM policies
- Separate billing per environment
- Clear security boundaries

## Security

### Network Security
- Cloud SQL accessible only via private IP
- Cloud Run uses VPC Connector for database access
- Cloud SQL Auth Proxy handles authentication and encryption

### Authentication
- GitHub Actions uses Workload Identity Federation (no long-lived keys)
- Cloud Run service accounts with minimal permissions
- Secret Manager for all credentials

### TLS
- HTTPS Load Balancer with managed SSL certificates
- All internal traffic encrypted (Cloud SQL Auth Proxy)

## Scalability

### Cloud Run
- Autoscaling from 0 to 10 instances (configurable)
- Scales based on request concurrency
- Cold start mitigated by min instance setting in prod

### Cloud SQL
- Vertical scaling by changing machine type
- Read replicas available if needed
- Connection pooling via HikariCP

### Cloud Storage + CDN
- Effectively unlimited scale
- Global edge caching
- High availability by default
