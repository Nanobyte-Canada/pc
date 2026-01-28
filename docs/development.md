# Development Guide

## Getting Started

### Prerequisites
- Docker and Docker Compose
- JDK 21 (for local backend development)
- Node.js 20 (for local frontend development)
- Git

### Clone and Setup

```bash
git clone https://github.com/your-org/portfolio-app.git
cd portfolio-app

# Copy environment file
cp .env.example .env.local
```

## Running Locally

### Option 1: Docker Compose (Recommended)

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down

# Reset database
docker-compose down -v
docker-compose up -d
```

### Option 2: Individual Services

**Database:**
```bash
docker-compose up -d postgres
```

**Backend:**
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

## Project Structure

```
portfolio-app/
├── backend/              # Kotlin Spring Boot API
│   ├── src/main/
│   │   ├── kotlin/       # Application code
│   │   └── resources/    # Configuration files
│   ├── src/test/         # Tests
│   └── build.gradle.kts  # Build configuration
│
├── frontend/             # React TypeScript SPA
│   ├── src/              # Application code
│   ├── public/           # Static assets
│   └── package.json      # Dependencies
│
├── infra/                # Infrastructure as code
│   └── terraform/        # Terraform modules
│
├── scripts/              # Utility scripts
├── docs/                 # Documentation
└── docker-compose.yml    # Local development
```

## Backend Development

### Running Tests

```bash
cd backend

# All tests
./gradlew test

# Specific test
./gradlew test --tests "HealthControllerTest"

# With coverage
./gradlew test jacocoTestReport
```

### Adding Dependencies

Edit `build.gradle.kts`:
```kotlin
dependencies {
    implementation("new:dependency:version")
}
```

### Database Migrations

Add new migration in `src/main/resources/db/migration/`:
```
V2__add_portfolios_table.sql
```

Migrations run automatically on startup.

### Configuration

Environment-specific config in `src/main/resources/`:
- `application.yml` - Base config
- `application-local.yml` - Local development
- `application-dev.yml` - Development environment
- `application-prod.yml` - Production environment

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/api/v1/version` | GET | Application version |

## Frontend Development

### Running Tests

```bash
cd frontend

# Watch mode
npm test

# Single run
npm run test:run

# With coverage
npm run test:coverage
```

### Adding Dependencies

```bash
npm install package-name
npm install -D dev-package-name
```

### Environment Variables

Create `.env` files for different environments:
```
VITE_API_URL=http://localhost:8080
```

Access in code:
```typescript
const apiUrl = import.meta.env.VITE_API_URL
```

### Building

```bash
npm run build
```

Output in `dist/` directory.

## Code Style

### Backend (Kotlin)
- Follow Kotlin coding conventions
- Use data classes for DTOs
- Prefer immutability
- Use meaningful names

### Frontend (TypeScript)
- ESLint rules enforced
- Prefer functional components
- Use TypeScript strict mode
- Follow React hooks best practices

## Debugging

### Backend
IDE debug configuration:
- Main class: `com.portfolio.ApplicationKt`
- Program arguments: `--spring.profiles.active=local`

Or with Docker:
```bash
docker-compose up backend
# Debug port available at 5005
```

### Frontend
- React Developer Tools (browser extension)
- Vite provides source maps automatically
- Console logging with `console.log()`

## Common Tasks

### Reset Database
```bash
docker-compose down -v
docker-compose up -d postgres
```

### Update Dependencies

**Backend:**
```bash
cd backend
./gradlew dependencyUpdates
```

**Frontend:**
```bash
cd frontend
npm outdated
npm update
```

### Generate API Types
If adding OpenAPI, generate TypeScript types:
```bash
npm run generate-types
```
