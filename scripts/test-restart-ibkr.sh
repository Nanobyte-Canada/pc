#!/bin/bash
# Unit tests for scripts/restart-ibkr.sh
# Usage: ./scripts/test-restart-ibkr.sh
#
# These tests verify script structure, argument parsing, and help output
# WITHOUT actually restarting Docker containers.
#
# Implements: https://github.com/Nanobyte-Canada/pc/issues/115 CI test plan

set -euo pipefail

PASS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESTART_SCRIPT="$SCRIPT_DIR/restart-ibkr.sh"

# --- Test helpers ----------------------------------------------------------

assert_exit_code() {
  local test_name="$1"
  local expected_code="$2"
  local actual_code="$3"
  TOTAL=$((TOTAL + 1))

  if [ "$actual_code" -eq "$expected_code" ]; then
    echo -e "  ${GREEN}PASS${NC} $test_name (exit=$actual_code)"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${NC} $test_name (expected exit=$expected_code, got=$actual_code)"
    FAIL=$((FAIL + 1))
  fi
}

assert_output_contains() {
  local test_name="$1"
  local expected_pattern="$2"
  local actual_output="$3"
  TOTAL=$((TOTAL + 1))

  if echo "$actual_output" | grep -q "$expected_pattern"; then
    echo -e "  ${GREEN}PASS${NC} $test_name (found: $expected_pattern)"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${NC} $test_name (pattern not found: $expected_pattern)"
    FAIL=$((FAIL + 1))
  fi
}

# --- Test cases ------------------------------------------------------------

echo ""
echo "=========================================="
echo "  restart-ibkr.sh Unit Tests"
echo "=========================================="
echo ""

# Read script content once for structural tests
SCRIPT_CONTENT=$(cat "$RESTART_SCRIPT")

# test-1: Script exits with error on invalid flag
echo -e "${YELLOW}test-1: Invalid flag handling${NC}"
set +e
OUTPUT=$(bash "$RESTART_SCRIPT" --invalid-flag 2>&1)
EXIT_CODE=$?
set -e
assert_exit_code "test-1a: Invalid flag exits non-zero" 1 $EXIT_CODE
assert_output_contains "test-1b: Error message for unknown option" "Unknown option" "$OUTPUT"

# test-2: Script displays usage with --help flag
echo ""
echo -e "${YELLOW}test-2: --help shows usage${NC}"
OUTPUT=$(bash "$RESTART_SCRIPT" --help 2>&1 || true)
EXIT_CODE=$?
assert_exit_code "test-2a: --help exits with code 0" 0 $EXIT_CODE
assert_output_contains "test-2b: Usage header" "Usage:" "$OUTPUT"
assert_output_contains "test-2c: Mentions uat" "uat" "$OUTPUT"
assert_output_contains "test-2d: Mentions prod" "prod" "$OUTPUT"
assert_output_contains "test-2e: Mentions all" "all" "$OUTPUT"
assert_output_contains "test-2f: Mentions IB Gateway port" "14001" "$OUTPUT"

# test-3: Script parses --env flag correctly
echo ""
echo -e "${YELLOW}test-3: --env flag parsing${NC}"

# Test invalid --env value
set +e
OUTPUT=$(bash "$RESTART_SCRIPT" --env invalid 2>&1)
EXIT_CODE=$?
set -e
assert_exit_code "test-3a: Invalid --env value exits non-zero" 1 $EXIT_CODE
assert_output_contains "test-3b: Error message for invalid env" "Invalid" "$OUTPUT"

# test-4: Script constructs correct docker compose commands
echo ""
echo -e "${YELLOW}test-4: Correct compose file references${NC}"
assert_output_contains "test-4a: Shared compose file" "deploy/shared/docker-compose.yml" "$SCRIPT_CONTENT"
assert_output_contains "test-4b: UAT compose file" "deploy/uat/docker-compose.yml" "$SCRIPT_CONTENT"
assert_output_contains "test-4c: Prod compose file" "deploy/prod/docker-compose.yml" "$SCRIPT_CONTENT"
assert_output_contains "test-4d: IB Gateway service name" "ib-gateway" "$SCRIPT_CONTENT"
assert_output_contains "test-4e: Market data service name" "market-data-service" "$SCRIPT_CONTENT"

# test-5: Script contains correct port numbers
echo ""
echo -e "${YELLOW}test-5: Correct port configuration${NC}"
assert_output_contains "test-5a: IB Gateway port" "IB_GATEWAY_PORT=14001" "$SCRIPT_CONTENT"
assert_output_contains "test-5b: UAT market data port" "UAT_MARKET_DATA_PORT=20082" "$SCRIPT_CONTENT"
assert_output_contains "test-5c: Prod market data port" "PROD_MARKET_DATA_PORT=10082" "$SCRIPT_CONTENT"

# test-6: Script contains correct container names
echo ""
echo -e "${YELLOW}test-6: Correct container names${NC}"
assert_output_contains "test-6a: IB Gateway container" "shared-ib-gateway" "$SCRIPT_CONTENT"
assert_output_contains "test-6b: UAT market data container" "uat-market-data" "$SCRIPT_CONTENT"
assert_output_contains "test-6c: Prod market data container" "prod-market-data" "$SCRIPT_CONTENT"

# test-7: Script has proper health check functions
echo ""
echo -e "${YELLOW}test-7: Health check functions present${NC}"
assert_output_contains "test-7a: check_tcp_port function" "check_tcp_port()" "$SCRIPT_CONTENT"
assert_output_contains "test-7b: check_http_health function" "check_http_health()" "$SCRIPT_CONTENT"
assert_output_contains "test-7c: wait_for_tcp function" "wait_for_tcp()" "$SCRIPT_CONTENT"
assert_output_contains "test-7d: wait_for_http_health function" "wait_for_http_health()" "$SCRIPT_CONTENT"

# test-8: Script has proper timeout values
echo ""
echo -e "${YELLOW}test-8: Timeout configuration${NC}"
assert_output_contains "test-8a: IB Gateway health timeout" "IB_GATEWAY_HEALTH_TIMEOUT=120" "$SCRIPT_CONTENT"
assert_output_contains "test-8b: Market data health timeout" "MARKET_DATA_HEALTH_TIMEOUT=60" "$SCRIPT_CONTENT"

# test-9: Script has color output
echo ""
echo -e "${YELLOW}test-9: Color output support${NC}"
assert_output_contains "test-9a: Red color code" "RED=" "$SCRIPT_CONTENT"
assert_output_contains "test-9b: Green color code" "GREEN=" "$SCRIPT_CONTENT"
assert_output_contains "test-9c: Yellow color code" "YELLOW=" "$SCRIPT_CONTENT"

# test-10: Script has proper exit code handling
echo ""
echo -e "${YELLOW}test-10: Exit code handling${NC}"
assert_output_contains "test-10a: set -euo pipefail" "set -euo pipefail" "$SCRIPT_CONTENT"
assert_output_contains "test-10b: exit_code variable" "exit_code" "$SCRIPT_CONTENT"

# test-11: Script is executable
echo ""
echo -e "${YELLOW}test-11: Script permissions${NC}"
TOTAL=$((TOTAL + 1))
if [ -x "$RESTART_SCRIPT" ]; then
  echo -e "  ${GREEN}PASS${NC} test-11a: Script is executable"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}FAIL${NC} test-11a: Script is not executable"
  FAIL=$((FAIL + 1))
fi

# Summary
echo ""
echo "=========================================="
if [ $FAIL -eq 0 ]; then
  echo -e "  ${GREEN}ALL TESTS PASSED${NC} ($PASS/$TOTAL)"
else
  echo -e "  ${RED}$FAIL FAILED${NC}, ${GREEN}$PASS passed${NC} ($TOTAL total)"
fi
echo "=========================================="
echo ""

exit $FAIL
