# Options Expiry Redis Cache Design

**Date:** 2026-07-17
**Status:** Approved
**Scope:** Options expiry caching with scheduled refresh

---

## Problem

Options expiry dates are inconsistent (sometimes none, sometimes one, sometimes a list) because:
1. Current 24-hour Redis cache expires too quickly
2. IBKR connectivity is unreliable
3. No scheduled refresh mechanism

## Solution

Two-tier expiry sourcing with scheduled refresh and on-demand fallback.

### Core Logic

**Tier 1: Scheduled Refresh** (Primary)
- Weekly job (Monday 8 AM, configurable) fetches expirations for all configured symbols from IBKR
- Stores in Redis with 90-day TTL
- Key pattern: `expiry:{symbol}` → `List<LocalDate>`

**Tier 2: On-Demand Fallback** (Fallback)
- If `expiry:{symbol}` not in Redis, fetch from IBKR
- Store result in Redis with 90-day TTL
- Log warning that on-demand fetch occurred

### Configuration

```yaml
# application.yml
expiry:
  refresh:
    cron: "0 0 8 ? * MON"  # Monday 8 AM
    symbols:
      - SOXL
      - TECL
      - TQQQ
      - SPXU
      - SPY
      - QQQ
      - XLF
      - NVDA
      - AVGO
  cache:
    ttl-days: 90
```

**Environment Variable Overrides:**
- `EXPIRY_REFRESH_CRON` - Cron expression for refresh schedule
- `EXPIRY_REFRESH_SYMBOLS` - Comma-separated list of symbols
- `EXPIRY_CACHE_TTL_DAYS` - TTL in days

### Redis Key Strategy

| Key Pattern | Value | TTL | Purpose |
|-------------|-------|-----|---------|
| `expiry:{symbol}` | JSON `List<LocalDate>` | 90 days | Options expiry dates |

**Example:**
- Key: `expiry:SOXL`
- Value: `["2026-07-18","2026-07-25","2026-08-01",...]`
- TTL: 7776000 seconds (90 days)

### Deployment Safety

- Redis is separate per environment (local, UAT, prod)
- 90-day TTL survives deployments
- Scheduled refresh repopulates after any Redis wipe
- On-demand fallback handles edge cases

### Components

1. **ExpiryProperties** - Configuration properties class
2. **ExpiryCacheService** - Redis cache operations for expiry
3. **ExpiryRefreshService** - Scheduled refresh logic
4. **ChainController** - Updated to use new cache

### Flow Diagrams

**Scheduled Refresh:**
```
Monday 8 AM → ExpiryRefreshService
  → For each symbol in config:
    → ibkrClient.requestOptionExpirations(symbol)
    → expiryCacheService.cacheExpiry(symbol, expirations)
```

**On-Demand Fallback:**
```
GET /api/v1/chains/{symbol}/expirations
  → expiryCacheService.getExpiry(symbol)
  → If cache hit: return cached
  → If cache miss:
    → ibkrClient.requestOptionExpirations(symbol)
    → expiryCacheService.cacheExpiry(symbol, expirations)
    → return result
```

### Testing Strategy

1. **Unit Tests:**
   - ExpiryCacheService: cache operations
   - ExpiryRefreshService: refresh logic

2. **Integration Tests:**
   - End-to-end: API → Cache → IBKR → Cache → API
   - Verify 90-day TTL
   - Verify scheduled refresh

3. **Manual Verification:**
   - Check Redis: `docker exec portfolio-redis redis-cli GET "expiry:SOXL"`
   - Verify expiry dates are correct
   - Verify TTL: `docker exec portfolio-redis redis-cli TTL "expiry:SOXL"`

### Migration

- New key pattern `expiry:{symbol}` does not conflict with existing `expirations:{symbol}`
- Old `expirations:{symbol}` keys will expire naturally (24-hour TTL)
- No migration needed

---

## Success Criteria

1. Expiry dates load consistently (no more inconsistent behavior)
2. Expiry dates are cached for 90 days
3. Scheduled refresh runs weekly (configurable)
4. On-demand fallback works when cache is empty
5. Integration tests pass
