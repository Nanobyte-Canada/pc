# Questrade Cutover Runbook

## Prerequisites
- Valid Questrade API refresh token (web API Centre). Rotate before prod if it transited chat.
- UAT uses practice token (QUESTRADE_USE_PRACTICE=true); prod uses live token.
- Real-time US/OPRA market data package active ($9.95/mo) - otherwise US options data is delayed.

## Deploy steps (per environment)
1. Set QUESTRADE_REFRESH_TOKEN (+ QUESTRADE_USE_PRACTICE for uat) in .env; deploy new images.
2. Flush stale provider-id caches BEFORE first request:
   redis-cli --scan --pattern 'contract:*' | xargs -r redis-cli del
   redis-cli --scan --pattern 'quote:*' | xargs -r redis-cli del
   redis-cli --scan --pattern 'chain:*' | xargs -r redis-cli del
   redis-cli --scan --pattern 'expiry:*' | xargs -r redis-cli del
   redis-cli --scan --pattern 'expirations:*' | xargs -r redis-cli del
3. Verify: GET /api/v1/chains/SPY/expirations returns expiries; open Wheel page,
   load a chain, confirm streaming ticks arrive; GET /api/v1/health/provider shows connected=true.

## Rollback
Revert to previous image tag; IBKR infra was deleted, so rollback assumes re-provisioning
the shared ib-gateway stack from git history (tag the pre-migration commit before deploying).
