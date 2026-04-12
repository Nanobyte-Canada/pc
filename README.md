# Portfolio Construction Application

A full-stack application for portfolio construction using public ETFs and Mutual Funds.

## Tech Stack

- **Backend**: Kotlin + Spring Boot 3
- **Frontend**: React + TypeScript + Vite
- **Database**: PostgreSQL 16
- **Migrations**: Flyway
- **Containerization**: Docker + Docker Compose
- **CI/CD**: GitHub Actions
- **Cloud**: GCP (Cloud Run, Cloud SQL, Cloud Storage, HTTPS Load Balancer)

## Project Structure

```
portfolio-app/
├── backend/
│   ├── portfolio/    # Kotlin Spring Boot API (main service)
│   └── ingestion/    # Data ingestion microservice
├── frontend/         # React TypeScript SPA
├── config/           # Environment configuration (.env.example)
├── infra/            # Terraform infrastructure
├── scripts/          # Utility scripts
├── docs/             # Documentation
└── docker-compose.yml
```

## Prerequisites

- Docker and Docker Compose
- JDK 21 (for local backend development)
- Node.js 20 (for local frontend development)

## Quick Start

### Using Docker Compose (Recommended)

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### Access Points

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **Health Check**: http://localhost:8080/health
- **Version**: http://localhost:8080/api/v1/version

## Local Development

### Backend

```bash
cd backend/portfolio

# Run with Gradle
./gradlew bootRun

# Run tests
./gradlew test

# Build JAR
./gradlew build
```

### Frontend

```bash
cd frontend

# Install dependencies
npm install

# Start dev server
npm run dev

# Run tests
npm test

# Build for production
npm run build
```

## Environment Configuration

Environment variables are configured per environment:

- `config/.env.example` - Template for all environments

Copy `config/.env.example` to `.env` and configure as needed.

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check endpoint |
| `/api/v1/version` | GET | Returns application version and environment |
| `/api/v1/model-portfolios` | GET, POST | Model portfolio management |
| `/api/v1/brokers/connections` | GET | List connected broker accounts |

See `docs/api.md` for complete API documentation.

## Data Pipeline

### Data Sources

- **EODHD**: Stocks & mutual funds universe discovery
- **etf.com**: ETF universe discovery & enrichment (holdings, sectors, performance)
- **Alpha Vantage**: Stock enrichment (fundamentals, financials)

### Pipeline Steps (Nightly)

1. **Universe Refresh** — Discover new tickers from EODHD
2. **ETF Universe** — Refresh ETF universe from etf.com
3. **Stock Ingestion** — Fetch raw stock data from Alpha Vantage
4. **Stock Enrichment** — Parse stock fundamentals into structured data
5. **ETF Enrichment** — Enrich ETFs from etf.com (holdings, sectors, performance)

### Admin Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/admin/ingestion/run` | POST | Run full ingestion pipeline |
| `/admin/ingestion/universe` | POST | Refresh universe from EODHD |
| `/admin/ingestion/stocks/run` | POST | Fetch raw stock data |
| `/admin/ingestion/etfcom/universe` | POST | Refresh ETF universe from etf.com |
| `/admin/enrichment/stocks/run` | POST | Enrich stocks from Alpha Vantage |
| `/admin/enrichment/etfcom/run` | POST | Enrich ETFs from etf.com |

## Features

### Portfolio Management
- **Model Portfolios**: Create and manage target allocation models with automatic rebalancing
- **Broker Integration**: Connect brokerage accounts via SnapTrade
- **Portfolio Analysis**: Sector exposure, risk metrics, and performance tracking
- **Drift Detection**: Automatic monitoring of portfolio deviation from target allocations
- **Rebalancing Automation**: Scheduled rebalancing with customizable frequency and drift thresholds

### Data Pipeline
- Multi-source data enrichment (EODHD, Alpha Vantage, ETF.com)
- Automated nightly ingestion and enrichment jobs
- ETF look-through analysis for true portfolio exposure
- Historical performance calculation

### Dashboard & Analytics
- Customizable widget-based dashboard
- Real-time portfolio metrics
- Connected accounts overview with model accuracy tracking
- Performance charts and reporting

## Testing

### Backend Tests

```bash
cd backend/portfolio
./gradlew test                    # All tests
./gradlew test --tests "*Unit*"   # Unit tests only
./gradlew test --tests "*Integration*"  # Integration tests
```

### Frontend Tests

```bash
cd frontend
npm test              # Run tests in watch mode
npm run test:coverage # Run with coverage report
```

## Docker

### Build Images

```bash
# Backend
docker build -t portfolio-backend ./backend/portfolio

# Frontend
docker build -t portfolio-frontend ./frontend
```

### Run Containers

```bash
# Using docker-compose (recommended)
docker-compose up -d

# Or run individually
docker run -p 8080:8080 portfolio-backend
docker run -p 3000:80 portfolio-frontend
```

## CI/CD

GitHub Actions workflows:

- **ci.yml**: Runs on PRs - lint, test, build
- **deploy.yml**: Runs on main branch - deploy to GCP

### Required GitHub Secrets

- `GCP_PROJECT_NUMBER`: GCP project number for Workload Identity
- Environment-specific secrets configured in GitHub Environments

## GCP Deployment

### Architecture

```
HTTPS Load Balancer
├── /* → Cloud Storage (React SPA)
└── /api/* → Cloud Run (Spring Boot)
           └── Cloud SQL (PostgreSQL)
```

### Resources

- **Cloud Run**: Backend API with autoscaling
- **Cloud SQL**: PostgreSQL database (private IP)
- **Cloud Storage**: Static frontend hosting
- **HTTPS Load Balancer**: TLS termination + routing
- **Secret Manager**: Secure credential storage
- **Artifact Registry**: Docker image storage

See `docs/deployment.md` for detailed deployment instructions.

## License

Private - All rights reserved
