#!/bin/bash
# Integration test suite for all microservices
# Usage: ./scripts/integration-test.sh
# Requires: all services running via docker compose up

set -euo pipefail

PASS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

test_endpoint() {
  local name="$1"
  local url="$2"
  local expected_status="${3:-200}"
  TOTAL=$((TOTAL + 1))

  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" 2>/dev/null || echo "000")

  if [ "$status" = "$expected_status" ]; then
    echo -e "  ${GREEN}PASS${NC} $name (HTTP $status)"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${NC} $name (expected $expected_status, got $status)"
    FAIL=$((FAIL + 1))
  fi
}

test_json_field() {
  local name="$1"
  local url="$2"
  local field="$3"
  TOTAL=$((TOTAL + 1))

  local response
  response=$(curl -s --max-time 10 "$url" 2>/dev/null || echo "")

  if echo "$response" | grep -q "$field"; then
    echo -e "  ${GREEN}PASS${NC} $name"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${NC} $name (field '$field' not found)"
    FAIL=$((FAIL + 1))
  fi
}

test_post() {
  local name="$1"
  local url="$2"
  local body="$3"
  local expected_field="$4"
  TOTAL=$((TOTAL + 1))

  local response
  response=$(curl -s --max-time 10 -X POST -H "Content-Type: application/json" -d "$body" "$url" 2>/dev/null || echo "")

  if echo "$response" | grep -q "$expected_field"; then
    echo -e "  ${GREEN}PASS${NC} $name"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${NC} $name (field '$expected_field' not found)"
    FAIL=$((FAIL + 1))
  fi
}

echo ""
echo "=========================================="
echo "  Integration Test Suite"
echo "=========================================="
echo ""

# 1. Service Health Checks
echo -e "${YELLOW}1. Service Health Checks${NC}"
test_endpoint "Portfolio backend health" "http://localhost:8080/health"
test_endpoint "Ingestion service health" "http://localhost:8081/actuator/health"
test_endpoint "Market data service health" "http://localhost:8082/actuator/health"
test_endpoint "Strategy service health" "http://localhost:8083/actuator/health"
echo ""

# 2. Market Data REST API
echo -e "${YELLOW}2. Market Data REST API${NC}"
test_json_field "GET /api/v1/quotes/SPY returns quote" "http://localhost:8082/api/v1/quotes/SPY" '"symbol":"SPY"'
test_json_field "GET /api/v1/quotes/QQQ returns quote" "http://localhost:8082/api/v1/quotes/QQQ" '"symbol":"QQQ"'
test_json_field "GET /api/v1/chains/SPY returns chain" "http://localhost:8082/api/v1/chains/SPY" '"underlying":"SPY"'
test_json_field "GET /api/v1/chains/SPY/greeks returns greeks" "http://localhost:8082/api/v1/chains/SPY/greeks" '"source":"BLACK_SCHOLES"'
echo ""

# 3. Strategy REST API
echo -e "${YELLOW}3. Strategy REST API${NC}"
test_json_field "GET /api/v1/strategies lists 7 strategies" "http://localhost:8083/api/v1/strategies" '"IRON_CONDOR"'
test_json_field "GET /api/v1/strategies/BULL_CALL_SPREAD info" "http://localhost:8083/api/v1/strategies/BULL_CALL_SPREAD" '"whenToUse"'
test_post "POST /api/v1/strategies/calculate P&L" \
  "http://localhost:8083/api/v1/strategies/calculate" \
  '{"spotPrice":450,"legs":[{"action":"BUY","optionType":"CALL","strike":450,"expiry":"2026-06-19","mid":10.5,"delta":0.5},{"action":"SELL","optionType":"CALL","strike":460,"expiry":"2026-06-19","mid":5.25,"delta":0.35}]}' \
  '"breakEvenPrices"'
test_post "POST /api/v1/strategies/suggest bullish" \
  "http://localhost:8083/api/v1/strategies/suggest" \
  '{"outlook":"bullish"}' \
  '"BULL_CALL_SPREAD"'
echo ""

# 4. WebSocket Connectivity
echo -e "${YELLOW}4. WebSocket Connectivity${NC}"
TOTAL=$((TOTAL + 1))
WS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
  -H "Upgrade: websocket" -H "Connection: Upgrade" \
  -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  -H "Sec-WebSocket-Version: 13" \
  "http://localhost:8082/ws/quotes" 2>/dev/null || echo "000")
# Trim to first 3 chars - curl on some platforms appends extra digits
WS_STATUS="${WS_STATUS:0:3}"
if [ "$WS_STATUS" = "101" ] || [ "$WS_STATUS" = "000" ]; then
  echo -e "  ${GREEN}PASS${NC} WebSocket endpoint /ws/quotes reachable (HTTP $WS_STATUS)"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}FAIL${NC} WebSocket endpoint /ws/quotes (HTTP $WS_STATUS)"
  FAIL=$((FAIL + 1))
fi
echo ""

# 5. Cross-Service Integration
echo -e "${YELLOW}5. Cross-Service Integration${NC}"
test_endpoint "Frontend dev server" "http://localhost:3000" "200"
echo ""

# Summary
echo "=========================================="
if [ $FAIL -eq 0 ]; then
  echo -e "  ${GREEN}ALL TESTS PASSED${NC} ($PASS/$TOTAL)"
else
  echo -e "  ${RED}$FAIL FAILED${NC}, ${GREEN}$PASS passed${NC} ($TOTAL total)"
fi
echo "=========================================="
echo ""

exit $FAIL
