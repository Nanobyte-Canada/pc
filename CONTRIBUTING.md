# Contributing to Portfolio Construction App

## Tech Stack Constraints
- Backend: Kotlin 2.0.21, Spring Boot 3.3.5, JDK 21
- Frontend: React 18, TypeScript 5.6, Vite 5 (use apiFetch() for all API calls)
- DB: PostgreSQL 16, Flyway migrations, Hibernate DDL=validate
- Testing: MockK (backend), Vitest + Testing Library (frontend)
- No Tailwind CSS — use plain CSS with custom properties
- No local JDK — backend work runs inside Docker

## PR Checklist
- [ ] All existing tests pass locally (./gradlew test / npm run test:run)
- [ ] New tests for new logic
- [ ] No breaking API contract changes without documentation
- [ ] Flyway migration scripts follow V##__description.sql naming
- [ ] Frontend uses apiFetch() for all API calls
- [ ] No secrets or keys in code (use config/.env.example pattern)
- [ ] Lint passes (npm run lint)

## Code Review Guidelines
The automated PR review checks for:
- Logic bugs, null safety, edge cases, error handling
- Breaking API/schema changes
- Security: auth gaps, injection risks, secret exposure
- Test coverage gaps
- Documentation staleness
- Violations of the constraints above

Human reviewers should focus on design decisions, architecture, and business logic
that automated review cannot assess.
