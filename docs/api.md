# API Documentation

## Base URL

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080` |
| Development | `https://api-dev.portfolio.example.com` |
| Production | `https://api.portfolio.example.com` |

## Endpoints

### Health Check

Check application health status.

**Request:**
```
GET /health
```

**Response:**
```json
{
  "status": "UP",
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

**Status Codes:**
- `200 OK` - Application is healthy
- `503 Service Unavailable` - Application is unhealthy

---

### Version

Get application version and environment information.

**Request:**
```
GET /api/v1/version
```

**Response:**
```json
{
  "version": "0.0.1-SNAPSHOT",
  "environment": "local"
}
```

**Response Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `version` | string | Application version (from `APP_VERSION` env var) |
| `environment` | string | Current environment (local/dev/prod) |

**Status Codes:**
- `200 OK` - Success

---

## Error Responses

All errors follow a consistent format:

```json
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Resource not found",
  "path": "/api/v1/unknown"
}
```

### Common Status Codes

| Code | Description |
|------|-------------|
| `200` | Success |
| `400` | Bad Request - Invalid input |
| `401` | Unauthorized - Authentication required |
| `403` | Forbidden - Insufficient permissions |
| `404` | Not Found - Resource doesn't exist |
| `500` | Internal Server Error |
| `503` | Service Unavailable |

---

## Future Endpoints (Placeholder)

These endpoints are planned but not yet implemented:

### Portfolios

```
GET    /api/v1/portfolios           # List portfolios
POST   /api/v1/portfolios           # Create portfolio
GET    /api/v1/portfolios/{id}      # Get portfolio
PUT    /api/v1/portfolios/{id}      # Update portfolio
DELETE /api/v1/portfolios/{id}      # Delete portfolio
```

### ETFs

```
GET    /api/v1/etfs                 # Search ETFs
GET    /api/v1/etfs/{ticker}        # Get ETF details
```

### Mutual Funds

```
GET    /api/v1/funds                # Search mutual funds
GET    /api/v1/funds/{symbol}       # Get fund details
```

---

## Rate Limiting

Currently no rate limiting is implemented. Future considerations:
- 100 requests per minute per IP (unauthenticated)
- 1000 requests per minute per user (authenticated)

---

## CORS

Cross-Origin Resource Sharing is configured for:
- `http://localhost:3000` (local development)
- `https://portfolio.example.com` (production)

---

## Authentication (Future)

When implemented, authentication will use:
- JWT tokens in `Authorization: Bearer <token>` header
- Token refresh endpoint
- OAuth2 integration options
