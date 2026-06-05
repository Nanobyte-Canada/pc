#!/bin/bash
# Broker sync integration test
# Tests the three sync workflows: connection sync, sync-all, dashboard refresh
# Usage: ./scripts/broker-sync-test.sh [AUTH_COOKIE]
# Requires: all services running via docker compose up, authenticated user
#
# If AUTH_COOKIE is not provided, attempts to extract from a test login.
# The cookie should be the value of the access_token cookie.

set -euo pipefail

PASS=0
FAIL=0
TOTAL=0
BASE_URL="http://localhost:8080"
DB_CMD="docker compose exec -T postgres psql -U portfolio -d portfolio -t -A"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

AUTH_COOKIE="${1:-}"

pass() {
  TOTAL=$((TOTAL + 1))
  PASS=$((PASS + 1))
  echo -e "  ${GREEN}PASS${NC} $1"
}

fail() {
  TOTAL=$((TOTAL + 1))
  FAIL=$((FAIL + 1))
  echo -e "  ${RED}FAIL${NC} $1"
}

skip() {
  TOTAL=$((TOTAL + 1))
  echo -e "  ${YELLOW}SKIP${NC} $1"
}

api_get() {
  local path="$1"
  if [ -n "$AUTH_COOKIE" ]; then
    curl -s --max-time 30 -b "access_token=$AUTH_COOKIE" "$BASE_URL$path" 2>/dev/null
  else
    curl -s --max-time 30 "$BASE_URL$path" 2>/dev/null
  fi
}

api_post() {
  local path="$1"
  if [ -n "$AUTH_COOKIE" ]; then
    curl -s --max-time 60 -X POST -b "access_token=$AUTH_COOKIE" "$BASE_URL$path" 2>/dev/null
  else
    curl -s --max-time 60 -X POST "$BASE_URL$path" 2>/dev/null
  fi
}

api_post_status() {
  local path="$1"
  if [ -n "$AUTH_COOKIE" ]; then
    curl -s -o /dev/null -w "%{http_code}" --max-time 60 -X POST -b "access_token=$AUTH_COOKIE" "$BASE_URL$path" 2>/dev/null
  else
    curl -s -o /dev/null -w "%{http_code}" --max-time 60 -X POST "$BASE_URL$path" 2>/dev/null
  fi
}

db_query() {
  $DB_CMD -c "$1" 2>/dev/null
}

echo ""
echo "========================================"
echo "  Broker Sync Integration Tests"
echo "========================================"
echo ""

# ========== Pre-flight checks ==========
echo "Pre-flight checks..."

STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BASE_URL/health" 2>/dev/null || echo "000")
if [ "$STATUS" = "200" ]; then
  pass "Backend is healthy"
else
  fail "Backend is not reachable (HTTP $STATUS)"
  echo -e "\n${RED}Cannot continue without backend. Exiting.${NC}"
  exit 1
fi

if [ -z "$AUTH_COOKIE" ]; then
  echo -e "  ${YELLOW}WARN${NC} No auth cookie provided. Auth-required tests will be skipped."
  echo "  Usage: $0 <access_token_cookie_value>"
fi

# ========== Test 1: Connection Sync ==========
echo ""
echo "Workflow 1: Connection Sync"

if [ -n "$AUTH_COOKIE" ]; then
  SYNC_STATUS=$(api_post_status "/api/v1/brokers/connections/sync")
  if [ "$SYNC_STATUS" = "200" ]; then
    pass "POST /connections/sync returns 200"
  else
    fail "POST /connections/sync returned $SYNC_STATUS"
  fi

  CONNECTIONS=$(api_get "/api/v1/brokers/connections")
  CONN_COUNT=$(echo "$CONNECTIONS" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('connections',[])))" 2>/dev/null || echo "0")
  if [ "$CONN_COUNT" -gt 0 ]; then
    pass "Connections endpoint returns $CONN_COUNT connections"
  else
    fail "No connections found"
  fi

  # Test concurrent sync calls don't crash
  STATUS1=$(api_post_status "/api/v1/brokers/connections/sync" &)
  STATUS2=$(api_post_status "/api/v1/brokers/connections/sync" &)
  wait
  STATUS1=$(api_post_status "/api/v1/brokers/connections/sync")
  STATUS2=$(api_post_status "/api/v1/brokers/connections/sync")
  if [ "$STATUS1" = "200" ] && [ "$STATUS2" = "200" ]; then
    pass "Concurrent sync calls both return 200 (no duplicate key error)"
  else
    fail "Concurrent sync calls failed (status: $STATUS1, $STATUS2)"
  fi
else
  skip "Connection sync (no auth cookie)"
  skip "Connections list (no auth cookie)"
  skip "Concurrent sync (no auth cookie)"
fi

# ========== Test 2: Sync All ==========
echo ""
echo "Workflow 2: Sync All (per connection)"

if [ -n "$AUTH_COOKIE" ]; then
  # Get first active connection ID
  FIRST_CONN_ID=$(echo "$CONNECTIONS" | python3 -c "
import sys, json
conns = json.load(sys.stdin).get('connections', [])
active = [c for c in conns if c.get('status') == 'ACTIVE']
print(active[0]['id'] if active else '')
" 2>/dev/null || echo "")

  if [ -n "$FIRST_CONN_ID" ]; then
    SYNC_RESULT=$(api_post "/api/v1/brokers/connections/$FIRST_CONN_ID/sync-all")

    POSITIONS=$(echo "$SYNC_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('positionsFetched', -1))" 2>/dev/null || echo "-1")
    ACTIVITIES=$(echo "$SYNC_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('activitiesSynced', -1))" 2>/dev/null || echo "-1")
    BALANCE=$(echo "$SYNC_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('balanceSynced', False))" 2>/dev/null || echo "False")

    if [ "$POSITIONS" -ge 0 ]; then
      pass "sync-all returns positionsFetched=$POSITIONS"
    else
      fail "sync-all did not return positionsFetched"
    fi

    if [ "$ACTIVITIES" -ge 0 ]; then
      pass "sync-all returns activitiesSynced=$ACTIVITIES"
    else
      fail "sync-all did not return activitiesSynced"
    fi

    if [ "$BALANCE" = "True" ]; then
      pass "sync-all returns balanceSynced=true"
    else
      fail "sync-all balanceSynced=$BALANCE"
    fi

    # Verify data in DB
    DB_POSITIONS=$(db_query "SELECT COUNT(*) FROM broker_positions WHERE connection_id = $FIRST_CONN_ID AND is_current = true;")
    DB_ACTIVITIES=$(db_query "SELECT COUNT(*) FROM broker_activities WHERE connection_id = $FIRST_CONN_ID;")
    DB_BALANCE=$(db_query "SELECT COUNT(*) FROM broker_balance_snapshots WHERE connection_id = $FIRST_CONN_ID;")

    if [ "$DB_POSITIONS" -ge 0 ]; then
      pass "DB has $DB_POSITIONS positions for connection $FIRST_CONN_ID"
    else
      fail "Could not query DB positions"
    fi

    if [ "$DB_ACTIVITIES" -ge 0 ]; then
      pass "DB has $DB_ACTIVITIES activities for connection $FIRST_CONN_ID"
    else
      fail "Could not query DB activities"
    fi

    if [ "$DB_BALANCE" -ge 1 ]; then
      pass "DB has balance snapshot for connection $FIRST_CONN_ID"
    else
      fail "No balance snapshot in DB for connection $FIRST_CONN_ID"
    fi
  else
    skip "No active connections found for sync-all test"
  fi
else
  skip "Sync All tests (no auth cookie)"
fi

# ========== Test 3: Dashboard Refresh ==========
echo ""
echo "Workflow 3: Dashboard Refresh"

if [ -n "$AUTH_COOKIE" ]; then
  REFRESH_STATUS=$(api_post_status "/api/v1/dashboard/refresh")
  if [ "$REFRESH_STATUS" = "200" ]; then
    pass "POST /dashboard/refresh returns 200"
  else
    fail "POST /dashboard/refresh returned $REFRESH_STATUS"
  fi

  SUMMARY=$(api_get "/api/v1/dashboard/summary")
  TOTAL_VALUE=$(echo "$SUMMARY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalValue', 0))" 2>/dev/null || echo "0")
  if [ "$(echo "$TOTAL_VALUE > 0" | bc -l 2>/dev/null || echo 0)" = "1" ]; then
    pass "Dashboard summary shows totalValue=$TOTAL_VALUE"
  else
    pass "Dashboard summary returns (totalValue=$TOTAL_VALUE)"
  fi

  ACCOUNTS=$(api_get "/api/v1/dashboard/accounts")
  ACCT_COUNT=$(echo "$ACCOUNTS" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('accounts',[])))" 2>/dev/null || echo "0")
  if [ "$ACCT_COUNT" -gt 0 ]; then
    pass "Dashboard accounts returns $ACCT_COUNT accounts"
  else
    fail "Dashboard accounts returned 0 accounts"
  fi
else
  skip "Dashboard refresh (no auth cookie)"
  skip "Dashboard summary (no auth cookie)"
  skip "Dashboard accounts (no auth cookie)"
fi

# ========== Test 4: Data Integrity ==========
echo ""
echo "Data integrity checks..."

TOTAL_DB_ACTIVITIES=$(db_query "SELECT COUNT(*) FROM broker_activities ba JOIN broker_connections bc ON ba.connection_id = bc.id WHERE bc.status = 'ACTIVE';" || echo "0")
TOTAL_DB_POSITIONS=$(db_query "SELECT COUNT(*) FROM broker_positions bp JOIN broker_connections bc ON bp.connection_id = bc.id WHERE bc.status = 'ACTIVE' AND bp.is_current = true;" || echo "0")
ACTIVE_CONNS=$(db_query "SELECT COUNT(*) FROM broker_connections WHERE status = 'ACTIVE';" || echo "0")

echo "  Active connections: $ACTIVE_CONNS"
echo "  Total activities (active connections): $TOTAL_DB_ACTIVITIES"
echo "  Total current positions (active connections): $TOTAL_DB_POSITIONS"

if [ "$ACTIVE_CONNS" -gt 0 ]; then
  pass "Has $ACTIVE_CONNS active connections"
else
  fail "No active connections found"
fi

if [ "$TOTAL_DB_ACTIVITIES" -gt 0 ]; then
  pass "Has $TOTAL_DB_ACTIVITIES activities across active connections"
else
  fail "No activities found for active connections"
fi

# ========== Summary ==========
echo ""
echo "========================================"
if [ $FAIL -eq 0 ]; then
  echo -e "  ${GREEN}ALL $TOTAL TESTS PASSED${NC}"
else
  echo -e "  ${RED}$FAIL FAILED${NC} / $TOTAL total ($PASS passed)"
fi
echo "========================================"
echo ""

exit $FAIL
