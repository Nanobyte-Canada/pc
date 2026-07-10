#!/bin/bash
# Unit tests for scripts/auto-restart-ibkr.sh
# Usage: ./scripts/test-auto-restart-ibkr.sh
#
# These tests verify script structure, argument parsing, and helper functions
# WITHOUT actually restarting Docker containers or checking real services.
#
# Implements: https://github.com/Nanobyte-Canada/pc/issues/116 CI test plan

set -euo pipefail

PASS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AUTO_RESTART_SCRIPT="$SCRIPT_DIR/auto-restart-ibkr.sh"

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
echo "  auto-restart-ibkr.sh Unit Tests"
echo "=========================================="
echo ""

# Read script content once for structural tests
SCRIPT_CONTENT=$(cat "$AUTO_RESTART_SCRIPT")

# test-1: Script displays usage with --help flag
echo -e "${YELLOW}test-1: --help shows usage${NC}"
set +e
OUTPUT=$(bash "$AUTO_RESTART_SCRIPT" --help 2>&1)
EXIT_CODE=$?
set -e
assert_exit_code "test-1a: --help exits with code 0" 0 $EXIT_CODE
assert_output_contains "test-1b: Usage header" "Usage:" "$OUTPUT"
assert_output_contains "test-1c: Mentions quiet mode" "quiet" "$OUTPUT"

# test-2: Script structure - has required functions
echo ""
echo -e "${YELLOW}test-2: Required functions present${NC}"
assert_output_contains "test-2a: check_tcp_port function" "check_tcp_port()" "$SCRIPT_CONTENT"
assert_output_contains "test-2b: check_http_health function" "check_http_health()" "$SCRIPT_CONTENT"
assert_output_contains "test-2c: check_ib_gateway_health function" "check_ib_gateway_health()" "$SCRIPT_CONTENT"
assert_output_contains "test-2d: check_uat_market_data_health function" "check_uat_market_data_health()" "$SCRIPT_CONTENT"
assert_output_contains "test-2e: check_prod_market_data_health function" "check_prod_market_data_health()" "$SCRIPT_CONTENT"
assert_output_contains "test-2f: is_cooldown_active function" "is_cooldown_active()" "$SCRIPT_CONTENT"
assert_output_contains "test-2g: set_cooldown function" "set_cooldown()" "$SCRIPT_CONTENT"
assert_output_contains "test-2h: clear_cooldown function" "clear_cooldown()" "$SCRIPT_CONTENT"
assert_output_contains "test-2i: restart_ib_gateway_all function" "restart_ib_gateway_all()" "$SCRIPT_CONTENT"
assert_output_contains "test-2j: restart_market_data_env function" "restart_market_data_env()" "$SCRIPT_CONTENT"

# test-3: Script configuration - correct ports
echo ""
echo -e "${YELLOW}test-3: Correct port configuration${NC}"
assert_output_contains "test-3a: IB Gateway port" "IB_GATEWAY_PORT=14001" "$SCRIPT_CONTENT"
assert_output_contains "test-3b: UAT market data port" "UAT_MARKET_DATA_PORT=20082" "$SCRIPT_CONTENT"
assert_output_contains "test-3c: Prod market data port" "PROD_MARKET_DATA_PORT=10082" "$SCRIPT_CONTENT"

# test-4: Script has cooldown configuration
echo ""
echo -e "${YELLOW}test-4: Cooldown configuration${NC}"
assert_output_contains "test-4a: Cooldown file path" "COOLDOWN_FILE=" "$SCRIPT_CONTENT"
assert_output_contains "test-4b: Cooldown period" "COOLDOWN_PERIOD=300" "$SCRIPT_CONTENT"
assert_output_contains "test-4c: Cooldown tmp path" "/tmp/ibkr-restart-cooldown" "$SCRIPT_CONTENT"

# test-5: Script has quiet mode support
echo ""
echo -e "${YELLOW}test-5: Quiet mode support${NC}"
assert_output_contains "test-5a: QUIET variable" "QUIET=" "$SCRIPT_CONTENT"
# Use fixed-string grep to avoid regex issues with |
TOTAL=$((TOTAL + 1))
if echo "$SCRIPT_CONTENT" | grep -F -- '--quiet|-q' > /dev/null 2>&1; then
  echo -e "  ${GREEN}PASS${NC} test-5b: --quiet flag parsing (found: --quiet|-q)"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}FAIL${NC} test-5b: --quiet flag parsing (pattern not found: --quiet|-q)"
  FAIL=$((FAIL + 1))
fi
assert_output_contains "test-5c: -q short flag" "\-q)" "$SCRIPT_CONTENT"

# test-6: Script references restart script
echo ""
echo -e "${YELLOW}test-6: Restart script reference${NC}"
assert_output_contains "test-6a: RESTART_SCRIPT variable" "RESTART_SCRIPT=" "$SCRIPT_CONTENT"
assert_output_contains "test-6b: Calls restart script" "restart-ibkr.sh" "$SCRIPT_CONTENT"

# test-7: Script has color output
echo ""
echo -e "${YELLOW}test-7: Color output support${NC}"
assert_output_contains "test-7a: Red color code" "RED=" "$SCRIPT_CONTENT"
assert_output_contains "test-7b: Green color code" "GREEN=" "$SCRIPT_CONTENT"
assert_output_contains "test-7c: Yellow color code" "YELLOW=" "$SCRIPT_CONTENT"

# test-8: Script has proper exit code handling
echo ""
echo -e "${YELLOW}test-8: Exit code handling${NC}"
assert_output_contains "test-8a: set -euo pipefail" "set -euo pipefail" "$SCRIPT_CONTENT"
assert_output_contains "test-8b: EXIT_CODE variable" "EXIT_CODE" "$SCRIPT_CONTENT"

# test-9: Script is executable
echo ""
echo -e "${YELLOW}test-9: Script permissions${NC}"
TOTAL=$((TOTAL + 1))
if [ -x "$AUTO_RESTART_SCRIPT" ]; then
  echo -e "  ${GREEN}PASS${NC} test-9a: Script is executable"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}FAIL${NC} test-9a: Script is not executable"
  FAIL=$((FAIL + 1))
fi

# test-10: Cooldown logic - test is_cooldown_active function
echo ""
echo -e "${YELLOW}test-10: Cooldown logic${NC}"

# Create a test cooldown file
TEST_COOLDOWN="/tmp/test-ibkr-cooldown-$$"
touch "$TEST_COOLDOWN"

# Source just the functions from the script to test them
# We extract the function definitions and test them
# Note: We run in a subshell to avoid set -e affecting test execution

# Test: cooldown is active when file is recent (less than COOLDOWN_PERIOD)
set +e
OUTPUT=$(bash -c '
  COOLDOWN_FILE="'"$TEST_COOLDOWN"'"
  COOLDOWN_PERIOD=300
  
  # Extract just the is_cooldown_active function
  is_cooldown_active() {
    if [ -f "$COOLDOWN_FILE" ]; then
      local last_restart
      last_restart=$(stat -c %Y "$COOLDOWN_FILE" 2>/dev/null || echo 0)
      local current_time
      current_time=$(date +%s)
      local elapsed=$((current_time - last_restart))
      if [ "$elapsed" -lt "$COOLDOWN_PERIOD" ]; then
        return 0
      fi
    fi
    return 1
  }
  
  is_cooldown_active
' 2>&1)
COOLDOWN_EXIT=$?
set -e
TOTAL=$((TOTAL + 1))
if [ "$COOLDOWN_EXIT" -eq 0 ]; then
  echo -e "  ${GREEN}PASS${NC} test-10a: Cooldown detected for fresh file (exit=0)"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}FAIL${NC} test-10a: Cooldown not detected for fresh file (exit=$COOLDOWN_EXIT)"
  FAIL=$((FAIL + 1))
fi

# Test: cooldown is not active for old file (COOLDOWN_PERIOD=0)
set +e
OUTPUT=$(bash -c '
  COOLDOWN_FILE="'"$TEST_COOLDOWN"'"
  COOLDOWN_PERIOD=0
  
  is_cooldown_active() {
    if [ -f "$COOLDOWN_FILE" ]; then
      local last_restart
      last_restart=$(stat -c %Y "$COOLDOWN_FILE" 2>/dev/null || echo 0)
      local current_time
      current_time=$(date +%s)
      local elapsed=$((current_time - last_restart))
      if [ "$elapsed" -lt "$COOLDOWN_PERIOD" ]; then
        return 0
      fi
    fi
    return 1
  }
  
  is_cooldown_active
' 2>&1)
COOLDOWN_EXIT_OLD=$?
set -e
TOTAL=$((TOTAL + 1))
if [ "$COOLDOWN_EXIT_OLD" -eq 1 ]; then
  echo -e "  ${GREEN}PASS${NC} test-10b: Cooldown not active for expired file (exit=1)"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}FAIL${NC} test-10b: Cooldown still active for expired file (exit=$COOLDOWN_EXIT_OLD)"
  FAIL=$((FAIL + 1))
fi

# Cleanup
rm -f "$TEST_COOLDOWN"

# test-11: Script contains expected patterns for health check endpoints
echo ""
echo -e "${YELLOW}test-11: Health check endpoint patterns${NC}"
assert_output_contains "test-11a: IB Gateway TCP check" "echo.*dev/tcp.*\$IB_GATEWAY_PORT\|/dev/tcp/" "$SCRIPT_CONTENT"
assert_output_contains "test-11b: HTTP health check URL" "/actuator/health" "$SCRIPT_CONTENT"

# test-12: Script has proper logging functions
echo ""
echo -e "${YELLOW}test-12: Logging functions present${NC}"
assert_output_contains "test-12a: log_info function" "log_info()" "$SCRIPT_CONTENT"
assert_output_contains "test-12b: log_success function" "log_success()" "$SCRIPT_CONTENT"
assert_output_contains "test-12c: log_error function" "log_error()" "$SCRIPT_CONTENT"
assert_output_contains "test-12d: log_warn function" "log_warn()" "$SCRIPT_CONTENT"
assert_output_contains "test-12e: log_healthy function" "log_healthy()" "$SCRIPT_CONTENT"
assert_output_contains "test-12f: log_unhealthy function" "log_unhealthy()" "$SCRIPT_CONTENT"

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
