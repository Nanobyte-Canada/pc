#!/bin/bash
set -euo pipefail

# =============================================================================
# auto-restart-ibkr.sh
#
# Automated health monitoring and restart script for IBKR TWS API connection
# services. Continuously monitors IB Gateway and market-data health, and
# automatically restarts services when they become unhealthy.
#
# Designed to run via cron job on the home server.
#
# Usage:
#   ./auto-restart-ibkr.sh [--quiet]
#
# Cron example:
#   */5 * * * * /opt/portfolio/scripts/auto-restart-ibkr.sh --quiet
#
# Prerequisites:
#   - scripts/restart-ibkr.sh must be available
#   - Docker and docker compose must be available
#
# Implements: https://github.com/Nanobyte-Canada/pc/issues/116
# =============================================================================

# --- Configuration ---------------------------------------------------------
IB_GATEWAY_PORT=14001
UAT_MARKET_DATA_PORT=20082
PROD_MARKET_DATA_PORT=10082

# Cooldown file to prevent restart loops
COOLDOWN_FILE="/tmp/ibkr-restart-cooldown"
COOLDOWN_PERIOD=300  # 5 minutes in seconds

# Health check timeout
HEALTH_CHECK_TIMEOUT=5

# --- Color codes -----------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# --- Script directory -------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESTART_SCRIPT="$SCRIPT_DIR/restart-ibkr.sh"

# --- Global state -----------------------------------------------------------
QUIET=false
EXIT_CODE=0

# --- Helper functions ------------------------------------------------------

timestamp() {
  date '+%Y-%m-%d %H:%M:%S'
}

log_info() {
  if [ "$QUIET" = false ]; then
    echo -e "$(timestamp) ${YELLOW}[INFO]${NC} $*"
  fi
}

log_success() {
  echo -e "$(timestamp) ${GREEN}[SUCCESS]${NC} $*"
}

log_error() {
  echo -e "$(timestamp) ${RED}[ERROR]${NC} $*"
}

log_warn() {
  echo -e "$(timestamp) ${YELLOW}[WARN]${NC} $*"
}

log_healthy() {
  if [ "$QUIET" = false ]; then
    echo -e "$(timestamp) ${GREEN}[HEALTHY]${NC} $*"
  fi
}

log_unhealthy() {
  echo -e "$(timestamp) ${RED}[UNHEALTHY]${NC} $*"
}

# --- Health check functions ------------------------------------------------

# Check if a TCP port is listening
check_tcp_port() {
  local host="$1"
  local port="$2"

  (echo > /dev/tcp/"$host"/"$port") 2>/dev/null
  return $?
}

# Check if an HTTP endpoint returns 200 with expected body
check_http_health() {
  local url="$1"

  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$HEALTH_CHECK_TIMEOUT" "$url" 2>/dev/null || echo "000")

  if [ "$http_code" = "200" ]; then
    # Verify the response contains "UP" status
    local response
    response=$(curl -s --max-time "$HEALTH_CHECK_TIMEOUT" "$url" 2>/dev/null || echo "")
    if echo "$response" | grep -q '"status":"UP"'; then
      return 0
    elif echo "$response" | grep -q '"status":"DOWN"'; then
      return 1
    fi
    # HTTP 200 but no clear UP/DOWN status - treat as healthy
    return 0
  fi
  return 1
}

# --- Health status checks ---------------------------------------------------

# Check IB Gateway health
check_ib_gateway_health() {
  if check_tcp_port "localhost" "$IB_GATEWAY_PORT"; then
    log_healthy "IB Gateway: HEALTHY (port $IB_GATEWAY_PORT)"
    return 0
  else
    log_unhealthy "IB Gateway: UNHEALTHY (port $IB_GATEWAY_PORT)"
    return 1
  fi
}

# Check UAT market-data health
check_uat_market_data_health() {
  local url="http://localhost:${UAT_MARKET_DATA_PORT}/actuator/health"
  if check_http_health "$url"; then
    log_healthy "UAT Market Data: HEALTHY (port $UAT_MARKET_DATA_PORT)"
    return 0
  else
    log_unhealthy "UAT Market Data: UNHEALTHY (port $UAT_MARKET_DATA_PORT)"
    return 1
  fi
}

# Check Production market-data health
check_prod_market_data_health() {
  local url="http://localhost:${PROD_MARKET_DATA_PORT}/actuator/health"
  if check_http_health "$url"; then
    log_healthy "Prod Market Data: HEALTHY (port $PROD_MARKET_DATA_PORT)"
    return 0
  else
    log_unhealthy "Prod Market Data: UNHEALTHY (port $PROD_MARKET_DATA_PORT)"
    return 1
  fi
}

# --- Cooldown functions -----------------------------------------------------

# Check if cooldown is active
is_cooldown_active() {
  if [ -f "$COOLDOWN_FILE" ]; then
    local last_restart
    last_restart=$(stat -c %Y "$COOLDOWN_FILE" 2>/dev/null || echo 0)
    local current_time
    current_time=$(date +%s)
    local elapsed=$((current_time - last_restart))

    if [ "$elapsed" -lt "$COOLDOWN_PERIOD" ]; then
      local remaining=$((COOLDOWN_PERIOD - elapsed))
      log_warn "Cooldown active: ${remaining}s remaining (last restart: ${elapsed}s ago)"
      return 0
    fi
  fi
  return 1
}

# Set cooldown file
set_cooldown() {
  touch "$COOLDOWN_FILE"
  log_info "Cooldown set for ${COOLDOWN_PERIOD}s"
}

# Clear cooldown file
clear_cooldown() {
  if [ -f "$COOLDOWN_FILE" ]; then
    rm -f "$COOLDOWN_FILE"
    log_info "Cooldown cleared"
  fi
}

# --- Restart functions ------------------------------------------------------

# Restart IB Gateway for all environments (shared container)
restart_ib_gateway_all() {
  log_info "Restarting IB Gateway (shared container for all environments)..."

  if [ ! -x "$RESTART_SCRIPT" ]; then
    log_error "Restart script not found or not executable: $RESTART_SCRIPT"
    return 1
  fi

  # Restart IB Gateway only (no market-data)
  # We call the restart script with --env all to restart IB Gateway
  # but the script will also restart market-data. We need to handle this carefully.
  # For simplicity, we'll restart the entire stack for the affected environment.
  if "$RESTART_SCRIPT" --env all 2>&1; then
    log_success "IB Gateway restart completed"
    set_cooldown
    return 0
  else
    log_error "IB Gateway restart failed"
    return 1
  fi
}

# Restart market-data for a specific environment
restart_market_data_env() {
  local env="$1"

  log_info "Restarting ${env^^} market-data..."

  if [ ! -x "$RESTART_SCRIPT" ]; then
    log_error "Restart script not found or not executable: $RESTART_SCRIPT"
    return 1
  fi

  if "$RESTART_SCRIPT" --env "$env" 2>&1; then
    log_success "${env^^} market-data restart completed"
    set_cooldown
    return 0
  else
    log_error "${env^^} market-data restart failed"
    return 1
  fi
}

# --- Main health monitoring -------------------------------------------------

main() {
  # Parse arguments
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --quiet|-q)
        QUIET=true
        shift
        ;;
      --help|-h)
        echo "Usage: $(basename "$0") [--quiet]"
        echo ""
        echo "Automated health monitoring and restart for IBKR services."
        echo ""
        echo "Options:"
        echo "  --quiet, -q    Suppress healthy-service output"
        echo "  --help, -h     Display this help message"
        exit 0
        ;;
      *)
        echo "Unknown option: $1"
        exit 1
        ;;
    esac
  done

  if [ "$QUIET" = false ]; then
    echo ""
    echo "=========================================="
    echo "  IBKR Auto-Restart Health Monitor"
    echo "  Started: $(timestamp)"
    echo "=========================================="
  fi

  # Check cooldown
  if is_cooldown_active; then
    if [ "$QUIET" = false ]; then
      echo ""
      log_warn "Skipping restart due to active cooldown"
    fi
    # Still check health to potentially clear cooldown
  fi

  # Check all services
  local ib_healthy=true
  local uat_md_healthy=true
  local prod_md_healthy=true

  echo ""
  log_info "=== Health Check ==="

  if ! check_ib_gateway_health; then
    ib_healthy=false
  fi

  if ! check_uat_market_data_health; then
    uat_md_healthy=false
  fi

  if ! check_prod_market_data_health; then
    prod_md_healthy=false
  fi

  echo ""

  # Determine what needs restart
  local needs_restart=false
  local restart_env=""

  if [ "$ib_healthy" = false ]; then
    # IB Gateway is shared - restart all environments
    needs_restart=true
    restart_env="all"
    log_warn "IB Gateway is unhealthy - will restart all environments"
  elif [ "$uat_md_healthy" = false ] && [ "$prod_md_healthy" = false ]; then
    # Both market-data services unhealthy - restart all
    needs_restart=true
    restart_env="all"
    log_warn "Both UAT and Prod market-data are unhealthy - will restart all"
  elif [ "$uat_md_healthy" = false ]; then
    # Only UAT market-data unhealthy
    needs_restart=true
    restart_env="uat"
    log_warn "UAT market-data is unhealthy - will restart UAT"
  elif [ "$prod_md_healthy" = false ]; then
    # Only Prod market-data unhealthy
    needs_restart=true
    restart_env="prod"
    log_warn "Prod market-data is unhealthy - will restart Prod"
  fi

  # Check if restart is needed and allowed
  if [ "$needs_restart" = true ]; then
    if is_cooldown_active; then
      log_warn "Cooldown active - cannot restart yet. Service remains unhealthy."
      EXIT_CODE=1
    else
      echo ""
      log_info "=== Restarting Services ==="

      if [ "$restart_env" = "all" ]; then
        restart_ib_gateway_all || EXIT_CODE=1
      else
        restart_market_data_env "$restart_env" || EXIT_CODE=1
      fi

      # Re-check health after restart
      echo ""
      log_info "=== Post-Restart Health Check ==="

      local post_ib_healthy=true
      local post_uat_healthy=true
      local post_prod_healthy=true

      if ! check_ib_gateway_health; then
        post_ib_healthy=false
      fi

      if ! check_uat_market_data_health; then
        post_uat_healthy=false
      fi

      if ! check_prod_market_data_health; then
        post_prod_healthy=false
      fi

      # Clear cooldown if all services are now healthy
      if [ "$post_ib_healthy" = true ] && [ "$post_uat_healthy" = true ] && [ "$post_prod_healthy" = true ]; then
        clear_cooldown
        log_success "All services recovered after restart"
      else
        log_warn "Some services still unhealthy after restart"
        EXIT_CODE=1
      fi
    fi
  else
    # All services healthy
    clear_cooldown
    log_success "All services are HEALTHY"
  fi

  if [ "$QUIET" = false ]; then
    echo ""
    echo "=========================================="
    echo "  Finished: $(timestamp)"
    if [ "$EXIT_CODE" -eq 0 ]; then
      echo -e "  ${GREEN}RESULT: ALL HEALTHY${NC}"
    else
      echo -e "  ${RED}RESULT: SOME UNHEALTHY${NC}"
    fi
    echo "=========================================="
    echo ""
  fi

  return "$EXIT_CODE"
}

main "$@"
