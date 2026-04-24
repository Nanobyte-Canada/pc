# Agent Reference Index

Master navigation hub for AI coding agents working on the Portfolio Construction App.

## Repository Overview

Monorepo for a portfolio construction and analysis application using public ETFs.

| Layer | Stack |
|-------|-------|
| Backend | Kotlin 2.0.21, Spring Boot 3.3.5, JDK 21, JPA/Hibernate, Flyway |
| Frontend | React 18.3.1, TypeScript 5.6.3, Vite 5.4.9, React Query, Zustand, AG Grid/Charts |
| Database | PostgreSQL 16 (53 tables, 1 view, managed by Flyway) |
| Cache | Redis 7 (Spring Data Redis) |
| Broker | Broker Gateway Service (port 8084) |
| Auth | Spring Security + JWT (HttpOnly cookies) + Google OAuth2 |
| Ingestion | Separate Spring Boot 3.3.5 microservice (port 8081), `ingestion` schema |
| Infra | Docker Compose (local), Terraform (GCP), Nginx (VPS) |

## Reference Files

| File | Description |
|------|-------------|
| [database-schema.md](database-schema.md) | All 53 database tables, 1 view, 216 indexes, 53 foreign keys, complete column definitions, and Flyway migration history (V1 through V65). The primary schema reference. |
| [api-endpoints.md](api-endpoints.md) | Every REST endpoint grouped by controller, with HTTP methods, paths, request/response types, and auth requirements. |
| [backend-services.md](backend-services.md) | All services, schedulers, adapters, configuration classes, and their responsibilities. Includes dependency injection patterns and key algorithms. |
| [frontend-map.md](frontend-map.md) | Complete frontend directory: components, pages, hooks, stores, services, types, CSS files, and test coverage. |
| [entity-relationships.md](entity-relationships.md) | JPA entities, DTOs, enum values, relationship graph, and the mapping between entities and database tables. |
| [infrastructure.md](infrastructure.md) | Docker Compose, CI/CD workflows (GitHub Actions), Terraform modules, Nginx config, deploy scripts, and environment files. |
| [configurations.md](configurations.md) | Spring profiles (local/dev/prod), all environment variables, feature flags, rate limit settings, security configuration, and Redis cache keys. |
| [unused-legacy.md](unused-legacy.md) | Dead code, orphan database tables, redundant services, unused widgets, and deprecated migration artifacts. |
| [improvements.md](improvements.md) | Improvement recommendations organized by priority and effort. Tracks completed items. |
| [ingestion-workflow.md](ingestion-workflow.md) | Ingestion microservice pipeline: Exchange Sync, Universe Discovery, Raw Data Fetch with batching and rate limiting. |

## Quick Reference: Common Agent Tasks

### Adding a New REST Endpoint

1. Define the DTO in `backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/` -- see [entity-relationships.md](entity-relationships.md)
2. Add or extend a controller in `backend/portfolio/src/main/kotlin/com/portfolio/broker/controller/` -- see [api-endpoints.md](api-endpoints.md)
3. Implement service logic in `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/` -- see [backend-services.md](backend-services.md)
4. Add frontend service function in `frontend/src/services/` using `apiFetch()` -- see [frontend-map.md](frontend-map.md)
5. Create React Query hook in `frontend/src/hooks/` -- see [frontend-map.md](frontend-map.md)

### Adding a Database Table

1. Find the highest migration number in [database-schema.md](database-schema.md) (currently V72) and increment by 1
2. Create `V73__description.sql` in `backend/portfolio/src/main/resources/db/migration/`
3. Create JPA entity in the appropriate `entity/` package -- see [entity-relationships.md](entity-relationships.md)
4. Create Spring Data repository interface -- see [backend-services.md](backend-services.md)
5. Add DTO for API responses -- never expose entities directly
6. Validate: `docker compose exec backend ./gradlew test`

### Adding a Frontend Page

1. Create page component in `frontend/src/pages/` -- see [frontend-map.md](frontend-map.md)
2. Add route in `frontend/src/App.tsx`
3. Create service module in `frontend/src/services/` using `apiFetch`
4. Create React Query hook in `frontend/src/hooks/`
5. Add companion CSS file alongside the page component
6. Validate: `npm run build && npm run test:run && npm run lint` (from `frontend/`)

### Adding a Dashboard Widget

1. Create widget component in `frontend/src/components/dashboard/widgets/`
2. Register widget key in the dashboard grid configuration
3. Add backend endpoint if new data is needed -- see [api-endpoints.md](api-endpoints.md)
4. Add migration to insert default `dashboard_preferences` row if needed -- see [database-schema.md](database-schema.md)

### Modifying the Data Ingestion Pipeline

The ingestion pipeline has been moved to a separate microservice at `backend/ingestion/` (port 8081).

1. Review the pipeline architecture in [ingestion-workflow.md](ingestion-workflow.md)
2. Review ingestion-service package structure in [backend-services.md](backend-services.md)
3. Strategy pattern: implement `DataProvider` interface, register in `ProviderRegistry`
4. Pipeline steps: `ExchangeSyncStep` -> `UniverseSyncStep` -> `RawDataFetchStep`
5. Schema: `ingestion` schema with its own Flyway migrations at `backend/ingestion/src/main/resources/db/migration/`
6. The main backend still has legacy ingestion code in `backend/portfolio/src/.../ingestion/` for EODHD/AV/ETF.com enrichment

### Adding a Broker Integration Feature

1. Add methods to `BrokerGatewayClient` for new gateway endpoints -- see [backend-services.md](backend-services.md)
2. Add service method in `BrokerService` or specialized service
3. Add controller endpoint -- see [api-endpoints.md](api-endpoints.md)
4. Key tables: `broker_connections`, `broker_positions`, `broker_activities` -- see [database-schema.md](database-schema.md)
5. Broker credentials are managed by the broker-gateway service; portfolio service communicates via `BrokerGatewayClient` using `GATEWAY_API_KEY` -- see [configurations.md](configurations.md)

### Debugging a Schema Issue

1. Check [database-schema.md](database-schema.md) for the current table definition and all columns
2. Check the Flyway migration history section for when columns were added/removed
3. Check [unused-legacy.md](unused-legacy.md) for orphan tables with no JPA entity
4. Run `docker compose exec backend ./gradlew test` to verify Hibernate validation passes

## Key Constraints

- **No local JDK**: All backend compilation/testing must run inside Docker containers
- **Hibernate DDL = validate**: Schema is managed exclusively by Flyway migrations
- **No Tailwind CSS**: Use plain CSS with CSS custom properties
- **API calls**: Frontend must always use `apiFetch()` from `services/api.ts`
- **Testing**: Backend uses MockK (not Mockito); frontend uses Vitest + Testing Library
