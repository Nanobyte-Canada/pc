#!/bin/bash
set -euo pipefail

# =============================================================================
# restart-ibkr.sh
#
# On-demand restart script for IBKR TWS API connection services (IB Gateway
# and market-data) across both UAT and Production environments.
#
# Usage:
#   ./restart-ibkr.sh --env <uat|prod|all>    (default: all)
#   ./restart-ibkr.sh --help
#
# Prerequisites:
#   - Docker and docker compose must be available
#   - Compose files in deploy/shared/, deploy/uat/, deploy/prod/
#
# Implements: https://github.com/Nanobyte-Canada/pc/issues/115
# =============================================================================

# --- Color codes -----------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# --- Configuration ---------------------------------------------------------
IB_GATEWAY_CONTAINER="shared-ib-gateway"
IB_GATEWAY_COMPOSE="deploy/shared/docker-compose.yml"
IB_GATEWAY_PORT=14001
IB_GATEWAY_HEALTH_TIMEOUT=120

UAT_MARKET_DATA_CONTAINER="uat-market-data"
UAT_MARKET_DATA_COMPOSE="deploy/uat/docker-compose.yml"
UAT_MARKET_DATA_PORT=20082

PROD_MARKET_DATA_CONTAINER="prod-market-data"
PROD_MARKET_DATA_COMPOSE="deploy/prod/docker-compose.yml"
PROD_MARKET_DATA_PORT=10082

MARKET_DATA_HEALTH_TIMEOUT=60

# --- Helper functions ------------------------------------------------------

timestamp() {
  date '+%Y-%m-%d %H:%M:%S'
}

log_info() {
  echo -e "$(timestamp) ${YELLOW}[INFO]${NC} $*"
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

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Restart IBKR TWS API connection services (IB Gateway and market-data).

Options:
  --env <env>    Target environment: uat, prod, or all (default: all)
  --help         Display this help message and exit

Examples:
  $(basename "$0") --env uat     # Restart IB Gateway + UAT market-data
  $(basename "$0") --env prod    # Restart IB Gateway + Production market-data
  $(basename "$0") --env all     # Restart IB Gateway + both market-data services
  $(basename "$0")               # Same as --env all

Health Checks:
  IB Gateway:    TCP port ${IB_GATEWAY_PORT} (timeout: ${IB_GATEWAY_HEALTH_TIMEOUT}s)
  UAT market-data:  HTTP /actuator/health on port ${UAT_MARKET_DATA_PORT} (timeout: ${MARKET_DATA_HEALTH_TIMEOUT}s)
  Prod market-data: HTTP /actuator/health on port ${PROD_MARKET_DATA_PORT} (timeout: ${MARKET_DATA_HEALTH_TIMEOUT}s)
EOF
}

# Check if a TCP port is listening
check_tcp_port() {
  local host="$1"
  local port="$2"
  local timeout="${3:-5}"

  (echo > /dev/tcp/"$host"/"$port") 2>/dev/null
  return $?
}

# Check if an HTTP endpoint returns 200 with expected body
check_http_health() {
  local url="$1"
  local timeout="${2:-5}"

  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$timeout" "$url" 2>/dev/null || echo "000")

  if [ "$http_code" = "200" ]; then
    # Verify the response contains "UP" status
    local response
    response=$(curl -s --max-time "$timeout" "$url" 2>/dev/null || echo "")
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

# Wait for a TCP port to become available
wait_for_tcp() {
  local host="$1"
  local port="$2"
  local timeout="$3"
  local service_name="$4"

  log_info "Waiting for $service_name on $host:$port (timeout: ${timeout}s)..."

  local elapsed=0
  local interval=5

  while [ "$elapsed" -lt "$timeout" ]; do
    if check_tcp_port "$host" "$port" 2; then
      log_success "$service_name is healthy on $host:$port after ${elapsed}s"
      return 0
    fi
    sleep "$interval"
    elapsed=$((elapsed + interval))
  done

  log_error "$service_name failed to become healthy within ${timeout}s"
  return 1
}

# Wait for an HTTP health endpoint to return healthy
wait_for_http_health() {
  local url="$1"
  local timeout="$2"
  local service_name="$3"

  log_info "Waiting for $service_name health check at $url (timeout: ${timeout}s)..."

  local elapsed=0
  local interval=5

  while [ "$elapsed" -lt "$timeout" ]; do
    if check_http_health "$url" 5; then
      log_success "$service_name is healthy at $url after ${elapsed}s"
      return 0
    fi
    sleep "$interval"
    elapsed=$((elapsed + interval))
  done

  log_error "$service_name failed to become healthy within ${timeout}s"
  return 1
}

# Restart a Docker container via docker compose
restart_container() {
  local compose_file="$1"
  local service_name="$2"
  local container_name="$3"

  log_info "Restarting $container_name ($service_name)..."

  if ! docker compose -f "$compose_file" restart "$service_name" 2>&1; then
    log_error "Failed to restart $container_name via docker compose"
    return 1
  fi

  log_success "$container_name restarted"
  return 0
}

# Restart IB Gateway
restart_ib_gateway() {
  echo ""
  log_info "=== Restarting IB Gateway ==="

  if restart_container "$IB_GATEWAY_COMPOSE" "ib-gateway" "$IB_GATEWAY_CONTAINER"; then
    wait_for_tcp "localhost" "$IB_GATEWAY_PORT" "$IB_GATEWAY_HEALTH_TIMEOUT" "IB Gateway"
    return $?
  fi
  return 1
}

# Restart market-data for a specific environment
restart_market_data() {
  local env="$1"
  local container_name=""
  local compose_file=""
  local port=""

  case "$env" in
    uat)
      container_name="$UAT_MARKET_DATA_CONTAINER"
      compose_file="$UAT_MARKET_DATA_COMPOSE"
      port="$UAT_MARKET_DATA_PORT"
      ;;
    prod)
      container_name="$PROD_MARKET_DATA_CONTAINER"
      compose_file="$PROD_MARKET_DATA_COMPOSE"
      port="$PROD_MARKET_DATA_PORT"
      ;;
    *)
      log_error "Unknown environment: $env"
      return 1
      ;;
  esac

  echo ""
  log_info "=== Restarting ${env^^} Market Data ==="

  if restart_container "$compose_file" "market-data-service" "$container_name"; then
    local health_url="http://localhost:${port}/actuator/health"
    wait_for_http_health "$health_url" "$MARKET_DATA_HEALTH_TIMEOUT" "${env^^} Market Data"
    return $?
  fi
  return 1
}

# Verify all services are healthy
verify_all_health() {
  local env="$1"

  echo ""
  log_info "=== Verifying All Services ==="

  local all_healthy=true

  # Check IB Gateway
  if check_tcp_port "localhost" "$IB_GATEWAY_PORT" 2; then
    log_success "IB Gateway: HEALTHY (port $IB_GATEWAY_PORT)"
  else
    log_error "IB Gateway: UNHEALTHY (port $IB_GATEWAY_PORT)"
    all_healthy=false
  fi

  # Check market-data based on environment
  if [ "$env" = "all" ] || [ "$env" = "uat" ]; then
    local uat_url="http://localhost:${UAT_MARKET_DATA_PORT}/actuator/health"
    if check_http_health "$uat_url" 5; then
      log_success "UAT Market Data: HEALTHY (port $UAT_MARKET_DATA_PORT)"
    else
      log_error "UAT Market Data: UNHEALTHY (port $UAT_MARKET_DATA_PORT)"
      all_healthy=false
    fi
  fi

  if [ "$env" = "all" ] || [ "$env" = "prod" ]; then
    local prod_url="http://localhost:${PROD_MARKET_DATA_PORT}/actuator/health"
    if check_http_health "$prod_url" 5; then
      log_success "Prod Market Data: HEALTHY (port $PROD_MARKET_DATA_PORT)"
    else
      log_error "Prod Market Data: UNHEALTHY (port $PROD_MARKET_DATA_PORT)"
      all_healthy=false
    fi
  fi

  echo ""
  if [ "$all_healthy" = true ]; then
    log_success "All services are HEALTHY"
    return 0
  else
    log_error "Some services are UNHEALTHY"
    return 1
  fi
}

# --- Main execution --------------------------------------------------------

main() {
  local env="all"
  local exit_code=0

  # Parse arguments
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --env)
        env="${2:-}"
        if [[ ! "$env" =~ ^(uat|prod|all)$ ]]; then
          log_error "Invalid --env value: $env (must be: uat, prod, or all)"
          usage
          exit 1
        fi
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        log_error "Unknown option: $1"
        usage
        exit 1
        ;;
    esac
  done

  echo ""
  echo "=========================================="
  echo "  IBKR Restart Script"
  echo "  Environment: ${env^^}"
  echo "  Started: $(timestamp)"
  echo "=========================================="

  # Step 1: Restart IB Gateway (shared between environments)
  if ! restart_ib_gateway; then
    exit_code=1
  fi

  # Step 2: Restart market-data for target environment(s)
  if [ "$env" = "all" ] || [ "$env" = "uat" ]; then
    if ! restart_market_data "uat"; then
      exit_code=1
    fi
  fi

  if [ "$env" = "all" ] || [ "$env" = "prod" ]; then
    if ! restart_market_data "prod"; then
      exit_code=1
    fi
  fi

  # Step 3: Verify all services
  verify_all_health "$env" || exit_code=1

  echo ""
  echo "=========================================="
  echo "  Finished: $(timestamp)"
  if [ "$exit_code" -eq 0 ]; then
    echo -e "  ${GREEN}RESULT: SUCCESS${NC}"
  else
    echo -e "  ${RED}RESULT: FAILURE${NC}"
  fi
  echo "=========================================="
  echo ""

  return "$exit_code"
}

main "$@"
